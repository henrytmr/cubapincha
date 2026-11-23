package com.cubapincha.job.data;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Datos de registro (trabajador o empresa). */
public class RegisterRequest {
    public String role;
    public String email;
    public String password; // solo Auth REST de Supabase; nunca se envía a la Edge Function
    /** ID token de Google (solo flujo Google); se intercambia con Supabase Auth. */
    public String googleIdToken;
    public String displayName;
    public boolean termsAccepted;
    public String phone; // teléfono de contacto (WhatsApp / llamada)
    public String city;
    public String country;
    public String provinceCode;
    public String provinceName;
    public String municipalityCode;
    public String municipalityName;
    public String locality;
    public String mobilityScope;
    public boolean canRelocate;
    public boolean remoteAvailable;
    public String preferredSalaryPeriod;
    public String contactHours;
    public String contactPreference;
    public String orgType;
    public String headline;
    public List<String> skills = new ArrayList<>();
    public Integer experienceYears;
    public String availability;
    public List<String> workModes = new ArrayList<>();
    public Integer salaryMin;
    public Integer salaryMax;
    public String legalName;
    public String industry;
    public String size;
    public String description;
    public String website;

    /** Copia para que Repository pueda añadir el googleIdToken sin mutar la
     *  petición del llamador (antes req.googleIdToken se escribía sobre el
     *  objeto original). */
    public RegisterRequest copy() {
        RegisterRequest c = new RegisterRequest();
        c.role = role;
        c.email = email;
        c.password = password;
        c.googleIdToken = googleIdToken;
        c.displayName = displayName;
        c.termsAccepted = termsAccepted;
        c.phone = phone;
        c.city = city;
        c.country = country;
        c.provinceCode = provinceCode;
        c.provinceName = provinceName;
        c.municipalityCode = municipalityCode;
        c.municipalityName = municipalityName;
        c.locality = locality;
        c.mobilityScope = mobilityScope;
        c.canRelocate = canRelocate;
        c.remoteAvailable = remoteAvailable;
        c.preferredSalaryPeriod = preferredSalaryPeriod;
        c.contactHours = contactHours;
        c.contactPreference = contactPreference;
        c.orgType = orgType;
        c.headline = headline;
        c.skills = new ArrayList<>(skills);
        c.experienceYears = experienceYears;
        c.availability = availability;
        c.workModes = new ArrayList<>(workModes);
        c.salaryMin = salaryMin;
        c.salaryMax = salaryMax;
        c.legalName = legalName;
        c.industry = industry;
        c.size = size;
        c.description = description;
        c.website = website;
        return c;
    }

    /** Perfil temporal para completar registro después de confirmar email.
     *  No serializa password, ID tokens ni credenciales. */
    public JSONObject toProfileJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("role", role); o.put("email", email); o.put("displayName", displayName);
        o.put("termsAccepted", termsAccepted); o.put("phone", phone); o.put("city", city);
        o.put("country", country); o.put("provinceCode", provinceCode);
        o.put("municipalityCode", municipalityCode); o.put("locality", locality);
        o.put("mobilityScope", mobilityScope); o.put("canRelocate", canRelocate);
        o.put("remoteAvailable", remoteAvailable); o.put("preferredSalaryPeriod", preferredSalaryPeriod);
        o.put("contactHours", contactHours); o.put("contactPreference", contactPreference);
        o.put("orgType", orgType); o.put("headline", headline);
        o.put("skills", new JSONArray(skills));
        if (experienceYears != null) o.put("experienceYears", experienceYears);
        o.put("availability", availability); o.put("workModes", new JSONArray(workModes));
        if (salaryMin != null) o.put("salaryMin", salaryMin);
        if (salaryMax != null) o.put("salaryMax", salaryMax);
        o.put("legalName", legalName); o.put("industry", industry); o.put("size", size);
        o.put("description", description); o.put("website", website);
        return o;
    }

    public static RegisterRequest fromProfileJson(JSONObject o) {
        RegisterRequest r = new RegisterRequest();
        r.role = o.optString("role", ""); r.email = o.optString("email", "");
        r.displayName = o.optString("displayName", ""); r.termsAccepted = o.optBoolean("termsAccepted", false);
        r.phone = o.optString("phone", ""); r.city = o.optString("city", "");
        r.country = o.optString("country", ""); r.provinceCode = o.optString("provinceCode", "");
        r.municipalityCode = o.optString("municipalityCode", ""); r.locality = o.optString("locality", "");
        r.mobilityScope = o.optString("mobilityScope", ""); r.canRelocate = o.optBoolean("canRelocate", false);
        r.remoteAvailable = o.optBoolean("remoteAvailable", false);
        r.preferredSalaryPeriod = o.optString("preferredSalaryPeriod", "");
        r.contactHours = o.optString("contactHours", ""); r.contactPreference = o.optString("contactPreference", "");
        r.orgType = o.optString("orgType", ""); r.headline = o.optString("headline", "");
        JSONArray skillsJson = o.optJSONArray("skills");
        if (skillsJson != null) for (int i = 0; i < skillsJson.length(); i++) r.skills.add(skillsJson.optString(i));
        r.experienceYears = o.has("experienceYears") && !o.isNull("experienceYears") ? o.optInt("experienceYears") : null;
        r.availability = o.optString("availability", "");
        JSONArray modesJson = o.optJSONArray("workModes");
        if (modesJson != null) for (int i = 0; i < modesJson.length(); i++) r.workModes.add(modesJson.optString(i));
        r.salaryMin = o.has("salaryMin") && !o.isNull("salaryMin") ? o.optInt("salaryMin") : null;
        r.salaryMax = o.has("salaryMax") && !o.isNull("salaryMax") ? o.optInt("salaryMax") : null;
        r.legalName = o.optString("legalName", ""); r.industry = o.optString("industry", "");
        r.size = o.optString("size", ""); r.description = o.optString("description", "");
        r.website = o.optString("website", "");
        return r;
    }
}
