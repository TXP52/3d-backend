package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.in3d.backend.entity.LoNhap;

import java.util.List;

public interface LoNhapRepository extends JpaRepository<LoNhap, Long> {

    /** Các đợt nhập của MỘT vật tư, đợt cũ trước. */
    List<LoNhap> findByVatTuIdOrderByNgayNhapAscIdAsc(Long vatTuId);

    /**
     * MỌI đợt nhập kèm tên nơi mua trong MỘT truy vấn — bộ nhớ đệm kho gọi một lần
     * rồi gom theo vat_tu_id (database ở xa, hỏi từng vật tư là mỗi vật tư một lượt đi-về).
     * Mỗi dòng: [LoNhap, tênNhàCungCấp]
     */
    @Query("select l, n.ten from LoNhap l left join NhaCungCap n on n.id = l.nhaCungCapId "
            + "order by l.vatTuId asc, l.ngayNhap asc, l.id asc")
    List<Object[]> napKemTenNcc();
}
