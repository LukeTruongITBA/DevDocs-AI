
package com.devdocs.ai.ingestdynamic.version;

public record VersionDetectionResult(
        String version,
        DetectionSource source
) {
    public VersionDetectionResult {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(
                    "Detected version must not be blank."
            );
        }

        if (source == null) {
            throw new IllegalArgumentException(
                    "Detection source must not be null."
            );
        }

        version = version.trim();
    }

    public enum DetectionSource {
        URL,
        HTML,
        USER_INPUT
    }
}
