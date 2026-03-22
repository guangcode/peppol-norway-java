package io.github.peppolnorway.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable domain model representing a PEPPOL BIS Billing 3.0 invoice or credit note.
 *
 * <p>Constructed exclusively via the nested {@link Builder}, which enforces
 * the Builder pattern and ensures the object is always in a valid, fully
 * populated state. All required fields are validated at build time by
 * {@link io.github.peppolnorway.validator.InvoiceValidator}.</p>
 *
 * <p>This model is deliberately decoupled from the XML representation — it
 * is the input to the {@code UblXmlWriter}, not the XML itself.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * InvoiceDocument invoice = InvoiceDocument.builder()
 *     .invoiceNo("INV-2024-001")
 *     .invoiceDate(LocalDate.now())
 *     .currency("NOK")
 *     .supplier(supplier)
 *     .customer(customer)
 *     .vatLine(vatLine)
 *     .line(invoiceLine)
 *     .netAmount(new BigDecimal("1000.00"))
 *     .vatAmount(new BigDecimal("250.00"))
 *     .totalAmountInclVat(new BigDecimal("1250.00"))
 *     .payableAmount(new BigDecimal("1250.00"))
 *     .build();
 * }</pre>
 */
@Getter
@Builder(toBuilder = true)
public class InvoiceDocument {

    // ── Document Header ───────────────────────────────────────────────────

    /** Invoice number (BR-02). Required. */
    private final String invoiceNo;

    /** Invoice issue date (BR-03). Required. */
    private final LocalDate invoiceDate;

    /** Payment due date. Optional. */
    private final LocalDate dueDate;

    /** ISO 4217 currency code (e.g. "NOK", "EUR"). Required. */
    private final String currency;

    /**
     * Buyer reference — effectively mandatory for Norwegian B2G invoicing.
     * Missing this field causes silent rejection by most municipal ERP systems.
     */
    private final String buyerReference;

    /** Purchase order reference number from the buyer. Optional. */
    private final String orderNo;

    /**
     * Contract or framework agreement reference number.
     * Maps to UBL {@code <cac:ContractDocumentReference>/<cbc:ID>}. Optional.
     */
    private final String contractNo;

    /** Free-text note or payment terms at document level. Optional. */
    private final String note;

    /**
     * UNCL1001 invoice type code.
     * 380 = Commercial Invoice (default), 381 = Credit Note.
     */
    @Builder.Default
    private final String invoiceTypeCode = "380";

    // ── Attachments ───────────────────────────────────────────────────────

    /**
     * Embedded document attachments, typically the visual PDF representation
     * of the invoice. Maps to UBL {@code <cac:AdditionalDocumentReference>}.
     */
    @Singular
    private final List<Attachment> attachments;

    // ── Parties ───────────────────────────────────────────────────────────

    /** Supplier / AccountingSupplierParty. Required. */
    private final Party supplier;

    /** Customer / AccountingCustomerParty. Required. */
    private final Party customer;

    // ── Delivery ──────────────────────────────────────────────────────────

    /** Actual delivery date of goods or services. Optional. */
    private final LocalDate deliveryDate;

    /**
     * Delivery location address.
     * Maps to UBL {@code <cac:Delivery>/<cac:DeliveryLocation>/<cac:Address>}. Optional.
     */
    private final Address deliveryAddress;

    // ── Payment ───────────────────────────────────────────────────────────

    /**
     * Norwegian KID (Kundeidentifikasjonsnummer) for OCR bank reconciliation.
     * Essential for automated payment matching in Nordic banking. Maps to UBL PaymentID.
     */
    private final String kid;

    /** Domestic Norwegian bank account number (11 digits). */
    private final String bankAccountNo;

    /** IBAN for cross-border / international payments. */
    private final String iban;

    /** BIC/SWIFT code — required when IBAN is provided. */
    private final String bic;

    // ── Monetary Totals ───────────────────────────────────────────────────

    /** Net amount — sum of all line extension amounts, exclusive of VAT. Required. */
    private final BigDecimal netAmount;

    /** Total VAT amount across all VAT breakdown lines. Required. */
    private final BigDecimal vatAmount;

    /** Total amount inclusive of VAT (= netAmount + vatAmount). Required. */
    private final BigDecimal totalAmountInclVat;

    /**
     * Rounding adjustment applied to arrive at a round payable amount.
     * Maps to UBL {@code <cbc:PayableRoundingAmount>}. May be null or zero.
     */
    private final BigDecimal rounding;

    /** Final payable amount = totalAmountInclVat + rounding. Required. */
    private final BigDecimal payableAmount;

    // ── VAT Breakdown (BR-CO-17) ──────────────────────────────────────────

    /**
     * VAT subtotals grouped by Norwegian MVA-kode + rate combination.
     * <p>
     * CRITICAL (BR-CO-17): Z, E, and O all produce 0.00% rate in XML but represent
     * legally distinct tax treatments and MUST NEVER be merged into a single subtotal.
     * </p>
     */
    @Singular
    private final List<VatBreakdown> vatLines;

    // ── Invoice Lines ─────────────────────────────────────────────────────

