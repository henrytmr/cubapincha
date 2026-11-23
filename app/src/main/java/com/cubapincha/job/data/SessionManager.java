package com.cubapincha.job.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cubapincha.job.BuildConfig;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/** Sesión y preferencias persistentes. Todos los valores de conexión y
 *  sesión (URL de la API, clave de Firebase, IDs de FCM y de Google,
 *  tokens) viven cifrados en un almacén aparte
 *  (EncryptedSharedPreferences, clave en el Keystore); solo los ajustes
 *  de interfaz quedan en preferencias normales. Si un valor de conexión
 *  no está guardado, se usa el integrado cifrado en la app
 *  BuildConfig (URL, anon key, Google Client ID fijos). */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREFS = "cubapincha_session";
    private static final String SECURE_PREFS = "cubapincha_secure";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_REFRESH_TOKEN = "supabase_refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ROLE = "role";
    private static final String KEY_STATUS = "status";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_PROFILE_PERCENT = "profile_percent";
    private static final String KEY_DEMO_MODE = "demo_mode";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_SUPABASE_API_KEY = "supabase_publishable_key";
    private static final String KEY_PENDING_REGISTER = "pending_register_profile";
    private static final String KEY_PKCE_VERIFIER = "supabase_pkce_verifier";
    private static final String KEY_REMEMBER = "remember";
    private static final String KEY_PENDING_ROLE = "pending_role";
    private static final String KEY_INTEREST_CONFIRMED = "interest_confirmed";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_DATA_SAVER = "data_saver";
    private static final String KEY_INTRO_SOUND = "intro_sound";
    private static final String KEY_LAST_SYNC = "last_sync";
    private static final String KEY_FIREBASE_API_KEY = "firebase_api_key";
    private static final String KEY_FIREBASE_ID_TOKEN = "firebase_id_token";
    private static final String KEY_FIREBASE_REFRESH_TOKEN = "firebase_refresh_token";
    private static final String KEY_AUTH_EXPIRED = "auth_expired";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_PHOTO_URI = "photo_uri";
    // Preferencias FCM antiguas: se conservan para migraciones/lectura legacy.
    // El push actual obtiene la configuración de los recursos Google Services.
    private static final String KEY_FCM_PROJECT_ID = "fcm_project_id";
    private static final String KEY_FCM_APP_ID = "fcm_app_id";
    private static final String KEY_FCM_SENDER_ID = "fcm_sender_id";
    private static final String KEY_FCM_TOKEN = "fcm_token";
    // Google Sign-In: ID de cliente web de OAuth (consola de Firebase).
    private static final String KEY_GOOGLE_CLIENT_ID = "google_client_id";
    // Cuenta de Google pendiente de registro (login con Google de un usuario
    // que aún no tiene cuenta en CubaPincha).
    private static final String KEY_PENDING_GOOGLE_EMAIL = "pending_google_email";
    private static final String KEY_PENDING_GOOGLE_NAME = "pending_google_name";
    private static final String KEY_PENDING_GOOGLE_ID_TOKEN = "pending_google_id_token";

    private static SessionManager instance;
    private final SharedPreferences prefs;
    /** Almacén cifrado para los valores sensibles (ver createSecurePrefs). */
    private final SharedPreferences secure;

    private SessionManager(Context ctx) {
        Context app = ctx.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        secure = createSecurePrefs(app);
        migrateSensitive();
        // Los tokens Firebase y las sesiones opacas 2.9.x ya no son válidos
        // para Supabase. No conservarlos ni reutilizarlos como JWT.
        if (!prefs.getBoolean("supabase_auth_migrated", false)) {
            SharedPreferences.Editor e = secure.edit()
                    .remove(KEY_FIREBASE_ID_TOKEN).remove(KEY_FIREBASE_REFRESH_TOKEN);
            if (!prefs.getBoolean(KEY_DEMO_MODE, false)) e.remove(KEY_TOKEN);
            e.apply();
            prefs.edit().putBoolean("supabase_auth_migrated", true).apply();
        }
    }

    /** Almacén cifrado con clave en Android Keystore. Si falla, se cierra
     *  de forma segura: nunca se degrada a guardar tokens en texto plano. */
    private static SharedPreferences createSecurePrefs(Context ctx) {
        try {
            MasterKey masterKey = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(ctx, SECURE_PREFS, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo crear el almacén cifrado; no se guardará la sesión en claro", e);
            throw new IllegalStateException("El almacenamiento seguro no está disponible en este dispositivo.", e);
        }
    }

    /** Migra los valores sensibles que versiones anteriores guardaron en
     *  claro en cubapincha_session al almacén cifrado, y los borra de allí. */
    private void migrateSensitive() {
        String[] keys = {KEY_TOKEN, KEY_REFRESH_TOKEN, KEY_FIREBASE_API_KEY, KEY_FIREBASE_ID_TOKEN,
                KEY_FIREBASE_REFRESH_TOKEN, KEY_FCM_TOKEN, KEY_PENDING_GOOGLE_ID_TOKEN,
                KEY_API_URL, KEY_SUPABASE_API_KEY, KEY_PENDING_REGISTER, KEY_PKCE_VERIFIER,
                KEY_FCM_PROJECT_ID, KEY_FCM_APP_ID, KEY_FCM_SENDER_ID,
                KEY_GOOGLE_CLIENT_ID};
        SharedPreferences.Editor plain = null;
        SharedPreferences.Editor sec = null;
        for (String key : keys) {
            String value = prefs.getString(key, null);
            if (value == null) continue;
            if (plain == null) { plain = prefs.edit(); sec = secure.edit(); }
            sec.putString(key, value);
            plain.remove(key);
        }
        if (sec != null) {
            // Primero persistir la copia cifrada; solo después borrar la versión en claro.
            if (!sec.commit()) {
                throw new IllegalStateException("No se pudieron migrar los datos de sesión al almacén seguro.");
            }
            if (!plain.commit()) {
                Log.e(TAG, "No se pudo borrar una copia histórica de preferencias en claro");
            }
        }
    }

    public static synchronized SessionManager get(Context ctx) {
        if (instance == null) instance = new SessionManager(ctx);
        return instance;
    }

    public void saveSession(Session s, boolean remember) {
        SharedPreferences.Editor secureEdit = secure.edit();
        if (s.token == null || s.token.isEmpty()) secureEdit.remove(KEY_TOKEN);
        else secureEdit.putString(KEY_TOKEN, s.token);
        if (s.refreshToken == null || s.refreshToken.isEmpty()) secureEdit.remove(KEY_REFRESH_TOKEN);
        else secureEdit.putString(KEY_REFRESH_TOKEN, s.refreshToken);
        secureEdit.apply();
        prefs.edit()
                .putString(KEY_EXPIRES_AT, s.expiresAt)
                .putString(KEY_USER_ID, s.userId)
                .putString(KEY_ROLE, s.role)
                .putString(KEY_STATUS, s.status)
                .putString(KEY_DISPLAY_NAME, s.displayName)
                .putBoolean(KEY_REMEMBER, remember)
                .apply();
    }

    /** Caducidad ISO de la sesión del servidor ("" si no se conoce). */
    public String getExpiresAt() { return prefs.getString(KEY_EXPIRES_AT, ""); }

    public boolean isLoggedIn() {
        String token = secure.getString(KEY_TOKEN, null);
        return token != null && !token.isEmpty() && !getUserId().isEmpty() && !getRole().isEmpty();
    }

    /** "Recordarme en este dispositivo" de la sesión actual. */
    public boolean isRemember() { return prefs.getBoolean(KEY_REMEMBER, true); }

    public String getToken() { return secure.getString(KEY_TOKEN, null); }
    public String getRefreshToken() { return secure.getString(KEY_REFRESH_TOKEN, null); }
    public void saveAuthTokens(String accessToken, String refreshToken, long expiresInSec) {
        SharedPreferences.Editor e = secure.edit();
        if (accessToken == null || accessToken.isEmpty()) e.remove(KEY_TOKEN); else e.putString(KEY_TOKEN, accessToken);
        if (refreshToken == null || refreshToken.isEmpty()) e.remove(KEY_REFRESH_TOKEN); else e.putString(KEY_REFRESH_TOKEN, refreshToken);
        e.apply();
        if (expiresInSec > 0) prefs.edit().putString(KEY_EXPIRES_AT,
                java.time.Instant.now().plusSeconds(expiresInSec).toString()).apply();
    }
    public String getUserId() { return prefs.getString(KEY_USER_ID, ""); }
    public String getRole() { return prefs.getString(KEY_ROLE, ""); }
    public String getStatus() { return prefs.getString(KEY_STATUS, ""); }
    public String getDisplayName() { return prefs.getString(KEY_DISPLAY_NAME, ""); }
    public int getProfilePercent() { return prefs.getInt(KEY_PROFILE_PERCENT, 0); }
    public void setProfilePercent(int p) { prefs.edit().putInt(KEY_PROFILE_PERCENT, p).apply(); }
    public void setStatus(String s) { prefs.edit().putString(KEY_STATUS, s).apply(); }
    public void setDisplayName(String n) { prefs.edit().putString(KEY_DISPLAY_NAME, n).apply(); }

    /** En 2.11.0 DEV el modo demo está desactivado: la clave va en BuildConfig. */
    public boolean isDemoMode() {
        return false;
    }
    public void setDemoMode(boolean demo) { /* no-op: demo desactivado */ }

    /** URL de la Edge Function fija desde BuildConfig. */
    public String getApiUrl() {
        return BuildConfig.EDGE_FUNCTION_URL;
    }
    public void setApiUrl(String url) { /* no-op: fija en BuildConfig */ }

    public String getSupabaseProjectUrl() { return BuildConfig.SUPABASE_URL; }
    public String getSupabaseApiKey() {
        return BuildConfig.SUPABASE_ANON_KEY != null ? BuildConfig.SUPABASE_ANON_KEY : "";
    }
    public void setSupabaseApiKey(String key) { /* no-op: fija en BuildConfig */ }

    public void savePendingRegistration(RegisterRequest request) {
        try {
            secure.edit().putString(KEY_PENDING_REGISTER, request.toProfileJson().toString()).apply();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo guardar el registro pendiente.", e);
        }
    }
    public RegisterRequest getPendingRegistration() {
        String json = secure.getString(KEY_PENDING_REGISTER, "");
        if (json == null || json.isEmpty()) return null;
        try { return RegisterRequest.fromProfileJson(new org.json.JSONObject(json)); }
        catch (Exception e) { return null; }
    }
    public void clearPendingRegistration() { secure.edit().remove(KEY_PENDING_REGISTER).apply(); }
    public void savePkceVerifier(String verifier) { secure.edit().putString(KEY_PKCE_VERIFIER, verifier == null ? "" : verifier).apply(); }
    public String getPkceVerifier() { return secure.getString(KEY_PKCE_VERIFIER, ""); }
    public void clearPkceVerifier() { secure.edit().remove(KEY_PKCE_VERIFIER).apply(); }

    public String getPendingRole() { return prefs.getString(KEY_PENDING_ROLE, "worker"); }
    public void setPendingRole(String role) { prefs.edit().putString(KEY_PENDING_ROLE, role).apply(); }

    public boolean isInterestConfirmed() { return prefs.getBoolean(KEY_INTEREST_CONFIRMED, false); }
    public void setInterestConfirmed() { prefs.edit().putBoolean(KEY_INTEREST_CONFIRMED, true).apply(); }

    public boolean isNotificationsEnabled() { return prefs.getBoolean(KEY_NOTIFICATIONS, true); }
    public void setNotificationsEnabled(boolean v) { prefs.edit().putBoolean(KEY_NOTIFICATIONS, v).apply(); }

    /** Tema: system | light | dark. Por defecto sigue al sistema. */
    public String getThemeMode() {
        String mode = prefs.getString(KEY_THEME_MODE, null);
        if (mode != null) return mode;
        // Migración del interruptor antiguo (solo claro/oscuro).
        return prefs.getBoolean(KEY_DARK_MODE, false) ? "dark" : "system";
    }
    public void setThemeMode(String mode) {
        prefs.edit().putString(KEY_THEME_MODE, mode == null ? "system" : mode).apply();
    }

    public boolean isDarkMode() { return "dark".equals(getThemeMode()); }
    public void setDarkMode(boolean v) { setThemeMode(v ? "dark" : "light"); }

    /** Ahorro de datos: menos resultados por carga, sincronización solo manual. */
    public boolean isDataSaver() { return prefs.getBoolean(KEY_DATA_SAVER, false); }
    public void setDataSaver(boolean v) { prefs.edit().putBoolean(KEY_DATA_SAVER, v).apply(); }

    /** Sonido de introducción del splash (activado por defecto). */
    public boolean isIntroSoundEnabled() { return prefs.getBoolean(KEY_INTRO_SOUND, true); }
    public void setIntroSoundEnabled(boolean v) { prefs.edit().putBoolean(KEY_INTRO_SOUND, v).apply(); }

    public String getLastSync() { return prefs.getString(KEY_LAST_SYNC, ""); }
    public void setLastSync(String iso) { prefs.edit().putString(KEY_LAST_SYNC, iso).apply(); }

    /** Clave Firebase guardada por versiones antiguas; ya no configura FCM. */
    public String getFirebaseApiKey() {
        // Ya no se usa para Auth; se mantiene por compatibilidad de push legacy.
        String v = secure.getString(KEY_FIREBASE_API_KEY, "");
        return v == null ? "" : v;
    }
    public void setFirebaseApiKey(String key) { secure.edit().putString(KEY_FIREBASE_API_KEY, key == null ? "" : key.trim()).apply(); }

    /** Compatibilidad de lectura histórica; el cliente ya no usa Firebase Auth. */
    /** La sesión del servidor caducó y no se pudo renovar sola. */
    public boolean isAuthExpired() { return prefs.getBoolean(KEY_AUTH_EXPIRED, false); }
    public void setAuthExpired(boolean v) { prefs.edit().putBoolean(KEY_AUTH_EXPIRED, v).apply(); }

    /** Identificador estable de esta instalación (para registerDevice). */
    public String getDeviceId() {
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null || id.isEmpty()) {
            id = "android-" + java.util.UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    /** IDs FCM legacy; las instalaciones actuales usan los recursos integrados. */
    public String getFcmProjectId() {
        String v = secure.getString(KEY_FCM_PROJECT_ID, "");
        if (v == null || v.isEmpty()) v = "cubapincha";
        return v;
    }
    public void setFcmProjectId(String v) { secure.edit().putString(KEY_FCM_PROJECT_ID, v == null ? "" : v.trim()).apply(); }
    public String getFcmAppId() {
        String v = secure.getString(KEY_FCM_APP_ID, "");
        if (v == null || v.isEmpty()) v = "1:654632132778:android:68db9aa57c515518aa30d3";
        return v;
    }
    public void setFcmAppId(String v) { secure.edit().putString(KEY_FCM_APP_ID, v == null ? "" : v.trim()).apply(); }
    public String getFcmSenderId() {
        String v = secure.getString(KEY_FCM_SENDER_ID, "");
        if (v == null || v.isEmpty()) v = "654632132778";
        return v;
    }
    public void setFcmSenderId(String v) { secure.edit().putString(KEY_FCM_SENDER_ID, v == null ? "" : v.trim()).apply(); }
    /** Token FCM actual de este dispositivo (null si aún no se obtuvo). */
    public String getFcmToken() { return secure.getString(KEY_FCM_TOKEN, null); }
    public void setFcmToken(String v) { secure.edit().putString(KEY_FCM_TOKEN, v).apply(); }
    /** ID de cliente web de Google (OAuth) fijo desde BuildConfig. */
    public String getGoogleClientId() {
        return BuildConfig.GOOGLE_WEB_CLIENT_ID != null ? BuildConfig.GOOGLE_WEB_CLIENT_ID : "";
    }
    public void setGoogleClientId(String v) { /* no-op: fija en BuildConfig */ }

    /** Cuenta de Google pendiente de registro (tras un login NOT_FOUND). */
    public void savePendingGoogle(String email, String name, String idToken) {
        secure.edit().putString(KEY_PENDING_GOOGLE_ID_TOKEN, idToken == null ? "" : idToken).apply();
        prefs.edit()
                .putString(KEY_PENDING_GOOGLE_EMAIL, email == null ? "" : email)
                .putString(KEY_PENDING_GOOGLE_NAME, name == null ? "" : name)
                .apply();
    }
    public String getPendingGoogleEmail() { return prefs.getString(KEY_PENDING_GOOGLE_EMAIL, ""); }
    public String getPendingGoogleName() { return prefs.getString(KEY_PENDING_GOOGLE_NAME, ""); }
    public String getPendingGoogleIdToken() { return secure.getString(KEY_PENDING_GOOGLE_ID_TOKEN, ""); }
    public boolean hasPendingGoogle() { return !getPendingGoogleEmail().isEmpty(); }
    public void clearPendingGoogle() {
        secure.edit().remove(KEY_PENDING_GOOGLE_ID_TOKEN).apply();
        prefs.edit().remove(KEY_PENDING_GOOGLE_EMAIL).remove(KEY_PENDING_GOOGLE_NAME).apply();
    }

    /** Foto de perfil / logotipo elegida (URI local; queda en el teléfono). */
    public String getPhotoUri() { return prefs.getString(KEY_PHOTO_URI, ""); }
    public void setPhotoUri(String uri) { prefs.edit().putString(KEY_PHOTO_URI, uri == null ? "" : uri).apply(); }

    public void logout() {
        secure.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_FIREBASE_ID_TOKEN).remove(KEY_FIREBASE_REFRESH_TOKEN)
                // El token FCM pertenece a la instalación, no a la cuenta:
                // se conserva para volver a registrarlo al siguiente login.
                .remove(KEY_PENDING_GOOGLE_ID_TOKEN)
                .apply();
        prefs.edit()
                .remove(KEY_USER_ID).remove(KEY_ROLE)
                .remove(KEY_STATUS).remove(KEY_DISPLAY_NAME)
                .remove(KEY_PROFILE_PERCENT).remove(KEY_REMEMBER)
                .remove(KEY_AUTH_EXPIRED).remove(KEY_PHOTO_URI)
                .remove(KEY_PENDING_GOOGLE_EMAIL).remove(KEY_PENDING_GOOGLE_NAME)
                // El cursor de sync y el aviso de interés pertenecen a la
                // cuenta que se va: la siguiente cuenta no debe heredarlos.
                .remove(KEY_LAST_SYNC).remove(KEY_INTEREST_CONFIRMED)
                .remove(KEY_EXPIRES_AT)
                .apply();
    }

    /** Estado mínimo para el informe de crash (sin valores sensibles). */
    public String crashStateSummary() {
        return "Sesion: " + (isLoggedIn() ? "si" : "no")
                + " · Rol: " + getRole()
                + " · Demo: " + isDemoMode();
    }
}
