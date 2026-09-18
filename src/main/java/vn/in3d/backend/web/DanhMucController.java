package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.dto.DanhMucDto;
import vn.in3d.backend.entity.DanhMuc;
import vn.in3d.backend.repository.DanhMucRepository;
import vn.in3d.backend.service.BoNhoDem;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API danh mục — MỘT bảng, hai nhóm (cột nhom):
 *   san_pham : danh mục sản phẩm cho khách lọc trên website
 *   vat_tu   : loại vật tư trong kho (Máy in, Nhựa in...), thay cho danh sách
 *              trước đây viết cứng trong giao diện.
 *
 * Với nhóm vat_tu, tinh_chat (may_in | nhua | dung_cu) cho backend biết
 * vật tư nào là NHỰA (theo dõi gram, sắp hết) hay MÁY IN (tính vào vốn máy) —
 * chủ shop đặt tên loại tuỳ ý ("Nhựa PLA", "Nhựa PETG"), tính chất vẫn đúng.
 */
@RestController
@RequestMapping("/api/danh-muc")
public class DanhMucController {

    public static final String NHOM_SAN_PHAM = "san_pham";
    public static final String NHOM_VAT_TU = "vat_tu";
    private static final Set<String> NHOM = Set.of(NHOM_SAN_PHAM, NHOM_VAT_TU);
    public static final Set<String> TINH_CHAT = Set.of("may_in", "nhua", "dung_cu");

    private final DanhMucRepository repo;
    private final BoNhoDem boNho;
    private final TransactionTemplate giaoDich;
    private final JdbcTemplate jdbc;

    public DanhMucController(DanhMucRepository repo, BoNhoDem boNho, TransactionTemplate giaoDich, JdbcTemplate jdbc) {
        this.repo = repo;
        this.boNho = boNho;
        this.giaoDich = giaoDich;
        this.jdbc = jdbc;
    }

    /**
     * Danh sách danh mục (từ bộ nhớ đệm).
     * ?nhom=san_pham hoặc ?nhom=vat_tu để lọc một nhóm; bỏ trống trả cả hai.
     * ?tatCa=false chỉ trả danh mục đang bật (cho website khách).
     * ?kemSoLuong=true mới đếm soSanPham / soVatTu — đếm trên bộ nhớ đệm sản phẩm và kho.
     */
    @GetMapping
    public List<Map<String, Object>> danhSach(@RequestParam(defaultValue = "true") boolean tatCa,
                                              @RequestParam(required = false) String nhom,
                                              @RequestParam(defaultValue = "false") boolean kemSoLuong) {
        return boNho.dsDanhMuc(tatCa, nhom, kemSoLuong);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> them(@RequestBody DanhMuc d) {
        DanhMuc daLuu = giaoDich.execute(gd -> {
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
        });
        return traDanhMuc(daLuu.getId(), daLuu);
    }

    /**
     * Sửa tên / mô tả / icon / thứ tự / ẩn-hiện / tính chất.
     * Không cho đổi nhóm: danh mục đã có sản phẩm hay vật tư trỏ tới, đổi nhóm là
     * liên kết đó vô nghĩa — muốn chuyển thì tạo dòng mới ở nhóm kia.
     */
    @PutMapping("/{id}")
    public Map<String, Object> sua(@PathVariable Long id, @RequestBody Map<String, Object> td) {
        DanhMuc daSua = giaoDich.execute(gd -> {
            DanhMuc d = timHoacBao(id);
            if (td.containsKey("ten")) {
                String ten = String.valueOf(td.get("ten")).trim();
                if (ten.isEmpty()) throw loi400("Tên danh mục không được để trống.");
                // Form luôn gửi lại tên: không đổi tên thì khỏi hỏi trùng
                if (!ten.equals(d.getTen())) {
                    repo.findByTenIgnoreCaseAndNhomAndDaXoaFalse(ten, d.getNhom()).ifPresent(cu -> {
                        if (!cu.getId().equals(id)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Tên \"" + cu.getTen() + "\" đã có trong nhóm này.");
                        }
                    });
                }
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
                    // Vật tư đang thuộc loại này đổi tính chất theo, để cột loai không lệch — MỘT lệnh UPDATE
                    jdbc.update("update vat_tu set loai = ?, updated_at = now() "
                            + "where danh_muc_id = ? and is_deleted = false", moi, id);
                }
            }
            return d;
        });
        return traDanhMuc(id, daSua);
    }

    /**
     * XOÁ MỀM. Sản phẩm / vật tư đang thuộc được gỡ liên kết chứ không xoá theo.
     * Vật tư giữ nguyên cột loai nên kho vẫn tính gram, tính vốn máy như cũ.
     * Cả ba việc (xoá danh mục, gỡ vật tư HOẶC gỡ sản phẩm tuỳ nhóm) trong MỘT câu lệnh.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        Long soDong = jdbc.queryForObject(
                "with d as (update danh_muc set is_deleted = true, updated_at = now() where id = ? returning nhom), "
                + "vt as (update vat_tu set danh_muc_id = null, updated_at = now() "
                + "       where danh_muc_id = ? and is_deleted = false "
                + "         and exists (select 1 from d where d.nhom = 'vat_tu')), "
                + "sp as (update san_pham set danh_muc_id = null, updated_at = now() "
                + "       where danh_muc_id = ? and is_deleted = false "
                + "         and exists (select 1 from d where d.nhom <> 'vat_tu')) "
                + "select count(*) from d", Long.class, id, id, id);
        if (soDong == null || soDong == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục.");
        }
        boNho.xoaVaNapLai(BoNhoDem.DM, BoNhoDem.VT, BoNhoDem.SP);
    }

    @PutMapping("/{id}/khoi-phuc")
    public Map<String, Object> khoiPhuc(@PathVariable Long id) {
        if (jdbc.update("update danh_muc set is_deleted = false, updated_at = now() where id = ?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục.");
        }
        return traDanhMuc(id, null);
    }

    /**
     * Đã commit: nạp lại danh mục + kho + sản phẩm (hai bảng kia hiện tên danh mục) rồi trả dòng danh sách.
     *
     * Nạp lại lỗi (Supabase chớp một nhịp) thì KHÔNG đọc lại bộ nhớ đệm nữa: đọc là mở thêm
     * một lượt nạp, lỗi lần hai thì ném ra ngoài và lệnh ghi ĐÃ COMMIT lại thành lỗi 500 —
     * chủ shop bấm Lưu lại là có hai danh mục trùng. Trả luôn dòng vừa ghi trong transaction.
     *
     * @param duPhong dòng vừa ghi, null nếu nơi gọi không có (chỉ chạy một lệnh UPDATE)
     */
    private Map<String, Object> traDanhMuc(Long id, DanhMuc duPhong) {
        if (boNho.xoaVaNapLai(BoNhoDem.DM, BoNhoDem.VT, BoNhoDem.SP)) {
            try {
                DanhMuc d = boNho.danhMuc().theoId().get(id);
                if (d != null) return DanhMucDto.tao(d);
            } catch (RuntimeException boQua) {
                // rơi xuống dùng bản dự phòng
            }
        }
        return duPhong == null ? null : DanhMucDto.tao(duPhong);
    }

    /** Tính chất trống -> dung_cu; ngoài danh sách -> báo lỗi rõ. */
    private String chuanHoaTinhChat(String tc) {
        if (tc == null || tc.isBlank()) return "dung_cu";
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
