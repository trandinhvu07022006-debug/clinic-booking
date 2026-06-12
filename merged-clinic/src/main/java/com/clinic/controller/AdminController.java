package com.clinic.controller;

import com.clinic.model.Appointment;
import com.clinic.service.AppointmentService;
import com.clinic.service.DoctorService;
import com.clinic.service.TOTPService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Controller
public class AdminController {

    private final DoctorService doctorService;
    private final AppointmentService appointmentService;
    private final TOTPService totpService;
    private final PasswordEncoder passwordEncoder;

    @Value("${clinic.name:Phòng Khám Demo}")
    private String clinicName;

    public AdminController(DoctorService ds, AppointmentService as,
                           TOTPService ts, PasswordEncoder pe) {
        this.doctorService = ds;
        this.appointmentService = as;
        this.totpService = ts;
        this.passwordEncoder = pe;
    }

    // ---- Admin Login ----
    @GetMapping("/auth/admin/login")
    public String loginPage(Model model) {
        model.addAttribute("clinicName", clinicName);
        return "auth/admin-login";
    }

    @PostMapping("/auth/admin/login")
    public String login(@RequestParam String username, @RequestParam String password,
                        HttpServletRequest request, HttpSession session, Model model) {
        // Admin dùng password only (hoặc có thể thêm TOTP sau)
        var opt = doctorService.findByUsername(username);
        boolean ok = opt.map(d -> passwordEncoder.matches(password, d.getPassword())
                                  && d.getSpecialty().equals("Admin"))
                        .orElse(false);
        if (!ok) {
            model.addAttribute("error", "Thông tin đăng nhập không đúng!");
            model.addAttribute("clinicName", clinicName);
            return "auth/admin-login";
        }
        session.invalidate();
        HttpSession ns = request.getSession(true);
        ns.setAttribute("loggedInAdmin", username);
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/auth/admin/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/auth/admin/login?logout";
    }

    // ---- Dashboard ----
    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        long confirmed  = appointmentService.countByStatus(Appointment.AppointmentStatus.CONFIRMED);
        long completed  = appointmentService.countByStatus(Appointment.AppointmentStatus.COMPLETED);
        long cancelled  = appointmentService.countByStatus(Appointment.AppointmentStatus.CANCELLED);
        long todayCount = appointmentService.countToday();
        long totalDoctors = doctorService.getAllDoctors().stream()
                             .filter(d -> !d.getSpecialty().equals("Admin")).count();

        model.addAttribute("confirmed", confirmed);
        model.addAttribute("completed", completed);
        model.addAttribute("cancelled", cancelled);
        model.addAttribute("todayCount", todayCount);
        model.addAttribute("totalDoctors", totalDoctors);
        model.addAttribute("recentAppointments", appointmentService.getAllAppointments()
                            .stream().limit(10).toList());

        // ĐÃ SỬA: Chart 7 ngày (Ép kiểu và Format an toàn bằng Stream)
        var weekly = appointmentService.getGlobalWeeklyStats();
        
        String chartLabels = weekly.keySet().stream()
                .map(date -> date.format(DateTimeFormatter.ofPattern("dd/MM")))
                .collect(Collectors.joining(","));
                
        String chartValues = weekly.values().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartValues", chartValues);
        model.addAttribute("statusStats", appointmentService.getStatusStats());
        model.addAttribute("clinicName", clinicName);
        return "admin/dashboard";
    }

    // ---- Quản lý bác sĩ ----
    @GetMapping("/admin/doctors")
    public String doctors(Model model) {
        model.addAttribute("doctors", doctorService.getAllDoctors().stream()
                            .filter(d -> !d.getSpecialty().equals("Admin")).toList());
        model.addAttribute("clinicName", clinicName);
        return "admin/doctors";
    }

    @GetMapping("/admin/doctors/new")
    public String newDoctorPage(Model model) {
        model.addAttribute("clinicName", clinicName);
        return "admin/doctor-form";
    }

    @PostMapping("/admin/doctors/new")
    public String createDoctor(@RequestParam String username, @RequestParam String password,
                                @RequestParam String fullName, @RequestParam String email,
                                @RequestParam String phone, @RequestParam String specialty,
                                @RequestParam(required = false) String bio,
                                Model model) {
        try {
            var doctor = doctorService.createDoctor(username, password, fullName, email, phone, specialty, bio);
            String qrUrl = totpService.getQRCodeUrl(username, doctor.getTotpSecret());
            model.addAttribute("doctor", doctor);
            model.addAttribute("qrUrl", qrUrl);
            model.addAttribute("clinicName", clinicName);
            return "admin/doctor-created";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("clinicName", clinicName);
            return "admin/doctor-form";
        }
    }

    // ---- Tất cả lịch hẹn ----
    @GetMapping("/admin/appointments")
    public String appointments(Model model) {
        model.addAttribute("appointments", appointmentService.getAllAppointments());
        model.addAttribute("clinicName", clinicName);
        return "admin/appointments";
    }
}