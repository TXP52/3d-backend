package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.in3d.backend.entity.VatTu;

import java.util.List;

public interface VatTuRepository extends JpaRepository<VatTu, Long> {
    List<VatTu> findAllByOrderByLoaiAscIdAsc();

    List<VatTu> findByDaXoaFalseOrderByLoaiAscIdAsc();

    /**
     * Danh sách vật tư kèm mã màu / tên nhà cung cấp / tên loại trong MỘT truy vấn.
     * Database ở xa: mỗi lượt đi-về ~300 ms, lại thêm một lượt kiểm tra kết nối mỗi lần
     * mượn từ pool — bản cũ 3 truy vấn cho mỗi dòng mất 17 giây, bản 4 truy vấn mất
     * 2 giây, gộp bằng subquery còn một lượt.
     * Mỗi dòng trả về: [VatTu, mãMàuTheoId, mãMàuTheoTên, tênNhàCungCấp, tênLoại]
     */
    @Query("select v, " +
           "(select min(ms.maMau) from MauSac ms where ms.id = v.mauSacId), " +
           "(select min(ms2.maMau) from MauSac ms2 where ms2.daXoa = false and lower(ms2.ten) = lower(v.mau)), " +
           "(select min(n.ten) from NhaCungCap n where n.id = v.nhaCungCapId), " +
           "(select min(d.ten) from DanhMuc d where d.id = v.danhMucId) " +
           "from VatTu v where v.daXoa = false order by v.loai asc, v.id asc")
    List<Object[]> danhSachKemTen();
}
