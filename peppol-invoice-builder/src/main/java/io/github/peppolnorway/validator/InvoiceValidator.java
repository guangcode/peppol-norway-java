package io.github.peppolnorway.validator;

import io.github.peppolnorway.api.VatCategory;
import io.github.peppolnorway.exception.InvoiceValidationException;
import io.github.peppolnorway.model.InvoiceDocument;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Validates an {@link InvoiceDocument} against PEPPOL BIS Billing 3.0 business rules
 * before XML generation, using the <b>Chain of Responsibility</b> pattern.
 *
 * <p>Each validation rule is an independent link in the chain. Rules are composed
 * via {@link #withRule(String, Consumer)} and executed in sequence. All violations
 * are collected before throwing, so callers see every problem at once rather than
 * fixing issues one by one.</p>
 *
 * <p>The validator is pre-configured with the mandatory PEPPOL rules but can be
 * extended with custom business rules for specific implementations.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * InvoiceValidator.standard()
 *     .withRule("Custom: order number required for dept X",
 *               doc -> { if (doc.getOrderNo() == null) throw new ... ; })
 *     .validate(invoice);
 * }</pre>
 */
public final class InvoiceValidator {

    private final List<ValidationRule> rules = new ArrayList<>();

    private InvoiceValidator() {}

    /**
     * Creates a validator pre-loaded with all mandatory PEPPOL BIS Billing 3.0 rules.
     */
    public static InvoiceValidator standard() {
        return new InvoiceValidator()
                .withRule("BR-02: Invoice number must not be blank",
                        doc -> requireNonBlank(doc.getInvoiceNo(), "invoiceNo"))

                .withRule("BR-03: Invoice date is required",
                        doc -> require(doc.getInvoiceDate() != null, "invoiceDate must not be null"))

                .withRule("BR-04: Invoice currency code is required",
                        doc -> requireNonBlank(doc.getCurrency(), "currency"))

                .withRule("BR-06: Supplier org number is required",
                        doc -> require(doc.getSupplier() != null && !isBlank(doc.getSupplier().getOrgNumber()),
                                "supplier.orgNumber must not be blank"))

                .withRule("BR-07: Supplier name is required",
                        doc -> require(doc.getSupplier() != null && !isBlank(doc.getSupplier().getName()),
                                "supplier.name must not be blank"))

                .withRule("BR-08: Customer org number is required",
                        doc -> require(doc.getCustomer() != null && !isBlank(doc.getCustomer().getOrgNumber()),
                                "customer.orgNumber must not be blank"))

                .withRule("BR-09: Customer name is required",
                        doc -> require(doc.getCustomer() != null && !isBlank(doc.getCustomer().getName()),
                                "customer.name must not be blank"))

                .withRule("BR-12: Net amount must not be null",
                        doc -> require(doc.getNetAmount() != null, "netAmount must not be null"))

                .withRule("BR-13: Payable amount must not be null",
                        doc -> require(doc.getPayableAmount() != null, "payableAmount must not be null"))

                .withRule("BR-16: At least one invoice line is required",
                        doc -> require(doc.getLines() != null && !doc.getLines().isEmpty(),
                                "At least one invoice line is required"))

                .withRule("BR-CO-17: VAT breakdown must not be empty",
                        doc -> require(doc.getVatLines() != null && !doc.getVatLines().isEmpty(),
                                "At least one VAT breakdown line is required"))

                .withRule("BR-CO-17: All Norwegian VAT codes must be valid",
                        doc -> {
                            if (doc.getVatLines() != null) {
                                doc.getVatLines().forEach(vl ->
                                        require(VatCategory.isSupportedCode(vl.getNorwegianVatCode()),
                                                "Unknown Norwegian VAT code: '" + vl.getNorwegianVatCode() + "'"));
                            }
                        })

                .withRule("BR-LIN-04: All invoice lines must have required fields",
                        doc -> {
                            if (doc.getLines() != null) {
                                for (int i = 0; i < doc.getLines().size(); i++) {
                                    var line = doc.getLines().get(i);
                                    int lineNum = i + 1;
                                    require(!isBlank(line.getDescription()),
                                            "Line " + lineNum + ": description is required");
                                    require(line.getQuantity() != null,
                                            "Line " + lineNum + ": quantity is required");
                                    require(!isBlank(line.getUnitCode()),
                                            "Line " + lineNum + ": unitCode is required");
                                    require(line.getUnitPrice() != null,
                                            "Line " + lineNum + ": unitPrice is required");
                                    require(line.getLineAmount() != null,
                                            "Line " + lineNum + ": lineAmount is required");
                                    require(VatCategory.isSupportedCode(line.getNorwegianVatCode()),
                                            "Line " + lineNum + ": unknown VAT code '" + line.getNorwegianVatCode() + "'");
                                }
                            }
                        })

                .withRule("WARN-BuyerReference: Strongly recommended for Norwegian B2G",
                        doc -> {
                            // Logged as warning, not hard failure
                            if (isBlank(doc.getBuyerReference())) {
                                System.getLogger(InvoiceValidator.class.getName())
                                        .log(System.Logger.Level.WARNING,
                                                "buyerReference is missing — required for most Norwegian B2G invoices");
                            }
                        });
    }

    /**
     * Adds a custom validation rule to the chain.
     *
     * @param description human-readable rule description (shown in error messages)
     * @param rule        a {@link Consumer} that throws {@link InvoiceValidationException}
     *                    if the document fails this rule
     * @return this validator (fluent API)
     */
    public InvoiceValidator withRule(String description, Consumer<InvoiceDocument> rule) {
        rules.add(new ValidationRule(description, rule));
        return this;
    }

    /**
     * Executes all rules in the chain against the given document.
     * Collects ALL violations before throwing, so the caller sees everything at once.
     *
     * @param document the invoice to validate
     * @throws InvoiceValidationException if any rule fails, with all violations listed
     */
    public void validate(InvoiceDocument document) {
        List<String> violations = new ArrayList<>();

        for (ValidationRule rule : rules) {
            try {
                rule.validator().accept(document);
            } catch (InvoiceValidationException e) {
                violations.addAll(e.getViolations());
            } catch (IllegalArgumentException e) {
                violations.add("[" + rule.description() + "] " + e.getMessage());
            }
        }

        if (!violations.isEmpty()) {
            throw new InvoiceValidationException(violations);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvoiceValidationException(List.of(message));
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        require(!isBlank(value), fieldName + " must not be blank");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record ValidationRule(String description, Consumer<InvoiceDocument> validator) {}
}
