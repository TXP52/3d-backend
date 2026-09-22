package vn.in3d.backend.entity;

import jakarta.persistence.*;

/** Sản phẩm — ánh xạ bảng public.san_pham (trùng schema Supabase). */
@Entity
@Table(name = "san_pham")
public class SanPham extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ten;

    /**
     * MÃ SẢN PHẨM chủ shop tự đặt (chữ, số, - _ . tối đa 40 ký tự); để trống thì backend
     * sinh "SP-<số lớn nhất đang dùng + 1>". Không trùng giữa các sản phẩm CHƯA xoá, không
     * phân biệt hoa thường (chỉ mục san_pham_ma_uq); xoá mềm là nhả mã cho sản phẩm khác.
     * null = dòng tạo từ trước khi có cột này — mọi chỗ hiển thị coi như "SP-<id>" (getMaHienThi).
     */
    @Column(name = "ma_san_pham", length = 40)
    private String maSanPham;

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
    /**
     * Loại sản phẩm: ban (hàng bán) | mau (hàng mẫu, chỉ trưng bày) | dich_vu (nhận in theo yêu cầu)
     * Hàng "mau" không tính vào doanh thu và không cho khách đặt.
     */
    @Column(name = "loai_san_pham", nullable = false,
            columnDefinition = "varchar(30) default 'ban' not null")
    private String loaiSanPham = "ban";

    // columnDefinition có DEFAULT để thêm cột vào bảng đã có dữ liệu không bị lỗi NOT NULL
    @Column(name = "trang_thai", nullable = false,
            columnDefinition = "varchar(40) default 'san_hang' not null")
    private String trangThai = "san_hang";

    /**
     * TỔNG SỐ CÁI ĐÃ IN của mẫu này.
     *
     * Không cộng từ các dòng nhựa được: một cái clicker nhiều màu ăn cả cuộn đỏ
     * lẫn cuộn vàng, in 10 cái thì mỗi cuộn đều "dùng cho 10 cái" nhưng tổng
     * vẫn là 10, không phải 20. Số cái dùng TỪNG cuộn nằm ở san_pham_vat_tu
     * (in 1 cái đen + 1 cái trắng thì mỗi dòng 1, tổng ở đây là 2).
     */
    @Column(name = "so_luong", nullable = false,
            columnDefinition = "integer default 1 not null")
    private Integer soLuong = 1;

    /**
     * NHIỀU MÀU hay MỘT MÀU — quyết định cách đếm số cái:
     *   nhiều màu: mỗi cái ăn TẤT CẢ các cuộn trong danh sách (clicker đỏ + vàng),
     *              nhập một số lượng chung, mọi dòng nhựa đều bằng số đó.
     *   một màu:   mỗi dòng là một lô riêng (1 cái đen, 1 cái trắng),
     *              nhập số cái từng dòng, soLuong = cộng các dòng.
     */
    @Column(name = "nhieu_mau", nullable = false,
            columnDefinition = "boolean default false not null")
    private Boolean nhieuMau = false;

    /**
     * TẤT CẢ ẢNH của sản phẩm, mỗi dòng một đường dẫn, ảnh đầu tiên là ảnh bìa.
     * Web khách dùng làm slide ở trang chi tiết. Cột hinh_anh vẫn giữ và luôn
     * bằng ảnh đầu tiên, để mấy chỗ chỉ cần một ảnh (thẻ sản phẩm, giỏ hàng)
     * khỏi phải đổi.
     */
    @Column(name = "danh_sach_anh", columnDefinition = "text")
    private String danhSachAnh;

    /*
     * Nhựa đã dùng nằm ở bảng nối san_pham_vat_tu — xem entity SanPhamVatTu.
     * Màu của sản phẩm suy từ màu của các cuộn đó, không lưu riêng.
     */

    // created_at / updated_at / is_deleted nằm ở lớp cha BanGhi

    // Getter / Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTen() { return ten; }
    public void setTen(String ten) { this.ten = ten; }
    public String getMaSanPham() { return maSanPham; }
    public void setMaSanPham(String maSanPham) { this.maSanPham = maSanPham; }

    /**
     * Mã đang HIỂN THỊ: mã đã lưu, dòng cũ chưa có mã thì "SP-<id>" — đúng mã web vẫn hiện
     * từ trước tới nay, và cũng là mã phần sinh mã / kiểm tra trùng coi là "đang dùng".
     */
    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getMaHienThi() {
        if (maSanPham != null && !maSanPham.isBlank()) return maSanPham;
        return id == null ? null : "SP-" + id;
    }

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
    public String getLoaiSanPham() { return loaiSanPham; }
    public void setLoaiSanPham(String loaiSanPham) { this.loaiSanPham = loaiSanPham; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
    /** Luôn ít nhất 1: in 0 cái thì không có sản phẩm để mà lưu. */
    public Integer getSoLuong() { return soLuong == null || soLuong < 1 ? 1 : soLuong; }
    public void setSoLuong(Integer soLuong) { this.soLuong = soLuong == null || soLuong < 1 ? 1 : soLuong; }
    public Boolean getNhieuMau() { return Boolean.TRUE.equals(nhieuMau); }
    public void setNhieuMau(Boolean nhieuMau) { this.nhieuMau = Boolean.TRUE.equals(nhieuMau); }

    /** Danh sách ảnh dạng list. Bản ghi cũ chưa có danh sách thì lấy ảnh bìa làm ảnh duy nhất. */
    @Transient
    public java.util.List<String> getDanhSachAnhList() {
        java.util.List<String> ds = new java.util.ArrayList<>();
        if (danhSachAnh != null) {
            for (String dong : danhSachAnh.split("\n")) {
                String u = dong.trim();
                if (!u.isEmpty() && !ds.contains(u)) ds.add(u);
            }
        }
        if (ds.isEmpty() && hinhAnh != null && !hinhAnh.isBlank()) ds.add(hinhAnh.trim());
        return ds;
    }

    /** Ghi danh sách ảnh và kéo ảnh bìa (hinh_anh) theo ảnh đầu tiên. */
    public void setDanhSachAnhList(java.util.List<String> ds) {
        java.util.List<String> sach = new java.util.ArrayList<>();
        if (ds != null) {
            for (String u : ds) {
                String t = u == null ? "" : u.trim();
                if (!t.isEmpty() && !sach.contains(t)) sach.add(t);
            }
        }
        this.danhSachAnh = sach.isEmpty() ? null : String.join("\n", sach);
        this.hinhAnh = sach.isEmpty() ? null : sach.get(0);
    }
}
