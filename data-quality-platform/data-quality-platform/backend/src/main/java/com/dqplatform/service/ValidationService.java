package com.dqplatform.service;

import com.dqplatform.entity.MasterRecord;
import com.dqplatform.repository.MasterRecordRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class ValidationService {
    private static final Pattern PHONE = Pattern.compile("^[0-9+() -]{7,20}$");
    private final MasterRecordRepository repository;

    public ValidationService(MasterRecordRepository repository) {
        this.repository = repository;
    }

    public List<String> validate(MasterRecord r, boolean update) {
        List<String> errors = new ArrayList<>();

        if (r.getRecordCode() == null || r.getRecordCode().isBlank())
            errors.add("Record code is mandatory");
        else if (update ? repository.existsByRecordCodeIgnoreCaseAndIdNot(r.getRecordCode(), r.getId())
                        : repository.existsByRecordCodeIgnoreCase(r.getRecordCode()))
            errors.add("Duplicate record code");

        if (r.getName() == null || r.getName().isBlank())
            errors.add("Name is mandatory");

        if (r.getCategory() == null || r.getCategory().isBlank())
            errors.add("Category is mandatory");

        if (r.getStatusValue() == null ||
                !Set.of("ACTIVE", "INACTIVE", "BLOCKED").contains(r.getStatusValue().toUpperCase()))
            errors.add("Status must be ACTIVE, INACTIVE or BLOCKED");

        if (r.getEmail() != null && !r.getEmail().isBlank() &&
                !r.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"))
            errors.add("Invalid email");

        if (r.getPhone() != null && !r.getPhone().isBlank() &&
                !PHONE.matcher(r.getPhone()).matches())
            errors.add("Invalid phone");

        return errors;
    }
}
