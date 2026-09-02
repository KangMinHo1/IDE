package com.myide.backend.dto.design.codegen;

import java.util.List;

/**
 * 코드를 어디에 넣을 수 있는지 미리 알려 준다.
 *
 * branches 는 git 이 아는 브랜치가 아니라 <b>디스크에 실제로 있는 작업 폴더</b>다.
 * 파일이 쓰이는 곳이 그곳이므로, 목록에 없는 이름을 고르면 "폴더가 없습니다"만
 * 보게 된다. 고를 수 있는 것만 보여 주는 편이 낫다.
 *
 * stack 과 basePackage 는 고른 곳을 실제로 열어 보고 판단한 결과다. 미리보기를
 * 누르기 전에 "여기는 Spring Boot 이고 패키지는 이것"임을 보여 주려고 함께 담는다.
 */
public record CodegenTargetsResponse(
        List<String> branches,
        String stack,
        String stackLabel,
        String basePackage,
        String note
) {
    public CodegenTargetsResponse {
        branches = branches == null ? List.of() : List.copyOf(branches);
    }
}
