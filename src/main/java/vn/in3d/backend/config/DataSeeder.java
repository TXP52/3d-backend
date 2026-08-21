package vn.in3d.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.entity.MauSac;
import vn.in3d.backend.entity.NhaCungCap;
import vn.in3d.backend.entity.VatTu;
import vn.in3d.backend.repository.NguoiDungRepository;
import vn.in3d.backend.repository.MauSacRepository;
import vn.in3d.backend.repository.NhaCungCapRepository;
import vn.in3d.backend.repository.VatTuRepository;

import java.util.List;

/**
 * Nạp dữ liệu ban đầu: tài khoản quản trị, nhà cung cấp, kho vật tư, sản phẩm mẫu.
 * Chạy ở MỌI profile nhưng chỉ thêm khi bảng còn trống nên an toàn với dữ liệu thật.
 */
@Configuration
public class DataSeeder {

    private static final String ADMIN_EMAIL = "txp5201aquarius@gmail.com";
    private static final String ADMIN_MAT_KHAU = "txP12345678@";

    @Bean
    CommandLineRunner napDuLieuBanDau(NguoiDungRepository nguoiDungRepo,
                                      NhaCungCapRepository nccRepo,
                                      VatTuRepository vatTuRepo,
                                      MauSacRepository mauSacRepo) {
        return args -> {
            napAdmin(nguoiDungRepo);
            napMauSac(mauSacRepo);
            Long shopeeId = napNhaCungCap(nccRepo);
            napVatTu(vatTuRepo, shopeeId);
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

    private Long napNhaCungCap(NhaCungCapRepository repo) {
        return repo.findByTenIgnoreCase("Shopee").map(NhaCungCap::getId).orElseGet(() -> {
            NhaCungCap shopee = new NhaCungCap();
            shopee.setTen("Shopee");
            shopee.setLienHe("https://shopee.vn");
            shopee.setGhiChu("Nơi mua nhựa in 3D (PLA, PETG)");
            return repo.save(shopee).getId();
        });
    }

    /** Kho vật tư ban đầu: 1 máy in + 2 cuộn PETG + 4 cuộn PLA. */
    private void napVatTu(VatTuRepository repo, Long shopeeId) {
        if (repo.count() > 0) return;

        VatTu may = new VatTu();
        may.setTen("Máy in 3D Bambu Lab A1");
        may.setLoai("may_in");
        may.setGia(10_900_000L);
        may.setSoLuong(1);
        may.setKhoiLuongGram(0);
        repo.save(may);

        // 2 cuộn PETG: 300.000đ cho cả 2 cuộn -> 150.000đ/cuộn, mỗi cuộn 1kg
        for (String mau : List.of("Đỏ", "Vàng")) {
            VatTu petg = new VatTu();
            petg.setTen("Nhựa PETG 1.75mm (1kg)");
            petg.setLoai("nhua");
            petg.setMau(mau);
            petg.setGia(150_000L);
            petg.setSoLuong(1);
            petg.setKhoiLuongGram(1000);
            petg.setNhaCungCapId(shopeeId);
            repo.save(petg);
        }

        // 4 cuộn PLA: 222.000đ/cuộn, mỗi cuộn 1kg
        for (String mau : List.of("Đen", "Trắng", "Be", "Xám")) {
            VatTu pla = new VatTu();
            pla.setTen("Nhựa PLA 1.75mm (1kg)");
            pla.setLoai("nhua");
            pla.setMau(mau);
            pla.setGia(222_000L);
            pla.setSoLuong(1);
            pla.setKhoiLuongGram(1000);
            pla.setNhaCungCapId(shopeeId);
            repo.save(pla);
        }
        System.out.println("[IN3D] Đã nạp kho vật tư: 1 máy in + 2 cuộn PETG + 4 cuộn PLA");
    }

}
