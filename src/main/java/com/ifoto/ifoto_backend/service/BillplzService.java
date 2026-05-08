package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.config.BillplzConfig;
import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillplzService {

    private static final DateTimeFormatter BILLPLZ_DATE = DateTimeFormatter.ofPattern("yyyy-M-d");

    private final RestClient billplzRestClient;
    private final BillplzConfig billplzConfig;

    public BillplzBillResponse createBill(EquipmentRental rental, User renter, long amount) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("collection_id", billplzConfig.getCollectionId());
        body.add("name", renter.getFullName() != null ? renter.getFullName() : renter.getUsername());
        body.add("email", renter.getEmail());
        if (renter.getPhoneNumber() != null)
            body.add("mobile", renter.getPhoneNumber());
        body.add("amount", String.valueOf(amount));
        body.add("callback_url", billplzConfig.getCallbackUrl());
        body.add("redirect_url", billplzConfig.getRedirectUrl() + "?rentalNumber=" + rental.getRentalNumber());
        body.add("description", rental.getRentalNumber());

        try {
            Map<?, ?> response = billplzRestClient.post()
                    .uri("/bills")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from Billplz");

            String billId = (String) response.get("id");
            String billUrl = (String) response.get("url");
            LocalDate dueAt = response.get("due_at") != null
                    ? LocalDate.parse((String) response.get("due_at"), BILLPLZ_DATE)
                    : null;

            return new BillplzBillResponse(billId, billUrl, dueAt);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Billplz createBill failed for rental {}: {}", rental.getRentalNumber(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to create Billplz bill: " + e.getMessage());
        }
    }

    public record BillplzBillResponse(String billId, String billUrl, LocalDate dueAt) {
    }
}
