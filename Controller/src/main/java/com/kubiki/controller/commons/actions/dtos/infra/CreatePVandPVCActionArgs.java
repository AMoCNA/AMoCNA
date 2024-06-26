package com.kubiki.controller.commons.actions.dtos.infra;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatePVandPVCActionArgs {
    private String namespace;
    private String persistentVolumeName;
    private String persistentVolumeClaimName;
    private String storageClassName;
    private String pvSize;
    private String pvcSize;
    private String hostPath;
}
