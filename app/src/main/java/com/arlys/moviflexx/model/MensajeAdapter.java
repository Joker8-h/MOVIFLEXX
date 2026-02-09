package com.arlys.moviflexx.model;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MensajeAdapter extends RecyclerView.Adapter<MensajeAdapter.ViewHolder> {

    private final List<Mensaje> mensajes;

    public MensajeAdapter(List<Mensaje> mensajes) {
        this.mensajes = mensajes;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        LinearLayout root = new LinearLayout(parent.getContext());
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.setPadding(16, 8, 16, 8);

        TextView texto = new TextView(parent.getContext());
        texto.setTextSize(15);
        texto.setPadding(25, 15, 25, 15);

        root.addView(texto);

        return new ViewHolder(root, texto);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {

        Mensaje m = mensajes.get(position);
        h.texto.setText(m.getMensaje());

        boolean esMio = m.getEmisor().equalsIgnoreCase("yo");

        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) h.texto.getLayoutParams();

        GradientDrawable bubble = new GradientDrawable();
        bubble.setCornerRadius(30);

        if (esMio) {
            params.gravity = Gravity.END;
            bubble.setColor(Color.parseColor("#DCF8C6")); // verde tipo WhatsApp
        } else {
            params.gravity = Gravity.START;
            bubble.setColor(Color.WHITE);
        }

        h.texto.setLayoutParams(params);
        h.texto.setBackground(bubble);
    }

    @Override
    public int getItemCount() {
        return mensajes.size();
    }

    // ================= HOLDER =================
    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView texto;

        ViewHolder(View v, TextView t) {
            super(v);
            texto = t;
        }
    }
}
