package com.stockflow.exception;
import org.springframework.http.HttpStatus;
public class DomainException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public DomainException(HttpStatus status, String code, String message) { super(message); this.status = status; this.code = code; }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public static DomainException notFound(String resource, Long id) { return new DomainException(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " " + id + " was not found"); }
    public static DomainException conflict(String code, String message) { return new DomainException(HttpStatus.CONFLICT, code, message); }
    public static DomainException invalidStock(String message) { return new DomainException(HttpStatus.BAD_REQUEST, "INVALID_STOCK_ADJUSTMENT", message); }
}

