package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.MauSac;

import java.util.List;
import java.util.Optional;

public interface MauSacRepository extends JpaRepository<MauSac, Long> {

    /** Chỉ lấy màu chưa bị xoá mềm, sắp theo thứ tự hiển thị. */
    List<MauSac> findByDaXoaFalseOrderByThuTuAscIdAsc();

    /** MỌI màu kể cả đã xoá, cùng thứ tự — bộ nhớ đệm nạp một lượt rồi tự lọc. */
    List<MauSac> findAllByOrderByThuTuAscIdAsc();

    Optional<MauSac> findByTenIgnoreCaseAndDaXoaFalse(String ten);
}
