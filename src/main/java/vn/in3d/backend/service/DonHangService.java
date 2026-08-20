package vn.in3d.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.dto.DatHangRequest;
import vn.in3d.backend.entity.DonHang;
import vn.in3d.backend.entity.DonHangChiTiet;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.entity.SanPham;
import vn.in3d.backend.entity.ThanhToan;
import vn.in3d.backend.repository.DonHangRepository;
import vn.in3d.backend.repository.SanPhamRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Nghiệp vụ đơn hàng: tạo đơn, đổi trạng thái, xác nhận thanh toán. */
@Service
public class DonHangService {

    private final DonHangRepository donHangRepo;
    private final KhuyenMaiService khuyenMaiService;
    private final SanPhamRepository sanPhamRepo;

    public DonHangService(DonHangRepository donHangRepo,
                          KhuyenMaiService khuyenMaiService,
                          SanPhamRepository sanPhamRepo) {
        this.donHangRepo = donHangRepo;
        this.khuyenMaiService = khuyenMaiService;
        this.sanPhamRepo = sanPhamRepo;
    }

    /** Tạo đơn mới: đơn hàng + từng món + bản ghi thanh toán COD. */
    @Transactional
    public DonHang datHang(DatHangRequest yeuCau) {
        DonHang don = new DonHang();
        don.setMaDon(sinhMaDon());
        don.setTenKhach(yeuCau.tenKhach().trim());
        don.setSoDienThoai(yeuCau.soDienThoai().trim());
        don.setDiaChi(yeuCau.diaChi().trim());
        don.setGhiChu(yeuCau.ghiChu());

        // Bước 1 — GIẢM GIÁ SẢN PHẨM (tự áp, không cần mã).
        // Giá lấy từ DATABASE chứ không lấy giá trình duyệt gửi lên: giỏ hàng nằm
        // trong localStorage, sửa một dòng là đặt được máy in 3D giá 1.000đ.
        // Món không có trong bảng sản phẩm (in theo yêu cầu, thiết kế file...) thì
        // đành theo giá gửi lên vì không có gì để đối chiếu.
        List<KhuyenMai> kmSanPham = khuyenMaiService.khuyenMaiSanPhamDangChay();
        long tongTien = 0;
        long giamSanPham = 0;

        for (DatHangRequest.MatHang mh : yeuCau.matHang()) {
            String ten = mh.ten().trim();
            long giaGui = mh.donGia() == null ? 0 : mh.donGia();
            int soLuong = mh.soLuong() == null ? 1 : mh.soLuong();

            SanPham sp = sanPhamRepo.findFirstByTenIgnoreCaseAndDaXoaFalse(ten).orElse(null);
            long giaGoc = sp != null ? (sp.getGia() == null ? 0 : sp.getGia()) : giaGui;
            KhuyenMaiService.GiaMon gia = sp != null
                    ? khuyenMaiService.giaSauGiam(sp.getId(), giaGoc, kmSanPham)
                    : new KhuyenMaiService.GiaMon(giaGoc, giaGoc, 0, null);

            DonHangChiTiet ct = new DonHangChiTiet();
            ct.setSanPhamId(sp != null ? sp.getId() : null);
            ct.setTenSanPham(ten);
            ct.setDonGiaGoc(gia.giaGoc());
            ct.setDonGia(gia.giaSauGiam());
            ct.setSoLuong(soLuong);
            don.themChiTiet(ct);

            tongTien += ct.getThanhTien();
            giamSanPham += ct.getTienGiamDong();
        }
        don.setTienGiamSanPham(giamSanPham);

        // Bước 2 — MÃ KHUYẾN MÃI ĐƠN HÀNG, tính trên số tiền ĐÃ giảm giá món.
        // TÍNH LẠI TỪ ĐẦU ở đây, không nhận số tiền giảm trình duyệt gửi lên.
        // Mã sai / hết hạn / chưa đủ điều kiện -> kiemTra ném lỗi 400, đơn không được tạo.
        Long idKhuyenMai = null;
        String ma = yeuCau.maKhuyenMai();
        if (ma != null && !ma.isBlank()) {
            KhuyenMaiService.KetQua kq = khuyenMaiService.kiemTra(ma, tongTien);
            don.setMaKhuyenMai(kq.khuyenMai().getMa());
            don.setTienGiam(kq.tienGiam());
            tongTien = kq.conLai();
            idKhuyenMai = kq.khuyenMai().getId();
        }
        don.setTongTien(tongTien);

        ThanhToan tt = new ThanhToan();
        tt.setPhuongThuc("cod");
        tt.setSoTien(tongTien);
        don.themThanhToan(tt);

        DonHang daLuu = donHangRepo.save(don);
        // Chỉ trừ lượt khi đơn đã lưu xong; cùng transaction nên đơn lỗi là lượt cũng không mất
        if (idKhuyenMai != null) khuyenMaiService.ghiNhanDaDung(idKhuyenMai);
        return daLuu;
    }

    @Transactional(readOnly = true)
    public List<DonHang> danhSachDon() {
        List<DonHang> ds = donHangRepo.findByDaXoaFalseOrderByCreatedAtDesc();
        // Nạp sẵn chi tiết + thanh toán TRONG transaction, tránh LazyInitializationException khi trả JSON
        ds.forEach(this::napDuLieuCon);
        return ds;
    }

    @Transactional
    public DonHang doiTrangThai(Long id, String trangThaiMoi) {
        if (!DonHang.TRANG_THAI_HOP_LE.contains(trangThaiMoi)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái không hợp lệ. Hợp lệ: " + DonHang.TRANG_THAI_HOP_LE);
        }
        DonHang don = timDon(id);
        don.setTrangThai(trangThaiMoi);
        DonHang daLuu = donHangRepo.save(don);
        napDuLieuCon(daLuu);
        return daLuu;
    }

    @Transactional
    public DonHang danhDauDaThanhToan(Long id) {
        DonHang don = timDon(id);
        for (ThanhToan tt : don.getThanhToan()) {
            tt.setTrangThai("da_thanh_toan");
            tt.setThanhToanLuc(OffsetDateTime.now());
        }
        DonHang daLuu = donHangRepo.save(don);
        napDuLieuCon(daLuu);
        return daLuu;
    }

    /** Ép Hibernate nạp các collection lazy trước khi entity rời transaction. */
    private void napDuLieuCon(DonHang don) {
        don.getChiTiet().size();
        don.getThanhToan().size();
    }

    @Transactional
    public void xoaDon(Long id) {
        // XOÁ MỀM: đơn hàng là chứng từ bán hàng, không xoá hẳn khỏi database
        DonHang don = timDon(id);
        don.xoaMem();
        donHangRepo.save(don);
    }

    private DonHang timDon(Long id) {
        return donHangRepo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng id=" + id));
    }

    /** Mã đơn dạng IN3D-XXXXXXXX, trùng phong cách mã bên frontend. */
    private String sinhMaDon() {
        String phan1 = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
        String phan2 = Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36), 36).toUpperCase(Locale.ROOT);
        return "IN3D-" + phan1 + phan2;
    }
}
