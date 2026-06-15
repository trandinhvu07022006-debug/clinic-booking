package com.clinic.service;

import com.clinic.model.Appointment;
import com.clinic.model.TimeSlot;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AppointmentOtpService otpService;
    private final SmsOtpService smsOtpService;

    public AppointmentService(AppointmentRepository ar, TimeSlotRepository ts,
                           AppointmentOtpService otpService,
                           SmsOtpService smsOtpService) {
    this.appointmentRepository = ar;
    this.timeSlotRepository = ts;
    this.otpService = otpService;
    this.smsOtpService = smsOtpService;
}

    /**
     * Tạo lịch hẹn mới và gửi OTP xác nhận.
     * Slot bị giữ (BOOKED tạm) để tránh đặt trùng trong lúc chờ OTP.
     */
   public enum OtpChannel { EMAIL, SMS }

    public Appointment createPendingAppointment(Long slotId, String name,
                                                 String email, String phone,
                                                 String symptoms,
                                                 OtpChannel channel) throws Exception {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Slot không tồn tại!"));

        if (slot.getStatus() != TimeSlot.SlotStatus.AVAILABLE)
            throw new IllegalStateException("Slot này đã được đặt, vui lòng chọn slot khác!");

        slot.setStatus(TimeSlot.SlotStatus.BOOKED);
        timeSlotRepository.save(slot);

        Appointment appt = new Appointment();
        appt.setPatientName(name);
        appt.setPatientEmail(email);
        appt.setPatientPhone(phone);
        appt.setSymptoms(symptoms);
        appt.setSlot(slot);
        appt.setStatus(Appointment.AppointmentStatus.PENDING_OTP);
        appointmentRepository.save(appt);

        if (channel == OtpChannel.SMS) {
            smsOtpService.sendConfirmationSms(appt);
        } else {
            otpService.sendConfirmationOtp(appt);
        }
        return appt;
    }

    // =====================================================================
//  THAY THẾ HÀM confirmWithOtp() CŨ TRONG AppointmentService.java
//  BẰNG HÀM DƯỚI ĐÂY (để bắt ngoại lệ Rate Limiting)
// =====================================================================
//
//  Cũng cần thêm import ở đầu file:
//    (không cần import gì mới — TooManyAttemptsException là inner class
//     của AppointmentOtpService, tham chiếu qua tên đầy đủ bên dưới)

    /**
     * Xác nhận lịch hẹn bằng OTP.
     *  - OTP đúng & còn hạn  -> true  (slot chuyển CONFIRMED trong verifyOtp)
     *  - OTP sai / hết hạn   -> false (slot vẫn giữ, user thử lại)
     *  - Vượt quá số lần sai -> ném TooManyAttemptsException để controller báo rõ
     */
    public boolean confirmWithOtp(Long appointmentId, String otpCode) {
        return appointmentRepository.findById(appointmentId)
            .map(appt -> otpService.verifyOtp(appt, otpCode))
            .orElse(false);
        // Ghi chú: TooManyAttemptsException (RuntimeException) sẽ tự động
        // propagate lên Controller. Bắt nó ở Controller bằng try/catch hoặc
        // @ExceptionHandler để trả HTTP 429 / thông báo "khóa tạm thời".
    }

    /**
     * Hủy lịch hẹn — trả lại slot về AVAILABLE.
     */
    public void cancelAppointment(Long appointmentId) {
        appointmentRepository.findById(appointmentId).ifPresent(appt -> {
            appt.setStatus(Appointment.AppointmentStatus.CANCELLED);
            appt.getSlot().setStatus(TimeSlot.SlotStatus.AVAILABLE);
            timeSlotRepository.save(appt.getSlot());
            appointmentRepository.save(appt);
        });
    }

    /** Bác sĩ hoàn thành ca khám */
    public void completeAppointment(Long appointmentId, String notes) {
        appointmentRepository.findById(appointmentId).ifPresent(appt -> {
            appt.setStatus(Appointment.AppointmentStatus.COMPLETED);
            appt.setNotes(notes);
            appointmentRepository.save(appt);
        });
    }

    @Transactional(readOnly = true)
    public Optional<Appointment> findById(Long id) {
        return appointmentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Appointment> getTodayAppointments(Long doctorId) {
        return appointmentRepository.findByDoctorAndDate(doctorId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Appointment> getUpcomingAppointments(Long doctorId) {
        return appointmentRepository.findUpcomingByDoctor(doctorId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public long countByStatus(Appointment.AppointmentStatus status) {
        return appointmentRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countToday() {
        return appointmentRepository.countByDate(LocalDate.now());
    }

    // ==========================================================
    // CÁC HÀM THỐNG KÊ ĐƯỢC THÊM VÀO ĐỂ SỬA LỖI COMPILATION
    // ==========================================================

    /**
     * Lấy thống kê số lượng lịch hẹn trong 7 ngày qua của một bác sĩ.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getWeeklyStats(Long doctorId) {
        Map<LocalDate, Long> stats = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            // Tận dụng hàm findByDoctorAndDate đã có sẵn trong Repository của bạn
            long count = appointmentRepository.findByDoctorAndDate(doctorId, date).size();
            stats.put(date, count);
        }
        return stats;
    }

    /**
     * Lấy thống kê số lượng lịch hẹn trong 7 ngày qua của toàn hệ thống (Admin).
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getGlobalWeeklyStats() {
        Map<LocalDate, Long> stats = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long count = appointmentRepository.countByDate(date);
            stats.put(date, count);
        }
        return stats;
    }

    /**
     * Lấy thống kê tổng số lịch hẹn phân theo từng trạng thái (Admin).
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStatusStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (Appointment.AppointmentStatus status : Appointment.AppointmentStatus.values()) {
            stats.put(status.name(), countByStatus(status));
        }
        return stats;
    }
}