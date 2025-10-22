package dev.pekelund.pklnd.receiptparser.legacy;

public record LegacyReceiptError(int lineNumber, String content, String message) { }
