package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.dto.BoSuuTapDto;
import vn.in3d.backend.entity.BoSuuTap;
import vn.in3d.backend.repository.BoSuuTapRepository;
import vn.in3d.backend.service.BoNhoDem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * API BỘ SƯU TẬP (chủ đề) — trang quản trị thêm/sửa/xoá, web khách đọc qua
 * /api/cua-hang/bo-suu-tap.
 *
 * Một sản phẩm nằm được nhiều bộ (bảng nối san_pham_bo_suu_tap). Đường dẫn tự sinh
 * từ tên nếu để trống, trùng thì thêm -2, -3... y như bài viết.
 *
 * Ghi xong phải nạp lại CẢ bộ sưu tập LẪN sản phẩm: DTO sản phẩm mang theo danh sách
 * bộ nó đang nằm trong (SanPhamDto.boSuuTap).
 */
@RestController
@RequestMapping("/api/bo-suu-tap")
public class BoSuuTapController {

    private final BoSuuTapRepository repo;
    private final BoNhoDem boNho;
    private final TransactionTemplate giaoDich;
    private final JdbcTemplate jdbc;

    public BoSuuTapController(BoSuuTapRepository repo, BoNhoDem boNho,
                              TransactionTemplate giaoDich, JdbcTemplate jdbc) {
        this.repo = repo;
        this.boNho = boNho;
        this.giaoDich = giaoDich;
        this.jdbc = jdbc;
    }

    /**
     * @param tatCa true = lấy cả bộ đang tắt hiển thị (dùng cho trang quản trị)
     */
    @GetMapping
    public List<Map<String, Object>> danhSach(@RequestParam(required = false, defaultValue = "false") boolean tatCa) {
        return boNho.dsBoSuuTap(tatCa);
    }

    @GetMapping("/{id}")
    public Map<String, Object> mot(@PathVariable Long id) {
        BoSuuTap b = boNho.boSuuTap().theoId().get(id);
        if (b == null) throw khongThay();
        return BoSuuTapDto.tao(b, b.getSanPham(), boNho.sanPham().theoId());
    }

    /** Thêm bộ mới. Trả đúng dạng một dòng trong danh sách. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> them(@RequestBody Map<String, Object> td) {
        List<Long> idSanPham = docSanPham(td);
        BoSuuTap daLuu = giaoDich.execute(gd -> {
            BoSuuTap b = new BoSuuTap();
            b.setTen(tenHopLe(chuoi(td.get("ten"))));
            b.setMoTa(chuoi(td.get("moTa")));
            b.setHinhAnh(chuoi(td.get("hinhAnh")));
            if (td.containsKey("hienThi")) b.setHienThi(Boolean.parseBoolean(String.valueOf(td.get("hienThi"))));
            if (td.containsKey("thuTu")) b.setThuTu(soNguyen(td.get("thuTu")));
            b.setDuongDan(duongDanDuyNhat(chuoi(td.get("duongDan")), b.getTen(), null, null));
            BoSuuTap luu = repo.save(b);            // cần id database sinh trước khi ghi bảng nối
            chenSanPham(luu.getId(), idSanPham);    // bộ mới nên khỏi xoá bảng nối trước
            return luu;
        });
        return traBoSuuTap(daLuu.getId(), daLuu, idSanPham);
    }

    /**
     * Sửa trong MỘT transaction: đọc + UPDATE + COMMIT. Khoá nào không gửi thì để yên
     * (gửi "sanPham" là THAY cả danh sách sản phẩm của bộ).
     */
    @PutMapping("/{id}")
    public Map<String, Object> sua(@PathVariable Long id, @RequestBody Map<String, Object> td) {
        List<Long> idSanPham = docSanPham(td);
        BoSuuTap daSua = giaoDich.execute(gd -> {
            BoSuuTap b = repo.findById(id).orElseThrow(this::khongThay);
            if (td.containsKey("ten")) b.setTen(tenHopLe(chuoi(td.get("ten"))));
            if (td.containsKey("duongDan") || td.containsKey("ten")) {
                String moi = td.containsKey("duongDan") ? chuoi(td.get("duongDan")) : b.getDuongDan();
                b.setDuongDan(duongDanDuyNhat(moi, b.getTen(), b.getId(), b.getDuongDan()));
            }
            if (td.containsKey("moTa")) b.setMoTa(chuoi(td.get("moTa")));
            if (td.containsKey("hinhAnh")) b.setHinhAnh(chuoi(td.get("hinhAnh")));
            if (td.containsKey("hienThi")) b.setHienThi(Boolean.parseBoolean(String.valueOf(td.get("hienThi"))));
            if (td.containsKey("thuTu")) b.setThuTu(soNguyen(td.get("thuTu")));
            if (td.containsKey("sanPham")) ganSanPham(b.getId(), idSanPham);
            // b đang được quản lý trong transaction: commit tự ghi, khỏi gọi save
            return b;
        });
        return traBoSuuTap(id, daSua, td.containsKey("sanPham") ? idSanPham : null);
    }

