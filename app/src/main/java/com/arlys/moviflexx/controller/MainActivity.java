package com.arlys.moviflexx.controller;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.arlys.moviflexx.R;

/**
 * ✈️ MOVIFLEX — SPLASH SCREEN PREMIUM VIAJES
 *
 * Secuencia cinematográfica:
 * 1. Fondo oscuro con partículas atmosféricas
 * 2. Logo circular con halo pulsante
 * 3. Nombre "MOVIFLEX" letra por letra (estilo Netflix)
 * 4. Línea de acento deslizante
 * 5. Slogan con fade-in elegante
 * 6. Ondas expansivas de radar (travel vibes)
 * 7. Puntos de destino flotantes
 * 8. Fade-out suave hacia Home
 */
public class MainActivity extends BaseActivity {

    // Duración total del splash
    private static final int SPLASH_DURATION = 4500;

    // Delay entre cada letra del nombre (ms)
    private static final int LETTER_DELAY = 90;

    // ── Vistas de fondo ──────────────────────────────────────────
    private View cinematicFlash;
    private View radialGlow;
    private ImageView lightRays;
    private View shineEffect;

    // ── Ondas expansivas (radar) ─────────────────────────────────
    private View wave1, wave2, wave3, wave4;

    // ── Partículas flotantes ─────────────────────────────────────
    private View[] particles = new View[20];

    // ── Anillos del logo ─────────────────────────────────────────
    private View circleOuter, circleMiddle, circleInner;
    private View orbitRing1, orbitRing2;

    // ── Logo ─────────────────────────────────────────────────────
    private ImageView splashLogo;
    private CardView logoCard;

    // ── Sparkles (puntos de destino) ─────────────────────────────
    private ImageView sparkle1, sparkle2, sparkle3;
    private ImageView sparkle4, sparkle5, sparkle6;

    // ── Texto ────────────────────────────────────────────────────
    private LinearLayout textContainer;
    private LinearLayout lettersContainer;   // contiene cada letra de "MOVIFLEX"
    private View decorativeLine;
    private TextView appSlogan;

    // ── Contenedores ─────────────────────────────────────────────
    private CardView mainContainer;
    private View progressContainer;
    private View versionContainer;

    // ── "MOVIFLEX" separado en TextViews individuales ─────────────
    // (se generan dinámicamente desde lettersContainer)
    private TextView[] letterViews;
    private static final String APP_LETTERS = "MOVIFLEX";

    private final Handler handler = new Handler(Looper.getMainLooper());

    // ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupImmersiveMode();
        setContentView(R.layout.activity_main);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { /* bloqueado durante splash */ }
        });

        initViews();
        buildLetterViews();       // inserta las letras en el layout
        startSplashSequence();

        // Navegar al Home al terminar
        handler.postDelayed(() -> {
            animateExit();
            handler.postDelayed(() -> {
                startActivity(new Intent(MainActivity.this, Home.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }, 700);
        }, SPLASH_DURATION);
    }

    // ─────────────────────────────────────────────────────────────
    // CONFIGURACIÓN DE PANTALLA
    // ─────────────────────────────────────────────────────────────
    private void setupImmersiveMode() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    // ─────────────────────────────────────────────────────────────
    // INICIALIZACIÓN DE VISTAS
    // ─────────────────────────────────────────────────────────────
    private void initViews() {
        cinematicFlash  = findViewById(R.id.cinematic_flash);
        radialGlow      = findViewById(R.id.radial_glow);
        lightRays       = findViewById(R.id.light_rays);
        shineEffect     = findViewById(R.id.shine_effect);

        wave1 = findViewById(R.id.wave_1);
        wave2 = findViewById(R.id.wave_2);
        wave3 = findViewById(R.id.wave_3);
        wave4 = findViewById(R.id.wave_4);

        for (int i = 0; i < 20; i++) {
            int resId = getResources().getIdentifier("particle_" + (i + 1), "id", getPackageName());
            particles[i] = findViewById(resId);
        }

        orbitRing1   = findViewById(R.id.orbit_ring_1);


        splashLogo = findViewById(R.id.splash_logo);


        sparkle1 = findViewById(R.id.sparkle_1);
        sparkle2 = findViewById(R.id.sparkle_2);
        sparkle3 = findViewById(R.id.sparkle_3);
        sparkle4 = findViewById(R.id.sparkle_4);
        sparkle5 = findViewById(R.id.sparkle_5);
        sparkle6 = findViewById(R.id.sparkle_6);

        textContainer    = findViewById(R.id.text_container);
        lettersContainer = findViewById(R.id.letters_container);
        decorativeLine   = findViewById(R.id.decorative_line);
        appSlogan        = findViewById(R.id.app_slogan);

        mainContainer    = findViewById(R.id.main_container);
        progressContainer = findViewById(R.id.progress_container);
        versionContainer  = findViewById(R.id.version_container);
    }

    /**
     * Crea un TextView por cada letra de "MOVIFLEX" dentro de lettersContainer,
     * con alpha=0 y translationY positiva (listos para animar).
     */
    private void buildLetterViews() {
        if (lettersContainer == null) return;

        letterViews = new TextView[APP_LETTERS.length()];
        for (int i = 0; i < APP_LETTERS.length(); i++) {
            TextView tv = new TextView(this);
            tv.setText(String.valueOf(APP_LETTERS.charAt(i)));
            tv.setTextSize(48f);                      // sp
            tv.setTextColor(0xFFFFFFFF);
            tv.setTypeface(android.graphics.Typeface.create("sans-serif-black",
                    android.graphics.Typeface.BOLD));
            tv.setLetterSpacing(0.06f);
            tv.setAlpha(0f);
            tv.setTranslationY(60f);
            tv.setShadowLayer(12f, 0f, 6f, 0x80000000);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginEnd(2);
            lettersContainer.addView(tv, lp);
            letterViews[i] = tv;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // SECUENCIA MAESTRA
    // ─────────────────────────────────────────────────────────────
    private void startSplashSequence() {

        // FASE 1 — Flash + ondas radar (0 ms)
        handler.post(this::phaseOneImpact);

        // FASE 2 — Brillo radial + rayos (250 ms)
        handler.postDelayed(this::phaseTwoEnergy, 250);

        // FASE 3 — Logo: anillos + círculos + logo 3D (500 ms)
        handler.postDelayed(this::phaseThreeLogo, 500);

        // FASE 4 — Sparkles / puntos de destino (900 ms)
        handler.postDelayed(this::phaseFourDestinationDots, 900);

        // FASE 5 — Nombre letra por letra (1 200 ms)
        handler.postDelayed(this::phaseFiveLetterReveal, 1200);

        // FASE 6 — Línea decorativa (1 200 + letras ms)
        int lineStart = 1200 + APP_LETTERS.length() * LETTER_DELAY + 100;
        handler.postDelayed(this::phaseSixAccentLine, lineStart);

        // FASE 7 — Slogan (lineStart + 300 ms)
        handler.postDelayed(this::phaseSevenSlogan, lineStart + 350);

        // FASE 8 — Progress + versión (lineStart + 600 ms)
        handler.postDelayed(this::phaseEightFinish, lineStart + 600);

        // FASE 9 — Partículas atmosféricas (200 ms, continuo)
        handler.postDelayed(this::phaseNineParticles, 200);

        // FASE 10 — Efectos continuos (2 500 ms)
        handler.postDelayed(this::phaseTenContinuous, 2500);
    }

    // ─────────────────────────────────────────────────────────────
    // FASE 1 — IMPACTO INICIAL: FLASH + ONDAS RADAR
    // ─────────────────────────────────────────────────────────────
    private void phaseOneImpact() {
        // Flash sutil — blanco muy rápido
        if (cinematicFlash != null) {
            ObjectAnimator flash = ObjectAnimator.ofFloat(cinematicFlash, "alpha", 0f, 0.5f, 0f);
            flash.setDuration(400);
            flash.setInterpolator(new DecelerateInterpolator(2f));
            flash.start();
        }
        // 4 ondas tipo "radar" — escalan y se desvanecen
        animateRadarWave(wave1,   0, 1200, 2.8f);
        animateRadarWave(wave2, 200, 1400, 3.8f);
        animateRadarWave(wave3, 400, 1600, 4.8f);
        animateRadarWave(wave4, 600, 1800, 5.8f);
    }

    private void animateRadarWave(View wave, long delay, int duration, float maxScale) {
        if (wave == null) return;
        wave.setScaleX(0f);
        wave.setScaleY(0f);
        wave.setAlpha(0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(wave, "scaleX", 0f, maxScale),
                ObjectAnimator.ofFloat(wave, "scaleY", 0f, maxScale),
                ObjectAnimator.ofFloat(wave, "alpha", 0f, 0.55f, 0f)
        );
        set.setDuration(duration);
        set.setStartDelay(delay);
        set.setInterpolator(new DecelerateInterpolator(1.5f));
        set.start();
    }

    // ─────────────────────────────────────────────────────────────
    // FASE 2 — ENERGÍA: GLOW RADIAL + RAYOS DE LUZ
    // ─────────────────────────────────────────────────────────────
    private void phaseTwoEnergy() {
        if (radialGlow != null) {
            radialGlow.setScaleX(0f);
            radialGlow.setScaleY(0f);
            AnimatorSet burst = new AnimatorSet();
            burst.playTogether(
                    ObjectAnimator.ofFloat(radialGlow, "scaleX", 0f, 3.5f),
                    ObjectAnimator.ofFloat(radialGlow, "scaleY", 0f, 3.5f),
                    ObjectAnimator.ofFloat(radialGlow, "alpha", 0f, 0.7f, 0f)
            );
            burst.setDuration(1800);
            burst.setInterpolator(new DecelerateInterpolator(1.5f));
            burst.start();
        }

        if (lightRays != null) {
            ObjectAnimator.ofFloat(lightRays, "alpha", 0f, 0.35f)
                    .setDuration(1200);
            lightRays.animate().alpha(0.35f).setDuration(1200).start();

            ObjectAnimator rotation = ObjectAnimator.ofFloat(lightRays, "rotation", 0f, 360f);
            rotation.setDuration(30000);
            rotation.setRepeatCount(ValueAnimator.INFINITE);
            rotation.setInterpolator(new LinearInterpolator());
            rotation.start();
        }

        if (shineEffect != null) {
            AnimatorSet shineSet = new AnimatorSet();
            shineSet.playTogether(
                    ObjectAnimator.ofFloat(shineEffect, "translationX", -1000f, 1000f),
                    ObjectAnimator.ofFloat(shineEffect, "alpha", 0f, 0.3f, 0f)
            );
            shineSet.setDuration(1600);
            shineSet.setInterpolator(new AccelerateDecelerateInterpolator());
            shineSet.start();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // FASE 3 — LOGO: ANILLOS + CÍRCULOS + IMAGEN
    // ─────────────────────────────────────────────────────────────
    private void phaseThreeLogo() {
        animateEnergyCircle(circleOuter, 0,   900, 1.25f);
        animateEnergyCircle(circleMiddle, 150, 800, 1.10f);
        animateEnergyCircle(circleInner,  300, 700, 1.00f);

        animateOrbitalRing(orbitRing1, 250, true);
        animateOrbitalRing(orbitRing2, 450, false);

        // Card del logo
        if (logoCard != null) {
            logoCard.setScaleX(0.75f);
            logoCard.setScaleY(0.75f);
            logoCard.setAlpha(0f);
            AnimatorSet cardSet = new AnimatorSet();
            cardSet.playTogether(
                    ObjectAnimator.ofFloat(logoCard, "scaleX", 0.75f, 1.04f, 1f),
                    ObjectAnimator.ofFloat(logoCard, "scaleY", 0.75f, 1.04f, 1f),
                    ObjectAnimator.ofFloat(logoCard, "alpha", 0f, 1f)
            );
            cardSet.setDuration(900);
            cardSet.setInterpolator(new OvershootInterpolator(1.2f));
            cardSet.start();
        }

        // Logo imagen — flip 3D dramático
        if (splashLogo != null) {
            splashLogo.setAlpha(0f);
            splashLogo.setRotationY(90f);
            splashLogo.setScaleX(0.8f);
            splashLogo.setScaleY(0.8f);

            AnimatorSet logoSet = new AnimatorSet();
            logoSet.playTogether(
                    ObjectAnimator.ofFloat(splashLogo, "rotationY", 90f, 0f),
                    ObjectAnimator.ofFloat(splashLogo, "scaleX", 0.8f, 1f),
                    ObjectAnimator.ofFloat(splashLogo, "scaleY", 0.8f, 1f),
                    ObjectAnimator.ofFloat(splashLogo, "alpha", 0f, 1f)
            );
            logoSet.setDuration(800);
            logoSet.setStartDelay(200);
            logoSet.setInterpolator(new DecelerateInterpolator(2f));
            logoSet.start();

            // Pulso suave continuo
            handler.postDelayed(() -> {
                ObjectAnimator px = ObjectAnimator.ofFloat(splashLogo, "scaleX", 1f, 1.04f, 1f);
                ObjectAnimator py = ObjectAnimator.ofFloat(splashLogo, "scaleY", 1f, 1.04f, 1f);
                px.setDuration(2200); px.setRepeatCount(ValueAnimator.INFINITE);
                py.setDuration(2200); py.setRepeatCount(ValueAnimator.INFINITE);
                px.setInterpolator(new AccelerateDecelerateInterpolator());
                py.setInterpolator(new AccelerateDecelerateInterpolator());
                px.start(); py.start();
            }, 1100);
        }
    }

    private void animateEnergyCircle(View circle, long delay, int duration, float maxScale) {
        if (circle == null) return;
        circle.setScaleX(0.2f); circle.setScaleY(0.2f); circle.setAlpha(0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(circle, "scaleX", 0.2f, maxScale, 1f),
                ObjectAnimator.ofFloat(circle, "scaleY", 0.2f, maxScale, 1f),
                ObjectAnimator.ofFloat(circle, "alpha", 0f, 0.6f)
        );
        set.setDuration(duration);
        set.setStartDelay(delay);
        set.setInterpolator(new OvershootInterpolator(1f));
        set.start();
        handler.postDelayed(() -> {
            ObjectAnimator pulse = ObjectAnimator.ofFloat(circle, "alpha", 0.6f, 0.3f, 0.6f);
            pulse.setDuration(2800);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.start();
        }, delay + duration);
    }

    private void animateOrbitalRing(View ring, long delay, boolean clockwise) {
        if (ring == null) return;
        ring.setScaleX(0f); ring.setScaleY(0f); ring.setAlpha(0f);
        AnimatorSet appear = new AnimatorSet();
        appear.playTogether(
                ObjectAnimator.ofFloat(ring, "scaleX", 0f, 1.1f, 1f),
                ObjectAnimator.ofFloat(ring, "scaleY", 0f, 1.1f, 1f),
                ObjectAnimator.ofFloat(ring, "alpha", 0f, 0.5f)
        );
        appear.setDuration(900);
        appear.setStartDelay(delay);
        appear.setInterpolator(new OvershootInterpolator());
        appear.start();

        handler.postDelayed(() -> {
            float dir = clockwise ? 360f : -360f;
            ObjectAnimator rotate = ObjectAnimator.ofFloat(ring, "rotation", 0f, dir);
            rotate.setDuration(12000);
            rotate.setRepeatCount(ValueAnimator.INFINITE);
            rotate.setInterpolator(new LinearInterpolator());
            rotate.start();
        }, delay + 900);
    }

    // ─────────────────────────────────────────────────────────────
    // FASE 4 — SPARKLES: PUNTOS DE DESTINO
    // ─────────────────────────────────────────────────────────────
    private void phaseFourDestinationDots() {
        animateDot(sparkle1,   0);
        animateDot(sparkle2, 120);
        animateDot(sparkle3, 240);
        animateDot(sparkle4, 180);
        animateDot(sparkle5, 300);
        animateDot(sparkle6,  60);
    }

    /** Aparece como un punto de luz que late suavemente — sin rotación. */
    private void animateDot(ImageView dot, long delay) {
        if (dot == null) return;
        handler.postDelayed(() -> {
            dot.setScaleX(0f); dot.setScaleY(0f); dot.setAlpha(0f);
            AnimatorSet appear = new AnimatorSet();
            appear.playTogether(
                    ObjectAnimator.ofFloat(dot, "scaleX", 0f, 1.3f, 1f),
                    ObjectAnimator.ofFloat(dot, "scaleY", 0f, 1.3f, 1f),
                    ObjectAnimator.ofFloat(dot, "alpha", 0f, 1f)
            );
            appear.setDuration(500);
            appear.setInterpolator(new OvershootInterpolator(2f));
            appear.start();

            // Pulso continuo — late como un pin de mapa
            handler.postDelayed(() -> {
                ObjectAnimator blink = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.2f, 1f);
                blink.setDuration(1800 + (int)(delay * 3));
                blink.setRepeatCount(ValueAnimator.INFINITE);
                blink.setInterpolator(new AccelerateDecelerateInterpolator());
                blink.start();

                ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.25f, 1f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.25f, 1f);
                scaleX.setDuration(2000); scaleY.setDuration(2000);
                scaleX.setRepeatCount(ValueAnimator.INFINITE);
                scaleY.setRepeatCount(ValueAnimator.INFINITE);
                scaleX.start(); scaleY.start();
            }, 500);
        }, delay);
    }

    // ─────────────────────────────────────────────────────────────
    // FASE 5 — REVEAL LETRA POR LETRA (estilo Netflix)
    // ─────────────────────────────────────────────────────────────
    private void phaseFiveLetterReveal() {
        if (textContainer != null) {
            textContainer.setAlpha(1f);
            textContainer.setTranslationY(0f);
        }
        if (letterViews == null) return;

        for (int i = 0; i < letterViews.length; i++) {
            final TextView letter = letterViews[i];
            final int index = i;
            handler.postDelayed(() -> revealLetter(letter, index), (long) i * LETTER_DELAY);
        }
    }

    /**
     * Revela una letra con:
     * - Slide-up desde abajo
     * - Fade-in
     * - Overshoot sutil
     * - Micro-scale pop
     */
    private void revealLetter(TextView letter, int index) {
        if (letter == null) return;
        letter.setAlpha(0f);
        letter.setTranslationY(55f);
        letter.setScaleX(0.7f);
        letter.setScaleY(0.7f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(letter, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(letter, "translationY", 55f, -4f, 0f),
                ObjectAnimator.ofFloat(letter, "scaleX", 0.7f, 1.12f, 1f),
                ObjectAnimator.ofFloat(letter, "scaleY", 0.7f, 1.12f, 1f)
        );
        set.setDuration(420);
        set.setInterpolator(new OvershootInterpolator(1.8f));
        set.start();
    }

    // ─────────────────────────────────────────────────────────────
    // FASE 6 — LÍNEA DE ACENTO DESLIZANTE
    // ─────────────────────────────────────────────────────────────
    private void phaseSixAccentLine() {
        if (decorativeLine == null) return;
        decorativeLine.setPivotX(0f);
        decorativeLine.setScaleX(0f);
        decorativeLine.setAlpha(0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(decorativeLine, "scaleX", 0f, 1.05f, 1f),
                ObjectAnimator.ofFloat(decorativeLine, "alpha", 0f, 1f)
        );
        set.setDuration(700);
        set.setInterpolator(new OvershootInterpolator(1.5f));
        set.start();
    }

    // ─────────────────────────────────────────────────────────────
    // FASE 7 — SLOGAN CON FADE + SLIDE SUAVE
    // ─────────────────────────────────────────────────────────────
    private void phaseSevenSlogan() {
        if (appSlogan == null) return;
        appSlogan.setAlpha(0f);
        appSlogan.setTranslationY(28f);
        appSlogan.setLetterSpacing(0.25f); // espaciado amplio al inicio

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(appSlogan, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(appSlogan, "translationY", 28f, 0f)
        );
        set.setDuration(800);
        set.setInterpolator(new DecelerateInterpolator(2f));
        set.start();
    }

    // ─────────────────────────────────────────────────────────────
    // FASE 8 — PROGRESS + VERSIÓN
    // ─────────────────────────────────────────────────────────────
    private void phaseEightFinish() {
        if (progressContainer != null) {
            progressContainer.setAlpha(0f);
            progressContainer.setScaleX(0f);
            progressContainer.setScaleY(0f);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(progressContainer, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(progressContainer, "scaleX", 0f, 1.1f, 1f),
                    ObjectAnimator.ofFloat(progressContainer, "scaleY", 0f, 1.1f, 1f)
            );
            set.setDuration(550);
            set.setInterpolator(new OvershootInterpolator(2f));
            set.start();
        }

        if (versionContainer != null) {
            handler.postDelayed(() -> {
                versionContainer.setAlpha(0f);
                versionContainer.setTranslationY(40f);
                AnimatorSet set = new AnimatorSet();
                set.playTogether(
                        ObjectAnimator.ofFloat(versionContainer, "alpha", 0f, 1f),
                        ObjectAnimator.ofFloat(versionContainer, "translationY", 40f, 0f)
                );
                set.setDuration(600);
                set.setInterpolator(new DecelerateInterpolator());
                set.start();
            }, 300);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // FASE 9 — PARTÍCULAS ATMOSFÉRICAS FLOTANTES
    // ─────────────────────────────────────────────────────────────
    private void phaseNineParticles() {
        for (int i = 0; i < particles.length; i++) {
            animateAtmosphericParticle(particles[i], i * 80L);
        }
    }

    private void animateAtmosphericParticle(View p, long delay) {
        if (p == null) return;
        handler.postDelayed(() -> {
            float dX = -80f + (float)(Math.random() * 160f);
            float dY = -80f + (float)(Math.random() * 160f);
            int dur = 3500 + (int)(Math.random() * 2500f);

            ObjectAnimator mX = ObjectAnimator.ofFloat(p, "translationX", 0f, dX);
            ObjectAnimator mY = ObjectAnimator.ofFloat(p, "translationY", 0f, dY);
            ObjectAnimator al = ObjectAnimator.ofFloat(p, "alpha", 0f, 0.6f, 0.2f);

            mX.setRepeatCount(ValueAnimator.INFINITE); mX.setRepeatMode(ValueAnimator.REVERSE);
            mY.setRepeatCount(ValueAnimator.INFINITE); mY.setRepeatMode(ValueAnimator.REVERSE);
            al.setRepeatCount(ValueAnimator.INFINITE); al.setRepeatMode(ValueAnimator.REVERSE);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(mX, mY, al);
            set.setDuration(dur);
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.start();
        }, delay);
    }

    // ─────────────────────────────────────────────────────────────
    // FASE 10 — EFECTOS CONTINUOS SUTILES
    // ─────────────────────────────────────────────────────────────
    private void phaseTenContinuous() {
        if (mainContainer != null) {
            ObjectAnimator px = ObjectAnimator.ofFloat(mainContainer, "scaleX", 1f, 1.012f, 1f);
            ObjectAnimator py = ObjectAnimator.ofFloat(mainContainer, "scaleY", 1f, 1.012f, 1f);
            px.setDuration(3000); py.setDuration(3000);
            px.setRepeatCount(ValueAnimator.INFINITE); py.setRepeatCount(ValueAnimator.INFINITE);
            px.setInterpolator(new AccelerateDecelerateInterpolator());
            py.setInterpolator(new AccelerateDecelerateInterpolator());
            px.start(); py.start();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // SALIDA SUAVE
    // ─────────────────────────────────────────────────────────────
    private void animateExit() {
        View root = findViewById(android.R.id.content);
        if (root != null) {
            ObjectAnimator out = ObjectAnimator.ofFloat(root, "alpha", 1f, 0f);
            out.setDuration(700);
            out.setInterpolator(new AccelerateInterpolator(1.5f));
            out.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}