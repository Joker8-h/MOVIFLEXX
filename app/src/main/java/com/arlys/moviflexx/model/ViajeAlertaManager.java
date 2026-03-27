package com.arlys.moviflexx.model;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.controller.Notificaciones;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gestiona el inicio automático del viaje Y las notificaciones previas al conductor:
 *
 *   · T-5 min  → "Tu viaje sale en 5 minutos"
 *   · T-1 min  → "¡Tu viaje sale en 1 minuto!"
 *   · T+0      → Inicia el viaje en el backend + "¡Es hora de iniciar!"
 *
 * Mantiene compatibilidad total con el código existente que llama a
 * programarInicioAutomatico() y cancelarAlertas(idViaje).
 */
public class ViajeAlertaManager {

    private static final String TAG = "ViajeAlertaManager";

    // ── Canales de notificación ───────────────────────────────────────────────
    private static final String CHANNEL_ID   = "moviflexx_alertas_viaje";
    private static final String CHANNEL_NAME = "Alertas de salida";

    // ── Mapas de handlers y runnables por viaje ───────────────────────────────
    // Cada viaje puede tener hasta 3 runnables: alerta5min, alerta1min, inicio
    private static final Map<Integer, Handler>  handlers5min   = new HashMap<>();
    private static final Map<Integer, Runnable> runnables5min  = new HashMap<>();

    private static final Map<Integer, Handler>  handlers1min   = new HashMap<>();
    private static final Map<Integer, Runnable> runnables1min  = new HashMap<>();

    private static final Map<Integer, Handler>  handlersInicio  = new HashMap<>();
    private static final Map<Integer, Runnable> runnablesInicio = new HashMap<>();

    // ── IDs de notificación (únicos por viaje) ────────────────────────────────
    private static int notifId5min(int viajeId)   { return viajeId * 10 + 1; }
    private static int notifId1min(int viajeId)   { return viajeId * 10 + 2; }
    private static int notifIdInicio(int viajeId) { return viajeId * 10 + 3; }

    // =========================================================================
    //  API PÚBLICA
    // =========================================================================

