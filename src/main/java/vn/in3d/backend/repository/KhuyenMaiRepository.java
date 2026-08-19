package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.KhuyenMai;

import java.util.List;
import java.util.Optional;

public interface KhuyenMaiRepository extends JpaRepository<KhuyenMai, Long> {

    List<KhuyenMai> findByDaXoaFalseOrderByIdDesc();

    List<KhuyenMai> findByDaXoaTrueOrderByIdDesc();

    /** Khách gõ mã kiểu gì cũng nhận: mã lưu chữ HOA, so sánh bỏ qua hoa thường. */
    Optional<KhuyenMai> findByMaIgnoreCaseAndDaXoaFalse(String ma);
}
