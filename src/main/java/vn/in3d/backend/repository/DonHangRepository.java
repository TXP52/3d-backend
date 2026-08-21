package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.DonHang;

import java.util.List;

public interface DonHangRepository extends JpaRepository<DonHang, Long> {
    List<DonHang> findAllByOrderByCreatedAtDesc();

    /** Đơn chưa bị xoá mềm, mới nhất trước. */
    List<DonHang> findByDaXoaFalseOrderByCreatedAtDesc();

    /**
     * Đếm đơn của một tài khoản để biết có phải khách mới không.
     * Đếm cả đơn đã xoá mềm: đặt rồi huỷ vẫn không còn là khách mới nữa.
     */
    long countByNguoiDungId(Long nguoiDungId);

    /** Khách chưa đăng nhập thì đối chiếu bằng số điện thoại. */
    long countBySoDienThoai(String soDienThoai);
}
