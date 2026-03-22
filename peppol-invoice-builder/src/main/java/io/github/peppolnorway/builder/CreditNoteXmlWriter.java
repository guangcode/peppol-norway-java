package io.github.peppolnorway.builder;

/**
 * Concrete Template Method implementation for UBL 2.1 Credit Note XML.
 *
 * <p>Produces a {@code <CreditNote>} document with UNCL1001 type code 381.
 * Reuses all the section-writing logic from {@link UblXmlWriter} —
 * the only differences from Invoice are the root element, namespace,
 * type code, and line element names.</p>
 */
public final class CreditNoteXmlWriter extends UblXmlWriter {

    private static final String CREDIT_NOTE_NS =
            "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";

    @Override
    protected String rootElementName() {
        return "CreditNote";
    }

    @Override
    protected String rootNamespace() {
        return CREDIT_NOTE_NS;
    }

    @Override
    protected String documentTypeCode() {
        return "381"; // UNCL1001: Credit Note
    }

    @Override
    protected String lineElementName() {
        return "CreditNoteLine";
    }

    @Override
    protected String quantityElementName() {
        return "CreditedQuantity";
    }
}
