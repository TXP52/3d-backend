package vn.in3d.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Chương trình khuyến mãi. Có HAI KIỂU:
 *
 *   kieu_ap_dung = "don_hang"  Khách gõ MÃ ở giỏ hàng, giảm trên tổng đơn.
 *                              Mỗi đơn dùng được 1 mã.
 *
 *   kieu_ap_dung = "san_pham"  Giảm thẳng vào giá từng món trong danh sách
 *                              sanPhamIds. TỰ ĐỘNG, khách không phải gõ gì,
 *                              trang bán hàng hiện luôn giá gạch ngang.
 *
 * Hai kiểu CỘNG DỒN được: giá món giảm trước, mã đơn hàng giảm tiếp trên
 * số tiền còn lại.
 *
 * Việc tính tiền giảm LUÔN LÀM Ở BACKEND khi tạo đơn, không tin con số
 * trình duyệt gửi lên. Trang khách chỉ gọi /kiem-tra để hiện trước cho khách xem.
 */
@Entity
@Table(name = "khuyen_mai")
// UPDATE chỉ ghi cột thực sự đổi: sửa mã ở trang quản trị không ghi đè da_dung
// mà một đơn hàng vừa tăng trong lúc đó
@org.hibernate.annotations.DynamicUpdate
public class KhuyenMai extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mã khách gõ ở giỏ hàng, viết HOA không dấu: GIAM10, FREESHIP...
     * ĐỂ TRỐNG với khuyến mãi sản phẩm — loại đó tự áp, không có gì để gõ.
     */
    @Column(name = "ma", unique = true, length = 40)
    private String ma;

    @Column(name = "ten", nullable = false)
    private String ten;

    @Column(name = "mo_ta", columnDefinition = "text")
    private String moTa;

    /** don_hang (giảm theo đơn, cần mã) | san_pham (giảm giá món, tự áp) */
    @Column(name = "kieu_ap_dung", nullable = false,
            columnDefinition = "varchar(20) default 'don_hang' not null")
    private String kieuApDung = "don_hang";

    /**
     * Danh sách id sản phẩm được giảm, ngăn bằng dấu phẩy: "12,15,18".
     * Để trống = áp cho MỌI sản phẩm. Chỉ dùng khi kieuApDung = san_pham.
     */
    @Column(name = "san_pham_ids", columnDefinition = "text")
    private String sanPhamIds;

    /**
     * phan_tram — giảm theo % tổng đơn (giaTri = số phần trăm)
     * so_tien   — giảm thẳng số tiền   (giaTri = số đồng)
     * mien_ship — miễn phí vận chuyển  (giaTri = số tiền ship được miễn)
     */
    @Column(name = "loai", nullable = false,
            columnDefinition = "varchar(20) default 'phan_tram' not null")
    private String loai = "phan_tram";

    @Column(name = "gia_tri", nullable = false,
            columnDefinition = "bigint default 0 not null")
    private Long giaTri = 0L;

    /** Trần giảm cho loại phần trăm. 0 = không giới hạn. */
    @Column(name = "giam_toi_da", nullable = false,
            columnDefinition = "bigint default 0 not null")
    private Long giamToiDa = 0L;

    /** Đơn phải đạt mức này mới dùng được mã. 0 = không yêu cầu. */
    @Column(name = "don_toi_thieu", nullable = false,
            columnDefinition = "bigint default 0 not null")
    private Long donToiThieu = 0L;

    /**
     * Chỉ khách hàng MỚI dùng được — người chưa từng có đơn nào.
     * Đối chiếu theo tài khoản đăng nhập; chưa đăng nhập thì theo số điện thoại.
     */
    @Column(name = "chi_khach_moi", nullable = false,
            columnDefinition = "boolean default false not null")
    private Boolean chiKhachMoi = Boolean.FALSE;

    /**
     * Địa chỉ nhận hàng phải chứa MỘT trong các từ khoá này, ngăn bằng dấu phẩy.
     * So sánh bỏ dấu và không phân biệt hoa thường, nên "Cau Giay" khớp "Cầu Giấy".
     * Để trống = giao đâu cũng dùng được.
     * Ví dụ mã freeship nội thành Hà Nội: "Ba Đình,Hoàn Kiếm,Đống Đa,..."
     */
    @Column(name = "dieu_kien_dia_chi", columnDefinition = "text")
    private String dieuKienDiaChi;

    /** Ngày bắt đầu / kết thúc, tính cả hai đầu. Để trống = không giới hạn. */
    @Column(name = "bat_dau")
    private LocalDate batDau;

    @Column(name = "ket_thuc")
    private LocalDate ketThuc;

    /** Tổng số lượt dùng cho phép. 0 = không giới hạn. */
    @Column(name = "so_luong", nullable = false,
            columnDefinition = "integer default 0 not null")
    private Integer soLuong = 0;

    @Column(name = "da_dung", nullable = false,
            columnDefinition = "integer default 0 not null")
    private Integer daDung = 0;

    /** tat_ca | san_pham | dich_vu — phạm vi áp dụng, hiện chỉ để ghi chú cho chủ shop. */
    @Column(name = "ap_dung_cho", nullable = false,
            columnDefinition = "varchar(20) default 'tat_ca' not null")
    private String apDungCho = "tat_ca";

    /** Tắt để ngừng mã ngay mà không phải sửa ngày. */
    @Column(name = "hoat_dong", nullable = false,
            columnDefinition = "boolean default true not null")
    private Boolean hoatDong = Boolean.TRUE;

    /** Có khoe mã này ở trang chủ cho khách thấy không. */
    @Column(name = "hien_thi", nullable = false,
            columnDefinition = "boolean default true not null")
    private Boolean hienThi = Boolean.TRUE;

    // created_at / updated_at / is_deleted nằm ở lớp cha BanGhi

    /* ---------------- Nghiệp vụ ---------------- */

    /**
     * Trạng thái tính ra từ ngày tháng và số lượt, KHÔNG lưu trong database
     * để không bao giờ lệch với thực tế:
     * tam_dung | het_luot | sap_dien_ra | het_han | dang_chay
     */
    @Transient
    public String getTrangThai() {
        if (Boolean.FALSE.equals(hoatDong)) return "tam_dung";
        if (getSoLuong() > 0 && getDaDung() >= getSoLuong()) return "het_luot";
        LocalDate homNay = LocalDate.now();
        if (batDau != null && homNay.isBefore(batDau)) return "sap_dien_ra";
        if (ketThuc != null && homNay.isAfter(ketThuc)) return "het_han";
        return "dang_chay";
    }

    @Transient
    public boolean dangChay() {
        return "dang_chay".equals(getTrangThai());
    }

    @Transient
    public boolean laKhuyenMaiSanPham() {
        return "san_pham".equals(kieuApDung);
    }

    /**
     * Chương trình này có giảm cho sản phẩm id không?
     * Danh sách trống nghĩa là áp cho tất cả sản phẩm.
     */
    @Transient
    public boolean apDungChoSanPham(Long sanPhamId) {
        if (!laKhuyenMaiSanPham() || sanPhamId == null) return false;
        if (sanPhamIds == null || sanPhamIds.isBlank()) return true;
        for (String phan : sanPhamIds.split(",")) {
            String s = phan.trim();
            if (!s.isEmpty() && s.equals(String.valueOf(sanPhamId))) return true;
        }
        return false;
    }

    /** Số lượt còn lại; -1 nghĩa là không giới hạn. */
    @Transient
    public int getConLai() {
        return getSoLuong() > 0 ? Math.max(0, getSoLuong() - getDaDung()) : -1;
    }

    /**
     * Địa chỉ giao hàng có thoả điều kiện không.
     * So sánh sau khi bỏ dấu để khách gõ "cau giay" hay "Cầu Giấy" đều nhận.
     */
    @Transient
    public boolean hopDiaChi(String diaChi) {
        if (dieuKienDiaChi == null || dieuKienDiaChi.isBlank()) return true;
        if (diaChi == null || diaChi.isBlank()) return false;
        String dc = boDau(diaChi);
        for (String tu : dieuKienDiaChi.split(",")) {
            String t = boDau(tu);
            if (!t.isEmpty() && dc.contains(t)) return true;
        }
        return false;
    }

    /** "Cầu Giấy, Hà Nội" -> "cau giay, ha noi" */
    static String boDau(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase();
    }

    /**
     * Tính tiền giảm cho một đơn có tạm tính = tongTien.
     * Không kiểm tra điều kiện ở đây — nơi gọi phải kiểm tra trước.
     */
    public long tinhTienGiam(long tongTien) {
        long giam;
        if ("phan_tram".equals(loai)) {
            giam = Math.round(tongTien * (getGiaTri() / 100.0));
            if (getGiamToiDa() > 0) giam = Math.min(giam, getGiamToiDa());
        } else {
            // so_tien và mien_ship đều là số tiền trừ thẳng
            giam = getGiaTri();
        }
        return Math.max(0, Math.min(giam, tongTien));   // không giảm quá tiền hàng
    }

    // Getter / Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMa() { return ma; }
    public void setMa(String ma) { this.ma = ma; }
    public String getTen() { return ten; }
    public void setTen(String ten) { this.ten = ten; }
    public String getMoTa() { return moTa; }
    public void setMoTa(String moTa) { this.moTa = moTa; }
    public String getKieuApDung() { return kieuApDung; }
    public void setKieuApDung(String kieuApDung) { this.kieuApDung = kieuApDung; }
    public String getSanPhamIds() { return sanPhamIds; }
    public void setSanPhamIds(String sanPhamIds) { this.sanPhamIds = sanPhamIds; }
    public String getLoai() { return loai; }
    public void setLoai(String loai) { this.loai = loai; }
    public Long getGiaTri() { return giaTri == null ? 0L : giaTri; }
    public void setGiaTri(Long giaTri) { this.giaTri = giaTri == null ? 0L : giaTri; }
    public Long getGiamToiDa() { return giamToiDa == null ? 0L : giamToiDa; }
    public void setGiamToiDa(Long giamToiDa) { this.giamToiDa = giamToiDa == null ? 0L : giamToiDa; }
    public Long getDonToiThieu() { return donToiThieu == null ? 0L : donToiThieu; }
    public void setDonToiThieu(Long donToiThieu) { this.donToiThieu = donToiThieu == null ? 0L : donToiThieu; }
    public Boolean getChiKhachMoi() { return chiKhachMoi != null && chiKhachMoi; }
    public void setChiKhachMoi(Boolean v) { this.chiKhachMoi = v != null && v; }
    public String getDieuKienDiaChi() { return dieuKienDiaChi; }
    public void setDieuKienDiaChi(String v) { this.dieuKienDiaChi = v; }
    public LocalDate getBatDau() { return batDau; }
    public void setBatDau(LocalDate batDau) { this.batDau = batDau; }
    public LocalDate getKetThuc() { return ketThuc; }
    public void setKetThuc(LocalDate ketThuc) { this.ketThuc = ketThuc; }
    public Integer getSoLuong() { return soLuong == null ? 0 : soLuong; }
    public void setSoLuong(Integer soLuong) { this.soLuong = soLuong == null ? 0 : soLuong; }
    public Integer getDaDung() { return daDung == null ? 0 : daDung; }
    public void setDaDung(Integer daDung) { this.daDung = daDung == null ? 0 : daDung; }
    public String getApDungCho() { return apDungCho; }
    public void setApDungCho(String apDungCho) { this.apDungCho = apDungCho; }
    public Boolean getHoatDong() { return hoatDong; }
    public void setHoatDong(Boolean hoatDong) { this.hoatDong = hoatDong; }
    public Boolean getHienThi() { return hienThi; }
    public void setHienThi(Boolean hienThi) { this.hienThi = hienThi; }
}
