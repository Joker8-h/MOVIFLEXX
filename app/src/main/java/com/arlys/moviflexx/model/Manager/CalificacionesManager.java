package com.arlys.moviflexx.model.Manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.pojo.Calificaciones;

import org.json.JSONObject;


public class CalificacionesManager {

    private static final String TAG   = "CalificacionesManager";
    private static final String PREFS = "calificaciones_cache";

    private final Context           context;
    private final SharedPreferences prefs;

    public CalificacionesManager(Context context) {
        this.context = context;
        this.prefs   = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // =========================================================================
    //  INTERFACES
    // =========================================================================

    public interface OnVerificacionListener {
        void onDebeCalificar();
        void onYaCalifico(int puntuacion, String estrellas);
    }

    public interface OnCalificacionListener {
        void onExito(int puntuacion, String mensaje);
        void onError(String mensaje);
    }

    public interface OnPromedioListener {
        void onPromedio(double promedio, int total, String estrellas);
        void onError(String mensaje);
    }

    // =========================================================================
    //  CACHE LOCAL
    // =========================================================================

    private String cacheKey(int idViaje, int idCalificador, int idCalificado) {
        return "viaje_" + idViaje + "_cal_" + idCalificador + "_to_" + idCalificado;
    }

    private boolean yaCalificoLocalmente(int idViaje, int idCalificador, int idCalificado) {
        return prefs.contains(cacheKey(idViaje, idCalificador, idCalificado));
    }

    private void guardarEnCache(int idViaje, int idCalificador, int idCalificado, int puntuacion) {
        prefs.edit()
                .putInt(cacheKey(idViaje, idCalificador, idCalificado), puntuacion)
                .apply();
        Log.d(TAG, "Cache guardado → viaje=" + idViaje
                + " calificador=" + idCalificador
                + " calificado=" + idCalificado
                + " puntuacion=" + puntuacion);
    }

    private int puntuacionCache(int idViaje, int idCalificador, int idCalificado) {
        return prefs.getInt(cacheKey(idViaje, idCalificador, idCalificado), 0);
    }

    // =========================================================================
    //  VERIFICAR SI YA CALIFICO
    // =========================================================================

    public void verificarCalificacion(int idViaje,
                                      int idCalificador,
                                      int idCalificado,
                                      OnVerificacionListener listener) {
        if (idViaje <= 0 || idCalificador <= 0 || idCalificado <= 0) {
            Log.w(TAG, "IDs inválidos — idViaje=" + idViaje
                    + " idCalificador=" + idCalificador
                    + " idCalificado=" + idCalificado);
            listener.onDebeCalificar();
            return;
        }

        if (yaCalificoLocalmente(idViaje, idCalificador, idCalificado)) {
            int p = puntuacionCache(idViaje, idCalificador, idCalificado);
            listener.onYaCalifico(p, Calificaciones.construirEstrellas(p));
        } else {
            listener.onDebeCalificar();
        }
    }

    // =========================================================================
    //  ENVIAR CALIFICACION  (comentario opcional)
    //  Body: { idViaje, idCalificador, idCalificado, puntuacion, comentario? }
    // =========================================================================

    public void enviarCalificacion(int idViaje,
                                   int idCalificador,
                                   int idCalificado,
                                   int puntuacion,
                                   String comentario,
                                   OnCalificacionListener listener) {

        if (idViaje <= 0)      { listener.onError("Viaje inválido");               return; }
        if (idCalificado <= 0) { listener.onError("Usuario a calificar inválido"); return; }
        if (idCalificador <= 0){ listener.onError("Sesión inválida");              return; }
        if (puntuacion < 1 || puntuacion > 5) {
            listener.onError("La puntuación debe ser entre 1 y 5");
            return;
        }

        if (yaCalificoLocalmente(idViaje, idCalificador, idCalificado)) {
            Log.d(TAG, "Ya calificado según cache — ignorando");
            listener.onError("Ya calificaste a este usuario en este viaje");
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("idViaje",       idViaje);
            body.put("idCalificador", idCalificador);
            body.put("idCalificado",  idCalificado);
            body.put("puntuacion",    puntuacion);
            // Comentario es opcional — solo se envía si el usuario escribió algo
            if (comentario != null && !comentario.trim().isEmpty())
                body.put("comentario", comentario.trim());

            Log.d(TAG, "Enviando → viaje=" + idViaje
                    + " calificador=" + idCalificador
                    + " calificado=" + idCalificado
                    + " puntuacion=" + puntuacion
                    + " comentario=" + (comentario != null ? comentario.trim() : "vacío"));

            ConexionApi.getInstance(context).post(
                    Constantes.CALIFICACIONES,
                    body,
                    response -> {
                        Log.d(TAG, "OK en backend");
                        guardarEnCache(idViaje, idCalificador, idCalificado, puntuacion);
                        listener.onExito(puntuacion, mensajeToast(puntuacion));
                    },
                    error -> {
                        int code = (error != null && error.networkResponse != null)
                                ? error.networkResponse.statusCode : 0;
                        Log.e(TAG, "Error status=" + code);
                        switch (code) {
                            case 409:
                                guardarEnCache(idViaje, idCalificador, idCalificado, puntuacion);
                                listener.onExito(puntuacion, "✅ Calificación registrada");
                                break;
                            case 400: listener.onError("Datos inválidos al calificar");     break;
                            case 403: listener.onError("Sin permiso para calificar");       break;
                            case 404: listener.onError("Viaje o usuario no encontrado");    break;
                            default:  listener.onError("Error al guardar la calificación"); break;
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Excepción construyendo calificación", e);
            listener.onError("Error interno al calificar");
        }
    }

    // =========================================================================
    //  OBTENER PROMEDIO
    // =========================================================================

    public void obtenerPromedio(int idUsuario, OnPromedioListener listener) {
        if (idUsuario <= 0) { listener.onError("ID de usuario inválido"); return; }
        ConexionApi.getInstance(context).getObject(
                Constantes.calificacionPromedio((long) idUsuario),
                response -> {
                    double promedio = response.optDouble("promedio",
                            response.optDouble("average",
                                    response.optDouble("calificacionPromedio", 0.0)));
                    int total = response.optInt("total",
                            response.optInt("count",
                                    response.optInt("totalCalificaciones", 0)));
                    listener.onPromedio(promedio, total,
                            Calificaciones.construirEstrellas((int) Math.round(promedio)));
                },
                error -> listener.onError("No se pudo obtener el promedio")
        );
    }

    // =========================================================================
    //  LIMPIAR CACHE
    // =========================================================================

    public void limpiarCache() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Cache limpiado completamente");
    }

    public void limpiarCacheViaje(int idViaje, int idCalificador, int idCalificado) {
        prefs.edit().remove(cacheKey(idViaje, idCalificador, idCalificado)).apply();
        Log.d(TAG, "Cache limpiado → viaje=" + idViaje
                + " calificador=" + idCalificador
                + " calificado=" + idCalificado);
    }

    // =========================================================================
    //  HELPER — mensaje Toast
    // =========================================================================

    public static String mensajeToast(int puntuacion) {
        switch (puntuacion) {
            case 1: return "😢 Gracias por tu honestidad. Guardada correctamente";
            case 2: return "😐 Gracias por calificar. Guardada correctamente";
            case 3: return "🙂 ¡Buena calificación! Guardada correctamente";
            case 4: return "😊 ¡Muy buena calificación! Guardada correctamente";
            case 5: return "⭐ ¡Calificación excelente! Guardada correctamente";
            default: return "✅ Calificación guardada";
        }
    }
}