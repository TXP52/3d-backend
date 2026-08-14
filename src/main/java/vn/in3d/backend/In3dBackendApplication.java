package vn.in3d.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Backend Java cho hệ thống bán hàng IN3D Shop.
 * - Profile mặc định: chạy với H2 (file local) để thử nghiệm ngay.
 * - Profile "supabase": kết nối PostgreSQL của Supabase (database dùng chung
 *   với website bán hàng và trang quản trị).
 */
@SpringBootApplication
public class In3dBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(In3dBackendApplication.class, args);
    }
}
