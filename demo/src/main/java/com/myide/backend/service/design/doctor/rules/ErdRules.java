package com.myide.backend.service.design.doctor.rules;

import com.myide.backend.dto.design.v2.ColumnV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.RelationV2;
import com.myide.backend.dto.design.v2.TableV2;
import com.myide.backend.service.design.doctor.DesignIndex;
import com.myide.backend.service.design.doctor.DesignRule;
import com.myide.backend.service.design.doctor.Finding;
import com.myide.backend.service.design.doctor.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ErdRules implements DesignRule {

    /** MySQL 에서 쓰는 타입만 받는다. 코드 생성이 이 값을 자바 타입으로 옮긴다. */
    private static final Set<String> KNOWN_TYPES = Set.of(
            "BIGINT", "INT", "SMALLINT", "TINYINT", "BOOLEAN", "DECIMAL", "DOUBLE", "FLOAT",
            "VARCHAR", "CHAR", "TEXT", "LONGTEXT", "DATE", "DATETIME", "TIMESTAMP", "TIME",
            "JSON", "BLOB"
    );

    /**
     * SQL 이나 자바에서 이름으로 쓰면 문제가 되는 단어들.
     *
     * AI 초안 후처리도 이 목록을 그대로 쓴다. 목록을 두 곳에 두면 한쪽에만
     * 단어가 늘어나는 날 초안이 곧바로 오류를 달고 나온다.
     */
    public static final Set<String> RESERVED = Set.of(
            "order", "group", "select", "from", "where", "table", "index", "key", "desc", "asc",
            "class", "public", "private", "static", "int", "long", "new", "return", "package"
    );

    @Override
    public List<Finding> check(DesignModelV2 model, DesignIndex index) {
        List<Finding> findings = new ArrayList<>();

        checkTables(model, index, findings);
        checkRelations(model, index, findings);
        checkCycles(model, findings);

        return findings;
    }

    private void checkTables(DesignModelV2 model, DesignIndex index, List<Finding> findings) {
        Map<String, Integer> nameCount = new HashMap<>();

        for (TableV2 table : model.erd().tables()) {
            String key = table.name().trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                nameCount.merge(key, 1, Integer::sum);
            }
        }

        for (TableV2 table : model.erd().tables()) {
            String label = table.name();

            if (table.name().isBlank()) {
                findings.add(Finding.of("TBL_NO_NAME", Severity.ERROR,
                        "table", table.id(), "이름 없는 테이블",
                        "테이블 이름이 비어 있습니다."));
            }

            String key = table.name().trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty() && nameCount.getOrDefault(key, 0) > 1) {
                findings.add(Finding.of("TBL_DUP_NAME", Severity.ERROR,
                        "table", table.id(), label,
                        "같은 이름의 테이블이 또 있습니다."));
            }

            if (table.columns().isEmpty()) {
                findings.add(Finding.of("TBL_NO_COLUMN", Severity.ERROR,
                        "table", table.id(), label,
                        "컬럼이 하나도 없습니다."));
            }

            if (table.columns().stream().noneMatch(ColumnV2::isPk)) {
                findings.add(Finding.of("TBL_NO_PK", Severity.ERROR,
                        "table", table.id(), label,
                        "기본키가 없습니다.",
                        "기본키가 없으면 Entity 코드를 만들 수 없습니다. id 컬럼에 pk 를 붙여 주세요."));
            }

            if (!index.isTableUsedByApi(table.id())) {
                findings.add(Finding.of("TBL_ORPHAN", Severity.WARNING,
                        "table", table.id(), label,
                        "이 테이블을 읽거나 쓰는 API가 없습니다.",
                        "정말 필요한 테이블인지, 아니면 API를 빠뜨린 것인지 확인해 주세요."));
            }

            checkColumns(table, findings);
        }
    }

    private void checkColumns(TableV2 table, List<Finding> findings) {
        Map<String, Integer> columnCount = new HashMap<>();

        for (ColumnV2 column : table.columns()) {
            String key = column.name().trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                columnCount.merge(key, 1, Integer::sum);
            }
        }

        for (ColumnV2 column : table.columns()) {
            String label = table.name() + "." + column.name();
            String key = column.name().trim().toLowerCase(Locale.ROOT);

            if (key.isEmpty()) {
                findings.add(Finding.of("COL_NO_NAME", Severity.ERROR,
                        "table", table.id(), label,
                        "이름이 없는 컬럼이 있습니다."));
                continue;
            }

            if (columnCount.getOrDefault(key, 0) > 1) {
                findings.add(Finding.of("COL_DUP_NAME", Severity.ERROR,
                        "table", table.id(), label,
                        "같은 이름의 컬럼이 또 있습니다."));
            }

            if (!KNOWN_TYPES.contains(column.type().toUpperCase(Locale.ROOT))) {
                findings.add(Finding.of("COL_UNKNOWN_TYPE", Severity.ERROR,
                        "table", table.id(), label,
                        "알 수 없는 타입입니다: " + column.type(),
                        "BIGINT, VARCHAR, TEXT, DATETIME, BOOLEAN 등에서 골라 주세요."));
            }

            if (RESERVED.contains(key)) {
                findings.add(Finding.of("COL_RESERVED_WORD", Severity.ERROR,
                        "table", table.id(), label,
                        "예약어라 컬럼 이름으로 쓸 수 없습니다: " + column.name(),
                        "뒤에 단어를 붙여 order_no 처럼 바꿔 주세요."));
            }

            if (!key.equals(column.name()) || column.name().contains("-")) {
                findings.add(Finding.of("COL_NAMING", Severity.INFO,
                        "table", table.id(), label,
                        "컬럼 이름은 소문자와 밑줄로 쓰는 것이 일반적입니다."));
            }
        }
    }

    private void checkRelations(DesignModelV2 model, DesignIndex index, List<Finding> findings) {
        for (RelationV2 relation : model.erd().relations()) {
            String label = describe(relation, index);

            boolean fromOk = index.hasTable(relation.fromTableId());
            boolean toOk = index.hasTable(relation.toTableId());

            if (!fromOk || !toOk) {
                findings.add(Finding.of("REL_DANGLING", Severity.ERROR,
                        "relation", relation.id(), label,
                        "존재하지 않는 테이블을 잇는 관계가 있습니다.",
                        "이 관계를 지우거나 대상 테이블을 다시 만들어 주세요."));
                continue;
            }

            ColumnV2 fromColumn = index.column(relation.fromColumnId());
            ColumnV2 toColumn = index.column(relation.toColumnId());

            if (fromColumn == null || toColumn == null) {
                findings.add(Finding.of("REL_NO_COLUMN", Severity.WARNING,
                        "relation", relation.id(), label,
                        "어떤 컬럼끼리 잇는 관계인지 정해지지 않았습니다.",
                        "ERD 텍스트에서 ref 로 컬럼을 지정해 주세요."));
                continue;
            }

            if (!fromColumn.type().equalsIgnoreCase(toColumn.type())) {
                findings.add(Finding.of("REL_TYPE_MISMATCH", Severity.ERROR,
                        "relation", relation.id(), label,
                        "이어진 두 컬럼의 타입이 다릅니다: "
                                + fromColumn.type() + " 와 " + toColumn.type(),
                        "외래키는 가리키는 기본키와 같은 타입이어야 합니다."));
            }

            if ("N:M".equals(relation.cardinality())) {
                findings.add(Finding.of("REL_NM_NO_JOIN", Severity.WARNING,
                        "relation", relation.id(), label,
                        "다대다 관계는 중간 테이블이 필요합니다.",
                        "두 테이블의 키를 담는 연결용 테이블을 하나 만들어 주세요."));
            }
        }
    }

    /** 외래키를 따라가다 제자리로 돌아오면 테이블 생성 순서가 꼬인다. */
    private void checkCycles(DesignModelV2 model, List<Finding> findings) {
        Map<String, List<String>> graph = new HashMap<>();

        model.erd().relations().forEach(relation ->
                graph.computeIfAbsent(relation.fromTableId(), key -> new ArrayList<>())
                        .add(relation.toTableId()));

        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();

        for (TableV2 table : model.erd().tables()) {
            if (hasCycle(table.id(), graph, visiting, visited)) {
                findings.add(Finding.of("REL_CYCLE", Severity.WARNING,
                        "table", table.id(), table.name(),
                        "외래키가 서로를 가리키며 돌고 있습니다.",
                        "테이블 생성 순서를 정할 수 없어 일부 외래키는 나중에 따로 추가해야 합니다."));
                return;
            }
        }
    }

    private boolean hasCycle(String node, Map<String, List<String>> graph,
                             Set<String> visiting, Set<String> visited) {
        if (visiting.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }

        visiting.add(node);

        for (String next : graph.getOrDefault(node, List.of())) {
            if (hasCycle(next, graph, visiting, visited)) {
                return true;
            }
        }

        visiting.remove(node);
        visited.add(node);
        return false;
    }

    private String describe(RelationV2 relation, DesignIndex index) {
        TableV2 from = index.table(relation.fromTableId());
        TableV2 to = index.table(relation.toTableId());

        return (from == null ? "?" : from.name()) + " → " + (to == null ? "?" : to.name());
    }
}
