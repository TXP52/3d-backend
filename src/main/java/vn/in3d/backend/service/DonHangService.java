package vn.in3d.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.dto.DatHangRequest;
import vn.in3d.backend.dto.DonTayRequest;
import vn.in3d.backend.entity.DonHang;
import vn.in3d.backend.entity.DonHangChiTiet;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.entity.ThanhToan;
import vn.in3d.backend.repository.DonHangRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nghiệp vụ đơn hàng: tính giá giỏ hàng, tạo đơn (khách đặt / shop gõ tay),
 * đổi trạng thái, xác nhận thanh toán, và TRỪ - TRẢ KHO theo biến thể.
 *
 * Kho thật nằm ở bien_the.ton_kho (xem BienThe). Luật (hợp đồng mục 1):
 *   - tạo đơn: trừ kho từng dòng; huỷ đơn: trả lại; bỏ huỷ: trừ lại; xoá đơn: trả lại
 *   - trả lại ĐÚNG MỘT LẦN: đơn đang huỷ mà xoá thì không trả thêm lần nữa
 *   - tồn kho ÂM được (shop in theo đơn), không bao giờ chặn đơn vì hết hàng
 *   - mọi thay đổi kho là câu UPDATE cộng dồn thẳng trong database, không đọc lên
 *     Java rồi ghi đè: hai đơn cùng lúc mới không nuốt mất của nhau
 * Xong thì đồng bộ lại san_pham.ton_kho = tổng tồn các biến thể để chỗ đọc cũ vẫn đúng.
 */
@Service
public class DonHangService {

    /** Cách thanh toán database nhận (ràng buộc CHECK của bảng thanh_toan). */
    private static final Set<String> PHUONG_THUC_HOP_LE = Set.of("cod", "chuyen_khoan", "vi_dien_tu", "the");

    private final DonHangRepository donHangRepo;
    private final KhuyenMaiService khuyenMaiService;
    private final BoNhoDem boNho;
    private final TransactionTemplate giaoDich;
    private final JdbcTemplate jdbc;

    public DonHangService(DonHangRepository donHangRepo,
                          KhuyenMaiService khuyenMaiService,
                          BoNhoDem boNho,
                          TransactionTemplate giaoDich,
                          JdbcTemplate jdbc) {
        this.donHangRepo = donHangRepo;
        this.khuyenMaiService = khuyenMaiService;
        this.boNho = boNho;
        this.giaoDich = giaoDich;
        this.jdbc = jdbc;
    }

    /* ============================================================
       TÍNH GIÁ — MỘT chỗ duy nhất, dùng chung cho giỏ hàng và tạo đơn
       ============================================================ */

    /**
     * Thông tin tối thiểu của một món để tính giá, lấy từ bộ nhớ đệm sản phẩm.
     *
     * @param id         id SẢN PHẨM
     * @param gia        giá của BIẾN THỂ đã chọn (biến thể để trống giá thì là giá sản phẩm)
     * @param bienTheId  biến thể sẽ bị trừ kho; null = món không gắn biến thể nào
     * @param tenBienThe tên phân loại, null với sản phẩm không phân loại
     * @param hetHang    trạng thái của biến thể (hoặc của sản phẩm) đang là "hết hàng"
     */
    public record MonHang(Long id, String ten, long gia, String loaiSanPham, String hinhAnh,
                          Long bienTheId, String tenBienThe, boolean hetHang) {}

    /** Cách tra món hàng lúc tính giá — xem traTuBoNhoDem. */
    public interface TraMon {
        MonHang theoBienThe(Long bienTheId);
        MonHang theoSanPham(Long sanPhamId);
        MonHang theoTen(String ten);
    }

    /** Tính giá cho ai: giỏ hàng xem trước, khách đặt thật, hay chủ shop gõ đơn tay. */
    public enum Kieu {
        /** Báo giá ở giỏ: món nào không đặt được thì ghi lý do vào dòng rồi tính tiếp. */
        BAO_GIA,
        /** Khách đặt hàng: hễ có dòng không đặt được hoặc mã sai là 400, đơn không được tạo. */
        DAT_HANG,
        /**
         * Chủ shop gõ đơn tay: chỉ chặn khi món chọn từ danh sách mà tra không ra
         * (chọn nhầm / vừa bị xoá); hàng mẫu hay hàng hết vẫn bán được, và mỗi dòng
         * được ghi đè đơn giá.
         */
        DON_TAY
    }

