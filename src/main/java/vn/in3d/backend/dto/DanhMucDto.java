package vn.in3d.backend.dto;

import vn.in3d.backend.entity.DanhMuc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Một danh mục dạng DANH SÁCH của GET /api/danh-muc — dùng chung cho GET và các lệnh ghi. */
public final class DanhMucDto {

    private DanhMucDto() {}

    /** Không kèm số đếm (mặc định của GET /api/danh-muc). */
    public static Map<String, Object> tao(DanhMuc d) {
        return tao(d, null, null);
    }

    /** soSanPham / soVatTu khác null thì mới đưa vào (GET ?kemSoLuong=true). */
    public static Map<String, Object> tao(DanhMuc d, Long soSanPham, Long soVatTu) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("ten", d.getTen());
        m.put("nhom", d.getNhom());
        m.put("tinhChat", d.getTinhChat());
        m.put("moTa", d.getMoTa());
        m.put("icon", d.getIcon());
        m.put("thuTu", d.getThuTu());
        m.put("dangHien", d.getDangHien());
        if (soSanPham != null && soVatTu != null) {
            m.put("soSanPham", soSanPham);
            m.put("soVatTu", soVatTu);
        }
        m.put("createdAt", d.getCreatedAt());
        m.put("updatedAt", d.getUpdatedAt());
        return Collections.unmodifiableMap(m);
    }
}
