package vn.in3d.backend.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Đơn hàng — ánh xạ bảng public.don_hang (trùng schema Supabase). */
@Entity
@Table(name = "don_hang")
public class DonHang extends BanGhi {

    /** Các trạng thái đơn hợp lệ (trùng ràng buộc CHECK trong schema.sql). */
    public static final List<String> TRANG_THAI_HOP_LE =
            List.of("cho_xac_nhan", "dang_xu_ly", "dang_giao", "hoan_thanh", "da_huy");

    /** Đơn đang HUỶ thì không giữ hàng: kho đã được trả lại (xem DonHangService). */
    public static final String DA_HUY = "da_huy";

    /**
     * Kênh bán hợp lệ và tên hiển thị — đơn từ web luôn là website.
     * LinkedHashMap chứ không Map.of: Map.of đảo thứ tự khoá mỗi lần chạy lại máy ảo Java,
     * mà thứ tự này còn đi vào câu báo lỗi "Hợp lệ: ...".
     */
    public static final Map<String, String> KENH_HOP_LE;
    static {
        Map<String, String> kenh = new LinkedHashMap<>();
        kenh.put("website", "Website");
        kenh.put("facebook", "Facebook");
        kenh.put("zalo", "Zalo");
        kenh.put("tai_shop", "Tại shop");
        kenh.put("khac", "Khác");
        KENH_HOP_LE = Collections.unmodifiableMap(kenh);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ma_don", nullable = false, unique = true)
    private String maDon;

    /**
     * Tài khoản đã đặt đơn này, trỏ sang nguoi_dung.id.
     * Cột cũ user_id (uuid trỏ auth.users của Supabase Auth) đã bỏ —
     * tài khoản do backend Java quản lý ở bảng nguoi_dung, id kiểu số.
     * Để trống nghĩa là khách đặt mà không đăng nhập.
     */
    @Column(name = "nguoi_dung_id")
    private Long nguoiDungId;

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

    /** Tiền giảm do MÃ khuyến mãi đơn hàng. */
    @Column(name = "tien_giam", nullable = false,
            columnDefinition = "bigint default 0 not null")
    private Long tienGiam = 0L;

    /** Tiền giảm do khuyến mãi SẢN PHẨM (tự áp vào giá món, không cần mã). */
    @Column(name = "tien_giam_san_pham", nullable = false,
            columnDefinition = "bigint default 0 not null")
    private Long tienGiamSanPham = 0L;

    @Column(name = "trang_thai", nullable = false)
    private String trangThai = "cho_xac_nhan";

    /**
     * Bán qua đâu: website | facebook | zalo | tai_shop | khac.
     * Đơn khách tự đặt trên web luôn là website; đơn chủ shop gõ tay thì chọn kênh.
     */
    @Column(name = "kenh", nullable = false, length = 20)
    private String kenh = "website";

    // Xếp theo id để danh sách món / thanh toán luôn cùng thứ tự dù nạp kiểu nào
    @OneToMany(mappedBy = "donHang", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<DonHangChiTiet> chiTiet = new ArrayList<>();

    @OneToMany(mappedBy = "donHang", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
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
    public Long getNguoiDungId() { return nguoiDungId; }
    public void setNguoiDungId(Long nguoiDungId) { this.nguoiDungId = nguoiDungId; }
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
    public Long getTienGiamSanPham() { return tienGiamSanPham == null ? 0L : tienGiamSanPham; }
    public void setTienGiamSanPham(Long v) { this.tienGiamSanPham = v == null ? 0L : v; }

    /** Tiền hàng sau giảm giá món, trước khi trừ mã đơn hàng. */
    @Transient
    public Long getTamTinh() { return getTongTien() + getTienGiam(); }

    /** Tiền hàng theo giá niêm yết, chưa trừ khoản nào. */
    @Transient
    public Long getTienHangGoc() { return getTamTinh() + getTienGiamSanPham(); }

    /** Tổng tất cả các khoản đã giảm cho đơn này. */
    @Transient
    public Long getTongGiam() { return getTienGiam() + getTienGiamSanPham(); }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
    public String getKenh() { return kenh == null || kenh.isBlank() ? "website" : kenh; }
    public void setKenh(String kenh) { this.kenh = kenh == null || kenh.isBlank() ? "website" : kenh; }

    /** Tên kênh cho trang quản trị khỏi phải tự dịch; kênh lạ thì trả nguyên chuỗi. */
    @Transient
    public String getKenhTen() { return KENH_HOP_LE.getOrDefault(getKenh(), getKenh()); }
    public List<DonHangChiTiet> getChiTiet() { return chiTiet; }
    public List<ThanhToan> getThanhToan() { return thanhToan; }
}
