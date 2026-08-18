package vn.in3d.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.OffsetDateTime;

/**
 * Cột chung cho MỌI bảng: ngày tạo, ngày sửa gần nhất và cờ đã xoá.
 *
 * Toàn hệ thống dùng XOÁ MỀM: khi xoá chỉ bật is_deleted = true, dữ liệu vẫn nằm
 * nguyên trong database. Nhờ vậy lỡ tay xoá vẫn khôi phục được, và đơn hàng cũ
 * vẫn tra ngược được sản phẩm dù sản phẩm đã ngừng bán.
 */
@MappedSuperclass
public abstract class BanGhi {

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** true = đã xoá (ẩn khỏi mọi danh sách), false = đang dùng */
    @Column(name = "is_deleted", nullable = false,
            columnDefinition = "boolean default false not null")
    private Boolean daXoa = Boolean.FALSE;

    @PrePersist
    void truocKhiTao() {
        OffsetDateTime bayGio = OffsetDateTime.now();
        if (createdAt == null) createdAt = bayGio;
        updatedAt = bayGio;
        if (daXoa == null) daXoa = Boolean.FALSE;
    }

    @PreUpdate
    void truocKhiSua() {
        updatedAt = OffsetDateTime.now();
    }

    /** Đánh dấu đã xoá thay vì xoá hẳn khỏi database. */
    public void xoaMem() {
        this.daXoa = Boolean.TRUE;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Khôi phục bản ghi đã xoá. */
    public void khoiPhuc() {
        this.daXoa = Boolean.FALSE;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getDaXoa() { return daXoa != null && daXoa; }
    public void setDaXoa(Boolean daXoa) { this.daXoa = daXoa != null && daXoa; }
}
