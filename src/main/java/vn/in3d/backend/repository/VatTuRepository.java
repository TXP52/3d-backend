package vn.in3d.backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.in3d.backend.entity.VatTu;

import java.util.Collection;
import java.util.List;

public interface VatTuRepository extends JpaRepository<VatTu, Long> {
    List<VatTu> findAllByOrderByLoaiAscIdAsc();

    List<VatTu> findByDaXoaFalseOrderByLoaiAscIdAsc();

    /**
     * MỌI vật tư (kể cả đã xoá mềm) kèm màu / tên nhà cung cấp / tên loại trong MỘT truy vấn.
     * Database ở xa: mỗi lượt đi-về ~300 ms — bản cũ 3 truy vấn cho mỗi dòng mất 17 giây.
     * Nối theo khoá chính nên mỗi vật tư đúng một dòng; bộ nhớ đệm tự lọc dòng đã xoá.
     * Mỗi dòng trả về: [VatTu, mãMàu, tênMàu, tênNhàCungCấp, tênLoại]
     */
    @Query("select v, ms.maMau, ms.ten, n.ten, d.ten from VatTu v " +
           "left join MauSac ms on ms.id = v.mauSacId " +
           "left join NhaCungCap n on n.id = v.nhaCungCapId " +
           "left join DanhMuc d on d.id = v.danhMucId " +
           "order by v.loai asc, v.id asc")
    List<Object[]> napKemTen();

    /**
     * Đọc và KHOÁ một lượt mọi cuộn nhựa cần trừ/hoàn gram khi lưu sản phẩm.
     * Khoá theo thứ tự id để hai lần lưu song song không chờ chéo nhau.
     * Phải chạy trong transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VatTu v where v.id in :ids order by v.id asc")
    List<VatTu> khoaCuon(@Param("ids") Collection<Long> ids);
}
