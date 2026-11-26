package com.cubapincha.job.data;

import android.content.Context;

import com.cubapincha.job.model.AppNotification;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.model.AddCommentResult;
import com.cubapincha.job.model.CommentsPage;
import com.cubapincha.job.model.DeletedCommentResult;
import com.cubapincha.job.model.ContactOptions;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.model.RatingsPage;
import com.cubapincha.job.model.RatingSubmission;
import com.cubapincha.job.model.ReactionSummary;
import com.cubapincha.job.model.PublicComment;
import com.cubapincha.job.model.RecommendedWorker;
import com.cubapincha.job.model.SavedSearch;
import com.cubapincha.job.model.User;
import com.cubapincha.job.util.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Cliente demo: datos locales realistas, sin red. Simula una latencia breve
 * para que skeletons y transiciones se perciban como en la app conectada.
 * Las mutaciones se persisten en LocalDb (demo_state) para sobrevivir
 * a reinicios de la app.
 */
public class DemoApiClient implements ApiClient {
    private static final String KEY_APPLICATIONS = "demo_applications";
    private static final String KEY_NOTIFICATIONS = "demo_notifications";
    private static final String KEY_MY_JOBS = "demo_my_jobs";
    private static final String KEY_SAVED_SEARCHES = "demo_saved_searches";
    private static final String KEY_DEMO_USER = "demo_user";
    private static final String KEY_CANDIDATES = "demo_candidates";
    private static final String KEY_SAVED_CANDIDATES = "demo_saved_candidates";

    private final LocalDb db;
    private final SessionManager session;
    private final List<Job> jobs = new ArrayList<>();
    private final List<Job> myJobs = new ArrayList<>();
    private final List<Application> applications = new ArrayList<>();
    private final List<Application> candidates = new ArrayList<>();
    private final List<AppNotification> notifications = new ArrayList<>();
    private final List<SavedSearch> savedSearches = new ArrayList<>();
    private final Map<String, ReactionSummary> reactionState = new HashMap<>();
    private final List<PublicComment> publicComments = new ArrayList<>();

    public DemoApiClient(Context ctx) {
        db = LocalDb.get(ctx);
        session = SessionManager.get(ctx);
        jobs.addAll(SeedData.jobs());
        loadMyJobs();
        loadApplications();
        loadCandidates();
        loadNotifications();
        loadSavedSearches();
    }

    private void latency() {
        try {
            Thread.sleep(280);
        } catch (InterruptedException ignored) { }
    }

    // ---------- Persistencia demo ----------
    private void loadMyJobs() {
        JSONArray arr = db.getDemoState(KEY_MY_JOBS);
        if (arr == null) {
            myJobs.addAll(SeedData.myCompanyJobs());
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            try {
                myJobs.add(Job.fromJson(arr.getJSONObject(i)));
            } catch (Exception ignored) { }
        }
    }

    private void saveMyJobs() {
        JSONArray arr = new JSONArray();
        for (Job j : myJobs) arr.put(j.toJson());
        db.setDemoState(KEY_MY_JOBS, arr);
    }

