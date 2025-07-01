package com.hit.server;

import java.util.Map;

/**
 * Generic network-level API response to be sent from server to client.
 * Wraps both metadata (headers) and the actual data (body),
 * which can be an ApiResponse object or another payload.
 * Designed for JSON serialization (Gson friendly).
 */
public class Response {
    /** Response headers (e.g. "status", "action", ...). */
    private Map<String, String> headers;

    /** Response body (payload) – usually ApiResponse or error info. */
    private Object body;

    /** Default constructor (for Gson). */
    public Response() {}

    /**
     * Full constructor.
     * @param headers Response headers.
     * @param body    Response payload (can be ApiResponse or error map).
     */
    public Response(Map<String, String> headers, Object body) {
        this.headers = headers;
        this.body = body;
    }

    /** @return Response headers (never null in normal flow). */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /** @param headers Set the response headers. */
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    /** @return Response payload (could be ApiResponse, List, etc). */
    public Object getBody() {
        return body;
    }

    /** @param body Set the response body. */
    public void setBody(Object body) {
        this.body = body;
    }

    // --- Static factory helpers for convenience ---

    /**
     * Build a successful Response, wrapping any payload (commonly an ApiResponse).
     * @param body Payload (usually ApiResponse<?>)
     * @return Response object with status "ok"
     */
    public static Response ok(Object body) {
        return build("ok", body, null);
    }

    /**
     * Build an error Response, with an error message in the body.
     * @param message Error message.
     * @return Response object with status "error".
     */
    public static Response error(String message) {
        return build("error", null, message);
    }

    /**
     * Internal helper to build a response.
     * @param status "ok" or "error"
     * @param body   Payload, if any (can be ApiResponse)
     * @param errorMsg Error message (if any)
     * @return Response
     */
    private static Response build(String status, Object body, String errorMsg) {
        Response resp = new Response();
        resp.headers = Map.of("status", status);
        resp.body = (errorMsg == null) ? body : Map.of("error", errorMsg);
        return resp;
    }
}
