package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;

import com.arlys.moviflexx.R;

public class Home extends AppCompatActivity {

    private ImageView logoHome, moviflexx;
    private TextView txtTitulo, txtSubtitulo;
    private Button btnComenzar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initViews();
        prepareInitialState();
        startProfessionalAnimations();
    }

    private void initViews() {
        logoHome = findViewById(R.id.logoHome);
        moviflexx = findViewById(R.id.Moviflex);
        txtTitulo = findViewById(R.id.txtTitulo);
        txtSubtitulo = findViewById(R.id.txtSubtitulo);
        btnComenzar = findViewById(R.id.btnComenzar);

        // Listener profesional para el botón con efecto de click
        btnComenzar.setOnClickListener(v -> animateButtonAndGo());
    }

    /**
     * Ocultamos los elementos o los movemos fuera de pantalla
     * antes de empezar para que la animación se note real.
     */
    private void prepareInitialState() {
        logoHome.setAlpha(0f);
        logoHome.setScaleX(0.5f);
        logoHome.setScaleY(0.5f);

        txtTitulo.setAlpha(0f);
        txtTitulo.setTranslationY(100f);

        moviflexx.setAlpha(0f);

        txtSubtitulo.setAlpha(0f);
        txtSubtitulo.setTranslationY(50f);

        btnComenzar.setAlpha(0f);
        btnComenzar.setScaleX(0.8f);
    }

    private void startProfessionalAnimations() {
        // 1. Logo principal con efecto de "rebote" (Overshoot)
        logoHome.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(1200)
                .setInterpolator(new OvershootInterpolator())
                .start();

        // 2. Título con entrada elegante desde abajo
        txtTitulo.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(800)
                .setStartDelay(400)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // 3. Logo secundario (Fade in suave)
        moviflexx.animate()
                .alpha(1f)
                .setDuration(1000)
                .setStartDelay(600)
                .start();

        // 4. Subtítulo
        txtSubtitulo.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(800)
                .setStartDelay(800)
                .start();

        // 5. Botón con entrada de escala
        btnComenzar.animate()
                .alpha(1f)
                .scaleX(1f)
                .setDuration(600)
                .setStartDelay(1100)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(this::startPulseAnimation) // Inicia un latido sutil
                .start();
    }

    /**
     * Hace que el botón "respire" para llamar la atención del usuario
     */
    private void startPulseAnimation() {
        btnComenzar.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(1000)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    btnComenzar.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(1000)
                            .withEndAction(this::startPulseAnimation)
                            .start();
                }).start();
    }

    private void animateButtonAndGo() {
        // Pequeña animación de "feedback" al tocar el botón
        btnComenzar.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction(() -> {
                    Intent intent = new Intent(this, Login.class);
                    // Transición de explosión/zoom profesional
                    ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                            this, android.R.anim.fade_in, android.R.anim.fade_out);
                    startActivity(intent, options.toBundle());
                }).start();
    }
}