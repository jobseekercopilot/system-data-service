package com.jobseekercopilot.systemdata.normaliser;

import com.jobseekercopilot.generated.postcodeiogateway.model.PostcodeLocation;
import com.jobseekercopilot.systemdata.model.DemoLocation;
import com.jobseekercopilot.systemdata.util.DatasetIdGenerator;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class PostcodeNormaliser {
    private final DatasetIdGenerator idGenerator;

    public PostcodeNormaliser(DatasetIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    public DemoLocation normalise(PostcodeLocation location, Instant retrievedAt) {
        String postcode = location.getPostcode();
        String district = postcode == null ? null : postcode.replaceAll("\\s.*$", "");
        String area = district == null ? null : district.replaceAll("[0-9].*$", "");
        String placeName = location.getAdminDistrict();
        return new DemoLocation(
                idGenerator.stableLocationId(postcode, placeName),
                postcode,
                district,
                area,
                placeName,
                location.getAdminDistrict(),
                location.getAdminCounty(),
                location.getRegion(),
                location.getCountry(),
                location.getLatitude(),
                location.getLongitude(),
                "postcode-io-gateway",
                retrievedAt);
    }
}
