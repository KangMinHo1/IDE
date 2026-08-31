package com.myide.backend.dto.design.v2;

/** 1단계 요청. 한 줄 설명과 기술 스택만 받는다. */
public record DesignDraftRequest(
        String summary,
        TechStackV2 techStack,
        String instruction
) {}
