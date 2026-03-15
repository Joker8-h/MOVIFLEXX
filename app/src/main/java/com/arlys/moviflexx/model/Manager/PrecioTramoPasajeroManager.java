package com.arlys.moviflexx.model.Manager;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;


public class PrecioTramoPasajeroManager {

    private static final String TAG = "PrecioTramo";

    // OSRM público — cambia a tu instancia propia si tienes una
    private static final String OSRM_PROPIO  =
            "https://optimizacionofrutas-production.up.railway.app";
    private static final String OSRM_PUBLICO =
            "https://router.project-osrm.org";

    // Precio mínimo y múltiplo de redondeo
    private static final double PRECIO_MINIMO   = 500.0;
    private static final double REDONDEO        = 100.0;

    // Factor de corrección Haversine → distancia real en carretera
    private static final double FACTOR_HAVERSINE = 1.35;

    // ── Modelo de resultado ───────────────────────────────────────────────────

    public static class Resultado {
        /** Distancia real del tramo en km (via OSRM o Haversine × factor) */
        public double distanciaKm;
        /** Duración estimada del tramo en minutos */
        public double duracionMin;
        /** Precio por km = precioTotal / distanciaTotal */
        public double precioPorKm;
        /** Precio final redondeado al múltiplo de 100 más cercano */
        public double precioFinal;
        /** Texto listo para mostrar en la UI  ej: "$ 4.500 COP" */
        public String precioFormateado;
        /** true = dato vino de OSRM, false = estimación Haversine */
        public boolean usóOSRM;

        @Override
        public String toString() {
            return "Resultado{km=" + distanciaKm
                    + ", min=" + duracionMin
                    + ", precio=" + precioFinal
                    + ", osrm=" + usóOSRM + "}";
        }
    }

    // ── Interfaz callback ─────────────────────────────────────────────────────

    public interface Callback {
        void onResultado(Resultado resultado);
    }

    // ── Método principal (dispara hilo en background) ─────────────────────────

