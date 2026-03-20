package com.arlys.moviflexx.controller;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID    = "moviflexx_notif";
    private static final String CHANNEL_NAME  = "Notificaciones MoviFlexx";

    // ─────────────────────────────────────────────────────────
    //  Se llama cada vez que llega una notificación push
    // ─────────────────────────────────────────────────────────
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String titulo  = "MoviFlexx";
        String mensaje = "";

        // Notificación normal (app en background → el sistema la muestra solo;
        // app en foreground → la recibimos aquí y la mostramos manualmente)
        if (remoteMessage.getNotification() != null) {
            String t = remoteMessage.getNotification().getTitle();
            String b = remoteMessage.getNotification().getBody();
            if (t != null && !t.isEmpty()) titulo  = t;
            if (b != null && !b.isEmpty()) mensaje = b;
        }

        // Data payload (siempre llega aquí sin importar si la app está abierta o no)
        if (!remoteMessage.getData().isEmpty()) {
            if (remoteMessage.getData().containsKey("titulo"))
                titulo  = remoteMessage.getData().get("titulo");
            if (remoteMessage.getData().containsKey("mensaje"))
                mensaje = remoteMessage.getData().get("mensaje");
        }

        mostrarNotificacion(titulo, mensaje, remoteMessage.getData().get("tipo"));
    }

    // ─────────────────────────────────────────────────────────
    //  Se llama cuando Firebase genera un nuevo token para este
    //  dispositivo (primera instalación o token renovado)
    // ─────────────────────────────────────────────────────────
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        enviarTokenAlBackend(token);
    }

    // ─────────────────────────────────────────────────────────
    //  Construye y lanza la notificación en la barra de estado
    // ─────────────────────────────────────────────────────────
    private void mostrarNotificacion(String titulo, String mensaje, String tipo) {
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Canal obligatorio en Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            canal.setDescription("Avisos de viajes, mensajes y pagos");
            canal.enableVibration(true);
            manager.createNotificationChannel(canal);
        }

        // Al tocar la notificación abre Notificaciones.class
        Intent intent = new Intent(this, Notificaciones.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // Elegir icono según tipo
        int icono = iconoPorTipo(tipo);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(icono)
                        .setContentTitle(titulo)
                        .setContentText(mensaje)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setContentIntent(pendingIntent);

        // ID único por tiempo para que no se sobreescriban
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    // ─────────────────────────────────────────────────────────
    //  Envía el token FCM al backend para guardarlo por usuario
    // ─────────────────────────────────────────────────────────
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
                    response -> { /* token guardado */ },
                    error    -> { /* silencioso     */ }
            );
        } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────────────────
    //  Icono según el tipo de notificación
    // ─────────────────────────────────────────────────────────
    private int iconoPorTipo(String tipo) {
        if (tipo == null) return R.drawable.ic_notifications;
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