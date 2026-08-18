package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.SanPham;

import java.util.List;

public interface SanPhamRepository extends JpaRepository<SanPham, Long> {
    List<SanPham> findByDangBanTrueOrderByIdAsc();

    /** Hàng đang bán, chưa bị xoá mềm — dùng cho trang bán hàng. */
    List<SanPham> findByDangBanTrueAndDaXoaFalseOrderByIdAsc();

    /** Toàn bộ hàng chưa bị xoá mềm — dùng cho trang quản trị. */
    List<SanPham> findByDaXoaFalseOrderByIdAsc();
}
