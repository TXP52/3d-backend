package vn.in3d.backend.entity;

import jakarta.persistence.*;

/**
 * Bài viết chia sẻ kiến thức in 3D hiện ở cuối trang chủ.
 * Trước đây chỗ này là danh sách máy in / vật liệu / phụ kiện viết cứng trong HTML,
 * bán không ai mua mà sửa thì phải sửa tay từng thẻ.
 *
 * NỘI DUNG LÀ VĂN BẢN THƯỜNG, KHÔNG PHẢI HTML:
 *   - Dòng bắt đầu bằng "## " là tiêu đề mục nhỏ
 *   - Dòng bắt đầu bằng "- " là gạch đầu dòng
 *   - Dòng trống ngăn hai đoạn
 * Trang khách tự escape rồi mới dựng thẻ, nên người viết bài không thể chèn
 * mã độc vào trang (chống XSS mà vẫn dễ gõ).
 */
@Entity
@Table(name = "bai_viet")
public class BaiViet extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tieu_de", nullable = false)
    private String tieuDe;

    /** Đường dẫn thân thiện, ví dụ "chon-nhua-pla-petg-abs" — dùng để mở bài. */
    @Column(name = "duong_dan", unique = true)
    private String duongDan;

    /** Tóm tắt 1-2 câu hiện ở thẻ ngoài trang chủ. */
    @Column(name = "tom_tat", columnDefinition = "text")
    private String tomTat;

    @Column(name = "noi_dung", columnDefinition = "text")
    private String noiDung;

    @Column(name = "hinh_anh")
    private String hinhAnh;

    @Column(name = "tac_gia")
    private String tacGia;

    /** Nhóm bài: huong-dan | vat-lieu | kinh-nghiem | tin-shop */
    @Column(name = "chuyen_muc", nullable = false,
            columnDefinition = "varchar(40) default 'huong-dan' not null")
    private String chuyenMuc = "huong-dan";

    @Column(name = "luot_xem", nullable = false,
            columnDefinition = "integer default 0 not null")
    private Integer luotXem = 0;

    /** Tắt để giấu bài khỏi trang khách mà vẫn giữ trong database. */
    @Column(name = "hien_thi", nullable = false,
            columnDefinition = "boolean default true not null")
    private Boolean hienThi = Boolean.TRUE;

    /** Số nhỏ lên trước; bằng nhau thì bài mới lên trước. */
    @Column(name = "thu_tu", nullable = false,
            columnDefinition = "integer default 0 not null")
    private Integer thuTu = 0;

    // created_at / updated_at / is_deleted nằm ở lớp cha BanGhi

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTieuDe() { return tieuDe; }
    public void setTieuDe(String tieuDe) { this.tieuDe = tieuDe; }
    public String getDuongDan() { return duongDan; }
    public void setDuongDan(String duongDan) { this.duongDan = duongDan; }
    public String getTomTat() { return tomTat; }
    public void setTomTat(String tomTat) { this.tomTat = tomTat; }
    public String getNoiDung() { return noiDung; }
    public void setNoiDung(String noiDung) { this.noiDung = noiDung; }
    public String getHinhAnh() { return hinhAnh; }
    public void setHinhAnh(String hinhAnh) { this.hinhAnh = hinhAnh; }
    public String getTacGia() { return tacGia; }
    public void setTacGia(String tacGia) { this.tacGia = tacGia; }
    public String getChuyenMuc() { return chuyenMuc; }
    public void setChuyenMuc(String chuyenMuc) { this.chuyenMuc = chuyenMuc; }
    public Integer getLuotXem() { return luotXem == null ? 0 : luotXem; }
    public void setLuotXem(Integer luotXem) { this.luotXem = luotXem == null ? 0 : luotXem; }
    public Boolean getHienThi() { return hienThi; }
    public void setHienThi(Boolean hienThi) { this.hienThi = hienThi; }
    public Integer getThuTu() { return thuTu == null ? 0 : thuTu; }
    public void setThuTu(Integer thuTu) { this.thuTu = thuTu == null ? 0 : thuTu; }
}
