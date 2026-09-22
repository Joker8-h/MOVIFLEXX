package com.arlys.moviflexx.controller.domiflex;

import android.content.Context;

import com.arlys.moviflexx.network.RetrofitClient;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class FcmRegistro {
    private FcmRegistro() {}

    public static void enviar(Context context, String jwt) {
        if (jwt == null || jwt.isEmpty()) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> publicar(jwt, token));
        } catch (Throwable ignored) {
            // Sin google-services.json Firebase no arranca y el pedido sigue con la notificación de la base.
        }
    }

    static void publicar(String jwt, String fcm) {
        if (fcm == null || fcm.isEmpty()) return;
        JsonObject body = new JsonObject();
        body.addProperty("token", fcm);
        RetrofitClient.getApiService().guardarFcmToken("Bearer " + jwt, body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {}
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }
}
