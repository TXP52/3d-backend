package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.NhaCungCap;

import java.util.List;
import java.util.Optional;

public interface NhaCungCapRepository extends JpaRepository<NhaCungCap, Long> {
    Optional<NhaCungCap> findByTenIgnoreCase(String ten);

    List<NhaCungCap> findByDaXoaFalseOrderByIdAsc();

    /** MỌI nhà cung cấp kể cả đã xoá — bộ nhớ đệm nạp một lượt rồi tự lọc. */
    List<NhaCungCap> findAllByOrderByIdAsc();
}
