package com.clinic.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "doctors")
public class Doctor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 100)
    private String specialty; // Chuyên khoa

    @Column(length = 500)
    private String bio; // Giới thiệu ngắn

    @Column(name = "totp_secret")
    private String totpSecret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DoctorStatus status = DoctorStatus.ACTIVE;

    @Column(name = "login_attempts", nullable = false)
    private int loginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL)
    private List<TimeSlot> timeSlots;

    public enum DoctorStatus { ACTIVE, INACTIVE }

    public Doctor() {}

    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    public void incrementLoginAttempts(int max, int lockMinutes) {
        loginAttempts++;
        if (loginAttempts >= max)
            lockedUntil = LocalDateTime.now().plusMinutes(lockMinutes);
    }

    public void resetLoginAttempts() {
        loginAttempts = 0;
        lockedUntil = null;
    }

    // Getters & Setters
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String v) { username = v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { password = v; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { fullName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String v) { specialty = v; }
    public String getBio() { return bio; }
    public void setBio(String v) { bio = v; }
    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String v) { totpSecret = v; }
    public DoctorStatus getStatus() { return status; }
    public void setStatus(DoctorStatus v) { status = v; }
    public int getLoginAttempts() { return loginAttempts; }
    public void setLoginAttempts(int v) { loginAttempts = v; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime v) { lockedUntil = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<TimeSlot> getTimeSlots() { return timeSlots; }
}
