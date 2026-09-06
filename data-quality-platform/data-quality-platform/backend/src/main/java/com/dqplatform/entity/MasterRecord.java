package com.dqplatform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "master_records", indexes = {
        @Index(name = "idx_record_code", columnList = "record_code"),
        @Index(name = "idx_record_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MasterRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_code", nullable = false, length = 50)
    private String recordCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 20)
    private String statusValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecordStatus status;

    @Column(length = 1000)
    private String validationErrors;

    @Column(length = 100)
    private String createdBy;

    @Column(length = 100)
    private String updatedBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
