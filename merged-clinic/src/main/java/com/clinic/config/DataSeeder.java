package com.clinic.config;

import com.clinic.model.Doctor;
import com.clinic.service.DoctorService;
import com.clinic.service.TOTPService;
import com.clinic.repository.DoctorRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalTime;

@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedData(DoctorService doctorService,
                                      DoctorRepository doctorRepository,
                                      TOTPService totpService,
                                      PasswordEncoder passwordEncoder) {
        return args -> {
            //Bác sĩ và slot demo
            try {
                Doctor d1 = doctorService.createDoctor(
                    "dr.vu", "password123",
                    "BS. Trần Đình Vũ", "vu@clinic.com", "0901234561",
                    "Ung thư học",
                    "Nhà tôi 3 đời chữa các bệnh về ung thư ai mắc ung thư cũng đến tôi chữa khỏi"
                );
                // Tạo slot 7 ngày tới
                for (int i = 0; i < 7; i++) {
                    LocalDate date = LocalDate.now().plusDays(i);
                    if (date.getDayOfWeek().getValue() < 6) { // Thứ 2 - Thứ 6
                        doctorService.generateSlots(d1.getId(), date,
                            LocalTime.of(8, 0), LocalTime.of(12, 0));
                        doctorService.generateSlots(d1.getId(), date,
                            LocalTime.of(13, 30), LocalTime.of(17, 0));
                    }
                }

                Doctor d2 = doctorService.createDoctor(
                    "dr.huy", "password123",
                    "BS. Trần Quốc Huy", "huy@clinic.com", "0901234562",
                    "Nhi khoa",
                    "Chuyên khám và điều trị các bệnh lý ở trẻ em, từ sơ sinh đến tuổi dậy thì."
                );
                for (int i = 0; i < 7; i++) {
                    LocalDate date = LocalDate.now().plusDays(i);
                    if (date.getDayOfWeek().getValue() < 6) {
                        doctorService.generateSlots(d2.getId(), date,
                            LocalTime.of(8, 0), LocalTime.of(11, 30));
                        doctorService.generateSlots(d2.getId(), date,
                            LocalTime.of(14, 0), LocalTime.of(17, 30));
                    }
                }

                Doctor d3 = doctorService.createDoctor(
                    "dr.hung", "password123",
                    "BS. Phùng Thanh Độ", "doshisa@clinic.com", "0901234563",
                    "Da liễu",
                    "Trong chuyện này tôi chỉ cần im lặng là tôi thắng rồi"
                );
                for (int i = 0; i < 7; i++) {
                    LocalDate date = LocalDate.now().plusDays(i);
                    if (date.getDayOfWeek().getValue() < 6) {
                        doctorService.generateSlots(d3.getId(), date,
                            LocalTime.of(9, 0), LocalTime.of(12, 0));
                    }
                }

                System.out.println("========================================");
                System.out.println("  CLINIC BOOKING SYSTEM - DEMO DATA");
                System.out.println("========================================");
                System.out.println("  Truy cập: http://localhost:8080");
                System.out.println();
                System.out.println("  👤 Bác sĩ:");
                System.out.println("     dr.vu / password123  (Nội tổng quát)");
                System.out.println("     dr.huy / password123  (Nhi khoa)");
                System.out.println("     dr.doshisa / password123 (Da liễu)");
                System.out.println();
                System.out.println("  🔐 Admin: admin / admin123");
                System.out.println("  📋 Bệnh nhân đặt lịch: không cần tài khoản");
                System.out.println("========================================");

            } catch (Exception e) {
                // Data đã tồn tại
            }

            // ---- Admin ----
            try {
                if (!doctorRepository.existsByUsername("admin")) {
                    Doctor admin = new Doctor();
                    admin.setUsername("admin");
                    admin.setPassword(passwordEncoder.encode("admin123"));
                    admin.setFullName("Quản trị viên");
                    admin.setEmail("admin@clinic.com");
                    admin.setSpecialty("Admin");
                    admin.setTotpSecret(totpService.generateSecretKey());
                    admin.setStatus(Doctor.DoctorStatus.ACTIVE);
                    doctorRepository.save(admin);
                }
            } catch (Exception ignored) {}
        };
    }
}
