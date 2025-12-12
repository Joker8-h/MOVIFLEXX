package com.arlys.moviflexx.utils;

import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

public class SuccessAnimationHelper {

    // 🔹 Animación de rebote para el ícono de éxito
    public static void animateBounceIcon(ImageView icon) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(icon, "scaleX", 0f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(icon, "scaleY", 0f, 1.2f, 1f);

        scaleX.setDuration(700);
        scaleY.setDuration(700);

        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        scaleX.start();
        scaleY.start();
    }

    // 🔹 Animación slide + fade para textos
    public static void animateSlideFade(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(40f);

        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(delay)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    // 🔹 Animación de giro para el botón
    public static void animateRotateButton(View button) {
        ObjectAnimator rotate = ObjectAnimator.ofFloat(button, "rotation", 0f, 360f);
        rotate.setDuration(800);
        rotate.setInterpolator(new AccelerateDecelerateInterpolator());
        rotate.start();
    }
}
