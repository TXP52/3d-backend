package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.MauSac;

import java.util.List;
import java.util.Optional;

public interface MauSacRepository extends JpaRepository<MauSac, Long> {

    /** Chỉ lấy màu chưa bị xoá mềm, sắp theo thứ tự hiển thị. */
    List<MauSac> findByDaXoaFalseOrderByThuTuAscIdAsc();

    Optional<MauSac> findByTenIgnoreCaseAndDaXoaFalse(String ten);
}
