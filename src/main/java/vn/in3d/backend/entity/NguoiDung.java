package vn.in3d.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * Người dùng — lưu TRỰC TIẾP trong database (không dùng Supabase Auth).
 * Mật khẩu được băm bằng BCrypt, không bao giờ lưu/trả về dạng gốc.
 *
 * Bảng này giữ CẢ khách chủ shop tự nhập ở trang quản trị (mua qua Facebook / Zalo /
 * tại shop): những khách đó thường không có email và chưa có mật khẩu nên hai cột
 * email và mat_khau_hash đều cho phép để trống (xem sql/2026-09-18-...). Không có
 * mật khẩu thì không đăng nhập được, chỉ là một dòng danh bạ khách hàng.
 */
@Entity
@Table(name = "nguoi_dung")
public class NguoiDung extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ho_ten", nullable = false)
    private String hoTen;

    /** null = khách chủ shop tự nhập, chưa cho email. Có email thì phải duy nhất. */
    @Column(unique = true)
    private String email;

    /** Băm BCrypt — không trả về trong JSON. null = chưa có mật khẩu, không đăng nhập được. */
    @Column(name = "mat_khau_hash")
    @JsonIgnore
    private String matKhauHash;

    @Column(name = "so_dien_thoai")
    private String soDienThoai;

    @Column(name = "dia_chi")
    private String diaChi;

    /** Ghi chú của chủ shop về khách (khách quen, hay mua gì...). */
    @Column(name = "ghi_chu", columnDefinition = "text")
    private String ghiChu;

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
    public String getGhiChu() { return ghiChu; }
    public void setGhiChu(String ghiChu) { this.ghiChu = ghiChu; }
    public String getVaiTro() { return vaiTro; }
    public void setVaiTro(String vaiTro) { this.vaiTro = vaiTro; }
}
