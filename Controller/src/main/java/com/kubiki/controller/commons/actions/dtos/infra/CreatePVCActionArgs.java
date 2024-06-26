package com.kubiki.controller.commons.actions.dtos.infra;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatePVCActionArgs {
    private String namespace;
    private String apiVersion;
    private String persistentVolumeClaimName;
    private String pvcSize;
    private String storageClassName;
}
