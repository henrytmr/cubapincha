package com.cubapincha.job.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Perfil de trabajador devuelto por la acción autenticada matchWorkers. */
public class RecommendedWorker {
    public String workerId;
    public String userId;
    public String workerName;
    public String headline;
    public String city;
    public String provinceName;
    public String municipalityName;
    public List<String> skills = new ArrayList<>();
    public Integer experienceYears;
    public List<String> workModes = new ArrayList<>();
    public Integer salaryMin;
    public Integer salaryMax;
    public String phone;
    public String contactPreference;
    public int profilePercent;
    public double ratingAverage;
    public int ratingTotal;
    public int matchPercent;
    public List<String> matchReasons = new ArrayList<>();
    public String matchedOfferId;
    public String matchedOfferTitle;

    public String locationText() {
        if (municipalityName != null && !municipalityName.isEmpty()
                && provinceName != null && !provinceName.isEmpty()) {
            return municipalityName + ", " + provinceName;
        }
        if (provinceName != null && !provinceName.isEmpty()) return provinceName;
        return city == null ? "" : city;
    }

    public String salaryText() {
        if (salaryMin == null && salaryMax == null) return "Salario: a convenir";
        if (salaryMin != null && salaryMax != null) return "Salario: " + salaryMin + "–" + salaryMax + " CUP";
        return "Salario: " + (salaryMin != null ? salaryMin : salaryMax) + " CUP";
    }

    public static RecommendedWorker fromJson(JSONObject o) {
        RecommendedWorker w = new RecommendedWorker();
        w.workerId = o.optString("workerId", "");
        w.userId = o.optString("userId", "");
        w.workerName = o.optString("workerName", o.optString("displayName", "Trabajador"));
        w.headline = o.optString("headline", "");
        w.city = o.optString("city", "");
        w.provinceName = o.optString("provinceName", "");
        w.municipalityName = o.optString("municipalityName", "");
        w.skills = strings(o.optJSONArray("skills"));
        w.experienceYears = nullableInt(o, "experienceYears");
        w.workModes = strings(o.optJSONArray("workModes"));
        w.salaryMin = nullableInt(o, "salaryMin");
        w.salaryMax = nullableInt(o, "salaryMax");
        w.phone = o.optString("phone", "");
        w.contactPreference = o.optString("contactPreference", "");
        w.profilePercent = o.optInt("profilePercent", 0);
        w.ratingAverage = o.optDouble("ratingAverage", 0d);
        w.ratingTotal = o.optInt("ratingTotal", 0);
        w.matchPercent = o.optInt("matchPercent", 0);
        w.matchReasons = strings(o.optJSONArray("matchReasons"));
        w.matchedOfferId = o.optString("matchedOfferId", "");
        w.matchedOfferTitle = o.optString("matchedOfferTitle", "");
        return w;
    }

    private static Integer nullableInt(JSONObject o, String key) {
        return o.has(key) && !o.isNull(key) ? o.optInt(key) : null;
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) out.add(a.optString(i, ""));
        return out;
    }
}
