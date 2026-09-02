package com.myide.backend.controller;

import com.myide.backend.dto.aireport.FinalReportDraftRequest;
import com.myide.backend.dto.aireport.FinalReportDraftResponse;
import com.myide.backend.security.WorkspaceAccessGuard;
import com.myide.backend.service.aireport.FinalReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/archive/final-report")
@RequiredArgsConstructor
public class FinalReportController {
    private final FinalReportService finalReportService;
    private final WorkspaceAccessGuard accessGuard;

    @PostMapping("/draft")
    public ResponseEntity<FinalReportDraftResponse> generateDraft(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal Long userId,
            @RequestBody FinalReportDraftRequest request
    ) {
        // 이 엔드포인트에는 권한 검사가 아예 없었다. 워크스페이스 id 만 알면
        // 남의 팀 설계와 개발일지가 담긴 보고서를 뽑을 수 있었다.
        accessGuard.requireAccess(workspaceId, userId);

        String draft = finalReportService.generateDraft(workspaceId, request);
        return ResponseEntity.ok(new FinalReportDraftResponse(draft));
    }
}
