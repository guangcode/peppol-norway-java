package io.github.peppolnorway.spring.dto;

import lombok.Getter;
import network.oxalis.ng.api.outbound.TransmissionResponse;

/**
 * Immutable result of a PEPPOL transmission pipeline execution.
 *
 * <p>Uses static factory methods (Factory Method pattern) to construct
 * different result types, making the intent at each call site clear
 * and avoiding boolean flag abuse.</p>
 *
 * <h3>Result states</h3>
 * <ul>
 *   <li>{@link Status#SUCCESS} — document accepted by receiver's AS4 endpoint</li>
 *   <li>{@link Status#RECEIVER_NOT_REGISTERED} — receiver not found in PEPPOL SMP</li>
 *   <li>{@link Status#TRANSMISSION_FAILED} — AS4 or network-level transmission error</li>
 *   <li>{@link Status#CONTENT_FAILED} — PEPPOL content validation error at the gateway</li>
 * </ul>
 */
@Getter
public final class SendResult {

    /**
     * Possible outcomes of a PEPPOL send operation.
     */
    public enum Status {
        /** Document successfully delivered to the receiver's PEPPOL access point. */
        SUCCESS,

        /** Receiver's organization number is not registered in the PEPPOL network. */
        RECEIVER_NOT_REGISTERED,

        /** AS4 transmission failed due to a network or protocol error. */
        TRANSMISSION_FAILED,

        /** PEPPOL content validation failed at the gateway level. */
        CONTENT_FAILED
    }

    private final Status status;
    private final String messageId;
    private final String errorDetail;
    private final TransmissionResponse transmissionResponse;

    private SendResult(Status status, String messageId, String errorDetail,
                       TransmissionResponse transmissionResponse) {
        this.status = status;
        this.messageId = messageId;
        this.errorDetail = errorDetail;
        this.transmissionResponse = transmissionResponse;
    }

    // ── Static factory methods ────────────────────────────────────────────

    /**
     * Creates a successful result with the assigned PEPPOL message ID
     * and the raw transmission response from Oxalis.
     */
    public static SendResult success(String messageId, TransmissionResponse response) {
        return new SendResult(Status.SUCCESS, messageId, null, response);
    }

    /**
     * Creates a result indicating the receiver organization is not registered
     * in the PEPPOL network and therefore cannot receive electronic invoices.
     *
     * @param receiverOrg the Norwegian organization number that was not found in SMP
     */
    public static SendResult receiverNotRegistered(String receiverOrg) {
        return new SendResult(Status.RECEIVER_NOT_REGISTERED, null,
                "Receiver organization " + receiverOrg
                        + " is not registered in the PEPPOL network.", null);
    }

    /**
     * Creates a result for an AS4 or network-level transmission failure.
     *
     * @param messageId the PEPPOL message ID (may be null if failure occurred before assignment)
     * @param cause     description of the transmission error
     */
    public static SendResult transmissionFailed(String messageId, String cause) {
        return new SendResult(Status.TRANSMISSION_FAILED, messageId,
                "AS4 transmission failed: " + cause, null);
    }

    /**
     * Creates a result for a PEPPOL content validation failure at the gateway.
     *
     * @param messageId the PEPPOL message ID
     * @param cause     description of the content validation error
     */
    public static SendResult contentFailed(String messageId, String cause) {
        return new SendResult(Status.CONTENT_FAILED, messageId,
                "PEPPOL content validation failed: " + cause, null);
    }

    /** Returns true if the document was successfully transmitted. */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    @Override
    public String toString() {
        return "SendResult{status=" + status + ", messageId='" + messageId + '\''
                + (errorDetail != null ? ", error='" + errorDetail + '\'' : "") + '}';
    }
}
