package com.cubapincha.job.data;

/** Resultado uniforme de cualquier llamada: éxito, error o en cola offline. */
public class ApiResult<T> {
    public final boolean ok;
    public final T data;
    public final String errorCode;
    public final String errorMessage;
    /** true si la acción quedó en cola para sincronizar al recuperar la conexión. */
    public final boolean queued;

    private ApiResult(boolean ok, T data, String errorCode, String errorMessage, boolean queued) {
        this.ok = ok;
        this.data = data;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.queued = queued;
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(true, data, null, null, false);
    }

    public static <T> ApiResult<T> fail(String code, String message) {
        return new ApiResult<>(false, null, code, message, false);
    }

    public static <T> ApiResult<T> queued() {
        return new ApiResult<>(true, null, null, null, true);
    }

    /** Mensaje amable para mostrar al usuario según el código de error. */
    public String friendlyMessage() {
        if (errorMessage != null && !errorMessage.isEmpty()) return errorMessage;
        if (errorCode == null) return "Algo no salió como esperábamos. Inténtalo de nuevo.";
        switch (errorCode) {
            case "AUTH_REQUIRED": return "Tu sesión terminó. Inicia sesión de nuevo, por favor.";
            case "FORBIDDEN_ROLE": return "Esta acción no está disponible para tu perfil.";
            case "VALIDATION_ERROR": return "Revisa los datos, parece que falta algo.";
            case "NOT_FOUND": return "No encontramos lo que buscas. Puede que ya no esté disponible.";
            case "DUPLICATE": return "Ya existe un registro igual. No se duplicó nada.";
            case "RATE_LIMITED": return "Vas muy rápido. Espera un momento e inténtalo de nuevo.";
            case "CONFLICT": return "Esta información cambió mientras tanto. Actualiza y revisa.";
            case "NO_CONNECTION": return "Sin conexión. Puedes seguir viendo tu contenido guardado.";
            case "SUPABASE_KEY_REQUIRED": return "Falta la clave publicable de Supabase. Ve a Perfil > Conexión.";
            case "EMAIL_CONFIRMATION_REQUIRED": return errorMessage != null ? errorMessage : "Confirma tu correo y luego inicia sesión para terminar el registro.";
            default: return "Algo no salió como esperábamos. Inténtalo de nuevo.";
        }
    }
}
