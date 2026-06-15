package com.clinic.service;

import com.clinic.model.Appointment;
import com.clinic.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * SmsOtpService — Kênh phân phối OTP qua SMS (chế độ GIẢ LẬP / Mock Mode).
 *
 * Mục đích minh họa cho Chương 2 & Chương 4 của báo cáo:
 *   - Thể hiện kiến trúc đa kênh (Multi-channel): cùng một lõi mật mã,
 *     nhưng có thể phân phối qua Email HOẶC SMS (Out-of-Band).
 *   - Cho phép phân tích các lỗ hổng đặc thù của kênh SMS (SS7, SIM Swapping)
 *     đã trình bày ở mục 4.1 — gắn lý thuyết với phần triển khai thực tế.
 *
 * QUAN TRỌNG — phần lõi mật mã GIỐNG HỆT AppointmentOtpService:
 *   - (5.3.1) Sinh mã bằng SecureRandom (CSPRNG)
 *   - (4.3.3) Băm SHA-256 + Salt trước khi lưu (không lưu cleartext)
 *   - (4.4)   So khớp constant-time (MessageDigest.isEqual)
 *   - (4.3.1) Rate Limiting chống Brute-force
 *   - (4.3.4) Atomic Invalidation chống Replay Attack
 *
 * KHÁC BIỆT DUY NHẤT so với Email: bước "Phân phối" (Deliver).
 *   - Production thật: gọi API của SMS Gateway (Twilio / eSMS / SpeedSMS...)
 *   - Mock Mode (demo): in mã ra Console/Log với nhãn [SMS GATEWAY - MOCK],
 *     mô phỏng việc bệnh nhân nhận tin nhắn trên điện thoại.
 */
@Service
public class SmsOtpService {

    private final AppointmentRepository appointmentRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${otp.sms.expiry-seconds:300}")
    private int expirySeconds;

    @Value("${otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${clinic.name:Phòng Khám Demo}")
    private String clinicName;

    public SmsOtpService(AppointmentRepository repo) {
        this.appointmentRepository = repo;
    }

    // =================================================================
    //  SINH MÃ + BĂM + LƯU + "GỬI SMS" (mock)
    // =================================================================
    public void sendConfirmationSms(Appointment appointment) {
        // 1) Sinh mã OTP 6 số bằng CSPRNG
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        // 2) Sinh muối ngẫu nhiên 16 byte riêng cho mỗi giao dịch
        byte[] saltBytes = new byte[16];
        secureRandom.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);

        // 3) Lưu BĂM của mã (không lưu cleartext) + salt + TTL + reset bộ đếm sai
        appointment.setOtpCode(hashOtp(code, salt));
        appointment.setOtpSalt(salt);
        appointment.setOtpAttempts(0);
        appointment.setOtpExpiry(LocalDateTime.now().plusSeconds(expirySeconds));
        appointmentRepository.save(appointment);

        // 4) "Gửi SMS" — Mock Mode: in ra console mô phỏng tin nhắn về điện thoại
        deliverSmsMock(appointment.getPatientPhone(), code);
    }

    /**
     * Giả lập SMS Gateway. Ở production, thay phần thân hàm này bằng lệnh gọi
     * API thật của nhà mạng/dịch vụ (ví dụ Twilio Message API).
     */
    private void deliverSmsMock(String phoneNumber, String code) {
        int mins = expirySeconds / 60;
        System.out.println("\n========== [SMS GATEWAY - MOCK] ==========");
        System.out.println("Gửi tới số (To)   : " + phoneNumber);
        System.out.println("Nội dung (Body)   : [" + clinicName + "] Ma OTP xac nhan dat lich cua ban la "
                + code + ". Co hieu luc " + mins + " phut. Khong chia se ma nay.");
        System.out.println("(Mã lưu trong DB ở dạng băm SHA-256 + Salt)");
        System.out.println("==========================================\n");
    }

    // =================================================================
    //  XÁC THỰC — logic giống hệt kênh Email
    // =================================================================
    public boolean verifyOtp(Appointment appointment, String inputCode) {
        // (4.3.1) Rate Limiting — chặn trước khi tính băm
        if (appointment.getOtpAttempts() >= maxAttempts) {
            throw new AppointmentOtpService.TooManyAttemptsException(
                "Bạn đã nhập sai quá " + maxAttempts + " lần. Vui lòng yêu cầu mã OTP mới.");
        }

        // (4.3.2) Kiểm tra tồn tại + hết hạn TTL
        if (appointment.getOtpCode() == null
                || appointment.getOtpSalt() == null
                || appointment.getOtpExpiry() == null
                || LocalDateTime.now().isAfter(appointment.getOtpExpiry())) {
            return false;
        }

        // Băm mã người dùng nhập với cùng salt đã lưu
        String computedHash = hashOtp(inputCode, appointment.getOtpSalt());

        // (4.4) Constant-time comparison
        boolean isMatch = MessageDigest.isEqual(
            computedHash.getBytes(StandardCharsets.UTF_8),
            appointment.getOtpCode().getBytes(StandardCharsets.UTF_8)
        );

        if (isMatch) {
            // (4.3.4) Atomic Invalidation
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
            digest.update(Base64.getDecoder().decode(salt));
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi băm OTP (SMS)", e);
        }
    }
}