package com.pixelflow.task;

import java.net.URI;

import com.pixelflow.common.ApiException;
import com.pixelflow.common.ErrorCodes;
import com.pixelflow.identity.CurrentUser;
import com.pixelflow.storage.StorageService;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskCommandService commandService;
    private final TaskQueryService queryService;
    private final StorageService storageService;
    private final CurrentUser currentUser;

    public TaskController(TaskCommandService commandService, TaskQueryService queryService,
                          StorageService storageService, CurrentUser currentUser) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.storageService = storageService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<TaskView> create(@RequestBody CreateTaskRequest request) {
        TaskView view = commandService.create(currentUser.requireUserId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/tasks/" + view.id()))
                .body(view);
    }

    @GetMapping
    public TaskPageView list(@RequestParam(name = "templateId", required = false) String templateId,
                             @RequestParam(name = "cursor", required = false) String cursor,
                             @RequestParam(name = "limit", required = false) Integer limit) {
        return queryService.list(currentUser.requireUserId(), templateId, cursor, limit);
    }

    @GetMapping("/{taskId}")
    public TaskView get(@PathVariable("taskId") String taskId) {
        return queryService.get(currentUser.requireUserId(), taskId);
    }

    /** Successful result bytes. A missing file stays a 404; the task itself is not changed. */
    @GetMapping("/{taskId}/result")
    public ResponseEntity<byte[]> result(@PathVariable("taskId") String taskId) {
        TaskResultEntity result = queryService.requireResult(currentUser.requireUserId(), taskId);
        if (!storageService.existsReadable(result.getStorageKey(), result.getSizeBytes())) {
            throw ApiException.notFound(ErrorCodes.RESULT_FILE_MISSING, "任务结果文件丢失");
        }
        byte[] bytes = storageService.read(result.getStorageKey());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.getMimeType()))
                .contentLength(bytes.length)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(bytes);
    }
}
