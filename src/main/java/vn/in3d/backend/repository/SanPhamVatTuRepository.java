package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.in3d.backend.entity.SanPhamVatTu;

public interface SanPhamVatTuRepository extends JpaRepository<SanPhamVatTu, Long> {

    /**
     * Xoá hết dòng nhựa của một sản phẩm (mọi biến thể) bằng MỘT lệnh DELETE.
     * Bản derived deleteBySanPhamId cũ SELECT lại các dòng rồi xoá từng dòng một.
     * Phải chạy trong transaction.
     *
     * Dòng nhựa đang lưu thì đọc kèm biến thể trong MỘT truy vấn, xem BienTheRepository.napKemNhua.
     */
    @Modifying
    @Query("delete from SanPhamVatTu n where n.sanPhamId = :sanPhamId")
    int xoaTheoSanPham(@Param("sanPhamId") Long sanPhamId);
}
