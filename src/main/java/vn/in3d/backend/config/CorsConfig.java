package vn.in3d.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cho phép website bán hàng (:8123) và trang quản trị (:8124) gọi API.
 * Đang mở "*" để tiện phát triển; khi lên production hãy giới hạn lại domain thật.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
        // Ảnh sản phẩm phục vụ từ ổ đĩa — cho phép trang bán hàng/quản trị hiển thị
        registry.addMapping("/anh/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "OPTIONS");
    }
}
