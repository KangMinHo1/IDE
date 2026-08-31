package com.myide.backend.service.design;

import com.myide.backend.domain.User;
import com.myide.backend.domain.design.DesignApiSpec;
import com.myide.backend.domain.design.DesignDocCheckpoint;
import com.myide.backend.domain.design.DesignDocSnapshot;
import com.myide.backend.domain.design.DesignDocument;
import com.myide.backend.domain.design.DesignRequirement;
import com.myide.backend.domain.workspace.Workspace;
import com.myide.backend.dto.design.v2.DesignCheckpointResponse;
import com.myide.backend.dto.design.v2.DesignDocResponse;
import com.myide.backend.dto.design.v2.DesignDocWriteRequest;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.LegacyProjectionV2;
import com.myide.backend.repository.UserRepository;
import com.myide.backend.repository.design.DesignApiSpecRepository;
import com.myide.backend.repository.design.DesignDocCheckpointRepository;
import com.myide.backend.repository.design.DesignDocSnapshotRepository;
import com.myide.backend.repository.design.DesignDocumentRepository;
import com.myide.backend.repository.design.DesignRequirementRepository;
import com.myide.backend.security.WorkspaceAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 설계 문서 저장본을 다루는 서비스.
 *
 * 협업 서버는 문서를 보관하지 않는 단순 중계기라, 여기 저장되지 않은
 * 편집은 마지막 접속자가 창을 닫는 순간 사라진다. 그래서 이 서비스가
 * 사실상 설계 데이터의 유일한 보관처다.
 *
 * 저장할 때마다 예전 형식으로도 되쓴다(역투영). 자료실과 마이페이지가
 * 예전 엔드포인트 세 개를 그대로 호출하고 있어서, 이걸 해 두면 두 화면을
 * 한 줄도 고치지 않고 새 편집기로 넘어갈 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DesignSnapshotService {

    /**
     * 새 스냅샷이 이전의 이 비율보다 작으면 덮어쓰기 전에 이전 상태를
     * 체크포인트로 남긴다.
     *
     * 거부하지 않는 이유: Yjs 는 지워진 내용을 정리하면서 실제로 작아질 수
     * 있어서, 크기만 보고 막으면 정상적인 대량 삭제까지 막힌다. 대신 되돌릴
     * 수단을 자동으로 만들어 두는 편이 사용자를 가로막지 않으면서 안전하다.
     */
    private static final double SHRINK_CHECKPOINT_RATIO = 0.5;

    private final DesignDocSnapshotRepository snapshotRepository;
    private final DesignDocCheckpointRepository checkpointRepository;
    private final DesignDocumentRepository designDocumentRepository;
    private final DesignRequirementRepository requirementRepository;
    private final DesignApiSpecRepository apiSpecRepository;
    private final UserRepository userRepository;

    private final WorkspaceAccessGuard accessGuard;
    private final DesignModelCodec codec;

    /** 시드 결과. created 가 false 면 다른 사람이 먼저 시드했다는 뜻이다. */
    public record SeedOutcome(boolean created, DesignDocResponse doc) {}

    // ── 조회 ────────────────────────────────────────────────────────

    public DesignDocResponse getDoc(String workspaceId, Long userId) {
        accessGuard.requireAccess(workspaceId, userId);

        return snapshotRepository.findByWorkspace_Uuid(workspaceId)
                .map(this::toResponse)
                .orElseGet(DesignDocResponse::seedRequired);
    }

    // ── 시드(워크스페이스당 1회) ─────────────────────────────────────

    /**
     * 저장본이 없을 때만 받아들인다.
     *
     * 이 1회 제한이 없으면, 두 명이 같은 시각에 워크스페이스를 처음 열었을 때
     * 각자 예전 데이터로 문서를 만들어 제출하고 둘 다 반영되어 모든 항목이
     * 두 벌이 된다. 진 쪽은 자기 문서를 버리고 여기서 돌려주는 것을 써야 한다.
     */
    @Transactional
    public SeedOutcome seed(String workspaceId, Long userId, DesignDocWriteRequest request) {
        WorkspaceAccessGuard.WorkspaceAccess access = accessGuard.requireAccess(workspaceId, userId);
        validate(request);

        return snapshotRepository.findByWorkspace_Uuid(workspaceId)
                .map(existing -> new SeedOutcome(false, toResponse(existing)))
                .orElseGet(() -> {
                    User user = getUser(userId);

                    DesignDocSnapshot created = snapshotRepository.save(
                            DesignDocSnapshot.builder()
                                    .workspace(access.workspace())
                                    .schemaVersion(schemaVersionOf(request))
                                    .yjsUpdateBase64(request.yjsUpdate())
                                    .projectionJson(codec.toJson(request.projection()))
                                    .updatedBy(user)
                                    .build());

                    syncLegacy(access.workspace(), user, request.projection());
                    log.info("🌱 [설계] 문서 시드 완료: 워크스페이스 = {}", workspaceId);

                    return new SeedOutcome(true, toResponse(created));
                });
    }

    // ── 저장 ────────────────────────────────────────────────────────

    @Transactional
    public DesignDocResponse save(String workspaceId, Long userId, DesignDocWriteRequest request) {
        WorkspaceAccessGuard.WorkspaceAccess access = accessGuard.requireAccess(workspaceId, userId);
        validate(request);

        User user = getUser(userId);

        DesignDocSnapshot snapshot = snapshotRepository.findByWorkspace_Uuid(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "아직 시드되지 않은 설계 문서입니다. 문서를 다시 열어주세요."));

        keepCheckpointIfShrunk(snapshot, request, user);

        snapshot.update(
                schemaVersionOf(request),
                request.yjsUpdate(),
                codec.toJson(request.projection()),
                user
        );

        syncLegacy(access.workspace(), user, request.projection());

        return toResponse(snapshot);
    }

    private void keepCheckpointIfShrunk(DesignDocSnapshot snapshot,
                                        DesignDocWriteRequest request,
                                        User user) {
        long previousSize = snapshot.getDocSize();
        long nextSize = request.yjsUpdate() == null ? 0 : request.yjsUpdate().length();

        if (previousSize <= 0 || nextSize >= previousSize * SHRINK_CHECKPOINT_RATIO) {
            return;
        }

        checkpointRepository.save(DesignDocCheckpoint.builder()
                .workspace(snapshot.getWorkspace())
                .label("자동 보관 (문서가 크게 줄어듦)")
                .yjsUpdateBase64(snapshot.getYjsUpdateBase64())
                .projectionJson(snapshot.getProjectionJson())
                .createdBy(user)
                .build());

        log.warn("⚠️ [설계] 문서가 {} → {} 로 크게 줄어 이전 상태를 자동 보관했습니다.",
                previousSize, nextSize);
    }

    // ── 체크포인트 ───────────────────────────────────────────────────

    @Transactional
    public DesignCheckpointResponse createCheckpoint(String workspaceId, Long userId, String label) {
        accessGuard.requireAccess(workspaceId, userId);

        DesignDocSnapshot snapshot = snapshotRepository.findByWorkspace_Uuid(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "저장된 설계 문서가 없습니다."));

        DesignDocCheckpoint checkpoint = checkpointRepository.save(DesignDocCheckpoint.builder()
                .workspace(snapshot.getWorkspace())
                .label(label == null || label.isBlank() ? "수동 저장" : label)
                .yjsUpdateBase64(snapshot.getYjsUpdateBase64())
                .projectionJson(snapshot.getProjectionJson())
                .createdBy(getUser(userId))
                .build());

        return DesignCheckpointResponse.from(checkpoint);
    }

    public List<DesignCheckpointResponse> listCheckpoints(String workspaceId, Long userId) {
        accessGuard.requireAccess(workspaceId, userId);

        return checkpointRepository.findByWorkspace_UuidOrderByCreatedAtDesc(workspaceId)
                .stream()
                .map(DesignCheckpointResponse::from)
                .toList();
    }

    /**
     * 문서 전체를 과거로 되돌리는 동작이라 방장만 할 수 있다.
     * 팀원 모두의 작업을 한 번에 날릴 수 있기 때문이다.
     */
    @Transactional
    public DesignDocResponse restoreCheckpoint(String workspaceId, Long userId, String checkpointId) {
        WorkspaceAccessGuard.WorkspaceAccess access = accessGuard.requireOwner(workspaceId, userId);

        DesignDocCheckpoint checkpoint = checkpointRepository
                .findByUuidAndWorkspace_Uuid(checkpointId, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "체크포인트를 찾을 수 없습니다."));

        DesignDocSnapshot snapshot = snapshotRepository.findByWorkspace_Uuid(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "저장된 설계 문서가 없습니다."));

        User user = getUser(userId);

        // 되돌리기 직전 상태도 남겨 둔다. 되돌린 것을 다시 되돌릴 수 있어야 한다.
        checkpointRepository.save(DesignDocCheckpoint.builder()
                .workspace(snapshot.getWorkspace())
                .label("복원 직전 자동 보관")
                .yjsUpdateBase64(snapshot.getYjsUpdateBase64())
                .projectionJson(snapshot.getProjectionJson())
                .createdBy(user)
                .build());

        DesignModelV2 restored = codec.fromJson(checkpoint.getProjectionJson());

        snapshot.update(
                restored.schemaVersion(),
                checkpoint.getYjsUpdateBase64(),
                checkpoint.getProjectionJson(),
                user
        );

        syncLegacy(access.workspace(), user, restored);

        return toResponse(snapshot);
    }

    // ── 역투영 ──────────────────────────────────────────────────────

    /**
     * v2 문서를 예전 형식으로 되쓴다.
     *
     * 요구사항과 API 명세 행까지 맞추는 이유: 자료실과 마이페이지가
     * /design/requirements, /design/apis, /design/document 세 곳을 모두
     * 호출한다. 문서 JSON만 되쓰면 그 두 화면의 요구사항과 API가 낡은 채로
     * 남는다.
     *
     * 행은 지웠다 다시 넣지 않고 문서의 항목 id 로 맞춰 제자리 갱신한다.
     * 자동 저장이 몇 초마다 도는데 매번 전체를 지웠다 넣으면 DB가 요동친다.
     */
    private void syncLegacy(Workspace workspace, User user, DesignModelV2 model) {
        LegacyProjectionV2 legacy = codec.toLegacy(model);

        syncLegacyDocument(workspace, user, legacy);
        syncLegacyRequirements(workspace, user, legacy);
        syncLegacyApiSpecs(workspace, user, legacy);
    }

    private void syncLegacyDocument(Workspace workspace, User user, LegacyProjectionV2 legacy) {
        designDocumentRepository.findByWorkspace_Uuid(workspace.getUuid())
                .ifPresentOrElse(
                        document -> document.update(
                                legacy.erdNodesJson(),
                                legacy.erdEdgesJson(),
                                legacy.flowNodesJson(),
                                legacy.flowEdgesJson()),
                        () -> designDocumentRepository.save(DesignDocument.builder()
                                .workspace(workspace)
                                .createdBy(user)
                                .erdNodesJson(legacy.erdNodesJson())
                                .erdEdgesJson(legacy.erdEdgesJson())
                                .flowNodesJson(legacy.flowNodesJson())
                                .flowEdgesJson(legacy.flowEdgesJson())
                                .build()));
    }

    private void syncLegacyRequirements(Workspace workspace, User user, LegacyProjectionV2 legacy) {
        Map<String, DesignRequirement> existing = new LinkedHashMap<>();
        requirementRepository.findByWorkspace_UuidOrderByCreatedAtAsc(workspace.getUuid())
                .forEach(row -> existing.put(row.getUuid(), row));

        Set<String> keep = new HashSet<>();

        for (LegacyProjectionV2.RequirementRow row : legacy.requirements()) {
            keep.add(row.id());
            DesignRequirement found = existing.get(row.id());

            if (found == null) {
                requirementRepository.save(DesignRequirement.builder()
                        .uuid(row.id())
                        .workspace(workspace)
                        .createdBy(user)
                        .category(row.category())
                        .name(row.name())
                        .description(row.description())
                        .build());
            } else {
                found.update(row.category(), row.name(), row.description());
            }
        }

        List<DesignRequirement> removed = new ArrayList<>();
        existing.forEach((uuid, row) -> {
            if (!keep.contains(uuid)) {
                removed.add(row);
            }
        });

        if (!removed.isEmpty()) {
            requirementRepository.deleteAll(removed);
        }
    }

    private void syncLegacyApiSpecs(Workspace workspace, User user, LegacyProjectionV2 legacy) {
        Map<String, DesignApiSpec> existing = new LinkedHashMap<>();
        apiSpecRepository.findByWorkspace_UuidOrderByCreatedAtAsc(workspace.getUuid())
                .forEach(row -> existing.put(row.getUuid(), row));

        Set<String> keep = new HashSet<>();

        for (LegacyProjectionV2.ApiSpecRow row : legacy.apiSpecs()) {
            keep.add(row.id());
            DesignApiSpec found = existing.get(row.id());

            if (found == null) {
                apiSpecRepository.save(DesignApiSpec.builder()
                        .uuid(row.id())
                        .workspace(workspace)
                        .createdBy(user)
                        .method(row.method())
                        .endpoint(row.endpoint())
                        .description(row.description())
                        .request(row.request())
                        .response(row.response())
                        .build());
            } else {
                found.update(row.method(), row.endpoint(), row.description(),
                        row.request(), row.response());
            }
        }

        List<DesignApiSpec> removed = new ArrayList<>();
        existing.forEach((uuid, row) -> {
            if (!keep.contains(uuid)) {
                removed.add(row);
            }
        });

        if (!removed.isEmpty()) {
            apiSpecRepository.deleteAll(removed);
        }
    }

    // ── 공용 ────────────────────────────────────────────────────────

    private void validate(DesignDocWriteRequest request) {
        if (request == null || request.yjsUpdate() == null || request.yjsUpdate().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "저장할 설계 문서가 비어 있습니다.");
        }
    }

    private int schemaVersionOf(DesignDocWriteRequest request) {
        return request.schemaVersion() <= 0
                ? DesignModelV2.CURRENT_SCHEMA_VERSION
                : request.schemaVersion();
    }

    private DesignDocResponse toResponse(DesignDocSnapshot snapshot) {
        return new DesignDocResponse(
                snapshot.getSchemaVersion(),
                snapshot.getRevision(),
                snapshot.getYjsUpdateBase64(),
                codec.fromJson(snapshot.getProjectionJson()),
                false
        );
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
    }
}
