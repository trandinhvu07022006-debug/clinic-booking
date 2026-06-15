package com.clinic.service;

import com.clinic.model.Appointment;
import com.clinic.model.TimeSlot;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.TimeSlotRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OtpCleanupService — Dọn dẹp các lịch hẹn "treo" do OTP hết hạn.
 *
 * Bối cảnh (lỗ hổng Denial of Inventory do chính cơ chế OTP gây ra):
 *   Khi bệnh nhân bắt đầu đặt lịch, slot bị giữ ngay (BOOKED) để tránh đặt trùng.
 *   Nhưng nếu họ KHÔNG nhập OTP (mã hết hạn TTL), slot sẽ bị giữ VĨNH VIỄN
 *   ở trạng thái PENDING_OTP. Kẻ xấu có thể lợi dụng: spam tạo lịch rồi bỏ dở
 *   để chiếm hết slot khám của bệnh nhân thật.
 *
 * Giải pháp:
 *   Một tác vụ nền chạy định kỳ, quét các Appointment ở trạng thái PENDING_OTP
 *   đã quá hạn OTP -> chuyển slot về AVAILABLE và đánh dấu lịch là EXPIRED.
 *
 * LƯU Ý CẤU HÌNH:
 *   - Thêm @EnableScheduling vào class chính (ClinicApplication) để kích hoạt.
 *   - Nếu enum AppointmentStatus CHƯA có giá trị EXPIRED, hãy thêm nó vào;
 *     hoặc thay bằng CANCELLED (xem ghi chú trong code).
 *   - Cần repository method findExpiredPendingOtp(...) — mẫu ở cuối file.
 */
@Service
public class OtpCleanupService {

    private final AppointmentRepository appointmentRepository;
    private final TimeSlotRepository timeSlotRepository;

    public OtpCleanupService(AppointmentRepository ar, TimeSlotRepository ts) {
        this.appointmentRepository = ar;
        this.timeSlotRepository = ts;
    }

    /**
     * Chạy mỗi 60 giây. fixedDelay tính từ lúc lần chạy trước KẾT THÚC.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseExpiredAppointments() {
        LocalDateTime now = LocalDateTime.now();

        List<Appointment> expired = appointmentRepository
            .findByStatusAndOtpExpiryBefore(Appointment.AppointmentStatus.PENDING_OTP, now);

        if (expired.isEmpty()) return;

        for (Appointment appt : expired) {
            // Trả slot về AVAILABLE để bệnh nhân khác đặt được
            TimeSlot slot = appt.getSlot();
            if (slot != null && slot.getStatus() == TimeSlot.SlotStatus.BOOKED) {
                slot.setStatus(TimeSlot.SlotStatus.AVAILABLE);
                timeSlotRepository.save(slot);
            }

            // Đánh dấu lịch hết hạn + dọn dấu vết OTP
            // (Nếu không có EXPIRED trong enum, đổi dòng dưới thành CANCELLED)
            appt.setStatus(Appointment.AppointmentStatus.EXPIRED);
            appt.setOtpCode(null);
            appt.setOtpSalt(null);
            appt.setOtpExpiry(null);
            appointmentRepository.save(appt);
        }

        System.out.println("[OTP-CLEANUP] Đã giải phóng " + expired.size()
            + " slot do lịch hẹn hết hạn OTP lúc " + now);
    }
}

// =====================================================================
//  THÊM METHOD NÀY VÀO INTERFACE AppointmentRepository
// =====================================================================
//
//  import java.time.LocalDateTime;
//  import java.util.List;
//
//  List<Appointment> findByStatusAndOtpExpiryBefore(
//          Appointment.AppointmentStatus status, LocalDateTime time);
//
//  (Spring Data JPA tự sinh query từ tên method — không cần viết SQL)
//
// =====================================================================
//  THÊM EXPIRED VÀO enum AppointmentStatus (trong Appointment.java)
//  NẾU CHƯA CÓ:
// =====================================================================
//
//  public enum AppointmentStatus {
//      PENDING_OTP, CONFIRMED, COMPLETED, CANCELLED, EXPIRED
//  }
//
// =====================================================================
//  THÊM @EnableScheduling VÀO CLASS CHÍNH:
// =====================================================================
//
//  @SpringBootApplication
//  @EnableScheduling          // <-- thêm dòng này
//  public class ClinicApplication { ... }