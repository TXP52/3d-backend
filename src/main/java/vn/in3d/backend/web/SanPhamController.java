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
            "du_kien", "da_dat", "dang_in", "da_in", "san_hang",
            "thanh_cong", "hoan_hang", "dang_van_chuyen", "het_hang");

    /** Loại sản phẩm hợp lệ. */
    private static final Set<String> LOAI_SAN_PHAM = Set.of("ban", "mau", "dich_vu");

    private final SanPhamRepository sanPhamRepo;
    private final vn.in3d.backend.repository.DanhMucRepository danhMucRepo;
    private final vn.in3d.backend.repository.VatTuRepository vatTuRepo;
    private final vn.in3d.backend.repository.SanPhamVatTuRepository spVatTuRepo;
    private final vn.in3d.backend.repository.MauSacRepository mauSacRepo;

    public SanPhamController(SanPhamRepository sanPhamRepo,
                             vn.in3d.backend.repository.DanhMucRepository danhMucRepo,
                             vn.in3d.backend.repository.VatTuRepository vatTuRepo,
                             vn.in3d.backend.repository.SanPhamVatTuRepository spVatTuRepo,
                             vn.in3d.backend.repository.MauSacRepository mauSacRepo) {
        this.sanPhamRepo = sanPhamRepo;
        this.danhMucRepo = danhMucRepo;
        this.vatTuRepo = vatTuRepo;
        this.spVatTuRepo = spVatTuRepo;
        this.mauSacRepo = mauSacRepo;
    }

    /** Kiểm tra backend còn sống — frontend gọi để quyết định dùng Java API hay Supabase. */
    @GetMapping("/suc-khoe")
    public Map<String, Object> sucKhoe() {
        return Map.of("ok", true, "backend", "java-spring-boot");
    }

    /**
     * Danh sách sản phẩm. Mặc định chỉ trả sản phẩm đang bán; ?tatCa=true trả hết (cho admin).
     *
     * Mỗi sản phẩm kèm luôn danh sách cuộn nhựa đã dùng và MÀU suy ra từ chính
     * mấy cuộn đó — trang quản trị khỏi phải gọi thêm rồi tự ghép.
     * Gom một lượt vat_tu và mau_sac rồi tra trong bộ nhớ: Supabase ở xa,
     * gọi findById cho từng dòng là trang tải mấy chục giây.
     */
    @GetMapping("/san-pham")
    public List<Map<String, Object>> danhSach(@RequestParam(defaultValue = "false") boolean tatCa) {
        List<SanPham> ds = tatCa ? sanPhamRepo.findByDaXoaFalseOrderByIdAsc()
                                 : sanPhamRepo.findByDangBanTrueAndDaXoaFalseOrderByIdAsc();

        Map<Long, vn.in3d.backend.entity.VatTu> vatTuTheoId = new java.util.HashMap<>();
        for (var v : vatTuRepo.findAll()) vatTuTheoId.put(v.getId(), v);
        Map<Long, vn.in3d.backend.entity.MauSac> mauTheoId = new java.util.HashMap<>();
        for (var m : mauSacRepo.findAll()) mauTheoId.put(m.getId(), m);

        Map<Long, List<vn.in3d.backend.entity.SanPhamVatTu>> noiTheoSanPham = new java.util.HashMap<>();
        for (var n : spVatTuRepo.findAll()) {
            noiTheoSanPham.computeIfAbsent(n.getSanPhamId(), k -> new java.util.ArrayList<>()).add(n);
        }
        Map<Long, String> tenDanhMuc = new java.util.HashMap<>();
        for (var d : danhMucRepo.findAll()) tenDanhMuc.put(d.getId(), d.getTen());

        return ds.stream().map(sp -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", sp.getId());
            m.put("ten", sp.getTen());
            m.put("moTa", sp.getMoTa());
            m.put("gia", sp.getGia());
            m.put("giaChu", sp.getGiaChu());
            m.put("danhMucId", sp.getDanhMucId());
            // Tên danh mục để trang chi tiết bên web khách khỏi phải gọi thêm /danh-muc
            m.put("danhMuc", tenDanhMuc.get(sp.getDanhMucId()));
            m.put("hinhAnh", sp.getHinhAnh());
            // Tất cả ảnh (ảnh đầu = ảnh bìa) — web khách làm slide ở trang chi tiết
            m.put("danhSachAnh", sp.getDanhSachAnhList());
            m.put("tonKho", sp.getTonKho());
            m.put("soLuong", sp.getSoLuong());            // tổng số cái đã in
            m.put("nhieuMau", sp.getNhieuMau());          // mỗi cái dùng mọi cuộn hay mỗi dòng một lô
            m.put("dangBan", sp.getDangBan());
            m.put("loaiSanPham", sp.getLoaiSanPham());
            m.put("trangThai", sp.getTrangThai());
            m.put("createdAt", sp.getCreatedAt());
            m.put("updatedAt", sp.getUpdatedAt());
            m.put("daXoa", sp.getDaXoa());

            List<Map<String, Object>> dsNhua = new java.util.ArrayList<>();
            List<Map<String, Object>> dsMau = new java.util.ArrayList<>();
            Set<Long> daCoMau = new java.util.LinkedHashSet<>();
            int tongGram = 0;
            long tienNhua = 0;
            int gramMin = Integer.MAX_VALUE, gramMax = 0;

            for (var n : noiTheoSanPham.getOrDefault(sp.getId(), List.of())) {
                var v = vatTuTheoId.get(n.getVatTuId());
                var mau = v == null ? null : mauTheoId.get(v.getMauSacId());

                Map<String, Object> d = new java.util.LinkedHashMap<>();
                d.put("id", n.getId());
                d.put("vatTuId", n.getVatTuId());
                d.put("ten", v == null ? "(cuộn đã xoá)" : v.getTen());
                // gramNhua / gramThua là của MỘT cái; tongGram mới là phần kho mất
                d.put("soLuong", n.getSoLuong());
                d.put("gramNhua", n.getGramNhua());
                d.put("gramThua", n.getGramThua());
                d.put("gramMoiCai", n.getGramMoiCai());
                d.put("tongGram", n.getTongGram());
                d.put("mauSacId", v == null ? null : v.getMauSacId());
                d.put("mau", mau == null ? null : mau.getTen());
                d.put("maMau", mau == null ? null : mau.getMaMau());
                d.put("donGiaMoiGram", v == null ? 0 : v.getDonGiaMoiGram());
                dsNhua.add(d);

                tongGram += n.getTongGram();
                if (v != null) tienNhua += Math.round(n.getTongGram() * v.getDonGiaMoiGram());
                if (n.getGramMoiCai() > 0) {
                    gramMin = Math.min(gramMin, n.getGramMoiCai());
                    gramMax = Math.max(gramMax, n.getGramMoiCai());
                }

                // Màu của sản phẩm CHÍNH LÀ màu của cuộn — không lưu riêng nên không lệch được
                if (mau != null && daCoMau.add(mau.getId())) {
                    dsMau.add(Map.of("id", mau.getId(), "ten", mau.getTen(),
                                     "maMau", mau.getMaMau() == null ? "" : mau.getMaMau()));
                }
            }
            m.put("vatTus", dsNhua);
            m.put("mauSac", dsMau);
            m.put("tongGramNhua", tongGram);              // nhựa đã trừ khỏi kho
            // Nhựa cho MỘT cái: các dòng có thể khác nhau nên trả cả khoảng
            m.put("gramMoiCaiMin", gramMax == 0 ? 0 : gramMin);
            m.put("gramMoiCaiMax", gramMax);
            m.put("tienNhua", tienNhua);
            return m;
        }).toList();
    }

    /** Thêm sản phẩm mới (admin). */
    @PostMapping("/san-pham")
    @Transactional
    public SanPham them(@RequestBody Map<String, Object> td) {
        SanPham sp = new SanPham();
        String ten = chuoi(td.get("ten"));
        if (ten == null || ten.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên sản phẩm không được để trống.");
        }
        sp.setTen(ten.trim());
        sp.setMoTa(chuoi(td.get("moTa")));
        ganAnh(sp, td);
        sp.setGia((long) soNguyen(td.get("gia")));
        sp.setGiaChu(dinhDangGia(sp.getGia()));
        sp.setTonKho(soNguyen(td.get("tonKho")));
        if (td.containsKey("soLuong")) sp.setSoLuong(soNguyen(td.get("soLuong")));
        if (td.containsKey("nhieuMau")) sp.setNhieuMau(Boolean.parseBoolean(String.valueOf(td.get("nhieuMau"))));
        if (td.containsKey("dangBan")) sp.setDangBan(Boolean.parseBoolean(String.valueOf(td.get("dangBan"))));

        Long dmId = soHoacNull(td.get("danhMucId"));
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

        List<DongNhua> dsNhua = chuanHoaNhua(sp, doDanhSachNhua(td.get("vatTus")));
        SanPham daLuu = sanPhamRepo.save(sp);
        luuDanhSachNhua(daLuu.getId(), dsNhua);
        return daLuu;
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
        ganAnh(sp, thayDoi);
        int soLuongCu = sp.getSoLuong();
        boolean nhieuMauCu = sp.getNhieuMau();
        if (thayDoi.containsKey("soLuong")) sp.setSoLuong(soNguyen(thayDoi.get("soLuong")));
        if (thayDoi.containsKey("nhieuMau")) {
            sp.setNhieuMau(Boolean.parseBoolean(String.valueOf(thayDoi.get("nhieuMau"))));
        }
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
        // Gửi lại danh sách nhựa, hoặc chỉ đổi số lượng / kiểu màu: chuẩn hoá theo
        // kiểu màu rồi trừ/hoàn kho phần chênh. PUT chỉ đổi trạng thái hay ẩn/hiện
        // thì không đụng tới nhựa.
        boolean doiCachDem = sp.getSoLuong() != soLuongCu || sp.getNhieuMau() != nhieuMauCu;
        if (thayDoi.containsKey("vatTus") || doiCachDem) {
            List<DongNhua> ds = thayDoi.containsKey("vatTus")
                    ? doDanhSachNhua(thayDoi.get("vatTus")) : nhuaHienTai(sp.getId());
            luuDanhSachNhua(sp.getId(), chuanHoaNhua(sp, ds));
        }
        return sanPhamRepo.save(sp);
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
     * Chuẩn hoá số cái theo kiểu màu (xem SanPham.nhieuMau):
     *   nhiều màu -> mọi dòng nhựa = số lượng chung của sản phẩm
     *   một màu   -> số lượng sản phẩm = cộng số cái các dòng
     */
    private List<DongNhua> chuanHoaNhua(SanPham sp, List<DongNhua> ds) {
        if (sp.getNhieuMau()) {
            int sl = sp.getSoLuong();
            return ds.stream().map(d -> new DongNhua(d.vatTuId(), sl, d.gramNhua(), d.gramThua())).toList();
        }
        if (!ds.isEmpty()) sp.setSoLuong(ds.stream().mapToInt(DongNhua::soLuong).sum());
        return ds;
    }

    /** Danh sách nhựa đang lưu, để tính lại khi chỉ đổi số lượng hoặc kiểu màu. */
    private List<DongNhua> nhuaHienTai(Long sanPhamId) {
        return spVatTuRepo.findBySanPhamIdOrderByIdAsc(sanPhamId).stream()
                .map(n -> new DongNhua(n.getVatTuId(), n.getSoLuong(), n.getGramNhua(), n.getGramThua()))
                .toList();
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

    /* ---------------- Nhựa: nhiều cuộn cho một sản phẩm ---------------- */

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
            Long vtId = soHoacNull(dong.get("vatTuId"));
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
     * Ghi lại danh sách nhựa của một sản phẩm và chỉnh số gram đã dùng của kho.
     *
     * Gram khai trong mỗi dòng là gram của MỘT cái, kho bị trừ gram × số lượng
     * của dòng đó. Nhiều dòng cùng một cuộn thì cộng dồn khi đối chiếu với kho.
     *
     * Chỉ đụng vào phần CHÊNH LỆCH của TỪNG cuộn, nên bấm Lưu bao nhiêu lần
     * cũng không trừ trùng:
     *   - vẫn cuộn đó, gram tăng 5 -> cuộn bị trừ thêm 5 × số lượng
     *   - vẫn cuộn đó, gram giảm 5 -> trả lại cuộn 5 × số lượng
     *   - in 2 cái thành 5 cái      -> trừ thêm phần của 3 cái
     *   - bỏ một dòng ra khỏi danh sách -> hoàn trọn phần dòng đó
     *
     * Trả lại (delta âm) TRƯỚC rồi mới trừ thêm, để lúc đổi từ cuộn A sang cuộn B
     * mà cả hai gần đầy vẫn không bị báo "không đủ nhựa" oan.
     */
    private void luuDanhSachNhua(Long sanPhamId, List<DongNhua> moi) {
        Map<Long, Integer> gramCu = new java.util.LinkedHashMap<>();
        for (var n : spVatTuRepo.findBySanPhamIdOrderByIdAsc(sanPhamId)) {
            gramCu.merge(n.getVatTuId(), n.getTongGram(), Integer::sum);
        }
        Map<Long, Integer> gramMoi = new java.util.LinkedHashMap<>();
        for (var d : moi) gramMoi.merge(d.vatTuId(), d.tongGram(), Integer::sum);

        for (Long vtId : gramMoi.keySet()) kiemTraLaCuonNhua(vtId);

        Set<Long> moiCuon = new java.util.LinkedHashSet<>(gramCu.keySet());
        moiCuon.addAll(gramMoi.keySet());

        for (Long vtId : moiCuon) {                       // lượt 1: hoàn lại
            int delta = gramMoi.getOrDefault(vtId, 0) - gramCu.getOrDefault(vtId, 0);
            if (delta < 0) congGram(vtId, delta);
        }
        for (Long vtId : moiCuon) {                       // lượt 2: trừ thêm
            int delta = gramMoi.getOrDefault(vtId, 0) - gramCu.getOrDefault(vtId, 0);
            if (delta > 0) congGram(vtId, delta);
        }

        spVatTuRepo.deleteBySanPhamId(sanPhamId);
        spVatTuRepo.flush();
        for (var d : moi) {
            var n = new vn.in3d.backend.entity.SanPhamVatTu();
            n.setSanPhamId(sanPhamId);
            n.setVatTuId(d.vatTuId());
            n.setSoLuong(d.soLuong());
            n.setGramNhua(d.gramNhua());
            n.setGramThua(d.gramThua());
            spVatTuRepo.save(n);
        }
    }

    /** Chỉ cho chọn vật tư là nhựa in — trừ gram của máy in hay phụ kiện thì vô nghĩa. */
    private void kiemTraLaCuonNhua(Long vatTuId) {
        vn.in3d.backend.entity.VatTu vt = vatTuRepo.findById(vatTuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Không tìm thấy cuộn nhựa trong kho."));
        if (Boolean.TRUE.equals(vt.getDaXoa())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cuộn nhựa \"" + vt.getTen() + "\" đã bị xoá khỏi kho.");
        }
        if (!"nhua".equals(vt.getLoai())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "\"" + vt.getTen() + "\" không phải nhựa in nên không trừ gram được.");
        }
    }

    /** Cộng (hoặc trả lại, khi delta âm) số gram vào cuộn nhựa. */
    private void congGram(Long vatTuId, int delta) {
        vn.in3d.backend.entity.VatTu vt = vatTuRepo.findById(vatTuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Không tìm thấy cuộn nhựa trong kho."));
        int daDung = vt.getDaDungGram() == null ? 0 : vt.getDaDungGram();
        int sucChua = (vt.getKhoiLuongGram() == null ? 0 : vt.getKhoiLuongGram())
                    * (vt.getSoLuong() == null ? 0 : vt.getSoLuong());
        int moi = daDung + delta;

        if (moi > sucChua) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cuộn \"" + vt.getTen() + "\" chỉ còn " + (sucChua - daDung)
                    + "g, không đủ cho " + delta + "g.");
        }
        vt.setDaDungGram(Math.max(0, moi));
        vatTuRepo.save(vt);
    }

    /** Ô để trống trên form gửi lên là "" hoặc null -> bỏ chọn cuộn nhựa. */
    private Long soHoacNull(Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuộn nhựa không hợp lệ.");
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
}
