package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Activity dedicada a seleccionar el punto de subida del pasajero.
 *
 * Cómo usarla desde DetalleViajeActivity:
 *
 *   Intent i = new Intent(this, SeleccionPuntoActivity.class);
 *   i.putExtra(EXTRA_LAT_ORIGEN,  latOrigen);
 *   i.putExtra(EXTRA_LNG_ORIGEN,  lngOrigen);
 *   i.putExtra(EXTRA_LAT_DESTINO, latDestino);
 *   i.putExtra(EXTRA_LNG_DESTINO, lngDestino);
 *   i.putExtra(EXTRA_GEOJSON,     geojsonRuta);       // opcional
 *   i.putExtra(EXTRA_VIAJE_ID,    viajeId);            // para posicion conductor
 *   i.putExtra(EXTRA_MODO,        MODO_SUBIDA);        // o MODO_BAJADA
 *   startActivityForResult(i, REQ_SELECCION_PUNTO);
 *
 * Resultado en onActivityResult:
 *   if (resultCode == RESULT_OK) {
 *       double lat  = data.getDoubleExtra(RESULT_LAT, 0);
 *       double lng  = data.getDoubleExtra(RESULT_LNG, 0);
 *       String addr = data.getStringExtra(RESULT_DIRECCION);
 *   }
 */
public class SeleccionPuntoActivity extends AppCompatActivity {

    // ── Claves de Intent ──────────────────────────────────────────────────────
    public static final String EXTRA_LAT_ORIGEN  = "lat_origen";
    public static final String EXTRA_LNG_ORIGEN  = "lng_origen";
    public static final String EXTRA_LAT_DESTINO = "lat_destino";
    public static final String EXTRA_LNG_DESTINO = "lng_destino";
    public static final String EXTRA_GEOJSON     = "geojson";
    public static final String EXTRA_VIAJE_ID    = "viaje_id";
    public static final String EXTRA_MODO        = "modo";    // MODO_SUBIDA / MODO_BAJADA

    public static final String MODO_SUBIDA = "SUBIDA";
    public static final String MODO_BAJADA = "BAJADA";

    // ── Claves de resultado ───────────────────────────────────────────────────
    public static final String RESULT_LAT       = "result_lat";
    public static final String RESULT_LNG       = "result_lng";
    public static final String RESULT_DIRECCION = "result_direccion";

    public static final int REQ_SELECCION_PUNTO = 2001;

    // ── OSRM (igual que en DetalleViajeActivity) ──────────────────────────────
    private static final String OSRM_URL =
            "https://optimizacionofrutas-production.up.railway.app";
    private static final String OSRM_URL_PUBLIC =
            "https://router.project-osrm.org";

    private static final int    GPS_POLLING_MS   = 3000;
    private static final String TAG              = "SeleccionPunto";

    // ── Datos recibidos ───────────────────────────────────────────────────────
    private double latOrigen, lngOrigen, latDestino, lngDestino;
    private String geojsonRuta = "";
    private int    viajeId     = 0;
    private String modo        = MODO_SUBIDA;

    // ── Estado interno ────────────────────────────────────────────────────────
    private GeoPoint puntoSeleccionado = null;
    private String   direccionSeleccionada = "";
    private boolean  geocodificando = false;

    private List<GeoPoint> puntosRuta = new ArrayList<>();

    // ── Conductor en tiempo real ──────────────────────────────────────────────
    private final Handler conductorHandler = new Handler(Looper.getMainLooper());
    private Runnable conductorRunnable;
    private Marker   marcadorConductor;
    private GeoPoint posConductor;

    // ── Pasajero ──────────────────────────────────────────────────────────────
    private Marker marcadorPasajero;

    // ── UI ────────────────────────────────────────────────────────────────────
    private MapView      map;
    private TextView     tvDireccion;
    private TextView     tvCoordenadas;
    private TextView     tvInstruccion;
    private ProgressBar  progressGeocoding;
    private MaterialButton btnConfirmar;
    private LinearLayout panelInfo;

