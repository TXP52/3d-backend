package vn.in3d.backend.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import vn.in3d.backend.dto.BoSuuTapDto;
import vn.in3d.backend.dto.DanhMucDto;
import vn.in3d.backend.entity.BaiViet;
import vn.in3d.backend.entity.BoSuuTap;
import vn.in3d.backend.entity.DanhMuc;
import vn.in3d.backend.entity.DonHang;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.entity.MauSac;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.entity.NhaCungCap;
import vn.in3d.backend.entity.SanPham;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * BỘ NHỚ ĐỆM dữ liệu đọc — mọi GET của trang quản trị và web khách lấy từ đây.
 *
 * Vì sao: database Supabase ở Sydney, mỗi lượt đi-về ~250 ms. Dữ liệu thì bé
 * (vài chục dòng mỗi bảng) và gần như chỉ chủ shop sửa qua chính backend này,
 * nên giữ sẵn ảnh chụp từng bảng trong RAM là trả trong vài mili-giây.
 *
 * Cách chạy:
 *   - 10 bộ dữ liệu (khoá SP, VT, MS, NCC, DM, KM, BV, DH, ND, BST), mỗi bộ nạp bằng
 *     MỘT truy vấn (xem NapDuLieu). Khởi động xong là nạp sẵn cả 10 song song.
 *   - Hết hạn (đơn hàng 60 giây, còn lại 10 phút — phòng khi sửa tay trên
 *     Supabase Dashboard): vẫn trả bản cũ ngay, nạp lại ở nền.
 *   - Sau mỗi lệnh ghi ĐÃ COMMIT, nơi ghi gọi xoaVaNapLai(khoá...): bỏ bản cũ
 *     và nạp lại NGAY (song song), chờ nạp xong mới trả response — trang quản
 *     trị hỏi lại liền sau đó là có dữ liệu mới, không phải chờ database.
 *   - Mỗi khoá có số THẾ HỆ tăng sau mỗi lần xoá: lượt nạp bắt đầu trước khi
 *     xoá (đọc dữ liệu cũ) xong sau thì KHÔNG được ghi đè vào bộ nhớ đệm.
 *   - Cùng một thế hệ chỉ nạp MỘT lượt; request đến trong lúc đang nạp thì chờ chung.
 *
 * Object trong bộ nhớ đệm DÙNG CHUNG cho mọi request: KHÔNG được sửa.
 * Danh sách và map DTO đều bọc không-sửa-được; entity (MauSac, KhuyenMai...)
 * thì không bọc được nên nơi dùng tự giữ ý — muốn lọc/thêm thì tạo list/map mới.
 */
