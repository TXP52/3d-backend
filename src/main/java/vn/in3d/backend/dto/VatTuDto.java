package vn.in3d.backend.dto;

import vn.in3d.backend.entity.LoNhap;
import vn.in3d.backend.entity.VatTu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Một vật tư dạng DANH SÁCH của GET /api/vat-tu — CHỖ DUY NHẤT dựng nó.
 * Bộ nhớ đệm dựng sẵn; POST/PUT vật tư và phần "vatTuThayDoi" khi lưu sản phẩm
 * đều trả đúng object này. Object trả về KHÔNG sửa được (dùng chung cho mọi request).
 */
public final class VatTuDto {

    private VatTuDto() {}

    /** Dòng vật tư KHÔNG kèm đợt nhập — chỗ nào chưa đọc bảng lo_nhap thì để danh sách rỗng. */
    public static Map<String, Object> tao(VatTu v, String maMau, String tenMau,
                                          String tenNhaCungCap, String tenDanhMuc) {
        return tao(v, maMau, tenMau, tenNhaCungCap, tenDanhMuc, List.of());
    }

    /**
     * @param loNhap các đợt nhập của vật tư này (đợt cũ trước), mỗi phần tử dựng bằng
     *               {@link #dongLoNhap}; danh sách rỗng = vật tư cũ chưa tách đợt.
     */
    public static Map<String, Object> tao(VatTu v, String maMau, String tenMau,
                                          String tenNhaCungCap, String tenDanhMuc,
                                          List<Map<String, Object>> loNhap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("ten", v.getTen());
        m.put("loai", v.getLoai());
        m.put("danhMucId", v.getDanhMucId());
        m.put("danhMuc", tenDanhMuc);
        // Màu chỉ có một nguồn: bảng mau_sac, tra theo mau_sac_id
        m.put("mau", tenMau);
        m.put("mauSacId", v.getMauSacId());
        m.put("maMau", maMau);
        m.put("gia", v.getGia());
        m.put("soLuong", v.getSoLuong());
        m.put("khoiLuongGram", v.getKhoiLuongGram());
        m.put("daDungGram", v.getDaDungGram());
        m.put("trangThai", v.getTrangThai());
        // Trạng thái suy từ số gram còn lại — frontend hiển thị cái này
        m.put("trangThaiTinh", v.getTrangThaiTinh());
        m.put("hinhAnh", v.getHinhAnh());
        m.put("nhaCungCapId", v.getNhaCungCapId());
        m.put("ghiChu", v.getGhiChu());
        // Các số tính sẵn cho frontend khỏi tính lại
        m.put("tongTienMua", v.getTongTienMua());
        m.put("donGiaMoiGram", v.getDonGiaMoiGram());
        m.put("tienDaDung", v.getTienDaDung());
        m.put("conLaiGram", v.getConLaiGram());
        // Đã dùng / còn lại theo đơn vị riêng: nhựa "g", máy in & dụng cụ "cái"
        m.put("donVi", v.getDonVi());
        m.put("tongCoThe", v.getTongCoThe());
        m.put("conLai", v.getConLai());
        m.put("nhaCungCap", tenNhaCungCap);
        // Các đợt nhập: mỗi đợt một giá riêng; gia / soLuong / tongTienMua ở trên là tổng của chúng
        m.put("loNhap", loNhap == null ? List.of() : Collections.unmodifiableList(loNhap));
        m.put("soDotNhap", loNhap == null ? 0 : loNhap.size());
        m.put("createdAt", v.getCreatedAt());
        m.put("updatedAt", v.getUpdatedAt());
        m.put("daXoa", v.getDaXoa());
        return Collections.unmodifiableMap(m);
    }

    /** Một đợt nhập trong danh sách "loNhap"; tenNhaCungCap tra sẵn để frontend khỏi hỏi thêm. */
    public static Map<String, Object> dongLoNhap(LoNhap l, String tenNhaCungCap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("ngayNhap", l.getNgayNhap() == null ? null : l.getNgayNhap().toString());
        m.put("soLuong", l.getSoLuong());
        m.put("gia", l.getGia());
        m.put("thanhTien", l.getThanhTien());
        m.put("nhaCungCapId", l.getNhaCungCapId());
        m.put("nhaCungCap", tenNhaCungCap);
        return Collections.unmodifiableMap(m);
    }
}
