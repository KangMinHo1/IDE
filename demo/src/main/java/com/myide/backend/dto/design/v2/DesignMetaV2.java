package com.myide.backend.dto.design.v2;

public record DesignMetaV2(
        String projectSummary,
        TechStackV2 techStack,
        LegacyFlowSnapshotV2 legacyFlow
) {
    public DesignMetaV2 {
        projectSummary = projectSummary == null ? "" : projectSummary;
        techStack = techStack == null ? TechStackV2.empty() : techStack;
        legacyFlow = legacyFlow == null ? LegacyFlowSnapshotV2.empty() : legacyFlow;
    }

    public static DesignMetaV2 empty() {
        return new DesignMetaV2("", TechStackV2.empty(), LegacyFlowSnapshotV2.empty());
    }
}
