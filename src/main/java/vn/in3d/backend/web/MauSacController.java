package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.MauSac;
import vn.in3d.backend.repository.MauSacRepository;

import java.util.List;
import java.util.Map;

/** API bảng màu sắc dùng cho nhựa in và sản phẩm. */
@RestController
@RequestMapping("/api/mau-sac")
public class MauSacController {

    private final MauSacRepository repo;

    public MauSacController(MauSacRepository repo) {
        this.repo = repo;
    }

    /** Danh sách màu chưa bị xoá, theo thứ tự hiển thị. */
    @GetMapping
    public List<MauSac> danhSach() {
        return repo.findByDaXoaFalseOrderByThuTuAscIdAsc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MauSac them(@RequestBody MauSac m) {
        m.setId(null);
        if (m.getTen() == null || m.getTen().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên màu không được để trống.");
        }
        m.setTen(m.getTen().trim());
        repo.findByTenIgnoreCaseAndDaXoaFalse(m.getTen()).ifPresent(cu -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Màu \"" + cu.getTen() + "\" đã có trong danh sách.");
        });
        kiemTraMaMau(m.getMaMau());
        return repo.save(m);
    }

    @PutMapping("/{id}")
    public MauSac sua(@PathVariable Long id, @RequestBody Map<String, Object> td) {
        MauSac m = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy màu."));
        if (td.containsKey("ten")) {
            String ten = String.valueOf(td.get("ten")).trim();
            if (ten.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên màu không được để trống.");
            m.setTen(ten);
        }
        if (td.containsKey("maMau")) {
            String ma = td.get("maMau") == null ? null : String.valueOf(td.get("maMau")).trim();
            kiemTraMaMau(ma);
            m.setMaMau(ma);
        }
        if (td.containsKey("ghiChu")) m.setGhiChu(chuoi(td.get("ghiChu")));
        if (td.containsKey("thuTu")) m.setThuTu(so(td.get("thuTu")));
        return repo.save(m);
    }

    /** XOÁ MỀM: màu vẫn nằm trong database để vật tư cũ còn tra ngược được. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        MauSac m = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy màu."));
        m.xoaMem();
        repo.save(m);
    }

    @PutMapping("/{id}/khoi-phuc")
    public MauSac khoiPhuc(@PathVariable Long id) {
        MauSac m = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy màu."));
        m.khoiPhuc();
        return repo.save(m);
    }

    private void kiemTraMaMau(String ma) {
        if (ma == null || ma.isBlank()) return;
        if (!ma.matches("#[0-9a-fA-F]{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mã màu phải dạng #rrggbb, ví dụ #e03131.");
        }
    }

    private Integer so(Object v) {
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception e) { return 0; }
    }

    private String chuoi(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
