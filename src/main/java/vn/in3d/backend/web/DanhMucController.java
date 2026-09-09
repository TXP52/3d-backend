package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.DanhMuc;
import vn.in3d.backend.entity.SanPham;
import vn.in3d.backend.entity.VatTu;
import vn.in3d.backend.repository.DanhMucRepository;
import vn.in3d.backend.repository.SanPhamRepository;
import vn.in3d.backend.repository.VatTuRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API danh mục — MỘT bảng, hai nhóm (cột nhom):
 *   san_pham : danh mục sản phẩm cho khách lọc trên website
 *   vat_tu   : loại vật tư trong kho (Máy in, Nhựa in...), thay cho danh sách
 *              trước đây viết cứng trong giao diện.
 *
 * Với nhóm vat_tu, tinh_chat (may_in | nhua | phu_kien | khac) cho backend biết
 * vật tư nào là NHỰA (theo dõi gram, sắp hết) hay MÁY IN (tính vào vốn máy) —
 * chủ shop đặt tên loại tuỳ ý ("Nhựa PLA", "Nhựa PETG"), tính chất vẫn đúng.
 */
@RestController
@RequestMapping("/api/danh-muc")
public class DanhMucController {

    public static final String NHOM_SAN_PHAM = "san_pham";
    public static final String NHOM_VAT_TU = "vat_tu";
    private static final Set<String> NHOM = Set.of(NHOM_SAN_PHAM, NHOM_VAT_TU);
    public static final Set<String> TINH_CHAT = Set.of("may_in", "nhua", "phu_kien", "khac");

    private final DanhMucRepository repo;
    private final SanPhamRepository sanPhamRepo;
    private final VatTuRepository vatTuRepo;

    public DanhMucController(DanhMucRepository repo, SanPhamRepository sanPhamRepo, VatTuRepository vatTuRepo) {
        this.repo = repo;
        this.sanPhamRepo = sanPhamRepo;
        this.vatTuRepo = vatTuRepo;
    }

