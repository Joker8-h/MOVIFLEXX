package com.arlys.moviflexx.controller.domiflex;

import android.content.Context;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class DomiFlexMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        Context context = getApplicationContext();
        String jwt = context.getSharedPreferences("domiflex", MODE_PRIVATE).getString("token", "");
        FcmRegistro.publicar(jwt, token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        // El sistema muestra la notificación cuando la app está en segundo plano.
    }
}
