package com.myide.backend.service.design.doctor;

import com.myide.backend.dto.design.v2.DesignModelV2;

import java.util.List;

/**
 * 설계 검사 규칙 묶음.
 *
 * 구현체에 Component 만 붙이면 스프링이 List 로 주입해 자동 등록한다.
 * 프로젝트의 다른 전략 패턴 세 곳과 같은 관용구라 팀이 즉시 읽는다.
 *
 * 규칙 하나에 클래스 하나를 만들지 않고 축(요구사항/화면/API/ERD/교차) 단위로
 * 묶는다. 서른 개가 넘는 규칙마다 파일을 만들면 찾기가 더 어려워진다.
 */
public interface DesignRule {

    List<Finding> check(DesignModelV2 model, DesignIndex index);
}
