package com.myide.backend.controller;

import com.myide.backend.dto.design.v2.DesignCheckpointRequest;
import com.myide.backend.dto.design.v2.DesignCheckpointResponse;
import com.myide.backend.dto.design.v2.DesignDocResponse;
import com.myide.backend.dto.design.v2.DesignDocWriteRequest;
import com.myide.backend.service.design.DesignSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 설계 문서 v2 저장본 API.
 *
 * 클라이언트가 지켜야 하는 순서가 있다.
 *   1) GET  /design/doc      로 저장본을 받는다
 *   2) needsSeed 면 예전 데이터를 읽어 문서를 만들고 POST /design/doc/seed
 *   3) 그 다음에야 WebSocket 에 접속한다
 *
 * 3번을 2번보다 먼저 하면 시드 1회 보장이 깨진다. 먼저 접속한 클라이언트가
 * 자기 문서를 이미 방에 뿌려 놓기 때문에, 서버가 나중 시드를 거절해도
 * 내용은 이미 두 벌이 되어 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DesignSnapshotController {

    private final DesignSnapshotService designSnapshotService;

    @GetMapping("/workspaces/{workspaceId}/design/doc")
    public DesignDocResponse getDoc(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal Long userId
    ) {
        return designSnapshotService.getDoc(workspaceId, userId);
    }

    /**
     * 저장본이 없을 때만 받아들인다.
     * 이미 있으면 409 와 함께 기존 문서를 돌려주며, 클라이언트는 자기가 만든
     * 문서를 버리고 받은 것을 써야 한다.
     */
    @PostMapping("/workspaces/{workspaceId}/design/doc/seed")
    public ResponseEntity<DesignDocResponse> seed(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal Long userId,
            @RequestBody DesignDocWriteRequest request
    ) {
        DesignSnapshotService.SeedOutcome outcome =
                designSnapshotService.seed(workspaceId, userId, request);

        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(outcome.doc());
    }

    @PutMapping("/workspaces/{workspaceId}/design/doc/snapshot")
    public DesignDocResponse saveSnapshot(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal Long userId,
            @RequestBody DesignDocWriteRequest request
    ) {
        return designSnapshotService.save(workspaceId, userId, request);
    }

    @GetMapping("/workspaces/{workspaceId}/design/doc/checkpoints")
    public List<DesignCheckpointResponse> listCheckpoints(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal Long userId
    ) {
        return designSnapshotService.listCheckpoints(workspaceId, userId);
    }

    @PostMapping("/workspaces/{workspaceId}/design/doc/checkpoints")
    @ResponseStatus(HttpStatus.CREATED)
    public DesignCheckpointResponse createCheckpoint(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal Long userId,
            @RequestBody(required = false) DesignCheckpointRequest request
    ) {
        String label = request == null ? null : request.label();
        return designSnapshotService.createCheckpoint(workspaceId, userId, label);
    }

    /** 문서 전체를 과거로 되돌리므로 방장만 할 수 있다. */
    @PostMapping("/workspaces/{workspaceId}/design/doc/checkpoints/{checkpointId}/restore")
    public DesignDocResponse restoreCheckpoint(
            @PathVariable String workspaceId,
            @PathVariable String checkpointId,
            @AuthenticationPrincipal Long userId
    ) {
        return designSnapshotService.restoreCheckpoint(workspaceId, userId, checkpointId);
    }
}
