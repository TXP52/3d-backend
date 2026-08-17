package vn.in3d.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Sản phẩm — ánh xạ bảng public.san_pham (trùng schema Supabase). */
@Entity
@Table(name = "san_pham")
public class SanPham {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ten;

    @Column(name = "mo_ta", columnDefinition = "text")
    private String moTa;

    @Column(nullable = false)
    private Long gia = 0L;

    @Column(name = "gia_chu")
    private String giaChu;

    @Column(name = "danh_muc_id")
    private Long danhMucId;

    @Column(name = "hinh_anh")
    private String hinhAnh;

    @Column(name = "ton_kho", nullable = false)
    private Integer tonKho = 0;

    @Column(name = "dang_ban", nullable = false)
    private Boolean dangBan = true;

    /**
     * Trạng thái sản phẩm trong quy trình in:
     * du_kien | da_dat | dang_in | san_hang | thanh_cong | hoan_hang | dang_van_chuyen | het_hang
     */
    // columnDefinition có DEFAULT để thêm cột vào bảng đã có dữ liệu không bị lỗi NOT NULL
    @Column(name = "trang_thai", nullable = false,
            columnDefinition = "varchar(40) default 'san_hang' not null")
    private String trangThai = "san_hang";

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void truocKhiLuu() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void truocKhiCapNhat() {
        updatedAt = OffsetDateTime.now();
    }

    // Getter / Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTen() { return ten; }
    public void setTen(String ten) { this.ten = ten; }
    public String getMoTa() { return moTa; }
    public void setMoTa(String moTa) { this.moTa = moTa; }
    public Long getGia() { return gia; }
    public void setGia(Long gia) { this.gia = gia; }
    public String getGiaChu() { return giaChu; }
    public void setGiaChu(String giaChu) { this.giaChu = giaChu; }
    public Long getDanhMucId() { return danhMucId; }
    public void setDanhMucId(Long danhMucId) { this.danhMucId = danhMucId; }
    public String getHinhAnh() { return hinhAnh; }
    public void setHinhAnh(String hinhAnh) { this.hinhAnh = hinhAnh; }
    public Integer getTonKho() { return tonKho; }
    public void setTonKho(Integer tonKho) { this.tonKho = tonKho; }
    public Boolean getDangBan() { return dangBan; }
    public void setDangBan(Boolean dangBan) { this.dangBan = dangBan; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
