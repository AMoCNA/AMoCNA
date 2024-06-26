package com.kubiki.controller.commons.definitons;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ActionResponseDto {

    Long id;

    Boolean isSuccess;

    String message;
}
