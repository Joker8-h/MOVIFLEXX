package com.arlys.moviflexx.model;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ConversacionAdapter — Paleta teal limpia.
 *
 * ❌ ELIMINADOS colores morados, azules y rosas de la paleta de avatares.
 * ✓  Solo tonos teal, verde, naranja y rojo — coherentes con Moviflexx.
 * ✓  Ticks honestos: ✓ gris, ✓✓ gris, ✓✓ teal (solo si backend confirma).
 * ✓  Preview "Sin mensajes aún" en gris claro cuando no hay mensajes.
 * ✓  Hora en teal SOLO cuando hay no leídos.
 * ✓  Animación de entrada suave sin parpadeo.
 */
public class ConversacionAdapter
        extends RecyclerView.Adapter<ConversacionAdapter.VH> {

    public interface OnClickListener {
        void onClick(Conversacion c);
    }

    // ── Paleta SIN morados — solo teal/verde/cálidos ─────────────────────────
    private static final int[] AVATAR_COLORS = {
            0xFF1AB99F,   // teal principal Moviflexx
            0xFF17A38D,   // teal oscuro
            0xFF26C6A8,   // teal claro
            0xFF2EB87A,   // verde menta
            0xFF43C59E,   // verde agua
            0xFFFF7B54,   // naranja Moviflexx
            0xFFFF9A3C,   // naranja claro
            0xFFE05C5C,   // rojo suave
            0xFF00897B,   // teal profundo
            0xFF4DB6AC,   // teal pastel
    };

    // ── Colores de ticks ──────────────────────────────────────────────────────
    private static final int COLOR_TICK_GRIS = 0xFF80AFAA;
    private static final int COLOR_TICK_TEAL = 0xFF1AB99F;

    private final List<Conversacion> lista;
    private final OnClickListener    listener;
    private int lastAnimatedPosition = -1;

    public ConversacionAdapter(List<Conversacion> lista, OnClickListener listener) {
        this.lista    = lista;
        this.listener = listener;
    }

    public void resetAnimations() {
        lastAnimatedPosition = -1;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversacion, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Conversacion c = lista.get(position);

        // ── 1. AVATAR ─────────────────────────────────────────────────────────
        String nombre  = c.getNombreContacto();
        String inicial = (nombre != null && !nombre.isEmpty())
                ? String.valueOf(nombre.charAt(0)).toUpperCase()
                : "?";
        // Color determinista por nombre — NUNCA morado
        int colorIdx = Math.abs((nombre != null ? nombre.hashCode() : 0)) % AVATAR_COLORS.length;
        h.txtInicial.setText(inicial);
        h.cardAvatar.setCardBackgroundColor(AVATAR_COLORS[colorIdx]);

        // ── 2. NOMBRE ─────────────────────────────────────────────────────────
        h.txtNombre.setText(nombre != null ? nombre : "");

        // ── 3. PREVIEW ────────────────────────────────────────────────────────
        String preview = c.getUltimoMensaje();
        boolean sinMensajes = preview == null || preview.trim().isEmpty();

        if (sinMensajes) {
            h.txtPreview.setText("Sin mensajes aún");
            h.txtPreview.setTextColor(Color.parseColor("#BBCDC9"));
            h.txtTicks.setVisibility(View.GONE);
        } else {
            // Prefijo "Tú: " si el último mensaje es mío
            h.txtPreview.setText(c.isUltimoMensajeMio() ? "Tú: " + preview : preview);
            h.txtPreview.setTextColor(c.getMensajesNoLeidos() > 0
                    ? Color.parseColor("#2A4D48")
                    : Color.parseColor("#88A8A3"));

            // Ticks honestos
            if (c.isUltimoMensajeMio()) {
                h.txtTicks.setVisibility(View.VISIBLE);
                if (c.isUltimoMensajeLeidoPorContacto()) {
                    h.txtTicks.setText("✓✓ ");
                    h.txtTicks.setTextColor(COLOR_TICK_TEAL);
                } else {
                    h.txtTicks.setText("✓✓ ");
                    h.txtTicks.setTextColor(COLOR_TICK_GRIS);
                }
            } else {
                h.txtTicks.setVisibility(View.GONE);
            }
        }

        // ── 4. HORA ───────────────────────────────────────────────────────────
        h.txtHora.setText(formatearHora(c.getFechaUltimoMensaje()));

        // ── 5. BADGE NO LEÍDOS ────────────────────────────────────────────────
        int noLeidos = c.getMensajesNoLeidos();
        if (noLeidos > 0) {
            h.badgeItem.setVisibility(View.VISIBLE);
            h.txtNoLeidosItem.setText(noLeidos > 99 ? "99+" : String.valueOf(noLeidos));
            h.txtHora.setTextColor(Color.parseColor("#1AB99F"));   // teal
            h.txtNombre.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            h.badgeItem.setVisibility(View.GONE);
            h.txtHora.setTextColor(Color.parseColor("#AABFBB"));   // gris suave
            h.txtNombre.setTypeface(null, android.graphics.Typeface.BOLD);
        }

        // ── 6. ANIMACIÓN DE ENTRADA ───────────────────────────────────────────
        if (position > lastAnimatedPosition) {
            h.itemView.setTranslationY(50f);
            h.itemView.setAlpha(0f);
            h.itemView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(250)
                    .setStartDelay((long) Math.min(position, 7) * 35L)
                    .setInterpolator(new DecelerateInterpolator(1.4f))
                    .start();
            lastAnimatedPosition = position;
        }

        // ── 7. CLICK ─────────────────────────────────────────────────────────
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(c);
        });
    }

    @Override
    public int getItemCount() { return lista.size(); }

    // ─────────────────────────────────────────────────────────────────────────
    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView cardAvatar, badgeItem;
        TextView txtInicial, txtNombre, txtPreview, txtTicks, txtHora, txtNoLeidosItem;

        VH(@NonNull View v) {
            super(v);
            cardAvatar      = v.findViewById(R.id.cardAvatar);
            txtInicial      = v.findViewById(R.id.txtInicialAvatar);
            txtNombre       = v.findViewById(R.id.txtNombreContacto);
            txtPreview      = v.findViewById(R.id.txtUltimoMensaje);
            txtTicks        = v.findViewById(R.id.txtTicks);
            txtHora         = v.findViewById(R.id.txtHora);
            badgeItem       = v.findViewById(R.id.badgeItem);
            txtNoLeidosItem = v.findViewById(R.id.txtNoLeidosItem);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static String formatearHora(String fechaISO) {
        if (fechaISO == null || fechaISO.isEmpty()) return "";
        try {
            // Epoch numérico
            try {
                long num = Long.parseLong(fechaISO);
                if (num < 10_000_000_000L) num *= 1000L;
                long ahora = System.currentTimeMillis();
                long diff  = ahora - num;
                long unDia = 86_400_000L;
                if (diff < unDia)
                    return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(num));
                else if (diff < 7 * unDia)
                    return new SimpleDateFormat("EEE", new Locale("es", "CO")).format(new Date(num));
                else
                    return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date(num));
            } catch (NumberFormatException ignored) {}

            // ISO 8601
            SimpleDateFormat[] formatos = {
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",   Locale.getDefault()),
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()),
            };
            Date fecha = null;
            for (SimpleDateFormat fmt : formatos) {
                try { fecha = fmt.parse(fechaISO); break; } catch (Exception ig) {}
            }
            if (fecha == null) return "";

            long ahora = System.currentTimeMillis();
            long diff  = ahora - fecha.getTime();
            long unDia = 86_400_000L;
            if (diff < unDia)
                return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(fecha);
            else if (diff < 7 * unDia)
                return new SimpleDateFormat("EEE", new Locale("es", "CO")).format(fecha);
            else
                return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(fecha);
        } catch (Exception e) {
            return "";
        }
    }
}