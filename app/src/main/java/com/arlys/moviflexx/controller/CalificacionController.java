package com.arlys.moviflexx.controller;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Manager.CalificacionesManager;

public class CalificacionController {

    private static final String[][] CHIPS_CONDUCTOR = {
            {},
            {"Muy impuntual",     "Conducción peligrosa", "Trato grosero"},
            {"Puede mejorar",     "Comunicación baja",    "Aceptable"},
            {"Viaje correcto",    "Puntual",              "Sin novedades"},
            {"Muy buena exp.",    "Recomendado",          "Trato amable"},
            {"Excelente viaje",   "Lo repetiría",         "Conductor 5 estrellas"},
    };

    private static final String[][] CHIPS_PASAJERO = {
            {},
            {"Pasajero grosero",  "Muy impuntual",        "Dejó el carro sucio"},
            {"Puede mejorar",     "Comunicación baja",    "Aceptable"},
            {"Pasajero correcto", "Puntual",              "Sin comentarios"},
            {"Muy buena exp.",    "Recomendado",          "Trato amable"},
            {"Excelente pasajero","Lo llevaría de nuevo", "Pasajero 5 estrellas"},
    };

    private static final String[] ETIQUETAS = {
            "", "😤  Muy malo", "😕  Regular", "😊  Bueno", "😃  Muy bueno", "🏆  ¡Excelente!"
    };

    public interface OnCalificacionEnviadaListener {
        void onCalificacionEnviada(int puntuacion, String comentario);
    }

    /**
     * @param fotoCalificado  URL Cloudinary de la foto del usuario a calificar.
     *                        Puede ser null o "" — en ese caso se muestra la inicial.
     */
    public static void mostrarBottomSheetCalificar(
            Context context,
            int viajeId,
            int idCalificado,
            String nomCalificado,
            String fotoCalificado,
            int idCalificador,
            boolean esElConductorCalificando,
            OnCalificacionEnviadaListener callback) {

        View root = LayoutInflater.from(context)
                .inflate(R.layout.bottom_sheet_calificar, null);

        BottomSheetDialog dialog = new BottomSheetDialog(context,
                R.style.BottomSheetDialogThemeMoviFlexx);
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(false);

        BottomSheetBehavior<View> behavior =
                BottomSheetBehavior.from((View) root.getParent());
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);

        // ── Vistas ───────────────────────────────────────────────────────
        TextView         txtTitulo     = root.findViewById(R.id.txtTituloSheet);
        TextView         txtSubtitulo  = root.findViewById(R.id.txtSubtituloSheet);
        TextView         txtEtiqueta   = root.findViewById(R.id.txtEtiquetaPuntuacion);
        TextView         tvNombreSheet = root.findViewById(R.id.tvNombreCalificadoSheet);
        TextView         tvRolSheet    = root.findViewById(R.id.tvRolCalificadoSheet);
        MaterialCardView cardFoto      = root.findViewById(R.id.cardAvatarFotoSheet);
        MaterialCardView cardInicial   = root.findViewById(R.id.cardAvatarInicialSheet);
        ImageView        ivFoto        = root.findViewById(R.id.ivAvatarSheet);
        TextView         tvInicial     = root.findViewById(R.id.tvInicialSheet);
        ImageView[]      stars         = {
                root.findViewById(R.id.star1), root.findViewById(R.id.star2),
                root.findViewById(R.id.star3), root.findViewById(R.id.star4),
                root.findViewById(R.id.star5)
        };
        HorizontalScrollView scrollChips = root.findViewById(R.id.scrollChips);
        LinearLayout         layoutChips = root.findViewById(R.id.layoutChips);
        TextInputLayout      tilComent   = root.findViewById(R.id.tilComentario);
        TextInputEditText    etComent    = root.findViewById(R.id.etComentario);
        Button               btnEnviar   = root.findViewById(R.id.btnEnviarCalificacion);
        TextView             btnOmitir   = root.findViewById(R.id.btnOmitir);
        LinearLayout         layoutExito = root.findViewById(R.id.layoutExito);

        final String[][] chipsActivos = esElConductorCalificando
                ? CHIPS_PASAJERO : CHIPS_CONDUCTOR;

