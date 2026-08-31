package com.myide.backend.controller;

import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.DoctorReport;
import com.myide.backend.dto.design.v2.DoctorRequest;
import com.myide.backend.security.WorkspaceAccessGuard;
import com.myide.backend.service.design.doctor.DesignDoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 설계 점검.
 *
 * DB 를 읽지 않고 요청 본문의 문서만 본다. 아직 저장되지 않은 편집 중
 * 상태에도 곧바로 답해야 하기 때문이다. 덕분에 수십 밀리초면 끝나고,
 * 편집하는 동안 실시간에 가까운 피드백을 줄 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DesignDoctorController {

    private final DesignDoctorService designDoctorService;
    private final WorkspaceAccessGuard accessGuard;

    @PostMapping("/workspaces/{workspaceId}/design/doctor")
    public DoctorReport inspect(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal Long userId,
            @RequestBody DoctorRequest request
    ) {
        // 남의 워크스페이스 설계를 넣어 검사시키는 것을 막는다.
        accessGuard.requireAccess(workspaceId, userId);

        DesignModelV2 projection = request == null ? null : request.projection();
        return designDoctorService.inspect(projection);
    }
}
