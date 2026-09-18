package vn.in3d.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.entity.DanhMuc;
import vn.in3d.backend.entity.MauSac;
import vn.in3d.backend.entity.VatTu;
import vn.in3d.backend.repository.NguoiDungRepository;
import vn.in3d.backend.repository.DanhMucRepository;
import vn.in3d.backend.repository.MauSacRepository;
import vn.in3d.backend.repository.VatTuRepository;
import vn.in3d.backend.service.BoNhoDem;

import java.util.ArrayList;
import java.util.List;

/**
 * Nạp dữ liệu ban đầu: CHỈ tài khoản quản trị và bảng màu.
 *
 * Trước đây nạp thêm 1 nhà cung cấp "Shopee", 7 vật tư (1 máy in + 6 cuộn nhựa)
 * và 5 sản phẩm mẫu. Đó là hàng bịa để trang không trống lúc mới dựng, nhưng
 * chủ shop nhìn vào tưởng kho có thật, lại còn tính vào tiền vốn.
 * Giờ chỉ giữ hai thứ thật sự cần để đăng nhập và dùng được ngay:
 *   - tài khoản quản trị
 *   - bảng màu (12 màu nhựa phổ thông, chỉ là danh mục để chọn, không phải hàng)
 *   - 4 loại vật tư cơ bản (Máy in / Nhựa in / Phụ kiện / Khác) — chính là danh sách
 *     trước đây viết cứng ở ô "Loại" trang Kho, giờ nằm trong bảng danh_muc để sửa được
 * Vật tư, nhà cung cấp, sản phẩm: chủ shop tự nhập.
 *
 * Kèm một việc kiểm soát: chỉ ĐÚNG MỘT email (in3d.admin.email) được mang vai trò
 * admin. Tài khoản nào khác lỡ là admin (người đầu tiên đăng ký ở website bán hàng
 * từng tự động thành admin) bị hạ về khách hàng — trang quản trị chỉ mở cho một người.
 *
 * Chỉ thêm khi bảng còn trống nên an toàn với dữ liệu thật.
 */
@Configuration
public class DataSeeder {

    private static final String ADMIN_MAT_KHAU = "txP12345678@";

    /** Email quản trị DUY NHẤT — đổi bằng in3d.admin.email trong application.properties. */
    private final String adminEmail;

    public DataSeeder(@Value("${in3d.admin.email:txp5201aquarius@gmail.com}") String adminEmail) {
        this.adminEmail = adminEmail.trim().toLowerCase();
    }

    @Bean
    CommandLineRunner napDuLieuBanDau(NguoiDungRepository nguoiDungRepo,
                                      MauSacRepository mauSacRepo,
                                      DanhMucRepository danhMucRepo,
                                      VatTuRepository vatTuRepo,
                                      BoNhoDem boNho) {
        return args -> {
            boolean ghiNguoiDung = napAdmin(nguoiDungRepo);
            boolean ghiMauSac = napMauSac(mauSacRepo);
            boolean ghiLoaiVatTu = napLoaiVatTu(danhMucRepo, vatTuRepo);
            // Bộ nhớ đệm đã nạp lúc dựng bean (để cổng mở là đã ấm), tức TRƯỚC seeder.
            // Lần đầu chạy trên database trống mà không nạp lại thì tài khoản quản trị
            // và bảng màu vừa tạo chưa có trong bộ nhớ đệm (đăng nhập admin sẽ trượt).
            List<String> khoa = new ArrayList<>();
            if (ghiNguoiDung) khoa.add(BoNhoDem.ND);
            if (ghiMauSac) khoa.add(BoNhoDem.MS);
            if (ghiLoaiVatTu) {
                khoa.add(BoNhoDem.DM);
                khoa.add(BoNhoDem.VT);
                khoa.add(BoNhoDem.SP);
            }
            if (!khoa.isEmpty()) boNho.xoaVaNapLai(khoa.toArray(new String[0]));
        };
    }

    /**
     * Ba loại vật tư cơ bản cho ô "Loại" ở trang Kho. Đây là CẤU HÌNH (danh sách
     * chọn) chứ không phải hàng hoá mẫu; chỉ nạp khi nhóm vat_tu còn trống, xoá đi
     * trong trang Danh mục thì lần khởi động sau cũng không mọc lại chừng nào còn
     * ít nhất một loại. Sau đó nối vật tư cũ (chỉ có cột loai) sang loại tương ứng.
     *
     * @return có ghi gì vào database không (để nơi gọi nạp lại bộ nhớ đệm)
     */
    private boolean napLoaiVatTu(DanhMucRepository repo, VatTuRepository vatTuRepo) {
        boolean daGhi = false;
        List<DanhMuc> loai = repo.findByNhomAndDaXoaFalseOrderByThuTuAscIdAsc("vat_tu");
        if (loai.isEmpty()) {
            record L(String ten, String tinhChat, String icon, int thuTu) {}
            List<L> ds = List.of(
                    new L("Máy in",   "may_in",   "fa-print",              1),
                    new L("Nhựa in",  "nhua",     "fa-record-vinyl",       2),
                    new L("Dụng cụ", "dung_cu", "fa-screwdriver-wrench", 3));
            for (L l : ds) {
                DanhMuc d = new DanhMuc();
                d.setTen(l.ten());
                d.setNhom("vat_tu");
                d.setTinhChat(l.tinhChat());
                d.setIcon(l.icon());
                d.setThuTu(l.thuTu());
                repo.save(d);
            }
            System.out.println("[IN3D] Đã nạp " + ds.size() + " loại vật tư cơ bản vào bảng danh mục");
            daGhi = true;
            // Vừa thêm thì mới phải hỏi lại; bình thường dùng luôn kết quả ở trên (bớt một lượt đi-về)
            loai = repo.findByNhomAndDaXoaFalseOrderByThuTuAscIdAsc("vat_tu");
        }

        int daNoi = 0;
        for (VatTu v : vatTuRepo.findByDaXoaFalseOrderByLoaiAscIdAsc()) {
            if (v.getDanhMucId() != null) continue;
            String tc = v.getLoai() == null ? "dung_cu" : v.getLoai();
            DanhMuc khop = loai.stream()
                    .filter(d -> tc.equals(d.getTinhChat()))
                    .findFirst().orElse(null);
            if (khop != null) {
                v.setDanhMucId(khop.getId());
                vatTuRepo.save(v);
                daNoi++;
            }
        }
        if (daNoi > 0) System.out.println("[IN3D] Đã nối loại cho " + daNoi + " vật tư cũ");
        return daGhi || daNoi > 0;
    }

