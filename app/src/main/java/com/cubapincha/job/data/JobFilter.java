package com.cubapincha.job.data;

/** Filtros de búsqueda de ofertas (contrato API 1.4.0). */
public class JobFilter {
    public String q;
    public String category;
    public String workMode;
    public String contractType;
    /** @deprecated Texto libre heredado; usar provinceCode/municipalityCode. */
    public String city;
    // Ubicación cubana estructurada
    public String provinceCode;
    public String municipalityCode;
    /** Alcance de movilidad: mi_municipio | mi_provincia | cualquier_lugar | remoto */
    public String mobilityScope;
    public Integer minSalary;
    public Integer maxSalary;
    public boolean verifiedOnly;
    public boolean onlyWithSalary;
    public String status; // empresa: draft|published|paused|closed|expired|archived
    public String sort = "recent"; // recent | salary | match
    public int limit = 20;

    public JobFilter copy() {
        JobFilter f = new JobFilter();
        f.q = q; f.category = category; f.workMode = workMode; f.contractType = contractType;
        f.city = city; f.provinceCode = provinceCode; f.municipalityCode = municipalityCode;
        f.mobilityScope = mobilityScope;
        f.minSalary = minSalary; f.maxSalary = maxSalary;
        f.verifiedOnly = verifiedOnly; f.onlyWithSalary = onlyWithSalary;
        f.status = status; f.sort = sort; f.limit = limit;
        return f;
    }

    public boolean hasActiveFilters() {
        return (q != null && !q.isEmpty())
                || (category != null && !category.isEmpty())
                || (workMode != null && !workMode.isEmpty())
                || (contractType != null && !contractType.isEmpty())
                || (city != null && !city.isEmpty())
                || (provinceCode != null && !provinceCode.isEmpty())
                || (municipalityCode != null && !municipalityCode.isEmpty())
                || (mobilityScope != null && !mobilityScope.isEmpty())
                || minSalary != null || maxSalary != null || verifiedOnly || onlyWithSalary;
    }

    /** Para pasar el filtro por argumentos de fragmento (sobrevive a rotar). */
    public android.os.Bundle toBundle() {
        android.os.Bundle b = new android.os.Bundle();
        b.putString("q", q);
        b.putString("category", category);
        b.putString("workMode", workMode);
        b.putString("contractType", contractType);
        b.putString("city", city);
        b.putString("provinceCode", provinceCode);
        b.putString("municipalityCode", municipalityCode);
        b.putString("mobilityScope", mobilityScope);
        if (minSalary != null) b.putInt("minSalary", minSalary);
        if (maxSalary != null) b.putInt("maxSalary", maxSalary);
        b.putBoolean("verifiedOnly", verifiedOnly);
        b.putBoolean("onlyWithSalary", onlyWithSalary);
        b.putString("status", status);
        b.putString("sort", sort);
        b.putInt("limit", limit);
        return b;
    }

    public static JobFilter fromBundle(android.os.Bundle b) {
        JobFilter f = new JobFilter();
        if (b == null) return f;
        f.q = b.getString("q");
        f.category = b.getString("category");
        f.workMode = b.getString("workMode");
        f.contractType = b.getString("contractType");
        f.city = b.getString("city");
        f.provinceCode = b.getString("provinceCode");
        f.municipalityCode = b.getString("municipalityCode");
        f.mobilityScope = b.getString("mobilityScope");
        if (b.containsKey("minSalary")) f.minSalary = b.getInt("minSalary");
        if (b.containsKey("maxSalary")) f.maxSalary = b.getInt("maxSalary");
        f.verifiedOnly = b.getBoolean("verifiedOnly", false);
        f.onlyWithSalary = b.getBoolean("onlyWithSalary", false);
        f.status = b.getString("status");
        f.sort = b.getString("sort", "recent");
        f.limit = b.getInt("limit", 20);
        return f;
    }

    public static String mobilityScopeLabel(String scope) {
        if (scope == null) return "";
        switch (scope) {
            case "mi_municipio": return "Solo mi municipio";
            case "mi_provincia": return "Toda mi provincia";
            case "cualquier_lugar": return "Cualquier lugar";
            case "remoto": return "Solo remoto";
            default: return scope;
        }
    }
}
