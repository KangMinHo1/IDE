package com.myide.backend.dto.design.codegen;

/**
 * 파일 하나를 쓴 결과.
 *
 * FileService 는 파일 단위라 전체 되돌리기가 없다. 그래서 절반만 쓰이는
 * 상황이 실제로 일어날 수 있고, 그것을 조용히 숨기는 것이 가장 나쁘다.
 * 파일마다 결과를 그대로 돌려준다.
 */
public record CodegenApplyResult(
        String path,
        Status status,
        String message
) {
    public enum Status {
        /** 썼다. */
        WRITTEN,

        /** 내용이 이미 같아서 쓰지 않았다. */
        SKIPPED,

        /** 미리보기 이후에 파일이 바뀌었다. 덮어쓰지 않았다. */
        CHANGED_MEANWHILE,

        /** 쓰다가 실패했다. */
        FAILED
    }
}
