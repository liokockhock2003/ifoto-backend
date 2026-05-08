package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.config.BillplzConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillplzXSignatureService {

    private final BillplzConfig billplzConfig;

    public boolean verify(Map<String, String> params, String receivedSignature) {
        try {
            List<String> elements = new ArrayList<>();
            params.forEach((k, v) -> {
                if (!k.equalsIgnoreCase("x_signature")) {
                    elements.add(k + (v == null ? "" : v));
                }
            });
            elements.sort(String.CASE_INSENSITIVE_ORDER);
            String source = String.join("|", elements);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    billplzConfig.getXSignatureKey().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] hash = mac.doFinal(source.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);

            String key = billplzConfig.getXSignatureKey();
            log.info("XSig key length={}, first20='{}', last20='{}'",
                    key.length(),
                    key.substring(0, Math.min(20, key.length())),
                    key.substring(Math.max(0, key.length() - 20)));
            log.info("XSig string-to-sign: '{}'", source);
            log.info("XSig computed='{}', received='{}'", computed, receivedSignature);
            return computed.equalsIgnoreCase(receivedSignature);
        } catch (Exception e) {
            log.error("XSig verify error", e);
            return false;
        }
    }
}
