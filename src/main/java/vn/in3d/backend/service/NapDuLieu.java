package vn.in3d.backend.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.stereotype.Component;
import vn.in3d.backend.dto.SanPhamDto;
import vn.in3d.backend.dto.VatTuDto;
import vn.in3d.backend.entity.BaiViet;
import vn.in3d.backend.entity.BanGhi;
import vn.in3d.backend.entity.BienThe;
import vn.in3d.backend.entity.BoSuuTap;
import vn.in3d.backend.entity.DanhMuc;
import vn.in3d.backend.entity.DonHang;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.entity.MauSac;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.entity.NhaCungCap;
import vn.in3d.backend.entity.SanPham;
import vn.in3d.backend.entity.SanPhamVatTu;
import vn.in3d.backend.entity.VatTu;
import vn.in3d.backend.repository.BaiVietRepository;
import vn.in3d.backend.repository.BoSuuTapRepository;
import vn.in3d.backend.repository.DanhMucRepository;
import vn.in3d.backend.repository.KhuyenMaiRepository;
import vn.in3d.backend.repository.MauSacRepository;
import vn.in3d.backend.repository.NguoiDungRepository;
import vn.in3d.backend.repository.NhaCungCapRepository;
import vn.in3d.backend.repository.SanPhamRepository;
import vn.in3d.backend.repository.VatTuRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;

/**
 * Nạp từng bộ dữ liệu cho BoNhoDem — mỗi bộ MỘT lượt đi-về (bộ SP chạy hai truy vấn
 * song song, xem napSanPham).
 *
 * Chỉ dùng truy vấn KHAI BÁO trong repository (derived / @Query): chạy ngoài
 * transaction nên đúng một lượt đi-về. findAll / findById có sẵn của Spring Data
 * tự mở transaction riêng, tốn thêm một lượt COMMIT (~250 ms) mỗi lần gọi.
 * Nạp cả dòng đã xoá mềm để tra theo id và thùng rác khỏi phải hỏi lại.
 */
@Component
public class NapDuLieu {

    private final SanPhamRepository sanPhamRepo;
    private final VatTuRepository vatTuRepo;
    private final MauSacRepository mauSacRepo;
    private final NhaCungCapRepository nccRepo;
    private final DanhMucRepository danhMucRepo;
    private final KhuyenMaiRepository khuyenMaiRepo;
    private final BaiVietRepository baiVietRepo;
    private final NguoiDungRepository nguoiDungRepo;
    private final BoSuuTapRepository boSuuTapRepo;
    private final EntityManagerFactory emf;

    public NapDuLieu(SanPhamRepository sanPhamRepo, VatTuRepository vatTuRepo, MauSacRepository mauSacRepo,
                     NhaCungCapRepository nccRepo, DanhMucRepository danhMucRepo,
                     KhuyenMaiRepository khuyenMaiRepo, BaiVietRepository baiVietRepo,
                     NguoiDungRepository nguoiDungRepo, BoSuuTapRepository boSuuTapRepo,
                     EntityManagerFactory emf) {
        this.sanPhamRepo = sanPhamRepo;
        this.vatTuRepo = vatTuRepo;
        this.mauSacRepo = mauSacRepo;
        this.nccRepo = nccRepo;
        this.danhMucRepo = danhMucRepo;
        this.khuyenMaiRepo = khuyenMaiRepo;
        this.baiVietRepo = baiVietRepo;
        this.nguoiDungRepo = nguoiDungRepo;
        this.boSuuTapRepo = boSuuTapRepo;
        this.emf = emf;
    }

