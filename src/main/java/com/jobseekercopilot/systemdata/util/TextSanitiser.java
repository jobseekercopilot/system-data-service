package com.jobseekercopilot.systemdata.util;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TextSanitiser {
    private static final Pattern SCRIPT_STYLE = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    public String clean(String value) {
        if (value == null) {
            return null;
        }
        String withoutScripts = SCRIPT_STYLE.matcher(value).replaceAll(" ");
        String withoutTags = TAGS.matcher(withoutScripts).replaceAll(" ");
        String withoutControl = CONTROL.matcher(withoutTags).replaceAll(" ");
        return SPACE.matcher(withoutControl).replaceAll(" ").trim();
    }

    public boolean usable(String value) {
        return clean(value) != null && clean(value).length() >= 3;
    }
}
