package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.repository.KhuyenMaiRepository;
import vn.in3d.backend.service.KhuyenMaiService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API chương trình khuyến mãi.
 *   GET  /api/khuyen-mai              trang khách — chỉ mã đang chạy và đang bật khoe
 *   GET  /api/khuyen-mai?tatCa=true   trang quản trị — mọi mã kể cả tạm dừng, hết hạn
 *   POST /api/khuyen-mai/kiem-tra     khách bấm "Áp dụng" ở giỏ hàng
 */
@RestController
@RequestMapping("/api/khuyen-mai")
public class KhuyenMaiController {

    private static final Set<String> LOAI = Set.of("phan_tram", "so_tien", "mien_ship");
    private static final Set<String> AP_DUNG_CHO = Set.of("tat_ca", "san_pham", "dich_vu");

    private final KhuyenMaiRepository repo;
    private final KhuyenMaiService dichVu;

    public KhuyenMaiController(KhuyenMaiRepository repo, KhuyenMaiService dichVu) {
        this.repo = repo;
        this.dichVu = dichVu;
    }

    /** @param tatCa true = lấy cả mã tạm dừng / hết hạn (dùng cho trang quản trị) */
    @GetMapping
    public List<KhuyenMai> danhSach(@RequestParam(required = false, defaultValue = "false") boolean tatCa) {
        List<KhuyenMai> ds = repo.findByDaXoaFalseOrderByIdDesc();
        if (tatCa) return ds;
        return ds.stream()
                .filter(km -> km.dangChay() && Boolean.TRUE.equals(km.getHienThi()))
                .toList();
    }

    @GetMapping("/thung-rac")
    public List<KhuyenMai> thungRac() {
        return repo.findByDaXoaTrueOrderByIdDesc();
    }

    @GetMapping("/{id}")
    public KhuyenMai mot(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(this::khongThay);
    }

