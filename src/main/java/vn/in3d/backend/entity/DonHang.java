package vn.in3d.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Đơn hàng — ánh xạ bảng public.don_hang (trùng schema Supabase). */
@Entity
@Table(name = "don_hang")
public class DonHang {

    /** Các trạng thái đơn hợp lệ (trùng ràng buộc CHECK trong schema.sql). */
    public static final List<String> TRANG_THAI_HOP_LE =
            List.of("cho_xac_nhan", "dang_xu_ly", "dang_giao", "hoan_thanh", "da_huy");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ma_don", nullable = false, unique = true)
    private String maDon;

    @Column(name = "ten_khach", nullable = false)
    private String tenKhach;

    @Column(name = "so_dien_thoai", nullable = false)
    private String soDienThoai;

    @Column(name = "dia_chi", nullable = false)
    private String diaChi;

    @Column(name = "ghi_chu", columnDefinition = "text")
    private String ghiChu;

    @Column(name = "tong_tien", nullable = false)
    private Long tongTien = 0L;

    @Column(name = "trang_thai", nullable = false)
    private String trangThai = "cho_xac_nhan";

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "donHang", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DonHangChiTiet> chiTiet = new ArrayList<>();

    @OneToMany(mappedBy = "donHang", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ThanhToan> thanhToan = new ArrayList<>();

    @PrePersist
    void truocKhiLuu() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void truocKhiCapNhat() {
        updatedAt = OffsetDateTime.now();
    }

    public void themChiTiet(DonHangChiTiet ct) {
        ct.setDonHang(this);
        this.chiTiet.add(ct);
    }

    public void themThanhToan(ThanhToan tt) {
        tt.setDonHang(this);
        this.thanhToan.add(tt);
    }

    // Getter / Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMaDon() { return maDon; }
    public void setMaDon(String maDon) { this.maDon = maDon; }
    public String getTenKhach() { return tenKhach; }
    public void setTenKhach(String tenKhach) { this.tenKhach = tenKhach; }
    public String getSoDienThoai() { return soDienThoai; }
    public void setSoDienThoai(String soDienThoai) { this.soDienThoai = soDienThoai; }
    public String getDiaChi() { return diaChi; }
    public void setDiaChi(String diaChi) { this.diaChi = diaChi; }
    public String getGhiChu() { return ghiChu; }
    public void setGhiChu(String ghiChu) { this.ghiChu = ghiChu; }
    public Long getTongTien() { return tongTien; }
    public void setTongTien(Long tongTien) { this.tongTien = tongTien; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<DonHangChiTiet> getChiTiet() { return chiTiet; }
    public List<ThanhToan> getThanhToan() { return thanhToan; }
}
