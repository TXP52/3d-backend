package vn.in3d.backend.dto;

import vn.in3d.backend.entity.BoSuuTap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Một bộ sưu tập dạng DANH SÁCH của GET /api/bo-suu-tap — CHỖ DUY NHẤT dựng nó.
 *
 * Tên và ảnh sản phẩm KHÔNG lưu lại ở đây mà tra sang bản chụp sản phẩm lúc trả lời:
 * đổi tên một sản phẩm thì khỏi phải xoá luôn bộ nhớ đệm bộ sưu tập (giống cách
 * BoNhoDem.dsDanhMuc đếm số sản phẩm của danh mục).
 */
public final class BoSuuTapDto {

    private BoSuuTapDto() {}

    /**
     * @param dongSanPham    dòng bảng nối của bộ, đã xếp theo thu_tu rồi id sản phẩm
     * @param sanPhamTheoId  DTO sản phẩm tra theo id (BoNhoDem.sanPham().theoId());
     *                       null = không đọc được bản chụp, khi đó danh sách sản phẩm để trống
     */
    public static Map<String, Object> tao(BoSuuTap b, List<BoSuuTap.DongSanPham> dongSanPham,
                                          Map<Long, Map<String, Object>> sanPhamTheoId) {
        List<Map<String, Object>> ds = new ArrayList<>();
        for (BoSuuTap.DongSanPham dong : dongSanPham == null ? List.<BoSuuTap.DongSanPham>of() : dongSanPham) {
            Map<String, Object> sp = sanPhamTheoId == null ? null : sanPhamTheoId.get(dong.getSanPhamId());
            // Sản phẩm đã xoá mềm thì không hiện trong bộ nữa (dòng nối vẫn nằm yên
            // trong database, khôi phục sản phẩm là nó về lại bộ cũ)
            if (sp == null || Boolean.TRUE.equals(sp.get("daXoa"))) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", sp.get("id"));
            m.put("ten", sp.get("ten"));
            m.put("hinhAnh", sp.get("hinhAnh"));
            m.put("thuTu", dong.getThuTu());
            ds.add(Collections.unmodifiableMap(m));
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("ten", b.getTen());
        m.put("duongDan", b.getDuongDan());
        m.put("moTa", b.getMoTa());
        m.put("hinhAnh", b.getHinhAnh());
        m.put("hienThi", b.getHienThi());
        m.put("thuTu", b.getThuTu());
        m.put("soSanPham", ds.size());
        m.put("sanPham", Collections.unmodifiableList(ds));
        m.put("createdAt", b.getCreatedAt());
        m.put("updatedAt", b.getUpdatedAt());
        return Collections.unmodifiableMap(m);
    }
}
