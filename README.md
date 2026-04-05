# peppol-norway-java

A complete Java library for generating and transmitting **EHF 3.0 / PEPPOL BIS Billing 3.0** invoices on the Norwegian PEPPOL network. No external UBL libraries required — just plain Java, Spring Boot, and oxalis-ng.

---

## Table of Contents

- [What is this?](#what-is-this)
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
  - [application.yml](#applicationyml)
  - [oxalis.conf](#oxalisconf)
  - [Certificate Setup](#certificate-setup)
- [API Reference](#api-reference)
  - [POST /api/v1/ehf/invoice](#post-apiv1ehfinvoice)
  - [POST /api/v1/ehf/credit-note](#post-apiv1ehfcredit-note)
  - [GET /api/v1/ehf/lookup/{orgNumber}](#get-apiv1ehflookuporgNumber)
  - [GET /api/v1/ehf/health](#get-apiv1ehfhealth)
- [Invoice Request Body](#invoice-request-body)
- [Norwegian VAT Code Mapping](#norwegian-vat-code-mapping)
- [Using the Builder Directly (without Spring)](#using-the-builder-directly-without-spring)
- [Design Patterns](#design-patterns)
- [Common Errors and Solutions](#common-errors-and-solutions)
- [License](#license)

---

## What is this?

Norwegian businesses are required to send invoices to government customers (B2G) via the **PEPPOL** network in the **EHF 3.0** format (a Norwegian profile of the European standard EN 16931 / UBL 2.1). This project gives you:

- **`peppol-invoice-builder`** — a pure-Java UBL 2.1 XML generator with Norwegian VAT code mapping. Zero dependency on ph-ubl or JAXB marshalling. Works standalone, no Spring required.
- **`peppol-sender-spring`** — a Spring Boot 3 REST API that wraps the builder and sends invoices via [oxalis-ng](https://github.com/OxalisCommunity/oxalis-ng) over PEPPOL AS4.
- **`peppol-api`** — shared enums: `VatCategory` (UNCL5305 codes) and `DocumentType` (PEPPOL document identifiers).

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 17+ |
| Maven | 3.8+ |
| Spring Boot | 3.2.5 |
| oxalis-ng | 1.2.2 |
| PEPPOL certificate | Production or TEST AP certificate (.p12) |

You need a valid PEPPOL Access Point certificate issued by an accredited authority (e.g. [Norstella](https://www.norstella.no/peppol/) for Norway). Without it, the application will start in `LOCAL` mode only.

---

## Project Structure

```
peppol-norway-java/
├── peppol-api/                    Pure interfaces and enums (no dependencies)
│   └── VatCategory.java           UNCL5305 tax category codes + Norwegian MVA-kode mapping
│   └── DocumentType.java          PEPPOL document type identifiers (invoice, credit note)
│
├── peppol-invoice-builder/        UBL XML generator (no Spring, no ph-ubl)
│   └── model/InvoiceDocument.java Immutable domain model (Builder pattern)
│   └── builder/UblXmlWriter.java  Abstract StAX-based XML writer (Template Method)
│   └── builder/InvoiceXmlWriter.java   Concrete: Invoice (type 380)
│   └── builder/CreditNoteXmlWriter.java Concrete: CreditNote (type 381)
│   └── builder/EhfDocumentService.java  Public API facade (validate + generate)
│   └── validator/InvoiceValidator.java  Chain of Responsibility validator
│
└── peppol-sender-spring/          Spring Boot REST API + AS4 transmission
    └── config/OxalisConfig.java   Guice↔Spring isolation bridge (Facade)
    └── pipeline/PeppolTransmissionPipeline.java  3-stage pipeline (SMP → SBDH → AS4)
    └── controller/EhfController.java  REST endpoints
    └── service/EhfInvoiceService.java  DTO → domain mapping + orchestration
```

---

## Quick Start

### 1. Build

```bash
git clone https://github.com/guangcode/oxalis-spring-boot-starter
cd peppol-norway-java
mvn clean package -DskipTests
```

### 2. Set up OXALIS_HOME

Create a directory for oxalis configuration, e.g. `~/oxalis/`:

```
~/oxalis/
├── oxalis.conf          (required — see Configuration section)
└── your-keystore.p12    (required — your PEPPOL AP certificate)
```

### 3. Run

```bash
cd peppol-sender-spring
java -DOXALIS_HOME=/path/to/oxalis -jar target/peppol-sender-spring-1.0.0.jar
```

Or set `oxalis.home` in `application.yml` (see [Configuration](#configuration)).

### 4. Test the health endpoint

```bash
curl http://localhost:8080/api/v1/ehf/health
```

Expected response:
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Oxalis-NG is ready. EHF transmission service is operational.",
  "timestamp": "2024-05-01T10:00:00Z"
}
```

---

## Configuration

### application.yml

```yaml
oxalis:
  home: "/path/to/oxalis"   # Sets OXALIS_HOME automatically at startup.
                             # Leave blank to use the OXALIS_HOME env variable
                             # or the default ~/.oxalis/ directory.
  mode: TEST                 # For logging and health output only.
                             # Actual mode is controlled by oxalis.conf.
  lookup-before-send: true   # Set to false to skip SMP lookup before each send
                             # (faster, but no pre-flight receiver check).
```

Priority order for OXALIS_HOME:
1. System environment variable `OXALIS_HOME`
2. JVM property `-DOXALIS_HOME=...`
3. `oxalis.home` in `application.yml`
4. Default: `~/.oxalis/`

### oxalis.conf

Minimum required configuration. Place this file inside your OXALIS_HOME directory.

```hocon
# PEPPOL AP Certificate
oxalis.keystore {
  path         = "your-keystore.p12"   # Relative to OXALIS_HOME, or absolute path
  password     = "your-password"
  key.alias    = "your-alias"
  key.password = "your-key-password"
}

# Network mode: TEST or PRODUCTION
# LOCAL is also valid for offline development (requires self-signed certificate)
oxalis.mode = TEST
```

### Certificate Setup

#### For production / real TEST network

You need a certificate issued by a PEPPOL-accredited Certification Authority. In Norway, contact [Norstella](https://www.norstella.no/peppol/).

Place the `.p12` file in your OXALIS_HOME directory and update `oxalis.conf` accordingly.

#### For local development (offline, no network)

Generate a self-signed certificate — this is the only certificate type that works with `oxalis.mode = LOCAL`:

```bash
keytool -genkeypair \
  -alias peppol-ap \
  -keyalg RSA -keysize 2048 \
  -validity 3650 \
  -storetype PKCS12 \
  -keystore local-test.p12 \
  -storepass test1234 \
  -keypass test1234 \
  -dname "CN=LOCAL-TEST, OU=PEPPOL LOCAL AP, O=Test, C=NO"
```

Then in `oxalis.conf`:

```hocon
oxalis.keystore {
  path         = "local-test.p12"
  password     = "test1234"
  key.alias    = "peppol-ap"
  key.password = "test1234"
}
oxalis.mode = LOCAL
```

> **Note:** In `LOCAL` mode, the SMP lookup stage is skipped automatically and no actual AS4 transmission occurs. This is useful for verifying that your invoice XML is generated correctly before going live.

---

## API Reference

### POST /api/v1/ehf/invoice

Validates and sends a PEPPOL BIS Billing 3.0 commercial invoice (UNCL1001 type code 380).

**Request:** `Content-Type: application/json` — see [Invoice Request Body](#invoice-request-body)

**Response:**
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Invoice successfully sent via PEPPOL AS4",
  "data": {
    "messageId": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
    "receiverOrg": "881086591",
    "status": "SUCCESS"
  },
  "timestamp": "2024-05-01T10:00:00Z"
}
```

### POST /api/v1/ehf/credit-note

Sends a credit note / correction invoice (UNCL1001 type code 381). The request body is identical to the invoice endpoint — the type code is applied automatically.

### GET /api/v1/ehf/lookup/{orgNumber}

Queries the PEPPOL SMP to check whether an organization is registered and can receive electronic invoices.

**Strongly recommended** to call this before sending, to avoid wasting certificate signing resources on unregistered receivers.

```bash
curl http://localhost:8080/api/v1/ehf/lookup/881086591
```

**Response:**
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "SMP lookup completed",
  "data": {
    "orgNumber": "881086591",
    "registered": true,
    "detail": "Organization is registered in PEPPOL and can receive BIS Billing 3.0 invoices."
  }
}
```

### GET /api/v1/ehf/health

Returns HTTP 200 when Oxalis-NG has initialized successfully and the AS4 transmission capability is available.

---

## Invoice Request Body

Full example with all fields:

```json
{
  "senderOrgNumber":   "336880697",
  "receiverOrgNumber": "881086591",

  "invoiceNo":       "INV-2024-001",
  "invoiceDate":     "2024-05-01",
  "dueDate":         "2024-05-31",
  "currency":        "NOK",
  "buyerReference":  "Buyer Cost Center 4512",
  "orderNo":         "PO-2024-0099",
  "contractNo":      "CONTRACT-2024-007",
  "note":            "Payment terms: 30 days net",

  "supplierOrgNumber":    "336880697",
  "supplierName":         "Acme Accounting AS",
  "supplierLegalName":    "ACME ACCOUNTING AS",
  "supplierAddress":      "Supplier Street 1",
  "supplierPostcode":     "0150",
  "supplierCity":         "Oslo",
  "supplierCountryCode":  "NO",
  "supplierPhone":        "91175475",
  "supplierEmail":        "invoice@acme.no",
  "supplierContactPerson": "Jane Smith",
  "supplierVatRegistered": true,

  "customerOrgNumber":   "881086591",
  "customerName":        "Oslo Municipality",
  "customerAddress":     "Municipal Street 5",
  "customerPostcode":    "0160",
  "customerCity":        "Oslo",
  "customerCountryCode": "NO",

  "deliveryDate":        "2024-04-30",
  "deliveryAddress":     "Delivery Road 10",
  "deliveryPostcode":    "0180",
  "deliveryCity":        "Oslo",
  "deliveryCountryCode": "NO",

  "kid":           "01010300130836",
  "bankAccountNo": "15037715716",
  "iban":          "NO1815037715716",
  "bic":           "DNBANOKKXXX",

  "netAmount":          108.00,
  "vatAmount":           25.75,
  "totalAmountInclVat": 133.75,
  "rounding":             0.25,
  "payableAmount":       134.00,

  "attachments": [
    {
      "filename":      "INV-2024-001.pdf",
      "mimeType":      "application/pdf",
      "description":   "Commercial invoice",
      "base64Content": "SlZCRVJpMHhMalVOQ2lXRGt2ci4uLg=="
    }
  ],

  "vatLines": [
    {
      "vatCode":     "3",
      "vatRate":     25.00,
      "baseAmount": 103.00,
      "vatAmount":   25.75
    },
    {
      "vatCode":    "5",
      "vatRate":     0.00,
      "baseAmount":  5.00,
      "vatAmount":   0.00
    }
  ],

  "lines": [
    {
      "description": "Accounting services, April 2024",
      "itemCode":    "SRV-001",
      "quantity":    1.0000,
      "unitCode":    "HUR",
      "unitPrice":   100.0000,
      "discountPercent": 10.0000,
      "discountAmount":  10.00,
      "lineAmount":      90.00,
      "vatCode":         "3",
      "vatRate":         25.00
    },
    {
      "description": "Reference book",
      "itemCode":    "BOOK-42",
      "quantity":    1.0000,
      "unitCode":    "EA",
      "unitPrice":   5.0000,
      "lineAmount":  5.00,
      "vatCode":     "5",
      "vatRate":     0.00
    }
  ]
}
```

### Key field notes

| Field | Notes |
|---|---|
| `buyerReference` | Effectively mandatory for B2G. Norwegian municipalities route invoices to internal departments using this field. Missing it causes silent rejection. |
| `supplierVatRegistered` | When `true`, generates `NO{orgNumber}MVA` in the VAT registration ID. |
| `contractNo` | Maps to `<cac:ContractDocumentReference>`. Optional. |
| `kid` | Norwegian OCR payment reference (Kundeidentifikasjonsnummer). Maps to UBL `PaymentID`. |
| `iban` + `bic` | When both are provided, a second `<cac:PaymentMeans>` block is generated with IBAN and BIC/SWIFT for cross-border payments. |
| `rounding` | Maps to `<cbc:PayableRoundingAmount>`. Use when `totalAmountInclVat` does not round to a whole number. |
| `vatCode` | Norwegian internal MVA-kode — see the table below. Must be set on both `vatLines` and individual `lines`. |
| `unitCode` | Must follow UN/ECE Recommendation 20. Common: `EA` (each), `HUR` (hour), `KGM` (kilogram), `MTR` (metre). |

---

## Norwegian VAT Code Mapping

The library maps Norwegian internal MVA-koder to the PEPPOL UNCL5305 tax category codes required in the XML.

| MVA-kode | PEPPOL Category | Description | Norwegian law |
|---|---|---|---|
| 3, 3U, 31, 31U, 32, 33, 33U | **S** | Standard rate (25%, 15%, 12%, 11.11%) | MVA-loven §§ 5, 5a |
| 5, 5U | **Z** | Zero-rated goods (within VAT scope, 0%) | Books, newspapers, used cars |
| 51 | **AE** | Reverse charge (buyer pays VAT) | Domestic construction B2B |
| 52 | **G** | Free export (outside Norwegian VAT jurisdiction) | Export of goods/services |
| 6 | **E** | Exempt from tax (no deduction right) | Healthcare, education, finance |
| 0, 7 | **O** | Outside scope of VAT | Financial transfers, fines |

> **CRITICAL — BR-CO-17:** Z, E, and O all produce `0.00%` in the XML but represent **legally distinct** tax treatments. They MUST NEVER be merged into a single `<cac:TaxSubtotal>`. The library enforces this rule at validation time.

---

## Using the Builder Directly (without Spring)

The `peppol-invoice-builder` module has no Spring dependency. You can use it standalone in any Java project:

```xml
<dependency>
    <groupId>io.github.peppolnorway</groupId>
    <artifactId>peppol-invoice-builder</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
import io.github.peppolnorway.builder.EhfDocumentService;
import io.github.peppolnorway.model.InvoiceDocument;
import io.github.peppolnorway.model.InvoiceDocument.*;

import java.math.BigDecimal;
import java.time.LocalDate;

EhfDocumentService service = new EhfDocumentService();

InvoiceDocument invoice = InvoiceDocument.builder()
    .invoiceNo("INV-2024-001")
    .invoiceDate(LocalDate.now())
    .dueDate(LocalDate.now().plusDays(30))
    .currency("NOK")
    .buyerReference("Cost Center 4512")
    .orderNo("PO-2024-0099")
    .supplier(Party.builder()
        .orgNumber("336880697")
        .name("Acme Accounting AS")
        .legalName("ACME ACCOUNTING AS")
        .vatRegistered(true)
        .address(Address.builder()
            .street("Supplier Street 1")
            .postcode("0150")
            .city("Oslo")
            .build())
        .email("invoice@acme.no")
        .build())
    .customer(Party.builder()
        .orgNumber("881086591")
        .name("Oslo Municipality")
        .address(Address.builder()
            .street("Municipal Street 5")
            .postcode("0160")
            .city("Oslo")
            .build())
        .build())
    .deliveryDate(LocalDate.now())
    .kid("01010300130836")
    .bankAccountNo("15037715716")
    .netAmount(new BigDecimal("1000.00"))
    .vatAmount(new BigDecimal("250.00"))
    .totalAmountInclVat(new BigDecimal("1250.00"))
    .payableAmount(new BigDecimal("1250.00"))
    .vatLine(VatBreakdown.builder()
        .norwegianVatCode("3")            // Standard 25%
        .vatRate(new BigDecimal("25.00"))
        .baseAmount(new BigDecimal("1000.00"))
        .vatAmount(new BigDecimal("250.00"))
        .build())
    .line(InvoiceLine.builder()
        .description("Consulting Services")
        .itemCode("CONSULT-001")
        .quantity(new BigDecimal("10.00"))
        .unitCode("HUR")                  // HUR = Hour (UN/ECE Rec 20)
        .unitPrice(new BigDecimal("100.00"))
        .lineAmount(new BigDecimal("1000.00"))
        .norwegianVatCode("3")
        .vatRate(new BigDecimal("25.00"))
        .build())
    .build();

// Validates against PEPPOL business rules, then generates UBL 2.1 XML
byte[] xmlBytes = service.buildInvoice(invoice);

// Write to file, send to your own AS4 stack, etc.
Files.write(Path.of("invoice.xml"), xmlBytes);
```

---

## Design Patterns

| Pattern | Where | Purpose |
|---|---|---|
| **Template Method** | `UblXmlWriter` → `InvoiceXmlWriter` / `CreditNoteXmlWriter` | Fixed XML generation algorithm; subclasses supply only the 5 varying values (root element, namespace, type code, line element name, quantity element name). |
| **Strategy** | `DocumentType` enum | Carries all PEPPOL routing identifiers. The pipeline selects the right identifiers without any if/switch. |
| **Builder** | `InvoiceDocument` and all nested types | Ensures domain objects are fully populated and immutable. |
| **Chain of Responsibility** | `InvoiceValidator` | Independent validation rules chained together; all violations collected before throwing. |
| **Facade** | `EhfDocumentService`, `OxalisConfig` | `EhfDocumentService` hides the validator + writer internals. `OxalisConfig` isolates the Guice container from the Spring context. |
| **Factory Method** | `SendResult.success()`, `SendResult.transmissionFailed()`, etc. | Clear, semantic construction of result objects without boolean flags. |
| **Pipeline** | `PeppolTransmissionPipeline` | Three explicit stages (SMP lookup → SBDH wrap → AS4 send) with clean error handling per stage. |

---

## Common Errors and Solutions

### `Unable to detect mode for certificate`

The certificate loaded by Oxalis was not recognized. The three possible causes:

| Error in log | Cause | Fix |
|---|---|---|
| `Certificate is revoked` | The certificate has been revoked by OpenPEPPOL | Obtain a new certificate from your CA |
| `Certificate should be self-signed` | You set `oxalis.mode = LOCAL` but the certificate is CA-signed | Generate a self-signed certificate with `keytool` (see [Certificate Setup](#certificate-setup)) |
| `Validation of subject principal(CN) failed` | Wrong mode for this certificate type | Use `TEST` for test CA certificates, `PRODUCTION` for production certificates |

### `Unable to initiate 'class BdxlLocator'`

This happens in `LOCAL` mode because BDXL DNS lookup is disabled. The `LOCAL` mode is for offline XML generation testing only and cannot perform real SMP lookups. If you need to test against the PEPPOL test network, use a valid TEST certificate with `oxalis.mode = TEST`.

### `NoSuchMethodError: DoubleHistogramBuilder.setExplicitBucketBoundariesAdvice`

OpenTelemetry version conflict between oxalis-ng and Spring Boot BOM. Fix by forcing the OpenTelemetry version in `peppol-sender-spring/pom.xml`:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.50.0</version>
</dependency>
<!-- also add: opentelemetry-context, opentelemetry-sdk, opentelemetry-sdk-common,
     opentelemetry-sdk-trace, opentelemetry-sdk-metrics, opentelemetry-sdk-logs -->
```

### Invoice validation fails

All PEPPOL business rule violations are collected before throwing, so the error message lists every problem at once. Common causes:

- `invoiceNo must not be blank` — BR-02: invoice number is required
- `At least one VAT breakdown line is required` — BR-CO-17: at least one `vatLines` entry must be present
- `Unknown Norwegian VAT code: '99'` — the `vatCode` value is not in the supported MVA-kode table
- `Line 1: unitCode is required` — every invoice line must have a UN/ECE Rec 20 unit code

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

*Built with ❤️ for the Norwegian EHF ecosystem.*
