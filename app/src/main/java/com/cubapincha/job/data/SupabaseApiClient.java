package com.cubapincha.job.data;

import android.content.Context;
import android.util.Log;

import com.cubapincha.job.BuildConfig;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cliente de la Edge Function Supabase. Envía siempre la clave publicable
 * en `apikey` y la sesión del usuario como `Authorization: Bearer <JWT>`;
 * los tokens nunca se colocan en URL ni en el cuerpo. Solo se permite el
 * host del proyecto fijado para impedir filtración a un endpoint arbitrario.
 */
public class SupabaseApiClient implements ApiClient {
    private static final int TIMEOUT_MS = 30000;
    private final String baseUrl;
    private final SessionManager session;

    public SupabaseApiClient(Context ctx, String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
        this.session = SessionManager.get(ctx);
    }

    // ---------- Transporte HTTP ----------
    private String readStream(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private JSONObject doPost(JSONObject body) throws Exception {
        return doPost(body, true);
    }

    private JSONObject doPost(JSONObject body, boolean includeSessionToken) throws Exception {
        String apiKey = session.getSupabaseApiKey();
        if (apiKey.isEmpty()) return errorResponse("SUPABASE_KEY_REQUIRED",
                "La configuración pública de Supabase no está disponible en esta compilación.");
        URL url = new URL(baseUrl);
        if (!"https".equalsIgnoreCase(url.getProtocol())
                || !"ogimwehtnhegfoamiwzm.supabase.co".equalsIgnoreCase(url.getHost())
                || (url.getPort() != -1 && url.getPort() != 443)
                || url.getUserInfo() != null) {
            return errorResponse("INVALID_API_URL", "La URL no corresponde al proyecto Supabase configurado.");
        }
        // Nunca permitir que datos de clientes antiguos manden credenciales en el payload.
        body.remove("accessToken"); body.remove("idToken"); body.remove("password");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS); conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("POST"); conn.setDoOutput(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("apikey", apiKey);
            String accessToken = includeSessionToken ? session.getToken() : null;
            if (accessToken != null && !accessToken.isEmpty())
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("X-Client-Info", "cubapincha-android/" + BuildConfig.VERSION_NAME);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String text = readStream(in);
            if (text.isEmpty()) return code >= 200 && code < 300 ? new JSONObject()
                    : errorResponse(code == 401 ? "AUTH_REQUIRED" : "HTTP_" + code,
                    "El servidor respondió HTTP " + code + ".");
            JSONObject json = new JSONObject(text);
            if (code < 200 || code >= 300) {
                // Normalizar errores de gateway a la envoltura de la Edge Function.
                if (!json.has("ok")) {
                    String message = json.optString("message", json.optString("msg", ""));
                    String errorCode = json.optString("code", code == 401 ? "AUTH_REQUIRED" : "HTTP_" + code);
                    return errorResponse(errorCode, message.isEmpty() ? "El servidor respondió HTTP " + code + "." : message);
                }
            }
            return json;
        } finally { if (conn != null) conn.disconnect(); }
    }

    private JSONObject errorResponse(String code, String message) {
        JSONObject root = new JSONObject();
        JSONObject error = new JSONObject();
        try { error.put("code", code); error.put("message", message); root.put("ok", false); root.put("error", error); }
        catch (JSONException ignored) { }
        return root;
    }

    /** Todas las acciones, incluidas las lecturas, usan POST al contrato de la Edge Function. */
    private JSONObject doRead(String action, JSONObject params) throws Exception {
        JSONObject body = params != null ? new JSONObject(params.toString()) : new JSONObject();
        body.put("action", action);
        return doPost(body);
    }

    /** La consulta de valoraciones es pública: no enviar el JWT aunque exista una sesión local. */
    private JSONObject doPublicRead(String action, JSONObject params) throws Exception {
        JSONObject body = params != null ? new JSONObject(params.toString()) : new JSONObject();
        body.put("action", action);
        return doPost(body, false);
    }

    private <T> ApiResult<T> unwrap(JSONObject resp, Parser<T> parser) {
        try {
            if (!resp.optBoolean("ok", false)) {
                JSONObject err = resp.optJSONObject("error");
                String code = err != null ? err.optString("code", "INTERNAL_ERROR") : "INTERNAL_ERROR";
                String msg = err != null ? err.optString("message", "") : "";
                return ApiResult.fail(code, msg);
            }
            return ApiResult.success(parser.parse(resp.opt("data")));
        } catch (Exception e) {
            return ApiResult.fail("INTERNAL_ERROR", "No pudimos leer la respuesta del servidor.");
        }
    }

