package com.myide.backend.service.design.doctor;

/**
 * 기계적으로 고칠 수 있는 문제를 어떻게 고칠지 적어 둔 것.
 *
 * 판정은 서버가 하는데 수정을 화면이 따로 계산하면 둘이 어긋난다. 눌러도
 * 오류가 안 사라지거나, 고친 결과가 AI 초안 후처리와 달라진다. 그래서
 * <b>무엇을 어떻게 바꿀지까지 서버가 정하고</b> 화면은 그대로 적용만 한다.
 *
 * @param kind     무엇을 하는 수정인가
 * @param targetId 대상 항목의 id (표·화면·관계·화살표)
 * @param columnId 컬럼을 고치는 수정에서만 쓴다
 * @param value    새로 넣을 값 (이름, 경로, 타입)
 * @param length   타입에 길이가 딸린 경우
 */
public record Fix(
        Kind kind,
        String targetId,
        String columnId,
        String value,
        Integer length
) {
    public enum Kind {
        /** 컬럼 이름을 value 로 바꾼다. */
        RENAME_COLUMN,

        /** 화면 경로를 value 로 바꾼다. */
        SET_SCREEN_ROUTE,

        /** 표에 id BIGINT 기본키를 넣는다. */
        ADD_PK_COLUMN,

        /** 외래키 컬럼의 타입을 가리키는 기본키에 맞춘다. */
        ALIGN_FK_TYPE,

        /** 가리키는 대상이 없는 관계를 지운다. */
        DELETE_RELATION,

        /** 가리키는 대상이 없는 화살표를 지운다. */
        DELETE_TRANSITION
    }

    public static Fix renameColumn(String tableId, String columnId, String newName) {
        return new Fix(Kind.RENAME_COLUMN, tableId, columnId, newName, null);
    }

    public static Fix setScreenRoute(String screenId, String route) {
        return new Fix(Kind.SET_SCREEN_ROUTE, screenId, null, route, null);
    }

    public static Fix addPrimaryKey(String tableId) {
        return new Fix(Kind.ADD_PK_COLUMN, tableId, null, null, null);
    }

    public static Fix alignForeignKeyType(String tableId, String columnId,
                                          String type, Integer length) {
        return new Fix(Kind.ALIGN_FK_TYPE, tableId, columnId, type, length);
    }

    public static Fix deleteRelation(String relationId) {
        return new Fix(Kind.DELETE_RELATION, relationId, null, null, null);
    }

    public static Fix deleteTransition(String transitionId) {
        return new Fix(Kind.DELETE_TRANSITION, transitionId, null, null, null);
    }
}
