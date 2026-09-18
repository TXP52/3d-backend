package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.BaiViet;
import vn.in3d.backend.repository.BaiVietRepository;
import vn.in3d.backend.service.BoNhoDem;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API bài viết chia sẻ kiến thức in 3D.
 * Trang khách gọi GET (chỉ thấy bài đang bật), trang quản trị gọi ?tatCa=true.
 */
@RestController
@RequestMapping("/api/bai-viet")
public class BaiVietController {

    private static final Set<String> CHUYEN_MUC =
            Set.of("huong-dan", "vat-lieu", "kinh-nghiem", "tin-shop");

    private final BaiVietRepository repo;
    private final BoNhoDem boNho;
    private final TransactionTemplate giaoDich;
    private final JdbcTemplate jdbc;

    public BaiVietController(BaiVietRepository repo, BoNhoDem boNho, TransactionTemplate giaoDich, JdbcTemplate jdbc) {
        this.repo = repo;
        this.boNho = boNho;
        this.giaoDich = giaoDich;
        this.jdbc = jdbc;
    }

    /**
     * @param tatCa true = lấy cả bài đang tắt (dùng cho trang quản trị)
     */
    @GetMapping
    public List<BaiViet> danhSach(@RequestParam(required = false, defaultValue = "false") boolean tatCa) {
        return boNho.dsBaiViet(tatCa);
    }

    @GetMapping("/thung-rac")
    public List<BaiViet> thungRac() {
        return boNho.baiViet().thungRac();
    }

    @GetMapping("/{id}")
    public BaiViet mot(@PathVariable Long id) {
        BaiViet b = boNho.baiViet().theoId().get(id);
        if (b == null) throw khongThay();
        return b;
    }

    /** Mở bài theo đường dẫn thân thiện: /api/bai-viet/duong-dan/chon-nhua-pla-petg-abs */
    @GetMapping("/duong-dan/{duongDan}")
    public BaiViet theoDuongDan(@PathVariable String duongDan) {
        BaiViet b = boNho.baiViet().theoDuongDan().get(duongDan);
        if (b == null) throw khongThay();
        return b;
    }

