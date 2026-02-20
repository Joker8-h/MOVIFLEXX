package com.arlys.moviflexx.adapter;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.controller.DetalleViajeActivity;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ViajesAdapter — adaptado a las reglas de visibilidad de MoviFlex:
 *
 *  CONDUCTOR:
 *    CREADO    → muestra [Iniciar] [Cancelar]  (aún no visible para pasajeros)
 *    EN_CURSO  → muestra [Finalizar]           (pasajeros lo pueden ver)
 *    FINALIZADO/CANCELADO → muestra [Detalle]
 *
 *  PASAJERO:
 *    Solo se muestran viajes EN_CURSO con cupos > 0.
 *    (El filtrado principal ocurre en MisReservasActivity antes de llegar aquí,
 *     pero el adapter refuerza la lógica de botones para seguridad.)
 */
public class ViajesAdapter extends RecyclerView.Adapter<ViajesAdapter.ViajeViewHolder> {

    private static final String TAG = "ViajesAdapter";

    // ── Estados del viaje ─────────────────────────────────────────────────────
    private static final String EST_CREADO     = "CREADO";
    private static final String EST_PROGRAMADO = "PROGRAMADO";   // alias backend
    private static final String EST_DISPONIBLE = "DISPONIBLE";   // alias backend
    private static final String EST_EN_CURSO   = "EN_CURSO";
    private static final String EST_INICIADO   = "INICIADO";     // alias backend
    private static final String EST_FINALIZADO = "FINALIZADO";
    private static final String EST_CANCELADO  = "CANCELADO";

    // ── Listener para resaltar ruta en mapa ──────────────────────────────────
    public interface OnViajeClickListener {
        void onViajeClick(int viajeId);
    }

    private final Context          context;
    private final List<JSONObject> viajes;
    private final boolean          esConductor;
    private final Map<Integer, String> cacheConductores = new HashMap<>();

    private OnViajeClickListener viajeClickListener;

    public ViajesAdapter(Context context, List<JSONObject> viajes) {
        this.context     = context;
        this.viajes      = viajes;
        this.esConductor = new SessionManager(context).isConductor();
    }

    public void setOnViajeClickListener(OnViajeClickListener listener) {
        this.viajeClickListener = listener;
    }

