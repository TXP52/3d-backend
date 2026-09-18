package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.NguoiDung;

import java.util.List;
import java.util.Optional;

public interface NguoiDungRepository extends JpaRepository<NguoiDung, Long> {
    Optional<NguoiDung> findByEmailIgnoreCase(String email);

    Optional<NguoiDung> findByEmailIgnoreCaseAndDaXoaFalse(String email);

    List<NguoiDung> findByDaXoaFalseOrderByIdAsc();

    /** MỌI tài khoản kể cả đã xoá — bộ nhớ đệm dùng để đọc token khỏi hỏi database. */
    List<NguoiDung> findAllByOrderByIdAsc();
    boolean existsByEmailIgnoreCase(String email);
}