    /**
     * Sản phẩm + biến thể + dòng nhựa + cuộn + màu + tên danh mục: một truy vấn nối bảng.
     * Bộ sưu tập của từng sản phẩm là truy vấn thứ hai, chạy SONG SONG trên luồng khác
     * (kết nối khác) nên cả bộ SP chỉ tốn thời gian của MỘT lượt đi-về chứ không phải hai —
     * bộ SP nạp lại ngay sau mỗi lượt lưu sản phẩm / đơn có trừ kho, chủ shop chờ đúng chỗ này.
     *
     * @param luong luồng chạy truy vấn bộ sưu tập (luồng nạp của BoNhoDem). Luồng nạp bận hết
     *              thì chính luồng này chạy nốt truy vấn đó sau truy vấn chính (FutureTask chỉ
     *              chạy MỘT lần, ai nhận trước người đó chạy) — không bao giờ ngồi chờ một việc
     *              còn nằm trong hàng đợi của chính cái hồ luồng mình đang chiếm.
     */
    public BoNhoDem.DuLieuSanPham napSanPham(Executor luong) {
        FutureTask<Map<Long, List<Map<String, Object>>>> boSuuTapSongSong =
                new FutureTask<>(this::napBoSuuTapTheoSanPham);
        try {
            luong.execute(boSuuTapSongSong);
        } catch (RejectedExecutionException dangTat) {
            // hồ luồng đang tắt: chạy tại chỗ bên dưới
        }

        Map<Long, SanPham> sanPham = new LinkedHashMap<>();
        Map<Long, List<BienThe>> bienTheTheoSanPham = new HashMap<>();
        Map<Long, List<SanPhamVatTu>> dongTheoBienThe = new HashMap<>();
        Map<Long, String> tenDanhMuc = new HashMap<>();
        Map<Long, VatTu> cuonTheoId = new HashMap<>();
        Map<Long, MauSac> mauTheoId = new HashMap<>();
        Set<Long> daCoBienThe = new HashSet<>();      // nối nhựa nên mỗi biến thể lặp lại nhiều dòng

        try {
            for (Object[] dong : sanPhamRepo.napKemNhua()) {
                SanPham sp = (SanPham) dong[0];
                sanPham.putIfAbsent(sp.getId(), sp);
                tenDanhMuc.put(sp.getId(), (String) dong[6]);
                if (dong[1] instanceof BienThe b) {
                    if (daCoBienThe.add(b.getId())) {
                        bienTheTheoSanPham.computeIfAbsent(sp.getId(), k -> new ArrayList<>()).add(b);
                    }
                    if (dong[2] instanceof SanPhamVatTu n) {
                        dongTheoBienThe.computeIfAbsent(b.getId(), k -> new ArrayList<>()).add(n);
                    }
                }
                if (dong[3] instanceof VatTu v) cuonTheoId.put(v.getId(), v);
                if (dong[4] instanceof MauSac m) mauTheoId.put(m.getId(), m);     // màu của cuộn
                if (dong[5] instanceof MauSac m) mauTheoId.put(m.getId(), m);     // màu của biến thể
            }
        } catch (RuntimeException | Error loi) {
            // Truy vấn chính hỏng thì lượt nạp này bỏ: truy vấn bộ sưu tập chưa chạy thì khỏi chạy
            boSuuTapSongSong.cancel(false);
            throw loi;
        }
        boSuuTapSongSong.run();      // chưa luồng nào nhận thì chạy luôn ở đây; đang / đã chạy thì bỏ qua
        Map<Long, List<Map<String, Object>>> boSuuTap = ketQua(boSuuTapSongSong);

        List<Map<String, Object>> danhSach = new ArrayList<>();
        Map<Long, Map<String, Object>> theoId = new HashMap<>();
        List<SanPham> thungRac = new ArrayList<>();
        Map<Long, Map<String, Object>> bienTheTheoId = new HashMap<>();
        Map<Long, Long> sanPhamCuaBienThe = new HashMap<>();
        for (SanPham sp : sanPham.values()) {
            Map<String, Object> dto = SanPhamDto.tao(sp, bienTheTheoSanPham.getOrDefault(sp.getId(), List.of()),
                    dongTheoBienThe, cuonTheoId, mauTheoId, tenDanhMuc.get(sp.getId()),
                    boSuuTap.getOrDefault(sp.getId(), List.of()));
            theoId.put(sp.getId(), dto);
            for (Object o : (List<?>) dto.get("bienThe")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> bt = (Map<String, Object>) o;
                bienTheTheoId.put((Long) bt.get("id"), bt);
                sanPhamCuaBienThe.put((Long) bt.get("id"), sp.getId());
            }
            if (sp.getDaXoa()) thungRac.add(sp);
            else danhSach.add(dto);
        }
        return new BoNhoDem.DuLieuSanPham(Collections.unmodifiableList(danhSach),
                Collections.unmodifiableMap(theoId), Collections.unmodifiableList(thungRac),
                Collections.unmodifiableMap(bienTheTheoId), Collections.unmodifiableMap(sanPhamCuaBienThe));
    }

