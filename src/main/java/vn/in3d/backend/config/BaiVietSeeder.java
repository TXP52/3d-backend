package vn.in3d.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.in3d.backend.entity.BaiViet;
import vn.in3d.backend.repository.BaiVietRepository;

import java.text.Normalizer;
import java.util.List;

/**
 * Nạp bộ bài viết đầu tiên cho mục "Kiến thức in 3D" ở cuối trang chủ.
 *
 * Chỉ nạp khi bảng bai_viet còn TRỐNG, nên chủ shop xoá/sửa bài thoải mái
 * mà khởi động lại server không bị bài cũ mọc ra đè lên.
 *
 * Nội dung là văn bản thường, không phải HTML:
 *   "## " mở đầu dòng = tiêu đề mục nhỏ
 *   "- "  mở đầu dòng = gạch đầu dòng
 *   dòng trống        = ngăn hai đoạn
 */
@Configuration
public class BaiVietSeeder {

    private static final String TAC_GIA = "Bedecraft";

    @Bean
    CommandLineRunner napBaiViet(BaiVietRepository repo) {
        return args -> {
            if (repo.count() > 0) return;

            record B(String tieuDe, String chuyenMuc, String tomTat, String noiDung) {}
            List<B> ds = List.of(
                    new B("PLA, PETG hay ABS — chọn nhựa nào cho món đồ bạn định in?",
                            "vat-lieu",
                            "Ba loại nhựa phổ biến nhất, mỗi loại hợp với một kiểu đồ khác nhau. "
                                    + "Chọn sai thì món đồ cong, gãy hoặc bạc màu chỉ sau vài tuần.",
                            NHUA),

                    new B("Lớp in đầu tiên quyết định cả bản in",
                            "huong-dan",
                            "Phần lớn bản in hỏng là hỏng ngay ở lớp đầu. "
                                    + "Bốn thứ cần chỉnh trước khi đổ lỗi cho máy.",
                            LOP_DAU),

                    new B("Tính giá một món đồ in 3D: từ gram nhựa ra giá bán",
                            "kinh-nghiem",
                            "Nhựa chỉ là một phần chi phí. Công thức đầy đủ gồm cả điện, hao mòn máy, "
                                    + "tỉ lệ in hỏng và công hậu kỳ.",
                            TINH_GIA),

                    new B("Infill bao nhiêu phần trăm là đủ?",
                            "huong-dan",
                            "Tăng infill từ 20% lên 50% gần như không làm đồ cứng thêm bao nhiêu "
                                    + "nhưng tốn gấp đôi nhựa và thời gian.",
                            INFILL),

                    new B("Cuộn nhựa bị ẩm: dấu hiệu nhận biết và cách sấy",
                            "vat-lieu",
                            "Nhựa hút ẩm in ra bề mặt rỗ, có tiếng lách tách và dễ đứt sợi. "
                                    + "Cách sấy lại bằng đồ có sẵn trong nhà.",
                            HUT_AM),

                    new B("Gửi file in 3D cho shop: STL, 3MF và những lỗi hay gặp",
                            "huong-dan",
                            "Gửi đúng định dạng và đúng đơn vị đo giúp shop báo giá trong ngày, "
                                    + "không phải hỏi đi hỏi lại.",
                            GUI_FILE)
            );

            int thuTu = 1;
            for (B b : ds) {
                BaiViet bv = new BaiViet();
                bv.setTieuDe(b.tieuDe());
                bv.setDuongDan(khongDau(b.tieuDe()));
                bv.setTomTat(b.tomTat());
                bv.setNoiDung(b.noiDung());
                bv.setChuyenMuc(b.chuyenMuc());
                bv.setTacGia(TAC_GIA);
                bv.setHienThi(true);
                bv.setThuTu(thuTu++);
                repo.save(bv);
            }
            System.out.println("[IN3D] Đã nạp " + ds.size() + " bài viết kiến thức in 3D");
        };
    }

    /** "Chọn nhựa PLA hay PETG?" -> "chon-nhua-pla-hay-petg" */
    private static String khongDau(String s) {
        String t = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase();
        return t.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }

    // ============================================================
    // NỘI DUNG CÁC BÀI
    // ============================================================

