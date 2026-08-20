package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.SanPham;

import java.util.List;
import java.util.Optional;

public interface SanPhamRepository extends JpaRepository<SanPham, Long> {
    List<SanPham> findByDangBanTrueOrderByIdAsc();

    /**
     * Tra sản phẩm theo tên để đối chiếu giá lúc tạo đơn.
     * Giỏ hàng của khách chỉ mang theo TÊN món, không mang id.
     */
    Optional<SanPham> findFirstByTenIgnoreCaseAndDaXoaFalse(String ten);

    /** Hàng đang bán, chưa bị xoá mềm — dùng cho trang bán hàng. */
    List<SanPham> findByDangBanTrueAndDaXoaFalseOrderByIdAsc();

    /** Toàn bộ hàng chưa bị xoá mềm — dùng cho trang quản trị. */
    List<SanPham> findByDaXoaFalseOrderByIdAsc();
}
