package com.myide.backend.dto.design.v2;

import com.myide.backend.domain.design.DesignDocCheckpoint;

import java.time.LocalDateTime;

public record DesignCheckpointResponse(
        String id,
        String label,
        String createdByNickname,
        LocalDateTime createdAt
) {
    public static DesignCheckpointResponse from(DesignDocCheckpoint checkpoint) {
        return new DesignCheckpointResponse(
                checkpoint.getUuid(),
                checkpoint.getLabel(),
                checkpoint.getCreatedBy() == null ? "" : checkpoint.getCreatedBy().getNickname(),
                checkpoint.getCreatedAt()
        );
    }
}
