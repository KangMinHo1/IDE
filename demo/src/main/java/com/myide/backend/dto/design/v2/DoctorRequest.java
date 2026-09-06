package com.myide.backend.dto.design.v2;

/**
 * 저장되지 않은 편집 중 상태도 검사해야 하므로 문서를 통째로 받는다.
 * 서버는 DB 를 읽지 않고 이 값만 보고 답한다.
 */
public record DoctorRequest(DesignModelV2 projection) {}
