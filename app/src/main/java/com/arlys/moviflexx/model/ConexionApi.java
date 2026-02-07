package com.arlys.moviflexx.model;

import android.content.Context;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class ConexionApi {

    private static ConexionApi instance;
    private RequestQueue requestQueue;
    private static Context ctx;

    private ConexionApi(Context context) {
        ctx = context;
        requestQueue = Volley.newRequestQueue(ctx.getApplicationContext());
    }

    public static synchronized ConexionApi getInstance(Context context) {
        if (instance == null) {
            instance = new ConexionApi(context);
        }
        return instance;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        requestQueue.add(req);
    }
}
