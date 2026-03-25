package com.arlys.moviflexx.controller;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

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

    private static final String CHANNEL_ID   = "moviflexx_notif";
    private static final String CHANNEL_NAME = "Notificaciones MoviFlexx";

    // Acción del broadcast — la escucha Notificaciones.java
    public static final String ACTION_NUEVA_NOTIF = "com.arlys.moviflexx.NUEVA_NOTIFICACION";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String titulo  = "MoviFlexx";
        String mensaje = "";
        String tipo    = null;

        if (remoteMessage.getNotification() != null) {
            String t = remoteMessage.getNotification().getTitle();
            String b = remoteMessage.getNotification().getBody();
            if (t != null && !t.isEmpty()) titulo  = t;
            if (b != null && !b.isEmpty()) mensaje = b;
        }

        if (!remoteMessage.getData().isEmpty()) {
            if (remoteMessage.getData().containsKey("titulo"))
                titulo  = remoteMessage.getData().get("titulo");
            if (remoteMessage.getData().containsKey("mensaje"))
                mensaje = remoteMessage.getData().get("mensaje");
            tipo = remoteMessage.getData().get("tipo");
        }

        // 1. Mostrar la notificación en la barra de estado
        mostrarNotificacion(titulo, mensaje, tipo);

        // 2. Avisar a Notificaciones.java si está abierta para que recargue
        Intent broadcast = new Intent(ACTION_NUEVA_NOTIF);
        broadcast.putExtra("titulo",  titulo);
        broadcast.putExtra("mensaje", mensaje);
        broadcast.putExtra("tipo",    tipo != null ? tipo : "SISTEMA");
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        enviarTokenAlBackend(token);
    }

    private void mostrarNotificacion(String titulo, String mensaje, String tipo) {
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            canal.setDescription("Avisos de viajes, mensajes y pagos");
            canal.enableVibration(true);
            manager.createNotificationChannel(canal);
        }

        Intent intent = new Intent(this, Notificaciones.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(iconoPorTipo(tipo))
                        .setContentTitle(titulo)
                        .setContentText(mensaje)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void enviarTokenAlBackend(String token) {
        SessionManager session   = new SessionManager(this);
        int            idUsuario = session.getIdUsuario();
        if (idUsuario <= 0) return;

        try {
            JSONObject body = new JSONObject();
            body.put("token",     token);
            body.put("idUsuario", idUsuario);

            ConexionApi.getInstance(this).post(
                    Constantes.BASE_URL + "/api/usuarios/" + idUsuario + "/fcm-token",
                    body,
                    response -> { },
                    error    -> { }
            );
        } catch (Exception ignored) {}
    }

    private int iconoPorTipo(String tipo) {
        if (tipo == null) return R.drawable.logomo;
        switch (tipo.toUpperCase()) {
            case "MENSAJE":
            case "CHAT":    return R.drawable.ic_chat;
            case "VIAJE":   return R.drawable.ic_directions_car;
            case "RESERVA": return R.drawable.ic_description;
            case "PAGO":    return R.drawable.ic_credit_card;
            default:        return R.drawable.ic_notifications;
        }
    }
}