    /**
     * Một dòng giỏ hàng đã tính giá.
     * @param sanPhamId null = món không có trong bảng sản phẩm (in theo yêu cầu...)
     * @param bienTheId phân loại sẽ bị trừ kho; null = dòng không đụng tới kho
     * @param loi       null = đặt được; khác null = lý do không đặt được món này
     */
    public record DongGia(Long sanPhamId, Long bienTheId, String ten, String tenBienThe, String hinhAnh,
                          long giaGoc, long donGia, int soLuong, boolean coTheDat, String loi) {
        public long thanhTien() { return donGia * soLuong; }
        public long tienGiamDong() { return Math.max(0, (giaGoc - donGia) * soLuong); }
    }

    /**
     * Cả giỏ đã tính giá.
     * @param tamTinh         tiền hàng SAU giảm giá món, TRƯỚC mã đơn (= DonHang.tamTinh);
     *                        chỉ cộng các dòng đặt được (loi = null)
     * @param tienGiamSanPham tổng tiền giảm nhờ khuyến mãi sản phẩm (cũng chỉ các dòng đặt được)
     * @param khuyenMai       kết quả áp mã đơn hàng, null nếu không nhập mã hoặc mã không dùng được
     * @param loiMa           lý do mã không dùng được (chỉ khi báo giá; đặt hàng thì ném lỗi luôn)
     * @param tongCong        khách phải trả = tamTinh - tiền giảm của mã
     */
    public record BangGia(List<DongGia> dong, long tamTinh, long tienGiamSanPham,
                          KhuyenMaiService.KetQua khuyenMai, String loiMa, long tongCong) {}

    /**
     * Tra món trên MỘT bản chụp bộ nhớ đệm sản phẩm — giỏ hàng (bao-gia), đơn khách
     * đặt và đơn shop gõ tay đều đi qua đây nên ba nơi không thể ra giá khác nhau.
     *
     * Nơi gọi phải lấy bản chụp TRƯỚC khi mở transaction: đọc bộ nhớ đệm lúc đang
     * mở transaction là để một lượt nạp (thêm một kết nối, thêm một lượt đi-về)
     * chen vào giữa trong khi mình đang giữ khoá dòng.
     */
    public static TraMon traTuBoNhoDem(BoNhoDem.DuLieuSanPham ban) {
        return new TraMon() {
            @Override
            public MonHang theoBienThe(Long bienTheId) {
                Map<String, Object> bt = bienTheId == null ? null : ban.bienTheTheoId().get(bienTheId);
                if (bt == null) return null;          // biến thể đã xoá / không có thật
                Long spId = ban.sanPhamCuaBienThe().get(bienTheId);
                return monHang(spId == null ? null : ban.theoId().get(spId), bt);
            }

            @Override
            public MonHang theoSanPham(Long sanPhamId) {
                return monHang(sanPhamId == null ? null : ban.theoId().get(sanPhamId), null);
            }

            @Override
            public MonHang theoTen(String ten) {
                // Trùng tên thì lấy sản phẩm id nhỏ nhất (danh sách đã xếp theo id)
                for (Map<String, Object> sp : ban.danhSach()) {
                    Object t = sp.get("ten");
                    if (t != null && String.valueOf(t).equalsIgnoreCase(ten)) return monHang(sp, null);
                }
                return null;
            }
        };
    }

    /**
     * DTO sản phẩm (+ biến thể) trong bộ nhớ đệm -> thông tin tính giá.
     * Sản phẩm đã xoá mềm coi như không còn bán.
     *
     * Không chỉ rõ biến thể thì lấy biến thể MẶC ĐỊNH: giỏ hàng cũ chưa biết biến thể
     * vẫn đặt được và VẪN TRỪ ĐÚNG KHO, còn web khách bấm ĐẶT HÀNG ngay trên thẻ sản
     * phẩm cũng chính là mua biến thể mặc định.
     */
    private static MonHang monHang(Map<String, Object> sp, Map<String, Object> bienThe) {
        if (sp == null || Boolean.TRUE.equals(sp.get("daXoa"))) return null;
        Map<String, Object> bt = bienThe != null ? bienThe : bienTheMacDinh(sp);
        long gia = bt != null ? so(bt.get("giaHienThi")) : so(sp.get("gia"));
        String anh = bt != null && bt.get("hinhAnh") != null ? (String) bt.get("hinhAnh") : (String) sp.get("hinhAnh");
        String trangThai = bt != null ? (String) bt.get("trangThaiHienThi") : (String) sp.get("trangThai");
        return new MonHang((Long) sp.get("id"), (String) sp.get("ten"), gia,
                (String) sp.get("loaiSanPham"), anh,
                bt == null ? null : (Long) bt.get("id"), bt == null ? null : (String) bt.get("ten"),
                "het_hang".equals(trangThai));
    }

