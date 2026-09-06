package com.myide.backend.dto.design.codegen;

import com.myide.backend.service.design.codegen.CodegenTarget;

/**
 * 만들어진 파일 하나. 아직 디스크에 쓰이지 않았다.
 *
 * 생성기는 디스크를 절대 만지지 않고 이 값만 돌려준다. 그래서 미리보기가
 * 공짜로 얻어지고, 같은 입력이면 항상 같은 결과라 두 번 생성해도 안전하다.
 *
 * sourceLabel 은 "이 파일이 설계의 무엇에서 나왔는지"를 사람 말로 적은 것이다.
 * 미리보기 목록에서 파일 이름만 보면 무엇인지 알기 어렵다.
 */
public record GeneratedFile(
        String path,
        String content,
        CodegenTarget target,
        String sourceLabel
) {
}
