package com.arlys.moviflexx.model;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║       FieldTooltip — Tooltips de campo premium       ║
 * ║              MOVIFLEX  ·  by Arlys Dev               ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Muestra un tooltip animado con el error al tocar el ícono ⚠️
 * de cualquier TextInputLayout con error activo.
 *
 * ── USO en onCreate() ───────────────────────────────────
 *
 *   FieldTooltip.attach(tilNombre,   "Nombre completo");
 *   FieldTooltip.attach(tilEmail,    "Correo electrónico");
 *   FieldTooltip.attach(tilTelefono, "Teléfono");
 *   FieldTooltip.attach(tilPassword, "Contraseña");
 *
 * ────────────────────────────────────────────────────────
 */
public class FieldTooltip {

    private static final int COLOR_BG      = 0xFF1A1A2E;   // fondo oscuro elegante
    private static final int COLOR_ACCENT  = 0xFF2EC4B6;   // teal MOVIFLEX
    private static final int COLOR_TEXT    = 0xFFFFFFFF;
    private static final int COLOR_SUBTEXT = 0xFFB0BEC5;

    // Tooltip actualmente visible (solo uno a la vez)
    private static View currentTooltip = null;
    private static Handler autoHideHandler = new Handler(Looper.getMainLooper());
    private static Runnable autoHideRunnable = null;

