package vn.in3d.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Trình duyệt chỉ cho trang web gọi sang máy chủ khác khi máy chủ đó đồng ý — đó là CORS.
 *
 * Ở máy nhà để "*" cho tiện (trang bán hàng :8123, trang quản trị :8124, mở thẳng file...).
 * Khi lên máy chủ thật thì khai biến môi trường IN3D_CORS_ORIGINS bằng đúng địa chỉ trang
 * của mình, ngăn cách bởi dấu phẩy, ví dụ:
 *   https://txp52.github.io,https://bedecraft.com
 * Dùng allowedOriginPatterns nên viết được cả dạng có sao: https://*.pages.dev
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] nguonChoPhep;

    public CorsConfig(@Value("${in3d.cors.origins:*}") String nguon) {
        this.nguonChoPhep = nguon.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(nguonChoPhep)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
        // Ảnh sản phẩm phục vụ từ ổ đĩa — cho phép trang bán hàng/quản trị hiển thị
        registry.addMapping("/anh/**")
                .allowedOriginPatterns(nguonChoPhep)
                .allowedMethods("GET", "OPTIONS")
                .maxAge(3600);
    }
}
