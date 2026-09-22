package vn.in3d.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.dto.SanPhamDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CHI PHÍ CHẠY MÁY IN mỗi giờ in = khấu hao máy + tiền điện + bảo trì.
 *
 *   khấu hao / giờ = TRUNG BÌNH theo từng chiếc máy (vat_tu loai = may_in, chưa xoá, tính theo
 *                    số lượng) của giá một chiếc ÷ tuổi thọ (giờ in)
 *   điện / giờ     = công suất trung bình (W) ÷ 1000 × giá điện (₫/kWh)
 *   bảo trì / giờ  = linh kiện hao mòn: đầu in, tấm in, ống PTFE, dầu mỡ... (₫ mỗi giờ in)
 *   tổng / giờ     = cộng ba phần ĐÃ LÀM TRÒN, để thẻ trên trang Quản lý vốn cộng khớp từng dòng
 *
 * Bốn định mức nằm ở bảng cai_dat (sửa ở trang Quản lý vốn); khoá chưa có dòng thì dùng số
 * mặc định đã nghiên cứu cho máy của shop (Bambu Lab A1 Combo, in PLA là chính), nguồn ở NGUON.
 *
 * Định mức thứ năm — nhựa in được mỗi giờ (g/giờ) — không đổi tiền mỗi giờ mà đổi nó ra tiền
 * máy MỖI GRAM cho giá bán đề xuất theo gram ở trang Quản lý vốn (xem nangSuat): đủ dữ liệu
 * giờ in của sản phẩm thì tự tính trung bình, chưa đủ mới dùng định mức này.
 *
 * Mọi số đọc từ bộ nhớ đệm (máy in: bộ VT; định mức: bộ CD) — không hỏi database khi đọc.
 * Tiền máy của từng sản phẩm KHÔNG nằm sẵn trong bộ nhớ đệm sản phẩm mà ghép lúc trả lời
 * (SanPhamDto.kemChiPhiMay), nên sửa định mức chỉ phải nạp lại đúng bộ CD.
 */
@Service
public class ChiPhiMayService {

    public static final String KHOA_TUOI_THO = "may_in.tuoi_tho_gio";
    public static final String KHOA_CONG_SUAT = "may_in.cong_suat_w";
    public static final String KHOA_GIA_DIEN = "dien.gia_kwh";
    public static final String KHOA_BAO_TRI = "may_in.bao_tri_moi_gio";
    public static final String KHOA_GRAM_MOI_GIO = "may_in.gram_moi_gio";

    /**
     * Mặc định (nghiên cứu 09/2026, nguồn ở NGUON):
     *   - tuổi thọ 5.000 giờ in: mốc "thận trọng" phổ biến nhất của các công cụ tính giá (3DPCC,
     *     Snapmaker, ResinCalc riêng cho A1); linh kiện hao mòn đã tính riêng ở phần bảo trì.
     *     Máy 10.900.000 ₫ -> 2.180 ₫/giờ.
     *   - công suất 100 W: hãng công bố A1 in PLA trung bình 95 W + AMS lite 3,69 W; đo thực tế
     *     25 giờ in ≈ 2,5 kWh. Đỉnh hâm bàn ~1.300 W chỉ vài chục giây, chia đều không đáng kể.
     *   - giá điện 3.500 ₫/kWh: giá biên bậc 4–6 của biểu sinh hoạt (QĐ 1279/QĐ-BCT) đã gồm VAT 8%
     *     — điện máy in cộng lên trên điện nền của gia đình nên rơi vào mấy bậc trên cùng.
     *   - bảo trì 2.100 ₫/giờ: cộng giá ÷ chu kỳ thay của từng linh kiện hao mòn (tấm PEI ~467,
     *     đầu in ~233, PTFE ~270, dao cắt ~200 ₫/giờ...) được ~2.136, làm tròn.
     */
    public static final double MAC_DINH_TUOI_THO_GIO = 5000;
    public static final double MAC_DINH_CONG_SUAT_W = 100;
    public static final double MAC_DINH_GIA_DIEN_KWH = 3500;
    public static final double MAC_DINH_BAO_TRI_MOI_GIO = 2100;
    /**
     * Nhựa in được mỗi giờ máy: giả định ~25 g/giờ của bản nghiên cứu cho A1 in PLA (cuộn 1 kg
     * ≈ 40 giờ in) — chỉ dùng khi chưa đủ SO_BIEN_THE_TU_TINH biến thể có giờ in để tự tính.
     */
    public static final double MAC_DINH_GRAM_MOI_GIO = 25;
    /** Có ít nhất bấy nhiêu biến thể có cả giờ in lẫn nhựa thì tự tính g/giờ từ dữ liệu. */
    public static final int SO_BIEN_THE_TU_TINH = 3;

