package com.myide.backend.dto.design.codegen;

import com.myide.backend.service.design.doctor.Finding;

import java.util.List;

/**
 * 미리보기 결과.
 *
 * blockedBy 가 비어 있지 않으면 files 는 비어 있다. 설계에 오류가 있는데
 * 코드를 먼저 보여 주면, 사람은 오류를 고치는 대신 코드를 손보게 된다.
 */
public record CodegenPreviewResponse(
        String stack,
        String stackLabel,
        String basePackage,
        String note,
        List<CodegenFileView> files,
        List<Finding> blockedBy
) {
    public CodegenPreviewResponse {
        files = files == null ? List.of() : List.copyOf(files);
        blockedBy = blockedBy == null ? List.of() : List.copyOf(blockedBy);
    }
}
