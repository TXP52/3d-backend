package vn.in3d.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * Chi tiết đơn hàng — ánh xạ bảng public.don_hang_chi_tiet.
 * Lưu ý: cột thanh_tien trong Supabase là GENERATED COLUMN (tự tính = don_gia * so_luong)
 * nên KHÔNG ánh xạ ở đây; muốn lấy thành tiền thì gọi getThanhTien().
 */
@Entity
@Table(name = "don_hang_chi_tiet")
public class DonHangChiTiet extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "don_hang_id")
    @JsonIgnore
    private DonHang donHang;

    @Column(name = "san_pham_id")
    private Long sanPhamId;

    @Column(name = "ten_san_pham", nullable = false)
    private String tenSanPham;

    /**
     * Đơn giá SAU khi trừ khuyến mãi sản phẩm.
     * Phải là giá cuối vì cột thanh_tien trong Supabase tự tính = don_gia * so_luong.
     */
    @Column(name = "don_gia", nullable = false)
    private Long donGia = 0L;

    /** Giá niêm yết trước khi giảm; bằng donGia nghĩa là món này không được giảm. */
    @Column(name = "don_gia_goc", nullable = false,
            columnDefinition = "bigint default 0 not null")
    private Long donGiaGoc = 0L;

    @Column(name = "so_luong", nullable = false)
    private Integer soLuong = 1;

    /** Thành tiền = đơn giá x số lượng (tính trong Java, không đọc cột generated). */
    @Transient
    public Long getThanhTien() {
        return (donGia == null ? 0 : donGia) * (soLuong == null ? 0 : soLuong);
    }

    /** Số tiền dòng này được giảm nhờ khuyến mãi sản phẩm. */
    @Transient
    public Long getTienGiamDong() {
        long goc = donGiaGoc == null || donGiaGoc == 0 ? (donGia == null ? 0 : donGia) : donGiaGoc;
        return Math.max(0, (goc - (donGia == null ? 0 : donGia)) * (soLuong == null ? 0 : soLuong));
    }

    // Getter / Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DonHang getDonHang() { return donHang; }
    public void setDonHang(DonHang donHang) { this.donHang = donHang; }
    public Long getSanPhamId() { return sanPhamId; }
    public void setSanPhamId(Long sanPhamId) { this.sanPhamId = sanPhamId; }
    public String getTenSanPham() { return tenSanPham; }
    public void setTenSanPham(String tenSanPham) { this.tenSanPham = tenSanPham; }
    public Long getDonGia() { return donGia; }
    public void setDonGia(Long donGia) { this.donGia = donGia; }
    public Long getDonGiaGoc() { return donGiaGoc == null || donGiaGoc == 0 ? donGia : donGiaGoc; }
    public void setDonGiaGoc(Long donGiaGoc) { this.donGiaGoc = donGiaGoc == null ? 0L : donGiaGoc; }
    public Integer getSoLuong() { return soLuong; }
    public void setSoLuong(Integer soLuong) { this.soLuong = soLuong; }
}
