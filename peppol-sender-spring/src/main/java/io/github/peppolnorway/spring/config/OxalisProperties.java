package io.github.peppolnorway.spring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration properties for the Oxalis integration.
 * Binds to {@code oxalis.*} in {@code application.yml}.
 *
 * <p>These properties complement the native {@code oxalis.conf} file
 * (which Oxalis reads directly from OXALIS_HOME). Use this class for
 * Spring-side configuration such as startup timeout and behavior flags.</p>
 */
@Data
@ConfigurationProperties(prefix = "oxalis")
public class OxalisProperties {

    /**
     * Path to the OXALIS_HOME directory.
     * If set here, the JVM system property {@code OXALIS_HOME} is set automatically
     * at startup, so callers don't need to set it externally.
     *
     * <p>Priority: System env {@code OXALIS_HOME} > JVM property {@code -DOXALIS_HOME} > this value.</p>
     */
    private String home;

    /**
     * PEPPOL network mode. Must match the value in oxalis.conf.
     * Used for logging and health check output only — the actual mode
     * is controlled by oxalis.conf.
     */
    private String mode = "TEST";

    /**
     * If true (default), calls SMP lookup before every transmission to verify
     * the receiver is registered. Set to false to skip lookup for performance.
     */
    private boolean lookupBeforeSend = true;
}
