package io.github.peppolnorway.spring.service;

import io.github.peppolnorway.api.DocumentType;
import io.github.peppolnorway.builder.EhfDocumentService;
import io.github.peppolnorway.model.InvoiceDocument;
import io.github.peppolnorway.spring.dto.ApiResponse;
import io.github.peppolnorway.spring.dto.InvoiceRequest;
import io.github.peppolnorway.spring.dto.SendResult;
import io.github.peppolnorway.spring.pipeline.PeppolTransmissionPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Application service for sending EHF 3.0 / PEPPOL BIS Billing 3.0 invoices.
 *
 * <p>Responsibilities (Single Responsibility Principle):</p>
 * <ol>
 *   <li>Map the API layer {@link InvoiceRequest} DTO to the domain model
 *       {@link InvoiceDocument}.</li>
 *   <li>Delegate XML generation to {@link EhfDocumentService} (validates + builds UBL XML).</li>
 *   <li>Hand the generated XML off to {@link PeppolTransmissionPipeline} for AS4 transmission.</li>
 * </ol>
 *
 * <p>This class is the only place that knows how to translate an API request into
 * a domain object, keeping the two layers independently evolvable.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EhfInvoiceService {

    private final EhfDocumentService documentService;
    private final PeppolTransmissionPipeline pipeline;

    /**
     * Sends an EHF commercial invoice (UNCL1001 type code 380).
     *
     * @param request the API request body
     * @return unified API response with send result or error details
     */
    public ApiResponse<ApiResponse.SendData> sendInvoice(InvoiceRequest request) {
        return sendDocument(request, DocumentType.INVOICE);
    }

    /**
     * Sends an EHF credit note / correction invoice (UNCL1001 type code 381).
     *
     * @param request the API request body (same structure as a regular invoice)
     * @return unified API response with send result or error details
     */
    public ApiResponse<ApiResponse.SendData> sendCreditNote(InvoiceRequest request) {
        return sendDocument(request, DocumentType.CREDIT_NOTE);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internal pipeline orchestration
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Orchestrates the full send pipeline for both invoice and credit note types.
     *
     * <p>Pipeline stages:</p>
     * <ol>
     *   <li>Map DTO → domain model</li>
     *   <li>Validate + generate UBL XML</li>
     *   <li>PEPPOL AS4 transmission (SMP lookup → SBDH wrap → AS4 send)</li>
     * </ol>
     */
    private ApiResponse<ApiResponse.SendData> sendDocument(InvoiceRequest request,
                                                            DocumentType documentType) {
        log.info("Sending {} | invoiceNo={} | sender={} | receiver={}",
                documentType.name(), request.getInvoiceNo(),
                request.getSenderOrgNumber(), request.getReceiverOrgNumber());

        try {
            // Step 1: DTO → domain model
            InvoiceDocument document = toDocument(request);

            // Step 2: Validate against PEPPOL business rules + generate UBL XML
            byte[] xmlBytes = documentType == DocumentType.INVOICE
                    ? documentService.buildInvoice(document)
                    : documentService.buildCreditNote(document);
            log.debug("UBL XML generated: {} bytes", xmlBytes.length);

            // Step 3: Transmit via PEPPOL AS4 (SMP lookup → SBDH wrapping → AS4 send)
            SendResult result = pipeline.send(xmlBytes, documentType,
                    request.getSenderOrgNumber(), request.getReceiverOrgNumber());

            return toApiResponse(result, request.getReceiverOrgNumber());

        } catch (io.github.peppolnorway.exception.InvoiceValidationException e) {
            log.warn("Invoice validation failed | invoiceNo={} | violations={}",
                    request.getInvoiceNo(), e.getViolations());
            return ApiResponse.fail("VALIDATION_FAILED",
                    "Invoice validation failed: " + String.join("; ", e.getViolations()));

        } catch (Exception e) {
            log.error("Unexpected error during send | invoiceNo={}", request.getInvoiceNo(), e);
            return ApiResponse.fail("INTERNAL_ERROR", "Internal error: " + e.getMessage());
        }
    }

    /**
     * Maps the API request DTO to the domain model.
     * This is the single place responsible for DTO-to-domain translation.
     */
    private InvoiceDocument toDocument(InvoiceRequest req) {
        return InvoiceDocument.builder()
                // Header
                .invoiceNo(req.getInvoiceNo())
                .invoiceDate(req.getInvoiceDate())
                .dueDate(req.getDueDate())
                .currency(req.getCurrency())
                .buyerReference(req.getBuyerReference())
                .orderNo(req.getOrderNo())
                .contractNo(req.getContractNo())
                .note(req.getNote())
                // Supplier
                .supplier(InvoiceDocument.Party.builder()
                        .orgNumber(req.getSupplierOrgNumber())
                        .name(req.getSupplierName())
                        .legalName(req.getSupplierLegalName())
                        .vatRegistered(req.isSupplierVatRegistered())
                        .address(InvoiceDocument.Address.builder()
                                .street(req.getSupplierAddress())
                                .postcode(req.getSupplierPostcode())
                                .city(req.getSupplierCity())
                                .countryCode(req.getSupplierCountryCode())
                                .build())
                        .phone(req.getSupplierPhone())
                        .email(req.getSupplierEmail())
                        .contactPerson(req.getSupplierContactPerson())
                        .build())
                // Customer
                .customer(InvoiceDocument.Party.builder()
                        .orgNumber(req.getCustomerOrgNumber())
                        .name(req.getCustomerName())
                        .address(InvoiceDocument.Address.builder()
                                .street(req.getCustomerAddress())
                                .postcode(req.getCustomerPostcode())
                                .city(req.getCustomerCity())
                                .countryCode(req.getCustomerCountryCode())
                                .build())
                        .build())
                // Delivery
                .deliveryDate(req.getDeliveryDate())
                .deliveryAddress(req.getDeliveryAddress() != null
                        ? InvoiceDocument.Address.builder()
                                .street(req.getDeliveryAddress())
                                .postcode(req.getDeliveryPostcode())
                                .city(req.getDeliveryCity())
                                .countryCode(req.getDeliveryCountryCode())
                                .build()
                        : null)
                // Payment
                .kid(req.getKid())
                .bankAccountNo(req.getBankAccountNo())
                .iban(req.getIban())
                .bic(req.getBic())
                // Monetary totals
                .netAmount(req.getNetAmount())
                .vatAmount(req.getVatAmount())
                .totalAmountInclVat(req.getTotalAmountInclVat())
                .rounding(req.getRounding())
                .payableAmount(req.getPayableAmount())
                // Attachments
                .attachments(req.getAttachments() == null ? List.of() :
                        req.getAttachments().stream()
                                .map(a -> InvoiceDocument.Attachment.builder()
                                        .filename(a.getFilename())
                                        .mimeType(a.getMimeType())
                                        .description(a.getDescription())
                                        .base64Content(a.getBase64Content())
                                        .build())
                                .collect(Collectors.toList()))
                // VAT breakdown
                .vatLines(req.getVatLines() == null ? List.of() :
                        req.getVatLines().stream()
                                .map(vl -> InvoiceDocument.VatBreakdown.builder()
                                        .norwegianVatCode(vl.getVatCode())
                                        .vatRate(vl.getVatRate())
                                        .baseAmount(vl.getBaseAmount())
                                        .vatAmount(vl.getVatAmount())
                                        .build())
                                .collect(Collectors.toList()))
                // Invoice lines
                .lines(req.getLines() == null ? List.of() :
                        req.getLines().stream()
                                .map(l -> InvoiceDocument.InvoiceLine.builder()
                                        .description(l.getDescription())
                                        .itemCode(l.getItemCode())
                                        .quantity(l.getQuantity())
                                        .unitCode(l.getUnitCode())
                                        .unitPrice(l.getUnitPrice())
                                        .discountPercent(l.getDiscountPercent())
                                        .discountAmount(l.getDiscountAmount())
                                        .lineAmount(l.getLineAmount())
                                        .norwegianVatCode(l.getVatCode())
                                        .vatRate(l.getVatRate())
                                        .build())
                                .collect(Collectors.toList()))
                .build();
    }

    /**
     * Converts a pipeline {@link SendResult} to the unified API response format.
     */
    private ApiResponse<ApiResponse.SendData> toApiResponse(SendResult result, String receiverOrg) {
        if (result.isSuccess()) {
            return ApiResponse.ok("Invoice successfully sent via PEPPOL AS4",
                    ApiResponse.SendData.builder()
                            .messageId(result.getMessageId())
                            .receiverOrg(receiverOrg)
                            .status(result.getStatus().name())
                            .build());
        }
        return ApiResponse.fail(result.getStatus().name(), result.getErrorDetail());
    }
}
