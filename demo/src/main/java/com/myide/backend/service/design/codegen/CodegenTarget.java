package com.myide.backend.service.design.codegen;

/**
 * 무엇을 만들 것인가.
 *
 * 언어를 늘리지 않는다. 시연 대상이 Spring Boot 와 React 두 가지이고,
 * 어설프게 여러 언어를 지원하면 어느 것도 실제로 컴파일되지 않는다.
 */
public enum CodegenTarget {

    SPRING_ENTITY(ProjectStack.SPRING, "Entity"),
    SPRING_REPOSITORY(ProjectStack.SPRING, "Repository"),
    SPRING_CONTROLLER_DTO(ProjectStack.SPRING, "Controller / DTO"),
    DDL(ProjectStack.SPRING, "테이블 생성 SQL"),

    REACT_ROUTE(ProjectStack.REACT, "라우트"),
    REACT_PAGE(ProjectStack.REACT, "화면 뼈대"),
    REACT_API_CLIENT(ProjectStack.REACT, "API 호출 함수");

    private final ProjectStack stack;
    private final String label;

    CodegenTarget(ProjectStack stack, String label) {
        this.stack = stack;
        this.label = label;
    }

    public ProjectStack stack() {
        return stack;
    }

    public String label() {
        return label;
    }
}
