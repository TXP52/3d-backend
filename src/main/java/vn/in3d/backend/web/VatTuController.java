package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.in3d.backend.entity.NhaCungCap;
import vn.in3d.backend.entity.VatTu;
import vn.in3d.backend.repository.NhaCungCapRepository;
import vn.in3d.backend.repository.VatTuRepository;

import java.util.List;
import java.util.Map;

/** API kho vật tư (máy in, cuộn nhựa...) và nhà cung cấp. */
@RestController
@RequestMapping("/api")
public class VatTuController {

    /** Trạng thái vật tư hợp lệ: đặt mua → vận chuyển → về kho → hết. */
    private static final java.util.Set<String> TRANG_THAI =
            java.util.Set.of("da_dat", "dang_van_chuyen", "thanh_cong", "het_hang");

    private final VatTuRepository vatTuRepo;
    private final NhaCungCapRepository nccRepo;
    private final vn.in3d.backend.repository.MauSacRepository mauSacRepo;

    public VatTuController(VatTuRepository vatTuRepo, NhaCungCapRepository nccRepo,
                           vn.in3d.backend.repository.MauSacRepository mauSacRepo) {
        this.vatTuRepo = vatTuRepo;
        this.nccRepo = nccRepo;
        this.mauSacRepo = mauSacRepo;
    }

    /* ---------------- Vật tư ---------------- */

