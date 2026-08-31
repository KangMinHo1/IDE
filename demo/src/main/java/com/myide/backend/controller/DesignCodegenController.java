package com.myide.backend.controller;

import com.myide.backend.dto.design.codegen.CodegenApplyRequest;
import com.myide.backend.dto.design.codegen.CodegenApplyResponse;
import com.myide.backend.dto.design.codegen.CodegenPreviewRequest;
import com.myide.backend.dto.design.codegen.CodegenPreviewResponse;
import com.myide.backend.security.WorkspaceAccessGuard;
import com.myide.backend.service.design.codegen.DesignCodegenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 설계에서 코드 만들기.
 *
 * 미리보기와 적용을 나눈 이유는 하나다. 무엇이 덮어써지는지 보지 않고
 * 결정하게 하면 안 된다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/{workspaceId}/design/codegen")
public class DesignCodegenController {

    private final DesignCodegenService codegenService;
    private final WorkspaceAccessGuard accessGuard;

    @PostMapping("/preview")
    public CodegenPreviewResponse preview(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal Long userId,
            @RequestBody CodegenPreviewRequest request
    ) {
        accessGuard.requireAccess(workspaceId, userId);
        requireProject(request.projectName());

        return codegenService.preview(workspaceId, request);
    }

    @PostMapping("/apply")
    public CodegenApplyResponse apply(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal Long userId,
            @RequestBody CodegenApplyRequest request
    ) {
        accessGuard.requireAccess(workspaceId, userId);
        requireProject(request.projectName());

        if (request.files().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "적용할 파일을 골라 주세요.");
        }

        return codegenService.apply(workspaceId, request);
    }

    private void requireProject(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "코드를 넣을 프로젝트를 골라 주세요.");
        }
    }
}
