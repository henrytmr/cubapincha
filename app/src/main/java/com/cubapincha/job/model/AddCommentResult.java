package com.cubapincha.job.model;

import org.json.JSONObject;

/** Resultado de añadir un comentario autenticado. */
public class AddCommentResult extends PublicComment {
    public String entityType;
    public String entityId;
    public int commentCount;

    public static AddCommentResult fromJson(JSONObject o) {
        AddCommentResult r = new AddCommentResult();
        PublicComment c = PublicComment.fromJson(o);
        r.commentId = c.commentId; r.authorId = c.authorId; r.authorName = c.authorName;
        r.authorRole = c.authorRole; r.text = c.text; r.createdAt = c.createdAt;
        r.entityType = o.optString("entityType", ""); r.entityId = o.optString("entityId", "");
        r.commentCount = o.optInt("commentCount", 0);
        return r;
    }
}
