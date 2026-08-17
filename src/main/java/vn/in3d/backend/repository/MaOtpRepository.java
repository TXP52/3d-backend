package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.MaOtp;

import java.util.Optional;

public interface MaOtpRepository extends JpaRepository<MaOtp, Long> {
    Optional<MaOtp> findFirstByEmailIgnoreCaseAndDaDungFalseOrderByIdDesc(String email);
}
