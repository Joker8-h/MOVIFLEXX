package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Manager.CalificacionesManager;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class MisViajesActivity extends AppCompatActivity {

    private static final String TAG = "MisViajesActivity";

    private ProgressBar  progress;
    private LinearLayout layoutLista;
    private TextView     tvVacio;
    private SessionManager session;
    private int idPasajero;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(
                android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_mis_viajes);

        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }
        idPasajero = session.getIdUsuario();
        bindViews();
        cargarViajesRealizados();
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void bindViews() {
        progress    = findViewById(R.id.progress);
        layoutLista = findViewById(R.id.layout_lista_viajess);
        tvVacio     = findViewById(R.id.tv_vacioo);

        // fallback por si el XML no tiene los IDs
        if (layoutLista == null) {
            ViewGroup root = (ViewGroup) getWindow().getDecorView()
                    .findViewById(android.R.id.content);
            ScrollView sv = new ScrollView(this);
            sv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            layoutLista = new LinearLayout(this);
            layoutLista.setOrientation(LinearLayout.VERTICAL);
            int p = dp(16);
            layoutLista.setPadding(p, p, p, p);
            sv.addView(layoutLista);
            root.addView(sv);
        }
        if (tvVacio == null) {
            tvVacio = new TextView(this);
            tvVacio.setVisibility(View.GONE);
            layoutLista.addView(tvVacio);
        }

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    // =========================================================================
    //  PASO 1: cargar mis reservas
    // =========================================================================
    private void cargarViajesRealizados() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        layoutLista.removeAllViews();

        ConexionApi.getInstance(this).getArray(Constantes.MIS_RESERVAS,
                response -> {
                    Log.d(TAG, "MIS_RESERVAS total=" + response.length());

                    // Filtrar solo las finalizadas / completadas
                    List<JSONObject> reservasFin = new ArrayList<>();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject r = response.optJSONObject(i);
                        if (r != null && esFinalizado(r)) reservasFin.add(r);
                    }

                    if (reservasFin.isEmpty()) {
                        runOnUiThread(() -> {
                            if (progress != null) progress.setVisibility(View.GONE);
                            mostrarVacio(response.length());
                        });
                        return;
                    }

                    // PASO 2: enriquecer cada reserva con el detalle del viaje
                    enriquecerConDetalleViaje(reservasFin);
                },
                error -> runOnUiThread(() -> {
                    if (progress != null) progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Error cargando viajes", Toast.LENGTH_LONG).show();
                    mostrarVacio(0);
                })
        );
    }

    // =========================================================================
    //  PASO 2: por cada reserva, llamar GET /api/viajes/{id} para el detalle
    // =========================================================================
    private void enriquecerConDetalleViaje(List<JSONObject> reservas) {
        List<JSONObject> items = new ArrayList<>();
        AtomicInteger pendientes = new AtomicInteger(reservas.size());

        for (int i = 0; i < reservas.size(); i++) {
            JSONObject reserva = reservas.get(i);
            final int idx = i;

            // Obtener idViaje desde la reserva
            int idViaje = reserva.optInt("idViaje", 0);
            if (idViaje == 0) {
                JSONObject vObj = reserva.optJSONObject("viaje");
                if (vObj != null) idViaje = vObj.optInt("idViaje", vObj.optInt("id", 0));
            }
            if (idViaje == 0) idViaje = reserva.optInt("id", 0); // fallback

            if (idViaje <= 0) {
                // Sin ID de viaje: usar solo la reserva base
                items.add(construirItem(reserva, new JSONObject(), idx));
                if (pendientes.decrementAndGet() == 0) mostrarCards(items);
                continue;
            }

            final int fIdViaje = idViaje;
            items.add(null); // placeholder para mantener orden

            ConexionApi.getInstance(this).getObject(
                    Constantes.viajePorId((long) fIdViaje),
                    detalleViaje -> {
                        Log.d(TAG, "Detalle viaje #" + fIdViaje + " ok");
                        items.set(idx, construirItem(reserva, detalleViaje, idx));
                        if (pendientes.decrementAndGet() == 0) mostrarCards(items);
                    },
                    error -> {
                        Log.w(TAG, "No se pudo cargar detalle viaje #" + fIdViaje);
                        items.set(idx, construirItem(reserva, new JSONObject(), idx));
                        if (pendientes.decrementAndGet() == 0) mostrarCards(items);
                    }
            );
        }
    }

    // =========================================================================
    //  PASO 3: construir el item combinando reserva + detalleViaje
    // =========================================================================
    private JSONObject construirItem(JSONObject reserva, JSONObject viaje, int idx) {
        try {
            // ── ID del viaje ──
            int viajeId = viaje.optInt("idViaje", viaje.optInt("id", 0));
            if (viajeId == 0) viajeId = reserva.optInt("idViaje", reserva.optInt("id", -(idx+1)));

            // ── Conductor ──────────────────────────────────────────────────
            int    idCond  = -1;
            String nomCond = "";
            String telCond = "";

            JSONObject co = viaje.optJSONObject("conductor");
            if (co != null) {
                idCond  = primerInt(co, new String[]{"id","idUsuarios","idUsuario"});
                nomCond = extraerNombre(co);
                telCond = co.optString("telefono", co.optString("celular",""));
                JSONObject u = co.optJSONObject("usuario");
                if (u != null) {
                    if (idCond  <= 0) idCond  = primerInt(u, new String[]{"id","idUsuarios","idUsuario"});
                    if (nomCond.isEmpty()) nomCond = extraerNombre(u);
                    if (telCond.isEmpty()) telCond = u.optString("telefono", u.optString("celular",""));
                }
            }
            // fallback: campo idConductor directo
            if (idCond <= 0)
                idCond = viaje.optInt("idConductor", reserva.optInt("idConductor", -1));

            // ── Vehículo ───────────────────────────────────────────────────
            String vehiculo = "";
            JSONObject veh = viaje.optJSONObject("vehiculo");
            if (veh != null) {
                String marca  = veh.optString("marca","");
                String modelo = veh.optString("modelo","");
                String placa  = veh.optString("placa","");
                if (!marca.isEmpty() || !modelo.isEmpty())
                    vehiculo = (marca + " " + modelo
                            + (!placa.isEmpty() ? " • " + placa.toUpperCase() : "")).trim();
            }

            // ── Ruta ───────────────────────────────────────────────────────
            String rutaLabel = "";
            String origen = "", destino = "";
            double distKm = 0, durMin = 0;

            JSONObject ruta = viaje.optJSONObject("ruta");
            if (ruta != null) {
                distKm = ruta.optDouble("distanciaKm", ruta.optDouble("distancia", 0));
                durMin = ruta.optDouble("duracionMin",  ruta.optDouble("duracion", 0));

                // Nombres desde paradas
                JSONArray paradas = ruta.optJSONArray("paradas");
                if (paradas != null && paradas.length() >= 2) {
                    JSONObject p0 = paradas.optJSONObject(0);
                    JSONObject pN = paradas.optJSONObject(paradas.length() - 1);
                    if (p0 != null) origen  = p0.optString("nombre","");
                    if (pN != null) destino = pN.optString("nombre","");
                }
                // Fallback nombre directo
                if (origen.isEmpty())
                    origen  = primerStr(ruta, new String[]{"origen","nombreOrigen","puntoOrigen"});
                if (destino.isEmpty())
                    destino = primerStr(ruta, new String[]{"destino","nombreDestino","puntoDestino"});
                // Fallback nombre de la ruta
                if (origen.isEmpty() && destino.isEmpty()) {
                    String nomR = primerStr(ruta, new String[]{"nombre","descripcion"});
                    if (nomR.contains("→")) {
                        String[] p = nomR.split("→", 2);
                        origen = p[0].trim(); destino = p[1].trim();
                    } else {
                        rutaLabel = nomR;
                    }
                }
            }
            // Destino desde la reserva si aún no tenemos
            if (destino.isEmpty())
                destino = primerStr(reserva, new String[]{
                        "nombreParadaBajada","nombreParada","destino","parada"});

            if (!origen.isEmpty() && !destino.isEmpty())
                rutaLabel = origen + " → " + destino;
            else if (!destino.isEmpty()) rutaLabel = destino;
            else if (rutaLabel.isEmpty()) rutaLabel = "Ruta del viaje";

            // ── Fecha ──────────────────────────────────────────────────────
            String fecha = "";
            for (String k : new String[]{"fechaHoraSalida","fechaSalida","fecha","fechaViaje"}) {
                String f = viaje.optString(k,"");
                if (!f.isEmpty() && !f.equals("null")) { fecha = f; break; }
            }
            if (fecha.isEmpty()) {
                for (String k : new String[]{"fechaHoraSalida","fechaSalida","fecha","fechaViaje"}) {
                    String f = reserva.optString(k,"");
                    if (!f.isEmpty() && !f.equals("null")) { fecha = f; break; }
                }
            }

            // ── Precio ─────────────────────────────────────────────────────
            double precio = viaje.optDouble("precio", 0);
            if (precio <= 0 && ruta != null)
                precio = ruta.optDouble("precio", ruta.optDouble("costoPorPasajero", 0));
            if (precio <= 0)
                precio = reserva.optDouble("precioFinal", reserva.optDouble("precio", 0));

            // ── Cupos ──────────────────────────────────────────────────────
            int cuposTotal = viaje.optInt("cuposTotales", 0);
            int cuposDisp  = viaje.optInt("cuposDisponibles", 0);
            int asientos   = reserva.optInt("asientos",
                    reserva.optInt("numeroAsientos",
                            reserva.optInt("asientosReservados", 1)));

            // ── Pasajeros (desde el detalle del viaje) ─────────────────────
            JSONArray pasajeros = viaje.optJSONArray("usuarios");

            // ── Armar el item ──────────────────────────────────────────────
            JSONObject item = new JSONObject();
            item.put("viajeId",    viajeId);
            item.put("idCond",     idCond);
            item.put("nomCond",    nomCond.isEmpty() ? "Conductor" : nomCond);
            item.put("telCond",    telCond);
            item.put("vehiculo",   vehiculo);
            item.put("rutaLabel",  rutaLabel);
            item.put("fecha",      fecha);
            item.put("precio",     precio);
            item.put("cuposTotal", cuposTotal);
            item.put("cuposDisp",  cuposDisp);
            item.put("asientos",   asientos);
            item.put("distKm",     distKm);
            item.put("durMin",     durMin);
            if (pasajeros != null) item.put("pasajeros", pasajeros);
            return item;

        } catch (Exception e) {
            Log.e(TAG, "construirItem: " + e.getMessage());
            return new JSONObject();
        }
    }

    // =========================================================================
    //  RENDER
    // =========================================================================
    private void mostrarCards(List<JSONObject> items) {
        runOnUiThread(() -> {
            if (progress != null) progress.setVisibility(View.GONE);
            layoutLista.removeAllViews();

            // Filtrar nulls
            List<JSONObject> validos = new ArrayList<>();
            for (JSONObject it : items) if (it != null && it.length() > 0) validos.add(it);

            if (validos.isEmpty()) { mostrarVacio(0); return; }

            // ── Encabezado ──
            layoutLista.addView(buildEncabezado(validos.size()));
            for (JSONObject item : validos) layoutLista.addView(buildCard(item));
        });
    }

    private View buildEncabezado(int count) {
        LinearLayout cab = new LinearLayout(this);
        cab.setOrientation(LinearLayout.HORIZONTAL);
        cab.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(16));
        cab.setLayoutParams(lp);

        View barra = new View(this);
        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(dp(4), dp(24));
        lpB.setMargins(0, 0, dp(10), 0);
        barra.setLayoutParams(lpB);
        GradientDrawable bgB = new GradientDrawable();
        bgB.setShape(GradientDrawable.RECTANGLE);
        bgB.setCornerRadius(dp(3));
        bgB.setColor(Color.parseColor("#FF9800"));
        barra.setBackground(bgB);
        cab.addView(barra);

        TextView tvTit = new TextView(this);
        tvTit.setText("🚗 Mis Viajes Realizados");
        tvTit.setTextSize(17f);
        tvTit.setTypeface(null, Typeface.BOLD);
        tvTit.setTextColor(Color.parseColor("#004D40"));
        tvTit.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        cab.addView(tvTit);

        TextView tvN = new TextView(this);
        tvN.setText(count + " viajes");
        tvN.setTextSize(12f);
        tvN.setTypeface(null, Typeface.BOLD);
        tvN.setTextColor(Color.WHITE);
        tvN.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable bgN = new GradientDrawable();
        bgN.setShape(GradientDrawable.RECTANGLE);
        bgN.setCornerRadius(dp(20));
        bgN.setColor(Color.parseColor("#FF9800"));
        tvN.setBackground(bgN);
        cab.addView(tvN);
        return cab;
    }

    // ─────────────────────────────────────────────────────────────────────────
    private View buildCard(JSONObject item) {
        int    viajeId    = item.optInt("viajeId", 0);
        int    idCond     = item.optInt("idCond", -1);
        String nomCond    = item.optString("nomCond", "Conductor");
        String telCond    = item.optString("telCond", "");
        String vehiculo   = item.optString("vehiculo", "");
        String rutaLabel  = item.optString("rutaLabel", "Ruta del viaje");
        String fecha      = item.optString("fecha", "");
        double precio     = item.optDouble("precio", 0);
        int    cuposTotal = item.optInt("cuposTotal", 0);
        int    cuposDisp  = item.optInt("cuposDisp", 0);
        int    asientos   = item.optInt("asientos", 1);
        double distKm     = item.optDouble("distKm", 0);
        JSONArray pasajeros = item.optJSONArray("pasajeros");

        // Formatear fecha
        String fechaFmt = "";
        if (!fecha.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault());
                fechaFmt = out.format(sdf.parse(fecha.replaceAll("\\.\\d{3}Z?$", "")));
            } catch (Exception e) {
                fechaFmt = fecha.replace("T", " ").replaceAll("\\.\\d{3}Z?$", "");
            }
        }

        // ══ CARD ══════════════════════════════════════════════════════════════
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bgCard = new GradientDrawable();
        bgCard.setShape(GradientDrawable.RECTANGLE);
        bgCard.setCornerRadius(dp(20));
        bgCard.setColor(Color.WHITE);
        bgCard.setStroke(dp(1), Color.parseColor("#E0F2F1"));
        card.setBackground(bgCard);
        card.setElevation(dp(4));
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(lpCard);

        // ── HEADER TEAL ───────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bgH = new GradientDrawable();
        bgH.setShape(GradientDrawable.RECTANGLE);
        bgH.setCornerRadii(new float[]{dp(20),dp(20),dp(20),dp(20),0,0,0,0});
        bgH.setColors(new int[]{Color.parseColor("#009B8D"), Color.parseColor("#00695C")});
        bgH.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        header.setBackground(bgH);

        // Fila ruta + badge
        LinearLayout filaRuta = new LinearLayout(this);
        filaRuta.setOrientation(LinearLayout.HORIZONTAL);
        filaRuta.setGravity(Gravity.TOP);

        TextView tvPin = new TextView(this);
        tvPin.setText("📍");
        tvPin.setTextSize(15f);
        LinearLayout.LayoutParams lpPin = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpPin.setMargins(0, 0, dp(6), 0);
        tvPin.setLayoutParams(lpPin);
        filaRuta.addView(tvPin);

        TextView tvRuta = new TextView(this);
        tvRuta.setText(rutaLabel);
        tvRuta.setTextSize(14f);
        tvRuta.setTypeface(null, Typeface.BOLD);
        tvRuta.setTextColor(Color.WHITE);
        tvRuta.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        filaRuta.addView(tvRuta);

        TextView tvComp = new TextView(this);
        tvComp.setText("✅ Completado");
        tvComp.setTextSize(10f);
        tvComp.setTypeface(null, Typeface.BOLD);
        tvComp.setTextColor(Color.WHITE);
        tvComp.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable bgComp = new GradientDrawable();
        bgComp.setShape(GradientDrawable.RECTANGLE);
        bgComp.setCornerRadius(dp(20));
        bgComp.setColor(Color.parseColor("#2E7D32"));
        tvComp.setBackground(bgComp);
        LinearLayout.LayoutParams lpComp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpComp.setMargins(dp(8), 0, 0, 0);
        tvComp.setLayoutParams(lpComp);
        filaRuta.addView(tvComp);
        header.addView(filaRuta);

        // Fecha + distancia en header
        if (!fechaFmt.isEmpty() || distKm > 0) {
            LinearLayout filaSubH = new LinearLayout(this);
            filaSubH.setOrientation(LinearLayout.HORIZONTAL);
            filaSubH.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpSH = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpSH.setMargins(dp(22), dp(6), 0, 0);
            filaSubH.setLayoutParams(lpSH);

            if (!fechaFmt.isEmpty()) {
                TextView tvFH = new TextView(this);
                tvFH.setText("🗓 " + fechaFmt);
                tvFH.setTextSize(11.5f);
                tvFH.setTextColor(Color.parseColor("#B2DFDB"));
                tvFH.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                filaSubH.addView(tvFH);
            }
            if (distKm > 0) {
                TextView tvDist = new TextView(this);
                tvDist.setText(String.format(Locale.getDefault(), "🛣 %.1f km", distKm));
                tvDist.setTextSize(11f);
                tvDist.setTextColor(Color.parseColor("#B2DFDB"));
                filaSubH.addView(tvDist);
            }
            header.addView(filaSubH);
        }
        card.addView(header);

        // ── BODY ──────────────────────────────────────────────────────────────
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(14), dp(16), dp(14));

        // Conductor
        body.addView(buildFilaConductor(nomCond, telCond));

        // Vehículo
        if (!vehiculo.isEmpty()) {
            body.addView(buildFilaInfo("🚗", vehiculo, "#546E7A", dp(8)));
        }

        // Chips precio + cupos
        if (precio > 0 || cuposTotal > 0) {
            LinearLayout filaChips = new LinearLayout(this);
            filaChips.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lpFC = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpFC.setMargins(0, dp(10), 0, 0);
            filaChips.setLayoutParams(lpFC);

            if (precio > 0) {
                TextView tvP = buildChip(
                        "💰 $" + String.format(Locale.getDefault(), "%,.0f", precio),
                        "#E8F5E9", "#1B5E20");
                LinearLayout.LayoutParams lpP = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lpP.setMargins(0, 0, dp(8), 0);
                tvP.setLayoutParams(lpP);
                filaChips.addView(tvP);
            }
            if (cuposTotal > 0) {
                int ocupados = cuposTotal - cuposDisp;
                filaChips.addView(buildChip(
                        "👥 " + ocupados + "/" + cuposTotal + " cupos",
                        "#E8EAF6", "#3949AB"));
            }
            if (asientos > 0) {
                TextView tvA = buildChip(
                        "🎫 " + asientos + (asientos == 1 ? " asiento" : " asientos"),
                        "#FFF8E1", "#E65100");
                LinearLayout.LayoutParams lpA = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lpA.setMargins(dp(8), 0, 0, 0);
                tvA.setLayoutParams(lpA);
                filaChips.addView(tvA);
            }
            body.addView(filaChips);
        }

        body.addView(buildSep(dp(12), dp(12)));

        // Pasajeros
        if (pasajeros != null && pasajeros.length() > 0) {
            body.addView(buildSeccionPasajeros(pasajeros));
            body.addView(buildSep(dp(10), dp(10)));
        }

        // Botones
        body.addView(buildBotones(viajeId, idCond, nomCond));

        card.addView(body);
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  COMPONENTES
    // ─────────────────────────────────────────────────────────────────────────
    private LinearLayout buildFilaConductor(String nom, String tel) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);

        // Avatar
        String ini = nom.isEmpty() ? "C" : nom.substring(0, 1).toUpperCase();
        TextView tvAv = new TextView(this);
        tvAv.setText(ini);
        tvAv.setTextSize(18f);
        tvAv.setTypeface(null, Typeface.BOLD);
        tvAv.setTextColor(Color.WHITE);
        tvAv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpAv = new LinearLayout.LayoutParams(dp(46), dp(46));
        lpAv.setMargins(0, 0, dp(12), 0);
        tvAv.setLayoutParams(lpAv);
        GradientDrawable bgAv = new GradientDrawable();
        bgAv.setShape(GradientDrawable.OVAL);
        bgAv.setColors(new int[]{Color.parseColor("#00CED1"), Color.parseColor("#00897B")});
        bgAv.setOrientation(GradientDrawable.Orientation.TL_BR);
        tvAv.setBackground(bgAv);
        fila.addView(tvAv);

        // Textos
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvNom = new TextView(this);
        tvNom.setText("🚗  " + nom);
        tvNom.setTextSize(15f);
        tvNom.setTypeface(null, Typeface.BOLD);
        tvNom.setTextColor(Color.parseColor("#004D40"));
        col.addView(tvNom);

        if (!tel.isEmpty()) {
            TextView tvTel = new TextView(this);
            tvTel.setText("📞 " + tel);
            tvTel.setTextSize(12f);
            tvTel.setTextColor(Color.parseColor("#546E7A"));
            LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpT.topMargin = dp(2);
            tvTel.setLayoutParams(lpT);
            col.addView(tvTel);
        }
        fila.addView(col);
        return fila;
    }

    private LinearLayout buildFilaInfo(String emoji, String texto, String colorHex, int marginTop) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = marginTop;
        fila.setLayoutParams(lp);

        TextView tvIcon = new TextView(this);
        tvIcon.setText(emoji);
        tvIcon.setTextSize(18f);
        tvIcon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpI = new LinearLayout.LayoutParams(dp(46), dp(36));
        lpI.setMargins(0, 0, dp(12), 0);
        tvIcon.setLayoutParams(lpI);
        fila.addView(tvIcon);

        TextView tvTxt = new TextView(this);
        tvTxt.setText(texto);
        tvTxt.setTextSize(13f);
        tvTxt.setTextColor(Color.parseColor(colorHex));
        tvTxt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fila.addView(tvTxt);
        return fila;
    }

    private View buildSeccionPasajeros(JSONArray pasajeros) {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.VERTICAL);

        // Encabezado
        LinearLayout enc = new LinearLayout(this);
        enc.setOrientation(LinearLayout.HORIZONTAL);
        enc.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpEnc = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpEnc.setMargins(0, 0, 0, dp(8));
        enc.setLayoutParams(lpEnc);

        View barraAzul = new View(this);
        LinearLayout.LayoutParams lpBa = new LinearLayout.LayoutParams(dp(4), dp(18));
        lpBa.setMargins(0, 0, dp(8), 0);
        barraAzul.setLayoutParams(lpBa);
        GradientDrawable bgBa = new GradientDrawable();
        bgBa.setShape(GradientDrawable.RECTANGLE);
        bgBa.setCornerRadius(dp(3));
        bgBa.setColor(Color.parseColor("#1565C0"));
        barraAzul.setBackground(bgBa);
        enc.addView(barraAzul);

        TextView tvEnc = new TextView(this);
        tvEnc.setText("Pasajeros a bordo");
        tvEnc.setTextSize(13f);
        tvEnc.setTypeface(null, Typeface.BOLD);
        tvEnc.setTextColor(Color.parseColor("#004D40"));
        tvEnc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        enc.addView(tvEnc);

        TextView tvCnt = new TextView(this);
        tvCnt.setText(String.valueOf(pasajeros.length()));
        tvCnt.setTextSize(11f);
        tvCnt.setTypeface(null, Typeface.BOLD);
        tvCnt.setTextColor(Color.WHITE);
        tvCnt.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable bgCnt = new GradientDrawable();
        bgCnt.setShape(GradientDrawable.RECTANGLE);
        bgCnt.setCornerRadius(dp(12));
        bgCnt.setColor(Color.parseColor("#EF5350"));
        tvCnt.setBackground(bgCnt);
        enc.addView(tvCnt);
        sec.addView(enc);

        // Filas de pasajeros
        String[][] paleta = {
                {"#00CED1","#00897B"}, {"#1565C0","#1976D2"},
                {"#E65100","#FF9800"}, {"#6A1B9A","#8E24AA"}
        };
        for (int i = 0; i < pasajeros.length(); i++) {
            JSONObject p = pasajeros.optJSONObject(i);
            if (p == null) continue;

            String nom = "";
            String est = "CONFIRMADA";
            int    asi = 1;

            // Buscar nombre en usuario/pasajero anidado
            for (String key : new String[]{"usuario","pasajero","user"}) {
                JSONObject src = p.optJSONObject(key);
                if (src != null) {
                    if (nom.isEmpty()) nom = extraerNombre(src);
                }
            }
            if (nom.isEmpty()) nom = extraerNombre(p);
            if (nom.isEmpty()) nom = "Pasajero";

            String estTmp = p.optString("estado","");
            if (!estTmp.isEmpty() && !estTmp.equals("null")) est = estTmp.toUpperCase();

            asi = p.optInt("asientos", p.optInt("numeroAsientos", 1));

            sec.addView(buildFilaPasajero(nom, asi, est, paleta[i % paleta.length]));
        }
        return sec;
    }

    private View buildFilaPasajero(String nom, int asi, String est, String[] colores) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        fila.setLayoutParams(lp);

        // Avatar
        String ini = nom.isEmpty() ? "P" : nom.substring(0, 1).toUpperCase();
        TextView tvAv = new TextView(this);
        tvAv.setText(ini);
        tvAv.setTextSize(14f);
        tvAv.setTypeface(null, Typeface.BOLD);
        tvAv.setTextColor(Color.WHITE);
        tvAv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpAv = new LinearLayout.LayoutParams(dp(36), dp(36));
        lpAv.setMargins(0, 0, dp(10), 0);
        tvAv.setLayoutParams(lpAv);
        GradientDrawable bgAv = new GradientDrawable();
        bgAv.setShape(GradientDrawable.OVAL);
        bgAv.setColors(new int[]{Color.parseColor(colores[0]), Color.parseColor(colores[1])});
        bgAv.setOrientation(GradientDrawable.Orientation.TL_BR);
        tvAv.setBackground(bgAv);
        fila.addView(tvAv);

        // Nombre + asientos
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvNom = new TextView(this);
        tvNom.setText(nom);
        tvNom.setTextSize(13.5f);
        tvNom.setTypeface(null, Typeface.BOLD);
        tvNom.setTextColor(Color.parseColor("#1A1A2E"));
        col.addView(tvNom);

        TextView tvAsi = new TextView(this);
        tvAsi.setText("🎫 " + asi + (asi == 1 ? " asiento" : " asientos"));
        tvAsi.setTextSize(11f);
        tvAsi.setTextColor(Color.parseColor("#546E7A"));
        LinearLayout.LayoutParams lpA = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpA.topMargin = dp(2);
        tvAsi.setLayoutParams(lpA);
        col.addView(tvAsi);
        fila.addView(col);

        // Badge estado
        String labelEst; String colorEst;
        switch (est) {
            case "RECOGIDO":   labelEst = "RECOGIDO";   colorEst = "#00897B"; break;
            case "COMPLETADO": labelEst = "COMPLETADO"; colorEst = "#2E7D32"; break;
            case "CANCELADO":  labelEst = "CANCELADO";  colorEst = "#B71C1C"; break;
            default:           labelEst = "CONFIRMADO"; colorEst = "#1565C0"; break;
        }
        TextView tvEst = new TextView(this);
        tvEst.setText(labelEst);
        tvEst.setTextSize(10f);
        tvEst.setTypeface(null, Typeface.BOLD);
        tvEst.setTextColor(Color.WHITE);
        tvEst.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable bgEst = new GradientDrawable();
        bgEst.setShape(GradientDrawable.RECTANGLE);
        bgEst.setCornerRadius(dp(10));
        bgEst.setColor(Color.parseColor(colorEst));
        tvEst.setBackground(bgEst);
        fila.addView(tvEst);

        return fila;
    }

    private View buildBotones(int viajeId, int idCond, String nomCond) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);

        // Botón calificar
        MaterialButton btnCal = new MaterialButton(this);
        btnCal.setTextSize(13f);
        btnCal.setTextColor(Color.WHITE);
        btnCal.setCornerRadius(dp(20));
        LinearLayout.LayoutParams lpCal = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lpCal.setMargins(0, 0, dp(8), 0);
        btnCal.setLayoutParams(lpCal);

        final MaterialButton fBtn = btnCal;
        if (idCond > 0) {
            btnCal.setBackgroundColor(Color.parseColor("#7C4DFF"));
            btnCal.setText("⭐ Calificar");
            new CalificacionesManager(this).verificarCalificacion(
                    viajeId, idPasajero, idCond,
                    new CalificacionesManager.OnVerificacionListener() {
                        @Override public void onDebeCalificar() {
                            if (!isFinishing()) runOnUiThread(() -> {
                                fBtn.setEnabled(true); fBtn.setAlpha(1f);
                                fBtn.setBackgroundColor(Color.parseColor("#7C4DFF"));
                                fBtn.setText("⭐ Calificar");
                            });
                        }
                        @Override public void onYaCalifico(int p, String e) {
                            if (!isFinishing()) runOnUiThread(() -> {
                                fBtn.setEnabled(false); fBtn.setAlpha(0.7f);
                                fBtn.setBackgroundColor(Color.parseColor("#9E9E9E"));
                                fBtn.setText("Calificado " + p + "⭐");
                            });
                        }
                    });
            btnCal.setOnClickListener(v ->
                    CalificacionController.mostrarBottomSheetCalificar(
                            this, viajeId, idCond, nomCond,
                            "",              // ← fotoCalificado
                            idPasajero, false,
                            (pun, com) -> {
                                fBtn.setText("Calificado " + pun + "⭐");
                                fBtn.setBackgroundColor(Color.parseColor("#9E9E9E"));
                                fBtn.setEnabled(false);
                                fBtn.setAlpha(0.7f);
                            }));
        } else {
            btnCal.setText("Sin conductor");
            btnCal.setEnabled(false);
            btnCal.setAlpha(0.5f);
            btnCal.setBackgroundColor(Color.parseColor("#ECEFF1"));
            btnCal.setTextColor(Color.parseColor("#90A4AE"));
        }
        fila.addView(btnCal);

        // Botón ver detalle
        MaterialButton btnDet = new MaterialButton(this);
        btnDet.setText("Ver detalle");
        btnDet.setTextSize(13f);
        btnDet.setTextColor(Color.parseColor("#004D40"));
        btnDet.setCornerRadius(dp(20));
        btnDet.setBackgroundColor(Color.parseColor("#E0F2F1"));
        btnDet.setLayoutParams(new LinearLayout.LayoutParams(0, dp(46), 1f));
        btnDet.setOnClickListener(v -> {
            Intent i = new Intent(this, DetalleViajeActivity.class);
            i.putExtra("ID_VIAJE", viajeId);
            startActivity(i);
        });
        fila.addView(btnDet);
        return fila;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS VISUALES
    // ─────────────────────────────────────────────────────────────────────────
    private TextView buildChip(String texto, String bgColor, String textColor) {
        TextView tv = new TextView(this);
        tv.setText(texto);
        tv.setTextSize(11.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor(textColor));
        tv.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(12));
        bg.setColor(Color.parseColor(bgColor));
        tv.setBackground(bg);
        return tv;
    }

    private View buildSep(int mt, int mb) {
        View sep = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(0, mt, 0, mb);
        sep.setLayoutParams(lp);
        sep.setBackgroundColor(Color.parseColor("#E0F2F1"));
        return sep;
    }

    private void mostrarVacio(int total) {
        if (tvVacio == null) return;
        tvVacio.setText(total == 0
                ? "No tienes reservas registradas aún"
                : "No tienes viajes completados aún");
        tvVacio.setTextSize(14f);
        tvVacio.setTextColor(Color.parseColor("#90A4AE"));
        tvVacio.setGravity(Gravity.CENTER);
        tvVacio.setVisibility(View.VISIBLE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS JSON
    // ─────────────────────────────────────────────────────────────────────────
    private boolean esFinalizado(JSONObject r) {
        String estR = r.optString("estado", "").toUpperCase().trim();
        JSONObject v = r.optJSONObject("viaje");
        String estV = v != null ? v.optString("estado", "").toUpperCase().trim() : "";
        for (String s : new String[]{"FINALIZADO","COMPLETADO","COMPLETADA","PAGADO","REALIZADO","TERMINADO"})
            if (s.equals(estR) || s.equals(estV)) return true;
        // Si no tiene estado de viaje conocido y no está cancelado/pendiente, aceptar
        if (v == null && !estR.isEmpty()
                && !estR.equals("CANCELADO") && !estR.equals("CANCELADA")
                && !estR.equals("PENDIENTE")) return true;
        return false;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private int primerInt(JSONObject o, String[] claves) {
        for (String c : claves) { int v = o.optInt(c, -1); if (v > 0) return v; }
        return -1;
    }

    private String primerStr(JSONObject o, String[] claves) {
        if (o == null) return "";
        for (String c : claves) {
            String v = o.optString(c, "").trim();
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        return "";
    }

    private String extraerNombre(JSONObject o) {
        if (o == null) return "";
        for (String c : new String[]{"nombre","nombreCompleto","name","fullName","nombres","nombreUsuario"}) {
            String v = o.optString(c, "").trim();
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        String n = o.optString("nombres",""), a = o.optString("apellidos","");
        if (!n.isEmpty() || !a.isEmpty()) return (n + " " + a).trim();
        return "";
    }
}