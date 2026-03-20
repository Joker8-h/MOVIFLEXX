package com.arlys.moviflexx.model;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.TranslateAnimation;

import androidx.recyclerview.widget.RecyclerView;

/**
 * AnimUtils — Sistema de animaciones reutilizable para Moviflex.
 *
 * USO RÁPIDO:
 *   // Animar una vista al entrar en pantalla
 *   AnimUtils.fadeSlideIn(miCard, 100);
 *
 *   // Animar todos los hijos de un LinearLayout en cascada
 *   AnimUtils.animateChildren(miLinearLayout);
 *
 *   // Animar items del RecyclerView al hacer scroll
 *   recyclerView.addOnScrollListener(AnimUtils.scrollRevealListener());
 *
 *   // Tap con scale en un botón o card
 *   AnimUtils.tapScale(miBoton, () -> { /* acción *\/ });
 */
public class AnimUtils {

    // ── Duraciones estándar ───────────────────────────────────────────────────
    public static final int DURATION_FAST   = 180;
    public static final int DURATION_NORMAL = 300;
    public static final int DURATION_SLOW   = 450;
    public static final int STAGGER_DELAY   = 60;   // ms entre cada hijo en cascada

    // =========================================================================
    //  1. FADE + SLIDE IN (el más usado — cards, headers, listas)
    // =========================================================================

    /**
     * Anima una vista con fade + slide desde abajo.
     * @param view     Vista a animar
     * @param delayMs  Retraso antes de iniciar (para cascadas)
     */
    public static void fadeSlideIn(View view, int delayMs) {
        if (view == null) return;
        view.setVisibility(View.VISIBLE);

        AnimationSet set = new AnimationSet(true);
        set.setInterpolator(new DecelerateInterpolator(1.5f));

        AlphaAnimation alpha = new AlphaAnimation(0f, 1f);
        alpha.setDuration(DURATION_NORMAL);

        TranslateAnimation translate = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0.08f,   // 8% desde abajo (sutil)
                Animation.RELATIVE_TO_SELF, 0f
        );
        translate.setDuration(DURATION_NORMAL);

        set.addAnimation(alpha);
        set.addAnimation(translate);
        set.setStartOffset(delayMs);
        set.setFillAfter(true);

