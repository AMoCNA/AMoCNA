package com.kubiki.controller.sample.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangePeriodActionArgs {
    private int frequency;
    private String service;
}
