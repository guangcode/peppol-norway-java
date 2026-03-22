package io.github.peppolnorway.spring.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * REST API request body DTO for sending an EHF 3.0 / PEPPOL BIS Billing 3.0 invoice.
 *
 * <p>This DTO is designed for API consumers and follows Norwegian financial system
 * naming conventions. It is converted to the domain model
 * {@link io.github.peppolnorway.model.InvoiceDocument} by
 * {@link io.github.peppolnorway.spring.service.EhfInvoiceService}.</p>
 *
 * <p>Fields marked with * are mandatory according to the PEPPOL specification.</p>
 */
@Data
public class InvoiceRequest {

    // ── PEPPOL routing ────────────────────────────────────────────────────

    /** * Sender's Norwegian organization number (9 digits, no prefix). */
    private String senderOrgNumber;

    /**
     * * Receiver's Norwegian organization number (9 digits, no prefix).
     * Strongly recommended to call the /lookup endpoint first to verify registration.
     */
    private String receiverOrgNumber;

    // ── Invoice header ────────────────────────────────────────────────────

    /** * Unique invoice number assigned by the supplier (BR-02). */
    private String invoiceNo;

    /** * Invoice issue date (BR-03). */
    private LocalDate invoiceDate;

    /** Payment due date. */
    private LocalDate dueDate;

    /** * ISO 4217 currency code (e.g. "NOK", "EUR"). */
    private String currency;

    /**
     * Buyer reference (Kjøperreferanse).
     * Effectively mandatory for Norwegian B2G invoicing — missing this field
     * causes silent rejection by most municipal ERP systems.
     */
    private String buyerReference;

    /** Purchase order number from the buyer. */
    private String orderNo;

    /**
     * Contract or framework agreement reference number.
     * Maps to UBL ContractDocumentReference. Optional.
     */
    private String contractNo;

    /** Free-text note or payment terms at document level. */
    private String note;

    // ── Supplier (AccountingSupplierParty) ────────────────────────────────

    /** * Supplier's Norwegian organization number (9 digits). */
    private String supplierOrgNumber;

    /** * Supplier trading name. */
    private String supplierName;

    /** Supplier's official legal name from Foretaksregisteret. */
    private String supplierLegalName;

    private String supplierAddress;
    private String supplierPostcode;
    private String supplierCity;

    /** ISO 3166-1 alpha-2 country code. Defaults to "NO" when not provided. */
    private String supplierCountryCode;

    private String supplierPhone;
    private String supplierEmail;
    private String supplierContactPerson;

    /**
     * Whether the supplier is registered in the Norwegian VAT (MVA) register.
     * When true, the tax ID is formatted as NO{orgNumber}MVA.
     */
    private boolean supplierVatRegistered;

    // ── Customer (AccountingCustomerParty) ────────────────────────────────

    /** * Customer's Norwegian organization number (9 digits). */
    private String customerOrgNumber;

    /** * Customer name. */
    private String customerName;

    private String customerAddress;
    private String customerPostcode;
    private String customerCity;
    private String customerCountryCode;

    // ── Delivery ──────────────────────────────────────────────────────────

    /** Actual delivery date of the goods or services. */
    private LocalDate deliveryDate;
    private String deliveryAddress;
    private String deliveryPostcode;
    private String deliveryCity;
    private String deliveryCountryCode;

    // ── Payment ───────────────────────────────────────────────────────────

    /**
     * Norwegian KID (Kundeidentifikasjonsnummer) — OCR payment reference.
     * Used for automated bank reconciliation. Maps to UBL PaymentID.
     */
    private String kid;

    /** Domestic Norwegian bank account number (standard 11 digits). */
    private String bankAccountNo;

    /** IBAN for international / cross-border payments. */
    private String iban;

    /** BIC/SWIFT code — required when IBAN is provided. */
    private String bic;

    // ── Monetary totals ───────────────────────────────────────────────────

