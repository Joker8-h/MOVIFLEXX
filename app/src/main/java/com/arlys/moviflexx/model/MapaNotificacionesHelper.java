package com.arlys.moviflexx.model;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.arlys.moviflexx.R;

/**
 * Notificaciones locales (en-device) para los eventos del mapa en tiempo real.
 * NO usa Firebase — se disparan desde el propio dispositivo cuando ocurre el evento.
 */
public class MapaNotificacionesHelper {

    private static final String CHANNEL_ID   = "mapa_eventos";
    private static final String CHANNEL_NAME = "Eventos del viaje";

    // IDs únicos para que Android no apile notificaciones del mismo tipo
    public static final int ID_LLEGANDO_A_PASAJERO  = 2001;
    public static final int ID_PASAJERO_A_BORDO     = 2002;
    public static final int ID_LLEGADA_BAJADA        = 2003;
    public static final int ID_VIAJE_FINALIZADO      = 2004;
    public static final int ID_CONDUCTOR_CERCA       = 2005;
    public static final int ID_CONDUCTOR_LLEGO       = 2006;
    public static final int ID_PASAJERO_EN_PARADA    = 2007;
    public static final int ID_TODOS_A_BORDO         = 2008;

    // ── Canal de notificaciones (Android 8+) ─────────────────────────────────
    public static void crearCanal(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            canal.setDescription("Avisos en tiempo real de tu viaje");
            canal.enableVibration(true);
            canal.setShowBadge(true);
            NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(canal);
        }
    }

    // ── Método central de envío ───────────────────────────────────────────────
    private static void enviar(Context ctx, int id, String titulo,
                               String mensaje, int icono, Class<?> destino) {
        crearCanal(ctx);
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent intent = new Intent(ctx, destino);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                ctx, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(icono)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pi);

        nm.notify(id, b.build());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EVENTOS — CONDUCTOR
    // ═════════════════════════════════════════════════════════════════════════

    /** Conductor se acerca al punto de recogida del pasajero */
    public static void notificarLlegandoAPasajero(Context ctx, String nomPasajero, String nomParada) {
        enviar(ctx,
                ID_LLEGANDO_A_PASAJERO,
                "📍 Llegando al pasajero",
                "Estás a menos de 60 m de " + nomPasajero + " en " + nomParada,
                R.drawable.ic_directions_car,
                actividadMapa(ctx));
    }

    /** Pasajero confirmado a bordo */
    public static void notificarPasajeroABordo(Context ctx, String nomPasajero, double monto) {
        String montoTxt = monto > 0
                ? " · $" + String.format("%,.0f", monto)
                : "";
        enviar(ctx,
                ID_PASAJERO_A_BORDO,
                "✅ " + nomPasajero + " a bordo",
                "Pasajero recogido exitosamente" + montoTxt + " COP",
                R.drawable.ic_directions_car,
                actividadMapa(ctx));
    }

    /** Todos los pasajeros están a bordo */
    public static void notificarTodosABordo(Context ctx, int cantidad) {
        enviar(ctx,
                ID_TODOS_A_BORDO,
                "🚗 Todos a bordo",
                cantidad + " pasajero(s) recogidos — ¡directo al destino!",
                R.drawable.ic_directions_car,
                actividadMapa(ctx));
    }

    /** Conductor llegó a la parada de bajada de un pasajero */
    public static void notificarLlegadaBajada(Context ctx, String nomPasajero, String nomParada) {
        enviar(ctx,
                ID_LLEGADA_BAJADA,
                "🚏 Parada de " + nomPasajero,
                "Llegaste a la parada: " + nomParada,
                R.drawable.ic_directions_car,
                actividadMapa(ctx));
    }

    /** Viaje finalizado (vista conductor) */
    public static void notificarViajeFinalizado(Context ctx) {
        enviar(ctx,
                ID_VIAJE_FINALIZADO,
                "🏁 Viaje finalizado",
                "Has llegado al destino. Recuerda verificar los pagos.",
                R.drawable.ic_directions_car,
                actividadMapa(ctx));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EVENTOS — PASAJERO
    // ═════════════════════════════════════════════════════════════════════════

    /** El conductor está cerca del punto de recogida del pasajero */
    public static void notificarConductorCerca(Context ctx, String nomConductor, int metros) {
        enviar(ctx,
                ID_CONDUCTOR_CERCA,
                "🚗 " + nomConductor + " se acerca",
                "El conductor está a unos " + metros + " m de tu punto de recogida",
                R.drawable.ic_directions_car,
                actividadMapa(ctx));
    }

    /** El conductor llegó al punto de recogida */
    public static void notificarConductorLlego(Context ctx, String nomConductor) {
        enviar(ctx,
                ID_CONDUCTOR_LLEGO,
                "📍 ¡" + nomConductor + " llegó!",
                "El conductor está en tu punto de recogida. ¡Sal ya!",
                R.drawable.ic_directions_car,
                actividadMapa(ctx));
    }

    /** El conductor llegó a la parada de bajada del pasajero */
    public static void notificarPasajeroEnSuParada(Context ctx, String nomParada) {
        enviar(ctx,
                ID_PASAJERO_EN_PARADA,
                "🚏 Llegaste a tu parada",
                "Estás en: " + nomParada + ". Prepárate para bajar y registrar tu pago.",
                R.drawable.ic_directions_car,
                actividadMapa(ctx));
    }

    // ── Resuelve el nombre real de la clase Mapa sin importación circular ────
    private static Class<?> actividadMapa(Context ctx) {
        try {
            return Class.forName(ctx.getPackageName() + ".controller.Mapa");
        } catch (ClassNotFoundException e) {
            return ctx.getClass(); // fallback
        }
    }
}