    // =========================================================================
    //  LIFECYCLE
    // =========================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Leer extras
        latOrigen  = getIntent().getDoubleExtra(EXTRA_LAT_ORIGEN,  2.4419);
        lngOrigen  = getIntent().getDoubleExtra(EXTRA_LNG_ORIGEN,  -76.6063);
        latDestino = getIntent().getDoubleExtra(EXTRA_LAT_DESTINO, 2.4550);
        lngDestino = getIntent().getDoubleExtra(EXTRA_LNG_DESTINO, -76.5980);
        geojsonRuta = getIntent().getStringExtra(EXTRA_GEOJSON) != null
                ? getIntent().getStringExtra(EXTRA_GEOJSON) : "";
        viajeId    = getIntent().getIntExtra(EXTRA_VIAJE_ID, 0);
        modo       = getIntent().getStringExtra(EXTRA_MODO) != null
                ? getIntent().getStringExtra(EXTRA_MODO) : MODO_SUBIDA;

        construirUI();
        configurarMapa();
        dibujarRuta();
        iniciarPollingConductor();
    }

    @Override
    protected void onResume()  { super.onResume();  if (map != null) map.onResume(); }
    @Override
    protected void onPause()   { super.onPause();   if (map != null) map.onPause();  }
    @Override
    protected void onDestroy() { super.onDestroy(); detenerPollingConductor(); }

    // =========================================================================
    //  CONSTRUCCIÓN DE UI EN CÓDIGO (sin XML extra)
    // =========================================================================
    private void construirUI() {
        float d  = getResources().getDisplayMetrics().density;
        int p16  = (int)(16 * d);
        int p12  = (int)(12 * d);
        int p8   = (int)(8  * d);

        // Raíz vertical
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F8FAFB"));
        setContentView(root);

        // ── Header ──────────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.WHITE);
        header.setPadding(p12, (int)(48*d), p12, p12);
        LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        header.setLayoutParams(lpH);

        // Botón volver
        TextView btnVolver = new TextView(this);
        btnVolver.setText("←");
        btnVolver.setTextSize(22f);
        btnVolver.setTextColor(Color.parseColor("#0ABFA3"));
        btnVolver.setPadding(0, 0, p12, 0);
        btnVolver.setOnClickListener(v -> finish());
        header.addView(btnVolver);

        // Título
        TextView tvTitulo = new TextView(this);
        boolean esSubida = MODO_SUBIDA.equals(modo);
        tvTitulo.setText(esSubida ? "¿Dónde te vas a subir?" : "¿Dónde te vas a bajar?");
        tvTitulo.setTextSize(17f);
        tvTitulo.setTypeface(null, Typeface.BOLD);
        tvTitulo.setTextColor(Color.parseColor("#1A2744"));
        tvTitulo.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(tvTitulo);
        root.addView(header);

        // ── Banner instrucción ──────────────────────────────────────────────
        LinearLayout bannerBox = new LinearLayout(this);
        bannerBox.setOrientation(LinearLayout.HORIZONTAL);
        bannerBox.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bannerBox.setBackgroundColor(Color.parseColor("#E1F8F5"));
        bannerBox.setPadding(p16, p12, p16, p12);
        bannerBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        tvInstruccion = new TextView(this);
        tvInstruccion.setText(esSubida
                ? "Toca cualquier punto del mapa para marcar dónde te recogen"
                : "Toca el mapa para marcar dónde quieres bajar");
        tvInstruccion.setTextSize(13f);
        tvInstruccion.setTextColor(Color.parseColor("#0A7A6A"));
        tvInstruccion.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bannerBox.addView(tvInstruccion);
        root.addView(bannerBox);

        // ── Mapa (ocupa el espacio disponible) ──────────────────────────────
        map = new MapView(this);
        LinearLayout.LayoutParams lpMap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        map.setLayoutParams(lpMap);
        root.addView(map);

        // ── Panel inferior con info del punto ───────────────────────────────
        panelInfo = new LinearLayout(this);
        panelInfo.setOrientation(LinearLayout.VERTICAL);
        panelInfo.setBackgroundColor(Color.WHITE);
        panelInfo.setPadding(p16, p16, p16, (int)(32 * d));
        panelInfo.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Separador superior
        View sep = new View(this);
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d)));
        sep.setBackgroundColor(Color.parseColor("#E5E7EB"));
        panelInfo.addView(sep);

        // Etiqueta punto seleccionado
        TextView tvLabel = new TextView(this);
        tvLabel.setText(esSubida ? "📍 Punto de subida" : "🚏 Punto de bajada");
        tvLabel.setTextSize(11f);
        tvLabel.setTextColor(Color.parseColor("#9CA3AF"));
        tvLabel.setAllCaps(false);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lpLbl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpLbl.setMargins(0, p12, 0, (int)(4 * d));
        tvLabel.setLayoutParams(lpLbl);
        panelInfo.addView(tvLabel);

        // Dirección (grande)
        tvDireccion = new TextView(this);
        tvDireccion.setText("Toca el mapa para seleccionar");
        tvDireccion.setTextSize(16f);
        tvDireccion.setTypeface(null, Typeface.BOLD);
        tvDireccion.setTextColor(Color.parseColor("#1A2744"));
        tvDireccion.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panelInfo.addView(tvDireccion);

        // Coordenadas (pequeño)
        tvCoordenadas = new TextView(this);
        tvCoordenadas.setTextSize(12f);
        tvCoordenadas.setTextColor(Color.parseColor("#9CA3AF"));
        LinearLayout.LayoutParams lpCo = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCo.setMargins(0, (int)(2*d), 0, 0);
        tvCoordenadas.setLayoutParams(lpCo);
        panelInfo.addView(tvCoordenadas);

        // Spinner geocodificación
        progressGeocoding = new ProgressBar(this);
        progressGeocoding.setVisibility(View.GONE);
        LinearLayout.LayoutParams lpProg = new LinearLayout.LayoutParams(
                (int)(20 * d), (int)(20 * d));
        lpProg.setMargins(0, p8, 0, 0);
        progressGeocoding.setLayoutParams(lpProg);
        panelInfo.addView(progressGeocoding);

        // Botón confirmar
        btnConfirmar = new MaterialButton(this);
        btnConfirmar.setText(esSubida ? "Confirmar subida →" : "Confirmar bajada →");
        btnConfirmar.setTextSize(15f);
        btnConfirmar.setTextColor(Color.WHITE);
        btnConfirmar.setEnabled(false);
        btnConfirmar.setAlpha(0.45f);
        btnConfirmar.setCornerRadius((int)(14 * d));
        btnConfirmar.setBackgroundColor(Color.parseColor("#0ABFA3"));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(54 * d));
        lpBtn.setMargins(0, p16, 0, 0);
        btnConfirmar.setLayoutParams(lpBtn);
        btnConfirmar.setOnClickListener(v -> confirmarSeleccion());
        panelInfo.addView(btnConfirmar);

        root.addView(panelInfo);
    }

    // =========================================================================
    //  MAPA
    // =========================================================================
    private void configurarMapa() {
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.setFlingEnabled(true);
        map.setMinZoomLevel(5.0);
        map.setMaxZoomLevel(20.0);

        // Centrar en el origen de la ruta
        GeoPoint centro = new GeoPoint(latOrigen, lngOrigen);
        map.getController().setZoom(15.0);
        map.getController().setCenter(centro);

        // Escuchar toques en el mapa
        map.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                org.osmdroid.views.Projection proj = map.getProjection();
                GeoPoint tocado = (GeoPoint) proj.fromPixels(
                        (int) event.getX(), (int) event.getY());
                if (tocado != null) {
                    onPuntoSeleccionado(tocado);
                }
            }
            return false;
        });
    }

    // =========================================================================
    //  PUNTO SELECCIONADO
    // =========================================================================
    private void onPuntoSeleccionado(GeoPoint gp) {
        puntoSeleccionado = gp;
        direccionSeleccionada = "";

        // Mover marcador del pasajero
        actualizarMarcadorPasajero(gp);

        // Mostrar coords inmediatamente
        tvCoordenadas.setText(String.format(Locale.getDefault(),
                "%.5f, %.5f", gp.getLatitude(), gp.getLongitude()));
        tvDireccion.setText("Resolviendo dirección...");
        btnConfirmar.setEnabled(false);
        btnConfirmar.setAlpha(0.45f);
        progressGeocoding.setVisibility(View.VISIBLE);

        // Geocodificación inversa en background
        new Thread(() -> {
            try {
                String url = "https://nominatim.openstreetmap.org/reverse?lat="
                        + gp.getLatitude() + "&lon=" + gp.getLongitude()
                        + "&format=json&addressdetails=1&zoom=18&accept-language=es";
                String resp = peticionHttp(url);
                if (resp == null || resp.isEmpty()) return;

                JSONObject geo = new JSONObject(resp);
                String nombre = extraerNombreNominatim(geo.optJSONObject("address"), geo);

                if (nombre == null || nombre.isEmpty()) {
                    nombre = String.format(Locale.getDefault(),
                            "%.5f, %.5f", gp.getLatitude(), gp.getLongitude());
                }
                final String nomFinal = nombre;

                runOnUiThread(() -> {
                    direccionSeleccionada = nomFinal;
                    tvDireccion.setText(nomFinal);
                    progressGeocoding.setVisibility(View.GONE);
                    habilitarBotonConfirmar();
                });
            } catch (Exception e) {
                Log.w(TAG, "Geocodificación fallida: " + e.getMessage());
                runOnUiThread(() -> {
                    String coords = String.format(Locale.getDefault(),
                            "%.5f, %.5f", gp.getLatitude(), gp.getLongitude());
                    direccionSeleccionada = coords;
                    tvDireccion.setText(coords);
                    progressGeocoding.setVisibility(View.GONE);
                    habilitarBotonConfirmar();
                });
            }
        }).start();
    }

    private void habilitarBotonConfirmar() {
        btnConfirmar.setEnabled(true);
        btnConfirmar.setAlpha(1f);
    }

    private void actualizarMarcadorPasajero(GeoPoint gp) {
        boolean esSubida = MODO_SUBIDA.equals(modo);
        int color = esSubida ? Color.parseColor("#0ABFA3") : Color.parseColor("#FF6F00");
        String emoji = esSubida ? "🖐" : "🚏";

        if (marcadorPasajero == null) {
            marcadorPasajero = new Marker(map);
            marcadorPasajero.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            map.getOverlays().add(marcadorPasajero);
        }
        marcadorPasajero.setPosition(gp);
        marcadorPasajero.setTitle(esSubida ? "Tu subida" : "Tu bajada");
        marcadorPasajero.setIcon(new BitmapDrawable(getResources(),
                crearBitmapMarcador(color, emoji)));
        map.invalidate();
    }

    // =========================================================================
    //  RUTA
    // =========================================================================
    private void dibujarRuta() {
        if (!geojsonRuta.isEmpty()) {
            List<GeoPoint> pts = parsearGeojson(geojsonRuta);
            if (pts != null && pts.size() >= 2) {
                puntosRuta = pts;
                dibujarPolilinea(pts);
                ajustarCamara(pts);
                return;
            }
        }
        // Pedir ruta al OSRM propio
        new Thread(() -> {
            try {
                String seg = lngOrigen + "," + latOrigen + ";"
                        + lngDestino + "," + latDestino
                        + "?overview=full&geometries=geojson";
                String json = null;
                try { json = peticionHttp(OSRM_URL + "/route/v1/driving/" + seg); }
                catch (Exception ignored) {}
                if (json == null || json.isEmpty())
                    json = peticionHttp(OSRM_URL_PUBLIC + "/route/v1/driving/" + seg);

                List<GeoPoint> pts = parsearRutaOSRM(json);
                if (pts != null && pts.size() >= 2) {
                    puntosRuta = pts;
                    runOnUiThread(() -> {
                        dibujarPolilinea(pts);
                        ajustarCamara(pts);
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "Error pidiendo ruta: " + e.getMessage());
            }
        }).start();
    }

    private void dibujarPolilinea(List<GeoPoint> pts) {
        Polyline sombra = new Polyline(map);
        sombra.setPoints(new ArrayList<>(pts));
        sombra.setColor(Color.argb(50, 0, 0, 0));
        sombra.setWidth(22f);
        map.getOverlays().add(sombra);

        Polyline borde = new Polyline(map);
        borde.setPoints(new ArrayList<>(pts));
        borde.setColor(Color.WHITE);
        borde.setWidth(18f);
        map.getOverlays().add(borde);

        Polyline linea = new Polyline(map);
        linea.setPoints(new ArrayList<>(pts));
        linea.setColor(Color.parseColor("#0ABFA3"));
        linea.setWidth(11f);
        map.getOverlays().add(linea);

        // Marcador origen (A)
        Marker mA = new Marker(map);
        mA.setPosition(new GeoPoint(latOrigen, lngOrigen));
        mA.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mA.setTitle("Inicio de la ruta");
        mA.setIcon(new BitmapDrawable(getResources(),
                crearBitmapMarcador(Color.parseColor("#22C55E"), "A")));
        map.getOverlays().add(mA);

        // Marcador destino (B)
        Marker mB = new Marker(map);
        mB.setPosition(new GeoPoint(latDestino, lngDestino));
        mB.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mB.setTitle("Destino final");
        mB.setIcon(new BitmapDrawable(getResources(),
                crearBitmapMarcador(Color.parseColor("#EF4444"), "B")));
        map.getOverlays().add(mB);

        map.invalidate();
    }

    private void ajustarCamara(List<GeoPoint> pts) {
        double mnLat = Double.MAX_VALUE, mxLat = -Double.MAX_VALUE;
        double mnLng = Double.MAX_VALUE, mxLng = -Double.MAX_VALUE;
        for (GeoPoint p : pts) {
            mnLat = Math.min(mnLat, p.getLatitude());
            mxLat = Math.max(mxLat, p.getLatitude());
            mnLng = Math.min(mnLng, p.getLongitude());
            mxLng = Math.max(mxLng, p.getLongitude());
        }
        double pLat = Math.max((mxLat - mnLat) * 0.15, 0.005);
        double pLng = Math.max((mxLng - mnLng) * 0.15, 0.005);
        final double fMx = mxLat, fMn = mnLat, fMxL = mxLng, fMnL = mnLng;
        final double fPL = pLat, fPLn = pLng;
        map.post(() -> {
            try {
                map.zoomToBoundingBox(
                        new BoundingBox(fMx + fPL, fMxL + fPLn, fMn - fPL, fMnL - fPLn),
                        true, 80);
            } catch (Exception ignored) {}
        });
    }

    // =========================================================================
    //  CONDUCTOR EN TIEMPO REAL
    // =========================================================================
    private void iniciarPollingConductor() {
        if (viajeId <= 0) return;
        conductorRunnable = new Runnable() {
            @Override public void run() {
                obtenerUbicacionConductor();
                conductorHandler.postDelayed(this, GPS_POLLING_MS);
            }
        };
        conductorHandler.postDelayed(conductorRunnable, 1000);
    }

    private void detenerPollingConductor() {
        if (conductorRunnable != null)
            conductorHandler.removeCallbacks(conductorRunnable);
    }

    private void obtenerUbicacionConductor() {
        String url = "https://backendmovi-production-c6..." // ← pon tu URL base de BACKENDMOVI
                + "/api/viajes/" + viajeId
                + "/ubicacion-conductor?t=" + System.currentTimeMillis();
        // Usa ConexionApi de tu proyecto:
        com.arlys.moviflexx.model.ConexionApi.getInstance(this).getObjectNoCache(url,
                response -> {
                    double lat = response.optDouble("lat",
                            response.optDouble("latitud", Double.NaN));
                    double lng = response.optDouble("lng",
                            response.optDouble("longitud", Double.NaN));
                    if (!Double.isNaN(lat) && lat != 0) {
                        GeoPoint pos = new GeoPoint(lat, lng);
                        runOnUiThread(() -> actualizarMarcadorConductor(pos));
                    }
                },
                error -> Log.w(TAG, "Sin ubicación del conductor")
        );
    }

    private void actualizarMarcadorConductor(GeoPoint pos) {
        if (marcadorConductor == null) {
            marcadorConductor = new Marker(map);
            marcadorConductor.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marcadorConductor.setTitle("Conductor en camino");
            marcadorConductor.setIcon(new BitmapDrawable(getResources(),
                    crearBitmapMarcador(Color.parseColor("#1565C0"), "🚗")));
            map.getOverlays().add(marcadorConductor);
        }
        animarMarcador(marcadorConductor, pos);
    }

    private void animarMarcador(Marker marcador, GeoPoint destino) {
        GeoPoint inicio = marcador.getPosition();
        if (inicio == null) { marcador.setPosition(destino); map.invalidate(); return; }

        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1200);
        anim.setInterpolator(new android.view.animation.LinearInterpolator());
        anim.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            marcador.setPosition(new GeoPoint(
                    inicio.getLatitude()  + t * (destino.getLatitude()  - inicio.getLatitude()),
                    inicio.getLongitude() + t * (destino.getLongitude() - inicio.getLongitude())
            ));
            map.invalidate();
        });
        anim.start();
    }

    // =========================================================================
    //  CONFIRMAR Y DEVOLVER RESULTADO
    // =========================================================================
    private void confirmarSeleccion() {
        if (puntoSeleccionado == null) return;
        Intent result = new Intent();
        result.putExtra(RESULT_LAT,       puntoSeleccionado.getLatitude());
        result.putExtra(RESULT_LNG,       puntoSeleccionado.getLongitude());
        result.putExtra(RESULT_DIRECCION, direccionSeleccionada);
        setResult(RESULT_OK, result);
        finish();
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private String extraerNombreNominatim(JSONObject addr, JSONObject geo) {
        if (addr == null) return geo.optString("display_name", "");
        for (String c : new String[]{"neighbourhood", "suburb", "quarter",
                "city_district", "road", "pedestrian", "footway"}) {
            String v = addr.optString(c, "");
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        String d = geo.optString("display_name", "");
        return d.isEmpty() ? "" : d.split(",")[0].trim();
    }

    private List<GeoPoint> parsearRutaOSRM(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(json);
            if (!"Ok".equals(obj.optString("code", ""))) return null;
            JSONArray coords = obj.getJSONArray("routes")
                    .getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONArray("coordinates");
            List<GeoPoint> pts = new ArrayList<>();
            for (int i = 0; i < coords.length(); i++) {
                JSONArray pair = coords.getJSONArray(i);
                pts.add(new GeoPoint(pair.getDouble(1), pair.getDouble(0)));
            }
            return pts.size() >= 2 ? pts : null;
        } catch (Exception e) { return null; }
    }

    private List<GeoPoint> parsearGeojson(String geojsonStr) {
        if (geojsonStr == null || geojsonStr.trim().isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(geojsonStr.trim());
            String tipo = obj.optString("type", "");
            JSONArray coords = null;
            if ("LineString".equals(tipo)) {
                coords = obj.optJSONArray("coordinates");
            } else if ("Feature".equals(tipo)) {
                JSONObject geom = obj.optJSONObject("geometry");
                if (geom != null) coords = geom.optJSONArray("coordinates");
            }
            if (coords == null) return null;
            List<GeoPoint> pts = new ArrayList<>();
            for (int i = 0; i < coords.length(); i++) {
                JSONArray pair = coords.getJSONArray(i);
                pts.add(new GeoPoint(pair.getDouble(1), pair.getDouble(0)));
            }
            return pts.size() >= 2 ? pts : null;
        } catch (Exception e) { return null; }
    }

    private Bitmap crearBitmapMarcador(int colorInt, String letra) {
        int size = 96;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint pS = new Paint(Paint.ANTI_ALIAS_FLAG);
        pS.setColor(Color.argb(70, 0, 0, 0));
        c.drawCircle(size / 2f + 3, size / 2f + 5, size / 2f - 6, pS);
        Paint pC = new Paint(Paint.ANTI_ALIAS_FLAG);
        pC.setColor(colorInt);
        c.drawCircle(size / 2f, size / 2f - 4, size / 2f - 8, pC);
        Paint pB = new Paint(Paint.ANTI_ALIAS_FLAG);
        pB.setColor(Color.WHITE); pB.setStyle(Paint.Style.STROKE); pB.setStrokeWidth(5f);
        c.drawCircle(size / 2f, size / 2f - 4, size / 2f - 8, pB);
        Paint pT = new Paint(Paint.ANTI_ALIAS_FLAG);
        pT.setColor(Color.WHITE);
        pT.setTextSize(letra.length() > 1 ? 26f : 36f);
        pT.setTypeface(Typeface.DEFAULT_BOLD);
        pT.setTextAlign(Paint.Align.CENTER);
        c.drawText(letra, size / 2f, size / 2f + 10, pT);
        return bmp;
    }

    private String peticionHttp(String urlStr) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestProperty("User-Agent", "Moviflexx-App/1.0");
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            r.close();
            return sb.toString();
        } finally {
            if (c != null) c.disconnect();
        }
    }
}