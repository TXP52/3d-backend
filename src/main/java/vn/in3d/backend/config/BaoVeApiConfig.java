package vn.in3d.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.service.XacThucService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;

/**
 * KHOÁ TOÀN BỘ /api, chỉ mở đúng những đường website bán hàng cần.
 *
 * Nguyên tắc ngược lại với cách hay làm: mặc định là KHOÁ, muốn mở phải khai tên
 * ở dưới. Khai sót thì trang hỏng — thấy ngay mà sửa; còn nếu làm kiểu mặc định mở
 * thì quên một chỗ là thủng mà chẳng ai biết.
 *
 * Vì sao chặn tập trung chứ không gắn vào từng controller: có hơn ba mươi chỗ ghi và
 * gần hai mươi chỗ đọc (sản phẩm, vật tư, kho, danh mục, màu sắc, bộ sưu tập, bài viết,
 * khuyến mãi, ảnh, quản trị...). Gắn tay từng hàm thì lần nào thêm API mới cũng phải nhớ.
 *
 * Không chặn /anh/** — đó là ảnh sản phẩm, trang bán hàng phải hiện được.
 */
@Configuration
public class BaoVeApiConfig implements WebMvcConfigurer {

    private static final Set<String> CHI_DOC = Set.of("GET", "HEAD");

    /**
     * Đường ĐỌC mở cho khách. Đúng bằng những gì Website-GameOver gọi, không thừa dòng nào:
     * trang chủ, danh sách/chi tiết sản phẩm, bộ sưu tập, bài viết.
     */
    private static final List<String> DOC_MO = List.of(
            "/api/suc-khoe",           // kiểm tra backend còn sống
            "/api/cua-hang/**",        // toàn bộ dữ liệu trang bán hàng (đã lọc sẵn hàng đang bán)
            "/api/bai-viet",           // danh sách bài viết đang hiện
            "/api/bai-viet/*",         // một bài theo id
            "/api/bai-viet/duong-dan/*", // một bài theo đường dẫn thân thiện
            "/api/auth/toi"            // "tôi là ai" — tự kiểm tra token bên trong
    );

    /**
     * Đường GHI mở cho khách chưa đăng nhập. Mỗi dòng phải có lý do rõ ràng.
     */
    private static final List<String> GHI_MO = List.of(
            "/api/auth/**",                  // đăng ký, đăng nhập, gửi + xác thực OTP
            "/api/don-hang",                 // khách đặt hàng ở website (CHỈ đúng đường này,
                                             // /api/don-hang/{id}/... vẫn phải là admin)
            "/api/gio-hang/bao-gia",         // tính tiền giỏ hàng trước khi đặt
            "/api/khuyen-mai/kiem-tra",      // khách gõ mã giảm giá để thử
            "/api/khuyen-mai/xem-truoc-gia", // xem giá sau khuyến mãi
            "/api/bai-viet/*/luot-xem"       // đếm lượt xem bài viết
    );

    private final XacThucService xacThuc;

    public BaoVeApiConfig(XacThucService xacThuc) {
        this.xacThuc = xacThuc;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ChanApi(xacThuc)).addPathPatterns("/api/**");
    }

    static class ChanApi implements HandlerInterceptor {

        private final XacThucService xacThuc;
        private final AntPathMatcher soKhop = new AntPathMatcher();

        ChanApi(XacThucService xacThuc) {
            this.xacThuc = xacThuc;
        }

        @Override
        public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
            // Trình duyệt hỏi trước bằng OPTIONS (CORS) — chưa mang token, đừng chặn
            if ("OPTIONS".equals(req.getMethod())) return true;
            if (duocMo(req)) return true;

            // docToken tự ném 401 khi thiếu token / sai chữ ký / hết hạn
            NguoiDung nguoiGoi = xacThuc.docToken(req.getHeader("Authorization"));
            if (!"admin".equals(nguoiGoi.getVaiTro())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Chỉ tài khoản quản trị mới dùng được mục này.");
            }
            return true;
        }

        private boolean duocMo(HttpServletRequest req) {
            String duongDan = req.getRequestURI();
            if (!CHI_DOC.contains(req.getMethod())) {
                return khop(GHI_MO, duongDan);
            }
            // Thùng rác là dữ liệu đã xoá — chỉ chủ shop được xem.
            // Chặn riêng vì mẫu "/api/bai-viet/*" ở trên lỡ khớp cả /api/bai-viet/thung-rac.
            if (duongDan.endsWith("/thung-rac")) return false;
            // ?tatCa=true lấy cả hàng đang ẩn / bài chưa đăng — của trang quản trị.
            if ("true".equalsIgnoreCase(req.getParameter("tatCa"))) return false;
            return khop(DOC_MO, duongDan);
        }

        private boolean khop(List<String> mauDuong, String duongDan) {
            for (String mau : mauDuong) {
                if (soKhop.match(mau, duongDan)) return true;
            }
            return false;
        }
    }
}
