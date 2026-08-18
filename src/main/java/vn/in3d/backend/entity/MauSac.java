package vn.in3d.backend.entity;

import jakarta.persistence.*;

/**
 * Bảng màu sắc dùng chung cho nhựa in và sản phẩm.
 * Trước đây màu chỉ là chữ gõ tay trong vat_tu.mau nên hay sai chính tả
 * ("Xám" / "xam" / "Ghi") và không hiện được ô màu trên giao diện.
 */
@Entity
@Table(name = "mau_sac")
public class MauSac extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tên hiển thị: Đỏ, Vàng, Đen, Trắng, Be, Xám... */
    @Column(nullable = false)
    private String ten;

    /** Mã màu hex để vẽ ô màu trên giao diện, ví dụ #e03131 */
    @Column(name = "ma_mau", length = 20)
    private String maMau;

    @Column(name = "ghi_chu", columnDefinition = "text")
    private String ghiChu;

    /** Thứ tự hiển thị trong danh sách chọn (số nhỏ lên trước) */
    @Column(name = "thu_tu", nullable = false)
    private Integer thuTu = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTen() { return ten; }
    public void setTen(String ten) { this.ten = ten; }
    public String getMaMau() { return maMau; }
    public void setMaMau(String maMau) { this.maMau = maMau; }
    public String getGhiChu() { return ghiChu; }
    public void setGhiChu(String ghiChu) { this.ghiChu = ghiChu; }
    public Integer getThuTu() { return thuTu == null ? 0 : thuTu; }
    public void setThuTu(Integer thuTu) { this.thuTu = thuTu == null ? 0 : thuTu; }
}
