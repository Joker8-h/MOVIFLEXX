package com.arlys.moviflexx.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;

import org.json.JSONObject;

import java.util.List;

public class VehiculosAdapter extends RecyclerView.Adapter<VehiculosAdapter.VehiculoViewHolder> {

    private final Context          context;
    private final List<JSONObject> vehiculos;

    public VehiculosAdapter(Context context, List<JSONObject> vehiculos) {
        this.context   = context;
        this.vehiculos = vehiculos;
    }

    @NonNull
    @Override
    public VehiculoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_vehiculo, parent, false);
        return new VehiculoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VehiculoViewHolder h, int position) {
        JSONObject veh = vehiculos.get(position);

        String marca   = veh.optString("marca",   "Marca desconocida");
        String modelo  = veh.optString("modelo",  "");
        String placa   = veh.optString("placa",   "---");
        String color   = veh.optString("color",   "");
        String tipo    = veh.optString("tipo",    veh.optString("tipoVehiculo", ""));
        int    cupos   = veh.optInt   ("cupos",   veh.optInt("capacidad", 0));

        // Inicial del vehículo para el avatar
        h.txtAvatar.setText(marca.isEmpty() ? "V" : marca.substring(0, 1).toUpperCase());

        // Nombre principal
        String nombre = marca + (modelo.isEmpty() ? "" : " " + modelo);
        h.txtNombre.setText(nombre);

        // Placa + tipo
        StringBuilder sub = new StringBuilder("" + placa);
        if (!tipo.isEmpty())  sub.append("  ·  ").append(tipo);
        if (!color.isEmpty()) sub.append("  ·  ").append(color);
        h.txtSubtitulo.setText(sub.toString());

        // Cupos
        h.txtCupos.setText(cupos > 0 ? "👥 " + cupos + " cupos" : "👥 Cupos no definidos");

        // Estado
        String estado = veh.optString("estado", "ACTIVO").toUpperCase();
        switch (estado) {
            case "ACTIVO":
                h.txtEstado.setText("✅ Activo");
                h.txtEstado.setBackgroundResource(R.drawable.bg_estado_activo);
                break;
            case "INACTIVO":
                h.txtEstado.setText("⛔ Inactivo");
                h.txtEstado.setBackgroundResource(R.drawable.bg_estado_cancelado);
                break;
            default:
                h.txtEstado.setText("" + estado);
                break;
        }
    }

    @Override
    public int getItemCount() { return vehiculos.size(); }

    static class VehiculoViewHolder extends RecyclerView.ViewHolder {
        TextView txtAvatar, txtNombre, txtSubtitulo, txtCupos, txtEstado;

        VehiculoViewHolder(@NonNull View v) {
            super(v);
            txtAvatar    = v.findViewById(R.id.txtAvatarVehiculo);
            txtNombre    = v.findViewById(R.id.txtNombreVehiculo);
            txtSubtitulo = v.findViewById(R.id.txtSubtituloVehiculo);
            txtCupos     = v.findViewById(R.id.txtCuposVehiculo);
            txtEstado    = v.findViewById(R.id.txtEstadoVehiculo);
        }
    }
}