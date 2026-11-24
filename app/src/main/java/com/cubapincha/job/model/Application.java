package com.cubapincha.job.model;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Interés / candidatura. Alineado con readApplications_ del backend. */
public class Application {
    public String applicationId;
    public String jobId;
    public String jobTitle;
    public String jobCity;
    public String jobProvinceName;
    public String jobMunicipalityName;
    public String jobWorkMode;
    public String jobStatus;
    public String workerId;
    public String companyId;
    public String companyName;
    public boolean companyVerified;
    public String workerName;
    public String workerHeadline;
    public String workerCity;
    public String workerProvinceName;
    public String workerMunicipalityName;
    public List<String> workerSkills = new ArrayList<>();
    public Integer workerExperienceYears;
    public Integer matchScore;
    public String internalNote;
    public boolean hasCv;
    public String status; // sent | reviewing | interview | hired | rejected | withdrawn
    public String message;
    /** Último recordatorio enviado por el trabajador (ISO, o vacío). */
    public String reminderSentAt;
    public String createdAt;
    public String updatedAt;
    public double ratingAverage;
    public int ratingTotal;

    public static String statusLabel(String status) {
        if (status == null) return "";
        switch (status) {
            case "sent": return "Enviado";
            case "reviewing": return "En revisión";
            case "interview": return "Entrevista";
            case "hired": return "Contratado";
            case "rejected": return "Cerrado";
            case "withdrawn": return "Retirado";
            default: return status;
        }
    }

    /** Ubicación visible de la oferta: "Municipio, Provincia" (o texto heredado). */
    public String jobLocationText() {
        if (jobMunicipalityName != null && !jobMunicipalityName.isEmpty()
                && jobProvinceName != null && !jobProvinceName.isEmpty()) {
            return jobMunicipalityName + ", " + jobProvinceName;
        }
        if (jobProvinceName != null && !jobProvinceName.isEmpty()) return jobProvinceName;
        return jobCity != null ? jobCity : "";
    }

    /** Ubicación visible del trabajador: "Municipio, Provincia" (o texto heredado). */
    public String workerLocationText() {
        if (workerMunicipalityName != null && !workerMunicipalityName.isEmpty()
                && workerProvinceName != null && !workerProvinceName.isEmpty()) {
            return workerMunicipalityName + ", " + workerProvinceName;
        }
        if (workerProvinceName != null && !workerProvinceName.isEmpty()) return workerProvinceName;
        return workerCity != null ? workerCity : "";
    }

    /** Fusiona con una candidatura ya guardada: los campos vacíos de ESTA
     *  (la nueva) se completan con los de la guardada. El sync del servidor
     *  trae candidaturas parciales (solo ids, estado y fechas); sin fusionar,
     *  guardarlas borraría el título, la empresa y el trabajador que ya
     *  teníamos en caché. Los valores nuevos no vacíos siempre ganan. */
    public void mergeFrom(Application existing) {
        if (existing == null) return;
        if (isEmpty(jobTitle)) jobTitle = existing.jobTitle;
        if (isEmpty(jobCity)) jobCity = existing.jobCity;
        if (isEmpty(jobProvinceName)) jobProvinceName = existing.jobProvinceName;
        if (isEmpty(jobMunicipalityName)) jobMunicipalityName = existing.jobMunicipalityName;
        if (isEmpty(jobWorkMode)) jobWorkMode = existing.jobWorkMode;
        if (isEmpty(jobStatus)) jobStatus = existing.jobStatus;
        if (isEmpty(companyName)) companyName = existing.companyName;
        if (!companyVerified) companyVerified = existing.companyVerified;
        if (isEmpty(workerName)) workerName = existing.workerName;
        if (isEmpty(workerHeadline)) workerHeadline = existing.workerHeadline;
        if (isEmpty(workerCity)) workerCity = existing.workerCity;
        if (isEmpty(workerProvinceName)) workerProvinceName = existing.workerProvinceName;
        if (isEmpty(workerMunicipalityName)) workerMunicipalityName = existing.workerMunicipalityName;
        if (workerSkills == null || workerSkills.isEmpty()) workerSkills = existing.workerSkills;
        if (workerExperienceYears == null) workerExperienceYears = existing.workerExperienceYears;
        if (matchScore == null) matchScore = existing.matchScore;
        if (isEmpty(internalNote)) internalNote = existing.internalNote;
        if (!hasCv) hasCv = existing.hasCv;
        if (isEmpty(message)) message = existing.message;
        if (isEmpty(reminderSentAt)) reminderSentAt = existing.reminderSentAt;
        if (isEmpty(createdAt)) createdAt = existing.createdAt;
    }

