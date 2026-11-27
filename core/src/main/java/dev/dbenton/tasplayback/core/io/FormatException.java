package dev.dbenton.tasplayback.core.io;

/** Thrown when stored data is truncated, corrupt or of an unsupported version. */
public class FormatException extends RuntimeException {
    public FormatException(String message) { super(message); }
    public FormatException(String message, Throwable cause) { super(message, cause); }
}
