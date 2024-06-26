package com.kubiki.controller.commons.actions.dtos.infra;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangePodMemoryActionArgs {
    private String namespace;
    private String podName;
    private String newMemorySize;
    private String maxMemorySize;
}