    /**
     * Khách gõ mã ở giỏ hàng rồi bấm "Áp dụng".
     * Body: { "ma": "GIAM10", "tongTien": 250000 }
     * Mã sai / hết hạn / chưa đủ điều kiện -> 400 kèm câu giải thích tiếng Việt.
     */
    @PostMapping("/kiem-tra")
    public Map<String, Object> kiemTra(@RequestBody Map<String, Object> body) {
        String ma = body.get("ma") == null ? "" : String.valueOf(body.get("ma"));
        long tongTien = soLon(body.get("tongTien"));

        KhuyenMaiService.KetQua kq = dichVu.kiemTra(ma, tongTien);
        KhuyenMai km = kq.khuyenMai();

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("ok", true);
        ra.put("id", km.getId());
        ra.put("ma", km.getMa());
        ra.put("ten", km.getTen());
        ra.put("loai", km.getLoai());
        ra.put("tamTinh", tongTien);
        ra.put("tienGiam", kq.tienGiam());
        ra.put("conLai", kq.conLai());
        return ra;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KhuyenMai them(@RequestBody KhuyenMai km) {
        km.setId(null);
        km.setDaDung(0);
        km.setMa(chuanHoaMa(km.getMa()));
        kiemTraChung(km);
        repo.findByMaIgnoreCaseAndDaXoaFalse(km.getMa()).ifPresent(cu -> {
            throw badRequest("Mã \"" + cu.getMa() + "\" đã có rồi. Đặt mã khác nhé.");
        });
        return repo.save(km);
    }

    @PutMapping("/{id}")
    public KhuyenMai sua(@PathVariable Long id, @RequestBody Map<String, Object> td) {
        KhuyenMai km = repo.findById(id).orElseThrow(this::khongThay);

        if (td.containsKey("ma")) {
            String maMoi = chuanHoaMa(chuoi(td.get("ma")));
            repo.findByMaIgnoreCaseAndDaXoaFalse(maMoi)
                    .filter(cu -> !cu.getId().equals(id))
                    .ifPresent(cu -> { throw badRequest("Mã \"" + cu.getMa() + "\" đã có rồi. Đặt mã khác nhé."); });
            km.setMa(maMoi);
        }
        if (td.containsKey("ten")) km.setTen(chuoi(td.get("ten")));
        if (td.containsKey("moTa")) km.setMoTa(chuoi(td.get("moTa")));
        if (td.containsKey("loai")) km.setLoai(chuoi(td.get("loai")));
        if (td.containsKey("giaTri")) km.setGiaTri(soLon(td.get("giaTri")));
        if (td.containsKey("giamToiDa")) km.setGiamToiDa(soLon(td.get("giamToiDa")));
        if (td.containsKey("donToiThieu")) km.setDonToiThieu(soLon(td.get("donToiThieu")));
        if (td.containsKey("batDau")) km.setBatDau(ngay(td.get("batDau")));
        if (td.containsKey("ketThuc")) km.setKetThuc(ngay(td.get("ketThuc")));
        if (td.containsKey("soLuong")) km.setSoLuong((int) soLon(td.get("soLuong")));
        if (td.containsKey("apDungCho")) km.setApDungCho(chuoi(td.get("apDungCho")));
        if (td.containsKey("hoatDong")) km.setHoatDong(Boolean.parseBoolean(String.valueOf(td.get("hoatDong"))));
        if (td.containsKey("hienThi")) km.setHienThi(Boolean.parseBoolean(String.valueOf(td.get("hienThi"))));
        // daDung KHÔNG cho sửa qua API — đó là số đếm thật, sửa tay là mất ý nghĩa

        kiemTraChung(km);
        return repo.save(km);
    }

    /** XOÁ MỀM: đơn cũ đã dùng mã này vẫn tra ngược được. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        KhuyenMai km = repo.findById(id).orElseThrow(this::khongThay);
        km.xoaMem();
        repo.save(km);
    }

    @PutMapping("/{id}/khoi-phuc")
    public KhuyenMai khoiPhuc(@PathVariable Long id) {
        KhuyenMai km = repo.findById(id).orElseThrow(this::khongThay);
        km.khoiPhuc();
        return repo.save(km);
    }

    // ---------------- Kiểm tra dữ liệu ----------------

    private void kiemTraChung(KhuyenMai km) {
        if (km.getMa() == null || km.getMa().isBlank()) {
            throw badRequest("Mã khuyến mãi không được để trống.");
        }
        if (!km.getMa().matches("[A-Z0-9_-]{3,40}")) {
            throw badRequest("Mã chỉ gồm chữ HOA không dấu, số, gạch ngang hoặc gạch dưới, dài 3-40 ký tự. "
                    + "Ví dụ: GIAM10, FREESHIP-HN.");
        }
        if (km.getTen() == null || km.getTen().isBlank()) {
            throw badRequest("Tên chương trình không được để trống.");
        }
        if (!LOAI.contains(km.getLoai())) {
            throw badRequest("Loại khuyến mãi không hợp lệ. Chỉ nhận: " + String.join(", ", LOAI));
        }
        if (!AP_DUNG_CHO.contains(km.getApDungCho())) {
            throw badRequest("Phạm vi áp dụng không hợp lệ. Chỉ nhận: " + String.join(", ", AP_DUNG_CHO));
        }
        if (km.getGiaTri() <= 0) {
            throw badRequest("Mức giảm phải lớn hơn 0.");
        }
        if ("phan_tram".equals(km.getLoai()) && km.getGiaTri() > 100) {
            throw badRequest("Giảm theo phần trăm không được quá 100%.");
        }
        if (km.getBatDau() != null && km.getKetThuc() != null && km.getKetThuc().isBefore(km.getBatDau())) {
            throw badRequest("Ngày kết thúc phải sau ngày bắt đầu.");
        }
        if (km.getSoLuong() > 0 && km.getDaDung() > km.getSoLuong()) {
            throw badRequest("Số lượt cho phép (" + km.getSoLuong() + ") nhỏ hơn số lượt đã dùng ("
                    + km.getDaDung() + ").");
        }
    }

    private ResponseStatusException khongThay() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chương trình khuyến mãi.");
    }

    private ResponseStatusException badRequest(String thongBao) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, thongBao);
    }

    private String chuanHoaMa(String ma) {
        return ma == null ? null : ma.trim().toUpperCase().replaceAll("\\s+", "");
    }

    private String chuoi(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private long soLon(Object v) {
        if (v == null) return 0L;
        try { return Long.parseLong(String.valueOf(v).trim().replaceAll("[^0-9-]", "")); }
        catch (Exception e) { return 0L; }
    }

    /** Nhận "2026-08-19" từ ô <input type="date">; chuỗi rỗng = xoá ngày. */
    private LocalDate ngay(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try { return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s); }
        catch (Exception e) { throw badRequest("Ngày không hợp lệ: " + s); }
    }
}
