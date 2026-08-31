package com.myide.backend.dto.design.v2;

/**
 * 초안 결과.
 *
 * 문서에 바로 들어가지 않는다. 화면이 이것을 보여 주고 사용자가 받아들인
 * 뒤에야 반영된다. report 는 AI가 만든 결과를 그대로 설계 점검에 돌린
 * 것으로, "AI가 만든 것도 이만큼 구멍이 있다"를 즉시 보여 준다.
 */
public record DesignDraftResponse(
        DesignModelV2 model,
        DoctorReport report
) {}
