package vn.in3d.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import vn.in3d.backend.entity.SanPham;
import vn.in3d.backend.repository.SanPhamRepository;

import java.util.List;

/**
 * Nạp sản phẩm mẫu khi chạy local với H2 (database trống).
 * KHÔNG chạy ở profile "supabase" vì database Supabase đã có dữ liệu từ schema.sql.
 */
@Configuration
@Profile("!supabase")
public class DataSeeder {

    @Bean
    CommandLineRunner napDuLieuMau(SanPhamRepository repo) {
        return args -> {
            if (repo.count() > 0) return;

            record Mau(String ten, long gia, String giaChu, int tonKho) {}
            List<Mau> mau = List.of(
                    new Mau("Máy in 3D FDM Ender-3 V3 SE", 6490000, "6.490.000₫", 20),
                    new Mau("Máy in resin Photon Mono 4", 8990000, "8.990.000₫", 12),
                    new Mau("Bambu Lab A1 mini", 7290000, "7.290.000₫", 15),
                    new Mau("Nhựa PLA+ 1.75mm (1kg)", 290000, "290.000₫", 100),
                    new Mau("Nhựa PETG 1.75mm (1kg)", 320000, "320.000₫", 80),
                    new Mau("Resin tiêu chuẩn (1L)", 550000, "550.000₫", 50),
                    new Mau("Bộ đầu phun (nozzle) thép 0.4mm", 150000, "150.000₫", 200),
                    new Mau("Bàn in nhám PEI nam châm", 350000, "350.000₫", 60),
                    new Mau("Mô hình trang trí in 3D theo yêu cầu", 99000, "Từ 99.000₫", 0),
                    new Mau("Dịch vụ in & scan 3D theo yêu cầu", 0, "Liên hệ", 0)
            );
            for (Mau m : mau) {
                SanPham sp = new SanPham();
                sp.setTen(m.ten());
                sp.setGia(m.gia());
                sp.setGiaChu(m.giaChu());
                sp.setTonKho(m.tonKho());
                sp.setDangBan(true);
                repo.save(sp);
            }
            System.out.println("[IN3D] Đã nạp " + mau.size() + " sản phẩm mẫu vào H2.");
        };
    }
}
