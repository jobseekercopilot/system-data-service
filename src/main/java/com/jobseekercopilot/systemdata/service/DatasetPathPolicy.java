package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.exception.DatasetGenerationException;
import com.jobseekercopilot.systemdata.util.SemanticVersionValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DatasetPathPolicy {
    private static final Pattern DATASET_ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private final SemanticVersionValidator versionValidator;

    public DatasetPathPolicy(SemanticVersionValidator versionValidator) {
        this.versionValidator = versionValidator;
    }

    public Path datasetRoot(Path repositoryRoot, String datasetId) {
        requireDatasetId(datasetId);
        return resolve(repositoryRoot, List.of(datasetId));
    }

    public void requireDatasetId(String datasetId) {
        if (datasetId == null || !DATASET_ID.matcher(datasetId).matches()) {
            throw invalid();
        }
    }

    public Path datasetVersion(Path repositoryRoot, String datasetId, String version) {
        requireDatasetId(datasetId);
        if (!versionValidator.isValid(version)) {
            throw invalid();
        }
        return resolve(repositoryRoot, List.of(datasetId, version));
    }

    public Path requireContained(Path candidate, Path... allowedRoots) {
        Path absolute = candidate.toAbsolutePath().normalize();
        for (Path allowedRoot : allowedRoots) {
            Path root = allowedRoot.toAbsolutePath().normalize();
            if (absolute.startsWith(root)) {
                rejectSymlinks(root, absolute);
                return absolute;
            }
        }
        throw invalid();
    }

    private Path resolve(Path repositoryRoot, List<String> components) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path resolved = root;
        for (String component : components) {
            resolved = resolved.resolve(component).normalize();
        }
        if (!resolved.startsWith(root)) {
            throw invalid();
        }
        rejectSymlinks(root, resolved);
        return resolved;
    }

    private void rejectSymlinks(Path root, Path candidate) {
        Path current = root.getRoot();
        for (Path component : root) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw invalid();
            }
        }
        Path relative = root.relativize(candidate);
        for (Path component : relative) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw invalid();
            }
        }
    }

    private DatasetGenerationException invalid() {
        return new DatasetGenerationException("Dataset path is invalid");
    }
}
