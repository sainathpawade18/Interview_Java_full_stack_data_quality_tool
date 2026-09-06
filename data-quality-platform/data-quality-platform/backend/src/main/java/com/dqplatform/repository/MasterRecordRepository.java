package com.dqplatform.repository;

import com.dqplatform.entity.MasterRecord;
import com.dqplatform.entity.RecordStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MasterRecordRepository extends JpaRepository<MasterRecord, Long> {
    boolean existsByRecordCodeIgnoreCase(String recordCode);
    boolean existsByRecordCodeIgnoreCaseAndIdNot(String recordCode, Long id);
    List<MasterRecord> findByStatus(RecordStatus status);
    Page<MasterRecord> findByRecordCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
            String code, String name, Pageable pageable);
}
