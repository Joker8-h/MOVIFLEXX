package com.arlys.moviflexx.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.volley.Cache;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class ConexionApi {

    private static final String TAG       = "MOVIFLEXX_API";
    private static final String PREF_NAME = "MoviFlexxPrefs";
    private static final String KEY_TOKEN = "token";

    private static ConexionApi instance;
    private final  RequestQueue queue;
    private final  Context      appContext;

    private ConexionApi(Context ctx) {
        appContext = ctx.getApplicationContext();
        queue      = Volley.newRequestQueue(appContext);
    }

    public static synchronized ConexionApi getInstance(Context ctx) {
        if (instance == null) instance = new ConexionApi(ctx);
        return instance;
    }

    // ─── TOKEN ────────────────────────────────────────────────────────────────
    private String getToken() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null || token.isEmpty()) token = SesionUsuario.getToken();
        if (token != null && !token.isEmpty()) {
            Log.d(TAG, "🔑 Token OK: " + token.substring(0, Math.min(token.length(), 30)) + "...");
        } else {
            Log.e(TAG, "❌ Token nulo — el usuario debe iniciar sesión de nuevo");
        }
        return token;
    }

    private Map<String, String> getAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        String token = getToken();
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        } else {
            Log.w(TAG, "⚠️ Petición enviada SIN token de autorización");
        }
        return headers;
    }

    // ✅ Headers con no-cache para evitar que Volley devuelva 304 con datos viejos
    private Map<String, String> getAuthHeadersNoCache() {
        Map<String, String> headers = getAuthHeaders();
        headers.put("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.put("Pragma",        "no-cache");
        headers.put("Expires",       "0");
        return headers;
    }

    // ─── GET ARRAY ────────────────────────────────────────────────────────────
    public void getArray(String url,
                         Response.Listener<JSONArray> ok,
                         Response.ErrorListener error) {
        Log.d(TAG, "GET Array → " + url);
        JsonArrayRequest req = new JsonArrayRequest(Request.Method.GET, url, null, ok, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(req);
    }

    // ✅ NUEVO: GET Array forzando no-cache — soluciona el 304 cuando la tabla
    //    estaba vacía y Volley sigue devolviendo la respuesta vacía cacheada.
    public void getArrayNoCache(String url,
                                Response.Listener<JSONArray> ok,
                                Response.ErrorListener error) {
        Log.d(TAG, "GET Array NoCache → " + url);

        // Request personalizado que ignora caché de Volley Y envía headers no-cache
        Request<JSONArray> req = new Request<JSONArray>(Request.Method.GET, url, error) {

            @Override
            public Map<String, String> getHeaders() {
                return getAuthHeadersNoCache();
            }

            // ✅ Forzar que Volley no guarde ni use caché para este request
            @Override
            public Cache.Entry getCacheEntry() { return null; }

            @Override
            protected Response<JSONArray> parseNetworkResponse(NetworkResponse response) {
                try {
                    String charset = HttpHeaderParser.parseCharset(response.headers, "UTF-8");
                    String json    = new String(response.data, charset);
                    Log.d(TAG, "NoCache response [" + response.statusCode + "]: "
                            + json.substring(0, Math.min(json.length(), 200)));
                    return Response.success(
                            new JSONArray(json),
                            // ✅ Entrada de caché con TTL=0 para que expire inmediatamente
                            makeFreshCacheEntry(response)
                    );
                } catch (UnsupportedEncodingException | JSONException e) {
                    return Response.error(new ParseError(e));
                }
            }

            @Override
            protected void deliverResponse(JSONArray response) { ok.onResponse(response); }
        };

        // ✅ Marcar el request para que Volley siempre vaya a la red
        req.setShouldCache(false);
        queue.add(req);
    }

    /** Crea una Cache.Entry que expira en 0ms (fuerza revalidación siempre) */
    private Cache.Entry makeFreshCacheEntry(NetworkResponse response) {
        Cache.Entry entry = HttpHeaderParser.parseCacheHeaders(response);
        if (entry == null) entry = new Cache.Entry();
        entry.ttl         = 0;
        entry.softTtl     = 0;
        entry.data        = response.data;
        entry.responseHeaders = response.headers;
        return entry;
    }

    // ─── GET OBJECT ───────────────────────────────────────────────────────────
    public void getObject(String url,
                          Response.Listener<JSONObject> ok,
                          Response.ErrorListener error) {
        Log.d(TAG, "GET Object → " + url);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null, ok, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(req);
    }

    // ─── POST PÚBLICO (sin token — login/registro) ────────────────────────────
    public void postPublic(String url, JSONObject body,
                           Response.Listener<JSONObject> ok,
                           Response.ErrorListener error) {
        Log.i(TAG, "POST Public → " + url);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, body, ok, error) {
            @Override public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Content-Type", "application/json");
                return h;
            }
        };
        queue.add(req);
    }

    // ─── POST PRIVADO (con token) ─────────────────────────────────────────────
    public void post(String url, JSONObject body,
                     Response.Listener<JSONObject> ok,
                     Response.ErrorListener error) {
        Log.i(TAG, "POST Private → " + url);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, body, ok, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(req);
    }

    // ─── PUT ─────────────────────────────────────────────────────────────────
    public void put(String url, JSONObject body,
                    Response.Listener<JSONObject> ok,
                    Response.ErrorListener error) {
        Log.i(TAG, "PUT → " + url);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.PUT, url, body, ok, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(req);
    }

    // ─── PATCH ───────────────────────────────────────────────────────────────
    public void patch(String url, JSONObject body,
                      Response.Listener<JSONObject> ok,
                      Response.ErrorListener error) {
        Log.i(TAG, "PATCH → " + url);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.PATCH, url, body, ok, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(req);
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────
    public void delete(String url,
                       Response.Listener<JSONObject> ok,
                       Response.ErrorListener error) {
        Log.w(TAG, "DELETE → " + url);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.DELETE, url, null, ok, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(req);
    }

    // ─── ALIASES ──────────────────────────────────────────────────────────────
    public void postJson(String url, JSONObject body,
                         Response.Listener<JSONObject> ok,
                         Response.ErrorListener error) {
        post(url, body, ok, error);
    }

    public void addToRequestQueue(Request<?> request) {
        queue.add(request);
    }
}