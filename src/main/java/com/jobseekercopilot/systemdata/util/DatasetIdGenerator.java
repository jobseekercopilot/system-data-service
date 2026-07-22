package com.jobseekercopilot.systemdata.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DatasetIdGenerator {

    public String slug(String value) {
        String normalised = Normalizer.normalize(value == null ? "unknown" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.UK)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalised.isBlank() ? "unknown" : normalised;
    }

    public String stableJobId(String provider, String externalReference, String title, String company, String location) {
        return "job-" + sha256(String.join("|", nullSafe(provider), nullSafe(externalReference), nullSafe(title), nullSafe(company), nullSafe(location))).substring(0, 16);
    }

    public String stableLocationId(String postcode, String placeName) {
        return "loc-" + sha256(String.join("|", nullSafe(postcode), nullSafe(placeName))).substring(0, 16);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
