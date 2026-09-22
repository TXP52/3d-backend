package vn.in3d.backend.dto;

import vn.in3d.backend.entity.BienThe;
import vn.in3d.backend.entity.MauSac;
import vn.in3d.backend.entity.SanPham;
import vn.in3d.backend.entity.SanPhamVatTu;
import vn.in3d.backend.entity.VatTu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Một sản phẩm dạng DANH SÁCH của GET /api/san-pham — CHỖ DUY NHẤT dựng nó.
 * Bộ nhớ đệm (NapDuLieu) dựng sẵn cho mọi sản phẩm; POST/PUT sản phẩm trả lại
 * đúng object đó lấy từ bộ nhớ đệm vừa nạp lại, nên hai bên không bao giờ lệch.
 *
 * Số liệu của sản phẩm là TỔNG của các BIẾN THỂ chưa xoá: tồn kho, số cái đã in,
 * nhựa đã dùng, tiền nhựa; giá thì trả khoảng giaTu–giaDen. Danh sách nhựa và
 * màu cũng gộp từ các biến thể theo đúng thứ tự biến thể, nên sản phẩm chỉ có
 * một biến thể mặc định (dữ liệu cũ) cho ra y hệt JSON trước khi có biến thể.
 *
 * Object trả về KHÔNG sửa được (dùng chung cho mọi request); muốn thêm trường
 * thì chép ra new LinkedHashMap<>(dto) rồi thêm — y như kemChiPhiMay ghép tiền máy
 * cho trang quản trị lúc trả lời.
 */
public final class SanPhamDto {

    private SanPhamDto() {}

