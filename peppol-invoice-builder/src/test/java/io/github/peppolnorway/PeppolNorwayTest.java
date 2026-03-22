package io.github.peppolnorway;

import io.github.peppolnorway.api.VatCategory;
import io.github.peppolnorway.builder.EhfDocumentService;
import io.github.peppolnorway.exception.InvoiceValidationException;
import io.github.peppolnorway.model.InvoiceDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the core peppol-invoice-builder module.
 * Covers: Norwegian VAT code mapping, EHF XML generation, and invoice validation.
 */
class PeppolNorwayTest {

    // ─────────────────────────────────────────────────────────────────────
    // VatCategory mapping tests
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("VatCategory — Norwegian MVA-kode to PEPPOL category mapping")
    class VatCategoryMappingTest {

        @ParameterizedTest(name = "MVA-kode [{0}] → PEPPOL [{1}]")
        @CsvSource({
                "3,S",  "3U,S",  "31,S",  "31U,S",  "32,S",  "33,S",  "33U,S",
                "5,Z",  "5U,Z",
                "51,AE",
                "52,G",
                "6,E",
                "0,O",  "7,O"
        })
        @DisplayName("All supported MVA-koder should map to the correct PEPPOL category")
        void shouldMapCorrectly(String vatCode, String expectedCategory) {
            VatCategory result = VatCategory.fromNorwegianCode(vatCode);
            assertEquals(expectedCategory, result.getCode(),
                    "MVA-kode " + vatCode + " should map to PEPPOL category " + expectedCategory);
        }

        @Test
        @DisplayName("Unknown MVA-kode should throw IllegalArgumentException")
        void shouldThrowForUnknownCode() {
            assertThrows(IllegalArgumentException.class,
                    () -> VatCategory.fromNorwegianCode("99"),
                    "Unsupported MVA-kode '99' should cause fail-fast exception");
        }

