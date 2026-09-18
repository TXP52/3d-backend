package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.repository.KhuyenMaiRepository;
import vn.in3d.backend.service.BoNhoDem;
import vn.in3d.backend.service.KhuyenMaiService;

import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.service.XacThucService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API chương trình khuyến mãi.
 *   GET  /api/khuyen-mai              trang khách — chỉ mã đang chạy và đang bật khoe
 *   GET  /api/khuyen-mai?tatCa=true   trang quản trị — mọi mã kể cả tạm dừng, hết hạn
 *   POST /api/khuyen-mai/kiem-tra     khách bấm "Áp dụng" ở giỏ hàng
 */
@RestController
@RequestMapping("/api/khuyen-mai")
public class KhuyenMaiController {

    private static final Set<String> LOAI = Set.of("phan_tram", "so_tien", "mien_ship");
    private static final Set<String> AP_DUNG_CHO = Set.of("tat_ca", "san_pham", "dich_vu");
    private static final Set<String> KIEU_AP_DUNG = Set.of("don_hang", "san_pham");

    private final KhuyenMaiRepository repo;
    private final KhuyenMaiService dichVu;
    private final XacThucService xacThuc;
    private final BoNhoDem boNho;
    private final TransactionTemplate giaoDich;
    private final JdbcTemplate jdbc;

    public KhuyenMaiController(KhuyenMaiRepository repo, KhuyenMaiService dichVu, XacThucService xacThuc,
                               BoNhoDem boNho, TransactionTemplate giaoDich, JdbcTemplate jdbc) {
        this.repo = repo;
        this.dichVu = dichVu;
        this.xacThuc = xacThuc;
        this.boNho = boNho;
        this.giaoDich = giaoDich;
        this.jdbc = jdbc;
    }

    /** @param tatCa true = lấy cả mã tạm dừng / hết hạn (dùng cho trang quản trị) */
    @GetMapping
    public List<KhuyenMai> danhSach(@RequestParam(required = false, defaultValue = "false") boolean tatCa) {
        return boNho.dsKhuyenMai(tatCa);
    }

    @GetMapping("/thung-rac")
    public List<KhuyenMai> thungRac() {
        return boNho.khuyenMai().thungRac();
    }

    /**
     * Giá sau giảm của những sản phẩm ĐANG được khuyến mãi.
     * Trang bán hàng và trang quản trị gọi cái này để hiện giá gạch ngang,
     * khỏi phải cài lại luật "chọn chương trình giảm nhiều nhất" ở ba nơi.
     * Sản phẩm không được giảm thì không có trong danh sách trả về.
     * Tính trên bộ nhớ đệm khuyến mãi + sản phẩm (không hỏi database); "đang chạy"
     * vẫn tính theo ngày hôm nay lúc gọi.
     */
    @GetMapping("/gia-san-pham")
    public List<Map<String, Object>> giaSanPham() {
        // Cùng luật và cùng thứ tự (mới trước) với KhuyenMaiService.khuyenMaiSanPhamDangChay
        List<KhuyenMai> dangChay = boNho.khuyenMai().danhSach().stream()
                .filter(KhuyenMai::laKhuyenMaiSanPham)
                .filter(KhuyenMai::dangChay)
                .toList();
        List<Map<String, Object>> ra = new ArrayList<>();
        if (dangChay.isEmpty()) return ra;

        for (Map<String, Object> sp : boNho.dsSanPham(false)) {
            Long spId = (Long) sp.get("id");
            Long gia = (Long) sp.get("gia");
            long giaGoc = gia == null ? 0 : gia;
            if (giaGoc <= 0) continue;                       // hàng "Liên hệ" thì không giảm gì
            KhuyenMaiService.GiaMon g = dichVu.giaSauGiam(spId, giaGoc, dangChay);
            if (!g.coGiam()) continue;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sanPhamId", spId);
            m.put("ten", sp.get("ten"));
            m.put("giaGoc", g.giaGoc());
            m.put("giaSauGiam", g.giaSauGiam());
            m.put("giamMoiDonVi", g.giamMoiDonVi());
            m.put("phanTram", Math.round(g.giamMoiDonVi() * 100.0 / g.giaGoc()));
            m.put("tenKhuyenMai", g.khuyenMai() == null ? null : g.khuyenMai().getTen());
            ra.add(m);
        }
        return ra;
    }