    /**
     * Programa el inicio automático del viaje a la hora exacta de salida
     * Y además programa notificaciones 5 min y 1 min antes para el conductor.
     *
     * Este método reemplaza la versión anterior manteniendo la misma firma.
     *
     * @param context          Contexto de la Activity/Service
     * @param idViaje          ID del viaje a monitorear
     * @param fechaHoraSalida  ISO-8601 o "yyyy-MM-dd HH:mm:ss" / "yyyy-MM-ddTHH:mm:ss"
     * @param datosViaje       JSONObject con los datos del viaje (coords, nombres, etc.)
     */
    public static void programarInicioAutomatico(Context context, int idViaje,
                                                 String fechaHoraSalida,
                                                 JSONObject datosViaje) {
        // Cancelar cualquier tarea previa para este viaje
        cancelarAlertas(idViaje);

        long ahora      = System.currentTimeMillis();
        long horaSalida = parsearFecha(fechaHoraSalida);
        long demoraTotal = horaSalida - ahora;

        // Extraer origen y destino del JSON para los mensajes de notificación
        String origen  = extraerOrigen(datosViaje);
        String destino = extraerDestino(datosViaje);

        if (demoraTotal <= 0) {
            Log.d(TAG, "Viaje #" + idViaje + " ya debería haber salido — iniciando ahora");
            // Ejecutar inicio inmediato sin notificaciones previas
            ejecutarInicioViaje(context, idViaje, datosViaje);
            return;
        }

        Log.d(TAG, "Viaje #" + idViaje + " inicia en " + (demoraTotal / 60000) + " min ("
                + new Date(horaSalida) + ")");

        // ── Notificación 5 minutos antes ──────────────────────────────────────
        long demora5min = demoraTotal - 5 * 60 * 1000L;
        if (demora5min > 0) {
            Handler  h5 = new Handler(Looper.getMainLooper());
            Runnable r5 = () -> mostrarNotificacion(context, idViaje,
                    "⏰ Tu viaje sale en 5 minutos",
                    "Prepárate — ruta hacia " + destino,
                    notifId5min(idViaje));
            handlers5min.put(idViaje,  h5);
            runnables5min.put(idViaje, r5);
            h5.postDelayed(r5, demora5min);
            Log.d(TAG, "  → Alerta 5min programada en " + (demora5min / 60000) + " min");
        } else {
            Log.d(TAG, "  → Alerta 5min omitida (viaje en menos de 5 min)");
        }

        // ── Notificación 1 minuto antes ───────────────────────────────────────
        long demora1min = demoraTotal - 60_000L;
        if (demora1min > 0) {
            Handler  h1 = new Handler(Looper.getMainLooper());
            Runnable r1 = () -> mostrarNotificacion(context, idViaje,
                    "🚨 ¡1 minuto para salir!",
                    "Tu viaje hacia " + destino + " sale ya. ¡Arranca!",
                    notifId1min(idViaje));
            handlers1min.put(idViaje,  h1);
            runnables1min.put(idViaje, r1);
            h1.postDelayed(r1, demora1min);
            Log.d(TAG, "  → Alerta 1min programada en " + (demora1min / 1000) + " seg");
        } else {
            Log.d(TAG, "  → Alerta 1min omitida (viaje en menos de 1 min)");
        }

        // ── Inicio del viaje a la hora exacta ────────────────────────────────
        Handler  hInicio = new Handler(Looper.getMainLooper());
        Runnable rInicio = () -> {
            mostrarNotificacion(context, idViaje,
                    "🚗 ¡Es hora de iniciar el viaje!",
                    "Tus pasajeros esperan. " + origen + " → " + destino,
                    notifIdInicio(idViaje));
            ejecutarInicioViaje(context, idViaje, datosViaje);
        };
        handlersInicio.put(idViaje,  hInicio);
        runnablesInicio.put(idViaje, rInicio);
        hInicio.postDelayed(rInicio, demoraTotal);
        Log.d(TAG, "  → Inicio automático programado en " + (demoraTotal / 60000) + " min");
    }

