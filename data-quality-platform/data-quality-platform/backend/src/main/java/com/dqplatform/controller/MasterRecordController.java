package com.dqplatform.controller;

import com.dqplatform.dto.MasterRecordRequest;
import com.dqplatform.entity.MasterRecord;
import com.dqplatform.service.MasterRecordService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/records")
public class MasterRecordController {
    private final MasterRecordService service;

    public MasterRecordController(MasterRecordService service) {
        this.service = service;
    }

    @GetMapping
    public Page<MasterRecord> search(@RequestParam(required=false) String q,
                                     @RequestParam(defaultValue="0") int page,
                                     @RequestParam(defaultValue="10") int size) {
        return service.search(q, page, size);
    }

    @GetMapping("/{id}")
    public MasterRecord get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DATA_ENTRY')")
    public MasterRecord create(@Valid @RequestBody MasterRecordRequest request,
                               Authentication auth) {
        return service.create(request, auth.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DATA_ENTRY')")
    public MasterRecord update(@PathVariable Long id,
                               @Valid @RequestBody MasterRecordRequest request,
                               Authentication auth) {
        return service.update(id, request, auth.getName());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DATA_ENTRY')")
    public void delete(@PathVariable Long id, Authentication auth) {
        service.delete(id, auth.getName());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','DATA_ENTRY')")
    public MasterRecord submit(@PathVariable Long id, Authentication auth) {
        return service.submit(id, auth.getName());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','APPROVER')")
    public MasterRecord approve(@PathVariable Long id, Authentication auth) {
        return service.approve(id, auth.getName());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','APPROVER')")
    public MasterRecord reject(@PathVariable Long id, Authentication auth) {
        return service.reject(id, auth.getName());
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','DATA_ENTRY')")
    public String upload(@RequestParam("file") MultipartFile file, Authentication auth)
            throws Exception {
        return "{\"uploaded\":" + service.upload(file, auth.getName()) + "}";
    }
}
