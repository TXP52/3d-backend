package vn.in3d.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kiểm tra Supabase NGAY KHI KHỞI ĐỘNG.
 *
 * Hai việc:
 *   1. Nối được database chưa — thay lỗi khó hiểu "Unable to determine Dialect
 *      without JDBC metadata" bằng câu tiếng Việt chỉ rõ phải sửa ở đâu.
 *   2. Schema có đủ bảng và cột app cần chưa.
 *
 * Việc thứ hai cần thiết vì ddl-auto để "none": Java không tự thêm bảng/cột
 * nữa (để "update" thì Hibernate đòi sửa 40 cột của Supabase và làm chết app
 * lúc khởi động — xem ghi chú trong application-supabase.properties).
 * Thiếu gì thì báo ngay lúc chạy server, thay vì để trang quản trị lỗi 500
 * rồi mới đi mò.
 */
@Configuration
@Profile("supabase")
public class KiemTraKetNoiSupabase {

    /** Bảng -> cột bắt buộc phải có. Thiếu là chưa chạy SUPABASE-DONG-BO.sql bản mới. */
    // Map.ofEntries vì Map.of chỉ nhận tối đa 10 cặp
    private static final Map<String, List<String>> CAN_CO = Map.ofEntries(
            Map.entry("nguoi_dung",        List.of("id", "email", "mat_khau_hash", "vai_tro", "is_deleted")),
            Map.entry("san_pham",          List.of("id", "ten", "gia", "trang_thai", "loai_san_pham",
                                                   "danh_muc_id", "so_luong", "nhieu_mau", "danh_sach_anh",
                                                   "is_deleted")),
            // Bảng nối: mỗi dòng là "in mấy cái bằng cuộn nhựa nào"
            Map.entry("san_pham_vat_tu",   List.of("id", "san_pham_id", "vat_tu_id",
                                                   "so_luong", "gram_nhua", "gram_thua")),
            Map.entry("danh_muc",          List.of("id", "ten", "nhom", "thu_tu", "dang_hien", "is_deleted")),
            Map.entry("don_hang",          List.of("id", "ma_don", "tong_tien", "ma_khuyen_mai", "tien_giam",
                                                   "tien_giam_san_pham", "nguoi_dung_id", "is_deleted")),
            Map.entry("don_hang_chi_tiet", List.of("id", "don_hang_id", "don_gia", "don_gia_goc", "so_luong")),
            Map.entry("thanh_toan",        List.of("id", "don_hang_id", "so_tien", "trang_thai")),
            Map.entry("khuyen_mai",        List.of("id", "ma", "kieu_ap_dung", "chi_khach_moi",
                                                   "dieu_kien_dia_chi", "san_pham_ids", "is_deleted")),
            Map.entry("bai_viet",          List.of("id", "tieu_de", "duong_dan", "chuyen_muc", "is_deleted")),
            Map.entry("mau_sac",           List.of("id", "ten", "ma_mau", "is_deleted")),
            Map.entry("vat_tu",            List.of("id", "ten", "loai", "gia", "trang_thai", "mau_sac_id",
                                                   "danh_muc_id", "is_deleted")),
            Map.entry("nha_cung_cap",      List.of("id", "ten", "is_deleted")));

