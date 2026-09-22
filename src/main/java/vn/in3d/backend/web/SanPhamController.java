package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.BienThe;
import vn.in3d.backend.entity.MauSac;
import vn.in3d.backend.entity.SanPham;
import vn.in3d.backend.entity.SanPhamVatTu;
import vn.in3d.backend.entity.VatTu;
import vn.in3d.backend.repository.BienTheRepository;
import vn.in3d.backend.repository.DanhMucRepository;
import vn.in3d.backend.repository.SanPhamRepository;
import vn.in3d.backend.repository.SanPhamVatTuRepository;
import vn.in3d.backend.repository.VatTuRepository;
import vn.in3d.backend.service.BoNhoDem;
import vn.in3d.backend.service.ChiPhiMayService;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** API sản phẩm cho website bán hàng và trang quản trị. */
@RestController
@RequestMapping("/api")
public class SanPhamController {

    /** Các trạng thái sản phẩm hợp lệ (khớp với menu ở trang quản trị). */
    private static final Set<String> TRANG_THAI = Set.of(
            "du_kien", "da_dat", "dang_in", "da_in", "san_hang",
            "thanh_cong", "hoan_hang", "dang_van_chuyen", "het_hang");

    /** Loại sản phẩm hợp lệ. */
    private static final Set<String> LOAI_SAN_PHAM = Set.of("ban", "mau", "dich_vu");

    /**
     * Khoá bộ nhớ đệm bộ sưu tập — do phần bộ sưu tập đăng ký. Sửa sản phẩm có đổi
     * bộ sưu tập thì phải xoá luôn khoá đó, nhưng phần kia có thể chưa dựng xong nên
     * hỏi BoNhoDem.coBoDuLieu trước (xoá khoá không tồn tại là ném lỗi sau khi ĐÃ commit).
     */
    private static final String KHOA_BO_SUU_TAP = "BST";

    /** Các ô của form cũ (chưa biết biến thể) thuộc về BIẾN THỂ MẶC ĐỊNH. */
    private static final List<String> O_CUA_BIEN_THE = List.of("tonKho", "soLuong", "nhieuMau", "vatTus", "thoiGianInPhut");

    /** Mã sản phẩm chủ shop gõ: chữ (kể cả chữ có dấu), số và - _ . — không khoảng trắng. */
    private static final Pattern MA_HOP_LE = Pattern.compile("^[\\p{L}\\p{M}\\p{N}._-]+$");
    private static final int MA_TOI_DA = 40;

    /** Tên chỉ mục "mã không trùng giữa các sản phẩm chưa xoá" (sql/2026-09-21-ma-san-pham-gio-in.sql). */
    private static final String CHI_MUC_MA = "san_pham_ma_uq";

    /**
     * Mã một sản phẩm ĐANG DÙNG trong SQL: mã đã lưu, dòng cũ chưa có mã thì "SP-<id>" — khớp
     * SanPham.getMaHienThi, nên sinh mã / kiểm tra trùng không đụng với mã trang web đang hiện.
     */
    private static final String MA_DANG_DUNG = "coalesce(nullif(btrim(%1$s.ma_san_pham), ''), 'SP-' || %1$s.id)";

    /**
     * Mã TỰ SINH kế tiếp: SP-<số lớn nhất trong các mã dạng SP-<số> của sản phẩm chưa xoá + 1>
     * (không phân biệt hoa thường; sp-007 tính là 7). Chỉ xét phần số tới 18 chữ số cho vừa
     * bigint — ai lỡ gõ mã SP-<40 chữ số> thì mã sinh ra không vượt quá 40 ký tự của cột.
     */
    private static final String SQL_MA_TIEP_THEO =
            "(select 'SP-' || (coalesce(max(substring(x.ma from 4)::bigint), 0) + 1) "
            + "from (select " + MA_DANG_DUNG.formatted("m") + " as ma from san_pham m where not m.is_deleted) x "
            + "where x.ma ~* '^SP-[0-9]{1,18}$')";

    private final SanPhamRepository sanPhamRepo;
    private final DanhMucRepository danhMucRepo;
    private final VatTuRepository vatTuRepo;
    private final SanPhamVatTuRepository spVatTuRepo;
    private final BienTheRepository bienTheRepo;
    private final BoNhoDem boNho;
    private final ChiPhiMayService chiPhiMay;
    private final TransactionTemplate giaoDich;
    private final JdbcTemplate jdbc;

    public SanPhamController(SanPhamRepository sanPhamRepo,
                             DanhMucRepository danhMucRepo,
                             VatTuRepository vatTuRepo,
                             SanPhamVatTuRepository spVatTuRepo,
                             BienTheRepository bienTheRepo,
                             BoNhoDem boNho,
                             ChiPhiMayService chiPhiMay,
                             TransactionTemplate giaoDich,
                             JdbcTemplate jdbc) {
        this.sanPhamRepo = sanPhamRepo;
        this.danhMucRepo = danhMucRepo;
        this.vatTuRepo = vatTuRepo;
        this.spVatTuRepo = spVatTuRepo;
        this.bienTheRepo = bienTheRepo;
        this.boNho = boNho;
        this.chiPhiMay = chiPhiMay;
        this.giaoDich = giaoDich;
        this.jdbc = jdbc;
    }

    /** Kiểm tra backend còn sống — frontend gọi để quyết định dùng Java API hay Supabase. */
    @GetMapping("/suc-khoe")
    public Map<String, Object> sucKhoe() {
        // LinkedHashMap chứ không Map.of: Map.of đảo thứ tự khoá mỗi lần chạy lại máy ảo Java,
        // nhìn như dữ liệu đổi khi đem đối chiếu JSON giữa hai lần khởi động.
        Map<String, Object> ra = new java.util.LinkedHashMap<>();
        ra.put("ok", true);
        ra.put("backend", "java-spring-boot");
        return java.util.Collections.unmodifiableMap(ra);
    }

    /**
     * Danh sách sản phẩm. Mặc định chỉ trả sản phẩm đang bán; ?tatCa=true trả hết (cho admin).
     *
     * Mỗi sản phẩm kèm luôn BIẾN THỂ, danh sách cuộn nhựa đã dùng và MÀU suy ra từ chính
     * mấy cuộn đó — trang quản trị khỏi phải gọi thêm rồi tự ghép.
     * Lấy từ bộ nhớ đệm (BoNhoDem): dựng sẵn bằng MỘT truy vấn nối bảng, xem SanPhamDto.
     * Tiền máy / giá vốn mỗi cái ghép lúc trả lời từ chi phí chạy máy (ChiPhiMayService).
     */
    @GetMapping("/san-pham")
    public List<Map<String, Object>> danhSach(@RequestParam(defaultValue = "false") boolean tatCa) {
        return ChiPhiMayService.kemChiPhi(boNho.dsSanPham(tatCa), chiPhiMay.tinhAnToan());
    }

