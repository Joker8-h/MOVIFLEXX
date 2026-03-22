package com.arlys.moviflexx.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.android.volley.Cache;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
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
    private static final String KEY_EMAIL = "email";
    // ⚠️ Ajusta esta clave al nombre exacto con que guardas la contraseña en SharedPrefs
    private static final String KEY_PASS  = "password";

    private static ConexionApi instance;
    private final  RequestQueue queue;
    private final  Context      appContext;

    // Evita lanzar múltiples re-logins simultáneos si hay varias peticiones en vuelo
    private boolean reloginEnCurso = false;

    // ─── Listener global: se dispara cuando el re-login falla y no hay remedio ─
    public interface OnSesionInvalidaListener {
        void onSesionInvalida();
    }
    private static OnSesionInvalidaListener sesionInvalidaListener;

    public static void setOnSesionInvalidaListener(OnSesionInvalidaListener l) {
        sesionInvalidaListener = l;
    }

    private ConexionApi(Context ctx) {
        appContext = ctx.getApplicationContext();
        queue      = Volley.newRequestQueue(appContext);
    }

    public static synchronized ConexionApi getInstance(Context ctx) {
        if (instance == null) instance = new ConexionApi(ctx);
        return instance;
    }

    // =========================================================================
    //  TOKEN
    // =========================================================================

    /** Devuelve el token vigente, o null si expiró o no existe. */
    private String getToken() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null || token.isEmpty()) token = SesionUsuario.getToken();

        if (token != null && !token.isEmpty()) {
            if (isTokenExpirado(token)) {
                Log.e(TAG, "❌ Token EXPIRADO");
                return null;
            }
            Log.d(TAG, "🔑 Token OK: " + token.substring(0, Math.min(token.length(), 30)) + "...");
            return token;
        }
        Log.e(TAG, "❌ Token nulo");
        return null;
    }

    /** Persiste un token nuevo en SharedPreferences y en SesionUsuario. */
    public void guardarToken(String nuevoToken) {
        appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_TOKEN, nuevoToken).apply();
        SesionUsuario.setToken(nuevoToken);
        Log.i(TAG, "✅ Token renovado y guardado");
    }

    /** Devuelve true si el JWT ya pasó su fecha de expiración. */
    private boolean isTokenExpirado(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return false;
            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_PADDING);
            JSONObject payload = new JSONObject(new String(decoded, "UTF-8"));
            long exp = payload.optLong("exp", 0);
            long now = System.currentTimeMillis() / 1000;
            if (exp > 0) {
                Log.d(TAG, "JWT exp=" + exp + " now=" + now
                        + (exp > now
                        ? " ✅ vigente (" + (exp - now) + "s restantes)"
                        : " ⚠️ EXPIRADO hace " + (now - exp) + "s"));
                return now >= exp;
            }
        } catch (Exception e) {
            Log.w(TAG, "No se pudo decodificar JWT: " + e.getMessage());
        }
        return false;
    }

    // =========================================================================
    //  RE-LOGIN SILENCIOSO
    //  Cuando el token expiró usa las credenciales guardadas para obtener uno
    //  nuevo y luego reintenta la petición original, sin molestar al usuario.
    // =========================================================================

    public interface ReintentoCallback {
        void reintentar();
        void fallar();
    }

    /**
     * Llama al endpoint de login con las credenciales guardadas.
     * Si tiene éxito guarda el nuevo token y llama callback.reintentar().
     * Si falla, notifica onSesionInvalida y llama callback.fallar().
     *
     * @param loginUrl  URL del login, ej: Constantes.LOGIN
     * @param callback  qué hacer después del re-login
     */
    public void renovarTokenSilencioso(String loginUrl, ReintentoCallback callback) {
        // Si ya hay un re-login en curso, esperar 2s y probar con el token que haya quedado
        if (reloginEnCurso) {
            Log.d(TAG, "Re-login ya en curso, esperando...");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (getToken() != null) callback.reintentar();
                else callback.fallar();
            }, 2000);
            return;
        }

        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String email    = prefs.getString(KEY_EMAIL, null);
        String password = prefs.getString(KEY_PASS,  null);

        if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
            Log.e(TAG, "❌ Sin credenciales para re-login silencioso → ir al login");
            notificarSesionInvalida();
            callback.fallar();
            return;
        }

        reloginEnCurso = true;
        Log.i(TAG, "🔄 Re-login silencioso para: " + email);

        try {
            JSONObject body = new JSONObject();
            // ⚠️ Ajusta los campos según lo que espere tu endpoint de login
            body.put("email",    email);
            body.put("password", password);

            postPublic(loginUrl, body,
                    loginResponse -> {
                        reloginEnCurso = false;
                        // ⚠️ Ajusta la clave "token" según el campo de tu respuesta
                        String nuevoToken = loginResponse.optString("token", "");
                        if (nuevoToken.isEmpty()) {
                            JSONObject data = loginResponse.optJSONObject("data");
                            if (data != null) nuevoToken = data.optString("token", "");
                        }
                        if (!nuevoToken.isEmpty()) {
                            guardarToken(nuevoToken);
                            Log.i(TAG, "✅ Re-login silencioso exitoso");
                            callback.reintentar();
                        } else {
                            Log.e(TAG, "❌ Respuesta de re-login sin token: "
                                    + loginResponse.toString()
                                    .substring(0, Math.min(loginResponse.toString().length(), 200)));
                            notificarSesionInvalida();
                            callback.fallar();
                        }
                    },
                    error -> {
                        reloginEnCurso = false;
                        Log.e(TAG, "❌ Re-login falló HTTP "
                                + (error.networkResponse != null ? error.networkResponse.statusCode : "sin red"));
                        notificarSesionInvalida();
                        callback.fallar();
                    }
            );
        } catch (JSONException e) {
            reloginEnCurso = false;
            Log.e(TAG, "Error armando body de re-login: " + e.getMessage());
            notificarSesionInvalida();
            callback.fallar();
        }
    }

    private void notificarSesionInvalida() {
        if (sesionInvalidaListener != null) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> sesionInvalidaListener.onSesionInvalida());
        }
    }

    // =========================================================================
    //  HEADERS
    // =========================================================================

    private Map<String, String> getAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        String token = getToken();
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        } else {
            Log.w(TAG, "⚠️ Petición enviada SIN token");
        }
        return headers;
    }

    private Map<String, String> getAuthHeadersNoCache() {
        Map<String, String> headers = getAuthHeaders();
        headers.put("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.put("Pragma",        "no-cache");
        headers.put("Expires",       "0");
        return headers;
    }

    // =========================================================================
    //  GET ARRAY
    // =========================================================================

    public void getArray(String url,
                         Response.Listener<JSONArray> ok,
                         Response.ErrorListener error) {
        Log.d(TAG, "GET Array → " + url);
        JsonArrayRequest req = new JsonArrayRequest(Request.Method.GET, url, null, ok, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(req);
    }

    public void getArrayNoCache(String url,
                                Response.Listener<JSONArray> ok,
                                Response.ErrorListener error) {
        Log.d(TAG, "GET Array NoCache → " + url);

        Request<JSONArray> req = new Request<JSONArray>(Request.Method.GET, url, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeadersNoCache(); }
            @Override public Cache.Entry getCacheEntry() { return null; }

            @Override
            protected Response<JSONArray> parseNetworkResponse(NetworkResponse response) {
                try {
                    String charset = HttpHeaderParser.parseCharset(response.headers, "UTF-8");
                    String json    = new String(response.data, charset).trim();
                    Log.d(TAG, "NoCache [" + response.statusCode + "]: "
                            + json.substring(0, Math.min(json.length(), 200)));
                    return Response.success(new JSONArray(json), makeFreshCacheEntry(response));
                } catch (UnsupportedEncodingException | JSONException e) {
                    return Response.error(new ParseError(e));
                }
            }

            @Override protected void deliverResponse(JSONArray r) { ok.onResponse(r); }
        };

        req.setShouldCache(false);
        queue.add(req);
    }

    // =========================================================================
    //  GET OBJECT
    // =========================================================================

    public void getObject(String url,
                          Response.Listener<JSONObject> ok,
                          Response.ErrorListener error) {
        Log.d(TAG, "GET Object → " + url);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null, ok, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(req);
    }

    public void getObjectNoCache(String url,
                                 Response.Listener<JSONObject> ok,
                                 Response.ErrorListener error) {
        Log.d(TAG, "GET Object NoCache → " + url);

        Request<JSONObject> req = new Request<JSONObject>(Request.Method.GET, url, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeadersNoCache(); }
            @Override public Cache.Entry getCacheEntry() { return null; }

            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                try {
                    String charset = HttpHeaderParser.parseCharset(response.headers, "UTF-8");
                    String json    = new String(response.data, charset).trim();
                    Log.d(TAG, "ObjectNoCache [" + response.statusCode + "]: "
                            + json.substring(0, Math.min(json.length(), 200)));
                    JSONObject result;
                    if (json.startsWith("[")) {
                        JSONObject wrapper = new JSONObject();
                        wrapper.put("items", new JSONArray(json));
                        result = wrapper;
                    } else {
                        result = new JSONObject(json);
                    }
                    return Response.success(result, makeFreshCacheEntry(response));
                } catch (Exception e) {
                    return Response.error(new ParseError(e));
                }
            }

            @Override protected void deliverResponse(JSONObject r) { ok.onResponse(r); }
        };

        req.setShouldCache(false);
        queue.add(req);
    }

    // =========================================================================
    //  POST / PUT / PATCH / DELETE
    // =========================================================================

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

    public void post(String url, JSONObject body,
                     Response.Listener<JSONObject> ok,
                     Response.ErrorListener error) {
        Log.i(TAG, "POST Private → " + url);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, body, ok, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(req);
    }

    public void put(String url, JSONObject body,
                    Response.Listener<JSONObject> ok,
                    Response.ErrorListener error) {
        Log.i(TAG, "PUT → " + url);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.PUT, url, body, ok, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(req);
    }

    public void patch(String url, JSONObject body,
                      Response.Listener<JSONObject> ok,
                      Response.ErrorListener error) {
        Log.i(TAG, "PATCH → " + url);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.PATCH, url, body, ok, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(req);
    }

    public void delete(String url,
                       Response.Listener<JSONObject> ok,
                       Response.ErrorListener error) {
        Log.w(TAG, "DELETE → " + url);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.DELETE, url, null, ok, error) {
            @Override public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(req);
    }

    public void postJson(String url, JSONObject body,
                         Response.Listener<JSONObject> ok,
                         Response.ErrorListener error) {
        post(url, body, ok, error);
    }

    public void addToRequestQueue(Request<?> request) {
        queue.add(request);
    }

    // =========================================================================
    //  HELPER CACHÉ
    // =========================================================================
    private Cache.Entry makeFreshCacheEntry(NetworkResponse response) {
        Cache.Entry entry = HttpHeaderParser.parseCacheHeaders(response);
        if (entry == null) entry = new Cache.Entry();
        entry.ttl             = 0;
        entry.softTtl         = 0;
        entry.data            = response.data;
        entry.responseHeaders = response.headers;
        return entry;
    }
}