package com.arlys.moviflexx.model.Manager;

import android.content.Context;
import android.util.Log;

import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;

public class PrecioTramoPasajeroManager {

    private static final String TAG = "PrecioTramo";

    private static final String OSRM_PROPIO  = "https://optimizacionofrutas-production.up.railway.app";
    private static final String OSRM_PUBLICO = "https://router.project-osrm.org";

    private static final double PRECIO_MINIMO   = 500.0;
    private static final double REDONDEO        = 100.0;
    private static final double FACTOR_HAVERSINE = 1.35;

    public static class Resultado {
        public double distanciaKm;
        public double duracionMin;
        public double precioPorKm;
        public double precioFinal;
        public String precioFormateado;
        public boolean usóOSRM;

        @Override
        public String toString() {
            return "Resultado{km=" + distanciaKm + ", min=" + duracionMin + ", precio=" + precioFinal + ", osrm=" + usóOSRM + "}";
        }
    }

    public interface Callback {
        void onResultado(Resultado resultado);
    }

    /**
     * Versión RECOMENDADA: Calcula el precio usando el backend para asegurar PARIDAD TOTAL.
     */
    public static void calcular(
            Context ctx,
            int idViaje,
            double pickupLat,  double pickupLng,
            double dropoffLat, double dropoffLng,
            double distanciaTotalKm,
            double precioTotal,
            Callback callback) {

        if (idViaje <= 0 || ctx == null) {
            calcular(pickupLat, pickupLng, dropoffLat, dropoffLng, distanciaTotalKm, precioTotal, callback);
            return;
        }

        String url = Constantes.BASE_URL + "/api/viajes/" + idViaje + "/estimar-precio"
                + "?latSubida=" + pickupLat
                + "&lngSubida=" + pickupLng
                + "&latBajada=" + dropoffLat
                + "&lngBajada=" + dropoffLng;

        ConexionApi.getInstance(ctx).getObjectNoCache(url,
                response -> {
                    try {
                        Resultado r = new Resultado();
                        r.precioFinal    = response.optDouble("precioFinal", 0);
                        r.distanciaKm    = response.optDouble("distanciaRecorrida", 0);
                        r.precioFormateado = response.optString("precioFormateado", formatear(r.precioFinal));
                        r.usóOSRM        = true;
                        r.precioPorKm    = precioTotal / (distanciaTotalKm > 0 ? distanciaTotalKm : 1);
                        r.duracionMin    = (r.distanciaKm / 25.0) * 60.0;

                        if (r.precioFinal > 0) {
                            callback.onResultado(r);
                        } else {
                            calcular(pickupLat, pickupLng, dropoffLat, dropoffLng, distanciaTotalKm, precioTotal, callback);
                        }
                    } catch (Exception e) {
                        calcular(pickupLat, pickupLng, dropoffLat, dropoffLng, distanciaTotalKm, precioTotal, callback);
                    }
                },
                error -> {
                    Log.w(TAG, "Error backend precio, usando fallback local: " + error.getMessage());
                    calcular(pickupLat, pickupLng, dropoffLat, dropoffLng, distanciaTotalKm, precioTotal, callback);
                }
        );
    }

    /**
     * Fallback LOCAL / Cálculo offline.
     */
    public static void calcular(
            double pickupLat,  double pickupLng,
            double dropoffLat, double dropoffLng,
            double distanciaTotalKm,
            double precioTotal,
            Callback callback) {

        if (distanciaTotalKm <= 0 || precioTotal <= 0) {
            devolverEnMainThread(estimarConHaversine(pickupLat, pickupLng, dropoffLat, dropoffLng, 10.0, 5000.0), callback);
            return;
        }

        new Thread(() -> {
            Resultado resultado = null;
            try {
                resultado = consultarOSRM(OSRM_PROPIO, pickupLat, pickupLng, dropoffLat, dropoffLng, distanciaTotalKm, precioTotal);
            } catch (Exception e) {
                Log.w(TAG, "OSRM propio falló: " + e.getMessage());
            }

            if (resultado == null) {
                try {
                    resultado = consultarOSRM(OSRM_PUBLICO, pickupLat, pickupLng, dropoffLat, dropoffLng, distanciaTotalKm, precioTotal);
                } catch (Exception e) {
                    Log.w(TAG, "OSRM público falló: " + e.getMessage());
                }
            }

            if (resultado == null) {
                resultado = estimarConHaversine(pickupLat, pickupLng, dropoffLat, dropoffLng, distanciaTotalKm, precioTotal);
            }

            final Resultado r = resultado;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onResultado(r));
        }).start();
    }

    private static Resultado consultarOSRM(String baseUrl, double latA, double lngA, double latB, double lngB, double distTotal, double precioTotal) throws Exception {
        String url = baseUrl + "/route/v1/driving/" + lngA + "," + latA + ";" + lngB + "," + latB + "?overview=false";
        String json = peticionHttp(url);
        if (json == null) return null;

        JSONObject obj = new JSONObject(json);
        if (!"Ok".equals(obj.optString("code"))) return null;

        JSONArray routes = obj.optJSONArray("routes");
        if (routes == null || routes.length() == 0) return null;

        JSONObject route = routes.getJSONObject(0);
        double distKm = route.optDouble("distance", 0) / 1000.0;
        double durMin = route.optDouble("duration", 0) / 60.0;

        return construirResultado(distKm, durMin, distTotal, precioTotal, true);
    }

    private static Resultado estimarConHaversine(double latA, double lngA, double latB, double lngB, double distTotal, double precioTotal) {
        double distKm = haversineKm(latA, lngA, latB, lngB) * FACTOR_HAVERSINE;
        double durMin = (distKm / 25.0) * 60.0;
        return construirResultado(distKm, durMin, distTotal, precioTotal, false);
    }

    private static Resultado construirResultado(double distKm, double durMin, double distTotal, double precioTotal, boolean osrm) {
        double precioPorKm = precioTotal / distTotal;
        double precioRaw = distKm * precioPorKm;
        double precioFinal = redondear(precioRaw * 1.10);

        Resultado r = new Resultado();
        r.distanciaKm = Math.round(distKm * 100.0) / 100.0;
        r.duracionMin = Math.round(durMin * 10.0) / 10.0;
        r.precioPorKm = Math.round(precioPorKm * 100.0) / 100.0;
        r.precioFinal = precioFinal;
        r.precioFormateado = formatear(precioFinal);
        r.usóOSRM = osrm;
        return r;
    }

    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static double redondear(double precio) {
        return Math.max(Math.ceil(precio / REDONDEO) * REDONDEO, PRECIO_MINIMO);
    }

    public static String formatear(double precio) {
        return "$ " + NumberFormat.getNumberInstance(new Locale("es", "CO")).format((long) precio) + " COP";
    }

    private static String peticionHttp(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            conn.setRequestProperty("User-Agent", "Moviflexx-App/1.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static void devolverEnMainThread(Resultado r, Callback cb) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> cb.onResultado(r));
    }
}