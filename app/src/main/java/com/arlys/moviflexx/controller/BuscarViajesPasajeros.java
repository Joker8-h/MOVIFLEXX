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
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class BuscarViajesPasajeros extends AppCompatActivity {

    private static final String TAG         = "BuscarViajes";
    private static final double RADIO_KM    = 1.0; // radio de búsqueda en km

    // UI
    private TextInputEditText editOrigen, editDestino;
    private MaterialButton    btnBuscar;
    private ProgressBar       loader;
    private LinearLayout      layoutResultados;
    private TextView          txtResultadoVacio;
    private MapView           map;

    // Estado
    private GeoPoint puntoOrigen  = null;
    private GeoPoint puntoDestino = null;
    private SessionManager session;

    // Marcadores en mapa
    private final ArrayList<Marker> marcadores = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_buscar_viajes_pasajeros);

        session = new SessionManager(this);

        initViews();
        configurarMapa();
    }

    @Override protected void onResume()  { super.onResume();  if (map != null) map.onResume(); }
    @Override protected void onPause()   { super.onPause();   if (map != null) map.onPause();  }

    // =========================================================================
    //  INIT
    // =========================================================================
    private void initViews() {
        editOrigen        = findViewById(R.id.edit_origen_pasajero);
        editDestino       = findViewById(R.id.edit_destino_pasajero);
        btnBuscar         = findViewById(R.id.btn_buscar_viajes);
        loader            = findViewById(R.id.loader_buscar);
        layoutResultados  = findViewById(R.id.layout_resultados);
        txtResultadoVacio = findViewById(R.id.txt_resultado_vacio);
        map               = findViewById(R.id.map_buscar);

        btnBuscar.setOnClickListener(v -> iniciarBusqueda());
    }

    private void configurarMapa() {
        if (map == null) return;
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.getController().setZoom(14.0);
        map.getController().setCenter(new GeoPoint(2.4419, -76.6063)); // Popayán
    }

    // =========================================================================
    //  BUSQUEDA PRINCIPAL
    // =========================================================================
    private void iniciarBusqueda() {
        String textoOrigen  = editOrigen.getText().toString().trim();
        String textoDestino = editDestino.getText().toString().trim();

        if (textoOrigen.isEmpty()) {
            editOrigen.setError("Ingresa tu ubicación de origen");
            return;
        }

        btnBuscar.setEnabled(false);
        loader.setVisibility(View.VISIBLE);
        layoutResultados.removeAllViews();
        txtResultadoVacio.setVisibility(View.GONE);
        limpiarMarcadores();

        // Geocodificar origen en hilo separado
        new Thread(() -> {
            try {
                puntoOrigen = geocodificar(textoOrigen);
                Log.d(TAG, "✅ Origen geocodificado: " + puntoOrigen.getLatitude() + ", " + puntoOrigen.getLongitude());

                // Geocodificar destino si se ingresó
                if (!textoDestino.isEmpty()) {
                    try {
                        puntoDestino = geocodificar(textoDestino);
                        Log.d(TAG, "✅ Destino geocodificado: " + puntoDestino.getLatitude() + ", " + puntoDestino.getLongitude());
                    } catch (Exception e) {
                        Log.w(TAG, "No se pudo geocodificar el destino, se usará solo el origen");
                        puntoDestino = null;
                    }
                } else {
                    puntoDestino = null;
                }

                runOnUiThread(() -> {
                    // Centrar mapa en origen
                    map.getController().animateTo(puntoOrigen);
                    map.getController().setZoom(15.0);
                    agregarMarcadorOrigen(puntoOrigen, textoOrigen);
                    if (puntoDestino != null) agregarMarcadorDestino(puntoDestino, textoDestino);

                    // Buscar viajes que pasen cerca del origen
                    buscarViajesCercanos();
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Error geocodificando origen: " + e.getMessage());
                runOnUiThread(() -> {
                    loader.setVisibility(View.GONE);
                    btnBuscar.setEnabled(true);
                    Toast.makeText(this,
                            "No se encontró la dirección de origen. Intenta con más detalle.",
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // =========================================================================
    //  BÚSQUEDA EN BACKEND
    //  Usa el endpoint /api/viajes con filtro por latitud/longitud del origen
    // =========================================================================
    private void buscarViajesCercanos() {
        if (puntoOrigen == null) return;

        String url = Constantes.buscarViajes()
                + "?lat="   + puntoOrigen.getLatitude()
                + "&lng="   + puntoOrigen.getLongitude()
                + "&radio=" + RADIO_KM
                + "&estado=PROGRAMADO";

        Log.d(TAG, "🔍 Buscando viajes en: " + url);

        ConexionApi.getInstance(this).getArray(
                url,
                response -> {
                    Log.d(TAG, "📥 Respuesta búsqueda: " + response.toString());
                    procesarViajesEncontrados(response);
                },
                error -> {
                    // Si el endpoint con filtros no existe, traer todos y filtrar localmente
                    Log.w(TAG, "Endpoint de búsqueda con filtros falló, intentando endpoint general...");
                    buscarViajesGeneral();
                }
        );
    }

    private void buscarViajesGeneral() {
        // Fallback: traer todos los viajes disponibles y filtrar por proximidad al origen
        ConexionApi.getInstance(this).getArray(
                Constantes.VIAJES + "?estado=PROGRAMADO",
                response -> {
                    Log.d(TAG, "📥 Todos los viajes: " + response.length() + " encontrados");
                    // Filtrar por proximidad al origen del pasajero
                    JSONArray filtrados = filtrarPorProximidad(response);
                    procesarViajesEncontrados(filtrados);
                },
                error -> {
                    // Último fallback: endpoint sin filtro de estado
                    ConexionApi.getInstance(this).getArray(
                            Constantes.VIAJES,
                            response -> {
                                JSONArray filtrados = filtrarPorProximidad(response);
                                procesarViajesEncontrados(filtrados);
                            },
                            err -> runOnUiThread(() -> {
                                loader.setVisibility(View.GONE);
                                btnBuscar.setEnabled(true);
                                Toast.makeText(this, "Error conectando al servidor", Toast.LENGTH_LONG).show();
                            })
                    );
                }
        );
    }

    // =========================================================================
    //  FILTRO LOCAL POR PROXIMIDAD
    //  Si el backend no filtra, lo hacemos aquí comparando origen de la ruta
    // =========================================================================
    private JSONArray filtrarPorProximidad(JSONArray viajes) {
        JSONArray resultado = new JSONArray();
        if (puntoOrigen == null) return resultado;

        for (int i = 0; i < viajes.length(); i++) {
            try {
                JSONObject viaje = viajes.getJSONObject(i);

                // Saltar viajes no disponibles
                String estado = viaje.optString("estado", "").toUpperCase();
                if (estado.equals("FINALIZADO") || estado.equals("CANCELADO")) continue;

                // Obtener coordenadas de origen de la ruta del viaje
                double latRuta = 0, lngRuta = 0;

                JSONObject ruta = viaje.optJSONObject("ruta");
                if (ruta != null) {
                    latRuta = ruta.optDouble("latOrigen", 0);
                    lngRuta = ruta.optDouble("lngOrigen", 0);
                }
                if (latRuta == 0) latRuta = viaje.optDouble("latOrigen", 0);
                if (lngRuta == 0) lngRuta = viaje.optDouble("lngOrigen", 0);

                if (latRuta == 0 && lngRuta == 0) {
                    // Si no tiene coordenadas aún, incluirlo por defecto
                    resultado.put(viaje);
                    continue;
                }

                // Calcular distancia entre origen del pasajero y origen de la ruta
                double distancia = calcularDistanciaKm(
                        puntoOrigen.getLatitude(), puntoOrigen.getLongitude(),
                        latRuta, lngRuta
                );

                Log.d(TAG, "Viaje " + viaje.optInt("idViaje") + " → distancia origen: " + String.format("%.2f", distancia) + " km");

                if (distancia <= RADIO_KM) {
                    resultado.put(viaje);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error filtrando viaje", e);
            }
        }

        Log.d(TAG, "✅ Viajes filtrados por proximidad: " + resultado.length());
        return resultado;
    }

    // =========================================================================
    //  MOSTRAR RESULTADOS
    // =========================================================================
    private void procesarViajesEncontrados(JSONArray viajes) {
        runOnUiThread(() -> {
            loader.setVisibility(View.GONE);
            btnBuscar.setEnabled(true);
            layoutResultados.removeAllViews();

            if (viajes == null || viajes.length() == 0) {
                txtResultadoVacio.setVisibility(View.VISIBLE);
                txtResultadoVacio.setText(
                        "😔 No encontramos viajes que pasen cerca de tu ubicación.\n\n" +
                                "📍 Intenta con una calle más conocida o amplía la búsqueda."
                );
                return;
            }

            txtResultadoVacio.setVisibility(View.GONE);

            for (int i = 0; i < viajes.length(); i++) {
                JSONObject viaje = viajes.optJSONObject(i);
                if (viaje != null) agregarCardViaje(viaje);
            }
        });
    }

    private void agregarCardViaje(JSONObject viaje) {
        float d   = getResources().getDisplayMetrics().density;
        int   p16 = (int)(16 * d);
        int   p12 = (int)(12 * d);
        int   p8  = (int)(8  * d);
        int   p4  = (int)(4  * d);

        // ── Extraer datos ─────────────────────────────────────────────────────
        int    idViaje   = viaje.optInt("idViaje", viaje.optInt("id", 0));
        String estado    = viaje.optString("estado", "PROGRAMADO");
        double precio    = viaje.optDouble("precio", 0);
        int    cuposDisp = viaje.optInt("cuposDisponibles", 0);
        String fechaSal  = viaje.optString("fechaHoraSalida",
                viaje.optString("fecha", "Próximamente"));

        String origen  = "Origen";
        String destino = "Destino";
        String nombreCond = "Conductor";

        JSONObject ruta = viaje.optJSONObject("ruta");
        if (ruta != null) {
            origen  = ruta.optString("origen",  origen);
            destino = ruta.optString("destino", destino);
        } else {
            origen  = viaje.optString("origen",  origen);
            destino = viaje.optString("destino", destino);
        }

        // Nombre del conductor
        JSONObject cond = viaje.optJSONObject("conductor");
        if (cond != null) {
            nombreCond = extraerNombre(cond);
            if (nombreCond.isEmpty()) {
                JSONObject usuario = cond.optJSONObject("usuario");
                if (usuario != null) nombreCond = extraerNombre(usuario);
            }
        }
        if (nombreCond.isEmpty()) {
            nombreCond = viaje.optString("nombreConductor", "Conductor");
        }

        // ── Construir card ────────────────────────────────────────────────────
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

        // ── Fila superior: Ruta + Estado ──────────────────────────────────────
        LinearLayout filaTop = new LinearLayout(this);
        filaTop.setOrientation(LinearLayout.HORIZONTAL);
        filaTop.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Indicador visual de ruta
        LinearLayout indRuta = new LinearLayout(this);
        indRuta.setOrientation(LinearLayout.VERTICAL);
        indRuta.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lpInd = new LinearLayout.LayoutParams(
                (int)(20 * d), LinearLayout.LayoutParams.WRAP_CONTENT);
        lpInd.setMargins(0, 0, p12, 0);
        indRuta.setLayoutParams(lpInd);

        View circuloVerde = new View(this);
        circuloVerde.setLayoutParams(new LinearLayout.LayoutParams((int)(10*d), (int)(10*d)));
        GradientDrawable gv = new GradientDrawable();
        gv.setShape(GradientDrawable.OVAL); gv.setColor(Color.parseColor("#4CAF50"));
        circuloVerde.setBackground(gv);
        indRuta.addView(circuloVerde);

        View linea = new View(this);
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams((int)(2*d), (int)(28*d));
        lpL.setMargins((int)(4*d), (int)(2*d), (int)(4*d), (int)(2*d));
        linea.setLayoutParams(lpL);
        linea.setBackgroundColor(Color.parseColor("#B2DFDB"));
        indRuta.addView(linea);

        View circuloRojo = new View(this);
        circuloRojo.setLayoutParams(new LinearLayout.LayoutParams((int)(10*d), (int)(10*d)));
        GradientDrawable gr = new GradientDrawable();
        gr.setShape(GradientDrawable.OVAL); gr.setColor(Color.parseColor("#EF5350"));
        circuloRojo.setBackground(gr);
        indRuta.addView(circuloRojo);

        filaTop.addView(indRuta);

        // Origen y Destino
        LinearLayout colRuta = new LinearLayout(this);
        colRuta.setOrientation(LinearLayout.VERTICAL);
        colRuta.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvOrigen = new TextView(this);
        tvOrigen.setText("🟢 " + origen);
        tvOrigen.setTextSize(13f);
        tvOrigen.setTextColor(Color.parseColor("#004D40"));
        tvOrigen.setTypeface(null, android.graphics.Typeface.BOLD);
        tvOrigen.setMaxLines(1);
        tvOrigen.setEllipsize(android.text.TextUtils.TruncateAt.END);
        colRuta.addView(tvOrigen);

        TextView tvDestino = new TextView(this);
        tvDestino.setText("🔴 " + destino);
        tvDestino.setTextSize(13f);
        tvDestino.setTextColor(Color.parseColor("#546E7A"));
        tvDestino.setMaxLines(1);
        tvDestino.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lpDest = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpDest.topMargin = (int)(14 * d);
        tvDestino.setLayoutParams(lpDest);
        colRuta.addView(tvDestino);

        filaTop.addView(colRuta);

        // Chip de estado
        TextView tvEstado = new TextView(this);
        String estadoLabel;
        int estadoColor;
        switch (estado.toUpperCase()) {
            case "PROGRAMADO": estadoLabel = "📅 Programado"; estadoColor = 0xFF1565C0; break;
            case "INICIADO":   estadoLabel = "🚗 En curso";   estadoColor = 0xFF2E7D32; break;
            case "DISPONIBLE": estadoLabel = "✅ Disponible"; estadoColor = 0xFF00897B; break;
            default:           estadoLabel = "📌 " + estado;  estadoColor = 0xFF546E7A;
        }
        tvEstado.setText(estadoLabel);
        tvEstado.setTextSize(10f);
        tvEstado.setTextColor(Color.WHITE);
        tvEstado.setTypeface(null, android.graphics.Typeface.BOLD);
        tvEstado.setPadding(p8, p4, p8, p4);
        GradientDrawable bgEstado = new GradientDrawable();
        bgEstado.setShape(GradientDrawable.RECTANGLE);
        bgEstado.setCornerRadius(20 * d);
        bgEstado.setColor(estadoColor);
        tvEstado.setBackground(bgEstado);
        LinearLayout.LayoutParams lpEst = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpEst.setMargins(p8, 0, 0, 0);
        tvEstado.setLayoutParams(lpEst);
        filaTop.addView(tvEstado);

        inner.addView(filaTop);

        // ── Divisor ───────────────────────────────────────────────────────────
        View divisor = new View(this);
        LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d));
        lpDiv.setMargins(0, p12, 0, p12);
        divisor.setLayoutParams(lpDiv);
        divisor.setBackgroundColor(Color.parseColor("#E0F2F1"));
        inner.addView(divisor);

        // ── Fila inferior: Conductor, Precio, Cupos ───────────────────────────
        LinearLayout filaInfo = new LinearLayout(this);
        filaInfo.setOrientation(LinearLayout.HORIZONTAL);
        filaInfo.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Conductor
        TextView tvCond = new TextView(this);
        tvCond.setText("🚗 " + nombreCond);
        tvCond.setTextSize(12f);
        tvCond.setTextColor(Color.parseColor("#00695C"));
        tvCond.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvCond.setMaxLines(1);
        tvCond.setEllipsize(android.text.TextUtils.TruncateAt.END);
        filaInfo.addView(tvCond);

        // Precio
        TextView tvPrecio = new TextView(this);
        tvPrecio.setText("💵 $" + String.format("%.0f", precio));
        tvPrecio.setTextSize(13f);
        tvPrecio.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPrecio.setTextColor(Color.parseColor("#FF6F00"));
        LinearLayout.LayoutParams lpPrecio = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpPrecio.setMargins(p8, 0, p8, 0);
        tvPrecio.setLayoutParams(lpPrecio);
        filaInfo.addView(tvPrecio);

        // Cupos
        TextView tvCupos = new TextView(this);
        tvCupos.setText("💺 " + cuposDisp);
        tvCupos.setTextSize(12f);
        tvCupos.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCupos.setTextColor(cuposDisp > 0 ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
        tvCupos.setPadding(p8, p4, p8, p4);
        GradientDrawable bgCupos = new GradientDrawable();
        bgCupos.setShape(GradientDrawable.RECTANGLE);
        bgCupos.setCornerRadius(12 * d);
        bgCupos.setColor(cuposDisp > 0 ? Color.parseColor("#E8F5E9") : Color.parseColor("#FFEBEE"));
        tvCupos.setBackground(bgCupos);
        filaInfo.addView(tvCupos);

        inner.addView(filaInfo);

        // Fecha salida
        if (!fechaSal.isEmpty() && !fechaSal.equals("Próximamente")) {
            TextView tvFecha = new TextView(this);
            // Formatear fecha legible
            String fechaLegible = fechaSal.length() > 10
                    ? fechaSal.substring(0, 16).replace("T", " ")
                    : fechaSal;
            tvFecha.setText("🕐 Salida: " + fechaLegible);
            tvFecha.setTextSize(11f);
            tvFecha.setTextColor(Color.parseColor("#90A4AE"));
            LinearLayout.LayoutParams lpFecha = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpFecha.topMargin = p8;
            tvFecha.setLayoutParams(lpFecha);
            inner.addView(tvFecha);
        }

        card.addView(inner);

        // ── Click: ir al detalle del viaje ────────────────────────────────────
        final int idFinal     = idViaje;
        final String orFinal  = origen;
        final String destFinal = destino;
        card.setOnClickListener(v -> {
            if (idFinal == 0) {
                Toast.makeText(this, "No se puede abrir este viaje", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, DetalleViajeActivity.class);
            intent.putExtra("ID_VIAJE", idFinal);
            startActivity(intent);
        });

        layoutResultados.addView(card);

        // ── Marcar origen de la ruta en el mapa ───────────────────────────────
        JSONObject rutaObj = viaje.optJSONObject("ruta");
        double latR = 0, lngR = 0;
        if (rutaObj != null) {
            latR = rutaObj.optDouble("latOrigen", 0);
            lngR = rutaObj.optDouble("lngOrigen", 0);
        }
        if (latR != 0 && lngR != 0) {
            final double latFinal = latR, lngFinal = lngR;
            final String labelFinal = orFinal + " → " + destFinal;
            map.post(() -> agregarMarcadorViaje(latFinal, lngFinal, labelFinal));
        }
    }

    // =========================================================================
    //  MAPA — marcadores
    // =========================================================================
    private void agregarMarcadorOrigen(GeoPoint punto, String etiqueta) {
        Marker m = new Marker(map);
        m.setPosition(punto);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle("📍 Tu ubicación: " + etiqueta);
        m.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_mylocation));
        map.getOverlays().add(m);
        marcadores.add(m);
        map.invalidate();
    }

    private void agregarMarcadorDestino(GeoPoint punto, String etiqueta) {
        Marker m = new Marker(map);
        m.setPosition(punto);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle("🏁 Tu destino: " + etiqueta);
        m.setIcon(getResources().getDrawable(android.R.drawable.ic_dialog_map));
        map.getOverlays().add(m);
        marcadores.add(m);
        map.invalidate();
    }

    private void agregarMarcadorViaje(double lat, double lng, String etiqueta) {
        Marker m = new Marker(map);
        m.setPosition(new GeoPoint(lat, lng));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle("🚗 " + etiqueta);
        m.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_directions));
        map.getOverlays().add(m);
        marcadores.add(m);
        map.invalidate();
    }

    private void limpiarMarcadores() {
        for (Marker m : marcadores) map.getOverlays().remove(m);
        marcadores.clear();
        map.invalidate();
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================

    /** Haversine: distancia en km entre dos coordenadas */
    private double calcularDistanciaKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private GeoPoint geocodificar(String direccion) throws Exception {
        String url = "https://nominatim.openstreetmap.org/search?q="
                + java.net.URLEncoder.encode(direccion + ", Popayan, Colombia", "UTF-8")
                + "&format=json&limit=1";
        JSONArray arr = new JSONArray(peticionHttp(url));
        if (arr.length() == 0) {
            url = "https://nominatim.openstreetmap.org/search?q="
                    + java.net.URLEncoder.encode(direccion + ", Colombia", "UTF-8")
                    + "&format=json&limit=1";
            arr = new JSONArray(peticionHttp(url));
        }
        if (arr.length() == 0) throw new Exception("No encontrado: " + direccion);
        JSONObject o = arr.getJSONObject(0);
        return new GeoPoint(o.getDouble("lat"), o.getDouble("lon"));
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

    private String extraerNombre(JSONObject obj) {
        if (obj == null) return "";
        for (String key : new String[]{"nombre", "nombreCompleto", "name", "fullName", "nombres"}) {
            String v = obj.optString(key, "");
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        return "";
    }
}