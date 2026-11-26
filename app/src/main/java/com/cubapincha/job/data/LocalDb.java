package com.cubapincha.job.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.cubapincha.job.model.AppNotification;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.model.RecommendedWorker;
import com.cubapincha.job.model.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Caché local ligera: ofertas descargadas, guardadas, cola de acciones
 * offline y estado del modo demo. La app funciona sin conexión con esto.
 *
 * Cifrado (v4): los datos personales (candidaturas, notificaciones,
 * cola offline y borradores) se guardan cifrados con AES-256-GCM y clave
 * en el Keystore (CryptoHelper). Las ofertas en caché son datos públicos
 * y quedan en claro; las guardadas son solo IDs.
 */
public class LocalDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "cubapincha.db";
    private static final int DB_VERSION = 7;

    private static LocalDb instance;

    public static synchronized LocalDb get(Context ctx) {
        if (instance == null) instance = new LocalDb(ctx.getApplicationContext());
        return instance;
    }

    private LocalDb(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE cached_jobs (jobId TEXT PRIMARY KEY, json TEXT, updatedAt TEXT)");
        db.execSQL("CREATE TABLE cached_applications (applicationId TEXT PRIMARY KEY, json TEXT, updatedAt TEXT)");
        db.execSQL("CREATE TABLE cached_notifications (notificationId TEXT PRIMARY KEY, json TEXT, createdAt TEXT)");
        db.execSQL("CREATE TABLE saved_jobs (jobId TEXT PRIMARY KEY, folder TEXT, createdAt TEXT)");
        db.execSQL("CREATE TABLE saved_candidates (workerId TEXT PRIMARY KEY, json TEXT, savedAt TEXT)");
        db.execSQL("CREATE TABLE cached_recommended_workers (workerId TEXT PRIMARY KEY, json TEXT, updatedAt TEXT)");
        db.execSQL("CREATE TABLE cached_profile (profileId TEXT PRIMARY KEY, json TEXT, updatedAt TEXT)");
        db.execSQL("CREATE TABLE pending_actions (clientActionId TEXT PRIMARY KEY, actionType TEXT, payload TEXT, status TEXT, createdAt TEXT, errorCode TEXT)");
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)");
        db.execSQL("CREATE TABLE demo_state (key TEXT PRIMARY KEY, json TEXT)");
        db.execSQL("CREATE TABLE drafts (draftId TEXT PRIMARY KEY, json TEXT, createdAt TEXT, updatedAt TEXT)");
        db.execSQL("CREATE TABLE photo_visibility (entityType TEXT, entityId TEXT, visible INTEGER NOT NULL, updatedAt TEXT, PRIMARY KEY(entityType, entityId))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // v1.2: caché de candidaturas y notificaciones (sincronización).
            db.execSQL("CREATE TABLE IF NOT EXISTS cached_applications (applicationId TEXT PRIMARY KEY, json TEXT, updatedAt TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS cached_notifications (notificationId TEXT PRIMARY KEY, json TEXT, createdAt TEXT)");
        }
        if (oldVersion < 3) {
            // v2.0 (CubaPincha): borradores de oferta guardados offline.
            db.execSQL("CREATE TABLE IF NOT EXISTS drafts (draftId TEXT PRIMARY KEY, json TEXT, createdAt TEXT, updatedAt TEXT)");
        }
        if (oldVersion < 4) {
            // v2.9: cifrar los datos personales ya guardados (candidaturas,
            // notificaciones, cola y borradores). Por filas y con
            // tolerancia a fallos: una fila que no se pueda cifrar queda
            // en claro y se sigue leyendo igual (decrypt la acepta).
            encryptColumn(db, "cached_applications", "applicationId", "json");
            encryptColumn(db, "cached_notifications", "notificationId", "json");
            encryptColumn(db, "pending_actions", "clientActionId", "payload");
            encryptColumn(db, "drafts", "draftId", "json");
        }
        if (oldVersion < 5) {
            db.execSQL("CREATE TABLE IF NOT EXISTS saved_candidates (workerId TEXT PRIMARY KEY, json TEXT, savedAt TEXT)");
        }
        if (oldVersion < 6) {
            db.execSQL("CREATE TABLE IF NOT EXISTS cached_recommended_workers (workerId TEXT PRIMARY KEY, json TEXT, updatedAt TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS cached_profile (profileId TEXT PRIMARY KEY, json TEXT, updatedAt TEXT)");
        }
        if (oldVersion < 7) {
            db.execSQL("CREATE TABLE IF NOT EXISTS photo_visibility (entityType TEXT, entityId TEXT, visible INTEGER NOT NULL, updatedAt TEXT, PRIMARY KEY(entityType, entityId))");
        }
    }

    /** Estado local por entityType+id; no comparte visibilidad entre ofertas y trabajadores. */
    public boolean isPhotoVisible(String entityType, String entityId) {
        if (entityType == null || entityId == null || entityType.isEmpty() || entityId.isEmpty()) return false;
        try (Cursor c = getReadableDatabase().query("photo_visibility", new String[]{"visible"},
                "entityType = ? AND entityId = ?", new String[]{entityType, entityId}, null, null, null)) {
            return c.moveToFirst() && c.getInt(0) == 1;
        }
    }

    public void setPhotoVisible(String entityType, String entityId, boolean visible) {
        if (entityType == null || entityId == null || entityType.isEmpty() || entityId.isEmpty()) return;
        ContentValues values = new ContentValues();
        values.put("entityType", entityType);
        values.put("entityId", entityId);
        values.put("visible", visible ? 1 : 0);
        values.put("updatedAt", String.valueOf(System.currentTimeMillis()));
        getWritableDatabase().insertWithOnConflict("photo_visibility", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Cifra en su sitio los valores en claro de una columna (migración v4). */
    private void encryptColumn(SQLiteDatabase db, String table, String idCol, String col) {
        try (Cursor c = db.query(table, new String[]{idCol, col}, null, null, null, null, null)) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                String value = c.getString(1);
                if (id == null || value == null || value.startsWith("v1:")) continue;
                try {
                    ContentValues cv = new ContentValues();
                    cv.put(col, CryptoHelper.encrypt(value));
                    db.update(table, cv, idCol + " = ?", new String[]{id});
                } catch (Exception ignored) {
                    // La fila queda en claro; se cifra en la próxima escritura.
                }
            }
        } catch (Exception ignored) {
            // Tabla ausente o ilegible: nada que migrar.
        }
    }

    // ---- Ofertas en caché ----
    public void cacheJobs(List<Job> jobs) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Job j : jobs) {
                ContentValues cv = new ContentValues();
                cv.put("jobId", j.jobId);
                cv.put("json", j.toJson().toString());
                cv.put("updatedAt", j.updatedAt);
                db.insertWithOnConflict("cached_jobs", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Job> getCachedJobs() {
        List<Job> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("cached_jobs", new String[]{"json"}, null, null, null, null, "updatedAt DESC")) {
            while (c.moveToNext()) {
                try {
                    out.add(Job.fromJson(new JSONObject(c.getString(0))));
                } catch (Exception ignored) { }
            }
        }
        return out;
    }

    public Job getCachedJob(String jobId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("cached_jobs", new String[]{"json"}, "jobId = ?", new String[]{jobId}, null, null, null)) {
            if (c.moveToFirst()) {
                try {
                    return Job.fromJson(new JSONObject(c.getString(0)));
                } catch (Exception ignored) { }
            }
        }
        return null;
    }

    // ---- Candidaturas y notificaciones en caché (sincronización) ----
    /** Guarda candidaturas FUSIONANDO con lo ya guardado: el sync trae
     *  registros parciales (sin título ni nombres) y reemplazarlos tal cual
     *  borraría los datos completos que ya teníamos en caché. */
    public void cacheApplications(List<Application> apps) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Application a : apps) {
                Application existing = getCachedApplication(db, a.applicationId);
                if (existing != null) a.mergeFrom(existing);
                ContentValues cv = new ContentValues();
                cv.put("applicationId", a.applicationId);
                cv.put("json", CryptoHelper.encrypt(a.toJson().toString()));
                cv.put("updatedAt", a.updatedAt);
                db.insertWithOnConflict("cached_applications", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private Application getCachedApplication(SQLiteDatabase db, String applicationId) {
        if (applicationId == null || applicationId.isEmpty()) return null;
        try (Cursor c = db.query("cached_applications", new String[]{"json"},
                "applicationId = ?", new String[]{applicationId}, null, null, null)) {
            if (c.moveToFirst()) {
                try {
                    return Application.fromJson(new JSONObject(CryptoHelper.decrypt(c.getString(0))));
                } catch (Exception ignored) { }
            }
        }
        return null;
    }

    public List<Application> getCachedApplications() {
        List<Application> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("cached_applications", new String[]{"json"}, null, null, null, null, "updatedAt DESC")) {
            while (c.moveToNext()) {
                try {
                    out.add(Application.fromJson(new JSONObject(CryptoHelper.decrypt(c.getString(0)))));
                } catch (Exception ignored) { }
            }
        }
        return out;
    }

    public void cacheNotifications(List<AppNotification> notifs) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (AppNotification n : notifs) {
                ContentValues cv = new ContentValues();
                cv.put("notificationId", n.notificationId);
                cv.put("json", CryptoHelper.encrypt(n.toJson().toString()));
                cv.put("createdAt", n.createdAt);
                db.insertWithOnConflict("cached_notifications", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<AppNotification> getCachedNotifications() {
        List<AppNotification> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("cached_notifications", new String[]{"json"}, null, null, null, null, "createdAt DESC")) {
            while (c.moveToNext()) {
                try {
                    out.add(AppNotification.fromJson(new JSONObject(CryptoHelper.decrypt(c.getString(0)))));
                } catch (Exception ignored) { }
            }
        }
        return out;
    }

    public void deleteCachedNotification(String notificationId) {
        getWritableDatabase().delete("cached_notifications", "notificationId = ?", new String[]{notificationId});
    }

    public void clearCachedNotifications() {
        getWritableDatabase().delete("cached_notifications", null, null);
    }

    public void setSavedCandidate(com.cubapincha.job.model.RecommendedWorker worker, boolean saved) {
        SQLiteDatabase db = getWritableDatabase();
        if (!saved) {
            db.delete("saved_candidates", "workerId = ?", new String[]{worker.workerId});
            return;
        }
        try {
            ContentValues cv = new ContentValues();
            cv.put("workerId", worker.workerId);
            cv.put("json", CryptoHelper.encrypt(worker.toJson().toString()));
            cv.put("savedAt", String.valueOf(System.currentTimeMillis()));
            db.insertWithOnConflict("saved_candidates", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception ignored) { }
    }

    public List<com.cubapincha.job.model.RecommendedWorker> getSavedCandidates() {
        List<com.cubapincha.job.model.RecommendedWorker> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("saved_candidates", new String[]{"json"}, null, null, null, null, "savedAt DESC")) {
            while (c.moveToNext()) {
                try { out.add(com.cubapincha.job.model.RecommendedWorker.fromJson(new JSONObject(CryptoHelper.decrypt(c.getString(0))))); }
                catch (Exception ignored) { }
            }
        }
        return out;
    }

    public void cacheRecommendedWorkers(List<RecommendedWorker> workers) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (RecommendedWorker worker : workers) {
                ContentValues cv = new ContentValues();
                cv.put("workerId", worker.workerId);
                cv.put("json", CryptoHelper.encrypt(worker.toJson().toString()));
                cv.put("updatedAt", String.valueOf(System.currentTimeMillis()));
                db.insertWithOnConflict("cached_recommended_workers", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public List<RecommendedWorker> getCachedRecommendedWorkers() {
        List<RecommendedWorker> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("cached_recommended_workers", new String[]{"json"},
                null, null, null, null, "updatedAt DESC")) {
            while (c.moveToNext()) {
                try { out.add(RecommendedWorker.fromJson(new JSONObject(CryptoHelper.decrypt(c.getString(0))))); }
                catch (Exception ignored) { }
            }
        }
        return out;
    }

    public void cacheProfile(User user) {
        if (user == null || user.userId == null || user.userId.isEmpty()) return;
        ContentValues cv = new ContentValues();
        cv.put("profileId", user.userId);
        cv.put("json", CryptoHelper.encrypt(user.toJson().toString()));
        cv.put("updatedAt", String.valueOf(System.currentTimeMillis()));
        getWritableDatabase().insertWithOnConflict("cached_profile", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public User getCachedProfile(String userId) {
        if (userId == null || userId.isEmpty()) return null;
        try (Cursor c = getReadableDatabase().query("cached_profile", new String[]{"json"},
                "profileId = ?", new String[]{userId}, null, null, null)) {
            if (c.moveToFirst()) return User.fromJson(new JSONObject(CryptoHelper.decrypt(c.getString(0))));
        } catch (Exception ignored) { }
        return null;
    }

    // ---- Guardadas ----
    public void setSaved(String jobId, boolean saved) {
        SQLiteDatabase db = getWritableDatabase();
        if (saved) {
            ContentValues cv = new ContentValues();
            cv.put("jobId", jobId);
            cv.put("folder", "general");
            cv.put("createdAt", String.valueOf(System.currentTimeMillis()));
            db.insertWithOnConflict("saved_jobs", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        } else {
            db.delete("saved_jobs", "jobId = ?", new String[]{jobId});
        }
    }

    public boolean isSaved(String jobId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("saved_jobs", new String[]{"jobId"}, "jobId = ?", new String[]{jobId}, null, null, null)) {
            return c.moveToFirst();
        }
    }

    public List<String> getSavedJobIds() {
        List<String> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("saved_jobs", new String[]{"jobId"}, null, null, null, null, "createdAt DESC")) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    // ---- Cola offline ----
    public boolean enqueue(PendingAction action) {
        ContentValues cv = new ContentValues();
        cv.put("clientActionId", action.clientActionId);
        cv.put("actionType", action.actionType);
        cv.put("payload", CryptoHelper.encrypt(action.payloadJson));
        cv.put("status", action.status);
        cv.put("createdAt", action.createdAt);
        cv.put("errorCode", action.errorCode);
        return getWritableDatabase().insertWithOnConflict(
                "pending_actions", null, cv, SQLiteDatabase.CONFLICT_REPLACE) != -1;
    }

    public List<PendingAction> getPendingActions() {
        List<PendingAction> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("pending_actions", null, "status = 'pending'", null, null, null, "createdAt ASC")) {
            while (c.moveToNext()) {
                PendingAction a = new PendingAction();
                a.clientActionId = c.getString(c.getColumnIndexOrThrow("clientActionId"));
                a.actionType = c.getString(c.getColumnIndexOrThrow("actionType"));
                a.payloadJson = CryptoHelper.decrypt(c.getString(c.getColumnIndexOrThrow("payload")));
                // Sin contenido no se puede sincronizar: se omite (la fila
                // queda en la base; no se pierde ni rompe el sync).
                if (a.payloadJson == null) continue;
                a.status = c.getString(c.getColumnIndexOrThrow("status"));
                a.createdAt = c.getString(c.getColumnIndexOrThrow("createdAt"));
                a.errorCode = c.getString(c.getColumnIndexOrThrow("errorCode"));
                out.add(a);
            }
        }
        return out;
    }

    public int getPendingCount() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM pending_actions WHERE status = 'pending'", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Acciones ya rechazadas o en conflicto que necesitan intervención del usuario. */
    public int getReviewCount() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM pending_actions WHERE status IN ('rejected', 'conflict')", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void markActionDone(String clientActionId, String status, String errorCode) {
        ContentValues cv = new ContentValues();
        cv.put("status", status);
        cv.put("errorCode", errorCode);
        getWritableDatabase().update("pending_actions", cv, "clientActionId = ?", new String[]{clientActionId});
    }

    public void removeAction(String clientActionId) {
        getWritableDatabase().delete("pending_actions", "clientActionId = ?", new String[]{clientActionId});
    }

    // ---- Borradores offline (v2.0) ----
    /** Guarda un borrador de oferta completo en el teléfono (sin conexión). */
    public void saveDraft(Job draft) {
        ContentValues cv = new ContentValues();
        cv.put("draftId", draft.jobId);
        cv.put("json", CryptoHelper.encrypt(draft.toJson().toString()));
        cv.put("createdAt", draft.createdAt);
        cv.put("updatedAt", draft.updatedAt);
        getWritableDatabase().insertWithOnConflict("drafts", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<Job> getDrafts() {
        List<Job> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("drafts", new String[]{"json"}, null, null, null, null, "updatedAt DESC")) {
            while (c.moveToNext()) {
                try {
                    out.add(Job.fromJson(new JSONObject(CryptoHelper.decrypt(c.getString(0)))));
                } catch (Exception ignored) { }
            }
        }
        return out;
    }

    public Job getDraft(String draftId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("drafts", new String[]{"json"}, "draftId = ?", new String[]{draftId}, null, null, null)) {
            if (c.moveToFirst()) {
                try {
                    return Job.fromJson(new JSONObject(CryptoHelper.decrypt(c.getString(0))));
                } catch (Exception ignored) { }
            }
        }
        return null;
    }

    public void deleteDraft(String draftId) {
        getWritableDatabase().delete("drafts", "draftId = ?", new String[]{draftId});
    }

    public int getDraftCount() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM drafts", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Datos offline que no pueden mostrarse a una cuenta distinta. */
    public int getOfflineWorkCount() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT (SELECT COUNT(*) FROM pending_actions) + (SELECT COUNT(*) FROM drafts)", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    // ---- Meta ----
    public void setMeta(String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        getWritableDatabase().insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getMeta(String key) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("meta", new String[]{"value"}, "key = ?", new String[]{key}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    // ---- Estado demo (JSON por clave) ----
    public void setDemoState(String key, JSONArray json) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("json", json.toString());
        getWritableDatabase().insertWithOnConflict("demo_state", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public JSONArray getDemoState(String key) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("demo_state", new String[]{"json"}, "key = ?", new String[]{key}, null, null, null)) {
            if (c.moveToFirst()) return new JSONArray(c.getString(0));
        } catch (Exception ignored) { }
        return null;
    }

    /** Limpia cachés de la sesión, pero conserva borradores, cola y el ID remoto
     *  de publicación (`publish:<draftId>`) para que el mismo usuario pueda
     *  reanudar después de logout sin crear una oferta duplicada. */
    public void clearUserData() {
        clearAccountCaches(true);
    }

    /** Limpieza completa de datos de una cuenta al iniciar sesión con otra. */
    public void clearAccountData() {
        clearAccountCaches(false);
    }

    private void clearAccountCaches(boolean preserveDraftPublishIds) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("cached_jobs", null, null);
            db.delete("cached_applications", null, null);
            db.delete("cached_notifications", null, null);
            db.delete("cached_recommended_workers", null, null);
            db.delete("cached_profile", null, null);
            db.delete("saved_jobs", null, null);
            db.delete("demo_state", null, null);
            // La identidad de los datos offline sobrevive al logout/cambio.
            if (preserveDraftPublishIds) {
                db.delete("meta", "key != ? AND key NOT LIKE ?",
                        new String[]{"offline_owner_user_id", "publish:%"});
            } else {
                db.delete("meta", "key != ?", new String[]{"offline_owner_user_id"});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Borra únicamente contenido descargado, conservando sesión, guardados y cola. */
    public void clearDownloadedCache() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("cached_jobs", null, null);
        db.delete("cached_applications", null, null);
        db.delete("cached_notifications", null, null);
        db.delete("cached_recommended_workers", null, null);
        db.delete("cached_profile", null, null);
    }
    /** Borra la cola offline y los borradores: solo cuando entra una cuenta
     *  distinta a la que los creó (no le pertenecen a la nueva cuenta). */
    public void clearPendingAndDrafts() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("pending_actions", null, null);
            db.delete("drafts", null, null);
            db.delete("meta", "key LIKE ?", new String[]{"publish:%"});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
