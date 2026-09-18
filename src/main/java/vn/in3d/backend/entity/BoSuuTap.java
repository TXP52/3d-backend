package vn.in3d.backend.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * BỘ SƯU TẬP (chủ đề) — ánh xạ bảng public.bo_suu_tap.
 *
 * Khác DANH MỤC: mỗi sản phẩm chỉ thuộc MỘT danh mục (nó là cái gì), còn bộ sưu tập
 * là chủ đề chủ shop tự gom ("Hollow Knight", "Quà Tết") nên một sản phẩm nằm được
 * nhiều bộ — bảng nối san_pham_bo_suu_tap.
 *
 * Đường dẫn (duong_dan) là địa chỉ web của bộ, chỉ cần duy nhất trong các bộ CHƯA xoá
 * (database có chỉ mục bo_suu_tap_duong_dan_uq), nên xoá bộ rồi thì tên cũ dùng lại được.
 */
@Entity
@Table(name = "bo_suu_tap")
public class BoSuuTap extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ten;

    @Column(name = "duong_dan", nullable = false)
    private String duongDan;

    @Column(name = "mo_ta", columnDefinition = "text")
    private String moTa;

    /** Ảnh bìa của bộ (banner ở trang chủ web khách). */
    @Column(name = "hinh_anh")
    private String hinhAnh;

    @Column(name = "hien_thi", nullable = false)
    private Boolean hienThi = true;

    @Column(name = "thu_tu", nullable = false)
    private Integer thuTu = 0;

    /**
     * Sản phẩm của bộ, theo thứ tự chủ shop xếp — CHỈ DÙNG ĐỂ ĐỌC (bộ nhớ đệm nạp
     * kèm bằng left join fetch, một lượt đi-về cho cả bảng).
     *
     * Lúc GHI thì BoSuuTapController tự xoá rồi chèn lại bảng nối bằng SQL (giống
     * SanPhamController): sửa qua danh sách này thì Hibernate phải nạp bộ cũ lên
     * trước, tốn thêm một lượt đi-về mà kết quả y hệt.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "san_pham_bo_suu_tap", joinColumns = @JoinColumn(name = "bo_suu_tap_id"))
    @OrderBy("thuTu asc, sanPhamId asc")
    private List<DongSanPham> sanPham = new ArrayList<>();

    /** Một dòng của bảng nối: sản phẩm nào, đứng thứ mấy trong bộ. */
    @Embeddable
    public static class DongSanPham {

        @Column(name = "san_pham_id", nullable = false)
        private Long sanPhamId;

        @Column(name = "thu_tu", nullable = false)
        private Integer thuTu = 0;

        public DongSanPham() {}

        public DongSanPham(Long sanPhamId, Integer thuTu) {
            this.sanPhamId = sanPhamId;
            this.thuTu = thuTu;
        }

        public Long getSanPhamId() { return sanPhamId; }
        public void setSanPhamId(Long sanPhamId) { this.sanPhamId = sanPhamId; }
        public Integer getThuTu() { return thuTu == null ? 0 : thuTu; }
        public void setThuTu(Integer thuTu) { this.thuTu = thuTu == null ? 0 : thuTu; }
    }

    // Getter / Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTen() { return ten; }
    public void setTen(String ten) { this.ten = ten; }
    public String getDuongDan() { return duongDan; }
    public void setDuongDan(String duongDan) { this.duongDan = duongDan; }
    public String getMoTa() { return moTa; }
    public void setMoTa(String moTa) { this.moTa = moTa; }
    public String getHinhAnh() { return hinhAnh; }
    public void setHinhAnh(String hinhAnh) { this.hinhAnh = hinhAnh; }
    public Boolean getHienThi() { return hienThi == null || hienThi; }
    public void setHienThi(Boolean hienThi) { this.hienThi = hienThi == null || hienThi; }
    public Integer getThuTu() { return thuTu == null ? 0 : thuTu; }
    public void setThuTu(Integer thuTu) { this.thuTu = thuTu == null ? 0 : thuTu; }
    public List<DongSanPham> getSanPham() { return sanPham; }
}
