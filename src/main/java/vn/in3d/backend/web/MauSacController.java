package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.MauSac;
import vn.in3d.backend.repository.MauSacRepository;
import vn.in3d.backend.service.BoNhoDem;

import java.util.List;
import java.util.Map;

/** API bảng màu sắc dùng cho nhựa in và sản phẩm. */
@RestController
@RequestMapping("/api/mau-sac")
public class MauSacController {

    private final MauSacRepository repo;
    private final BoNhoDem boNho;
    private final TransactionTemplate giaoDich;
    private final JdbcTemplate jdbc;

    public MauSacController(MauSacRepository repo, BoNhoDem boNho, TransactionTemplate giaoDich, JdbcTemplate jdbc) {
        this.repo = repo;
        this.boNho = boNho;
        this.giaoDich = giaoDich;
        this.jdbc = jdbc;
    }

    /** Danh sách màu chưa bị xoá, theo thứ tự hiển thị (từ bộ nhớ đệm). */
    @GetMapping
    public List<MauSac> danhSach() {
        return boNho.dsMauSac();
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
        MauSac daLuu = repo.save(m);
        return traMau(daLuu.getId(), daLuu);
    }

    /** Sửa trong MỘT transaction: đọc + UPDATE + COMMIT (bản cũ đọc rồi merge riêng: 5 lượt đi-về). */
    @PutMapping("/{id}")
    public MauSac sua(@PathVariable Long id, @RequestBody Map<String, Object> td) {
        MauSac daSua = giaoDich.execute(gd -> {
            MauSac m = repo.findById(id).orElseThrow(this::khongThay);
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
            return m;
        });
        return traMau(id, daSua);
    }

    /** XOÁ MỀM: màu vẫn nằm trong database để vật tư cũ còn tra ngược được. MỘT lệnh UPDATE. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        if (jdbc.update("update mau_sac set is_deleted = true, updated_at = now() where id = ?", id) == 0) {
            throw khongThay();
        }
        boNho.xoaVaNapLai(BoNhoDem.MS, BoNhoDem.VT, BoNhoDem.SP);
    }

    @PutMapping("/{id}/khoi-phuc")
    public MauSac khoiPhuc(@PathVariable Long id) {
        if (jdbc.update("update mau_sac set is_deleted = false, updated_at = now() where id = ?", id) == 0) {
            throw khongThay();
        }
        return traMau(id, null);
    }

    /**
     * Đã commit: nạp lại màu + kho + sản phẩm (hai bảng kia hiện tên / mã màu) rồi trả dòng danh sách.
     *
     * Nạp lại lỗi (Supabase chớp một nhịp) thì KHÔNG đọc lại bộ nhớ đệm nữa: đọc là mở thêm
     * một lượt nạp, lỗi lần hai thì ném ra ngoài và lệnh ghi ĐÃ COMMIT lại thành lỗi 500 —
     * chủ shop bấm Lưu lại là có hai màu trùng. Trả luôn dòng vừa ghi trong transaction;
     * trang quản trị hỏi lại khoá của nó ngay sau đó nên vẫn thấy số mới.
     *
     * @param duPhong dòng vừa ghi, null nếu nơi gọi không có (chỉ chạy một lệnh UPDATE)
     */
    private MauSac traMau(Long id, MauSac duPhong) {
        if (boNho.xoaVaNapLai(BoNhoDem.MS, BoNhoDem.VT, BoNhoDem.SP)) {
            try {
                MauSac m = boNho.mauSac().theoId().get(id);
                if (m != null) return m;
            } catch (RuntimeException boQua) {
                // rơi xuống dùng bản dự phòng
            }
        }
        return duPhong;
    }

    private ResponseStatusException khongThay() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy màu.");
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
