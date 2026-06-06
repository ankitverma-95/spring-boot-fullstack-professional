package com.example.demo.repository;

import com.example.demo.entity.OvertimeEntry;
import com.example.demo.entity.SettlementStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OvertimeEntryRepository extends JpaRepository<OvertimeEntry, Long> {

    @EntityGraph(attributePaths = {"worker", "attendanceLog"})
    List<OvertimeEntry> findByWorkerIdAndDateBetween(Long workerId, LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = {"worker", "attendanceLog"})
    List<OvertimeEntry> findByWorkerIdAndDateBetweenAndSettlementStatus(
        Long workerId, 
        LocalDate start, 
        LocalDate end, 
        SettlementStatus settlementStatus
    );
}
