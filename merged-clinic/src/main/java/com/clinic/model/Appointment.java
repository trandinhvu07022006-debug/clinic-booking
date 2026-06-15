package com.clinic.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Thông tin bệnh nhân (không cần tài khoản)
    @Column(nullable = false, length = 100)
    private String patientName;

    @Column(nullable = false, length = 100)
    private String patientEmail;

    @Column(nullable = false, length = 20)
    private String patientPhone;

    @Column(length = 500)
    private String symptoms; // Triệu chứng / lý do khám

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private TimeSlot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.PENDING_OTP;

    // ✅ Transaction OTP fields
    // otpCode giờ lưu GIÁ TRỊ ĐÃ BĂM (SHA-256 + Salt), dài ~44 ký tự Base64
    @Column(name = "otp_code", length = 64)
    private String otpCode;

    // Muối ngẫu nhiên (Base64, 16 byte) dùng kèm khi băm OTP
    @Column(name = "otp_salt", length = 32)
    private String otpSalt;

    // Số lần nhập sai OTP cho lịch hẹn này (chống Brute-force)
    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts = 0;

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    @Column(name = "otp_verified", nullable = false)
    private boolean otpVerified = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "notes", length = 500)
    private String notes; // Ghi chú của bác sĩ

    public enum AppointmentStatus {
        PENDING_OTP,   // Chờ xác nhận OTP
        CONFIRMED,     // Đã xác nhận
        COMPLETED,     // Đã khám xong
        CANCELLED,     // Đã hủy
        EXPIRED        // Hết hạn OTP (job dọn dẹp tự trả slot về AVAILABLE)
    }

    public Appointment() {}

    public boolean isOtpValid(String code) {
        return code != null
            && code.equals(this.otpCode)
            && this.otpExpiry != null
            && LocalDateTime.now().isBefore(this.otpExpiry);
    }

    // Getters & Setters
    public Long getId() { return id; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String v) { patientName = v; }
    public String getPatientEmail() { return patientEmail; }
    public void setPatientEmail(String v) { patientEmail = v; }
    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String v) { patientPhone = v; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String v) { symptoms = v; }
    public TimeSlot getSlot() { return slot; }
    public void setSlot(TimeSlot v) { slot = v; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus v) { status = v; }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String v) { otpCode = v; }
    public LocalDateTime getOtpExpiry() { return otpExpiry; }
    public void setOtpExpiry(LocalDateTime v) { otpExpiry = v; }
    public boolean isOtpVerified() { return otpVerified; }
    public void setOtpVerified(boolean v) { otpVerified = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime v) { confirmedAt = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
    public String getOtpSalt() { return otpSalt; }
    public void setOtpSalt(String otpSalt) { this.otpSalt = otpSalt; }
    public int getOtpAttempts() { return otpAttempts; }
    public void setOtpAttempts(int otpAttempts) { this.otpAttempts = otpAttempts; }
}