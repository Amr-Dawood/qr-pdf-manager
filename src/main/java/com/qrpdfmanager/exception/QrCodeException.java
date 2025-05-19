package com.qrpdfmanager.exception;

public class QrCodeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public QrCodeException(String message) {
        super(message);
    }

    public QrCodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