        // ── Nombre ───────────────────────────────────────────────────────
        String nombre = (nomCalificado != null && !nomCalificado.trim().isEmpty())
                ? nomCalificado.trim() : "Usuario";

        if (txtTitulo != null)
            txtTitulo.setText(esElConductorCalificando
                    ? "⭐ Califica a tu pasajero"
                    : "⭐ Califica al conductor");

        if (txtSubtitulo != null)
            txtSubtitulo.setText("¿Cómo fue tu experiencia?");

        if (tvNombreSheet != null)
            tvNombreSheet.setText(nombre);

        if (tvRolSheet != null)
            tvRolSheet.setText(esElConductorCalificando ? "Pasajero" : "Conductor");

        // ── Avatar: foto Cloudinary o inicial ────────────────────────────
        boolean tieneFoto = fotoCalificado != null
                && !fotoCalificado.trim().isEmpty()
                && !fotoCalificado.equalsIgnoreCase("null");

        if (tieneFoto) {
            if (cardFoto    != null) cardFoto.setVisibility(View.VISIBLE);
            if (cardInicial != null) cardInicial.setVisibility(View.GONE);
            if (ivFoto != null)
                Glide.with(context)
                        .load(fotoCalificado)
                        .circleCrop()
                        .placeholder(R.drawable.logomo)
                        .error(R.drawable.logomo)
                        .into(ivFoto);
        } else {
            if (cardFoto    != null) cardFoto.setVisibility(View.GONE);
            if (cardInicial != null) cardInicial.setVisibility(View.VISIBLE);
            if (tvInicial   != null)
                tvInicial.setText(nombre.isEmpty() ? "?"
                        : String.valueOf(nombre.charAt(0)).toUpperCase());
        }

        final int[] puntaje = {0};

        for (int i = 0; i < stars.length; i++) {
            final int p = i + 1;
            stars[i].setOnClickListener(v -> {
                puntaje[0] = p;
                actualizarEstrellas(stars, p);
                animarEtiqueta(txtEtiqueta, p);
                mostrarChips(context, layoutChips, scrollChips, p, chipsActivos);
                habilitarEnviar(btnEnviar, true);
            });
        }
        animarEntrada(stars);

        btnEnviar.setOnClickListener(v -> {
            if (puntaje[0] == 0) { sacudir(root.findViewById(R.id.layoutEstrellas)); return; }

            String comentario = (etComent.getText() != null)
                    ? etComent.getText().toString().trim() : "";

            btnEnviar.setEnabled(false);
            btnEnviar.setText("Enviando...");
            btnOmitir.setEnabled(false);

            new CalificacionesManager(context).enviarCalificacion(
                    viajeId, idCalificador, idCalificado, puntaje[0], comentario,
                    new CalificacionesManager.OnCalificacionListener() {
                        @Override public void onExito(int p, String msg) {
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .post(() -> mostrarExito(
                                            btnEnviar, btnOmitir, tilComent,
                                            root.findViewById(R.id.layoutEstrellas),
                                            scrollChips, layoutExito,
                                            () -> { if (callback != null)
                                                callback.onCalificacionEnviada(p, comentario);
                                                dialog.dismiss(); }));
                        }
                        @Override public void onError(String msg) {
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .post(() -> {
                                        btnEnviar.setEnabled(true);
                                        btnEnviar.setText("⭐  ENVIAR CALIFICACIÓN");
                                        btnOmitir.setEnabled(true);
                                        Toast.makeText(context, "❌ " + msg, Toast.LENGTH_LONG).show();
                                    });
                        }
                    });
        });

        btnOmitir.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // ── Estrellas ─────────────────────────────────────────────────────────

    private static void actualizarEstrellas(ImageView[] stars, int puntaje) {
        for (int i = 0; i < stars.length; i++) {
            boolean on = i < puntaje;
            stars[i].setImageResource(on ? R.drawable.ic_star_filled_gold : R.drawable.ic_star_empty_navy);
            if (on) popStar(stars[i], i);
            else { stars[i].setScaleX(1f); stars[i].setScaleY(1f); }
        }
    }

