package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MisReservasActivity extends AppCompatActivity {

    private static final String TAG      = "MisReservas";
    private static final double RADIO_KM = 2.0;

    // ── UI ────────────────────────────────────────────────────────────────────
    private TextInputEditText editOrigen, editDestino;
    private MaterialButton    btnBuscar, btnBuscarDesdeVacio;
    private LinearLayout      layoutBuscando;
    private LinearLayout      layoutLabelResultados;
    private TextView          txtLabelResultados, txtContadorResultados;
    private LinearLayout      layoutResultadosViajes;
    private LinearLayout      layoutVacio;
    private TextView          txtVacio, txtVacioSub;
    private LinearLayout      layoutLabelMisReservas;
    private LinearLayout      layoutMisReservas;
    private ProgressBar       loaderMisReservas;

    // ── Estado ────────────────────────────────────────────────────────────────
    private SessionManager session;
    private double latOrigen = 0, lngOrigen = 0;
    private double latDestino = 0, lngDestino = 0;
    private boolean busquedaActiva = false;

    // =========================================================================
    //  LIFECYCLE
    // =========================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_reservas);

        session = new SessionManager(this);
        initViews();
        cargarMisReservas();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refrescar reservas al volver de DetalleViajeActivity
        cargarMisReservas();
    }

    // =========================================================================
    //  INIT
    // =========================================================================
    private void initViews() {
        editOrigen             = findViewById(R.id.edit_origen_pasajero);
        editDestino            = findViewById(R.id.edit_destino_pasajero);
        btnBuscar              = findViewById(R.id.btn_buscar_viajes);
        btnBuscarDesdeVacio    = findViewById(R.id.btn_buscar_desde_vacio);
        layoutBuscando         = findViewById(R.id.layout_buscando);
        layoutLabelResultados  = findViewById(R.id.layout_label_resultados);
        txtLabelResultados     = findViewById(R.id.txt_label_resultados);
        txtContadorResultados  = findViewById(R.id.txt_contador_resultados);
        layoutResultadosViajes = findViewById(R.id.layout_resultados_viajes);
        layoutVacio            = findViewById(R.id.layout_vacio);
        txtVacio               = findViewById(R.id.txt_vacio);
        txtVacioSub            = findViewById(R.id.txt_vacio_sub);
        layoutLabelMisReservas = findViewById(R.id.layout_label_mis_reservas);
        layoutMisReservas      = findViewById(R.id.layout_mis_reservas);
        loaderMisReservas      = findViewById(R.id.loader_mis_reservas);

        btnBuscar.setOnClickListener(v -> iniciarBusqueda());

        btnBuscarDesdeVacio.setOnClickListener(v -> {
            // Scroll al formulario y enfocar
            editOrigen.requestFocus();
            layoutVacio.setVisibility(View.GONE);
        });
    }

    // =========================================================================
    //  CARGAR MIS RESERVAS EXISTENTES
    // =========================================================================
    private void cargarMisReservas() {
        loaderMisReservas.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_RESERVAS,
                response -> {
                    loaderMisReservas.setVisibility(View.GONE);
                    runOnUiThread(() -> mostrarMisReservas(response));
                },
                error -> {
                    loaderMisReservas.setVisibility(View.GONE);
                    Log.e(TAG, "Error cargando reservas: " + error);
                    // Si no hay reservas y no hay búsqueda activa, mostrar estado vacío
                    if (!busquedaActiva) {
                        runOnUiThread(() -> {
                            layoutVacio.setVisibility(View.VISIBLE);
                            txtVacio.setText("No tienes reservas aún");
                            txtVacioSub.setText("Encuentra un viaje y reserva tu próximo trayecto");
                        });
                    }
                }
        );
    }

    private void mostrarMisReservas(JSONArray reservas) {
        layoutMisReservas.removeAllViews();

        if (reservas == null || reservas.length() == 0) {
            // Solo mostrar vacío si no hay búsqueda activa
            if (!busquedaActiva) {
                layoutVacio.setVisibility(View.VISIBLE);
                txtVacio.setText("No tienes reservas aún");
                txtVacioSub.setText("Encuentra un viaje y reserva tu próximo trayecto");
            }
            layoutLabelMisReservas.setVisibility(View.GONE);
            return;
        }

        layoutVacio.setVisibility(View.GONE);
        layoutLabelMisReservas.setVisibility(View.VISIBLE);

        int count = 0;
        for (int i = 0; i < reservas.length(); i++) {
            JSONObject reserva = reservas.optJSONObject(i);
            if (reserva == null) continue;

            String estado = reserva.optString("estado", "").toUpperCase();
            if (estado.equals("CANCELADA") || estado.equals("CANCELADO")) continue;

            agregarCardMiReserva(reserva);
            count++;
        }

        if (count == 0) {
            layoutLabelMisReservas.setVisibility(View.GONE);
            if (!busquedaActiva) {
                layoutVacio.setVisibility(View.VISIBLE);
            }
        }
    }

    private void agregarCardMiReserva(JSONObject reserva) {
        float d   = getResources().getDisplayMetrics().density;
        int   p16 = (int)(16 * d);
        int   p12 = (int)(12 * d);
        int   p8  = (int)(8  * d);
        int   p4  = (int)(4  * d);

        // Extraer datos de la reserva
        int    idViaje    = 0;
        String origen     = "Origen";
        String destino    = "Destino";
        String conductor  = "Conductor";
        double precio     = 0;
        String estado     = reserva.optString("estado", "ACTIVA");
        String fechaSal   = "";
        int    asientos   = reserva.optInt("numeroAsientos", reserva.optInt("asientos", 1));
        String nombrePar  = reserva.optString("nombreParada", "");

        // Extraer viaje dentro de la reserva
        JSONObject viajeObj = reserva.optJSONObject("viaje");
        if (viajeObj != null) {
            idViaje = viajeObj.optInt("idViaje", viajeObj.optInt("id", 0));
            precio  = viajeObj.optDouble("precio", 0);
            fechaSal = viajeObj.optString("fechaHoraSalida", "");
            JSONObject ruta = viajeObj.optJSONObject("ruta");
            if (ruta != null) {
                origen  = ruta.optString("origen",  origen);
                destino = ruta.optString("destino", destino);
            }
            JSONObject cond = viajeObj.optJSONObject("conductor");
            if (cond != null) conductor = extractNombre(cond);
        }
        if (idViaje == 0) idViaje = reserva.optInt("idViaje", 0);

        // ── Construir card ────────────────────────────────────────────────────
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, 0, 0, p12);
        card.setLayoutParams(lpCard);
        card.setRadius(18 * d);
        card.setCardElevation(5 * d);
        card.setCardBackgroundColor(Color.WHITE);
        card.setClickable(true);
        card.setFocusable(true);

        // Borde de color según estado
        int colorBorde;
        switch (estado.toUpperCase()) {
            case "ACTIVA": case "CONFIRMADA": colorBorde = Color.parseColor("#4CAF50"); break;
            case "EN_CURSO": case "INICIADO": colorBorde = Color.parseColor("#0097A7"); break;
            case "PENDIENTE":                 colorBorde = Color.parseColor("#FF6F00"); break;
            default:                          colorBorde = Color.parseColor("#B2DFDB");
        }
        card.setStrokeColor(colorBorde);
        card.setStrokeWidth((int)(2 * d));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p16, p16, p16, p16);

        // ── Chip estado ───────────────────────────────────────────────────────
        LinearLayout filaEstado = new LinearLayout(this);
        filaEstado.setOrientation(LinearLayout.HORIZONTAL);
        filaEstado.setGravity(android.view.Gravity.CENTER_VERTICAL);
        filaEstado.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvEstado = new TextView(this);
        String estadoLabel;
        int    estadoColor;
        switch (estado.toUpperCase()) {
            case "ACTIVA": case "CONFIRMADA": estadoLabel = "✅ Confirmada"; estadoColor = 0xFF2E7D32; break;
            case "EN_CURSO": case "INICIADO": estadoLabel = "🚗 En curso";   estadoColor = 0xFF00838F; break;
            case "PENDIENTE":                 estadoLabel = "⏳ Pendiente";  estadoColor = 0xFFE65100; break;
            default:                          estadoLabel = "📌 " + estado;  estadoColor = 0xFF546E7A;
        }
        tvEstado.setText(estadoLabel);
        tvEstado.setTextSize(11f);
        tvEstado.setTextColor(Color.WHITE);
        tvEstado.setTypeface(null, android.graphics.Typeface.BOLD);
        tvEstado.setPadding(p8, p4, p8, p4);
        GradientDrawable bgEstado = new GradientDrawable();
        bgEstado.setShape(GradientDrawable.RECTANGLE);
        bgEstado.setCornerRadius(20 * d);
        bgEstado.setColor(estadoColor);
        tvEstado.setBackground(bgEstado);
        filaEstado.addView(tvEstado);

        // Espaciador
        View esp = new View(this);
        esp.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        filaEstado.addView(esp);

        // Asientos chip
        TextView tvAsientos = new TextView(this);
        tvAsientos.setText("💺 " + asientos + (asientos == 1 ? " asiento" : " asientos"));
        tvAsientos.setTextSize(11f);
        tvAsientos.setTextColor(Color.parseColor("#1565C0"));
        tvAsientos.setTypeface(null, android.graphics.Typeface.BOLD);
        tvAsientos.setPadding(p8, p4, p8, p4);
        GradientDrawable bgAs = new GradientDrawable();
        bgAs.setShape(GradientDrawable.RECTANGLE);
        bgAs.setCornerRadius(20 * d);
        bgAs.setColor(Color.parseColor("#E3F2FD"));
        tvAsientos.setBackground(bgAs);
        filaEstado.addView(tvAsientos);
        inner.addView(filaEstado);

        // ── Separador ─────────────────────────────────────────────────────────
        View sep1 = new View(this);
        LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d));
        lpSep.setMargins(0, p8, 0, p8);
        sep1.setLayoutParams(lpSep);
        sep1.setBackgroundColor(Color.parseColor("#E0F2F1"));
        inner.addView(sep1);

        // ── Ruta con indicador visual ─────────────────────────────────────────
        LinearLayout filaRuta = new LinearLayout(this);
        filaRuta.setOrientation(LinearLayout.HORIZONTAL);
        filaRuta.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Indicador vertical
        LinearLayout indicador = new LinearLayout(this);
        indicador.setOrientation(LinearLayout.VERTICAL);
        indicador.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lpInd = new LinearLayout.LayoutParams(
                (int)(18 * d), LinearLayout.LayoutParams.WRAP_CONTENT);
        lpInd.setMargins(0, 0, p12, 0);
        indicador.setLayoutParams(lpInd);

        View cv = new View(this);
        cv.setLayoutParams(new LinearLayout.LayoutParams((int)(10*d), (int)(10*d)));
        GradientDrawable gv = new GradientDrawable();
        gv.setShape(GradientDrawable.OVAL); gv.setColor(Color.parseColor("#4CAF50"));
        cv.setBackground(gv);
        indicador.addView(cv);

        View lv = new View(this);
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams((int)(2*d), (int)(26*d));
        lpL.setMargins((int)(4*d),(int)(2*d),(int)(4*d),(int)(2*d));
        lv.setLayoutParams(lpL);
        lv.setBackgroundColor(Color.parseColor("#B2DFDB"));
        indicador.addView(lv);

        View cr = new View(this);
        cr.setLayoutParams(new LinearLayout.LayoutParams((int)(10*d), (int)(10*d)));
        GradientDrawable gr = new GradientDrawable();
        gr.setShape(GradientDrawable.OVAL); gr.setColor(Color.parseColor("#EF5350"));
        cr.setBackground(gr);
        indicador.addView(cr);

        filaRuta.addView(indicador);

        // Textos origen / destino
        LinearLayout colRuta = new LinearLayout(this);
        colRuta.setOrientation(LinearLayout.VERTICAL);
        colRuta.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvOrigen = new TextView(this);
        tvOrigen.setText(origen);
        tvOrigen.setTextSize(14f);
        tvOrigen.setTypeface(null, android.graphics.Typeface.BOLD);
        tvOrigen.setTextColor(Color.parseColor("#004D40"));
        tvOrigen.setMaxLines(1);
        tvOrigen.setEllipsize(android.text.TextUtils.TruncateAt.END);
        colRuta.addView(tvOrigen);

        // Parada del pasajero
        if (!nombrePar.isEmpty() && !nombrePar.equals(destino)) {
            TextView tvParada = new TextView(this);
            tvParada.setText("🔵 Bajas en: " + nombrePar);
            tvParada.setTextSize(12f);
            tvParada.setTextColor(Color.parseColor("#0097A7"));
            LinearLayout.LayoutParams lpP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpP.topMargin = (int)(10 * d);
            tvParada.setLayoutParams(lpP);
            colRuta.addView(tvParada);
        }

        TextView tvDestino = new TextView(this);
        tvDestino.setText(destino);
        tvDestino.setTextSize(13f);
        tvDestino.setTextColor(Color.parseColor("#546E7A"));
        tvDestino.setMaxLines(1);
        tvDestino.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lpDest = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpDest.topMargin = (int)(nombrePar.isEmpty() || nombrePar.equals(destino) ? 12 * d : 10 * d);
        tvDestino.setLayoutParams(lpDest);
        colRuta.addView(tvDestino);

        filaRuta.addView(colRuta);
        inner.addView(filaRuta);

        // ── Separador ─────────────────────────────────────────────────────────
        View sep2 = new View(this);
        LinearLayout.LayoutParams lpSep2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d));
        lpSep2.setMargins(0, p8, 0, p8);
        sep2.setLayoutParams(lpSep2);
        sep2.setBackgroundColor(Color.parseColor("#E0F2F1"));
        inner.addView(sep2);

        // ── Fila: conductor, precio, fecha ────────────────────────────────────
        LinearLayout filaInfo = new LinearLayout(this);
        filaInfo.setOrientation(LinearLayout.HORIZONTAL);
        filaInfo.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tvCond = new TextView(this);
        tvCond.setText("🚗 " + (conductor.isEmpty() ? "Conductor" : conductor));
        tvCond.setTextSize(12f);
        tvCond.setTextColor(Color.parseColor("#00695C"));
        tvCond.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvCond.setMaxLines(1);
        tvCond.setEllipsize(android.text.TextUtils.TruncateAt.END);
        filaInfo.addView(tvCond);

        if (precio > 0) {
            TextView tvPrecio = new TextView(this);
            tvPrecio.setText("💵 $" + String.format("%.0f", precio));
            tvPrecio.setTextSize(13f);
            tvPrecio.setTypeface(null, android.graphics.Typeface.BOLD);
            tvPrecio.setTextColor(Color.parseColor("#FF6F00"));
            LinearLayout.LayoutParams lpPrecio = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpPrecio.setMargins(p8, 0, 0, 0);
            tvPrecio.setLayoutParams(lpPrecio);
            filaInfo.addView(tvPrecio);
        }

        inner.addView(filaInfo);

        // Fecha salida
        if (!fechaSal.isEmpty()) {
            String fechaLeg = fechaSal.length() > 10
                    ? fechaSal.substring(0, 16).replace("T", " ") : fechaSal;
            TextView tvFecha = new TextView(this);
            tvFecha.setText("🕐 " + fechaLeg);
            tvFecha.setTextSize(11f);
            tvFecha.setTextColor(Color.parseColor("#90A4AE"));
            LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpF.topMargin = p4;
            tvFecha.setLayoutParams(lpF);
            inner.addView(tvFecha);
        }

        card.addView(inner);

        // Click → ver detalle
        final int idFinal = idViaje;
        card.setOnClickListener(v -> {
            if (idFinal == 0) {
                Toast.makeText(this, "No se puede abrir esta reserva", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, DetalleViajeActivity.class);
            intent.putExtra("ID_VIAJE", idFinal);
            startActivity(intent);
        });

        layoutMisReservas.addView(card);
    }

    // =========================================================================
    //  BÚSQUEDA DE VIAJES POR RUTA DEL PASAJERO
    // =========================================================================
    private void iniciarBusqueda() {
        String textoOrigen  = editOrigen.getText() != null ? editOrigen.getText().toString().trim() : "";
        String textoDestino = editDestino.getText() != null ? editDestino.getText().toString().trim() : "";

        if (textoOrigen.isEmpty()) {
            editOrigen.setError("Ingresa tu punto de origen");
            editOrigen.requestFocus();
            return;
        }

        busquedaActiva = true;
        btnBuscar.setEnabled(false);
        btnBuscar.setText("Buscando...");
        layoutBuscando.setVisibility(View.VISIBLE);
        layoutResultadosViajes.removeAllViews();
        layoutLabelResultados.setVisibility(View.GONE);
        layoutVacio.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                // Geocodificar origen
                double[] coordOrigen = geocodificar(textoOrigen);
                latOrigen = coordOrigen[0];
                lngOrigen = coordOrigen[1];
                Log.d(TAG, "✅ Origen: " + latOrigen + ", " + lngOrigen);

                // Geocodificar destino si se ingresó
                if (!textoDestino.isEmpty()) {
                    try {
                        double[] coordDestino = geocodificar(textoDestino);
                        latDestino = coordDestino[0];
                        lngDestino = coordDestino[1];
                        Log.d(TAG, "✅ Destino: " + latDestino + ", " + lngDestino);
                    } catch (Exception e) {
                        Log.w(TAG, "No se pudo geocodificar destino");
                        latDestino = 0; lngDestino = 0;
                    }
                } else {
                    latDestino = 0; lngDestino = 0;
                }

                runOnUiThread(() -> buscarViajesCercanos(textoOrigen, textoDestino));

            } catch (Exception e) {
                Log.e(TAG, "Error geocodificando: " + e.getMessage());
                runOnUiThread(() -> {
                    layoutBuscando.setVisibility(View.GONE);
                    btnBuscar.setEnabled(true);
                    btnBuscar.setText("🚗  Buscar viajes disponibles");
                    busquedaActiva = false;
                    Toast.makeText(this,
                            "No se encontró la dirección. Intenta con más detalle.",
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void buscarViajesCercanos(String textoOrigen, String textoDestino) {
        // Primero intentar endpoint con filtro por coordenadas
        String url = Constantes.BUSCAR_VIAJES
                + "?lat="   + latOrigen
                + "&lng="   + lngOrigen
                + "&radio=" + RADIO_KM
                + "&estado=PROGRAMADO";

        ConexionApi.getInstance(this).getArray(url,
                response -> procesarViajesEncontrados(response, textoOrigen, textoDestino),
                error -> buscarViajesGeneral(textoOrigen, textoDestino)
        );
    }

    private void buscarViajesGeneral(String textoOrigen, String textoDestino) {
        ConexionApi.getInstance(this).getArray(
                Constantes.VIAJES + "?estado=PROGRAMADO",
                response -> {
                    JSONArray filtrados = filtrarPorProximidad(response);
                    procesarViajesEncontrados(filtrados, textoOrigen, textoDestino);
                },
                error -> ConexionApi.getInstance(this).getArray(
                        Constantes.VIAJES,
                        response -> {
                            JSONArray filtrados = filtrarPorProximidad(response);
                            procesarViajesEncontrados(filtrados, textoOrigen, textoDestino);
                        },
                        err -> runOnUiThread(() -> {
                            layoutBuscando.setVisibility(View.GONE);
                            btnBuscar.setEnabled(true);
                            btnBuscar.setText("🚗  Buscar viajes disponibles");
                            busquedaActiva = false;
                            Toast.makeText(this, "Error conectando al servidor", Toast.LENGTH_LONG).show();
                        })
                )
        );
    }

    private JSONArray filtrarPorProximidad(JSONArray viajes) {
        JSONArray resultado = new JSONArray();
        if (latOrigen == 0 && lngOrigen == 0) return resultado;

        for (int i = 0; i < viajes.length(); i++) {
            try {
                JSONObject viaje = viajes.getJSONObject(i);
                String estado = viaje.optString("estado", "").toUpperCase();
                if (estado.equals("FINALIZADO") || estado.equals("CANCELADO")) continue;

                double latRuta = 0, lngRuta = 0;
                JSONObject ruta = viaje.optJSONObject("ruta");
                if (ruta != null) {
                    latRuta = ruta.optDouble("latOrigen", 0);
                    lngRuta = ruta.optDouble("lngOrigen", 0);
                }
                if (latRuta == 0) latRuta = viaje.optDouble("latOrigen", 0);
                if (lngRuta == 0) lngRuta = viaje.optDouble("lngOrigen", 0);

                if (latRuta == 0 && lngRuta == 0) {
                    resultado.put(viaje);
                    continue;
                }

                double distancia = calcularDistanciaKm(latOrigen, lngOrigen, latRuta, lngRuta);
                if (distancia <= RADIO_KM) resultado.put(viaje);

            } catch (Exception e) {
                Log.e(TAG, "Error filtrando viaje", e);
            }
        }
        Log.d(TAG, "✅ Viajes filtrados: " + resultado.length());
        return resultado;
    }

    // =========================================================================
    //  MOSTRAR VIAJES ENCONTRADOS
    // =========================================================================
    private void procesarViajesEncontrados(JSONArray viajes, String textoOrigen, String textoDestino) {
        runOnUiThread(() -> {
            layoutBuscando.setVisibility(View.GONE);
            btnBuscar.setEnabled(true);
            btnBuscar.setText("🚗  Buscar viajes disponibles");
            layoutResultadosViajes.removeAllViews();

            if (viajes == null || viajes.length() == 0) {
                layoutLabelResultados.setVisibility(View.GONE);
                layoutVacio.setVisibility(View.VISIBLE);
                txtVacio.setText("😔 No encontramos viajes");
                txtVacioSub.setText("No hay conductores que pasen cerca de \""
                        + textoOrigen + "\" en este momento.\n\nIntenta con una calle más conocida.");
                return;
            }

            layoutVacio.setVisibility(View.GONE);
            layoutLabelResultados.setVisibility(View.VISIBLE);
            txtLabelResultados.setText("Conductores que pasan cerca de ti");
            txtContadorResultados.setText(String.valueOf(viajes.length()));

            for (int i = 0; i < viajes.length(); i++) {
                JSONObject viaje = viajes.optJSONObject(i);
                if (viaje != null) agregarCardViajeEncontrado(viaje);
            }
        });
    }

    private void agregarCardViajeEncontrado(JSONObject viaje) {
        float d   = getResources().getDisplayMetrics().density;
        int   p16 = (int)(16 * d);
        int   p12 = (int)(12 * d);
        int   p8  = (int)(8  * d);
        int   p4  = (int)(4  * d);

        int    idViaje   = viaje.optInt("idViaje", viaje.optInt("id", 0));
        String estado    = viaje.optString("estado", "PROGRAMADO");
        double precio    = viaje.optDouble("precio", 0);
        int    cuposDisp = viaje.optInt("cuposDisponibles", 0);
        String fechaSal  = viaje.optString("fechaHoraSalida",
                viaje.optString("fecha", ""));
        String origen    = "Origen";
        String destino   = "Destino";
        String cond      = "Conductor";

        JSONObject ruta = viaje.optJSONObject("ruta");
        if (ruta != null) {
            origen  = ruta.optString("origen",  origen);
            destino = ruta.optString("destino", destino);
        } else {
            origen  = viaje.optString("origen",  origen);
            destino = viaje.optString("destino", destino);
        }

        JSONObject condObj = viaje.optJSONObject("conductor");
        if (condObj != null) {
            cond = extractNombre(condObj);
            if (cond.isEmpty()) {
                JSONObject u = condObj.optJSONObject("usuario");
                if (u != null) cond = extractNombre(u);
            }
        }
        if (cond.isEmpty()) cond = viaje.optString("nombreConductor", "Conductor");

        // ── Card ──────────────────────────────────────────────────────────────
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, 0, 0, p12);
        card.setLayoutParams(lpCard);
        card.setRadius(18 * d);
        card.setCardElevation(6 * d);
        card.setCardBackgroundColor(Color.WHITE);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p16, p16, p16, p16);

        // Fila superior: ruta + estado
        LinearLayout filaTop = new LinearLayout(this);
        filaTop.setOrientation(LinearLayout.HORIZONTAL);
        filaTop.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Indicador visual
        LinearLayout indRuta = new LinearLayout(this);
        indRuta.setOrientation(LinearLayout.VERTICAL);
        indRuta.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lpInd = new LinearLayout.LayoutParams(
                (int)(20 * d), LinearLayout.LayoutParams.WRAP_CONTENT);
        lpInd.setMargins(0, 0, p12, 0);
        indRuta.setLayoutParams(lpInd);

        View cVerde = new View(this);
        cVerde.setLayoutParams(new LinearLayout.LayoutParams((int)(10*d),(int)(10*d)));
        GradientDrawable gv2 = new GradientDrawable();
        gv2.setShape(GradientDrawable.OVAL); gv2.setColor(Color.parseColor("#4CAF50"));
        cVerde.setBackground(gv2); indRuta.addView(cVerde);

        View ln = new View(this);
        LinearLayout.LayoutParams lpLn = new LinearLayout.LayoutParams((int)(2*d),(int)(28*d));
        lpLn.setMargins((int)(4*d),(int)(2*d),(int)(4*d),(int)(2*d));
        ln.setLayoutParams(lpLn); ln.setBackgroundColor(Color.parseColor("#B2DFDB"));
        indRuta.addView(ln);

        View cRojo = new View(this);
        cRojo.setLayoutParams(new LinearLayout.LayoutParams((int)(10*d),(int)(10*d)));
        GradientDrawable gr2 = new GradientDrawable();
        gr2.setShape(GradientDrawable.OVAL); gr2.setColor(Color.parseColor("#EF5350"));
        cRojo.setBackground(gr2); indRuta.addView(cRojo);

        filaTop.addView(indRuta);

        // Textos ruta
        LinearLayout colRuta = new LinearLayout(this);
        colRuta.setOrientation(LinearLayout.VERTICAL);
        colRuta.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvOr = new TextView(this);
        tvOr.setText("🟢 " + origen);
        tvOr.setTextSize(13f);
        tvOr.setTypeface(null, android.graphics.Typeface.BOLD);
        tvOr.setTextColor(Color.parseColor("#004D40"));
        tvOr.setMaxLines(1);
        tvOr.setEllipsize(android.text.TextUtils.TruncateAt.END);
        colRuta.addView(tvOr);

        TextView tvDest = new TextView(this);
        tvDest.setText("🔴 " + destino);
        tvDest.setTextSize(13f);
        tvDest.setTextColor(Color.parseColor("#546E7A"));
        tvDest.setMaxLines(1);
        tvDest.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lpDst = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpDst.topMargin = (int)(14 * d);
        tvDest.setLayoutParams(lpDst);
        colRuta.addView(tvDest);

        filaTop.addView(colRuta);

        // Chip estado
        TextView tvEst = new TextView(this);
        String estLabel;
        int estColor;
        switch (estado.toUpperCase()) {
            case "PROGRAMADO": estLabel = "📅 Prog.";   estColor = 0xFF1565C0; break;
            case "INICIADO":   estLabel = "🚗 Activo";  estColor = 0xFF2E7D32; break;
            case "DISPONIBLE": estLabel = "✅ Libre";   estColor = 0xFF00897B; break;
            default:           estLabel = estado;       estColor = 0xFF546E7A;
        }
        tvEst.setText(estLabel);
        tvEst.setTextSize(10f);
        tvEst.setTextColor(Color.WHITE);
        tvEst.setTypeface(null, android.graphics.Typeface.BOLD);
        tvEst.setPadding(p8, p4, p8, p4);
        GradientDrawable bgEst = new GradientDrawable();
        bgEst.setShape(GradientDrawable.RECTANGLE);
        bgEst.setCornerRadius(20 * d);
        bgEst.setColor(estColor);
        tvEst.setBackground(bgEst);
        LinearLayout.LayoutParams lpEst = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpEst.setMargins(p8, 0, 0, 0);
        tvEst.setLayoutParams(lpEst);
        filaTop.addView(tvEst);

        inner.addView(filaTop);

        // Divisor
        View div = new View(this);
        LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d));
        lpDiv.setMargins(0, p12, 0, p12);
        div.setLayoutParams(lpDiv);
        div.setBackgroundColor(Color.parseColor("#E0F2F1"));
        inner.addView(div);

        // Fila inferior: conductor, precio, cupos
        LinearLayout filaInfo = new LinearLayout(this);
        filaInfo.setOrientation(LinearLayout.HORIZONTAL);
        filaInfo.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tvCond = new TextView(this);
        tvCond.setText("🚗 " + cond);
        tvCond.setTextSize(12f);
        tvCond.setTextColor(Color.parseColor("#00695C"));
        tvCond.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvCond.setMaxLines(1);
        tvCond.setEllipsize(android.text.TextUtils.TruncateAt.END);
        filaInfo.addView(tvCond);

        TextView tvPrecio = new TextView(this);
        tvPrecio.setText("💵 $" + String.format("%.0f", precio));
        tvPrecio.setTextSize(13f);
        tvPrecio.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPrecio.setTextColor(Color.parseColor("#FF6F00"));
        LinearLayout.LayoutParams lpPr = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpPr.setMargins(p8, 0, p8, 0);
        tvPrecio.setLayoutParams(lpPr);
        filaInfo.addView(tvPrecio);

        TextView tvCupos = new TextView(this);
        tvCupos.setText("💺 " + cuposDisp);
        tvCupos.setTextSize(12f);
        tvCupos.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCupos.setTextColor(cuposDisp > 0
                ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
        tvCupos.setPadding(p8, p4, p8, p4);
        GradientDrawable bgCupos = new GradientDrawable();
        bgCupos.setShape(GradientDrawable.RECTANGLE);
        bgCupos.setCornerRadius(12 * d);
        bgCupos.setColor(cuposDisp > 0
                ? Color.parseColor("#E8F5E9") : Color.parseColor("#FFEBEE"));
        tvCupos.setBackground(bgCupos);
        filaInfo.addView(tvCupos);

        inner.addView(filaInfo);

        // Fecha
        if (!fechaSal.isEmpty()) {
            String fechaLeg = fechaSal.length() > 10
                    ? fechaSal.substring(0, 16).replace("T", " ") : fechaSal;
            TextView tvFecha = new TextView(this);
            tvFecha.setText("🕐 Salida: " + fechaLeg);
            tvFecha.setTextSize(11f);
            tvFecha.setTextColor(Color.parseColor("#90A4AE"));
            LinearLayout.LayoutParams lpFe = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpFe.topMargin = p8;
            tvFecha.setLayoutParams(lpFe);
            inner.addView(tvFecha);
        }

        // Botón "Ver y reservar"
        MaterialButton btnReservar = new MaterialButton(this);
        btnReservar.setText("Ver viaje y reservar →");
        btnReservar.setTextColor(Color.WHITE);
        btnReservar.setTextSize(13f);
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(44 * d));
        lpBtn.setMargins(0, p12, 0, 0);
        btnReservar.setLayoutParams(lpBtn);
        GradientDrawable bgBtn = new GradientDrawable();
        bgBtn.setShape(GradientDrawable.RECTANGLE);
        bgBtn.setCornerRadius(12 * d);
        bgBtn.setColor(Color.parseColor("#00897B"));
        btnReservar.setBackgroundColor(Color.parseColor("#00897B"));
        // Usar setBackgroundTintList para MaterialButton
        btnReservar.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#00897B")));
        btnReservar.setCornerRadius((int)(12 * d));
        inner.addView(btnReservar);

        card.addView(inner);

        // Click card o botón → DetalleViajeActivity
        final int idFinal = idViaje;
        View.OnClickListener clickListener = v -> {
            if (idFinal == 0) {
                Toast.makeText(this, "No se puede abrir este viaje", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, DetalleViajeActivity.class);
            intent.putExtra("ID_VIAJE", idFinal);
            startActivity(intent);
        };
        card.setOnClickListener(clickListener);
        btnReservar.setOnClickListener(clickListener);

        layoutResultadosViajes.addView(card);
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private double[] geocodificar(String direccion) throws Exception {
        String url = "https://nominatim.openstreetmap.org/search?q="
                + java.net.URLEncoder.encode(direccion + ", Popayan, Colombia", "UTF-8")
                + "&format=json&limit=1";
        String respuesta = peticionHttp(url);
        JSONArray arr = new JSONArray(respuesta);

        if (arr.length() == 0) {
            url = "https://nominatim.openstreetmap.org/search?q="
                    + java.net.URLEncoder.encode(direccion + ", Colombia", "UTF-8")
                    + "&format=json&limit=1";
            arr = new JSONArray(peticionHttp(url));
        }
        if (arr.length() == 0) throw new Exception("No encontrado: " + direccion);

        JSONObject o = arr.getJSONObject(0);
        return new double[]{o.getDouble("lat"), o.getDouble("lon")};
    }

    private String peticionHttp(String urlString) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Moviflexx-App/1.0");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private double calcularDistanciaKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng/2) * Math.sin(dLng/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String extractNombre(JSONObject obj) {
        if (obj == null) return "";
        for (String key : new String[]{
                "nombre", "nombreCompleto", "name", "fullName",
                "nombreUsuario", "displayName", "nombres"}) {
            String v = obj.optString(key, "");
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        String n = obj.optString("nombres",   "");
        String a = obj.optString("apellidos", "");
        if (!n.isEmpty() || !a.isEmpty()) return (n + " " + a).trim();
        return "";
    }
}