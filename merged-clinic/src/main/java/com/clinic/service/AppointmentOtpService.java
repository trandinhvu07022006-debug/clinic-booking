package com.clinic.service;

import com.clinic.model.Appointment;
import com.clinic.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AppointmentOtpService — Transaction OTP cho đặt lịch.
 *
 * Khác với Login OTP:
 *   - OTP này gắn với 1 giao dịch cụ thể (appointmentId)
 *   - Email chứa thông tin lịch hẹn để bệnh nhân xác nhận đúng
 *   - Sau khi xác nhận, slot bị đánh dấu BOOKED ngay lập tức
 */
@Service
public class AppointmentOtpService {

    private final JavaMailSender mailSender;
    private final AppointmentRepository appointmentRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${otp.email.expiry-seconds:300}")
    private int expirySeconds;

    @Value("${spring.mail.username:no-reply@clinic.com}")
    private String fromEmail;

    @Value("${clinic.name:Phòng Khám Demo}")
    private String clinicName;

    public AppointmentOtpService(JavaMailSender mailSender, AppointmentRepository repo) {
        this.mailSender = mailSender;
        this.appointmentRepository = repo;
    }

    public void sendConfirmationOtp(Appointment appointment) throws Exception {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        appointment.setOtpCode(code);
        appointment.setOtpExpiry(LocalDateTime.now().plusSeconds(expirySeconds));
        appointmentRepository.save(appointment);

        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(appointment.getPatientEmail());
        helper.setSubject("[" + clinicName + "] Xác nhận đặt lịch khám");
        helper.setText(buildEmailHtml(appointment, code), true);
        
        // TẠM TẮT LỆNH GỬI MAIL THẬT ĐỂ VƯỢT TƯỜNG LỬA RENDER
        // mailSender.send(msg); 
        
        // IN MÃ OTP RA MÀN HÌNH LOGS CỦA RENDER
        System.out.println("\n=========================================");
        System.out.println("MÃ OTP CỦA BỆNH NHÂN LÀ: " + code); 
        System.out.println("=========================================\n");
    }

    public boolean verifyOtp(Appointment appointment, String inputCode) {
        if (!appointment.isOtpValid(inputCode)) return false;
        appointment.setOtpCode(null);
        appointment.setOtpExpiry(null);
        appointment.setOtpVerified(true);
        appointment.setStatus(Appointment.AppointmentStatus.CONFIRMED);
        appointment.setConfirmedAt(LocalDateTime.now());
        appointmentRepository.save(appointment);
        return true;
    }

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
