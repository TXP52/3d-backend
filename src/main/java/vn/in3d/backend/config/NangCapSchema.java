package vn.in3d.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Vài thay đổi schema mà Hibernate ddl-auto=update KHÔNG tự làm được.
 *
 * ddl-auto=update chỉ biết THÊM bảng và THÊM cột. Nó không bao giờ nới lỏng
 * ràng buộc có sẵn. Cụ thể: cột khuyen_mai.ma lúc đầu là NOT NULL vì khuyến mãi
 * nào cũng có mã; từ khi thêm kiểu "giảm giá sản phẩm" (tự áp, không cần mã)
 * thì cột đó phải cho phép để trống — database cũ vẫn giữ NOT NULL và chèn lỗi.
 *
 * Chạy TRƯỚC các seeder (@Order thấp nhất) và nuốt lỗi: câu lệnh đã chạy rồi,
 * hoặc bảng chưa tồn tại, đều không phải chuyện đáng dừng server.
 */
@Configuration
public class NangCapSchema {

    // Tên phương thức phải khác tên lớp, không thì trùng tên bean với chính @Configuration này
    @Bean
    @Order(0)
    CommandLineRunner chayNangCapSchema(JdbcTemplate jdbc) {
        return args -> {
            // Mỗi dòng là các cách viết khác nhau của CÙNG một việc (H2 và PostgreSQL
            // khác cú pháp), chạy được cái nào thì thôi cái đó.
            List<List<String>> viec = List.of(
                    List.of(
                            "alter table khuyen_mai alter column ma set null",        // H2
                            "alter table khuyen_mai alter column ma drop not null"    // PostgreSQL
                    ),
                    // Mã của chương trình ĐÃ XOÁ vẫn chiếm chỗ vì cột ma là khoá duy nhất
                    // trên toàn bảng. Gắn hậu tố "#id" để nhả mã ra cho chương trình mới
                    // dùng lại — khớp với cách xoá mới trong KhuyenMaiController.
                    List.of(
                            "update khuyen_mai set ma = concat(ma, '#', id) "
                            + "where is_deleted = true and ma is not null and ma not like '%#%'"
                    )
            );

            for (List<String> cachViet : viec) {
                for (String sql : cachViet) {
                    try {
                        jdbc.execute(sql);
                        System.out.println("[IN3D] Nâng cấp schema: " + sql);
                        break;   // xong việc này, khỏi thử cú pháp còn lại
                    } catch (Exception bo) {
                        // sai cú pháp với hệ quản trị này, hoặc đã sửa từ lần chạy trước
                    }
                }
            }
        };
    }
}
