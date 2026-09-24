package vn.in3d.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * MỘT ĐỢT NHẬP HÀNG của một vật tư: ngày nhập, số lượng, đơn giá của riêng đợt đó
 * và nơi mua.
 *
 * Cùng một cuộn nhựa mua tháng này 222.000đ, tháng sau 248.000đ — mỗi lần là một dòng
 * ở đây, không sửa đè lên nhau nữa. Vật tư giữ ba số cộng từ các đợt (so_luong,
 * tien_mua, gia bình quân) nên mọi chỗ tính vốn đang đọc vat_tu vẫn chạy như cũ.
 *
 * Bảng này KHÔNG xoá mềm: bỏ một đợt nhập là gõ nhầm, giữ lại chỉ làm lệch tổng tiền.
 */
@Entity
@Table(name = "lo_nhap")
public class LoNhap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vat_tu_id", nullable = false)
    private Long vatTuId;

    /** Ngày hàng về kho (chủ shop gõ, mặc định hôm nay). */
    @Column(name = "ngay_nhap", nullable = false)
    private LocalDate ngayNhap = LocalDate.now();

    /** Số cuộn / cái nhập ở đợt này. */
    @Column(name = "so_luong", nullable = false)
    private Integer soLuong = 1;

    /** Đơn giá MỘT đơn vị ở đợt này (VNĐ). */
    @Column(nullable = false)
    private Long gia = 0L;

    /** Nơi mua đợt này — mỗi đợt có thể một nơi khác nhau. */
    @Column(name = "nha_cung_cap_id")
    private Long nhaCungCapId;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void truocKhiTao() {
        OffsetDateTime bayGio = OffsetDateTime.now();
        if (createdAt == null) createdAt = bayGio;
        updatedAt = bayGio;
    }

    @PreUpdate
    void truocKhiSua() { updatedAt = OffsetDateTime.now(); }

    /** Tiền của riêng đợt này = đơn giá × số lượng. */
    @Transient
    public long getThanhTien() {
        return (gia == null ? 0 : gia) * (soLuong == null ? 0 : soLuong);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVatTuId() { return vatTuId; }
    public void setVatTuId(Long vatTuId) { this.vatTuId = vatTuId; }
    public LocalDate getNgayNhap() { return ngayNhap; }
    public void setNgayNhap(LocalDate ngayNhap) { this.ngayNhap = ngayNhap; }
    public Integer getSoLuong() { return soLuong; }
    public void setSoLuong(Integer soLuong) { this.soLuong = soLuong; }
    public Long getGia() { return gia; }
    public void setGia(Long gia) { this.gia = gia; }
    public Long getNhaCungCapId() { return nhaCungCapId; }
    public void setNhaCungCapId(Long nhaCungCapId) { this.nhaCungCapId = nhaCungCapId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
