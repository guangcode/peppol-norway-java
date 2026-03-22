package io.github.peppolnorway.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * UNCL5305 Tax Category Codes used in PEPPOL BIS Billing 3.0.
 *
 * <p>Each constant combines the PEPPOL category code with its Norwegian MVA-koder,
 * encoding the <b>Strategy pattern</b>: rather than a separate mapper class,
 * each category knows which Norwegian codes belong to it.</p>
 *
 * <p><b>Critical compliance rule (BR-CO-17):</b> Tax subtotals MUST be grouped by the
 * unique combination of category code AND rate. {@code Z}, {@code E}, and {@code O}
 * all produce 0.00% rate in the XML but represent legally distinct tax treatments
 * and MUST NEVER be merged.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * VatCategory category = VatCategory.fromNorwegianCode("31"); // → S
 * String peppolCode = category.getCode();                     // → "S"
 * boolean isZeroRated = category == VatCategory.Z;           // → false
 * }</pre>
 */
@Getter
public enum VatCategory {

    /**
     * Standard rate. Used for Norwegian standard (25%), reduced food (15%),
     * low transport/hotel (12%), and raw fish (11.11%) rates.
     * Norwegian MVA-koder: 3, 3U, 31, 31U, 32, 33, 33U
     */
    S("S", "Standard rate",
            "3", "3U", "31", "31U", "32", "33", "33U"),

    /**
     * Zero rated goods. Goods/services within VAT scope but taxed at 0%.
     * Examples: books, newspapers, used cars. Seller retains deduction rights.
     * Norwegian MVA-koder: 5, 5U
     */
    Z("Z", "Zero rated goods",
            "5", "5U"),

    /**
     * Exempt from tax. Transactions outside VAT scope with NO deduction rights.
     * Examples: healthcare, education, financial services.
     * Norwegian MVA-kode: 6
     */
    E("E", "Exempt from tax",
            "6"),

    /**
     * VAT Reverse Charge. Buyer is liable for calculating and reporting VAT.
     * Typically used for domestic construction services B2B.
     * Norwegian MVA-kode: 51
     */
    AE("AE", "VAT Reverse Charge",
            "51"),

    /**
     * Free export item. Goods/services exported outside Norwegian VAT jurisdiction.
     * Norwegian MVA-kode: 52
     */
    G("G", "Free export item, VAT not charged",
            "52"),

    /**
     * Services outside scope of tax. Completely outside VAT legislation.
     * Examples: pure financial transfers, fines, compensation payments.
     * Norwegian MVA-koder: 0, 7
     */
    O("O", "Services outside scope of tax",
            "0", "7"),

    /** VAT exempt for EEA intra-community supply. */
    K("K", "VAT exempt for EEA intra-community supply"),

    /** Canary Islands general indirect tax (IGIC). */
    L("L", "Canary Islands general indirect tax"),

    /** Tax for Ceuta and Melilla (IPSI). */
    M("M", "Tax for production, services and importation in Ceuta and Melilla"),

    /** Transferred VAT — Italy-specific. */
    B("B", "Transferred VAT (Italy)");

    // ─────────────────────────────────────────────────────────────────────
    // Static lookup map — built once at class load time
    // ─────────────────────────────────────────────────────────────────────

    private static final Map<String, VatCategory> CODE_MAP;

    static {
        Map<String, VatCategory> map = new HashMap<>();
        for (VatCategory cat : values()) {
            for (String code : cat.norwegianCodes) {
                map.put(code, cat);
            }
        }
        CODE_MAP = Collections.unmodifiableMap(map);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Instance fields
    // ─────────────────────────────────────────────────────────────────────

    private final String code;
    private final String description;
    private final String[] norwegianCodes;

    VatCategory(String code, String description, String... norwegianCodes) {
        this.code = code;
        this.description = description;
        this.norwegianCodes = norwegianCodes;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Static factory methods
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Maps a Norwegian MVA-kode to its PEPPOL {@link VatCategory}.
     *
     * <p><b>Fail-fast design:</b> throws immediately on unknown codes.
     * Silently defaulting to S or O would produce legally non-compliant invoices
     * that fail PEPPOL Schematron validation and could cause tax audit failures.</p>
     *
     * @param norwegianVatCode internal Norwegian MVA-kode (e.g. "3", "31", "5", "6")
     * @return the corresponding PEPPOL tax category
     * @throws IllegalArgumentException if the code is null or not in the mapping table
     */
    public static VatCategory fromNorwegianCode(String norwegianVatCode) {
        if (norwegianVatCode == null || !CODE_MAP.containsKey(norwegianVatCode)) {
            throw new IllegalArgumentException(String.format(
                    "Unknown Norwegian VAT code: '%s'. Valid codes: %s",
                    norwegianVatCode, CODE_MAP.keySet()));
        }
        return CODE_MAP.get(norwegianVatCode);
    }

    /**
     * Returns true if the given Norwegian MVA-kode is supported.
     */
    public static boolean isSupportedCode(String norwegianVatCode) {
        return norwegianVatCode != null && CODE_MAP.containsKey(norwegianVatCode);
    }

    /**
     * Returns an unmodifiable view of the full Norwegian code → category mapping.
     */
    public static Map<String, VatCategory> getMappingTable() {
        return CODE_MAP;
    }

    @Override
    public String toString() {
        return code + " (" + description + ")";
    }
}
