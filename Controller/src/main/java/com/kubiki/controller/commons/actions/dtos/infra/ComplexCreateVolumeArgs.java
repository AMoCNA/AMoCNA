package com.kubiki.controller.commons.actions.dtos.infra;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ComplexCreateVolumeArgs {
    private CreateStorageClassActionArgs storageClassActionArgs;
    private CreatePVActionArgs pvActionArgs;
    private CreatePVCActionArgs pvcActionArgs;

    private int createStorageClassActionRetryNumber;
    private int createPVActionRetryNumber;
    private int createPVCActionRetryNumber;

    private long createStorageClassActionIdempotencyWindow;
    private long createPVActionIdempotencyWindow;
    private long createPVCActionIdempotencyWindow;
}
