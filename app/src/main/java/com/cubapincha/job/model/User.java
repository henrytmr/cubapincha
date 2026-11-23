package com.cubapincha.job.model;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Usuario + perfil (trabajador o empresa). Alineado con readMe_ del backend (API 1.3.0). */
public class User {
    public String userId;
    public String role;   // worker | company
    public String status; // active | incomplete | pending_verification | suspended | deleted
    public String displayName;
    public String emailMasked;
    public String phone; // teléfono de contacto (WhatsApp / llamada)
    public int profilePercent;
    public int unreadNotifications;

    // Perfil trabajador
    public String workerId;
    public String headline;
    /** @deprecated Texto libre heredado; usar provincia/municipio. */
    public String city;
    public String country;
    public String provinceCode;
    public String provinceName;
    public String municipalityCode;
    public String municipalityName;
    public String locality;
    /** Alcance de movilidad: mi_municipio | mi_provincia | cualquier_lugar | remoto */
    public String mobilityScope;
    public boolean canRelocate;
    public boolean remoteAvailable;
    public String preferredSalaryPeriod; // mensual | quincenal | … | a_convenir
    public String contactHours;
    public String contactPreference; // whatsapp | llamada | correo
    public List<String> skills = new ArrayList<>();
    public Integer experienceYears;
    public String availability;
    public List<String> workModes = new ArrayList<>();
    public Integer salaryMin;
    public Integer salaryMax;
    public boolean hasCv;

    // Perfil empresa
    public String companyId;
    public String legalName;
    public String industry;
    public String size;
    public String description;
    public String website;
    public boolean verified;
    public boolean hasLogo;
    /** Tipo de organización: privada | mipyme | cooperativa | tcp | proyecto | institucional */
    public String orgType;
    public String verificationLevel; // basica | verificada

    public boolean isWorker() { return "worker".equals(role); }
    public boolean isCompany() { return "company".equals(role); }

    /** Ubicación visible del perfil: "Municipio, Provincia" (o provincia, o texto heredado). */
    public String locationText() {
        if (municipalityName != null && !municipalityName.isEmpty()
                && provinceName != null && !provinceName.isEmpty()) {
            return municipalityName + ", " + provinceName;
        }
        if (provinceName != null && !provinceName.isEmpty()) return provinceName;
        if (city != null && !city.isEmpty()) return city;
        return "";
    }

    public static String orgTypeLabel(String type) {
        if (type == null) return "";
        switch (type) {
            case "privada": return "Empresa privada";
            case "mipyme": return "Mipyme";
            case "cooperativa": return "Cooperativa";
            case "tcp": return "Trabajador por cuenta propia";
            case "proyecto": return "Proyecto de desarrollo local";
            case "institucional": return "Institución";
            default: return type;
        }
    }

