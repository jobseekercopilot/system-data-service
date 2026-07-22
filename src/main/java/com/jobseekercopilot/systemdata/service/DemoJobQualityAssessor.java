package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.util.TextSanitiser;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DemoJobQualityAssessor {
    private final TextSanitiser textSanitiser;

    public DemoJobQualityAssessor(TextSanitiser textSanitiser) {
        this.textSanitiser = textSanitiser;
    }

    public boolean suitable(String title, String company, String location, String description,
                            Integer salaryMinimum, Integer salaryMaximum, String salaryPeriod, String datePosted) {
        return textSanitiser.usable(title)
                && textSanitiser.usable(company)
                && textSanitiser.usable(description)
                && softwareRole(title, description)
                && !paidTraining(title, description)
                && !invalidSalary(salaryMinimum, salaryMaximum, salaryPeriod)
                && !nonUk(location, description)
                && !stale(datePosted);
    }

    public double qualityScore(String title, String company, String location, String description,
                               Integer salaryMinimum, Integer salaryMaximum, String salaryPeriod, String datePosted,
                               Double latitude, Double longitude) {
        double score = 0.15;
        if (textSanitiser.usable(title)) score += 0.12;
        if (textSanitiser.usable(company)) score += 0.08;
        if (textSanitiser.usable(location)) score += 0.08;
        if (StringUtils.hasText(description)) {
            score += Math.min(0.22, description.length() / 1200.0 * 0.22);
        }
        if (softwareRole(title, description)) score += 0.12;
        if (salaryMinimum != null || salaryMaximum != null) score += 0.08;
        if (latitude != null && longitude != null) score += 0.06;
        if (!stale(datePosted)) score += 0.05;
        if (paidTraining(title, description)) score -= 0.35;
        if (invalidSalary(salaryMinimum, salaryMaximum, salaryPeriod)) score -= 0.3;
        if (nonUk(location, description)) score -= 0.2;
        return Math.max(0.0, Math.min(1.0, Math.round(score * 100.0) / 100.0));
    }

    public boolean paidTraining(String title, String description) {
        String text = combined(title, description);
        return text.contains("training programme")
                || text.contains("training program")
                || text.contains("placement programme")
                || text.contains("self-funded")
                || text.contains("fee charging")
                || text.contains("fees apply")
                || text.contains("pay for your training");
    }

    public boolean invalidSalary(Integer salaryMinimum, Integer salaryMaximum, String salaryPeriod) {
        if (!"YEAR".equalsIgnoreCase(value(salaryPeriod))) {
            return false;
        }
        Integer low = salaryMinimum == null ? salaryMaximum : salaryMinimum;
        Integer high = salaryMaximum == null ? salaryMinimum : salaryMaximum;
        if (low == null && high == null) {
            return false;
        }
        return (high != null && high > 300_000) || (low != null && low > 0 && low < 10_000);
    }

    private boolean softwareRole(String title, String description) {
        String text = combined(title, description);
        return text.contains("software")
                || text.contains("developer")
                || text.contains("engineer")
                || text.contains("java")
                || text.contains("spring boot")
                || text.contains("angular")
                || text.contains("backend")
                || text.contains("full stack")
                || text.contains("typescript");
    }

    private boolean nonUk(String location, String description) {
        String text = combined(location, description);
        return text.contains("united states")
                || text.contains(" usa")
                || text.contains("canada")
                || text.contains("australia")
                || text.contains("india");
    }

    private boolean stale(String datePosted) {
        if (!StringUtils.hasText(datePosted)) {
            return false;
        }
        String value = datePosted.length() >= 10 ? datePosted.substring(0, 10) : datePosted;
        try {
            return LocalDate.parse(value).isBefore(LocalDate.now().minusDays(90));
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private String combined(String first, String second) {
        return (value(first) + " " + value(second)).toLowerCase(Locale.ROOT);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
