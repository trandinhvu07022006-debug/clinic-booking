package com.clinic.repository;

import com.clinic.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    List<TimeSlot> findByDoctorIdAndDateAndStatus(Long doctorId, LocalDate date, TimeSlot.SlotStatus status);
    List<TimeSlot> findByDoctorIdAndDateGreaterThanEqualAndStatusOrderByDateAscStartTimeAsc(
        Long doctorId, LocalDate date, TimeSlot.SlotStatus status);

    @Query("SELECT t FROM TimeSlot t WHERE t.doctor.id = :doctorId AND t.date >= :from ORDER BY t.date, t.startTime")
    List<TimeSlot> findUpcomingByDoctor(Long doctorId, LocalDate from);
}