    // Order thấp nhất: chạy trước mọi seeder, seeder không nên ghi vào schema hỏng
    @Bean
    @Order(-100)
    ApplicationRunner kiemTraKetNoiDb(DataSource dataSource,
                                      @Value("${spring.datasource.url:}") String url,
                                      @Value("${spring.datasource.password:}") String matKhau) {
        return args -> {
            if (url == null || url.isBlank() || url.contains("<") || matKhau.contains("<")) {
                throw new IllegalStateException("""

                        ============================================================
                        [IN3D] CHƯA ĐIỀN THÔNG TIN KẾT NỐI SUPABASE!
                        Mở file: src/main/resources/application-supabase.properties
                        và điền 2 chỗ đánh dấu <...>:

                        1. <region>  trong spring.datasource.url
                           -> lấy tại Dashboard -> nút "Connect" -> Session pooler
                              (ví dụ: aws-1-ap-southeast-1)
                        2. <dien-mat-khau-database-vao-day> trong spring.datasource.password
                           -> lấy tại Dashboard -> Settings -> Database (Reset nếu quên)

                        Chưa có file thì copy từ application-supabase.properties.example.
                        Sau đó build lại: mvn package -DskipTests  rồi chạy lại.
                        ============================================================
                        """);
            }

            try (Connection c = dataSource.getConnection()) {
                System.out.println("[IN3D] ✔ Kết nối Supabase PostgreSQL thành công: "
                        + c.getMetaData().getDatabaseProductName() + " "
                        + c.getMetaData().getDatabaseProductVersion());
                kiemTraSchema(c);
            } catch (IllegalStateException loiSchema) {
                throw loiSchema;
            } catch (Exception e) {
                throw new IllegalStateException("""

                        ============================================================
                        [IN3D] KHÔNG KẾT NỐI ĐƯỢC SUPABASE POSTGRESQL!
                        Nguyên nhân gốc: %s

                        Kiểm tra lần lượt:
                        1. Host phải là "Session pooler" (aws-1-<region>.pooler.supabase.com) —
                           host trực tiếp db.<ref>.supabase.co chỉ có IPv6, hay bị "Connect timed out".
                        2. User phải có đuôi project: postgres.nmptxzbtngztzxpwdprs (không phải "postgres" trơn).
                        3. Mật khẩu database đúng chưa? (Dashboard -> Settings -> Database -> Reset password nếu quên)
                        4. Project Supabase có đang bị tạm dừng (paused) không? Vào Dashboard bấm Restore.
                        5. Mạng/tường lửa có chặn cổng 5432 không? Thử cổng 6543 (Transaction pooler).
                        ============================================================
                        """.formatted(e.getMessage()), e);
            }
        };
    }

    /**
     * Soát từng bảng/cột app cần; thiếu thì dừng ngay kèm danh sách cụ thể.
     * Hỏi MỘT lượt cho cả 12 bảng rồi chia trong bộ nhớ — bản cũ hỏi từng bảng,
     * 12 lượt đi-về tới Sydney là ~3 giây mỗi lần khởi động.
     */
    private void kiemTraSchema(Connection c) throws Exception {
        List<String> thieu = new ArrayList<>();

        Map<String, List<String>> cotTheoBang = new HashMap<>();
        try (var ps = c.prepareStatement(
                "select table_name, column_name from information_schema.columns "
                + "where table_schema = 'public' and table_name = any(?)")) {
            ps.setArray(1, c.createArrayOf("text", CAN_CO.keySet().toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cotTheoBang.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                }
            }
        }

        for (var muc : CAN_CO.entrySet()) {
            String bang = muc.getKey();
            List<String> coTrongDb = cotTheoBang.getOrDefault(bang, List.of());
            if (coTrongDb.isEmpty()) {
                thieu.add("  - THIẾU HẲN BẢNG: " + bang);
                continue;
            }
            for (String cot : muc.getValue()) {
                if (!coTrongDb.contains(cot)) thieu.add("  - " + bang + " thiếu cột: " + cot);
            }
        }

        if (thieu.isEmpty()) {
            System.out.println("[IN3D] ✔ Schema Supabase đủ " + CAN_CO.size() + " bảng app cần");
            return;
        }
        throw new IllegalStateException("""

                ============================================================
                [IN3D] SCHEMA SUPABASE CHƯA ĐỦ — app dừng để bạn khỏi mất công mò.

                %s

                CÁCH SỬA: mở Supabase -> SQL Editor -> dán cả file
                          3d-backend/SUPABASE-DONG-BO.sql  -> Ctrl+A -> Run.
                Chạy lại bao nhiêu lần cũng được. Xong xem bảng kiểm tra ở cuối
                kết quả, cột ket_qua phải "OK" hết, rồi chạy lại server.
                ============================================================
                """.formatted(String.join("\n", thieu)));
    }
}
