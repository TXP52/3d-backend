package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.BaiViet;
import vn.in3d.backend.repository.BaiVietRepository;

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

    public BaiVietController(BaiVietRepository repo) {
        this.repo = repo;
    }

    /**
     * @param tatCa true = lấy cả bài đang tắt (dùng cho trang quản trị)
     */
    @GetMapping
    public List<BaiViet> danhSach(@RequestParam(required = false, defaultValue = "false") boolean tatCa) {
        return tatCa ? repo.findByDaXoaFalseOrderByThuTuAscIdDesc()
                     : repo.findByHienThiTrueAndDaXoaFalseOrderByThuTuAscIdDesc();
    }

    @GetMapping("/thung-rac")
    public List<BaiViet> thungRac() {
        return repo.findByDaXoaTrueOrderByIdDesc();
    }

    @GetMapping("/{id}")
    public BaiViet mot(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(this::khongThay);
    }

    /** Mở bài theo đường dẫn thân thiện: /api/bai-viet/duong-dan/chon-nhua-pla-petg-abs */
    @GetMapping("/duong-dan/{duongDan}")
    public BaiViet theoDuongDan(@PathVariable String duongDan) {
        return repo.findByDuongDanAndDaXoaFalse(duongDan).orElseThrow(this::khongThay);
    }

    /** Đếm lượt đọc — tách riêng để GET không ghi vào database. */
    @PostMapping("/{id}/luot-xem")
    public Map<String, Object> tangLuotXem(@PathVariable Long id) {
        BaiViet b = repo.findById(id).orElseThrow(this::khongThay);
        b.setLuotXem(b.getLuotXem() + 1);
        repo.save(b);
        return Map.of("id", b.getId(), "luotXem", b.getLuotXem());
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
        return repo.save(b);
    }

    @PutMapping("/{id}")
    public BaiViet sua(@PathVariable Long id, @RequestBody Map<String, Object> td) {
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
            b.setDuongDan(duongDanDuyNhat(moi, b.getTieuDe(), b.getId()));
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
        return repo.save(b);
    }

    /** XOÁ MỀM: bài vẫn nằm trong database, vào thùng rác khôi phục được. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        BaiViet b = repo.findById(id).orElseThrow(this::khongThay);
        b.xoaMem();
        repo.save(b);
    }

    @PutMapping("/{id}/khoi-phuc")
    public BaiViet khoiPhuc(@PathVariable Long id) {
        BaiViet b = repo.findById(id).orElseThrow(this::khongThay);
        b.khoiPhuc();
        return repo.save(b);
    }

    // ---------------- Tiện ích ----------------

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
        String goc = khongDau(mongMuon == null || mongMuon.isBlank() ? tieuDe : mongMuon);
        if (goc.isEmpty()) goc = "bai-viet";
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