        view.startAnimation(set);
    }

    /** Versión sin delay */
    public static void fadeSlideIn(View view) {
        fadeSlideIn(view, 0);
    }

    // =========================================================================
    //  2. FADE IN simple (para overlays, banners, textos)
    // =========================================================================

    public static void fadeIn(View view, int delayMs) {
        if (view == null) return;
        view.setVisibility(View.VISIBLE);
        AlphaAnimation anim = new AlphaAnimation(0f, 1f);
        anim.setDuration(DURATION_NORMAL);
        anim.setStartOffset(delayMs);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.setFillAfter(true);
        view.startAnimation(anim);
    }

    public static void fadeIn(View view) { fadeIn(view, 0); }

    // =========================================================================
    //  3. FADE OUT
    // =========================================================================

    public static void fadeOut(View view, Runnable onEnd) {
        if (view == null) return;
        AlphaAnimation anim = new AlphaAnimation(1f, 0f);
        anim.setDuration(DURATION_FAST);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.setFillAfter(true);
        if (onEnd != null) {
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation a) {}
                @Override public void onAnimationRepeat(Animation a) {}
                @Override public void onAnimationEnd(Animation a) { onEnd.run(); }
            });
        }
        view.startAnimation(anim);
    }

    // =========================================================================
    //  4. CASCADA — anima todos los hijos de un ViewGroup en secuencia
    //     Ideal para LinearLayouts con cards apiladas
    // =========================================================================

    /**
     * Anima todos los hijos directos de un ViewGroup en cascada.
     * @param parent    El LinearLayout / ViewGroup que contiene las vistas
     * @param baseDelay Retraso inicial antes del primer hijo (ms)
     */
    public static void animateChildren(ViewGroup parent, int baseDelay) {
        if (parent == null) return;
        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = parent.getChildAt(i);
            if (child != null && child.getVisibility() != View.GONE) {
                fadeSlideIn(child, baseDelay + (i * STAGGER_DELAY));
            }
        }
    }

    /** Versión con delay base = 80ms */
    public static void animateChildren(ViewGroup parent) {
        animateChildren(parent, 80);
    }

    // =========================================================================
    //  5. SCROLL REVEAL — RecyclerView: anima items al entrar en pantalla
    //     Uso: recycler.addOnScrollListener(AnimUtils.scrollRevealListener());
    // =========================================================================

    private static int lastAnimatedPosition = -1;

    /**
     * Resetea el contador para una nueva pantalla/lista.
     * Llama esto en onResume() o al cargar datos nuevos.
     */
    public static void resetScrollReveal() {
        lastAnimatedPosition = -1;
    }

    /**
     * Devuelve un OnScrollListener que anima items nuevos al hacer scroll.
     */
    public static RecyclerView.OnScrollListener scrollRevealListener() {
        return new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                RecyclerView.LayoutManager lm = rv.getLayoutManager();
                if (lm == null) return;

                // Obtener el último item visible
                int lastVisible;
                if (lm instanceof androidx.recyclerview.widget.LinearLayoutManager) {
                    lastVisible = ((androidx.recyclerview.widget.LinearLayoutManager) lm)
                            .findLastVisibleItemPosition();
                } else if (lm instanceof androidx.recyclerview.widget.GridLayoutManager) {
                    lastVisible = ((androidx.recyclerview.widget.GridLayoutManager) lm)
                            .findLastVisibleItemPosition();
                } else {
                    return;
                }

                if (lastVisible > lastAnimatedPosition) {
                    for (int i = lastAnimatedPosition + 1; i <= lastVisible; i++) {
                        View item = lm.findViewByPosition(i);
                        if (item != null) {
                            int delay = (i - lastAnimatedPosition - 1) * 40;
                            fadeSlideIn(item, delay);
                        }
                    }
                    lastAnimatedPosition = lastVisible;
                }
            }
        };
    }

    /**
     * Anima todos los items visibles de un RecyclerView al cargar.
     * Llama esto DESPUÉS de adapter.notifyDataSetChanged() o al recibir datos.
     */
    public static void animateRecyclerItems(RecyclerView recycler) {
        if (recycler == null) return;
        recycler.post(() -> {
            RecyclerView.LayoutManager lm = recycler.getLayoutManager();
            if (lm == null) return;
            int count = lm.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = lm.getChildAt(i);
                if (child != null) fadeSlideIn(child, 80 + i * 50);
            }
            lastAnimatedPosition = count - 1;
        });
    }

    // =========================================================================
    //  6. TAP SCALE — micro-interacción en cards y botones
    //     Igual al animateButton() de BaseActivity pero sin Runnable
    // =========================================================================

    /**
     * Aplica animación de escala al hacer tap (sin necesidad de Runnable).
     * @param view Vista a animar (card, button, etc.)
     */
    public static void tapPulse(View view) {
        if (view == null) return;
        view.animate()
                .scaleX(0.96f).scaleY(0.96f)
                .setDuration(80)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() ->
                        view.animate()
                                .scaleX(1.0f).scaleY(1.0f)
                                .setDuration(120)
                                .setInterpolator(new OvershootInterpolator(1.8f))
                                .start()
                ).start();
    }

    /**
     * Aplica tap + ejecuta acción al soltar.
     */
    public static void tapPulse(View view, Runnable accion) {
        if (view == null) return;
        view.setOnClickListener(v -> {
            tapPulse(v);
            v.postDelayed(accion, 150);
        });
    }

    // =========================================================================
    //  7. PARALLAX LIGERO en scroll (para headers con imagen o color)
    //     Uso: scrollView.getViewTreeObserver().addOnScrollChangedListener(
    //              () -> AnimUtils.parallax(headerView, scrollView, 0.4f));
    // =========================================================================

    /**
     * Efecto parallax ligero: el header se mueve más lento que el scroll.
     * @param header     La vista que actúa como header (AppBar, imagen, etc.)
     * @param scrollView El NestedScrollView o ScrollView
     * @param factor     0.0 = sin parallax, 0.5 = mitad de velocidad (recomendado 0.3-0.5)
     */
    public static void parallax(View header,
                                androidx.core.widget.NestedScrollView scrollView,
                                float factor) {
        if (header == null || scrollView == null) return;
        int scrollY = scrollView.getScrollY();
        header.setTranslationY(scrollY * factor);
    }

    // =========================================================================
    //  8. SHAKE — para errores de validación en formularios
    // =========================================================================

    public static void shake(View view) {
        if (view == null) return;
        TranslateAnimation shake = new TranslateAnimation(0, 12, 0, 0);
        shake.setDuration(60);
        shake.setRepeatMode(Animation.REVERSE);
        shake.setRepeatCount(5);
        shake.setInterpolator(new DecelerateInterpolator());
        view.startAnimation(shake);
    }

    // =========================================================================
    //  9. BOUNCE IN — para elementos que deben llamar la atención
    // =========================================================================

    public static void bounceIn(View view, int delayMs) {
        if (view == null) return;
        view.setVisibility(View.VISIBLE);
        view.setScaleX(0f);
        view.setScaleY(0f);
        view.setAlpha(0f);
        view.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(400)
                .setStartDelay(delayMs)
                .setInterpolator(new OvershootInterpolator(1.5f))
                .start();
    }

    public static void bounceIn(View view) { bounceIn(view, 0); }
}