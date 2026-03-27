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

        // ── Leer bloque notification (FCM desde consola) ──────────────────────
        if (remoteMessage.getNotification() != null) {
            String t = remoteMessage.getNotification().getTitle();
            String b = remoteMessage.getNotification().getBody();
            if (t != null && !t.isEmpty()) titulo  = t;
            if (b != null && !b.isEmpty()) mensaje = b;
        }

        // ── Leer bloque data (FCM desde backend) ──────────────────────────────
        if (!remoteMessage.getData().isEmpty()) {
            if (remoteMessage.getData().containsKey("titulo"))
                titulo  = remoteMessage.getData().get("titulo");
            if (remoteMessage.getData().containsKey("mensaje"))
                mensaje = remoteMessage.getData().get("mensaje");
            tipo = remoteMessage.getData().get("tipo");
        }

        // ── 1. Despachar notificación especializada según tipo ────────────────
        if (tipo != null) {
            switch (tipo.toUpperCase()) {

                case "RESERVA":
                    // El backend debe enviar en el payload: nombrePasajero, origen, destino
                    String nombrePasajero = remoteMessage.getData().containsKey("nombrePasajero")
                            ? remoteMessage.getData().get("nombrePasajero") : "Un pasajero";
                    String origenReserva  = remoteMessage.getData().containsKey("origen")
                            ? remoteMessage.getData().get("origen")  : "Origen";
                    String destinoReserva = remoteMessage.getData().containsKey("destino")
                            ? remoteMessage.getData().get("destino") : "Destino";

                    // Mostrar notificación enriquecida de reserva
                    PublicarViaje.mostrarNotificacionReserva(
                            this, nombrePasajero, origenReserva, destinoReserva);
                    break;

                case "ALERTA_SALIDA":
                    // El backend debe enviar: minutosRestantes, origen, destino, idViaje
                    int minutosRestantes = 5;
                    int idViaje          = 0;
                    String origenAlerta  = "Origen";
                    String destinoAlerta = "Destino";

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

                    // Mostrar alerta de salida
                    PublicarViaje.mostrarAlertaAntesDePartir(
                            this, minutosRestantes, origenAlerta, destinoAlerta, idViaje);
                    break;

                default:
                    // Para cualquier otro tipo usar la notificación genérica
                    mostrarNotificacion(titulo, mensaje, tipo);
                    break;
            }
        } else {
            // Sin tipo → notificación genérica
            mostrarNotificacion(titulo, mensaje, null);
        }

        // ── 2. Avisar a Notificaciones.java si está abierta para que recargue ─
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

    /* ════════════════════════════════════════════════════════════════════════
       NOTIFICACIÓN GENÉRICA (barra de estado)
    ════════════════════════════════════════════════════════════════════════ */

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

    /* ════════════════════════════════════════════════════════════════════════
       ENVIAR TOKEN FCM AL BACKEND
    ════════════════════════════════════════════════════════════════════════ */

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

    /* ════════════════════════════════════════════════════════════════════════
       ÍCONO POR TIPO
    ════════════════════════════════════════════════════════════════════════ */

    private int iconoPorTipo(String tipo) {
        if (tipo == null) return R.drawable.logomo;
        switch (tipo.toUpperCase()) {
            case "MENSAJE":
            case "CHAT":         return R.drawable.ic_chat;
            case "VIAJE":        return R.drawable.ic_directions_car;
            case "RESERVA":      return R.drawable.ic_description;
            case "PAGO":         return R.drawable.ic_credit_card;
            case "ALERTA_SALIDA":return R.drawable.ic_directions_car;
            default:             return R.drawable.ic_notifications;
        }
    }
}