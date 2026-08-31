package com.myide.backend.service.design.doctor;

/**
 * ERROR 가 하나라도 있으면 코드 생성을 막는다.
 * 깨진 설계에서 나온 코드는 안 만드는 편이 낫다.
 *
 * WARNING 은 설계가 덜 여물었다는 신호이고, INFO 는 관례에 관한 조언이다.
 * 학생 팀이 주 사용자라 INFO 도 배울 거리가 되도록 남긴다.
 */
public enum Severity {
    ERROR,
    WARNING,
    INFO
}
