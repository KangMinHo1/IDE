package com.myide.backend.dto.design.codegen;

import com.myide.backend.dto.design.v2.DesignModelV2;

/**
 * 무엇을, 어디에 만들지.
 *
 * 설계 모델을 통째로 받는다. 저장되지 않은 편집 상태에서도 미리보기를 볼 수
 * 있어야 하기 때문이다. basePackage 가 비어 있으면 프로젝트 폴더를 보고
 * 알아서 정한다.
 */
public record CodegenPreviewRequest(
        DesignModelV2 model,
        String projectName,
        String branchName,
        String basePackage
) {
}
