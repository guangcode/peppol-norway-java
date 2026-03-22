package io.github.peppolnorway.builder;

import io.github.peppolnorway.api.VatCategory;
import io.github.peppolnorway.model.InvoiceDocument;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * Generates UBL 2.1 XML from an {@link InvoiceDocument}, using the
 * <b>Template Method</b> pattern.
 *
 * <p>The abstract base class defines the fixed skeleton of the UBL document
 * structure (the invariant parts of the algorithm). Subclasses override the
 * hook methods to change the root element, namespace, type code, and line
 * element names for invoices vs. credit notes — without duplicating any
 * of the shared section logic.</p>
 *
 * <pre>
 * UblXmlWriter (abstract — Template Method)
 *   ├── InvoiceXmlWriter    — root "Invoice",    type code "380"
 *   └── CreditNoteXmlWriter — root "CreditNote", type code "381"
 * </pre>
 *
 * <p>The class uses raw StAX ({@link XMLStreamWriter}) for zero external
 * UBL library dependency — any Java project can include this module.</p>
 *
 * <h3>XML compliance notes</h3>
 * <ul>
 *   <li>Supplier PartyTaxScheme uses {@code schemeID="UN/ECE 5153"} on
 *       the VAT TaxScheme ID (required by Norwegian EHF validators).</li>
 *   <li>Supplier always includes a second PartyTaxScheme for
 *       Foretaksregisteret (BR-CO-26, mandatory for Norwegian AS/ASA).</li>
 *   <li>TaxSubtotal and InvoiceLine ClassifiedTaxCategory TaxScheme both
 *       carry {@code schemeAgencyID="UN/ECE 5153"} on the ID element.</li>
 *   <li>Each InvoiceLine includes an {@code <cac:OrderLineReference>}.</li>
 *   <li>AllowanceCharge includes {@code <cbc:BaseAmount>} for full compliance.</li>
 * </ul>
 */
public abstract class UblXmlWriter {

    // ── UBL 2.1 Namespaces ────────────────────────────────────────────────

    /** Common Aggregate Components namespace URI. */
    protected static final String CAC =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";

    /** Common Basic Components namespace URI. */
    protected static final String CBC =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    /** PEPPOL BIS Billing 3.0 CustomizationID value. */
    protected static final String CUSTOMIZATION_ID =
            "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0";

    /** PEPPOL BIS Billing 3.0 ProfileID value. */
    protected static final String PROFILE_ID =
            "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";

    /**
     * ICD 0192 — International Code Designator for the Norwegian Register
     * of Business Enterprises (Enhetsregisteret). Used for PEPPOL routing.
     */
    private static final String ICD_NO = "0192";

    /**
     * UN/ECE 5153 — agency identifier for VAT scheme IDs in Norwegian EHF.
     * Required on {@code cbc:ID} inside {@code cac:TaxScheme}.
     */
    private static final String VAT_SCHEME_AGENCY = "UN/ECE 5153";

    /**
     * "Foretaksregisteret" — Norwegian enterprise registry declaration.
     * Required for AS/ASA companies per business rule BR-CO-26.
     */
    private static final String FORETAKSREGISTERET = "Foretaksregisteret";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    // ─────────────────────────────────────────────────────────────────────
    // Template Method — fixed algorithm skeleton
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generates UTF-8 UBL 2.1 XML bytes for the given invoice document.
     *
     * <p>This is the template method. The fixed algorithm skeleton is defined here.
     * Subclasses override the hook methods to customize per-document-type behavior
     * (root element name, namespace, type code, line element names).</p>
     *
     * @param doc the validated invoice document
     * @return UTF-8 XML bytes, ready for SBDH wrapping and PEPPOL transmission
     * @throws UblWriterException if XML generation fails
     */
    public final byte[] write(InvoiceDocument doc) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(out, "UTF-8");

            w.writeStartDocument("UTF-8", "1.0");
            w.writeStartElement(rootElementName());
            w.writeDefaultNamespace(rootNamespace());
            w.writeNamespace("cac", CAC);
            w.writeNamespace("cbc", CBC);

