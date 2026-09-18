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
    /**
     * Một món trong giỏ.
     * bienTheId: phân loại khách đã chọn — ƯU TIÊN nhất (giá và kho đều theo biến thể).
     * sanPhamId: có thì tra theo id (chắc chắn đúng món); không có hoặc món đã xoá
     * thì mới tra theo tên như bản cũ — giỏ cũ chưa biết biến thể vẫn đặt được,
     * backend tự lấy biến thể MẶC ĐỊNH của sản phẩm. donGia chỉ dùng cho món KHÔNG
     * có trong bảng sản phẩm (in theo yêu cầu...), món có trong bảng luôn lấy giá database.
     */
    public record MatHang(
            Long bienTheId,
            Long sanPhamId,
            @NotBlank(message = "Thiếu tên sản phẩm") String ten,
            Long donGia,
            @Positive(message = "Số lượng phải lớn hơn 0") Integer soLuong
    ) {}
}
