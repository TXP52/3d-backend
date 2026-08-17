package vn.in3d.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Phục vụ ảnh đã tải lên tại /anh/** — đọc thẳng từ thư mục ./data/anh trên ổ đĩa.
 * Nhờ vậy lưu ảnh KHÔNG TỐN TIỀN, không phụ thuộc dịch vụ ngoài.
 */
@Configuration
public class TaiNguyenAnhConfig implements WebMvcConfigurer {

    private final String thuMucAnh;

    public TaiNguyenAnhConfig(@Value("${in3d.thu-muc-anh:./data/anh}") String thuMucAnh) {
        this.thuMucAnh = thuMucAnh;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path duong = Paths.get(thuMucAnh).toAbsolutePath().normalize();
        registry.addResourceHandler("/anh/**")
                .addResourceLocations(duong.toUri().toString())
                // Tên file là UUID và không bao giờ ghi đè -> cache vĩnh viễn được
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }

    /** Chặn trình duyệt "đoán" kiểu file khác với Content-Type đã khai báo. */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> khongDoanKieuFile() {
        FilterRegistrationBean<OncePerRequestFilter> dangKy = new FilterRegistrationBean<>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                res.setHeader("X-Content-Type-Options", "nosniff");
                chain.doFilter(req, res);
            }
        });
        dangKy.addUrlPatterns("/anh/*");
        return dangKy;
    }
}
