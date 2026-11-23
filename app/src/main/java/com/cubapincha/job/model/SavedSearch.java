package com.cubapincha.job.model;

import com.cubapincha.job.data.JobFilter;

import org.json.JSONObject;

/** Búsqueda guardada: el filtro COMPLETO (texto, categoría, modalidad,
 *  contrato, ciudad, salario, verificadas, orden) + frecuencia de alerta. */
public class SavedSearch {
    public String filterId;
    public String name;
    public String query;
    public String category;
    public String workMode;
    public String contractType;
    public String city;
    public String provinceCode;
    public String municipalityCode;
    public String mobilityScope;
    public Integer minSalary;
    public Integer maxSalary;
    public boolean verifiedOnly;
    public boolean onlyWithSalary;
    public String sort = "recent";
    public String frequency; // immediate | daily | off
    public boolean enabled = true;

    /** Reconstruye el filtro tal como se guardó, para aplicarlo en Buscar. */
    public JobFilter toFilter() {
        JobFilter f = new JobFilter();
        f.q = query;
        f.category = category;
        f.workMode = workMode;
        f.contractType = contractType;
        f.city = city;
        f.provinceCode = provinceCode;
        f.municipalityCode = municipalityCode;
        f.mobilityScope = mobilityScope;
        f.minSalary = minSalary;
        f.maxSalary = maxSalary;
        f.verifiedOnly = verifiedOnly;
        f.onlyWithSalary = onlyWithSalary;
        f.sort = sort != null && !sort.isEmpty() ? sort : "recent";
        return f;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("filterId", filterId);
            o.put("name", name);
            o.put("query", query);
            o.put("category", category);
            o.put("workMode", workMode);
            o.put("contractType", contractType);
            o.put("city", city);
            o.put("provinceCode", provinceCode);
            o.put("municipalityCode", municipalityCode);
            o.put("mobilityScope", mobilityScope);
            if (minSalary != null) o.put("minSalary", minSalary);
            if (maxSalary != null) o.put("maxSalary", maxSalary);
            o.put("verifiedOnly", verifiedOnly);
            o.put("onlyWithSalary", onlyWithSalary);
            o.put("sort", sort);
            o.put("frequency", frequency);
            o.put("enabled", enabled);
        } catch (Exception ignored) { }
        return o;
    }

    public static SavedSearch fromJson(JSONObject o) {
        SavedSearch s = new SavedSearch();
        s.filterId = o.optString("filterId", "");
        s.name = o.optString("name", "");
        s.query = o.optString("query", "");
        s.category = o.optString("category", "");
        s.workMode = o.optString("workMode", "");
        s.contractType = o.optString("contractType", "");
        s.city = o.optString("city", "");
        s.provinceCode = o.optString("provinceCode", "");
        s.municipalityCode = o.optString("municipalityCode", "");
        s.mobilityScope = o.optString("mobilityScope", "");
        s.minSalary = o.has("minSalary") && !o.isNull("minSalary") ? o.optInt("minSalary") : null;
        s.maxSalary = o.has("maxSalary") && !o.isNull("maxSalary") ? o.optInt("maxSalary") : null;
        s.verifiedOnly = o.optBoolean("verifiedOnly", false);
        s.onlyWithSalary = o.optBoolean("onlyWithSalary", false);
        s.sort = o.optString("sort", "recent");
        s.frequency = o.optString("frequency", "daily");
        s.enabled = o.optBoolean("enabled", true);
        return s;
    }
}
