package vn.in3d.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Mã OTP gửi qua email khi đăng nhập trang quản trị. */
@Entity
@Table(name = "ma_otp")
public class MaOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 6)
    private String ma;

    @Column(name = "het_han", nullable = false)
    private OffsetDateTime hetHan;

    @Column(name = "da_dung", nullable = false)
    private Boolean daDung = false;

    /** Số lần nhập sai — quá 5 lần thì mã bị vô hiệu. */
    @Column(name = "so_lan_sai", nullable = false)
    private Integer soLanSai = 0;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void truocKhiLuu() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMa() { return ma; }
    public void setMa(String ma) { this.ma = ma; }
    public OffsetDateTime getHetHan() { return hetHan; }
    public void setHetHan(OffsetDateTime hetHan) { this.hetHan = hetHan; }
    public Boolean getDaDung() { return daDung; }
    public void setDaDung(Boolean daDung) { this.daDung = daDung; }
    public Integer getSoLanSai() { return soLanSai; }
    public void setSoLanSai(Integer soLanSai) { this.soLanSai = soLanSai; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
