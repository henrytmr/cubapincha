package com.cubapincha.job.data;

import com.cubapincha.job.model.AppNotification;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.model.ContactOptions;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.model.RatingsPage;
import com.cubapincha.job.model.RatingSubmission;
import com.cubapincha.job.model.RecommendedWorker;
import com.cubapincha.job.model.SavedSearch;
import com.cubapincha.job.model.User;

import java.util.List;

/**
 * Contrato del cliente de datos. Dos implementaciones:
 * DemoApiClient (datos locales de ejemplo) y SupabaseApiClient (Edge Function).
 * Todos los métodos son síncronos: el Repository los ejecuta en segundo plano.
 *
 * La sesión conectada se obtiene por Supabase Auth REST. El token nunca es
 * parte de este contrato de datos: SupabaseApiClient lo envía como Bearer.
 */
public interface ApiClient {
    ApiResult<String> health();

    /** Login local de demo (cualquier correo válido). Nunca se usa en modo conectado. */
    ApiResult<Session> login(String email, String role);

    ApiResult<Session> register(RegisterRequest request);

    /** Revoca la sesión en el servidor (modo conectado); en demo no hace nada. */
    ApiResult<Void> logout();

    ApiResult<User> me();

    ApiResult<User> updateProfile(User user);

    /** Extrae campos de una oferta sin crearla ni publicarla. */
    ApiResult<org.json.JSONObject> extractJob(String text);

    ApiResult<Page<Job>> getJobs(JobFilter filter, String cursor);

    ApiResult<Job> getJob(String jobId);

    ApiResult<List<Application>> getApplications(String status);

    /** Lee una página pública de valoraciones y su promedio/conteo global. */
    ApiResult<RatingsPage> getRatings(String userId, int limit, String cursor);

    /** Envía una valoración autenticada; no se debe encolar offline. */
    ApiResult<RatingSubmission> rateApplication(String applicationId, int stars, String comment);

    ApiResult<Application> expressInterest(String jobId, String clientActionId);

    ApiResult<Void> withdrawInterest(String applicationId);

    /** Recordatorio educado a la empresa (solo si el interés lleva ≥3 días sin respuesta). */
    ApiResult<Void> sendReminder(String applicationId);

    ApiResult<Application> changeApplicationStatus(String applicationId, String status, String note);

    /** Elimina una candidatura desde la empresa dueña de la oferta. */
    ApiResult<Void> deleteApplication(String applicationId);

    ApiResult<Boolean> saveJob(String jobId, boolean save);

    ApiResult<Boolean> saveCandidate(String workerId, String applicationId, boolean save);

    /** Trabajadores guardados por la empresa, contrato v17. */
    ApiResult<List<RecommendedWorker>> getSavedCandidates(int limit, String cursor);

    default ApiResult<List<RecommendedWorker>> getRecommendedWorkers(int minMatch, int limit) { return getRecommendedWorkers(null,null,null,null,minMatch,limit); }
    ApiResult<List<RecommendedWorker>> getRecommendedWorkers(String q, String provinceCode, String municipalityCode, String category, int minMatch, int limit);

    ApiResult<Boolean> inviteWorker(String workerId, String offerId, String message);
    ApiResult<Boolean> uninviteWorker(String workerId, String offerId);
    ApiResult<RecommendedWorker> getPublicWorker(String workerId);

    ApiResult<NotificationPage> getNotifications(boolean unreadOnly);

    ApiResult<Void> markNotificationRead(String notificationId);

    ApiResult<Void> markAllNotificationsRead();

    /** Borra una notificación o todo el historial propio, contrato v17. */
    ApiResult<Void> deleteNotifications(String notificationId, boolean all);

    ApiResult<Job> createJob(Job draft);

    ApiResult<Job> updateJob(Job draft);

    ApiResult<Job> publishJob(String jobId);

    ApiResult<Job> changeJobStatus(String jobId, String status);

    /** Archiva una oferta conservando su historial (no es un borrado). */
    ApiResult<Job> archiveJob(String jobId, String reason);

    /** Elimina DEFINITIVAMENTE un borrador sin candidaturas ni historial. */
    ApiResult<Void> deleteJob(String jobId);

    /** Catálogo de municipios corregido desde el backend (pestaña `catalogos`). */
    ApiResult<org.json.JSONObject> listCatalogs();

    ApiResult<SavedSearch> saveFilter(SavedSearch search);

    ApiResult<List<SavedSearch>> getSavedSearches();

    ApiResult<Void> deleteSavedSearch(String filterId);

    ApiResult<ContactOptions> getContactOptions(String entityType, String entityId);

    ApiResult<Void> contactEvent(String entityType, String entityId, String channel);

    ApiResult<org.json.JSONObject> uploadPhoto(String kind, String jobId,
                                                String imageBase64, String thumbBase64);

    ApiResult<Void> registerDevice(String token);

    /** Da de baja un token push (al cerrar sesión). */
    ApiResult<Void> unregisterDevice(String token);

    ApiResult<Void> report(String entityType, String entityId, String reason);

    /** Cambios desde `since`, página `offset` (el servidor entrega 100 por
     *  colección; sin offset, más de 100 cambios se perderían). */
    ApiResult<SyncResult> sync(String since, int offset);

    /** Procesa acciones offline encoladas; devuelve el resultado POR ACCIÓN. */
    ApiResult<List<SyncActionResult>> syncActions(List<PendingAction> actions);
}
