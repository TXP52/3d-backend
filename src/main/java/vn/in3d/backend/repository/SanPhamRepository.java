package vn.in3d.backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.in3d.backend.entity.SanPham;

import java.util.List;
import java.util.Optional;

public interface SanPhamRepository extends JpaRepository<SanPham, Long> {
    List<SanPham> findByDangBanTrueOrderByIdAsc();

    /**
     * Tra sản phẩm theo tên để đối chiếu giá lúc tạo đơn.
     * Giỏ hàng của khách chỉ mang theo TÊN món, không mang id.
     */
    Optional<SanPham> findFirstByTenIgnoreCaseAndDaXoaFalse(String ten);

    /** Hàng đang bán, chưa bị xoá mềm — dùng cho trang bán hàng. */
    List<SanPham> findByDangBanTrueAndDaXoaFalseOrderByIdAsc();

    /** Toàn bộ hàng chưa bị xoá mềm — dùng cho trang quản trị. */
    List<SanPham> findByDaXoaFalseOrderByIdAsc();

    /**
     * MỌI sản phẩm (kể cả đã xoá mềm) kèm BIẾN THỂ, dòng nhựa của từng biến thể,
     * cuộn, màu và tên danh mục trong MỘT truy vấn — bộ nhớ đệm dựng danh sách
     * sản phẩm từ đây.
     * Bản cũ gọi 5 truy vấn, 4 cái findAll mỗi cái thêm một lượt COMMIT: 9 lượt, ~2,4 giây.
     * Mỗi dòng trả về:
     *   [SanPham, BienThe|null, SanPhamVatTu|null, VatTu|null, MàuCủaCuộn|null, MàuCủaBiếnThể|null, tênDanhMục|null]
     * Biến thể có n dòng nhựa thì lặp lại n dòng — NapDuLieu gộp theo id.
     * Thứ tự: sản phẩm tăng dần, biến thể mặc định trước rồi thu_tu rồi id, dòng nhựa theo id.
     */
    @Query("select sp, b, n, v, ms, msb, dm.ten from SanPham sp " +
           "left join BienThe b on b.sanPhamId = sp.id and b.daXoa = false " +
           "left join SanPhamVatTu n on n.bienTheId = b.id " +
           "left join VatTu v on v.id = n.vatTuId " +
           "left join MauSac ms on ms.id = v.mauSacId " +
           "left join MauSac msb on msb.id = b.mauSacId " +
           "left join DanhMuc dm on dm.id = sp.danhMucId " +
           "order by sp.id asc, b.macDinh desc, b.thuTu asc, b.id asc, n.id asc")
    List<Object[]> napKemNhua();

    /**
     * Đọc và KHOÁ dòng sản phẩm tới hết transaction: hai lần lưu cùng một sản phẩm
     * chạy song song thì lần sau phải chờ, nên không trừ kho hai lần.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SanPham s where s.id = :id")
    Optional<SanPham> khoaTheoId(@Param("id") Long id);
}