    /**
     * Đếm lượt đọc — tách riêng để GET không ghi vào database.
     * MỘT lệnh UPDATE cộng dồn ngay trong database: hai người đọc cùng lúc không mất lượt.
     * Không xoá bộ nhớ đệm (mỗi lượt đọc mà nạp lại bài viết thì phí); số lượt xem
     * trong danh sách cập nhật ở lần nạp lại kế tiếp.
     */
    @PostMapping("/{id}/luot-xem")
    public Map<String, Object> tangLuotXem(@PathVariable Long id) {
        List<Integer> luotXem = jdbc.queryForList(
                "update bai_viet set luot_xem = luot_xem + 1 where id = ? returning luot_xem", Integer.class, id);
        if (luotXem.isEmpty()) throw khongThay();
        return Map.of("id", id, "luotXem", luotXem.get(0));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BaiViet them(@RequestBody BaiViet b) {
        b.setId(null);
        b.setLuotXem(0);
        if (b.getTieuDe() == null || b.getTieuDe().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề bài viết không được để trống.");
        }
        b.setTieuDe(b.getTieuDe().trim());
        kiemTraChuyenMuc(b.getChuyenMuc());
        b.setDuongDan(duongDanDuyNhat(b.getDuongDan(), b.getTieuDe(), null));
        BaiViet daLuu = repo.save(b);
        return traBaiViet(daLuu.getId(), daLuu);
    }

    /** Sửa trong MỘT transaction: đọc + UPDATE + COMMIT (bản cũ đọc rồi merge riêng: 5-6 lượt đi-về). */
    @PutMapping("/{id}")
    public BaiViet sua(@PathVariable Long id, @RequestBody Map<String, Object> td) {
        BaiViet daSua = giaoDich.execute(gd -> {
            BaiViet b = repo.findById(id).orElseThrow(this::khongThay);

            if (td.containsKey("tieuDe")) {
                String t = String.valueOf(td.get("tieuDe")).trim();
                if (t.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề bài viết không được để trống.");
                }
                b.setTieuDe(t);
            }
            if (td.containsKey("duongDan") || td.containsKey("tieuDe")) {
                String moi = td.containsKey("duongDan") ? chuoi(td.get("duongDan")) : b.getDuongDan();
                b.setDuongDan(duongDanDuyNhat(moi, b.getTieuDe(), b.getId(), b.getDuongDan()));
            }
            if (td.containsKey("tomTat")) b.setTomTat(chuoi(td.get("tomTat")));
            if (td.containsKey("noiDung")) b.setNoiDung(chuoi(td.get("noiDung")));
            if (td.containsKey("hinhAnh")) b.setHinhAnh(chuoi(td.get("hinhAnh")));
            if (td.containsKey("tacGia")) b.setTacGia(chuoi(td.get("tacGia")));
            if (td.containsKey("chuyenMuc")) {
                String cm = chuoi(td.get("chuyenMuc"));
                kiemTraChuyenMuc(cm);
                b.setChuyenMuc(cm);
            }
            if (td.containsKey("hienThi")) b.setHienThi(Boolean.parseBoolean(String.valueOf(td.get("hienThi"))));
            if (td.containsKey("thuTu")) b.setThuTu(so(td.get("thuTu")));
            // b đang được quản lý trong transaction: commit tự ghi, khỏi gọi save
            return b;
        });
        return traBaiViet(id, daSua);
    }

    /** XOÁ MỀM: bài vẫn nằm trong database, vào thùng rác khôi phục được. MỘT lệnh UPDATE. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        if (jdbc.update("update bai_viet set is_deleted = true, updated_at = now() where id = ?", id) == 0) {
            throw khongThay();
        }
        boNho.xoaVaNapLai(BoNhoDem.BV);
    }

    @PutMapping("/{id}/khoi-phuc")
    public BaiViet khoiPhuc(@PathVariable Long id) {
        if (jdbc.update("update bai_viet set is_deleted = false, updated_at = now() where id = ?", id) == 0) {
            throw khongThay();
        }
        return traBaiViet(id, null);
    }

    // ---------------- Tiện ích ----------------

    /**
     * Đã commit: nạp lại bài viết rồi trả đúng dòng danh sách từ bộ nhớ đệm.
     *
     * Nạp lại lỗi (Supabase chớp một nhịp) thì KHÔNG đọc lại bộ nhớ đệm nữa: đọc là mở thêm
     * một lượt nạp, lỗi lần hai thì ném ra ngoài và lệnh ghi ĐÃ COMMIT lại thành lỗi 500 —
     * bấm Lưu lại là có hai bài trùng. Trả luôn bài vừa ghi trong transaction.
     *
     * @param duPhong bài vừa ghi, null nếu nơi gọi không có (chỉ chạy một lệnh UPDATE)
     */
    private BaiViet traBaiViet(Long id, BaiViet duPhong) {
        if (boNho.xoaVaNapLai(BoNhoDem.BV)) {
            try {
                BaiViet b = boNho.baiViet().theoId().get(id);
                if (b != null) return b;
            } catch (RuntimeException boQua) {
                // rơi xuống dùng bản dự phòng
            }
        }
        return duPhong;
    }

    private ResponseStatusException khongThay() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết.");
    }

    private void kiemTraChuyenMuc(String cm) {
        if (cm == null || cm.isBlank()) return;
        if (!CHUYEN_MUC.contains(cm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chuyên mục không hợp lệ. Chỉ nhận: " + String.join(", ", CHUYEN_MUC));
        }
    }

    /**
     * Sinh đường dẫn từ tiêu đề nếu người dùng bỏ trống, và thêm hậu tố -2, -3...
     * khi trùng với bài khác (đường dẫn là khoá duy nhất trong database).
     */
    private String duongDanDuyNhat(String mongMuon, String tieuDe, Long boQuaId) {
        return duongDanDuyNhat(mongMuon, tieuDe, boQuaId, null);
    }

    /**
     * @param hienTai đường dẫn bài đang sửa đang giữ: kết quả trùng đúng nó thì khỏi hỏi
     *                database — cột duong_dan là khoá duy nhất nên chẳng bài nào khác có được.
     */
    private String duongDanDuyNhat(String mongMuon, String tieuDe, Long boQuaId, String hienTai) {
        String goc = khongDau(mongMuon == null || mongMuon.isBlank() ? tieuDe : mongMuon);
        if (goc.isEmpty()) goc = "bai-viet";
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

    /** "Chọn nhựa PLA hay PETG?" -> "chon-nhua-pla-hay-petg" */
    static String khongDau(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase();
        return t.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }

    private Integer so(Object v) {
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception e) { return 0; }
    }

    private String chuoi(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
