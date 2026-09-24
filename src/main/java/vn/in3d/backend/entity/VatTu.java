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

    /** may_in | nhua | dung_cu */
    @Column(nullable = false)
    private String loai = "dung_cu";

    /**
     * Màu CHỈ nằm ở bảng mau_sac. Trước đây còn một cột "mau" chép tên màu ra
     * đây (thời chưa có bảng màu) — đã bỏ vì trùng dữ liệu, sửa tên màu trong
     * bảng màu mà cột chép này không đổi theo thì hai nơi lệch nhau.
     * API vẫn trả trường "mau" nhưng lấy từ mau_sac.ten qua mauSacId.
     */
    @Column(name = "mau_sac_id")
    private Long mauSacId;

    /**
     * Giá mua 1 đơn vị (VNĐ) — BÌNH QUÂN các đợt nhập (bảng lo_nhap), đã làm tròn.
     * Backend ghi lại cột này mỗi lần đợt nhập thay đổi; chỉ để hiển thị và so sánh,
     * tiền thật luôn lấy từ tienMua để không cộng dồn sai số làm tròn.
     */
    @Column(nullable = false)
    private Long gia = 0L;

    /** Số lượng đang có trong kho = tổng số lượng các đợt nhập. */
    @Column(name = "so_luong", nullable = false)
    private Integer soLuong = 1;

    /**
     * Tổng tiền đã bỏ ra mua vật tư này = Σ (đơn giá × số lượng) của từng đợt nhập.
     * null = dòng cũ chưa có đợt nhập nào (backend tự suy gia × so_luong như trước).
     */
    @Column(name = "tien_mua")
    private Long tienMua;

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

    /**
     * Tổng tiền đã bỏ ra mua vật tư này: cộng từ các đợt nhập (tien_mua).
     * Dòng cũ chưa có đợt nhập nào thì vẫn là giá × số lượng như trước.
     */
    @Transient
    public long getTongTienMua() {
        if (tienMua != null) return tienMua;
        return (gia == null ? 0 : gia) * (soLuong == null ? 0 : soLuong);
    }

    /**
     * Đơn giá mỗi gram (VNĐ/g) — chỉ có nghĩa với nhựa.
     * Tính từ TỔNG tiền / TỔNG gram đã mua: nhiều đợt khác giá thì ra đúng giá bình
     * quân, một đợt thì đúng bằng giá cuộn / gram mỗi cuộn như trước.
     */
    @Transient
    public double getDonGiaMoiGram() {
        if (khoiLuongGram == null || khoiLuongGram <= 0) return 0;
        int sl = soLuong == null ? 0 : soLuong;
        if (sl <= 0) return (double) (gia == null ? 0 : gia) / khoiLuongGram;
        return (double) getTongTienMua() / ((long) khoiLuongGram * sl);
    }

    /**
     * ĐƠN VỊ ĐỂ ĐẾM "đã dùng / còn lại".
     *
     * Nhựa đo bằng GRAM (một cuộn 1000g dùng dần), còn máy in và dụng cụ đếm
     * bằng CÁI — 50 cái móc khoá dùng 3 cái thì còn 47, nói "còn 997g móc khoá"
     * thì vô nghĩa. Cột da_dung_gram dùng chung cho cả hai, đơn vị tuỳ tính chất.
     */
    @Transient
    public boolean isLaNhua() { return "nhua".equals(loai); }

    @Transient
    public String getDonVi() { return isLaNhua() ? "g" : "cái"; }

    /** Tổng sức chứa: nhựa là gram của tất cả cuộn, thứ khác là số cái đã mua. */
    @Transient
    public int getTongCoThe() {
        int sl = soLuong == null ? 0 : soLuong;
        return isLaNhua() ? (khoiLuongGram == null ? 0 : khoiLuongGram) * sl : sl;
    }

    /** Còn lại theo đúng đơn vị của nó. */
    @Transient
    public int getConLai() {
        return Math.max(0, getTongCoThe() - (daDungGram == null ? 0 : daDungGram));
    }

    /**
     * Tiền đã tiêu hao: nhựa tính theo gram, thứ khác theo số cái đã dùng.
     * Cả hai đều dùng giá BÌNH QUÂN các đợt nhập (không biết cái đang dùng thuộc đợt nào).
     */
    @Transient
    public long getTienDaDung() {
        int daDung = daDungGram == null ? 0 : daDungGram;
        if (isLaNhua()) return Math.round(getDonGiaMoiGram() * daDung);
        int sl = soLuong == null ? 0 : soLuong;
        if (sl > 0) return Math.round((double) getTongTienMua() / sl * daDung);
        return (gia == null ? 0 : gia) * daDung;
    }

    /** Giữ tên cũ cho chỗ nào chỉ làm việc với nhựa (ô chọn cuộn ở trang Sản phẩm). */
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
        int con = getConLai();
        if (con <= 0 && getTongCoThe() > 0) return "het_hang";
        // Ngưỡng "sắp hết" chỉ áp cho nhựa: 200g. Dụng cụ đếm theo cái, hết là hết.
        if (isLaNhua() && con <= NGUONG_NHUA_SAP_HET) return "sap_het";
        return "con_hang";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTen() { return ten; }
    public void setTen(String ten) { this.ten = ten; }
    public String getLoai() { return loai; }
    public void setLoai(String loai) { this.loai = loai; }
    public Long getMauSacId() { return mauSacId; }
    public void setMauSacId(Long mauSacId) { this.mauSacId = mauSacId; }
    public Long getGia() { return gia; }
    public void setGia(Long gia) { this.gia = gia; }
    public Integer getSoLuong() { return soLuong; }
    public void setSoLuong(Integer soLuong) { this.soLuong = soLuong; }
    public Long getTienMua() { return tienMua; }
    public void setTienMua(Long tienMua) { this.tienMua = tienMua; }
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
