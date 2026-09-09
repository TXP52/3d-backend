package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * Trạng thái vật tư hợp lệ.
     * con_hang / sap_het / het_hang nói về lượng còn dùng được;
     * da_dat / dang_van_chuyen là hàng đã mua nhưng chưa về tới kho.
     * "thanh_cong" là tên cũ của con_hang, vẫn nhận để dữ liệu cũ không lỗi.
     */
    private static final java.util.Set<String> TRANG_THAI =
            java.util.Set.of("con_hang", "sap_het", "het_hang", "da_dat", "dang_van_chuyen");

    /** Đổi tên trạng thái cũ sang tên mới. */
    private static String chuanHoaTrangThai(String tt) {
        if (tt == null || tt.isBlank()) return null;
        return "thanh_cong".equals(tt) ? "con_hang" : tt;
    }

    private final VatTuRepository vatTuRepo;
    private final NhaCungCapRepository nccRepo;
    private final vn.in3d.backend.repository.MauSacRepository mauSacRepo;
    private final vn.in3d.backend.repository.DanhMucRepository danhMucRepo;

    public VatTuController(VatTuRepository vatTuRepo, NhaCungCapRepository nccRepo,
                           vn.in3d.backend.repository.MauSacRepository mauSacRepo,
                           vn.in3d.backend.repository.DanhMucRepository danhMucRepo) {
        this.vatTuRepo = vatTuRepo;
        this.nccRepo = nccRepo;
        this.mauSacRepo = mauSacRepo;
        this.danhMucRepo = danhMucRepo;
    }

    /* ---------------- Vật tư ---------------- */

    /**
     * Danh sách vật tư kèm tên màu / nhà cung cấp / loại — MỘT truy vấn (xem
     * VatTuRepository.danhSachKemTen). Bản cũ gọi findById ba lần cho MỖI dòng:
     * database Supabase ở xa nên 10 vật tư mất 17 giây — chính là cái "load rất chậm".
     */
    // Không bọc @Transactional: chỉ MỘT truy vấn, mở transaction là thêm một lượt BEGIN/COMMIT (~300 ms)
    @GetMapping("/vat-tu")
    public List<Map<String, Object>> danhSach() {
        return vatTuRepo.danhSachKemTen().stream().map(dong -> {
            VatTu v = (VatTu) dong[0];
            String maMauTheoId = (String) dong[1];
            String maMauTheoTen = (String) dong[2];
            String maMau = maMauTheoId != null && !maMauTheoId.isBlank() ? maMauTheoId : maMauTheoTen;

            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("ten", v.getTen());
            m.put("loai", v.getLoai());
            m.put("danhMucId", v.getDanhMucId());
            m.put("danhMuc", dong[4]);
            m.put("mau", v.getMau());
            m.put("mauSacId", v.getMauSacId());
            // Mã màu: theo id đã trỏ; bản ghi cũ chỉ có TÊN màu thì tra theo tên
            m.put("maMau", maMau);
            m.put("gia", v.getGia());
            m.put("soLuong", v.getSoLuong());
            m.put("khoiLuongGram", v.getKhoiLuongGram());
            m.put("daDungGram", v.getDaDungGram());
            m.put("trangThai", v.getTrangThai());
            // Trạng thái suy từ số gram còn lại — frontend hiển thị cái này
            m.put("trangThaiTinh", v.getTrangThaiTinh());
            m.put("hinhAnh", v.getHinhAnh());
            m.put("nhaCungCapId", v.getNhaCungCapId());
            m.put("ghiChu", v.getGhiChu());
            // Các số tính sẵn cho frontend khỏi tính lại
            m.put("tongTienMua", v.getTongTienMua());
            m.put("donGiaMoiGram", v.getDonGiaMoiGram());
            m.put("tienDaDung", v.getTienDaDung());
            m.put("conLaiGram", v.getConLaiGram());
            m.put("nhaCungCap", dong[3]);
            m.put("createdAt", v.getCreatedAt());
            m.put("updatedAt", v.getUpdatedAt());
            m.put("daXoa", v.getDaXoa());
            return m;
        }).toList();
    }

    @PostMapping("/vat-tu")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public VatTu them(@RequestBody VatTu vt) {
        vt.setId(null);
        vt.setTrangThai(chuanHoaTrangThai(vt.getTrangThai()));
        kiemTraTrangThai(vt.getTrangThai());
        if (vt.getTrangThai() == null) vt.setTrangThai("con_hang");
        apDungDanhMuc(vt);
        if (vt.getMauSacId() != null) {
            mauSacRepo.findById(vt.getMauSacId()).ifPresent(ms -> vt.setMau(ms.getTen()));
        }
        return vatTuRepo.save(vt);
    }

    /** Cập nhật: gia, soLuong, daDungGram, khoiLuongGram, mau, ten, trangThai, hinhAnh, nhaCungCapId, ghiChu. */
    @PutMapping("/vat-tu/{id}")
    @Transactional
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
            String tt = chuanHoaTrangThai(String.valueOf(td.get("trangThai")));
            kiemTraTrangThai(tt);
            if (tt != null) vt.setTrangThai(tt);
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
        if (td.containsKey("danhMucId")) {
            Object v = td.get("danhMucId");
            vt.setDanhMucId(v == null || String.valueOf(v).isBlank() || "null".equals(String.valueOf(v)) ? null : so(v));
            apDungDanhMuc(vt);
        }
        if (td.containsKey("nhaCungCapId")) {
            Object v = td.get("nhaCungCapId");
            vt.setNhaCungCapId(v == null || String.valueOf(v).isBlank() ? null : so(v));
        }
        return vatTuRepo.save(vt);
    }

    /** Ghi nhận vừa in hết thêm N gram nhựa (cộng dồn vào daDungGram). */
    @PutMapping("/vat-tu/{id}/dung-them")
    @Transactional
    public VatTu dungThem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        VatTu vt = vatTuRepo.findById(id).orElseThrow();
        int them = so(body.getOrDefault("gram", 0)).intValue();
        vt.setDaDungGram(Math.max(0, vt.getDaDungGram() + them));
        return vatTuRepo.save(vt);
    }

    @DeleteMapping("/vat-tu/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
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

    /**
     * Chép tính chất của loại (danh_muc.tinh_chat) sang cột loai. Nhờ vậy chủ shop
     * đặt tên loại tuỳ ý mà kho vẫn biết đâu là nhựa để tính gram, đâu là máy in.
     */
    private void apDungDanhMuc(VatTu vt) {
        if (vt.getDanhMucId() == null) return;
        vn.in3d.backend.entity.DanhMuc dm = danhMucRepo.findById(vt.getDanhMucId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Loại vật tư không tồn tại."));
        if (!DanhMucController.NHOM_VAT_TU.equals(dm.getNhom())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "\"" + dm.getTen() + "\" là danh mục sản phẩm, không dùng làm loại vật tư được.");
        }
        String tc = dm.getTinhChat();
        vt.setLoai(tc != null && DanhMucController.TINH_CHAT.contains(tc) ? tc : "khac");
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
