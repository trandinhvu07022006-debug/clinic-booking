package com.clinic.service;

import com.clinic.model.Doctor;
import com.clinic.model.TimeSlot;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final PasswordEncoder passwordEncoder;
    private final TOTPService totpService;

    @Value("${otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${otp.lockout-minutes:15}")
    private int lockoutMinutes;

    public DoctorService(DoctorRepository dr, TimeSlotRepository ts,
                         PasswordEncoder pe, TOTPService totp) {
        this.doctorRepository = dr;
        this.timeSlotRepository = ts;
        this.passwordEncoder = pe;
        this.totpService = totp;
    }

    public Doctor createDoctor(String username, String password, String fullName,
                               String email, String phone, String specialty, String bio) {
        if (doctorRepository.existsByUsername(username))
            throw new IllegalArgumentException("Username đã tồn tại!");
        if (doctorRepository.existsByEmail(email))
            throw new IllegalArgumentException("Email đã được dùng!");

        Doctor d = new Doctor();
        d.setUsername(username);
        d.setPassword(passwordEncoder.encode(password));
        d.setFullName(fullName);
        d.setEmail(email);
        d.setPhone(phone);
        d.setSpecialty(specialty);
        d.setBio(bio);
        d.setTotpSecret(totpService.generateSecretKey());
        return doctorRepository.save(d);
    }

    /** Bước 1: xác thực password bác sĩ */
    public Optional<Doctor> authenticatePassword(String username, String rawPassword) {
        var opt = doctorRepository.findByUsername(username);
        if (opt.isPresent() && opt.get().isLocked())
            throw new AccountLockedException("Tài khoản bị khóa. Thử lại sau " + lockoutMinutes + " phút.");

        boolean ok = opt.map(d -> passwordEncoder.matches(rawPassword, d.getPassword())).orElse(false);
        if (!ok) {
            opt.ifPresent(d -> { d.incrementLoginAttempts(maxAttempts, lockoutMinutes); doctorRepository.save(d); });
            return Optional.empty();
        }
        return opt;
    }

    /** Bước 2: xác thực TOTP bác sĩ */
    public boolean authenticateTOTP(String username, int otpCode) {
        return doctorRepository.findByUsername(username).map(d -> {
            boolean ok = totpService.verifyOTP(d.getTotpSecret(), otpCode);
            if (ok) { d.resetLoginAttempts(); doctorRepository.save(d); }
            return ok;
        }).orElse(false);
    }

    /** Tạo slot giờ khám cho bác sĩ — mỗi slot 30 phút */
    public void generateSlots(Long doctorId, LocalDate date, LocalTime from, LocalTime to) {
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow();
        LocalTime current = from;
        while (current.plusMinutes(30).compareTo(to) <= 0) {
            timeSlotRepository.save(new TimeSlot(doctor, date, current, current.plusMinutes(30)));
            current = current.plusMinutes(30);
        }
    }

    @Transactional(readOnly = true)
    public List<TimeSlot> getAvailableSlots(Long doctorId, LocalDate date) {
        return timeSlotRepository.findByDoctorIdAndDateAndStatus(doctorId, date, TimeSlot.SlotStatus.AVAILABLE);
    }

    @Transactional(readOnly = true)
    public List<TimeSlot> getUpcomingSlots(Long doctorId) {
        return timeSlotRepository.findUpcomingByDoctor(doctorId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Optional<Doctor> findByUsername(String username) {
        return doctorRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public List<Doctor> getActiveDoctors() {
        return doctorRepository.findByStatus(Doctor.DoctorStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAll();
    }

    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException(String msg) { super(msg); }
    }
}
