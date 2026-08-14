package vn.in3d.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.dto.DatHangRequest;
import vn.in3d.backend.entity.DonHang;
import vn.in3d.backend.entity.DonHangChiTiet;
import vn.in3d.backend.entity.ThanhToan;
import vn.in3d.backend.repository.DonHangRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Nghiệp vụ đơn hàng: tạo đơn, đổi trạng thái, xác nhận thanh toán. */
@Service
public class DonHangService {

    private final DonHangRepository donHangRepo;

    public DonHangService(DonHangRepository donHangRepo) {
        this.donHangRepo = donHangRepo;
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

        long tongTien = 0;
        for (DatHangRequest.MatHang mh : yeuCau.matHang()) {
            DonHangChiTiet ct = new DonHangChiTiet();
            ct.setTenSanPham(mh.ten().trim());
            ct.setDonGia(mh.donGia() == null ? 0 : mh.donGia());
            ct.setSoLuong(mh.soLuong() == null ? 1 : mh.soLuong());
            don.themChiTiet(ct);
            tongTien += ct.getThanhTien();
        }
        don.setTongTien(tongTien);

        ThanhToan tt = new ThanhToan();
        tt.setPhuongThuc("cod");
        tt.setSoTien(tongTien);
        don.themThanhToan(tt);

        return donHangRepo.save(don);
    }

    @Transactional(readOnly = true)
    public List<DonHang> danhSachDon() {
        List<DonHang> ds = donHangRepo.findAllByOrderByCreatedAtDesc();
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
        donHangRepo.delete(timDon(id));
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