            writeHeader(w, doc);         // Step 1: document metadata + references
            writeAttachments(w, doc);    // Step 2: AdditionalDocumentReference (embedded PDFs)
            writeSupplier(w, doc);       // Step 3: AccountingSupplierParty
            writeCustomer(w, doc);       // Step 4: AccountingCustomerParty
            writeDelivery(w, doc);       // Step 5: Delivery (optional)
            writePaymentMeans(w, doc);   // Step 6: PaymentMeans (domestic + IBAN/BIC)
            writeTaxTotal(w, doc);       // Step 7: TaxTotal + TaxSubtotals
            writeMonetaryTotal(w, doc);  // Step 8: LegalMonetaryTotal
            writeLines(w, doc);          // Step 9: InvoiceLine / CreditNoteLine

            w.writeEndElement();
            w.writeEndDocument();
            w.flush();
            w.close();

            return out.toByteArray();

        } catch (XMLStreamException e) {
            throw new UblWriterException("Failed to generate UBL XML: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Hook methods — subclasses must override these
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Root element name: "Invoice" or "CreditNote".
     * Maps to the UBL document root element.
     */
    protected abstract String rootElementName();

    /**
     * Root namespace URI for the document type.
     * E.g. "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
     */
    protected abstract String rootNamespace();

    /**
     * UNCL1001 document type code.
     * "380" for Commercial Invoice, "381" for Credit Note.
     */
    protected abstract String documentTypeCode();

    /**
     * UBL line element name: "InvoiceLine" or "CreditNoteLine".
     */
    protected abstract String lineElementName();

    /**
     * UBL quantity element name: "InvoicedQuantity" or "CreditedQuantity".
     */
    protected abstract String quantityElementName();

    // ─────────────────────────────────────────────────────────────────────
    // Section writers — shared implementation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Writes document-level metadata: CustomizationID, ProfileID, invoice ID,
     * dates, type code, currency, BuyerReference, OrderReference, and
     * ContractDocumentReference.
     * Ensures compliance with BR-02 (invoice number) and BR-03 (issue date).
     */
    private void writeHeader(XMLStreamWriter w, InvoiceDocument doc) throws XMLStreamException {
        cbc(w, "CustomizationID", CUSTOMIZATION_ID);
        cbc(w, "ProfileID", PROFILE_ID);
        cbc(w, "ID", doc.getInvoiceNo());
        cbc(w, "IssueDate", doc.getInvoiceDate().format(DATE_FMT));

        if (doc.getDueDate() != null) {
            cbc(w, "DueDate", doc.getDueDate().format(DATE_FMT));
        }

        cbc(w, documentTypeCode().equals("380") ? "InvoiceTypeCode" : "CreditNoteTypeCode",
                documentTypeCode());

        if (!isBlank(doc.getNote())) {
            cbc(w, "Note", doc.getNote());
        }

        cbc(w, "DocumentCurrencyCode", doc.getCurrency());

        // BuyerReference — effectively mandatory for Norwegian B2G invoices
        if (!isBlank(doc.getBuyerReference())) {
            cbc(w, "BuyerReference", doc.getBuyerReference());
        }

        // OrderReference (purchase order number from buyer)
        if (!isBlank(doc.getOrderNo())) {
            startElement(w, CAC, "OrderReference");
            cbc(w, "ID", doc.getOrderNo());
            w.writeEndElement();
        }

        // ContractDocumentReference (framework agreement or contract reference)
        if (!isBlank(doc.getContractNo())) {
            startElement(w, CAC, "ContractDocumentReference");
            cbc(w, "ID", doc.getContractNo());
            w.writeEndElement();
        }
    }

    /**
     * Writes embedded document attachments as AdditionalDocumentReference elements.
     * Typically used to embed a visual PDF representation of the invoice
     * alongside the machine-readable UBL XML.
     */
    private void writeAttachments(XMLStreamWriter w, InvoiceDocument doc) throws XMLStreamException {
        if (doc.getAttachments() == null || doc.getAttachments().isEmpty()) return;

        for (InvoiceDocument.Attachment att : doc.getAttachments()) {
            startElement(w, CAC, "AdditionalDocumentReference");
            cbc(w, "ID", att.getFilename());

            if (!isBlank(att.getDescription())) {
                cbc(w, "DocumentDescription", att.getDescription());
            }

            if (!isBlank(att.getBase64Content())) {
                startElement(w, CAC, "Attachment");
                // EmbeddedDocumentBinaryObject requires mimeCode and filename attributes
                w.writeStartElement(CBC, "EmbeddedDocumentBinaryObject");
                String mime = isBlank(att.getMimeType()) ? "application/pdf" : att.getMimeType();
                w.writeAttribute("mimeCode", mime);
                w.writeAttribute("filename", att.getFilename());
                w.writeCharacters(att.getBase64Content());
                w.writeEndElement();
                w.writeEndElement(); // Attachment
            }

            w.writeEndElement(); // AdditionalDocumentReference
        }
    }

    /**
     * Writes the AccountingSupplierParty element.
     *
     * <p>Norwegian EHF-specific behaviour:</p>
     * <ul>
     *   <li>If VAT-registered: PartyTaxScheme with CompanyID = NO{orgNo}MVA,
     *       TaxScheme/ID has {@code schemeID="UN/ECE 5153"}.</li>
     *   <li>Always: second PartyTaxScheme with CompanyID = "Foretaksregisteret"
     *       (BR-CO-26, mandatory for AS/ASA companies).</li>
     * </ul>
     */
    private void writeSupplier(XMLStreamWriter w, InvoiceDocument doc) throws XMLStreamException {
        startElement(w, CAC, "AccountingSupplierParty");
        startElement(w, CAC, "Party");

        // PEPPOL endpoint routing address (SML/SMP lookup key)
        writeEndpointId(w, doc.getSupplier().getOrgNumber());

        startElement(w, CAC, "PartyName");
        cbc(w, "Name", doc.getSupplier().getName());
        w.writeEndElement();

        if (doc.getSupplier().getAddress() != null) {
            writeAddress(w, doc.getSupplier().getAddress());
        }

        // VAT registration scheme — only if supplier is VAT-registered
        // Norwegian format: NO{orgNumber}MVA, with schemeID="UN/ECE 5153" on TaxScheme/ID
        if (doc.getSupplier().isVatRegistered()) {
            startElement(w, CAC, "PartyTaxScheme");
            w.writeStartElement(CBC, "CompanyID");
            w.writeCharacters("NO" + doc.getSupplier().getOrgNumber() + "MVA");
            w.writeEndElement();
            startElement(w, CAC, "TaxScheme");
            writeVatSchemeId(w);
            w.writeEndElement(); // TaxScheme
            w.writeEndElement(); // PartyTaxScheme
        }

        // Foretaksregisteret declaration — mandatory for all Norwegian AS/ASA (BR-CO-26)
        startElement(w, CAC, "PartyTaxScheme");
        w.writeStartElement(CBC, "CompanyID");
        w.writeCharacters(FORETAKSREGISTERET);
        w.writeEndElement();
        startElement(w, CAC, "TaxScheme");
        w.writeStartElement(CBC, "ID");
        w.writeAttribute("schemeID", "TAX");
        w.writeCharacters("TAX");
        w.writeEndElement();
        w.writeEndElement(); // TaxScheme
        w.writeEndElement(); // PartyTaxScheme

        // Legal entity (BR-CO-26 compliant)
        startElement(w, CAC, "PartyLegalEntity");
        cbc(w, "RegistrationName",
                !isBlank(doc.getSupplier().getLegalName())
                        ? doc.getSupplier().getLegalName()
                        : doc.getSupplier().getName());
        w.writeStartElement(CBC, "CompanyID");
        w.writeAttribute("schemeID", ICD_NO);
        w.writeCharacters(doc.getSupplier().getOrgNumber());
        w.writeEndElement();
        w.writeEndElement(); // PartyLegalEntity

        writeContact(w, doc.getSupplier());

        w.writeEndElement(); // Party
        w.writeEndElement(); // AccountingSupplierParty
    }

    /**
     * Writes the AccountingCustomerParty element.
     *
     * <p>Includes PartyIdentification (required for Norwegian B2G routing),
     * PostalAddress, and PartyLegalEntity. Customer VAT scheme is included
     * only when {@code vatRegistered} is true on the customer party.</p>
     */
    private void writeCustomer(XMLStreamWriter w, InvoiceDocument doc) throws XMLStreamException {
        startElement(w, CAC, "AccountingCustomerParty");
        startElement(w, CAC, "Party");

        // PEPPOL endpoint routing address for the receiver
        writeEndpointId(w, doc.getCustomer().getOrgNumber());

        // PartyIdentification — required for Norwegian e-invoicing routing
        startElement(w, CAC, "PartyIdentification");
        w.writeStartElement(CBC, "ID");
        w.writeAttribute("schemeID", ICD_NO);
        w.writeCharacters(doc.getCustomer().getOrgNumber());
        w.writeEndElement();
        w.writeEndElement();

        startElement(w, CAC, "PartyName");
        cbc(w, "Name", doc.getCustomer().getName());
        w.writeEndElement();

        if (doc.getCustomer().getAddress() != null) {
            writeAddress(w, doc.getCustomer().getAddress());
        }

        // Customer VAT scheme — included only if customer is VAT-registered
        if (doc.getCustomer().isVatRegistered()) {
            startElement(w, CAC, "PartyTaxScheme");
            w.writeStartElement(CBC, "CompanyID");
            w.writeCharacters("NO" + doc.getCustomer().getOrgNumber() + "MVA");
            w.writeEndElement();
            startElement(w, CAC, "TaxScheme");
            writeVatSchemeId(w);
            w.writeEndElement(); // TaxScheme
            w.writeEndElement(); // PartyTaxScheme
        }

        // Customer legal entity
        startElement(w, CAC, "PartyLegalEntity");
        cbc(w, "RegistrationName", doc.getCustomer().getName());
        w.writeStartElement(CBC, "CompanyID");
        w.writeAttribute("schemeID", ICD_NO);
        w.writeCharacters(doc.getCustomer().getOrgNumber());
        w.writeEndElement();
        w.writeEndElement(); // PartyLegalEntity

        w.writeEndElement(); // Party
        w.writeEndElement(); // AccountingCustomerParty
    }

    /**
     * Writes the Delivery element with actual delivery date and optional
     * DeliveryLocation/Address when a delivery address is provided.
     */
    private void writeDelivery(XMLStreamWriter w, InvoiceDocument doc) throws XMLStreamException {
        if (doc.getDeliveryDate() == null && doc.getDeliveryAddress() == null) return;

        startElement(w, CAC, "Delivery");

        if (doc.getDeliveryDate() != null) {
            cbc(w, "ActualDeliveryDate", doc.getDeliveryDate().format(DATE_FMT));
        }

        if (doc.getDeliveryAddress() != null) {
            startElement(w, CAC, "DeliveryLocation");
            writeAddress(w, doc.getDeliveryAddress());
            w.writeEndElement(); // DeliveryLocation
        }

        w.writeEndElement(); // Delivery
    }

    /**
     * Writes one or two PaymentMeans elements:
     * <ol>
     *   <li>Domestic bank account with optional KID (OCR) payment reference.</li>
     *   <li>International IBAN/BIC block — only if both {@code iban} and
     *       {@code bic} are provided on the document.</li>
     * </ol>
     *
     * <p>PaymentMeansCode 30 = credit transfer (UNCL4461).</p>
     */
    private void writePaymentMeans(XMLStreamWriter w, InvoiceDocument doc) throws XMLStreamException {
        // Domestic bank account (Norwegian standard 11-digit account number)
        startElement(w, CAC, "PaymentMeans");
        cbc(w, "PaymentMeansCode", "30");

        // KID (Kundeidentifikasjonsnummer) — Norwegian OCR payment reference
        if (!isBlank(doc.getKid())) {
            cbc(w, "PaymentID", doc.getKid());
        }

        if (!isBlank(doc.getBankAccountNo())) {
            startElement(w, CAC, "PayeeFinancialAccount");
            cbc(w, "ID", doc.getBankAccountNo());
            w.writeEndElement();
        }
        w.writeEndElement(); // PaymentMeans

        // International IBAN + BIC block for cross-border payments
        if (!isBlank(doc.getIban()) && !isBlank(doc.getBic())) {
            startElement(w, CAC, "PaymentMeans");
            cbc(w, "PaymentMeansCode", "30");
            startElement(w, CAC, "PayeeFinancialAccount");
            cbc(w, "ID", doc.getIban());
            startElement(w, CAC, "FinancialInstitutionBranch");
            cbc(w, "ID", doc.getBic());
            w.writeEndElement(); // FinancialInstitutionBranch
            w.writeEndElement(); // PayeeFinancialAccount
            w.writeEndElement(); // PaymentMeans
        }
    }

    /**
     * Writes the TaxTotal element with document-level VAT sum and all
     * TaxSubtotal entries grouped by Norwegian MVA-kode.
     *
     * <p>TaxScheme ID carries {@code schemeAgencyID="UN/ECE 5153"} as required
     * by Norwegian EHF validators. See also BR-CO-17 compliance notes
     * in {@link InvoiceDocument.VatBreakdown}.</p>
     */
    private void writeTaxTotal(XMLStreamWriter w, InvoiceDocument doc) throws XMLStreamException {
        startElement(w, CAC, "TaxTotal");
        amountElement(w, "TaxAmount", doc.getVatAmount(), doc.getCurrency());

        for (InvoiceDocument.VatBreakdown vl : doc.getVatLines()) {
            VatCategory category = VatCategory.fromNorwegianCode(vl.getNorwegianVatCode());

            startElement(w, CAC, "TaxSubtotal");
            amountElement(w, "TaxableAmount", vl.getBaseAmount(), doc.getCurrency());
            amountElement(w, "TaxAmount", vl.getVatAmount(), doc.getCurrency());
            writeTaxCategory(w, category, vl.getVatRate());
            w.writeEndElement(); // TaxSubtotal
        }

        w.writeEndElement(); // TaxTotal
    }

    /**
     * Writes the LegalMonetaryTotal element with all required monetary amounts.
     * Includes PayableRoundingAmount when a rounding amount is present on the document.
     */
    private void writeMonetaryTotal(XMLStreamWriter w, InvoiceDocument doc) throws XMLStreamException {
        startElement(w, CAC, "LegalMonetaryTotal");
        amountElement(w, "LineExtensionAmount", doc.getNetAmount(), doc.getCurrency());
        amountElement(w, "TaxExclusiveAmount", doc.getNetAmount(), doc.getCurrency());
        amountElement(w, "TaxInclusiveAmount", doc.getTotalAmountInclVat(), doc.getCurrency());

        if (doc.getRounding() != null) {
            amountElement(w, "PayableRoundingAmount", doc.getRounding(), doc.getCurrency());
        }

        amountElement(w, "PayableAmount", doc.getPayableAmount(), doc.getCurrency());
        w.writeEndElement(); // LegalMonetaryTotal
    }

    /**
     * Writes all invoice lines (or credit note lines).
     *
     * <p>Each line includes:</p>
     * <ul>
     *   <li>InvoicedQuantity / CreditedQuantity with unitCode</li>
     *   <li>LineExtensionAmount</li>
     *   <li>OrderLineReference (links to buyer's purchase order line)</li>
     *   <li>AllowanceCharge (discount) when present, including BaseAmount</li>
     *   <li>Item with ClassifiedTaxCategory carrying schemeAgencyID="UN/ECE 5153"</li>
     *   <li>Price/PriceAmount</li>
     * </ul>
     */
    private void writeLines(XMLStreamWriter w, InvoiceDocument doc) throws XMLStreamException {
        for (int i = 0; i < doc.getLines().size(); i++) {
            var line = doc.getLines().get(i);
            VatCategory category = VatCategory.fromNorwegianCode(line.getNorwegianVatCode());
            String lineId = String.valueOf(i + 1);

            startElement(w, CAC, lineElementName());
            cbc(w, "ID", lineId);

            // Invoiced / credited quantity with mandatory UN/ECE Rec 20 unit code
            w.writeStartElement(CBC, quantityElementName());
            w.writeAttribute("unitCode", line.getUnitCode());
            w.writeCharacters(fmt(line.getQuantity()));
            w.writeEndElement();

            amountElement(w, "LineExtensionAmount", line.getLineAmount(), doc.getCurrency());

            // OrderLineReference — links this line to the buyer's purchase order line
            startElement(w, CAC, "OrderLineReference");
            cbc(w, "LineID", lineId);
            w.writeEndElement();

            // AllowanceCharge (line-level discount)
            if (line.getDiscountPercent() != null) {
                // Base amount = unit price × quantity (before discount)
                BigDecimal baseAmount = line.getUnitPrice() != null && line.getQuantity() != null
                        ? line.getUnitPrice().multiply(line.getQuantity())
                                .setScale(2, RoundingMode.HALF_DOWN)
                        : BigDecimal.ZERO;

                BigDecimal discAmount = line.getDiscountAmount() != null
                        ? line.getDiscountAmount() : BigDecimal.ZERO;

                startElement(w, CAC, "AllowanceCharge");
                cbc(w, "ChargeIndicator", "false"); // false = allowance (discount)
                cbc(w, "AllowanceChargeReason", "Discount");

                // MultiplierFactorNumeric: the discount percentage as a decimal multiplier
                // e.g. 10% is stored as 10.0000 in the original EhfInvoiceFactory
                w.writeStartElement(CBC, "MultiplierFactorNumeric");
                w.writeCharacters(line.getDiscountPercent().setScale(4, RoundingMode.HALF_UP)
                        .toPlainString());
                w.writeEndElement();

                amountElement(w, "Amount", discAmount, doc.getCurrency());
                amountElement(w, "BaseAmount", baseAmount, doc.getCurrency());
                w.writeEndElement(); // AllowanceCharge
            }

            // Item description and tax category
            startElement(w, CAC, "Item");
            cbc(w, "Name", !isBlank(line.getDescription()) ? line.getDescription() : "NA");

            if (!isBlank(line.getItemCode())) {
                startElement(w, CAC, "SellersItemIdentification");
                cbc(w, "ID", line.getItemCode());
                w.writeEndElement();
            }

            writeClassifiedTaxCategory(w, category, line.getVatRate());
            w.writeEndElement(); // Item

            // Price per unit
            startElement(w, CAC, "Price");
            amountElement(w, "PriceAmount", line.getUnitPrice(), doc.getCurrency());
            w.writeEndElement(); // Price

            w.writeEndElement(); // InvoiceLine / CreditNoteLine
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Shared XML helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Writes a {@code <cac:ClassifiedTaxCategory>} element inside an Item.
     * The TaxScheme ID carries {@code schemeAgencyID="UN/ECE 5153"}.
     */
    private void writeClassifiedTaxCategory(XMLStreamWriter w, VatCategory category,
            BigDecimal vatRate) throws XMLStreamException {
        startElement(w, CAC, "ClassifiedTaxCategory");
        cbc(w, "ID", category.getCode());
        w.writeStartElement(CBC, "Percent");
        w.writeCharacters(fmt(vatRate != null ? vatRate : BigDecimal.ZERO));
        w.writeEndElement();
        startElement(w, CAC, "TaxScheme");
        writeVatSchemeIdAgency(w);
        w.writeEndElement(); // TaxScheme
        w.writeEndElement(); // ClassifiedTaxCategory
    }

    /**
     * Writes a {@code <cac:TaxCategory>} element inside a TaxSubtotal.
     * The TaxScheme ID carries {@code schemeAgencyID="UN/ECE 5153"}.
     */
    private void writeTaxCategory(XMLStreamWriter w, VatCategory category,
            BigDecimal vatRate) throws XMLStreamException {
        startElement(w, CAC, "TaxCategory");
        cbc(w, "ID", category.getCode());
        w.writeStartElement(CBC, "Percent");
        w.writeCharacters(fmt(vatRate != null ? vatRate : BigDecimal.ZERO));
        w.writeEndElement();
        startElement(w, CAC, "TaxScheme");
        writeVatSchemeIdAgency(w);
        w.writeEndElement(); // TaxScheme
        w.writeEndElement(); // TaxCategory
    }

    /**
     * Writes a {@code <cac:PostalAddress>} element.
     * Country defaults to "NO" (Norway) when not specified.
     */
    private void writeAddress(XMLStreamWriter w, InvoiceDocument.Address addr)
            throws XMLStreamException {
        startElement(w, CAC, "PostalAddress");
        if (!isBlank(addr.getStreet()))   cbc(w, "StreetName", addr.getStreet());
        if (!isBlank(addr.getCity()))     cbc(w, "CityName", addr.getCity());
        if (!isBlank(addr.getPostcode())) cbc(w, "PostalZone", addr.getPostcode());
        startElement(w, CAC, "Country");
        cbc(w, "IdentificationCode",
                !isBlank(addr.getCountryCode()) ? addr.getCountryCode() : "NO");
        w.writeEndElement(); // Country
        w.writeEndElement(); // PostalAddress
    }

    /**
     * Writes {@code <cbc:EndpointID schemeID="0192">orgNumber</cbc:EndpointID>}.
     * The PEPPOL endpoint ID is the primary routing key for SML/SMP lookup.
     */
    private void writeEndpointId(XMLStreamWriter w, String orgNumber) throws XMLStreamException {
        w.writeStartElement(CBC, "EndpointID");
        w.writeAttribute("schemeID", ICD_NO);
        w.writeCharacters(orgNumber);
        w.writeEndElement();
    }

    /**
     * Writes the contact block (Name, Telephone, ElectronicMail) for a party.
     * Skipped entirely when all contact fields are blank.
     */
    private void writeContact(XMLStreamWriter w, InvoiceDocument.Party party)
            throws XMLStreamException {
        if (isBlank(party.getContactPerson()) && isBlank(party.getPhone())
                && isBlank(party.getEmail())) return;
        startElement(w, CAC, "Contact");
        if (!isBlank(party.getContactPerson())) cbc(w, "Name", party.getContactPerson());
        if (!isBlank(party.getPhone()))          cbc(w, "Telephone", party.getPhone());
        if (!isBlank(party.getEmail()))          cbc(w, "ElectronicMail", party.getEmail());
        w.writeEndElement(); // Contact
    }

    /**
     * Writes {@code <cbc:ID schemeID="UN/ECE 5153">VAT</cbc:ID>}.
     * Used inside PartyTaxScheme/TaxScheme for supplier VAT registration.
     */
    private void writeVatSchemeId(XMLStreamWriter w) throws XMLStreamException {
        w.writeStartElement(CBC, "ID");
        w.writeAttribute("schemeID", VAT_SCHEME_AGENCY);
        w.writeCharacters("VAT");
        w.writeEndElement();
    }

    /**
     * Writes {@code <cbc:ID schemeAgencyID="UN/ECE 5153">VAT</cbc:ID>}.
     * Used inside TaxCategory/TaxScheme and ClassifiedTaxCategory/TaxScheme.
     * Note: uses {@code schemeAgencyID} (not schemeID) per EHF validation rules.
     */
    private void writeVatSchemeIdAgency(XMLStreamWriter w) throws XMLStreamException {
        w.writeStartElement(CBC, "ID");
        w.writeAttribute("schemeAgencyID", VAT_SCHEME_AGENCY);
        w.writeCharacters("VAT");
        w.writeEndElement();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Low-level StAX helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Writes a simple {@code cbc:localName} text element.
     */
    protected void cbc(XMLStreamWriter w, String localName, String value)
            throws XMLStreamException {
        w.writeStartElement(CBC, localName);
        w.writeCharacters(value != null ? value : "");
        w.writeEndElement();
    }

    /**
     * Writes a {@code cac:localName} start element (no attributes, no text).
     * Caller must write children and then call {@code w.writeEndElement()}.
     */
    private void startElement(XMLStreamWriter w, String ns, String localName)
            throws XMLStreamException {
        w.writeStartElement(ns, localName);
    }

    /**
     * Writes a monetary amount element with a {@code currencyID} attribute.
     * Amount is rounded to 2 decimal places.
     */
    private void amountElement(XMLStreamWriter w, String name, BigDecimal amount, String currency)
            throws XMLStreamException {
        w.writeStartElement(CBC, name);
        w.writeAttribute("currencyID", currency);
        w.writeCharacters(fmt(amount));
        w.writeEndElement();
    }

    /**
     * Formats a BigDecimal to exactly 2 decimal places using HALF_UP rounding.
     * Returns "0.00" for null values.
     */
    private String fmt(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Returns true if the string is null or contains only whitespace.
     */
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Exception
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Thrown when UBL XML generation fails due to a StAX error.
     */
    public static class UblWriterException extends RuntimeException {
        public UblWriterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
