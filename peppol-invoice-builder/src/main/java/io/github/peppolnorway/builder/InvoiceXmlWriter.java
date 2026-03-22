package io.github.peppolnorway.builder;

/**
 * Concrete Template Method implementation for UBL 2.1 Invoice XML.
 *
 * <p>Produces a {@code <Invoice>} document with UNCL1001 type code 380.
 * All section-writing logic is inherited from {@link UblXmlWriter} —
 * this class only provides the four hook values that distinguish an
 * Invoice from a Credit Note.</p>
 *
 * <p>To generate an invoice:</p>
 * <pre>{@code
 * EhfDocumentService service = new EhfDocumentService();
 * byte[] xml = service.buildInvoice(invoiceDocument);
 * }</pre>
 */
public final class InvoiceXmlWriter extends UblXmlWriter {

    private static final String INVOICE_NS =
            "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";

    @Override
    protected String rootElementName() {
        return "Invoice";
    }

    @Override
    protected String rootNamespace() {
        return INVOICE_NS;
    }

    @Override
    protected String documentTypeCode() {
        return "380"; // UNCL1001: Commercial Invoice
    }

    @Override
    protected String lineElementName() {
        return "InvoiceLine";
    }

    @Override
    protected String quantityElementName() {
        return "InvoicedQuantity";
    }
}
