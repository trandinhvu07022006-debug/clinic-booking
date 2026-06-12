package com.clinic.controller;

import com.clinic.service.AppointmentService;
import com.clinic.service.DoctorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/doctor")
public class DoctorDashboardController {

    private final DoctorService doctorService;
    private final AppointmentService appointmentService;

    @Value("${clinic.name:Phòng Khám Demo}")
    private String clinicName;

    public DoctorDashboardController(DoctorService ds, AppointmentService as) {
        this.doctorService = ds;
        this.appointmentService = as;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        String username = (String) session.getAttribute("loggedInDoctor");
        var doctor = doctorService.findByUsername(username).orElseThrow();

        var today = appointmentService.getTodayAppointments(doctor.getId());
        var upcoming = appointmentService.getUpcomingAppointments(doctor.getId());

        model.addAttribute("doctor", doctor);
        model.addAttribute("todayAppointments", today);
        model.addAttribute("upcomingAppointments", upcoming);
        model.addAttribute("todayCount", today.size());
        model.addAttribute("confirmedCount",
            today.stream().filter(a -> a.getStatus().name().equals("CONFIRMED")).count());
        model.addAttribute("completedCount",
            today.stream().filter(a -> a.getStatus().name().equals("COMPLETED")).count());
        
        // Chart 7 ngày gần nhất (Ép kiểu an toàn bằng Stream)
        var weekly = appointmentService.getWeeklyStats(doctor.getId());
        
        String chartLabels = weekly.keySet().stream()
                .map(date -> date.format(DateTimeFormatter.ofPattern("dd/MM")))
                .collect(Collectors.joining(","));
                
        String chartValues = weekly.values().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartValues", chartValues);
        model.addAttribute("clinicName", clinicName);
        model.addAttribute("today", LocalDate.now());
        return "doctor/dashboard";
    }

    @GetMapping("/schedule")
    public String schedule(@RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           HttpSession session, Model model) {
        if (date == null) date = LocalDate.now();
        String username = (String) session.getAttribute("loggedInDoctor");
        var doctor = doctorService.findByUsername(username).orElseThrow();
        var appointments = appointmentService.getTodayAppointments(doctor.getId());

        model.addAttribute("doctor", doctor);
        model.addAttribute("appointments", appointments);
        model.addAttribute("selectedDate", date);
        model.addAttribute("clinicName", clinicName);
        return "doctor/schedule";
    }

    @PostMapping("/appointment/{id}/complete")
    public String complete(@PathVariable Long id,
                           @RequestParam(required = false) String notes) {
        appointmentService.completeAppointment(id, notes);
        return "redirect:/doctor/dashboard";
    }

    @PostMapping("/appointment/{id}/cancel")
    public String cancel(@PathVariable Long id) {
        appointmentService.cancelAppointment(id);
        return "redirect:/doctor/dashboard";
    }

    @GetMapping("/slots")
    public String manageSlots(@RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               HttpSession session, Model model) {
        if (date == null) date = LocalDate.now();
        String username = (String) session.getAttribute("loggedInDoctor");
        var doctor = doctorService.findByUsername(username).orElseThrow();
        var slots = doctorService.getAvailableSlots(doctor.getId(), date);

        model.addAttribute("doctor", doctor);
        model.addAttribute("slots", slots);
        model.addAttribute("selectedDate", date);
        model.addAttribute("clinicName", clinicName);
        return "doctor/slots";
    }

    @PostMapping("/slots/generate")
    public String generateSlots(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 @RequestParam String fromTime,
                                 @RequestParam String toTime,
                                 HttpSession session) {
        String username = (String) session.getAttribute("loggedInDoctor");
        var doctor = doctorService.findByUsername(username).orElseThrow();
        doctorService.generateSlots(doctor.getId(), date,
            java.time.LocalTime.parse(fromTime), java.time.LocalTime.parse(toTime));
        return "redirect:/doctor/slots?date=" + date;
    }
}