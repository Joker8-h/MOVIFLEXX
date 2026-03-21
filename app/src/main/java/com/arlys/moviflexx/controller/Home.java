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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.viewpager2.widget.ViewPager2;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.OnboardingAdapter;

/**
 * Home — Onboarding.
 * Al presionar "Ingresar" o "Saltar" va a Login.
 * Queda en el back stack para que el usuario pueda
 * volver aquí desde Login con el botón atrás.
 */
public class Home extends BaseActivity {

    private ImageView  logoHome;
    private ViewPager2 viewPager;
    private View       dot1, dot2, dot3;
    private View       btnComenzar, shimmerBar;
    private TextView   txtBtnLabel, txtBtnArrow, txtSkip;

    private int     currentPage = 0;
    private boolean isLastPage  = false;
    private boolean navegando   = false;

    private final Handler handler            = new Handler(Looper.getMainLooper());
    private static final long AUTO_SCROLL_MS = 3500L;

    private ObjectAnimator shimmerAnim;
    private boolean shimmerStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_home);

        bindViews();
        initViewPager();
        initListeners();
        runEntranceAnimation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(scrollNext);
        if (shimmerAnim != null) shimmerAnim.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    private void initViewPager() {
        viewPager.setAdapter(new OnboardingAdapter());
        viewPager.setOffscreenPageLimit(1);
        viewPager.setClipToPadding(true);
        viewPager.setClipChildren(true);
        viewPager.setPageTransformer((page, position) ->
                page.setAlpha(position == 0 ? 1f : 0f));

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int pos) {
                currentPage = pos;
                isLastPage  = (pos == 2);
                animateDots(pos);
                animateBtnLabel(isLastPage ? "Ingresar" : "Siguiente");
                resetAutoScroll();
            }
        });

        startAutoScroll();
    }

    private void animateDots(int active) {
        View[] dots = {dot1, dot2, dot3};
        int tealColor = android.graphics.Color.parseColor("#0ABFA3");
        int grayColor = android.graphics.Color.parseColor("#E5E7EB");

        for (int i = 0; i < dots.length; i++) {
            final int  targetW = dpToPx(i == active ? 28 : 6);
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

    private final Runnable scrollNext = () -> {
        if (!navegando) {
            viewPager.setCurrentItem((currentPage + 1) % 3, true);
        }
    };

    private void startAutoScroll() {
        handler.postDelayed(scrollNext, AUTO_SCROLL_MS);
    }

    private void resetAutoScroll() {
        handler.removeCallbacks(scrollNext);
        if (!navegando) handler.postDelayed(scrollNext, AUTO_SCROLL_MS);
    }

    private void initListeners() {
        btnComenzar.setOnClickListener(v -> {
            if (navegando) return;
            if (!isLastPage) {
                viewPager.setCurrentItem(currentPage + 1, true);
            } else {
                irAlLogin();
            }
        });

        txtSkip.setOnClickListener(v -> {
            if (navegando) return;
            irAlLogin();
        });
    }

    private void irAlLogin() {
        navegando = true;
        btnComenzar.setEnabled(false);
        txtSkip.setEnabled(false);
        handler.removeCallbacks(scrollNext);
        doExitAndNavigate();
    }

    private void runEntranceAnimation() {
        logoHome.setAlpha(0f);
        logoHome.setTranslationY(-12f);
        viewPager.setAlpha(0f);
        viewPager.setTranslationY(28f);
        btnComenzar.setAlpha(0f);
        btnComenzar.setTranslationY(20f);
        txtSkip.setAlpha(0f);
        dot1.setAlpha(0f);
        dot2.setAlpha(0f);
        dot3.setAlpha(0f);

        logoHome.animate()
                .alpha(1f).translationY(0f)
                .setDuration(550)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();

        viewPager.animate()
                .alpha(1f).translationY(0f)
                .setDuration(550)
                .setStartDelay(180)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .start();

        for (View d : new View[]{dot1, dot2, dot3}) {
            d.animate().alpha(1f).setDuration(300).setStartDelay(420).start();
        }

        btnComenzar.animate()
                .alpha(1f).translationY(0f)
                .setDuration(480)
                .setStartDelay(520)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .withEndAction(() -> {
                    startShimmer();
                    startArrowOscillation();
                    startLogoFloat();
                })
                .start();

        txtSkip.animate()
                .alpha(1f)
                .setDuration(350)
                .setStartDelay(700)
                .start();
    }

    private void startShimmer() {
        if (shimmerStarted) return;
        shimmerStarted = true;

        shimmerBar.post(() -> {
            float btnW = btnComenzar.getWidth();
            float barW = shimmerBar.getWidth();

            shimmerAnim = ObjectAnimator.ofFloat(shimmerBar, "translationX",
                    -barW, btnW + barW);
            shimmerAnim.setDuration(1800);
            shimmerAnim.setInterpolator(new LinearInterpolator());
            shimmerAnim.setRepeatCount(ObjectAnimator.INFINITE);
            shimmerAnim.setRepeatMode(ObjectAnimator.RESTART);
            shimmerAnim.setStartDelay(2000);
            shimmerAnim.start();

            shimmerAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationRepeat(android.animation.Animator a) {
                    a.setStartDelay(2000);
                }
            });
        });
    }

    private void startArrowOscillation() {
        Runnable oscillate = new Runnable() {
            boolean right = true;
            @Override public void run() {
                if (isFinishing() || isDestroyed()) return;
                txtBtnArrow.animate()
                        .translationX(right ? 7f : 0f)
                        .setDuration(600)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .withEndAction(() -> { right = !right; run(); })
                        .start();
            }
        };
        oscillate.run();
    }

    private void startLogoFloat() {
        Runnable floater = new Runnable() {
            boolean up = true;
            @Override public void run() {
                if (isFinishing() || isDestroyed()) return;
                logoHome.animate()
                        .translationY(up ? -5f : 0f)
                        .setDuration(2000)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .withEndAction(() -> { up = !up; run(); })
                        .start();
            }
        };
        floater.run();
    }

    private void animateBtnLabel(String newLabel) {
        txtBtnLabel.animate().alpha(0f).setDuration(100)
                .withEndAction(() -> {
                    txtBtnLabel.setText(newLabel);
                    txtBtnLabel.animate().alpha(1f).setDuration(150).start();
                }).start();
    }

    private void doExitAndNavigate() {
        getWindow().getDecorView()
                .animate().alpha(0f).setDuration(280)
                .withEndAction(() -> {
                    // NO se hace finish() para que Home quede en el back stack
                    startActivity(new Intent(Home.this, Login.class));
                    overridePendingTransition(
                            android.R.anim.fade_in,
                            android.R.anim.fade_out);
                }).start();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}