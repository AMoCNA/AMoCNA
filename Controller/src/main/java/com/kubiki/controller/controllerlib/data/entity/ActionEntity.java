package com.kubiki.controller.controllerlib.data.entity;

import com.kubiki.controller.commons.definitons.ActionState;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.Date;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name="action")
public class ActionEntity implements Action {

    @Getter
    @Setter
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @Column(name = "action_name", nullable = false)
    private String actionName;

    @Getter
    @Setter
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "perform_time")
    private Date performTime;

    @Getter
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ActionState state;

    @Getter
    @Setter
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "success_perform_time")
    private Date successPerformTime;

    @Getter
    @Setter
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "last_try_time")
    private Date lastTryTime;

    @Getter
    @Setter
    @Column(name = "idempotency_id")
    private String idempotencyId;

    @Getter
    @Setter
    @Column(name = "priority")
    private Integer priority;

    @Getter
    @Setter
    @Column(name = "parent_id")
    private String parentId;

    @Getter
    @Setter
    @Column(name = "arguments")
    private String arguments;

    @Getter
    @Setter
    @Column(name = "sharing_counter", columnDefinition = "integer default 0")
    private Integer sharingCounter;
}
