package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.in3d.backend.entity.BoSuuTap;

import java.util.List;
import java.util.Optional;

public interface BoSuuTapRepository extends JpaRepository<BoSuuTap, Long> {

    /**
     * MỌI bộ sưu tập (kể cả đã xoá mềm) kèm luôn danh sách sản phẩm của từng bộ —
     * MỘT truy vấn cho cả bộ dữ liệu, xem NapDuLieu.napBoSuuTap.
     * Thứ tự: thu_tu rồi id; sản phẩm trong bộ theo @OrderBy của BoSuuTap.sanPham.
     */
    @Query("select distinct b from BoSuuTap b left join fetch b.sanPham order by b.thuTu asc, b.id asc")
    List<BoSuuTap> napKemSanPham();

    /** Đường dẫn chỉ duy nhất trong các bộ CHƯA xoá — dùng lúc sinh đường dẫn mới. */
    Optional<BoSuuTap> findByDuongDanAndDaXoaFalse(String duongDan);
}
