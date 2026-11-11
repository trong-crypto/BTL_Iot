package org.example.back_end_iot.entity;

import jakarta.persistence.*;
import org.example.back_end_iot.enums.ActionStatus;
import org.example.back_end_iot.enums.DeviceAction;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "action_history",
        indexes = {
                @Index(name = "idx_action_history_time", columnList = "created_at"),
                @Index(
                        name = "idx_action_history_device_time",
                        columnList = "device_id, created_at"
                ),
                @Index(name = "idx_action_history_user", columnList = "user_id")
        }
)
public class ActionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeviceAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ActionStatus status = ActionStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "request_id", unique = true, length = 36)
    private String requestId;

    public ActionHistory() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Integer getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public DeviceAction getAction() {
        return action;
    }

    public void setAction(DeviceAction action) {
        this.action = action;
    }

    public ActionStatus getStatus() {
        return status;
    }

    public void setStatus(ActionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}