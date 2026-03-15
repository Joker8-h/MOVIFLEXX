package com.arlys.moviflexx.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.controller.DetalleViajeActivity;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public class RutasAdapter extends RecyclerView.Adapter<RutasAdapter.RutaViewHolder> {

    private final Context          context;
    private final List<JSONObject> rutas;

    public RutasAdapter(Context context, List<JSONObject> rutas) {
        this.context = context;
        this.rutas   = rutas;
    }

    @NonNull
    @Override
    public RutaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_ruta, parent, false);
        return new RutaViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RutaViewHolder h, int position) {
        JSONObject ruta = rutas.get(position);

        int    idRuta    = ruta.optInt   ("idRuta",      ruta.optInt("id", position + 1));
        String origen    = ruta.optString("origen",      "Origen no definido");
        String destino   = ruta.optString("destino",     ruta.optString("nombre", ""));
        String desc      = ruta.optString("descripcion", "");
        String distancia = ruta.optString("distancia",   "");
        String duracion  = ruta.optString("duracion",    ruta.optString("tiempoEstimado", ""));
        String tipo      = ruta.optString("tipo",        "Carro");

        h.txtNumero.setText("#" + idRuta);
        h.txtOrigen.setText(origen);

        if (!destino.isEmpty()) {
            h.txtDestino.setVisibility(View.VISIBLE);
            h.txtDestino.setText("→ " + destino);
        } else if (!desc.isEmpty()) {
            h.txtDestino.setVisibility(View.VISIBLE);
            h.txtDestino.setText(desc);
        } else {
            h.txtDestino.setVisibility(View.GONE);
        }

        StringBuilder meta = new StringBuilder();
        if (!distancia.isEmpty()) meta.append(" ").append(distancia);
        if (!duracion.isEmpty())  meta.append(meta.length() > 0 ? "  ·  " : "").append("⏱ ").append(duracion);
        if (!tipo.isEmpty())      meta.append(meta.length() > 0 ? "  ·  " : "").append(" ").append(tipo);
        h.txtMeta.setText(meta.length() > 0 ? meta.toString() : " Ruta #" + idRuta);

        // ── CLICK: mostrar viajes de esta ruta ────────────────────────────────
        h.itemView.setOnClickListener(v ->
                mostrarViajesDeRuta(idRuta, origen, destino.isEmpty() ? desc : destino));
    }

    // =========================================================================
    //  BOTTOM SHEET — Viajes de la ruta
    // =========================================================================
    private void mostrarViajesDeRuta(int idRuta, String origen, String destino) {
        float dp = context.getResources().getDisplayMetrics().density;
        int p16 = (int)(16*dp), p12 = (int)(12*dp), p8 = (int)(8*dp), p4 = (int)(4*dp);

        BottomSheetDialog sheet = new BottomSheetDialog(context, R.style.BottomSheetTheme);

        // ── Root ──────────────────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p16, p16, p16, (int)(32*dp));
        root.setBackgroundColor(Color.parseColor("#F0F4F8"));

        // Tirón
        View tiron = new View(context);
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams((int)(40*dp), (int)(4*dp));
        lpT.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lpT.bottomMargin = p12;
        tiron.setLayoutParams(lpT);
        GradientDrawable tGd = new GradientDrawable();
        tGd.setShape(GradientDrawable.RECTANGLE);
        tGd.setCornerRadius(4*dp);
        tGd.setColor(Color.parseColor("#BDBDBD"));
        tiron.setBackground(tGd);
        root.addView(tiron);

        // Título
        TextView txtTitulo = new TextView(context);
        txtTitulo.setText("🗺️  Ruta #" + idRuta);
        txtTitulo.setTextSize(18f);
        txtTitulo.setTypeface(null, Typeface.BOLD);
        txtTitulo.setTextColor(Color.parseColor("#1A2035"));
        LinearLayout.LayoutParams lpTit = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTit.bottomMargin = p4;
        txtTitulo.setLayoutParams(lpTit);
        root.addView(txtTitulo);

        // Subtítulo origen → destino
        if (!origen.isEmpty()) {
            TextView txtSub = new TextView(context);
            String sub = "📍 " + origen + (destino.isEmpty() ? "" : " → " + destino);
            txtSub.setText(sub);
            txtSub.setTextSize(13f);
            txtSub.setTextColor(Color.parseColor("#6B7280"));
            LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpSub.bottomMargin = p12;
            txtSub.setLayoutParams(lpSub);
            root.addView(txtSub);
        }

        // Separador
        View sep = new View(context);
        LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1*dp));
        lpSep.bottomMargin = p12;
        sep.setLayoutParams(lpSep);
        sep.setBackgroundColor(Color.parseColor("#E5E7EB"));
        root.addView(sep);

        // ProgressBar
        ProgressBar pb = new ProgressBar(context);
        LinearLayout.LayoutParams lpPb = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpPb.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lpPb.topMargin = p16;
        lpPb.bottomMargin = p16;
        pb.setLayoutParams(lpPb);
        root.addView(pb);

        // Contenedor de viajes (se llena async)
        LinearLayout listaViajes = new LinearLayout(context);
        listaViajes.setOrientation(LinearLayout.VERTICAL);
        root.addView(listaViajes);

        ScrollView sv = new ScrollView(context);
        sv.addView(root);
        sheet.setContentView(sv);
        sheet.show();

        // ── Cargar viajes de esta ruta ────────────────────────────────────────
        String url = Constantes.BASE_URL + "/api/viajes?idRuta=" + idRuta;

        ConexionApi.getInstance(context).getArrayNoCache(
                url,
                response -> new Handler(Looper.getMainLooper()).post(() -> {
                    pb.setVisibility(View.GONE);

                    if (response.length() == 0) {
                        mostrarVacioEnSheet(listaViajes, dp, p16);
                        return;
                    }

                    for (int i = 0; i < response.length(); i++) {
                        JSONObject viaje = response.optJSONObject(i);
                        if (viaje == null) continue;
                        listaViajes.addView(
                                crearTarjetaViaje(viaje, dp, p12, p8, p4, sheet));
                    }
                }),
                error -> new Handler(Looper.getMainLooper()).post(() -> {
                    pb.setVisibility(View.GONE);
                    // Intentar endpoint alternativo
                    String url2 = Constantes.BASE_URL + "/api/viajes?ruta=" + idRuta;
                    ConexionApi.getInstance(context).getArrayNoCache(
                            url2,
                            response2 -> new Handler(Looper.getMainLooper()).post(() -> {
                                if (response2.length() == 0) {
                                    mostrarVacioEnSheet(listaViajes, dp, p16);
                                } else {
                                    for (int i = 0; i < response2.length(); i++) {
                                        JSONObject viaje = response2.optJSONObject(i);
                                        if (viaje != null)
                                            listaViajes.addView(
                                                    crearTarjetaViaje(viaje, dp, p12, p8, p4, sheet));
                                    }
                                }
                            }),
                            error2 -> new Handler(Looper.getMainLooper()).post(() ->
                                    mostrarVacioEnSheet(listaViajes, dp, p16))
                    );
                })
        );
    }

    private void mostrarVacioEnSheet(LinearLayout container, float dp, int p16) {
        TextView tv = new TextView(context);
        tv.setText("🗺️  No hay viajes publicados\npara esta ruta aún");
        tv.setTextSize(14f);
        tv.setTextColor(Color.parseColor("#9CA3AF"));
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(0, p16, 0, p16);
        container.addView(tv);
    }

    // ── Tarjeta de viaje dentro del BottomSheet ───────────────────────────────
    private View crearTarjetaViaje(JSONObject viaje, float dp,
                                   int p12, int p8, int p4,
                                   BottomSheetDialog sheet) {
        int viajeId = viaje.optInt("idViajes", viaje.optInt("id", 0));
        String estado = viaje.optString("estado", "").trim().toUpperCase();

        // Fecha
        String fecha = viaje.optString("fechaHoraSalida", "");
        if (fecha.contains("T")) fecha = fecha.replace("T", " ").replaceAll("\\.\\d{3}Z$", "");
        if (fecha.length() >= 16) fecha = fecha.substring(0, 16);

        // Precio
        double precio = viaje.optDouble("precio", -1);
        if (precio < 0) {
            try { precio = Double.parseDouble(viaje.optString("precio", "0")); }
            catch (Exception ignored) { precio = 0; }
        }

        // Cupos
        int cuposDisp = viaje.optInt("cuposDisponibles", 0);
        int cuposTot  = viaje.optInt("cuposTotales", cuposDisp);

        // Card
        com.google.android.material.card.MaterialCardView card =
                new com.google.android.material.card.MaterialCardView(context);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, p8, 0, p4);
        card.setLayoutParams(lpCard);
        card.setRadius(16 * dp);
        card.setCardElevation(3 * dp);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p12, p12, p12, p12);

        // Fila estado + fecha
        LinearLayout fila1 = new LinearLayout(context);
        fila1.setOrientation(LinearLayout.HORIZONTAL);
        fila1.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView txtEstado = new TextView(context);
        txtEstado.setText(etiquetaEstado(estado));
        txtEstado.setTextSize(11f);
        txtEstado.setTypeface(null, Typeface.BOLD);
        txtEstado.setTextColor(Color.WHITE);
        txtEstado.setPadding(p8, p4, p8, p4);
        GradientDrawable bgEstado = new GradientDrawable();
        bgEstado.setShape(GradientDrawable.RECTANGLE);
        bgEstado.setCornerRadius(20 * dp);
        bgEstado.setColor(colorEstado(estado));
        txtEstado.setBackground(bgEstado);
        LinearLayout.LayoutParams lpE = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpE.setMargins(0, 0, p8, 0);
        txtEstado.setLayoutParams(lpE);
        fila1.addView(txtEstado);

        TextView txtFecha = new TextView(context);
        txtFecha.setText(fecha.isEmpty() ? "Sin fecha" : "🕒 " + fecha);
        txtFecha.setTextSize(12f);
        txtFecha.setTextColor(Color.parseColor("#6B7280"));
        txtFecha.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fila1.addView(txtFecha);
        inner.addView(fila1);

        // Precio + cupos
        LinearLayout fila2 = new LinearLayout(context);
        fila2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpF2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpF2.topMargin = p8;
        fila2.setLayoutParams(lpF2);

        TextView txtPrecio = new TextView(context);
        txtPrecio.setText(precio > 0
                ? String.format(Locale.getDefault(), " $%,.0f", precio)
                : " Sin precio");
        txtPrecio.setTextSize(13f);
        txtPrecio.setTypeface(null, Typeface.BOLD);
        txtPrecio.setTextColor(Color.parseColor("#1A2035"));
        txtPrecio.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fila2.addView(txtPrecio);

        TextView txtCupos = new TextView(context);
        txtCupos.setText("👥 " + cuposDisp + "/" + cuposTot);
        txtCupos.setTextSize(12f);
        txtCupos.setTextColor(cuposDisp > 0
                ? Color.parseColor("#059669") : Color.parseColor("#EF4444"));
        fila2.addView(txtCupos);
        inner.addView(fila2);

        // Botón ver detalle
        com.google.android.material.button.MaterialButton btn =
                new com.google.android.material.button.MaterialButton(context);
        btn.setText("Ver detalle →");
        btn.setTextSize(13f);
        btn.setTextColor(Color.WHITE);
        btn.setCornerRadius((int)(12 * dp));
        btn.setBackgroundColor(Color.parseColor("#2EC4B6"));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(44 * dp));
        lpBtn.topMargin = p8;
        btn.setLayoutParams(lpBtn);

        final int fViajeId = viajeId;
        btn.setOnClickListener(v -> {
            sheet.dismiss();
            Intent intent = new Intent(context, DetalleViajeActivity.class);
            intent.putExtra("ID_VIAJE", fViajeId);
            context.startActivity(intent);
        });
        inner.addView(btn);

        card.addView(inner);
        return card;
    }

    private String etiquetaEstado(String e) {
        switch (e) {
            case "CREADO":
            case "DISPONIBLE":  return "✅ Publicado";
            case "PROGRAMADO":  return "📅 Programado";
            case "EN_CURSO":
            case "INICIADO":    return "En curso";
            case "FINALIZADO":  return "Finalizado";
            case "CANCELADO":   return "❌ Cancelado";
            default:            return "📌 " + e;
        }
    }

    private int colorEstado(String e) {
        switch (e) {
            case "EN_CURSO":
            case "INICIADO":   return Color.parseColor("#0891B2");
            case "FINALIZADO": return Color.parseColor("#6B7280");
            case "CANCELADO":  return Color.parseColor("#EF4444");
            default:           return Color.parseColor("#059669");
        }
    }

    @Override
    public int getItemCount() { return rutas.size(); }

    static class RutaViewHolder extends RecyclerView.ViewHolder {
        TextView txtNumero, txtOrigen, txtDestino, txtMeta;

        RutaViewHolder(@NonNull View v) {
            super(v);
            txtNumero  = v.findViewById(R.id.txtNumeroRuta);
            txtOrigen  = v.findViewById(R.id.txtOrigenRuta);
            txtDestino = v.findViewById(R.id.txtDestinoRuta);
            txtMeta    = v.findViewById(R.id.txtMetaRuta);
        }
    }
}