    private static boolean isEmpty(String s) { return s == null || s.isEmpty(); }

    public static Application fromJson(JSONObject o) {
        Application a = new Application();
        a.applicationId = o.optString("applicationId", "");
        a.jobId = o.optString("jobId", "");
        a.jobTitle = o.optString("jobTitle", "");
        a.jobCity = o.optString("jobCity", "");
        a.jobProvinceName = o.optString("jobProvinceName", "");
        a.jobMunicipalityName = o.optString("jobMunicipalityName", "");
        a.jobWorkMode = o.optString("jobWorkMode", "");
        a.jobStatus = o.optString("jobStatus", "");
        a.workerId = o.optString("workerId", "");
        a.companyId = o.optString("companyId", "");
        a.companyName = o.optString("companyName", "");
        a.companyVerified = o.optBoolean("companyVerified", false);
        a.workerName = o.optString("workerName", "");
        a.workerHeadline = o.optString("workerHeadline", "");
        a.workerCity = o.optString("workerCity", "");
        a.workerProvinceName = o.optString("workerProvinceName", "");
        a.workerMunicipalityName = o.optString("workerMunicipalityName", "");
        a.workerSkills = Job.jsonArrayToList(o.optJSONArray("workerSkills"));
        a.workerExperienceYears = o.has("workerExperienceYears") && !o.isNull("workerExperienceYears") ? o.optInt("workerExperienceYears") : null;
        a.matchScore = o.has("matchScore") && !o.isNull("matchScore") ? o.optInt("matchScore") : null;
        a.internalNote = o.optString("internalNote", "");
        a.hasCv = o.optBoolean("hasCv", false);
        a.status = o.optString("status", "sent");
        a.message = o.optString("message", "");
        a.reminderSentAt = o.optString("reminderSentAt", "");
        a.createdAt = o.optString("createdAt", "");
        a.updatedAt = o.optString("updatedAt", "");
        a.ratingAverage = o.optDouble("ratingAverage", 0d);
        a.ratingTotal = o.optInt("ratingTotal", 0);
        return a;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("applicationId", applicationId);
            o.put("jobId", jobId);
            o.put("jobTitle", jobTitle);
            o.put("jobCity", jobCity);
            o.put("jobProvinceName", jobProvinceName);
            o.put("jobMunicipalityName", jobMunicipalityName);
            o.put("jobWorkMode", jobWorkMode);
            o.put("jobStatus", jobStatus);
            o.put("workerId", workerId);
            o.put("companyId", companyId);
            o.put("companyName", companyName);
            o.put("companyVerified", companyVerified);
            o.put("workerName", workerName);
            o.put("workerHeadline", workerHeadline);
            o.put("workerCity", workerCity);
            o.put("workerProvinceName", workerProvinceName);
            o.put("workerMunicipalityName", workerMunicipalityName);
            o.put("workerSkills", Job.listToJson(workerSkills));
            if (workerExperienceYears != null) o.put("workerExperienceYears", workerExperienceYears);
            if (matchScore != null) o.put("matchScore", matchScore);
            o.put("internalNote", internalNote);
            o.put("hasCv", hasCv);
            o.put("status", status);
            o.put("message", message);
            o.put("reminderSentAt", reminderSentAt);
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
        } catch (Exception ignored) { }
        return o;
    }
}