    /** Nguồn của số nhựa in được mỗi giờ (nangSuat). */
    public static final String NGUON_DU_LIEU = "du-lieu";
    public static final String NGUON_CAI_DAT = "cai-dat";
    public static final String NGUON_MAC_DINH = "mac-dinh";

    /** Chặn số gõ nhầm quá tay (thêm vài số 0); số thật của shop nhỏ hơn rất xa. */
    private static final double GIOI_HAN = 1_000_000_000d;

    /**
     * Một định mức: tên ô trong body PUT, khoá trong cai_dat, mặc định, mô tả ghi kèm dòng
     * cai_dat (cho ai mở Supabase Dashboard đọc hiểu), tên trong câu báo lỗi.
     * laTien = số tiền: làm tròn tới đồng lúc lưu (giờ và oát thì giữ số lẻ).
     */
    private record DinhMuc(String oBody, String khoa, double macDinh, String moTa, String ten, boolean laTien) {}

    private static final List<DinhMuc> DINH_MUC = List.of(
            new DinhMuc("tuoiThoGio", KHOA_TUOI_THO, MAC_DINH_TUOI_THO_GIO,
                    "Tuổi thọ máy in (giờ in) — giá máy chia số giờ này ra khấu hao mỗi giờ", "Tuổi thọ máy", false),
            new DinhMuc("congSuatW", KHOA_CONG_SUAT, MAC_DINH_CONG_SUAT_W,
                    "Công suất trung bình khi in (W)", "Công suất máy", false),
            new DinhMuc("giaDienKwh", KHOA_GIA_DIEN, MAC_DINH_GIA_DIEN_KWH,
                    "Giá điện (₫/kWh, đã gồm VAT)", "Giá điện", true),
            new DinhMuc("baoTriMoiGio", KHOA_BAO_TRI, MAC_DINH_BAO_TRI_MOI_GIO,
                    "Bảo trì, linh kiện hao mòn (₫ mỗi giờ in)", "Chi phí bảo trì", true),
            new DinhMuc("gramMoiGio", KHOA_GRAM_MOI_GIO, MAC_DINH_GRAM_MOI_GIO,
                    "Nhựa in được mỗi giờ máy (g) — dùng khi chưa đủ 3 biến thể có giờ in để tự tính trung bình",
                    "Nhựa in được mỗi giờ", false));