    private static final String NHUA = """
            Đi mua nhựa in 3D lần đầu ai cũng gặp một rừng tên: PLA, PLA+, PETG, ABS, TPU, ASA.
            Thật ra chỉ cần trả lời một câu hỏi: món đồ này sẽ nằm ở đâu và chịu lực thế nào?

            ## PLA — mặc định cho hầu hết mọi thứ
            PLA dễ in nhất: nhiệt độ thấp, gần như không cong vênh, không mùi khó chịu.
            Bề mặt lên đẹp, màu tươi, giá rẻ nhất trong ba loại.

            Nhược điểm duy nhất nhưng rất quan trọng: PLA mềm ra ở khoảng 55-60°C.
            Để trong ô tô đậu ngoài nắng một buổi trưa là món đồ có thể chảy xệ.

            Hợp với: mô hình trang trí, móc khoá, hộp đựng để bàn, quà tặng, đồ chơi.
            Không hợp với: đồ để trong xe, đồ gần bếp, đồ ngoài trời quanh năm.

            ## PETG — bền hơn, chịu nóng hơn, khó in hơn một chút
            PETG dai hơn PLA rõ rệt: rơi xuống nền gạch thường móp chứ không vỡ vụn.
            Chịu được khoảng 75-80°C, không ngại nắng và nước.

            Đổi lại PETG hay bị "chỉ nhện" — những sợi tơ mảnh vắt ngang giữa các chi tiết.
            Khắc phục bằng cách sấy khô nhựa và giảm nhiệt độ đầu phun xuống 5-10°C.

            Hợp với: giá đỡ chịu lực, vỏ hộp kỹ thuật, đồ dùng ngoài ban công, chậu cây tưới nước.

            ## ABS / ASA — chỉ khi thật sự cần
            Chịu nhiệt trên 95°C, chà nhám và sơn đẹp, nhưng co rút mạnh khi nguội.
            In ABS gần như bắt buộc phải có buồng kín, không thì góc bản in sẽ bong khỏi bàn.
            Mùi khi in cũng khó chịu, cần phòng thoáng.

            Chỉ chọn ABS/ASA khi món đồ phải chịu nhiệt cao hoặc phơi nắng nhiều năm.

            ## TPU — nhựa dẻo
            Bấm vào thấy đàn hồi như cao su. Dùng cho ốp điện thoại, đệm chống rung, gioăng.
            In chậm (20-30mm/s) và cần máy đẩy sợi trực tiếp thì mới ăn chắc.

            ## Tóm lại
            - Đồ trang trí, quà tặng, đồ để bàn: PLA
            - Đồ chịu lực, để ngoài trời, gặp nước: PETG
            - Đồ chịu nhiệt cao: ABS hoặc ASA
            - Đồ cần đàn hồi: TPU

            Chưa chắc chắn thì cứ nhắn cho shop kèm ảnh và mô tả chỗ đặt món đồ,
            shop sẽ tư vấn loại nhựa phù hợp trước khi in.
            """;

    private static final String LOP_DAU = """
            Bản in 3D hỏng thì chín trên mười lần là hỏng ở lớp đầu tiên.
            Lớp đầu bám không chắc thì mọi lớp sau đều xây trên nền lệch.

            ## 1. Cân bàn in
            Khoảng cách giữa đầu phun và mặt bàn phải bằng nhau ở mọi góc.
            Cách kiểm tra thủ công: kẹp tờ giấy A4 giữa đầu phun và bàn, kéo ra thấy hơi sượt là vừa.
            Quá lỏng thì sợi nhựa không dính, quá chặt thì nhựa bị ép không ra được.

            Máy có cân bàn tự động vẫn nên kiểm tra lại sau mỗi lần tháo lắp đầu phun.

            ## 2. Độ cao Z offset
            Đây là thứ hay bị bỏ qua nhất. Lớp đầu đạt là khi các đường nhựa dẹt xuống
            và dính sát vào nhau thành một mặt phẳng liền, không nhìn thấy khe hở giữa các đường.

            - Còn khe hở giữa các đường: hạ Z offset xuống 0,02-0,05mm
            - Bề mặt gợn sóng, nhựa bị đùn tràn sang hai bên: nâng Z offset lên

            ## 3. Mặt bàn sạch
            Dấu vân tay để lại lớp dầu mỏng đủ làm bản in bong ra giữa chừng.
            Lau mặt bàn bằng cồn 70 độ trước mỗi lần in quan trọng.
            Bàn PEI dùng lâu bị bóng thì rửa nước ấm với nước rửa chén rồi lau khô.

            ## 4. Nhiệt độ bàn
            - PLA: 55-60°C
            - PETG: 70-80°C
            - ABS: 95-110°C

            Nóng quá thì chân bản in phình ra như bị bẹp — gọi là hiện tượng "chân voi".
            Gặp lỗi này thì hạ nhiệt độ bàn xuống 5°C và bật quạt sớm hơn một lớp.

            ## Mẹo cuối
            Trước khi in một món mất 8 tiếng, hãy in thử một hình vuông mỏng 1 lớp trong 3 phút.
            Nhìn lớp đó là biết ngay có nên bấm in tiếp hay không.
            """;