    @GetMapping("/{id}")
    public KhuyenMai mot(@PathVariable Long id) {
        KhuyenMai km = boNho.khuyenMai().theoId().get(id);
        if (km == null) throw khongThay();
        return km;
    }

    /**
     * Khách gõ mã ở giỏ hàng rồi bấm "Áp dụng".
     * Body: { "ma": "GIAM10", "tongTien": 250000 }
     * Mã sai / hết hạn / chưa đủ điều kiện -> 400 kèm câu giải thích tiếng Việt.
     * Tra mã trong bộ nhớ đệm khuyến mãi (không hỏi database, trừ câu đếm của mã "chỉ khách mới").
     */
    @PostMapping("/kiem-tra")
    public Map<String, Object> kiemTra(@RequestBody Map<String, Object> body,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        String ma = body.get("ma") == null ? "" : String.valueOf(body.get("ma"));
        long tongTien = soLon(body.get("tongTien"));

        // Địa chỉ + tài khoản để kiểm tra "chỉ khách mới" và "chỉ giao khu vực này"
        NguoiDung nd = xacThuc.docTokenNeuCo(authorization);
        KhuyenMaiService.NguoiDat nguoiDat = new KhuyenMaiService.NguoiDat(
                nd == null ? null : nd.getId(),
                chuoi(body.get("soDienThoai")),
                chuoi(body.get("diaChi")));

        KhuyenMaiService.KetQua kq = dichVu.kiemTra(ma, tongTien, nguoiDat, boNho.khuyenMai().danhSach());
        KhuyenMai km = kq.khuyenMai();

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("ok", true);
        ra.put("id", km.getId());
        ra.put("ma", km.getMa());
        ra.put("ten", km.getTen());
        ra.put("loai", km.getLoai());
        ra.put("tamTinh", tongTien);
        ra.put("tienGiam", kq.tienGiam());
        ra.put("conLai", kq.conLai());
        return ra;
    }

