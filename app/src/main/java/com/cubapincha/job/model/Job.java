package com.cubapincha.job.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Oferta de empleo. Campos alineados con la Edge Function Supabase
 * (contrato API 1.4.0: provincia/municipio, salario con periodo, beneficios
 * incluidos, horario de contacto, archivado).
 *
 * `city` se conserva solo como texto heredado de ofertas antiguas: la
 * ubicación real es provincia + municipio + localidad (locationText()).
 */
public class Job {
    public String jobId;
    public String companyId;
    public String companyName;
    public boolean companyVerified;
    public String title;
    public String category;
    public String description;
    public List<String> responsibilities = new ArrayList<>();
    public List<String> requirements = new ArrayList<>();
    public Integer experienceMin;
    public List<String> languages = new ArrayList<>();
    public List<String> keywords = new ArrayList<>();
    public List<String> benefits = new ArrayList<>();
    /** @deprecated Texto libre heredado; usar provincia/municipio. */
    public String city;
    // Ubicación cubana estructurada (contrato 1.3.0)
    public String countryCode = "CU";
    public String provinceCode;    // HAB, ART, MAY, MAT, CFG, VCL, SSP, CAV, CAM, LTU, HOL, GRA, SCU, GUA, PRI, IJ
    public String provinceName;
    public String municipalityCode;
    public String municipalityName;
    public String locality;        // barrio, reparto o referencia opcional
    public String workMode;      // presencial | hibrido | remoto | por_zonas
    public String contractType;  // tiempo_completo | medio_tiempo | temporal | por_proyecto | indefinido | practicas
    // Salario en CUP con periodo (contrato 1.3.0)
    public Integer salaryMin;
    public Integer salaryMax;
    public String salaryPeriod;  // mensual | quincenal | semanal | diario | por_hora | por_proyecto | comision | a_convenir
    public boolean includesFood;
    public boolean includesTransport;
    public boolean includesAccommodation;
    public boolean includesTips;
    public String paymentNotes;
    public String contactHours;  // p. ej. "9:00–17:00"
    public String startDate;     // fecha de inicio (texto libre / ISO)
    public String status;        // draft | published | paused | closed | expired | archived
    public String archivedAt;
    public String archiveReason;
    public String publishedAt;
    public String expiresAt;
    public String createdAt;
    public String updatedAt;
    // Campos extra del cliente
    public boolean saved;
    public int matchScore;       // 0-100
    public String applicationStatus; // null si no hay interés
    public String contactPhone;  // solo demo / contacto autorizado
    public boolean whatsappEnabled;
    public double ratingAverage;
    public int ratingTotal;
    public String photoUrl = "";
    public String photoThumbUrl = "";
    /** Miniatura del perfil de la empresa que publica la oferta. */
    public String companyPhotoThumbUrl = "";
    public int likeCount;
    public int dislikeCount;
    public String myReaction;
    public int commentCount;

    public Job() {}

    public Job(String jobId, String title, String companyName, String city) {
        this.jobId = jobId;
        this.title = title;
        this.companyName = companyName;
        this.city = city;
        this.status = "published";
    }

    /** Ubicación visible: "Municipio, Provincia" (o provincia, o texto heredado). */
    public String locationText() {
        if (municipalityName != null && !municipalityName.isEmpty()
                && provinceName != null && !provinceName.isEmpty()) {
            // Muchas capitales se llaman igual que su provincia (Isla de la
            // Juventud, Cienfuegos, Camagüey...): no repetir el nombre.
            if (municipalityName.equalsIgnoreCase(provinceName)) return provinceName;
            return municipalityName + ", " + provinceName;
        }
        if (provinceName != null && !provinceName.isEmpty()) return provinceName;
        if (municipalityName != null && !municipalityName.isEmpty()) return municipalityName;
        if (city != null && !city.isEmpty()) return city;
        return "remoto".equals(workMode) ? "Remoto" : "Sin ubicación";
    }

    /** Localidad visible (barrio/reparto), si se indicó. */
    public String localityText() {
        return locality != null ? locality : "";
    }

    public String salaryText() {
        if ("a_convenir".equals(salaryPeriod)) return "A convenir";
        String period = salaryPeriodSuffix(salaryPeriod);
        if (salaryMin != null && salaryMax != null) {
            return formatMoney(salaryMin) + " – " + formatMoney(salaryMax) + " CUP" + period;
        }
        if (salaryMin != null) return "Desde " + formatMoney(salaryMin) + " CUP" + period;
        if (salaryMax != null) return "Hasta " + formatMoney(salaryMax) + " CUP" + period;
        return "A convenir";
    }

    /** Sufijo de periodo para mostrar junto al monto ("/mes", "/día", …). */
    public static String salaryPeriodSuffix(String period) {
        if (period == null) return "/mes";
        switch (period) {
            case "mensual": return "/mes";
            case "quincenal": return "/quincena";
            case "semanal": return "/semana";
            case "diario": return "/día";
            case "por_hora": return "/hora";
            case "por_proyecto": return " por proyecto";
            case "comision": return " por comisión";
            case "a_convenir": return "";
            default: return "/mes";
        }
    }

