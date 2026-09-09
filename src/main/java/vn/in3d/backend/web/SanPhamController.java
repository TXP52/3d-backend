package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.SanPham;
import vn.in3d.backend.repository.SanPhamRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** API sản phẩm cho website bán hàng và trang quản trị. */
@RestController
@RequestMapping("/api")
public class SanPhamController {

    /** Các trạng thái sản phẩm hợp lệ (khớp với menu ở trang quản trị). */
    private static final Set<String> TRANG_THAI = Set.of(
            "du_kien", "da_dat", "dang_in", "san_hang",
            "thanh_cong", "hoan_hang", "dang_van_chuyen", "het_hang");

    /** Loại sản phẩm hợp lệ. */
    private static final Set<String> LOAI_SAN_PHAM = Set.of("ban", "mau", "dich_vu");

    private final SanPhamRepository sanPhamRepo;
    private final vn.in3d.backend.repository.DanhMucRepository danhMucRepo;

    public SanPhamController(SanPhamRepository sanPhamRepo,
                             vn.in3d.backend.repository.DanhMucRepository danhMucRepo) {
        this.sanPhamRepo = sanPhamRepo;
        this.danhMucRepo = danhMucRepo;
    }

    /** Kiểm tra backend còn sống — frontend gọi để quyết định dùng Java API hay Supabase. */
    @GetMapping("/suc-khoe")
    public Map<String, Object> sucKhoe() {
        return Map.of("ok", true, "backend", "java-spring-boot");
    }

    /** Danh sách sản phẩm. Mặc định chỉ trả sản phẩm đang bán; ?tatCa=true trả hết (cho admin). */
    @GetMapping("/san-pham")
    public List<SanPham> danhSach(@RequestParam(defaultValue = "false") boolean tatCa) {
        return tatCa ? sanPhamRepo.findByDaXoaFalseOrderByIdAsc()
                     : sanPhamRepo.findByDangBanTrueAndDaXoaFalseOrderByIdAsc();
    }

    /** Thêm sản phẩm mới (admin). */
    @PostMapping("/san-pham")
    @Transactional
    public SanPham them(@RequestBody SanPham sp) {
        sp.setId(null);
        if (sp.getTen() == null || sp.getTen().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên sản phẩm không được để trống.");
        }
        kiemTraTrangThai(sp.getTrangThai());
        kiemTraLoai(sp.getLoaiSanPham());
        if (sp.getDanhMucId() != null && !danhMucRepo.existsById(sp.getDanhMucId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh mục không tồn tại.");
        }
        if (sp.getGiaChu() == null || sp.getGiaChu().isBlank()) sp.setGiaChu(dinhDangGia(sp.getGia()));
        return sanPhamRepo.save(sp);
    }

    /** Cập nhật tên / mô tả / ảnh / giá / tồn kho / ẩn-hiện / trạng thái (admin). */
    @PutMapping("/san-pham/{id}")
    @Transactional
    public SanPham capNhat(@PathVariable Long id, @RequestBody Map<String, Object> thayDoi) {
        SanPham sp = sanPhamRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm."));
        if (thayDoi.containsKey("ten")) {
            String ten = String.valueOf(thayDoi.get("ten")).trim();
            if (ten.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên sản phẩm không được để trống.");
            sp.setTen(ten);
        }
        if (thayDoi.containsKey("moTa")) sp.setMoTa(chuoi(thayDoi.get("moTa")));
        if (thayDoi.containsKey("hinhAnh")) sp.setHinhAnh(chuoi(thayDoi.get("hinhAnh")));
        if (thayDoi.containsKey("gia")) {
            long gia = Long.parseLong(String.valueOf(thayDoi.get("gia")));
            sp.setGia(gia);
            sp.setGiaChu(dinhDangGia(gia));
        }
        if (thayDoi.containsKey("tonKho")) {
            sp.setTonKho(Integer.parseInt(String.valueOf(thayDoi.get("tonKho"))));
        }
        if (thayDoi.containsKey("dangBan")) {
            sp.setDangBan(Boolean.parseBoolean(String.valueOf(thayDoi.get("dangBan"))));
        }
        if (thayDoi.containsKey("danhMucId")) {
            Object v = thayDoi.get("danhMucId");
            String chuoi = v == null ? "" : String.valueOf(v).trim();
            if (chuoi.isEmpty() || "null".equals(chuoi)) {
                sp.setDanhMucId(null);           // gỡ khỏi danh mục -> chưa phân loại
            } else {
                Long dmId = Long.parseLong(chuoi);
                if (!danhMucRepo.existsById(dmId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh mục không tồn tại.");
                }
                sp.setDanhMucId(dmId);
            }
        }
        if (thayDoi.containsKey("loaiSanPham")) {
            String l = String.valueOf(thayDoi.get("loaiSanPham"));
            kiemTraLoai(l);
            sp.setLoaiSanPham(l);
        }
        if (thayDoi.containsKey("trangThai")) {
            String tt = String.valueOf(thayDoi.get("trangThai"));
            kiemTraTrangThai(tt);
            sp.setTrangThai(tt);
        }
        return sanPhamRepo.save(sp);
    }

    /** XOÁ MỀM sản phẩm: chỉ bật cờ is_deleted, dữ liệu vẫn nằm trong database. */
    @DeleteMapping("/san-pham/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        SanPham sp = sanPhamRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm."));
        sp.xoaMem();
        sanPhamRepo.save(sp);
    }

    /** Khôi phục sản phẩm đã xoá. */
    @PutMapping("/san-pham/{id}/khoi-phuc")
    public SanPham khoiPhuc(@PathVariable Long id) {
        SanPham sp = sanPhamRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm."));
        sp.khoiPhuc();
        return sanPhamRepo.save(sp);
    }

    /** Danh sách sản phẩm đã xoá — để xem lại hoặc khôi phục. */
    @GetMapping("/san-pham/thung-rac")
    public List<SanPham> thungRac() {
        return sanPhamRepo.findAll().stream().filter(SanPham::getDaXoa).toList();
    }

    private void kiemTraLoai(String l) {
        if (l == null || l.isBlank()) return;
        if (!LOAI_SAN_PHAM.contains(l)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Loại sản phẩm không hợp lệ. Chỉ nhận: " + String.join(", ", LOAI_SAN_PHAM));
        }
    }

    private void kiemTraTrangThai(String tt) {
        if (tt == null || tt.isBlank()) return; // để mặc định
        if (!TRANG_THAI.contains(tt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái không hợp lệ. Chỉ nhận: " + String.join(", ", TRANG_THAI));
        }
    }

    private String dinhDangGia(Long gia) {
        return gia != null && gia > 0 ? String.format("%,d₫", gia).replace(',', '.') : "Liên hệ";
    }

    private String chuoi(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
