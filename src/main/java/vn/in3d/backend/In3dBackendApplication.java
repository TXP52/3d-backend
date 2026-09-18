package vn.in3d.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Backend Java cho hệ thống bán hàng IN3D Shop.
 * Database duy nhất là PostgreSQL của Supabase (dùng chung với website bán hàng
 * và trang quản trị); profile "supabase" được bật sẵn trong application.properties,
 * thông tin kết nối nằm ở application-supabase.properties. Không còn H2.
 */
@SpringBootApplication
public class In3dBackendApplication {

    public static void main(String[] args) {
        // Hikari mặc định hỏi lại database "kết nối còn sống không" mỗi lần mượn kết nối
        // đã rảnh quá 0,5 giây — thêm một lượt đi-về (~250 ms) cho gần như mọi cú bấm.
        // Nới lên 30 giây; keepalive-time trong application.properties vẫn ping nền.
        // Chỉ đặt khi chưa ai truyền -Dcom.zaxxer.hikari.aliveBypassWindowMs.
        if (System.getProperty("com.zaxxer.hikari.aliveBypassWindowMs") == null) {
            System.setProperty("com.zaxxer.hikari.aliveBypassWindowMs", "30000");
        }
        SpringApplication.run(In3dBackendApplication.class, args);
    }
}
