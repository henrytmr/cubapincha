package com.cubapincha.job.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.cubapincha.job.R;
import com.cubapincha.job.data.LocalDb;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.model.AppNotification;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.ui.MainActivity;

import java.util.Collections;

/**
 * Recibe los push reales de CubaPincha (FCM) y los muestra como
 * notificaciones del sistema. El backend envía solo datos (sin payload
 * "notification"), así este método se ejecuta siempre, con la app abierta
 * o cerrada. Tocar la notificación abre la oferta si trae ruta.
 */
public class CubaPinchaMessagingService extends FirebaseMessagingService {
    public static final String CHANNEL_ID = "cubapincha";
    public static final String EXTRA_PUSH_ROUTE = "push_route";

    @Override
    public void onCreate() {
        super.onCreate();
        // El proceso puede arrancar solo para este servicio: Firebase debe
        // estar inicializado antes de cualquier uso.
        PushManager.initIfNeeded(this);
    }

    /** FCM rotó el token: guardarlo y re-registrarlo (si no, el backend
     *  queda con un token muerto y el push deja de llegar). */
    @Override
    public void onNewToken(String token) {
        if (token == null || token.isEmpty()) return;
        SessionManager.get(this).setFcmToken(token);
        Repository repo = Repository.get(this);
        if (!repo.isDemoMode() && SessionManager.get(this).isLoggedIn()) {
            repo.registerPushToken(token, r -> { });
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        SessionManager session = SessionManager.get(this);
        // El backend usa mensajes solo-data; aplica aquí la preferencia incluso
        // si llega un envío tardío tras logout o con avisos desactivados.
        if (!session.isLoggedIn() || !session.isNotificationsEnabled()) return;
        String title = null, body = null, route = null;
        if (message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        }
        if (message.getData() != null && !message.getData().isEmpty()) {
            if (title == null) title = message.getData().get("title");
            if (body == null) body = message.getData().get("body");
            route = message.getData().get("route");
        }
        if ((title == null || title.isEmpty()) && (body == null || body.isEmpty())) return;
        String notificationId = message.getData().get("notification_id");
        if (notificationId == null || notificationId.isEmpty()) {
            notificationId = message.getMessageId();
        }
        if (notificationId == null || notificationId.isEmpty()) {
            notificationId = "push-" + System.currentTimeMillis();
        }
        AppNotification notification = new AppNotification();
        notification.notificationId = notificationId;
        notification.type = message.getData().get("type");
        notification.title = title;
        notification.body = body;
        notification.entityId = message.getData().get("entity_id");
        notification.route = route;
        notification.read = false;
        notification.createdAt = message.getData().get("created_at");
        if (notification.createdAt == null || notification.createdAt.isEmpty()) {
            notification.createdAt = java.time.Instant.now().toString();
        }
        LocalDb.get(this).cacheNotifications(Collections.singletonList(notification));
        sendBroadcast(new Intent(MainActivity.ACTION_NOTIFICATIONS_CHANGED)
                .setPackage(getPackageName()));
        showNotification(title, body, route);
    }

    private void showNotification(String title, String body, String route) {
        createChannel();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (route != null && !route.isEmpty()) intent.putExtra(EXTRA_PUSH_ROUTE, route);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title != null ? title : getString(R.string.app_name))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(this)
                .notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Ofertas, candidaturas y avisos de CubaPincha.");
        nm.createNotificationChannel(channel);
    }
}
