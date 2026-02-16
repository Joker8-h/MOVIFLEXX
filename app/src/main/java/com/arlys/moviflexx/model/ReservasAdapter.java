package com.arlys.moviflexx.model;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

import java.util.List;

public class ReservasAdapter extends RecyclerView.Adapter<ReservasAdapter.ReservaViewHolder> {

    // ─────────────────────────────────────────────────────────────────────────
    //  INTERFAZ
    // ─────────────────────────────────────────────────────────────────────────

    public interface OnReservaClickListener {
        void onReservaClick(JSONObject reserva);
        void onCancelarReserva(JSONObject reserva);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CAMPOS
    // ─────────────────────────────────────────────────────────────────────────

    private final Context                context;
    private final List<JSONObject>       reservas;
    private final OnReservaClickListener listener;

    public ReservasAdapter(Context context,
                           List<JSONObject> reservas,
                           OnReservaClickListener listener) {
        this.context  = context;
        this.reservas = reservas;
        this.listener = listener;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RECYCLER
    // ─────────────────────────────────────────────────────────────────────────

    @NonNull
    @Override
    public ReservaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_reserva, parent, false);
        return new ReservaViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ReservaViewHolder h, int position) {
        JSONObject reserva = reservas.get(position);

        // ── 1. Extraer objeto viaje y ruta (anidados) ─────────────────────────
        JSONObject viajeObj = reserva.optJSONObject("viaje");
        JSONObject rutaObj  = viajeObj != null ? viajeObj.optJSONObject("ruta") : null;

        // ── 2. Origen / Destino ───────────────────────────────────────────────
        // Prioridad: ruta anidada > viaje anidado > raíz de reserva
        String origen  = rutaObj  != null ? rutaObj.optString("origen",  "")
                : viajeObj != null ? viajeObj.optString("origen", "")
                : "";
        String destino = rutaObj  != null ? rutaObj.optString("destino",  "")
                : viajeObj != null ? viajeObj.optString("destino", "")
                : "";
        if (origen.isEmpty())  origen  = reserva.optString("origen",  "Origen");
        if (destino.isEmpty()) destino = reserva.optString("destino", "Destino");

        h.tvOrigen.setText("📍 " + origen);
        h.tvDestino.setText("📍 " + destino);

        // ── 3. Parada donde baja ──────────────────────────────────────────────
        String nombreParada = reserva.optString("nombreParada", "");
        if (nombreParada.isEmpty()) nombreParada = destino;
        h.tvParada.setText("🔵 Bajarás en: " + nombreParada);

        // ── 4. Asientos ───────────────────────────────────────────────────────
        int numAsientos = reserva.optInt("numeroAsientos",
                reserva.optInt("asientos",
                        reserva.optInt("cantidadAsientos", 1)));
        h.tvAsientos.setText("👥 " + numAsientos + " asiento"
                + (numAsientos == 1 ? "" : "s"));

        // ── 5. Código de reserva ──────────────────────────────────────────────
        String codigo = reserva.optString("codigoReserva",
                reserva.optString("codigo",
                        reserva.optString("codigoreserva", "")));
        if (!codigo.isEmpty()) {
            h.tvCodigo.setText("Código: " + codigo);
            h.tvCodigo.setVisibility(View.VISIBLE);
        } else {
            h.tvCodigo.setVisibility(View.GONE);
        }

        // ── 6. Fecha de salida ────────────────────────────────────────────────
        String fechaHora = "";
        if (viajeObj != null) {
            fechaHora = viajeObj.optString("fechaHoraSalida",
                    viajeObj.optString("fechaSalida",
                            viajeObj.optString("fecha", "")));
        }
        if (fechaHora.isEmpty())
            fechaHora = reserva.optString("fechaHoraSalida",
                    reserva.optString("fechaSalida", ""));

        if (!fechaHora.isEmpty()) {
            h.tvFecha.setText("📅 " + formatearFecha(fechaHora));
            h.tvFecha.setVisibility(View.VISIBLE);
        } else {
            h.tvFecha.setVisibility(View.GONE);
        }

        // ── 7. Precio ─────────────────────────────────────────────────────────
        double precio = 0;
        if (viajeObj != null) precio = viajeObj.optDouble("precio", 0);
        if (precio == 0)      precio = reserva.optDouble("precio",  0);
        if (precio == 0)      precio = reserva.optDouble("total",   0);

        if (precio > 0) {
            h.tvPrecio.setText("💵 $" + String.format("%,.0f", precio));
            h.tvPrecio.setVisibility(View.VISIBLE);
        } else {
            h.tvPrecio.setVisibility(View.GONE);
        }

        // ── 8. Estado ─────────────────────────────────────────────────────────
        String estado = reserva.optString("estado", "DESCONOCIDO").trim().toUpperCase();
        aplicarEstadoVisual(h, estado);

        // ── 9. Botón cancelar ─────────────────────────────────────────────────
        boolean puedeAnular = estado.equals("ACTIVA")
                || estado.equals("CONFIRMADA")
                || estado.equals("PENDIENTE");

        h.btnCancelar.setVisibility(puedeAnular ? View.VISIBLE : View.GONE);
        if (puedeAnular) {
            h.btnCancelar.setOnClickListener(v -> {
                if (listener != null) listener.onCancelarReserva(reserva);
            });
        }

        // ── 10. Click en tarjeta ──────────────────────────────────────────────
        h.card.setOnClickListener(v -> {
            if (listener != null) listener.onReservaClick(reserva);
        });
    }

