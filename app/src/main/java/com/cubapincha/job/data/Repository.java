package com.cubapincha.job.data;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.os.Looper;

import com.cubapincha.job.model.AppNotification;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.model.ContactOptions;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.model.RatingsPage;
import com.cubapincha.job.model.RatingSubmission;
import com.cubapincha.job.model.RecommendedWorker;
import com.cubapincha.job.model.SavedSearch;
import com.cubapincha.job.model.User;
import com.cubapincha.job.util.NetworkUtil;
import com.cubapincha.job.util.TextUtils;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Punto único de acceso a datos. Elige el cliente activo (demo o conectado),
 * ejecuta en segundo plano, entrega en el hilo principal, mantiene la caché
 * local y la cola de acciones offline.
 *
 * Sesión conectada: Supabase Auth emite JWT y refresh token; la Edge Function
 * recibe el JWT en Authorization. Si una llamada devuelve AUTH_REQUIRED, se
 * rota el refresh token una vez y se reintenta. Demo y cola offline permanecen.

 */
public class Repository {
    private static Repository instance;

    private final Context appContext;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SessionManager session;
    private final LocalDb db;
    private DemoApiClient demoClient;
    private SupabaseApiClient remoteClient;
    /** Guarda de reentrancia: dos sync a la vez duplicarían el trabajo. */
    private volatile boolean syncRunning;

    public static synchronized Repository get(Context ctx) {
        if (instance == null) instance = new Repository(ctx.getApplicationContext());
        return instance;
    }

    private Repository(Context ctx) {
        appContext = ctx;
        session = SessionManager.get(ctx);
        db = LocalDb.get(ctx);
    }

    /** Cliente activo según el modo y la URL configurada. Sincronizado:
     *  sin esto, dos hilos podían crear dos DemoApiClient con estados
     *  divergentes. */
    public synchronized ApiClient activeClient() {
        if (session.isDemoMode() || session.getApiUrl().isEmpty()) {
            if (demoClient == null) demoClient = new DemoApiClient(appContext);
            return demoClient;
        }
        if (remoteClient == null) remoteClient = new SupabaseApiClient(appContext, session.getApiUrl());
        return remoteClient;
    }

    public void resetRemoteClient() {
        remoteClient = null;
    }

    /** Olvida el cliente demo (sus listas en memoria se resiembra al crear otro). */
    public void resetDemoClient() {
        demoClient = null;
    }

    public boolean isDemoMode() {
        return session.isDemoMode() || session.getApiUrl().isEmpty();
    }

    public boolean isOnline() {
        return NetworkUtil.isOnline(appContext);
    }

    public LocalDb db() { return db; }
    public SessionManager session() { return session; }

    /** Ejecuta con renovación automática de sesión ante AUTH_REQUIRED (un reintento). */
    private <T> void run(Supplier<ApiResult<T>> work, ApiCallback<T> cb) {
        executor.execute(() -> {
            ApiResult<T> result;
            try {
                result = work.get();
                if (!result.ok && "AUTH_REQUIRED".equals(result.errorCode)
                        && !isDemoMode() && tryRefreshSession()) {
                    result = work.get();
                }
                if (!result.ok && "AUTH_REQUIRED".equals(result.errorCode) && !isDemoMode()) {
                    session.logout();
                    session.setAuthExpired(true);
                    resetRemoteClient();
                }
            } catch (Exception e) {
                Log.w("Repository", "Error ejecutando llamada", e);
                result = ApiResult.fail("INTERNAL_ERROR", "Algo no salió como esperábamos.");
            }
            final ApiResult<T> finalResult = result;
            main.post(() -> cb.onResult(finalResult));
        });
    }

    /** Ejecuta sin renovación de sesión (login, registro, logout, salud). */
    private <T> void runPlain(Supplier<ApiResult<T>> work, ApiCallback<T> cb) {
        executor.execute(() -> {
            ApiResult<T> result;
            try {
                result = work.get();
            } catch (Exception e) {
                Log.w("Repository", "Error ejecutando llamada", e);
                result = ApiResult.fail("INTERNAL_ERROR", "Algo no salió como esperábamos.");
            }
            final ApiResult<T> finalResult = result;
            main.post(() -> cb.onResult(finalResult));
        });
    }

