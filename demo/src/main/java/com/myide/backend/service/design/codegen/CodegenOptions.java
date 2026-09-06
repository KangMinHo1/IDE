package com.myide.backend.service.design.codegen;

/**
 * 생성기에게 필요한 프로젝트 쪽 사정.
 *
 * 설계 모델만으로는 코드를 만들 수 없다. 자바 파일은 패키지 이름을 알아야
 * 하고, 그 이름을 잘못 짚으면 파일이 아예 컴파일되지 않는다.
 */
public record CodegenOptions(
        String basePackage,
        ProjectStack stack
) {
    public CodegenOptions {
        basePackage = basePackage == null || basePackage.isBlank()
                ? "com.example.demo"
                : basePackage.trim();
        stack = stack == null ? ProjectStack.UNKNOWN : stack;
    }

    /** 자바 파일이 놓일 폴더. com.example.demo → src/main/java/com/example/demo */
    public String javaSourceDir() {
        return "src/main/java/" + basePackage.replace('.', '/');
    }
}
