package vn.in3d.backend.entity;

import jakarta.persistence.*;

/**
 * Vật tư trong kho: máy in, cuộn nhựa, phụ kiện...
 * Cuộn nhựa có khoiLuongGram (thường 1000g) và daDungGram để tính tiền nhựa đã dùng.
 */
@Entity
@Table(name = "vat_tu")
public class VatTu extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ten;

    /** may_in | nhua | phu_kien | khac */
    @Column(nullable = false)
    private String loai = "khac";

    /** Tên màu hiển thị (giữ lại để dữ liệu cũ không mất) */
    private String mau;

    /** Trỏ sang bảng mau_sac — nguồn màu chuẩn, có kèm mã màu để vẽ ô màu */
    @Column(name = "mau_sac_id")
    private Long mauSacId;

    /** Giá mua 1 đơn vị (VNĐ) */
    @Column(nullable = false)
    private Long gia = 0L;

    /** Số lượng đang có trong kho */
    @Column(name = "so_luong", nullable = false)
    private Integer soLuong = 1;

    /** Khối lượng 1 đơn vị (gram) — cuộn nhựa thường 1000g; máy in để 0 */
    @Column(name = "khoi_luong_gram", nullable = false)
    private Integer khoiLuongGram = 0;

    /** Đã dùng bao nhiêu gram (chỉ áp dụng cho nhựa) */
    @Column(name = "da_dung_gram", nullable = false)
    private Integer daDungGram = 0;

    /**
     * Trạng thái vật tư: con_hang | sap_het | het_hang | da_dat | dang_van_chuyen
     * (da_dat / dang_van_chuyen = đã mua nhưng hàng chưa về tới kho)
     */
    @Column(name = "trang_thai", nullable = false,
            columnDefinition = "varchar(40) default 'con_hang' not null")
    private String trangThai = "con_hang";

    /** Ảnh vật tư (URL do API /api/anh trả về hoặc link ngoài) */
    @Column(name = "hinh_anh")
    private String hinhAnh;

    @Column(name = "nha_cung_cap_id")
    private Long nhaCungCapId;

    /**
     * Loại vật tư — trỏ sang danh_muc (nhom = vat_tu). Cột loai được chép từ
     * tinh_chat của loại đó nên mọi chỗ tính toán cũ vẫn chạy với loai.
     */
    @Column(name = "danh_muc_id")
    private Long danhMucId;

    @Column(name = "ghi_chu", columnDefinition = "text")
    private String ghiChu;

    /** Tổng tiền đã bỏ ra mua vật tư này = giá × số lượng. */
    @Transient
    public long getTongTienMua() {
        return (gia == null ? 0 : gia) * (soLuong == null ? 0 : soLuong);
    }

    /** Đơn giá mỗi gram (VNĐ/g) — chỉ có nghĩa với nhựa. */
    @Transient
    public double getDonGiaMoiGram() {
        if (khoiLuongGram == null || khoiLuongGram <= 0) return 0;
        return (double) (gia == null ? 0 : gia) / khoiLuongGram;
    }

    /** Tiền nhựa đã tiêu hao = đơn giá/gram × số gram đã dùng. */
    @Transient
    public long getTienDaDung() {
        return Math.round(getDonGiaMoiGram() * (daDungGram == null ? 0 : daDungGram));
    }

    /** Số gram còn lại trong kho (tổng khối lượng tất cả đơn vị trừ phần đã dùng). */
    @Transient
    public int getConLaiGram() {
        int tong = (khoiLuongGram == null ? 0 : khoiLuongGram) * (soLuong == null ? 0 : soLuong);
        return Math.max(0, tong - (daDungGram == null ? 0 : daDungGram));
    }

    /** Cuộn nhựa còn từ 200g trở xuống được coi là SẮP HẾT. */
    public static final int NGUONG_NHUA_SAP_HET = 200;

    /**
     * Trạng thái THỰC TẾ để hiển thị.
     *
     * Với nhựa, còn hàng / sắp hết / hết hàng suy thẳng từ số gram còn lại — bắt
     * chủ shop sửa tay sau mỗi lần in thì bảng kho chỉ đúng được vài hôm.
     * Hàng chưa về kho (đã đặt, đang vận chuyển) và hàng tự đánh dấu hết
     * thì giữ nguyên, không suy diễn đè lên.
     */
    @Transient
    public String getTrangThaiTinh() {
        String tt = trangThai == null || trangThai.isBlank() ? "con_hang" : trangThai;
        if ("da_dat".equals(tt) || "dang_van_chuyen".equals(tt) || "het_hang".equals(tt)) return tt;
        if (!"nhua".equals(loai)) return tt;
        int con = getConLaiGram();
        if (con <= 0) return "het_hang";
        if (con <= NGUONG_NHUA_SAP_HET) return "sap_het";
        return "con_hang";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTen() { return ten; }
    public void setTen(String ten) { this.ten = ten; }
    public String getLoai() { return loai; }
    public void setLoai(String loai) { this.loai = loai; }
    public String getMau() { return mau; }
    public void setMau(String mau) { this.mau = mau; }
    public Long getMauSacId() { return mauSacId; }
    public void setMauSacId(Long mauSacId) { this.mauSacId = mauSacId; }
    public Long getGia() { return gia; }
    public void setGia(Long gia) { this.gia = gia; }
    public Integer getSoLuong() { return soLuong; }
    public void setSoLuong(Integer soLuong) { this.soLuong = soLuong; }
    public Integer getKhoiLuongGram() { return khoiLuongGram; }
    public void setKhoiLuongGram(Integer khoiLuongGram) { this.khoiLuongGram = khoiLuongGram; }
    public Integer getDaDungGram() { return daDungGram; }
    public void setDaDungGram(Integer daDungGram) { this.daDungGram = daDungGram; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
    public String getHinhAnh() { return hinhAnh; }
    public void setHinhAnh(String hinhAnh) { this.hinhAnh = hinhAnh; }
    public Long getNhaCungCapId() { return nhaCungCapId; }
    public void setNhaCungCapId(Long nhaCungCapId) { this.nhaCungCapId = nhaCungCapId; }
    public Long getDanhMucId() { return danhMucId; }
    public void setDanhMucId(Long danhMucId) { this.danhMucId = danhMucId; }
    public String getGhiChu() { return ghiChu; }
    public void setGhiChu(String ghiChu) { this.ghiChu = ghiChu; }
}
