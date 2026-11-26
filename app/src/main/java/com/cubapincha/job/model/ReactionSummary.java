package com.cubapincha.job.model;

import org.json.JSONObject;

/** Resumen de reacciones y comentarios de una entidad pública. */
public class ReactionSummary {
    public String entityType;
    public String entityId;
    public String myReaction;
    public int likeCount;
    public int dislikeCount;
    public int commentCount;

    public static ReactionSummary fromJson(JSONObject o) {
        ReactionSummary r = new ReactionSummary();
        r.entityType = o.optString("entityType", "");
        r.entityId = o.optString("entityId", "");
        r.myReaction = o.has("myReaction") && !o.isNull("myReaction") ? o.optString("myReaction") : null;
        r.likeCount = o.optInt("likeCount", 0);
        r.dislikeCount = o.optInt("dislikeCount", 0);
        r.commentCount = o.optInt("commentCount", 0);
        return r;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("entityType", entityType); o.put("entityId", entityId);
            if (myReaction != null) o.put("myReaction", myReaction); else o.put("myReaction", JSONObject.NULL);
            o.put("likeCount", likeCount); o.put("dislikeCount", dislikeCount); o.put("commentCount", commentCount);
        } catch (Exception ignored) { }
        return o;
    }
}
