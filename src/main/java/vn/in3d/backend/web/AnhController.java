package vn.in3d.backend.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * API tải ảnh — LƯU MIỄN PHÍ ngay trên ổ đĩa máy chạy backend.
 *
 * Ảnh nằm ở thư mục ./data/anh (đổi bằng thuộc tính in3d.thu-muc-anh),
 * được phục vụ công khai tại /anh/<tên-file> (xem TaiNguyenAnhConfig).
 *
 * Khi nào cần chuyển sang Supabase Storage / CDN: đọc HUONG-DAN-LUU-ANH.md.
 */
@RestController
@RequestMapping("/api/anh")
public class AnhController {

    /** Chỉ nhận các định dạng ảnh thông dụng — chặn file thực thi. */
    private static final Set<String> DUOI_CHO_PHEP = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long TOI_DA_BYTE = 5L * 1024 * 1024; // 5MB

    private final Path thuMuc;

    public AnhController(@Value("${in3d.thu-muc-anh:./data/anh}") String duongDan) throws IOException {
        this.thuMuc = Paths.get(duongDan).toAbsolutePath().normalize();
        Files.createDirectories(this.thuMuc);
    }

    /** Tải 1 ảnh lên, trả về URL công khai để lưu vào san_pham.hinh_anh / vat_tu.hinh_anh. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> tai(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chưa chọn ảnh để tải lên.");
        }
        if (file.getSize() > TOI_DA_BYTE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Ảnh nặng quá 5MB. Hãy nén nhỏ lại rồi tải lại.");
        }
        String kieu = file.getContentType();
        if (kieu == null || !kieu.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Chỉ nhận file ảnh (jpg, png, webp, gif).");
        }

        String duoi = layDuoi(file.getOriginalFilename());
        if (!DUOI_CHO_PHEP.contains(duoi)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Đuôi file không hợp lệ. Chỉ nhận: " + String.join(", ", DUOI_CHO_PHEP));
        }

        byte[] duLieu;
        try {
            duLieu = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không đọc được file tải lên.");
        }
        if (!laAnhThat(duLieu)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Nội dung file không phải ảnh thật (đổi đuôi file không qua được).");
        }

        String ten = UUID.randomUUID().toString().replace("-", "") + "." + duoi;
        Path dich = thuMuc.resolve(ten).normalize();
        if (!dich.startsWith(thuMuc)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file không hợp lệ.");
        }
        try {
            Files.write(dich, duLieu);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không ghi được ảnh xuống ổ đĩa: " + e.getMessage());
        }

        // duongDan (tương đối) là thứ nên LƯU VÀO DB — đổi tên miền/host sau này không chết link.
        // url (tuyệt đối) chỉ để hiển thị ngay cho tiện.
        String duongDan = "/anh/" + ten;
        String url = ServletUriComponentsBuilder.fromCurrentContextPath().path(duongDan).toUriString();

        Map<String, Object> kq = new LinkedHashMap<>();
        kq.put("ten", ten);
        kq.put("duongDan", duongDan);
        kq.put("url", url);
        kq.put("kichThuoc", (long) duLieu.length);
        return kq;
    }

    /**
     * Kiểm tra chữ ký (magic bytes) đầu file — chặn file thực thi đổi tên thành .jpg.
     * Nhận JPEG (FF D8 FF), PNG, GIF87a/89a, WebP (RIFF....WEBP).
     */
    private boolean laAnhThat(byte[] b) {
        if (b == null || b.length < 12) return false;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return true;               // JPEG
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return true;                     // PNG
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return true;                               // GIF
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return true;                      // WebP
        return false;
    }

    /** Xoá 1 ảnh khỏi ổ đĩa (dùng khi đổi ảnh sản phẩm). */
    @DeleteMapping("/{ten}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable String ten) {
        if (!ten.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên ảnh không hợp lệ.");
        }
        Path f = thuMuc.resolve(ten).normalize();
        if (!f.startsWith(thuMuc)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên ảnh không hợp lệ.");
        }
        try {
            Files.deleteIfExists(f);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không xoá được ảnh.");
        }
    }

    private String layDuoi(String tenGoc) {
        if (tenGoc == null) return "";
        int i = tenGoc.lastIndexOf('.');
        return i < 0 ? "" : tenGoc.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
