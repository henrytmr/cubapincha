package com.cubapincha.job.data;

/** Sesión local: JWT de acceso Supabase + identidad mínima. */
public class Session {
    public String userId;
    public String role;
    public String status;
    public String displayName;
    public String token;
    public String refreshToken;
    public String expiresAt;
    public boolean isNew;

    public Session() {}

    public Session(String userId, String role, String status, String displayName, String token) {
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.displayName = displayName;
        this.token = token;
    }
}
