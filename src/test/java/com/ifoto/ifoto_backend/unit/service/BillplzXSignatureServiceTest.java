package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.config.BillplzConfig;
import com.ifoto.ifoto_backend.service.BillplzXSignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillplzXSignatureServiceTest {

    @Mock
    private BillplzConfig billplzConfig;

    @InjectMocks
    private BillplzXSignatureService service;

    private static final String KEY = "test-x-signature-key-32-chars-ok!";

    @BeforeEach
    void setUp() {
        when(billplzConfig.getXSignatureKey()).thenReturn(KEY);
    }

    @Test
    void verify_correctSignature_returnsTrue() throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("x_id", "bill123");
        params.put("x_paid", "true");
        params.put("x_amount", "5000");

        String expectedSig = computeHmac(params, KEY);
        assertTrue(service.verify(params, expectedSig));
    }

    @Test
    void verify_wrongSignature_returnsFalse() {
        Map<String, String> params = Map.of("x_id", "bill123", "x_paid", "true");
        assertFalse(service.verify(params, "totally-wrong-signature"));
    }

    @Test
    void verify_tamperedParam_returnsFalse() throws Exception {
        Map<String, String> original = new LinkedHashMap<>();
        original.put("x_id", "bill123");
        original.put("x_paid", "true");
        String sig = computeHmac(original, KEY);

        Map<String, String> tampered = new LinkedHashMap<>();
        tampered.put("x_id", "TAMPERED");
        tampered.put("x_paid", "true");

        assertFalse(service.verify(tampered, sig));
    }

    @Test
    void verify_xSignatureKeyExcludedFromHash() throws Exception {
        // x_signature param in the map must be excluded from hash computation
        Map<String, String> withSigField = new LinkedHashMap<>();
        withSigField.put("x_id", "bill123");
        withSigField.put("x_signature", "should-be-ignored");

        Map<String, String> withoutSigField = new LinkedHashMap<>();
        withoutSigField.put("x_id", "bill123");

        String expectedSig = computeHmac(withoutSigField, KEY);
        assertTrue(service.verify(withSigField, expectedSig));
    }

    @Test
    void verify_caseInsensitiveSignatureComparison_returnsTrue() throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("x_id", "bill123");
        String sig = computeHmac(params, KEY).toUpperCase();
        assertTrue(service.verify(params, sig));
    }

    private String computeHmac(Map<String, String> params, String key) throws Exception {
        List<String> elements = new ArrayList<>();
        params.forEach((k, v) -> {
            if (!k.equalsIgnoreCase("x_signature")) {
                elements.add(k + (v == null ? "" : v));
            }
        });
        elements.sort(String.CASE_INSENSITIVE_ORDER);
        String source = String.join("|", elements);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(source.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
