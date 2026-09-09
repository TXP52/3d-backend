package vn.in3d.backend.entity;

import jakarta.persistence.*;

/**
 * Danh mục — một bảng, hai nhóm (cột nhom):
 *   san_pham : danh mục sản phẩm (móc khoá, mô hình, đồ trang trí...) — san_pham.danh_muc_id trỏ tới
 *   vat_tu   : loại vật tư trong kho (Máy in, Nhựa in, Phụ kiện...) — vat_tu.danh_muc_id trỏ tới
 *
 * Với nhóm vat_tu, tinh_chat (may_in | nhua | phu_kien | khac) là thứ backend thật sự
 * dựa vào: nhựa thì theo dõi gram và báo sắp hết, máy in thì tính vào vốn máy.
 * Tên loại chủ shop đặt tuỳ ý ("Nhựa PLA", "Nhựa PETG"...), tính chất vẫn đúng.
 */
@Entity
@Table(name = "danh_muc")
public class DanhMuc extends BanGhi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ten;

    @Column(name = "mo_ta", columnDefinition = "text")
    private String moTa;

    /** Tên icon Font Awesome hiện kèm danh mục, ví dụ fa-key. */
    @Column(name = "icon", length = 60)
    private String icon = "fa-folder";

    /** Thứ tự hiển thị (số nhỏ lên trước). */
    @Column(name = "thu_tu", nullable = false)
    private Integer thuTu = 0;

    /** Tắt đi thì danh mục không hiện cho khách chọn nữa nhưng sản phẩm cũ vẫn giữ. */
    @Column(name = "dang_hien", nullable = false,
            columnDefinition = "boolean default true not null")
    private Boolean dangHien = Boolean.TRUE;

    /** Dùng cho: san_pham (danh mục sản phẩm) | vat_tu (loại vật tư trong kho). */
    @Column(name = "nhom", nullable = false,
            columnDefinition = "varchar(20) default 'san_pham' not null")
    private String nhom = "san_pham";

    /** Chỉ với nhom = vat_tu: may_in | nhua | phu_kien | khac. */
    @Column(name = "tinh_chat", length = 20)
    private String tinhChat;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTen() { return ten; }
    public void setTen(String ten) { this.ten = ten; }
    public String getMoTa() { return moTa; }
    public void setMoTa(String moTa) { this.moTa = moTa; }
    public String getIcon() { return icon == null || icon.isBlank() ? "fa-folder" : icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getThuTu() { return thuTu == null ? 0 : thuTu; }
    public void setThuTu(Integer thuTu) { this.thuTu = thuTu == null ? 0 : thuTu; }
    public Boolean getDangHien() { return dangHien == null || dangHien; }
    public void setDangHien(Boolean dangHien) { this.dangHien = dangHien == null || dangHien; }
    public String getNhom() { return nhom == null || nhom.isBlank() ? "san_pham" : nhom; }
    public void setNhom(String nhom) { this.nhom = nhom == null || nhom.isBlank() ? "san_pham" : nhom; }
    public String getTinhChat() { return tinhChat; }
    public void setTinhChat(String tinhChat) { this.tinhChat = tinhChat; }
}
