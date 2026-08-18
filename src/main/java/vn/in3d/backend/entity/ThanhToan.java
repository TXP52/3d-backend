package vn.in3d.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Thanh toán — ánh xạ bảng public.thanh_toan (trùng schema Supabase). */
@Entity
@Table(name = "thanh_toan")
public class ThanhToan extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "don_hang_id")
    @JsonIgnore
    private DonHang donHang;

    /** cod | chuyen_khoan | vi_dien_tu | the */
    @Column(name = "phuong_thuc", nullable = false)
    private String phuongThuc = "cod";

    @Column(name = "so_tien", nullable = false)
    private Long soTien = 0L;

    /** chua_thanh_toan | da_thanh_toan | hoan_tien */
    @Column(name = "trang_thai", nullable = false)
    private String trangThai = "chua_thanh_toan";

    @Column(name = "ma_giao_dich")
    private String maGiaoDich;

    @Column(name = "thanh_toan_luc")
    private OffsetDateTime thanhToanLuc;

    // Getter / Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DonHang getDonHang() { return donHang; }
    public void setDonHang(DonHang donHang) { this.donHang = donHang; }
    public String getPhuongThuc() { return phuongThuc; }
    public void setPhuongThuc(String phuongThuc) { this.phuongThuc = phuongThuc; }
    public Long getSoTien() { return soTien; }
    public void setSoTien(Long soTien) { this.soTien = soTien; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
    public String getMaGiaoDich() { return maGiaoDich; }
    public void setMaGiaoDich(String maGiaoDich) { this.maGiaoDich = maGiaoDich; }
    public OffsetDateTime getThanhToanLuc() { return thanhToanLuc; }
    public void setThanhToanLuc(OffsetDateTime thanhToanLuc) { this.thanhToanLuc = thanhToanLuc; }
}
