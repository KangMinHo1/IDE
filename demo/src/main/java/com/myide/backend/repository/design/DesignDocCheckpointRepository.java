package com.myide.backend.repository.design;

import com.myide.backend.domain.design.DesignDocCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DesignDocCheckpointRepository extends JpaRepository<DesignDocCheckpoint, Long> {

    List<DesignDocCheckpoint> findByWorkspace_UuidOrderByCreatedAtDesc(String workspaceUuid);

    Optional<DesignDocCheckpoint> findByUuidAndWorkspace_Uuid(String uuid, String workspaceUuid);

    /**
     * 가장 최근 기록 하나.
     *
     * 방금 만들려는 것과 내용이 같은지 견주는 데 쓴다. 목록을 통째로
     * 읽으면 문서 전체를 담은 행을 스무 개 불러오게 된다.
     */
    Optional<DesignDocCheckpoint> findFirstByWorkspace_UuidOrderByCreatedAtDesc(String workspaceUuid);
}