    /**
     * Adjunta el listener de tooltip al ícono de error del TextInputLayout.
     *
     * @param til        El TextInputLayout al que adjuntar
     * @param fieldLabel Nombre legible del campo (ej: "Correo electrónico")
     */
    public static void attach(TextInputLayout til, String fieldLabel) {
        // Esperamos a que el layout esté listo para encontrar el ícono de error
        til.post(() -> {
            // El ícono de error está en el índice 1 del FrameLayout interno de TIL
            // Interceptamos el touch en toda la zona derecha del TIL
            View endIconArea = findEndIconView(til);
            if (endIconArea != null) {
                endIconArea.setOnClickListener(v -> {
                    String error = til.getError() != null ? til.getError().toString() : null;
                    if (error != null && !error.isEmpty()) {
                        showTooltip(til, fieldLabel, error);
                    }
                });
            } else {
                // Fallback: touch en la zona derecha del EditText
                View editText = til.getEditText();
                if (editText != null) {
                    editText.setOnTouchListener((v, event) -> {
                        if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                            // Si toca el lado derecho (donde está el ícono)
                            float x = event.getX();
                            if (x > editText.getWidth() * 0.75f) {
                                String error = til.getError() != null ? til.getError().toString() : null;
                                if (error != null && !error.isEmpty()) {
                                    showTooltip(til, fieldLabel, error);
                                    return true;
                                }
                            }
                        }
                        return false;
                    });
                }
            }
        });
    }

    /**
     * Muestra el tooltip animado encima del campo.
     */
    private static void showTooltip(TextInputLayout til, String fieldLabel, String errorText) {
        Context ctx = til.getContext();

        // Obtener el ViewGroup raíz de la Activity
        ViewGroup rootView = getRootView(til);
        if (rootView == null) return;

        // Cancelar tooltip anterior
        dismissCurrentTooltip(rootView);

        // ── Construir el tooltip ──
        LinearLayout tooltip = new LinearLayout(ctx);
        tooltip.setOrientation(LinearLayout.VERTICAL);
        tooltip.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        tooltip.setBackground(buildTooltipBg(ctx));
        tooltip.setElevation(dp(ctx, 12));

        // Título del campo (ej: "📋 Nombre completo")
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("📋  " + fieldLabel);
        tvTitle.setTextColor(COLOR_ACCENT);
        tvTitle.setTextSize(12f);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        // Línea divisoria
        View divider = new View(ctx);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1));
        divParams.topMargin    = dp(ctx, 6);
        divParams.bottomMargin = dp(ctx, 6);
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(0x33FFFFFF); // blanco 20% opacidad

        // Líneas de error (una por cada punto si hay varias)
        String[] lines = errorText.split("\n");
        LinearLayout linesContainer = new LinearLayout(ctx);
        linesContainer.setOrientation(LinearLayout.VERTICAL);

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(ctx, 3);
            row.setLayoutParams(rowParams);

            // Bullet
            TextView bullet = new TextView(ctx);
            bullet.setText("•  ");
            bullet.setTextColor(COLOR_ACCENT);
            bullet.setTextSize(13f);

            // Texto del error
            TextView tvLine = new TextView(ctx);
            // Quitar el bullet del string si ya lo trae (ej: "• Mínimo 3 caracteres")
            String cleanLine = trimmed.startsWith("•") ? trimmed.substring(1).trim() : trimmed;
            // Quitar prefijos como "Faltan requisitos:"
            if (cleanLine.toLowerCase().startsWith("faltan requisitos")) continue;
            tvLine.setText(cleanLine);
            tvLine.setTextColor(COLOR_TEXT);
            tvLine.setTextSize(13f);

            row.addView(bullet);
            row.addView(tvLine);
            linesContainer.addView(row);
        }

        // Cola del tooltip (triángulo apuntando abajo, hacia el campo)
        View tail = buildTail(ctx);

        tooltip.addView(tvTitle);
        tooltip.addView(divider);
        tooltip.addView(linesContainer);

        // Wrapper para posicionar el triángulo
        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout.LayoutParams tooltipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tooltip.setLayoutParams(tooltipParams);

        wrapper.addView(tooltip);
        wrapper.addView(tail);

        // Posición en pantalla
        int[] location = new int[2];
        til.getLocationInWindow(location);

        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int tooltipWidth = (int) (screenWidth * 0.78f);

        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                tooltipWidth, ViewGroup.LayoutParams.WRAP_CONTENT);

        // Centrar horizontalmente sobre el campo
        int leftMargin = Math.max(dp(ctx, 16),
                location[0] + til.getWidth() / 2 - tooltipWidth / 2);
        leftMargin = Math.min(leftMargin, screenWidth - tooltipWidth - dp(ctx, 16));
        frameParams.leftMargin = leftMargin;

        // Posicionar encima del campo (restar altura estimada del tooltip ~80dp + cola ~8dp)
        int statusBarHeight = getStatusBarHeight(ctx);
        int topMargin = location[1] - statusBarHeight - dp(ctx, 100);
        topMargin = Math.max(dp(ctx, 60), topMargin); // nunca fuera de pantalla
        frameParams.topMargin = topMargin;

        wrapper.setLayoutParams(frameParams);
        rootView.addView(wrapper);
        currentTooltip = wrapper;

        // ── Animación de entrada ──
        wrapper.setAlpha(0f);
        wrapper.setScaleX(0.85f);
        wrapper.setScaleY(0.85f);
        wrapper.setPivotX(tooltipWidth / 2f);
        wrapper.setPivotY(wrapper.getHeight()); // escala desde abajo
        wrapper.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280)
                .setInterpolator(new OvershootInterpolator(1.3f))
                .start();

        // ── Auto-dismiss después de 4 segundos ──
        autoHideRunnable = () -> dismissCurrentTooltip(rootView);
        autoHideHandler.postDelayed(autoHideRunnable, 4000);

        // ── Tocar fuera cierra el tooltip ──
        wrapper.setOnClickListener(v -> dismissCurrentTooltip(rootView));
        rootView.setOnTouchListener((v, event) -> {
            if (currentTooltip != null) {
                dismissCurrentTooltip(rootView);
            }
            return false;
        });
    }

    private static void dismissCurrentTooltip(ViewGroup rootView) {
        if (currentTooltip == null) return;
        View tooltip = currentTooltip;
        currentTooltip = null;

        if (autoHideRunnable != null) {
            autoHideHandler.removeCallbacks(autoHideRunnable);
            autoHideRunnable = null;
        }

        tooltip.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(180)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (tooltip.getParent() != null)
                        ((ViewGroup) tooltip.getParent()).removeView(tooltip);
                    rootView.setOnTouchListener(null);
                })
                .start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BUILDERS DE VISTAS
    // ══════════════════════════════════════════════════════════════════════════

    private static GradientDrawable buildTooltipBg(Context ctx) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(ctx, 12));
        bg.setColor(COLOR_BG);
        // Borde teal sutil
        bg.setStroke(dp(ctx, 1), 0x552EC4B6);
        return bg;
    }

    private static View buildTail(Context ctx) {
        // Triángulo apuntando hacia abajo (hacia el campo)
        // Lo simulamos con un View rotado 45° que sobresale del card
        View tail = new View(ctx);
        int size = dp(ctx, 12);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.gravity = Gravity.END;
        params.rightMargin = dp(ctx, 24);
        tail.setLayoutParams(params);

        GradientDrawable diamond = new GradientDrawable();
        diamond.setShape(GradientDrawable.RECTANGLE);
        diamond.setCornerRadius(dp(ctx, 2));
        diamond.setColor(COLOR_BG);
        tail.setBackground(diamond);
        tail.setRotation(45f);
        tail.setTranslationY(-dp(ctx, 6f)); // superponer con el card
        return tail;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════════════════════════════════════════════

    /** Busca el ícono de error (endIcon) dentro del TextInputLayout */
    private static View findEndIconView(TextInputLayout til) {
        try {
            // El TIL tiene un FrameLayout interno que contiene el endIcon
            for (int i = 0; i < til.getChildCount(); i++) {
                View child = til.getChildAt(i);
                if (child instanceof FrameLayout) {
                    FrameLayout frame = (FrameLayout) child;
                    for (int j = 0; j < frame.getChildCount(); j++) {
                        View sub = frame.getChildAt(j);
                        String className = sub.getClass().getSimpleName();
                        if (className.contains("CheckableImageButton") || className.contains("ImageView")) {
                            return sub;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static ViewGroup getRootView(View view) {
        try {
            View root = view.getRootView();
            if (root instanceof ViewGroup) {
                // Buscamos el FrameLayout de contenido de la Activity
                View content = ((ViewGroup) root).findViewById(android.R.id.content);
                if (content instanceof ViewGroup) return (ViewGroup) content;
                return (ViewGroup) root;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static int getStatusBarHeight(Context ctx) {
        int result = 0;
        int resourceId = ctx.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        if (resourceId > 0) result = ctx.getResources().getDimensionPixelSize(resourceId);
        return result;
    }

    private static int dp(Context ctx, float dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}