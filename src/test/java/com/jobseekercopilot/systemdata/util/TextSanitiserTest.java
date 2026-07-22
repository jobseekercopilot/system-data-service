package com.jobseekercopilot.systemdata.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextSanitiserTest {
    private final TextSanitiser sanitiser = new TextSanitiser();

    @Test
    void removesScriptsTagsControlCharactersAndMalformedWhitespace() {
        String result = sanitiser.clean("<p>Hello\u0000</p><script>alert('x')</script>   world");

        assertThat(result).isEqualTo("Hello world");
    }
}
