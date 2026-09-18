package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.in3d.backend.entity.BienThe;

import java.util.List;

public interface BienTheRepository extends JpaRepository<BienThe, Long> {

    /**
     * Biến thể CHƯA xoá của MỘT sản phẩm kèm dòng nhựa của từng biến thể —
     * một truy vấn cho cả lượt lưu (trước đây đọc dòng nhựa là một lượt riêng).
     * Mỗi dòng trả về: [BienThe, SanPhamVatTu|null]; biến thể có n dòng nhựa
     * thì lặp lại n lần, nơi gọi tự gộp theo id.
     */
    @Query("select b, n from BienThe b "
         + "left join SanPhamVatTu n on n.bienTheId = b.id "
         + "where b.sanPhamId = :sanPhamId and b.daXoa = false "
         + "order by b.id asc, n.id asc")
    List<Object[]> napKemNhua(@Param("sanPhamId") Long sanPhamId);
}
