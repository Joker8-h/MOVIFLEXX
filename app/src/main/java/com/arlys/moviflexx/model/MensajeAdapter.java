package com.arlys.moviflexx.model;

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

    private final List<Mensaje> mensajes;
    private final int           idUsuarioActual;

    public MensajeAdapter(List<Mensaje> mensajes, int idUsuarioActual) {
        this.mensajes        = mensajes;
        this.idUsuarioActual = idUsuarioActual;
    }

    @Override
    public int getItemViewType(int position) {
        Mensaje m = mensajes.get(position);
        return (m.getIdEmisor() == idUsuarioActual || m.isEsPropio())
                ? TIPO_ENVIADO : TIPO_RECIBIDO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TIPO_ENVIADO) {
            return new EnviadoVH(
                    inflater.inflate(R.layout.item_mensaje_enviado, parent, false));
        } else {
            return new RecibidoVH(
                    inflater.inflate(R.layout.item_mensaje_recibido, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        Mensaje m = mensajes.get(pos);

        if (holder instanceof EnviadoVH) {
            EnviadoVH vh = (EnviadoVH) holder;
            vh.tvTexto.setText(m.getContenido());
            vh.tvHora.setText(formatHora(m.getFechaEnvio()));

            if (m.isFallido()) {
                vh.ivEstado.setImageResource(R.drawable.ic_error);
                vh.ivEstado.setVisibility(View.VISIBLE);
                vh.ivEstado.setAlpha(1f);
            } else if (m.isEnviando()) {
                vh.ivEstado.setImageResource(R.drawable.ic_clock);
                vh.ivEstado.setVisibility(View.VISIBLE);
                vh.ivEstado.setAlpha(0.7f);
            } else {
                vh.ivEstado.setImageResource(R.drawable.ic_check_double);
                vh.ivEstado.setVisibility(View.VISIBLE);
                vh.ivEstado.setAlpha(0.9f);
            }

        } else if (holder instanceof RecibidoVH) {
            RecibidoVH vh = (RecibidoVH) holder;
            vh.tvTexto.setText(m.getContenido());
            vh.tvHora.setText(formatHora(m.getFechaEnvio()));

            String nombre = m.getNombreEmisor();
            if (nombre != null && !nombre.isEmpty()) {
                vh.tvNombre.setVisibility(View.VISIBLE);
                vh.tvNombre.setText(nombre);
            } else {
                vh.tvNombre.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() { return mensajes.size(); }

    private String formatHora(String iso) {
        if (iso == null || iso.isEmpty()) return "";
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
        if (iso.contains("T") && iso.length() >= 16) return iso.substring(11, 16);
        return "";
    }

    // ─── ViewHolders ─────────────────────────────────────────────────────────
    static class EnviadoVH extends RecyclerView.ViewHolder {
        TextView  tvTexto, tvHora;
        ImageView ivEstado;
        EnviadoVH(View v) {
            super(v);
            tvTexto  = v.findViewById(R.id.tvMensaje);
            tvHora   = v.findViewById(R.id.tvHora);
            ivEstado = v.findViewById(R.id.ivEstado);
        }
    }

    static class RecibidoVH extends RecyclerView.ViewHolder {
        TextView tvTexto, tvHora, tvNombre;
        RecibidoVH(View v) {
            super(v);
            tvTexto  = v.findViewById(R.id.tvMensaje);
            tvHora   = v.findViewById(R.id.tvHora);
            tvNombre = v.findViewById(R.id.tvNombre);
        }
    }
}