    /** Nguồn của các số mặc định — trang Quản lý vốn in ra cho chủ shop đối chiếu. */
    private static final List<Map<String, Object>> NGUON = nguon(
            "Công suất: Bambu Lab Wiki — A1 in PLA trung bình 95 W, ABS 200 W, chờ khoảng 5 W; AMS lite khi chạy 3,69 W",
            "https://wiki.bambulab.com/en/general/power-consumption",
            "Công suất: đo thực tế trên diễn đàn Bambu — bản in 25 giờ trên A1 tốn khoảng 2,5 kWh (≈ 100 W)",
            "https://forum.bambulab.com/t/general-energy-usage/57113",
            "Công suất: diễn đàn Bambu — hâm bàn đỉnh khoảng 1.270 W chỉ trong khoảng 20 giây; khi in 70–100 W tuỳ tấm in",
            "https://forum.bambulab.com/t/power-consumption-for-a1-printer/92095/36",
            "Tuổi thọ: ResinCalc (tính riêng cho A1) — mốc thận trọng 5.000 giờ in",
            "https://resincalc.com/bambu-lab-a1-print-cost-calculator",
            "Tuổi thọ: 3DPCC — khấu hao = giá máy ÷ 5.000 giờ (điểm giữa khoảng 3.000–10.000 giờ)",
            "https://3dpcc.news/3d-printing-cost-formula",
            "Tuổi thọ: Snapmaker — tuổi thọ máy thường ước khoảng 5.000 giờ; công suất mặc định 100 W",
            "https://www.snapmaker.com/blog/how-to-calculate-your-3d-printing-costs/",
            "Tuổi thọ: Printpal — máy in để bàn sống khoảng 3.000–10.000 giờ in",
            "https://printpal.io/tools/3d-print-cost-calculator",
            "Bảo trì: Bambu Lab Wiki — lịch bảo dưỡng A1 (thay PTFE mỗi 6 cuộn, xem dao cắt mỗi 3 cuộn, bôi trơn hằng tháng / 3 tháng)",
            "https://wiki.bambulab.com/en/a1/maintenance/basic-maintenance",
            "Bảo trì: pea3d — đầu in 1.500–2.000 giờ (PLA/PETG), curoa 4.000–5.000 giờ, quạt khoảng 5.000 giờ",
            "https://pea3d.com/en/bambu-lab-life-expectancy-calculator-health-analysis/",
            "Bảo trì: Meme3D — đầu in (hotend) A1 giá 260.000 ₫",
            "https://www.meme3d.com/san-pham/dau-in-cho-may-in-3d-bambu-lab-a1-series-hotend/",
            "Bảo trì: Meme3D — tấm in Textured PEI 256×256 giá 700.000 ₫",
            "https://www.meme3d.com/san-pham/tam-ban-pei-kep-hang-bambu-lab-dual-texture-pei-plate/",
            "Bảo trì: RenderWrench — dự phòng bảo trì thường 0,05–0,30 USD mỗi giờ in",
            "https://renderwrench.com/how-to-calculate-cost-of-3d-printing-accurately-real-hourly-breakdown/",
            "Giá điện: EVNHCMC — biểu giá QĐ 1279/QĐ-BCT, sinh hoạt bậc 4 / 5 / 6: 2.998 / 3.350 / 3.460 ₫/kWh chưa VAT",
            "https://cskh.evnhcmc.vn/Tracuu/giabandien",
            "Giá điện: thuế GTGT tiền điện 8% đến hết 31/12/2026 (NQ 204/2025/QH15)",
            "https://apluslaw.vn/doanh-nghiep/thue-gtgt-tien-dien.html",
            "Giá điện: TT 60/2025/TT-BCT — công tơ sinh hoạt dùng chung cho kinh doanh vẫn tính giá sinh hoạt",
            "https://atld.vn/law/4127/content",
            // Không có trang nào công bố: số giả định của bản nghiên cứu, nên không kèm link
            "Nhựa in được mỗi giờ: giả định khoảng 25 g/giờ cho A1 in PLA (cuộn 1 kg ≈ 40 giờ in)",
            null);

    /**
     * Kết quả tính. Định mức là số ĐANG DÙNG (cai_dat hoặc mặc định), chưa làm tròn;
     * mọi số tiền mỗi giờ đã làm tròn tới đồng, tongMoiGio = khấu hao + điện + bảo trì.
     * gramMoiGio là ĐỊNH MỨC nhựa in được mỗi giờ (gramMoiGioTuCaiDat = có dòng cai_dat hợp lệ);
     * số g/giờ thật sự dùng để tính giá thì xem nangSuat.
     */
    public record ChiPhiMay(long soMayIn, long giaMayTrungBinh, double tuoiThoGio, double congSuatW,
                            double giaDienKwh, double baoTriMoiGio,
                            long khauHaoMoiGio, long dienMoiGio, long baoTriLamTron, long tongMoiGio,
                            double gramMoiGio, boolean gramMoiGioTuCaiDat) {}

