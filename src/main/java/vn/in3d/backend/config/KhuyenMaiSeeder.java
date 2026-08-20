package vn.in3d.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.repository.KhuyenMaiRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Ba mã khuyến mãi mẫu, chỉ nạp khi bảng khuyen_mai còn TRỐNG.
 *
 * CẢ BA ĐỀU ĐỂ TẠM DỪNG (hoatDong = false).
 * Mã khuyến mãi là tiền thật: nếu nạp sẵn ở trạng thái đang chạy thì ngay khi
 * mở web ai đoán trúng "GIAM50K" là mua được rẻ hơn mà chủ shop không hề biết.
 * Vào trang Khuyến mãi, sửa lại cho đúng ý rồi bấm "Bật" thì mã mới có hiệu lực.
 */
@Configuration
public class KhuyenMaiSeeder {

    @Bean
    CommandLineRunner napKhuyenMai(KhuyenMaiRepository repo) {
        return args -> {
            if (repo.count() > 0) return;

            LocalDate homNay = LocalDate.now();
            record K(String kieu, String ma, String ten, String moTa, String loai,
                     long giaTri, long giamToiDa, long donToiThieu, int soLuong, int soNgay) {}

            List<K> ds = List.of(
                    // --- Giảm theo đơn: khách gõ mã ở giỏ hàng ---
                    new K("don_hang", "CHAOBAN", "Giảm 10% cho khách mới",
                            "Áp dụng cho đơn đầu tiên, giảm tối đa 50.000đ.",
                            "phan_tram", 10, 50_000, 100_000, 100, 60),
                    new K("don_hang", "FREESHIPHN", "Miễn phí giao hàng nội thành Hà Nội",
                            "Đơn từ 200.000đ được miễn phí ship trong nội thành.",
                            "mien_ship", 25_000, 0, 200_000, 0, 90),
                    new K("don_hang", "GIAM50K", "Giảm thẳng 50.000đ",
                            "Dành cho đơn từ 500.000đ, mỗi tháng 50 lượt.",
                            "so_tien", 50_000, 0, 500_000, 50, 30),
                    // --- Giảm giá sản phẩm: tự trừ vào giá món, không có mã ---
                    // sanPhamIds để trống = áp cho mọi sản phẩm; vào trang Khuyến mãi
                    // tích chọn lại đúng mấy món muốn giảm rồi hãy bật
                    new K("san_pham", null, "Sale cuối tuần — giảm 20% toàn bộ sản phẩm",
                            "Giảm thẳng vào giá từng món, khách không cần nhập mã.",
                            "phan_tram", 20, 0, 0, 0, 14)
            );

            for (K k : ds) {
                KhuyenMai km = new KhuyenMai();
                km.setKieuApDung(k.kieu());
                km.setMa(k.ma());
                km.setTen(k.ten());
                km.setMoTa(k.moTa());
                km.setLoai(k.loai());
                km.setGiaTri(k.giaTri());
                km.setGiamToiDa(k.giamToiDa());
                km.setDonToiThieu(k.donToiThieu());
                km.setSoLuong(k.soLuong());
                km.setBatDau(homNay);
                km.setKetThuc(homNay.plusDays(k.soNgay()));
                km.setApDungCho("tat_ca");
                km.setHoatDong(false);   // xem ghi chú đầu file
                km.setHienThi(true);
                repo.save(km);
            }
            System.out.println("[IN3D] Đã nạp " + ds.size()
                    + " chương trình khuyến mãi mẫu (đang TẠM DỪNG, vào trang Khuyến mãi bật khi cần)");
        };
    }
}