    /** Individual invoice lines. Required — at least one line must be present. */
    @Singular("line")
    private final List<InvoiceLine> lines;

    // ─────────────────────────────────────────────────────────────────────
    // Nested domain objects
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Embedded document attachment (e.g. visual PDF representation of the invoice).
     * Maps to UBL {@code <cac:AdditionalDocumentReference>}.
     */
    @Getter
    @Builder
    public static class Attachment {
        /** Filename including extension, e.g. "13083.pdf". Required. */
        private final String filename;

        /** MIME type of the attachment, e.g. "application/pdf". */
        private final String mimeType;

        /** Human-readable description, e.g. "Commercial invoice". Optional. */
        private final String description;

        /**
         * Base64-encoded binary content of the attachment.
         * Maps to UBL {@code <cbc:EmbeddedDocumentBinaryObject>}.
         */
        private final String base64Content;
    }

    /**
     * Trading party — used for both supplier (AccountingSupplierParty)
     * and customer (AccountingCustomerParty).
     */
    @Getter
    @Builder
    public static class Party {
        /** Norwegian organization number (9 digits, Enhetsregisteret). Required. */
        private final String orgNumber;

        /** Trading / display name. Required. */
        private final String name;

        /**
         * Official legal name registered in Foretaksregisteret.
         * Falls back to {@code name} if null.
         */
        private final String legalName;

        /** Postal address. Optional. */
        private final Address address;

        /**
         * Whether this party is registered in the Norwegian VAT (MVA) register.
         * When true, the supplier tax ID is formatted as NO{orgNumber}MVA.
         */
        @Builder.Default
        private final boolean vatRegistered = false;

        /** Telephone number of the contact person. Optional. */
        private final String phone;

        /** Email address of the contact person. Optional. */
        private final String email;

        /** Full name of the contact person. Optional. */
        private final String contactPerson;
    }

    /**
     * Postal address — used for supplier, customer, and delivery location.
     */
    @Getter
    @Builder
    public static class Address {
        /** Street name and building number. */
        private final String street;

        /** Postal code / ZIP code. */
        private final String postcode;

        /** City or municipality name. */
        private final String city;

        /**
         * ISO 3166-1 alpha-2 country code.
         * Defaults to "NO" (Norway) if not specified.
         */
        @Builder.Default
        private final String countryCode = "NO";
    }

    /**
     * VAT subtotal entry — maps to UBL {@code <cac:TaxSubtotal>}.
     *
     * <p>Each instance represents one distinct (category, rate) combination.
     * See BR-CO-17: subtotals MUST be grouped by the combination of
     * UNCL5305 category code AND VAT rate. Never merge two different categories
     * even if both have a 0.00% rate (e.g. Z and E are legally distinct).</p>
     */
    @Getter
    @Builder
    public static class VatBreakdown {
        /**
         * Norwegian internal MVA-kode (e.g. "3", "5", "6", "0", "31", "51", "52").
         * The XML writer maps this automatically to the correct PEPPOL UNCL5305 category.
         */
        private final String norwegianVatCode;

        /** VAT rate percentage (e.g. 25.00, 15.00, 0.00). */
        private final BigDecimal vatRate;

        /** Net taxable amount for this category/rate combination. */
        private final BigDecimal baseAmount;

        /** Calculated VAT amount for this category/rate combination. */
        private final BigDecimal vatAmount;
    }

    /**
     * Single invoice line — maps to UBL {@code <cac:InvoiceLine>}.
     */
    @Getter
    @Builder
    public static class InvoiceLine {
        /** Item description / product name. Required. */
        private final String description;

        /** Seller's internal item code / SKU. Optional. */
        private final String itemCode;

        /** Invoiced quantity. Required. */
        private final BigDecimal quantity;

        /**
         * UN/ECE Rec 20 unit code. Required.
         * Common values: "EA" (each), "HUR" (hour), "KGM" (kilogram), "MTR" (metre).
         */
        private final String unitCode;

        /** Net unit price exclusive of VAT and discount. Required. */
        private final BigDecimal unitPrice;

        /**
         * Discount percentage applied to the base price (e.g. 10.00 = 10%). Optional.
         * Maps to UBL {@code <cac:AllowanceCharge>/<cbc:MultiplierFactorNumeric>}.
         * When set, {@code discountAmount} must also be provided.
         */
        private final BigDecimal discountPercent;

        /**
         * Calculated discount amount = unitPrice * quantity * (discountPercent / 100).
         * Maps to UBL {@code <cac:AllowanceCharge>/<cbc:Amount>}.
         * Required when {@code discountPercent} is set.
         */
        private final BigDecimal discountAmount;

        /**
         * Net line amount = (unitPrice × quantity) − discountAmount.
         * Must satisfy PEPPOL business rule BR-LIN-04. Required.
         */
        private final BigDecimal lineAmount;

        /**
         * Norwegian MVA-kode for this line (e.g. "3", "5", "6", "31").
         * Mapped automatically to the PEPPOL UNCL5305 category in XML output.
         */
        private final String norwegianVatCode;

        /** VAT rate percentage applicable to this line (e.g. 25.00, 15.00, 0.00). */
        private final BigDecimal vatRate;
    }
}
