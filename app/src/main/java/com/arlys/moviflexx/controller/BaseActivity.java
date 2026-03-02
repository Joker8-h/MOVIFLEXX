package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

/**
 * BaseActivity — Clase base para todas las Activities.
 * Proporciona transiciones, animaciones de botones y entrada de vistas.
 */
public abstract class BaseActivity extends AppCompatActivity {

    public enum Transition { SLIDE, FADE, NONE }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    // ─── NAVEGACIÓN CON ANIMACIÓN ─────────────────────────────────────────────

    protected void goTo(Class<?> destino) {
        goTo(destino, Transition.SLIDE, false);
    }

    protected void goTo(Class<?> destino, Transition transition) {
        goTo(destino, transition, false);
    }

    protected void goTo(Class<?> destino, Transition transition, boolean finishCurrent) {
        startActivity(new Intent(this, destino));
        applyTransition(transition, false);
        if (finishCurrent) finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        applyTransition(Transition.SLIDE, true);
    }

    private void applyTransition(Transition type, boolean isBack) {
        switch (type) {
            case SLIDE:
                if (isBack) {
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                } else {
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                }
                break;
            case FADE:
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                break;
            case NONE:
                overridePendingTransition(0, 0);
                break;
        }
    }

    // ─── MICROINTERACCIÓN BOTÓN (escala 0.95) ────────────────────────────────

    protected void animateButton(View view, Runnable accion) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(0.95f).scaleY(0.95f)
                            .setDuration(80)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    break;

                case MotionEvent.ACTION_UP:
                    v.animate()
                            .scaleX(1.02f).scaleY(1.02f)
                            .setDuration(100)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() ->
                                    v.animate()
                                            .scaleX(1.0f).scaleY(1.0f)
                                            .setDuration(80)
                                            .setInterpolator(new OvershootInterpolator(1.5f))
                                            .withEndAction(accion)
                                            .start()
                            ).start();
                    break;

                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1.0f).scaleY(1.0f)
                            .setDuration(100)
                            .start();
                    break;
            }
            return false;
        });
    }

    // ─── ANIMACIÓN DE ENTRADA (fade + slide desde abajo) ─────────────────────

    protected void animateViewEntrance(View view, int delayMs) {
        view.setAlpha(0f);
        view.setTranslationY(40f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }
}