    /**
     * Nhựa in được mỗi giờ máy đang dùng để tính giá: gramMoiGio (g/giờ, 1 chữ số lẻ, luôn dương),
     * nguon = du-lieu | cai-dat | mac-dinh, soBienThe = số biến thể có cả giờ in lẫn nhựa
     * (đếm cả khi chưa đủ SO_BIEN_THE_TU_TINH để dùng).
     */
    public record NangSuat(double gramMoiGio, String nguon, int soBienThe) {}

    private final BoNhoDem boNho;
    private final JdbcTemplate jdbc;

    public ChiPhiMayService(BoNhoDem boNho, JdbcTemplate jdbc) {
        this.boNho = boNho;
        this.jdbc = jdbc;
    }

    /* ---------------- Tính ---------------- */

    /** Tính trên bộ nhớ đệm (kho + cài đặt). Đọc được thì không hỏi database. */
    public ChiPhiMay tinh() {
        List<Map<String, Object>> kho = boNho.dsVatTu();
        return tinh(kho, boNho.caiDat().theoKhoa());
    }

    /**
     * Như tinh() nhưng bộ nhớ đệm lỗi thì trả null thay vì ném: chỗ chỉ GHÉP tiền máy vào
     * danh sách sản phẩm thà thiếu mấy ô tiền máy còn hơn làm hỏng cả trang sản phẩm.
     */
    public ChiPhiMay tinhAnToan() {
        try {
            return tinh();
        } catch (RuntimeException boQua) {
            return null;
        }
    }

    /**
     * Hàm thuần (không đọc gì thêm) — tính từ danh sách kho dạng GET /api/vat-tu và map cai_dat.
     * Máy in có số lượng 0 (đã bán / chưa về) không tính; kho không có máy nào thì khấu hao 0,
     * điện và bảo trì vẫn tính (giờ in vẫn tốn điện, vẫn mòn đầu in).
     */
    public static ChiPhiMay tinh(List<Map<String, Object>> dsVatTu, Map<String, String> caiDat) {
        long soMay = 0;
        double tongGia = 0;
        for (Map<String, Object> v : dsVatTu == null ? List.<Map<String, Object>>of() : dsVatTu) {
            if (!"may_in".equals(v.get("loai")) || Boolean.TRUE.equals(v.get("daXoa"))) continue;
            long sl = so(v.get("soLuong"));
            if (sl <= 0) continue;
            soMay += sl;
            tongGia += (double) so(v.get("gia")) * sl;
        }
        double giaTrungBinh = soMay == 0 ? 0 : tongGia / soMay;
        Map<String, String> cd = caiDat == null ? Map.of() : caiDat;
        double tuoiTho = dinhMuc(cd, KHOA_TUOI_THO, MAC_DINH_TUOI_THO_GIO);
        double congSuat = dinhMuc(cd, KHOA_CONG_SUAT, MAC_DINH_CONG_SUAT_W);
        double giaDien = dinhMuc(cd, KHOA_GIA_DIEN, MAC_DINH_GIA_DIEN_KWH);
        double baoTri = dinhMuc(cd, KHOA_BAO_TRI, MAC_DINH_BAO_TRI_MOI_GIO);
        double gramMoiGio = dinhMuc(cd, KHOA_GRAM_MOI_GIO, MAC_DINH_GRAM_MOI_GIO);
        // Dòng cai_dat hỏng (không phải số dương) thì dinhMuc đã quay về mặc định: nguồn là mặc định
        boolean gramTuCaiDat = dinhMuc(cd, KHOA_GRAM_MOI_GIO, -1) > 0;

        long khauHao = Math.round(giaTrungBinh / tuoiTho);
        long dien = Math.round(congSuat / 1000 * giaDien);
        long baoTriLamTron = Math.round(baoTri);
        return new ChiPhiMay(soMay, Math.round(giaTrungBinh), tuoiTho, congSuat, giaDien, baoTri,
                khauHao, dien, baoTriLamTron, khauHao + dien + baoTriLamTron,
                gramMoiGio, gramTuCaiDat);
    }

