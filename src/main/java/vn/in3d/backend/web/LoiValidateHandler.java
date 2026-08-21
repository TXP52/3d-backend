package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Trả lỗi về dạng {"loi": "..."} tiếng Việt cho frontend hiển thị. */
@RestControllerAdvice
public class LoiValidateHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> xuLyValidate(MethodArgumentNotValidException ex) {
        String thongBao = ex.getBindingResult().getFieldErrors().stream()
                .map(l -> l.getDefaultMessage())
                .findFirst()
                .orElse("Dữ liệu không hợp lệ");
        return Map.of("loi", thongBao);
    }

    /** Tomcat chặn file quá cỡ TRƯỚC khi vào controller — trả JSON tiếng Việt thay vì trang lỗi HTML. */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> xuLyAnhQuaNang(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return Map.of("loi", "Ảnh nặng quá 5MB. Hãy chọn ảnh nhỏ hơn hoặc nén lại rồi tải lên.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> xuLyLoiCoChu(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("loi", ex.getReason() == null ? "Có lỗi xảy ra" : ex.getReason()));
    }

    /**
     * Đụng ràng buộc của database (trùng khoá duy nhất, thiếu khoá ngoại...).
     * Không bắt thì Spring trả 500 kèm trang lỗi, người dùng chỉ thấy "Lỗi backend: HTTP 500"
     * mà không biết vướng ở đâu.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> xuLyTrungDuLieu(org.springframework.dao.DataIntegrityViolationException ex) {
        String chiTiet = ex.getMostSpecificCause().getMessage();
        boolean trung = chiTiet != null
                && (chiTiet.toLowerCase().contains("unique") || chiTiet.toLowerCase().contains("duplicate"));
        return Map.of("loi", trung
                ? "Dữ liệu bị trùng với một bản ghi đã có (mã, email hoặc đường dẫn). Hãy đổi giá trị khác."
                : "Database từ chối dữ liệu này. Kiểm tra lại các ô bắt buộc rồi thử lại.");
    }
}
