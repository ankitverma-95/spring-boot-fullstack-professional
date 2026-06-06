package com.example.demo.repository;

import com.example.demo.entity.AttendanceLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    @EntityGraph(attributePaths = {"worker", "site"})
    Optional<AttendanceLog> findByWorkerIdAndClockOutIsNull(Long workerId);

    @EntityGraph(attributePaths = {"worker", "site"})
    List<AttendanceLog> findByClockOutIsNull();

    @EntityGraph(attributePaths = {"worker", "site"})
    Page<AttendanceLog> findByWorkerIdAndClockInBetween(
        Long workerId, 
        LocalDateTime from, 
        LocalDateTime to, 
        Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(a.overtimeHours), 0.0) FROM AttendanceLog a " +
           "WHERE a.worker.id = :workerId AND a.clockIn >= :start AND a.clockIn < :end")
    Double sumOvertimeHoursForWorkerInMonth(
        @Param("workerId") Long workerId, 
        @Param("start") LocalDateTime start, 
        @Param("end") LocalDateTime end
    );
}