    /** Tipos de organización en el orden del desplegable. */
    public static final String[] ORG_TYPES = {
            "privada", "mipyme", "cooperativa", "tcp", "proyecto", "institucional"
    };

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("userId", userId);
            o.put("role", role);
            o.put("status", status);
            o.put("displayName", displayName);
            o.put("emailMasked", emailMasked);
            o.put("phone", phone);
            o.put("unreadNotifications", unreadNotifications);
            JSONObject p = new JSONObject();
            if (isWorker()) {
                p.put("workerId", workerId);
                p.put("headline", headline);
                p.put("city", city);
                p.put("country", country);
                p.put("provinceCode", provinceCode);
                p.put("provinceName", provinceName);
                p.put("municipalityCode", municipalityCode);
                p.put("municipalityName", municipalityName);
                p.put("locality", locality);
                p.put("mobilityScope", mobilityScope);
                p.put("canRelocate", canRelocate);
                p.put("remoteAvailable", remoteAvailable);
                p.put("preferredSalaryPeriod", preferredSalaryPeriod);
                p.put("contactHours", contactHours);
                p.put("contactPreference", contactPreference);
                p.put("skills", Job.listToJson(skills));
                if (experienceYears != null) p.put("experienceYears", experienceYears);
                p.put("availability", availability);
                p.put("workModes", Job.listToJson(workModes));
                if (salaryMin != null) p.put("salaryMin", salaryMin);
                if (salaryMax != null) p.put("salaryMax", salaryMax);
                p.put("hasCv", hasCv);
                p.put("profilePercent", profilePercent);
            } else {
                p.put("companyId", companyId);
                p.put("legalName", legalName);
                p.put("displayName", displayName);
                p.put("industry", industry);
                p.put("size", size);
                p.put("description", description);
                p.put("city", city);
                p.put("country", country);
                p.put("provinceCode", provinceCode);
                p.put("provinceName", provinceName);
                p.put("municipalityCode", municipalityCode);
                p.put("municipalityName", municipalityName);
                p.put("locality", locality);
                p.put("contactHours", contactHours);
                p.put("website", website);
                p.put("verified", verified);
                p.put("verificationLevel", verificationLevel);
                p.put("orgType", orgType);
                p.put("hasLogo", hasLogo);
                p.put("profilePercent", profilePercent);
            }
            o.put("profile", p);
        } catch (Exception ignored) { }
        return o;
    }

    public static User fromJson(JSONObject o) {
        User u = new User();
        u.userId = o.optString("userId", "");
        u.role = o.optString("role", "");
        u.status = o.optString("status", "");
        u.displayName = o.optString("displayName", "");
        u.emailMasked = o.optString("emailMasked", "");
        u.phone = o.optString("phone", "");
        u.unreadNotifications = o.optInt("unreadNotifications", 0);
        JSONObject p = o.optJSONObject("profile");
        if (p != null) {
            if (u.isWorker()) {
                u.workerId = p.optString("workerId", "");
                u.headline = p.optString("headline", "");
                u.city = p.optString("city", "");
                u.country = p.optString("country", "");
                u.provinceCode = p.optString("provinceCode", "");
                u.provinceName = p.optString("provinceName", "");
                u.municipalityCode = p.optString("municipalityCode", "");
                u.municipalityName = p.optString("municipalityName", "");
                u.locality = p.optString("locality", "");
                u.mobilityScope = p.optString("mobilityScope", "");
                u.canRelocate = p.optBoolean("canRelocate", false);
                u.remoteAvailable = p.optBoolean("remoteAvailable", false);
                u.preferredSalaryPeriod = p.optString("preferredSalaryPeriod", "");
                u.contactHours = p.optString("contactHours", "");
                u.contactPreference = p.optString("contactPreference", "");
                u.skills = Job.jsonArrayToList(p.optJSONArray("skills"));
                u.experienceYears = p.has("experienceYears") && !p.isNull("experienceYears") ? p.optInt("experienceYears") : null;
                u.availability = p.optString("availability", "");
                u.workModes = Job.jsonArrayToList(p.optJSONArray("workModes"));
                u.salaryMin = p.has("salaryMin") && !p.isNull("salaryMin") ? p.optInt("salaryMin") : null;
                u.salaryMax = p.has("salaryMax") && !p.isNull("salaryMax") ? p.optInt("salaryMax") : null;
                u.hasCv = p.optBoolean("hasCv", false);
                u.profilePercent = p.optInt("profilePercent", 0);
            } else if ("company".equals(u.role)) {
                // Rama explícita de empresa: con rol vacío NO se cae aquí
                // (antes un usuario sin rol se interpretaba como empresa).
                u.companyId = p.optString("companyId", "");
                u.legalName = p.optString("legalName", "");
                if (!p.optString("displayName", "").isEmpty()) u.displayName = p.optString("displayName");
                u.industry = p.optString("industry", "");
                u.size = p.optString("size", "");
                u.description = p.optString("description", "");
                u.city = p.optString("city", "");
                u.country = p.optString("country", "");
                u.provinceCode = p.optString("provinceCode", "");
                u.provinceName = p.optString("provinceName", "");
                u.municipalityCode = p.optString("municipalityCode", "");
                u.municipalityName = p.optString("municipalityName", "");
                u.locality = p.optString("locality", "");
                u.contactHours = p.optString("contactHours", "");
                u.website = p.optString("website", "");
                u.verified = p.optBoolean("verified", false);
                u.verificationLevel = p.optString("verificationLevel", "");
                u.orgType = p.optString("orgType", "");
                u.hasLogo = p.optBoolean("hasLogo", false);
                u.profilePercent = p.optInt("profilePercent", 0);
            }
        }
        return u;
    }
}
