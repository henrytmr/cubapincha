package com.cubapincha.job.data;

/** Resultado de UNA acción offline procesada por el servidor. */
public class SyncActionResult {
    /** applied | already_applied | conflict | rejected */
    public String result;
    public String clientActionId;
    public String errorCode;

    public SyncActionResult() {}

    public SyncActionResult(String clientActionId, String result, String errorCode) {
        this.clientActionId = clientActionId;
        this.result = result;
        this.errorCode = errorCode;
    }

    public boolean isApplied() {
        return "applied".equals(result) || "already_applied".equals(result);
    }
}
