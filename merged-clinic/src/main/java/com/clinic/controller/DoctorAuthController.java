package com.clinic.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.clinic.service.AppointmentService;
import com.clinic.service.DoctorService;
import com.clinic.service.TOTPService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/auth/doctor")
public class DoctorAuthController {

    private final DoctorService doctorService;
    private final TOTPService totpService;
    private final AppointmentService appointmentService;

    @Value("${clinic.name:Phòng Khám Demo}")
    private String clinicName;

    public DoctorAuthController(DoctorService ds, TOTPService ts, AppointmentService as) {
        this.doctorService = ds;
        this.totpService = ts;
        this.appointmentService = as;
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("clinicName", clinicName);
        return "auth/doctor-login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password,
                        HttpSession session, Model model) {
        try {
            var opt = doctorService.authenticatePassword(username, password);
            if (opt.isEmpty()) {
                model.addAttribute("error", "Tên đăng nhập hoặc mật khẩu không đúng!");
                model.addAttribute("clinicName", clinicName);
                return "auth/doctor-login";
            }
            session.setAttribute("pendingDoctorUsername", username);
            return "redirect:/auth/doctor/verify-totp";
        } catch (DoctorService.AccountLockedException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("clinicName", clinicName);
            return "auth/doctor-login";
        }
    }

   @GetMapping("/verify-totp")
    public String totpPage(HttpSession session, Model model) {
        String username = (String) session.getAttribute("pendingDoctorUsername");
        if (username == null)
            return "redirect:/auth/doctor/login";

        // [DEMO MODE] In mã TOTP hiện tại ra console để kiểm thử (chưa có app thật)
        doctorService.findByUsername(username).ifPresent(d ->
            totpService.printCurrentCodeForDemo(d.getTotpSecret(), username)
        );

        model.addAttribute("clinicName", clinicName);
        return "auth/doctor-totp";
    }

    @PostMapping("/verify-totp")
    public String verifyTotp(@RequestParam String otpCode, HttpSession session,
                              HttpServletRequest request, Model model) {
        String username = (String) session.getAttribute("pendingDoctorUsername");
        if (username == null) return "redirect:/auth/doctor/login";

        try {
            int otp = Integer.parseInt(otpCode.trim());
            boolean ok = doctorService.authenticateTOTP(username, otp);
            if (ok) {
                session.invalidate();
                HttpSession newSession = request.getSession(true);
                newSession.setAttribute("loggedInDoctor", username);
                return "redirect:/doctor/dashboard";
            }
            model.addAttribute("error", "Mã TOTP không hợp lệ!");
            model.addAttribute("clinicName", clinicName);
            return "auth/doctor-totp";
        } catch (NumberFormatException e) {
            model.addAttribute("error", "Vui lòng nhập đúng 6 chữ số!");
            model.addAttribute("clinicName", clinicName);
            return "auth/doctor-totp";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/auth/doctor/login?logout";
    }
}
