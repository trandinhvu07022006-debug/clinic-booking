package com.clinic.controller;

import com.clinic.model.Doctor;
import com.clinic.model.TimeSlot;
import com.clinic.service.AppointmentService;
import com.clinic.service.DoctorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * BookingController — Luồng bệnh nhân đặt lịch (KHÔNG cần đăng nhập).
 *
 * Luồng:
 * 1. / → chọn bác sĩ
 * 2. /booking/slots?doctorId=&date= → chọn slot
 * 3. /booking/form?slotId= → nhập thông tin
 * 4. POST /booking/submit → tạo appointment + gửi OTP
 * 5. /booking/verify/{id} → nhập OTP xác nhận
 * 6. /booking/success/{id} → xác nhận thành công
 */
@Controller
public class BookingController {

    private final DoctorService doctorService;
    private final AppointmentService appointmentService;
    private final com.clinic.service.AppointmentOtpService otpService;

    @Value("${clinic.name:Phòng Khám Demo}")
    private String clinicName;

    @Value("${clinic.phone:}")
    private String clinicPhone;

    @Value("${clinic.address:}")
    private String clinicAddress;

    public BookingController(DoctorService ds, AppointmentService as, com.clinic.service.AppointmentOtpService os) {
        this.doctorService = ds;
        this.appointmentService = as;
        this.otpService = os;
    }

    // ==================== TRANG CHỦ — Chọn bác sĩ ====================

    @GetMapping("/")
    public String home(Model model) {
        List<Doctor> realDoctors = doctorService.getActiveDoctors().stream()
                .filter(doc -> doc.getSpecialty() != null
                            && !"Admin".equalsIgnoreCase(doc.getSpecialty()))
                .toList();

        // Đếm slot còn trống hôm nay cho từng bác sĩ
        LocalDate today = LocalDate.now();
        java.util.Map<Long, Integer> slotCounts = new java.util.HashMap<>();
        for (Doctor d : realDoctors) {
            int count = doctorService.getAvailableSlots(d.getId(), today).size();
            slotCounts.put(d.getId(), count);
        }

        // Danh sách chuyên khoa duy nhất cho filter chips
        List<String> specialties = realDoctors.stream()
                .map(Doctor::getSpecialty)
                .distinct()
                .sorted()
                .toList();

        // Thống kê nhanh cho trust badges
        long totalCompleted = appointmentService
                .countByStatus(com.clinic.model.Appointment.AppointmentStatus.COMPLETED);

        model.addAttribute("doctors",     realDoctors);
        model.addAttribute("slotCounts",  slotCounts);
        model.addAttribute("specialties", specialties);
        model.addAttribute("totalCompleted", totalCompleted);
        model.addAttribute("clinicName",  clinicName);
        model.addAttribute("clinicPhone", clinicPhone);
        model.addAttribute("clinicAddress", clinicAddress);
        model.addAttribute("today",       today);
        return "patient/home";
    }

    // ==================== Chọn ngày & slot ====================

    @GetMapping("/booking/slots")
    public String selectSlot(@RequestParam Long doctorId,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             Model model) {
        if (date == null) date = LocalDate.now();
        Doctor doctor = doctorService.findByUsername(
            doctorService.getActiveDoctors().stream()
                .filter(d -> d.getId().equals(doctorId)).findFirst()
                .orElseThrow().getUsername()
        ).orElseThrow();

        List<TimeSlot> slots = doctorService.getAvailableSlots(doctorId, date);

        model.addAttribute("doctor", doctor);
        model.addAttribute("slots", slots);
        model.addAttribute("selectedDate", date);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("clinicName", clinicName);
        return "patient/select-slot";
    }

    // ==================== Form nhập thông tin bệnh nhân ====================

    @GetMapping("/booking/form")
    public String bookingForm(@RequestParam Long slotId, Model model) {
        var slot = doctorService.getActiveDoctors().stream()
            .flatMap(d -> doctorService.getUpcomingSlots(d.getId()).stream())
            .filter(s -> s.getId().equals(slotId))
            .findFirst();

        if (slot.isEmpty()) return "redirect:/";

        model.addAttribute("slot", slot.get());
        model.addAttribute("clinicName", clinicName);
        return "patient/booking-form";
    }

