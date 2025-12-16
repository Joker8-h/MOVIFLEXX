package com.arlys.moviflexx.controller;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.arlys.moviflexx.R;

public class MainActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 3500;

    private View shineEffect;
    private View particle1, particle2, particle3;
    private View circleOuter, circleMiddle;
    private View splashLogo;
    private View textContainer;
    private View decorativeLine;
    private View progressContainer;
    private View versionContainer;
    private CardView mainContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        startAnimations();


        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(MainActivity.this, Home.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, SPLASH_DURATION);
    }

    private void initViews() {
        shineEffect = findViewById(R.id.shine_effect);
        particle1 = findViewById(R.id.particle_1);
        particle2 = findViewById(R.id.particle_2);
        particle3 = findViewById(R.id.particle_3);
        circleOuter = findViewById(R.id.circle_outer);
        circleMiddle = findViewById(R.id.circle_middle);
        splashLogo = findViewById(R.id.splash_logo);
        textContainer = findViewById(R.id.text_container);
        decorativeLine = findViewById(R.id.decorative_line);
        progressContainer = findViewById(R.id.progress_container);
        versionContainer = findViewById(R.id.version_container);
        mainContainer = findViewById(R.id.main_container);
    }

    // ================= ANIMACIONES =================

    private void startAnimations() {
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(this::animateShineEffect, 200);
        h.postDelayed(this::animateLogo, 400);
        h.postDelayed(this::animateCircles, 700);
        h.postDelayed(this::animateParticles, 900);
        h.postDelayed(this::animateText, 1200);
        h.postDelayed(this::animateProgress, 1800);
        h.postDelayed(this::animateVersion, 2200);
        h.postDelayed(this::animateContainerPulse, 1500);
    }

    private void animateShineEffect() {
        ObjectAnimator alpha = ObjectAnimator.ofFloat(shineEffect, "alpha", 0f, 0.3f, 0f);
        ObjectAnimator move = ObjectAnimator.ofFloat(shineEffect, "translationX", -500f, 500f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, move);
        set.setDuration(1500);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    private void animateLogo() {
        splashLogo.setScaleX(0f);
        splashLogo.setScaleY(0f);
        splashLogo.setRotation(-180f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(splashLogo, "scaleX", 0f, 1.1f, 1f),
                ObjectAnimator.ofFloat(splashLogo, "scaleY", 0f, 1.1f, 1f),
                ObjectAnimator.ofFloat(splashLogo, "rotation", -180f, 0f),
                ObjectAnimator.ofFloat(splashLogo, "alpha", 0f, 1f)
        );
        set.setDuration(1000);
        set.setInterpolator(new OvershootInterpolator(1.5f));
        set.start();
    }

    private void animateCircles() {
        animateCircle(circleOuter, 800, 0);
        animateCircle(circleMiddle, 700, 100);
    }

    private void animateCircle(View circle, int duration, int delay) {
        ObjectAnimator sx = ObjectAnimator.ofFloat(circle, "scaleX", 0.5f, 1.2f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(circle, "scaleY", 0.5f, 1.2f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(circle, "alpha", 0f, 0.6f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy, alpha);
        set.setDuration(duration);
        set.setStartDelay(delay);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();

        // Pulso infinito (CORRECTO)
        ObjectAnimator pulse = ObjectAnimator.ofFloat(circle, "alpha", 0.6f, 0.3f, 0.6f);
        pulse.setDuration(2000);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.start();
    }

    private void animateParticles() {
        animateParticle(particle1, -100f, 100f, 3000, 0);
        animateParticle(particle2, 100f, -100f, 3500, 200);
        animateParticle(particle3, -80f, -120f, 4000, 400);
    }

    private void animateParticle(View p, float x, float y, int duration, int delay) {
        ObjectAnimator tx = ObjectAnimator.ofFloat(p, "translationX", 0f, x);
        ObjectAnimator ty = ObjectAnimator.ofFloat(p, "translationY", 0f, y);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(p, "alpha", 0f, 0.8f, 0.4f);

        tx.setRepeatCount(ValueAnimator.INFINITE);
        ty.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setRepeatCount(ValueAnimator.INFINITE);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(tx, ty, alpha);
        set.setDuration(duration);
        set.setStartDelay(delay);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
    }

    private void animateText() {
        textContainer.setTranslationY(100f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(textContainer, "translationY", 100f, 0f),
                ObjectAnimator.ofFloat(textContainer, "alpha", 0f, 1f)
        );
        set.setDuration(800);
        set.setInterpolator(new BounceInterpolator());
        set.start();

        decorativeLine.setScaleX(0f);

        ObjectAnimator lineAnim =
                ObjectAnimator.ofFloat(decorativeLine, "scaleX", 0f, 1f);

        lineAnim.setDuration(600);
        lineAnim.setInterpolator(new DecelerateInterpolator());
        lineAnim.start();
    }

    private void animateProgress() {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(progressContainer, "scaleX", 0f, 1f),
                ObjectAnimator.ofFloat(progressContainer, "scaleY", 0f, 1f),
                ObjectAnimator.ofFloat(progressContainer, "alpha", 0f, 1f)
        );
        set.setDuration(500);
        set.setInterpolator(new OvershootInterpolator(2f));
        set.start();
    }

    private void animateVersion() {
        versionContainer.setTranslationY(50f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(versionContainer, "translationY", 50f, 0f),
                ObjectAnimator.ofFloat(versionContainer, "alpha", 0f, 1f)
        );
        set.setDuration(600);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    private void animateContainerPulse() {
        ObjectAnimator sx = ObjectAnimator.ofFloat(mainContainer, "scaleX", 1f, 1.02f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(mainContainer, "scaleY", 1f, 1.02f, 1f);

        sx.setRepeatCount(ValueAnimator.INFINITE);
        sy.setRepeatCount(ValueAnimator.INFINITE);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy);
        set.setDuration(2000);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
    }
}
