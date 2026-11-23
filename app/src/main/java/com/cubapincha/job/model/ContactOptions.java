package com.cubapincha.job.model;

import org.json.JSONObject;

/** Opciones de contacto permitidas (sin revelar datos no autorizados). */
public class ContactOptions {
    public boolean whatsappEnabled;
    public boolean phoneEnabled;
    public String publicPhone; // vacío si no autorizado
    public boolean authorized;
    public String contactHours; // horario recomendado, p. ej. "9:00–17:00"

    public static ContactOptions fromJson(JSONObject o) {
        ContactOptions c = new ContactOptions();
        c.whatsappEnabled = o.optBoolean("whatsappEnabled", false);
        c.phoneEnabled = o.optBoolean("phoneEnabled", false);
        c.publicPhone = o.optString("publicPhone", "");
        c.authorized = o.optBoolean("authorized", false);
        c.contactHours = o.optString("contactHours", "");
        return c;
    }
}
