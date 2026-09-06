package com.myide.backend.dto.design.codegen;

/**
 * 실제로 쓰기로 고른 파일 하나.
 *
 * expectedExistingHash 는 미리보기를 볼 때의 파일 내용이다. 지금 디스크에
 * 있는 것과 다르면 그 파일만 건너뛴다. 미리보기와 적용 사이에 팀원이 같은
 * 파일을 고쳤을 수 있고, 그것을 모르고 덮어쓰면 남의 작업이 사라진다.
 */
public record CodegenApplySelection(
        String path,
        String expectedExistingHash
) {
}