    /**
     * Calcula el precio del tramo del pasajero de forma asíncrona.
     * El callback se ejecuta en el HILO PRINCIPAL (main thread).
     *
     * @param pickupLat       latitud punto de subida
     * @param pickupLng       longitud punto de subida
     * @param dropoffLat      latitud punto de bajada
     * @param dropoffLng      longitud punto de bajada
     * @param distanciaTotalKm distancia total de la ruta del conductor (km)
     * @param precioTotal     precio total que cobra el conductor por su ruta
     * @param callback        resultado devuelto en UI thread
     */
    public static void calcular(
            double pickupLat,  double pickupLng,
            double dropoffLat, double dropoffLng,
            double distanciaTotalKm,
            double precioTotal,
            Callback callback) {

        // Validación rápida
        if (distanciaTotalKm <= 0 || precioTotal <= 0) {
            Log.w(TAG, "distanciaTotal o precioTotal inválidos — usando estimación directa");
            devolverEnMainThread(estimarConHaversine(
                    pickupLat, pickupLng, dropoffLat, dropoffLng,
                    distanciaTotalKm > 0 ? distanciaTotalKm : 10.0,
                    precioTotal > 0 ? precioTotal : 5000.0), callback);
            return;
        }

        new Thread(() -> {
            Resultado resultado = null;

            // ── Intentar OSRM propio primero ──────────────────────────────────
            try {
                resultado = consultarOSRM(
                        OSRM_PROPIO,
                        pickupLat, pickupLng,
                        dropoffLat, dropoffLng,
                        distanciaTotalKm, precioTotal);
                if (resultado != null) Log.d(TAG, "✅ OSRM propio: " + resultado);
            } catch (Exception e) {
                Log.w(TAG, "OSRM propio falló: " + e.getMessage());
            }

            // ── Fallback: OSRM público ────────────────────────────────────────
            if (resultado == null) {
                try {
                    resultado = consultarOSRM(
                            OSRM_PUBLICO,
                            pickupLat, pickupLng,
                            dropoffLat, dropoffLng,
                            distanciaTotalKm, precioTotal);
                    if (resultado != null) Log.d(TAG, "✅ OSRM público: " + resultado);
                } catch (Exception e) {
                    Log.w(TAG, "OSRM público falló: " + e.getMessage());
                }
            }

            // ── Fallback final: Haversine ─────────────────────────────────────
            if (resultado == null) {
                Log.w(TAG, "Usando Haversine como fallback final");
                resultado = estimarConHaversine(
                        pickupLat, pickupLng, dropoffLat, dropoffLng,
                        distanciaTotalKm, precioTotal);
            }

            final Resultado r = resultado;
            android.os.Handler mainHandler =
                    new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> callback.onResultado(r));

        }).start();
    }

    // ── OSRM ─────────────────────────────────────────────────────────────────

    private static Resultado consultarOSRM(
            String baseUrl,
            double latA, double lngA,
            double latB, double lngB,
            double distanciaTotalKm,
            double precioTotal) throws Exception {

        String url = baseUrl + "/route/v1/driving/"
                + lngA + "," + latA + ";"
                + lngB + "," + latB
                + "?overview=false";

        String json = peticionHttp(url);
        if (json == null || json.isEmpty()) return null;

        JSONObject obj = new JSONObject(json);
        if (!"Ok".equals(obj.optString("code", ""))) return null;

        JSONArray routes = obj.optJSONArray("routes");
        if (routes == null || routes.length() == 0) return null;

        JSONObject route    = routes.getJSONObject(0);
        double distanciaM   = route.optDouble("distance", 0);
        double duracionSeg  = route.optDouble("duration",  0);

        if (distanciaM <= 0) return null;

        double distanciaKm  = distanciaM / 1000.0;
        double duracionMin  = duracionSeg / 60.0;

        Resultado r = construirResultado(
                distanciaKm, duracionMin, distanciaTotalKm, precioTotal, true);
        Log.d(TAG, "OSRM (" + baseUrl + "): " + r);
        return r;
    }

    // ── Haversine ─────────────────────────────────────────────────────────────

    private static Resultado estimarConHaversine(
            double latA, double lngA,
            double latB, double lngB,
            double distanciaTotalKm,
            double precioTotal) {

        double lineaRecta  = haversineKm(latA, lngA, latB, lngB);
        double distanciaKm = lineaRecta * FACTOR_HAVERSINE;
        // Velocidad media urbana 25 km/h para estimar tiempo
        double duracionMin = (distanciaKm / 25.0) * 60.0;

        return construirResultado(distanciaKm, duracionMin,
                distanciaTotalKm, precioTotal, false);
    }

    // ── Construcción del resultado ────────────────────────────────────────────

    private static Resultado construirResultado(
            double distanciaKm,
            double duracionMin,
            double distanciaTotalKm,
            double precioTotal,
            boolean usóOSRM) {

        double precioPorKm  = precioTotal / distanciaTotalKm;
        double precioRaw    = distanciaKm  * precioPorKm;
        double precioFinal  = redondear(precioRaw);

        Resultado r = new Resultado();
        r.distanciaKm    = Math.round(distanciaKm * 100.0) / 100.0;
        r.duracionMin    = Math.round(duracionMin  * 10.0)  / 10.0;
        r.precioPorKm    = Math.round(precioPorKm  * 100.0) / 100.0;
        r.precioFinal    = precioFinal;
        r.precioFormateado = formatear(precioFinal);
        r.usóOSRM        = usóOSRM;
        return r;
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    /** Fórmula de Haversine → distancia en km entre dos coordenadas */
    public static double haversineKm(
            double lat1, double lng1,
            double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Redondea al múltiplo de 100 más cercano, mínimo 500 COP */
    public static double redondear(double precio) {
        double redondeado = Math.ceil(precio / REDONDEO) * REDONDEO;
        return Math.max(redondeado, PRECIO_MINIMO);
    }

    /** Formatea el precio para la UI:  "$ 4.500 COP" */
    public static String formatear(double precio) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "CO"));
        return "$ " + nf.format((long) precio) + " COP";
    }

    /** Petición HTTP simple (GET) en hilo de red */
    private static String peticionHttp(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestProperty("User-Agent", "Moviflexx-App/1.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Envía el resultado al main thread directamente (cuando ya estamos en background) */
    private static void devolverEnMainThread(Resultado r, Callback cb) {
        new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> cb.onResultado(r));
    }
}