    /**
     * Thêm sản phẩm mới (admin).
     * Trả { sanPham: <dòng danh sách>, vatTuThayDoi: [<dòng vật tư> của các cuộn vừa bị trừ gram] }.
     */
    @PostMapping("/san-pham")
    public Map<String, Object> them(@RequestBody Map<String, Object> td) {
        // Màu và chi phí máy đọc TRƯỚC khi mở transaction: đọc bộ nhớ đệm giữa transaction là để
        // lượt nạp (một truy vấn nữa) chen vào giữa, giữ khoá dòng sản phẩm lâu thêm cả lượt đi-về
        Map<Long, MauSac> mauTheoId = mauDangCo();
        ChiPhiMayService.ChiPhiMay chiPhiTruoc = chiPhiMay.tinhAnToan();
        String maGui = docMaSanPham(td);                // kiểm tra dạng trước, khỏi mở transaction vô ích
        boolean sinhMa = maGui == null || maGui.isEmpty();
        KetQuaLuu kq = luuCoThuLai(maGui, sinhMa, () -> giaoDich.execute(gd -> {
            SanPham sp = new SanPham();
            String ten = chuoi(td.get("ten"));
            if (ten == null || ten.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên sản phẩm không được để trống.");
            }
            sp.setTen(ten.trim());
            // Mã sản phẩm: sinh / kiểm tra trùng NGAY TRONG transaction ghi (chỉ mục duy nhất
            // chặn nốt trường hợp hai lượt lưu song song cùng lọt qua bước này)
            if (sinhMa) {
                sp.setMaSanPham(sinhMaSanPham());
            } else {
                if (maDaDung(maGui, null)) throw maDaCo(maGui);
                sp.setMaSanPham(maGui);
            }
            sp.setMoTa(chuoi(td.get("moTa")));
            ganAnh(sp, td);
            sp.setGia((long) soNguyen(td.get("gia")));
            sp.setGiaChu(dinhDangGia(sp.getGia()));
            if (td.containsKey("dangBan")) sp.setDangBan(Boolean.parseBoolean(String.valueOf(td.get("dangBan"))));

            Long dmId = soHoacNull(td.get("danhMucId"), "Cuộn nhựa không hợp lệ.");
            if (dmId != null && !danhMucRepo.existsById(dmId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh mục không tồn tại.");
            }
            sp.setDanhMucId(dmId);

            if (td.containsKey("loaiSanPham")) {
                String l = chuoi(td.get("loaiSanPham"));
                kiemTraLoai(l);
                if (l != null && !l.isBlank()) sp.setLoaiSanPham(l);
            }
            if (td.containsKey("trangThai")) {
                String tt = chuoi(td.get("trangThai"));
                kiemTraTrangThai(tt);
                if (tt != null && !tt.isBlank()) sp.setTrangThai(tt);
            }

            // Sản phẩm mới luôn có biến thể: form cũ (không gửi "bienThe") thành MỘT biến thể
            // mặc định không tên, mang tồn kho / số cái / nhựa của form — nhìn y như trước
            List<DongBienThe> yeuCau = docBienThe(td, List.of(), Map.of());
            dongBoSanPham(sp, yeuCau);
            SanPham daLuu = sanPhamRepo.save(sp);       // cần id database sinh trước khi ghi biến thể
            KetQuaBienThe bt = luuBienThe(daLuu, List.of(), Map.of(), yeuCau, mauTheoId);
            ganBoSuuTap(daLuu.getId(), td);
            return new KetQuaLuu(daLuu.getId(), daLuu, bt);
        }));
        return traKetQuaLuu(kq, mauTheoId, chiPhiTruoc, td.containsKey("boSuuTap"));
    }

    /**
     * Cập nhật tên / mô tả / ảnh / giá / tồn kho / ẩn-hiện / trạng thái / biến thể (admin).
     * Trả cùng dạng với POST: { sanPham, vatTuThayDoi }.
     */
    @PutMapping("/san-pham/{id}")
    public Map<String, Object> capNhat(@PathVariable Long id, @RequestBody Map<String, Object> thayDoi) {
        Map<Long, MauSac> mauTheoId = mauDangCo();
        ChiPhiMayService.ChiPhiMay chiPhiTruoc = chiPhiMay.tinhAnToan();
        String maGui = docMaSanPham(thayDoi);           // null = không gửi ô mã -> để yên
        boolean sinhMa = maGui != null && maGui.isEmpty();
        KetQuaLuu kq = luuCoThuLai(maGui, sinhMa, () -> giaoDich.execute(gd -> {
            // Khoá dòng sản phẩm tới lúc commit: hai lần lưu song song không trừ kho hai lần
            SanPham sp = sanPhamRepo.khoaTheoId(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm."));
            if (maGui != null) ganMaSanPham(sp, maGui);
            if (thayDoi.containsKey("ten")) {
                String ten = String.valueOf(thayDoi.get("ten")).trim();
                if (ten.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên sản phẩm không được để trống.");
                sp.setTen(ten);
            }
            if (thayDoi.containsKey("moTa")) sp.setMoTa(chuoi(thayDoi.get("moTa")));
            ganAnh(sp, thayDoi);
            if (thayDoi.containsKey("gia")) {
                long gia = Long.parseLong(String.valueOf(thayDoi.get("gia")));
                sp.setGia(gia);
                sp.setGiaChu(dinhDangGia(gia));
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
                    // Form gửi lại danh mục cũ thì khỏi hỏi database: nó đã hợp lệ lúc gán
                    if (!dmId.equals(sp.getDanhMucId()) && !danhMucRepo.existsById(dmId)) {
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
            // Chỉ đụng biến thể khi form có gửi phần của biến thể: PUT đổi mỗi trạng thái hay
            // ẩn/hiện thì không đọc bảng bien_the, giữ đúng số lượt đi-về như trước
            KetQuaBienThe bt = KetQuaBienThe.trong();
            if (chamBienThe(thayDoi)) {
                // Biến thể đang lưu + dòng nhựa của chúng: MỘT truy vấn
                List<BienThe> btCu = new java.util.ArrayList<>();
                Map<Long, List<SanPhamVatTu>> nhuaCu = new java.util.LinkedHashMap<>();
                docBienTheDangLuu(sp.getId(), btCu, nhuaCu);
                List<DongBienThe> yeuCau = docBienThe(thayDoi, btCu, nhuaCu);
                dongBoSanPham(sp, yeuCau);
                bt = luuBienThe(sp, btCu, nhuaCu, yeuCau, mauTheoId);
            }
            ganBoSuuTap(sp.getId(), thayDoi);
            // sp đang được quản lý trong transaction: commit tự ghi phần thay đổi, khỏi gọi save
            return new KetQuaLuu(sp.getId(), sp, bt);
        }));
        return traKetQuaLuu(kq, mauTheoId, chiPhiTruoc, thayDoi.containsKey("boSuuTap"));
    }

    /* ---------------- Mã sản phẩm ---------------- */

    /**
     * Ô "maSanPham" của form: null = không gửi, "" = để trống (backend sinh mã), còn lại là
     * mã đã cắt khoảng trắng hai đầu và đúng dạng. Kiểm tra dạng TRƯỚC khi mở transaction.
     */
    private String docMaSanPham(Map<String, Object> td) {
        if (!td.containsKey("maSanPham")) return null;
        String ma = rong(chuoi(td.get("maSanPham")));
        if (ma == null) return "";
        if (ma.codePointCount(0, ma.length()) > MA_TOI_DA || !MA_HOP_LE.matcher(ma).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mã sản phẩm chỉ gồm chữ, số và các dấu - _ . (không có khoảng trắng), tối đa "
                    + MA_TOI_DA + " ký tự.");
        }
        return ma;
    }

    /**
     * PUT có gửi ô mã (chạy trong transaction, sp đã khoá):
     *   - để trống: sinh mã mới như lúc thêm;
     *   - gửi lại đúng mã đang hiện (kể cả "SP-<id>" của dòng cũ chưa lưu mã): khỏi kiểm tra,
     *     chỉ ghi hẳn xuống cột — form sửa sản phẩm luôn gửi lại ô này nên đây là đường hay gặp;
     *   - mã khác: không được trùng sản phẩm chưa xoá nào khác.
     */
    private void ganMaSanPham(SanPham sp, String maGui) {
        if (maGui.isEmpty()) {
            sp.setMaSanPham(sinhMaSanPham());
            return;
        }
        if (!maGui.equals(sp.getMaHienThi()) && maDaDung(maGui, sp.getId())) throw maDaCo(maGui);
        sp.setMaSanPham(maGui);
    }

    /** Mã tự sinh kế tiếp — MỘT truy vấn, chạy trong transaction lưu. */
    private String sinhMaSanPham() {
        return jdbc.queryForObject("select " + SQL_MA_TIEP_THEO, String.class);
    }

    /** Có sản phẩm CHƯA xoá nào khác (khác boQuaId) đang dùng mã này không — không phân biệt hoa thường. */
    private boolean maDaDung(String ma, Long boQuaId) {
        Boolean co = jdbc.queryForObject("select exists (select 1 from san_pham s where not s.is_deleted "
                + "and s.id <> ? and upper(" + MA_DANG_DUNG.formatted("s") + ") = upper(?))", Boolean.class,
                boQuaId == null ? -1L : boQuaId, ma);
        return Boolean.TRUE.equals(co);
    }

    private static ResponseStatusException maDaCo(String ma) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã sản phẩm \"" + ma + "\" đã có ở sản phẩm khác.");
    }

    /**
     * Chạy một lượt lưu (cả transaction). Đụng chỉ mục mã sản phẩm — hai lượt lưu song song
     * cùng qua được bước kiểm tra / cùng sinh ra một số — thì transaction đã rollback sạch:
     *   - mã TỰ SINH: chạy lại cả lượt MỘT lần (lượt sau đọc được mã lượt kia vừa commit);
     *   - mã chủ shop gõ: báo trùng như bước kiểm tra.
     */
    private KetQuaLuu luuCoThuLai(String maGui, boolean sinhMa, Supplier<KetQuaLuu> luot) {
        for (int lan = 1; ; lan++) {
            try {
                return luot.get();
            } catch (RuntimeException loi) {
                if (!dungChiMucMa(loi)) throw loi;
                if (sinhMa && lan < 2) continue;
                if (sinhMa) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Mã sản phẩm tự sinh vừa trùng với sản phẩm khác. Hãy bấm Lưu lại.");
                }
                throw maDaCo(maGui);
            }
        }
    }

    /** Lỗi này có phải do chỉ mục "mã sản phẩm không trùng" không (dò cả chuỗi nguyên nhân). */
    private static boolean dungChiMucMa(Throwable loi) {
        for (Throwable t = loi; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ResponseStatusException) return false;
            String chu = t.getMessage();
            if (chu != null && chu.contains(CHI_MUC_MA)) return true;
        }
        return false;
    }

