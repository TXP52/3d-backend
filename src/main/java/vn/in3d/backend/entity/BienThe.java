package vn.in3d.backend.entity;

import jakarta.persistence.*;

/**
 * BIẾN THỂ (phân loại) của một sản phẩm — ánh xạ bảng public.bien_the.
 *
 * Một tầng phân loại PHẲNG, không phải ma trận hai tầng: "Đỏ", "Xám", "Bản lớn"...
 * Mỗi biến thể có giá / tồn kho / ảnh / trạng thái / lô nhựa riêng; để trống giá
 * hay trạng thái thì lấy theo sản phẩm mẹ.
 *
 * Mỗi sản phẩm có ĐÚNG MỘT biến thể mặc định (database có chỉ mục duy nhất
 * bien_the_mac_dinh_uq): nó xếp đầu ở trang quản trị và là biến thể web khách
 * chọn sẵn. Sản phẩm không phân loại vẫn có một biến thể mặc định tên null,
 * nên nhìn ở trang quản trị y như trước khi có bảng này.
 *
 * TỒN KHO THẬT nằm ở đây. Cột san_pham.ton_kho / so_luong / nhieu_mau là TỔNG
 * của các biến thể chưa xoá, SanPhamController đồng bộ lại sau mỗi lượt ghi để
 * mấy chỗ đọc cũ (web khách, thống kê) vẫn chạy y như cũ.
 */
@Entity
@Table(name = "bien_the")
public class BienThe extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "san_pham_id", nullable = false)
    private Long sanPhamId;

    /** null = biến thể mặc định của sản phẩm không phân loại (form để trống ô Tên). */
    @Column(columnDefinition = "text")
    private String ten;

    /** Màu của biến thể; null = không theo màu / phối nhiều màu (màu suy từ cuộn nhựa). */
    @Column(name = "mau_sac_id")
    private Long mauSacId;

    @Column(name = "ma_sku", columnDefinition = "text")
    private String maSku;

    /** null = lấy giá sản phẩm. */
    private Long gia;

    /** Tồn kho THẬT của biến thể; âm được (shop in theo đơn, xem hợp đồng mục 1). */
    @Column(name = "ton_kho", nullable = false)
    private Integer tonKho = 0;

    /** null = theo trạng thái sản phẩm. */
    @Column(name = "trang_thai")
    private String trangThai;

    /** Ảnh của biến thể, mỗi dòng một đường dẫn, ảnh đầu là ảnh đại diện. */
    @Column(name = "danh_sach_anh", columnDefinition = "text")
    private String danhSachAnh;

    /** Tổng số cái đã in của biến thể này — xem SanPham.soLuong. */
    @Column(name = "so_luong", nullable = false)
    private Integer soLuong = 1;

    /** Nhiều màu hay một màu — cách đếm số cái, xem SanPham.nhieuMau. */
    @Column(name = "nhieu_mau", nullable = false)
    private Boolean nhieuMau = false;

    /** Biến thể được chọn sẵn; mỗi sản phẩm đúng một cái. */
    @Column(name = "mac_dinh", nullable = false)
    private Boolean macDinh = false;

    @Column(name = "thu_tu", nullable = false)
    private Integer thuTu = 0;

    /**
     * Thời gian in MỘT cái của biến thể (phút). Nhân với chi phí chạy máy mỗi giờ
     * (khấu hao + điện + bảo trì, xem ChiPhiMayService) ra tiền máy của một cái.
     * 0 = chưa nhập.
     */
    @Column(name = "thoi_gian_in_phut", nullable = false)
    private Integer thoiGianInPhut = 0;

    // created_at / updated_at / is_deleted nằm ở lớp cha BanGhi

    // Getter / Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSanPhamId() { return sanPhamId; }
    public void setSanPhamId(Long sanPhamId) { this.sanPhamId = sanPhamId; }
    public String getTen() { return ten; }
    public void setTen(String ten) { this.ten = ten; }
    public Long getMauSacId() { return mauSacId; }
    public void setMauSacId(Long mauSacId) { this.mauSacId = mauSacId; }
    public String getMaSku() { return maSku; }
    public void setMaSku(String maSku) { this.maSku = maSku; }
    public Long getGia() { return gia; }
    public void setGia(Long gia) { this.gia = gia; }
    public Integer getTonKho() { return tonKho == null ? 0 : tonKho; }
    public void setTonKho(Integer tonKho) { this.tonKho = tonKho == null ? 0 : tonKho; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
    /** Luôn ít nhất 1, y như SanPham.soLuong: in 0 cái thì không có gì để lưu. */
    public Integer getSoLuong() { return soLuong == null || soLuong < 1 ? 1 : soLuong; }
    public void setSoLuong(Integer soLuong) { this.soLuong = soLuong == null || soLuong < 1 ? 1 : soLuong; }
    public Boolean getNhieuMau() { return Boolean.TRUE.equals(nhieuMau); }
    public void setNhieuMau(Boolean nhieuMau) { this.nhieuMau = Boolean.TRUE.equals(nhieuMau); }
    public Boolean getMacDinh() { return Boolean.TRUE.equals(macDinh); }
    public void setMacDinh(Boolean macDinh) { this.macDinh = Boolean.TRUE.equals(macDinh); }
    public Integer getThuTu() { return thuTu == null ? 0 : thuTu; }
    public void setThuTu(Integer thuTu) { this.thuTu = thuTu == null ? 0 : thuTu; }
    public Integer getThoiGianInPhut() { return thoiGianInPhut == null || thoiGianInPhut < 0 ? 0 : thoiGianInPhut; }
    public void setThoiGianInPhut(Integer phut) { this.thoiGianInPhut = phut == null || phut < 0 ? 0 : phut; }

    /** Danh sách ảnh dạng list (ảnh đầu = ảnh đại diện biến thể). */
    @Transient
    public java.util.List<String> getDanhSachAnhList() {
        java.util.List<String> ds = new java.util.ArrayList<>();
        if (danhSachAnh != null) {
            for (String dong : danhSachAnh.split("\n")) {
                String u = dong.trim();
                if (!u.isEmpty() && !ds.contains(u)) ds.add(u);
            }
        }
        return ds;
    }

    public void setDanhSachAnhList(java.util.List<String> ds) {
        this.danhSachAnh = chuoiAnh(ds);
    }

    /** Danh sách ảnh gộp thành chuỗi để ghi thẳng xuống database (null = không có ảnh). */
    public static String chuoiAnh(java.util.List<String> ds) {
        java.util.List<String> sach = new java.util.ArrayList<>();
        if (ds != null) {
            for (String u : ds) {
                String t = u == null ? "" : u.trim();
                if (!t.isEmpty() && !sach.contains(t)) sach.add(t);
            }
        }
        return sach.isEmpty() ? null : String.join("\n", sach);
    }
}
