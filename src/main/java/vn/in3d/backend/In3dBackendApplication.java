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
        SpringApplication.run(In3dBackendApplication.class, args);
    }
}