    /** Bộ màu nhựa đang dùng trong kho + vài màu phổ biến. @return có ghi gì không */
    private boolean napMauSac(MauSacRepository repo) {
        if (repo.count() > 0) return false;
        record M(String ten, String ma, int thuTu) {}
        List<M> ds = List.of(
                new M("Đỏ",     "#e03131", 1),
                new M("Vàng",   "#f5b400", 2),
                new M("Đen",    "#1c1c1c", 3),
                new M("Trắng",  "#f8f9fa", 4),
                new M("Be",     "#e0cda9", 5),
                new M("Xám",    "#868e96", 6),
                new M("Xanh lá","#2f9e44", 7),
                new M("Xanh dương", "#1971c2", 8),
                new M("Cam",    "#f76707", 9),
                new M("Hồng",   "#e64980", 10),
                new M("Tím",    "#7048e8", 11),
                new M("Trong suốt", "#dee2e6", 12)
        );
        for (M m : ds) {
            MauSac ms = new MauSac();
            ms.setTen(m.ten());
            ms.setMaMau(m.ma());
            ms.setThuTu(m.thuTu());
            repo.save(ms);
        }
        System.out.println("[IN3D] Đã nạp bảng màu sắc: " + ds.size() + " màu");
        return true;
    }

    /**
     * Đảm bảo tài khoản quản trị luôn đúng cấu hình (email, mật khẩu, vai trò admin).
     * Nếu tài khoản đã tồn tại với mật khẩu/vai trò khác thì đặt lại cho khớp.
     * Muốn tắt: đặt biến môi trường IN3D_TU_TAO_ADMIN=false
     *
     * @return có ghi gì vào bảng nguoi_dung không (để nơi gọi nạp lại bộ nhớ đệm)
     */
    private boolean napAdmin(NguoiDungRepository repo) {
        if ("false".equalsIgnoreCase(System.getenv("IN3D_TU_TAO_ADMIN"))) return false;

        BCryptPasswordEncoder maHoa = new BCryptPasswordEncoder();
        var hienCo = repo.findByEmailIgnoreCase(adminEmail);

        if (hienCo.isEmpty()) {
            NguoiDung admin = new NguoiDung();
            admin.setHoTen("Trương Xuân Phương");
            admin.setEmail(adminEmail);
            admin.setMatKhauHash(maHoa.encode(ADMIN_MAT_KHAU));
            admin.setVaiTro("admin");
            repo.save(admin);
            System.out.println("[IN3D] Đã tạo tài khoản quản trị: " + adminEmail);
            chiMotAdmin(repo);
            return true;
        }
        boolean daHaQuyen = chiMotAdmin(repo);

        NguoiDung nd = hienCo.get();
        boolean doiVaiTro = !"admin".equals(nd.getVaiTro());
        boolean doiMatKhau = !maHoa.matches(ADMIN_MAT_KHAU, nd.getMatKhauHash());
        if (doiVaiTro || doiMatKhau) {
            nd.setVaiTro("admin");
            nd.setMatKhauHash(maHoa.encode(ADMIN_MAT_KHAU));
            repo.save(nd);
            System.out.println("[IN3D] Đã cập nhật tài khoản quản trị " + adminEmail
                    + (doiVaiTro ? " (nâng quyền admin)" : "") + (doiMatKhau ? " (đặt lại mật khẩu)" : ""));
        }
        return daHaQuyen || doiVaiTro || doiMatKhau;
    }

    /**
     * Chỉ ĐÚNG MỘT email được quyền quản trị. Mọi tài khoản khác lỡ mang vai trò
     * admin bị hạ về khách hàng — XacThucService cũng chặn ở bước đăng nhập,
     * nhưng dọn ở đây thì danh sách người dùng không còn hiện hai "Quản trị".
     *
     * @return có hạ quyền tài khoản nào không
     */
    private boolean chiMotAdmin(NguoiDungRepository repo) {
        List<NguoiDung> haQuyen = repo.findByDaXoaFalseOrderByIdAsc().stream()
                .filter(nd -> "admin".equals(nd.getVaiTro()))
                .filter(nd -> !adminEmail.equalsIgnoreCase(nd.getEmail()))
                .toList();
        for (NguoiDung nd : haQuyen) {
            nd.setVaiTro("khach_hang");
            repo.save(nd);
            System.out.println("[IN3D] Hạ quyền admin của " + nd.getEmail()
                    + " — chỉ " + adminEmail + " được vào trang quản trị.");
        }
        return !haQuyen.isEmpty();
    }

}
