package com.myide.backend.controller;

import com.myide.backend.dto.design.v2.DesignDetailRequest;
import com.myide.backend.dto.design.v2.DesignDraftRequest;
import com.myide.backend.dto.design.v2.DesignDraftResponse;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.TechStackV2;
import com.myide.backend.security.WorkspaceAccessGuard;
import com.myide.backend.service.design.ai.DesignDraftService;
import com.myide.backend.service.design.doctor.DesignDoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * AI 초안 생성.
 *
 * 두 단계로 나눠 부른다. 한 요청에서 다 처리하면 응답까지 수십 초가 걸리는데,
 * 프론트의 공용 HTTP 클라이언트에는 요청 시간 제한이 없어서 실패해도 사용자는
 * 멈춘 화면만 본다. 나누면 각 호출이 짧아지고 "1/2 진행 중"을 보여 줄 수 있으며,
 * 2단계만 실패했을 때 1단계 결과를 버리지 않고 다시 시도할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DesignAiController {

    private final DesignDraftService draftService;
    private final DesignDoctorService doctorService;
    private final WorkspaceAccessGuard accessGuard;

    /** 1단계: 요구사항과 화면, 화면 이동. */
    @PostMapping("/workspaces/{workspaceId}/design/ai/skeleton")
    public DesignDraftResponse skeleton(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal Long userId,
            @RequestBody DesignDraftRequest request
    ) {
        accessGuard.requireAccess(workspaceId, userId);
        requireAi();

        if (request == null || request.summary() == null || request.summary().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "어떤 서비스를 만들지 한 줄로 적어 주세요.");
        }

        TechStackV2 stack = request.techStack() == null
                ? TechStackV2.empty()
                : request.techStack();

        DesignModelV2 model = draftService.generateSkeleton(
                request.summary(), stack, request.instruction(), request.existing());

        return new DesignDraftResponse(model, doctorService.inspect(model));
    }

    /** 2단계: 표와 관계, API. 1단계 결과를 그대로 받아 이어서 만든다. */
    @PostMapping("/workspaces/{workspaceId}/design/ai/detail")
    public DesignDraftResponse detail(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal Long userId,
            @RequestBody DesignDetailRequest request
    ) {
        accessGuard.requireAccess(workspaceId, userId);
        requireAi();

        if (request == null || request.skeleton() == null
                || request.skeleton().screens().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "1단계 결과가 없습니다. 처음부터 다시 시도해 주세요.");
        }

        DesignModelV2 model = draftService.generateDetail(
                request.skeleton(), request.instruction(), request.existing());

        return new DesignDraftResponse(model, doctorService.inspect(model));
    }

    /** AI 설정이 없을 때 빈 화면 대신 이유를 알려 준다. */
    @GetMapping("/design/ai/status")
    public AiStatus status() {
        return new AiStatus(draftService.isAvailable());
    }

    public record AiStatus(boolean available) {}

    private void requireAi() {
        if (!draftService.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 설정이 되어 있지 않습니다. 직접 작성하거나 관리자에게 문의해 주세요.");
        }
    }
}
