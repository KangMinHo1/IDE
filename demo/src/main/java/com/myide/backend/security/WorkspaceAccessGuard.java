package com.myide.backend.security;

import com.myide.backend.domain.workspace.Workspace;
import com.myide.backend.domain.workspace.WorkspaceMember;
import com.myide.backend.repository.workspace.WorkspaceMemberRepository;
import com.myide.backend.repository.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * 워크스페이스 접근 권한을 확인하는 단일 창구.
 *
 * 기존에는 DesignService 와 DesignDocumentService 가 같은 검사 메서드를
 * 각자 복제해 갖고 있었고, 둘 다 findMyAllWorkspaces 로 내 워크스페이스를
 * 전부 가져와 메모리에서 걸러냈다. 여기서는 이미 있는 인덱스 조회
 * (WorkspaceMemberRepository.findByWorkspace_UuidAndUser_Id)를 쓴다.
 *
 * 초대를 아직 수락하지 않은 멤버(PENDING)는 접근할 수 없다.
 * 이 조건이 빠지면 초대만 받아 둔 사람이 팀 설계를 열람·편집하게 된다.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceAccessGuard {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public record WorkspaceAccess(Workspace workspace, WorkspaceMember.WorkspaceRole role) {

        public boolean isOwner() {
            return role == WorkspaceMember.WorkspaceRole.OWNER;
        }
    }

    /** 접근 권한을 확인하고 워크스페이스와 역할을 돌려준다. 권한이 없으면 예외를 던진다. */
    @Transactional(readOnly = true)
    public WorkspaceAccess requireAccess(String workspaceId, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        if (workspaceId == null || workspaceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "워크스페이스를 먼저 선택해주세요.");
        }

        Workspace workspace = workspaceRepository.findByUuid(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));

        if (workspace.getOwner() != null && userId.equals(workspace.getOwner().getId())) {
            return new WorkspaceAccess(workspace, WorkspaceMember.WorkspaceRole.OWNER);
        }

        Optional<WorkspaceMember> member =
                workspaceMemberRepository.findByWorkspace_UuidAndUser_Id(workspaceId, userId);

        WorkspaceMember accepted = member
                .filter(it -> it.getStatus() == WorkspaceMember.JoinStatus.ACCEPTED)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "워크스페이스 접근 권한이 없습니다."));

        return new WorkspaceAccess(workspace, accepted.getRole());
    }

    /** 역할이 필요 없을 때 쓰는 축약형. */
    @Transactional(readOnly = true)
    public Workspace requireWorkspace(String workspaceId, Long userId) {
        return requireAccess(workspaceId, userId).workspace();
    }

    /**
     * 방장만 할 수 있는 동작에 쓴다.
     * 편집은 멤버도 할 수 있지만, 문서 전체를 과거로 되돌리는 체크포인트
     * 복원처럼 남의 작업을 통째로 날릴 수 있는 동작은 방장으로 제한한다.
     */
    @Transactional(readOnly = true)
    public WorkspaceAccess requireOwner(String workspaceId, Long userId) {
        WorkspaceAccess access = requireAccess(workspaceId, userId);

        if (!access.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "방장만 할 수 있는 작업입니다.");
        }

        return access;
    }

    /** WebSocket 핸드셰이크처럼 예외 대신 참/거짓이 필요한 곳에서 쓴다. */
    @Transactional(readOnly = true)
    public boolean hasAccess(String workspaceId, Long userId) {
        try {
            requireAccess(workspaceId, userId);
            return true;
        } catch (ResponseStatusException e) {
            return false;
        }
    }
}
