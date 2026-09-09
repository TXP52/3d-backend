package vn.in3d.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.in3d.backend.entity.DanhMuc;

import java.util.List;
import java.util.Optional;

public interface DanhMucRepository extends JpaRepository<DanhMuc, Long> {

    /** Danh mục chưa xoá, theo thứ tự hiển thị. */
    List<DanhMuc> findByDaXoaFalseOrderByThuTuAscIdAsc();

    /** Chỉ những danh mục còn bật — dùng cho website bán hàng. */
    List<DanhMuc> findByDaXoaFalseAndDangHienTrueOrderByThuTuAscIdAsc();

    /** Trùng tên chỉ tính trong cùng nhóm: "Phụ kiện" vừa là loại vật tư vừa là danh mục sản phẩm được. */
    Optional<DanhMuc> findByTenIgnoreCaseAndNhomAndDaXoaFalse(String ten, String nhom);

    List<DanhMuc> findByNhomAndDaXoaFalseOrderByThuTuAscIdAsc(String nhom);
}