    /**
     * Programa SOLO las notificaciones de alerta (5min, 1min, inicio)
     * SIN iniciar el viaje automáticamente en el backend.
     *
     * Úsalo cuando el conductor inicia manualmente pero quieres avisarle
     * con tiempo de que se acerca la hora de salida.
     *
     * @param context          Contexto
     * @param idViaje          ID del viaje
     * @param fechaHoraSalida  Fecha/hora de salida del viaje
     * @param origen           Texto del origen (para el mensaje de notificación)
     * @param destino          Texto del destino (para el mensaje de notificación)
     */
    public static void programarAlertas(Context context, int idViaje,
                                        String fechaHoraSalida,
                                        String origen, String destino) {
        // Cancelar alertas previas
        cancelarAlertas(idViaje);

        long ahora       = System.currentTimeMillis();
        long horaSalida  = parsearFecha(fechaHoraSalida);
        long demoraTotal = horaSalida - ahora;

        if (demoraTotal <= 0) {
            Log.d(TAG, "programarAlertas: viaje #" + idViaje + " ya pasó su hora — no se programan alertas");
            return;
        }

        String origenTxt  = (origen  != null && !origen.isEmpty())  ? origen  : "Origen";
        String destinoTxt = (destino != null && !destino.isEmpty()) ? destino : "Destino";

        Log.d(TAG, "programarAlertas viaje #" + idViaje + " — salida en "
                + (demoraTotal / 60000) + " min");

        // ── 5 minutos antes ───────────────────────────────────────────────────
        long demora5min = demoraTotal - 5 * 60 * 1000L;
        if (demora5min > 0) {
            Handler  h5 = new Handler(Looper.getMainLooper());
            Runnable r5 = () -> mostrarNotificacion(context, idViaje,
                    "⏰ Tu viaje sale en 5 minutos",
                    "Prepárate — ruta hacia " + destinoTxt,
                    notifId5min(idViaje));
            handlers5min.put(idViaje,  h5);
            runnables5min.put(idViaje, r5);
            h5.postDelayed(r5, demora5min);
        }

        // ── 1 minuto antes ────────────────────────────────────────────────────
        long demora1min = demoraTotal - 60_000L;
        if (demora1min > 0) {
            Handler  h1 = new Handler(Looper.getMainLooper());
            Runnable r1 = () -> mostrarNotificacion(context, idViaje,
                    "🚨 ¡1 minuto para salir!",
                    "Tu viaje hacia " + destinoTxt + " sale ya. ¡Arranca!",
                    notifId1min(idViaje));
            handlers1min.put(idViaje,  h1);
            runnables1min.put(idViaje, r1);
            h1.postDelayed(r1, demora1min);
        }

        // ── Hora exacta de salida — solo notificación, NO inicia backend ──────
        Handler  hInicio = new Handler(Looper.getMainLooper());
        Runnable rInicio = () -> mostrarNotificacion(context, idViaje,
                "🚗 ¡Es hora de iniciar el viaje!",
                "Tus pasajeros esperan. " + origenTxt + " → " + destinoTxt,
                notifIdInicio(idViaje));
        handlersInicio.put(idViaje,  hInicio);
        runnablesInicio.put(idViaje, rInicio);
        hInicio.postDelayed(rInicio, demoraTotal);
    }

    /**
     * Cancela TODAS las alertas programadas para el viaje indicado.
     * Llamar cuando el conductor presiona "Iniciar" manualmente o cancela el viaje.
     */
    public static void cancelarAlertas(int idViaje) {
        cancelar(handlers5min,   runnables5min,   idViaje, "5min");
        cancelar(handlers1min,   runnables1min,   idViaje, "1min");
        cancelar(handlersInicio, runnablesInicio, idViaje, "inicio");
    }

    /**
     * Parsea una fecha/hora en varios formatos ISO y devuelve milisegundos epoch.
     * Método público para que ViajesAdapter pueda usarlo en la cuenta regresiva.
     */
    public static long parsearFechaPublic(String fecha) {
        return parsearFecha(fecha);
    }

    // =========================================================================
    //  LÓGICA INTERNA — inicio del viaje
    // =========================================================================

    private static void ejecutarInicioViaje(Context context, int idViaje, JSONObject datosViaje) {
        Log.d(TAG, "⏰ Hora de salida alcanzada — iniciando viaje #" + idViaje);

        ConexionApi.getInstance(context).post(
                Constantes.viajeIniciar((long) idViaje), null,
                response -> {
                    Log.d(TAG, "Viaje #" + idViaje + " iniciado en backend");
                    lanzarMapaConductor(context, idViaje, datosViaje);
                },
                error -> {
                    Log.w(TAG, "Error al iniciar viaje #" + idViaje + " en backend — lanzando mapa igual");
                    lanzarMapaConductor(context, idViaje, datosViaje);
                }
        );
    }

    private static void lanzarMapaConductor(Context context, int idViaje, JSONObject v) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Intent intent = new Intent(context, com.arlys.moviflexx.controller.Mapa.class);
            intent.putExtra("ID_VIAJE",    idViaje);
            intent.putExtra("DESDE_VIAJE", true);