    /**
     * @param dsBienThe        biến thể CHƯA xoá của sản phẩm (thứ tự nào cũng được, hàm tự xếp)
     * @param dongTheoBienThe  dòng nhựa tra theo id biến thể, mỗi danh sách theo id tăng dần
     * @param cuonTheoId       cuộn nhựa (vat_tu) tra theo id, kể cả cuộn đã xoá mềm
     * @param mauTheoId        màu tra theo id, kể cả màu đã xoá mềm
     * @param tenDanhMuc       tên danh mục của sản phẩm (null nếu chưa phân loại)
     * @param boSuuTap         bộ sưu tập sản phẩm đang nằm trong, mỗi phần tử {id, ten, duongDan}
     */
    public static Map<String, Object> tao(SanPham sp, List<BienThe> dsBienThe,
                                          Map<Long, List<SanPhamVatTu>> dongTheoBienThe,
                                          Map<Long, VatTu> cuonTheoId, Map<Long, MauSac> mauTheoId,
                                          String tenDanhMuc, List<Map<String, Object>> boSuuTap) {
        // Biến thể mặc định trước, rồi thu_tu, rồi id — thứ tự này dùng chung cho
        // bienThe[], danh sách nhựa gộp và danh sách màu gộp
        List<BienThe> dsBt = new ArrayList<>(dsBienThe);
        dsBt.sort(Comparator.comparingInt((BienThe b) -> b.getMacDinh() ? 0 : 1)
                .thenComparingInt(BienThe::getThuTu)
                .thenComparingLong(b -> b.getId() == null ? Long.MAX_VALUE : b.getId()));

        List<Map<String, Object>> dsBienTheDto = new ArrayList<>();
        List<Map<String, Object>> dsNhua = new ArrayList<>();
        List<Map<String, Object>> dsMau = new ArrayList<>();
        Set<Long> daCoMau = new LinkedHashSet<>();
        int tongGram = 0, tonKho = 0, soLuong = 0;
        long tienNhua = 0;
        int gramMin = Integer.MAX_VALUE, gramMax = 0;
        boolean nhieuMau = false;
        long giaTu = Long.MAX_VALUE, giaDen = Long.MIN_VALUE;

        for (BienThe bt : dsBt) {
            List<SanPhamVatTu> dong = bt.getId() == null
                    ? List.of() : dongTheoBienThe.getOrDefault(bt.getId(), List.of());
            List<Map<String, Object>> nhuaBt = new ArrayList<>();
            int gramBt = 0;
            long tienBt = 0;

            // Màu KHAI BÁO của biến thể đứng trước màu suy từ cuộn nhựa
            MauSac mauBt = bt.getMauSacId() == null ? null : mauTheoId.get(bt.getMauSacId());
            if (mauBt != null && daCoMau.add(mauBt.getId())) dsMau.add(dongMau(mauBt));

            for (var n : dong) {
                var v = cuonTheoId.get(n.getVatTuId());
                // Cuộn chưa chọn màu thì khỏi tra (Map.of ném NullPointerException khi tra khoá null)
                var mau = v == null || v.getMauSacId() == null ? null : mauTheoId.get(v.getMauSacId());

                Map<String, Object> d = new LinkedHashMap<>();
                d.put("id", n.getId());
                d.put("vatTuId", n.getVatTuId());
                d.put("ten", v == null ? "(cuộn đã xoá)" : v.getTen());
                // gramNhua / gramThua là của MỘT cái; tongGram mới là phần kho mất
                d.put("soLuong", n.getSoLuong());
                d.put("gramNhua", n.getGramNhua());
                d.put("gramThua", n.getGramThua());
                d.put("gramMoiCai", n.getGramMoiCai());
                d.put("tongGram", n.getTongGram());
                d.put("mauSacId", v == null ? null : v.getMauSacId());
                d.put("mau", mau == null ? null : mau.getTen());
                d.put("maMau", mau == null ? null : mau.getMaMau());
                d.put("donGiaMoiGram", v == null ? 0 : v.getDonGiaMoiGram());
                Map<String, Object> dongNhua = Collections.unmodifiableMap(d);
                nhuaBt.add(dongNhua);
                dsNhua.add(dongNhua);                 // sản phẩm gộp nhựa của mọi biến thể

                gramBt += n.getTongGram();
                if (v != null) tienBt += Math.round(n.getTongGram() * v.getDonGiaMoiGram());
                if (n.getGramMoiCai() > 0) {
                    gramMin = Math.min(gramMin, n.getGramMoiCai());
                    gramMax = Math.max(gramMax, n.getGramMoiCai());
                }

                // Màu của sản phẩm CHÍNH LÀ màu của cuộn — không lưu riêng nên không lệch được.
                // LinkedHashMap chứ không Map.of: Map.of xáo thứ tự khoá theo từng lần chạy JVM,
                // nên JSON trả về đổi thứ tự khoá sau mỗi lần khởi động lại backend.
                if (mau != null && daCoMau.add(mau.getId())) dsMau.add(dongMau(mau));
            }

            long giaHien = bt.getGia() != null ? bt.getGia() : (sp.getGia() == null ? 0L : sp.getGia());
            giaTu = Math.min(giaTu, giaHien);
            giaDen = Math.max(giaDen, giaHien);
            tonKho += bt.getTonKho();
            soLuong += bt.getSoLuong();
            nhieuMau = nhieuMau || bt.getNhieuMau();
            tongGram += gramBt;
            tienNhua += tienBt;

            Map<String, Object> b = new LinkedHashMap<>();
            b.put("id", bt.getId());
            b.put("ten", bt.getTen());
            b.put("macDinh", bt.getMacDinh());
            b.put("thuTu", bt.getThuTu());
            b.put("maSku", bt.getMaSku());
            b.put("mauSacId", bt.getMauSacId());
            b.put("mau", mauBt == null ? null : mauBt.getTen());
            b.put("maMau", mauBt == null ? null : mauBt.getMaMau());
            b.put("gia", bt.getGia());                // null = lấy giá sản phẩm
            b.put("giaHienThi", giaHien);
            b.put("tonKho", bt.getTonKho());
            b.put("soLuong", bt.getSoLuong());
            b.put("thoiGianInPhut", bt.getThoiGianInPhut());   // phút in MỘT cái
            b.put("nhieuMau", bt.getNhieuMau());
            b.put("trangThai", bt.getTrangThai());    // null = theo trạng thái sản phẩm
            b.put("trangThaiHienThi", bt.getTrangThai() == null || bt.getTrangThai().isBlank()
                    ? sp.getTrangThai() : bt.getTrangThai());
            b.put("danhSachAnh", Collections.unmodifiableList(bt.getDanhSachAnhList()));
            List<String> anhBt = bt.getDanhSachAnhList();
            b.put("hinhAnh", anhBt.isEmpty() ? null : anhBt.get(0));
            b.put("vatTus", Collections.unmodifiableList(nhuaBt));
            b.put("tongGramNhua", gramBt);
            b.put("tienNhua", tienBt);
            dsBienTheDto.add(Collections.unmodifiableMap(b));
        }

        // Sản phẩm chưa có biến thể nào (chỉ xảy ra khi sửa tay trên database): giữ số
        // liệu của chính sản phẩm, đừng trả 0 làm chủ shop tưởng mất hàng
        if (dsBt.isEmpty()) {
            tonKho = sp.getTonKho() == null ? 0 : sp.getTonKho();
            soLuong = sp.getSoLuong();
            nhieuMau = sp.getNhieuMau();
            giaTu = giaDen = sp.getGia() == null ? 0L : sp.getGia();
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", sp.getId());
        // Dòng cũ chưa có mã thì "SP-<id>" — đúng mã trang web vẫn hiện trước giờ
        m.put("maSanPham", sp.getMaHienThi());
        m.put("ten", sp.getTen());
        m.put("moTa", sp.getMoTa());
        m.put("gia", sp.getGia());
        m.put("giaChu", sp.getGiaChu());
        m.put("danhMucId", sp.getDanhMucId());
        // Tên danh mục để trang chi tiết bên web khách khỏi phải gọi thêm /danh-muc
        m.put("danhMuc", tenDanhMuc);
        m.put("hinhAnh", sp.getHinhAnh());
        // Tất cả ảnh (ảnh đầu = ảnh bìa) — web khách làm slide ở trang chi tiết
        m.put("danhSachAnh", Collections.unmodifiableList(sp.getDanhSachAnhList()));
        m.put("tonKho", tonKho);                      // tổng tồn kho các biến thể
        m.put("soLuong", soLuong);                    // tổng số cái đã in
        m.put("nhieuMau", nhieuMau);                  // có biến thể nào đếm kiểu nhiều màu không
        m.put("dangBan", sp.getDangBan());
        m.put("loaiSanPham", sp.getLoaiSanPham());
        m.put("trangThai", sp.getTrangThai());
        m.put("createdAt", sp.getCreatedAt());
        m.put("updatedAt", sp.getUpdatedAt());
        m.put("daXoa", sp.getDaXoa());
        m.put("vatTus", Collections.unmodifiableList(dsNhua));
        m.put("mauSac", Collections.unmodifiableList(dsMau));
        m.put("tongGramNhua", tongGram);              // nhựa đã trừ khỏi kho
        // Nhựa cho MỘT cái: các dòng có thể khác nhau nên trả cả khoảng
        m.put("gramMoiCaiMin", gramMax == 0 ? 0 : gramMin);
        m.put("gramMoiCaiMax", gramMax);
        m.put("tienNhua", tienNhua);
        m.put("bienThe", Collections.unmodifiableList(dsBienTheDto));
        // Khoảng giá của sản phẩm: bằng nhau hết thì hai số bằng nhau, trang ngoài tự gộp
        m.put("giaTu", giaTu);
        m.put("giaDen", giaDen);
        m.put("boSuuTap", boSuuTap == null ? List.of() : boSuuTap);
        return Collections.unmodifiableMap(m);
    }

    /**
     * Bản sao DTO sản phẩm KÈM TIỀN MÁY — chỉ trang quản trị thấy (web khách dựng DTO gọn
     * riêng, không bao giờ đi qua đây).
     *
     * Không nằm sẵn trong bộ nhớ đệm sản phẩm: chi phí chạy máy mỗi giờ suy từ máy in trong
     * kho + bảng cai_dat (ChiPhiMayService), nên ghép lúc trả lời — sửa giá máy hay định mức
     * điện / bảo trì là thấy số mới ngay, khỏi nạp lại cả bộ sản phẩm.
     *
     * Thêm vào mỗi biến thể (cuối object):
     *   tienMayMoiCai = làm tròn(phút in một cái / 60 × tiền máy mỗi giờ)
     *   tienMay       = tienMayMoiCai × số cái đã in
     *   giaVonMoiCai  = làm tròn(tiền nhựa / số cái đã in) + tienMayMoiCai
     *                   (tiền nhựa đã gồm gram thừa; đúng bằng nhựa của MỘT cái cả khi đếm
     *                   một màu lẫn nhiều màu, vì số cái đã được chuẩn hoá theo dòng nhựa)
     * và vào sản phẩm: tongPhutIn = Σ phút × số cái, tienMay = Σ, giaVonMoiCaiMin / Max.
     *
     * @param tienMayMoiGio tổng tiền máy mỗi giờ in (₫, đã làm tròn — đúng số khoi-tao chi-phi-may trả)
     */
    public static Map<String, Object> kemChiPhiMay(Map<String, Object> sp, long tienMayMoiGio) {
        List<Map<String, Object>> dsBienThe = new ArrayList<>();
        long tongPhut = 0, tienMay = 0;
        long giaVonMin = Long.MAX_VALUE, giaVonMax = Long.MIN_VALUE;
        for (Object o : sp.get("bienThe") instanceof List<?> ds ? ds : List.of()) {
            if (!(o instanceof Map<?, ?> tho)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> bt = (Map<String, Object>) tho;
            long phut = so(bt.get("thoiGianInPhut"));
            long soCai = Math.max(1, so(bt.get("soLuong")));
            long mayMoiCai = Math.round(phut * tienMayMoiGio / 60.0);
            long nhuaMoiCai = Math.round(so(bt.get("tienNhua")) / (double) soCai);
            long giaVon = nhuaMoiCai + mayMoiCai;

            Map<String, Object> b = new LinkedHashMap<>(bt);
            b.put("tienMayMoiCai", mayMoiCai);
            b.put("tienMay", mayMoiCai * soCai);
            b.put("giaVonMoiCai", giaVon);
            dsBienThe.add(Collections.unmodifiableMap(b));

            tongPhut += phut * soCai;
            tienMay += mayMoiCai * soCai;
            giaVonMin = Math.min(giaVonMin, giaVon);
            giaVonMax = Math.max(giaVonMax, giaVon);
        }
        Map<String, Object> m = new LinkedHashMap<>(sp);
        m.put("bienThe", Collections.unmodifiableList(dsBienThe));
        m.put("tongPhutIn", tongPhut);
        m.put("tienMay", tienMay);
        // Sản phẩm chưa có biến thể nào (chỉ khi sửa tay trên database): 0 chứ đừng trả số vô cực
        m.put("giaVonMoiCaiMin", dsBienThe.isEmpty() ? 0L : giaVonMin);
        m.put("giaVonMoiCaiMax", dsBienThe.isEmpty() ? 0L : giaVonMax);
        return Collections.unmodifiableMap(m);
    }

    private static long so(Object v) { return v instanceof Number n ? n.longValue() : 0L; }

    /** Một dòng màu {id, ten, maMau} của sản phẩm. */
    private static Map<String, Object> dongMau(MauSac mau) {
        Map<String, Object> dongMau = new LinkedHashMap<>();
        dongMau.put("id", mau.getId());
        dongMau.put("ten", mau.getTen());
        dongMau.put("maMau", mau.getMaMau() == null ? "" : mau.getMaMau());
        return Collections.unmodifiableMap(dongMau);
    }
}
