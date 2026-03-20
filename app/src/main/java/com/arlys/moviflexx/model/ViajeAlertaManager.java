package com.arlys.moviflexx.model;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.arlys.moviflexx.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ViajeAlertaManager.java
public class ViajeAlertaManager {

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Map<Integer, List<Runnable>> jobsActivos = new HashMap<>();

    public static void programarAlertas(Context context, int viajeId, String fechaHoraSalida) {
        cancelarAlertas(viajeId); // limpiar si ya existían

        Date fechaSalida = parsearFecha(fechaHoraSalida);
        if (fechaSalida == null) return;

        long ahora        = System.currentTimeMillis();
        long tSalida      = fechaSalida.getTime();
        long delay5min    = tSalida - ahora - (5 * 60 * 1000);
        long delay1min    = tSalida - ahora - (1 * 60 * 1000);
        long delayInicio  = tSalida - ahora;

        List<Runnable> jobs = new ArrayList<>();

        if (delay5min > 0) {
            Runnable job5 = () -> mostrarAlerta(context, viajeId,
                    "Tu viaje inicia en 5 minutos", "Prepárate para salir");
            handler.postDelayed(job5, delay5min);
            jobs.add(job5);
        }

        if (delay1min > 0) {
            Runnable job1 = () -> mostrarAlerta(context, viajeId,
                    "Tu viaje inicia en 1 minuto", "¡Es la hora de partir!");
            handler.postDelayed(job1, delay1min);
            jobs.add(job1);
        }

        if (delayInicio > 0) {
            Runnable jobAuto = () -> iniciarViajeAutomatico(context, viajeId);
            handler.postDelayed(jobAuto, delayInicio);
            jobs.add(jobAuto);
        } else if (delayInicio <= 0 && delayInicio > -60_000) {
            // Llegó la hora mientras la app estaba cerrada, iniciar ya
            iniciarViajeAutomatico(context, viajeId);
        }

        jobsActivos.put(viajeId, jobs);
    }

    public static void cancelarAlertas(int viajeId) {
        List<Runnable> jobs = jobsActivos.remove(viajeId);
        if (jobs != null) for (Runnable r : jobs) handler.removeCallbacks(r);
    }

    private static void mostrarAlerta(Context context, int viajeId, String titulo, String subtitulo) {
        Activity act = obtenerActivity(context);
        if (act == null || act.isFinishing()) {
            enviarNotificacionPush(context, titulo, subtitulo);
            return;
        }
        act.runOnUiThread(() -> {
            enviarNotificacionPush(context, titulo, subtitulo);
            new AlertDialog.Builder(context)
                    .setTitle(titulo)
                    .setMessage(subtitulo)
                    .setPositiveButton("Entendido", null)
                    .setCancelable(true)
                    .show();
        });
    }

    private static void iniciarViajeAutomatico(Context context, int viajeId) {
        ConexionApi.getInstance(context).getObjectNoCache(
                Constantes.viajePorId((long) viajeId),
                viaje -> {
                    String estado = viaje.optString("estado", "").toUpperCase();
                    if ("DISPONIBLE".equals(estado) || "PROGRAMADO".equals(estado)
                            || "CREADO".equals(estado)) {
                        ConexionApi.getInstance(context).post(
                                Constantes.viajeIniciar((long) viajeId), null,
                                resp -> {
                                    notificarPasajeros(context, viajeId);
                                    new Handler(Looper.getMainLooper()).post(() ->
                                            Toast.makeText(context,
                                                    "Viaje iniciado automáticamente",
                                                    Toast.LENGTH_SHORT).show());
                                },
                                err -> Log.e("ViajeAlerta", "Error inicio auto viaje " + viajeId)
                        );
                    }
                },
                err -> Log.e("ViajeAlerta", "No se pudo consultar estado viaje " + viajeId)
        );
    }

    private static void notificarPasajeros(Context context, int viajeId) {
        ConexionApi.getInstance(context).getObjectNoCache(
                Constantes.viajePorId((long) viajeId),
                viaje -> {
                    JSONArray usuarios = viaje.optJSONArray("usuarios");
                    if (usuarios == null) return;
                    for (int i = 0; i < usuarios.length(); i++) {
                        JSONObject u = usuarios.optJSONObject(i);
                        if (u == null) continue;
                        String est = u.optString("estado", "").toUpperCase();
                        if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;
                        JSONObject usuObj = u.optJSONObject("usuario");
                        if (usuObj == null) continue;
                        String token = usuObj.optString("fcmToken",
                                usuObj.optString("tokenFCM", ""));
                        if (!token.isEmpty()) {
                            enviarPushPasajero(context, token,
                                    "El conductor ha iniciado el viaje",
                                    "Prepárate en tu punto de recogida");
                        }
                    }
                },
                err -> {}
        );
    }

    private static void enviarNotificacionPush(Context context, String titulo, String cuerpo) {
        // Notificación local mientras la app está en primer plano
        android.app.NotificationManager nm =
                (android.app.NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "viaje_alertas";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    channelId, "Alertas de viaje",
                    android.app.NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        android.app.Notification notif = new androidx.core.app.NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_directions_car)
                .setContentTitle(titulo)
                .setContentText(cuerpo)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();
        nm.notify((int) System.currentTimeMillis(), notif);
    }

    private static void enviarPushPasajero(Context context, String token,
                                           String titulo, String cuerpo) {
        // Llama a tu backend para que envíe el push vía FCM server-side
        try {
            JSONObject body = new JSONObject();
            body.put("token", token);
            body.put("titulo", titulo);
            body.put("cuerpo", cuerpo);
            ConexionApi.getInstance(context).post(
                    Constantes.BASE_URL + "/api/notificaciones/push", body,
                    r -> {}, e -> {});
        } catch (Exception ignored) {}
    }

    private static Date parsearFecha(String raw) {
        String[] formatos = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"};
        for (String fmt : formatos) {
            try { return new java.text.SimpleDateFormat(fmt, Locale.getDefault()).parse(raw); }
            catch (Exception ignored) {}
        }
        return null;
    }

    private static Activity obtenerActivity(Context ctx) {
        if (ctx instanceof Activity) return (Activity) ctx;
        if (ctx instanceof android.content.ContextWrapper)
            return obtenerActivity(((android.content.ContextWrapper) ctx).getBaseContext());
        return null;
    }
}