    public static String salaryPeriodLabel(String period) {
        if (period == null) return "Mensual";
        switch (period) {
            case "mensual": return "Mensual";
            case "quincenal": return "Quincenal";
            case "semanal": return "Semanal";
            case "diario": return "Diario";
            case "por_hora": return "Por hora";
            case "por_proyecto": return "Por proyecto";
            case "comision": return "Comisión";
            case "a_convenir": return "A convenir";
            default: return period;
        }
    }

    /** Periodos de salario en el orden del desplegable. */
    public static final String[] SALARY_PERIODS = {
            "mensual", "quincenal", "semanal", "diario",
            "por_hora", "por_proyecto", "comision", "a_convenir"
    };

    /** Etiquetas de periodo en el mismo orden que SALARY_PERIODS. */
    public static String[] salaryPeriodLabels() {
        String[] labels = new String[SALARY_PERIODS.length];
        for (int i = 0; i < SALARY_PERIODS.length; i++) {
            labels[i] = salaryPeriodLabel(SALARY_PERIODS[i]);
        }
        return labels;
    }

    /** Código de periodo a partir de su etiqueta visible ("" si no coincide). */
    public static String salaryPeriodFromLabel(String label) {
        if (label == null) return "";
        for (String code : SALARY_PERIODS) {
            if (salaryPeriodLabel(code).equals(label)) return code;
        }
        return "";
    }

    /** Código de modalidad a partir de su etiqueta visible ("" si no coincide). */
    public static String workModeFromLabel(String label) {
        if (label == null) return "";
        String m = label.toLowerCase();
        if (m.equals("híbrido")) return "hibrido";
        if (m.equals("por zonas")) return "por_zonas";
        if (m.equals("presencial") || m.equals("remoto")) return m;
        return "";
    }

