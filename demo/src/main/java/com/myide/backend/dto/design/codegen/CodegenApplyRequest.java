package com.myide.backend.dto.design.codegen;

import com.myide.backend.dto.design.v2.DesignModelV2;

import java.util.List;

/**
 * 고른 파일을 실제로 쓴다.
 *
 * 파일 내용을 클라이언트에서 받지 않는다는 점이 중요하다. 경로와 내용을
 * 그대로 받아 쓰면 그것은 사실상 "아무 파일이나 쓸 수 있는 API" 가 된다.
 * 서버가 같은 설계로 다시 생성해서, 고른 경로만 쓴다.
 */
public record CodegenApplyRequest(
        DesignModelV2 model,
        String projectName,
        String branchName,
        String basePackage,
        List<CodegenApplySelection> files
) {
    public CodegenApplyRequest {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
