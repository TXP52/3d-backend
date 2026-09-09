package vn.in3d.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.entity.MauSac;
import vn.in3d.backend.repository.NguoiDungRepository;
import vn.in3d.backend.repository.MauSacRepository;

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
 * Vật tư, nhà cung cấp, sản phẩm: chủ shop tự nhập.
 *
 * Chạy ở MỌI profile nhưng chỉ thêm khi bảng còn trống nên an toàn với dữ liệu thật.
 */
@Configuration
public class DataSeeder {

    private static final String ADMIN_EMAIL = "txp5201aquarius@gmail.com";
    private static final String ADMIN_MAT_KHAU = "txP12345678@";

    @Bean
    CommandLineRunner napDuLieuBanDau(NguoiDungRepository nguoiDungRepo,
                                      MauSacRepository mauSacRepo) {
        return args -> {
            napAdmin(nguoiDungRepo);
            napMauSac(mauSacRepo);
        };
    }

    /** Bộ màu nhựa đang dùng trong kho + vài màu phổ biến. */
    private void napMauSac(MauSacRepository repo) {
        if (repo.count() > 0) return;
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
    }

    /**
     * Đảm bảo tài khoản quản trị luôn đúng cấu hình (email, mật khẩu, vai trò admin).
     * Nếu tài khoản đã tồn tại với mật khẩu/vai trò khác thì đặt lại cho khớp.
     * Muốn tắt: đặt biến môi trường IN3D_TU_TAO_ADMIN=false
     */
    private void napAdmin(NguoiDungRepository repo) {
        if ("false".equalsIgnoreCase(System.getenv("IN3D_TU_TAO_ADMIN"))) return;

        BCryptPasswordEncoder maHoa = new BCryptPasswordEncoder();
        var hienCo = repo.findByEmailIgnoreCase(ADMIN_EMAIL);

        if (hienCo.isEmpty()) {
            NguoiDung admin = new NguoiDung();
            admin.setHoTen("Trương Xuân Phương");
            admin.setEmail(ADMIN_EMAIL);
            admin.setMatKhauHash(maHoa.encode(ADMIN_MAT_KHAU));
            admin.setVaiTro("admin");
            repo.save(admin);
            System.out.println("[IN3D] Đã tạo tài khoản quản trị: " + ADMIN_EMAIL);
            return;
        }

        NguoiDung nd = hienCo.get();
        boolean doiVaiTro = !"admin".equals(nd.getVaiTro());
        boolean doiMatKhau = !maHoa.matches(ADMIN_MAT_KHAU, nd.getMatKhauHash());
        if (doiVaiTro || doiMatKhau) {
            nd.setVaiTro("admin");
            nd.setMatKhauHash(maHoa.encode(ADMIN_MAT_KHAU));
            repo.save(nd);
            System.out.println("[IN3D] Đã cập nhật tài khoản quản trị " + ADMIN_EMAIL
                    + (doiVaiTro ? " (nâng quyền admin)" : "") + (doiMatKhau ? " (đặt lại mật khẩu)" : ""));
        }
    }

}