    private interface Parser<T> {
        T parse(Object data) throws Exception;
    }

    private <T> ApiResult<T> failOf(Exception e) {
        Log.w("ApiClient", "Fallo de red/API", e);
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("UnknownHost") || msg.contains("ConnectException")
                || msg.contains("SocketTimeout") || msg.contains("No route")) {
            return ApiResult.fail("NO_CONNECTION", "");
        }
        return ApiResult.fail("INTERNAL_ERROR", "No pudimos conectar con el servidor.");
    }

    // ---------- ApiClient ----------
    @Override
    public ApiResult<String> health() {
        try {
            JSONObject resp = doRead("health", null);
            return unwrap(resp, data -> ((JSONObject) data).optString("version", "ok"));
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Session> login(String email, String role) {
        return ApiResult.fail("SUPABASE_AUTH_REQUIRED", "Inicia sesión con Supabase Auth.");
    }



    @Override
    public ApiResult<Void> logout() {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "logout");
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    private Session sessionFromJson(JSONObject o) {
        Session s = new Session();
        s.userId = o.optString("userId", "");
        s.role = o.optString("role", "");
        s.status = o.optString("status", "");
        s.displayName = o.optString("displayName", "");
        s.token = session.getToken();
        s.refreshToken = session.getRefreshToken();
        s.expiresAt = o.optString("sessionExpiresAt", "");
        s.isNew = o.optBoolean("isNew", false);
        return s;
    }

    @Override
    public ApiResult<Session> register(RegisterRequest req) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "registerProfile");
            body.put("role", req.role);
            body.put("email", req.email);
            // El consentimiento real: la UI solo llega aquí con la casilla
            // de términos marcada (antes se enviaba true fijo).
            body.put("termsAccepted", req.termsAccepted);
            putIf(body, "displayName", req.displayName);
            putIf(body, "phone", req.phone);
            putIf(body, "city", req.city);
            putIf(body, "country", req.country);
            putIf(body, "provinceCode", req.provinceCode);
            putIf(body, "municipalityCode", req.municipalityCode);
            putIf(body, "locality", req.locality);
            putIf(body, "mobilityScope", req.mobilityScope);
            if (req.canRelocate) body.put("canRelocate", true);
            if (req.remoteAvailable) body.put("remoteAvailable", true);
            putIf(body, "preferredSalaryPeriod", req.preferredSalaryPeriod);
            putIf(body, "contactHours", req.contactHours);
            putIf(body, "contactPreference", req.contactPreference);
            putIf(body, "orgType", req.orgType);
            putIf(body, "headline", req.headline);
            if (!req.skills.isEmpty()) body.put("skills", Job.listToJson(req.skills));
            if (req.experienceYears != null) body.put("experienceYears", req.experienceYears);
            putIf(body, "availability", req.availability);
            if (!req.workModes.isEmpty()) body.put("workModes", Job.listToJson(req.workModes));
            if (req.salaryMin != null) body.put("salaryMin", req.salaryMin);
            if (req.salaryMax != null) body.put("salaryMax", req.salaryMax);
            putIf(body, "legalName", req.legalName);
            putIf(body, "industry", req.industry);
            putIf(body, "size", req.size);
            putIf(body, "description", req.description);
            putIf(body, "website", req.website);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> sessionFromJson((JSONObject) data));
        } catch (Exception e) {
            return failOf(e);
        }
    }

    private static void putIf(JSONObject o, String key, String value) {
        if (value == null || value.isEmpty()) return;
        try {
            o.put(key, value);
        } catch (JSONException e) {
            // Claves constantes: no puede fallar en la práctica.
            throw new RuntimeException(e);
        }
    }

    @Override
    public ApiResult<User> me() {
        try {
            JSONObject resp = doRead("me", null);
            return unwrap(resp, data -> User.fromJson((JSONObject) data));
        } catch (Exception e) {
            return failOf(e);
        }
    }

    /** Cuerpo del perfil (campos planos): lo comparten updateProfile y la
     *  cola offline (Repository encola el mismo cuerpo para syncActions). */
    public static JSONObject profileBody(User user) {
        JSONObject body = new JSONObject();
        try {
            putIf(body, "displayName", user.displayName);
            putIf(body, "phone", user.phone);
            putIf(body, "headline", user.headline);
            putIf(body, "city", user.city);
            putIf(body, "country", user.country);
            putIf(body, "provinceCode", user.provinceCode);
            putIf(body, "municipalityCode", user.municipalityCode);
            putIf(body, "locality", user.locality);
            putIf(body, "mobilityScope", user.mobilityScope);
            body.put("canRelocate", user.canRelocate);
            body.put("remoteAvailable", user.remoteAvailable);
            putIf(body, "preferredSalaryPeriod", user.preferredSalaryPeriod);
            putIf(body, "contactHours", user.contactHours);
            putIf(body, "contactPreference", user.contactPreference);
            putIf(body, "orgType", user.orgType);
            if (!user.skills.isEmpty()) body.put("skills", Job.listToJson(user.skills));
            if (user.experienceYears != null) body.put("experienceYears", user.experienceYears);
            putIf(body, "availability", user.availability);
            if (!user.workModes.isEmpty()) body.put("workModes", Job.listToJson(user.workModes));
            if (user.salaryMin != null) body.put("salaryMin", user.salaryMin);
            if (user.salaryMax != null) body.put("salaryMax", user.salaryMax);
            putIf(body, "industry", user.industry);
            putIf(body, "size", user.size);
            putIf(body, "description", user.description);
            putIf(body, "website", user.website);
        } catch (JSONException e) {
            // Claves constantes: no puede fallar en la práctica.
            throw new RuntimeException(e);
        }
        return body;
    }

    @Override
    public ApiResult<User> updateProfile(User user) {
        try {
            JSONObject body = profileBody(user);
            body.put("action", "updateProfile");
            JSONObject resp = doPost(body);
            // El servidor devuelve { profile, status }: el perfil va anidado.
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                JSONObject profile = d.has("profile") ? d.getJSONObject("profile") : d;
                User u = User.fromJson(profile);
                if (d.has("status")) u.status = d.optString("status", u.status);
                return u;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<JSONObject> extractJob(String text) {
        try {
            String value = text == null ? "" : text.trim();
            if (value.isEmpty()) return ApiResult.fail("VALIDATION_ERROR", "Pega el texto de la oferta primero.");
            if (value.length() > 4000) return ApiResult.fail("VALIDATION_ERROR", "El texto no puede superar 4000 caracteres.");
            JSONObject body = new JSONObject();
            body.put("action", "extractJob");
            body.put("text", value);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                return d.optJSONObject("fields") != null ? d.optJSONObject("fields") : new JSONObject();
            });
        } catch (Exception e) { return failOf(e); }
    }

    @Override
    public ApiResult<Page<Job>> getJobs(JobFilter filter, String cursor) {
        try {
            JSONObject params = new JSONObject();
            params.put("limit", filter != null ? filter.limit : 20);
            if (cursor != null && !cursor.isEmpty()) params.put("cursor", cursor);
            if (filter != null) {
                putIf(params, "q", filter.q);
                putIf(params, "category", filter.category);
                putIf(params, "workMode", filter.workMode);
                putIf(params, "contractType", filter.contractType);
                putIf(params, "city", filter.city);
                putIf(params, "provinceCode", filter.provinceCode);
                putIf(params, "municipalityCode", filter.municipalityCode);
                putIf(params, "mobilityScope", filter.mobilityScope);
                if (filter.minSalary != null) params.put("minSalary", filter.minSalary);
                if (filter.maxSalary != null) params.put("maxSalary", filter.maxSalary);
                if (filter.verifiedOnly) params.put("verifiedOnly", true);
                if (filter.onlyWithSalary) params.put("onlyWithSalary", true);
                putIf(params, "status", filter.status);
                // Ordenar por salario o match también en modo conectado
                // (antes solo funcionaba en demo: el backend no recibía sort).
                putIf(params, "sort", filter.sort);
            }
            JSONObject resp = doRead("jobs", params);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                Page<Job> page = new Page<>();
                JSONArray items = d.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) page.items.add(Job.fromJson(items.getJSONObject(i)));
                }
                page.nextCursor = d.has("nextCursor") && !d.isNull("nextCursor") ? d.optString("nextCursor") : null;
                page.total = d.optInt("total", page.items.size());
                return page;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Job> getJob(String jobId) {
        try {
            JSONObject params = new JSONObject();
            params.put("id", jobId);
            JSONObject resp = doRead("job", params);
            return unwrap(resp, data -> Job.fromJson((JSONObject) data));
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<List<Application>> getApplications(String status) {
        try {
            JSONObject params = new JSONObject();
            params.put("limit", 50);
            if (status != null && !status.isEmpty() && !"closed".equals(status)) params.put("status", status);
            JSONObject resp = doRead("applications", params);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                List<Application> out = new ArrayList<>();
                JSONArray items = d.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        Application a = Application.fromJson(items.getJSONObject(i));
                        if ("closed".equals(status)) {
                            if ("rejected".equals(a.status) || "withdrawn".equals(a.status)) out.add(a);
                        } else {
                            out.add(a);
                        }
                    }
                }
                return out;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    private ApiResult<Void> validateEntity(String entityType, String entityId) {
        if (!("job".equals(entityType) || "worker".equals(entityType)) || entityId == null || entityId.trim().isEmpty())
            return ApiResult.fail("VALIDATION_ERROR", "La entidad solicitada no es válida.");
        return null;
    }

    @Override
    public ApiResult<ReactionSummary> react(String entityType, String entityId, String reaction) {
        try {
            ApiResult<Void> invalid = validateEntity(entityType, entityId);
            if (invalid != null) return ApiResult.fail(invalid.errorCode, invalid.errorMessage);
            if (!("like".equals(reaction) || "dislike".equals(reaction)))
                return ApiResult.fail("VALIDATION_ERROR", "La reacción no es válida.");
            JSONObject body = new JSONObject(); body.put("action", "react");
            body.put("entityType", entityType); body.put("entityId", entityId.trim()); body.put("reaction", reaction);
            return unwrap(doPost(body), data -> ReactionSummary.fromJson((JSONObject) data));
        } catch (Exception e) { return failOf(e); }
    }

    @Override
    public ApiResult<ReactionSummary> getReactions(String entityType, String entityId) {
        try {
            ApiResult<Void> invalid = validateEntity(entityType, entityId);
            if (invalid != null) return ApiResult.fail(invalid.errorCode, invalid.errorMessage);
            JSONObject params = new JSONObject(); params.put("entityType", entityType); params.put("entityId", entityId.trim());
            return unwrap(doPublicRead("getReactions", params), data -> ReactionSummary.fromJson((JSONObject) data));
        } catch (Exception e) { return failOf(e); }
    }

    @Override
    public ApiResult<CommentsPage> listComments(String entityType, String entityId, int limit, String cursor) {
        try {
            ApiResult<Void> invalid = validateEntity(entityType, entityId);
            if (invalid != null) return ApiResult.fail(invalid.errorCode, invalid.errorMessage);
            JSONObject params = new JSONObject(); params.put("entityType", entityType); params.put("entityId", entityId.trim());
            params.put("limit", Math.max(1, Math.min(100, limit)));
            if (cursor != null && !cursor.trim().isEmpty()) params.put("cursor", cursor.trim());
            return unwrap(doPublicRead("listComments", params), data -> CommentsPage.fromJson((JSONObject) data));
        } catch (Exception e) { return failOf(e); }
    }

    @Override
    public ApiResult<AddCommentResult> addComment(String entityType, String entityId, String text) {
        try {
            ApiResult<Void> invalid = validateEntity(entityType, entityId);
            if (invalid != null) return ApiResult.fail(invalid.errorCode, invalid.errorMessage);
            String normalized = text == null ? "" : text.trim();
            if (normalized.isEmpty() || normalized.length() > 1000)
                return ApiResult.fail("VALIDATION_ERROR", "El comentario debe tener entre 1 y 1000 caracteres.");
            JSONObject body = new JSONObject(); body.put("action", "addComment");
            body.put("entityType", entityType); body.put("entityId", entityId.trim()); body.put("text", normalized);
            return unwrap(doPost(body), data -> AddCommentResult.fromJson((JSONObject) data));
        } catch (Exception e) { return failOf(e); }
    }

    @Override
    public ApiResult<DeletedCommentResult> deleteComment(String commentId) {
        try {
            if (commentId == null || commentId.trim().isEmpty()) return ApiResult.fail("VALIDATION_ERROR", "El comentario no es válido.");
            JSONObject body = new JSONObject(); body.put("action", "deleteComment"); body.put("commentId", commentId.trim());
            return unwrap(doPost(body), data -> DeletedCommentResult.fromJson((JSONObject) data));
        } catch (Exception e) { return failOf(e); }
    }

    @Override
    public ApiResult<RatingsPage> getRatings(String userId, int limit, String cursor) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                return ApiResult.fail("VALIDATION_ERROR", "No encontramos el perfil solicitado.");
            }
            JSONObject params = new JSONObject();
            params.put("userId", userId.trim());
            params.put("limit", limit <= 0 ? 20 : Math.min(limit, 100));
            params.put("cursor", cursor == null || cursor.isEmpty() ? "0" : cursor);
            JSONObject resp = doPublicRead("getRatings", params);
            return unwrap(resp, data -> RatingsPage.fromJson((JSONObject) data));
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Application> expressInterest(String jobId, String clientActionId) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "expressInterest");
            body.put("jobId", jobId);
            // Idempotencia del cliente: el servidor la refuerza con el
            // dedupeKey jobId+workerId (antes este id se ignoraba).
            if (clientActionId != null && !clientActionId.isEmpty()) {
                body.put("clientActionId", clientActionId);
            }
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> Application.fromJson((JSONObject) data));
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Void> withdrawInterest(String applicationId) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "changeApplicationStatus");
            body.put("applicationId", applicationId);
            body.put("status", "withdrawn");
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Void> sendReminder(String applicationId) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "sendReminder");
            body.put("applicationId", applicationId);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Application> changeApplicationStatus(String applicationId, String status, String note) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "changeApplicationStatus");
            body.put("applicationId", applicationId);
            body.put("status", status);
            if (note != null && !note.isEmpty()) body.put("internalNote", note);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> Application.fromJson((JSONObject) data));
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Void> deleteApplication(String applicationId) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "deleteApplication");
            body.put("applicationId", applicationId);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<RatingSubmission> rateApplication(String applicationId, int stars, String comment) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "rateApplication");
            body.put("applicationId", applicationId);
            body.put("stars", stars);
            String normalizedComment = comment == null ? "" : comment.trim();
            if (!normalizedComment.isEmpty()) body.put("comment", normalizedComment);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> RatingSubmission.fromJson((JSONObject) data));
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Boolean> saveJob(String jobId, boolean save) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "saveJob");
            body.put("jobId", jobId);
            body.put("active", save);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> {
                if (data instanceof JSONObject) return ((JSONObject) data).optBoolean("saved", save);
                return save;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Boolean> saveCandidate(String workerId, String applicationId, boolean save) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "saveCandidate");
            body.put("workerId", workerId);
            if (applicationId != null && !applicationId.isEmpty()) body.put("applicationId", applicationId);
            body.put("active", save);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> {
                if (data instanceof JSONObject) return ((JSONObject) data).optBoolean("saved", save);
                return save;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<List<RecommendedWorker>> getSavedCandidates(int limit, String cursor) {
        try {
            JSONObject params = new JSONObject();
            params.put("limit", Math.max(1, Math.min(100, limit)));
            if (cursor != null && !cursor.isEmpty()) params.put("cursor", cursor);
            JSONObject resp = doRead("getSavedCandidates", params);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                JSONArray a = d.optJSONArray("workers");
                List<RecommendedWorker> out = new ArrayList<>();
                if (a != null) for (int i = 0; i < a.length(); i++) out.add(RecommendedWorker.fromJson(a.getJSONObject(i)));
                return out;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<List<RecommendedWorker>> getRecommendedWorkers(String q, String provinceCode, String municipalityCode, String category, int minMatch, int limit) {
        try {
            JSONObject params = new JSONObject();
            if (q != null && !q.trim().isEmpty()) params.put("q", q.trim());
            if (provinceCode != null && !provinceCode.isEmpty()) params.put("provinceCode", provinceCode);
            if (municipalityCode != null && !municipalityCode.isEmpty()) params.put("municipalityCode", municipalityCode);
            if (category != null && !category.isEmpty()) params.put("category", category);
            params.put("minMatch", Math.max(0, Math.min(100, minMatch)));
            params.put("limit", Math.max(1, Math.min(20, limit)));
            JSONObject resp = doRead("matchWorkers", params);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                JSONArray a = d.optJSONArray("workers");
                List<RecommendedWorker> out = new ArrayList<>();
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) out.add(RecommendedWorker.fromJson(a.getJSONObject(i)));
                }
                return out;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Boolean> inviteWorker(String workerId, String offerId, String message) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "inviteWorker");
            body.put("workerId", workerId);
            if (offerId != null && !offerId.isEmpty()) body.put("offerId", offerId);
            if (message != null && !message.trim().isEmpty()) body.put("message", message.trim());
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> {
                if (data instanceof JSONObject) return ((JSONObject) data).optBoolean("invited", true);
                return true;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Boolean> uninviteWorker(String workerId, String offerId) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "uninviteWorker");
            body.put("workerId", workerId);
            if (offerId != null && !offerId.isEmpty()) body.put("offerId", offerId);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> data instanceof JSONObject
                    ? ((JSONObject) data).optBoolean("invited", false) : false);
        } catch (Exception e) { return failOf(e); }
    }
    @Override
    public ApiResult<RecommendedWorker> getPublicWorker(String workerId) {
        try {
            JSONObject params = new JSONObject(); params.put("id", workerId);
            JSONObject resp = doRead("publicWorker", params);
            return unwrap(resp, data -> RecommendedWorker.fromJson((JSONObject) data));
        } catch (Exception e) { return failOf(e); }
    }
    @Override
    public ApiResult<NotificationPage> getNotifications(boolean unreadOnly) {
        try {
            JSONObject params = new JSONObject();
            params.put("limit", 50);
            if (unreadOnly) params.put("unreadOnly", true);
            JSONObject resp = doRead("notifications", params);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                NotificationPage page = new NotificationPage();
                JSONArray items = d.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) page.items.add(AppNotification.fromJson(items.getJSONObject(i)));
                }
                page.unreadCount = d.optInt("unreadCount", 0);
                page.total = d.optInt("total", page.items.size());
                return page;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Void> markNotificationRead(String notificationId) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "markNotificationRead");
            body.put("notificationId", notificationId);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Void> markAllNotificationsRead() {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "markNotificationRead");
            body.put("all", true);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Void> deleteNotifications(String notificationId, boolean all) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "deleteNotifications");
            if (all) body.put("all", true);
            else body.put("notificationId", notificationId);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    private JSONObject jobBody(Job draft) throws Exception {
        JSONObject body = new JSONObject();
        putIf(body, "title", draft.title);
        putIf(body, "category", draft.category);
        putIf(body, "description", draft.description);
        if (!draft.responsibilities.isEmpty()) body.put("responsibilities", Job.listToJson(draft.responsibilities));
        if (!draft.requirements.isEmpty()) body.put("requirements", Job.listToJson(draft.requirements));
        if (!draft.benefits.isEmpty()) body.put("benefits", Job.listToJson(draft.benefits));
        putIf(body, "city", draft.city);
        putIf(body, "provinceCode", draft.provinceCode);
        putIf(body, "municipalityCode", draft.municipalityCode);
        putIf(body, "locality", draft.locality);
        putIf(body, "workMode", draft.workMode);
        putIf(body, "contractType", draft.contractType);
        if (draft.salaryMin != null) body.put("salaryMin", draft.salaryMin);
        if (draft.salaryMax != null) body.put("salaryMax", draft.salaryMax);
        putIf(body, "salaryPeriod", draft.salaryPeriod);
        // Sin salaryCurrency: el backend lo rechaza como campo no permitido
        // (la moneda siempre es CUP y el servidor ya fija countryCode CU).
        // Este campo hacía fallar SIEMPRE crear y editar ofertas en conectado.
        body.put("includesFood", draft.includesFood);
        body.put("includesTransport", draft.includesTransport);
        body.put("includesAccommodation", draft.includesAccommodation);
        body.put("includesTips", draft.includesTips);
        putIf(body, "paymentNotes", draft.paymentNotes);
        putIf(body, "contactHours", draft.contactHours);
        putIf(body, "startDate", draft.startDate);
        if (draft.expiresAt != null && !draft.expiresAt.isEmpty()) body.put("expiresAt", draft.expiresAt);
        if (draft.experienceMin != null) body.put("experienceMin", draft.experienceMin);
        if (!draft.languages.isEmpty()) body.put("languages", Job.listToJson(draft.languages));
        if (!draft.keywords.isEmpty()) body.put("keywords", Job.listToJson(draft.keywords));
        return body;
    }

    @Override
    public ApiResult<Job> createJob(Job draft) {
        try {
            JSONObject body = jobBody(draft);
            body.put("action", "createJob");
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                return Job.fromJson(d.has("job") ? d.getJSONObject("job") : d);
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Job> updateJob(Job draft) {
        try {
            JSONObject body = jobBody(draft);
            body.put("action", "updateJob");
            body.put("jobId", draft.jobId);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                return Job.fromJson(d.has("job") ? d.getJSONObject("job") : d);
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Job> publishJob(String jobId) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "publishJob");
            body.put("jobId", jobId);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                return Job.fromJson(d.has("job") ? d.getJSONObject("job") : d);
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Job> changeJobStatus(String jobId, String status) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "changeJobStatus");
            body.put("jobId", jobId);
            body.put("status", status);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                return Job.fromJson(d.has("job") ? d.getJSONObject("job") : d);
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Job> archiveJob(String jobId, String reason) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "archiveJob");
            body.put("jobId", jobId);
            if (reason != null && !reason.isEmpty()) body.put("reason", reason);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                return Job.fromJson(d.has("job") ? d.getJSONObject("job") : d);
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Void> deleteJob(String jobId) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "deleteJob");
            body.put("jobId", jobId);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<JSONObject> listCatalogs() {
        try {
            JSONObject resp = doRead("listCatalogs", null);
            return unwrap(resp, data -> (JSONObject) data);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    /** Cuerpo de una búsqueda guardada (sin la acción): lo comparten
     *  saveFilter y la cola offline (Repository encola el mismo cuerpo). */
    public static JSONObject filterBody(SavedSearch search) {
        JSONObject body = new JSONObject();
        try {
            // Editar: sin filterId el backend crea un duplicado.
            if (search.filterId != null && !search.filterId.isEmpty()) {
                body.put("filterId", search.filterId);
            }
            body.put("name", search.name);
            body.put("enabled", search.enabled);
            // El backend guarda la frecuencia en español.
            body.put("frequency", frequencyToBackend(search.frequency));
            JSONObject filters = new JSONObject();
            putIf(filters, "q", search.query);
            putIf(filters, "category", search.category);
            putIf(filters, "workMode", search.workMode);
            putIf(filters, "contractType", search.contractType);
            putIf(filters, "city", search.city);
            putIf(filters, "provinceCode", search.provinceCode);
            putIf(filters, "municipalityCode", search.municipalityCode);
            putIf(filters, "mobilityScope", search.mobilityScope);
            if (search.minSalary != null) filters.put("minSalary", search.minSalary);
            if (search.maxSalary != null) filters.put("maxSalary", search.maxSalary);
            filters.put("verifiedOnly", search.verifiedOnly);
            filters.put("onlyWithSalary", search.onlyWithSalary);
            putIf(filters, "sort", search.sort);
            body.put("filters", filters);
            return body;
        } catch (JSONException e) {
            // Claves constantes: no puede fallar en la práctica.
            throw new RuntimeException(e);
        }
    }

    @Override
    public ApiResult<SavedSearch> saveFilter(SavedSearch search) {
        try {
            JSONObject body = filterBody(search);
            body.put("action", "saveFilter");
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> {
                SavedSearch s = SavedSearch.fromJson((JSONObject) data);
                s.frequency = frequencyFromBackend(s.frequency);
                // El backend solo devuelve id/nombre/frecuencia/activada:
                // completar el objeto con el filtro que acabamos de enviar,
                // si no la búsqueda guardada vuelve hueca (sin filtros).
                s.query = search.query;
                s.category = search.category;
                s.workMode = search.workMode;
                s.contractType = search.contractType;
                s.city = search.city;
                s.provinceCode = search.provinceCode;
                s.municipalityCode = search.municipalityCode;
                s.mobilityScope = search.mobilityScope;
                s.minSalary = search.minSalary;
                s.maxSalary = search.maxSalary;
                s.verifiedOnly = search.verifiedOnly;
                s.onlyWithSalary = search.onlyWithSalary;
                s.sort = search.sort;
                return s;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<List<SavedSearch>> getSavedSearches() {
        try {
            JSONObject resp = doRead("listFilters", null);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                List<SavedSearch> out = new ArrayList<>();
                JSONArray items = d.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        SavedSearch s = SavedSearch.fromJson(items.getJSONObject(i));
                        s.frequency = frequencyFromBackend(s.frequency);
                        out.add(s);
                    }
                }
                return out;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Void> deleteSavedSearch(String filterId) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "deleteFilter");
            body.put("filterId", filterId);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    /** Frecuencia app (inglés) → backend (español). */
    private static String frequencyToBackend(String f) {
        if ("immediate".equals(f)) return "inmediata";
        if ("off".equals(f)) return "desactivada";
        return "diaria";
    }

    /** Frecuencia backend (español) → app (inglés). */
    private static String frequencyFromBackend(String f) {
        if ("inmediata".equals(f)) return "immediate";
        if ("desactivada".equals(f)) return "off";
        if ("diaria".equals(f)) return "daily";
        return f;
    }

    @Override
    public ApiResult<ContactOptions> getContactOptions(String entityType, String entityId) {
        try {
            JSONObject params = new JSONObject();
            params.put("entityType", entityType);
            params.put("entityId", entityId);
            JSONObject resp = doRead("contactOptions", params);
            return unwrap(resp, data -> ContactOptions.fromJson((JSONObject) data));
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Void> contactEvent(String entityType, String entityId, String channel) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "contactEvent");
            body.put("entityType", entityType);
            body.put("entityId", entityId);
            body.put("channel", channel);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<JSONObject> uploadPhoto(String kind, String jobId, String imageBase64, String thumbBase64) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "uploadPhoto");
            body.put("kind", kind);
            if (jobId != null && !jobId.isEmpty()) body.put("jobId", jobId);
            body.put("imageBase64", imageBase64);
            body.put("thumbBase64", thumbBase64);
            return unwrap(doPost(body), data -> (JSONObject) data);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Void> registerDevice(String token) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "registerDevice");
            body.put("token", token);
            body.put("platform", "android");
            body.put("appVersion", BuildConfig.VERSION_NAME);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Void> unregisterDevice(String token) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "unregisterDevice");
            body.put("token", token);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<Void> report(String entityType, String entityId, String reason) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "report");
            body.put("entityType", entityType);
            body.put("entityId", entityId);
            body.put("reason", reason);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> null);
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<SyncResult> sync(String since, int offset) {
        try {
            JSONObject params = new JSONObject();
            params.put("since", since == null ? "" : since);
            params.put("offset", offset);
            JSONObject resp = doRead("sync", params);
            return unwrap(resp, data -> {
                JSONObject d = (JSONObject) data;
                SyncResult r = new SyncResult();
                r.serverTime = d.optString("serverTime", "");
                JSONArray jobs = d.optJSONArray("jobs");
                if (jobs != null) for (int i = 0; i < jobs.length(); i++) r.jobs.add(Job.fromJson(jobs.getJSONObject(i)));
                JSONArray apps = d.optJSONArray("applications");
                if (apps != null) for (int i = 0; i < apps.length(); i++) r.applications.add(Application.fromJson(apps.getJSONObject(i)));
                JSONArray notifs = d.optJSONArray("notifications");
                if (notifs != null) for (int i = 0; i < notifs.length(); i++) r.notifications.add(AppNotification.fromJson(notifs.getJSONObject(i)));
                r.hasMore = d.optBoolean("hasMore", false);
                return r;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }

    @Override
    public ApiResult<List<SyncActionResult>> syncActions(List<PendingAction> actions) {
        try {
            JSONArray arr = new JSONArray();
            for (PendingAction a : actions) {
                // Payload ANIDADO (contrato 1.2.0): el servidor lo espera
                // en `payload`, no mezclado con los metadatos.
                JSONObject o = new JSONObject();
                o.put("clientActionId", a.clientActionId);
                o.put("actionType", a.actionType);
                o.put("payload", new JSONObject(a.payloadJson != null ? a.payloadJson : "{}"));
                arr.put(o);
            }
            JSONObject body = new JSONObject();
            body.put("action", "syncActions");
            body.put("actions", arr);
            JSONObject resp = doPost(body);
            return unwrap(resp, data -> {
                List<SyncActionResult> out = new ArrayList<>();
                if (data instanceof JSONObject) {
                    JSONArray results = ((JSONObject) data).optJSONArray("results");
                    if (results != null) {
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject r = results.getJSONObject(i);
                            out.add(new SyncActionResult(
                                    r.optString("clientActionId", ""),
                                    r.optString("result", "rejected"),
                                    r.optString("errorCode", null)));
                        }
                    }
                }
                return out;
            });
        } catch (Exception e) {
            return failOf(e);
        }
    }
}
