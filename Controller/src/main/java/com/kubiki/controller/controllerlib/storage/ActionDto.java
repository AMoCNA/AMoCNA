package com.kubiki.controller.controllerlib.storage;

import com.kubiki.controller.commons.definitons.ActionBase;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RequiredArgsConstructor
@Builder(toBuilder = true)
public class ActionDto {
    @NonNull
    private ActionBase actionBase;
    @Builder.Default
    @Min(0)
    private int sharingCounter = 0;
    private String idempotencyKey;
    @Builder.Default
    @Min(0)
    private int priority = 0;
    private Date successPerformTime;
    private Date lastTryTime;
}
