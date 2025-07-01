package com.hit.controller;

/**
 * Generic API Response wrapper for all server–client communications.
 * Encapsulates success status, messages, data, and an optional status code.
 *
 * @param <T> The type of the returned data (payload).
 */
public class ApiResponse<T> {
    private final boolean success;
    private final String message;
    private final T data;
    private final int statusCode;

    /**
     * Standard constructor.
     */
    public ApiResponse(boolean success, String message, T data, int statusCode) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.statusCode = statusCode;
    }

    /**
     * Minimal constructor (statusCode defaults to 0).
     */
    public ApiResponse(boolean success, String message, T data) {
        this(success, message, data, 0);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public int getStatusCode() { return statusCode; }

    /**
     * Build a success response with data and optional message.
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, 200);
    }

    /**
     * Build a success response with data (no message).
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, 200);
    }

    /**
     * Build an error response with a message and status code.
     */
    public static <T> ApiResponse<T> error(String message, int statusCode) {
        return new ApiResponse<>(false, message, null, statusCode);
    }

    /**
     * Build an error response with a message (default code 400).
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, 400);
    }
}
