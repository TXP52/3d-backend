package vn.in3d.backend.entity;

import jakarta.persistence.*;

/**
 * MỘT LẦN IN BẰNG MỘT CUỘN NHỰA — bảng nối public.san_pham_vat_tu.
 *
 * Mỗi dòng đọc là: "in soLuong cái, mỗi cái ăn gramNhua + gramThua của cuộn vatTuId".
 * Nên in 1 cái đen và 1 cái trắng cùng mẫu = hai dòng, mỗi dòng số lượng 1.
 *
 * Màu của sản phẩm KHÔNG lưu riêng — nó chính là màu của những cuộn nhựa
 * trong danh sách này, nên hai bên không bao giờ lệch nhau.
 *
 * gramNhua = nhựa nằm trong thành phẩm, gramThua = support / brim / bản hỏng.
 * Kho bị trừ (gramNhua + gramThua) × soLuong của đúng cuộn đó; SanPhamController
 * chỉ trừ/hoàn phần chênh lệch mỗi lần lưu nên không bao giờ trừ trùng.
 */
@Entity
@Table(name = "san_pham_vat_tu")
public class SanPhamVatTu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "san_pham_id", nullable = false)
    private Long sanPhamId;

    @Column(name = "vat_tu_id", nullable = false)
    private Long vatTuId;

    @Column(name = "gram_nhua", nullable = false)
    private Integer gramNhua = 0;

    @Column(name = "gram_thua", nullable = false)
    private Integer gramThua = 0;

    /** In mấy cái bằng cuộn này. */
    @Column(name = "so_luong", nullable = false)
    private Integer soLuong = 1;

    /** Nhựa cho MỘT cái. */
    @Transient
    public int getGramMoiCai() {
        return getGramNhua() + getGramThua();
    }

    /** Tổng nhựa dòng này đã trừ khỏi cuộn = gram mỗi cái × số lượng. */
    @Transient
    public int getTongGram() {
        return getGramMoiCai() * getSoLuong();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSanPhamId() { return sanPhamId; }
    public void setSanPhamId(Long sanPhamId) { this.sanPhamId = sanPhamId; }
    public Long getVatTuId() { return vatTuId; }
    public void setVatTuId(Long vatTuId) { this.vatTuId = vatTuId; }
    public Integer getGramNhua() { return gramNhua == null ? 0 : gramNhua; }
    public void setGramNhua(Integer gramNhua) { this.gramNhua = gramNhua == null ? 0 : gramNhua; }
    public Integer getGramThua() { return gramThua == null ? 0 : gramThua; }
    public void setGramThua(Integer gramThua) { this.gramThua = gramThua == null ? 0 : gramThua; }
    /** Luôn ít nhất 1: in 0 cái thì đâu có dòng nào để lưu. */
    public Integer getSoLuong() { return soLuong == null || soLuong < 1 ? 1 : soLuong; }
    public void setSoLuong(Integer soLuong) { this.soLuong = soLuong == null || soLuong < 1 ? 1 : soLuong; }
}
