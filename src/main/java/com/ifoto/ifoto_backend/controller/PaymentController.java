package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.service.PaymentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // Billplz POSTs here after payment — must always return HTTP 200
    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam Map<String, String> params) {
        try {
            paymentService.handleBillplzCallback(params);
        } catch (Exception e) {
            log.error("Billplz callback processing error", e);
        }
        return ResponseEntity.ok().build();
    }

    // Browser redirect from Billplz — best-effort, not source of truth
    @GetMapping("/result")
    public void result(@RequestParam Map<String, String> params,
            HttpServletResponse response) throws IOException {
        String redirectUrl = paymentService.handleBillplzRedirect(params);
        response.sendRedirect(redirectUrl);
    }
}
