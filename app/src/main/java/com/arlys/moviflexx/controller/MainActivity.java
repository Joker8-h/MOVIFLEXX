package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class MainActivity extends AppCompatActivity {

    // Duraciones
    private static final int SPLASH_DURATION = 2500; // 2.5 segundos
    private static final int LOGO_ANIMATION_DURATION = 800;
    private static final int TEXT_ANIMATION_DURATION = 600;
    private static final int APP_NAME_DELAY = 400;
    private static final int SLOGAN_DELAY = 600;

    // Vistas
    private ImageView logoImageView;
    private TextView appNameTextView;
    private TextView sloganTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ⛔ Deshabilitar botón atrás en el splash
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        // No hacer nada → bloquear atrás
                    }
                }
        );

        // Ocultar la barra de acción
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initViews();
        startAnimations();
        navigateToNextScreen();
    }

    private void initViews() {
        logoImageView = findViewById(R.id.splash_logo);
        appNameTextView = findViewById(R.id.app_name);
        sloganTextView = findViewById(R.id.app_slogan);
    }

    private void startAnimations() {
        animateLogo();
        animateText(appNameTextView, APP_NAME_DELAY, 1.0f);
        animateText(sloganTextView, SLOGAN_DELAY, 0.9f);
    }

    private void animateLogo() {
        logoImageView.setScaleX(0.5f);
        logoImageView.setScaleY(0.5f);
        logoImageView.setAlpha(0f);

        logoImageView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(LOGO_ANIMATION_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void animateText(TextView textView, long delay, float targetAlpha) {
        textView.setAlpha(0f);
        textView.setTranslationY(20f);

        textView.animate()
                .alpha(targetAlpha)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(TEXT_ANIMATION_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void navigateToNextScreen() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(MainActivity.this, Home.class);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, SPLASH_DURATION);
    }
}