    // ==================== Submit đặt lịch — gửi OTP ====================

    @PostMapping("/booking/submit")
    public String submitBooking(@RequestParam Long slotId,
                                @RequestParam String patientName,
                                @RequestParam String patientEmail,
                                @RequestParam String patientPhone,
                                @RequestParam(required = false) String symptoms,
                                @RequestParam(required = false, defaultValue = "EMAIL") String otpChannel,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        try {
            // Đọc kênh người dùng chọn (EMAIL mặc định nếu không có)
            AppointmentService.OtpChannel channel;
            try {
                channel = AppointmentService.OtpChannel.valueOf(otpChannel.toUpperCase());
            } catch (Exception ex) {
                channel = AppointmentService.OtpChannel.EMAIL;
            }

            var appointment = appointmentService.createPendingAppointment(
                slotId, patientName, patientEmail, patientPhone, symptoms, channel);
            session.setAttribute("pendingAppointmentId", appointment.getId());
            return "redirect:/booking/verify/" + appointment.getId();

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/booking/form?slotId=" + slotId;
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Lỗi thật sự là: " + e.getMessage());
            return "redirect:/booking/form?slotId=" + slotId;
        }
    }

    // ==================== Xác nhận OTP ====================

    @GetMapping("/booking/verify/{id}")
    public String verifyPage(@PathVariable Long id, HttpSession session, Model model) {
        var appt = appointmentService.findById(id);
        if (appt.isEmpty()) return "redirect:/";

        model.addAttribute("appointment", appt.get());
        model.addAttribute("maskedEmail", maskEmail(appt.get().getPatientEmail()));
        model.addAttribute("clinicName", clinicName);
        return "patient/verify-otp";
    }

    @PostMapping("/booking/verify/{id}")
    public String verifyOtp(@PathVariable Long id,
                            @RequestParam String otpCode,
                            Model model) {
        boolean ok = appointmentService.confirmWithOtp(id, otpCode.trim());
        if (ok) {
            return "redirect:/booking/success/" + id;
        }
        var appt = appointmentService.findById(id);
        model.addAttribute("appointment", appt.orElse(null));
        model.addAttribute("maskedEmail", appt.map(a -> maskEmail(a.getPatientEmail())).orElse("***"));
        model.addAttribute("error", "Mã OTP không đúng hoặc đã hết hạn!");
        model.addAttribute("clinicName", clinicName);
        return "patient/verify-otp";
    }

    @PostMapping("/booking/resend-otp/{id}")
    public String resendOtp(@PathVariable Long id,
                            RedirectAttributes ra) {
        var apptOpt = appointmentService.findById(id);
        if (apptOpt.isEmpty()) return "redirect:/";
        var appt = apptOpt.get();

        // Chỉ cho phép resend nếu còn ở trạng thái chờ OTP
        if (appt.getStatus() != com.clinic.model.Appointment.AppointmentStatus.PENDING_OTP) {
            ra.addFlashAttribute("error", "Lịch hẹn này không thể gửi lại OTP.");
            return "redirect:/booking/verify/" + id;
        }

        try {
            // ĐÃ SỬA LỖI TẠI ĐÂY: sendOtp -> sendConfirmationOtp
            otpService.sendConfirmationOtp(appt);          // reset + gửi OTP mới
            ra.addFlashAttribute("message", "✓ Đã gửi lại mã OTP. Kiểm tra hộp thư (kể cả thư rác).");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Không thể gửi lại email. Vui lòng thử lại sau.");
        }
        return "redirect:/booking/verify/" + id;
    }

    // ==================== Đặt lịch thành công ====================

    @GetMapping("/booking/success/{id}")
    public String success(@PathVariable Long id, Model model) {
        var appt = appointmentService.findById(id);
        if (appt.isEmpty()) return "redirect:/";
        model.addAttribute("appointment", appt.get());
        model.addAttribute("clinicName", clinicName);
        return "patient/success";
    }

    // ==================== Hủy lịch ====================

    @PostMapping("/booking/cancel/{id}")
    public String cancel(@PathVariable Long id) {
        appointmentService.cancelAppointment(id);
        return "redirect:/?cancelled=true";
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] p = email.split("@");
        int n = Math.min(3, p[0].length());
        return p[0].substring(0, n) + "***@" + p[1];
    }
}