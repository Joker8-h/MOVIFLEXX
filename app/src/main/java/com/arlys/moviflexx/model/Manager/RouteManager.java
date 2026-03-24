package com.arlys.moviflexx.model.Manager;

import android.util.Log;

import com.arlys.moviflexx.model.pojo.RouteOption;
import com.arlys.moviflexx.model.pojo.RouteOptionsResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RouteManager — llama al backend FastAPI POST /route-options
 * y parsea la respuesta en RouteOptionsResponse.
 *
 * El backend devuelve fuel_cost_cop (snake_case).
 * Este manager lo mapea a RouteOption.fuelCostCop (camelCase).
 *
 * URL del backend: https://backendmovi-production-c657.up.railway.app
 * (o el FastAPI de rutas si está en otra URL)
 */
public class RouteManager {

    private static final String TAG = "RouteManager";

    // ── Ajusta esta URL si el FastAPI de rutas está en otro dominio ──
    private static final String FASTAPI_BASE_URL =
            "https://optimizacionofrutas-production.up.railway.app"; // ← cambia si es necesario

    public interface RouteCallback {
        void onSuccess(RouteOptionsResponse response);
        void onError(String errorMessage);
    }

    /**
     * Llama a POST /route-options con las coordenadas dadas.
     * Debe ejecutarse en un hilo secundario (el caller es responsable de eso).
     */
    public void fetchRoutes(double latOrigen, double lngOrigen,
                            double latDestino, double lngDestino,
                            String preference,
                            RouteCallback callback) {
        new Thread(() -> {
            try {
                // ── Construir el cuerpo JSON que espera el backend ──
                JSONObject body = new JSONObject();

                JSONObject origin = new JSONObject();
                origin.put("lat", latOrigen);
                origin.put("lng", lngOrigen);

                JSONObject destination = new JSONObject();
                destination.put("lat", latDestino);
                destination.put("lng", lngDestino);

                body.put("origin",      origin);
                body.put("destination", destination);
                body.put("preference",  preference != null ? preference : "FASTEST");
                body.put("k",           3); // pedir hasta 3 rutas alternativas

                String responseStr = postJson(FASTAPI_BASE_URL + "/route-options", body.toString());

                if (responseStr == null || responseStr.isEmpty()) {
                    callback.onError("Respuesta vacía del backend de rutas");
                    return;
                }

                // ── Parsear la respuesta ──
                RouteOptionsResponse result = parseResponse(responseStr);
                callback.onSuccess(result);

            } catch (Exception e) {
                Log.e(TAG, "Error en fetchRoutes: " + e.getMessage(), e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    // ── Parser manual de JSON → RouteOptionsResponse ──────────────────────────
    @androidx.annotation.VisibleForTesting
    RouteOptionsResponse parseResponse(String json) throws Exception {
        JSONObject root = new JSONObject(json);

        RouteOptionsResponse response = new RouteOptionsResponse();
        response.preference = root.optString("preference", "FASTEST");
        response.requested  = root.optInt("requested", 0);
        response.returned   = root.optInt("returned",  0);
        response.routes     = new ArrayList<>();

        JSONArray routesArr = root.optJSONArray("routes");
        if (routesArr == null) return response;

        for (int i = 0; i < routesArr.length(); i++) {
            JSONObject r = routesArr.getJSONObject(i);

            RouteOption option = new RouteOption();
            option.id          = r.optString("id");
            option.distanceKm  = r.optDouble("distance_km",  0);   // snake_case del backend
            option.durationMin = r.optDouble("duration_min", 0);   // snake_case del backend
            option.fuelLiters  = r.optDouble("fuel_liters",  0);   // snake_case del backend
            option.fuelCostCop = r.optDouble("fuel_cost_cop", 0);  // ← ESTE es el precio $COP
            option.score       = r.optDouble("score", 0);

            // Parsear el geojson como Map<String, Object>
            JSONObject geojsonObj = r.optJSONObject("geojson");
            if (geojsonObj != null) {
                option.geojson = jsonObjectToMap(geojsonObj);
            }

            response.routes.add(option);
            Log.d(TAG, String.format(
                    "Ruta %s: %.1f km · %.0f min · $%.0f COP combustible",
                    option.id, option.distanceKm, option.durationMin, option.fuelCostCop));
        }

        return response;
    }

    // ── HTTP POST helper ────────────────────────────────────────────────────────
    private String postJson(String urlStr, String jsonBody) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "HTTP " + code + " desde " + urlStr);
                return "";
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ── Convertir JSONObject a Map<String, Object> (para geojson) ─────────────
    private Map<String, Object> jsonObjectToMap(JSONObject obj) {
        Map<String, Object> map = new HashMap<>();
        try {
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object val = obj.get(key);
                if (val instanceof JSONObject) {
                    map.put(key, jsonObjectToMap((JSONObject) val));
                } else if (val instanceof JSONArray) {
                    map.put(key, jsonArrayToList((JSONArray) val));
                } else {
                    map.put(key, val);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parseando geojson", e);
        }
        return map;
    }

    private List<Object> jsonArrayToList(JSONArray arr) {
        List<Object> list = new ArrayList<>();
        try {
            for (int i = 0; i < arr.length(); i++) {
                Object val = arr.get(i);
                if (val instanceof JSONObject) {
                    list.add(jsonObjectToMap((JSONObject) val));
                } else if (val instanceof JSONArray) {
                    list.add(jsonArrayToList((JSONArray) val));
                } else {
                    list.add(val);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }
}