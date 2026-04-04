package com.arlys.moviflexx.controller;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_SERVICE";

    private static final String CHANNEL_MENSAJES = "moviflexx_mensajes";
    private static final String CHANNEL_VIAJES   = "moviflexx_viajes";
    private static final String CHANNEL_PAGOS    = "moviflexx_pagos";
    private static final String CHANNEL_SISTEMA  = "moviflexx_sistema";

    public static final String ACTION_NUEVA_NOTIF =
            "com.arlys.moviflexx.NUEVA_NOTIFICACION";

    // ═══════════════════════════════════════════════════════
    //  onMessageReceived
    // ═══════════════════════════════════════════════════════

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "📩 Push recibido de: " + remoteMessage.getFrom());

        String titulo  = "MoviFlexx";
        String mensaje = "";
        String tipo    = null;
        long   idConversacion = -1;

        if (remoteMessage.getNotification() != null) {
            String t = remoteMessage.getNotification().getTitle();
            String b = remoteMessage.getNotification().getBody();
            if (t != null && !t.isEmpty()) titulo  = t;
            if (b != null && !b.isEmpty()) mensaje = b;
            Log.d(TAG, "  [notification block] titulo=" + titulo + " | body=" + mensaje);
        }

        if (!remoteMessage.getData().isEmpty()) {
            Log.d(TAG, "  [data block] " + remoteMessage.getData().toString());

            if (remoteMessage.getData().containsKey("titulo"))
                titulo = remoteMessage.getData().get("titulo");
            if (remoteMessage.getData().containsKey("mensaje"))
                mensaje = remoteMessage.getData().get("mensaje");
            if ((mensaje == null || mensaje.isEmpty())
                    && remoteMessage.getData().containsKey("cuerpo"))
                mensaje = remoteMessage.getData().get("cuerpo");
            if ((mensaje == null || mensaje.isEmpty())
                    && remoteMessage.getData().containsKey("body"))
                mensaje = remoteMessage.getData().get("body");

            tipo = remoteMessage.getData().get("tipo");

            // Extraer idConversacion si viene en el payload
            try {
                if (remoteMessage.getData().containsKey("idConversacion"))
                    idConversacion = Long.parseLong(
                            remoteMessage.getData().get("idConversacion"));
                else if (remoteMessage.getData().containsKey("conversacionId"))
                    idConversacion = Long.parseLong(
                            remoteMessage.getData().get("conversacionId"));
            } catch (NumberFormatException ignored) {}
        }

        Log.d(TAG, "  → tipo=" + tipo + " | titulo=" + titulo
                + " | mensaje=" + mensaje + " | idConv=" + idConversacion);

        if (tipo != null) {
            switch (tipo.toUpperCase()) {

                case "MENSAJE":
                case "CHAT":
                    mostrarNotificacionMensaje(titulo, mensaje, idConversacion);
                    break;

                case "RESERVA":
                    String nombrePasajero = remoteMessage.getData().containsKey("nombrePasajero")
                            ? remoteMessage.getData().get("nombrePasajero") : "Un pasajero";
                    String origenReserva  = remoteMessage.getData().containsKey("origen")
                            ? remoteMessage.getData().get("origen")  : "Origen";
                    String destinoReserva = remoteMessage.getData().containsKey("destino")
                            ? remoteMessage.getData().get("destino") : "Destino";
                    PublicarViaje.mostrarNotificacionReserva(
                            this, nombrePasajero, origenReserva, destinoReserva);
                    break;

                case "ALERTA_SALIDA":
                    int    minutosRestantes = 5;
                    int    idViaje          = 0;
                    String origenAlerta     = "Origen";
                    String destinoAlerta    = "Destino";
                    try {
                        if (remoteMessage.getData().containsKey("minutosRestantes"))
                            minutosRestantes = Integer.parseInt(
                                    remoteMessage.getData().get("minutosRestantes"));
                        if (remoteMessage.getData().containsKey("idViaje"))
                            idViaje = Integer.parseInt(
                                    remoteMessage.getData().get("idViaje"));
                        if (remoteMessage.getData().containsKey("origen"))
                            origenAlerta  = remoteMessage.getData().get("origen");
                        if (remoteMessage.getData().containsKey("destino"))
                            destinoAlerta = remoteMessage.getData().get("destino");
                    } catch (NumberFormatException ignored) {}
                    PublicarViaje.mostrarAlertaAntesDePartir(
                            this, minutosRestantes, origenAlerta, destinoAlerta, idViaje);
                    break;

                default:
                    mostrarNotificacion(titulo, mensaje, tipo, -1);
                    break;
            }
        } else {
            mostrarNotificacion(titulo, mensaje, null, -1);
        }

        // Broadcast para avisar a Notificaciones.java si está abierta
        Intent broadcast = new Intent(ACTION_NUEVA_NOTIF);
        broadcast.putExtra("titulo",  titulo);
        broadcast.putExtra("mensaje", mensaje);
        broadcast.putExtra("tipo",    tipo != null ? tipo : "SISTEMA");
        broadcast.putExtra("idConversacion", idConversacion);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    // ═══════════════════════════════════════════════════════
    //  onNewToken
    // ═══════════════════════════════════════════════════════

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "🔑 Nuevo token FCM: "
                + token.substring(0, Math.min(20, token.length())) + "...");

        getSharedPreferences("fcm_prefs", MODE_PRIVATE)
                .edit()
                .putString("pending_token", token)
                .apply();

        enviarTokenAlBackend(token);
    }

    // ═══════════════════════════════════════════════════════
    //  NOTIFICACIÓN DE MENSAJE — abre el chat directamente
    // ═══════════════════════════════════════════════════════

    private void mostrarNotificacionMensaje(String titulo, String mensaje,
                                            long idConversacion) {
        if (mensaje == null || mensaje.isEmpty()) {
            Log.w(TAG, "⚠️ Notificación mensaje vacía — omitida");
            return;
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CHANNEL_MENSAJES,
                    "Mensajes",
                    NotificationManager.IMPORTANCE_HIGH);
            canal.setDescription("Mensajes de chat");
            canal.enableVibration(true);
            canal.setVibrationPattern(new long[]{0, 250, 100, 250});
            canal.enableLights(true);
            canal.setLightColor(android.graphics.Color.parseColor("#0ABFA3"));
            canal.setShowBadge(true);
            manager.createNotificationChannel(canal);
        }

        // Si tenemos el ID de conversación, abrir el chat directamente
        Intent intent;
        if (idConversacion > 0) {
            intent = new Intent(this, Chat.class);
            intent.putExtra("idConversacion", idConversacion);
        } else {
            // Sin ID, abrir la lista de conversaciones
            intent = new Intent(this, Mensajes.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_MENSAJES)
                        .setSmallIcon(R.drawable.logomo)
                        .setContentTitle(titulo)
                        .setContentText(mensaje)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setContentIntent(pendingIntent)
                        .setColor(android.graphics.Color.parseColor("#0ABFA3"))
                        .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL);

        int notifId = (int) System.currentTimeMillis();
        manager.notify(notifId, builder.build());

        Log.d(TAG, "✅ Notificación mensaje — id=" + notifId
                + " | conv=" + idConversacion + " | " + titulo + ": " + mensaje);
    }

    // ═══════════════════════════════════════════════════════
    //  NOTIFICACIÓN GENÉRICA — abre Notificaciones.java
    // ═══════════════════════════════════════════════════════

    private void mostrarNotificacion(String titulo, String mensaje,
                                     String tipo, long idReferencia) {
        if (mensaje == null || mensaje.isEmpty()) {
            Log.w(TAG, "⚠️ Notificación con cuerpo vacío — omitida");
            return;
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;

        String channelId   = obtenerChannelId(tipo);
        String channelName = obtenerChannelName(tipo);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH);
            canal.setDescription("Avisos de " + channelName.toLowerCase());
            canal.enableVibration(true);
            canal.setVibrationPattern(new long[]{0, 250, 100, 250});
            canal.enableLights(true);
            canal.setLightColor(android.graphics.Color.parseColor("#0ABFA3"));
            canal.setShowBadge(true);
            manager.createNotificationChannel(canal);
        }

        Intent intent = new Intent(this, Notificaciones.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (idReferencia > 0)
            intent.putExtra("idReferencia", idReferencia);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.logomo)
                        .setContentTitle(titulo)
                        .setContentText(mensaje)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setContentIntent(pendingIntent)
                        .setColor(android.graphics.Color.parseColor("#0ABFA3"))
                        .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL);

        int notifId = (int) System.currentTimeMillis();
        manager.notify(notifId, builder.build());

        Log.d(TAG, "✅ Notificación mostrada — id=" + notifId
                + " | " + titulo + ": " + mensaje);
    }

    // ═══════════════════════════════════════════════════════
    //  ENVIAR TOKEN FCM AL BACKEND
    // ═══════════════════════════════════════════════════════

    public void enviarTokenAlBackend(String token) {
        SessionManager session   = new SessionManager(this);
        int            idUsuario = session.getIdUsuario();
        if (idUsuario <= 0) {
            Log.w(TAG, "⚠️ No hay sesión activa — token guardado localmente");
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("token",     token);
            body.put("idUsuario", idUsuario);

            String url = Constantes.BASE_URL + "/api/usuarios/" + idUsuario + "/fcm-token";
            Log.d(TAG, "📤 Enviando token FCM → " + url);

            ConexionApi.getInstance(this).post(url, body,
                    response -> Log.d(TAG, "✅ Token FCM guardado en backend"),
                    error    -> Log.e(TAG, "❌ Error guardando token: " + error.toString()));
        } catch (Exception e) {
            Log.e(TAG, "❌ Excepción en enviarTokenAlBackend: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════

    private String obtenerChannelId(String tipo) {
        if (tipo == null) return CHANNEL_SISTEMA;
        switch (tipo.toUpperCase()) {
            case "MENSAJE":
            case "CHAT":    return CHANNEL_MENSAJES;
            case "VIAJE":
            case "RESERVA": return CHANNEL_VIAJES;
            case "PAGO":    return CHANNEL_PAGOS;
            default:        return CHANNEL_SISTEMA;
        }
    }

    private String obtenerChannelName(String tipo) {
        if (tipo == null) return "Sistema";
        switch (tipo.toUpperCase()) {
            case "MENSAJE":
            case "CHAT":    return "Mensajes";
            case "VIAJE":
            case "RESERVA": return "Viajes y reservas";
            case "PAGO":    return "Pagos";
            default:        return "Sistema";
        }
    }
}