package com.cubapincha.job.model;

import org.json.JSONObject;

/** Notificación del centro de notificaciones. Alineada con readNotifications_. */
public class AppNotification {
    public String notificationId;
    public String type;
    public String title;
    public String body;
    public String entityId;
    public String route;
    public boolean read;
    public String createdAt;

    public static AppNotification fromJson(JSONObject o) {
        AppNotification n = new AppNotification();
        n.notificationId = o.optString("notificationId", "");
        n.type = o.optString("type", "");
        n.title = o.optString("title", "");
        n.body = o.optString("body", "");
        n.entityId = o.optString("entityId", "");
        n.route = o.optString("route", "");
        n.read = o.optBoolean("read", false);
        n.createdAt = o.optString("createdAt", "");
        return n;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("notificationId", notificationId);
            o.put("type", type);
            o.put("title", title);
            o.put("body", body);
            o.put("entityId", entityId);
            o.put("route", route);
            o.put("read", read);
            o.put("createdAt", createdAt);
        } catch (Exception ignored) { }
        return o;
    }
}
