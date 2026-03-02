package com.arlys.moviflexx.model;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;

/**
 * OnboardingAdapter — 3 slides para el carrusel premium de MoviFlexx
 */
public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.SlideHolder> {

    // ── Contenido de los 3 slides ────────────────────────────────────────
    private static final String[] EMOJIS       = {"🚌", "🗺️", "⭐"};
    private static final String[] CARD1_ICONS  = {"🛡️", "📍", "💬"};
    private static final String[] CARD1_TEXTS  = {"Viaje seguro", "GPS en vivo", "Comunidad real"};
    private static final String[] CARD2_ICONS  = {"⚡", "🔔", "🏆"};
    private static final String[] CARD2_TEXTS  = {"Rápido", "Al instante", "Top rated"};

    private static final String[] TITULOS = {
            "Viaja con tu\ncomunidad",
            "Rutas\ninteligentes",
            "Calificaciones\nconfiables"
    };

    private static final String[] SUBTITULOS = {
            "Conecta con vecinos. Viaja de forma económica, segura y sostenible cada día.",
            "Encuentra rutas cercanas en segundos. Todo en tiempo real desde tu barrio.",
            "Conductores y pasajeros verificados por la comunidad. Confía en quien viaja."
    };

    // stats[slide][index] = {valor, etiqueta}
    private static final String[][][] STATS = {
            {{"10K+", "Usuarios"}, {"50+", "Rutas"},       {"4.9★", "Rating"}},
            {{"<2min", "Espera"},  {"GPS", "Tiempo real"}, {"100%", "Cobertura"}},
            {{"98%", "Satisf."},   {"✓", "Verificado"},    {"🔒", "Seguro"}}
    };

    @NonNull
    @Override
    public SlideHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_slide_onboarding, parent, false);
        return new SlideHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideHolder h, int position) {
        h.emoji.setText(EMOJIS[position]);
        h.titulo.setText(TITULOS[position]);
        h.subtitulo.setText(SUBTITULOS[position]);
        h.val1.setText(STATS[position][0][0]);
        h.lbl1.setText(STATS[position][0][1]);
        h.val2.setText(STATS[position][1][0]);
        h.lbl2.setText(STATS[position][1][1]);
        h.val3.setText(STATS[position][2][0]);
        h.lbl3.setText(STATS[position][2][1]);

        // Animación de entrada del contenido
        h.itemView.setAlpha(0f);
        h.itemView.setTranslationY(24f);
        h.itemView.animate().alpha(1f).translationY(0f)
                .setDuration(380).setStartDelay(60).start();
    }

    @Override
    public int getItemCount() { return 3; }

    static class SlideHolder extends RecyclerView.ViewHolder {
        TextView emoji, card1Icon, card1Text, card2Icon, card2Text;
        TextView titulo, subtitulo;
        TextView val1, lbl1, val2, lbl2, val3, lbl3;

        SlideHolder(@NonNull View v) {
            super(v);
            emoji      = v.findViewById(R.id.slideEmoji);
            titulo     = v.findViewById(R.id.slideTitulo);
            subtitulo  = v.findViewById(R.id.slideSubtitulo);
            val1       = v.findViewById(R.id.statVal1);
            lbl1       = v.findViewById(R.id.statLbl1);
            val2       = v.findViewById(R.id.statVal2);
            lbl2       = v.findViewById(R.id.statLbl2);
            val3       = v.findViewById(R.id.statVal3);
            lbl3       = v.findViewById(R.id.statLbl3);
        }
    }
}