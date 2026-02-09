package com.arlys.moviflexx.controller;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;

import org.json.JSONObject;

import java.util.List;

public class ViajesAdapter extends RecyclerView.Adapter<ViajesAdapter.ViajeViewHolder> {

    private final Context context;
    private final List<JSONObject> viajes;
    private final boolean esConductor;

    public ViajesAdapter(Context context, List<JSONObject> viajes) {
        this.context = context;
        this.viajes = viajes;
        this.esConductor = new SessionManager(context).isConductor();
    }

    @NonNull
    @Override
    public ViajeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_viaje, parent, false);
        return new ViajeViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViajeViewHolder h, int position) {

        JSONObject v = viajes.get(position);

        JSONObject ruta = v.optJSONObject("ruta");
        JSONObject conductor = v.optJSONObject("conductor");
        JSONObject vehiculo = v.optJSONObject("vehiculo");

        int viajeId = v.optInt("idViajes", v.optInt("id"));
        String estado = v.optString("estado", "CREADO");

        // ===== TEXTOS =====
        h.txtRuta.setText(
                ruta != null
                        ? ruta.optString("origen") + " → " + ruta.optString("destino")
                        : "Ruta no disponible"
        );

        h.txtFecha.setText("🕒 " + v.optString("fechaHoraSalida", "--"));
        h.txtCupos.setText("👥 Cupos: " + v.optInt("cuposDisponibles", 0));
        h.txtPrecio.setText("💰 $" + v.optDouble("precio", 0));

        h.txtConductor.setText(
                "👤 " + (conductor != null ? conductor.optString("nombre", "Conductor") : "Conductor")
        );

        h.txtTelefono.setText(
                "📞 " + (conductor != null ? conductor.optString("telefono", "N/A") : "N/A")
        );

        h.txtVehiculo.setText(
                "🚘 " + (vehiculo != null
                        ? vehiculo.optString("marca", "") + " "
                        + vehiculo.optString("modelo", "")
                        + " (" + vehiculo.optString("placa", "---") + ")"
                        : "Vehículo no asignado")
        );

        h.txtEstado.setText("📌 " + estado);

        // ===== RESET BOTONES =====
        h.btnAceptar.setVisibility(View.GONE);
        h.btnIniciar.setVisibility(View.GONE);
        h.btnFinalizar.setVisibility(View.GONE);
        h.btnCancelar.setVisibility(View.GONE);
        h.btnDetalle.setVisibility(View.GONE);

        // ===== LÓGICA POR ROL =====
        if (esConductor) {

            switch (estado) {
                case "CREADO":
                    h.btnIniciar.setVisibility(View.VISIBLE);
                    h.btnCancelar.setVisibility(View.VISIBLE);
                    break;

                case "INICIADO":
                    h.btnFinalizar.setVisibility(View.VISIBLE);
                    break;

                default:
                    h.btnDetalle.setVisibility(View.VISIBLE);
                    break;
            }

        } else {
            // PASAJERO
            if (v.optInt("cuposDisponibles", 0) > 0) {
                h.btnAceptar.setVisibility(View.VISIBLE);
            }
            h.btnDetalle.setVisibility(View.VISIBLE);
        }

        // ===== ACCIONES =====
        h.btnAceptar.setOnClickListener(vw -> reservarViaje(viajeId));
        h.btnIniciar.setOnClickListener(vw -> cambiarEstado(viajeId, "iniciar"));
        h.btnFinalizar.setOnClickListener(vw -> cambiarEstado(viajeId, "finalizar"));
        h.btnCancelar.setOnClickListener(vw -> cambiarEstado(viajeId, "cancelar"));

        h.btnDetalle.setOnClickListener(vw -> abrirDetalle(viajeId));

        // 🔥 CLICK EN TODA LA CARD
        h.itemView.setOnClickListener(vw -> abrirDetalle(viajeId));
    }

    // ================= DETALLE =================
    private void abrirDetalle(int viajeId) {
        Intent i = new Intent(context, DetalleViajeActivity.class);
        i.putExtra("ID_VIAJE", viajeId);
        context.startActivity(i);
    }

    // ================= RESERVA =================
    private void reservarViaje(int viajeId) {
        try {
            JSONObject body = new JSONObject();
            body.put("viajeId", viajeId);

            ConexionApi.getInstance(context).post(
                    Constantes.RESERVAS,
                    body,
                    response -> Toast.makeText(
                            context,
                            "Reserva realizada correctamente ✅",
                            Toast.LENGTH_LONG
                    ).show(),
                    error -> Toast.makeText(
                            context,
                            "No se pudo reservar",
                            Toast.LENGTH_LONG
                    ).show()
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= CAMBIAR ESTADO =================
    private void cambiarEstado(int viajeId, String accion) {

        String url = "/api/viajes/" + viajeId + "/" + accion;

        ConexionApi.getInstance(context).post(
                url,
                null,
                response -> Toast.makeText(
                        context,
                        "Estado actualizado correctamente",
                        Toast.LENGTH_SHORT
                ).show(),
                error -> Toast.makeText(
                        context,
                        "Error cambiando estado",
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    @Override
    public int getItemCount() {
        return viajes.size();
    }

    // ================= HOLDER =================
    static class ViajeViewHolder extends RecyclerView.ViewHolder {

        TextView txtRuta, txtFecha, txtCupos, txtPrecio;
        TextView txtConductor, txtTelefono, txtVehiculo, txtEstado;

        Button btnAceptar, btnIniciar, btnFinalizar, btnCancelar, btnDetalle;

        public ViajeViewHolder(@NonNull View itemView) {
            super(itemView);

            txtRuta = itemView.findViewById(R.id.txtRuta);
            txtFecha = itemView.findViewById(R.id.txtFecha);
            txtCupos = itemView.findViewById(R.id.txtCupos);
            txtPrecio = itemView.findViewById(R.id.txtPrecio);
            txtConductor = itemView.findViewById(R.id.txtConductor);
            txtTelefono = itemView.findViewById(R.id.txtTelefono);
            txtVehiculo = itemView.findViewById(R.id.txtVehiculo);
            txtEstado = itemView.findViewById(R.id.txtEstado);

            btnAceptar = itemView.findViewById(R.id.btnAceptar);
            btnIniciar = itemView.findViewById(R.id.btnIniciar);
            btnFinalizar = itemView.findViewById(R.id.btnFinalizar);
            btnCancelar = itemView.findViewById(R.id.btnCancelar);
            btnDetalle = itemView.findViewById(R.id.btnDetalle);
        }
    }
}