    /** * Net amount, exclusive of VAT (sum of all line extension amounts). */
    private BigDecimal netAmount;

    /** * Total VAT amount. */
    private BigDecimal vatAmount;

    /** * Total amount inclusive of VAT (= netAmount + vatAmount). */
    private BigDecimal totalAmountInclVat;

    /**
     * Rounding adjustment to reach a round payable amount.
     * Maps to UBL PayableRoundingAmount. May be null or zero.
     */
    private BigDecimal rounding;

    /** * Final payable amount (= totalAmountInclVat + rounding). */
    private BigDecimal payableAmount;

    // ── Attachments ───────────────────────────────────────────────────────

    /**
     * Embedded document attachments (e.g. PDF visual representation of the invoice).
     * Each entry maps to a UBL AdditionalDocumentReference.
     */
    private List<AttachmentRequest> attachments;

    // ── VAT breakdown (BR-CO-17) ──────────────────────────────────────────

    /**
     * VAT subtotals grouped by Norwegian MVA-kode + rate.
     * IMPORTANT: Z, E, and O all have 0% rate but are legally distinct
     * tax treatments and must never be merged into a single subtotal.
     */
    private List<VatLineRequest> vatLines;

    // ── Invoice line items ────────────────────────────────────────────────

    /** Individual invoice lines. At least one line is required. */
    private List<LineRequest> lines;

    // ─────────────────────────────────────────────────────────────────────
    // Nested DTOs
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Document attachment request.
     * Corresponds to UBL AdditionalDocumentReference with embedded binary content.
     */
    @Data
    public static class AttachmentRequest {
        /** Filename including extension, e.g. "invoice-13083.pdf". */
        private String filename;

        /** MIME type, e.g. "application/pdf". */
        private String mimeType;

        /** Human-readable description, e.g. "Commercial invoice". */
        private String description;

        /** Base64-encoded binary content of the attachment. */
        private String base64Content;
    }

    /**
     * VAT subtotal line — corresponds to UBL TaxSubtotal.
     */
    @Data
    public static class VatLineRequest {
        /**
         * Norwegian internal MVA-kode (e.g. "3", "5", "6", "0", "31", "51", "52").
         * The service layer automatically maps this to the PEPPOL UNCL5305 category (S/Z/E/AE/G/O).
         */
        private String vatCode;

        /** VAT rate percentage (e.g. 25.00, 15.00, 0.00). */
        private BigDecimal vatRate;

        /** Net taxable base amount for this category/rate combination. */
        private BigDecimal baseAmount;

        /** Calculated VAT amount for this category/rate combination. */
        private BigDecimal vatAmount;
    }

    /**
     * Invoice line item — corresponds to UBL InvoiceLine.
     */
    @Data
    public static class LineRequest {
        /** Item description or product/service name. */
        private String description;

        /** Seller's internal item code / SKU. */
        private String itemCode;

        /** Invoiced quantity. */
        private BigDecimal quantity;

        /**
         * UN/ECE Rec 20 unit of measure code.
         * Common values: EA (each), HUR (hour), KGM (kilogram), MTR (metre).
         */
        private String unitCode;

        /** Net unit price exclusive of VAT and discount. */
        private BigDecimal unitPrice;

        /**
         * Discount percentage (e.g. 10.00 = 10%). Optional.
         * When provided, {@code discountAmount} must also be set.
         */
        private BigDecimal discountPercent;

        /** Calculated discount amount. Required when discountPercent is set. */
        private BigDecimal discountAmount;

        /**
         * Net line amount = (unitPrice × quantity) − discountAmount.
         * Must satisfy PEPPOL business rule BR-LIN-04.
         */
        private BigDecimal lineAmount;

        /**
         * Norwegian MVA-kode for this line (same codes as VatLineRequest.vatCode).
         * Mapped automatically to the PEPPOL UNCL5305 category in XML output.
         */
        private String vatCode;

        /** VAT rate percentage applicable to this line. */
        private BigDecimal vatRate;
    }
}