    private static final String TINH_GIA = """
            Câu hỏi shop hay nhận nhất: "In cái này bao nhiêu tiền?"
            Giá không nằm ở kích thước món đồ mà nằm ở số gram nhựa và số giờ máy chạy.

            ## Bước 1: Biết giá thật của một gram nhựa
            Một cuộn PLA 1kg giá 222.000đ thì mỗi gram là 222đ.
            Nhưng đừng lấy con số đó làm giá bán — đó mới là giá vốn nguyên liệu.

            ## Bước 2: Cộng tiền điện
            Máy in FDM ăn khoảng 120-150W khi chạy, tính tròn 0,15 số điện mỗi giờ.
            Với giá điện sinh hoạt khoảng 2.500đ/số, mỗi giờ in tốn chưa tới 400đ tiền điện.
            Nhỏ, nhưng bản in 10 tiếng thì cũng thành 4.000đ.

            ## Bước 3: Cộng hao mòn máy
            Đầu phun, tấm bàn, dây đai, ống dẫn nhựa đều là đồ tiêu hao.
            Cách tính đơn giản: lấy giá máy chia cho số giờ máy chạy được trước khi cần đại tu.
            Máy 11 triệu chạy được khoảng 3.000 giờ thì mỗi giờ hao mòn gần 3.700đ.

            ## Bước 4: Cộng tỉ lệ in hỏng
            Kể cả thợ quen tay vẫn có khoảng 5-10% bản in phải bỏ.
            Nhân toàn bộ chi phí ở trên với 1,1 để bù phần đó.

            ## Bước 5: Cộng công hậu kỳ
            Gỡ support, chà nhám, dán ghép, sơn — đây thường là phần tốn thời gian nhất
            và cũng là phần hay bị quên khi báo giá.

            ## Ví dụ cụ thể
            Một con robot mô hình nặng 60g, in hết 5 giờ:

            - Nhựa: 60g x 222đ = 13.320đ
            - Điện: 5 giờ x 400đ = 2.000đ
            - Hao mòn: 5 giờ x 3.700đ = 18.500đ
            - Cộng lại: 33.820đ, nhân 1,1 bù in hỏng = 37.200đ
            - Công gỡ support và chà nhám 20 phút: 20.000đ

            Giá vốn khoảng 57.000đ. Bán 90.000-110.000đ là hợp lý.

            ## Điều đáng nhớ nhất
            Tiền nhựa thường chỉ chiếm khoảng một phần tư giá thành.
            Ai báo giá chỉ dựa trên gram nhựa thì càng in nhiều càng lỗ.
            """;

    private static final String INFILL = """
            Infill là phần ruột rỗng bên trong món đồ. Đây là chỗ dễ tốn tiền oan nhất.

            ## Vì sao 20% là đủ cho hầu hết mọi thứ
            Độ cứng của một món đồ in 3D phần lớn đến từ lớp vỏ ngoài chứ không phải ruột.
            Tăng infill từ 20% lên 50% chỉ làm đồ cứng thêm chừng 15-20%
            nhưng tốn thêm gần gấp đôi nhựa và thời gian.

            Muốn chắc hơn thì tăng số lớp vỏ từ 2 lên 3-4 — rẻ và hiệu quả hơn nhiều.

            ## Chọn theo mục đích
            - Mô hình trưng bày, không chịu lực: 10-15%
            - Đồ dùng hằng ngày: 20%
            - Đồ chịu lực, bản lề, ngàm: 40-50% kèm 4 lớp vỏ
            - Đồ cần nặng tay, làm chặn giấy: 60% trở lên

            ## Kiểu infill nào?
            - Gyroid: chắc đều mọi hướng, in nhanh, mặc định tốt nhất
            - Grid: nhanh, đủ dùng cho đồ trang trí
            - Honeycomb: chắc nhưng in lâu hơn
            - Lightning: cực tiết kiệm, chỉ đỡ phần trần, dùng cho mô hình trưng bày

            ## Thử nghiệm nhỏ nên làm một lần
            In cùng một khối hộp ở 15%, 25% và 50%, ghi lại số gram và thời gian.
            Bóp thử ba khối đó bằng tay là bạn sẽ tự tin chọn thông số
            cho mọi bản in về sau mà không phải đoán nữa.
            """;