    /**
     * NHỰA IN ĐƯỢC MỖI GIỜ MÁY (g/giờ) — chia tiền máy mỗi giờ ra tiền máy mỗi gram, và ước
     * giờ in của mẫu chỉ biết số gram.
     *   - Tự tính trung bình từ dữ liệu: các biến thể chưa xoá của sản phẩm chưa xoá có giờ in > 0
     *     và nhựa > 0 -> Σ gram MỘT cái (gồm gram thừa) ÷ Σ giờ in MỘT cái. Trung bình có trọng số
     *     theo giờ in (mẫu in lâu nặng ký hơn), không phải trung bình cộng các tỉ số. Gram một cái
     *     = tổng nhựa ÷ số cái đã in — đúng cách kemChiPhiMay chia tiền nhựa ra một cái.
     *     Cần ít nhất SO_BIEN_THE_TU_TINH biến thể như vậy, ít hơn thì một mẫu lạ kéo lệch cả giá.
     *   - Chưa đủ: định mức may_in.gram_moi_gio trong cai_dat, chưa có thì mặc định 25 g/giờ.
     * Làm tròn 1 chữ số lẻ — đúng số trang hiện ra, để phép chia in trên trang khớp với kết quả.
     *
     * @param dsSanPham DTO sản phẩm chưa xoá (BoNhoDem.dsSanPham(true)), bienThe[] là biến thể chưa xoá
     */
    public static NangSuat nangSuat(List<Map<String, Object>> dsSanPham, ChiPhiMay cp) {
        double tongGram = 0, tongPhut = 0;
        int soBienThe = 0;
        for (Map<String, Object> sp : dsSanPham == null ? List.<Map<String, Object>>of() : dsSanPham) {
            if (Boolean.TRUE.equals(sp.get("daXoa")) || !(sp.get("bienThe") instanceof List<?> ds)) continue;
            for (Object o : ds) {
                if (!(o instanceof Map<?, ?> bt)) continue;
                long phut = so(bt.get("thoiGianInPhut"));
                long gram = so(bt.get("tongGramNhua"));
                if (phut <= 0 || gram <= 0) continue;
                tongGram += (double) gram / Math.max(1, so(bt.get("soLuong")));
                tongPhut += phut;
                soBienThe++;
            }
        }
        if (soBienThe >= SO_BIEN_THE_TU_TINH) {
            double g = motSoLe(tongGram * 60 / tongPhut);
            // Toàn mẫu siêu nhẹ in rất lâu có thể làm tròn ra 0: khi đó chia cho 0.1 chứ đừng chia cho 0
            return new NangSuat(Math.max(0.1, g), NGUON_DU_LIEU, soBienThe);
        }
        if (cp != null && cp.gramMoiGioTuCaiDat()) {
            return new NangSuat(Math.max(0.1, motSoLe(cp.gramMoiGio())), NGUON_CAI_DAT, soBienThe);
        }
        return new NangSuat(MAC_DINH_GRAM_MOI_GIO, NGUON_MAC_DINH, soBienThe);
    }

    /** 25.04 -> 25.0, 22.37 -> 22.4. */
    private static double motSoLe(double x) {
        return Math.round(x * 10) / 10.0;
    }

    /** Giá trị trong cai_dat; thiếu, không phải số hoặc không dương (sửa tay hỏng) thì mặc định. */
    private static double dinhMuc(Map<String, String> cd, String khoa, double macDinh) {
        String s = cd.get(khoa);
        if (s == null || s.isBlank()) return macDinh;
        try {
            double x = Double.parseDouble(s.trim());
            return x > 0 && !Double.isInfinite(x) ? x : macDinh;
        } catch (NumberFormatException hong) {
            return macDinh;
        }
    }

