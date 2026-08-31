package com.myide.backend.dto.design.codegen;

/**
 * 이 파일이 지금 프로젝트와 어떤 관계인가.
 *
 * IDENTICAL 이 있다는 것이 중요하다. 같은 설계로 두 번 생성하면 두 번째는
 * 전부 IDENTICAL 이어야 하고, 그것이 생성기가 결정론적이라는 증거다.
 */
public enum CodegenFileStatus {

    /** 아직 없는 파일. 그냥 쓰면 된다. */
    NEW,

    /** 이미 있고 내용도 똑같다. 쓸 필요가 없다. */
    IDENTICAL,

    /** 이미 있는데 내용이 다르다. 사람이 보고 정해야 한다. */
    CONFLICT
}
