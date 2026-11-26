package com.cubapincha.job.model;

import org.json.JSONObject;

/** Comentario público enriquecido con datos del autor. */
public class PublicComment {
    /** Solo estado local demo; el contrato público lo mantiene fuera del JSON. */
    public String entityId;
    public String commentId;
    public String authorId;
    public String authorName;
    public String authorRole;
    public String text;
    public String createdAt;

    public static PublicComment fromJson(JSONObject o) {
        PublicComment c = new PublicComment();
        c.commentId = o.optString("commentId", "");
        c.authorId = o.optString("authorId", "");
        c.authorName = o.optString("authorName", "");
        c.authorRole = o.optString("authorRole", "");
        c.text = o.optString("text", "");
        c.createdAt = o.optString("createdAt", "");
        return c;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("commentId", commentId); o.put("authorId", authorId); o.put("authorName", authorName);
            o.put("authorRole", authorRole); o.put("text", text); o.put("createdAt", createdAt);
        } catch (Exception ignored) { }
        return o;
    }
}
