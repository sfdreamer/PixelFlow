package com.pixelflow.task;

import java.util.List;

public record TaskPageView(List<TaskView> items, String nextCursor) {
}
