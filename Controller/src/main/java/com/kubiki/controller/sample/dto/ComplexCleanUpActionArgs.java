package com.kubiki.controller.sample.dto;

import com.kubiki.controller.commons.actions.dtos.infra.CreatePVActionArgs;
import com.kubiki.controller.commons.actions.dtos.infra.CreateStorageClassActionArgs;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ComplexCleanUpActionArgs {
    private CreateStorageClassActionArgs createStorageClassActionArgs;
    private CreatePVActionArgs createPVActionArgs;
}
