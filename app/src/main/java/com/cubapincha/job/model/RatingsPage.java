package com.cubapincha.job.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Resumen global más una página de valoraciones recibidas. */
public class RatingsPage {
    public String userId = "";
    public double average;
    public int total;
    public final List<Rating> ratings = new ArrayList<>();
    public String nextCursor;

    public static RatingsPage fromJson(JSONObject o) {
        RatingsPage page = new RatingsPage();
        page.userId = o.optString("userId", "");
        page.average = o.optDouble("average", 0.0);
        page.total = o.optInt("total", 0);
        page.nextCursor = o.isNull("nextCursor") ? null : o.optString("nextCursor", null);
        JSONArray items = o.optJSONArray("ratings");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) page.ratings.add(Rating.fromJson(item));
            }
        }
        return page;
    }
}
