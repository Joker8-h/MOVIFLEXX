package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.model.SessionManager;


public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            SessionManager session = new SessionManager(this);

            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            if (session.isLoggedIn()) {
                session.loadSessionToMemory();
                // Le decimos a MainActivity que ya hay sesión y el rol
                intent.putExtra("LOGGED_IN",    true);
                intent.putExtra("ES_CONDUCTOR", session.isConductor());
            }
            // Si no está logueado no se pasan extras →
            // MainActivity irá a Home (onboarding)

            startActivity(intent);
            finish();

        }, 0); // sin delay extra — MainActivity tiene su propio splash
    }
}