    /** Biến thể mặc định của sản phẩm — bienThe[] đã xếp mặc định lên đầu (xem SanPhamDto). */
    private static Map<String, Object> bienTheMacDinh(Map<String, Object> sp) {
        Object ds = sp.get("bienThe");
        if (!(ds instanceof List<?> danhSach) || danhSach.isEmpty()) return null;
        for (Object o : danhSach) {
            if (o instanceof Map<?, ?> bt && Boolean.TRUE.equals(bt.get("macDinh"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> chon = (Map<String, Object>) bt;
                return chon;
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> dau = (Map<String, Object>) danhSach.get(0);
        return dau;
    }

    /**
     * Tính giá cả giỏ. Báo giá, đặt hàng và đơn gõ tay đều gọi hàm này, nên số tiền
     * khách thấy ở giỏ hàng đúng bằng số tiền của đơn được tạo.
     *
     * Luật (giữ nguyên bản cũ của datHang, thêm phần biến thể):
     *   - món có bienTheId -> theo biến thể đó (giá riêng của biến thể, kho của biến thể);
     *     biến thể đó không còn thì dòng hỏng luôn, KHÔNG dò tiếp sang sản phẩm / tên
     *   - không gửi bienTheId thì theo sanPhamId, không nữa thì tra theo TÊN (không phân
     *     biệt hoa thường) như bản cũ; hai đường này lấy biến thể MẶC ĐỊNH của sản phẩm
     *   - món tìm thấy: giá lấy từ database rồi trừ khuyến mãi sản phẩm tốt nhất
     *   - món không có trong bảng: đành theo giá gửi lên (không nhận giá âm)
     *   - món gửi kèm id mà tra không ra (đã xoá khỏi shop): coTheDat = false kèm lý do,
     *     KHÔNG rơi về giá 0 rồi tạo đơn 0₫
     *   - hàng mẫu (loai = mau) chỉ trưng bày, phân loại đang "hết hàng" thì shop
     *     không nhận đặt: coTheDat = false kèm lý do
     *   - mã đơn hàng tính trên tổng tiền ĐÃ giảm giá món
     *
     * @param tra         cách tra món (traTuBoNhoDem)
     * @param dsKhuyenMai khuyến mãi CHƯA xoá đã nạp sẵn — dùng cho cả giá món lẫn mã đơn
     * @param kieu        xem Kieu
     */
    public BangGia tinhBangGia(List<DatHangRequest.MatHang> matHang, TraMon tra,
                               List<KhuyenMai> dsKhuyenMai, String maKhuyenMai,
                               KhuyenMaiService.NguoiDat nguoiDat, Kieu kieu) {
        List<KhuyenMai> kmSanPham = khuyenMaiService.khuyenMaiSanPhamDangChay(dsKhuyenMai);
        List<DongGia> dsDong = new ArrayList<>();
        long tongTien = 0;
        long giamSanPham = 0;

        for (DatHangRequest.MatHang mh : matHang == null ? List.<DatHangRequest.MatHang>of() : matHang) {
            if (mh == null) continue;
            String tenGui = mh.ten() == null ? "" : mh.ten().trim();
            // Giá âm thì coi như 0: không để một dòng "giảm tiền" cả đơn
            long giaGui = mh.donGia() == null ? 0 : Math.max(0, mh.donGia());
            int soLuong = mh.soLuong() == null ? 1 : mh.soLuong();

            MonHang sp = null;
            // Chọn rõ phân loại mà phân loại đó không còn: KHÔNG được rơi về sản phẩm
            // rồi bán biến thể mặc định — khách chọn "Đỏ" thì không thể giao "Xám"
            boolean matBienThe = false;
            if (mh.bienTheId() != null) {
                sp = tra.theoBienThe(mh.bienTheId());
                matBienThe = sp == null;
            }
            if (sp == null && !matBienThe) {
                if (mh.sanPhamId() != null) sp = tra.theoSanPham(mh.sanPhamId());
                if (sp == null && !tenGui.isEmpty()) sp = tra.theoTen(tenGui);
            }

            long giaGoc = sp != null ? sp.gia() : giaGui;
            KhuyenMaiService.GiaMon gia = sp != null
                    ? khuyenMaiService.giaSauGiam(sp.id(), giaGoc, kmSanPham)
                    : new KhuyenMaiService.GiaMon(giaGoc, giaGoc, 0, null);
            long donGia = gia.giaSauGiam();
            // Đơn gõ tay: chủ shop bớt cho khách quen thì lấy đúng giá chủ shop gõ
            if (kieu == Kieu.DON_TAY && sp != null && mh.donGia() != null) donGia = Math.max(0, mh.donGia());

            // Món giỏ hàng gửi kèm id mà tra không ra = sản phẩm / phân loại đã bị xoá khỏi shop.
            // Dòng đó KHÔNG đặt được: giá gửi lên của dòng kiểu này là 0 (giỏ chỉ giữ id),
            // để nguyên thì giỏ hiện "Liên hệ" mà đơn thật lại thành một dòng 0₫.
            boolean khongConBan = sp == null && (mh.bienTheId() != null || mh.sanPhamId() != null);
            boolean hangMau = sp != null && "mau".equals(sp.loaiSanPham());
            boolean hetHang = sp != null && sp.hetHang();
            boolean coTheDat = !khongConBan && !hangMau && !hetHang;
            String loi = null;
            if (khongConBan && matBienThe) {
                loi = tenGui.isEmpty()
                        ? "Phân loại bạn chọn shop không còn bán nữa, bạn chọn lại nhé."
                        : "\"" + tenGui + "\" không còn phân loại bạn đã chọn, bạn chọn lại nhé.";
            } else if (khongConBan) {
                loi = tenGui.isEmpty()
                        ? "Món này shop không còn bán nữa, bạn xoá khỏi giỏ nhé."
                        : "\"" + tenGui + "\" shop không còn bán nữa, bạn xoá món này khỏi giỏ nhé.";
            } else if (hangMau) {
                loi = "\"" + sp.ten() + "\" là hàng mẫu trưng bày, shop không nhận đặt món này.";
            } else if (hetHang) {
                loi = "\"" + tenDayDu(sp) + "\" đang hết hàng, bạn chọn phân loại khác nhé.";
            } else if (soLuong <= 0) {
                loi = "Số lượng phải lớn hơn 0";
            } else if (sp == null && tenGui.isEmpty()) {
                loi = "Thiếu tên sản phẩm";
            }
            // Khách đặt hàng thì dòng nào hỏng cũng chặn; đơn gõ tay chỉ chặn dòng
            // chủ shop không sửa được bằng cách gõ tay (chọn nhầm món, thiếu tên, số lượng 0)
            boolean chanDon = kieu == Kieu.DAT_HANG
                    || (kieu == Kieu.DON_TAY && (khongConBan || soLuong <= 0 || (sp == null && tenGui.isEmpty())));
            if (loi != null && chanDon) {
                // Chủ shop chọn món từ danh sách chứ không có giỏ hàng để xoá
                throw loi400(kieu == Kieu.DON_TAY && khongConBan
                        ? (tenGui.isEmpty() ? "Món đã chọn không còn trong shop, bạn chọn lại nhé."
                                            : "\"" + tenGui + "\" không còn trong shop, bạn chọn lại nhé.")
                        : loi);
            }

            DongGia d = new DongGia(sp != null ? sp.id() : null, sp != null ? sp.bienTheId() : null,
                    sp != null ? sp.ten() : tenGui, sp != null ? sp.tenBienThe() : null,
                    sp != null ? sp.hinhAnh() : null, gia.giaGoc(), donGia, soLuong, coTheDat, loi);
            dsDong.add(d);
            // Dòng có lỗi không bao giờ thành đơn (đặt hàng đã bị chặn ở trên) nên không cộng
            // vào tiền; riêng đơn gõ tay thì hàng mẫu / hàng hết vẫn bán nên vẫn cộng
            if (loi == null || kieu == Kieu.DON_TAY) {
                tongTien += d.thanhTien();
                giamSanPham += d.tienGiamDong();
            }
        }

        // Mã khuyến mãi đơn hàng — TÍNH LẠI TỪ ĐẦU, không nhận số tiền giảm trình duyệt gửi lên.
        // Mã sai / hết hạn / chưa đủ điều kiện: tạo đơn thì 400 (đơn không được tạo), báo giá thì ghi loiMa.
        KhuyenMaiService.KetQua kq = null;
        String loiMa = null;
        if (maKhuyenMai != null && !maKhuyenMai.isBlank()) {
            try {
                kq = khuyenMaiService.kiemTra(maKhuyenMai, tongTien,
                        nguoiDat == null ? KhuyenMaiService.NguoiDat.khongRo() : nguoiDat, dsKhuyenMai);
            } catch (ResponseStatusException e) {
                if (kieu != Kieu.BAO_GIA) throw e;
                loiMa = e.getReason();
            }
        }
        long tongCong = kq != null ? kq.conLai() : tongTien;
        return new BangGia(dsDong, tongTien, giamSanPham, kq, loiMa, tongCong);
    }

    /** "Clicker Vietnam - Đỏ" (sản phẩm không phân loại thì chỉ tên sản phẩm). */
    private static String tenDayDu(MonHang sp) {
        return sp.tenBienThe() == null || sp.tenBienThe().isBlank()
                ? sp.ten() : sp.ten() + " - " + sp.tenBienThe();
    }

    /* ============================================================
       TẠO ĐƠN
       ============================================================ */

    /**
     * Kết quả tạo đơn.
     * @param doiKho có trừ kho biến thể nào không — nơi gọi phải xoá thêm bộ nhớ đệm sản phẩm
     */
    public record KetQuaTaoDon(DonHang don, boolean doiKho) {}

    /**
     * Khách đặt hàng từ website.
     *
     * Lượt đi-về database: [đếm đơn cũ nếu mã chỉ cho khách mới] + INSERT đơn / từng
     * món / thanh toán + [tăng lượt dùng mã] + trừ kho 2 câu + COMMIT. Sản phẩm và
     * khuyến mãi lấy từ BỘ NHỚ ĐỆM (đọc TRƯỚC khi mở transaction) nên không tốn lượt nào.
     *
     * @param nguoiDungId tài khoản đang đăng nhập (null nếu khách đặt không đăng nhập)
     */
    public KetQuaTaoDon datHang(DatHangRequest yeuCau, Long nguoiDungId) {
        DonHang don = new DonHang();
        don.setNguoiDungId(nguoiDungId);
        don.setTenKhach(yeuCau.tenKhach().trim());
        don.setSoDienThoai(yeuCau.soDienThoai().trim());
        don.setDiaChi(yeuCau.diaChi().trim());
        don.setGhiChu(yeuCau.ghiChu());
        don.setKenh("website");

        // Giá lấy từ DATABASE (qua bộ nhớ đệm) chứ không lấy giá trình duyệt gửi lên:
        // giỏ hàng nằm trong localStorage, sửa một dòng là đặt được máy in 3D giá 1.000đ.
        // Món không có trong bảng sản phẩm (in theo yêu cầu, thiết kế file...) thì
        // đành theo giá gửi lên vì không có gì để đối chiếu.
        BangGia bangGia = tinhBangGia(yeuCau.matHang(), traTuBoNhoDem(boNho.sanPham()),
                boNho.khuyenMai().danhSach(), yeuCau.maKhuyenMai(),
                // Kèm theo người đặt để kiểm tra "chỉ khách mới" và "chỉ giao khu vực này"
                new KhuyenMaiService.NguoiDat(nguoiDungId, don.getSoDienThoai(), don.getDiaChi()),
                Kieu.DAT_HANG);
        return luuDon(don, bangGia);
    }

    /**
     * Chủ shop gõ đơn tay ở trang quản trị (khách mua qua Facebook / Zalo / tại shop).
     * Cùng một đường tính giá với đơn khách tự đặt, chỉ khác: mỗi dòng được ghi đè
     * đơn giá, chọn được kênh bán / trạng thái / cách thanh toán, và trừ kho y như đơn web.
     */
    public KetQuaTaoDon datHangTay(DonTayRequest yeuCau) {
        DonHang don = new DonHang();
        don.setNguoiDungId(kiemTraKhach(yeuCau.nguoiDungId()));
        don.setTenKhach(yeuCau.tenKhach().trim());
        don.setSoDienThoai(yeuCau.soDienThoai().trim());
        don.setDiaChi(yeuCau.diaChi() == null ? "" : yeuCau.diaChi().trim());
        don.setGhiChu(yeuCau.ghiChu());
        don.setKenh(kenhHopLe(yeuCau.kenh()));
        if (yeuCau.trangThai() != null && !yeuCau.trangThai().isBlank()) {
            if (!DonHang.TRANG_THAI_HOP_LE.contains(yeuCau.trangThai())) throw trangThaiSai();
            don.setTrangThai(yeuCau.trangThai());
        }

        BangGia bangGia = tinhBangGia(yeuCau.matHang(), traTuBoNhoDem(boNho.sanPham()),
                boNho.khuyenMai().danhSach(), yeuCau.maKhuyenMai(),
                new KhuyenMaiService.NguoiDat(yeuCau.nguoiDungId(), don.getSoDienThoai(), don.getDiaChi()),
                Kieu.DON_TAY);
        ganThanhToan(don, yeuCau.thanhToan(), bangGia.tongCong());
        return luuDon(don, bangGia);
    }

    /** Thanh toán của đơn gõ tay: chủ shop chọn cách trả và đã thu tiền hay chưa. */
    private void ganThanhToan(DonHang don, DonTayRequest.ThanhToanTay tt, long soTien) {
        ThanhToan bg = new ThanhToan();
        bg.setPhuongThuc(phuongThucHopLe(tt == null ? null : tt.phuongThuc()));
        bg.setSoTien(soTien);
        if (tt != null && Boolean.TRUE.equals(tt.daThanhToan())) {
            bg.setTrangThai("da_thanh_toan");
            bg.setThanhToanLuc(OffsetDateTime.now());
        }
        don.themThanhToan(bg);
    }

    /**
     * Ghi đơn xuống database: đơn + từng món + một bản ghi thanh toán, rồi trừ kho.
     * Cách thanh toán mặc định là COD chưa trả (đơn web); đơn gõ tay tự đặt lại sau.
     *
     * Đơn tạo thẳng ở trạng thái đã huỷ thì KHÔNG trừ kho — huỷ rồi thì hàng vẫn còn đó.
     */
    private KetQuaTaoDon luuDon(DonHang don, BangGia bangGia) {
        don.setMaDon(sinhMaDon());
        Map<Long, Integer> kho = new LinkedHashMap<>();
        for (DongGia d : bangGia.dong()) {
            DonHangChiTiet ct = new DonHangChiTiet();
            ct.setSanPhamId(d.sanPhamId());
            ct.setBienTheId(d.bienTheId());
            ct.setTenBienThe(d.tenBienThe());
            ct.setTenSanPham(d.ten());
            ct.setDonGiaGoc(d.giaGoc());
            ct.setDonGia(d.donGia());
            ct.setSoLuong(d.soLuong());
            don.themChiTiet(ct);
            // Dòng không gắn biến thể (món shop tự gõ) thì không đụng tới kho
            if (d.bienTheId() != null) kho.merge(d.bienTheId(), -d.soLuong(), Integer::sum);
        }
        don.setTienGiamSanPham(bangGia.tienGiamSanPham());

        Long idKhuyenMai = null;
        if (bangGia.khuyenMai() != null) {
            don.setMaKhuyenMai(bangGia.khuyenMai().khuyenMai().getMa());
            don.setTienGiam(bangGia.khuyenMai().tienGiam());
            idKhuyenMai = bangGia.khuyenMai().khuyenMai().getId();
        }
        don.setTongTien(bangGia.tongCong());
        if (don.getThanhToan().isEmpty()) {
            ThanhToan tt = new ThanhToan();
            tt.setPhuongThuc("cod");
            tt.setSoTien(bangGia.tongCong());
            don.themThanhToan(tt);
        }

        boolean truKho = !DonHang.DA_HUY.equals(don.getTrangThai()) && !kho.isEmpty();
        Long maKm = idKhuyenMai;
        DonHang daLuu = giaoDich.execute(gd -> {
            DonHang luu = donHangRepo.save(don);
            // Chỉ trừ lượt khi đơn đã lưu xong; cùng transaction nên mã vừa hết lượt (ném 400)
            // thì đơn cũng rollback, không mất gì
            if (maKm != null) khuyenMaiService.ghiNhanDaDung(maKm);
            if (truKho) doiKho(kho);
            return luu;
        });
        return new KetQuaTaoDon(daLuu, truKho);
    }

    /*
     * Danh sách đơn cho trang quản trị: lấy từ bộ nhớ đệm (BoNhoDem.dsDonHang),
     * nạp bằng NapDuLieu.napDonHang.
     */

    /**
     * Đổi trạng thái đơn. Không có đơn -> 404.
     *
     * Vào / ra khỏi trạng thái ĐÃ HUỶ là phải trả lại hoặc trừ lại kho, nên phải đọc
     * trạng thái CŨ trước — đọc kèm khoá dòng (for update) trong cùng transaction với
     * lệnh ghi, hai người bấm cùng lúc mới không trả kho hai lần.
     *
     * @return true nếu kho có thay đổi (nơi gọi phải xoá thêm bộ nhớ đệm sản phẩm)
     */
    public boolean doiTrangThai(Long id, String trangThaiMoi) {
        if (!DonHang.TRANG_THAI_HOP_LE.contains(trangThaiMoi)) throw trangThaiSai();
        return Boolean.TRUE.equals(giaoDich.execute(gd -> {
            TinhTrangDon cu = khoaDon(id);
            int huong = huongKho(cu.giuKho(), !cu.daXoa() && !DonHang.DA_HUY.equals(trangThaiMoi));
            jdbc.update("update don_hang set trang_thai = ?, updated_at = now() where id = ?", trangThaiMoi, id);
            return huong != 0 && doiKhoCaDon(id, huong) > 0;
        }));
    }

    /** Mọi bản ghi thanh toán của đơn thành "đã thanh toán". Không có đơn -> 404. */
    public void danhDauDaThanhToan(Long id) {
        Long soDon = jdbc.queryForObject(
                "with d as (select id from don_hang where id = ?), "
                + "t as (update thanh_toan set trang_thai = 'da_thanh_toan', thanh_toan_luc = now(), updated_at = now() "
                + "      where don_hang_id = (select id from d)) "
                + "select count(*) from d", Long.class, id);
        if (soDon == null || soDon == 0) throw khongThayDon(id);
    }

    /**
     * XOÁ MỀM: đơn hàng là chứng từ bán hàng, không xoá hẳn khỏi database.
     * Đơn đang giữ hàng thì trả kho; đơn đã huỷ (đã trả rồi) hoặc đã xoá thì thôi.
     *
     * @return true nếu kho có thay đổi
     */
    public boolean xoaDon(Long id) {
        return Boolean.TRUE.equals(giaoDich.execute(gd -> {
            TinhTrangDon cu = khoaDon(id);
            jdbc.update("update don_hang set is_deleted = true, updated_at = now() where id = ?", id);
            return cu.giuKho() && doiKhoCaDon(id, 1) > 0;
        }));
    }

    /* ============================================================
       KHO — trừ và trả theo biến thể
       ============================================================ */

    /**
     * Tình trạng đơn ngay trước lệnh ghi.
     * @param giuKho đơn đang GIỮ hàng: chưa xoá và chưa huỷ, nên kho đang bị trừ phần của nó
     */
    private record TinhTrangDon(boolean daXoa, boolean giuKho) {}

    /** Đọc và KHOÁ dòng đơn tới hết transaction. Không có đơn -> 404. */
    private TinhTrangDon khoaDon(Long id) {
        List<Map<String, Object>> dong = jdbc.queryForList(
                "select trang_thai, is_deleted from don_hang where id = ? for update", id);
        if (dong.isEmpty()) throw khongThayDon(id);
        String tt = (String) dong.get(0).get("trang_thai");
        boolean daXoa = Boolean.TRUE.equals(dong.get(0).get("is_deleted"));
        return new TinhTrangDon(daXoa, !daXoa && !DonHang.DA_HUY.equals(tt));
    }

    /** +1 = trả hàng về kho, -1 = trừ kho, 0 = không đổi gì. */
    private static int huongKho(boolean giuKhoCu, boolean giuKhoMoi) {
        if (giuKhoCu == giuKhoMoi) return 0;
        return giuKhoCu ? 1 : -1;
    }

    /**
     * Cộng dồn tồn kho của một loạt biến thể — MỘT câu UPDATE, không đọc lên Java.
     * @param delta id biến thể -> số phải cộng vào tồn kho (âm = trừ). Tồn kho âm được.
     */
    private void doiKho(Map<Long, Integer> delta) {
        if (delta.isEmpty()) return;
        StringBuilder cau = new StringBuilder(
                "update bien_the b set ton_kho = b.ton_kho + v.sl, updated_at = now() from (values ");
        List<Object> thamSo = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : delta.entrySet()) {
            // Dòng đầu ghi rõ kiểu để Postgres khỏi đoán kiểu của tham số trong VALUES
            cau.append(thamSo.isEmpty() ? "(?::bigint, ?::int)" : ", (?, ?)");
            thamSo.add(e.getKey());
            thamSo.add(e.getValue());
        }
        cau.append(") as v(id, sl) where b.id = v.id");
        jdbc.update(cau.toString(), thamSo.toArray());
        dongBoTonKhoSanPham(delta.keySet());
    }

    /**
     * Trả lại (huong = 1) hoặc trừ lại (huong = -1) kho cho MỌI món của một đơn.
     * Số lượng đọc thẳng từ don_hang_chi_tiet trong chính câu UPDATE nên không tốn
     * thêm lượt đi-về nào, và luôn khớp với lúc tạo đơn dù đơn cũ tới đâu.
     *
     * @return số dòng biến thể bị đổi (0 = đơn không có món nào gắn biến thể)
     */
    private int doiKhoCaDon(Long donHangId, int huong) {
        int soDong = jdbc.update(
                "update bien_the b set ton_kho = b.ton_kho + ?::int * d.sl, updated_at = now() "
                + "from (select ct.bien_the_id as id, sum(ct.so_luong)::int as sl from don_hang_chi_tiet ct "
                + "      where ct.don_hang_id = ? and ct.bien_the_id is not null group by ct.bien_the_id) d "
                + "where b.id = d.id", huong, donHangId);
        if (soDong == 0) return 0;
        jdbc.update("update san_pham s set ton_kho = (select coalesce(sum(b.ton_kho), 0) from bien_the b "
                + "where b.san_pham_id = s.id and b.is_deleted = false), updated_at = now() "
                + "where s.id in (select b2.san_pham_id from bien_the b2 where b2.id in "
                + "               (select ct.bien_the_id from don_hang_chi_tiet ct "
                + "                where ct.don_hang_id = ? and ct.bien_the_id is not null))", donHangId);
        return soDong;
    }

    /**
     * san_pham.ton_kho = tổng tồn kho các biến thể chưa xoá của nó — MỘT câu, không
     * đọc gì lên Java. Cột này chỉ là bản sao cho mấy chỗ đọc cũ; số thật nằm ở bien_the.
     */
    private void dongBoTonKhoSanPham(Collection<Long> idBienThe) {
        if (idBienThe.isEmpty()) return;
        String cho = String.join(", ", Collections.nCopies(idBienThe.size(), "?"));
        jdbc.update("update san_pham s set ton_kho = (select coalesce(sum(b.ton_kho), 0) from bien_the b "
                + "where b.san_pham_id = s.id and b.is_deleted = false), updated_at = now() "
                + "where s.id in (select b2.san_pham_id from bien_the b2 where b2.id in (" + cho + "))",
                idBienThe.toArray());
    }

    /* ============================================================
       Tiện ích
       ============================================================ */

    /**
     * Khách được nối vào đơn phải có thật trong danh bạ — không thì lệnh INSERT vướng
     * khoá ngoại và trả về lỗi 500 khó hiểu. Tra ở bộ nhớ đệm nên không tốn lượt đi-về;
     * đọc bộ nhớ đệm hỏng thì bỏ qua, để khoá ngoại của database lo.
     */
    private Long kiemTraKhach(Long nguoiDungId) {
        if (nguoiDungId == null) return null;
        try {
            for (var nd : boNho.dsNguoiDung()) {
                if (nguoiDungId.equals(nd.getId())) return nguoiDungId;
            }
        } catch (RuntimeException boQua) {
            return nguoiDungId;
        }
        throw loi400("Không tìm thấy khách hàng này trong danh bạ.");
    }

    /** Kênh bán lạ hoặc để trống thì tính là website (giống mặc định của database). */
    private static String kenhHopLe(String kenh) {
        String k = kenh == null ? "" : kenh.trim();
        if (k.isEmpty()) return "website";
        if (!DonHang.KENH_HOP_LE.containsKey(k)) {
            throw loi400("Kênh bán không hợp lệ. Hợp lệ: " + String.join(", ", DonHang.KENH_HOP_LE.keySet()));
        }
        return k;
    }

    /** Cách thanh toán lạ hoặc để trống thì là COD (database cũng chỉ nhận 4 giá trị này). */
    private static String phuongThucHopLe(String phuongThuc) {
        String p = phuongThuc == null ? "" : phuongThuc.trim();
        if (p.isEmpty()) return "cod";
        if (!PHUONG_THUC_HOP_LE.contains(p)) {
            throw loi400("Cách thanh toán không hợp lệ. Hợp lệ: cod, chuyen_khoan, vi_dien_tu, the");
        }
        return p;
    }

    private ResponseStatusException khongThayDon(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng id=" + id);
    }

    private static ResponseStatusException trangThaiSai() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Trạng thái không hợp lệ. Hợp lệ: " + DonHang.TRANG_THAI_HOP_LE);
    }

    private static ResponseStatusException loi400(String thongBao) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, thongBao);
    }

    private static long so(Object v) { return v instanceof Number n ? n.longValue() : 0L; }

    /** Mã đơn dạng IN3D-XXXXXXXX, trùng phong cách mã bên frontend. */
    private String sinhMaDon() {
        String phan1 = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
        String phan2 = Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36), 36).toUpperCase(Locale.ROOT);
        return "IN3D-" + phan1 + phan2;
    }
}