    /**
     * Danh sách danh mục.
     * ?nhom=san_pham hoặc ?nhom=vat_tu để lọc một nhóm; bỏ trống trả cả hai.
     * ?tatCa=false chỉ trả danh mục đang bật (cho website khách).
     * ?kemSoLuong=true mới đếm soSanPham / soVatTu — đếm là thêm 2 truy vấn
     * (~600 ms vì database ở xa) mà trang quản trị đã có sẵn hai danh sách đó để tự đếm.
     */
    // Không bọc @Transactional: mặc định chỉ MỘT truy vấn, mở transaction là thêm một lượt đi-về (~300 ms)
    @GetMapping
    public List<Map<String, Object>> danhSach(@RequestParam(defaultValue = "true") boolean tatCa,
                                              @RequestParam(required = false) String nhom,
                                              @RequestParam(defaultValue = "false") boolean kemSoLuong) {
        List<SanPham> sanPham = kemSoLuong ? sanPhamRepo.findByDaXoaFalseOrderByIdAsc() : List.of();
        List<VatTu> vatTu = kemSoLuong ? vatTuRepo.findByDaXoaFalseOrderByLoaiAscIdAsc() : List.of();
        List<DanhMuc> ds = tatCa ? repo.findByDaXoaFalseOrderByThuTuAscIdAsc()
                                 : repo.findByDaXoaFalseAndDangHienTrueOrderByThuTuAscIdAsc();
        return ds.stream()
                .filter(d -> nhom == null || nhom.isBlank() || nhom.equals(d.getNhom()))
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", d.getId());
                    m.put("ten", d.getTen());
                    m.put("nhom", d.getNhom());
                    m.put("tinhChat", d.getTinhChat());
                    m.put("moTa", d.getMoTa());
                    m.put("icon", d.getIcon());
                    m.put("thuTu", d.getThuTu());
                    m.put("dangHien", d.getDangHien());
                    if (kemSoLuong) {
                        m.put("soSanPham", sanPham.stream()
                                .filter(sp -> d.getId().equals(sp.getDanhMucId())).count());
                        m.put("soVatTu", vatTu.stream()
                                .filter(v -> d.getId().equals(v.getDanhMucId())).count());
                    }
                    m.put("createdAt", d.getCreatedAt());
                    m.put("updatedAt", d.getUpdatedAt());
                    return m;
                }).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public DanhMuc them(@RequestBody DanhMuc d) {
        d.setId(null);
        String ten = d.getTen() == null ? "" : d.getTen().trim();
        if (ten.isEmpty()) throw loi400("Tên danh mục không được để trống.");
        d.setTen(ten);

        String nhom = d.getNhom();
        if (!NHOM.contains(nhom)) throw loi400("Nhóm danh mục chỉ nhận: san_pham hoặc vat_tu.");
        if (NHOM_VAT_TU.equals(nhom)) {
            d.setTinhChat(chuanHoaTinhChat(d.getTinhChat()));
        } else {
            d.setTinhChat(null);   // danh mục sản phẩm không có tính chất
        }

        repo.findByTenIgnoreCaseAndNhomAndDaXoaFalse(ten, nhom).ifPresent(cu -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    (NHOM_VAT_TU.equals(nhom) ? "Loại vật tư \"" : "Danh mục \"") + cu.getTen() + "\" đã có rồi.");
        });
        return repo.save(d);
    }

    /**
     * Sửa tên / mô tả / icon / thứ tự / ẩn-hiện / tính chất.
     * Không cho đổi nhóm: danh mục đã có sản phẩm hay vật tư trỏ tới, đổi nhóm là
     * liên kết đó vô nghĩa — muốn chuyển thì tạo dòng mới ở nhóm kia.
     */
    @PutMapping("/{id}")
    @Transactional
    public DanhMuc sua(@PathVariable Long id, @RequestBody Map<String, Object> td) {
        DanhMuc d = timHoacBao(id);
        if (td.containsKey("ten")) {
            String ten = String.valueOf(td.get("ten")).trim();
            if (ten.isEmpty()) throw loi400("Tên danh mục không được để trống.");
            repo.findByTenIgnoreCaseAndNhomAndDaXoaFalse(ten, d.getNhom()).ifPresent(cu -> {
                if (!cu.getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Tên \"" + cu.getTen() + "\" đã có trong nhóm này.");
                }
            });
            d.setTen(ten);
        }
        if (td.containsKey("moTa")) d.setMoTa(chuoi(td.get("moTa")));
        if (td.containsKey("icon")) d.setIcon(chuoi(td.get("icon")));
        if (td.containsKey("thuTu")) d.setThuTu(so(td.get("thuTu")));
        if (td.containsKey("dangHien")) {
            d.setDangHien(Boolean.parseBoolean(String.valueOf(td.get("dangHien"))));
        }
        if (td.containsKey("tinhChat") && NHOM_VAT_TU.equals(d.getNhom())) {
            String moi = chuanHoaTinhChat(chuoi(td.get("tinhChat")));
            if (!moi.equals(d.getTinhChat())) {
                d.setTinhChat(moi);
                // Vật tư đang thuộc loại này đổi tính chất theo, để cột loai không lệch
                vatTuRepo.findByDaXoaFalseOrderByLoaiAscIdAsc().stream()
                        .filter(v -> id.equals(v.getDanhMucId()))
                        .forEach(v -> { v.setLoai(moi); vatTuRepo.save(v); });
            }
        }
        return repo.save(d);
    }

    /**
     * XOÁ MỀM. Sản phẩm / vật tư đang thuộc được gỡ liên kết chứ không xoá theo.
     * Vật tư giữ nguyên cột loai nên kho vẫn tính gram, tính vốn máy như cũ.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void xoa(@PathVariable Long id) {
        DanhMuc d = timHoacBao(id);
        if (NHOM_VAT_TU.equals(d.getNhom())) {
            vatTuRepo.findByDaXoaFalseOrderByLoaiAscIdAsc().stream()
                    .filter(v -> id.equals(v.getDanhMucId()))
                    .forEach(v -> { v.setDanhMucId(null); vatTuRepo.save(v); });
        } else {
            sanPhamRepo.findByDaXoaFalseOrderByIdAsc().stream()
                    .filter(sp -> id.equals(sp.getDanhMucId()))
                    .forEach(sp -> { sp.setDanhMucId(null); sanPhamRepo.save(sp); });
        }
        d.xoaMem();
        repo.save(d);
    }

    @PutMapping("/{id}/khoi-phuc")
    public DanhMuc khoiPhuc(@PathVariable Long id) {
        DanhMuc d = timHoacBao(id);
        d.khoiPhuc();
        return repo.save(d);
    }

    /** Tính chất trống -> khac; ngoài danh sách -> báo lỗi rõ. */
    private String chuanHoaTinhChat(String tc) {
        if (tc == null || tc.isBlank()) return "khac";
        if (!TINH_CHAT.contains(tc)) {
            throw loi400("Tính chất vật tư chỉ nhận: " + String.join(", ", TINH_CHAT));
        }
        return tc;
    }

    private DanhMuc timHoacBao(Long id) {
        return repo.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục."));
    }

    private ResponseStatusException loi400(String thongBao) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, thongBao);
    }

    private Integer so(Object v) {
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception e) { return 0; }
    }

    private String chuoi(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
