package com.arlys.moviflexx.model;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MensajeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TIPO_ENVIADO  = 1;
    private static final int TIPO_RECIBIDO = 2;

    // Callback: Chat.java lo usa para saber qué mensajes fueron vistos por el usuario
    public interface OnMensajeVisto {
        void onVisto(Mensaje mensaje);
    }

    private final List<Mensaje>  mensajes;
    private final int            idUsuarioActual;
    private       OnMensajeVisto callbackVisto;

    public MensajeAdapter(List<Mensaje> mensajes, int idUsuarioActual) {
        this.mensajes        = mensajes;
        this.idUsuarioActual = idUsuarioActual;
    }

    public void setOnMensajeVistoListener(OnMensajeVisto cb) {
        this.callbackVisto = cb;
    }

    @Override
    public int getItemViewType(int pos) {
        Mensaje m = mensajes.get(pos);
        return (m.getIdEmisor() == idUsuarioActual || m.isEsPropio())
                ? TIPO_ENVIADO : TIPO_RECIBIDO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TIPO_ENVIADO) {
            return new EnviadoVH(inf.inflate(R.layout.item_mensaje_enviado, parent, false));
        } else {
            return new RecibidoVH(inf.inflate(R.layout.item_mensaje_recibido, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        Mensaje m = mensajes.get(pos);

        if (holder instanceof EnviadoVH) {
            bindEnviado((EnviadoVH) holder, m);
        } else if (holder instanceof RecibidoVH) {
            bindRecibido((RecibidoVH) holder, m);
        }
    }

    // ─── MENSAJE ENVIADO ─────────────────────────────────────────────────────
    private void bindEnviado(EnviadoVH vh, Mensaje m) {
        vh.tvTexto.setText(m.getContenido());
        vh.tvHora.setText(formatHora(m.getFechaEnvio()));

        if (vh.ivEstado == null) return;

        if (m.isFallido()) {
            // ✗ Error — rojo
            vh.ivEstado.setVisibility(View.VISIBLE);
            vh.ivEstado.setImageResource(android.R.drawable.ic_delete);
            vh.ivEstado.setColorFilter(Color.parseColor("#F44336"));
            vh.ivEstado.setAlpha(1f);

        } else if (m.isEnviando()) {
            // ◷ Enviando — reloj gris
            vh.ivEstado.setVisibility(View.VISIBLE);
            vh.ivEstado.setImageResource(android.R.drawable.ic_menu_recent_history);
            vh.ivEstado.clearColorFilter();
            vh.ivEstado.setAlpha(0.55f);

        } else if (m.isLeido()) {
            // ✓✓ Leído — doble tick teal
            vh.ivEstado.setVisibility(View.VISIBLE);
            vh.ivEstado.setImageResource(R.drawable.ic_check_double);
            vh.ivEstado.setColorFilter(Color.parseColor("#00897B"));
            vh.ivEstado.setAlpha(1f);

        } else {
            // ✓✓ Entregado, no leído — doble tick gris
            vh.ivEstado.setVisibility(View.VISIBLE);
            vh.ivEstado.setImageResource(R.drawable.ic_check_double);
            vh.ivEstado.clearColorFilter();
            vh.ivEstado.setAlpha(0.45f);
        }
    }

    // ─── MENSAJE RECIBIDO ────────────────────────────────────────────────────
    private void bindRecibido(RecibidoVH vh, Mensaje m) {
        vh.tvTexto.setText(m.getContenido());
        vh.tvHora.setText(formatHora(m.getFechaEnvio()));

        // Nombre del emisor
        if (vh.tvNombre != null) {
            String nombre = m.getNombreEmisor();
            if (nombre != null && !nombre.isEmpty()) {
                vh.tvNombre.setVisibility(View.VISIBLE);
                vh.tvNombre.setText(nombre);
            } else {
                vh.tvNombre.setVisibility(View.GONE);
            }
        }

        // ── Indicador visual de NO LEÍDO ──────────────────────────────────────
        if (!m.isLeido()) {
            // Punto verde visible
            if (vh.dotNoLeido != null) vh.dotNoLeido.setVisibility(View.VISIBLE);
            // Fondo ligeramente destacado
            vh.itemView.setBackgroundColor(Color.parseColor("#F0FFF8"));

            // Notificar a Chat.java que el usuario vio este mensaje en pantalla
            if (callbackVisto != null) callbackVisto.onVisto(m);

        } else {
            // Ya leído — sin indicadores especiales
            if (vh.dotNoLeido != null) vh.dotNoLeido.setVisibility(View.GONE);
            vh.itemView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    public int getItemCount() { return mensajes.size(); }

    // ─────────────────────────────────────────────────────────────────────────
    //  FORMATO DE HORA
    // ─────────────────────────────────────────────────────────────────────────
    private String formatHora(String iso) {
        if (iso == null || iso.isEmpty()) return "";

        // Timestamp en milisegundos (nuestro fallback local)
        try {
            long ms = Long.parseLong(iso);
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
        } catch (NumberFormatException ignored) {}

        // Formatos ISO que puede devolver el backend
        String[] fmts = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss"
        };
        SimpleDateFormat out = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (String fmt : fmts) {
            try {
                Date d = new SimpleDateFormat(fmt, Locale.US).parse(iso);
                if (d != null) return out.format(d);
            } catch (ParseException ignored) {}
        }

        // Último recurso: extraer HH:mm del string ISO
        if (iso.contains("T") && iso.length() >= 16) return iso.substring(11, 16);
        return "";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VIEW HOLDERS
    // ─────────────────────────────────────────────────────────────────────────
    static class EnviadoVH extends RecyclerView.ViewHolder {
        TextView  tvTexto, tvHora;
        ImageView ivEstado;   // tick de estado (enviando / entregado / leído / error)

        EnviadoVH(View v) {
            super(v);
            tvTexto  = v.findViewById(R.id.tvMensaje);
            tvHora   = v.findViewById(R.id.tvHora);
            ivEstado = v.findViewById(R.id.ivEstado);
        }
    }

    static class RecibidoVH extends RecyclerView.ViewHolder {
        TextView tvTexto, tvHora, tvNombre;
        View     dotNoLeido;   // punto verde de "no leído"

        RecibidoVH(View v) {
            super(v);
            tvTexto    = v.findViewById(R.id.tvMensaje);
            tvHora     = v.findViewById(R.id.tvHora);
            tvNombre   = v.findViewById(R.id.tvNombre);
            dotNoLeido = v.findViewById(R.id.dot_no_leido);
        }
    }
}