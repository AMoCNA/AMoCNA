package com.kubiki.controller.sample.dto;

import com.kubiki.controller.commons.actions.dtos.infra.ChangePodCPUActionArgs;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangeCPUAndPeriodActionArgs {
    private ChangePodCPUActionArgs changePodCPUActionArgs;
    private ChangePeriodActionArgs changePeriodActionArgs;
}
