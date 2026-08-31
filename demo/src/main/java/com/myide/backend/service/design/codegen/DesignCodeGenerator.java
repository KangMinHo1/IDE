package com.myide.backend.service.design.codegen;

import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.codegen.GeneratedFile;

import java.util.List;

/**
 * 설계에서 파일을 만드는 사람.
 *
 * 이 프로젝트가 이미 쓰고 있는 전략 패턴 관용구를 그대로 따른다
 * (ProjectTemplateStrategy, DebugStrategy, CodeAnalyzer). @Component 를
 * 붙이면 스프링이 List 로 모아 주므로 새 생성기를 추가할 때 등록할 곳을
 * 찾아다닐 필요가 없다.
 *
 * 규칙이 하나 있다 — <b>디스크를 만지지 않는다.</b> 순수하게 값만 돌려주기
 * 때문에 미리보기와 실제 적용이 같은 코드를 지나가고, 두 번 생성해도 결과가
 * 같다는 것을 검사로 확인할 수 있다.
 */
public interface DesignCodeGenerator {

    boolean supports(CodegenTarget target);

    List<GeneratedFile> generate(DesignModelV2 model, CodegenOptions options);
}
