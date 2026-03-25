package com.arlys.moviflexx.model;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ViajeAlertaManager {

    private static final String TAG = "ViajeAlertaManager";
    private static final Map<Integer, Handler>  handlers  = new HashMap<>();
    private static final Map<Integer, Runnable> runnables = new HashMap<>();

    /**
     * Programa el inicio automático del viaje a la hora exacta de salida.
     * Cuando llegue el momento:
     *   1. Cambia estado a INICIADO en el backend
     *   2. Lanza MapaActivity en modo conductor
     *
     * @param context          Context de la Activity/Service
     * @param idViaje          ID del viaje a monitorear
     * @param fechaHoraSalida  String ISO: "2024-12-25T14:30:00" o "2024-12-25 14:30"
     * @param datosViaje       JSONObject con los datos del viaje (coords, nombres, etc.)
     */
    public static void programarInicioAutomatico(Context context, int idViaje,
                                                 String fechaHoraSalida,
                                                 JSONObject datosViaje) {
        cancelarAlertas(idViaje);

        long ahora      = System.currentTimeMillis();
        long horaSalida = parsearFecha(fechaHoraSalida);
        long demora     = horaSalida - ahora;

        if (demora <= 0) {
            Log.d(TAG, "Viaje #" + idViaje + " ya debería haber salido — iniciando ahora");
            demora = 0;
        } else {
            Log.d(TAG, "Viaje #" + idViaje + " inicia en " + (demora / 60000) + " min");
        }

        Handler  handler  = new Handler(Looper.getMainLooper());
        Runnable runnable = () -> ejecutarInicioViaje(context, idViaje, datosViaje);

        handlers.put(idViaje,  handler);
        runnables.put(idViaje, runnable);
        handler.postDelayed(runnable, demora);
    }

    private static void ejecutarInicioViaje(Context context, int idViaje, JSONObject datosViaje) {
        Log.d(TAG, "⏰ Hora de salida alcanzada — iniciando viaje #" + idViaje);

        // 1. Cambiar estado en el backend
        ConexionApi.getInstance(context).post(
                Constantes.viajeIniciar((long) idViaje), null,
                response -> {
                    Log.d(TAG, "Viaje #" + idViaje + " iniciado en backend");
                    lanzarMapaConductor(context, idViaje, datosViaje);
                },
                error -> {
                    Log.w(TAG, "Error al iniciar viaje en backend — lanzando mapa de todas formas");
                    lanzarMapaConductor(context, idViaje, datosViaje);
                }
        );
    }

    private static void lanzarMapaConductor(Context context, int idViaje, JSONObject v) {
        new Handler(Looper.getMainLooper()).post(() -> {
            android.content.Intent intent =
                    new android.content.Intent(context, com.arlys.moviflexx.controller.Mapa.class);

            intent.putExtra("ID_VIAJE",    idViaje);
            intent.putExtra("DESDE_VIAJE", true);

            // Extraer coords del JSON del viaje
            if (v != null) {
                JSONObject ruta = v.optJSONObject("ruta");
                JSONObject src  = ruta != null ? ruta : v;

                double latO = primerDouble(src, "latOrigen","latitudOrigen","latInicio");
                double lngO = primerDouble(src, "lngOrigen","longitudOrigen","lngInicio");
                double latD = primerDouble(src, "latDestino","latitudDestino","latFin");
                double lngD = primerDouble(src, "lngDestino","longitudDestino","lngFin");

                if (latO != 0) {
                    intent.putExtra("ORIGEN_LAT",  latO);
                    intent.putExtra("ORIGEN_LNG",  lngO);
                }
                if (latD != 0) {
                    intent.putExtra("DESTINO_LAT", latD);
                    intent.putExtra("DESTINO_LNG", lngD);
                }

                String nomSubida = "";
                String nomBajada = "";
                if (ruta != null) {
                    String nom = ruta.optString("nombre","");
                    if (nom.contains("→")) {
                        String[] p = nom.split("→", 2);
                        nomSubida = p[0].trim();
                        nomBajada = p[1].trim();
                    }
                }
                intent.putExtra("NOM_SUBIDA", nomSubida);
                intent.putExtra("NOM_BAJADA", nomBajada);

                String paradas = v.optString("PARADAS_JSON","");
                if (!paradas.isEmpty()) intent.putExtra("PARADAS_JSON", paradas);
            }

            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        });
    }

    /**
     * Cancela el inicio automático programado para el viaje indicado.
     */
    public static void cancelarAlertas(int idViaje) {
        Handler  h = handlers.remove(idViaje);
        Runnable r = runnables.remove(idViaje);
        if (h != null && r != null) h.removeCallbacks(r);
    }

    /**
     * Parsea una fecha/hora en varios formatos ISO y devuelve milisegundos epoch.
     * Método público para que ViajesAdapter pueda usarlo en la cuenta regresiva.
     */
    public static long parsearFechaPublic(String fecha) {
        return parsearFecha(fecha);
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
                return new SimpleDateFormat(fmt, Locale.getDefault()).parse(fecha).getTime();
            } catch (Exception ignored) {}
        }
        return System.currentTimeMillis();
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