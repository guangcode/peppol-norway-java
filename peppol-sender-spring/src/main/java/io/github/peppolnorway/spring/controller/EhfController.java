package io.github.peppolnorway.spring.controller;

import io.github.peppolnorway.api.DocumentType;
import io.github.peppolnorway.spring.dto.ApiResponse;
import io.github.peppolnorway.spring.dto.InvoiceRequest;
import io.github.peppolnorway.spring.pipeline.PeppolTransmissionPipeline;
import io.github.peppolnorway.spring.service.EhfInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for EHF 3.0 / PEPPOL BIS Billing 3.0 invoice transmission.
 *
 * <p>The controller's responsibility is minimal: accept HTTP requests, delegate
 * all business logic to {@link EhfInvoiceService} and downstream components,
 * and return the response. No business logic belongs here.</p>
 *
 * <h3>Endpoints</h3>
 * <pre>
 * POST /api/v1/ehf/invoice         Send commercial invoice (UNCL1001: 380)
 * POST /api/v1/ehf/credit-note     Send credit note / correction (UNCL1001: 381)
 * GET  /api/v1/ehf/lookup/{org}    Check if organization is registered in PEPPOL
 * GET  /api/v1/ehf/health          Service health check
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ehf")
@RequiredArgsConstructor
public class EhfController {

    private final EhfInvoiceService invoiceService;
    private final PeppolTransmissionPipeline pipeline;

    /**
     * Sends an EHF commercial invoice (UNCL1001 type code 380) via PEPPOL AS4.
     *
     * <p>See the project README for a complete request body example.</p>
     *
     * @param request the invoice request body
     * @return unified API response with transmission result or error details
     */
    @PostMapping("/invoice")
    public ResponseEntity<ApiResponse<ApiResponse.SendData>> sendInvoice(
            @RequestBody InvoiceRequest request) {

        log.info("Invoice send request received | invoiceNo={} | receiver={}",
                request.getInvoiceNo(), request.getReceiverOrgNumber());

        ApiResponse<ApiResponse.SendData> response = invoiceService.sendInvoice(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Sends an EHF credit note / correction invoice (UNCL1001 type code 381) via PEPPOL AS4.
     *
     * <p>The request body structure is identical to a regular invoice — the type code
     * is automatically set to 381 by the service layer.</p>
     *
     * @param request the credit note request body
     * @return unified API response with transmission result or error details
     */
    @PostMapping("/credit-note")
    public ResponseEntity<ApiResponse<ApiResponse.SendData>> sendCreditNote(
            @RequestBody InvoiceRequest request) {

        log.info("Credit note send request received | invoiceNo={} | receiver={}",
                request.getInvoiceNo(), request.getReceiverOrgNumber());

        ApiResponse<ApiResponse.SendData> response = invoiceService.sendCreditNote(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Queries the PEPPOL SMP to check whether a Norwegian organization number is
     * registered in the PEPPOL network and can receive electronic invoices.
     *
     * <p><strong>Strongly recommended</strong> to call this endpoint before every send
     * operation. Attempting to send to an unregistered receiver wastes certificate
     * signing resources and results in a transmission failure.</p>
     *
     * <p>Example: {@code GET /api/v1/ehf/lookup/881086591}</p>
     *
     * @param orgNumber 9-digit Norwegian organization number (no prefix)
     * @return lookup result indicating whether the organization is registered
     */
    @GetMapping("/lookup/{orgNumber}")
    public ResponseEntity<ApiResponse<ApiResponse.LookupData>> lookup(
            @PathVariable String orgNumber) {

        log.debug("PEPPOL SMP lookup request | org={}", orgNumber);

        boolean registered;
        try {
            registered = pipeline.isReceiverRegistered(orgNumber, DocumentType.INVOICE);
        } catch (Exception e) {
            log.warn("SMP lookup failed | org={} | reason={}", orgNumber, e.getMessage());
            registered = false;
        }

        ApiResponse.LookupData data = ApiResponse.LookupData.builder()
                .orgNumber(orgNumber)
                .registered(registered)
                .detail(registered
                        ? "Organization is registered in PEPPOL and can receive BIS Billing 3.0 invoices."
                        : "Organization is not registered in PEPPOL. Consider using an alternative delivery method.")
                .build();

        return ResponseEntity.ok(ApiResponse.ok("SMP lookup completed", data));
    }

    /**
     * Health check endpoint.
     *
     * <p>Returns HTTP 200 when Oxalis-NG has been successfully initialized
     * and the AS4 send capability is available. If Oxalis fails to initialize,
     * the application will not start and this endpoint will be unreachable.</p>
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Void>> health() {
        return ResponseEntity.ok(
                ApiResponse.ok("Oxalis-NG is ready. EHF transmission service is operational.", null));
    }
}
