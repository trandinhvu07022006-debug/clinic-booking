package com.clinic.service;

import com.clinic.model.Appointment;
import com.clinic.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * AppointmentOtpService — Transaction OTP cho đặt lịch khám.
 *
 * Vai trò: OTP "ký giao dịch" (Transaction Signing) — mỗi mã gắn chặt với
 * MỘT lịch hẹn cụ thể (appointmentId + slot + bác sĩ), không phải OTP đăng nhập chung.
 *
 * Các lớp phòng thủ ATTT được hiện thực ở đây:
 *   - (4.3.3) OTP được BĂM SHA-256 + Salt trước khi lưu DB (không lưu cleartext).
 *   - (4.4)   So khớp bằng MessageDigest.isEqual() — Constant-time, chống Timing Attack.
 *   - (4.3.1) Rate Limiting: khóa xác thực sau N lần nhập sai (chống Brute-force).
 *   - (4.3.2) TTL: mã hết hạn theo otp.email.expiry-seconds.
 *   - (4.3.4) Atomic Invalidation: xóa mã ngay sau lần xác thực thành công đầu tiên
 *             (chống Replay Attack).
 */
@Service
public class AppointmentOtpService {

    private final JavaMailSender mailSender;
    private final AppointmentRepository appointmentRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${otp.email.expiry-seconds:300}")
    private int expirySeconds;

    @Value("${otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${spring.mail.username:no-reply@clinic.com}")
    private String fromEmail;

    @Value("${clinic.name:Phòng Khám Demo}")
    private String clinicName;

    public AppointmentOtpService(JavaMailSender mailSender, AppointmentRepository repo) {
        this.mailSender = mailSender;
        this.appointmentRepository = repo;
    }

    // =================================================================
    //  SINH MÃ + BĂM + LƯU  (mã gốc chỉ in ra console/gửi email, KHÔNG lưu DB)
    // =================================================================
    public void sendConfirmationOtp(Appointment appointment) throws Exception {
        // 1) Sinh mã OTP 6 số bằng CSPRNG (SecureRandom)
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        // 2) Sinh muối ngẫu nhiên 16 byte, riêng cho mỗi giao dịch
        byte[] saltBytes = new byte[16];
        secureRandom.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);

        // 3) Lưu BĂM của mã (không lưu mã gốc), kèm salt + TTL + reset bộ đếm sai
        appointment.setOtpCode(hashOtp(code, salt));
        appointment.setOtpSalt(salt);
        appointment.setOtpAttempts(0);
        appointment.setOtpExpiry(LocalDateTime.now().plusSeconds(expirySeconds));
        appointmentRepository.save(appointment);

        // 4) Soạn email (giữ nguyên template cũ)
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(appointment.getPatientEmail());
        helper.setSubject("[" + clinicName + "] Xác nhận đặt lịch khám");
        helper.setText(buildEmailHtml(appointment, code), true);