    /**
     * Object khoá chi-phi-may của khoi-tao (và response của PUT):
     * { soMayIn, giaMayTrungBinh, tuoiThoGio, congSuatW, giaDienKwh, baoTriMoiGio,
     *   khauHaoMoiGio, dienMoiGio, tongMoiGio, gramMoiGio, macDinh: {5 định mức},
     *   nguon: [{noiDung, link}] }
     * Tiền là số nguyên đồng; giờ / oát / gram giữ số lẻ nếu chủ shop nhập lẻ.
     * gramMoiGio ở đây là ĐỊNH MỨC (cai_dat hoặc mặc định), không phải số tự tính từ sản phẩm.
     */
    public static Map<String, Object> thanhMap(ChiPhiMay cp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("soMayIn", cp.soMayIn());
        m.put("giaMayTrungBinh", cp.giaMayTrungBinh());
        m.put("tuoiThoGio", soGon(cp.tuoiThoGio()));
        m.put("congSuatW", soGon(cp.congSuatW()));
        m.put("giaDienKwh", Math.round(cp.giaDienKwh()));
        m.put("baoTriMoiGio", cp.baoTriLamTron());
        m.put("khauHaoMoiGio", cp.khauHaoMoiGio());
        m.put("dienMoiGio", cp.dienMoiGio());
        m.put("tongMoiGio", cp.tongMoiGio());
        m.put("gramMoiGio", soGon(cp.gramMoiGio()));
        Map<String, Object> macDinh = new LinkedHashMap<>();
        for (DinhMuc d : DINH_MUC) {
            macDinh.put(d.oBody(), d.laTien() ? (Number) Math.round(d.macDinh()) : soGon(d.macDinh()));
        }
        m.put("macDinh", macDinh);
        m.put("nguon", NGUON);
        return m;
    }

    /* ---------------- Ghép vào DTO sản phẩm của trang quản trị ---------------- */

    /** Danh sách sản phẩm kèm tiền máy; cp null (bộ nhớ đệm lỗi) thì trả nguyên danh sách. */
    public static List<Map<String, Object>> kemChiPhi(List<Map<String, Object>> ds, ChiPhiMay cp) {
        if (cp == null || ds == null) return ds;
        List<Map<String, Object>> ra = new ArrayList<>(ds.size());
        for (Map<String, Object> sp : ds) ra.add(SanPhamDto.kemChiPhiMay(sp, cp.tongMoiGio()));
        return Collections.unmodifiableList(ra);
    }

    /** Một sản phẩm kèm tiền máy; cp hoặc sp null thì trả nguyên. */
    public static Map<String, Object> kemChiPhi(Map<String, Object> sp, ChiPhiMay cp) {
        if (cp == null || sp == null || !sp.containsKey("bienThe")) return sp;
        return SanPhamDto.kemChiPhiMay(sp, cp.tongMoiGio());
    }

    /* ---------------- PUT /api/quan-tri/chi-phi-may ---------------- */