    /**
     * Bộ sưu tập của từng sản phẩm, {id, ten, duongDan} theo thu_tu rồi id — MỘT truy vấn.
     * Viết bằng SQL gốc chứ không dựng entity: bảng bo_suu_tap do phần bộ sưu tập quản lý,
     * hai nơi cùng khai báo một bảng thì sớm muộn cũng lệch.
     */
    private Map<Long, List<Map<String, Object>>> napBoSuuTapTheoSanPham() {
        Map<Long, List<Map<String, Object>>> ra = new HashMap<>();
        try (EntityManager em = emf.createEntityManager()) {
            List<?> dong = em.createNativeQuery(
                    "select l.san_pham_id, b.id, b.ten, b.duong_dan from san_pham_bo_suu_tap l "
                    + "join bo_suu_tap b on b.id = l.bo_suu_tap_id "
                    + "where not b.is_deleted order by l.san_pham_id, b.thu_tu, b.id").getResultList();
            for (Object o : dong) {
                Object[] d = (Object[]) o;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", ((Number) d[1]).longValue());
                m.put("ten", d[2]);
                m.put("duongDan", d[3]);
                ra.computeIfAbsent(((Number) d[0]).longValue(), k -> new ArrayList<>())
                        .add(Collections.unmodifiableMap(m));
            }
        }
        for (var e : ra.entrySet()) e.setValue(Collections.unmodifiableList(e.getValue()));
        return ra;
    }

    /** Kết quả của việc chạy song song; lỗi của nó ném lại y nguyên như gọi thẳng. */
    private static <T> T ketQua(FutureTask<T> viec) {
        try {
            return viec.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị ngắt khi đang nạp bộ nhớ đệm", e);
        } catch (ExecutionException e) {
            Throwable goc = e.getCause();
            if (goc instanceof RuntimeException re) throw re;
            if (goc instanceof Error er) throw er;
            throw new IllegalStateException(goc);
        }
    }

    /** Vật tư kèm tên màu / nhà cung cấp / loại: một truy vấn nối bảng. */
    public BoNhoDem.DuLieuVatTu napVatTu() {
        List<Map<String, Object>> danhSach = new ArrayList<>();
        Map<Long, Map<String, Object>> theoId = new HashMap<>();
        for (Object[] dong : vatTuRepo.napKemTen()) {
            VatTu v = (VatTu) dong[0];
            Map<String, Object> dto = VatTuDto.tao(v, (String) dong[1], (String) dong[2],
                    (String) dong[3], (String) dong[4]);
            theoId.put(v.getId(), dto);
            if (!v.getDaXoa()) danhSach.add(dto);
        }
        return new BoNhoDem.DuLieuVatTu(Collections.unmodifiableList(danhSach), Collections.unmodifiableMap(theoId));
    }

    public BoNhoDem.DuLieuBang<MauSac> napMauSac() {
        return bang(mauSacRepo.findAllByOrderByThuTuAscIdAsc(), MauSac::getId);
    }

    public BoNhoDem.DuLieuBang<NhaCungCap> napNhaCungCap() {
        return bang(nccRepo.findAllByOrderByIdAsc(), NhaCungCap::getId);
    }

    public BoNhoDem.DuLieuBang<DanhMuc> napDanhMuc() {
        return bang(danhMucRepo.findAllByOrderByThuTuAscIdAsc(), DanhMuc::getId);
    }

    public BoNhoDem.DuLieuKhuyenMai napKhuyenMai() {
        List<KhuyenMai> tatCa = khuyenMaiRepo.findAllByOrderByIdDesc();
        List<KhuyenMai> danhSach = new ArrayList<>();
        List<KhuyenMai> thungRac = new ArrayList<>();
        Map<Long, KhuyenMai> theoId = new HashMap<>();
        for (KhuyenMai km : tatCa) {
            theoId.put(km.getId(), km);
            (km.getDaXoa() ? thungRac : danhSach).add(km);
        }
        return new BoNhoDem.DuLieuKhuyenMai(Collections.unmodifiableList(danhSach),
                Collections.unmodifiableList(thungRac), Collections.unmodifiableMap(theoId));
    }

    public BoNhoDem.DuLieuBaiViet napBaiViet() {
        List<BaiViet> tatCa = baiVietRepo.findAllByOrderByThuTuAscIdDesc();
        List<BaiViet> danhSach = new ArrayList<>();
        List<BaiViet> thungRac = new ArrayList<>();
        Map<Long, BaiViet> theoId = new HashMap<>();
        Map<String, BaiViet> theoDuongDan = new HashMap<>();
        for (BaiViet b : tatCa) {
            theoId.put(b.getId(), b);
            if (b.getDaXoa()) {
                thungRac.add(b);
            } else {
                danhSach.add(b);
                if (b.getDuongDan() != null) theoDuongDan.put(b.getDuongDan(), b);
            }
        }
        // Thùng rác giữ đúng thứ tự của truy vấn cũ: id giảm dần
        thungRac.sort(Comparator.comparing(BaiViet::getId).reversed());
        return new BoNhoDem.DuLieuBaiViet(Collections.unmodifiableList(danhSach),
                Collections.unmodifiableList(thungRac), Collections.unmodifiableMap(theoId),
                Collections.unmodifiableMap(theoDuongDan));
    }

