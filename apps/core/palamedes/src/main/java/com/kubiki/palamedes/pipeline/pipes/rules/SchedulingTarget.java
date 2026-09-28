package com.kubiki.palamedes.pipeline.pipes.rules;

import com.kubiki.palamedes.pipeline.WorkflowContext;

public record SchedulingTarget(WorkflowContext context, SchedulingState state) {
}