    /**
     * XOÁ MỀM: bộ biến khỏi mọi danh sách nhưng bảng nối vẫn nằm yên, bật lại
     * is_deleted là có nguyên danh sách sản phẩm cũ. MỘT lệnh UPDATE.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        if (jdbc.update("update bo_suu_tap set is_deleted = true, updated_at = now() where id = ?", id) == 0) {
            throw khongThay();
        }
        boNho.xoaVaNapLai(BoNhoDem.BST, BoNhoDem.SP);
    }

    // ---------------- Tiện ích ----------------

    /**
     * Sản phẩm của bộ: xoá hết rồi chèn lại (một bộ chỉ vài chục sản phẩm nên dò từng
     * dòng không đáng), thứ tự trong bộ chính là thứ tự id gửi lên.
     */
    private void ganSanPham(Long boSuuTapId, List<Long> ids) {
        jdbc.update("delete from san_pham_bo_suu_tap where bo_suu_tap_id = ?", boSuuTapId);
        chenSanPham(boSuuTapId, ids);
    }

    private void chenSanPham(Long boSuuTapId, List<Long> ids) {
        if (ids.isEmpty()) return;
        jdbc.batchUpdate("insert into san_pham_bo_suu_tap (bo_suu_tap_id, san_pham_id, thu_tu) values (?, ?, ?)",
                ids, ids.size(), (ps, id) -> {
            ps.setLong(1, boSuuTapId);
            ps.setLong(2, id);
            ps.setInt(3, ids.indexOf(id));
        });
    }

    /**
     * Danh sách id sản phẩm gửi lên, bỏ trùng và giữ thứ tự.
     *
     * Đọc TRƯỚC khi mở transaction (đọc bộ nhớ đệm lúc đang mở transaction là để một
     * lượt nạp chen vào giữa) và kiểm tra luôn sản phẩm có thật không — id bậy thì
     * vướng khoá ngoại và thành lỗi 500 khó hiểu. Bộ nhớ đệm hỏng thì bỏ qua bước
     * kiểm tra, để khoá ngoại của database lo.
     */
    private List<Long> docSanPham(Map<String, Object> td) {
        List<Long> ids = new ArrayList<>();
        if (td.get("sanPham") instanceof List<?> tho) {
            for (Object o : tho) {
                Long id = soHoacNull(o);
                if (id != null && !ids.contains(id)) ids.add(id);
            }
        }
        Map<Long, Map<String, Object>> daCo = docSanPhamTheoId();
        if (daCo != null) {
            for (Long id : ids) {
                if (!daCo.containsKey(id)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm không tồn tại.");
                }
            }
        }
        return ids;
    }