    private static final String HUT_AM = """
            Nhựa in 3D hút ẩm từ không khí, nhanh nhất là PETG, TPU và Nylon.
            Ở độ ẩm Hà Nội mùa nồm, một cuộn để hở vài ngày là đã ngấm nước.

            ## Dấu hiệu nhận biết
            - Nghe tiếng lách tách nhỏ ở đầu phun khi in — đó là hơi nước nổ trong nhựa
            - Bề mặt bản in rỗ, mờ, sần thay vì bóng mịn
            - Nhiều chỉ nhện vắt ngang giữa các chi tiết
            - Sợi nhựa giòn, bẻ nhẹ là gãy thay vì cong

            ## Cách sấy lại
            Cách rẻ nhất là dùng nồi chiên không dầu hoặc lò nướng có chỉnh nhiệt chính xác:

            - PLA: 45-50°C trong 4-6 giờ
            - PETG: 60-65°C trong 4-6 giờ
            - TPU: 50°C trong 6-8 giờ
            - Nylon: 70-80°C trong 8-12 giờ

            Tuyệt đối không vượt quá mốc nhiệt trên. PLA quá 55°C là cả cuộn dính bết vào nhau,
            gỡ ra không nổi và coi như bỏ cuộn.

            ## Bảo quản để khỏi phải sấy
            Cất cuộn trong thùng nhựa kín kèm vài gói hút ẩm silica gel.
            Gói hút ẩm dùng lại được: sấy 100°C khoảng 2 giờ là hồi lại.

            Cuộn đang in dở cũng nên cất vào thùng ngay khi in xong,
            đừng để cả tuần trên máy.

            ## Một lưu ý về cách kiểm tra
            Nhiều người cân cuộn nhựa để đoán độ ẩm. Cách này không đáng tin
            vì lượng nước hút vào chỉ vài gram, lẫn trong sai số của cân và của chính lõi cuộn.
            Cứ nhìn bề mặt bản in và nghe tiếng đầu phun là chính xác hơn.
            """;

    private static final String GUI_FILE = """
            Gửi file đúng chuẩn giúp shop báo giá trong ngày thay vì hỏi qua hỏi lại vài lượt.

            ## Nên gửi định dạng nào
            - 3MF: tốt nhất. Giữ được đơn vị đo, màu sắc và cả thông số in
            - STL: phổ biến nhất, dùng được, nhưng không mang theo đơn vị đo
            - STEP: hợp khi cần chỉnh sửa kích thước trước khi in
            - OBJ: dùng khi mô hình có màu hoặc vân bề mặt

            Không gửi ảnh chụp màn hình mô hình và hỏi giá — từ ảnh không tính được số gram nhựa.

            ## Bốn lỗi hay gặp nhất
            1. Sai đơn vị đo. File xuất từ phần mềm để đơn vị inch, mở ra trong máy in
               thành lớn gấp 25,4 lần. Luôn ghi rõ kích thước mong muốn theo mm khi gửi.

            2. Mặt lưới hở. Mô hình có lỗ thủng thì máy không biết đâu là trong đâu là ngoài.
               Kiểm tra và vá tự động bằng Microsoft 3D Builder hoặc Meshmixer, đều miễn phí.

            3. Thành quá mỏng. Chi tiết mỏng dưới 0,8mm thường in ra bị thủng hoặc gãy ngay khi gỡ.
               Cứ để tối thiểu 1,2mm cho phần nào cần cầm nắm.

            4. Chi tiết quá nhỏ. Chữ khắc nổi dưới 0,5mm chiều cao sẽ không đọc được sau khi in.

            ## Kèm theo file, hãy nói rõ
            - Kích thước mong muốn (dài x rộng x cao, theo mm)
            - Món đồ dùng để làm gì, đặt ở đâu — để shop chọn loại nhựa phù hợp
            - Màu muốn in
            - Cần nhẵn mịn để sơn, hay chỉ cần dùng được là xong

            ## Chưa có file 3D thì sao
            Gửi ảnh chụp vật mẫu từ vài góc kèm kích thước đo được, hoặc bản vẽ tay có ghi số đo.
            Shop nhận thiết kế file 3D từ đầu, báo giá riêng phần thiết kế trước khi bắt tay làm.
            """;
}
