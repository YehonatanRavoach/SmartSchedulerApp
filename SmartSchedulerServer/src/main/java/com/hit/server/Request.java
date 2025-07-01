package com.hit.server;

import java.util.Map;

/**
 * Represents a generic API request sent from the client to the server.
 * Includes a header map (for action, metadata, etc.) and a body map (for parameters or objects).
 * Designed for compatibility with JSON serialization/deserialization via Gson.
 */
public class Request {
    /** Header fields such as "action", "client", etc. */
    private Map<String, String> headers;

    /** Request payload, typically parameters or object data. */
    private Map<String, Object> body;

    /** Default constructor (for Gson) */
    public Request() {}

    /**
     * Full constructor.
     * @param headers Request headers (may be null).
     * @param body    Request body/payload (may be null).
     */
    public Request(Map<String, String> headers, Map<String, Object> body) {
        this.headers = headers;
        this.body = body;
    }

    /** @return Request headers (may be null). */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /** @param headers Set the request headers. */
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    /** @return Request body (may be null). */
    public Map<String, Object> getBody() {
        return body;
    }

    /** @param body Set the request body. */
    public void setBody(Map<String, Object> body) {
        this.body = body;
    }
}
