package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.RentalPricingDTO.RentalPricingBulkUpdateRequest;
import com.ifoto.ifoto_backend.dto.RentalPricingDTO.RentalPricingResponse;
import com.ifoto.ifoto_backend.service.RentalPricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rental-pricing")
@RequiredArgsConstructor
public class RentalPricingController {

    private final RentalPricingService service;

    @GetMapping
    public ResponseEntity<List<RentalPricingResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PutMapping("/bulk")
    public ResponseEntity<List<RentalPricingResponse>> bulkUpdate(
            @Valid @RequestBody RentalPricingBulkUpdateRequest request) {
        return ResponseEntity.ok(service.bulkUpdate(request));
    }
}
