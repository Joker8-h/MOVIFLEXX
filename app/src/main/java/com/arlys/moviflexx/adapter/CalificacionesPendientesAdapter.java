package com.arlys.moviflexx.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.controller.HomeConductor.PasajeroPendiente;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.function.Consumer;


public class CalificacionesPendientesAdapter
        extends RecyclerView.Adapter<CalificacionesPendientesAdapter.ViewHolder> {

    private final Context                   context;
    private final List<PasajeroPendiente>   lista;
    private final Consumer<PasajeroPendiente> onCalificar;

    public CalificacionesPendientesAdapter(Context context,
                                           List<PasajeroPendiente> lista,
                                           Consumer<PasajeroPendiente> onCalificar) {
        this.context     = context;
        this.lista       = lista;
        this.onCalificar = onCalificar;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_calificacion_pendiente, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        PasajeroPendiente p = lista.get(position);

        // Inicial del nombre para el avatar
        String inicial = p.nombrePasajero != null && !p.nombrePasajero.isEmpty()
                ? String.valueOf(p.nombrePasajero.charAt(0)).toUpperCase()
                : "P";

        h.tvInicial.setText(inicial);
        h.tvNombre.setText(p.nombrePasajero);
        h.tvViaje.setText("Viaje #" + p.viajeId);

        h.btnCalificar.setOnClickListener(v -> {
            if (onCalificar != null) onCalificar.accept(p);
        });
    }

    @Override
    public int getItemCount() {
        return lista != null ? lista.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView       tvInicial;
        final TextView       tvNombre;
        final TextView       tvViaje;
        final MaterialButton btnCalificar;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInicial    = itemView.findViewById(R.id.tv_inicial_pasajero);
            tvNombre     = itemView.findViewById(R.id.tv_nombre_pasajero);
            tvViaje      = itemView.findViewById(R.id.tv_viaje_pendiente);
            btnCalificar = itemView.findViewById(R.id.btn_calificar_pendiente);
        }
    }
}