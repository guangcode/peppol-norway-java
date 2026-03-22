package io.github.peppolnorway.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * PEPPOL document types supported by this library.
 *
 * <p>Each constant encapsulates all the PEPPOL identifiers required to route and
 * transmit that document type: the document type URN, the process identifier,
 * the SBDH instance type, and the UBL local element name.</p>
 *
 * <p>This enum is the foundation of the <b>Strategy pattern</b> used throughout
 * the transmission pipeline — callers select a DocumentType, and the pipeline
 * automatically uses the correct identifiers without any conditional branching.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // Strategy selection at call site — no if/switch needed downstream
 * transmitter.send(xmlBytes, DocumentType.INVOICE, senderOrg, receiverOrg);
 * transmitter.send(xmlBytes, DocumentType.CREDIT_NOTE, senderOrg, receiverOrg);
 * }</pre>
 */
@Getter
@RequiredArgsConstructor
public enum DocumentType {

    /**
     * PEPPOL BIS Billing 3.0 — Commercial Invoice (UNCL1001: 380).
     * Standard document for B2B and B2G invoicing in Norway.
     */
    INVOICE(
            "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice" +
            "##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0::2.1",
            "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
            "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
            "Invoice",
            "2.1",
            "0192"
    ),

    /**
     * PEPPOL BIS Billing 3.0 — Credit Note (UNCL1001: 381).
     * Used for corrections, returns, and partial refunds.
     */
    CREDIT_NOTE(
            "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2::CreditNote" +
            "##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0::2.1",
            "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
            "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2",
            "CreditNote",
            "2.1",
            "0192"
    ),

    /**
     * PEPPOL End-User Statistics Report v1.1.
     * Access Points must submit this monthly to the PEPPOL Authority (9925 scheme).
     */
    END_USER_STATISTICS_REPORT(
            "urn:fdc:peppol:end-user-statistics-report:1.1::EndUserStatisticsReport" +
            "##urn:fdc:peppol.eu:edec:trns:end-user-statistics-report:1.1::1.1",
            "urn:fdc:peppol.eu:edec:bis:reporting:1.0",
            "urn:fdc:peppol:end-user-statistics-report:1.1",
            "EndUserStatisticsReport",
            "1.1",
            "9925"
    ),

    /**
     * PEPPOL Transaction Statistics Report v1.0.
     * Access Points must submit this monthly to the PEPPOL Authority (9925 scheme).
     */
    TRANSACTION_STATISTICS_REPORT(
            "urn:fdc:peppol:transaction-statistics-report:1.0::TransactionStatisticsReport" +
            "##urn:fdc:peppol.eu:edec:trns:transaction-statistics-reporting:1.0::1.0",
            "urn:fdc:peppol.eu:edec:bis:reporting:1.0",
            "urn:fdc:peppol:transaction-statistics-report:1.0",
            "TransactionStatisticsReport",
            "1.0",
            "9925"
    );

    /** Full PEPPOL document type identifier URN (used for SMP lookup and SBDH). */
    private final String docTypeId;

    /** PEPPOL process identifier URN. */
    private final String processId;

    /** SBDH instance type namespace URI. */
    private final String instanceTypeId;

    /** UBL document local element name (e.g. "Invoice", "CreditNote"). */
    private final String localName;

    /** UBL version (e.g. "2.1", "1.1"). */
    private final String version;

    /**
     * ICD (International Code Designator) for the receiver.
     * "0192" for Norwegian org numbers, "9925" for PEPPOL Authority VAT IDs.
     */
    private final String receiverIcd;

    /** Returns true if this document type uses Norwegian org numbers for routing. */
    public boolean isNorwegianOrgRouting() {
        return "0192".equals(receiverIcd);
    }
}
