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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> xuLyLoiCoChu(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("loi", ex.getReason() == null ? "Có lỗi xảy ra" : ex.getReason()));
    }
}
