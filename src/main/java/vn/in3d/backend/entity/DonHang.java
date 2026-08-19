package vn.in3d.backend.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/** Đơn hàng — ánh xạ bảng public.don_hang (trùng schema Supabase). */
@Entity
@Table(name = "don_hang")
public class DonHang extends BanGhi {

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

    /** Số tiền khách phải trả, ĐÃ trừ khuyến mãi. Tạm tính = tongTien + tienGiam. */
    @Column(name = "tong_tien", nullable = false)
    private Long tongTien = 0L;

    /** Mã khuyến mãi đã áp cho đơn này, để trống nếu không dùng mã. */
    @Column(name = "ma_khuyen_mai", length = 40)
    private String maKhuyenMai;

    @Column(name = "tien_giam", nullable = false,
            columnDefinition = "bigint default 0 not null")
    private Long tienGiam = 0L;

    @Column(name = "trang_thai", nullable = false)
    private String trangThai = "cho_xac_nhan";

    @OneToMany(mappedBy = "donHang", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DonHangChiTiet> chiTiet = new ArrayList<>();

    @OneToMany(mappedBy = "donHang", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ThanhToan> thanhToan = new ArrayList<>();

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
    public String getMaKhuyenMai() { return maKhuyenMai; }
    public void setMaKhuyenMai(String maKhuyenMai) { this.maKhuyenMai = maKhuyenMai; }
    public Long getTienGiam() { return tienGiam == null ? 0L : tienGiam; }
    public void setTienGiam(Long tienGiam) { this.tienGiam = tienGiam == null ? 0L : tienGiam; }
    /** Tiền hàng trước khi trừ khuyến mãi — tiện cho trang quản trị hiển thị. */
    @Transient
    public Long getTamTinh() { return getTongTien() + getTienGiam(); }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
    public List<DonHangChiTiet> getChiTiet() { return chiTiet; }
    public List<ThanhToan> getThanhToan() { return thanhToan; }
}
