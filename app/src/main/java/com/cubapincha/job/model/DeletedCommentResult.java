package com.cubapincha.job.model;

import org.json.JSONObject;

/** Confirmación de borrado de comentario. */
public class DeletedCommentResult {
    public boolean deleted;
    public String commentId;

    public static DeletedCommentResult fromJson(JSONObject o) {
        DeletedCommentResult r = new DeletedCommentResult();
        r.deleted = o.optBoolean("deleted", false);
        r.commentId = o.optString("commentId", "");
        return r;
    }
}