            if (v != null) {
                JSONObject ruta = v.optJSONObject("ruta");
                JSONObject src  = ruta != null ? ruta : v;

                double latO = primerDouble(src, "latOrigen", "latitudOrigen", "latInicio");
                double lngO = primerDouble(src, "lngOrigen", "longitudOrigen", "lngInicio");
                double latD = primerDouble(src, "latDestino", "latitudDestino", "latFin");
                double lngD = primerDouble(src, "lngDestino", "longitudDestino", "lngFin");

                if (latO != 0) { intent.putExtra("ORIGEN_LAT",  latO); intent.putExtra("ORIGEN_LNG",  lngO); }
                if (latD != 0) { intent.putExtra("DESTINO_LAT", latD); intent.putExtra("DESTINO_LNG", lngD); }

                String nomSubida = "", nomBajada = "";
                if (ruta != null) {
                    String nom = ruta.optString("nombre", "");
                    if (nom.contains("→")) {
                        String[] p = nom.split("→", 2);
                        nomSubida = p[0].trim();
                        nomBajada = p[1].trim();
                    }
                }
                intent.putExtra("NOM_SUBIDA", nomSubida);
                intent.putExtra("NOM_BAJADA", nomBajada);

                String paradas = v.optString("PARADAS_JSON", "");
                if (!paradas.isEmpty()) intent.putExtra("PARADAS_JSON", paradas);
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        });
    }

    // =========================================================================
    //  NOTIFICACIÓN LOCAL
    // =========================================================================

    private static void mostrarNotificacion(Context context, int viajeId,
                                            String titulo, String mensaje, int notifId) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        // Crear canal (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            canal.setDescription("Avisos de salida de tus viajes");
            canal.enableVibration(true);
            canal.enableLights(true);
            manager.createNotificationChannel(canal);
        }

        // Al tocar la notificación → abrir pantalla de Notificaciones
        Intent tapIntent = new Intent(context, Notificaciones.class);
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, notifId, tapIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_directions_car)
                        .setContentTitle(titulo)
                        .setContentText(mensaje)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setAutoCancel(true)
                        .setContentIntent(pi);

        manager.notify(notifId, builder.build());
        Log.d(TAG, "Notificación mostrada [" + notifId + "]: " + titulo);
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================

    private static void cancelar(Map<Integer, Handler>  hs,
                                 Map<Integer, Runnable> rs,
                                 int idViaje, String tipo) {
        Handler  h = hs.remove(idViaje);
        Runnable r = rs.remove(idViaje);
        if (h != null && r != null) {
            h.removeCallbacks(r);
            Log.d(TAG, "Alerta " + tipo + " cancelada para viaje #" + idViaje);
        }
    }

    private static long parsearFecha(String fecha) {
        if (fecha == null || fecha.isEmpty()) return System.currentTimeMillis();
        String[] formatos = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm"
        };
        for (String fmt : formatos) {
            try {
                Date d = new SimpleDateFormat(fmt, Locale.getDefault()).parse(fecha);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        Log.w(TAG, "No se pudo parsear la fecha: " + fecha);
        return System.currentTimeMillis();
    }

    private static String extraerOrigen(JSONObject v) {
        if (v == null) return "Origen";
        JSONObject ruta = v.optJSONObject("ruta");
        if (ruta != null) {
            String o = ruta.optString("origen", ruta.optString("nombre", ""));
            if (!o.isEmpty() && o.contains("→")) return o.split("→")[0].trim();
            if (!o.isEmpty()) return o;
        }
        return v.optString("origen", "Origen");
    }

    private static String extraerDestino(JSONObject v) {
        if (v == null) return "Destino";
        JSONObject ruta = v.optJSONObject("ruta");
        if (ruta != null) {
            String nom = ruta.optString("nombre", "");
            if (nom.contains("→")) return nom.split("→")[1].trim();
            String desc = ruta.optString("descripcion", "");
            if (!desc.isEmpty()) return desc;
        }
        return v.optString("destino", "Destino");
    }

    private static double primerDouble(JSONObject obj, String... campos) {
        if (obj == null) return 0;
        for (String c : campos) {
            double val = obj.optDouble(c, 0);
            if (val != 0) return val;
        }
        return 0;
    }
}