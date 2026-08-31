package com.myide.backend.service.design.doctor;

import com.myide.backend.dto.design.v2.ApiSpecV2;
import com.myide.backend.dto.design.v2.ColumnV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.RequirementV2;
import com.myide.backend.dto.design.v2.ScreenTransitionV2;
import com.myide.backend.dto.design.v2.ScreenV2;
import com.myide.backend.dto.design.v2.TableV2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 모든 규칙이 함께 쓰는 조회용 색인.
 *
 * 규칙마다 문서를 다시 훑으면 규칙이 서른 개일 때 서른 번 훑게 된다.
 * 여기서 한 번만 만들어 돌려쓴다.
 */
public class DesignIndex {

    private final Map<String, RequirementV2> requirementById = new HashMap<>();
    private final Map<String, ScreenV2> screenById = new HashMap<>();
    private final Map<String, ApiSpecV2> apiById = new HashMap<>();
    private final Map<String, TableV2> tableById = new HashMap<>();

    /** 컬럼 id 는 문서 전체에서 유일하므로 평평하게 담는다. */
    private final Map<String, ColumnV2> columnById = new HashMap<>();
    private final Map<String, String> tableIdByColumnId = new HashMap<>();

    /** 어떤 API 라도 참조하는 테이블. 아무도 안 쓰는 테이블을 찾는 데 쓴다. */
    private final Set<String> tablesUsedByApis = new HashSet<>();

    /** 어떤 화면이라도 호출하는 API. */
    private final Set<String> apisUsedByScreens = new HashSet<>();

    private final Map<String, List<String>> screenAdjacency = new HashMap<>();
    private final Set<String> reachableScreenIds = new HashSet<>();

    public DesignIndex(DesignModelV2 model) {
        model.requirements().forEach(item -> requirementById.put(item.id(), item));
        model.screens().forEach(item -> screenById.put(item.id(), item));
        model.apis().forEach(item -> apiById.put(item.id(), item));

        model.erd().tables().forEach(table -> {
            tableById.put(table.id(), table);
            table.columns().forEach(column -> {
                columnById.put(column.id(), column);
                tableIdByColumnId.put(column.id(), table.id());
            });
        });

        model.apis().forEach(api -> {
            tablesUsedByApis.addAll(api.tableIds());
        });

        model.screens().forEach(screen -> {
            apisUsedByScreens.addAll(screen.apiIds());
        });

        model.screenTransitions().forEach(transition -> {
            screenAdjacency
                    .computeIfAbsent(transition.from(), key -> new ArrayList<>())
                    .add(transition.to());
        });

        computeReachable(model);
    }

    /** 시작 화면에서 화살표를 따라 갈 수 있는 화면을 모은다. */
    private void computeReachable(DesignModelV2 model) {
        Deque<String> queue = new ArrayDeque<>();

        model.screens().stream()
                .filter(ScreenV2::isEntry)
                .forEach(screen -> {
                    if (reachableScreenIds.add(screen.id())) {
                        queue.add(screen.id());
                    }
                });

        while (!queue.isEmpty()) {
            String current = queue.poll();

            for (String next : screenAdjacency.getOrDefault(current, List.of())) {
                if (reachableScreenIds.add(next)) {
                    queue.add(next);
                }
            }
        }
    }

    public boolean hasRequirement(String id) {
        return requirementById.containsKey(id);
    }

    public boolean hasScreen(String id) {
        return screenById.containsKey(id);
    }

    public boolean hasApi(String id) {
        return apiById.containsKey(id);
    }

    public boolean hasTable(String id) {
        return tableById.containsKey(id);
    }

    public ColumnV2 column(String columnId) {
        return columnById.get(columnId);
    }

    public String tableIdOfColumn(String columnId) {
        return tableIdByColumnId.get(columnId);
    }

    public TableV2 table(String tableId) {
        return tableById.get(tableId);
    }

    public boolean isTableUsedByApi(String tableId) {
        return tablesUsedByApis.contains(tableId);
    }

    public boolean isApiUsedByScreen(String apiId) {
        return apisUsedByScreens.contains(apiId);
    }

    public boolean isScreenReachable(String screenId) {
        return reachableScreenIds.contains(screenId);
    }

    public boolean hasOutgoing(String screenId) {
        return !screenAdjacency.getOrDefault(screenId, List.of()).isEmpty();
    }

    public Map<String, List<String>> screenAdjacency() {
        return screenAdjacency;
    }

    /** 화면 전이 중 양 끝이 실제로 존재하는 것만. */
    public boolean isTransitionValid(ScreenTransitionV2 transition) {
        return hasScreen(transition.from()) && hasScreen(transition.to());
    }
}
