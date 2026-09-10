package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.SanPhamVatTu;

import java.util.List;

public interface SanPhamVatTuRepository extends JpaRepository<SanPhamVatTu, Long> {

    List<SanPhamVatTu> findBySanPhamIdOrderByIdAsc(Long sanPhamId);

    void deleteBySanPhamId(Long sanPhamId);
}
