package vn.in3d.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.repository.KhuyenMaiRepository;

import java.time.LocalDate;

/**
 * Hai chương trình khuyến mãi của shop, chỉ nạp khi bảng khuyen_mai còn TRỐNG.
 *
 *   KHACHHANGMOI  Giảm 10% cho người vừa đăng ký tài khoản.
 *                 Backend đối chiếu "chưa từng có đơn nào" nên mã không xài lại được.
 *
 *   FREESHIPHN    Miễn phí ship, chỉ áp cho địa chỉ nội thành Hà Nội.
 *                 Backend so địa chỉ với danh sách quận, khách ngoại tỉnh gõ mã cũng không ăn.
 *
 * Cả hai BẬT SẴN vì đây là chương trình chủ shop yêu cầu chạy thật,
 * khác với mấy mã mẫu trước đây (đã xoá).
 */
@Configuration
public class KhuyenMaiSeeder {

    /** 12 quận nội thành Hà Nội — dùng cho điều kiện địa chỉ của mã freeship. */
    public static final String NOI_THANH_HA_NOI =
            "Ba Đình,Hoàn Kiếm,Hai Bà Trưng,Đống Đa,Tây Hồ,Cầu Giấy,"
            + "Thanh Xuân,Hoàng Mai,Long Biên,Hà Đông,Bắc Từ Liêm,Nam Từ Liêm";

    @Bean
    CommandLineRunner napKhuyenMai(KhuyenMaiRepository repo) {
        return args -> {
            if (repo.count() > 0) return;
            LocalDate homNay = LocalDate.now();

            KhuyenMai khachMoi = new KhuyenMai();
            khachMoi.setKieuApDung("don_hang");
            khachMoi.setMa("KHACHHANGMOI");
            khachMoi.setTen("Chào khách hàng mới — giảm 10%");
            khachMoi.setMoTa("Tặng người vừa đăng ký tài khoản. Mỗi khách dùng được một lần, "
                    + "giảm 10% đơn đầu tiên, tối đa 50.000đ.");
            khachMoi.setLoai("phan_tram");
            khachMoi.setGiaTri(10L);
            khachMoi.setGiamToiDa(50_000L);
            khachMoi.setDonToiThieu(100_000L);
            khachMoi.setChiKhachMoi(true);
            khachMoi.setBatDau(homNay);
            khachMoi.setHoatDong(true);
            khachMoi.setHienThi(true);
            repo.save(khachMoi);

            KhuyenMai freeship = new KhuyenMai();
            freeship.setKieuApDung("don_hang");
            freeship.setMa("FREESHIPHN");
            freeship.setTen("Miễn phí giao hàng nội thành Hà Nội");
            freeship.setMoTa("Đơn từ 200.000đ, giao trong 12 quận nội thành Hà Nội được miễn phí ship.");
            freeship.setLoai("mien_ship");
            freeship.setGiaTri(25_000L);
            freeship.setDonToiThieu(200_000L);
            freeship.setDieuKienDiaChi(NOI_THANH_HA_NOI);
            freeship.setBatDau(homNay);
            freeship.setHoatDong(true);
            freeship.setHienThi(true);
            repo.save(freeship);

            System.out.println("[IN3D] Đã nạp 2 chương trình khuyến mãi: KHACHHANGMOI, FREESHIPHN");
        };
    }
}
