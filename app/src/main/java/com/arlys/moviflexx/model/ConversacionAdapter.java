package com.arlys.moviflexx.model;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversacionAdapter
        extends RecyclerView.Adapter<ConversacionAdapter.VH> {

    public interface OnClick { void onClick(Conversacion c); }

    private final List<Conversacion> lista;
    private final OnClick            listener;

    public ConversacionAdapter(List<Conversacion> lista, OnClick listener) {
        this.lista    = lista;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversacion, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH vh, int pos) {
        vh.bind(lista.get(pos), listener);
    }

    @Override
    public int getItemCount() { return lista.size(); }

    // ─── ViewHolder ───────────────────────────────────────────────────────────
    static class VH extends RecyclerView.ViewHolder {

        private final TextView tvAvatar;
        private final TextView tvNombre;
        private final TextView tvUltimo;
        private final TextView tvHora;
        private final TextView tvBadge;

        VH(View v) {
            super(v);
            tvAvatar = v.findViewById(R.id.tvAvatar);
            tvNombre = v.findViewById(R.id.tvNombre);
            tvUltimo = v.findViewById(R.id.tvUltimoMensaje);
            tvHora   = v.findViewById(R.id.tvHora);
            tvBadge  = v.findViewById(R.id.tvBadge);
        }

        void bind(Conversacion c, OnClick listener) {
            String nombre = c.getNombreContacto();
            tvNombre.setText(nombre.isEmpty() ? "Usuario" : nombre);
            tvAvatar.setText(nombre.isEmpty() ? "?"
                    : String.valueOf(nombre.charAt(0)).toUpperCase());

            String ultimo = c.getUltimoMensaje();
            tvUltimo.setText((ultimo == null || ultimo.isEmpty())
                    ? "Toca para chatear" : ultimo);

            tvHora.setText(formatHora(c.getFechaUltimo()));

            int nr = c.getNoLeidos();
            if (nr > 0) {
                tvBadge.setVisibility(View.VISIBLE);
                tvBadge.setText(nr > 99 ? "99+" : String.valueOf(nr));
            } else {
                tvBadge.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onClick(c));
        }

        private String formatHora(String iso) {
            if (iso == null || iso.isEmpty()) return "";
            String[] fmts = {
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
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
    }
}