package com.dqplatform.service;

import com.dqplatform.dto.MasterRecordRequest;
import com.dqplatform.entity.*;
import com.dqplatform.repository.MasterRecordRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MasterRecordService {
    private final MasterRecordRepository repository;
    private final ValidationService validation;
    private final AuditService audit;

    public MasterRecordService(MasterRecordRepository repository,
                               ValidationService validation,
                               AuditService audit) {
        this.repository = repository;
        this.validation = validation;
        this.audit = audit;
    }

    public Page<MasterRecord> search(String q, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        if (q == null || q.isBlank()) return repository.findAll(pageable);
        return repository.findByRecordCodeContainingIgnoreCaseOrNameContainingIgnoreCase(q, q, pageable);
    }

    @Transactional
    public MasterRecord create(MasterRecordRequest req, String username) {
        MasterRecord r = MasterRecord.builder()
                .recordCode(req.recordCode()).name(req.name()).email(req.email())
                .phone(req.phone()).category(req.category()).statusValue(req.statusValue())
                .status(RecordStatus.DRAFT).createdBy(username).updatedBy(username)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        applyValidation(r, false);
        var saved = repository.save(r);
        audit.log(username, "CREATE", "MASTER_RECORD", saved.getId().toString(),
                "Created master record");
        return saved;
    }

    @Transactional
    public MasterRecord update(Long id, MasterRecordRequest req, String username) {
        MasterRecord r = get(id);
        r.setRecordCode(req.recordCode()); r.setName(req.name()); r.setEmail(req.email());
        r.setPhone(req.phone()); r.setCategory(req.category()); r.setStatusValue(req.statusValue());
        r.setUpdatedBy(username); r.setUpdatedAt(LocalDateTime.now());
        applyValidation(r, true);
        var saved = repository.save(r);
        audit.log(username, "UPDATE", "MASTER_RECORD", id.toString(), "Updated master record");
        return saved;
    }

    public MasterRecord get(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new NoSuchElementException("Master record not found: " + id));
    }

    public void delete(Long id, String username) {
        get(id);
        repository.deleteById(id);
        audit.log(username, "DELETE", "MASTER_RECORD", id.toString(), "Deleted master record");
    }

    @Transactional
    public MasterRecord submit(Long id, String username) {
        MasterRecord r = get(id);
        applyValidation(r, true);
        if (r.getValidationErrors() != null && !r.getValidationErrors().isBlank())
            throw new IllegalStateException("Cannot submit invalid record: " + r.getValidationErrors());
        r.setStatus(RecordStatus.PENDING_APPROVAL);
        r.setUpdatedBy(username); r.setUpdatedAt(LocalDateTime.now());
        audit.log(username, "SUBMIT", "MASTER_RECORD", id.toString(), "Submitted for approval");
        return repository.save(r);
    }

    @Transactional
    public MasterRecord approve(Long id, String username) {
        MasterRecord r = get(id);
        if (r.getStatus() != RecordStatus.PENDING_APPROVAL)
            throw new IllegalStateException("Only pending records can be approved");
        r.setStatus(RecordStatus.APPROVED);
        r.setUpdatedBy(username); r.setUpdatedAt(LocalDateTime.now());
        audit.log(username, "APPROVE", "MASTER_RECORD", id.toString(), "Approved record");
        return repository.save(r);
    }

    @Transactional
    public MasterRecord reject(Long id, String username) {
        MasterRecord r = get(id);
        if (r.getStatus() != RecordStatus.PENDING_APPROVAL)
            throw new IllegalStateException("Only pending records can be rejected");
        r.setStatus(RecordStatus.REJECTED);
        r.setUpdatedBy(username); r.setUpdatedAt(LocalDateTime.now());
        audit.log(username, "REJECT", "MASTER_RECORD", id.toString(), "Rejected record");
        return repository.save(r);
    }

    @Transactional
    public int upload(MultipartFile file, String username) throws IOException {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        if (!(filename.endsWith(".csv") || filename.endsWith(".xlsx")))
            throw new IllegalArgumentException("Only CSV and XLSX files are supported");

        int count = filename.endsWith(".csv")
                ? uploadCsv(file.getInputStream(), username)
                : uploadXlsx(file.getInputStream(), username);

        audit.log(username, "BULK_UPLOAD", "MASTER_RECORD", "-", "Uploaded " + count + " records");
        return count;
    }

    private int uploadCsv(InputStream input, String username) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(input));
        String line = br.readLine(); // header
        if (line == null) return 0;
        int count = 0;
        while ((line = br.readLine()) != null) {
            String[] c = line.split(",", -1);
            if (c.length < 6) continue;
            saveImported(c[0], c[1], c[2], c[3], c[4], c[5], username);
            count++;
        }
        return count;
    }

    private int uploadXlsx(InputStream input, String username) throws IOException {
        try (var workbook = org.apache.poi.xssf.usermodel.XSSFWorkbookFactory.create(input)) {
            var sheet = workbook.getSheetAt(0);
            int count = 0;
            boolean header = true;
            for (var row : sheet) {
                if (header) { header = false; continue; }
                String[] c = new String[6];
                for (int i = 0; i < 6; i++) {
                    c[i] = row.getCell(i) == null ? "" :
                            row.getCell(i).toString().trim();
                }
                if (c[0].isBlank()) continue;
                saveImported(c[0], c[1], c[2], c[3], c[4], c[5], username);
                count++;
            }
            return count;
        }
    }

    private void saveImported(String code, String name, String email, String phone,
                              String category, String status, String username) {
        MasterRecord r = MasterRecord.builder()
                .recordCode(code.trim()).name(name.trim()).email(email.trim())
                .phone(phone.trim()).category(category.trim()).statusValue(status.trim())
                .status(RecordStatus.DRAFT).createdBy(username).updatedBy(username)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        applyValidation(r, false);
        repository.save(r);
    }

    private void applyValidation(MasterRecord r, boolean update) {
        List<String> errors = validation.validate(r, update);
        r.setValidationErrors(String.join("; ", errors));
    }
}
