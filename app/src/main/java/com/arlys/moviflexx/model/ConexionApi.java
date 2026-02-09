package com.arlys.moviflexx.model;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Moviflexx API Client - Ultra Professional Edition
 */
public class ConexionApi {

    private static final String TAG = "MOVIFLEXX_API";
    private static ConexionApi instance;
    private final RequestQueue queue;

    private ConexionApi(Context ctx) {
        queue = Volley.newRequestQueue(ctx.getApplicationContext());
    }

    public static synchronized ConexionApi getInstance(Context ctx) {
        if (instance == null) {
            instance = new ConexionApi(ctx);
        }
        return instance;
    }

    // --- MANEJO CENTRALIZADO DE HEADERS (Evita repetir código) ---
    private Map<String, String> getAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = SesionUsuario.getToken();

        headers.put("Content-Type", "application/json");
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        } else {
            Log.w(TAG, "⚠️ Intento de petición con Token nulo o vacío");
        }
        return headers;
    }

    // ================= GET ARRAY (CON TOKEN) =================
    public void getArray(String url,
                         com.android.volley.Response.Listener<JSONArray> ok,
                         com.android.volley.Response.ErrorListener error) {

        Log.d(TAG, "GET Array -> " + url);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null, ok, error) {
            @Override
            public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(request);
    }

    // ================= GET OBJECT (CON TOKEN) =================
    public void getObject(String url,
                          com.android.volley.Response.Listener<JSONObject> ok,
                          com.android.volley.Response.ErrorListener error) {

        Log.d(TAG, "GET Object -> " + url);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null, ok, error) {
            @Override
            public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(request);
    }

    // ================= POST SIN TOKEN (LOGIN / REGISTER) =================
    public void postPublic(String url,
                           JSONObject body,
                           com.android.volley.Response.Listener<JSONObject> ok,
                           com.android.volley.Response.ErrorListener error) {

        Log.i(TAG, "POST Public -> " + url);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body, ok, error) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };
        queue.add(request);
    }

    // ================= POST CON TOKEN =================
    public void post(String url,
                     JSONObject body,
                     com.android.volley.Response.Listener<JSONObject> ok,
                     com.android.volley.Response.ErrorListener error) {

        Log.i(TAG, "POST Private -> " + url);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body, ok, error) {
            @Override
            public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(request);
    }

    // ================= MÉTODO PRO: DELETE (Útil para cancelar viajes) =================
    public void delete(String url,
                       com.android.volley.Response.Listener<JSONObject> ok,
                       com.android.volley.Response.ErrorListener error) {

        Log.w(TAG, "DELETE -> " + url);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.DELETE, url, null, ok, error) {
            @Override
            public Map<String, String> getHeaders() { return getAuthHeaders(); }
        };
        queue.add(request);
    }

    // Aliases y compatibilidad
    public void postJson(String url, JSONObject body, com.android.volley.Response.Listener<JSONObject> ok, com.android.volley.Response.ErrorListener error) {
        post(url, body, ok, error);
    }

    public void addToRequestQueue(Request<?> request) {
        queue.add(request);
    }
}