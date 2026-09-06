package com.myide.backend.controller;

import com.myide.backend.handler.CollaborationWebSocketHandler;
import com.myide.backend.security.CollabRoomName;
import com.myide.backend.security.WorkspaceAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 동시 편집 방의 최초 내용을 누가 넣을지 정한다.
 *
 * 협업 서버는 문서를 보관하지 않는 단순 중계기라, 파일을 처음 여는 사람이
 * 디스크 내용을 문서에 넣어 주어야 한다. 그런데 <b>누가 처음인지를
 * 클라이언트끼리 알아낼 방법이 없다.</b> 접속 정보가 오가기 전에 판단하게
 * 되므로, 둘이 같은 파일을 동시에 열면 서로 자기가 처음이라고 여겨 같은
 * 내용을 두 번 넣거나, 서로 상대가 넣을 거라 여겨 아무도 안 넣는다.
 *
 * 그 판단을 서버가 한다. 방마다 한 사람에게만 허락하므로 경쟁이 없다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/collab/rooms")
public class CollabRoomController {

    private final CollaborationWebSocketHandler collaborationHandler;
    private final WorkspaceAccessGuard accessGuard;

    public record SeedClaimRequest(String room) {}

    public record SeedClaimResponse(boolean granted) {}

    @PostMapping("/seed-claim")
    public SeedClaimResponse claimSeed(
            @AuthenticationPrincipal Long userId,
            @RequestBody SeedClaimRequest request
    ) {
        String room = request == null ? null : request.room();

        if (room == null || room.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "방 이름이 없습니다.");
        }

        // 방 이름에 워크스페이스가 들어 있다는 규약 덕분에, 방 이름만으로
        // 권한을 볼 수 있다. 접속 검사와 같은 해석을 쓴다.
        String workspaceId = CollabRoomName.workspaceIdOf(room);

        if (workspaceId == null || workspaceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "방 이름에서 워크스페이스를 찾지 못했습니다.");
        }

        accessGuard.requireAccess(workspaceId, userId);

        return new SeedClaimResponse(collaborationHandler.claimSeed(room));
    }
}
