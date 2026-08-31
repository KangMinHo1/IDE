package com.myide.backend.dto.design.codegen;

/**
 * 미리보기 목록에 뜨는 파일 하나.
 *
 * existingContent 를 함께 보내는 이유는 화면에서 바뀐 곳을 보여 주기
 * 위해서다. 무엇이 덮어써지는지 보지 않고 결정하게 하면 안 된다.
 */
public record CodegenFileView(
        String path,
        String content,
        CodegenFileStatus status,
        String existingHash,
        String existingContent,
        String target,
        String targetLabel,
        String sourceLabel
) {
}
