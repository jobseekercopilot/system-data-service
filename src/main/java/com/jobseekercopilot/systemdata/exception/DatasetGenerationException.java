package com.jobseekercopilot.systemdata.exception;

public class DatasetGenerationException extends RuntimeException {
    public DatasetGenerationException(String message) {
        super(message);
    }

    public DatasetGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
