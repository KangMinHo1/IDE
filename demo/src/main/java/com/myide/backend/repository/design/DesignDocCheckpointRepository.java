package com.myide.backend.repository.design;

import com.myide.backend.domain.design.DesignDocCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DesignDocCheckpointRepository extends JpaRepository<DesignDocCheckpoint, Long> {

    List<DesignDocCheckpoint> findByWorkspace_UuidOrderByCreatedAtDesc(String workspaceUuid);

    Optional<DesignDocCheckpoint> findByUuidAndWorkspace_Uuid(String uuid, String workspaceUuid);
}
