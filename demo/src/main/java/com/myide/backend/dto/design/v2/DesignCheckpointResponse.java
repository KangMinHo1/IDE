package com.myide.backend.dto.design.v2;

import com.myide.backend.domain.design.DesignDocCheckpoint;

import java.time.LocalDateTime;

/**
 * @param summary 그 시점에 문서에 담겨 있던 것. 예전 기록에는 없어서 빌 수 있다.
 */
public record DesignCheckpointResponse(
        String id,
        String label,
        String summary,
        String createdByNickname,
        LocalDateTime createdAt
) {
    public static DesignCheckpointResponse from(DesignDocCheckpoint checkpoint) {
        return new DesignCheckpointResponse(
                checkpoint.getUuid(),
                checkpoint.getLabel(),
                checkpoint.getSummary() == null ? "" : checkpoint.getSummary(),
                checkpoint.getCreatedBy() == null ? "" : checkpoint.getCreatedBy().getNickname(),
                checkpoint.getCreatedAt()
        );
    }
}
