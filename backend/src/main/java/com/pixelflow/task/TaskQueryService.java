package com.pixelflow.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelflow.common.ApiException;
import com.pixelflow.common.ErrorCodes;
import com.pixelflow.common.Uuids;
import com.pixelflow.task.mapper.TaskMapper;
import com.pixelflow.task.mapper.TaskResultMapper;
import com.pixelflow.task.mapper.TaskStepMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only queries. Polling a task never advances it, never bumps revision and never touches
 * updated_at. Each read runs in one repeatable-read transaction so a SUCCEEDED task always carries
 * its result and consistent steps.
 */
@Service
public class TaskQueryService {

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final TaskResultMapper taskResultMapper;
    private final ObjectMapper objectMapper;

    public TaskQueryService(TaskMapper taskMapper, TaskStepMapper taskStepMapper,
                            TaskResultMapper taskResultMapper, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.taskResultMapper = taskResultMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TaskView get(String userId, String taskIdRaw) {
        String taskId = Uuids.require(taskIdRaw, "taskId");
        TaskEntity task = taskMapper.findByIdAndUser(taskId, userId);
        if (task == null) {
            throw ApiException.notFound(ErrorCodes.TASK_NOT_FOUND, "任务不存在或不可访问");
        }
        return toViews(List.of(task)).get(0);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TaskPageView list(String userId, String templateIdRaw, String cursorRaw, Integer limitRaw) {
        String templateId = templateIdRaw == null || templateIdRaw.isBlank()
                ? null : Uuids.require(templateIdRaw, "templateId");
        int limit = limitRaw == null ? 20 : limitRaw;
        if (limit < 1 || limit > 100) {
            throw ApiException.badRequest(ErrorCodes.INVALID_REQUEST, "limit 必须在 1 到 100 之间");
        }
        CursorCodec.Cursor cursor = cursorRaw == null || cursorRaw.isBlank() ? null : CursorCodec.decode(cursorRaw);

        List<TaskEntity> rows = taskMapper.findPage(userId, templateId,
                cursor == null ? null : cursor.createdAt(), cursor == null ? null : cursor.id(), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<TaskEntity> page = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            TaskEntity last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(last.getCreatedAt(), last.getId());
        }
        return new TaskPageView(toViews(page), nextCursor);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TaskResultEntity requireResult(String userId, String taskIdRaw) {
        String taskId = Uuids.require(taskIdRaw, "taskId");
        TaskEntity task = taskMapper.findByIdAndUser(taskId, userId);
        if (task == null) {
            throw ApiException.notFound(ErrorCodes.TASK_NOT_FOUND, "任务不存在或不可访问");
        }
        TaskResultEntity result = taskResultMapper.findByTaskId(taskId);
        if (result == null) {
            throw ApiException.notFound(ErrorCodes.RESULT_FILE_MISSING, "任务结果不存在或尚未生成");
        }
        return result;
    }

    public List<TaskView> toViews(List<TaskEntity> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<String> ids = tasks.stream().map(TaskEntity::getId).toList();
        Map<String, List<TaskView.StepView>> stepsByTask = new HashMap<>();
        for (TaskStep step : taskStepMapper.findByTaskIds(ids)) {
            stepsByTask.computeIfAbsent(step.getTaskId(), key -> new ArrayList<>())
                    .add(new TaskView.StepView(step.getStage(), step.getStatus(), step.getStartedAt(),
                            step.getFinishedAt(), step.getErrorCode(), step.getErrorMessage()));
        }
        Map<String, TaskResultEntity> resultsByTask = new HashMap<>();
        for (TaskResultEntity result : taskResultMapper.findByTaskIds(ids)) {
            resultsByTask.put(result.getTaskId(), result);
        }
        return tasks.stream().map(task -> toView(task, stepsByTask.getOrDefault(task.getId(), List.of()),
                resultsByTask.get(task.getId()))).toList();
    }

    private TaskView toView(TaskEntity task, List<TaskView.StepView> steps, TaskResultEntity result) {
        TemplateSnapshot snapshot = readSnapshot(task.getTemplateSnapshot());
        TaskView.ResultView resultView = result == null ? null
                : new TaskView.ResultView("/api/v1/tasks/" + task.getId() + "/result",
                result.getMimeType(), result.getWidth(), result.getHeight());
        TaskView.TaskErrorView errorView = task.getErrorCode() == null ? null
                : new TaskView.TaskErrorView(task.getErrorCode(), task.getErrorMessage(), task.getCurrentStage());
        return new TaskView(task.getId(), task.getTemplateId(), snapshot.title(), task.getStatus(),
                task.getCurrentStage(), steps, resultView, errorView, task.getCreatedAt(), task.getUpdatedAt(),
                task.getStartedAt(), task.getFinishedAt(), task.getRevision());
    }

    private TemplateSnapshot readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, TemplateSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("task.template_snapshot is not valid", e);
        }
    }
}
