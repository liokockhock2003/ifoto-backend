package com.ifoto.ifoto_backend.model.enumerator;

public enum DocumentType {
    INVOICE("I"),
    RECEIPT("R"),
    OVERDUE_INVOICE("PI"),
    OVERDUE_RECEIPT("PR");

    private final String prefix;

    DocumentType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
