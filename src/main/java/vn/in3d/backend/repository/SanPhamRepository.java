package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.SanPham;

import java.util.List;

public interface SanPhamRepository extends JpaRepository<SanPham, Long> {
    List<SanPham> findByDangBanTrueOrderByIdAsc();
}
