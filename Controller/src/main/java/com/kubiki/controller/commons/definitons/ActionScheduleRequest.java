package com.kubiki.controller.commons.definitons;

import jakarta.validation.constraints.Min;
import lombok.*;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class ActionScheduleRequest {
    @NonNull
    private ActionBase action;
    private String idempotencyKey;
    @Builder.Default
    @Min(0)
    private int priority = 0;
    @Builder.Default
    @Min(0)
    private int delay = 0;

    public ActionScheduleRequest(ActionBase actionBase, String idempotencyKey) {
        this.action = actionBase;
        this.idempotencyKey = idempotencyKey;
    }
}
