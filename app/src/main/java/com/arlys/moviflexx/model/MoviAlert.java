package com.arlys.moviflexx.model;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MoviAlert {

    public static final int SUCCESS = 0;
    public static final int ERROR   = 1;
    public static final int WARNING = 2;
    public static final int INFO    = 3;

    // ✅ Evita que se abran múltiples dialogs a la vez
    private static Dialog dialogActivo = null;

    private static void cerrarDialogActivo() {
        if (dialogActivo != null && dialogActivo.isShowing()) {
            try { dialogActivo.dismiss(); } catch (Exception ignored) {}
        }
        dialogActivo = null;
    }

    private static final int COLOR_SUCCESS = 0xFF2EC4B6;
    private static final int COLOR_ERROR   = 0xFFFF6B6B;
    private static final int COLOR_WARNING = 0xFF1AA8A0;
    private static final int COLOR_INFO    = 0xFF4361EE;

    private static final int COLOR_BG      = 0xFFFFFFFF;
    private static final int COLOR_TEXT    = 0xFF1A1A2E;
    private static final int COLOR_SUBTEXT = 0xFF6B7280;

    // ══════════════════════════════════════════════════════════════════════════
    //  API PÚBLICA
    // ══════════════════════════════════════════════════════════════════════════

    public static void success(Context ctx, String titulo, String mensaje) {
        show(ctx, SUCCESS, titulo, mensaje, "¡Entendido!", null, null);
    }

    public static void success(Context ctx, String titulo, String mensaje, Runnable onOk) {
        show(ctx, SUCCESS, titulo, mensaje, "¡Entendido!", onOk, null);
    }

    public static void error(Context ctx, String titulo, String mensaje) {
        show(ctx, ERROR, titulo, mensaje, "Cerrar", null, null);
    }

    public static void error(Context ctx, String titulo, String mensaje, Runnable onOk) {
        show(ctx, ERROR, titulo, mensaje, "Cerrar", onOk, null);
    }

    public static void warning(Context ctx, String titulo, String mensaje) {
        show(ctx, WARNING, titulo, mensaje, "Entendido", null, null);
    }

    public static void warning(Context ctx, String titulo, String mensaje, Runnable onOk) {
        show(ctx, WARNING, titulo, mensaje, "Entendido", onOk, null);
    }

    public static void info(Context ctx, String titulo, String mensaje) {
        show(ctx, INFO, titulo, mensaje, "OK", null, null);
    }

    public static void info(Context ctx, String titulo, String mensaje, Runnable onOk) {
        show(ctx, INFO, titulo, mensaje, "OK", onOk, null);
    }

    public static void confirm(Context ctx, String titulo, String mensaje,
                               String btnPositivo, Runnable onConfirm,
                               String btnNegativo, Runnable onCancel) {
        showTwoButtons(ctx, WARNING, titulo, mensaje,
                btnPositivo, onConfirm, btnNegativo, onCancel);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LOADING
    // ══════════════════════════════════════════════════════════════════════════

    public static Dialog loading(Context ctx, String mensaje) {
        Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.5f);
        }

        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dp(ctx, 32), dp(ctx, 28), dp(ctx, 32), dp(ctx, 28));
        container.setBackground(buildRoundedBg(COLOR_BG, dp(ctx, 20)));

        FrameLayout spinnerContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(ctx, 64), dp(ctx, 64));
        spinnerParams.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerContainer.setLayoutParams(spinnerParams);

        View spinnerOuter = new View(ctx);
        spinnerOuter.setLayoutParams(new FrameLayout.LayoutParams(dp(ctx, 64), dp(ctx, 64)));
        spinnerOuter.setBackground(buildSpinnerDrawable(COLOR_SUCCESS, dp(ctx, 6)));

        View spinnerInner = new View(ctx);
        FrameLayout.LayoutParams innerParams = new FrameLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44));
        innerParams.gravity = Gravity.CENTER;
        spinnerInner.setLayoutParams(innerParams);
        spinnerInner.setBackground(buildSpinnerDrawableInner(COLOR_SUCCESS, dp(ctx, 4)));

        spinnerContainer.addView(spinnerOuter);
        spinnerContainer.addView(spinnerInner);

        ObjectAnimator rotateOuter = ObjectAnimator.ofFloat(spinnerOuter, "rotation", 0f, 360f);
        rotateOuter.setDuration(900);
        rotateOuter.setRepeatCount(ValueAnimator.INFINITE);
        rotateOuter.setInterpolator(new android.view.animation.LinearInterpolator());
        rotateOuter.start();

        ObjectAnimator rotateInner = ObjectAnimator.ofFloat(spinnerInner, "rotation", 360f, 0f);
        rotateInner.setDuration(600);
        rotateInner.setRepeatCount(ValueAnimator.INFINITE);
        rotateInner.setInterpolator(new android.view.animation.LinearInterpolator());
        rotateInner.start();

        TextView tvMensaje = new TextView(ctx);
        tvMensaje.setText(mensaje);
        tvMensaje.setTextColor(COLOR_TEXT);
        tvMensaje.setTextSize(15);
        tvMensaje.setGravity(Gravity.CENTER);
        // ✅ Sin límite de líneas para el loading
        tvMensaje.setSingleLine(false);
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvParams.topMargin = dp(ctx, 16);
        tvMensaje.setLayoutParams(tvParams);

        container.addView(spinnerContainer);
        container.addView(tvMensaje);

        dialog.setContentView(container);

        container.setAlpha(0f);
        container.setScaleX(0.85f);
        container.setScaleY(0.85f);
        container.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(250).setInterpolator(new OvershootInterpolator(1.2f)).start();

        dialog.show();
        return dialog;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TOAST PREMIUM
    // ══════════════════════════════════════════════════════════════════════════

    public static void toast(Context ctx, String mensaje, int tipo) {
        if (!(ctx instanceof android.app.Activity)) return;
        android.app.Activity activity = (android.app.Activity) ctx;

        activity.runOnUiThread(() -> {
            ViewGroup rootView = activity.findViewById(android.R.id.content);

            LinearLayout toastView = new LinearLayout(ctx);
            toastView.setOrientation(LinearLayout.HORIZONTAL);
            toastView.setGravity(Gravity.CENTER_VERTICAL);
            toastView.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 20), dp(ctx, 12));
            toastView.setBackground(buildRoundedBg(getColor(tipo), dp(ctx, 12)));
            toastView.setElevation(dp(ctx, 8));

            TextView icono = new TextView(ctx);
            icono.setText(getEmoji(tipo));
            icono.setTextSize(18);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            iconParams.rightMargin = dp(ctx, 10);
            icono.setLayoutParams(iconParams);

            TextView tvMsg = new TextView(ctx);
            tvMsg.setText(mensaje);
            tvMsg.setTextColor(Color.WHITE);
            tvMsg.setTextSize(14);
            tvMsg.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            // ✅ Sin límite de líneas
            tvMsg.setSingleLine(false);

            toastView.addView(icono);
            toastView.addView(tvMsg);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.topMargin = dp(ctx, 48);
            toastView.setLayoutParams(params);

            rootView.addView(toastView);

            toastView.setTranslationY(-dp(ctx, 80));
            toastView.setAlpha(0f);
            toastView.animate()
                    .translationY(0).alpha(1f)
                    .setDuration(350)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();

            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    toastView.animate()
                            .translationY(-dp(ctx, 80)).alpha(0f)
                            .setDuration(300)
                            .setInterpolator(new AccelerateInterpolator())
                            .withEndAction(() -> {
                                if (toastView.getParent() != null) rootView.removeView(toastView);
                            }).start(), 2500);
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MOTOR — Un botón
    // ══════════════════════════════════════════════════════════════════════════

    private static void show(Context ctx, int tipo, String titulo, String mensaje,
                             String btnText, Runnable onOk, Runnable onDismiss) {
        Dialog dialog = buildDialog(ctx);
        View card = buildCard(ctx, tipo, titulo, mensaje);
        LinearLayout cardLayout = (LinearLayout) card;

        View btn = buildButton(ctx, btnText, getColor(tipo));
        // ✅ Alto WRAP_CONTENT para que el botón no corte el texto
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = dp(ctx, 20);
        // ✅ Padding interno generoso
        btn.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        btn.setLayoutParams(btnParams);

        btn.setOnClickListener(v -> animateDismiss(card, () -> {
            dialog.dismiss();
            if (onOk != null) onOk.run();
        }));

        cardLayout.addView(btn);
        dialog.setContentView(card);
        dialog.show();

        // ✅ Forzar ancho DESPUÉS del show() para que tome efecto real
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getScreenWidth(ctx) * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        animateEntrance(card, tipo);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MOTOR — Dos botones
    // ══════════════════════════════════════════════════════════════════════════

    private static void showTwoButtons(Context ctx, int tipo, String titulo, String mensaje,
                                       String btnPos, Runnable onConfirm,
                                       String btnNeg, Runnable onCancel) {
        Dialog dialog = buildDialog(ctx);
        View card = buildCard(ctx, tipo, titulo, mensaje);
        LinearLayout cardLayout = (LinearLayout) card;

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(ctx, 20);
        row.setLayoutParams(rowParams);

        View btnNegView = buildButtonOutline(ctx, btnNeg != null ? btnNeg : "Cancelar", getColor(tipo));
        LinearLayout.LayoutParams negParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        negParams.rightMargin = dp(ctx, 8);
        btnNegView.setPadding(dp(ctx, 8), dp(ctx, 14), dp(ctx, 8), dp(ctx, 14));
        btnNegView.setLayoutParams(negParams);
        btnNegView.setOnClickListener(v -> animateDismiss(card, () -> {
            dialog.dismiss();
            if (onCancel != null) onCancel.run();
        }));

        View btnPosView = buildButton(ctx, btnPos != null ? btnPos : "Confirmar", getColor(tipo));
        LinearLayout.LayoutParams posParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        posParams.leftMargin = dp(ctx, 8);
        btnPosView.setPadding(dp(ctx, 8), dp(ctx, 14), dp(ctx, 8), dp(ctx, 14));
        btnPosView.setLayoutParams(posParams);
        btnPosView.setOnClickListener(v -> animateDismiss(card, () -> {
            dialog.dismiss();
            if (onConfirm != null) onConfirm.run();
        }));

        row.addView(btnNegView);
        row.addView(btnPosView);
        cardLayout.addView(row);

        dialog.setContentView(card);
        dialog.show();

        // ✅ Forzar ancho DESPUÉS del show()
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getScreenWidth(ctx) * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        animateEntrance(card, tipo);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTORES DE VISTAS
    // ══════════════════════════════════════════════════════════════════════════

    private static Dialog buildDialog(Context ctx) {
        Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            // ✅ Aumentado a 92% para dar más espacio al texto
            dialog.getWindow().setLayout(
                    (int) (getScreenWidth(ctx) * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.55f);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
        return dialog;
    }

    private static View buildCard(Context ctx, int tipo, String titulo, String mensaje) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        // ✅ Padding horizontal aumentado para respirar mejor
        card.setPadding(dp(ctx, 24), dp(ctx, 28), dp(ctx, 24), dp(ctx, 20));
        card.setBackground(buildRoundedBg(COLOR_BG, dp(ctx, 24)));
        card.setElevation(dp(ctx, 16));

        // Franja de color superior
        View accentBar = new View(ctx);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 4));
        barParams.bottomMargin = dp(ctx, 20);
        accentBar.setLayoutParams(barParams);
        accentBar.setBackground(buildRoundedBg(getColor(tipo), dp(ctx, 2)));

        // Círculo con emoji
        FrameLayout iconCircle = new FrameLayout(ctx);
        int circleSize = dp(ctx, 72);
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(circleSize, circleSize);
        circleParams.gravity = Gravity.CENTER_HORIZONTAL;
        circleParams.bottomMargin = dp(ctx, 16);
        iconCircle.setLayoutParams(circleParams);

        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(adjustAlpha(getColor(tipo), 0.12f));
        iconCircle.setBackground(circleBg);

        TextView iconView = new TextView(ctx);
        iconView.setText(getEmoji(tipo));
        iconView.setTextSize(30);
        iconView.setGravity(Gravity.CENTER);
        iconView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iconCircle.addView(iconView);

        // ✅ Título — sin límite de líneas, centrado
        TextView tvTitulo = new TextView(ctx);
        tvTitulo.setText(titulo);
        tvTitulo.setTextColor(COLOR_TEXT);
        tvTitulo.setTextSize(18);
        tvTitulo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitulo.setGravity(Gravity.CENTER);
        tvTitulo.setSingleLine(false);
        tvTitulo.setMaxLines(4);
        tvTitulo.setEllipsize(null);                      // ✅ sin truncar
        tvTitulo.setHorizontallyScrolling(false);         // ✅ fuerza wrap del texto
        LinearLayout.LayoutParams tituloParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tituloParams.bottomMargin = dp(ctx, 8);
        tvTitulo.setLayoutParams(tituloParams);

        // ✅ Mensaje — sin límite de líneas
        TextView tvMensaje = new TextView(ctx);
        tvMensaje.setText(mensaje);
        tvMensaje.setTextColor(COLOR_SUBTEXT);
        tvMensaje.setTextSize(14);
        tvMensaje.setGravity(Gravity.CENTER);
        tvMensaje.setSingleLine(false);
        tvMensaje.setEllipsize(null);                     // ✅ sin truncar
        tvMensaje.setHorizontallyScrolling(false);        // ✅ fuerza wrap del texto
        tvMensaje.setLineSpacing(dp(ctx, 2), 1.0f);
        tvMensaje.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(accentBar);
        card.addView(iconCircle);
        card.addView(tvTitulo);
        card.addView(tvMensaje);

        return card;
    }

    private static View buildButton(Context ctx, String texto, int color) {
        TextView btn = new TextView(ctx);
        btn.setText(texto);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setSingleLine(false);
        btn.setEllipsize(null);
        btn.setHorizontallyScrolling(false); // ✅ texto del botón no se corta
        btn.setBackground(buildRoundedBg(color, dp(ctx, 12)));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                    || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .setInterpolator(new OvershootInterpolator()).start();
            }
            return false;
        });
        return btn;
    }

    private static View buildButtonOutline(Context ctx, String texto, int color) {
        TextView btn = new TextView(ctx);
        btn.setText(texto);
        btn.setTextColor(color);
        btn.setTextSize(15);
        btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setSingleLine(false);
        btn.setEllipsize(null);
        btn.setHorizontallyScrolling(false); // ✅ texto del botón no se corta

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(ctx, 12));
        bg.setStroke(dp(ctx, 2), color);
        bg.setColor(Color.TRANSPARENT);
        btn.setBackground(bg);

        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                    || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .setInterpolator(new OvershootInterpolator()).start();
            }
            return false;
        });
        return btn;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ANIMACIONES
    // ══════════════════════════════════════════════════════════════════════════

    private static void animateEntrance(View card, int tipo) {
        card.setAlpha(0f);
        card.setScaleX(0.7f);
        card.setScaleY(0.7f);
        card.setTranslationY(dp(card.getContext(), 30));

        if (tipo == ERROR) {
            card.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f).translationY(0)
                    .setDuration(300).setInterpolator(new OvershootInterpolator(1.5f))
                    .withEndAction(() -> shakeView(card)).start();
        } else if (tipo == SUCCESS) {
            card.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f).translationY(0)
                    .setDuration(450).setInterpolator(new BounceInterpolator()).start();
        } else {
            card.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f).translationY(0)
                    .setDuration(350).setInterpolator(new OvershootInterpolator(1.2f)).start();
        }
    }

    private static void animateDismiss(View card, Runnable onEnd) {
        card.animate()
                .alpha(0f).scaleX(0.85f).scaleY(0.85f)
                .translationY(dp(card.getContext(), 20))
                .setDuration(200).setInterpolator(new AccelerateInterpolator())
                .withEndAction(onEnd).start();
    }

    private static void shakeView(View view) {
        float d = dp(view.getContext(), 8);
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX",
                0, d, -d, d * 0.7f, -d * 0.7f, d * 0.4f, -d * 0.4f, 0);
        shake.setDuration(500);
        shake.setInterpolator(new DecelerateInterpolator());
        shake.start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════════════════════════════════════════════

    private static int getColor(int tipo) {
        switch (tipo) {
            case SUCCESS: return COLOR_SUCCESS;
            case ERROR:   return COLOR_ERROR;
            case WARNING: return COLOR_WARNING;
            case INFO:    return COLOR_INFO;
            default:      return COLOR_SUCCESS;
        }
    }

    private static String getEmoji(int tipo) {
        switch (tipo) {
            case SUCCESS: return "✅";
            case ERROR:   return "❌";
            case WARNING: return "⚠️";
            case INFO:    return "ℹ️";
            default:      return "✅";
        }
    }

    private static GradientDrawable buildRoundedBg(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(radius);
        bg.setColor(color);
        return bg;
    }

    private static GradientDrawable buildSpinnerDrawable(int color, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setStroke(strokeWidth, color);
        d.setColor(Color.TRANSPARENT);
        return d;
    }

    private static GradientDrawable buildSpinnerDrawableInner(int color, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setStroke(strokeWidth, adjustAlpha(color, 0.35f));
        d.setColor(Color.TRANSPARENT);
        return d;
    }

    private static int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    private static int getScreenWidth(Context ctx) {
        return ctx.getResources().getDisplayMetrics().widthPixels;
    }
}