    // =========================================================================
    //  RECYCLER
    // =========================================================================
    @NonNull
    @Override
    public ViajeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_viaje, parent, false);
        return new ViajeViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViajeViewHolder h, int position) {
        JSONObject v = viajes.get(position);
        Log.d(TAG, "bind[" + position + "] = " + v.toString());

        JSONObject ruta      = v.optJSONObject("ruta");
        JSONObject vehiculo  = v.optJSONObject("vehiculo");
        JSONObject conductor = v.optJSONObject("conductor");

        int    viajeId = v.optInt("idViajes", v.optInt("id", 0));
        int    idRuta  = v.optInt("idRuta", ruta != null ? ruta.optInt("idRuta", 0) : 0);
        String estado  = v.optString("estado", EST_CREADO).trim().toUpperCase();

        // ── RUTA ─────────────────────────────────────────────────────────────
        if (ruta != null) {
            String origen      = ruta.optString("origen", "");
            String descripcion = ruta.optString("descripcion", ruta.optString("nombre", ""));
            StringBuilder textoRuta = new StringBuilder();
            if (!origen.isEmpty()) textoRuta.append(origen);
            if (!descripcion.isEmpty()) textoRuta.append("\n📋 ").append(descripcion);
            if (idRuta > 0) textoRuta.append("\n🔑 Ruta #").append(idRuta);
            h.txtRuta.setText(textoRuta.length() > 0 ? textoRuta.toString() : "Ruta no disponible");
        } else {
            h.txtRuta.setText(idRuta > 0 ? "Ruta #" + idRuta : "Ruta no disponible");
        }

        // ── FECHA ────────────────────────────────────────────────────────────
        String fecha = v.optString("fechaHoraSalida", "");
        if (fecha.contains("T")) fecha = fecha.replace("T", " ").replaceAll("\\.\\d{3}Z$", "");
        if (fecha.length() >= 16) fecha = fecha.substring(0, 16);
        h.txtFecha.setText("🕒 " + (fecha.isEmpty() ? "--" : fecha));

        // ── CUPOS ────────────────────────────────────────────────────────────
        int cuposDisponibles = v.optInt("cuposDisponibles", 0);
        int cuposTotales     = v.optInt("cuposTotales", cuposDisponibles);
        String cuposTexto;
        if (cuposTotales > 8 || cuposDisponibles > 8) {
            cuposTexto = "👥 Cupos: (actualiza tu vehículo)";
            cuposDisponibles = 1;
        } else {
            cuposTexto = "👥 " + cuposDisponibles + " / " + cuposTotales + " cupos";
        }
        h.txtCupos.setText(cuposTexto);

        // ── PRECIO ───────────────────────────────────────────────────────────
        double precio = v.optDouble("precio", -1);
        if (precio < 0) {
            try { precio = Double.parseDouble(v.optString("precio", "0")); }
            catch (NumberFormatException e) { precio = 0; }
        }
        h.txtPrecio.setText(precio > 0
                ? String.format(Locale.getDefault(), "💰 $%,.0f COP", precio)
                : "💰 Precio no definido");

        // ── ESTADO — con etiqueta clara ───────────────────────────────────────
        h.txtEstado.setText("📌 " + etiquetaEstado(estado));

        // ── CONDUCTOR ────────────────────────────────────────────────────────
        SessionManager session = new SessionManager(context);
        if (esConductor) {
            String nombre = conductor != null
                    ? conductor.optString("nombre", session.getNombre())
                    : session.getNombre();
            String tel = conductor != null
                    ? conductor.optString("telefono", conductor.optString("celular", session.getTelefono()))
                    : session.getTelefono();
            h.txtConductor.setText("👤 " + nombre);
            h.txtTelefono.setText("📞 " + (tel == null || tel.isEmpty() ? "N/A" : tel));
        } else {
            int idConductor = v.optInt("idConductor",
                    v.optInt("conductorId", v.optInt("conductor_id", -1)));
            if (idConductor <= 0 && vehiculo != null)
                idConductor = vehiculo.optInt("idUsuario", -1);

            if (idConductor > 0) {
                if (cacheConductores.containsKey(idConductor)) {
                    h.txtConductor.setText("👤 " + cacheConductores.get(idConductor));
                    h.txtTelefono.setText("");
                } else {
                    h.txtConductor.setText("👤 Cargando...");
                    h.txtTelefono.setText("");
                    cargarNombreConductor(idConductor, h);
                }
            } else {
                h.txtConductor.setText("👤 Conductor");
                if (vehiculo != null) {
                    String marca = vehiculo.optString("marca", "");
                    String placa = vehiculo.optString("placa", "");
                    h.txtTelefono.setText(!marca.isEmpty()
                            ? "🚘 " + marca + (!placa.isEmpty() ? " · " + placa : "")
                            : "");
                } else {
                    h.txtTelefono.setText("");
                }
            }
        }

        // ── VEHÍCULO ─────────────────────────────────────────────────────────
        if (vehiculo != null) {
            h.txtVehiculo.setText("🚘 "
                    + vehiculo.optString("marca","") + " "
                    + vehiculo.optString("modelo","") + " ("
                    + vehiculo.optString("placa","---") + ")");
        } else {
            String n = session.getVehiculoNombre();
            String p = session.getVehiculoPlaca();
            h.txtVehiculo.setText(n != null && !n.isEmpty()
                    ? "🚘 " + n + " (" + p + ")" : "🚘 Vehículo no asignado");
        }

        // ── BOTONES — reset ───────────────────────────────────────────────────
        h.btnAceptar.setVisibility(View.GONE);
        h.btnIniciar.setVisibility(View.GONE);
        h.btnFinalizar.setVisibility(View.GONE);
        h.btnCancelar.setVisibility(View.GONE);
        h.btnDetalle.setVisibility(View.GONE);

        // ── BOTONES — por rol y estado ────────────────────────────────────────
        if (esConductor) {
            switch (estado) {
                case EST_CREADO:
                case EST_PROGRAMADO:
                case EST_DISPONIBLE:
                    // Viaje no iniciado → conductor puede iniciar o cancelar
                    h.btnIniciar.setVisibility(View.VISIBLE);
                    h.btnCancelar.setVisibility(View.VISIBLE);
                    break;

                case EST_EN_CURSO:
                case EST_INICIADO:
                    // Viaje en curso → conductor puede finalizar
                    h.btnFinalizar.setVisibility(View.VISIBLE);
                    h.btnDetalle.setVisibility(View.VISIBLE);
                    break;

                default:
                    // FINALIZADO, CANCELADO → solo ver detalle
                    h.btnDetalle.setVisibility(View.VISIBLE);
                    break;
            }
        } else {
            // PASAJERO: solo mostrar botones si el viaje está EN_CURSO y hay cupos
            boolean enCurso = estado.equals(EST_EN_CURSO) || estado.equals(EST_INICIADO);
            if (enCurso && cuposDisponibles > 0 && cuposDisponibles <= 8) {
                h.btnAceptar.setVisibility(View.VISIBLE);
            }
            // "Ver detalle" siempre (para ver la ruta), pero solo si en curso
            if (enCurso) {
                h.btnDetalle.setVisibility(View.VISIBLE);
            }
        }

        // ── LISTENERS ─────────────────────────────────────────────────────────
        final int fViajeId = viajeId;

        View.OnClickListener tapGeneral = vw -> {
            if (viajeClickListener != null) viajeClickListener.onViajeClick(fViajeId);
            abrirDetalle(fViajeId);
        };
        h.itemView.setOnClickListener(tapGeneral);
        h.btnAceptar.setOnClickListener(tapGeneral);
        h.btnDetalle.setOnClickListener(tapGeneral);

        h.btnIniciar.setOnClickListener(vw ->
                cambiarEstado(fViajeId, Constantes.viajeIniciar((long) fViajeId), "iniciado"));
        h.btnFinalizar.setOnClickListener(vw ->
                cambiarEstado(fViajeId, Constantes.viajeFinalizar((long) fViajeId), "finalizado"));
        h.btnCancelar.setOnClickListener(vw ->
                cambiarEstado(fViajeId, Constantes.viajeCancelar((long) fViajeId), "cancelado"));
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================

    /** Etiqueta legible según el estado del backend */
    private String etiquetaEstado(String e) {
        switch (e) {
            case EST_CREADO:
            case EST_DISPONIBLE:    return "Publicado (no iniciado)";
            case EST_PROGRAMADO:    return "Programado";
            case EST_EN_CURSO:
            case EST_INICIADO:      return "En curso 🚗";
            case EST_FINALIZADO:    return "Finalizado 🏁";
            case EST_CANCELADO:     return "Cancelado ❌";
            default:                return e;
        }
    }

    private void cargarNombreConductor(int idConductor, ViajeViewHolder holder) {
        ConexionApi.getInstance(context).getObject(
                Constantes.usuarioDetalle((long) idConductor),
                response -> {
                    String nombre = extraerNombre(response);
                    if (nombre.isEmpty()) nombre = "Conductor";
                    cacheConductores.put(idConductor, nombre);
                    final String n = nombre;
                    if (holder.itemView.isAttachedToWindow())
                        holder.itemView.post(() -> {
                            holder.txtConductor.setText("👤 " + n);
                            String tel = response.optString("telefono",
                                    response.optString("celular", ""));
                            holder.txtTelefono.setText(tel.isEmpty() ? "" : "📞 " + tel);
                        });
                },
                error -> ConexionApi.getInstance(context).getObject(
                        Constantes.USUARIOS + "/" + idConductor,
                        response2 -> {
                            String n2 = extraerNombre(response2);
                            if (n2.isEmpty()) n2 = "Conductor";
                            cacheConductores.put(idConductor, n2);
                            final String nf = n2;
                            if (holder.itemView.isAttachedToWindow())
                                holder.itemView.post(() -> holder.txtConductor.setText("👤 " + nf));
                        },
                        error2 -> {
                            if (holder.itemView.isAttachedToWindow())
                                holder.itemView.post(() -> holder.txtConductor.setText("👤 Conductor"));
                        }
                )
        );
    }

    private String extraerNombre(JSONObject obj) {
        if (obj == null) return "";
        for (String k : new String[]{"nombre","nombreCompleto","name","fullName",
                "nombreUsuario","displayName","nombres"}) {
            String v = obj.optString(k, "");
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        String n = obj.optString("nombres",""), a = obj.optString("apellidos","");
        if (!n.isEmpty() || !a.isEmpty()) return (n + " " + a).trim();
        return "";
    }

    private void abrirDetalle(int viajeId) {
        Intent i = new Intent(context, DetalleViajeActivity.class);
        i.putExtra("ID_VIAJE", viajeId);
        context.startActivity(i);
    }

    private void cambiarEstado(int viajeId, String url, String accion) {
        ConexionApi.getInstance(context).post(url, null,
                r -> {
                    Toast.makeText(context, "Viaje " + accion + " ✅", Toast.LENGTH_SHORT).show();
                    notifyDataSetChanged();
                },
                e -> Toast.makeText(context, "Error al " + accion + " ❌", Toast.LENGTH_SHORT).show()
        );
    }

    @Override public int getItemCount() { return viajes.size(); }

    // =========================================================================
    //  VIEW HOLDER
    // =========================================================================
    static class ViajeViewHolder extends RecyclerView.ViewHolder {
        TextView txtRuta, txtFecha, txtCupos, txtPrecio;
        TextView txtConductor, txtTelefono, txtVehiculo, txtEstado;
        Button   btnAceptar, btnIniciar, btnFinalizar, btnCancelar, btnDetalle;

        public ViajeViewHolder(@NonNull View itemView) {
            super(itemView);
            txtRuta      = itemView.findViewById(R.id.txtRuta);
            txtFecha     = itemView.findViewById(R.id.txtFecha);
            txtCupos     = itemView.findViewById(R.id.txtCupos);
            txtPrecio    = itemView.findViewById(R.id.txtPrecio);
            txtConductor = itemView.findViewById(R.id.txtConductor);
            txtTelefono  = itemView.findViewById(R.id.txtTelefono);
            txtVehiculo  = itemView.findViewById(R.id.txtVehiculo);
            txtEstado    = itemView.findViewById(R.id.txtEstado);
            btnAceptar   = itemView.findViewById(R.id.btnAceptar);
            btnIniciar   = itemView.findViewById(R.id.btnIniciar);
            btnFinalizar = itemView.findViewById(R.id.btnFinalizar);
            btnCancelar  = itemView.findViewById(R.id.btnCancelar);
            btnDetalle   = itemView.findViewById(R.id.btnDetalle);
        }
    }
}