    /**
     * Lưu định mức: {tuoiThoGio?, congSuatW?, giaDienKwh?, baoTriMoiGio?, gramMoiGio?}.
     *   - ô không gửi: để yên;
     *   - ô gửi null / rỗng: xoá dòng cai_dat -> quay về mặc định;
     *   - ô có số: phải là số dương (tiền làm tròn tới đồng) -> ghi đè dòng cai_dat.
     * Kiểm tra HẾT rồi mới ghi, và ghi bằng MỘT câu lệnh (xoá + upsert trong một CTE): một lượt
     * đi-về, không bao giờ lưu nửa chừng. Đã ghi thì nạp lại bộ CD rồi trả object chi-phi-may.
     *
     * Đã commit thì KHÔNG được trả 5xx: nạp lại lỗi thì tính từ bản chụp đọc TRƯỚC lúc ghi
     * cộng phần vừa ghi (đọc lại bộ nhớ đệm lúc đó là mở thêm một lượt nạp, lỗi tiếp là ném).
     */
    public Map<String, Object> luu(Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Map<String, String> ghi = new LinkedHashMap<>();       // khoá -> giá trị chuỗi
        Map<String, String> moTa = new LinkedHashMap<>();
        List<String> xoa = new ArrayList<>();
        for (DinhMuc d : DINH_MUC) {
            if (!b.containsKey(d.oBody())) continue;
            Object v = b.get(d.oBody());
            String s = v == null ? "" : String.valueOf(v).trim();
            if (s.isEmpty() || "null".equals(s)) {
                xoa.add(d.khoa());
                continue;
            }
            double x;
            try {
                x = v instanceof Number n ? n.doubleValue() : Double.parseDouble(s);
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, d.ten() + " phải là số dương.");
            }
            if (d.laTien()) x = Math.round(x);
            if (!(x > 0) || Double.isInfinite(x) || x > GIOI_HAN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, d.ten() + " phải là số dương.");
            }
            ghi.put(d.khoa(), chuoiSo(x));
            moTa.put(d.khoa(), d.moTa());
        }
        if (ghi.isEmpty() && xoa.isEmpty()) return thanhMap(tinh());   // không đổi gì

        // Bản chụp TRƯỚC khi ghi, chỉ để dựng response khi nạp lại lỗi
        List<Map<String, Object>> khoTruoc = null;
        Map<String, String> cdTruoc = null;
        try {
            khoTruoc = boNho.dsVatTu();
            cdTruoc = boNho.caiDat().theoKhoa();
        } catch (RuntimeException boQua) {
            // database đang chớp: vẫn ghi được thì cứ ghi, phần dự phòng tính với những gì có
        }

        String[] khoaXoa = xoa.toArray(new String[0]);
        String[] khoaGhi = ghi.keySet().toArray(new String[0]);
        String[] giaTri = ghi.values().toArray(new String[0]);
        String[] dsMoTa = moTa.values().toArray(new String[0]);
        // CTE xoá luôn chạy dù câu chính không đọc tới nó; một khoá không bao giờ vừa xoá vừa ghi
        jdbc.update("with xoa as (delete from cai_dat where khoa = any(?::text[])) "
                + "insert into cai_dat (khoa, gia_tri, mo_ta, updated_at) "
                + "select t.khoa, t.gia_tri, t.mo_ta, now() "
                + "from unnest(?::text[], ?::text[], ?::text[]) as t(khoa, gia_tri, mo_ta) "
                + "on conflict (khoa) do update set gia_tri = excluded.gia_tri, mo_ta = excluded.mo_ta, "
                + "updated_at = now()", ps -> {
            ps.setArray(1, ps.getConnection().createArrayOf("text", khoaXoa));
            ps.setArray(2, ps.getConnection().createArrayOf("text", khoaGhi));
            ps.setArray(3, ps.getConnection().createArrayOf("text", giaTri));
            ps.setArray(4, ps.getConnection().createArrayOf("text", dsMoTa));
        });

        if (boNho.xoaVaNapLai(BoNhoDem.CD)) {
            try {
                return thanhMap(tinh());
            } catch (RuntimeException boQua) {
                // rơi xuống bản dự phòng
            }
        }
        Map<String, String> cdSau = new LinkedHashMap<>(cdTruoc == null ? Map.of() : cdTruoc);
        xoa.forEach(cdSau::remove);
        cdSau.putAll(ghi);
        return thanhMap(tinh(khoTruoc, cdSau));
    }

    /* ---------------- Dùng chung ---------------- */

    private static long so(Object v) { return v instanceof Number n ? n.longValue() : 0L; }

    /** 5000.0 -> 5000 (số nguyên cho JSON gọn), 98.7 giữ nguyên. TongHopQuanTri dùng chung. */
    static Number soGon(double x) {
        return x == Math.rint(x) && Math.abs(x) < 1e15 ? (Number) Math.round(x) : (Number) x;
    }

    /** Số ghi vào cai_dat.gia_tri: "5000", "98.7" (không số mũ, không đuôi .0). */
    private static String chuoiSo(double x) {
        return BigDecimal.valueOf(x).stripTrailingZeros().toPlainString();
    }

    private static List<Map<String, Object>> nguon(String... cap) {
        List<Map<String, Object>> ra = new ArrayList<>();
        for (int i = 0; i + 1 < cap.length; i += 2) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("noiDung", cap[i]);
            m.put("link", cap[i + 1]);
            ra.add(Collections.unmodifiableMap(m));
        }
        return Collections.unmodifiableList(ra);
    }
}