    private static String formatMoney(int value) {
        // Locale.ROOT: el separador de miles no debe depender del idioma
        // del teléfono (en algunos locales la coma no es el separador).
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    public static String workModeLabel(String mode) {
        if (mode == null) return "";
        switch (mode) {
            case "presencial": return "Presencial";
            case "hibrido": return "Híbrido";
            case "remoto": return "Remoto";
            case "por_zonas": return "Por zonas";
            default: return mode;
        }
    }

    public static String contractLabel(String type) {
        if (type == null) return "";
        switch (type) {
            case "tiempo_completo":
            case "completo": return "Tiempo completo";
            case "medio_tiempo":
            case "parcial": return "Medio tiempo";
            case "temporal": return "Temporal";
            case "por_proyecto":
            case "proyecto": return "Por proyecto";
            case "indefinido": return "Indefinido";
            case "practicas": return "Prácticas";
            default: return type;
        }
    }

    public static String statusLabel(String status) {
        if (status == null) return "";
        switch (status) {
            case "draft": return "Borrador";
            case "published": return "Activa";
            case "paused": return "Pausada";
            case "closed": return "Cerrada";
            case "expired": return "Vencida";
            case "archived": return "Archivada";
            default: return status;
        }
    }

    /** La oferta admite interés de trabajadores. */
    public boolean isActive() {
        return "published".equals(status);
    }

    /** La oferta conserva historial (no puede borrarse físicamente). */
    public boolean hasHistory() {
        return !"draft".equals(status);
    }

    public static List<String> jsonArrayToList(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i, ""));
        return out;
    }

    public static JSONArray listToJson(List<String> list) {
        JSONArray arr = new JSONArray();
        if (list != null) for (String s : list) arr.put(s);
        return arr;
    }

    public static Job fromJson(JSONObject o) {
        Job j = new Job();
        j.jobId = o.optString("jobId", "");
        j.companyId = o.optString("companyId", "");
        j.companyName = o.optString("companyName", "");
        j.companyVerified = o.optBoolean("companyVerified", false);
        j.title = o.optString("title", "");
        j.category = o.optString("category", "");
        j.description = o.optString("description", "");
        j.responsibilities = jsonArrayToList(o.optJSONArray("responsibilities"));
        j.requirements = jsonArrayToList(o.optJSONArray("requirements"));
        j.experienceMin = o.has("experienceMin") && !o.isNull("experienceMin") ? o.optInt("experienceMin") : null;
        j.languages = jsonArrayToList(o.optJSONArray("languages"));
        j.keywords = jsonArrayToList(o.optJSONArray("keywords"));
        j.benefits = jsonArrayToList(o.optJSONArray("benefits"));
        j.city = o.optString("city", "");
        j.countryCode = o.optString("countryCode", "CU");
        j.provinceCode = o.optString("provinceCode", "");
        j.provinceName = o.optString("provinceName", "");
        j.municipalityCode = o.optString("municipalityCode", "");
        j.municipalityName = o.optString("municipalityName", "");
        j.locality = o.optString("locality", "");
        j.workMode = o.optString("workMode", "");
        j.contractType = o.optString("contractType", "");
        j.salaryMin = o.has("salaryMin") && !o.isNull("salaryMin") ? o.optInt("salaryMin") : null;
        j.salaryMax = o.has("salaryMax") && !o.isNull("salaryMax") ? o.optInt("salaryMax") : null;
        j.salaryPeriod = o.optString("salaryPeriod", "mensual");
        j.includesFood = o.optBoolean("includesFood", false);
        j.includesTransport = o.optBoolean("includesTransport", false);
        j.includesAccommodation = o.optBoolean("includesAccommodation", false);
        j.includesTips = o.optBoolean("includesTips", false);
        j.paymentNotes = o.optString("paymentNotes", "");
        j.contactHours = o.optString("contactHours", "");
        j.startDate = o.optString("startDate", "");
        // Sin estado en el JSON, NO se asume "published": un borrador sin
        // estado se mostraría como publicado. El servidor siempre envía status.
        j.status = o.optString("status", "");
        j.archivedAt = o.optString("archivedAt", "");
        j.archiveReason = o.optString("archiveReason", "");
        j.publishedAt = o.optString("publishedAt", "");
        j.expiresAt = o.optString("expiresAt", "");
        j.createdAt = o.optString("createdAt", "");
        j.updatedAt = o.optString("updatedAt", "");
        j.saved = o.optBoolean("saved", false);
        j.matchScore = o.optInt("matchScore", 0);
        j.applicationStatus = o.has("applicationStatus") && !o.isNull("applicationStatus")
                ? o.optString("applicationStatus") : null;
        j.contactPhone = o.optString("contactPhone", "");
        j.whatsappEnabled = o.optBoolean("whatsappEnabled", false);
        j.ratingAverage = o.optDouble("ratingAverage", 0d);
        j.ratingTotal = o.optInt("ratingTotal", 0);
        j.photoUrl = o.optString("photoUrl", "");
        j.photoThumbUrl = o.optString("photoThumbUrl", "");
        j.companyPhotoThumbUrl = o.optString("companyPhotoThumbUrl", "");
        j.likeCount = o.optInt("likeCount", 0);
        j.dislikeCount = o.optInt("dislikeCount", 0);
        j.myReaction = o.has("myReaction") && !o.isNull("myReaction") ? o.optString("myReaction") : null;
        j.commentCount = o.optInt("commentCount", 0);
        return j;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("jobId", jobId);
            o.put("companyId", companyId);
            o.put("companyName", companyName);
            o.put("companyVerified", companyVerified);
            o.put("title", title);
            o.put("category", category);
            o.put("description", description);
            o.put("responsibilities", listToJson(responsibilities));
            o.put("requirements", listToJson(requirements));
            if (experienceMin != null) o.put("experienceMin", experienceMin);
            o.put("languages", listToJson(languages));
            o.put("keywords", listToJson(keywords));
            o.put("benefits", listToJson(benefits));
            o.put("city", city);
            o.put("countryCode", countryCode);
            o.put("provinceCode", provinceCode);
            o.put("provinceName", provinceName);
            o.put("municipalityCode", municipalityCode);
            o.put("municipalityName", municipalityName);
            o.put("locality", locality);
            o.put("workMode", workMode);
            o.put("contractType", contractType);
            if (salaryMin != null) o.put("salaryMin", salaryMin);
            if (salaryMax != null) o.put("salaryMax", salaryMax);
            o.put("salaryPeriod", salaryPeriod);
            o.put("salaryCurrency", "CUP");
            o.put("includesFood", includesFood);
            o.put("includesTransport", includesTransport);
            o.put("includesAccommodation", includesAccommodation);
            o.put("includesTips", includesTips);
            o.put("paymentNotes", paymentNotes);
            o.put("contactHours", contactHours);
            o.put("startDate", startDate);
            o.put("status", status);
            o.put("archivedAt", archivedAt);
            o.put("archiveReason", archiveReason);
            o.put("publishedAt", publishedAt);
            o.put("expiresAt", expiresAt);
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
            o.put("saved", saved);
            o.put("matchScore", matchScore);
            if (applicationStatus != null) o.put("applicationStatus", applicationStatus);
            o.put("contactPhone", contactPhone);
            o.put("whatsappEnabled", whatsappEnabled);
            o.put("photoUrl", photoUrl);
            o.put("photoThumbUrl", photoThumbUrl);
            o.put("companyPhotoThumbUrl", companyPhotoThumbUrl);
            o.put("likeCount", likeCount); o.put("dislikeCount", dislikeCount);
            if (myReaction != null) o.put("myReaction", myReaction); else o.put("myReaction", JSONObject.NULL);
            o.put("commentCount", commentCount);
        } catch (Exception ignored) { }
        return o;
    }
}
