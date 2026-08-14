package vn.in3d.backend.web;

import org.springframework.web.bind.annotation.*;
import vn.in3d.backend.entity.SanPham;
import vn.in3d.backend.repository.SanPhamRepository;

import java.util.List;
import java.util.Map;

/** API sản phẩm cho website bán hàng và trang quản trị. */
@RestController
@RequestMapping("/api")
public class SanPhamController {

    private final SanPhamRepository sanPhamRepo;

    public SanPhamController(SanPhamRepository sanPhamRepo) {
        this.sanPhamRepo = sanPhamRepo;
    }

    /** Kiểm tra backend còn sống — frontend gọi để quyết định dùng Java API hay Supabase. */
    @GetMapping("/suc-khoe")
    public Map<String, Object> sucKhoe() {
        return Map.of("ok", true, "backend", "java-spring-boot");
    }

    /** Danh sách sản phẩm. Mặc định chỉ trả sản phẩm đang bán; ?tatCa=true trả hết (cho admin). */
    @GetMapping("/san-pham")
    public List<SanPham> danhSach(@RequestParam(defaultValue = "false") boolean tatCa) {
        return tatCa ? sanPhamRepo.findAll() : sanPhamRepo.findByDangBanTrueOrderByIdAsc();
    }

    /** Thêm sản phẩm mới (admin). */
    @PostMapping("/san-pham")
    public SanPham them(@RequestBody SanPham sp) {
        sp.setId(null);
        return sanPhamRepo.save(sp);
    }

    /** Cập nhật giá / tồn kho / ẩn-hiện (admin). */
    @PutMapping("/san-pham/{id}")
    public SanPham capNhat(@PathVariable Long id, @RequestBody Map<String, Object> thayDoi) {
        SanPham sp = sanPhamRepo.findById(id).orElseThrow();
        if (thayDoi.containsKey("gia")) {
            long gia = Long.parseLong(String.valueOf(thayDoi.get("gia")));
            sp.setGia(gia);
            sp.setGiaChu(gia > 0 ? String.format("%,d₫", gia).replace(',', '.') : "Liên hệ");
        }
        if (thayDoi.containsKey("tonKho")) {
            sp.setTonKho(Integer.parseInt(String.valueOf(thayDoi.get("tonKho"))));
        }
        if (thayDoi.containsKey("dangBan")) {
            sp.setDangBan(Boolean.parseBoolean(String.valueOf(thayDoi.get("dangBan"))));
        }
        return sanPhamRepo.save(sp);
    }
}
