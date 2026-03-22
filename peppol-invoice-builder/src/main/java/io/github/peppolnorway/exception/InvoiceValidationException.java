package io.github.peppolnorway.exception;

import java.util.List;

/**
 * Thrown when an {@link io.github.peppolnorway.model.InvoiceDocument} fails
 * PEPPOL BIS Billing 3.0 validation rules.
 *
 * <p>All violations are collected before throwing, so callers see the
 * complete list of problems rather than fixing them one at a time.</p>
 */
public class InvoiceValidationException extends RuntimeException {

    private final List<String> violations;

    public InvoiceValidationException(List<String> violations) {
        super("Invoice validation failed with " + violations.size() + " violation(s):\n  - "
                + String.join("\n  - ", violations));
        this.violations = List.copyOf(violations);
    }

    /** Returns all collected validation violations. */
    public List<String> getViolations() {
        return violations;
    }
}
