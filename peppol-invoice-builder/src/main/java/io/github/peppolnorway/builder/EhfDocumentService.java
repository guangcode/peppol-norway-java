package io.github.peppolnorway.builder;

import io.github.peppolnorway.model.InvoiceDocument;
import io.github.peppolnorway.validator.InvoiceValidator;

/**
 * Main entry point for the {@code peppol-invoice-builder} module.
 *
 * <p>Implements the <b>Facade pattern</b> — provides a simple, high-level API
 * over the validator, XML writers, and document model. Callers interact only
 * with this class; they do not need to know about {@link UblXmlWriter},
 * {@link InvoiceXmlWriter}, or {@link InvoiceValidator} directly.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Build the document model
 * InvoiceDocument invoice = InvoiceDocument.builder()
 *     .invoiceNo("INV-2024-001")
 *     .invoiceDate(LocalDate.now())
 *     .currency("NOK")
 *     .supplier(Party.builder().orgNumber("336880697").name("Supplier AS")
 *                 .vatRegistered(true).build())
 *     .customer(Party.builder().orgNumber("881086591").name("Buyer Kommune").build())
 *     .vatLine(VatBreakdown.builder()
 *                 .norwegianVatCode("3").vatRate(new BigDecimal("25"))
 *                 .baseAmount(new BigDecimal("1000")).vatAmount(new BigDecimal("250")).build())
 *     .line(InvoiceLine.builder()
 *                 .description("Consulting").quantity(BigDecimal.TEN)
 *                 .unitCode("HUR").unitPrice(new BigDecimal("100"))
 *                 .lineAmount(new BigDecimal("1000"))
 *                 .norwegianVatCode("3").vatRate(new BigDecimal("25")).build())
 *     .netAmount(new BigDecimal("1000")).vatAmount(new BigDecimal("250"))
 *     .totalAmountInclVat(new BigDecimal("1250")).payableAmount(new BigDecimal("1250"))
 *     .build();
 *
 * EhfDocumentService service = new EhfDocumentService();
 * byte[] xml = service.buildInvoice(invoice);   // validates + generates XML
 * }</pre>
 */
public class EhfDocumentService {

    private final InvoiceValidator validator;
    private final InvoiceXmlWriter invoiceWriter;
    private final CreditNoteXmlWriter creditNoteWriter;

    /**
     * Creates a service with the standard PEPPOL BIS Billing 3.0 validator.
     */
    public EhfDocumentService() {
        this(InvoiceValidator.standard());
    }

    /**
     * Creates a service with a custom validator.
     * Use this when you need additional business rules beyond the PEPPOL standard.
     *
     * @param validator custom validator, typically built as:
     *                  {@code InvoiceValidator.standard().withRule(...)}
     */
    public EhfDocumentService(InvoiceValidator validator) {
        this.validator = validator;
        this.invoiceWriter = new InvoiceXmlWriter();
        this.creditNoteWriter = new CreditNoteXmlWriter();
    }

    /**
     * Validates and generates a UBL 2.1 Invoice XML document.
     *
     * @param document the invoice document model
     * @return UTF-8 XML bytes compliant with PEPPOL BIS Billing 3.0 and EHF 3.0
     * @throws io.github.peppolnorway.exception.InvoiceValidationException if validation fails
     * @throws UblXmlWriter.UblWriterException if XML generation fails
     */
    public byte[] buildInvoice(InvoiceDocument document) {
        validator.validate(document);
        return invoiceWriter.write(document);
    }

    /**
     * Validates and generates a UBL 2.1 Credit Note XML document.
     *
     * @param document the credit note document model (same structure as invoice,
     *                 the type code 381 is applied automatically)
     * @return UTF-8 XML bytes compliant with PEPPOL BIS Billing 3.0 and EHF 3.0
     * @throws io.github.peppolnorway.exception.InvoiceValidationException if validation fails
     * @throws UblXmlWriter.UblWriterException if XML generation fails
     */
    public byte[] buildCreditNote(InvoiceDocument document) {
        validator.validate(document);
        return creditNoteWriter.write(document);
    }

    /**
     * Generates XML without running validation. Use with caution —
     * only appropriate when you have already validated the document externally,
     * or during testing with incomplete documents.
     */
    public byte[] buildInvoiceSkipValidation(InvoiceDocument document) {
        return invoiceWriter.write(document);
    }
}
