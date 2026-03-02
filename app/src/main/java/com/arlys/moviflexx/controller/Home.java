package com.arlys.moviflexx.controller;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.OnboardingAdapter;

public class Home extends AppCompatActivity {

    // ── Vistas ────────────────────────────────────────────────────────────
    private ImageView  logoHome;
    private ViewPager2 viewPager;
    private View       dot1, dot2, dot3;
    private View       btnComenzar, shimmerBar;
    private TextView   txtBtnLabel, txtBtnArrow, txtSkip;

    // ── Estado ────────────────────────────────────────────────────────────
    private int     currentPage  = 0;
    private boolean isLastPage   = false;

    // FIX 1: bandera para bloquear clicks múltiples mientras se navega
    private boolean navegando = false;

    // ── Timers ────────────────────────────────────────────────────────────
    private final Handler handler       = new Handler(Looper.getMainLooper());
    private static final long AUTO_SCROLL_MS = 3500L;

    // ── Animadores ────────────────────────────────────────────────────────
    private ObjectAnimator shimmerAnim;
    private boolean shimmerStarted = false;

    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_home);


        bindViews();
        initViewPager();
        initListeners();
        runEntranceAnimation();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  BIND
    // ═════════════════════════════════════════════════════════════════════
    private void bindViews() {
        logoHome    = findViewById(R.id.logoHome);
        viewPager   = findViewById(R.id.viewPagerOnboarding);
        dot1        = findViewById(R.id.dot1);
        dot2        = findViewById(R.id.dot2);
        dot3        = findViewById(R.id.dot3);
        btnComenzar = findViewById(R.id.btnComenzar);
        shimmerBar  = findViewById(R.id.shimmerBar);
        txtBtnLabel = findViewById(R.id.txtBtnLabel);
        txtBtnArrow = findViewById(R.id.txtBtnArrow);
        txtSkip     = findViewById(R.id.txtSkip);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  VIEWPAGER2
    // ═════════════════════════════════════════════════════════════════════
    private void initViewPager() {
        viewPager.setAdapter(new OnboardingAdapter());
        viewPager.setOffscreenPageLimit(1);
        viewPager.setClipToPadding(true);
        viewPager.setClipChildren(true);
        viewPager.setPageTransformer((page, position) -> {
            page.setAlpha(position == 0 ? 1f : 0f);
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int pos) {
                currentPage = pos;
                isLastPage  = (pos == 2);
                animateDots(pos);
                animateBtnLabel(isLastPage ? "INGRESAR" : "SIGUIENTE");
                // FIX 2: el carrusel siempre se reinicia al cambiar de página,
                // incluso si el cambio lo hizo el usuario manualmente
                resetAutoScroll();
            }
        });

        startAutoScroll();
    }

    // ─── Dots ─────────────────────────────────────────────────────────────
    private void animateDots(int active) {
        View[] dots   = {dot1, dot2, dot3};
        int tealColor = 0xFF1A2035;
        int grayColor = 0xFFC0C8D0;

        for (int i = 0; i < dots.length; i++) {
            final int  targetW = dpToPx(i == active ? 28 : 7);
            final View dot     = dots[i];
            final int  color   = (i == active) ? tealColor : grayColor;

            ValueAnimator wa = ValueAnimator.ofInt(dot.getWidth(), targetW);
            wa.setDuration(260);
            wa.setInterpolator(new DecelerateInterpolator());
            wa.addUpdateListener(a -> {
                ViewGroup.LayoutParams lp = dot.getLayoutParams();
                lp.width = (int) a.getAnimatedValue();
                dot.setLayoutParams(lp);
            });
            wa.start();

            dot.setBackgroundTintList(ColorStateList.valueOf(color));
        }
    }

    // ─── Auto-scroll ──────────────────────────────────────────────────────
    private final Runnable scrollNext = () -> {
        // FIX 3: el carrusel sigue aunque estemos en la última página
        // solo se detiene si ya estamos navegando hacia Login
        if (!navegando) {
            int next = (currentPage + 1) % 3;
            viewPager.setCurrentItem(next, true);
        }
    };

    private void startAutoScroll() {
        handler.postDelayed(scrollNext, AUTO_SCROLL_MS);
    }

    private void resetAutoScroll() {
        handler.removeCallbacks(scrollNext);
        // Solo reposta si no estamos saliendo de la pantalla
        if (!navegando) {
            handler.postDelayed(scrollNext, AUTO_SCROLL_MS);
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  LISTENERS
    // ═════════════════════════════════════════════════════════════════════
    private void initListeners() {

        btnComenzar.setOnClickListener(v -> {
            // FIX 4: si ya se está navegando, ignorar el click completamente
            if (navegando) return;

            if (!isLastPage) {
                // Avanzar slide — el carrusel sigue activo
                viewPager.setCurrentItem(currentPage + 1, true);
            } else {
                // Última página → ir al Login
                // Desactivar el botón inmediatamente para evitar doble click
                navegando = true;
                btnComenzar.setEnabled(false);
                txtSkip.setEnabled(false);
                handler.removeCallbacks(scrollNext); // detener carrusel solo al salir
                doExitAndNavigate();
            }
        });

        txtSkip.setOnClickListener(v -> {
            if (navegando) return;
            navegando = true;
            btnComenzar.setEnabled(false);
            txtSkip.setEnabled(false);
            handler.removeCallbacks(scrollNext);
            doExitAndNavigate();
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    //  ANIMACIÓN DE ENTRADA
    // ═════════════════════════════════════════════════════════════════════
    private void runEntranceAnimation() {
        logoHome.setAlpha(0f);
        logoHome.setScaleX(0.4f);
        logoHome.setScaleY(0.4f);
        logoHome.setTranslationY(-20f);

        viewPager.setAlpha(0f);
        viewPager.setTranslationY(50f);

        btnComenzar.setAlpha(0f);
        btnComenzar.setTranslationY(30f);
        btnComenzar.setScaleX(0.9f);

        txtSkip.setAlpha(0f);
        dot1.setAlpha(0f); dot2.setAlpha(0f); dot3.setAlpha(0f);

        logoHome.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(900)
                .setInterpolator(new OvershootInterpolator(2.2f))
                .start();

        viewPager.animate()
                .alpha(1f).translationY(0f)
                .setDuration(700)
                .setStartDelay(300)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .start();

        for (View d : new View[]{dot1, dot2, dot3}) {
            d.animate().alpha(1f).setDuration(300).setStartDelay(700).start();
        }

        btnComenzar.animate()
                .alpha(1f).translationY(0f).scaleX(1f)
                .setDuration(600)
                .setStartDelay(850)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .withEndAction(() -> {
                    startShimmer();
                    startArrowOscillation();
                    startButtonPulse();
                    startLogoFloat();
                })
                .start();

        txtSkip.animate()
                .alpha(1f).setDuration(400).setStartDelay(1100).start();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  SHIMMER
    // ═════════════════════════════════════════════════════════════════════
    private void startShimmer() {
        if (shimmerStarted) return;
        shimmerStarted = true;

        shimmerBar.post(() -> {
            float btnW = btnComenzar.getWidth();
            float barW = shimmerBar.getWidth();

            shimmerAnim = ObjectAnimator.ofFloat(shimmerBar, "translationX",
                    -barW, btnW + barW);
            shimmerAnim.setDuration(1600);
            shimmerAnim.setInterpolator(new LinearInterpolator());
            shimmerAnim.setRepeatCount(ObjectAnimator.INFINITE);
            shimmerAnim.setRepeatMode(ObjectAnimator.RESTART);
            shimmerAnim.setStartDelay(1800);
            shimmerAnim.start();

            shimmerAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationRepeat(android.animation.Animator a) {
                    a.setStartDelay(1800);
                }
            });
        });
    }

    // ─── Flecha oscila ────────────────────────────────────────────────────
    private void startArrowOscillation() {
        Runnable oscillate = new Runnable() {
            boolean right = true;
            @Override public void run() {
                if (isFinishing() || isDestroyed()) return;
                txtBtnArrow.animate()
                        .translationX(right ? 9f : 0f)
                        .setDuration(550)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .withEndAction(() -> { right = !right; run(); })
                        .start();
            }
        };
        oscillate.run();
    }

    // ─── Pulso botón ──────────────────────────────────────────────────────
    private void startButtonPulse() {
        Runnable pulse = new Runnable() {
            boolean grow = true;
            @Override public void run() {
                if (isFinishing() || isDestroyed()) return;
                btnComenzar.animate()
                        .scaleX(grow ? 1.025f : 1f)
                        .scaleY(grow ? 1.025f : 1f)
                        .setDuration(900)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .withEndAction(() -> { grow = !grow; run(); })
                        .start();
            }
        };
        pulse.run();
    }

    // ─── Logo flota ───────────────────────────────────────────────────────
    private void startLogoFloat() {
        Runnable floater = new Runnable() {
            boolean up = true;
            @Override public void run() {
                if (isFinishing() || isDestroyed()) return;
                logoHome.animate()
                        .translationY(up ? -6f : 0f)
                        .setDuration(1800)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .withEndAction(() -> { up = !up; run(); })
                        .start();
            }
        };
        floater.run();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  CAMBIO DE TEXTO DEL BOTÓN
    // ═════════════════════════════════════════════════════════════════════
    private void animateBtnLabel(String newLabel) {
        txtBtnLabel.animate().alpha(0f).setDuration(120)
                .withEndAction(() -> {
                    txtBtnLabel.setText(newLabel);
                    txtBtnLabel.animate().alpha(1f).setDuration(160).start();
                }).start();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN
    // ═════════════════════════════════════════════════════════════════════
    private void doExitAndNavigate() {
        btnComenzar.animate().scaleX(0.91f).scaleY(0.91f).setDuration(90)
                .withEndAction(() ->
                        btnComenzar.animate().scaleX(1f).scaleY(1f).setDuration(90)
                                .withEndAction(() ->
                                        getWindow().getDecorView()
                                                .animate().alpha(0f).setDuration(320)
                                                .withEndAction(() -> {
                                                    startActivity(new Intent(Home.this, Login.class));
                                                    overridePendingTransition(
                                                            android.R.anim.fade_in,
                                                            android.R.anim.fade_out);
                                                    finish();
                                                }).start()
                                ).start()
                ).start();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(scrollNext);
        if (shimmerAnim != null) shimmerAnim.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Solo reanudar carrusel si no estamos navegando hacia Login
        if (!navegando) {
            startAutoScroll();
            if (shimmerAnim != null && shimmerAnim.isPaused()) shimmerAnim.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(scrollNext);
        if (shimmerAnim != null) shimmerAnim.cancel();
    }

    // ── Util ──────────────────────────────────────────────────────────────
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}