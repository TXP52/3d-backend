package vn.in3d.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Kiểm tra kết nối Supabase NGAY KHI KHỞI ĐỘNG (chỉ chạy ở profile "supabase").
 * Mục đích: thay lỗi khó hiểu "Unable to determine Dialect without JDBC metadata"
 * bằng thông báo tiếng Việt chỉ rõ nguyên nhân và cách sửa.
 */
@Configuration
@Profile("supabase")
public class KiemTraKetNoiSupabase {

    @Bean
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

                        Sau đó build lại: mvn package -DskipTests  rồi chạy lại.
                        Hoặc bỏ profile supabase để chạy thử với H2:  java -jar target\\in3d-backend-1.0.0.jar
                        ============================================================
                        """);
            }
            try (Connection c = dataSource.getConnection()) {
                System.out.println("[IN3D] ✔ Kết nối Supabase PostgreSQL thành công: "
                        + c.getMetaData().getDatabaseProductName() + " "
                        + c.getMetaData().getDatabaseProductVersion());
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
}
