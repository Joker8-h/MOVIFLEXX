package com.arlys.moviflexx.model;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ConversacionAdapter
        extends RecyclerView.Adapter<ConversacionAdapter.ViewHolder> {

    // ================= INTERFAZ CLICK =================
    public interface OnChatClickListener {
        void onChatClick(Conversacion conversacion);
    }

    private final List<Conversacion> lista;
    private final OnChatClickListener listener;

    // ================= CONSTRUCTOR =================
    public ConversacionAdapter(List<Conversacion> lista,
                               OnChatClickListener listener) {
        this.lista = lista;
        this.listener = listener;
    }

    // ================= VIEW HOLDER =================
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        LinearLayout card = new LinearLayout(parent.getContext());
        card.setPadding(30, 25, 30, 25);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(30);
        card.setBackground(bg);

        ImageView avatar = new ImageView(parent.getContext());
        avatar.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);

        LinearLayout center = new LinearLayout(parent.getContext());
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(30, 0, 0, 0);
        center.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));

        TextView nombre = new TextView(parent.getContext());
        nombre.setTypeface(null, Typeface.BOLD);
        nombre.setTextSize(16);

        TextView mensaje = new TextView(parent.getContext());
        mensaje.setTextColor(Color.GRAY);

        TextView hora = new TextView(parent.getContext());
        hora.setTextColor(Color.parseColor("#6A11CB"));
        hora.setTextSize(12);

        center.addView(nombre);
        center.addView(mensaje);

        card.addView(avatar);
        card.addView(center);
        card.addView(hora);

        return new ViewHolder(card, avatar, nombre, mensaje, hora);
    }

    // ================= BIND =================
    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int p) {
        Conversacion c = lista.get(p);

        h.nombre.setText(c.getNombre());
        h.mensaje.setText(c.getMensaje());
        h.hora.setText(c.getHora());

        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChatClick(c);
            }
        });
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    // ================= HOLDER =================
    static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView avatar;
        TextView nombre, mensaje, hora;

        ViewHolder(View v,
                   ImageView a,
                   TextView n,
                   TextView m,
                   TextView h) {
            super(v);
            avatar = a;
            nombre = n;
            mensaje = m;
            hora = h;
        }
    }
}