    /**
     * Kết quả trong transaction: id sản phẩm, chính entity vừa ghi và phần biến thể —
     * entity giữ lại để dựng được response ngay cả khi bộ nhớ đệm nạp lại lỗi.
     */
    private record KetQuaLuu(Long id, SanPham sanPham, KetQuaBienThe bienThe) {}

    /**
     * Phần biến thể + nhựa của một lượt lưu.
     * @param ds       biến thể của sản phẩm SAU khi lưu (rỗng = lượt lưu không đụng biến thể)
     * @param nhua     dòng nhựa SAU khi lưu, tra theo id biến thể
     * @param cuonDoi  id các cuộn có số gram đã dùng thay đổi, tăng dần
     * @param cuon     các cuộn liên quan (đã khoá trong transaction), tra theo id
     */
    private record KetQuaBienThe(List<BienThe> ds, Map<Long, List<SanPhamVatTu>> nhua,
                                 Set<Long> cuonDoi, Map<Long, VatTu> cuon) {
        static KetQuaBienThe trong() { return new KetQuaBienThe(List.of(), Map.of(), Set.of(), Map.of()); }
    }

    /**
     * Đã commit: nạp lại sản phẩm + kho rồi trả đúng dòng danh sách từ bộ nhớ đệm.
     *
     * Nạp lại lỗi (Supabase chớp một nhịp) thì KHÔNG đọc lại bộ nhớ đệm nữa: đọc là mở thêm
     * một lượt nạp, lỗi lần hai thì ném ra ngoài và lệnh ghi ĐÃ COMMIT lại thành lỗi 500 —
     * chủ shop bấm Lưu lại là thêm một sản phẩm nữa VÀ trừ gram cuộn nhựa lần hai.
     * Nạp không được thì dựng dòng từ chính entity transaction vừa ghi; trang quản trị hỏi
     * lại các khoá của nó ngay sau đó nên vẫn thấy số mới.
     *
     * Tiền máy ghép bằng chi phí máy đọc TRƯỚC transaction (lượt lưu sản phẩm không đổi máy
     * in hay định mức nào); lúc đó đọc lỗi thì chỉ tính lại khi bộ nhớ đệm vừa nạp lại được.
     */
    private Map<String, Object> traKetQuaLuu(KetQuaLuu kq, Map<Long, MauSac> mauTheoId,
                                             ChiPhiMayService.ChiPhiMay chiPhiTruoc, boolean doiBoSuuTap) {
        Map<String, Object> dongSanPham = null;
        List<Map<String, Object>> vatTuThayDoi = new java.util.ArrayList<>();
        ChiPhiMayService.ChiPhiMay chiPhi = chiPhiTruoc;
        if (boNho.xoaVaNapLai(khoaCanXoa(doiBoSuuTap))) {
            try {
                dongSanPham = boNho.sanPham().theoId().get(kq.id());
                Map<Long, Map<String, Object>> vatTuTheoId = boNho.vatTu().theoId();
                for (Long vtId : kq.bienThe().cuonDoi()) {
                    Map<String, Object> dong = vatTuTheoId.get(vtId);
                    if (dong != null) vatTuThayDoi.add(dong);
                }
            } catch (RuntimeException boQua) {
                dongSanPham = null;
                vatTuThayDoi.clear();
            }
            if (chiPhi == null) chiPhi = chiPhiMay.tinhAnToan();
        }
        Map<String, Object> ra = new java.util.LinkedHashMap<>();
        ra.put("sanPham", ChiPhiMayService.kemChiPhi(
                dongSanPham != null ? dongSanPham : dongSanPhamDuPhong(kq, mauTheoId), chiPhi));
        ra.put("vatTuThayDoi", vatTuThayDoi);
        return ra;
    }

    /** Các bộ dữ liệu phải xoá sau lượt ghi sản phẩm (bộ sưu tập chỉ khi có đổi và đã có khoá). */
    private String[] khoaCanXoa(boolean doiBoSuuTap) {
        if (doiBoSuuTap && boNho.coBoDuLieu(KHOA_BO_SUU_TAP)) {
            return new String[] {BoNhoDem.SP, BoNhoDem.VT, KHOA_BO_SUU_TAP};
        }
        return new String[] {BoNhoDem.SP, BoNhoDem.VT};
    }

    /**
     * Dòng danh sách dựng từ entity vừa ghi trong transaction — dùng khi bộ nhớ đệm
     * chưa nạp lại được. Tên danh mục tra ở bản chụp KHÔNG bị lệnh ghi này xoá; đọc
     * không được thì để trống chứ không báo lỗi lệnh đã ghi xong. Lượt lưu không đụng
     * biến thể thì phần biến thể để rỗng và số liệu lấy thẳng ở sản phẩm (xem SanPhamDto).
     */
    private Map<String, Object> dongSanPhamDuPhong(KetQuaLuu kq, Map<Long, MauSac> mauTheoId) {
        String tenDanhMuc = null;
        try {
            Long dmId = kq.sanPham().getDanhMucId();
            vn.in3d.backend.entity.DanhMuc dm = dmId == null ? null : boNho.danhMuc().theoId().get(dmId);
            if (dm != null) tenDanhMuc = dm.getTen();
        } catch (RuntimeException boQua) {
            // database đang chớp: thiếu tên danh mục vẫn hơn là báo lỗi
        }
        return vn.in3d.backend.dto.SanPhamDto.tao(kq.sanPham(), kq.bienThe().ds(), kq.bienThe().nhua(),
                kq.bienThe().cuon(), mauTheoId, tenDanhMuc, List.of());
    }

    /**
     * Màu tra theo id, lấy TRƯỚC khi mở transaction. Bộ nhớ đệm lỗi thì trả map rỗng:
     * thiếu tên màu trong thông báo lỗi vẫn hơn là không lưu được sản phẩm.
     */
    private Map<Long, MauSac> mauDangCo() {
        try {
            return boNho.mauSac().theoId();
        } catch (RuntimeException boQua) {
            return Map.of();
        }
    }

    /**
     * Ảnh: form mới gửi "danhSachAnh" (mảng, ảnh đầu = bìa); chỗ cũ chỉ gửi
     * "hinhAnh" thì coi là đổi ảnh bìa và giữ nguyên các ảnh còn lại.
     */
    private void ganAnh(SanPham sp, Map<String, Object> td) {
        if (td.containsKey("danhSachAnh")) {
            List<String> ds = new java.util.ArrayList<>();
            if (td.get("danhSachAnh") instanceof List<?> tho) {
                for (Object o : tho) if (o != null) ds.add(String.valueOf(o));
            }
            sp.setDanhSachAnhList(ds);
        } else if (td.containsKey("hinhAnh")) {
            List<String> ds = new java.util.ArrayList<>(sp.getDanhSachAnhList());
            String bia = chuoi(td.get("hinhAnh"));
            if (!ds.isEmpty()) ds.remove(0);
            if (bia != null && !bia.isBlank()) ds.add(0, bia);
            sp.setDanhSachAnhList(ds);
        }
    }

