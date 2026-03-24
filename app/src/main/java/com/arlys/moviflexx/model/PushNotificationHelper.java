package com.arlys.moviflexx.model;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

/**
 * Helpler class to send Push Notifications directly from the Android Client to the Backend API.
 */
public class PushNotificationHelper {

    private static final String TAG = "PushNotificationHelper";

    /**
     * Send a Push Notification to a specific FCM token via backend API.
     * 
     * @param context Application/Activity context
     * @param fcmToken Target user's FCM token
     * @param titulo Notification Title
     * @param cuerpo Notification Body Message
     * @param tipo Notification Type (e.g. "RESERVA", "VIAJE", "MENSAJE") - Used for icon/handling
     */
    public static void enviarPush(Context context, String fcmToken, String titulo, String cuerpo, String tipo) {
        if (context == null || fcmToken == null || fcmToken.trim().isEmpty()) {
            Log.w(TAG, "Cannot send push: context or fcmToken is missing.");
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("token", fcmToken);
            body.put("titulo", titulo);
            body.put("mensaje", cuerpo); // The backend might use "cuerpo" or "mensaje", let's send both to be safe
            body.put("cuerpo", cuerpo);
            body.put("tipo", tipo != null ? tipo : "SISTEMA");

            String url = Constantes.BASE_URL + "/api/notificaciones/push";

            ConexionApi.getInstance(context).post(
                    url, 
                    body,
                    response -> Log.d(TAG, "Push Notification sent successfully: " + titulo),
                    error -> Log.e(TAG, "Error sending Push Notification: " + error.toString())
            );

        } catch (Exception e) {
            Log.e(TAG, "Error building Push Notification payload: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches the trip details and sends a Push Notification to all active passengers.
     * 
     * @param context Application/Activity context
     * @param idViaje Trip ID
     * @param titulo Notification Title
     * @param cuerpo Notification Body Message
     */
    public static void notificarPasajerosViaje(Context context, int idViaje, String titulo, String cuerpo) {
        ConexionApi.getInstance(context).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viaje -> {
                    org.json.JSONArray usuarios = viaje.optJSONArray("usuarios");
                    if (usuarios == null) return;
                    for (int i = 0; i < usuarios.length(); i++) {
                        JSONObject u = usuarios.optJSONObject(i);
                        if (u == null) continue;
                        
                        String est = u.optString("estado", "").toUpperCase();
                        if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;
                        if ("RECHAZADO".equals(est)) continue;

                        JSONObject usuObj = u.optJSONObject("usuario");
                        if (usuObj == null) continue;
                        
                        String token = usuObj.optString("fcmToken", usuObj.optString("tokenFCM", ""));
                        if (!token.isEmpty()) {
                            enviarPush(context, token, titulo, cuerpo, "VIAJE");
                        }
                    }
                },
                err -> Log.e(TAG, "Error fetching trip details for notifying passengers: " + err.toString())
        );
    }
}
