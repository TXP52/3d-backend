package vn.in3d.backend.web;

import org.springframework.web.bind.annotation.*;
import vn.in3d.backend.dto.BaoGiaRequest;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.service.BoNhoDem;
import vn.in3d.backend.service.DonHangService;
import vn.in3d.backend.service.KhuyenMaiService;
import vn.in3d.backend.service.XacThucService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Giỏ hàng của website bán hàng.
 *   POST /api/gio-hang/bao-gia   giá từng dòng + tổng tiền + kết quả mã + gợi ý mã theo địa chỉ
 *
 * CHỈ ĐỌC: không ghi gì vào database. Tính bằng đúng hàm VÀ đúng cách tra món của
 * lúc tạo đơn (DonHangService.tinhBangGia + traTuBoNhoDem) nên tiền ở giỏ hàng bằng
 * tiền của đơn được tạo; sản phẩm và khuyến mãi lấy từ bộ nhớ đệm. Chỉ khi khách nhập
 * mã "chỉ khách mới" mới hỏi database một câu đếm đơn cũ.
 *
 * Mỗi dòng gửi lên kèm được bienTheId (phân loại khách chọn): giá và tồn kho lấy
 * theo biến thể đó; không gửi thì backend tự lấy biến thể mặc định của sản phẩm.
 */
@RestController
@RequestMapping("/api/gio-hang")
public class GioHangController {

    private final DonHangService donHangService;
    private final XacThucService xacThuc;
    private final BoNhoDem boNho;

    public GioHangController(DonHangService donHangService, XacThucService xacThuc, BoNhoDem boNho) {
        this.donHangService = donHangService;
        this.xacThuc = xacThuc;
        this.boNho = boNho;
    }

    /**
     * Body: { matHang: [{ bienTheId?, sanPhamId?, ten, donGia?, soLuong }], maKhuyenMai?, diaChi?, soDienThoai? }
     * Có token thì kiểm tra "chỉ khách mới" theo tài khoản, không thì theo số điện thoại.
     * Mã sai không phải lỗi HTTP: trả 200 kèm loiMa để giỏ hàng vẫn hiện được giá.
     */
    @PostMapping("/bao-gia")
    public Map<String, Object> baoGia(@RequestBody(required = false) BaoGiaRequest yeuCau,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        BaoGiaRequest gio = yeuCau == null ? new BaoGiaRequest(List.of(), null, null, null) : yeuCau;
        NguoiDung nd = xacThuc.docTokenNeuCo(authorization);
        KhuyenMaiService.NguoiDat nguoiDat = new KhuyenMaiService.NguoiDat(
                nd == null ? null : nd.getId(), chuoi(gio.soDienThoai()), chuoi(gio.diaChi()));

        // MỘT bản chụp sản phẩm cho cả giỏ, và đúng cách tra của lúc tạo đơn
        List<KhuyenMai> dsKhuyenMai = boNho.khuyenMai().danhSach();
        DonHangService.BangGia bg = donHangService.tinhBangGia(gio.matHang(),
                DonHangService.traTuBoNhoDem(boNho.sanPham()),
                dsKhuyenMai, gio.maKhuyenMai(), nguoiDat, DonHangService.Kieu.BAO_GIA);

        List<Map<String, Object>> dong = new ArrayList<>();
        for (DonHangService.DongGia d : bg.dong()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sanPhamId", d.sanPhamId());
            m.put("bienTheId", d.bienTheId());
            m.put("ten", d.ten());
            m.put("tenBienThe", d.tenBienThe());
            m.put("hinhAnh", d.hinhAnh());
            m.put("giaGoc", d.giaGoc());
            m.put("donGia", d.donGia());
            m.put("soLuong", d.soLuong());
            m.put("thanhTien", d.thanhTien());
            m.put("coTheDat", d.coTheDat());
            m.put("loi", d.loi());
            dong.add(m);
        }

        Map<String, Object> khuyenMai = null;
        if (bg.khuyenMai() != null) {
            khuyenMai = new LinkedHashMap<>();
            khuyenMai.put("ma", bg.khuyenMai().khuyenMai().getMa());
            khuyenMai.put("ten", bg.khuyenMai().khuyenMai().getTen());
            khuyenMai.put("tienGiam", bg.khuyenMai().tienGiam());
        }

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("dong", dong);
        ra.put("tamTinh", bg.tamTinh());
        ra.put("tienGiamSanPham", bg.tienGiamSanPham());
        ra.put("khuyenMai", khuyenMai);
        ra.put("loiMa", bg.loiMa());
        ra.put("goiYMa", goiYMa(dsKhuyenMai, nguoiDat.diaChi()));
        ra.put("tongCong", bg.tongCong());
        return ra;
    }

    /**
     * Mã đang chạy, đang khoe, có điều kiện khu vực và địa chỉ khách gõ thuộc khu vực đó —
     * thay cho đoạn JS tự so địa chỉ ở giỏ hàng (dùng đúng KhuyenMai.hopDiaChi của backend).
     */
    private List<Map<String, Object>> goiYMa(List<KhuyenMai> dsKhuyenMai, String diaChi) {
        List<Map<String, Object>> ra = new ArrayList<>();
        if (diaChi == null || diaChi.isBlank()) return ra;
        for (KhuyenMai km : dsKhuyenMai) {
            if (km.laKhuyenMaiSanPham() || km.getMa() == null || km.getMa().isBlank()) continue;
            if (!km.dangChay() || !Boolean.TRUE.equals(km.getHienThi())) continue;
            if (km.getDieuKienDiaChi() == null || km.getDieuKienDiaChi().isBlank()) continue;
            if (!km.hopDiaChi(diaChi)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ma", km.getMa());
            m.put("ten", km.getTen());
            m.put("moTa", km.getMoTa());
            ra.add(m);
        }
        return ra;
    }

    private static String chuoi(String s) {
        return s == null ? null : s.trim();
    }
}
