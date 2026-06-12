package com.clinic.repository;

import com.clinic.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("SELECT a FROM Appointment a JOIN a.slot s WHERE s.doctor.id = :doctorId AND s.date = :date ORDER BY s.startTime")
    List<Appointment> findByDoctorAndDate(Long doctorId, LocalDate date);

    @Query("SELECT a FROM Appointment a JOIN a.slot s WHERE s.doctor.id = :doctorId AND s.date >= :from ORDER BY s.date, s.startTime")
    List<Appointment> findUpcomingByDoctor(Long doctorId, LocalDate from);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.status = :status")
    long countByStatus(Appointment.AppointmentStatus status);

    @Query("SELECT COUNT(a) FROM Appointment a JOIN a.slot s WHERE s.date = :date")
    long countByDate(LocalDate date);

    @Query("SELECT s.date, COUNT(a) FROM Appointment a JOIN a.slot s " +
           "WHERE s.doctor.id = :doctorId AND s.date BETWEEN :from AND :to " +
           "GROUP BY s.date ORDER BY s.date")
    List<Object[]> countByDoctorAndDateRange(Long doctorId, LocalDate from, LocalDate to);

    @Query("SELECT s.date, COUNT(a) FROM Appointment a JOIN a.slot s " +
           "WHERE s.date BETWEEN :from AND :to " +
           "GROUP BY s.date ORDER BY s.date")
    List<Object[]> countByDateRange(LocalDate from, LocalDate to);

    @Query("SELECT a.status, COUNT(a) FROM Appointment a GROUP BY a.status")
    List<Object[]> countGroupByStatus();

    List<Appointment> findByPatientEmailOrderByCreatedAtDesc(String email);
}