    /** Rota el refresh token de Supabase (tokens guardados cifrados). */
    private synchronized boolean tryRefreshSession() {
        String refreshToken = session.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty() || session.getSupabaseApiKey().isEmpty()) return false;
        ApiResult<SupabaseAuthRest.AuthTokens> r = SupabaseAuthRest.refresh(
                session.getSupabaseProjectUrl(), session.getSupabaseApiKey(), refreshToken);
        if (!r.ok || r.data == null || !r.data.hasSession()) return false;
        session.saveAuthTokens(r.data.accessToken, r.data.refreshToken, r.data.expiresInSec);
        return true;
    }

    private Session sessionFromAuthAndUser(SupabaseAuthRest.AuthTokens auth, User user) {
        Session s = new Session();
        s.userId = user.userId;
        s.role = user.role;
        s.status = user.status;
        s.displayName = user.displayName;
        s.token = auth.accessToken;
        s.refreshToken = auth.refreshToken;
        long expiresAt = auth.expiresAtEpochSec > 0 ? auth.expiresAtEpochSec
                : (auth.expiresInSec > 0 ? java.time.Instant.now().getEpochSecond() + auth.expiresInSec : 0);
        s.expiresAt = expiresAt > 0 ? java.time.Instant.ofEpochSecond(expiresAt).toString() : "";
        session.setProfilePercent(user.profilePercent);
        return s;
    }

    /** Registra el dispositivo en el backend (best-effort, sin bloquear).
     *  Solo el token FCM real: el deviceId (UUID de instalación) no es un
     *  token válido y contaminaría la tabla de dispositivos. */
    private void registerDeviceBestEffort() {
        try {
            String token = session.getFcmToken();
            if (token != null && !token.isEmpty()) {
                activeClient().registerDevice(token);
            }
        } catch (Exception ignored) { }
    }

    /** Registra el token FCM real en el backend (push). */
    public void registerPushToken(String token, ApiCallback<Void> cb) {
        run(() -> activeClient().registerDevice(token), cb);
    }

    /** Guarda la sesión y, si entra OTRA cuenta distinta, borra la cola
     *  offline y los borradores de la anterior (no le pertenecen a la
     *  nueva). La misma cuenta al volver a entrar conserva su trabajo. */
    private void saveSessionChecked(Session s, boolean remember) {
        String prevUser = session.getUserId();
        String offlineOwner = db.getMeta("offline_owner_user_id");
        String candidateOwner = offlineOwner != null && !offlineOwner.isEmpty()
                ? offlineOwner : prevUser;
        boolean hasLegacyUnownedWork = (offlineOwner == null || offlineOwner.isEmpty())
                && db.getOfflineWorkCount() > 0;
        boolean ownerChanged = candidateOwner != null && !candidateOwner.isEmpty()
                && s.userId != null && !s.userId.isEmpty() && !s.userId.equals(candidateOwner);
        // Datos pre-migración sin propietario conocido se eliminan de forma
        // conservadora para que nunca aparezcan en una cuenta recién iniciada.
        if (ownerChanged || (hasLegacyUnownedWork
                && (prevUser == null || prevUser.isEmpty() || !String.valueOf(s.userId).equals(prevUser)))) {
            db.clearAccountData();
            db.clearPendingAndDrafts();
        }
        if (s.userId != null && !s.userId.isEmpty()) {
            db.setMeta("offline_owner_user_id", s.userId);
        }
        session.saveSession(s, remember);
    }

    // ---------- Sesión ----------
    public void login(String email, String password, String role, boolean remember, ApiCallback<Session> cb) {
        runPlain(() -> {
            if (isDemoMode()) {
                ApiResult<Session> demo = activeClient().login(email, role);
                if (demo.ok && demo.data != null) saveSessionChecked(demo.data, remember);
                return demo;
            }
            String key = session.getSupabaseApiKey();
            if (key.isEmpty()) return ApiResult.fail("SUPABASE_KEY_REQUIRED", "La configuración de Supabase no está disponible en esta compilación.");
            ApiResult<SupabaseAuthRest.AuthTokens> auth = SupabaseAuthRest.signIn(
                    session.getSupabaseProjectUrl(), key, email, password);
            if (!auth.ok || auth.data == null) return ApiResult.fail(auth.errorCode, auth.errorMessage);
            session.saveAuthTokens(auth.data.accessToken, auth.data.refreshToken, auth.data.expiresInSec);

            // Si el usuario confirmó su correo después del signup, completar el
            // perfil ahora con el JWT validado y los datos locales cifrados.
            RegisterRequest pending = session.getPendingRegistration();
            if (pending != null && pending.email != null && pending.email.equalsIgnoreCase(email.trim())) {
                pending.password = null;
                ApiResult<Session> registered = activeClient().register(pending);
                if (registered.ok && registered.data != null) {
                    registered.data.token = auth.data.accessToken;
                    registered.data.refreshToken = auth.data.refreshToken;
                    saveSessionChecked(registered.data, remember);
                    session.clearPendingRegistration();
                    registerDeviceBestEffort();
                }
                return registered;
            }
            ApiResult<User> me = activeClient().me();
            if (!me.ok || me.data == null) return ApiResult.fail(me.errorCode, me.errorMessage);
            Session result = sessionFromAuthAndUser(auth.data, me.data);
            saveSessionChecked(result, remember);
            session.setAuthExpired(false);
            registerDeviceBestEffort();
            return ApiResult.success(result);
        }, cb);
    }

    /** Google ID token se intercambia directamente con Supabase Auth; Firebase
     *  Auth no participa en el flujo. */
    public void loginWithGoogle(String googleIdToken, String email, String role,
                                boolean remember, ApiCallback<Session> cb) {
        runPlain(() -> {
            if (isDemoMode()) {
                ApiResult<Session> demo = activeClient().login(email, role);
                if (demo.ok && demo.data != null) saveSessionChecked(demo.data, remember);
                return demo;
            }
            String key = session.getSupabaseApiKey();
            if (key.isEmpty()) return ApiResult.fail("SUPABASE_KEY_REQUIRED", "La configuración de Supabase no está disponible en esta compilación.");
            if (googleIdToken == null || googleIdToken.isEmpty())
                return ApiResult.fail("AUTH_REQUIRED", "Google no devolvió un ID token. Revisa la configuración OAuth.");
            ApiResult<SupabaseAuthRest.AuthTokens> auth = SupabaseAuthRest.signInWithGoogle(
                    session.getSupabaseProjectUrl(), key, googleIdToken);
            if (!auth.ok || auth.data == null) return ApiResult.fail(auth.errorCode, auth.errorMessage);
            session.saveAuthTokens(auth.data.accessToken, auth.data.refreshToken, auth.data.expiresInSec);
            ApiResult<User> me = activeClient().me();
            if (me.ok && me.data != null) {
                Session result = sessionFromAuthAndUser(auth.data, me.data);
                saveSessionChecked(result, remember);
                registerDeviceBestEffort();
                return ApiResult.success(result);
            }
            if ("NOT_FOUND".equals(me.errorCode)) {
                session.savePendingGoogle(email, auth.data.displayName, googleIdToken);
                return ApiResult.fail("NOT_FOUND", "Completa tu perfil para crear la cuenta de CubaPincha.");
            }
            return ApiResult.fail(me.errorCode, me.errorMessage);
        }, cb);
    }

    public void register(RegisterRequest req, ApiCallback<Session> cb) {
        runPlain(() -> {
            RegisterRequest request = req.copy();
            if (isDemoMode()) {
                ApiResult<Session> demo = activeClient().register(request);
                if (demo.ok && demo.data != null) saveSessionChecked(demo.data, true);
                return demo;
            }
            String key = session.getSupabaseApiKey();
            if (key.isEmpty()) return ApiResult.fail("SUPABASE_KEY_REQUIRED", "La configuración de Supabase no está disponible en esta compilación.");
            if (!request.termsAccepted) return ApiResult.fail("VALIDATION_ERROR", "Debes aceptar los términos para crear la cuenta.");
            session.savePendingRegistration(request); // no guarda password ni ID token
            ApiResult<SupabaseAuthRest.AuthTokens> auth;
            if (request.googleIdToken != null && !request.googleIdToken.isEmpty()) {
                boolean samePendingGoogle = session.hasPendingGoogle()
                        && session.getPendingGoogleEmail().equalsIgnoreCase(request.email.trim())
                        && request.googleIdToken.equals(session.getPendingGoogleIdToken());
                String currentAccess = session.getToken();
                String currentRefresh = session.getRefreshToken();
                if (samePendingGoogle && currentAccess != null && !currentAccess.isEmpty()
                        && currentRefresh != null && !currentRefresh.isEmpty()) {
                    // El ID token de Google ya se canjeó al detectar que aún no
                    // existía perfil. Reutiliza la sesión Supabase cifrada; el
                    // ID token de Google puede haber caducado durante el formulario.
                    auth = ApiResult.success(new SupabaseAuthRest.AuthTokens());
                    auth.data.accessToken = currentAccess;
                    auth.data.refreshToken = currentRefresh;
                    auth.data.displayName = session.getPendingGoogleName();
                    try {
                        String expiresAt = session.getExpiresAt();
                        if (expiresAt != null && !expiresAt.isEmpty()) {
                            auth.data.expiresAtEpochSec = java.time.Instant.parse(expiresAt).getEpochSecond();
                        }
                    } catch (Exception ignored) { }
                } else {
                    auth = SupabaseAuthRest.signInWithGoogle(
                            session.getSupabaseProjectUrl(), key, request.googleIdToken);
                }
            } else {
                String verifier = SupabaseAuthRest.createPkceVerifier();
                session.savePkceVerifier(verifier);
                auth = SupabaseAuthRest.signUp(session.getSupabaseProjectUrl(), key, request, verifier);
            }
            if (!auth.ok || auth.data == null) {
                if ("DUPLICATE".equals(auth.errorCode) && request.password != null && !request.password.isEmpty()) {
                    auth = SupabaseAuthRest.signIn(session.getSupabaseProjectUrl(), key, request.email, request.password);
                }
            }
            if (!auth.ok || auth.data == null) return ApiResult.fail(auth.errorCode, auth.errorMessage);
            if (!auth.data.hasSession()) {
                return ApiResult.fail("EMAIL_CONFIRMATION_REQUIRED",
                        "Te enviamos un correo para confirmar tu cuenta. Cuando lo confirmes, inicia sesión con ese correo y contraseña para completar el perfil.");
            }
            session.clearPkceVerifier();
            session.saveAuthTokens(auth.data.accessToken, auth.data.refreshToken, auth.data.expiresInSec);
            request.password = null; request.googleIdToken = null;
            ApiResult<Session> registered = activeClient().register(request);
            if (!registered.ok && "AUTH_REQUIRED".equals(registered.errorCode) && tryRefreshSession()) {
                auth.data.accessToken = session.getToken();
                auth.data.refreshToken = session.getRefreshToken();
                registered = activeClient().register(request);
            }
            if (registered.ok && registered.data != null) {
                registered.data.token = session.getToken();
                registered.data.refreshToken = session.getRefreshToken();
                saveSessionChecked(registered.data, true);
                session.clearPendingRegistration();
                session.clearPendingGoogle();
                registerDeviceBestEffort();
            }
            return registered;
        }, cb);
    }

    /** Cierra sesión: revoca en el servidor (si hay conexión) y limpia todo lo local. */
    public void logout(ApiCallback<Void> cb) {
        runPlain(() -> {
            if (!isDemoMode() && isOnline() && session.getToken() != null) {
                // Dar de baja el token push: si no, el teléfono seguiría
                // recibiendo los avisos de esta cuenta tras cerrar sesión.
                try {
                    String fcmToken = session.getFcmToken();
                    if (fcmToken != null && !fcmToken.isEmpty()) {
                        activeClient().unregisterDevice(fcmToken);
                    }
                } catch (Exception ignored) { }
                try { activeClient().logout(); } catch (Exception ignored) { }
            }
            session.logout();
            db.clearUserData();
            resetDemoClient();
            resetRemoteClient();
            return ApiResult.success(null);
        }, cb);
    }

    /** Correo de restablecimiento de contraseña de Supabase Auth. */
    public void resetPassword(String email, ApiCallback<Void> cb) {
        runPlain(() -> {
            if (isDemoMode()) return ApiResult.success(null);
            String apiKey = session.getSupabaseApiKey();
            if (apiKey.isEmpty()) return ApiResult.fail("SUPABASE_KEY_REQUIRED", "La configuración de Supabase no está disponible en esta compilación.");
            String verifier = SupabaseAuthRest.createPkceVerifier();
            session.savePkceVerifier(verifier);
            ApiResult<Void> r = SupabaseAuthRest.resetPassword(session.getSupabaseProjectUrl(), apiKey, email, verifier);
            if (!r.ok) session.clearPkceVerifier();
            return r;
        }, cb);
    }

    /** Intercambia el authorization code PKCE del deep link y guarda los
     *  tokens en el almacén cifrado para completar registro o recovery. */
    public void exchangeAuthCode(String code, ApiCallback<SupabaseAuthRest.AuthTokens> cb) {
        runPlain(() -> {
            String verifier = session.getPkceVerifier();
            if (verifier == null || verifier.isEmpty())
                return ApiResult.fail("AUTH_REQUIRED", "No encontramos la verificación segura del enlace. Solicita uno nuevo.");
            ApiResult<SupabaseAuthRest.AuthTokens> result = SupabaseAuthRest.exchangeCode(
                    session.getSupabaseProjectUrl(), session.getSupabaseApiKey(), code, verifier);
            if (result.ok && result.data != null) {
                session.saveAuthTokens(result.data.accessToken, result.data.refreshToken, result.data.expiresInSec);
                session.clearPkceVerifier();
            }
            return result;
        }, cb);
    }

    /** Termina el perfil local pendiente usando el JWT de confirmación ya
     *  recibido por el deep link de Supabase Auth. */
    public void completePendingRegistration(ApiCallback<Session> cb) {
        runPlain(() -> {
            RegisterRequest pending = session.getPendingRegistration();
            if (pending == null) return ApiResult.fail("NOT_FOUND", "No hay un registro pendiente en este dispositivo.");
            if (session.getToken() == null || session.getToken().isEmpty())
                return ApiResult.fail("AUTH_REQUIRED", "Inicia sesión para terminar el registro.");
            ApiResult<Session> registered = activeClient().register(pending);
            if (registered.ok && registered.data != null) {
                registered.data.token = session.getToken();
                registered.data.refreshToken = session.getRefreshToken();
                saveSessionChecked(registered.data, true);
                session.clearPendingRegistration();
                session.clearPendingGoogle();
                registerDeviceBestEffort();
            }
            return registered;
        }, cb);
    }

    /** Actualiza contraseña con la sesión temporal que entrega el enlace de
     *  recuperación; el JWT no sale del almacén cifrado ni del header Auth. */
    public void updatePassword(String newPassword, ApiCallback<Void> cb) {
        runPlain(() -> {
            if (newPassword == null || newPassword.length() < 6)
                return ApiResult.fail("VALIDATION_ERROR", "La contraseña debe tener al menos 6 caracteres.");
            if (session.getToken() == null || session.getToken().isEmpty())
                return ApiResult.fail("AUTH_REQUIRED", "Abre de nuevo el enlace de recuperación.");
            return SupabaseAuthRest.updatePassword(session.getSupabaseProjectUrl(),
                    session.getSupabaseApiKey(), session.getToken(), newPassword);
        }, cb);
    }

    public void me(ApiCallback<User> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) {
                User cached = db.getCachedProfile(session.getUserId());
                if (cached != null) session.setProfilePercent(cached.profilePercent);
                return cached == null ? ApiResult.fail("NO_CONNECTION", "Necesitas conexión para cargar tu perfil.")
                        : ApiResult.success(cached);
            }
            ApiResult<User> r = activeClient().me();
            if (r.ok && r.data != null) {
                db.cacheProfile(r.data);
                session.setProfilePercent(r.data.profilePercent);
                if (r.data.status != null && !r.data.status.isEmpty()) session.setStatus(r.data.status);
            }
            return r;
        }, cb);
    }

    public void updateProfile(User user, ApiCallback<User> cb) {
        run(() -> {
            // Sin conexión queda en cola (el backend lo acepta en
            // syncActions): antes fallaba con NO_CONNECTION.
            ApiResult<User> r = offlineOr(() -> activeClient().updateProfile(user),
                    "updateProfile", SupabaseApiClient.profileBody(user));
            if (r.ok && r.data != null && r.data.status != null && !r.data.status.isEmpty()) {
                session.setStatus(r.data.status);
            }
            if (r.ok && r.data != null) session.setProfilePercent(r.data.profilePercent);
            return r;
        }, cb);
    }

    public void extractJob(String text, ApiCallback<JSONObject> cb) {
        run(() -> {
            if (text == null || text.trim().isEmpty())
                return ApiResult.fail("VALIDATION_ERROR", "Pega el texto de la oferta primero.");
            if (text.trim().length() > 4000)
                return ApiResult.fail("VALIDATION_ERROR", "El texto no puede superar 4000 caracteres.");
            if (!isDemoMode() && !isOnline())
                return ApiResult.fail("NO_CONNECTION", "Necesitas conexión para extraer los datos.");
            return activeClient().extractJob(text.trim());
        }, cb);
    }

    public void health(String apiUrl, ApiCallback<String> cb) {
        runPlain(() -> new SupabaseApiClient(appContext, apiUrl).health(), cb);
    }

    // ---------- Ofertas (con caché offline) ----------

    /** Ofertas guardadas en el teléfono, con el mismo filtro que la red.
     *  Síncrono y rápido: las pantallas pintan primero esto y después
     *  refrescan en segundo plano, sin esqueleto. */
    public Page<Job> getCachedJobsPage(JobFilter filter) {
        JobFilter effective = filter != null ? filter.copy() : new JobFilter();
        boolean workerView = !"company".equals(session.getRole());
        List<Job> cached = DemoApiClient.filterCachedJobs(
                db.getCachedJobs(), effective, workerView, null, null);
        if (effective.limit > 0 && cached.size() > effective.limit) {
            cached = new ArrayList<>(cached.subList(0, effective.limit));
        }
        Page<Job> page = new Page<>();
        page.items.addAll(cached);
        page.total = cached.size();
        return page;
    }

    /** Notificaciones guardadas en el teléfono (para pintar al instante). */
    public List<AppNotification> getCachedNotifications() {
        return db.getCachedNotifications();
    }

    /** Candidaturas guardadas en el teléfono (para pintar al instante). */
    public List<Application> getCachedApplications() {
        return db.getCachedApplications();
    }

    public void getJobs(JobFilter filter, String cursor, ApiCallback<Page<Job>> cb) {
        run(() -> {
            // Ahorro de datos: menos resultados por carga (máximo 15).
            JobFilter effective = filter != null ? filter.copy() : new JobFilter();
            if (session.isDataSaver() && effective.limit > 15) effective.limit = 15;
            if (!isDemoMode() && !isOnline()) {
                // Sin conexión: contenido guardado, con el MISMO filtro
                // (antes se devolvía toda la caché ignorando provincia,
                // salario y texto).
                boolean workerView = !"company".equals(session.getRole());
                List<Job> cached = DemoApiClient.filterCachedJobs(
                        db.getCachedJobs(), effective, workerView, null, null);
                Page<Job> page = new Page<>();
                page.items.addAll(cached);
                page.total = cached.size();
                return ApiResult.success(page);
            }
            ApiResult<Page<Job>> r = activeClient().getJobs(effective, cursor);
            if (r.ok && r.data != null && (cursor == null || cursor.isEmpty())) {
                db.cacheJobs(r.data.items);
            }
            return r;
        }, cb);
    }

    public void getJob(String jobId, ApiCallback<Job> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) {
                Job cached = db.getCachedJob(jobId);
                if (cached != null) return ApiResult.success(cached);
                return ApiResult.<Job>fail("NO_CONNECTION", "");
            }
            ApiResult<Job> r = activeClient().getJob(jobId);
            if (r.ok && r.data != null) {
                List<Job> one = new ArrayList<>();
                one.add(r.data);
                db.cacheJobs(one);
            }
            return r;
        }, cb);
    }

    public void getSavedJobs(ApiCallback<List<Job>> cb) {
        run(() -> {
            List<Job> out = new ArrayList<>();
            for (String id : db.getSavedJobIds()) {
                Job j = db.getCachedJob(id);
                if (j == null) {
                    ApiResult<Job> r = activeClient().getJob(id);
                    if (r.ok) j = r.data;
                }
                if (j != null) {
                    j.saved = true;
                    out.add(j);
                }
            }
            return ApiResult.success(out);
        }, cb);
    }

    // ---------- Mutaciones con cola offline ----------
    private <T> ApiResult<T> offlineOr(Supplier<ApiResult<T>> online, String actionType, JSONObject payload) {
        if (isDemoMode() || isOnline()) return online.get();
        PendingAction a = new PendingAction(UUID.randomUUID().toString(), actionType,
                payload != null ? payload.toString() : "{}", TextUtils.nowIso());
        if (!db.enqueue(a)) {
            return ApiResult.fail("LOCAL_STORAGE_ERROR",
                    "No se pudo guardar la acción sin conexión. Libera espacio e inténtalo de nuevo.");
        }
        return ApiResult.queued();
    }

    public void expressInterest(String jobId, ApiCallback<Application> cb) {
        run(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("jobId", jobId);
                return offlineOr(() -> activeClient().expressInterest(jobId, UUID.randomUUID().toString()),
                        "expressInterest", payload);
            } catch (Exception e) {
                return ApiResult.fail("INTERNAL_ERROR", "");
            }
        }, cb);
    }

    public void withdrawInterest(String applicationId, ApiCallback<Void> cb) {
        run(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("applicationId", applicationId);
                return offlineOr(() -> activeClient().withdrawInterest(applicationId),
                        "withdrawInterest", payload);
            } catch (Exception e) {
                return ApiResult.fail("INTERNAL_ERROR", "");
            }
        }, cb);
    }

    /** Recordatorio educado a la empresa; sin conexión queda en cola (no duplica el interés). */
    public void sendReminder(String applicationId, ApiCallback<Void> cb) {
        run(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("applicationId", applicationId);
                return offlineOr(() -> activeClient().sendReminder(applicationId),
                        "sendReminder", payload);
            } catch (Exception e) {
                return ApiResult.fail("INTERNAL_ERROR", "");
            }
        }, cb);
    }

    public void changeApplicationStatus(String applicationId, String status, String note, ApiCallback<Application> cb) {
        run(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("applicationId", applicationId);
                payload.put("status", status);
                if (note != null) payload.put("internalNote", note);
                return offlineOr(() -> activeClient().changeApplicationStatus(applicationId, status, note),
                        "changeApplicationStatus", payload);
            } catch (Exception e) {
                return ApiResult.fail("INTERNAL_ERROR", "");
            }
        }, cb);
    }

    public void deleteApplication(String applicationId, ApiCallback<Void> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) return ApiResult.fail("NO_CONNECTION", "");
            return activeClient().deleteApplication(applicationId);
        }, cb);
    }

    public void saveJob(String jobId, boolean save, ApiCallback<Boolean> cb) {
        run(() -> {
            boolean wasSaved = db.isSaved(jobId);
            db.setSaved(jobId, save); // optimista: se refleja al instante
            if (isDemoMode()) {
                ApiResult<Boolean> r = activeClient().saveJob(jobId, save);
                if (!r.ok) db.setSaved(jobId, wasSaved);
                else if (r.data != null) db.setSaved(jobId, r.data);
                return r;
            }
            if (!isOnline()) {
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("jobId", jobId);
                    payload.put("active", save);
                    PendingAction a = new PendingAction(UUID.randomUUID().toString(), "saveJob",
                            payload.toString(), TextUtils.nowIso());
                    if (!db.enqueue(a)) {
                        db.setSaved(jobId, wasSaved);
                        return ApiResult.fail("LOCAL_STORAGE_ERROR",
                                "No se pudo guardar la acción sin conexión. Libera espacio e inténtalo de nuevo.");
                    }
                } catch (Exception e) {
                    db.setSaved(jobId, wasSaved);
                    return ApiResult.fail("LOCAL_STORAGE_ERROR", "No se pudo guardar el cambio localmente.");
                }
                return ApiResult.<Boolean>queued();
            }
            ApiResult<Boolean> r = activeClient().saveJob(jobId, save);
            if (!r.ok) db.setSaved(jobId, wasSaved);
            else if (r.data != null) db.setSaved(jobId, r.data);
            return r;
        }, cb);
    }

    public void saveCandidate(String workerId, String applicationId, boolean save, ApiCallback<Boolean> cb) {
        run(() -> {
            boolean wasSaved = false;
            try {
                RecommendedWorker cached = null;
                for (RecommendedWorker existing : db.getSavedCandidates()) {
                    if (workerId != null && workerId.equals(existing.workerId)) {
                        cached = existing; wasSaved = true; break;
                    }
                }
                if (cached == null) {
                    for (RecommendedWorker existing : db.getCachedRecommendedWorkers()) {
                        if (workerId != null && workerId.equals(existing.workerId)) { cached = existing; break; }
                    }
                }
                if (cached == null) { cached = new RecommendedWorker(); cached.workerId = workerId; }
                db.setSavedCandidate(cached, save);
                JSONObject payload = new JSONObject();
                payload.put("workerId", workerId);
                if (applicationId != null && !applicationId.isEmpty()) payload.put("applicationId", applicationId);
                payload.put("active", save);
                ApiResult<Boolean> result = offlineOr(() -> activeClient().saveCandidate(workerId, applicationId, save),
                        "saveCandidate", payload);
                if (!result.ok && !result.queued) db.setSavedCandidate(cached, wasSaved);
                else if (result.ok && result.data != null) db.setSavedCandidate(cached, result.data);
                return result;
            } catch (Exception e) {
                // Nunca dejar el perfil marcado/desmarcado si la construcción de
                // la petición o el almacenamiento local falla.
                try {
                    RecommendedWorker rollback = new RecommendedWorker();
                    rollback.workerId = workerId;
                    db.setSavedCandidate(rollback, wasSaved);
                } catch (Exception ignored) { }
                return ApiResult.fail("INTERNAL_ERROR", "No se pudo guardar el perfil. Inténtalo de nuevo.");
            }
        }, cb);
    }

    public void getSavedCandidates(int limit, String cursor, ApiCallback<List<RecommendedWorker>> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) return ApiResult.success(db.getSavedCandidates());
            ApiResult<List<RecommendedWorker>> r = activeClient().getSavedCandidates(limit, cursor);
            if (r.ok && r.data != null) for (RecommendedWorker w : r.data) db.setSavedCandidate(w, true);
            return r;
        }, cb);
    }

    public void clearDownloadedCache() {
        db.clearDownloadedCache();
        com.cubapincha.job.util.NetworkImageLoader.clearCache(appContext);
    }
    public void uploadPhoto(String kind, String jobId, String imageBase64, String thumbBase64,
                            ApiCallback<JSONObject> cb) {
        run(() -> activeClient().uploadPhoto(kind, jobId, imageBase64, thumbBase64), cb);
    }

    public void getRecommendedWorkers(int minMatch, int limit, ApiCallback<List<RecommendedWorker>> cb) { getRecommendedWorkers(null,null,null,null,minMatch,limit,cb); }

    public void getRecommendedWorkers(String q, String provinceCode, String municipalityCode, String category, int minMatch, int limit,
                                      ApiCallback<List<RecommendedWorker>> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) {
                List<RecommendedWorker> cached = db.getCachedRecommendedWorkers();
                return cached.isEmpty()
                        ? ApiResult.fail("NO_CONNECTION", "Necesitas conexión para cargar trabajadores recomendados.")
                        : ApiResult.success(cached);
            }
            ApiResult<List<RecommendedWorker>> r = activeClient().getRecommendedWorkers(q, provinceCode, municipalityCode, category, minMatch, limit);
            if (r.ok && r.data != null) db.cacheRecommendedWorkers(r.data);
            return r;
        }, cb);
    }

    public void uninviteWorker(String workerId, String offerId, ApiCallback<Boolean> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) return ApiResult.fail("NO_CONNECTION", "Necesitas conexión para retirar la invitación.");
            return activeClient().uninviteWorker(workerId, offerId);
        }, cb);
    }
    public void getPublicWorker(String workerId, ApiCallback<RecommendedWorker> cb) {
        run(() -> activeClient().getPublicWorker(workerId), cb);
    }
    public void inviteWorker(String workerId, String offerId, String message,
                             ApiCallback<Boolean> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) {
                return ApiResult.fail("NO_CONNECTION", "Necesitas conexión para enviar la invitación.");
            }
            return activeClient().inviteWorker(workerId, offerId, message);
        }, cb);
    }

    // ---------- Candidaturas / notificaciones ----------
    public void getApplications(String status, ApiCallback<List<Application>> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) {
                List<Application> cached = db.getCachedApplications();
                if ("closed".equals(status)) {
                    cached.removeIf(a -> !("rejected".equals(a.status) || "withdrawn".equals(a.status)));
                } else if (status != null && !status.isEmpty()) {
                    cached.removeIf(a -> !status.equals(a.status));
                }
                return ApiResult.success(cached);
            }
            ApiResult<List<Application>> r = activeClient().getApplications(status);
            if (r.ok && r.data != null) db.cacheApplications(r.data);
            return r;
        }, cb);
    }

    /** Lectura pública paginada; se solicita a red y no bloquea el hilo UI. */
    public void getRatings(String userId, int limit, String cursor, ApiCallback<RatingsPage> cb) {
        run(() -> {
            if (!isOnline()) return ApiResult.fail("NO_CONNECTION", "Necesitas conexión para cargar valoraciones.");
            return activeClient().getRatings(userId, limit, cursor);
        }, cb);
    }

    /** Escritura autenticada, nunca pasa por la cola offline ni por syncActions. */
    public void rateApplication(String applicationId, int stars, String comment,
                                ApiCallback<RatingSubmission> cb) {
        run(() -> {
            if (applicationId == null || applicationId.trim().isEmpty() || stars < 1 || stars > 5) {
                return ApiResult.fail("VALIDATION_ERROR", "Revisa los datos: estrellas de 1 a 5.");
            }
            String normalizedComment = comment == null ? "" : comment.trim();
            if (normalizedComment.length() > 1000) {
                return ApiResult.fail("VALIDATION_ERROR", "El comentario no puede superar 1000 caracteres.");
            }
            if (!session.isLoggedIn()) return ApiResult.fail("AUTH_REQUIRED", "Inicia sesión para valorar este trabajo.");
            if (!isOnline()) return ApiResult.fail("NO_CONNECTION", "Necesitas conexión para enviar la valoración.");
            return activeClient().rateApplication(applicationId.trim(), stars, normalizedComment);
        }, cb);
    }

    public void getNotifications(boolean unreadOnly, ApiCallback<NotificationPage> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) {
                List<AppNotification> cached = db.getCachedNotifications();
                NotificationPage page = new NotificationPage();
                int unread = 0;
                for (AppNotification n : cached) {
                    if (!n.read) unread++;
                    if (!unreadOnly || !n.read) page.items.add(n);
                }
                page.unreadCount = unread;
                page.total = page.items.size();
                return ApiResult.success(page);
            }
            ApiResult<NotificationPage> r = activeClient().getNotifications(unreadOnly);
            if (r.ok && r.data != null) db.cacheNotifications(r.data.items);
            return r;
        }, cb);
    }

    public void markNotificationRead(String id, ApiCallback<Void> cb) {
        run(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("notificationId", id);
                return offlineOr(() -> activeClient().markNotificationRead(id),
                        "markNotificationRead", payload);
            } catch (Exception e) {
                return ApiResult.fail("INTERNAL_ERROR", "");
            }
        }, cb);
    }

    public void markAllNotificationsRead(ApiCallback<Void> cb) {
        run(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("all", true);
                return offlineOr(() -> activeClient().markAllNotificationsRead(),
                        "markNotificationRead", payload);
            } catch (Exception e) {
                return ApiResult.fail("INTERNAL_ERROR", "");
            }
        }, cb);
    }

    public void deleteNotifications(String notificationId, boolean all, ApiCallback<Void> cb) {
        run(() -> {
            if (all) db.clearCachedNotifications();
            else db.deleteCachedNotification(notificationId);
            try {
                JSONObject payload = new JSONObject();
                if (all) payload.put("all", true); else payload.put("notificationId", notificationId);
                return offlineOr(() -> activeClient().deleteNotifications(notificationId, all), "deleteNotifications", payload);
            } catch (Exception e) {
                return ApiResult.fail("INTERNAL_ERROR", "");
            }
        }, cb);
    }

    // ---------- Empresa ----------
    // Las mutaciones de empresa NO se encolan offline: crear o publicar una
    // oferta exige validación del servidor (campos, estado de cuenta, matching).
    // Sin conexión se indica claramente en vez de prometer el envío.
    public void createJob(Job draft, ApiCallback<Job> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) return ApiResult.fail("NO_CONNECTION", "");
            return activeClient().createJob(draft);
        }, cb);
    }

    public void updateJob(Job draft, ApiCallback<Job> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) return ApiResult.fail("NO_CONNECTION", "");
            return activeClient().updateJob(draft);
        }, cb);
    }

    public void publishJob(String jobId, ApiCallback<Job> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) return ApiResult.fail("NO_CONNECTION", "");
            return activeClient().publishJob(jobId);
        }, cb);
    }

    public void changeJobStatus(String jobId, String status, ApiCallback<Job> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) return ApiResult.fail("NO_CONNECTION", "");
            return activeClient().changeJobStatus(jobId, status);
        }, cb);
    }

    public void archiveJob(String jobId, String reason, ApiCallback<Job> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) return ApiResult.fail("NO_CONNECTION", "");
            return activeClient().archiveJob(jobId, reason);
        }, cb);
    }

    public void deleteJob(String jobId, ApiCallback<Void> cb) {
        run(() -> {
            if (!isDemoMode() && !isOnline()) return ApiResult.fail("NO_CONNECTION", "");
            return activeClient().deleteJob(jobId);
        }, cb);
    }

    // ---------- Borradores offline (v2.0) ----------
    /** Guarda un borrador completo en el teléfono (funciona sin conexión). */
    public void saveDraft(Job draft, ApiCallback<Job> cb) {
        runPlain(() -> {
            // Se trabaja sobre una copia: antes se mutaba el Job del
            // llamador (status, jobId, fechas) como efecto secundario.
            Job copy = Job.fromJson(draft.toJson());
            if (copy.jobId == null || copy.jobId.isEmpty()) {
                copy.jobId = "draft-" + UUID.randomUUID().toString();
            }
            copy.status = "draft";
            if (copy.createdAt == null || copy.createdAt.isEmpty()) {
                copy.createdAt = TextUtils.nowIso();
            }
            copy.updatedAt = TextUtils.nowIso();
            db.saveDraft(copy);
            return ApiResult.success(copy);
        }, cb);
    }

    public void getDrafts(ApiCallback<List<Job>> cb) {
        runPlain(() -> ApiResult.success(db.getDrafts()), cb);
    }

    public void deleteDraft(String draftId, ApiCallback<Void> cb) {
        runPlain(() -> {
            db.deleteDraft(draftId);
            return ApiResult.success(null);
        }, cb);
    }

    /** Publica un borrador guardado: lo crea en el servidor y lo borra del teléfono.
     *  Idempotente: si la creación ya salió pero falló la publicación, el
     *  reintento reutiliza la oferta creada en vez de crear un duplicado. */
    public void publishDraft(String draftId, ApiCallback<Job> cb) {
        run(() -> {
            Job draft = db.getDraft(draftId);
            if (draft == null) return ApiResult.fail("NOT_FOUND", "No encontramos este borrador.");
            if (!isDemoMode() && !isOnline()) return ApiResult.fail("NO_CONNECTION", "");
            String createdId = db.getMeta("publish:" + draftId);
            if (createdId == null || createdId.isEmpty()) {
                Job toCreate = Job.fromJson(draft.toJson());
                toCreate.jobId = null; // el servidor asigna el id real
                ApiResult<Job> created = activeClient().createJob(toCreate);
                if (!created.ok || created.data == null) return created;
                createdId = created.data.jobId;
                db.setMeta("publish:" + draftId, createdId);
            }
            ApiResult<Job> published = activeClient().publishJob(createdId);
            if (!published.ok) return published;
            db.deleteDraft(draftId);
            db.setMeta("publish:" + draftId, "");
            return published;
        }, cb);
    }

    public int draftCount() {
        return db.getDraftCount();
    }

    /** Actualiza el catálogo de municipios desde el backend (best-effort). */
    public void refreshCatalogs() {
        executor.execute(() -> {
            try {
                if (isDemoMode() || !isOnline()) return;
                ApiResult<org.json.JSONObject> r = activeClient().listCatalogs();
                if (r.ok && r.data != null && r.data.length() > 0) {
                    CubaCatalog.saveOverrides(appContext, r.data);
                }
            } catch (Exception ignored) { }
        });
    }

    // ---------- Búsquedas / contacto / varios ----------
    public void saveFilter(SavedSearch search, ApiCallback<SavedSearch> cb) {
        run(() -> offlineOr(() -> activeClient().saveFilter(search),
                "saveFilter", SupabaseApiClient.filterBody(search)), cb);
    }

    public void getSavedSearches(ApiCallback<List<SavedSearch>> cb) {
        run(() -> activeClient().getSavedSearches(), cb);
    }

    public void deleteSavedSearch(String filterId, ApiCallback<Void> cb) {
        run(() -> activeClient().deleteSavedSearch(filterId), cb);
    }

    public void getContactOptions(String entityType, String entityId, ApiCallback<ContactOptions> cb) {
        run(() -> activeClient().getContactOptions(entityType, entityId), cb);
    }

    public void contactEvent(String entityType, String entityId, String channel) {
        executor.execute(() -> {
            try {
                if (!isDemoMode() && !isOnline()) {
                    enqueueContactEvent(entityType, entityId, channel);
                    return;
                }
                ApiResult<Void> r = activeClient().contactEvent(entityType, entityId, channel);
                if (!r.ok) {
                    // Falló también online: encolar para no perder el evento
                    // (antes un fallo online se tragaba en silencio).
                    enqueueContactEvent(entityType, entityId, channel);
                }
            } catch (Exception ignored) { }
        });
    }

    private void enqueueContactEvent(String entityType, String entityId, String channel) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("entityType", entityType);
        payload.put("entityId", entityId);
        payload.put("channel", channel);
        db.enqueue(new PendingAction(UUID.randomUUID().toString(), "contactEvent",
                payload.toString(), TextUtils.nowIso()));
    }

    public void report(String entityType, String entityId, String reason, ApiCallback<Void> cb) {
        run(() -> activeClient().report(entityType, entityId, reason), cb);
    }

    // ---------- Sincronización ----------
    /**
     * Envía la cola offline en orden y trae cambios del servidor.
     * Cada acción se marca según SU resultado (applied, already_applied,
     * conflict, rejected): nunca todas por el contador global.
     * El pull respeta hasMore (hasta 5 páginas) y persiste ofertas,
     * candidaturas y notificaciones.
     * Devuelve "Sincronizado: X · Pendiente: Y · Revisar: Z".
     */
    public void syncNow(ApiCallback<String> cb) {
        run(this::syncNowSync, cb);
    }

    /** Sincronización síncrona (en el hilo de quien llama): la usa el
     *  trabajo en segundo plano (SyncWorker). No usar desde el hilo
     *  principal: hace red y base de datos. */
    public ApiResult<String> syncNowSync() {
        if (isDemoMode()) return ApiResult.success("Sincronizado: 0 · Pendiente: 0 · Revisar: 0");
        if (!isOnline()) return ApiResult.fail("NO_CONNECTION", "");
        if (syncRunning) return ApiResult.success("Sincronización en curso…");
        syncRunning = true;
        try {
            ApiResult<String> result = doSyncNow();
            if (!result.ok && "AUTH_REQUIRED".equals(result.errorCode) && tryRefreshSession()) {
                result = doSyncNow();
            }
            if (!result.ok && "AUTH_REQUIRED".equals(result.errorCode)) {
                session.logout();
                session.setAuthExpired(true);
                resetRemoteClient();
            }
            return result;
        } finally {
            syncRunning = false;
        }
    }

    private ApiResult<String> doSyncNow() {
        List<PendingAction> pending = db.getPendingActions();
        int applied = 0;
        final int batchSize = 50; // backend admite hasta 100; margen para el límite de 256 KB.
        for (int start = 0; start < pending.size(); start += batchSize) {
            List<PendingAction> batch = new ArrayList<>(pending.subList(
                    start, Math.min(start + batchSize, pending.size())));
            ApiResult<List<SyncActionResult>> r = activeClient().syncActions(batch);
            if (!r.ok || r.data == null) {
                // Deja pendiente este y los siguientes lotes para reintentar.
                for (PendingAction a : batch) db.markActionDone(a.clientActionId, "pending", r.errorCode);
                if ("AUTH_REQUIRED".equals(r.errorCode)) {
                    return ApiResult.fail(r.errorCode, r.errorMessage);
                }
                break;
            }
            Map<String, SyncActionResult> byId = new HashMap<>();
            for (SyncActionResult res : r.data) byId.put(res.clientActionId, res);
            for (PendingAction a : batch) {
                SyncActionResult res = byId.get(a.clientActionId);
                if (res != null && res.isApplied()) {
                    db.markActionDone(a.clientActionId, "sent", null);
                    applied++;
                } else if (res != null && "conflict".equals(res.result)) {
                    db.markActionDone(a.clientActionId, "conflict", res.errorCode);
                } else if (res != null && "rejected".equals(res.result)) {
                    // Rechazada: no se reintenta eternamente; queda marcada para revisar.
                    db.markActionDone(a.clientActionId, "rejected", res.errorCode);
                } else {
                    // Respuesta incompleta: conserva pendiente para el siguiente intento.
                    db.markActionDone(a.clientActionId, "pending",
                            res != null ? res.errorCode : "UNKNOWN");
                }
            }
        }
        // Incluye conflictos/rechazos de sincronizaciones anteriores, no solo el lote actual.
        int review = db.getReviewCount();
        String since = session.getLastSync();
        int offset = 0;
        for (int page = 0; page < 5; page++) {
            ApiResult<SyncResult> sr = activeClient().sync(since, offset);
            if (!sr.ok || sr.data == null) {
                if ("AUTH_REQUIRED".equals(sr.errorCode)) {
                    return ApiResult.fail(sr.errorCode, sr.errorMessage);
                }
                break;
            }
            if (!sr.data.jobs.isEmpty()) db.cacheJobs(sr.data.jobs);
            if (!sr.data.applications.isEmpty()) db.cacheApplications(sr.data.applications);
            if (!sr.data.notifications.isEmpty()) db.cacheNotifications(sr.data.notifications);
            if (!sr.data.hasMore) {
                // El cursor solo avanza cuando NO quedan más páginas: antes
                // se avanzaba a "ahora" tras cada página y los cambios por
                // encima de 100 se perdían para siempre.
                if (sr.data.serverTime != null && !sr.data.serverTime.isEmpty()) {
                    session.setLastSync(sr.data.serverTime);
                }
                break;
            }
            offset += 100;
        }
        int left = db.getPendingCount();
        // Catálogo de municipios: correcciones del backend (sin bloquear).
        refreshCatalogs();
        return ApiResult.success("Sincronizado: " + applied + " · Pendiente: " + left + " · Revisar: " + review);
    }

    public int pendingCount() {
        return db.getPendingCount();
    }
}
