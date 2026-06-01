package com.ifoto.ifoto_backend.event;

import com.ifoto.ifoto_backend.model.enumerator.DocumentType;
import org.springframework.context.ApplicationEvent;

public class ReceiptReadyEvent extends ApplicationEvent {

    private final Long rentalId;
    private final DocumentType documentType;
    private final String receiptNumber;

    public ReceiptReadyEvent(Object source, Long rentalId, DocumentType documentType, String receiptNumber) {
        super(source);
        this.rentalId = rentalId;
        this.documentType = documentType;
        this.receiptNumber = receiptNumber;
    }

    public Long getRentalId() { return rentalId; }
    public DocumentType getDocumentType() { return documentType; }
    public String getReceiptNumber() { return receiptNumber; }
}
