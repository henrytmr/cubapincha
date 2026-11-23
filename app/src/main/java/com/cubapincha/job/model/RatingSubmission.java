package com.cubapincha.job.model;

import org.json.JSONObject;

/** Resultado de rateApplication: la app solo lee esta respuesta, nunca escribe tablas directamente. */
public class RatingSubmission {
    public String ratingId = "";
    public String applicationId = "";
    public int stars;
    public String recipientId = "";
    public double average;
    public int total;

    public static RatingSubmission fromJson(JSONObject o) {
        RatingSubmission r = new RatingSubmission();
        r.ratingId = o.optString("ratingId", "");
        r.applicationId = o.optString("applicationId", "");
        r.stars = o.optInt("stars", 0);
        r.recipientId = o.optString("recipientId", "");
        r.average = o.optDouble("average", 0.0);
        r.total = o.optInt("total", 0);
        return r;
    }
}
