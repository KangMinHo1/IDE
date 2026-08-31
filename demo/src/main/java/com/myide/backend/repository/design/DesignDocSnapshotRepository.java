package com.myide.backend.repository.design;

import com.myide.backend.domain.design.DesignDocSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DesignDocSnapshotRepository extends JpaRepository<DesignDocSnapshot, Long> {

    Optional<DesignDocSnapshot> findByWorkspace_Uuid(String workspaceUuid);

    boolean existsByWorkspace_Uuid(String workspaceUuid);
}
