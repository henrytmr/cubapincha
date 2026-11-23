package com.cubapincha.job.data;

import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import android.util.Base64;

/** Cliente REST de Supabase Auth. Solo usa la clave publicable/anon del proyecto. */
public final class SupabaseAuthRest {
    private static final String TAG = "SupabaseAuthRest";
    private static final int TIMEOUT_MS = 25000;
    public static final String CALLBACK_URL = "cubapincha://auth/callback";

    private SupabaseAuthRest() { }

    public static String createPkceVerifier() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return Base64.encodeToString(random, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static String createPkceChallenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static class AuthTokens {
        public String accessToken = "";
        public String refreshToken = "";
        public String userId = "";
        public String email = "";
        public String displayName = "";
        public long expiresInSec;
        public long expiresAtEpochSec;
        public boolean hasSession() { return !accessToken.isEmpty() && !refreshToken.isEmpty(); }
    }

    public static ApiResult<AuthTokens> signIn(String projectUrl, String apiKey,
                                                String email, String password) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email.trim());
            body.put("password", password);
            return authRequest(projectUrl, apiKey, "POST", "/token?grant_type=password", body, null, true);
        } catch (Exception e) { return failOf(e); }
    }

    public static ApiResult<AuthTokens> signUp(String projectUrl, String apiKey,
                                                RegisterRequest request, String verifier) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", request.email.trim());
            body.put("password", request.password);
            JSONObject metadata = new JSONObject();
            metadata.put("display_name", request.displayName == null ? "" : request.displayName);
            metadata.put("role", request.role == null ? "" : request.role);
            body.put("data", metadata);
            body.put("code_challenge", createPkceChallenge(verifier));
            body.put("code_challenge_method", "s256");
            String path = "/signup?redirect_to=" + Uri.encode(CALLBACK_URL);
            return authRequest(projectUrl, apiKey, "POST", path, body, null, false);
        } catch (Exception e) { return failOf(e); }
    }

    /** Acepta el ID token emitido por Google Sign-In y lo cambia por una sesión Supabase. */
    public static ApiResult<AuthTokens> signInWithGoogle(String projectUrl, String apiKey,
                                                          String googleIdToken) {
        try {
            JSONObject body = new JSONObject();
            body.put("provider", "google");
            body.put("id_token", googleIdToken);
            return authRequest(projectUrl, apiKey, "POST", "/token?grant_type=id_token", body, null, true);
        } catch (Exception e) { return failOf(e); }
    }

    public static ApiResult<AuthTokens> refresh(String projectUrl, String apiKey, String refreshToken) {
        try {
            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);
            return authRequest(projectUrl, apiKey, "POST", "/token?grant_type=refresh_token", body, null, true);
        } catch (Exception e) { return failOf(e); }
    }

    public static ApiResult<Void> resetPassword(String projectUrl, String apiKey, String email) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email.trim());
            String path = "/recover?redirect_to=" + Uri.encode(CALLBACK_URL);
            ApiResult<JSONObject> result = authRequestRaw(projectUrl, apiKey, "POST", path, body, null);
            return result.ok ? ApiResult.success(null) : ApiResult.fail(result.errorCode, result.errorMessage);
        } catch (Exception e) { return failOf(e); }
    }

    public static ApiResult<Void> resetPassword(String projectUrl, String apiKey, String email,
                                                 String verifier) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email.trim());
            body.put("code_challenge", createPkceChallenge(verifier));
            body.put("code_challenge_method", "s256");
            String path = "/recover?redirect_to=" + Uri.encode(CALLBACK_URL);
            ApiResult<JSONObject> result = authRequestRaw(projectUrl, apiKey, "POST", path, body, null);
            return result.ok ? ApiResult.success(null) : ApiResult.fail(result.errorCode, result.errorMessage);
        } catch (Exception e) { return failOf(e); }
    }

    public static ApiResult<AuthTokens> exchangeCode(String projectUrl, String apiKey,
                                                      String authCode, String verifier) {
        try {
            JSONObject body = new JSONObject();
            body.put("auth_code", authCode);
            body.put("code_verifier", verifier);
            return authRequest(projectUrl, apiKey, "POST", "/token?grant_type=pkce", body, null, true);
        } catch (Exception e) { return failOf(e); }
    }

    public static ApiResult<Void> updatePassword(String projectUrl, String apiKey,
                                                  String accessToken, String newPassword) {
        try {
            JSONObject body = new JSONObject();
            body.put("password", newPassword);
            ApiResult<JSONObject> result = authRequestRaw(projectUrl, apiKey, "PUT", "/user", body, accessToken);
            return result.ok ? ApiResult.success(null) : ApiResult.fail(result.errorCode, result.errorMessage);
        } catch (Exception e) { return failOf(e); }
    }

    private static ApiResult<AuthTokens> authRequest(String projectUrl, String apiKey,
                                                      String method, String path,
                                                      JSONObject body, String accessToken,
                                                      boolean requireSession) {
        ApiResult<JSONObject> raw = authRequestRaw(projectUrl, apiKey, method, path, body, accessToken);
        if (!raw.ok || raw.data == null) return ApiResult.fail(raw.errorCode, raw.errorMessage);
        AuthTokens tokens = parseTokens(raw.data);
        if (requireSession && !tokens.hasSession()) {
            return ApiResult.fail("AUTH_REQUIRED", "Supabase no devolvió una sesión válida.");
        }
        return ApiResult.success(tokens);
    }

    private static ApiResult<JSONObject> authRequestRaw(String projectUrl, String apiKey,
                                                         String method, String path,
                                                         JSONObject body, String accessToken) {
        HttpURLConnection conn = null;
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                return ApiResult.fail("SUPABASE_KEY_REQUIRED", "La configuración de Supabase no está disponible en esta compilación.");
            }
            String root = projectUrl == null ? "" : projectUrl.trim().replaceAll("/+$", "");
            URL url = new URL(root + "/auth/v1" + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("apikey", apiKey.trim());
            if (accessToken != null && !accessToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String text = readStream(stream);
            JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (status < 200 || status >= 300) return mapError(json, status);
            return ApiResult.success(json);
        } catch (Exception e) {
            Log.w(TAG, "Auth request failed", e);
            return failOf(e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static AuthTokens parseTokens(JSONObject response) {
        AuthTokens t = new AuthTokens();
        t.accessToken = response.optString("access_token", "");
        t.refreshToken = response.optString("refresh_token", "");
        t.expiresInSec = response.optLong("expires_in", 0L);
        t.expiresAtEpochSec = response.optLong("expires_at", 0L);
        JSONObject user = response.optJSONObject("user");
        if (user != null) {
            t.userId = user.optString("id", "");
            t.email = user.optString("email", "");
            JSONObject metadata = user.optJSONObject("user_metadata");
            if (metadata != null) {
                t.displayName = metadata.optString("display_name",
                        metadata.optString("full_name", metadata.optString("name", "")));
            }
        }
        return t;
    }

    private static <T> ApiResult<T> mapError(JSONObject json, int status) {
        JSONObject error = json.optJSONObject("error");
        String code = json.optString("code", "");
        if (code.isEmpty() && error != null) code = error.optString("code", "");
        String message = json.optString("msg", json.optString("message", ""));
        if (message.isEmpty() && error != null) message = error.optString("message", "");
        String normalized = (code + " " + message).toLowerCase(java.util.Locale.US);
        if (normalized.contains("invalid_credentials") || normalized.contains("invalid login")
                || normalized.contains("email_not_confirmed") || status == 400 && normalized.contains("credentials")) {
            return ApiResult.fail("AUTH_REQUIRED", "Correo o contraseña incorrectos, o falta confirmar el correo.");
        }
        if (normalized.contains("already registered") || normalized.contains("user_already_exists")
                || normalized.contains("email_exists")) {
            return ApiResult.fail("DUPLICATE", "Ya existe una cuenta con ese correo. Inicia sesión.");
        }
        if (normalized.contains("weak_password") || normalized.contains("password should be")
                || normalized.contains("password is too short")) {
            return ApiResult.fail("VALIDATION_ERROR", "La contraseña debe tener al menos 6 caracteres.");
        }
        if (normalized.contains("rate limit") || normalized.contains("too many requests")
                || status == 429) {
            return ApiResult.fail("RATE_LIMITED", "Demasiados intentos. Espera un poco y vuelve a intentarlo.");
        }
        if (normalized.contains("refresh_token_not_found") || normalized.contains("invalid refresh token")) {
            return ApiResult.fail("AUTH_REQUIRED", "La sesión caducó. Inicia sesión de nuevo.");
        }
        if (message.isEmpty()) message = "No se pudo completar la solicitud de autenticación.";
        return ApiResult.fail(status == 401 ? "AUTH_REQUIRED" : "INTERNAL_ERROR", message);
    }

    private static <T> ApiResult<T> failOf(Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("UnknownHost") || m.contains("ConnectException")
                || m.contains("SocketTimeout") || m.contains("No route")) {
            return ApiResult.fail("NO_CONNECTION", "Comprueba tu conexión e inténtalo de nuevo.");
        }
        return ApiResult.fail("INTERNAL_ERROR", "No pudimos contactar con Supabase Auth.");
    }

    private static String readStream(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            return out.toString();
        }
    }
}
