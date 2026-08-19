package vn.in3d.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;

/** Dữ liệu website bán hàng gửi lên khi khách bấm "Xác nhận đặt hàng". */
public record DatHangRequest(
        @NotBlank(message = "Vui lòng nhập họ và tên")
        String tenKhach,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        @Pattern(regexp = "^0\\d{8,10}$", message = "Số điện thoại không hợp lệ (bắt đầu bằng 0, 9-11 chữ số)")
        String soDienThoai,

        @NotBlank(message = "Vui lòng nhập địa chỉ nhận hàng")
        String diaChi,

        String ghiChu,

        /**
         * Mã khuyến mãi khách nhập ở giỏ hàng (có thể bỏ trống).
         * Backend TỰ TÍNH lại tiền giảm từ mã này, không nhận số tiền giảm
         * do trình duyệt gửi lên — sửa vài dòng JavaScript là mua được giá 0đ.
         */
        String maKhuyenMai,

        @NotEmpty(message = "Giỏ hàng đang trống")
        @Valid
        List<MatHang> matHang
) {
    /** Một món trong giỏ. */
    public record MatHang(
            @NotBlank(message = "Thiếu tên sản phẩm") String ten,
            Long donGia,
            @Positive(message = "Số lượng phải lớn hơn 0") Integer soLuong
    ) {}
}
