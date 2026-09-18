package vn.in3d.backend.dto;

import java.util.List;

/**
 * Giỏ hàng gửi lên để backend báo giá (POST /api/gio-hang/bao-gia).
 * Cùng dạng món với DatHangRequest nhưng KHÔNG bắt buộc gì: đang gõ dở địa chỉ,
 * chưa nhập tên vẫn phải xem được giá.
 */
public record BaoGiaRequest(
        List<DatHangRequest.MatHang> matHang,
        String maKhuyenMai,
        String diaChi,
        String soDienThoai
) {}
