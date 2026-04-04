package com.arlys.moviflexx.model;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

public class PushNotificationHelper {

    private static final String TAG = "PushNotificationHelper";

    /**
     * Envía una notificación push al destinatario a través del backend.
     *
     * @param context   Contexto Android
     * @param fcmToken  Token FCM del destinatario (pasajero o conductor)
     * @param titulo    Nombre del remitente (quien envía el mensaje)
     * @param cuerpo    Texto del mensaje
     * @param tipo      "MENSAJE", "VIAJE", "RESERVA", etc.
     */
    public static void enviarPush(Context context, String fcmToken,
                                  String titulo, String cuerpo, String tipo) {
        if (context == null || fcmToken == null || fcmToken.trim().isEmpty()) {
            Log.w(TAG, "❌ No se puede enviar push: context o fcmToken nulo/vacío.");
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("token",   fcmToken);
            body.put("titulo",  titulo  != null ? titulo  : "MoviFlexx");
            body.put("mensaje", cuerpo  != null ? cuerpo  : "");
            body.put("cuerpo",  cuerpo  != null ? cuerpo  : "");   // alias
            body.put("tipo",    tipo    != null ? tipo    : "SISTEMA");

            // Usar la constante centralizada
            String url = Constantes.PUSH_ENVIAR;

            Log.d(TAG, "📤 Enviando push → " + url);
            Log.d(TAG, "   token:  " + fcmToken.substring(0, Math.min(20, fcmToken.length())) + "...");
            Log.d(TAG, "   titulo: " + titulo + " | tipo: " + tipo);
            Log.d(TAG, "   cuerpo: " + cuerpo);

            ConexionApi.getInstance(context).post(
                    url,
                    body,
                    response -> Log.d(TAG, "✅ Push enviado OK → " + titulo
                            + " | respuesta: " + response.toString()),
                    error -> {
                        int status = (error.networkResponse != null)
                                ? error.networkResponse.statusCode : -1;
                        String detalle = "";
                        if (error.networkResponse != null
                                && error.networkResponse.data != null) {
                            detalle = new String(error.networkResponse.data);
                        }
                        Log.e(TAG, "❌ Error enviando push. HTTP " + status
                                + " | " + error.toString()
                                + " | body: " + detalle);
                    }
            );

        } catch (Exception e) {
            Log.e(TAG, "❌ Excepción armando payload push: " + e.getMessage(), e);
        }
    }

    /**
     * Notifica a todos los pasajeros activos de un viaje.
     */
    public static void notificarPasajerosViaje(Context context, int idViaje,
                                               String titulo, String cuerpo) {
        ConexionApi.getInstance(context).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viaje -> {
                    org.json.JSONArray usuarios = viaje.optJSONArray("usuarios");
                    if (usuarios == null) {
                        Log.w(TAG, "⚠️ Viaje " + idViaje + " sin usuarios");
                        return;
                    }
                    int enviados = 0;
                    for (int i = 0; i < usuarios.length(); i++) {
                        JSONObject u = usuarios.optJSONObject(i);
                        if (u == null) continue;
                        String est = u.optString("estado", "").toUpperCase();
                        if ("CANCELADO".equals(est) || "CANCELADA".equals(est)
                                || "RECHAZADO".equals(est)) continue;
                        JSONObject usuObj = u.optJSONObject("usuario");
                        if (usuObj == null) continue;
                        String token = usuObj.optString("fcmToken",
                                usuObj.optString("tokenFCM",
                                        usuObj.optString("fcm_token", "")));
                        if (!token.isEmpty() && !token.equals("null")) {
                            enviarPush(context, token, titulo, cuerpo, "VIAJE");
                            enviados++;
                        }
                    }
                    Log.d(TAG, "✅ Push enviado a " + enviados + " pasajero(s) del viaje " + idViaje);
                },
                err -> Log.e(TAG, "❌ Error cargando viaje " + idViaje + ": " + err.toString())
        );
    }
}