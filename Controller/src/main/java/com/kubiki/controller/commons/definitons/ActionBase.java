package com.kubiki.controller.commons.definitons;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActionBase {
    private Long id;
    private String name;
    private Object args;
    @With
    private ActionState state;
    private Integer retryNum;
    private Long idempotencyWindow;
    private String parentId; // if null -> simple action

    public ActionBase(Object args, Integer retryNum, Long idempotencyWindow) {
        this.args = args;
        this.retryNum = retryNum;
        this.idempotencyWindow = idempotencyWindow;
        this.state = ActionState.STARTED;
    }
}
