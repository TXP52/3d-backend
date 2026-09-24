package vn.in3d.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Đơn chủ shop GÕ TAY ở trang quản trị (POST /api/quan-tri/don-hang) — khách mua
 * qua Facebook / Zalo / tại shop, không tự đặt trên web.
 *
 * Khác đơn của khách (DatHangRequest):
 *   - có kênh bán, trạng thái ban đầu và cách thanh toán
 *   - mỗi dòng được phép GHI ĐÈ đơn giá (shop bớt cho khách quen); để trống thì lấy
 *     đúng giá web đang bán (cùng một đường tính giá với đơn khách tự đặt)
 *   - dòng không bắt buộc có tên: chọn sẵn phân loại là đủ, backend tự điền tên
 *
 * Số điện thoại KHÔNG bắt theo mẫu như bên web: khách Facebook nhiều khi chỉ cho
 * số kiểu "090 123 4567" hoặc số nước ngoài, chặn lại thì chủ shop không tạo được đơn.
 */
public record DonTayRequest(
        @NotBlank(message = "Vui lòng nhập họ và tên")
        String tenKhach,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        String soDienThoai,

        @NotBlank(message = "Vui lòng nhập địa chỉ")
        String diaChi,

        String ghiChu,

        /** Nối đơn vào một khách trong danh bạ (bảng nguoi_dung), để trống = khách lẻ. */
        Long nguoiDungId,

        /** facebook | zalo | shopee | tiktok | threads | website | tai_shop | khac — trống = website. */
        String kenh,

        /** Cộng thêm vào đơn ngoài tiền hàng (ship khách trả, gói quà...); để trống = 0. */
        Long phuThu,

        /** Trừ khỏi tổng: phí sàn, phí ship shop chịu...; để trống = 0. */
        Long phi,

        /** Trạng thái ban đầu, để trống = cho_xac_nhan. Tạo thẳng đơn da_huy thì KHÔNG trừ kho. */
        String trangThai,

        String maKhuyenMai,

        ThanhToanTay thanhToan,

        @NotEmpty(message = "Đơn phải có ít nhất một món")
        List<DatHangRequest.MatHang> matHang
) {
    /**
     * @param phuongThuc  cod | chuyen_khoan | vi_dien_tu | the (để trống = cod)
     * @param daThanhToan khách trả tiền rồi hay chưa
     */
    public record ThanhToanTay(String phuongThuc, Boolean daThanhToan) {}
}