    private static void popStar(ImageView star, int delay) {
        star.setScaleX(0.1f); star.setScaleY(0.1f);
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator sx = ObjectAnimator.ofFloat(star, "scaleX", 0.1f, 1.35f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(star, "scaleY", 0.1f, 1.35f, 1f);
        sx.setInterpolator(new OvershootInterpolator(4.5f));
        sy.setInterpolator(new OvershootInterpolator(4.5f));
        set.playTogether(sx, sy);
        set.setDuration(350);
        set.setStartDelay(delay * 50L);
        set.start();
    }

    private static void animarEntrada(ImageView[] stars) {
        for (int i = 0; i < stars.length; i++) {
            stars[i].setAlpha(0f); stars[i].setTranslationY(20f);
            stars[i].animate().alpha(1f).translationY(0f)
                    .setDuration(260).setStartDelay(150 + i * 60L)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private static void sacudir(View layout) {
        if (layout == null) return;
        ObjectAnimator.ofFloat(layout, "translationX", 0f,-18f,18f,-12f,12f,-6f,6f,0f)
                .setDuration(450).start();
    }

    private static void animarEtiqueta(TextView txt, int p) {
        if (p < 1 || p > 5) { txt.setText(""); return; }
        txt.setAlpha(0f); txt.setText(ETIQUETAS[p]);
        txt.animate().alpha(1f).setDuration(200).start();
    }

    // ── Chips ─────────────────────────────────────────────────────────────

    private static void mostrarChips(Context ctx, LinearLayout layout,
                                     HorizontalScrollView scroll, int p, String[][] chips) {
        layout.removeAllViews();
        if (p < 1 || p > 5 || chips[p].length == 0) { ocultarChips(scroll); return; }
        int m = dp(ctx, 6), mEnd = dp(ctx, 8);
        for (String label : chips[p]) {
            TextView chip = new TextView(ctx);
            chip.setText(label); chip.setTextSize(12.5f);
            chip.setTextColor(Color.parseColor("#0E7C76"));
            chip.setBackground(ctx.getResources().getDrawable(R.drawable.bg_chip_sugerencia, ctx.getTheme()));
            chip.setPadding(dp(ctx,14), m, dp(ctx,14), m);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setTag(false); chip.setSingleLine(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(mEnd); chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                boolean sel = !(boolean) chip.getTag(); chip.setTag(sel);
                chip.setBackground(ctx.getResources().getDrawable(
                        sel ? R.drawable.bg_chip_sugerencia_selected : R.drawable.bg_chip_sugerencia, ctx.getTheme()));
                chip.setTextColor(sel ? Color.WHITE : Color.parseColor("#0E7C76"));
                chip.animate().scaleX(sel?1.06f:1f).scaleY(sel?1.06f:1f).setDuration(130).start();
            });
            layout.addView(chip);
        }
        scroll.setVisibility(View.VISIBLE);
        scroll.animate().alpha(1f).setDuration(200).start();
    }

    private static void ocultarChips(HorizontalScrollView scroll) {
        scroll.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> scroll.setVisibility(View.GONE)).start();
    }

    private static void habilitarEnviar(Button btn, boolean ok) {
        btn.setEnabled(ok);
        btn.animate().alpha(ok ? 1f : 0.4f).setDuration(200).start();
    }

    private static void mostrarExito(Button btnEnviar, TextView btnOmitir,
                                     View tilComent, View layoutStars,
                                     View scrollChips, LinearLayout layoutExito, Runnable onDone) {
        for (View v : new View[]{btnEnviar, btnOmitir, tilComent, scrollChips, layoutStars})
            if (v != null) v.animate().alpha(0f).setDuration(220)
                    .withEndAction(() -> v.setVisibility(View.GONE)).start();
        layoutExito.setVisibility(View.VISIBLE);
        layoutExito.setTranslationY(14f);
        layoutExito.animate().alpha(1f).translationY(0f)
                .setDuration(340).setStartDelay(200)
                .setInterpolator(new DecelerateInterpolator()).start();
        layoutExito.postDelayed(onDone, 1900);
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}