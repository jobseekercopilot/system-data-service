package com.jobseekercopilot.systemdata.normaliser;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.generated.postcodeiogateway.model.PostcodeLocation;
import com.jobseekercopilot.systemdata.util.DatasetIdGenerator;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostcodeNormaliserTest {
    private final PostcodeNormaliser normaliser = new PostcodeNormaliser(new DatasetIdGenerator());

    @Test
    void convertsPostcodeShapeToStableCanonicalLocation() {
        PostcodeLocation source = new PostcodeLocation()
                .postcode("RG1 1AA")
                .adminDistrict("Reading")
                .adminCounty("Berkshire")
                .region("South East")
                .country("England")
                .latitude(51.4543)
                .longitude(-0.9781);
        Instant retrieved = Instant.parse("2026-07-10T09:00:00Z");

        var first = normaliser.normalise(source, retrieved);
        var second = normaliser.normalise(source, retrieved);

        assertThat(first).isEqualTo(second);
        assertThat(first.postcodeDistrict()).isEqualTo("RG1");
        assertThat(first.postcodeArea()).isEqualTo("RG");
        assertThat(first.placeName()).isEqualTo("Reading");
        assertThat(first.sourceProvider()).isEqualTo("postcode-io-gateway");
    }
}
