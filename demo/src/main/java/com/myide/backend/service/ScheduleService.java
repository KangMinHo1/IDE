package com.myide.backend.service;

import com.myide.backend.domain.User;
import com.myide.backend.domain.notification.NotificationType;
import com.myide.backend.domain.schedule.Schedule;
import com.myide.backend.domain.workspace.Workspace;
import com.myide.backend.dto.schedule.ScheduleCreateRequest;
import com.myide.backend.dto.schedule.SchedulePeriodUpdateRequest;
import com.myide.backend.dto.schedule.ScheduleResponse;
import com.myide.backend.dto.schedule.ScheduleStatusUpdateRequest;
import com.myide.backend.dto.schedule.ScheduleUpdateRequest;
import com.myide.backend.repository.DevlogRepository;
import com.myide.backend.repository.ScheduleRepository;
import com.myide.backend.repository.UserRepository;
import com.myide.backend.repository.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final DevlogRepository devlogRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /* =====================================================
       일정 목록
       ===================================================== */

    public List<ScheduleResponse> getSchedules(
            String workspaceId,
            Long userId
    ) {
        validateUserId(userId);

        Workspace workspace =
                getAccessibleWorkspace(
                        workspaceId,
                        userId
                );

        return scheduleRepository
                .findByWorkspace_UuidOrderByStartDateAscCreatedAtDesc(
                        workspace.getUuid()
                )
                .stream()
                .map(schedule ->
                        ScheduleResponse.from(
                                schedule,
                                devlogRepository.existsBySchedule_Uuid(
                                        schedule.getUuid()
                                )
                        )
                )
                .toList();
    }

    /* =====================================================
       기간 일정 조회
       ===================================================== */

    public List<ScheduleResponse> getSchedulesInRange(
            String workspaceId,
            Long userId,
            LocalDate rangeStart,
            LocalDate rangeEnd
    ) {
        validateUserId(userId);

        Workspace workspace =
                getAccessibleWorkspace(
                        workspaceId,
                        userId
                );

        return scheduleRepository
                .findByWorkspace_UuidAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAscCreatedAtDesc(
                        workspace.getUuid(),
                        rangeEnd,
                        rangeStart
                )
                .stream()
                .map(schedule ->
                        ScheduleResponse.from(
                                schedule,
                                devlogRepository.existsBySchedule_Uuid(
                                        schedule.getUuid()
                                )
                        )
                )
                .toList();
    }

    /* =====================================================
       일정 생성
       ===================================================== */

    @Transactional
    public ScheduleResponse createSchedule(
            String workspaceId,
            Long userId,
            ScheduleCreateRequest request
    ) {
        validateUserId(userId);

        validateTitle(
                request.title()
        );

        validatePeriod(
                request.startDate(),
                request.endDate()
        );

        Workspace workspace =
                getAccessibleWorkspace(
                        workspaceId,
                        userId
                );

        User creator =
                getUser(userId);

        /*
         * 담당자를 지정하지 않았으면
         * 일정 생성자가 자동 담당자.
         */
        User assignee;

        if (
                request.assigneeUserId() == null
        ) {
            assignee = creator;
        } else {
            assignee =
                    getWorkspaceMemberUser(
                            workspace,
                            request.assigneeUserId()
                    );
        }

        Schedule schedule =
                Schedule.builder()
                        .workspace(workspace)

                        .createdBy(
                                creator
                        )

                        .assignee(
                                assignee
                        )

                        .title(
                                request
                                        .title()
                                        .trim()
                        )

                        .description(
                                normalizeDescription(
                                        request.description()
                                )
                        )

                        .startDate(
                                request.startDate()
                        )

                        .endDate(
                                request.endDate()
                        )

                        .status(
                                request.status()
                        )

                        .build();

        Schedule saved =
                scheduleRepository.save(
                        schedule
                );

        notificationService
                .notifyWorkspaceMembersExcept(
                        workspace.getUuid(),

                        userId,

                        NotificationType.SCHEDULE,

                        "일정 알림",

                        creator.getNickname()
                                + "님이 새 일정을 등록했습니다: "
                                + saved.getTitle(),

                        "/schedules?view="
                                + workspace
                                .getType()
                                .name()
                                .toLowerCase()
                                + "&workspaceId="
                                + workspace.getUuid()
                );

        return ScheduleResponse.from(
                saved,
                false
        );
    }

    /* =====================================================
       일정 전체 수정
       ===================================================== */

    @Transactional
    public ScheduleResponse updateSchedule(
            String scheduleId,
            Long userId,
            ScheduleUpdateRequest request
    ) {
        validateUserId(userId);

        validateTitle(
                request.title()
        );

        validatePeriod(
                request.startDate(),
                request.endDate()
        );

        if (
                request.status() == null
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "상태는 필수입니다."
            );
        }

        Schedule schedule =
                getAccessibleSchedule(
                        scheduleId,
                        userId
                );

        /*
         * request에서 담당자가 넘어오지 않으면
         * 기존 담당자를 유지한다.
         */
        User assignee =
                schedule.getAssignee();

        if (
                request.assigneeUserId() != null
        ) {
            assignee =
                    getWorkspaceMemberUser(
                            schedule.getWorkspace(),
                            request.assigneeUserId()
                    );
        }

        schedule.updateContent(
                request
                        .title()
                        .trim(),

                normalizeDescription(
                        request.description()
                ),

                request.startDate(),

                request.endDate(),

                request.status(),

                schedule.getCategory(),

                assignee
        );

        return ScheduleResponse.from(
                schedule,

                devlogRepository
                        .existsBySchedule_Uuid(
                                schedule.getUuid()
                        )
        );
    }

    /* =====================================================
       상태 변경
       ===================================================== */

    @Transactional
    public ScheduleResponse updateStatus(
            String scheduleId,
            Long userId,
            ScheduleStatusUpdateRequest request
    ) {
        validateUserId(
                userId
        );

        Schedule schedule =
                getAccessibleSchedule(
                        scheduleId,
                        userId
                );

        schedule.updateStatus(
                request.status()
        );

        return ScheduleResponse.from(
                schedule,

                devlogRepository
                        .existsBySchedule_Uuid(
                                schedule.getUuid()
                        )
        );
    }

    /* =====================================================
       기간 변경
       ===================================================== */

    @Transactional
    public ScheduleResponse updatePeriod(
            String scheduleId,
            Long userId,
            SchedulePeriodUpdateRequest request
    ) {
        validateUserId(
                userId
        );

        validatePeriod(
                request.startDate(),
                request.endDate()
        );

        Schedule schedule =
                getAccessibleSchedule(
                        scheduleId,
                        userId
                );

        schedule.updatePeriod(
                request.startDate(),
                request.endDate()
        );

        return ScheduleResponse.from(
                schedule,

                devlogRepository
                        .existsBySchedule_Uuid(
                                schedule.getUuid()
                        )
        );
    }

    /* =====================================================
       삭제
       ===================================================== */

    @Transactional
    public void deleteSchedule(
            String scheduleId,
            Long userId
    ) {
        validateUserId(
                userId
        );

        Schedule schedule =
                getAccessibleSchedule(
                        scheduleId,
                        userId
                );

        scheduleRepository.delete(
                schedule
        );
    }

    /* =====================================================
       ACCESSIBLE SCHEDULE
       ===================================================== */

    private Schedule getAccessibleSchedule(
            String scheduleId,
            Long userId
    ) {
        Schedule schedule =
                scheduleRepository
                        .findByUuid(
                                scheduleId
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "일정을 찾을 수 없습니다."
                                        )
                        );

        getAccessibleWorkspace(
                schedule
                        .getWorkspace()
                        .getUuid(),

                userId
        );

        return schedule;
    }

    /* =====================================================
       ACCESSIBLE WORKSPACE
       ===================================================== */

    private Workspace getAccessibleWorkspace(
            String workspaceId,
            Long userId
    ) {
        return workspaceRepository
                .findMyAllWorkspaces(
                        userId
                )
                .stream()
                .filter(
                        workspace ->
                                workspace
                                        .getUuid()
                                        .equals(
                                                workspaceId
                                        )
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "워크스페이스 접근 권한이 없습니다."
                                )
                );
    }

    /* =====================================================
       ASSIGNEE VALIDATION
       ===================================================== */

    private User getWorkspaceMemberUser(
            Workspace workspace,
            Long assigneeUserId
    ) {
        User assignee =
                getUser(
                        assigneeUserId
                );

        /*
         * 해당 사용자에게 현재 workspace 접근 권한이 있는지 확인.
         *
         * 개인 프로젝트:
         *   본인만 통과.
         *
         * 팀 프로젝트:
         *   OWNER / MEMBER 모두 통과.
         */
        boolean member =
                workspaceRepository
                        .findMyAllWorkspaces(
                                assigneeUserId
                        )
                        .stream()
                        .anyMatch(
                                userWorkspace ->
                                        userWorkspace
                                                .getUuid()
                                                .equals(
                                                        workspace.getUuid()
                                                )
                        );

        if (!member) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "해당 사용자는 이 프로젝트의 팀원이 아닙니다."
            );
        }

        return assignee;
    }

    /* =====================================================
       USER
       ===================================================== */

    private User getUser(
            Long userId
    ) {
        return userRepository
                .findById(userId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "사용자를 찾을 수 없습니다."
                                )
                );
    }

    /* =====================================================
       VALIDATION
       ===================================================== */

    private void validateUserId(
            Long userId
    ) {
        if (
                userId == null
        ) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }
    }

    private void validateTitle(
            String title
    ) {
        if (
                title == null ||
                        title.trim().isEmpty()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "일정 제목은 필수입니다."
            );
        }
    }

    private void validatePeriod(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (
                startDate == null ||
                        endDate == null
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "시작일과 종료일은 필수입니다."
            );
        }

        if (
                endDate.isBefore(
                        startDate
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "종료일은 시작일보다 빠를 수 없습니다."
            );
        }
    }

    private String normalizeDescription(
            String description
    ) {
        if (
                description == null ||
                        description
                                .trim()
                                .isEmpty()
        ) {
            return "등록된 상세 내용이 없습니다.";
        }

        return description.trim();
    }
}