package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.event.ReceiptReadyEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class RentalNotificationService {

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long rentalId) {
        SseEmitter emitter = new SseEmitter(180_000L);

        emitters.computeIfAbsent(rentalId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> {
            CopyOnWriteArrayList<SseEmitter> list = emitters.get(rentalId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    emitters.remove(rentalId, list);
                }
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReceiptReady(ReceiptReadyEvent event) {
        CopyOnWriteArrayList<SseEmitter> targets = emitters.get(event.getRentalId());
        if (targets == null || targets.isEmpty()) return;

        String payload = buildPayload(event);
        List<SseEmitter> dead = new ArrayList<>();

        for (SseEmitter emitter : targets) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("receipt-ready")
                                .id(event.getDocumentType().name())
                                .data(payload)
                );
            } catch (IOException | IllegalStateException ex) {
                dead.add(emitter);
            }
        }
        targets.removeAll(dead);
    }

    private String buildPayload(ReceiptReadyEvent event) {
        return "{\"documentType\":\"%s\",\"receiptNumber\":\"%s\",\"rentalId\":%d}".formatted(
                event.getDocumentType().name(),
                event.getReceiptNumber(),
                event.getRentalId()
        );
    }
}