    private void loadApplications() {
        JSONArray arr = db.getDemoState(KEY_APPLICATIONS);
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            try {
                applications.add(Application.fromJson(arr.getJSONObject(i)));
            } catch (Exception ignored) { }
        }
    }

    private void saveApplications() {
        JSONArray arr = new JSONArray();
        for (Application a : applications) arr.put(a.toJson());
        db.setDemoState(KEY_APPLICATIONS, arr);
    }

    private void loadNotifications() {
        JSONArray arr = db.getDemoState(KEY_NOTIFICATIONS);
        if (arr == null) {
            notifications.addAll(SeedData.welcomeNotifications());
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            try {
                notifications.add(AppNotification.fromJson(arr.getJSONObject(i)));
            } catch (Exception ignored) { }
        }
    }

    private void saveNotifications() {
        JSONArray arr = new JSONArray();
        for (AppNotification n : notifications) arr.put(n.toJson());
        db.setDemoState(KEY_NOTIFICATIONS, arr);
    }

    private void loadSavedSearches() {
        JSONArray arr = db.getDemoState(KEY_SAVED_SEARCHES);
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            try {
                savedSearches.add(SavedSearch.fromJson(arr.getJSONObject(i)));
            } catch (Exception ignored) { }
        }
    }

    private void saveSavedSearches() {
        JSONArray arr = new JSONArray();
        for (SavedSearch s : savedSearches) arr.put(s.toJson());
        db.setDemoState(KEY_SAVED_SEARCHES, arr);
    }

    private void loadCandidates() {
        JSONArray arr = db.getDemoState(KEY_CANDIDATES);
        if (arr == null) {
            candidates.addAll(SeedData.demoCandidates());
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            try {
                candidates.add(Application.fromJson(arr.getJSONObject(i)));
            } catch (Exception ignored) { }
        }
    }

    private void saveCandidates() {
        JSONArray arr = new JSONArray();
        for (Application a : candidates) arr.put(a.toJson());
        db.setDemoState(KEY_CANDIDATES, arr);
    }

    private User loadDemoUser() {
        JSONArray arr = db.getDemoState(KEY_DEMO_USER);
        if (arr == null || arr.length() == 0) return null;
        try {
            return User.fromJson(arr.getJSONObject(0));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveDemoUser(User user) {
        JSONArray arr = new JSONArray();
        arr.put(user.toJson());
        db.setDemoState(KEY_DEMO_USER, arr);
    }

    private void addNotification(String type, String title, String body, String entityId, String route) {
        // La preferencia de notificaciones se respeta: si está desactivada, no se genera nada.
        if (!session.isNotificationsEnabled()) return;
        AppNotification n = new AppNotification();
        n.notificationId = UUID.randomUUID().toString();
        n.type = type;
        n.title = title;
        n.body = body;
        n.entityId = entityId;
        n.route = route;
        n.read = false;
        n.createdAt = TextUtils.nowIso();
        notifications.add(0, n);
        saveNotifications();
    }

    // ---------- ApiClient ----------
    @Override
    public ApiResult<String> health() {
        return ApiResult.success("demo-ok");
    }

    @Override
    public ApiResult<Session> login(String email, String role) {
        latency();
        // Demo: cualquier correo válido inicia sesión (sin contraseña real).
        String safeEmail = email != null ? email : "";
        String name = safeEmail.contains("@") ? safeEmail.substring(0, safeEmail.indexOf('@')) : "Usuario";
        name = name.isEmpty() ? "Usuario" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        Session s = new Session("demo-" + UUID.randomUUID().toString(), role,
                "active", name, "demo-token-" + UUID.randomUUID());
        s.isNew = false;
        return ApiResult.success(s);
    }



    @Override
    public ApiResult<Void> logout() {
        return ApiResult.success(null);
    }

    @Override
    public ApiResult<Session> register(RegisterRequest req) {
        latency();
        String name = req.displayName != null && !req.displayName.isEmpty()
                ? req.displayName : (req.legalName != null ? req.legalName : "Usuario");
        Session s = new Session("demo-" + UUID.randomUUID().toString(), req.role,
                "worker".equals(req.role) ? "active" : "pending_verification",
                name, "demo-token-" + UUID.randomUUID());
        s.isNew = true;
        // El perfil registrado se guarda tal como lo escribió el usuario:
        // antes se descartaba y me() devolvía siempre los valores de ejemplo.
        User u = new User();
        u.userId = s.userId;
        u.role = req.role;
        u.status = s.status;
        u.displayName = name;
        u.phone = req.phone;
        u.city = req.city;
        u.country = req.country != null && !req.country.isEmpty() ? req.country : "Cuba";
        u.provinceCode = req.provinceCode;
        u.provinceName = req.provinceName;
        u.municipalityCode = req.municipalityCode;
        u.municipalityName = req.municipalityName;
        u.locality = req.locality;
        u.contactHours = req.contactHours;
        if ("worker".equals(req.role)) {
            u.headline = req.headline;
            u.skills = new ArrayList<>(req.skills);
            u.experienceYears = req.experienceYears;
            u.availability = req.availability;
            u.workModes = new ArrayList<>(req.workModes);
            u.salaryMin = req.salaryMin;
            u.salaryMax = req.salaryMax;
            u.mobilityScope = req.mobilityScope;
            u.canRelocate = req.canRelocate;
            u.remoteAvailable = req.remoteAvailable;
            u.preferredSalaryPeriod = req.preferredSalaryPeriod;
            u.contactPreference = req.contactPreference;
        } else {
            u.legalName = req.legalName;
            u.industry = req.industry;
            u.size = req.size;
            u.description = req.description;
            u.website = req.website;
            u.orgType = req.orgType;
            u.verified = false;
            u.verificationLevel = "basica";
            for (Job j : myJobs) j.companyName = name;
            saveMyJobs();
        }
        saveDemoUser(u);
        return ApiResult.success(s);
    }

    @Override
    public ApiResult<JSONObject> extractJob(String text) {
        return ApiResult.fail("DEMO_UNSUPPORTED", "La extracción inteligente requiere una sesión conectada.");
    }

    @Override
    public ApiResult<User> me() {
        latency();
        User saved = loadDemoUser();
        User u = saved != null ? saved : new User();
        u.userId = session.getUserId();
        u.role = session.getRole();
        u.status = session.getStatus();
        u.displayName = session.getDisplayName();
        u.emailMasked = "u***@ejemplo.com";
        u.profilePercent = session.getProfilePercent();
        u.unreadNotifications = countUnread();
        if (saved == null) {
            if (u.isWorker()) {
                u.headline = "Profesional en búsqueda de oportunidades";
                u.city = "Miramar, La Habana";
                u.country = "Cuba";
                u.provinceCode = "HAB";
                u.provinceName = "La Habana";
                u.municipalityCode = "HAB-playa";
                u.municipalityName = "Playa";
                u.locality = "Miramar";
                u.mobilityScope = "mi_provincia";
                u.canRelocate = false;
                u.remoteAvailable = true;
                u.preferredSalaryPeriod = "mensual";
                u.contactPreference = "whatsapp";
                u.contactHours = "9:00–18:00";
                u.skills = new ArrayList<>(java.util.Arrays.asList("Java", "Android", "Atención al cliente"));
                u.workModes = new ArrayList<>(java.util.Arrays.asList("hibrido", "remoto"));
                u.availability = "Inmediata";
            } else {
                u.legalName = u.displayName;
                u.city = "Miramar, La Habana";
                u.country = "Cuba";
                u.provinceCode = "HAB";
                u.provinceName = "La Habana";
                u.municipalityCode = "HAB-playa";
                u.municipalityName = "Playa";
                u.locality = "Miramar";
                u.orgType = "mipyme";
                u.contactHours = "9:00–17:00";
                u.verified = false;
            }
        }
        return ApiResult.success(u);
    }

    @Override
    public ApiResult<User> updateProfile(User user) {
        latency();
        user.userId = session.getUserId();
        user.role = session.getRole();
        user.status = session.getStatus();
        session.setDisplayName(user.displayName);
        session.setProfilePercent(user.profilePercent);
        saveDemoUser(user);
        return ApiResult.success(user);
    }

    /** Filtrado + orden puros, sin estado de instancia: lo usa el modo demo
     *  y la búsqueda offline sobre la caché (antes Buscar sin conexión
     *  devolvía toda la caché ignorando el filtro). La movilidad ("mi
     *  municipio / mi provincia") necesita la provincia del usuario: si no
     *  se conoce (códigos null), esa cláusula se omite en vez de filtrar mal. */
    public static List<Job> filterCachedJobs(List<Job> source, JobFilter f, boolean workerView,
                                             String provinceCode, String municipalityCode) {
        List<Job> out = new ArrayList<>();
        for (Job j : source) {
            if (workerView && !"published".equals(j.status)) continue;
            if (f != null) {
                if (f.status != null && !f.status.isEmpty() && !f.status.equals(j.status)) continue;
                if (f.category != null && !f.category.isEmpty() && !f.category.equals(j.category)) continue;
                if (f.workMode != null && !f.workMode.isEmpty() && !f.workMode.equals(j.workMode)) continue;
                if (f.contractType != null && !f.contractType.isEmpty() && !f.contractType.equals(j.contractType)) continue;
                if (f.city != null && !f.city.isEmpty() && !TextUtils.normalize(j.city).contains(TextUtils.normalize(f.city))) continue;
                if (f.provinceCode != null && !f.provinceCode.isEmpty()
                        && !f.provinceCode.equals(j.provinceCode)) continue;
                if (f.municipalityCode != null && !f.municipalityCode.isEmpty()
                        && !f.municipalityCode.equals(j.municipalityCode)) continue;
                if (f.mobilityScope != null && !f.mobilityScope.isEmpty() && workerView) {
                    if ("mi_municipio".equals(f.mobilityScope) && municipalityCode != null
                            && !municipalityCode.equals(j.municipalityCode)) continue;
                    if ("mi_provincia".equals(f.mobilityScope) && provinceCode != null
                            && !provinceCode.equals(j.provinceCode)) continue;
                    if ("remoto".equals(f.mobilityScope) && !"remoto".equals(j.workMode)) continue;
                }
                if (f.onlyWithSalary && j.salaryMin == null && j.salaryMax == null) continue;
                if (f.minSalary != null && j.salaryMax != null && j.salaryMax < f.minSalary) continue;
                if (f.maxSalary != null && j.salaryMin != null && j.salaryMin > f.maxSalary) continue;
                if (f.verifiedOnly && !j.companyVerified) continue;
                if (f.q != null && !f.q.isEmpty()) {
                    String hay = j.title + " " + j.category + " " + j.description + " " + j.city
                            + " " + j.provinceName + " " + j.municipalityName + " " + j.locality
                            + " " + j.companyName;
                    if (!TextUtils.matchesQuery(hay, f.q)) continue;
                }
            }
            out.add(j);
        }
        if (f != null && "salary".equals(f.sort)) {
            Collections.sort(out, (a, b) -> Integer.compare(
                    b.salaryMax != null ? b.salaryMax : 0, a.salaryMax != null ? a.salaryMax : 0));
        } else if (f != null && "match".equals(f.sort)) {
            Collections.sort(out, (a, b) -> Integer.compare(b.matchScore, a.matchScore));
        } else {
            Collections.sort(out, (a, b) -> String.valueOf(b.publishedAt).compareTo(String.valueOf(a.publishedAt)));
        }
        return out;
    }

    private List<Job> filterJobs(List<Job> source, JobFilter f, boolean workerView) {
        List<Job> out = filterCachedJobs(source, f, workerView, demoProvinceCode(), demoMunicipalityCode());
        for (Job j : out) {
            j.saved = db.isSaved(j.jobId);
            for (Application a : applications) {
                if (a.jobId.equals(j.jobId) && !"withdrawn".equals(a.status) && !"rejected".equals(a.status)) {
                    j.applicationStatus = a.status;
                    break;
                }
            }
        }
        return out;
    }

    /** Provincia del usuario demo (para el filtro "mi municipio / mi provincia"). */
    private String demoProvinceCode() {
        User u = loadDemoUser();
        if (u != null && u.provinceCode != null && !u.provinceCode.isEmpty()) return u.provinceCode;
        return "HAB";
    }

    private String demoMunicipalityCode() {
        User u = loadDemoUser();
        if (u != null && u.municipalityCode != null && !u.municipalityCode.isEmpty()) return u.municipalityCode;
        return "HAB-playa";
    }

    @Override
    public ApiResult<Page<Job>> getJobs(JobFilter filter, String cursor) {
        latency();
        boolean workerView = !"company".equals(session.getRole());
        List<Job> source = workerView ? jobs : myJobs;
        List<Job> filtered = filterJobs(source, filter, workerView);
        Page<Job> page = new Page<>();
        int limit = filter != null ? filter.limit : 20;
        int offset = 0;
        try {
            if (cursor != null && !cursor.isEmpty()) offset = Integer.parseInt(cursor);
        } catch (NumberFormatException ignored) { }
        int end = Math.min(offset + limit, filtered.size());
        if (offset < filtered.size()) page.items.addAll(filtered.subList(offset, end));
        page.nextCursor = end < filtered.size() ? String.valueOf(end) : null;
        page.total = filtered.size();
        return ApiResult.success(page);
    }

    @Override
    public ApiResult<Job> getJob(String jobId) {
        latency();
        for (Job j : jobs) {
            if (j.jobId.equals(jobId)) {
                j.saved = db.isSaved(j.jobId);
                for (Application a : applications) {
                    if (a.jobId.equals(jobId) && !"withdrawn".equals(a.status) && !"rejected".equals(a.status)) {
                        j.applicationStatus = a.status;
                    }
                }
                return ApiResult.success(j);
            }
        }
        for (Job j : myJobs) {
            if (j.jobId.equals(jobId)) return ApiResult.success(j);
        }
        return ApiResult.fail("NOT_FOUND", "No encontramos esta oferta.");
    }

    @Override
    public ApiResult<List<Application>> getApplications(String status) {
        latency();
        List<Application> out = new ArrayList<>();
        boolean company = "company".equals(session.getRole());
        List<Application> source = company ? candidates : applications;
        for (Application a : source) {
            if (status == null || status.isEmpty()) {
                if (!company || !"withdrawn".equals(a.status)) out.add(a);
            } else if ("closed".equals(status)) {
                if ("rejected".equals(a.status) || "withdrawn".equals(a.status)) out.add(a);
            } else if (status.equals(a.status)) {
                out.add(a);
            }
        }
        Collections.sort(out, (a, b) -> String.valueOf(b.createdAt).compareTo(String.valueOf(a.createdAt)));
        return ApiResult.success(out);
    }

    private static String reactionKey(String type, String id) { return type + ":" + id; }
    private static boolean validEntity(String type, String id) {
        return ("job".equals(type) || "worker".equals(type)) && id != null && !id.trim().isEmpty();
    }
    private ReactionSummary demoSummary(String type, String id) {
        ReactionSummary r = reactionState.get(reactionKey(type, id));
        if (r == null) { r = new ReactionSummary(); r.entityType = type; r.entityId = id; reactionState.put(reactionKey(type, id), r); }
        r.commentCount = 0;
        for (PublicComment c : publicComments) if (id.equals(c.entityId)) r.commentCount++;
        return r;
    }

    @Override
    public ApiResult<ReactionSummary> react(String entityType, String entityId, String reaction) {
        latency();
        if (!validEntity(entityType, entityId) || !("like".equals(reaction) || "dislike".equals(reaction)))
            return ApiResult.fail("VALIDATION_ERROR", "La entidad o reacción no es válida.");
        ReactionSummary r = demoSummary(entityType, entityId);
        if (reaction.equals(r.myReaction)) { if ("like".equals(reaction)) r.likeCount--; else r.dislikeCount--; r.myReaction = null; }
        else { if ("like".equals(reaction)) r.likeCount++; else r.dislikeCount++; if ("like".equals(r.myReaction)) r.likeCount--; if ("dislike".equals(r.myReaction)) r.dislikeCount--; r.myReaction = reaction; }
        return ApiResult.success(r);
    }

    @Override
    public ApiResult<ReactionSummary> getReactions(String entityType, String entityId) {
        latency();
        if (!validEntity(entityType, entityId)) return ApiResult.fail("VALIDATION_ERROR", "La entidad solicitada no es válida.");
        return ApiResult.success(demoSummary(entityType, entityId));
    }

    @Override
    public ApiResult<CommentsPage> listComments(String entityType, String entityId, int limit, String cursor) {
        latency();
        if (!validEntity(entityType, entityId)) return ApiResult.fail("VALIDATION_ERROR", "La entidad solicitada no es válida.");
        CommentsPage page = new CommentsPage(); page.entityType = entityType; page.entityId = entityId;
        int offset = 0; try { if (cursor != null && !cursor.isEmpty()) offset = Integer.parseInt(cursor); } catch (Exception ignored) { }
        List<PublicComment> matches = new ArrayList<>(); for (PublicComment c : publicComments) if (entityId.equals(c.entityId)) matches.add(c);
        page.total = matches.size(); int end = Math.min(matches.size(), offset + Math.max(1, Math.min(100, limit)));
        if (offset < matches.size()) page.comments.addAll(matches.subList(offset, end));
        page.nextCursor = end < matches.size() ? String.valueOf(end) : null; return ApiResult.success(page);
    }

    @Override
    public ApiResult<AddCommentResult> addComment(String entityType, String entityId, String text) {
        latency(); String normalized = text == null ? "" : text.trim();
        if (!validEntity(entityType, entityId) || normalized.isEmpty() || normalized.length() > 1000)
            return ApiResult.fail("VALIDATION_ERROR", "El comentario debe tener entre 1 y 1000 caracteres.");
        PublicComment c = new PublicComment(); c.commentId = UUID.randomUUID().toString(); c.entityId = entityId;
        c.authorId = session.getUserId(); c.authorName = session.getDisplayName(); c.authorRole = session.getRole(); c.text = normalized; c.createdAt = TextUtils.nowIso();
        publicComments.add(0, c); ReactionSummary summary = demoSummary(entityType, entityId);
        AddCommentResult r = new AddCommentResult(); r.commentId=c.commentId; r.entityType=entityType; r.entityId=entityId; r.authorId=c.authorId; r.authorName=c.authorName; r.authorRole=c.authorRole; r.text=c.text; r.createdAt=c.createdAt; r.commentCount=summary.commentCount;
        return ApiResult.success(r);
    }

    @Override
    public ApiResult<DeletedCommentResult> deleteComment(String commentId) {
        latency(); if (commentId == null || commentId.trim().isEmpty()) return ApiResult.fail("VALIDATION_ERROR", "El comentario no es válido.");
        for (int i=0;i<publicComments.size();i++) if (commentId.equals(publicComments.get(i).commentId)) { publicComments.remove(i); DeletedCommentResult r=new DeletedCommentResult(); r.deleted=true; r.commentId=commentId; return ApiResult.success(r); }
        return ApiResult.fail("NOT_FOUND", "No encontramos el comentario.");
    }

    @Override
    public ApiResult<RatingsPage> getRatings(String userId, int limit, String cursor) {
        latency();
        RatingsPage page = new RatingsPage();
        page.userId = userId == null ? "" : userId;
        return ApiResult.success(page);
    }

    @Override
    public ApiResult<RatingSubmission> rateApplication(String applicationId, int stars, String comment) {
        latency();
        return ApiResult.fail("DEMO_UNSUPPORTED", "Las valoraciones requieren una sesión conectada.");
    }

    @Override
    public ApiResult<Application> expressInterest(String jobId, String clientActionId) {
        latency();
        for (Application a : applications) {
            if (a.jobId.equals(jobId) && !"withdrawn".equals(a.status) && !"rejected".equals(a.status)) {
                return ApiResult.success(a); // idempotente: no duplica
            }
        }
        Job job = null;
        for (Job j : jobs) if (j.jobId.equals(jobId)) job = j;
        if (job == null) return ApiResult.fail("NOT_FOUND", "No encontramos esta oferta.");
        if (!"published".equals(job.status)) {
            return ApiResult.fail("CONFLICT", "Esta publicación ya no está activa.");
        }
        Application a = new Application();
        a.applicationId = UUID.randomUUID().toString();
        a.jobId = jobId;
        a.jobTitle = job.title;
        a.jobCity = job.city;
        a.jobProvinceName = job.provinceName;
        a.jobMunicipalityName = job.municipalityName;
        a.jobWorkMode = job.workMode;
        a.companyName = job.companyName;
        a.companyVerified = job.companyVerified;
        a.status = "sent";
        a.createdAt = TextUtils.nowIso();
        a.updatedAt = a.createdAt;
        applications.add(0, a);
        saveApplications();
        addNotification("application_status_changed", "Interés enviado",
                "Tu interés en «" + job.title + "» llegó a " + job.companyName + ".",
                a.applicationId, "application/" + a.applicationId);
        return ApiResult.success(a);
    }

    @Override
    public ApiResult<Void> withdrawInterest(String applicationId) {
        latency();
        for (Application a : applications) {
            if (a.applicationId.equals(applicationId)) {
                a.status = "withdrawn";
                a.updatedAt = TextUtils.nowIso();
                saveApplications();
                return ApiResult.success(null);
            }
        }
        return ApiResult.fail("NOT_FOUND", "No encontramos esta candidatura.");
    }

    @Override
    public ApiResult<Void> deleteApplication(String applicationId) {
        latency();
        for (int i = 0; i < applications.size(); i++) {
            if (applications.get(i).applicationId.equals(applicationId)) {
                applications.remove(i);
                saveApplications();
                return ApiResult.success(null);
            }
        }
        return ApiResult.fail("NOT_FOUND", "No encontramos esta candidatura.");
    }

    @Override
    public ApiResult<Void> sendReminder(String applicationId) {
        latency();
        for (Application a : applications) {
            if (a.applicationId.equals(applicationId)) {
                if (!"sent".equals(a.status)) {
                    return ApiResult.fail("CONFLICT", "Esta candidatura ya avanzó; no hace falta recordatorio.");
                }
                a.reminderSentAt = TextUtils.nowIso();
                a.updatedAt = TextUtils.nowIso();
                saveApplications();
                return ApiResult.success(null);
            }
        }
        return ApiResult.fail("NOT_FOUND", "No encontramos esta candidatura.");
    }

    @Override
    public ApiResult<Application> changeApplicationStatus(String applicationId, String status, String note) {
        latency();
        for (Application a : candidates) {
            if (a.applicationId.equals(applicationId)) {
                a.status = status;
                if (note != null) a.internalNote = note;
                a.updatedAt = TextUtils.nowIso();
                saveCandidates();
                addNotification("application_status_changed", "Candidatura actualizada",
                        a.workerName + " · " + Application.statusLabel(status), applicationId, "candidate/" + applicationId);
                return ApiResult.success(a);
            }
        }
        return ApiResult.fail("NOT_FOUND", "No encontramos este candidato.");
    }

    @Override
    public ApiResult<Boolean> saveJob(String jobId, boolean save) {
        db.setSaved(jobId, save);
        return ApiResult.success(save);
    }

    @Override
    public ApiResult<Boolean> saveCandidate(String workerId, String applicationId, boolean save) {
        latency();
        String key = workerId != null && !workerId.isEmpty() ? workerId : applicationId;
        JSONArray arr = db.getDemoState(KEY_SAVED_CANDIDATES);
        java.util.Set<String> saved = new java.util.HashSet<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) saved.add(arr.optString(i, ""));
        }
        if (save) saved.add(key);
        else saved.remove(key);
        JSONArray out = new JSONArray();
        for (String id : saved) if (!id.isEmpty()) out.put(id);
        db.setDemoState(KEY_SAVED_CANDIDATES, out);
        return ApiResult.success(save);
    }

    @Override
    public ApiResult<List<RecommendedWorker>> getRecommendedWorkers(String q, String provinceCode, String municipalityCode, String category, int minMatch, int limit) {
        latency();
        List<RecommendedWorker> out = new ArrayList<>();
        for (Application a : candidates) {
            RecommendedWorker w = new RecommendedWorker();
            w.workerId = a.workerId;
            w.workerName = a.workerName;
            w.headline = a.workerHeadline;
            w.city = a.workerCity;
            w.skills.addAll(a.workerSkills);
            w.matchPercent = a.matchScore != null ? a.matchScore : 72;
            if (w.matchPercent >= minMatch) out.add(w);
            if (out.size() >= limit) break;
        }
        return ApiResult.success(out);
    }

    @Override
    public ApiResult<List<RecommendedWorker>> getSavedCandidates(int limit, String cursor) {
        latency();
        java.util.Set<String> ids = new java.util.HashSet<>();
        JSONArray saved = db.getDemoState(KEY_SAVED_CANDIDATES);
        if (saved != null) for (int i = 0; i < saved.length(); i++) ids.add(saved.optString(i, ""));
        List<RecommendedWorker> all = getRecommendedWorkers(null, null, null, null, 0, 100).data;
        List<RecommendedWorker> out = new ArrayList<>();
        for (RecommendedWorker w : all) if (ids.contains(w.workerId)) out.add(w);
        return ApiResult.success(out);
    }

    @Override
    public ApiResult<Boolean> inviteWorker(String workerId, String offerId, String message) {
        latency();
        addNotification("worker_invited", "Invitación enviada", "Se avisó al trabajador.", workerId, "candidate/" + workerId);
        saveNotifications();
        return ApiResult.success(true);
    }

    private int countUnread() {
        int n = 0;
        for (AppNotification notif : notifications) if (!notif.read) n++;
        return n;
    }

    @Override
    public ApiResult<Boolean> uninviteWorker(String workerId, String offerId) {
        latency(); return ApiResult.success(false);
    }
    @Override
    public ApiResult<RecommendedWorker> getPublicWorker(String workerId) {
        List<RecommendedWorker> all = getRecommendedWorkers(null,null,null,null,0,100).data;
        if (all != null) for (RecommendedWorker w : all) if (workerId.equals(w.workerId)) return ApiResult.success(w);
        return ApiResult.fail("NOT_FOUND", "Trabajador no encontrado.");
    }
    @Override
    public ApiResult<NotificationPage> getNotifications(boolean unreadOnly) {
        latency();
        NotificationPage page = new NotificationPage();
        for (AppNotification n : notifications) {
            if (unreadOnly && n.read) continue;
            page.items.add(n);
        }
        page.unreadCount = countUnread();
        page.total = notifications.size();
        return ApiResult.success(page);
    }

    @Override
    public ApiResult<Void> markNotificationRead(String notificationId) {
        for (AppNotification n : notifications) {
            if (n.notificationId.equals(notificationId)) {
                n.read = true;
                saveNotifications();
                return ApiResult.success(null);
            }
        }
        return ApiResult.fail("NOT_FOUND", "Notificación no encontrada.");
    }

    @Override
    public ApiResult<Void> markAllNotificationsRead() {
        for (AppNotification n : notifications) n.read = true;
        saveNotifications();
        return ApiResult.success(null);
    }

    @Override
    public ApiResult<Void> deleteNotifications(String notificationId, boolean all) {
        if (all) notifications.clear();
        else notifications.removeIf(n -> n.notificationId.equals(notificationId));
        saveNotifications();
        return ApiResult.success(null);
    }

    @Override
    public ApiResult<Job> createJob(Job draft) {
        latency();
        draft.jobId = "demo-my-" + UUID.randomUUID().toString();
        draft.companyName = session.getDisplayName();
        draft.companyVerified = false;
        draft.status = "draft";
        draft.createdAt = TextUtils.nowIso();
        draft.updatedAt = draft.createdAt;
        myJobs.add(0, draft);
        saveMyJobs();
        return ApiResult.success(draft);
    }

    @Override
    public ApiResult<Job> updateJob(Job draft) {
        latency();
        for (int i = 0; i < myJobs.size(); i++) {
            if (myJobs.get(i).jobId.equals(draft.jobId)) {
                draft.updatedAt = TextUtils.nowIso();
                myJobs.set(i, draft);
                saveMyJobs();
                return ApiResult.success(draft);
            }
        }
        return ApiResult.fail("NOT_FOUND", "No encontramos esta oferta.");
    }

    @Override
    public ApiResult<Job> publishJob(String jobId) {
        latency();
        for (Job j : myJobs) {
            if (j.jobId.equals(jobId)) {
                j.status = "published";
                j.publishedAt = TextUtils.nowIso();
                j.updatedAt = j.publishedAt;
                saveMyJobs();
                addNotification("new_relevant_job", "Oferta publicada",
                        "«" + j.title + "» ya está visible para los trabajadores.", jobId, "job/" + jobId);
                return ApiResult.success(j);
            }
        }
        return ApiResult.fail("NOT_FOUND", "No encontramos esta oferta.");
    }

    @Override
    public ApiResult<Job> changeJobStatus(String jobId, String status) {
        latency();
        for (Job j : myJobs) {
            if (j.jobId.equals(jobId)) {
                // Transiciones válidas: publicada ⇄ pausada; cualquiera → cerrada.
                // Un borrador no se pausa ni se publica por aquí (usa Publicar),
                // y una cerrada o vencida no resucita.
                String from = j.status;
                // Alineado con JOB_STATUS_TRANSITIONS del backend: sin
                // transiciones no-op (el backend las rechaza con CONFLICT).
                boolean allowed = "closed".equals(status)
                        || ("published".equals(from) && "paused".equals(status))
                        || ("paused".equals(from) && "published".equals(status))
                        || ("archived".equals(from) && "published".equals(status))
                        || ("archived".equals(from) && "paused".equals(status));
                if (!allowed || from.equals(status)) {
                    return ApiResult.fail("CONFLICT", "Este cambio de estado no está permitido.");
                }
                j.status = status;
                j.updatedAt = TextUtils.nowIso();
                saveMyJobs();
                return ApiResult.success(j);
            }
        }
        return ApiResult.fail("NOT_FOUND", "No encontramos esta oferta.");
    }

    @Override
    public ApiResult<Job> archiveJob(String jobId, String reason) {
        latency();
        for (Job j : myJobs) {
            if (j.jobId.equals(jobId)) {
                if ("draft".equals(j.status) || "archived".equals(j.status)) {
                    return ApiResult.fail("CONFLICT", "Esta oferta no se puede archivar.");
                }
                j.status = "archived";
                j.archivedAt = TextUtils.nowIso();
                j.archiveReason = reason != null ? reason : "";
                j.updatedAt = j.archivedAt;
                saveMyJobs();
                return ApiResult.success(j);
            }
        }
        return ApiResult.fail("NOT_FOUND", "No encontramos esta oferta.");
    }

    @Override
    public ApiResult<Void> deleteJob(String jobId) {
        latency();
        for (int i = 0; i < myJobs.size(); i++) {
            if (myJobs.get(i).jobId.equals(jobId)) {
                Job j = myJobs.get(i);
                if (!"draft".equals(j.status)) {
                    return ApiResult.fail("CONFLICT",
                            "Solo se puede eliminar un borrador. Las ofertas con historial se archivan.");
                }
                for (Application a : candidates) {
                    if (a.jobId.equals(jobId)) {
                        return ApiResult.fail("CONFLICT",
                                "Este borrador tiene candidaturas y no se puede eliminar.");
                    }
                }
                myJobs.remove(i);
                saveMyJobs();
                return ApiResult.success(null);
            }
        }
        return ApiResult.fail("NOT_FOUND", "No encontramos esta oferta.");
    }

    @Override
    public ApiResult<JSONObject> listCatalogs() {
        // En demo no hay correcciones del backend: el catálogo embebido manda.
        return ApiResult.success(new JSONObject());
    }

    @Override
    public ApiResult<SavedSearch> saveFilter(SavedSearch search) {
        latency();
        if (search.filterId == null || search.filterId.isEmpty()) {
            search.filterId = UUID.randomUUID().toString();
            savedSearches.add(search);
        } else {
            boolean found = false;
            for (int i = 0; i < savedSearches.size(); i++) {
                if (savedSearches.get(i).filterId.equals(search.filterId)) {
                    savedSearches.set(i, search);
                    found = true;
                    break;
                }
            }
            // Actualizar un id inexistente antes se ignoraba en silencio y
            // la búsqueda se perdía: si no existe, se guarda como nueva.
            if (!found) savedSearches.add(search);
        }
        saveSavedSearches();
        return ApiResult.success(search);
    }

    @Override
    public ApiResult<List<SavedSearch>> getSavedSearches() {
        latency();
        return ApiResult.success(new ArrayList<>(savedSearches));
    }

    @Override
    public ApiResult<Void> deleteSavedSearch(String filterId) {
        latency();
        for (int i = 0; i < savedSearches.size(); i++) {
            if (savedSearches.get(i).filterId.equals(filterId)) {
                savedSearches.remove(i);
                saveSavedSearches();
                return ApiResult.success(null);
            }
        }
        return ApiResult.fail("NOT_FOUND", "No encontramos esa búsqueda guardada.");
    }

    @Override
    public ApiResult<ContactOptions> getContactOptions(String entityType, String entityId) {
        latency();
        ContactOptions c = new ContactOptions();
        for (Job j : jobs) {
            if (j.jobId.equals(entityId)) {
                fillContactFromJob(c, j);
                return ApiResult.success(c);
            }
        }
        // Empresa: el contacto demo vive en sus ofertas (cualquiera vale).
        if ("company".equals(entityType)) {
            for (Job j : jobs) {
                if (j.companyId != null && j.companyId.equals(entityId) && j.contactPhone != null) {
                    fillContactFromJob(c, j);
                    return ApiResult.success(c);
                }
            }
            for (Job j : myJobs) {
                if (j.companyId != null && j.companyId.equals(entityId) && j.contactPhone != null) {
                    fillContactFromJob(c, j);
                    return ApiResult.success(c);
                }
            }
        }
        // Trabajador: en demo no hay teléfonos de trabajadores semilla.
        return ApiResult.success(c);
    }

    private static void fillContactFromJob(ContactOptions c, Job j) {
        c.whatsappEnabled = j.whatsappEnabled;
        c.phoneEnabled = j.contactPhone != null;
        c.authorized = true;
        c.publicPhone = j.contactPhone != null ? j.contactPhone : "";
        c.contactHours = j.contactHours != null ? j.contactHours : "";
    }

    @Override
    public ApiResult<Void> contactEvent(String entityType, String entityId, String channel) {
        return ApiResult.success(null);
    }

    @Override
    public ApiResult<org.json.JSONObject> uploadPhoto(String kind, String jobId,
                                                       String imageBase64, String thumbBase64) {
        return ApiResult.success(new org.json.JSONObject());
    }

    @Override
    public ApiResult<Void> registerDevice(String token) {
        return ApiResult.success(null);
    }

    @Override
    public ApiResult<Void> unregisterDevice(String token) {
        return ApiResult.success(null);
    }

    @Override
    public ApiResult<Void> report(String entityType, String entityId, String reason) {
        latency();
        return ApiResult.success(null);
    }

    @Override
    public ApiResult<SyncResult> sync(String since, int offset) {
        latency();
        SyncResult r = new SyncResult();
        r.serverTime = TextUtils.nowIso();
        return ApiResult.success(r);
    }

    @Override
    public ApiResult<List<SyncActionResult>> syncActions(List<PendingAction> actions) {
        latency();
        List<SyncActionResult> out = new ArrayList<>();
        for (PendingAction a : actions) {
            out.add(new SyncActionResult(a.clientActionId, "applied", null));
        }
        return ApiResult.success(out);
    }
}