    @Override
    public int getItemCount() { return reservas.size(); }

    // ─────────────────────────────────────────────────────────────────────────
    //  APLICAR COLORES SEGÚN ESTADO (todos desde datos reales)
    // ─────────────────────────────────────────────────────────────────────────

    private void aplicarEstadoVisual(ReservaViewHolder h, String estado) {
        String  etiqueta;
        int     colorTexto;
        int     colorFondo;

        switch (estado) {
            case "ACTIVA":
            case "CONFIRMADA":
                etiqueta   = "✅ Confirmada";
                colorTexto = Color.parseColor("#2E7D32");
                colorFondo = Color.parseColor("#F1F8E9");
                break;
            case "PENDIENTE":
                etiqueta   = "⏳ Pendiente";
                colorTexto = Color.parseColor("#E65100");
                colorFondo = Color.parseColor("#FFF3E0");
                break;
            case "EN_CURSO":
            case "INICIADO":
                etiqueta   = "🚗 En curso";
                colorTexto = Color.parseColor("#1565C0");
                colorFondo = Color.parseColor("#E3F2FD");
                break;
            case "COMPLETADA":
            case "FINALIZADA":
            case "FINALIZADO":
                etiqueta   = "🏁 Completada";
                colorTexto = Color.parseColor("#00695C");
                colorFondo = Color.parseColor("#E0F2F1");
                break;
            case "CANCELADA":
            case "CANCELADO":
                etiqueta   = "❌ Cancelada";
                colorTexto = Color.parseColor("#C62828");
                colorFondo = Color.parseColor("#FFEBEE");
                break;
            default:
                etiqueta   = "📌 " + estado;
                colorTexto = Color.parseColor("#546E7A");
                colorFondo = Color.WHITE;
                break;
        }

        h.tvEstado.setText(etiqueta);
        h.tvEstado.setTextColor(colorTexto);
        h.card.setCardBackgroundColor(colorFondo);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FORMATEAR FECHA (desde API, sin hardcodear)
    // ─────────────────────────────────────────────────────────────────────────

    private String formatearFecha(String fechaHora) {
        if (fechaHora == null || fechaHora.isEmpty()) return "";
        try {
            // Soporta: "2026-02-20T14:30:00" o "2026-02-20 14:30:00"
            String normalizada = fechaHora.replace("T", " ");
            String[] partes    = normalizada.split(" ");
            if (partes.length >= 2) {
                String[] fecha = partes[0].split("-");
                String   hora  = partes[1].length() >= 5
                        ? partes[1].substring(0, 5) : partes[1];
                if (fecha.length == 3)
                    return fecha[2] + "/" + fecha[1] + "/" + fecha[0] + "  " + hora;
            }
            return fechaHora; // devolver sin modificar si no parsea
        } catch (Exception e) {
            return fechaHora;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VIEW HOLDER
    // ─────────────────────────────────────────────────────────────────────────

    static class ReservaViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView         tvOrigen, tvDestino, tvParada, tvAsientos;
        TextView         tvFecha, tvPrecio, tvEstado, tvCodigo;
        MaterialButton   btnCancelar;

        ReservaViewHolder(@NonNull View v) {
            super(v);
            card         = (MaterialCardView) v;
            tvOrigen     = v.findViewById(R.id.tv_origen);
            tvDestino    = v.findViewById(R.id.tv_destino);
            tvParada     = v.findViewById(R.id.tv_parada);
            tvAsientos   = v.findViewById(R.id.tv_asientos);
            tvFecha      = v.findViewById(R.id.tv_fecha);
            tvPrecio     = v.findViewById(R.id.tv_precio);
            tvEstado     = v.findViewById(R.id.tv_estado);
            tvCodigo     = v.findViewById(R.id.tv_codigo);
            btnCancelar  = v.findViewById(R.id.btn_cancelar);
        }
    }
}