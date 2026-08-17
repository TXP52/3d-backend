package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.VatTu;

import java.util.List;

public interface VatTuRepository extends JpaRepository<VatTu, Long> {
    List<VatTu> findAllByOrderByLoaiAscIdAsc();
}