    /**
     * XOÁ MỀM sản phẩm: chỉ bật cờ is_deleted, dữ liệu vẫn nằm trong database.
     * MỘT lệnh UPDATE (không mở transaction): bản cũ findById + save mất 5 lượt đi-về.
     * Biến thể giữ nguyên để khôi phục lại là có đủ tồn kho từng phân loại.
     */
    @DeleteMapping("/san-pham/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        if (jdbc.update("update san_pham set is_deleted = true, updated_at = now() where id = ?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm.");
        }
        boNho.xoaVaNapLai(BoNhoDem.SP, BoNhoDem.VT);
    }

    /**
     * Khôi phục sản phẩm đã xoá. Trả dòng danh sách của sản phẩm đó.
     *
     * Xoá mềm là nhả mã cho sản phẩm khác dùng, nên lúc khôi phục mã cũ có thể đã có chủ:
     * khi đó sản phẩm nhận mã tự sinh mới — vẫn MỘT câu UPDATE, không thêm lượt đi-về nào,
     * và không bao giờ đụng chỉ mục mã rồi báo lỗi khôi phục.
     */
    @PutMapping("/san-pham/{id}/khoi-phuc")
    public Map<String, Object> khoiPhuc(@PathVariable Long id) {
        ChiPhiMayService.ChiPhiMay chiPhiTruoc = chiPhiMay.tinhAnToan();
        if (jdbc.update("update san_pham s set is_deleted = false, updated_at = now(), "
                + "ma_san_pham = case when exists (select 1 from san_pham o where not o.is_deleted and o.id <> s.id "
                + "and upper(" + MA_DANG_DUNG.formatted("o") + ") = upper(" + MA_DANG_DUNG.formatted("s") + ")) "
                + "then " + SQL_MA_TIEP_THEO + " else s.ma_san_pham end "
                + "where s.id = ?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm.");
        }
        // Đã commit: nạp lại lỗi thì KHÔNG đọc bộ nhớ đệm nữa (đọc là mở thêm một lượt nạp,
        // lỗi tiếp là lệnh khôi phục ĐÃ chạy xong lại thành lỗi 500). Trả tạm id, trang quản trị
        // hỏi lại các khoá của nó ngay sau đó.
        Map<String, Object> dong = null;
        if (boNho.xoaVaNapLai(BoNhoDem.SP, BoNhoDem.VT)) {
            try {
                dong = boNho.sanPham().theoId().get(id);
            } catch (RuntimeException boQua) {
                dong = null;
            }
        }
        return dong != null ? ChiPhiMayService.kemChiPhi(dong, chiPhiTruoc) : Map.of("id", id);
    }

    /** Danh sách sản phẩm đã xoá — để xem lại hoặc khôi phục. */
    @GetMapping("/san-pham/thung-rac")
    public List<SanPham> thungRac() {
        return boNho.sanPham().thungRac();
    }

    /* ---------------- Biến thể (phân loại) ---------------- */

    /**
     * Một biến thể gửi lên từ form. id null = biến thể mới; danh sách gửi lên là
     * TOÀN BỘ biến thể sau lượt sửa nên biến thể cũ vắng mặt là bị xoá mềm.
     */
    private record DongBienThe(Long id, String ten, Long mauSacId, String maSku, Long gia,
                               int tonKho, int soLuong, boolean nhieuMau, String trangThai,
                               List<String> danhSachAnh, boolean macDinh, int thuTu,
                               int thoiGianInPhut, List<DongNhua> vatTus) {
        DongBienThe voiNhua(int soLuongMoi, List<DongNhua> nhua) {
            return new DongBienThe(id, ten, mauSacId, maSku, gia, tonKho, Math.max(1, soLuongMoi), nhieuMau,
                    trangThai, danhSachAnh, macDinh, thuTu, thoiGianInPhut, nhua);
        }
        DongBienThe voiMacDinh(boolean md) {
            return new DongBienThe(id, ten, mauSacId, maSku, gia, tonKho, soLuong, nhieuMau,
                    trangThai, danhSachAnh, md, thuTu, thoiGianInPhut, vatTus);
        }
        String moTa() { return ten == null || ten.isBlank() ? "mặc định" : "\"" + ten + "\""; }
    }

    /** Lượt lưu này có đụng tới biến thể không (form mới gửi bienThe, form cũ gửi ô của biến thể). */
    private boolean chamBienThe(Map<String, Object> td) {
        if (td.containsKey("bienThe")) return true;
        for (String o : O_CUA_BIEN_THE) if (td.containsKey(o)) return true;
        return false;
    }

    /** Biến thể chưa xoá + dòng nhựa của từng biến thể, MỘT truy vấn (xem BienTheRepository). */
    private void docBienTheDangLuu(Long sanPhamId, List<BienThe> btCu, Map<Long, List<SanPhamVatTu>> nhuaCu) {
        Set<Long> daCo = new java.util.HashSet<>();
        for (Object[] dong : bienTheRepo.napKemNhua(sanPhamId)) {
            BienThe b = (BienThe) dong[0];
            if (daCo.add(b.getId())) btCu.add(b);
            if (dong[1] instanceof SanPhamVatTu n) {
                nhuaCu.computeIfAbsent(b.getId(), k -> new java.util.ArrayList<>()).add(n);
            }
        }
    }

    /**
     * Danh sách biến thể SAU lượt sửa, dựng từ form:
     *   - form mới gửi "bienThe": dùng nguyên danh sách đó;
     *   - form cũ không biết biến thể: tonKho / soLuong / nhieuMau / vatTus / thoiGianInPhut áp
     *     vào BIẾN THỂ MẶC ĐỊNH, các biến thể khác giữ y nguyên (nên trang chỉ sửa tên, giá vẫn chạy).
     */
    private List<DongBienThe> docBienThe(Map<String, Object> td, List<BienThe> btCu,
                                         Map<Long, List<SanPhamVatTu>> nhuaCu) {
        List<DongBienThe> ds = new java.util.ArrayList<>();
        if (td.containsKey("bienThe")) {
            Map<Long, BienThe> cuTheoId = new java.util.LinkedHashMap<>();
            for (BienThe b : btCu) cuTheoId.put(b.getId(), b);
            if (td.get("bienThe") instanceof List<?> tho) {
                for (Object o : tho) if (o instanceof Map<?, ?> dong) ds.add(doMotBienThe(dong, cuTheoId, nhuaCu));
            }
            if (ds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Sản phẩm phải có ít nhất một biến thể.");
            }
            return chonMacDinh(ds);
        }

        // Biến thể mặc định là nơi nhận các ô của form cũ; dữ liệu sửa tay trên database
        // lỡ mất cờ mặc định thì lấy biến thể đầu tiên cho khỏi rơi mất số liệu form gửi lên
        Long idMacDinh = null;
        for (BienThe b : btCu) if (b.getMacDinh()) { idMacDinh = b.getId(); break; }
        if (idMacDinh == null && !btCu.isEmpty()) idMacDinh = btCu.get(0).getId();

        for (BienThe b : btCu) {
            List<DongNhua> nhua = nhuaHienTai(nhuaCu.getOrDefault(b.getId(), List.of()));
            DongBienThe dong = tuBienTheDangLuu(b, nhua);
            if (b.getId().equals(idMacDinh)) {
                int tonKho = tonKhoSauSua(td.containsKey("tonKho"), td.get("tonKho"), td.get("tonKhoGoc"), b.getTonKho());
                int soLuong = td.containsKey("soLuong") ? soNguyen(td.get("soLuong")) : b.getSoLuong();
                boolean nhieuMau = td.containsKey("nhieuMau")
                        ? Boolean.parseBoolean(String.valueOf(td.get("nhieuMau"))) : b.getNhieuMau();
                // Gửi lại danh sách nhựa, hoặc chỉ đổi số lượng / kiểu màu: chuẩn hoá lại
                // theo kiểu màu rồi trừ/hoàn kho phần chênh, y như bản chưa có biến thể
                List<DongNhua> nhuaMoi = td.containsKey("vatTus") ? doDanhSachNhua(td.get("vatTus")) : nhua;
                int phut = td.containsKey("thoiGianInPhut") ? phutIn(td.get("thoiGianInPhut")) : b.getThoiGianInPhut();
                dong = chuanHoaNhua(new DongBienThe(b.getId(), b.getTen(), b.getMauSacId(), b.getMaSku(),
                        b.getGia(), tonKho, Math.max(1, soLuong), nhieuMau, b.getTrangThai(),
                        b.getDanhSachAnhList(), true, b.getThuTu(), phut, nhuaMoi));
            }
            ds.add(dong);
        }
        if (ds.isEmpty()) {
            // Sản phẩm mới, hoặc sản phẩm cũ bị sửa tay trên database mất hết biến thể:
            // dựng MỘT biến thể mặc định không tên mang đúng số liệu của form cũ
            ds.add(chuanHoaNhua(new DongBienThe(null, null, null, null, null,
                    soNguyen(td.get("tonKho")),
                    td.containsKey("soLuong") ? Math.max(1, soNguyen(td.get("soLuong"))) : 1,
                    td.containsKey("nhieuMau") && Boolean.parseBoolean(String.valueOf(td.get("nhieuMau"))),
                    null, List.of(), true, 0, phutIn(td.get("thoiGianInPhut")), doDanhSachNhua(td.get("vatTus")))));
        }
        return chonMacDinh(ds);
    }

    /**
     * Thời gian in MỘT cái (phút) gửi lên: trống = 0, số lẻ làm tròn (form nhập giờ + phút
     * rồi quy ra phút), âm hoặc không phải số thì báo lỗi.
     */
    private int phutIn(Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        if (s.isEmpty() || "null".equals(s)) return 0;
        double x;
        try {
            x = v instanceof Number n ? n.doubleValue() : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian in phải là số phút.");
        }
        if (Double.isNaN(x) || x < 0 || x > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian in phải là số phút không âm.");
        }
        return (int) Math.round(x);
    }

    /**
     * Tồn kho của một biến thể sau lượt sửa.
     *
     * Form gửi kèm "tonKhoGoc" (số tồn LÚC MỞ FORM) thì chỉ cộng PHẦN CHÊNH chủ shop vừa
     * gõ vào số đang lưu: mở form thấy 5, trong lúc đó khách đặt 1 (còn 4), chủ shop sửa
     * tên rồi lưu vẫn gửi 5 — tính tuyệt đối là hàng khách vừa mua "mọc" lại. Không có
     * tonKhoGoc (form cũ, biến thể mới) thì lấy đúng số gửi lên như trước.
     *
     * @param dangLuu số tồn đang lưu trong database (null = biến thể mới)
     */
    private int tonKhoSauSua(boolean coGui, Object tonKho, Object tonKhoGoc, Integer dangLuu) {
        if (!coGui) return dangLuu == null ? 0 : dangLuu;
        int moi = soNguyen(tonKho);
        if (dangLuu == null || tonKhoGoc == null || String.valueOf(tonKhoGoc).isBlank()) return moi;
        return dangLuu + (moi - soNguyen(tonKhoGoc));
    }

    /** Biến thể đang lưu, dạng dòng form — dùng cho các biến thể form cũ không đụng tới. */
    private DongBienThe tuBienTheDangLuu(BienThe b, List<DongNhua> nhua) {
        return new DongBienThe(b.getId(), b.getTen(), b.getMauSacId(), b.getMaSku(), b.getGia(),
                b.getTonKho(), b.getSoLuong(), b.getNhieuMau(), b.getTrangThai(),
                b.getDanhSachAnhList(), b.getMacDinh(), b.getThuTu(), b.getThoiGianInPhut(), nhua);
    }

    /**
     * Đọc một dòng biến thể của form. Ô KHÔNG gửi lên thì giữ nguyên giá trị đang lưu
     * (y như PUT sản phẩm chỉ đổi những khoá có trong body) — form gửi thiếu ô Tồn kho
     * mà thành xoá sạch tồn kho thì chết chủ shop.
     */
    private DongBienThe doMotBienThe(Map<?, ?> d, Map<Long, BienThe> cuTheoId,
                                     Map<Long, List<SanPhamVatTu>> nhuaCu) {
        Long id = soHoacNull(d.get("id"), "Biến thể không hợp lệ.");
        BienThe c = id == null ? null : cuTheoId.get(id);
        String ten = d.containsKey("ten") ? rong(chuoi(d.get("ten"))) : (c == null ? null : c.getTen());
        Long mauSacId = d.containsKey("mauSacId")
                ? soHoacNull(d.get("mauSacId"), "Màu của biến thể không hợp lệ.")
                : (c == null ? null : c.getMauSacId());
        String maSku = d.containsKey("maSku") ? rong(chuoi(d.get("maSku"))) : (c == null ? null : c.getMaSku());
        Long gia = d.containsKey("gia")
                ? soHoacNull(d.get("gia"), "Giá biến thể phải là số nguyên.")
                : (c == null ? null : c.getGia());
        String trangThai = d.containsKey("trangThai")
                ? rong(chuoi(d.get("trangThai"))) : (c == null ? null : c.getTrangThai());
        kiemTraTrangThai(trangThai);

        List<String> anh;
        if (d.containsKey("danhSachAnh") || d.containsKey("hinhAnh")) {
            anh = new java.util.ArrayList<>();
            if (d.get("danhSachAnh") instanceof List<?> tho) {
                for (Object o : tho) if (o != null) anh.add(String.valueOf(o));
            } else {
                String bia = rong(chuoi(d.get("hinhAnh")));
                if (bia != null) anh.add(bia);
            }
        } else {
            anh = c == null ? List.of() : c.getDanhSachAnhList();
        }
        List<DongNhua> nhua = d.containsKey("vatTus")
                ? doDanhSachNhua(d.get("vatTus"))
                : (c == null ? List.of() : nhuaHienTai(nhuaCu.getOrDefault(id, List.of())));

        return chuanHoaNhua(new DongBienThe(id, ten, mauSacId, maSku, gia,
                tonKhoSauSua(d.containsKey("tonKho"), d.get("tonKho"), d.get("tonKhoGoc"), c == null ? null : c.getTonKho()),
                d.containsKey("soLuong") ? Math.max(1, soNguyen(d.get("soLuong"))) : (c == null ? 1 : c.getSoLuong()),
                d.containsKey("nhieuMau")
                        ? Boolean.parseBoolean(String.valueOf(d.get("nhieuMau"))) : (c != null && c.getNhieuMau()),
                trangThai, anh,
                Boolean.parseBoolean(String.valueOf(d.get("macDinh"))),
                d.containsKey("thuTu") ? soNguyen(d.get("thuTu")) : (c == null ? 0 : c.getThuTu()),
                d.containsKey("thoiGianInPhut")
                        ? phutIn(d.get("thoiGianInPhut")) : (c == null ? 0 : c.getThoiGianInPhut()),
                nhua));
    }

    /**
     * Đúng MỘT biến thể mặc định: lấy dòng ĐẦU TIÊN có cờ, không dòng nào có thì lấy dòng
     * đầu danh sách. Xoá biến thể mặc định cũng rơi vào đây (nó không còn trong danh sách
     * nên cờ chuyển sang dòng đầu — đúng thứ tự thu_tu form gửi lên).
     */
    private List<DongBienThe> chonMacDinh(List<DongBienThe> ds) {
        int chon = -1;
        for (int i = 0; i < ds.size(); i++) {
            if (ds.get(i).macDinh()) { chon = i; break; }
        }
        if (chon < 0) chon = 0;
        List<DongBienThe> ra = new java.util.ArrayList<>(ds.size());
        for (int i = 0; i < ds.size(); i++) ra.add(ds.get(i).voiMacDinh(i == chon));
        return ra;
    }

    /**
     * Chuẩn hoá số cái theo kiểu màu của BIẾN THỂ (xem SanPham.nhieuMau):
     *   nhiều màu -> mọi dòng nhựa = số lượng chung của biến thể
     *   một màu   -> số lượng biến thể = cộng số cái các dòng
     */
    private DongBienThe chuanHoaNhua(DongBienThe bt) {
        if (bt.nhieuMau()) {
            int sl = bt.soLuong();
            return bt.voiNhua(sl, bt.vatTus().stream()
                    .map(d -> new DongNhua(d.vatTuId(), sl, d.gramNhua(), d.gramThua())).toList());
        }
        if (bt.vatTus().isEmpty()) return bt;
        return bt.voiNhua(bt.vatTus().stream().mapToInt(DongNhua::soLuong).sum(), bt.vatTus());
    }

    /** Danh sách nhựa đang lưu, để tính lại khi chỉ đổi số lượng hoặc kiểu màu. */
    private List<DongNhua> nhuaHienTai(List<SanPhamVatTu> dongCu) {
        return dongCu.stream()
                .map(n -> new DongNhua(n.getVatTuId(), n.getSoLuong(), n.getGramNhua(), n.getGramThua()))
                .toList();
    }

    /** Số liệu sản phẩm = TỔNG các biến thể, để mấy chỗ đọc cột cũ (web khách, thống kê) vẫn đúng. */
    private void dongBoSanPham(SanPham sp, List<DongBienThe> ds) {
        int tonKho = 0, soLuong = 0;
        boolean nhieuMau = false;
        for (DongBienThe d : ds) {
            tonKho += d.tonKho();
            soLuong += d.soLuong();
            nhieuMau = nhieuMau || d.nhieuMau();
        }
        sp.setTonKho(tonKho);
        sp.setSoLuong(soLuong);
        sp.setNhieuMau(nhieuMau);
    }

    /**
     * Ghi biến thể + dòng nhựa của cả sản phẩm.
     *
     * THỨ TỰ CÁC LỆNH phải đúng như dưới vì database có chỉ mục duy nhất "mỗi sản phẩm
     * một biến thể mặc định": xoá mềm trước (bỏ luôn cờ mặc định), rồi sửa (dòng BỎ cờ
     * xếp trước dòng NHẬN cờ), rồi mới thêm. Làm ngược là có một khoảnh khắc hai dòng
     * cùng mặc định và Postgres chặn ngay giữa transaction.
     *
     * Nhựa vẫn tính CHÊNH LỆCH trên toàn sản phẩm chứ không từng biến thể: kéo một lô
     * từ biến thể Đỏ sang biến thể Xám mà vẫn cuộn đó thì kho không nhúc nhích.
     */
    private KetQuaBienThe luuBienThe(SanPham sp, List<BienThe> btCu, Map<Long, List<SanPhamVatTu>> nhuaCu,
                                     List<DongBienThe> yeuCau, Map<Long, MauSac> mauTheoId) {
        Map<Long, BienThe> cuTheoId = new java.util.LinkedHashMap<>();
        for (BienThe b : btCu) cuTheoId.put(b.getId(), b);
        Set<Long> daGap = new java.util.HashSet<>();
        for (DongBienThe d : yeuCau) {
            if (d.id() != null && !cuTheoId.containsKey(d.id())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Biến thể không thuộc sản phẩm này.");
            }
            if (d.id() != null && !daGap.add(d.id())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Biến thể bị lặp trong danh sách.");
            }
            // Bộ nhớ đệm màu lỗi thì bỏ qua bước kiểm tra chứ không chặn lượt lưu
            if (d.mauSacId() != null && !mauTheoId.isEmpty() && !mauTheoId.containsKey(d.mauSacId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Màu không tồn tại.");
            }
        }

        /* --- 1. Nhựa: khoá cuộn, kiểm tra, trừ/hoàn phần chênh --- */
        Map<Long, Integer> gramCu = new java.util.LinkedHashMap<>();
        for (var dong : nhuaCu.values()) {
            for (var n : dong) gramCu.merge(n.getVatTuId(), n.getTongGram(), Integer::sum);
        }
        Map<Long, Integer> gramMoi = new java.util.LinkedHashMap<>();
        for (DongBienThe d : yeuCau) {
            for (DongNhua n : d.vatTus()) gramMoi.merge(n.vatTuId(), n.tongGram(), Integer::sum);
        }
        boolean giuNguyenNhua = giongNhauNhua(nhuaCu, yeuCau);
        Map<Long, VatTu> cuon = new java.util.HashMap<>();
        Set<Long> cuonDoi = new java.util.TreeSet<>();
        // Form gửi lại đúng danh sách nhựa đang lưu (sửa tên, giá, ảnh... chứ không đụng nhựa):
        // mọi phần chênh đều bằng 0 nên không trừ, không hoàn, không ghi lại dòng nào. Khỏi
        // khoá các cuộn -> bớt MỘT lượt đi-về Supabase (~250 ms) cho lượt lưu hay gặp nhất.
        if (!giuNguyenNhua || phaiKiemMauCuon(yeuCau, cuTheoId)) {
            Set<Long> moiCuon = new java.util.LinkedHashSet<>(gramCu.keySet());
            moiCuon.addAll(gramMoi.keySet());
            if (!moiCuon.isEmpty()) {
                // Mọi cuộn liên quan (cũ lẫn mới) đọc + KHOÁ trong MỘT truy vấn
                for (VatTu v : vatTuRepo.khoaCuon(moiCuon)) cuon.put(v.getId(), v);
            }
            for (Long vtId : gramMoi.keySet()) kiemTraLaCuonNhua(cuon.get(vtId));
            kiemTraMauCuon(yeuCau, cuon, mauTheoId);
            for (Long vtId : moiCuon) {                   // lượt 1: hoàn lại
                int delta = gramMoi.getOrDefault(vtId, 0) - gramCu.getOrDefault(vtId, 0);
                if (delta < 0) congGram(cuon.get(vtId), delta, cuonDoi);
            }
            for (Long vtId : moiCuon) {                   // lượt 2: trừ thêm
                int delta = gramMoi.getOrDefault(vtId, 0) - gramCu.getOrDefault(vtId, 0);
                if (delta > 0) congGram(cuon.get(vtId), delta, cuonDoi);
            }
        }

        /* --- 2. Xoá mềm biến thể không còn trong danh sách --- */
        Set<Long> conLai = new java.util.HashSet<>();
        for (DongBienThe d : yeuCau) if (d.id() != null) conLai.add(d.id());
        List<Long> xoa = new java.util.ArrayList<>();
        for (BienThe b : btCu) if (!conLai.contains(b.getId())) xoa.add(b.getId());
        if (!xoa.isEmpty()) {
            // Bỏ luôn cờ mặc định: chỉ mục duy nhất chỉ tính dòng chưa xoá, nhưng để lại
            // cờ thì lần khôi phục tay trên database sau này lại thành hai dòng mặc định
            jdbc.batchUpdate("update bien_the set is_deleted = true, mac_dinh = false, updated_at = now() "
                    + "where id = ?", xoa, xoa.size(), (ps, id) -> ps.setLong(1, id));
        }

        /* --- 3. Sửa biến thể đang có: dòng BỎ cờ mặc định chạy trước dòng NHẬN cờ --- */
        List<DongBienThe> sua = new java.util.ArrayList<>();
        for (DongBienThe d : yeuCau) {
            if (d.id() != null && !giongNhauBienThe(cuTheoId.get(d.id()), d)) sua.add(d);
        }
        sua.sort(java.util.Comparator.comparing(DongBienThe::macDinh));
        if (!sua.isEmpty()) {
            // Tồn kho ghi dạng CỘNG PHẦN CHÊNH (ton_kho = ton_kho + ?) chứ không ghi đè:
            // đơn hàng trừ kho cũng bằng phép cộng nên đơn chốt xen giữa lúc đọc biến thể và
            // lúc ghi ở đây không bị lượt lưu này xoá mất (xem tonKhoSauSua)
            jdbc.batchUpdate("update bien_the set ten = ?, mau_sac_id = ?, ma_sku = ?, gia = ?, ton_kho = ton_kho + ?, "
                    + "trang_thai = ?, danh_sach_anh = ?, so_luong = ?, nhieu_mau = ?, mac_dinh = ?, "
                    + "thu_tu = ?, thoi_gian_in_phut = ?, updated_at = now() where id = ?", sua, sua.size(), (ps, d) -> {
                ganThamSoBienThe(ps, d, 1);
                ps.setInt(5, d.tonKho() - cuTheoId.get(d.id()).getTonKho());
                ps.setLong(13, d.id());
            });
        }

        /* --- 4. Thêm biến thể mới: MỘT câu INSERT nhiều dòng, đọc lại id database vừa sinh --- */
        List<DongBienThe> them = yeuCau.stream().filter(d -> d.id() == null).toList();
        Map<DongBienThe, Long> idMoi = new java.util.IdentityHashMap<>();
        if (!them.isEmpty()) {
            StringBuilder sql = new StringBuilder("insert into bien_the (san_pham_id, ten, mau_sac_id, ma_sku, "
                    + "gia, ton_kho, trang_thai, danh_sach_anh, so_luong, nhieu_mau, mac_dinh, thu_tu, "
                    + "thoi_gian_in_phut) values ");
            for (int i = 0; i < them.size(); i++) {
                sql.append(i == 0 ? "" : ", ").append("(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            }
            // Postgres ghi các dòng theo đúng thứ tự trong VALUES nên id trả về cũng theo thứ tự đó
            sql.append(" returning id");
            List<Long> ds = jdbc.query(sql.toString(), ps -> {
                int i = 1;
                for (DongBienThe d : them) {
                    ps.setLong(i++, sp.getId());
                    i = ganThamSoBienThe(ps, d, i);
                }
            }, (rs, i) -> rs.getLong(1));
            for (int i = 0; i < them.size(); i++) idMoi.put(them.get(i), ds.get(i));
        }

        /* --- 5. Dòng nhựa: xoá hết rồi ghi lại (chỉ khi thật sự khác) --- */
        List<SanPhamVatTu> dongSau = new java.util.ArrayList<>();
        Map<Long, List<SanPhamVatTu>> nhuaMoi = new java.util.LinkedHashMap<>();
        for (DongBienThe d : yeuCau) {
            Long btId = d.id() != null ? d.id() : idMoi.get(d);
            for (DongNhua n : d.vatTus()) {
                SanPhamVatTu dong = new SanPhamVatTu();
                dong.setSanPhamId(sp.getId());
                dong.setBienTheId(btId);
                dong.setVatTuId(n.vatTuId());
                dong.setSoLuong(n.soLuong());
                dong.setGramNhua(n.gramNhua());
                dong.setGramThua(n.gramThua());
                dongSau.add(dong);
                nhuaMoi.computeIfAbsent(btId, k -> new java.util.ArrayList<>()).add(dong);
            }
        }
        // Không ghi lại thì dùng chính dòng đang lưu (có id database) cho bản dự phòng
        Map<Long, List<SanPhamVatTu>> nhuaSau = giuNguyenNhua ? nhuaCu : nhuaMoi;
        if (!giuNguyenNhua) {
            spVatTuRepo.xoaTheoSanPham(sp.getId());
            if (!dongSau.isEmpty()) {
                // MỘT lượt: reWriteBatchedInserts gộp thành một câu INSERT nhiều dòng
                jdbc.batchUpdate("insert into san_pham_vat_tu (san_pham_id, bien_the_id, vat_tu_id, so_luong, "
                                + "gram_nhua, gram_thua) values (?, ?, ?, ?, ?, ?)",
                        dongSau, dongSau.size(), (ps, d) -> {
                    ps.setLong(1, d.getSanPhamId());
                    ps.setObject(2, d.getBienTheId(), Types.BIGINT);
                    ps.setLong(3, d.getVatTuId());
                    ps.setInt(4, d.getSoLuong());
                    ps.setInt(5, d.getGramNhua());
                    ps.setInt(6, d.getGramThua());
                });
            }
        }

        /* --- 6. Biến thể SAU khi lưu, để dựng response khi bộ nhớ đệm nạp lại lỗi --- */
        List<BienThe> dsSau = new java.util.ArrayList<>();
        for (DongBienThe d : yeuCau) {
            BienThe b = new BienThe();
            b.setId(d.id() != null ? d.id() : idMoi.get(d));
            b.setSanPhamId(sp.getId());
            b.setTen(d.ten());
            b.setMauSacId(d.mauSacId());
            b.setMaSku(d.maSku());
            b.setGia(d.gia());
            b.setTonKho(d.tonKho());
            b.setTrangThai(d.trangThai());
            b.setDanhSachAnhList(d.danhSachAnh());
            b.setSoLuong(d.soLuong());
            b.setNhieuMau(d.nhieuMau());
            b.setMacDinh(d.macDinh());
            b.setThuTu(d.thuTu());
            b.setThoiGianInPhut(d.thoiGianInPhut());
            dsSau.add(b);
        }
        return new KetQuaBienThe(dsSau, nhuaSau, cuonDoi, cuon);
    }

    /** Gán 12 tham số dữ liệu của một biến thể, trả vị trí tham số kế tiếp. */
    private int ganThamSoBienThe(java.sql.PreparedStatement ps, DongBienThe d, int i) throws java.sql.SQLException {
        ps.setString(i++, d.ten());
        ps.setObject(i++, d.mauSacId(), Types.BIGINT);
        ps.setString(i++, d.maSku());
        ps.setObject(i++, d.gia(), Types.BIGINT);
        ps.setInt(i++, d.tonKho());
        ps.setString(i++, d.trangThai());
        ps.setString(i++, BienThe.chuoiAnh(d.danhSachAnh()));
        ps.setInt(i++, d.soLuong());
        ps.setBoolean(i++, d.nhieuMau());
        ps.setBoolean(i++, d.macDinh());
        ps.setInt(i++, d.thuTu());
        ps.setInt(i++, d.thoiGianInPhut());
        return i;
    }

    /** Dòng biến thể gửi lên có y hệt dòng đang lưu không — giống thì khỏi ghi lại. */
    private boolean giongNhauBienThe(BienThe c, DongBienThe m) {
        return c != null
                && Objects.equals(c.getTen(), m.ten())
                && Objects.equals(c.getMauSacId(), m.mauSacId())
                && Objects.equals(c.getMaSku(), m.maSku())
                && Objects.equals(c.getGia(), m.gia())
                && c.getTonKho() == m.tonKho()
                && Objects.equals(c.getTrangThai(), m.trangThai())
                && Objects.equals(BienThe.chuoiAnh(c.getDanhSachAnhList()), BienThe.chuoiAnh(m.danhSachAnh()))
                && c.getSoLuong() == m.soLuong()
                && c.getNhieuMau() == m.nhieuMau()
                && c.getMacDinh() == m.macDinh()
                && c.getThuTu() == m.thuTu()
                && c.getThoiGianInPhut() == m.thoiGianInPhut();
    }

    /**
     * Có phải kiểm tra màu cuộn nhựa không: chỉ khi biến thể VỪA đổi màu (hoặc vừa bỏ kiểu
     * nhiều màu). Lưu lại y nguyên thì dữ liệu đã hợp lệ từ lượt trước, khỏi tốn một lượt
     * đi-về chỉ để đọc màu của cuộn.
     */
    private boolean phaiKiemMauCuon(List<DongBienThe> yeuCau, Map<Long, BienThe> cuTheoId) {
        for (DongBienThe d : yeuCau) {
            if (d.mauSacId() == null || d.nhieuMau() || d.vatTus().isEmpty()) continue;
            BienThe c = d.id() == null ? null : cuTheoId.get(d.id());
            if (c == null || !Objects.equals(c.getMauSacId(), d.mauSacId()) || c.getNhieuMau()) return true;
        }
        return false;
    }

    /**
     * Biến thể đã chọn màu thì chỉ in được bằng cuộn nhựa ĐÚNG MÀU đó — trừ khi biến thể
     * đếm kiểu nhiều màu (một cái ăn nhiều cuộn khác màu). Cuộn chưa gán màu thì cho qua.
     */
    private void kiemTraMauCuon(List<DongBienThe> yeuCau, Map<Long, VatTu> cuon, Map<Long, MauSac> mauTheoId) {
        for (DongBienThe d : yeuCau) {
            if (d.mauSacId() == null || d.nhieuMau()) continue;
            for (DongNhua n : d.vatTus()) {
                VatTu v = cuon.get(n.vatTuId());
                if (v == null || v.getMauSacId() == null || v.getMauSacId().equals(d.mauSacId())) continue;
                MauSac mau = mauTheoId.get(d.mauSacId());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Biến thể " + d.moTa() + " màu " + (mau == null ? d.mauSacId() : mau.getTen())
                        + " không dùng được cuộn \"" + v.getTen() + "\" khác màu.");
            }
        }
    }

    /**
     * Bộ sưu tập của sản phẩm: gửi "boSuuTap" là THAY cả bộ (mảng id), không gửi thì để yên.
     *
     * Chỉ đụng tới dòng nối THẬT SỰ đổi. thu_tu của dòng nối là VỊ TRÍ của sản phẩm TRONG
     * BỘ (chủ shop xếp tay ở trang Bộ sưu tập) — xoá hết rồi chèn lại theo thứ tự danh sách
     * bộ của sản phẩm là xáo mất thứ tự đó ở mọi bộ sản phẩm này đang nằm. Nên:
     *   - bộ không còn trong danh sách: xoá đúng dòng nối của bộ đó;
     *   - bộ mới thêm: sản phẩm đứng CUỐI bộ (thu_tu lớn nhất của bộ + 1);
     *   - bộ vẫn giữ: dòng nối để y nguyên.
     * Vẫn hai lệnh như trước (một DELETE, một INSERT ... SELECT), không đọc gì lên Java.
     */
    private void ganBoSuuTap(Long sanPhamId, Map<String, Object> td) {
        if (!td.containsKey("boSuuTap")) return;
        List<Long> ids = new java.util.ArrayList<>();
        if (td.get("boSuuTap") instanceof List<?> tho) {
            for (Object o : tho) {
                Long id = soHoacNull(o, "Bộ sưu tập không hợp lệ.");
                if (id != null && !ids.contains(id)) ids.add(id);
            }
        }
        Long[] mang = ids.toArray(new Long[0]);
        // "<> all(mảng rỗng)" đúng với mọi dòng: gửi danh sách rỗng là gỡ khỏi mọi bộ
        jdbc.update("delete from san_pham_bo_suu_tap where san_pham_id = ? and bo_suu_tap_id <> all(?::bigint[])", ps -> {
            ps.setLong(1, sanPhamId);
            ps.setArray(2, ps.getConnection().createArrayOf("bigint", mang));
        });
        if (ids.isEmpty()) return;
        jdbc.update("insert into san_pham_bo_suu_tap (bo_suu_tap_id, san_pham_id, thu_tu) "
                + "select m.id, ?, coalesce((select max(l.thu_tu) from san_pham_bo_suu_tap l "
                + "                          where l.bo_suu_tap_id = m.id), -1) + 1 "
                + "from unnest(?::bigint[]) as m(id) "
                + "where not exists (select 1 from san_pham_bo_suu_tap l "
                + "                  where l.bo_suu_tap_id = m.id and l.san_pham_id = ?)", ps -> {
            ps.setLong(1, sanPhamId);
            ps.setArray(2, ps.getConnection().createArrayOf("bigint", mang));
            ps.setLong(3, sanPhamId);
        });
    }

    /* ---------------- Nhựa: nhiều cuộn cho một biến thể ---------------- */

    /** Một dòng nhựa gửi lên từ form. */
    private record DongNhua(Long vatTuId, int soLuong, int gramNhua, int gramThua) {
        int tongGram() { return (gramNhua + gramThua) * soLuong; }
    }

    /**
     * Đọc danh sách nhựa từ form: [{vatTuId, soLuong, gramNhua, gramThua}, ...]
     *
     * Giữ nguyên TỪNG DÒNG chứ không gộp theo cuộn: "in 1 cái đen, 1 cái trắng
     * cùng mẫu" là hai dòng, và chọn cùng một cuộn hai lần với số lượng khác
     * nhau cũng là hai lần in khác nhau. Dòng chưa chọn cuộn thì bỏ qua.
     */
    private List<DongNhua> doDanhSachNhua(Object tho) {
        List<DongNhua> ket = new java.util.ArrayList<>();
        if (!(tho instanceof List<?> ds)) return ket;

        for (Object o : ds) {
            if (!(o instanceof Map<?, ?> dong)) continue;
            Long vtId = soHoacNull(dong.get("vatTuId"), "Cuộn nhựa không hợp lệ.");
            if (vtId == null) continue;
            int sl = Math.max(1, soNguyen(dong.get("soLuong")));
            int g = soNguyen(dong.get("gramNhua"));
            int t = soNguyen(dong.get("gramThua"));
            if (g < 0 || t < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số gram không được âm.");
            }
            ket.add(new DongNhua(vtId, sl, g, t));
        }
        return ket;
    }

    /**
     * Dòng nhựa gửi lên có y hệt dòng đang lưu không (cùng biến thể, cùng thứ tự,
     * cùng cuộn / số cái / gram). Giống thì cả bảng nối lẫn kho đều không phải đụng:
     * bấm Lưu bao nhiêu lần cũng không trừ trùng gram.
     *
     * Chỉ đụng vào phần CHÊNH LỆCH của TỪNG cuộn khi có khác:
     *   - vẫn cuộn đó, gram tăng 5 -> cuộn bị trừ thêm 5 × số lượng
     *   - vẫn cuộn đó, gram giảm 5 -> trả lại cuộn 5 × số lượng
     *   - in 2 cái thành 5 cái      -> trừ thêm phần của 3 cái
     *   - bỏ một dòng (hoặc cả biến thể) ra khỏi danh sách -> hoàn trọn phần đó
     */
    private boolean giongNhauNhua(Map<Long, List<SanPhamVatTu>> nhuaCu, List<DongBienThe> moi) {
        int soBienTheCoNhua = 0;
        for (DongBienThe d : moi) {
            if (d.vatTus().isEmpty()) continue;
            if (d.id() == null) return false;              // biến thể mới mang theo nhựa
            soBienTheCoNhua++;
            List<SanPhamVatTu> cu = nhuaCu.get(d.id());
            if (cu == null || cu.size() != d.vatTus().size()) return false;
            for (int i = 0; i < cu.size(); i++) {
                SanPhamVatTu c = cu.get(i);
                DongNhua m = d.vatTus().get(i);
                if (!c.getVatTuId().equals(m.vatTuId()) || c.getSoLuong() != m.soLuong()
                        || c.getGramNhua() != m.gramNhua() || c.getGramThua() != m.gramThua()) {
                    return false;
                }
            }
        }
        return soBienTheCoNhua == nhuaCu.size();           // biến thể cũ có nhựa mà nay không còn
    }

    /** Chỉ cho chọn vật tư là nhựa in — trừ gram của máy in hay phụ kiện thì vô nghĩa. */
    private void kiemTraLaCuonNhua(VatTu vt) {
        if (vt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy cuộn nhựa trong kho.");
        }
        if (Boolean.TRUE.equals(vt.getDaXoa())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cuộn nhựa \"" + vt.getTen() + "\" đã bị xoá khỏi kho.");
        }
        if (!"nhua".equals(vt.getLoai())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "\"" + vt.getTen() + "\" không phải nhựa in nên không trừ gram được.");
        }
    }

    /**
     * Cộng (hoặc trả lại, khi delta âm) số gram vào cuộn nhựa.
     * vt đang được quản lý trong transaction nên commit tự ghi, khỏi gọi save.
     */
    private void congGram(VatTu vt, int delta, Set<Long> cuonDoi) {
        if (vt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy cuộn nhựa trong kho.");
        }
        int daDung = vt.getDaDungGram() == null ? 0 : vt.getDaDungGram();
        int sucChua = (vt.getKhoiLuongGram() == null ? 0 : vt.getKhoiLuongGram())
                    * (vt.getSoLuong() == null ? 0 : vt.getSoLuong());
        int moi = daDung + delta;

        if (moi > sucChua) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cuộn \"" + vt.getTen() + "\" chỉ còn " + (sucChua - daDung)
                    + "g, không đủ cho " + delta + "g.");
        }
        Integer truoc = vt.getDaDungGram();
        vt.setDaDungGram(Math.max(0, moi));
        if (!vt.getDaDungGram().equals(truoc)) cuonDoi.add(vt.getId());
    }

    /** Ô để trống trên form gửi lên là "" hoặc null -> không chọn gì. */
    private Long soHoacNull(Object v, String loi) {
        String s = v == null ? "" : String.valueOf(v).trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, loi);
        }
    }

    private int soNguyen(Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        if (s.isEmpty() || "null".equals(s)) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số gram phải là số nguyên.");
        }
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

    /** Ô để trống trên form -> null, để cột trong database là NULL chứ không phải chuỗi rỗng. */
    private String rong(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() || "null".equals(t) ? null : t;
    }
}
