package io.github.peppolnorway.spring.config;

import io.github.peppolnorway.builder.EhfDocumentService;
import lombok.extern.slf4j.Slf4j;
import network.oxalis.ng.api.lookup.LookupService;
import network.oxalis.ng.api.outbound.TransmissionService;
import network.oxalis.ng.outbound.OxalisOutboundComponent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that bridges <b>oxalis-ng (Google Guice)</b> with the
 * <b>Spring ApplicationContext</b>, implementing the <b>Facade pattern</b>.
 *
 * <h3>The core problem</h3>
 * <p>oxalis-ng uses Google Guice 7 for dependency injection internally.
 * Spring Boot 3 uses its own IoC container. Running two DI containers
 * simultaneously causes class-loader conflicts, circular dependency issues,
 * and Jakarta annotation resolution failures.</p>
 *
 * <h3>The solution: strict isolation</h3>
 * <p>This class is the <em>only place in the entire application that knows
 * Guice exists</em>. {@link OxalisOutboundComponent} is instantiated here
 * as a Spring {@code @Bean}, encapsulating the Guice Injector entirely
 * inside its implementation. Spring receives clean, framework-agnostic
 * interfaces ({@link TransmissionService}, {@link LookupService}) with
 * no Guice types leaking out.</p>
 *
 * <pre>
 * ┌─ Spring ApplicationContext ──────────────────────────────────────┐
 * │  OxalisConfig (@Configuration)                                   │
 * │    └── OxalisOutboundComponent (@Bean, singleton)                │
 * │           ╔══════════════════════════════════╗                   │
 * │           ║  Guice Injector (internal only)  ║                   │
 * │           ║    ├── TransmissionService        ║                   │
 * │           ║    ├── LookupService              ║                   │
 * │           ║    └── ... (all Oxalis internals) ║                   │
 * │           ╚══════════════════════════════════╝                   │
 * │    ├── TransmissionService (@Bean) ← extracted for Spring        │
 * │    └── LookupService (@Bean)       ← extracted for Spring        │
 * └──────────────────────────────────────────────────────────────────┘
 *       ↕ No Guice types visible below this line
 * ┌─ Business Layer ─────────────────────────────────────────────────┐
 * │  PeppolTransmissionPipeline (@Service)                           │
 * │  EhfController (@RestController)                                 │
 * └──────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Startup note</h3>
 * <p>Oxalis initialization takes 5–15 seconds on first start (certificate
 * loading, CXF/AS4 stack setup). This is normal and happens once.</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OxalisProperties.class)
public class OxalisConfig {

    /**
     * Initializes the Oxalis Guice container as a Spring-managed singleton.
     *
     * <p>If {@code oxalis.home} is configured in {@code application.yml},
     * the system property is set before Oxalis tries to detect its home directory.</p>
     */
    @Bean
    public OxalisOutboundComponent oxalisOutboundComponent(OxalisProperties properties) {
        // Apply home directory from Spring config if not already set externally
        applyOxalisHome(properties.getHome());

        log.info("Initializing oxalis-ng outbound component [mode={}]...", properties.getMode());
        long start = System.currentTimeMillis();

        OxalisOutboundComponent component = new OxalisOutboundComponent();

        log.info("oxalis-ng initialized in {}ms. PEPPOL network: {}",
                System.currentTimeMillis() - start, properties.getMode());
        return component;
    }

    /**
     * Exposes {@link TransmissionService} as a Spring bean.
     * Use this to send SBDH-wrapped PEPPOL documents via AS4.
     */
    @Bean
    public TransmissionService transmissionService(OxalisOutboundComponent component) {
        return component.getTransmissionService();
    }

    /**
     * Exposes {@link LookupService} as a Spring bean.
     * Use this to query the PEPPOL SMP before sending.
     */
    @Bean
    public LookupService lookupService(OxalisOutboundComponent component) {
        return component.getLookupService();
    }

    /**
     * Registers the invoice/credit note document builder as a Spring bean.
     * This bean is from the framework-agnostic {@code peppol-invoice-builder} module.
     */
    @Bean
    public EhfDocumentService ehfDocumentService() {
        return new EhfDocumentService();
    }

    // ─────────────────────────────────────────────────────────────────────

    private void applyOxalisHome(String configuredHome) {
        if (configuredHome == null || configuredHome.isBlank()) return;

        // Only set if not already provided externally
        if (System.getenv("OXALIS_HOME") == null && System.getProperty("OXALIS_HOME") == null) {
            System.setProperty("OXALIS_HOME", configuredHome);
            log.info("Set OXALIS_HOME from application configuration: {}", configuredHome);
        }
    }
}