        // TẠM TẮT GỬI MAIL THẬT (Mocking Mode) — IN MÃ GỐC RA CONSOLE/LOG
        // mailSender.send(msg);
        System.out.println("\n=========================================");
        System.out.println("MÃ OTP CỦA BỆNH NHÂN LÀ: " + code);
        System.out.println("(Mã lưu trong DB ở dạng băm SHA-256 + Salt)");
        System.out.println("=========================================\n");
    }

    // =================================================================
    //  XÁC THỰC  (Rate Limiting + Constant-time + Atomic Invalidation)
    // =================================================================
    public boolean verifyOtp(Appointment appointment, String inputCode) {
        // (4.3.1) Rate Limiting — chặn trước khi tính toán băm để tiết kiệm CPU
        if (appointment.getOtpAttempts() >= maxAttempts) {
            throw new TooManyAttemptsException(
                "Bạn đã nhập sai quá " + maxAttempts + " lần. Vui lòng yêu cầu mã OTP mới.");
        }

        // (4.3.2) Kiểm tra tồn tại + hết hạn TTL
        if (appointment.getOtpCode() == null
                || appointment.getOtpSalt() == null
                || appointment.getOtpExpiry() == null
                || LocalDateTime.now().isAfter(appointment.getOtpExpiry())) {
            return false;
        }

        // Băm mã người dùng nhập với CÙNG salt đã lưu, rồi so khớp hai chuỗi băm
        String computedHash = hashOtp(inputCode, appointment.getOtpSalt());

        // (4.4) Constant-time comparison — chống Timing Attack
        boolean isMatch = MessageDigest.isEqual(
            computedHash.getBytes(StandardCharsets.UTF_8),
            appointment.getOtpCode().getBytes(StandardCharsets.UTF_8)
        );

        if (isMatch) {
            // (4.3.4) Atomic Invalidation — vô hiệu hóa mã ngay, chống Replay Attack
            appointment.setOtpCode(null);
            appointment.setOtpSalt(null);
            appointment.setOtpExpiry(null);
            appointment.setOtpAttempts(0);
            appointment.setOtpVerified(true);
            appointment.setStatus(Appointment.AppointmentStatus.CONFIRMED);
            appointment.setConfirmedAt(LocalDateTime.now());
            appointmentRepository.save(appointment);
            return true;
        } else {
            // Nhập sai → tăng bộ đếm
            appointment.setOtpAttempts(appointment.getOtpAttempts() + 1);
            appointmentRepository.save(appointment);
            return false;
        }
    }

    // =================================================================
    //  HÀM BĂM: SHA-256(salt || code)
    // =================================================================
    private String hashOtp(String code, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(salt));        // trộn salt
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi băm OTP", e);
        }
    }

    /** Ngoại lệ khi vượt quá số lần nhập sai cho phép. */
    public static class TooManyAttemptsException extends RuntimeException {
        public TooManyAttemptsException(String msg) { super(msg); }
    }

    // =================================================================
    //  EMAIL TEMPLATE (giữ nguyên)
    // =================================================================
    private String buildEmailHtml(Appointment appt, String code) {
        var slot = appt.getSlot();
        var dateStr = slot.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        var timeStr = slot.getDisplayTime();
        var doctorName = slot.getDoctor().getFullName();
        var specialty  = slot.getDoctor().getSpecialty();
        int mins = expirySeconds / 60;

        return """
        <!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"></head>
        <body style="font-family:'Segoe UI',Arial,sans-serif;background:#f5f5f5;padding:20px;margin:0">
          <div style="max-width:500px;margin:0 auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,.08)">
            <div style="background:linear-gradient(135deg,#0f766e,#0d9488);padding:28px 32px">
              <h2 style="color:#fff;margin:0;font-size:20px">🏥 Xác nhận đặt lịch khám</h2>
              <p style="color:rgba(255,255,255,.7);margin:6px 0 0;font-size:13px">%s</p>
            </div>
            <div style="padding:28px 32px">
              <p style="color:#333;margin:0 0 16px">Xin chào <strong>%s</strong>,</p>
              <div style="background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px;padding:16px;margin-bottom:20px">
                <p style="margin:0 0 6px;font-size:13px;color:#166534"><strong>📋 Thông tin lịch hẹn</strong></p>
                <p style="margin:4px 0;font-size:14px;color:#333">👨‍⚕️ Bác sĩ: <strong>%s</strong> — %s</p>
                <p style="margin:4px 0;font-size:14px;color:#333">📅 Ngày: <strong>%s</strong></p>
                <p style="margin:4px 0;font-size:14px;color:#333">⏰ Giờ: <strong>%s</strong></p>
              </div>
              <p style="color:#555;font-size:14px;margin:0 0 16px">Nhập mã OTP bên dưới để xác nhận lịch hẹn:</p>
              <div style="text-align:center;margin:0 0 20px">
                <div style="display:inline-block;background:#f0fdf4;border:2px dashed #0d9488;border-radius:10px;padding:16px 40px">
                  <span style="font-family:'Courier New',monospace;font-size:36px;font-weight:700;letter-spacing:10px;color:#0f766e">%s</span>
                </div>
              </div>
              <div style="background:#fff8e1;border-left:4px solid #f59e0b;padding:10px 14px;border-radius:4px;margin-bottom:16px">
                <p style="margin:0;color:#78350f;font-size:13px">⏱ Mã có hiệu lực <strong>%d phút</strong>. Không chia sẻ mã này.</p>
              </div>
              <p style="color:#999;font-size:12px;margin:0">Nếu bạn không đặt lịch, hãy bỏ qua email này.</p>
            </div>
          </div>
        </body></html>
        """.formatted(clinicName, appt.getPatientName(), doctorName, specialty, dateStr, timeStr, code, mins);
    }
}