    /**
     * XEM TRƯỚC giá sau giảm của một chương trình giảm giá sản phẩm đang soạn ở trang quản trị.
     * Body: đúng các ô form gửi lên khi lưu (kieuApDung, sanPhamIds, loai, giaTri, giamToiDa,
     * batDau, ketThuc, soLuong, hoatDong...) + "id" nếu đang sửa một chương trình có sẵn.
     *
     * CHỈ ĐỌC: dựng một KhuyenMai tạm trong bộ nhớ (không bao giờ save), tính bằng đúng
     * KhuyenMaiService.giaSauGiam trên bộ nhớ đệm sản phẩm + khuyến mãi — trang khỏi giữ
     * bản sao công thức. Kiểu giảm theo đơn thì không có giá món nào để xem: trả [].
     *
     * Mỗi dòng (hàng BÁN, kể cả đang ẩn, theo id tăng dần — đúng danh sách ô chọn sản phẩm):
     *   giaSauGiam           giá khi chỉ áp chương trình đang soạn
     *   giaKhachTra          giá khách thật sự trả nếu lưu: chương trình này (nếu đang chạy)
     *                        cùng các chương trình sản phẩm khác đang chạy, lấy cái giảm nhiều nhất
     *   tenKhuyenMaiKhachTra chương trình được chọn cho giaKhachTra (null nếu không giảm)
     */
    @PostMapping("/xem-truoc-gia")
    public List<Map<String, Object>> xemTruocGia(@RequestBody(required = false) Map<String, Object> td) {
        Map<String, Object> f = td == null ? Map.of() : td;
        Long id = f.get("id") == null || String.valueOf(f.get("id")).isBlank() ? null : soLon(f.get("id"));
        KhuyenMai cu = id == null ? null : boNho.khuyenMai().theoId().get(id);
        if (cu != null && cu.getDaXoa()) cu = null;   // bản trong thùng rác không chạy: coi như soạn mới

        KhuyenMai thu = new KhuyenMai();   // bản tạm, KHÔNG lưu
        if (cu != null) {
            thu.setId(cu.getId());
            thu.setKieuApDung(cu.getKieuApDung());
            thu.setSanPhamIds(cu.getSanPhamIds());
            thu.setLoai(cu.getLoai());
            thu.setGiaTri(cu.getGiaTri());
            thu.setGiamToiDa(cu.getGiamToiDa());
            thu.setBatDau(cu.getBatDau());
            thu.setKetThuc(cu.getKetThuc());
            thu.setSoLuong(cu.getSoLuong());
            thu.setDaDung(cu.getDaDung());
            thu.setHoatDong(cu.getHoatDong());
            thu.setTen(cu.getTen());
        }
        if (f.containsKey("kieuApDung")) thu.setKieuApDung(chuoi(f.get("kieuApDung")));
        if (f.containsKey("sanPhamIds")) thu.setSanPhamIds(chuanHoaDsId(f.get("sanPhamIds")));
        if (f.containsKey("loai")) thu.setLoai(chuoi(f.get("loai")));
        if (f.containsKey("giaTri")) thu.setGiaTri(soLon(f.get("giaTri")));
        if (f.containsKey("giamToiDa")) thu.setGiamToiDa(soLon(f.get("giamToiDa")));
        if (f.containsKey("batDau")) thu.setBatDau(ngay(f.get("batDau")));
        if (f.containsKey("ketThuc")) thu.setKetThuc(ngay(f.get("ketThuc")));
        if (f.containsKey("soLuong")) thu.setSoLuong((int) soLon(f.get("soLuong")));
        if (f.containsKey("hoatDong")) thu.setHoatDong(Boolean.parseBoolean(String.valueOf(f.get("hoatDong"))));
        if (f.containsKey("ten")) thu.setTen(chuoi(f.get("ten")));

        List<Map<String, Object>> ra = new ArrayList<>();
        if (!thu.laKhuyenMaiSanPham()) return ra;

        // Các chương trình sản phẩm đang chạy nếu lưu bản này: thay bản cũ cùng id, hoặc thêm lên đầu (mới nhất)
        List<KhuyenMai> neuLuu = new ArrayList<>();
        if (cu == null) neuLuu.add(thu);
        for (KhuyenMai k : boNho.khuyenMai().danhSach()) neuLuu.add(cu != null && k.getId().equals(cu.getId()) ? thu : k);
        List<KhuyenMai> dangChay = dichVu.khuyenMaiSanPhamDangChay(neuLuu);
        List<KhuyenMai> chiBanNay = List.of(thu);

        for (Map<String, Object> sp : boNho.dsSanPham(true)) {
            Object loai = sp.get("loaiSanPham");
            if (loai != null && !"ban".equals(loai)) continue;      // chỉ hàng bán mới giảm giá được
            Long spId = (Long) sp.get("id");
            if (!thu.apDungChoSanPham(spId)) continue;
            Long gia = (Long) sp.get("gia");
            long giaGoc = gia == null ? 0 : gia;

            KhuyenMaiService.GiaMon g = dichVu.giaSauGiam(spId, giaGoc, chiBanNay);
            KhuyenMaiService.GiaMon that = dichVu.giaSauGiam(spId, giaGoc, dangChay);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sanPhamId", spId);
            m.put("ten", sp.get("ten"));
            m.put("giaGoc", giaGoc);
            m.put("giaSauGiam", g.giaSauGiam());
            m.put("giamMoiDonVi", g.giamMoiDonVi());
            m.put("phanTram", giaGoc > 0 ? Math.round(g.giamMoiDonVi() * 100.0 / giaGoc) : 0L);
            m.put("giaKhachTra", that.giaSauGiam());
            m.put("tenKhuyenMaiKhachTra", that.khuyenMai() == null ? null : that.khuyenMai().getTen());
            ra.add(m);
        }
        return ra;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KhuyenMai them(@RequestBody KhuyenMai km) {
        km.setId(null);
        km.setDaDung(0);
        km.setMa(chuanHoaMa(km.getMa()));
        km.setSanPhamIds(chuanHoaDsId(km.getSanPhamIds()));
        kiemTraChung(km);
        if (km.getMa() != null) {
            repo.findByMaIgnoreCaseAndDaXoaFalse(km.getMa()).ifPresent(cu -> {
                throw badRequest("Mã \"" + cu.getMa() + "\" đã có rồi. Đặt mã khác nhé.");
            });
        }
        KhuyenMai daLuu = repo.save(km);
        return traKhuyenMai(daLuu.getId(), daLuu);
    }

    /**
     * Sửa trong MỘT transaction: đọc + UPDATE + COMMIT. Entity có @DynamicUpdate nên
     * UPDATE chỉ ghi cột vừa đổi — không ghi đè da_dung mà một đơn hàng vừa tăng.
     */
    @PutMapping("/{id}")
    public KhuyenMai sua(@PathVariable Long id, @RequestBody Map<String, Object> td) {
        KhuyenMai daSua = giaoDich.execute(gd -> {
            KhuyenMai km = repo.findById(id).orElseThrow(this::khongThay);

            if (td.containsKey("ma")) {
                String maMoi = chuanHoaMa(chuoi(td.get("ma")));
                // Form luôn gửi lại mã: vẫn mã cũ thì khỏi hỏi trùng
                if (maMoi != null && !maMoi.equals(km.getMa())) {
                    repo.findByMaIgnoreCaseAndDaXoaFalse(maMoi)
                            .filter(cu -> !cu.getId().equals(id))
                            .ifPresent(cu -> { throw badRequest("Mã \"" + cu.getMa() + "\" đã có rồi. Đặt mã khác nhé."); });
                }
                km.setMa(maMoi);
            }
            if (td.containsKey("ten")) km.setTen(chuoi(td.get("ten")));
            if (td.containsKey("moTa")) km.setMoTa(chuoi(td.get("moTa")));
            if (td.containsKey("kieuApDung")) km.setKieuApDung(chuoi(td.get("kieuApDung")));
            if (td.containsKey("sanPhamIds")) km.setSanPhamIds(chuanHoaDsId(td.get("sanPhamIds")));
            if (td.containsKey("loai")) km.setLoai(chuoi(td.get("loai")));
            if (td.containsKey("giaTri")) km.setGiaTri(soLon(td.get("giaTri")));
            if (td.containsKey("giamToiDa")) km.setGiamToiDa(soLon(td.get("giamToiDa")));
            if (td.containsKey("donToiThieu")) km.setDonToiThieu(soLon(td.get("donToiThieu")));
            if (td.containsKey("chiKhachMoi")) km.setChiKhachMoi(Boolean.parseBoolean(String.valueOf(td.get("chiKhachMoi"))));
            if (td.containsKey("dieuKienDiaChi")) km.setDieuKienDiaChi(chuoi(td.get("dieuKienDiaChi")));
            if (td.containsKey("batDau")) km.setBatDau(ngay(td.get("batDau")));
            if (td.containsKey("ketThuc")) km.setKetThuc(ngay(td.get("ketThuc")));
            if (td.containsKey("soLuong")) km.setSoLuong((int) soLon(td.get("soLuong")));
            if (td.containsKey("apDungCho")) km.setApDungCho(chuoi(td.get("apDungCho")));
            if (td.containsKey("hoatDong")) km.setHoatDong(Boolean.parseBoolean(String.valueOf(td.get("hoatDong"))));
            if (td.containsKey("hienThi")) km.setHienThi(Boolean.parseBoolean(String.valueOf(td.get("hienThi"))));
            // daDung KHÔNG cho sửa qua API — đó là số đếm thật, sửa tay là mất ý nghĩa

            kiemTraChung(km);
            // km đang được quản lý trong transaction: commit tự ghi, khỏi gọi save
            return km;
        });
        return traKhuyenMai(id, daSua);
    }

    /**
     * XOÁ MỀM: đơn cũ đã dùng mã này vẫn tra ngược được.
     *
     * Cột ma là khoá DUY NHẤT trên toàn bảng, kể cả dòng đã xoá mềm. Nếu giữ
     * nguyên mã thì xoá "FREESHIPHN" xong tạo lại "FREESHIPHN" là database chặn,
     * mà giao diện lại không thấy mã cũ đâu để hiểu vì sao. Nên khi xoá thì gắn
     * hậu tố "#id" để nhả mã ra; khôi phục sẽ cắt hậu tố đi.
     * MỘT lệnh UPDATE làm cả hai việc (bản cũ findById + save: 5 lượt đi-về).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        int soDong = jdbc.update("update khuyen_mai set "
                + "ma = case when ma is not null and strpos(ma, '#') = 0 then ma || '#' || id else ma end, "
                + "is_deleted = true, updated_at = now() where id = ?", id);
        if (soDong == 0) throw khongThay();
        boNho.xoaVaNapLai(BoNhoDem.KM);
    }

    /**
     * Mã gốc chưa ai chiếm thì trả lại; chiếm rồi thì giữ nguyên mã có hậu tố
     * và báo cho chủ shop tự đặt mã khác, chứ không âm thầm đổi mã của người ta.
     * MỘT lệnh UPDATE có điều kiện; chỉ khi không khôi phục được mới hỏi thêm để báo đúng lỗi.
     */
    @PutMapping("/{id}/khoi-phuc")
    public KhuyenMai khoiPhuc(@PathVariable Long id) {
        int soDong = jdbc.update("update khuyen_mai k set "
                + "ma = case when k.ma is not null and strpos(k.ma, '#') > 0 then split_part(k.ma, '#', 1) else k.ma end, "
                + "is_deleted = false, updated_at = now() "
                + "where k.id = ? and not (k.ma is not null and strpos(k.ma, '#') > 0 and exists ("
                + "select 1 from khuyen_mai o where upper(o.ma) = upper(split_part(k.ma, '#', 1)) "
                + "and o.is_deleted = false))", id);
        if (soDong == 0) {
            List<String> ma = jdbc.queryForList("select ma from khuyen_mai where id = ?", String.class, id);
            if (ma.isEmpty()) throw khongThay();
            String m = ma.get(0) == null ? "" : ma.get(0);
            String goc = m.contains("#") ? m.substring(0, m.indexOf('#')) : m;
            throw badRequest("Đã có mã \"" + goc + "\" đang dùng nên không khôi phục nguyên mã được. "
                    + "Hãy đổi tên mã đang chạy rồi khôi phục lại.");
        }
        return traKhuyenMai(id, null);
    }

    /**
     * Đã commit: nạp lại khuyến mãi rồi trả đúng dòng danh sách từ bộ nhớ đệm.
     *
     * Nạp lại lỗi (Supabase chớp một nhịp) thì KHÔNG đọc lại bộ nhớ đệm nữa: đọc là mở thêm
     * một lượt nạp, lỗi lần hai thì ném ra ngoài và lệnh ghi ĐÃ COMMIT lại thành lỗi 500 —
     * bấm Lưu lại là có hai chương trình trùng. Trả luôn dòng vừa ghi trong transaction.
     *
     * @param duPhong dòng vừa ghi, null nếu nơi gọi không có (chỉ chạy một lệnh UPDATE)
     */
    private KhuyenMai traKhuyenMai(Long id, KhuyenMai duPhong) {
        if (boNho.xoaVaNapLai(BoNhoDem.KM)) {
            try {
                KhuyenMai km = boNho.khuyenMai().theoId().get(id);
                if (km != null) return km;
            } catch (RuntimeException boQua) {
                // rơi xuống dùng bản dự phòng
            }
        }
        return duPhong;
    }

    // ---------------- Kiểm tra dữ liệu ----------------

    private void kiemTraChung(KhuyenMai km) {
        if (!KIEU_AP_DUNG.contains(km.getKieuApDung())) {
            throw badRequest("Kiểu áp dụng không hợp lệ. Chỉ nhận: don_hang (giảm theo đơn), "
                    + "san_pham (giảm giá sản phẩm).");
        }
        // Giảm theo đơn thì phải có mã để khách gõ; giảm giá sản phẩm thì tự áp, không cần mã
        if (km.laKhuyenMaiSanPham()) {
            if (km.getMa() != null && !km.getMa().isBlank() && !km.getMa().matches("[A-Z0-9_-]{3,40}")) {
                throw badRequest("Mã chỉ gồm chữ HOA không dấu, số, gạch ngang hoặc gạch dưới, dài 3-40 ký tự.");
            }
            if ("mien_ship".equals(km.getLoai())) {
                throw badRequest("Miễn phí ship là ưu đãi của cả đơn, không giảm được vào giá từng món. "
                        + "Chọn kiểu \"Giảm theo đơn hàng\" cho chương trình này.");
            }
        } else {
            if (km.getMa() == null || km.getMa().isBlank()) {
                throw badRequest("Khuyến mãi theo đơn phải có mã để khách nhập ở giỏ hàng.");
            }
            if (!km.getMa().matches("[A-Z0-9_-]{3,40}")) {
                throw badRequest("Mã chỉ gồm chữ HOA không dấu, số, gạch ngang hoặc gạch dưới, dài 3-40 ký tự. "
                        + "Ví dụ: GIAM10, FREESHIP-HN.");
            }
        }
        if (km.getTen() == null || km.getTen().isBlank()) {
            throw badRequest("Tên chương trình không được để trống.");
        }
        if (!LOAI.contains(km.getLoai())) {
            throw badRequest("Loại khuyến mãi không hợp lệ. Chỉ nhận: " + String.join(", ", LOAI));
        }
        if (!AP_DUNG_CHO.contains(km.getApDungCho())) {
            throw badRequest("Phạm vi áp dụng không hợp lệ. Chỉ nhận: " + String.join(", ", AP_DUNG_CHO));
        }
        if (km.getGiaTri() <= 0) {
            throw badRequest("Mức giảm phải lớn hơn 0.");
        }
        if ("phan_tram".equals(km.getLoai()) && km.getGiaTri() > 100) {
            throw badRequest("Giảm theo phần trăm không được quá 100%.");
        }
        if (km.getBatDau() != null && km.getKetThuc() != null && km.getKetThuc().isBefore(km.getBatDau())) {
            throw badRequest("Ngày kết thúc phải sau ngày bắt đầu.");
        }
        if (km.getSoLuong() > 0 && km.getDaDung() > km.getSoLuong()) {
            throw badRequest("Số lượt cho phép (" + km.getSoLuong() + ") nhỏ hơn số lượt đã dùng ("
                    + km.getDaDung() + ").");
        }
    }

    private ResponseStatusException khongThay() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chương trình khuyến mãi.");
    }

