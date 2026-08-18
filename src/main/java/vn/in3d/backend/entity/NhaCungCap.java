package vn.in3d.backend.entity;

import jakarta.persistence.*;

/** Nhà cung cấp / nơi mua vật tư (Shopee, Lazada, cửa hàng...). */
@Entity
@Table(name = "nha_cung_cap")
public class NhaCungCap extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ten;

    /** Link shop, số điện thoại, địa chỉ... */
    @Column(name = "lien_he")
    private String lienHe;

    @Column(name = "ghi_chu", columnDefinition = "text")
    private String ghiChu;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTen() { return ten; }
    public void setTen(String ten) { this.ten = ten; }
    public String getLienHe() { return lienHe; }
    public void setLienHe(String lienHe) { this.lienHe = lienHe; }
    public String getGhiChu() { return ghiChu; }
    public void setGhiChu(String ghiChu) { this.ghiChu = ghiChu; }
}