        @Test
        @DisplayName("Null MVA-kode should throw IllegalArgumentException")
        void shouldThrowForNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> VatCategory.fromNorwegianCode(null),
                    "Null VAT code should cause fail-fast exception");
        }

        @Test
        @DisplayName("BR-CO-17: Z, E, and O are legally distinct and must not be confused")
        void zeroRatedCategoriesMustBeDistinct() {
            VatCategory z = VatCategory.fromNorwegianCode("5");  // Zero-rated goods
            VatCategory e = VatCategory.fromNorwegianCode("6");  // Exempt from tax
            VatCategory o = VatCategory.fromNorwegianCode("0");  // Outside scope

            assertAll(
                    () -> assertEquals("Z", z.getCode()),
                    () -> assertEquals("E", e.getCode()),
                    () -> assertEquals("O", o.getCode()),
                    () -> assertNotEquals(z, e, "Z and E have different legal meanings — must not be merged"),
                    () -> assertNotEquals(z, o, "Z and O have different legal meanings — must not be merged"),
                    () -> assertNotEquals(e, o, "E and O have different legal meanings — must not be merged")
            );
        }

        @Test
        @DisplayName("isSupportedCode should correctly identify known and unknown codes")
        void isSupportedCodeShouldWork() {
            assertTrue(VatCategory.isSupportedCode("3"),  "Standard rate code '3' should be supported");
            assertTrue(VatCategory.isSupportedCode("6"),  "Exempt code '6' should be supported");
            assertFalse(VatCategory.isSupportedCode("99"), "Unknown code '99' should not be supported");
            assertFalse(VatCategory.isSupportedCode(null), "Null should not be supported");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // EhfDocumentService XML generation tests
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EhfDocumentService — UBL XML generation")
    class DocumentServiceTest {

        private EhfDocumentService service;
        private InvoiceDocument validInvoice;

        @BeforeEach
        void setUp() {
            service = new EhfDocumentService();
            validInvoice = buildTestInvoice();
        }

        @Test
        @DisplayName("Generated XML should contain PEPPOL-compliant header identifiers")
        void shouldGeneratePeppolCompliantHeader() {
            String xml = new String(service.buildInvoice(validInvoice));
            System.out.println(xml);
            assertAll(
                    () -> assertTrue(xml.contains("urn:cen.eu:en16931:2017"),
                            "XML should contain EN16931 CustomizationID"),
                    () -> assertTrue(xml.contains("urn:fdc:peppol.eu:2017:poacc:billing:01:1.0"),
                            "XML should contain ProfileID"),
                    () -> assertTrue(xml.contains("INV-TEST-001"),
                            "XML should contain invoice number"),
                    () -> assertTrue(xml.contains("NOK"),
                            "XML should contain currency code"),
                    () -> assertTrue(xml.contains("380"),
                            "XML should contain invoice type code"),
                    () -> assertTrue(xml.contains("Buyer Dept Oslo"),
                            "XML should contain buyer reference")
            );
        }

        @Test
        @DisplayName("Supplier VAT registration should produce NO{orgNo}MVA format")
        void shouldGenerateSupplierVatId() {
            String xml = new String(service.buildInvoice(validInvoice));
            assertTrue(xml.contains("NO336880697MVA"),
                    "VAT-registered supplier should produce NO{orgNumber}MVA format");
        }

        @Test
        @DisplayName("Supplier should include Foretaksregisteret PartyTaxScheme (BR-CO-26)")
        void shouldIncludeFoRetaksregisteret() {
            String xml = new String(service.buildInvoice(validInvoice));
            assertTrue(xml.contains("Foretaksregisteret"),
                    "Norwegian AS/ASA supplier must declare Foretaksregisteret (BR-CO-26)");
        }

        @Test
        @DisplayName("TaxScheme ID should carry schemeAgencyID='UN/ECE 5153'")
        void shouldIncludeSchemeAgencyId() {
            String xml = new String(service.buildInvoice(validInvoice));
            assertTrue(xml.contains("schemeAgencyID=\"UN/ECE 5153\""),
                    "TaxScheme ID must carry schemeAgencyID='UN/ECE 5153' per EHF requirements");
        }

        @Test
        @DisplayName("Credit note should use CreditNote root element and type code 381")
        void shouldGenerateCreditNoteWithCorrectTypeCode() {
            String xml = new String(service.buildCreditNote(validInvoice));
            assertAll(
                    () -> assertTrue(xml.contains("<CreditNote"),
                            "Root element should be CreditNote"),
                    () -> assertTrue(xml.contains("381"),
                            "Credit note type code should be 381"),
                    () -> assertTrue(xml.contains("CreditNoteLine"),
                            "Line element should be CreditNoteLine"),
                    () -> assertTrue(xml.contains("CreditedQuantity"),
                            "Quantity element should be CreditedQuantity")
            );
        }

        @Test
        @DisplayName("VAT subtotal should correctly map Norwegian MVA-kode to PEPPOL category")
        void shouldMapVatCategoryInXml() {
            String xml = new String(service.buildInvoice(validInvoice));
            assertTrue(xml.contains(">S<"),
                    "MVA-kode '3' should map to PEPPOL category S in XML output");
        }

        @Test
        @DisplayName("Invoice lines should contain all required UBL fields")
        void shouldGenerateInvoiceLines() {
            String xml = new String(service.buildInvoice(validInvoice));
            assertAll(
                    () -> assertTrue(xml.contains("InvoiceLine"),
                            "XML should contain InvoiceLine element"),
                    () -> assertTrue(xml.contains("Consulting Services"),
                            "XML should contain line item description"),
                    () -> assertTrue(xml.contains("HUR"),
                            "XML should contain unit code HUR (hour)"),
                    () -> assertTrue(xml.contains("unitCode"),
                            "XML should contain unitCode attribute")
            );
        }

        @Test
        @DisplayName("Invoice line with discount should include AllowanceCharge with BaseAmount")
        void shouldGenerateAllowanceChargeWithBaseAmount() {
            InvoiceDocument invoiceWithDiscount = buildInvoiceWithDiscount();
            String xml = new String(service.buildInvoice(invoiceWithDiscount));
            assertAll(
                    () -> assertTrue(xml.contains("AllowanceCharge"),
                            "XML should contain AllowanceCharge for discounted line"),
                    () -> assertTrue(xml.contains("false"),
                            "ChargeIndicator should be false for a discount"),
                    () -> assertTrue(xml.contains("Discount"),
                            "AllowanceChargeReason should be 'Discount'"),
                    () -> assertTrue(xml.contains("BaseAmount"),
                            "AllowanceCharge should include BaseAmount")
            );
        }

        @Test
        @DisplayName("Invoice line should include OrderLineReference")
        void shouldGenerateOrderLineReference() {
            String xml = new String(service.buildInvoice(validInvoice));
            assertTrue(xml.contains("OrderLineReference"),
                    "Each invoice line must include OrderLineReference");
        }

        @Test
        @DisplayName("Invoice with IBAN and BIC should produce two PaymentMeans elements")
        void shouldGenerateTwoPaymentMeansForIban() {
            InvoiceDocument invoiceWithIban = buildTestInvoice().toBuilder()
                    .iban("NO1815037715716")
                    .bic("DNBANOKKXXX")
                    .build();
            String xml = new String(service.buildInvoice(invoiceWithIban));
            long paymentMeansCount = xml.lines()
                    .filter(l -> l.contains("<cac:PaymentMeans"))
                    .count();
            assertEquals(2, paymentMeansCount,
                    "Invoice with IBAN+BIC should produce two PaymentMeans elements");
            assertTrue(xml.contains("FinancialInstitutionBranch"),
                    "Second PaymentMeans should contain FinancialInstitutionBranch for BIC");
        }

        @Test
        @DisplayName("PayableRoundingAmount should appear when rounding is set")
        void shouldIncludeRoundingAmount() {
            InvoiceDocument invoiceWithRounding = buildTestInvoice().toBuilder()
                    .rounding(new BigDecimal("0.25"))
                    .payableAmount(new BigDecimal("1250.25"))
                    .build();
            String xml = new String(service.buildInvoice(invoiceWithRounding));
            assertTrue(xml.contains("PayableRoundingAmount"),
                    "PayableRoundingAmount should be present when rounding is non-null");
        }

        @Test
        @DisplayName("Attachment should produce AdditionalDocumentReference with embedded PDF")
        void shouldGenerateAttachment() {
            InvoiceDocument invoiceWithAttachment = buildTestInvoice().toBuilder()
                    .attachment(InvoiceDocument.Attachment.builder()
                            .filename("INV-TEST-001.pdf")
                            .mimeType("application/pdf")
                            .description("Commercial invoice")
                            .base64Content("SlZCRVJpMHhMalVOQ2lXRGt2ci4uLg==")
                            .build())
                    .build();
            String xml = new String(service.buildInvoice(invoiceWithAttachment));
            assertAll(
                    () -> assertTrue(xml.contains("AdditionalDocumentReference"),
                            "XML should contain AdditionalDocumentReference"),
                    () -> assertTrue(xml.contains("INV-TEST-001.pdf"),
                            "XML should contain attachment filename"),
                    () -> assertTrue(xml.contains("Commercial invoice"),
                            "XML should contain attachment description"),
                    () -> assertTrue(xml.contains("EmbeddedDocumentBinaryObject"),
                            "XML should contain the embedded binary content element")
            );
        }

        @Test
        @DisplayName("ContractDocumentReference should appear when contractNo is set")
        void shouldIncludeContractDocumentReference() {
            InvoiceDocument invoiceWithContract = buildTestInvoice().toBuilder()
                    .contractNo("CONTRACT-2024-007")
                    .build();
            String xml = new String(service.buildInvoice(invoiceWithContract));
            assertAll(
                    () -> assertTrue(xml.contains("ContractDocumentReference"),
                            "XML should contain ContractDocumentReference"),
                    () -> assertTrue(xml.contains("CONTRACT-2024-007"),
                            "XML should contain the contract number")
            );
        }

        @Test
        @DisplayName("Delivery with address should produce DeliveryLocation element")
        void shouldIncludeDeliveryLocation() {
            InvoiceDocument invoiceWithDelivery = buildTestInvoice().toBuilder()
                    .deliveryDate(LocalDate.of(2024, 5, 1))
                    .deliveryAddress(InvoiceDocument.Address.builder()
                            .street("Delivery Street 10")
                            .postcode("0180")
                            .city("Oslo")
                            .countryCode("NO")
                            .build())
                    .build();
            String xml = new String(service.buildInvoice(invoiceWithDelivery));
            assertAll(
                    () -> assertTrue(xml.contains("DeliveryLocation"),
                            "XML should contain DeliveryLocation when delivery address is set"),
                    () -> assertTrue(xml.contains("Delivery Street 10"),
                            "XML should contain delivery street name")
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // InvoiceValidator tests
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("InvoiceValidator — PEPPOL business rule validation")
    class ValidatorTest {

        private EhfDocumentService service;

        @BeforeEach
        void setUp() {
            service = new EhfDocumentService();
        }

        @Test
        @DisplayName("Missing invoice number should trigger validation failure (BR-02)")
        void shouldFailWhenInvoiceNoMissing() {
            InvoiceDocument doc = buildTestInvoice().toBuilder().invoiceNo(null).build();
            InvoiceValidationException ex = assertThrows(
                    InvoiceValidationException.class, () -> service.buildInvoice(doc));
            assertTrue(ex.getViolations().stream().anyMatch(v -> v.contains("invoiceNo")),
                    "Violation message should reference invoiceNo field");
        }

        @Test
        @DisplayName("Empty invoice lines should trigger validation failure (BR-16)")
        void shouldFailWhenNoLines() {
            InvoiceDocument doc = buildTestInvoice().toBuilder().lines(List.of()).build();
            InvoiceValidationException ex = assertThrows(
                    InvoiceValidationException.class, () -> service.buildInvoice(doc));
            assertTrue(ex.getViolations().stream().anyMatch(v -> v.contains("invoice line")),
                    "Violation message should mention missing invoice lines");
        }

        @Test
        @DisplayName("Unknown MVA-kode should trigger validation failure (BR-CO-17)")
        void shouldFailWhenUnknownVatCode() {
            InvoiceDocument.VatBreakdown badVat = InvoiceDocument.VatBreakdown.builder()
                    .norwegianVatCode("99")
                    .vatRate(BigDecimal.valueOf(25))
                    .baseAmount(BigDecimal.valueOf(1000))
                    .vatAmount(BigDecimal.valueOf(250))
                    .build();

            InvoiceDocument doc = buildTestInvoice().toBuilder()
                    .vatLines(List.of(badVat))
                    .build();

            assertThrows(InvoiceValidationException.class, () -> service.buildInvoice(doc),
                    "Unknown MVA-kode '99' should cause validation failure");
        }

        @Test
        @DisplayName("Valid invoice should pass all validation rules without exceptions")
        void shouldPassForValidInvoice() {
            assertDoesNotThrow(() -> service.buildInvoice(buildTestInvoice()),
                    "A fully populated valid invoice should not trigger any validation exceptions");
        }

        @Test
        @DisplayName("Missing supplier org number should trigger validation failure (BR-06)")
        void shouldFailWhenSupplierOrgNumberMissing() {
            InvoiceDocument doc = buildTestInvoice().toBuilder()
                    .supplier(InvoiceDocument.Party.builder()
                            .orgNumber(null)
                            .name("Supplier AS")
                            .vatRegistered(true)
                            .build())
                    .build();
            InvoiceValidationException ex = assertThrows(
                    InvoiceValidationException.class, () -> service.buildInvoice(doc));
            assertTrue(ex.getViolations().stream().anyMatch(v -> v.contains("supplier.orgNumber")),
                    "Violation message should reference supplier.orgNumber");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Test data builders
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds a fully populated valid test invoice representing a real-world
     * Norwegian B2G scenario: an accounting firm billing a car dealership
     * for consulting services.
     */
    private static InvoiceDocument buildTestInvoice() {
        return InvoiceDocument.builder()
                .invoiceNo("INV-TEST-001")
                .invoiceDate(LocalDate.of(2024, 5, 1))
                .dueDate(LocalDate.of(2024, 5, 31))
                .currency("NOK")
                .buyerReference("Buyer Dept Oslo")
                .orderNo("PO-2024-001")
                .note("Payment terms: 30 days net")
                .supplier(InvoiceDocument.Party.builder()
                        .orgNumber("336880697")
                        .name("Test Supplier AS")
                        .legalName("TEST SUPPLIER AS")
                        .vatRegistered(true)
                        .address(InvoiceDocument.Address.builder()
                                .street("Supplier Street 1")
                                .postcode("0150")
                                .city("Oslo")
                                .countryCode("NO")
                                .build())
                        .contactPerson("Jane Smith")
                        .phone("91175475")
                        .email("invoice@supplier.no")
                        .build())
                .customer(InvoiceDocument.Party.builder()
                        .orgNumber("881086591")
                        .name("Test Customer Municipality")
                        .address(InvoiceDocument.Address.builder()
                                .street("Municipal Street 5")
                                .postcode("0160")
                                .city("Oslo")
                                .countryCode("NO")
                                .build())
                        .build())
                .deliveryDate(LocalDate.of(2024, 4, 30))
                .kid("01010300130836")
                .bankAccountNo("15037715716")
                .netAmount(new BigDecimal("1000.00"))
                .vatAmount(new BigDecimal("250.00"))
                .totalAmountInclVat(new BigDecimal("1250.00"))
                .payableAmount(new BigDecimal("1250.00"))
                .vatLine(InvoiceDocument.VatBreakdown.builder()
                        .norwegianVatCode("3")       // Standard rate (25%)
                        .vatRate(new BigDecimal("25.00"))
                        .baseAmount(new BigDecimal("1000.00"))
                        .vatAmount(new BigDecimal("250.00"))
                        .build())
                .line(InvoiceDocument.InvoiceLine.builder()
                        .description("Consulting Services")
                        .itemCode("CONSULT-001")
                        .quantity(new BigDecimal("10.00"))
                        .unitCode("HUR")             // HUR = Hour (UN/ECE Rec 20)
                        .unitPrice(new BigDecimal("100.00"))
                        .lineAmount(new BigDecimal("1000.00"))
                        .norwegianVatCode("3")
                        .vatRate(new BigDecimal("25.00"))
                        .build())
                .build();
    }

    /**
     * Builds a test invoice with a discounted line item.
     * Used to verify AllowanceCharge XML output.
     * Line: 1 unit × 100.00 NOK with 10% discount = 90.00 NOK net.
     */
    private static InvoiceDocument buildInvoiceWithDiscount() {
        return InvoiceDocument.builder()
                .invoiceNo("INV-DISC-001")
                .invoiceDate(LocalDate.of(2024, 6, 1))
                .dueDate(LocalDate.of(2024, 6, 30))
                .currency("NOK")
                .buyerReference("Buyer Dept Bergen")
                .supplier(InvoiceDocument.Party.builder()
                        .orgNumber("336880697")
                        .name("Test Supplier AS")
                        .vatRegistered(true)
                        .build())
                .customer(InvoiceDocument.Party.builder()
                        .orgNumber("881086591")
                        .name("Test Customer AS")
                        .build())
                .bankAccountNo("15037715716")
                .netAmount(new BigDecimal("90.00"))
                .vatAmount(new BigDecimal("22.50"))
                .totalAmountInclVat(new BigDecimal("112.50"))
                .payableAmount(new BigDecimal("112.50"))
                .vatLine(InvoiceDocument.VatBreakdown.builder()
                        .norwegianVatCode("3")
                        .vatRate(new BigDecimal("25.00"))
                        .baseAmount(new BigDecimal("90.00"))
                        .vatAmount(new BigDecimal("22.50"))
                        .build())
                .line(InvoiceDocument.InvoiceLine.builder()
                        .description("Product with 10% Discount")
                        .itemCode("PROD-001")
                        .quantity(new BigDecimal("1.0000"))
                        .unitCode("EA")              // EA = Each (UN/ECE Rec 20)
                        .unitPrice(new BigDecimal("100.00"))
                        .discountPercent(new BigDecimal("10.0000")) // 10% discount
                        .discountAmount(new BigDecimal("10.00"))
                        .lineAmount(new BigDecimal("90.00"))
                        .norwegianVatCode("3")
                        .vatRate(new BigDecimal("25.00"))
                        .build())
                .build();
    }
}
