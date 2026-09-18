package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.BaiViet;

import java.util.List;
import java.util.Optional;

public interface BaiVietRepository extends JpaRepository<BaiViet, Long> {

    /** Trang quản trị: mọi bài chưa xoá, kể cả bài đang tắt hiển thị. */
    List<BaiViet> findByDaXoaFalseOrderByThuTuAscIdDesc();

    /** Trang khách: chỉ bài đang bật hiển thị. */
    List<BaiViet> findByHienThiTrueAndDaXoaFalseOrderByThuTuAscIdDesc();

    List<BaiViet> findByDaXoaTrueOrderByIdDesc();

    /** MỌI bài kể cả đã xoá, theo thứ tự trang quản trị — bộ nhớ đệm chia ra danh sách và thùng rác. */
    List<BaiViet> findAllByOrderByThuTuAscIdDesc();

    Optional<BaiViet> findByDuongDanAndDaXoaFalse(String duongDan);

    boolean existsByDuongDanAndDaXoaFalse(String duongDan);
}