    /**
     * Đã commit: nạp lại bộ sưu tập + sản phẩm rồi trả đúng dòng danh sách từ bộ nhớ đệm.
     *
     * Nạp lại lỗi (Supabase chớp một nhịp) thì KHÔNG đọc lại bộ nhớ đệm nữa: đọc là mở
     * thêm một lượt nạp, lỗi lần hai thì ném ra ngoài và lệnh ghi ĐÃ COMMIT lại thành
     * lỗi 500 — bấm Lưu lại là có hai bộ trùng. Dựng tạm dòng từ chính entity vừa ghi;
     * trang quản trị hỏi lại ngay sau đó nên vẫn thấy số mới.
     *
     * @param idSanPham danh sách sản phẩm vừa ghi, null = lượt ghi này không đụng tới
     */
    private Map<String, Object> traBoSuuTap(Long id, BoSuuTap duPhong, List<Long> idSanPham) {
        if (boNho.xoaVaNapLai(BoNhoDem.BST, BoNhoDem.SP)) {
            try {
                for (Map<String, Object> m : boNho.dsBoSuuTap(true)) {
                    if (id.equals(m.get("id"))) return m;
                }
            } catch (RuntimeException boQua) {
                // rơi xuống dùng bản dự phòng
            }
        }
        return BoSuuTapDto.tao(duPhong, dongSanPham(idSanPham), docSanPhamTheoId());
    }

    /** Bảng nối vừa ghi, dựng lại từ danh sách id (entity không đọc được nữa sau transaction). */
    private static List<BoSuuTap.DongSanPham> dongSanPham(List<Long> ids) {
        List<BoSuuTap.DongSanPham> ds = new ArrayList<>();
        for (int i = 0; ids != null && i < ids.size(); i++) ds.add(new BoSuuTap.DongSanPham(ids.get(i), i));
        return ds;
    }

    /** Bản chụp sản phẩm để lấy tên / ảnh; đọc không được thì thôi, đừng làm hỏng lệnh đã ghi. */
    private Map<Long, Map<String, Object>> docSanPhamTheoId() {
        try {
            return boNho.sanPham().theoId();
        } catch (RuntimeException boQua) {
            return null;
        }
    }

    private ResponseStatusException khongThay() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bộ sưu tập.");
    }

    private static String tenHopLe(String ten) {
        if (ten == null || ten.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên bộ sưu tập không được để trống.");
        }
        return ten.trim();
    }

    /**
     * Sinh đường dẫn từ tên nếu chủ shop bỏ trống, và thêm hậu tố -2, -3... khi trùng
     * với bộ khác (duong_dan là khoá duy nhất trong các bộ chưa xoá).
     *
     * @param hienTai đường dẫn bộ đang sửa đang giữ: kết quả trùng đúng nó thì khỏi hỏi database
     */
    private String duongDanDuyNhat(String mongMuon, String ten, Long boQuaId, String hienTai) {
        // Dùng chung cách bỏ dấu với bài viết cho đường dẫn hai nơi giống hệt nhau
        String goc = BaiVietController.khongDau(mongMuon == null || mongMuon.isBlank() ? ten : mongMuon);
        if (goc.isEmpty()) goc = "bo-suu-tap";
        if (boQuaId != null && goc.equals(hienTai)) return goc;
        String thu = goc;
        int n = 2;
        while (biTrung(thu, boQuaId)) {
            thu = goc + "-" + n;
            n++;
        }
        return thu;
    }

    private boolean biTrung(String duongDan, Long boQuaId) {
        return repo.findByDuongDanAndDaXoaFalse(duongDan)
                .filter(cu -> boQuaId == null || !cu.getId().equals(boQuaId))
                .isPresent();
    }

    private static Integer soNguyen(Object v) {
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception boQua) { return 0; }
    }

    /** Id kiểu số; rỗng / không phải số thì bỏ qua dòng đó. */
    private static Long soHoacNull(Object v) {
        if (v instanceof Number n) return n.longValue();
        String s = v == null ? "" : String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException boQua) { return null; }
    }

    private static String chuoi(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
