package com.cubapincha.job.model;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Página pública de comentarios, ordenada por más recientes. */
public class CommentsPage {
    public String entityType;
    public String entityId;
    public List<PublicComment> comments = new ArrayList<>();
    public int total;
    public String nextCursor;

    public static CommentsPage fromJson(JSONObject o) {
        CommentsPage p = new CommentsPage();
        p.entityType = o.optString("entityType", ""); p.entityId = o.optString("entityId", "");
        JSONArray a = o.optJSONArray("comments");
        if (a != null) for (int i = 0; i < a.length(); i++) p.comments.add(PublicComment.fromJson(a.optJSONObject(i)));
        p.total = o.optInt("total", p.comments.size());
        p.nextCursor = o.has("nextCursor") && !o.isNull("nextCursor") ? o.optString("nextCursor") : null;
        return p;
    }
}
