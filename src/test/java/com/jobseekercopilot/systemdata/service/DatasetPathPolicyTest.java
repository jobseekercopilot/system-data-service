package com.jobseekercopilot.systemdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobseekercopilot.systemdata.exception.DatasetGenerationException;
import com.jobseekercopilot.systemdata.util.SemanticVersionValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetPathPolicyTest {
    private final DatasetPathPolicy policy = new DatasetPathPolicy(new SemanticVersionValidator());

    @TempDir
    Path tempDirectory;

    @Test
    void resolvesAllowlistedDatasetAndVersionWithinRoot() {
        Path root = tempDirectory.resolve("repository");

        assertThat(policy.datasetVersion(root, "uk-software-demo", "1.2.3"))
                .isEqualTo(root.resolve("uk-software-demo/1.2.3").toAbsolutePath());
    }

    @Test
    void rejectsTraversalAbsoluteAndMalformedIdentifiersWithoutEchoingInput() {
        for (String datasetId : new String[]{"../outside", "/tmp/outside", "UPPER", "a/b", "a..b", ""}) {
            assertThatThrownBy(() -> policy.datasetVersion(tempDirectory, datasetId, "1.0.0"))
                    .isInstanceOf(DatasetGenerationException.class)
                    .hasMessage("Dataset path is invalid");
        }
        for (String version : new String[]{"../1.0.0", "/1.0.0", "1.0", "1.0.0/../../outside", ""}) {
            assertThatThrownBy(() -> policy.datasetVersion(tempDirectory, "safe-dataset", version))
                    .isInstanceOf(DatasetGenerationException.class)
                    .hasMessage("Dataset path is invalid");
        }
    }

    @Test
    void rejectsCandidatesOutsideAllowedRoots() {
        assertThatThrownBy(() -> policy.requireContained(
                tempDirectory.resolveSibling("outside"), tempDirectory.resolve("repository")))
                .isInstanceOf(DatasetGenerationException.class)
                .hasMessage("Dataset path is invalid");
    }

    @Test
    void rejectsSymlinkedRootsAndDatasetComponents() throws Exception {
        Path actual = Files.createDirectories(tempDirectory.resolve("actual"));
        Path linkedRoot = tempDirectory.resolve("linked-root");
        Files.createSymbolicLink(linkedRoot, actual);

        assertThatThrownBy(() -> policy.datasetVersion(linkedRoot, "safe-dataset", "1.0.0"))
                .isInstanceOf(DatasetGenerationException.class);

        Path repository = Files.createDirectories(tempDirectory.resolve("repository"));
        Files.createSymbolicLink(repository.resolve("safe-dataset"), actual);
        assertThatThrownBy(() -> policy.datasetVersion(repository, "safe-dataset", "1.0.0"))
                .isInstanceOf(DatasetGenerationException.class);
    }
}
