package com.cubapincha.job.model;

import org.json.JSONObject;

/** Valoración recibida: autor, oferta y fecha que devuelve la Edge Function. */
public class Rating {
    public String ratingId = "";
    public String applicationId = "";
    public int stars;
    public String comment = "";
    public String authorId = "";
    public String authorName = "";
    public String jobId = "";
    public String jobTitle = "";
    public String createdAt = "";

    public static Rating fromJson(JSONObject o) {
        Rating r = new Rating();
        r.ratingId = o.optString("ratingId", "");
        r.applicationId = o.optString("applicationId", "");
        r.stars = o.optInt("stars", 0);
        r.comment = o.optString("comment", "");
        r.authorId = o.optString("authorId", "");
        r.authorName = o.optString("authorName", "");
        r.jobId = o.optString("jobId", "");
        r.jobTitle = o.optString("jobTitle", "");
        r.createdAt = o.optString("createdAt", "");
        return r;
    }
}