    @GetMapping("/vat-tu")
    public List<Map<String, Object>> danhSach() {
        return vatTuRepo.findByDaXoaFalseOrderByLoaiAscIdAsc().stream().map(v -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("ten", v.getTen());
            m.put("loai", v.getLoai());
            m.put("mau", v.getMau());
            m.put("mauSacId", v.getMauSacId());
            m.put("maMau", v.getMauSacId() == null ? null :
                    mauSacRepo.findById(v.getMauSacId())
                              .map(vn.in3d.backend.entity.MauSac::getMaMau).orElse(null));
            m.put("gia", v.getGia());
            m.put("soLuong", v.getSoLuong());
            m.put("khoiLuongGram", v.getKhoiLuongGram());
            m.put("daDungGram", v.getDaDungGram());
            m.put("trangThai", v.getTrangThai());
            m.put("hinhAnh", v.getHinhAnh());
            m.put("nhaCungCapId", v.getNhaCungCapId());
            m.put("ghiChu", v.getGhiChu());
            // Các số tính sẵn cho frontend khỏi tính lại
            m.put("tongTienMua", v.getTongTienMua());
            m.put("donGiaMoiGram", v.getDonGiaMoiGram());
            m.put("tienDaDung", v.getTienDaDung());
            m.put("conLaiGram", v.getConLaiGram());
            m.put("nhaCungCap", v.getNhaCungCapId() == null ? null :
                    nccRepo.findById(v.getNhaCungCapId()).map(NhaCungCap::getTen).orElse(null));
            m.put("createdAt", v.getCreatedAt());
            m.put("updatedAt", v.getUpdatedAt());
            m.put("daXoa", v.getDaXoa());
            return m;
        }).toList();
    }

    @PostMapping("/vat-tu")
    @ResponseStatus(HttpStatus.CREATED)
    public VatTu them(@RequestBody VatTu vt) {
        vt.setId(null);
        kiemTraTrangThai(vt.getTrangThai());
        if (vt.getMauSacId() != null) {
            mauSacRepo.findById(vt.getMauSacId()).ifPresent(ms -> vt.setMau(ms.getTen()));
        }
        return vatTuRepo.save(vt);
    }

    /** Cập nhật: gia, soLuong, daDungGram, khoiLuongGram, mau, ten, trangThai, hinhAnh, nhaCungCapId, ghiChu. */
    @PutMapping("/vat-tu/{id}")
    public VatTu capNhat(@PathVariable Long id, @RequestBody Map<String, Object> td) {
        VatTu vt = vatTuRepo.findById(id).orElseThrow();
        if (td.containsKey("ten")) vt.setTen(String.valueOf(td.get("ten")));
        if (td.containsKey("mau")) vt.setMau(String.valueOf(td.get("mau")));
        if (td.containsKey("loai")) vt.setLoai(String.valueOf(td.get("loai")));
        if (td.containsKey("ghiChu")) vt.setGhiChu(String.valueOf(td.get("ghiChu")));
        if (td.containsKey("hinhAnh")) {
            Object a = td.get("hinhAnh");
            vt.setHinhAnh(a == null ? null : String.valueOf(a));
        }
        if (td.containsKey("trangThai")) {
            String tt = String.valueOf(td.get("trangThai"));
            kiemTraTrangThai(tt);
            vt.setTrangThai(tt);
        }
        if (td.containsKey("gia")) vt.setGia(so(td.get("gia")));
        if (td.containsKey("soLuong")) vt.setSoLuong(so(td.get("soLuong")).intValue());
        if (td.containsKey("khoiLuongGram")) vt.setKhoiLuongGram(so(td.get("khoiLuongGram")).intValue());
        if (td.containsKey("daDungGram")) vt.setDaDungGram(Math.max(0, so(td.get("daDungGram")).intValue()));
        if (td.containsKey("mauSacId")) {
            Object v = td.get("mauSacId");
            if (v == null || String.valueOf(v).isBlank()) {
                vt.setMauSacId(null);
            } else {
                Long mid = so(v);
                vt.setMauSacId(mid);
                // Chép tên màu sang cột mau để danh sách cũ vẫn đọc được
                mauSacRepo.findById(mid).ifPresent(ms -> vt.setMau(ms.getTen()));
            }
        }
        if (td.containsKey("nhaCungCapId")) {
            Object v = td.get("nhaCungCapId");
            vt.setNhaCungCapId(v == null || String.valueOf(v).isBlank() ? null : so(v));
        }
        return vatTuRepo.save(vt);
    }

    /** Ghi nhận vừa in hết thêm N gram nhựa (cộng dồn vào daDungGram). */
    @PutMapping("/vat-tu/{id}/dung-them")
    public VatTu dungThem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        VatTu vt = vatTuRepo.findById(id).orElseThrow();
        int them = so(body.getOrDefault("gram", 0)).intValue();
        vt.setDaDungGram(Math.max(0, vt.getDaDungGram() + them));
        return vatTuRepo.save(vt);
    }

    @DeleteMapping("/vat-tu/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        VatTu vt = vatTuRepo.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy vật tư."));
        vt.xoaMem();
        vatTuRepo.save(vt);
    }

    /* ---------------- Nhà cung cấp ---------------- */

    @GetMapping("/nha-cung-cap")
    public List<NhaCungCap> danhSachNcc() {
        return nccRepo.findByDaXoaFalseOrderByIdAsc();
    }

    @PostMapping("/nha-cung-cap")
    @ResponseStatus(HttpStatus.CREATED)
    public NhaCungCap themNcc(@RequestBody NhaCungCap ncc) {
        ncc.setId(null);
        return nccRepo.save(ncc);
    }

    @PutMapping("/nha-cung-cap/{id}")
    public NhaCungCap suaNcc(@PathVariable Long id, @RequestBody Map<String, String> td) {
        NhaCungCap n = nccRepo.findById(id).orElseThrow();
        if (td.containsKey("ten")) n.setTen(td.get("ten"));
        if (td.containsKey("lienHe")) n.setLienHe(td.get("lienHe"));
        if (td.containsKey("ghiChu")) n.setGhiChu(td.get("ghiChu"));
        return nccRepo.save(n);
    }

    @DeleteMapping("/nha-cung-cap/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoaNcc(@PathVariable Long id) {
        NhaCungCap n = nccRepo.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy nhà cung cấp."));
        n.xoaMem();
        nccRepo.save(n);
    }

    private void kiemTraTrangThai(String tt) {
        if (tt == null || tt.isBlank()) return; // để mặc định
        if (!TRANG_THAI.contains(tt)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái vật tư không hợp lệ. Chỉ nhận: " + String.join(", ", TRANG_THAI));
        }
    }

    private Long so(Object v) {
        try { return Long.parseLong(String.valueOf(v).trim()); } catch (Exception e) { return 0L; }
    }
}
