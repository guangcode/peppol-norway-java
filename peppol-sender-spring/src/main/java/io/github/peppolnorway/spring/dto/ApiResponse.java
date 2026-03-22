package io.github.peppolnorway.spring.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Unified API response wrapper for all endpoints in this service.
 *
 * <p>All REST endpoints return this structure, making it straightforward
 * for API consumers to handle both success and error responses with
 * a consistent format.</p>
 *
 * @param <T> the type of the business data payload
 */
@Getter
@Builder
public class ApiResponse<T> {

    /** Whether the operation succeeded. */
    private final boolean success;

    /**
     * Result code: "SUCCESS" on success, or an error code such as
     * "VALIDATION_FAILED", "RECEIVER_NOT_REGISTERED", "INTERNAL_ERROR".
     */
    private final String code;

    /** Human-readable result message. */
    private final String message;

    /** Business data payload. Null on failure. */
    private final T data;

    /** UTC timestamp when this response was created. */
    @Builder.Default
    private final Instant timestamp = Instant.now();

    // ── Static factory methods ────────────────────────────────────────────

    /**
     * Creates a successful response with a message and data payload.
     *
     * @param message human-readable success message
     * @param data    business data to include in the response
     * @param <T>     payload type
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true).code("SUCCESS").message(message).data(data).build();
    }

    /**
     * Creates a failure response with an error code and message.
     *
     * @param code    machine-readable error code (e.g. "VALIDATION_FAILED")
     * @param message human-readable error description
     * @param <T>     payload type (no data is included on failure)
     */
    public static <T> ApiResponse<T> fail(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false).code(code).message(message).build();
    }

    // ── Nested payload types ──────────────────────────────────────────────

    /**
     * Payload for successful invoice/credit note send operations.
     */
    @Getter
    @Builder
    public static class SendData {
        /** PEPPOL message ID assigned to this transmission. */
        private final String messageId;

        /** Receiver's Norwegian organization number. */
        private final String receiverOrg;

        /** Transmission status name (e.g. "SUCCESS"). */
        private final String status;
    }

    /**
     * Payload for PEPPOL SMP receiver lookup operations.
     */
    @Getter
    @Builder
    public static class LookupData {
        /** The Norwegian organization number that was queried. */
        private final String orgNumber;

        /** Whether the organization is registered in the PEPPOL network. */
        private final boolean registered;

        /** Human-readable description of the lookup result. */
        private final String detail;
    }
}
