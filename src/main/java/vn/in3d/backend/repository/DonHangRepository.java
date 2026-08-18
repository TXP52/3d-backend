package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.DonHang;

import java.util.List;

public interface DonHangRepository extends JpaRepository<DonHang, Long> {
    List<DonHang> findAllByOrderByCreatedAtDesc();

    /** Đơn chưa bị xoá mềm, mới nhất trước. */
    List<DonHang> findByDaXoaFalseOrderByCreatedAtDesc();
}
