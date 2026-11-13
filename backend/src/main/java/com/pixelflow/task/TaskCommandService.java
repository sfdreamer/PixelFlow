package com.pixelflow.task;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelflow.asset.Asset;
import com.pixelflow.asset.mapper.AssetMapper;
import com.pixelflow.catalog.CatalogService;
import com.pixelflow.catalog.Template;
import com.pixelflow.common.ApiError;
import com.pixelflow.common.ApiException;
import com.pixelflow.common.ErrorCodes;
import com.pixelflow.common.PixelFlowProperties;
import com.pixelflow.common.Uuids;
import com.pixelflow.storage.StorageService;
import com.pixelflow.task.mapper.TaskAssetMapper;
import com.pixelflow.task.mapper.TaskMapper;
import com.pixelflow.task.mapper.TaskStepMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts a task: validate, snapshot, write task + 3 steps + slot rows in one transaction, then
 * return the QUEUED snapshot. The request thread never executes a stage.
 */
@Service
public class TaskCommandService {

    private static final int MAX_STAGE_DELAY_MS = 30_000;

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final TaskAssetMapper taskAssetMapper;
    private final AssetMapper assetMapper;
    private final CatalogService catalogService;
    private final StorageService storageService;
    private final PixelFlowProperties properties;
    private final ObjectMapper objectMapper;

    public TaskCommandService(TaskMapper taskMapper, TaskStepMapper taskStepMapper,
                              TaskAssetMapper taskAssetMapper, AssetMapper assetMapper,
                              CatalogService catalogService, StorageService storageService,
                              PixelFlowProperties properties, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.taskAssetMapper = taskAssetMapper;
        this.assetMapper = assetMapper;
        this.catalogService = catalogService;
        this.storageService = storageService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TaskView create(String userId, CreateTaskRequest request) {
        if (request == null || request.templateId() == null || request.templateId().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_REQUEST, "templateId 不能为空");
        }
        String templateId = Uuids.require(request.templateId(), "templateId");
        Template template = catalogService.requireTemplate(templateId);

        SimulationSnapshot simulation = normalizeSimulation(request.simulation());

        List<String> assetIds = request.assetIds();
        if (assetIds == null || assetIds.isEmpty()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_REQUEST, "assetIds 不能为空");
        }
        if (assetIds.size() != template.getRequiredImageCount()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCodes.ASSET_COUNT_MISMATCH,
                    "该模板需要 " + template.getRequiredImageCount() + " 张图片",
                    List.of(new ApiError.Detail("assetIds",
                            "需要恰好 " + template.getRequiredImageCount() + " 项")));
        }
        Set<String> unique = new HashSet<>(assetIds);
        if (unique.size() != assetIds.size()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCodes.ASSET_DUPLICATED,
                    "同一张素材不能重复使用",
                    List.of(new ApiError.Detail("assetIds", "素材 ID 不能重复")));
        }

        List<Asset> resolved = new ArrayList<>(assetIds.size());
        for (String rawId : assetIds) {
            String assetId = Uuids.require(rawId, "assetIds");
            Asset asset = assetMapper.findByIdAndUser(assetId, userId);
            if (asset == null) {
                throw ApiException.notFound(ErrorCodes.ASSET_NOT_FOUND, "素材不存在或不可访问");
            }
            if (!templateId.equals(asset.getTemplateId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCodes.ASSET_TEMPLATE_MISMATCH,
                        "素材与模板不匹配",
                        List.of(new ApiError.Detail("assetIds", "素材 " + assetId + " 属于其他模板")));
            }
            if (!storageService.existsReadable(asset.getStorageKey(), asset.getSizeBytes())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCodes.ASSET_UNAVAILABLE,
                        "素材文件不可用，请重新上传");
            }
            resolved.add(asset);
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String taskId = Uuids.newId();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setTemplateId(templateId);
        task.setTemplateSnapshot(writeJson(new TemplateSnapshot(template.getTitle(),
                template.getRequiredImageCount(), catalogService.parseMimeTypes(template.getAcceptedMimeTypes()),
                template.getMaxFileSizeBytes(), template.getDemoResultKey())));
        task.setSimulationSnapshot(writeJson(simulation));
        task.setStatus(TaskStatus.QUEUED.name());
        task.setCurrentStage(null);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setRevision(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        taskMapper.insert(task);

        List<TaskStep> steps = new ArrayList<>(3);
        for (Stage stage : Stage.values()) {
            TaskStep step = new TaskStep();
            step.setTaskId(taskId);
            step.setStage(stage.name());
            step.setStepOrder(stage.order());
            step.setStatus(StepStatus.PENDING.name());
            steps.add(step);
        }
        taskStepMapper.insertBatch(steps);
        taskAssetMapper.insertBatch(taskId, assetIds);

        List<TaskView.StepView> stepViews = steps.stream()
                .map(step -> new TaskView.StepView(step.getStage(), step.getStatus(), null, null, null, null))
                .toList();
        return new TaskView(taskId, templateId, template.getTitle(), TaskStatus.QUEUED.name(), null, stepViews,
                null, null, task.getCreatedAt(), task.getUpdatedAt(), null, null, 0L);
    }

    private SimulationSnapshot normalizeSimulation(CreateTaskRequest.Simulation request) {
        if (request != null && !properties.isDemoControlsEnabled()) {
            throw ApiException.forbidden(ErrorCodes.SIMULATION_DISABLED, "当前环境不允许使用模拟控制参数");
        }
        String failAt = null;
        if (request != null && request.failAt() != null) {
            try {
                failAt = Stage.valueOf(request.failAt().trim().toUpperCase(Locale.ROOT)).name();
            } catch (IllegalArgumentException e) {
                throw ApiException.validation("simulation.failAt 取值不合法",
                        List.of(new ApiError.Detail("simulation.failAt",
                                "只能是 ASSET_CHECK、GENERATE、FINALIZE 或 null")));
            }
        }
        int assetCheck = properties.getStageDelayMs().getAssetCheck();
        int generate = properties.getStageDelayMs().getGenerate();
        int finalizeStage = properties.getStageDelayMs().getFinalizeStage();
        if (request != null && request.stageDelayMs() != null) {
            for (var entry : request.stageDelayMs().entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().trim().toUpperCase(Locale.ROOT);
                Integer value = entry.getValue();
                if (!Set.of("ASSET_CHECK", "GENERATE", "FINALIZE").contains(key)) {
                    throw ApiException.validation("simulation.stageDelayMs 包含未知阶段",
                            List.of(new ApiError.Detail("simulation.stageDelayMs", "未知阶段：" + entry.getKey())));
                }
                if (value == null || value < 0 || value > MAX_STAGE_DELAY_MS) {
                    throw ApiException.validation("阶段耗时必须在 0 到 30000 毫秒之间",
                            List.of(new ApiError.Detail("simulation.stageDelayMs." + key, "取值超出范围")));
                }
                switch (key) {
                    case "ASSET_CHECK" -> assetCheck = value;
                    case "GENERATE" -> generate = value;
                    default -> finalizeStage = value;
                }
            }
        }
        return new SimulationSnapshot(failAt, new SimulationSnapshot.StageDelays(assetCheck, generate, finalizeStage));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize snapshot", e);
        }
    }
}