    private ResponseStatusException badRequest(String thongBao) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, thongBao);
    }

    /** Chuỗi rỗng phải thành null, không thì hai khuyến mãi sản phẩm cùng mã "" là đụng unique. */
    private String chuanHoaMa(String ma) {
        if (ma == null) return null;
        String s = ma.trim().toUpperCase().replaceAll("\\s+", "");
        return s.isEmpty() ? null : s;
    }

    /** "12, 15,,18" -> "12,15,18". Rỗng thành null = áp cho mọi sản phẩm. */
    private String chuanHoaDsId(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        List<String> ds = new ArrayList<>();
        for (String phan : s.split(",")) {
            String t = phan.trim();
            if (t.isEmpty()) continue;
            try { ds.add(String.valueOf(Long.parseLong(t))); }
            catch (NumberFormatException e) { throw badRequest("Danh sách sản phẩm có phần tử không phải số: " + t); }
        }
        return ds.isEmpty() ? null : String.join(",", ds);
    }

    private String chuoi(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private long soLon(Object v) {
        if (v == null) return 0L;
        try { return Long.parseLong(String.valueOf(v).trim().replaceAll("[^0-9-]", "")); }
        catch (Exception e) { return 0L; }
    }

    /** Nhận "2026-08-19" từ ô <input type="date">; chuỗi rỗng = xoá ngày. */
    private LocalDate ngay(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try { return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s); }
        catch (Exception e) { throw badRequest("Ngày không hợp lệ: " + s); }
    }
}
