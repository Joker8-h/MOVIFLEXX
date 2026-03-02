package com.arlys.moviflexx.controller;

import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * Maneja el overlay de carga con animación de rotación pura en Java.
 * Compatible con cualquier minSdk (no usa AnimatedVectorDrawable).
 *
 * USO en tu Activity/Fragment:
 *
 *   private LoadingAnimationHelper loadingHelper;
 *
 *   // En onCreate, después de bindViews():
 *   loadingHelper = new LoadingAnimationHelper(loadingOverlay, ivLoadingAnim);
 *
 *   // Para mostrar:
 *   loadingHelper.mostrar();
 *
 *   // Para ocultar (con fade):
 *   loadingHelper.ocultar();
 */
public class LoadingAnimationHelper {

    private final LinearLayout  overlay;
    private final ImageView     icon;
    private ObjectAnimator      rotator;

    public LoadingAnimationHelper(LinearLayout overlay, ImageView icon) {
        this.overlay = overlay;
        this.icon    = icon;
    }

    /** Muestra el overlay y arranca la rotación */
    public void mostrar() {
        if (overlay == null) return;
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(1f);
        arrancarRotacion();
    }

    /** Oculta el overlay con fade y detiene la animación */
    public void ocultar() {
        if (overlay == null) return;
        overlay.animate()
                .alpha(0f)
                .setDuration(350)
                .withEndAction(() -> {
                    overlay.setVisibility(View.GONE);
                    overlay.setAlpha(1f);
                    detenerRotacion();
                })
                .start();
    }

    /** Detiene y libera el animador (llama desde onDestroy) */
    public void cancelar() {
        detenerRotacion();
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    private void arrancarRotacion() {
        if (icon == null) return;
        detenerRotacion();
        rotator = ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 360f);
        rotator.setDuration(900);
        rotator.setInterpolator(new LinearInterpolator());
        rotator.setRepeatCount(ObjectAnimator.INFINITE);
        rotator.start();
    }

    private void detenerRotacion() {
        if (rotator != null) {
            rotator.cancel();
            rotator = null;
        }
        if (icon != null) icon.setRotation(0f);
    }
}