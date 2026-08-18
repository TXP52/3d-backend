package vn.in3d.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * Người dùng — lưu TRỰC TIẾP trong database (không dùng Supabase Auth).
 * Mật khẩu được băm bằng BCrypt, không bao giờ lưu/trả về dạng gốc.
 */
@Entity
@Table(name = "nguoi_dung")
public class NguoiDung extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ho_ten", nullable = false)
    private String hoTen;

    @Column(nullable = false, unique = true)
    private String email;

    /** Băm BCrypt — không trả về trong JSON. */
    @Column(name = "mat_khau_hash", nullable = false)
    @JsonIgnore
    private String matKhauHash;

    @Column(name = "so_dien_thoai")
    private String soDienThoai;

    @Column(name = "dia_chi")
    private String diaChi;

    /** khach_hang | admin */
    @Column(name = "vai_tro", nullable = false)
    private String vaiTro = "khach_hang";

    // Getter / Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getHoTen() { return hoTen; }
    public void setHoTen(String hoTen) { this.hoTen = hoTen; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMatKhauHash() { return matKhauHash; }
    public void setMatKhauHash(String matKhauHash) { this.matKhauHash = matKhauHash; }
    public String getSoDienThoai() { return soDienThoai; }
    public void setSoDienThoai(String soDienThoai) { this.soDienThoai = soDienThoai; }
    public String getDiaChi() { return diaChi; }
    public void setDiaChi(String diaChi) { this.diaChi = diaChi; }
    public String getVaiTro() { return vaiTro; }
    public void setVaiTro(String vaiTro) { this.vaiTro = vaiTro; }
}
