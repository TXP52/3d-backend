package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import vn.in3d.backend.entity.NhaCungCap;
import vn.in3d.backend.entity.VatTu;
import vn.in3d.backend.repository.NhaCungCapRepository;
import vn.in3d.backend.repository.VatTuRepository;
import vn.in3d.backend.service.BoNhoDem;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final BoNhoDem boNho;
    private final TransactionTemplate giaoDich;
    private final JdbcTemplate jdbc;

    public VatTuController(VatTuRepository vatTuRepo, NhaCungCapRepository nccRepo,
                           vn.in3d.backend.repository.MauSacRepository mauSacRepo,
                           vn.in3d.backend.repository.DanhMucRepository danhMucRepo,
                           BoNhoDem boNho, TransactionTemplate giaoDich, JdbcTemplate jdbc) {
        this.vatTuRepo = vatTuRepo;
        this.nccRepo = nccRepo;
        this.mauSacRepo = mauSacRepo;
        this.danhMucRepo = danhMucRepo;
        this.boNho = boNho;
        this.giaoDich = giaoDich;
        this.jdbc = jdbc;
    }

    /* ---------------- Vật tư ---------------- */

    /**
     * Danh sách vật tư kèm tên màu / nhà cung cấp / loại — lấy từ bộ nhớ đệm, dựng
     * sẵn bằng MỘT truy vấn nối bảng (VatTuRepository.napKemTen, VatTuDto).
     * Bản cũ gọi findById ba lần cho MỖI dòng: database Supabase ở xa nên 10 vật tư
     * mất 17 giây — chính là cái "load rất chậm".
     */
    @GetMapping("/vat-tu")
    public List<Map<String, Object>> danhSach() {
        return boNho.dsVatTu();
    }

    /**
     * Thêm vật tư. Trả dòng danh sách (kèm tên màu / nhà cung cấp / loại).
     *
     * Form gửi kèm "loNhap" (mảng các đợt nhập) thì số lượng / giá / tổng tiền của vật tư
     * cộng từ các đợt đó; không gửi thì ô gia + soLuong thành ĐỢT NHẬP ĐẦU TIÊN, nhờ vậy
     * chỗ nào còn gọi kiểu cũ vẫn tạo ra dữ liệu đầy đủ.
     */
    @PostMapping("/vat-tu")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> them(@RequestBody Map<String, Object> td) {
        VatTu vt = docVatTuMoi(td);
        List<DotNhap> guiLen = docLoNhap(td);
        // Không gửi đợt nào: ô giá + số lượng của form cũ chính là đợt nhập đầu tiên
        List<DotNhap> dot = guiLen != null ? guiLen
                : List.of(new DotNhap(ngayNhapCua(vt), soDuong(vt.getSoLuong()),
                        vt.getGia() == null ? 0 : vt.getGia(), vt.getNhaCungCapId()));
        Long id = giaoDich.execute(gd -> {
            vt.setId(null);
            vt.setTrangThai(chuanHoaTrangThai(vt.getTrangThai()));
            kiemTraTrangThai(vt.getTrangThai());
            if (vt.getTrangThai() == null) vt.setTrangThai("con_hang");
            apDungDanhMuc(vt);
            if (vt.getMauSacId() != null && !mauSacRepo.existsById(vt.getMauSacId())) {
                throw new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Màu không tồn tại trong bảng màu.");
            }
            congTuLoNhap(vt, dot);
            Long moi = vatTuRepo.save(vt).getId();
            ghiLoNhap(moi, dot);
            return moi;
        });
        return traDongVatTu(id, vt);
    }

    /**
     * Cập nhật: gia, soLuong, daDungGram, khoiLuongGram, mauSacId, ten, trangThai, hinhAnh,
     * nhaCungCapId, ghiChu và loNhap (TOÀN BỘ danh sách đợt nhập sau khi sửa).
     * Form kho luôn gửi lại mauSacId + danhMucId: chỉ hỏi database khi giá trị THẬT SỰ đổi.
     */
    @PutMapping("/vat-tu/{id}")
    public Map<String, Object> capNhat(@PathVariable Long id, @RequestBody Map<String, Object> td) {
        List<DotNhap> dot = docLoNhap(td);     // null = không gửi phần đợt nhập -> để yên
        // Bản chụp danh mục lấy TRƯỚC khi mở transaction. Đọc bộ nhớ đệm ở trong
        // transaction thì lúc khoá DM vừa bị xoá, luồng nạp phải xin kết nối THỨ HAI
        // trong khi luồng request đang giữ một kết nối — pool đầy là treo rồi lỗi,
        // dù lệnh ghi chẳng có gì sai.
        Map<Long, vn.in3d.backend.entity.DanhMuc> dmTheoId = boNho.danhMuc().theoId();
        VatTu daSua = giaoDich.execute(gd -> {
            VatTu vt = vatTuRepo.findById(id).orElseThrow(this::khongThayVatTu);
            Long mauCu = vt.getMauSacId();
            Long danhMucCu = vt.getDanhMucId();
            if (td.containsKey("ten")) vt.setTen(String.valueOf(td.get("ten")));
            // Không nhận "mau" dạng chữ nữa — màu đặt bằng mauSacId, trỏ sang bảng mau_sac
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
            // Có gửi các đợt nhập thì giá / số lượng / tổng tiền cộng từ chúng, hai ô rời
            // gửi kèm (nếu có) bị bỏ qua — nếu không hai nguồn số sẽ đá nhau
            if (dot != null) {
                congTuLoNhap(vt, dot);
                ghiLoNhap(id, dot);
            } else {
                if (td.containsKey("gia")) vt.setGia(so(td.get("gia")));
                if (td.containsKey("soLuong")) vt.setSoLuong(so(td.get("soLuong")).intValue());
                if (td.containsKey("gia") || td.containsKey("soLuong")) {
                    // Sửa kiểu cũ (một giá, một số lượng): đợt nhập duy nhất đi theo cho khớp
                    vt.setTienMua((vt.getGia() == null ? 0 : vt.getGia())
                            * (vt.getSoLuong() == null ? 0 : vt.getSoLuong()));
                    ghiLoNhap(id, List.of(new DotNhap(ngayNhapCua(vt), soDuong(vt.getSoLuong()),
                            vt.getGia() == null ? 0 : vt.getGia(), vt.getNhaCungCapId())));
                }
            }
            if (td.containsKey("khoiLuongGram")) vt.setKhoiLuongGram(so(td.get("khoiLuongGram")).intValue());
            if (td.containsKey("daDungGram")) vt.setDaDungGram(Math.max(0, so(td.get("daDungGram")).intValue()));
            if (td.containsKey("mauSacId")) {
                Object v = td.get("mauSacId");
                if (v == null || String.valueOf(v).isBlank()) {
                    vt.setMauSacId(null);
                } else {
                    Long mid = so(v);
                    // Vẫn màu cũ thì khỏi kiểm tra: nó đã hợp lệ lúc gán (màu chỉ xoá mềm)
                    if (!mid.equals(mauCu) && !mauSacRepo.existsById(mid)) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Màu không tồn tại trong bảng màu.");
                    }
                    vt.setMauSacId(mid);
                }
            }
            if (td.containsKey("danhMucId")) {
                Object v = td.get("danhMucId");
                vt.setDanhMucId(v == null || String.valueOf(v).isBlank() || "null".equals(String.valueOf(v)) ? null : so(v));
                if (Objects.equals(vt.getDanhMucId(), danhMucCu)) {
                    // Vẫn loại cũ: lấy tính chất từ bản chụp danh mục, khỏi một lượt hỏi database
                    apDungDanhMucDaBiet(vt, dmTheoId);
                } else {
                    apDungDanhMuc(vt);
                }
            }
            if (td.containsKey("nhaCungCapId")) {
                Object v = td.get("nhaCungCapId");
                vt.setNhaCungCapId(v == null || String.valueOf(v).isBlank() ? null : so(v));
            }
            // vt đang được quản lý trong transaction: commit tự ghi, khỏi gọi save
            return vt;
        });
        return traDongVatTu(id, daSua);
    }

    /* ---------------- Đợt nhập hàng ----------------
       Một vật tư mua nhiều lần, mỗi lần một giá: mỗi lần là một dòng lo_nhap.
       Vật tư giữ ba số cộng từ các đợt — soLuong (tổng), tienMua (tổng tiền, chính xác)
       và gia (đơn giá BÌNH QUÂN, làm tròn, chỉ để hiện) — nên mọi chỗ tính vốn đang đọc
       vat_tu chạy y như trước khi có bảng này. */

    /** Một đợt nhập form gửi lên (chưa có id: ghi là xoá hết rồi chép lại cả danh sách). */
    private record DotNhap(LocalDate ngayNhap, int soLuong, long gia, Long nhaCungCapId) {}

    /**
     * Đọc mảng "loNhap" của form: null = không gửi (PUT thì để yên các đợt đang có).
     * Mỗi đợt cần số lượng ≥ 1 và đơn giá ≥ 0; ngày để trống thì tính là hôm nay.
     */
    private List<DotNhap> docLoNhap(Map<String, Object> td) {
        if (!td.containsKey("loNhap")) return null;
        if (!(td.get("loNhap") instanceof List<?> tho)) return List.of();
        List<DotNhap> ra = new java.util.ArrayList<>();
        for (Object o : tho) {
            if (!(o instanceof Map<?, ?> m)) continue;
            int sl = (int) soCoDau(m.get("soLuong"));
            long gia = soCoDau(m.get("gia"));
            if (sl < 1) throw loiXau("Số lượng của mỗi đợt nhập phải từ 1 trở lên.");
            if (gia < 0) throw loiXau("Đơn giá của đợt nhập không được là số âm.");
            ra.add(new DotNhap(ngay(m.get("ngayNhap")), sl, gia, idHoacNull(m.get("nhaCungCapId"))));
        }
        if (ra.isEmpty()) throw loiXau("Vật tư phải có ít nhất một đợt nhập.");
        return List.copyOf(ra);
    }

    /** Cộng các đợt vào vật tư: tổng số lượng, tổng tiền và đơn giá bình quân (làm tròn). */
    private static void congTuLoNhap(VatTu vt, List<DotNhap> dot) {
        long sl = 0, tien = 0;
        for (DotNhap d : dot) {
            sl += d.soLuong();
            tien += d.gia() * d.soLuong();
        }
        vt.setSoLuong((int) sl);
        vt.setTienMua(tien);
        vt.setGia(sl > 0 ? Math.round((double) tien / sl) : 0L);
    }

    /**
     * Ghi lại TOÀN BỘ các đợt nhập của một vật tư: xoá hết rồi chép lại danh sách mới
     * (hai lệnh, mỗi lệnh một lượt đi-về — sửa từng dòng thì mỗi dòng một lượt).
     * Đợt nhập không được nơi nào trỏ tới nên đánh số lại không ảnh hưởng gì.
     */
    private void ghiLoNhap(Long vatTuId, List<DotNhap> dot) {
        jdbc.update("delete from lo_nhap where vat_tu_id = ?", vatTuId);
        if (dot.isEmpty()) return;
        List<Object[]> thamSo = new java.util.ArrayList<>();
        for (DotNhap d : dot) {
            thamSo.add(new Object[]{vatTuId, java.sql.Date.valueOf(d.ngayNhap()),
                    d.soLuong(), d.gia(), d.nhaCungCapId()});
        }
        jdbc.batchUpdate("insert into lo_nhap (vat_tu_id, ngay_nhap, so_luong, gia, nha_cung_cap_id) "
                + "values (?, ?, ?, ?, ?)", thamSo);
    }

    /** Ngày nhập của đợt duy nhất khi form cũ chỉ gửi giá + số lượng: ngày tạo vật tư, không có thì hôm nay. */
    private static LocalDate ngayNhapCua(VatTu vt) {
        return vt.getCreatedAt() == null ? LocalDate.now() : vt.getCreatedAt().toLocalDate();
    }

    private static int soDuong(Integer v) { return v == null || v < 1 ? 1 : v; }

    private static LocalDate ngay(Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        if (s.isEmpty()) return LocalDate.now();
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (RuntimeException sai) {
            throw loiXau("Ngày nhập không hợp lệ (cần dạng 2026-09-24).");
        }
    }

    /** Số nguyên có thể âm (để bắt lỗi "đơn giá âm"); không phải số thì coi là 0. */
    private static long soCoDau(Object v) {
        try { return Long.parseLong(String.valueOf(v).trim()); } catch (Exception e) { return 0L; }
    }

    private Long idHoacNull(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private static org.springframework.web.server.ResponseStatusException loiXau(String chu) {
        return new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, chu);
    }

    /** Vật tư mới dựng từ JSON của form (trước đây Spring tự đổ vào entity). */
    private VatTu docVatTuMoi(Map<String, Object> td) {
        VatTu vt = new VatTu();
        if (td.get("ten") != null) vt.setTen(String.valueOf(td.get("ten")).trim());
        if (td.get("loai") != null) vt.setLoai(String.valueOf(td.get("loai")));
        vt.setMauSacId(idHoacNull(td.get("mauSacId")));
        vt.setDanhMucId(idHoacNull(td.get("danhMucId")));
        vt.setNhaCungCapId(idHoacNull(td.get("nhaCungCapId")));
        if (td.containsKey("gia")) vt.setGia(so(td.get("gia")));
        if (td.containsKey("soLuong")) vt.setSoLuong(so(td.get("soLuong")).intValue());
        if (td.containsKey("khoiLuongGram")) vt.setKhoiLuongGram(so(td.get("khoiLuongGram")).intValue());
        if (td.containsKey("daDungGram")) vt.setDaDungGram(Math.max(0, so(td.get("daDungGram")).intValue()));
        if (td.get("trangThai") != null) vt.setTrangThai(String.valueOf(td.get("trangThai")));
        if (td.get("hinhAnh") != null) vt.setHinhAnh(String.valueOf(td.get("hinhAnh")));
        if (td.get("ghiChu") != null) vt.setGhiChu(String.valueOf(td.get("ghiChu")));
        return vt;
    }

    /** Ghi nhận vừa in hết thêm N gram nhựa (cộng dồn vào daDungGram) — MỘT lệnh UPDATE. */
    @PutMapping("/vat-tu/{id}/dung-them")
    public Map<String, Object> dungThem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int them = so(body.getOrDefault("gram", 0)).intValue();
        if (jdbc.update("update vat_tu set da_dung_gram = greatest(0, da_dung_gram + ?), updated_at = now() "
                + "where id = ?", them, id) == 0) {
            throw khongThayVatTu();
        }
        return traDongVatTu(id, null);
    }

    /** XOÁ MỀM — MỘT lệnh UPDATE, không mở transaction. */
    @DeleteMapping("/vat-tu/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        if (jdbc.update("update vat_tu set is_deleted = true, updated_at = now() where id = ?", id) == 0) {
            throw khongThayVatTu();
        }
        boNho.xoaVaNapLai(BoNhoDem.VT, BoNhoDem.SP);
    }

    /**
     * Đã commit: nạp lại kho + sản phẩm (sản phẩm hiện tên / màu / giá cuộn) rồi trả dòng danh sách.
     *
     * Lệnh ghi ĐÃ COMMIT nên nạp lại lỗi (Supabase chớp một nhịp) KHÔNG được thành lỗi 500:
     * đọc lại bộ nhớ đệm lúc đó là mở thêm một lượt nạp nữa, lỗi lần hai thì ném ra ngoài,
     * trang quản trị báo "Lỗi backend: HTTP 500" và chủ shop bấm Lưu lại — thành hai dòng kho.
     * Nạp không được thì dựng dòng từ chính entity transaction vừa ghi (tên màu / nhà cung cấp /
     * loại tra ở các bản chụp KHÔNG bị lệnh ghi này xoá); trang quản trị hỏi lại khoá của nó
     * ngay sau đó nên vẫn thấy số mới.
     *
     * @param duPhong entity vừa ghi, null nếu nơi gọi không có (chỉ chạy một lệnh UPDATE)
     */
    private Map<String, Object> traDongVatTu(Long id, VatTu duPhong) {
        if (boNho.xoaVaNapLai(BoNhoDem.VT, BoNhoDem.SP)) {
            try {
                Map<String, Object> dong = boNho.vatTu().theoId().get(id);
                if (dong != null) return dong;
            } catch (RuntimeException boQua) {
                // rơi xuống dùng bản dự phòng
            }
        }
        return duPhong == null ? Map.of("id", id) : dongVatTuDuPhong(duPhong);
    }

    /** Dòng danh sách dựng từ entity vừa ghi; thiếu tên tra kèm thì để null chứ không báo lỗi. */
    private Map<String, Object> dongVatTuDuPhong(VatTu vt) {
        String maMau = null, tenMau = null, tenNcc = null, tenDanhMuc = null;
        try {
            vn.in3d.backend.entity.MauSac ms = vt.getMauSacId() == null ? null
                    : boNho.mauSac().theoId().get(vt.getMauSacId());
            if (ms != null) { maMau = ms.getMaMau(); tenMau = ms.getTen(); }
            NhaCungCap ncc = vt.getNhaCungCapId() == null ? null
                    : boNho.nhaCungCap().theoId().get(vt.getNhaCungCapId());
            if (ncc != null) tenNcc = ncc.getTen();
            vn.in3d.backend.entity.DanhMuc dm = vt.getDanhMucId() == null ? null
                    : boNho.danhMuc().theoId().get(vt.getDanhMucId());
            if (dm != null) tenDanhMuc = dm.getTen();
        } catch (RuntimeException boQua) {
            // database đang chớp: thiếu mấy cái tên tra kèm vẫn hơn là báo lỗi lệnh đã ghi xong
        }
        return vn.in3d.backend.dto.VatTuDto.tao(vt, maMau, tenMau, tenNcc, tenDanhMuc);
    }

    private org.springframework.web.server.ResponseStatusException khongThayVatTu() {
        return new org.springframework.web.server.ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy vật tư.");
    }

    /* ---------------- Nhà cung cấp ---------------- */

    @GetMapping("/nha-cung-cap")
    public List<NhaCungCap> danhSachNcc() {
        return boNho.dsNhaCungCap();
    }

    @PostMapping("/nha-cung-cap")
    @ResponseStatus(HttpStatus.CREATED)
    public NhaCungCap themNcc(@RequestBody NhaCungCap ncc) {
        ncc.setId(null);
        NhaCungCap daLuu = nccRepo.save(ncc);
        return traNcc(daLuu.getId(), daLuu);
    }

    /** Sửa trong MỘT transaction: đọc + UPDATE + COMMIT (bản cũ đọc rồi merge riêng: 5 lượt). */
    @PutMapping("/nha-cung-cap/{id}")
    public NhaCungCap suaNcc(@PathVariable Long id, @RequestBody Map<String, String> td) {
        NhaCungCap daSua = giaoDich.execute(gd -> {
            NhaCungCap n = nccRepo.findById(id).orElseThrow(this::khongThayNcc);
            if (td.containsKey("ten")) n.setTen(td.get("ten"));
            if (td.containsKey("lienHe")) n.setLienHe(td.get("lienHe"));
            if (td.containsKey("ghiChu")) n.setGhiChu(td.get("ghiChu"));
            return n;
        });
        return traNcc(id, daSua);
    }

    /** XOÁ MỀM — MỘT lệnh UPDATE. */
    @DeleteMapping("/nha-cung-cap/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoaNcc(@PathVariable Long id) {
        if (jdbc.update("update nha_cung_cap set is_deleted = true, updated_at = now() where id = ?", id) == 0) {
            throw khongThayNcc();
        }
        boNho.xoaVaNapLai(BoNhoDem.NCC, BoNhoDem.VT);
    }

    /**
     * Đã commit: nạp lại nhà cung cấp + kho (kho hiện tên nhà cung cấp) rồi trả dòng danh sách.
     * Nạp lại lỗi thì trả chính dòng vừa ghi — xem ghi chú ở traDongVatTu.
     */
    private NhaCungCap traNcc(Long id, NhaCungCap duPhong) {
        if (boNho.xoaVaNapLai(BoNhoDem.NCC, BoNhoDem.VT)) {
            try {
                NhaCungCap n = boNho.nhaCungCap().theoId().get(id);
                if (n != null) return n;
            } catch (RuntimeException boQua) {
                // rơi xuống dùng bản dự phòng
            }
        }
        return duPhong;
    }

    private org.springframework.web.server.ResponseStatusException khongThayNcc() {
        return new org.springframework.web.server.ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy nhà cung cấp.");
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
        apDungDanhMuc(vt, dm);
    }

    /**
     * Như apDungDanhMuc nhưng tra trong bản chụp danh mục nơi gọi đã lấy sẵn (LẤY TRƯỚC
     * khi mở transaction); không có trong đó mới hỏi database.
     */
    private void apDungDanhMucDaBiet(VatTu vt, Map<Long, vn.in3d.backend.entity.DanhMuc> dmTheoId) {
        if (vt.getDanhMucId() == null) return;
        vn.in3d.backend.entity.DanhMuc dm = dmTheoId.get(vt.getDanhMucId());
        if (dm == null) apDungDanhMuc(vt);
        else apDungDanhMuc(vt, dm);
    }

    private void apDungDanhMuc(VatTu vt, vn.in3d.backend.entity.DanhMuc dm) {
        if (!DanhMucController.NHOM_VAT_TU.equals(dm.getNhom())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "\"" + dm.getTen() + "\" là danh mục sản phẩm, không dùng làm loại vật tư được.");
        }
        String tc = dm.getTinhChat();
        vt.setLoai(tc != null && DanhMucController.TINH_CHAT.contains(tc) ? tc : "dung_cu");
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
