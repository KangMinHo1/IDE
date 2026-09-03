package com.myide.backend.dto.design.v2;

/**
 * 2단계 요청.
 *
 * 1단계 결과를 클라이언트가 그대로 돌려준다. 서버가 중간 상태를 들고 있지
 * 않아도 되고, 사용자가 1단계 결과를 손본 뒤 2단계를 부를 수도 있다.
 *
 * existing 은 지금 문서에 이미 있는 내용이다. 이미 있는 표를 또 만들지
 * 않게 하려고 함께 보낸다.
 */
public record DesignDetailRequest(
        DesignModelV2 skeleton,
        String instruction,
        DesignModelV2 existing
) {}
