package com.myide.backend.dto.design.v2;

/**
 * 1단계 요청.
 *
 * existing 은 지금 문서에 이미 있는 내용이다. 이것을 알려 주지 않으면 AI 가
 * 처음부터 만드는 줄 알고 이미 있는 요구사항과 화면을 다시 내놓는다.
 * 그러면 같은 경로를 쓰는 화면이 생겨 설계 점검 오류가 붙는다.
 */
public record DesignDraftRequest(
        String summary,
        TechStackV2 techStack,
        String instruction,
        DesignModelV2 existing
) {}
