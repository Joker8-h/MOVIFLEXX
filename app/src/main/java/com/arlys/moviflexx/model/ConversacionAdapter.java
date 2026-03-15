package com.arlys.moviflexx.model;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Calendar;


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
    private final OnClickListener listener;
    private int lastAnimatedPosition = -1;

    public ConversacionAdapter(List<Conversacion> lista, OnClickListener listener) {
        this.lista = lista;
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

        // ── 1. AVATAR — foto real o inicial ──────────────────────────────────
        String nombre = c.getNombreContacto();
        String inicial = (nombre != null && !nombre.isEmpty())
                ? String.valueOf(nombre.charAt(0)).toUpperCase()
                : "?";
        int colorIdx = Math.abs((nombre != null ? nombre.hashCode() : 0)) % AVATAR_COLORS.length;
        h.txtInicial.setText(inicial);
        h.cardAvatar.setCardBackgroundColor(AVATAR_COLORS[colorIdx]);

        String fotoUrl = c.getFotoContacto();
        if (h.cardAvatarFoto != null && h.ivAvatarFoto != null) {
            if (fotoUrl != null && !fotoUrl.isEmpty() && !fotoUrl.equals("null")) {
                // Mostrar foto real, ocultar card de inicial
                h.cardAvatarFoto.setVisibility(View.VISIBLE);
                h.cardAvatar.setVisibility(View.GONE);
                Glide.with(h.itemView.getContext())
                        .load(fotoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.logomo)
                        .error(R.drawable.logomo)
                        .into(h.ivAvatarFoto);
            } else {
                // Fallback: mostrar inicial
                h.cardAvatarFoto.setVisibility(View.GONE);
                h.cardAvatar.setVisibility(View.VISIBLE);
            }
        }

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
            h.txtPreview.setText(c.isUltimoMensajeMio() ? "Tú: " + preview : preview);
            h.txtPreview.setTextColor(c.getMensajesNoLeidos() > 0
                    ? Color.parseColor("#2A4D48")
                    : Color.parseColor("#88A8A3"));

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
            h.txtHora.setTextColor(Color.parseColor("#1AB99F"));
            h.txtNombre.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            h.badgeItem.setVisibility(View.GONE);
            h.txtHora.setTextColor(Color.parseColor("#AABFBB"));
            h.txtNombre.setTypeface(null, android.graphics.Typeface.BOLD);
        }

        // ── 6. ANIMACIÓN — solo para ítems nuevos ──────────────────
        if (position > lastAnimatedPosition) {
            h.itemView.setTranslationY(30f);
            h.itemView.setAlpha(0f);
            h.itemView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .setStartDelay((long) Math.min(position, 3) * 40L)
                    .setInterpolator(new DecelerateInterpolator(1.4f))
                    .start();
            lastAnimatedPosition = position;
        } else {
            // Ya visible — no animar, solo asegurar estado correcto
            h.itemView.setTranslationY(0f);
            h.itemView.setAlpha(1f);
        }

        // ── 7. CLICK ──────────────────────────────────────────────────────────
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(c);
        });
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView cardAvatar, cardAvatarFoto, badgeItem;
        ImageView ivAvatarFoto;
        TextView txtInicial, txtNombre, txtPreview, txtTicks, txtHora, txtNoLeidosItem;

        VH(@NonNull View v) {
            super(v);
            cardAvatar = v.findViewById(R.id.cardAvatar);
            cardAvatarFoto = v.findViewById(R.id.cardAvatarFoto);
            ivAvatarFoto = v.findViewById(R.id.ivAvatarFoto);
            txtInicial = v.findViewById(R.id.txtInicialAvatar);
            txtNombre = v.findViewById(R.id.txtNombreContacto);
            txtPreview = v.findViewById(R.id.txtUltimoMensaje);
            txtTicks = v.findViewById(R.id.txtTicks);
            txtHora = v.findViewById(R.id.txtHora);
            badgeItem = v.findViewById(R.id.badgeItem);
            txtNoLeidosItem = v.findViewById(R.id.txtNoLeidosItem);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static String formatearHora(String fechaISO) {
        if (fechaISO == null || fechaISO.isEmpty()) return "";
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("America/Bogota");
        try {
            long num;
            try {
                num = Long.parseLong(fechaISO);
                if (num < 10_000_000_000L) num *= 1000L;
            } catch (NumberFormatException ignored) {
                SimpleDateFormat[] formatos = {
                        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()),
                        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
                };
                Date fecha = null;
                for (SimpleDateFormat fmt : formatos) {
                    fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC")); // backend manda en UTC
                    try {
                        fecha = fmt.parse(fechaISO);
                        break;
                    } catch (Exception ig) {
                    }
                }
                if (fecha == null) return "";
                num = fecha.getTime();
            }

            Calendar ahora = Calendar.getInstance(tz);
            Calendar fecha = Calendar.getInstance(tz);
            fecha.setTimeInMillis(num);
            long diff = ahora.getTimeInMillis() - num;
            long unDia = 86_400_000L;

            SimpleDateFormat sdf;
            if (diff < unDia && ahora.get(Calendar.DAY_OF_YEAR) == fecha.get(Calendar.DAY_OF_YEAR)) {
                sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            } else if (diff < 7 * unDia) {
                sdf = new SimpleDateFormat("EEE", new Locale("es", "CO"));
            } else {
                sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
            }
            sdf.setTimeZone(tz);
            return sdf.format(new Date(num));

        } catch (Exception e) {
            return "";
        }
    }
}