    /**
     * Đơn hàng CHƯA xoá kèm chi tiết (nối luôn trong truy vấn chính) và thanh toán
     * (nạp theo lô 100 đơn một truy vấn). Dùng EntityManager riêng, không mở transaction:
     * 2 lượt đi-về thay vì BEGIN/SELECT/SELECT/SELECT/COMMIT như bản cũ.
     */
    public BoNhoDem.DuLieuDonHang napDonHang() {
        try (EntityManager em = emf.createEntityManager()) {
            List<DonHang> ds = em.createQuery(
                    "select d from DonHang d left join fetch d.chiTiet "
                    + "where d.daXoa = false order by d.createdAt desc", DonHang.class).getResultList();
            // Nạp thanh toán khi EntityManager còn mở — trả JSON lúc đã đóng thì lỗi lazy
            ds.forEach(d -> d.getThanhToan().size());
            Map<Long, DonHang> theoId = new HashMap<>();
            for (DonHang d : ds) theoId.put(d.getId(), d);
            return new BoNhoDem.DuLieuDonHang(Collections.unmodifiableList(new ArrayList<>(ds)),
                    Collections.unmodifiableMap(theoId));
        }
    }

    /** Một đơn bất kỳ (kể cả đã xoá mềm) đã nạp sẵn chi tiết + thanh toán; không có thì null. */
    public DonHang motDonHang(Long id) {
        try (EntityManager em = emf.createEntityManager()) {
            DonHang d = em.find(DonHang.class, id);
            if (d != null) {
                d.getChiTiet().size();
                d.getThanhToan().size();
            }
            return d;
        }
    }

    /**
     * Bộ sưu tập kèm danh sách sản phẩm của từng bộ — MỘT truy vấn (left join fetch).
     * Nạp cả bộ đã xoá mềm để tra theo id khỏi phải hỏi lại database.
     * Tên / ảnh sản phẩm không nằm ở đây: lúc trả lời mới tra sang bản chụp sản phẩm
     * (xem BoSuuTapDto), nên đổi tên sản phẩm không phải xoá bộ nhớ đệm bộ sưu tập.
     */
    public BoNhoDem.DuLieuBoSuuTap napBoSuuTap() {
        List<BoSuuTap> danhSach = new ArrayList<>();
        Map<Long, BoSuuTap> theoId = new HashMap<>();
        Map<String, BoSuuTap> theoDuongDan = new HashMap<>();
        for (BoSuuTap b : boSuuTapRepo.napKemSanPham()) {
            theoId.put(b.getId(), b);
            if (b.getDaXoa()) continue;
            danhSach.add(b);
            if (b.getDuongDan() != null) theoDuongDan.put(b.getDuongDan(), b);
        }
        return new BoNhoDem.DuLieuBoSuuTap(Collections.unmodifiableList(danhSach),
                Collections.unmodifiableMap(theoId), Collections.unmodifiableMap(theoDuongDan));
    }

    public BoNhoDem.DuLieuNguoiDung napNguoiDung() {
        List<NguoiDung> danhSach = new ArrayList<>();
        Map<String, NguoiDung> theoEmail = new HashMap<>();
        for (NguoiDung nd : nguoiDungRepo.findAllByOrderByIdAsc()) {
            if (nd.getEmail() != null) theoEmail.put(nd.getEmail().toLowerCase(Locale.ROOT), nd);
            if (!nd.getDaXoa()) danhSach.add(nd);
        }
        return new BoNhoDem.DuLieuNguoiDung(Collections.unmodifiableList(danhSach),
                Collections.unmodifiableMap(theoEmail));
    }

    /** Chia bảng đơn giản: danh sách dòng chưa xoá (giữ thứ tự truy vấn) + tra theo id mọi dòng. */
    private static <T extends BanGhi> BoNhoDem.DuLieuBang<T> bang(List<T> tatCa, Function<T, Long> layId) {
        List<T> danhSach = new ArrayList<>();
        Map<Long, T> theoId = new HashMap<>();
        for (T t : tatCa) {
            theoId.put(layId.apply(t), t);
            if (!t.getDaXoa()) danhSach.add(t);
        }
        return new BoNhoDem.DuLieuBang<>(Collections.unmodifiableList(danhSach), Collections.unmodifiableMap(theoId));
    }
}
