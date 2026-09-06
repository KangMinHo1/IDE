package com.myide.backend.service.design.codegen;

/**
 * 코드를 놓을 프로젝트가 실제로 무엇인가.
 *
 * 사용자가 고른 값을 믿지 않고 디스크를 보고 판단한다. "React 프로젝트"라고
 * 골랐는데 실제로는 Next.js 라면 라우팅 구조가 달라서 생성물이 무의미해지고,
 * 패키지 이름을 잘못 짚으면 Java 파일이 아예 컴파일되지 않는다.
 */
public enum ProjectStack {

    SPRING("Spring Boot"),
    REACT("React"),
    NEXT("Next.js"),
    UNKNOWN("알 수 없음");

    private final String label;

    ProjectStack(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
