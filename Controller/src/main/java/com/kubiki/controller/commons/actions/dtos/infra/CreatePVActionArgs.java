package com.kubiki.controller.commons.actions.dtos.infra;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatePVActionArgs {
    private String apiVersion;
    private String persistentVolumeName;
    private String pvSize;
    private String storageClassName;
    private String hostPath;
}
