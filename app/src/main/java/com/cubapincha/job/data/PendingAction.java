package com.cubapincha.job.data;

/** Acción offline en cola, con clientActionId idempotente. */
public class PendingAction {
    public String clientActionId;
    public String actionType; // saveJob | expressInterest | changeApplicationStatus | ...
    public String payloadJson;
    public String status = "pending"; // pending | sent | conflict | rejected
    public String createdAt;
    public String errorCode;

    public PendingAction() {}

    public PendingAction(String clientActionId, String actionType, String payloadJson, String createdAt) {
        this.clientActionId = clientActionId;
        this.actionType = actionType;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
    }
}