@Component
public class BoNhoDem implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(BoNhoDem.class);

    /* ---------------- Khoá các bộ dữ liệu ---------------- */

    /** Sản phẩm (DTO danh sách đầy đủ của trang quản trị). */
    public static final String SP = "SP";
    /** Vật tư / kho (DTO danh sách). */
    public static final String VT = "VT";
    /** Màu sắc. */
    public static final String MS = "MS";
    /** Nhà cung cấp. */
    public static final String NCC = "NCC";
    /** Danh mục (cả hai nhóm). */
    public static final String DM = "DM";
    /** Khuyến mãi. */
    public static final String KM = "KM";
    /** Bài viết. */
    public static final String BV = "BV";
    /** Đơn hàng. */
    public static final String DH = "DH";
    /** Người dùng. */
    public static final String ND = "ND";
    /** Bộ sưu tập (kèm sản phẩm của từng bộ). */
    public static final String BST = "BST";

    public static final List<String> TAT_CA = List.of(SP, VT, MS, NCC, DM, KM, BV, DH, ND, BST);

    private static final long TTL_MAC_DINH_MS = 10 * 60_000L;
    /** Đơn hàng sống ngắn: web khách còn đường ghi thẳng vào Supabase khi Java lỗi. */
    private static final long TTL_DON_HANG_MS = 60_000L;
    /** Nạp lỗi rồi thì CHỜ NGUỘI bấy nhiêu trước khi thử nạp lại ở nền (xem Muc.lay). */
    private static final long CHO_NGUOI_MS = 5_000L;
    /** Khoảng nghỉ tối thiểu giữa hai lượt làm mới TOÀN BỘ; gọi dày hơn thì gộp lại. */
    private static final long CACH_LAM_MOI_MS = 5_000L;

    /* ---------------- Ảnh chụp từng bộ dữ liệu ---------------- */

    /**
     * @param danhSach          DTO sản phẩm CHƯA xoá, id tăng dần = GET /api/san-pham?tatCa=true
     * @param theoId            DTO tra theo id, kể cả sản phẩm đã xoá mềm
     * @param thungRac          entity sản phẩm đã xoá mềm, id tăng dần = GET /api/san-pham/thung-rac
     * @param bienTheTheoId     DTO biến thể CHƯA xoá tra theo id biến thể — chính object nằm
     *                          trong bienThe[] của sản phẩm (kể cả biến thể của sản phẩm đã xoá mềm);
     *                          không có khoá = biến thể không còn, chỗ đặt hàng báo lỗi dòng đó
     * @param sanPhamCuaBienThe id sản phẩm của từng biến thể, để từ dòng đơn tra ngược ra sản phẩm
     */
    public record DuLieuSanPham(List<Map<String, Object>> danhSach,
                                Map<Long, Map<String, Object>> theoId,
                                List<SanPham> thungRac,
                                Map<Long, Map<String, Object>> bienTheTheoId,
                                Map<Long, Long> sanPhamCuaBienThe) {}

    /**
     * @param danhSach  DTO vật tư CHƯA xoá, theo loai rồi id = GET /api/vat-tu
     * @param theoId    DTO tra theo id, kể cả vật tư đã xoá mềm
     */
    public record DuLieuVatTu(List<Map<String, Object>> danhSach,
                              Map<Long, Map<String, Object>> theoId) {}

    /**
     * Bảng đơn giản trả nguyên entity (màu, nhà cung cấp, danh mục).
     * @param danhSach  dòng CHƯA xoá, đúng thứ tự của GET tương ứng
     * @param theoId    tra theo id, kể cả dòng đã xoá mềm
     */
    public record DuLieuBang<T>(List<T> danhSach, Map<Long, T> theoId) {}

    /**
     * @param danhSach  khuyến mãi CHƯA xoá, mới trước = GET /api/khuyen-mai?tatCa=true
     * @param thungRac  đã xoá mềm, mới trước = GET /api/khuyen-mai/thung-rac
     * @param theoId    tra theo id, kể cả đã xoá (GET /api/khuyen-mai/{id})
     */
    public record DuLieuKhuyenMai(List<KhuyenMai> danhSach, List<KhuyenMai> thungRac,
                                  Map<Long, KhuyenMai> theoId) {}

    /**
     * @param danhSach     bài CHƯA xoá, thu_tu tăng rồi id giảm = GET /api/bai-viet?tatCa=true
     * @param thungRac     bài đã xoá mềm, id giảm = GET /api/bai-viet/thung-rac
     * @param theoId       tra theo id, kể cả đã xoá (GET /api/bai-viet/{id})
     * @param theoDuongDan bài CHƯA xoá tra theo đường dẫn (GET /api/bai-viet/duong-dan/{..})
     */
    public record DuLieuBaiViet(List<BaiViet> danhSach, List<BaiViet> thungRac,
                                Map<Long, BaiViet> theoId, Map<String, BaiViet> theoDuongDan) {}

    /**
     * @param danhSach  đơn CHƯA xoá, mới nhất trước, đã nạp sẵn chiTiet + thanhToan = GET /api/don-hang
     * @param theoId    tra theo id (chỉ đơn chưa xoá)
     */
    public record DuLieuDonHang(List<DonHang> danhSach, Map<Long, DonHang> theoId) {}

    /**
     * @param danhSach    tài khoản CHƯA xoá, id tăng dần = GET /api/nguoi-dung
     * @param theoEmail   MỌI tài khoản (kể cả đã xoá) tra theo email viết thường — để đọc token
     */
    public record DuLieuNguoiDung(List<NguoiDung> danhSach, Map<String, NguoiDung> theoEmail) {}

    /**
     * @param danhSach     bộ CHƯA xoá, thu_tu tăng rồi id = GET /api/bo-suu-tap?tatCa=true
     * @param theoId       tra theo id, kể cả bộ đã xoá mềm
     * @param theoDuongDan bộ CHƯA xoá tra theo đường dẫn (trang bộ sưu tập bên web khách)
     */
    public record DuLieuBoSuuTap(List<BoSuuTap> danhSach, Map<Long, BoSuuTap> theoId,
                                 Map<String, BoSuuTap> theoDuongDan) {}

    /* ---------------- Máy chạy ---------------- */

    private final Map<String, Muc<?>> cacMuc = new LinkedHashMap<>();
    private final Muc<DuLieuSanPham> sanPham;
    private final Muc<DuLieuVatTu> vatTu;
    private final Muc<DuLieuBang<MauSac>> mauSac;
    private final Muc<DuLieuBang<NhaCungCap>> nhaCungCap;
    private final Muc<DuLieuBang<DanhMuc>> danhMuc;
    private final Muc<DuLieuKhuyenMai> khuyenMai;
    private final Muc<DuLieuBaiViet> baiViet;
    private final Muc<DuLieuDonHang> donHang;
    private final Muc<DuLieuNguoiDung> nguoiDung;
    private final Muc<DuLieuBoSuuTap> boSuuTap;

    /**
     * Luồng chạy các lượt nạp (và truy vấn phụ chạy song song của bộ SP); mỗi khoá tối đa
     * vài lượt cùng lúc nên 13 là dư. Hết luồng thì truy vấn phụ tự chạy trên luồng của
     * lượt nạp SP, không kẹt.
     */
    private final ExecutorService luongNap;

    /** Lúc lượt làm mới toàn bộ gần nhất NẠP XONG (0 = chưa lần nào) và nó tốn bao lâu. */
    private long lanLamMoiGanNhat;
    private long msLamMoiGanNhat;

    public BoNhoDem(NapDuLieu nap) {
        AtomicInteger dem = new AtomicInteger();
        this.luongNap = Executors.newFixedThreadPool(TAT_CA.size() + 3, r -> {
            Thread t = new Thread(r, "bo-nho-dem-" + dem.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        // Bộ SP chạy thêm một truy vấn song song trên chính hồ luồng này (xem NapDuLieu.napSanPham)
        sanPham    = dangKy(SP,  () -> nap.napSanPham(luongNap), TTL_MAC_DINH_MS);
        vatTu      = dangKy(VT,  nap::napVatTu,      TTL_MAC_DINH_MS);
        mauSac     = dangKy(MS,  nap::napMauSac,     TTL_MAC_DINH_MS);
        nhaCungCap = dangKy(NCC, nap::napNhaCungCap, TTL_MAC_DINH_MS);
        danhMuc    = dangKy(DM,  nap::napDanhMuc,    TTL_MAC_DINH_MS);
        khuyenMai  = dangKy(KM,  nap::napKhuyenMai,  TTL_MAC_DINH_MS);
        baiViet    = dangKy(BV,  nap::napBaiViet,    TTL_MAC_DINH_MS);
        donHang    = dangKy(DH,  nap::napDonHang,    TTL_DON_HANG_MS);
        nguoiDung  = dangKy(ND,  nap::napNguoiDung,  TTL_MAC_DINH_MS);
        boSuuTap   = dangKy(BST, nap::napBoSuuTap,   TTL_MAC_DINH_MS);
    }

    private <T> Muc<T> dangKy(String khoa, Supplier<T> hamNap, long ttlMs) {
        Muc<T> m = new Muc<>(khoa, hamNap, ttlMs);
        cacMuc.put(khoa, m);
        return m;
    }

    /* ---------------- Lấy từng bộ dữ liệu ---------------- */

    public DuLieuSanPham sanPham() { return sanPham.lay(); }
    public DuLieuVatTu vatTu() { return vatTu.lay(); }
    public DuLieuBang<MauSac> mauSac() { return mauSac.lay(); }
    public DuLieuBang<NhaCungCap> nhaCungCap() { return nhaCungCap.lay(); }
    public DuLieuBang<DanhMuc> danhMuc() { return danhMuc.lay(); }
    public DuLieuKhuyenMai khuyenMai() { return khuyenMai.lay(); }
    public DuLieuBaiViet baiViet() { return baiViet.lay(); }
    public DuLieuDonHang donHang() { return donHang.lay(); }
    public DuLieuNguoiDung nguoiDung() { return nguoiDung.lay(); }
    public DuLieuBoSuuTap boSuuTap() { return boSuuTap.lay(); }

    /* ---------------- Đúng giá trị của các GET hiện có ---------------- */

    /** = GET /api/san-pham[?tatCa=true]. Mặc định chỉ hàng đang bán. */
    public List<Map<String, Object>> dsSanPham(boolean tatCa) {
        return dsSanPham(sanPham(), tatCa);
    }

    /**
     * Như trên nhưng tính trên MỘT bản chụp nơi gọi đã lấy sẵn — dùng khi một response
     * cần cả bản chụp (tra theo id, danh sách liên quan...) để khỏi lẫn hai thế hệ.
     */
    public static List<Map<String, Object>> dsSanPham(DuLieuSanPham ban, boolean tatCa) {
        List<Map<String, Object>> ds = ban.danhSach();
        if (tatCa) return ds;
        return ds.stream().filter(m -> Boolean.TRUE.equals(m.get("dangBan"))).toList();
    }

    /** = GET /api/vat-tu */
    public List<Map<String, Object>> dsVatTu() { return vatTu().danhSach(); }

    /** = GET /api/nha-cung-cap */
    public List<NhaCungCap> dsNhaCungCap() { return nhaCungCap().danhSach(); }

    /** = GET /api/mau-sac */
    public List<MauSac> dsMauSac() { return mauSac().danhSach(); }

    /**
     * = GET /api/danh-muc?tatCa=&nhom=&kemSoLuong= (mặc định tatCa=true, không lọc nhóm, không đếm).
     * Số đếm lấy từ bộ nhớ đệm sản phẩm / vật tư, không hỏi database.
     */
    public List<Map<String, Object>> dsDanhMuc(boolean tatCa, String nhom, boolean kemSoLuong) {
        // Lấy mỗi bản chụp ĐÚNG MỘT lần, ngay từ đầu: đọc rời rạc thì soSanPham / soVatTu
        // có thể thuộc thế hệ khác danh sách danh mục và không khớp với các dòng bên cạnh
        DuLieuBang<DanhMuc> banDm = danhMuc();
        Map<Long, Long> soSanPham = new HashMap<>();
        Map<Long, Long> soVatTu = new HashMap<>();
        if (kemSoLuong) {
            for (var sp : sanPham().danhSach()) {
                Object dm = sp.get("danhMucId");
                if (dm != null) soSanPham.merge((Long) dm, 1L, Long::sum);
            }
            for (var vt : vatTu().danhSach()) {
                Object dm = vt.get("danhMucId");
                if (dm != null) soVatTu.merge((Long) dm, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> ra = new ArrayList<>();
        for (DanhMuc d : banDm.danhSach()) {
            if (!tatCa && !d.getDangHien()) continue;
            if (nhom != null && !nhom.isBlank() && !nhom.equals(d.getNhom())) continue;
            ra.add(kemSoLuong
                    ? DanhMucDto.tao(d, soSanPham.getOrDefault(d.getId(), 0L), soVatTu.getOrDefault(d.getId(), 0L))
                    : DanhMucDto.tao(d));
        }
        return ra;
    }

    /** = GET /api/bai-viet[?tatCa=true]. Mặc định chỉ bài đang bật hiển thị. */
    public List<BaiViet> dsBaiViet(boolean tatCa) {
        List<BaiViet> ds = baiViet().danhSach();
        if (tatCa) return ds;
        return ds.stream().filter(b -> Boolean.TRUE.equals(b.getHienThi())).toList();
    }

    /**
     * = GET /api/khuyen-mai[?tatCa=true]. Mặc định chỉ mã đang chạy và đang bật khoe.
     * "Đang chạy" tính theo ngày HÔM NAY lúc gọi, nên bộ nhớ đệm không cần xoá lúc nửa đêm.
     */
    public List<KhuyenMai> dsKhuyenMai(boolean tatCa) {
        List<KhuyenMai> ds = khuyenMai().danhSach();
        if (tatCa) return ds;
        return ds.stream()
                .filter(km -> km.dangChay() && Boolean.TRUE.equals(km.getHienThi()))
                .toList();
    }

    /**
     * = GET /api/bo-suu-tap[?tatCa=true]. Mặc định chỉ bộ đang bật hiển thị.
     * Tên và ảnh sản phẩm tra sang bản chụp sản phẩm ngay lúc này (một bản chụp duy
     * nhất cho cả danh sách) nên đổi tên sản phẩm là thấy đổi luôn, khỏi xoá khoá BST.
     */
    public List<Map<String, Object>> dsBoSuuTap(boolean tatCa) {
        Map<Long, Map<String, Object>> spTheoId = sanPham().theoId();
        List<Map<String, Object>> ra = new ArrayList<>();
        for (BoSuuTap b : boSuuTap().danhSach()) {
            if (!tatCa && !b.getHienThi()) continue;
            ra.add(BoSuuTapDto.tao(b, b.getSanPham(), spTheoId));
        }
        return ra;
    }

    /** = GET /api/don-hang */
    public List<DonHang> dsDonHang() { return donHang().danhSach(); }

    /** = GET /api/nguoi-dung (nơi gọi tự kiểm tra token admin trước). */
    public List<NguoiDung> dsNguoiDung() { return nguoiDung().danhSach(); }

    /** Tài khoản theo email, không phân biệt hoa thường, kể cả đã xoá; không có thì null. */
    public NguoiDung nguoiDungTheoEmail(String email) {
        if (email == null) return null;
        return nguoiDung().theoEmail().get(email.toLowerCase(Locale.ROOT));
    }

    /* ---------------- Xoá và nạp lại ---------------- */

    /**
     * Gọi SAU KHI lệnh ghi đã commit: bỏ bản cũ của các khoá rồi nạp lại song song,
     * chờ xong mới trả về. Lượt nạp nào lỗi thì chỉ ghi log (task nạp tự ghi) — khoá
     * đó vẫn ở trạng thái "đã xoá" nên lần đọc sau tự nạp lại.
     *
     * @return true nếu MỌI khoá đã nạp lại xong. false = có khoá nạp lỗi, nên nơi ghi
     *         ĐỪNG đọc lại khoá đó từ bộ nhớ đệm (đọc là mở thêm một lượt nạp nữa, lỗi
     *         lần hai thì ném ra ngoài và biến lệnh ghi ĐÃ COMMIT thành lỗi 500) —
     *         hãy trả lời bằng dữ liệu đang có trong tay.
     */
    /**
     * Có bộ dữ liệu mang khoá này không. Nơi ghi dùng để xoá thêm khoá của phần khác
     * mới thêm sau (xoá khoá chưa đăng ký là ném lỗi, mà lệnh ghi thì ĐÃ commit rồi).
     */
    public boolean coBoDuLieu(String khoa) {
        return cacMuc.containsKey(khoa);
    }

    public boolean xoaVaNapLai(String... khoa) {
        List<CompletableFuture<?>> dangCho = new ArrayList<>();
        for (String k : khoa) dangCho.add(muc(k).xoaVaTai());
        boolean duNap = true;
        for (CompletableFuture<?> f : dangCho) {
            try {
                f.join();
            } catch (Exception boQua) {
                // đã ghi log trong task nạp
                duNap = false;
            }
        }
        return duNap;
    }

    /**
     * Xoá và nạp lại MỌI bộ dữ liệu — cho nút "Làm mới" sau khi sửa tay trên Supabase Dashboard.
     * Hai lượt gọi cách nhau dưới CACH_LAM_MOI_MS thì GỘP thành một: mỗi lượt là 10 truy vấn
     * toàn bảng, bấm liên tục vừa làm nguội bộ nhớ đệm vừa hút hết kết nối của pooler.
     *
     * @return số mili-giây của lượt nạp; lượt bị gộp trả lại thời gian của lượt vừa xong
     */
    public synchronized long lamMoiTatCa() {
        long batDau = System.currentTimeMillis();
        if (lanLamMoiGanNhat != 0 && batDau - lanLamMoiGanNhat < CACH_LAM_MOI_MS) return msLamMoiGanNhat;
        xoaVaNapLai(TAT_CA.toArray(new String[0]));
        msLamMoiGanNhat = System.currentTimeMillis() - batDau;
        lanLamMoiGanNhat = System.currentTimeMillis();   // nghỉ tính từ lúc NẠP XONG
        return msLamMoiGanNhat;
    }

    /**
     * Nạp sẵn cả 10 bộ NGAY LÚC DỰNG BEAN (afterSingletonsInstantiated chạy trong refresh,
     * trước khi Tomcat mở cổng) — trước đây nghe ApplicationReadyEvent nên cổng mở sớm
     * hơn bộ nhớ đệm ~2,8 giây và trang quản trị đầu tiên sau khi khởi động lại phải chờ
     * hết phần khởi động đó. Seeder chạy sau (trong callRunners) và tự nạp lại khoá nào nó ghi.
     */
    @Override
    public void afterSingletonsInstantiated() {
        long batDau = System.currentTimeMillis();
        lamMoiTatCa();
        StringBuilder tungBo = new StringBuilder();
        cacMuc.forEach((k, m) -> tungBo.append(tungBo.length() == 0 ? "" : ", ").append(k).append(' ')
                .append(m.msNapGanNhat).append(" ms"));
        System.out.println("[IN3D] ✔ Bộ nhớ đệm đã nạp sẵn " + TAT_CA.size() + " bộ dữ liệu trong "
                + (System.currentTimeMillis() - batDau) + " ms (" + tungBo + ")");
    }

    @PreDestroy
    void dung() {
        luongNap.shutdownNow();
    }

    private Muc<?> muc(String khoa) {
        Muc<?> m = cacMuc.get(khoa);
        if (m == null) throw new IllegalArgumentException("Không có bộ dữ liệu " + khoa);
        return m;
    }

    private static Throwable goc(Throwable e) {
        return (e instanceof CompletionException && e.getCause() != null) ? e.getCause() : e;
    }

    /** Một bộ dữ liệu: bản chụp hiện tại + thế hệ + lượt nạp đang chạy. */
    private final class Muc<T> {
        private final String khoa;
        private final Supplier<T> hamNap;
        private final long ttlMs;

        /** Tăng mỗi lần xoá; bản chụp khác thế hệ hiện tại coi như không có. */
        private final AtomicLong theHe = new AtomicLong();
        private volatile BanChup<T> ban;
        /** Thời gian lượt nạp gần nhất (kể cả lúc chờ kết nối), để ghi log lúc khởi động. */
        private volatile long msNapGanNhat = -1;
        /** Lúc lượt nạp gần nhất BỊ LỖI; 0 = lượt gần nhất thành công. */
        private volatile long loiLuc;

        // Lượt nạp đang chạy và thế hệ của nó — đọc/ghi trong synchronized (this)
        private CompletableFuture<T> dangNap;
        private long theHeDangNap = -1;

        Muc(String khoa, Supplier<T> hamNap, long ttlMs) {
            this.khoa = khoa;
            this.hamNap = hamNap;
            this.ttlMs = ttlMs;
        }

        T lay() {
            BanChup<T> b = ban;
            if (b != null && b.theHe == theHe.get()) {
                long gio = System.currentTimeMillis();
                // Hết hạn: trả bản cũ ngay, nạp lại ở nền (chỉ một lượt; lỗi thì task tự ghi log).
                // Vừa nạp lỗi thì CHỜ NGUỘI: database chết mà mỗi lượt đọc lại mở một lượt nạp
                // nữa là tốn kết nối (mỗi lượt chờ tới connection-timeout) và đầy log WARN,
                // trong khi bản cũ vẫn trả được y như cũ.
                if (gio - b.napLuc >= ttlMs && gio - loiLuc >= CHO_NGUOI_MS) {
                    synchronized (this) { taiTheHeHienTai(); }
                }
                return b.giaTri;
            }
            // Chưa có hoặc vừa bị xoá: chờ lượt nạp của thế hệ hiện tại
            CompletableFuture<T> f;
            synchronized (this) { f = taiTheHeHienTai(); }
            try {
                return f.join();
            } catch (CompletionException e) {
                Throwable g = goc(e);
                if (g instanceof RuntimeException re) throw re;
                throw e;
            }
        }

        synchronized CompletableFuture<T> xoaVaTai() {
            theHe.incrementAndGet();
            return taiTheHeHienTai();
        }

        /** Gọi trong synchronized (this). Cùng thế hệ thì dùng chung lượt đang nạp. */
        private CompletableFuture<T> taiTheHeHienTai() {
            long g = theHe.get();
            if (dangNap != null && theHeDangNap == g) return dangNap;
            CompletableFuture<T> f = new CompletableFuture<>();
            dangNap = f;
            theHeDangNap = g;
            try {
                luongNap.execute(() -> {
                    try {
                        long batDau = System.currentTimeMillis();
                        T v = hamNap.get();
                        msNapGanNhat = System.currentTimeMillis() - batDau;
                        loiLuc = 0;                       // nạp được rồi: hết chờ nguội
                        synchronized (this) {
                            // Bị xoá trong lúc đang nạp thì dữ liệu này có thể đã cũ: không lưu
                            if (theHe.get() == g) ban = new BanChup<>(v, batDau, g);
                            if (dangNap == f) dangNap = null;
                        }
                        f.complete(v);
                    } catch (Throwable e) {
                        loiLuc = System.currentTimeMillis();
                        log.warn("[IN3D] Nạp bộ nhớ đệm {} lỗi: {}", khoa, e.toString());
                        synchronized (this) {
                            if (dangNap == f) dangNap = null;
                        }
                        f.completeExceptionally(e);
                    }
                });
            } catch (RuntimeException tuChoi) {
                // Đang tắt ứng dụng: luồng nạp đã dừng
                dangNap = null;
                f.completeExceptionally(tuChoi);
            }
            return f;
        }

        @Override
        public String toString() { return khoa; }
    }

    /** napLuc = lúc BẮT ĐẦU nạp, để hạn dùng tính thận trọng. */
    private record BanChup<T>(T giaTri, long napLuc, long theHe) {
        BanChup { Objects.requireNonNull(giaTri); }
    }
}
