package com.arlys.moviflexx.controller;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;                    // ← quitado AppCompatActivity import
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Manager.RouteManager;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.pojo.RouteOption;
import com.arlys.moviflexx.model.pojo.RouteOptionsResponse;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.*;
import org.osmdroid.views.overlay.mylocation.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// ════════════════════════════════════════════════════════
// CAMBIO 1/3: extends BaseActivity (antes AppCompatActivity)
// ════════════════════════════════════════════════════════
public class PublicarRuta extends BaseActivity {

    private static final String TAG          = "PublicarRuta";
    private static final int    REQ_LOCATION = 1001;

    private static final double MIN_LAT = -90, MAX_LAT = 90;
    private static final double MIN_LNG = -180, MAX_LNG = 180;
    private static final double MIN_DISTANCIA_KM = 0.1;
    private static final double MAX_DISTANCIA_KM = 1000;
    private static final int    AUTOCOMPLETADO_DELAY_MS = 600;
    private static final int    MAX_RUTAS = 5;

    // ── UI ──────────────────────────────────────────────────────────────────
    private TextInputEditText    editOrigen, editDestino;
    private TextView             txtInfoRuta, txtContadorRutas, txtEstadoGps;
    private MapView              map;
    private ProgressBar          loader;
    private MaterialButton       btnCalcular, btnPublicar;
    private LinearLayout         cardRutasOpciones;
    private LinearLayout         containerRutas;
    private ChipGroup            chipGroupTransporte;
    private Chip                 chipCarro, chipMoto;
    private FloatingActionButton btnZoomIn, btnZoomOut, btnMiUbicacion;
    private View                 rootView;
    private View                 loaderContainer;
    private LinearLayout         cardContadorHeader;
    private TextView             txtContadorHeader;

    // ── Datos ───────────────────────────────────────────────────────────────
    private GeoPoint             origenPoint, destinoPoint;
    private MyLocationNewOverlay myLocationOverlay;
    private Marker               marcadorOrigen, marcadorDestino;
    private final List<RutaInfo> listaRutas  = new ArrayList<>();
    private RutaInfo             rutaSeleccionada;
    private String               tipoTransporte = "driving";
    private SessionManager       session;

    private RouteManager         routeManager;
    private final List<Polyline> rutasBackend = new ArrayList<>();

    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private static final int[] COLORES_INT = {
            0xFF009B8D, 0xFFF59E0B, 0xFFEF4444, 0xFF3B82F6, 0xFF10B981
    };
    private static final String[] COLORES_RUTAS = {
            "#26C6B0", "#F59E0B", "#EF4444", "#3B82F6", "#10B981"
    };

    /* ═══════════ CICLO DE VIDA ═══════════ */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_publicar_ruta);

        rootView = findViewById(android.R.id.content);
        session  = new SessionManager(this);

        if (!session.isLoggedIn() || !session.isConductor()) {
            mostrarSnackbar("⚠️ Debes iniciar sesión como conductor", true);
            finish();
            return;
        }

        initViews();
        configurarMapa();
        configurarZoomButtons();
        configurarTransporte();
        verificarPermisosUbicacion();
        configurarAutocompletado();

        routeManager = new RouteManager();

        // ════════════════════════════════════════════════════════
        // CAMBIO 2/3: animateButton reemplaza setOnClickListener
        //   - da escala 0.95 al presionar (microinteracción)
        //   - lanza la acción al soltar con rebote suave
        // ════════════════════════════════════════════════════════
        animateButton(btnCalcular, this::buscarRutasMultiples);
        animateButton(btnPublicar, this::crearRuta);
    }

    @Override protected void onResume()  { super.onResume();  if (map != null) map.onResume(); }
    @Override protected void onPause()   { super.onPause();   if (map != null) map.onPause();  }
    @Override protected void onDestroy() { super.onDestroy(); executor.shutdown();              }

    // ════════════════════════════════════════════════════════
    // CAMBIO 3/3: onBackPressed usa la animación slide de BaseActivity
    //   (no hace falta override — BaseActivity ya lo maneja)
    // ════════════════════════════════════════════════════════

    /* ═══════════ INIT ═══════════ */

    private void initViews() {
        editOrigen          = findViewById(R.id.edit_origen);
        editDestino         = findViewById(R.id.edit_destino);
        txtInfoRuta         = findViewById(R.id.txt_info_ruta);
        map                 = findViewById(R.id.map_mini);
        loader              = findViewById(R.id.loader_ruta);
        btnCalcular         = findViewById(R.id.btn_calcular_ruta);
        btnPublicar         = findViewById(R.id.btn_publicar_ruta);
        cardRutasOpciones   = findViewById(R.id.card_rutas_opciones);
        containerRutas      = findViewById(R.id.container_rutas);
        chipGroupTransporte = findViewById(R.id.chip_group_transporte);
        chipCarro           = findViewById(R.id.chip_carro);
        chipMoto            = findViewById(R.id.chip_moto);
        btnZoomIn           = findViewById(R.id.fab_zoom_in);
        btnZoomOut          = findViewById(R.id.fab_zoom_out);
        btnMiUbicacion      = findViewById(R.id.fab_mi_ubicacion);
        txtContadorRutas    = findViewById(R.id.txt_contador_rutas);
        txtEstadoGps        = findViewById(R.id.txt_estado_gps);
        loaderContainer     = findViewById(R.id.loader_container);
        cardContadorHeader  = findViewById(R.id.card_contador_rutas);
        txtContadorHeader   = findViewById(R.id.txt_contador_header);
    }

    /* ═══════════ MAPA ═══════════ */

    private void configurarMapa() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.getController().setZoom(14.0);
        map.setMinZoomLevel(5.0);
        map.setMaxZoomLevel(20.0);
        map.setFlingEnabled(true);
        map.setTilesScaledToDpi(true);
        map.setHorizontalMapRepetitionEnabled(false);
        map.setVerticalMapRepetitionEnabled(false);
        map.setUseDataConnection(true);
        map.getController().setCenter(new GeoPoint(2.4448, -76.6147));
        Configuration.getInstance().setOsmdroidTileCache(
                new File(getCacheDir(), "osmdroid_tiles"));
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(100L * 1024 * 1024);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(80L * 1024 * 1024);
    }

    /* ═══════════ ZOOM ═══════════ */

    private void configurarZoomButtons() {
        btnZoomIn.setOnClickListener(v -> {
            if (map.getZoomLevelDouble() < map.getMaxZoomLevel()) {
                map.getController().zoomIn();
                animarFab(btnZoomIn);
            }
        });
        btnZoomOut.setOnClickListener(v -> {
            if (map.getZoomLevelDouble() > map.getMinZoomLevel()) {
                map.getController().zoomOut();
                animarFab(btnZoomOut);
            }
        });
        btnMiUbicacion.setOnClickListener(v -> {
            animarFab(btnMiUbicacion);
            if (origenPoint != null) {
                map.getController().animateTo(origenPoint);
                map.getController().setZoom(16.0);
                mostrarSnackbar("📍 Tu ubicación", false);
            } else {
                mostrarSnackbar("⏳ Esperando señal GPS...", false);
            }
        });
    }

    /* ═══════════ TRANSPORTE ═══════════ */

    private void configurarTransporte() {
        chipCarro.setChecked(true);
        chipGroupTransporte.setOnCheckedChangeListener((group, checkedId) -> {
            tipoTransporte = (checkedId == R.id.chip_moto) ? "motorcycle" : "driving";
            if (!listaRutas.isEmpty() && destinoPoint != null) {
                mostrarSnackbar("🔄 Recalculando para " +
                        (tipoTransporte.equals("motorcycle") ? "moto" : "carro") + "...", false);
                buscarRutasMultiples();
            }
        });
    }

    /* ═══════════ GPS ═══════════ */

    private void verificarPermisosUbicacion() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
        } else {
            activarUbicacion();
        }
    }

    private void activarUbicacion() {
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        try {
            Bitmap carBmp = android.graphics.BitmapFactory.decodeResource(
                    getResources(), R.drawable.ic_car);
            if (carBmp != null) {
                myLocationOverlay.setPersonIcon(carBmp);
                myLocationOverlay.setDirectionIcon(carBmp);
            }
        } catch (Exception ignored) {}
        map.getOverlays().add(myLocationOverlay);
        myLocationOverlay.runOnFirstFix(() -> mainHandler.post(() -> {
            origenPoint = myLocationOverlay.getMyLocation();
            if (origenPoint != null) {
                myLocationOverlay.disableFollowLocation();
                editOrigen.setText(obtenerDireccion(origenPoint));
                map.getController().animateTo(origenPoint);
                map.getController().setZoom(15.0);
                agregarMarcadorOrigen();
                if (txtEstadoGps != null) {
                    txtEstadoGps.setText("GPS ✓");
                    txtEstadoGps.setTextColor(Color.parseColor("#10B981"));
                }
                mostrarSnackbar("📍 GPS activo — Ubicación detectada", false);
            }
        }));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            activarUbicacion();
        } else {
            mostrarSnackbar("⚠️ Sin permiso de ubicación", true);
        }
    }

    /* ═══════════ AUTOCOMPLETADO ═══════════ */

    private Runnable autocompletadoRunnable;
    private final Handler autoHandler = new Handler(Looper.getMainLooper());

    private void configurarAutocompletado() {
        editDestino.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (autocompletadoRunnable != null) autoHandler.removeCallbacks(autocompletadoRunnable);
                String txt = s.toString().trim();
                if (txt.length() < 3) return;
                autocompletadoRunnable = () -> sugerirDestinos(txt);
                autoHandler.postDelayed(autocompletadoRunnable, AUTOCOMPLETADO_DELAY_MS);
            }
        });
    }

    private void sugerirDestinos(String texto) {
        executor.execute(() -> {
            try {
                String url = "https://nominatim.openstreetmap.org/search?q="
                        + java.net.URLEncoder.encode(texto + " Popayan Colombia", "UTF-8")
                        + "&format=json&limit=5&addressdetails=1";
                JSONArray arr = new JSONArray(peticionHttp(url));
                Log.d(TAG, "Sugerencias obtenidas: " + arr.length());
            } catch (Exception ignored) {}
        });
    }

    /* ═══════════ MARCADORES ═══════════ */

    private void agregarMarcadorOrigen() {
        if (marcadorOrigen != null) map.getOverlays().remove(marcadorOrigen);
        marcadorOrigen = crearMarcadorPersonalizado(origenPoint,
                "🚗 Origen",
                editOrigen.getText() != null ? editOrigen.getText().toString() : "",
                COLORES_INT[0], "A");
        map.getOverlays().add(marcadorOrigen);
        map.invalidate();
    }

    private void agregarMarcadorDestino() {
        if (marcadorDestino != null) map.getOverlays().remove(marcadorDestino);
        marcadorDestino = crearMarcadorPersonalizado(destinoPoint,
                "📍 Destino",
                editDestino.getText() != null ? editDestino.getText().toString() : "",
                0xFFEF4444, "B");
        map.getOverlays().add(marcadorDestino);
        map.invalidate();
    }

    private Marker crearMarcadorPersonalizado(GeoPoint punto, String titulo,
                                              String snippet, int colorInt, String letra) {
        Marker m = new Marker(map);
        m.setPosition(punto);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle(titulo);
        m.setSnippet(snippet);
        int size = 96;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint sombra = new Paint(Paint.ANTI_ALIAS_FLAG);
        sombra.setColor(Color.argb(80, 0, 0, 0));
        c.drawCircle(size / 2f + 3, size / 2f + 5, size / 2f - 6, sombra);
        Paint circulo = new Paint(Paint.ANTI_ALIAS_FLAG);
        circulo.setColor(colorInt);
        c.drawCircle(size / 2f, size / 2f - 4, size / 2f - 8, circulo);
        Paint borde = new Paint(Paint.ANTI_ALIAS_FLAG);
        borde.setColor(Color.WHITE);
        borde.setStyle(Paint.Style.STROKE);
        borde.setStrokeWidth(4f);
        c.drawCircle(size / 2f, size / 2f - 4, size / 2f - 8, borde);
        Paint texto = new Paint(Paint.ANTI_ALIAS_FLAG);
        texto.setColor(Color.WHITE);
        texto.setTextSize(36f);
        texto.setTypeface(Typeface.DEFAULT_BOLD);
        texto.setTextAlign(Paint.Align.CENTER);
        c.drawText(letra, size / 2f, size / 2f + 9, texto);
        m.setIcon(new BitmapDrawable(getResources(), bmp));
        return m;
    }

    /* ═══════════ BUSCAR RUTAS ═══════════ */

    private void buscarRutasMultiples() {
        if (origenPoint == null) {
            mostrarSnackbar("⏳ Esperando señal GPS...", true);
            return;
        }
        String destinoTxt = editDestino.getText() != null
                ? editDestino.getText().toString().trim() : "";
        if (destinoTxt.isEmpty()) {
            mostrarSnackbar("📌 Ingresa un destino primero", true);
            return;
        }
        setLoadingState(true);
        executor.execute(() -> {
            try {
                String geoUrl = "https://nominatim.openstreetmap.org/search?q="
                        + java.net.URLEncoder.encode(destinoTxt + " Popayan Colombia", "UTF-8")
                        + "&format=json&limit=1";
                JSONArray geoArr = new JSONArray(peticionHttp(geoUrl));
                if (geoArr.length() == 0) {
                    mainHandler.post(() -> { setLoadingState(false); mostrarSnackbar("❌ Destino no encontrado. Intenta ser más específico", true); });
                    return;
                }
                JSONObject geoObj = geoArr.getJSONObject(0);
                destinoPoint = new GeoPoint(geoObj.getDouble("lat"), geoObj.getDouble("lon"));
                if (distanciaEnMetros(origenPoint, destinoPoint) < 100) {
                    mainHandler.post(() -> { setLoadingState(false); mostrarSnackbar("⚠️ El origen y el destino son demasiado cercanos", true); });
                    return;
                }
                if (!coordenadasValidas(destinoPoint)) {
                    mainHandler.post(() -> { setLoadingState(false); mostrarSnackbar("⚠️ Coordenadas del destino inválidas", true); });
                    return;
                }
                String coordsOSRM = origenPoint.getLongitude() + "," + origenPoint.getLatitude()
                        + ";" + destinoPoint.getLongitude() + "," + destinoPoint.getLatitude();
                listaRutas.clear();
                mainHandler.post(() -> actualizarMensajeLoader("🔍 Consultando OSRM Popayán..."));
                try {
                    agregarRutasDeOSRM("https://osrm-popayan-production.up.railway.app/route/v1/driving/"
                            + coordsOSRM + "?overview=full&geometries=geojson&alternatives=true", "OSRM-Popayan");
                } catch (Exception e) { Log.w(TAG, "OSRM propio no disponible: " + e.getMessage()); }
                mainHandler.post(() -> actualizarMensajeLoader("🌐 Consultando OSRM público..."));
                try {
                    String modoOsrm = tipoTransporte.equals("motorcycle") ? "driving" : tipoTransporte;
                    agregarRutasDeOSRM("https://router.project-osrm.org/route/v1/" + modoOsrm + "/"
                            + coordsOSRM + "?overview=full&geometries=geojson&alternatives=true", "OSRM-Public");
                } catch (Exception e) { Log.w(TAG, "OSRM público no disponible: " + e.getMessage()); }
                mainHandler.post(() -> actualizarMensajeLoader("🗺️ Consultando GraphHopper..."));
                try {
                    String perfilGH = tipoTransporte.equals("motorcycle") ? "motorcycle" : "car";
                    agregarRutasDeGraphHopper("https://graphhopper.com/api/1/route"
                            + "?point=" + origenPoint.getLatitude() + "," + origenPoint.getLongitude()
                            + "&point=" + destinoPoint.getLatitude() + "," + destinoPoint.getLongitude()
                            + "&profile=" + perfilGH
                            + "&alternative_route.max_paths=3&alternative_route.max_weight_factor=1.8"
                            + "&alternative_route.max_share_factor=0.6&points_encoded=false&key=");
                } catch (Exception e) { Log.w(TAG, "GraphHopper no disponible: " + e.getMessage()); }
                if (listaRutas.isEmpty()) {
                    mainHandler.post(() -> { setLoadingState(false); mostrarSnackbar("⚠️ No se encontraron rutas. Verifica conectividad.", true); });
                    return;
                }
                listaRutas.removeIf(r -> r.distancia < MIN_DISTANCIA_KM || r.distancia > MAX_DISTANCIA_KM);
                String[] nombresTipo = {"⚡ Ruta Rápida","🔵 Alternativa 1","🔴 Alternativa 2","🟡 Alternativa 3","🟢 Alternativa 4"};
                for (int i = 0; i < listaRutas.size(); i++) {
                    RutaInfo r = listaRutas.get(i);
                    r.indice      = i;
                    r.colorHex    = COLORES_RUTAS[Math.min(i, COLORES_RUTAS.length - 1)];
                    r.colorInt    = COLORES_INT[Math.min(i, COLORES_INT.length - 1)];
                    r.tipo        = nombresTipo[Math.min(i, nombresTipo.length - 1)];
                    r.descripcion = String.format("%.1f km · %.0f min · desde %s", r.distancia, r.duracion, r.fuente);
                }
                mainHandler.post(() -> { mostrarRutas(); consultarBackendFastAPI(); });
            } catch (Exception e) {
                Log.e(TAG, "Error buscando rutas", e);
                mainHandler.post(() -> { setLoadingState(false); mostrarSnackbar("❌ Error de red calculando rutas", true); });
            }
        });
    }

    /* ═══════════ PARSEO RUTAS ═══════════ */

    private void agregarRutasDeOSRM(String url, String fuente) throws Exception {
        String json = peticionHttp(url);
        if (json == null || json.isEmpty()) return;
        JSONObject res = new JSONObject(json);
        if (!"Ok".equals(res.optString("code"))) return;
        JSONArray routes = res.optJSONArray("routes");
        if (routes == null) return;
        for (int i = 0; i < routes.length() && listaRutas.size() < MAX_RUTAS; i++) {
            JSONObject route = routes.getJSONObject(i);
            JSONObject geometry = route.optJSONObject("geometry");
            if (geometry == null) continue;
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if (coordinates == null || coordinates.length() < 2) continue;
            ArrayList<GeoPoint> puntos = coordsOSRMaGeoPoints(coordinates);
            if (puntos.size() < 2 || esRutaDuplicada(puntos)) continue;
            RutaInfo info = new RutaInfo();
            info.puntos    = puntos;
            info.distancia = route.getDouble("distance") / 1000.0;
            info.duracion  = route.getDouble("duration") / 60.0;
            info.fuente    = fuente;
            listaRutas.add(info);
        }
    }

    private void agregarRutasDeGraphHopper(String url) throws Exception {
        String json = peticionHttp(url);
        if (json == null || json.isEmpty()) return;
        JSONObject res = new JSONObject(json);
        JSONArray paths = res.optJSONArray("paths");
        if (paths == null) return;
        for (int i = 0; i < paths.length() && listaRutas.size() < MAX_RUTAS; i++) {
            JSONObject path = paths.getJSONObject(i);
            JSONObject pointsObj = path.optJSONObject("points");
            if (pointsObj == null) continue;
            JSONArray coords = pointsObj.optJSONArray("coordinates");
            if (coords == null || coords.length() < 2) continue;
            ArrayList<GeoPoint> puntos = new ArrayList<>();
            for (int j = 0; j < coords.length(); j++) {
                JSONArray cc = coords.getJSONArray(j);
                puntos.add(new GeoPoint(cc.getDouble(1), cc.getDouble(0)));
            }
            if (puntos.size() < 2 || esRutaDuplicada(puntos)) continue;
            RutaInfo info = new RutaInfo();
            info.puntos    = puntos;
            info.distancia = path.getDouble("distance") / 1000.0;
            info.duracion  = path.getDouble("time") / 60000.0;
            info.fuente    = "GraphHopper";
            listaRutas.add(info);
        }
    }

    private boolean esRutaDuplicada(ArrayList<GeoPoint> nuevos) {
        if (listaRutas.isEmpty()) return false;
        GeoPoint midNuevo = nuevos.get(nuevos.size() / 2);
        for (RutaInfo existente : listaRutas) {
            if (existente.puntos == null || existente.puntos.isEmpty()) continue;
            GeoPoint midExistente = existente.puntos.get(existente.puntos.size() / 2);
            if (distanciaEnMetros(midNuevo, midExistente) < 200) return true;
        }
        return false;
    }

    private ArrayList<GeoPoint> coordsOSRMaGeoPoints(JSONArray coords) throws Exception {
        ArrayList<GeoPoint> puntos = new ArrayList<>();
        for (int i = 0; i < coords.length(); i++) {
            JSONArray c = coords.getJSONArray(i);
            puntos.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
        }
        return puntos;
    }

    /* ═══════════ BACKEND FastAPI ═══════════ */

    private void consultarBackendFastAPI() {
        if (origenPoint == null || destinoPoint == null) return;
        routeManager.fetchRoutes(
                origenPoint.getLatitude(), origenPoint.getLongitude(),
                destinoPoint.getLatitude(), destinoPoint.getLongitude(),
                "FASTEST",
                new RouteManager.RouteCallback() {
                    @Override public void onSuccess(RouteOptionsResponse response) {
                        mainHandler.post(() -> actualizarCardsConDatosBackend(response));
                    }
                    @Override public void onError(String errorMessage) {
                        Log.w(TAG, "Backend sin datos de combustible: " + errorMessage);
                        mainHandler.post(() -> {
                            for (int i = 0; i < containerRutas.getChildCount(); i++) {
                                View v = containerRutas.getChildAt(i);
                                setTextoSeguro(v, R.id.txt_combustible, "N/D");
                                setTextoSeguro(v, R.id.txt_costo, "N/D");
                            }
                        });
                    }
                }
        );
    }

    private void actualizarCardsConDatosBackend(RouteOptionsResponse response) {
        if (response == null || response.routes == null || response.routes.isEmpty()) return;
        for (Polyline p : rutasBackend) map.getOverlays().remove(p);
        rutasBackend.clear();
        for (int i = 0; i < response.routes.size() && i < listaRutas.size(); i++) {
            RouteOption br = response.routes.get(i);
            RutaInfo    lr = listaRutas.get(i);
            lr.distancia = br.distanceKm; lr.duracion = br.durationMin;
            lr.fuelLiters = br.fuelLiters; lr.fuelCostCop = br.fuelCostCop;
            if (i < containerRutas.getChildCount()) {
                View cv = containerRutas.getChildAt(i);
                setTextoSeguro(cv, R.id.txt_distancia,   String.format("%.1f km", br.distanceKm));
                setTextoSeguro(cv, R.id.txt_duracion,    String.format("%.0f min", br.durationMin));
                setTextoSeguro(cv, R.id.txt_combustible, String.format("%.2f L", br.fuelLiters));
                setTextoSeguro(cv, R.id.txt_costo,       String.format("$%,.0f COP", br.fuelCostCop));
                setTextoSeguro(cv, R.id.txt_descripcion, lr.descripcion + "  ·  Score " + String.format("%.1f", br.score));
                animarEntradaCard(cv, i * 80L);
            }
            if (br.geojson != null) {
                Polyline line = geojsonAPolyline(br.geojson, COLORES_RUTAS[Math.min(i, COLORES_RUTAS.length - 1)], 6f, 160);
                if (line != null) { map.getOverlays().add(line); rutasBackend.add(line); }
            }
        }
        rePinMarkers();
        map.invalidate();
        RouteOption mejor = response.routes.get(0);
        mostrarSnackbar(String.format("✅ %.1f km · %d min · %.2f L · $%,.0f COP",
                mejor.distanceKm, (int) mejor.durationMin, mejor.fuelLiters, mejor.fuelCostCop), false);
    }

    /* ═══════════ MOSTRAR RUTAS ═══════════ */

    private void mostrarRutas() {
        setLoadingState(false);
        if (listaRutas.isEmpty()) { mostrarSnackbar("⚠️ Sin rutas disponibles", true); return; }
        limpiarRutasDelMapa();
        for (int i = listaRutas.size() - 1; i >= 0; i--) dibujarRutaEnMapa(listaRutas.get(i), i == 0);
        agregarMarcadorDestino();
        agregarMarcadorOrigen();
        ajustarVistaTodasLasRutas();
        containerRutas.removeAllViews();
        for (int i = 0; i < listaRutas.size(); i++) {
            View card = crearCardRuta(listaRutas.get(i));
            containerRutas.addView(card);
            animarEntradaCard(card, i * 100L);
        }
        cardRutasOpciones.setVisibility(View.VISIBLE);
        animarEntradaCard(cardRutasOpciones, 0);
        rutaSeleccionada = null;
        String msg = listaRutas.size() == 1 ? "1 ruta encontrada"
                : listaRutas.size() + " rutas encontradas — toca para ver detalles";
        txtInfoRuta.setText(msg);
        String textoContador = listaRutas.size() + " rutas";
        if (txtContadorRutas  != null) txtContadorRutas.setText(textoContador);
        if (txtContadorHeader != null) txtContadorHeader.setText(textoContador);
        if (cardContadorHeader != null) cardContadorHeader.setVisibility(View.VISIBLE);
        mainHandler.postDelayed(() -> { if (!listaRutas.isEmpty()) seleccionarRuta(listaRutas.get(0)); }, 800);
    }

    private void limpiarRutasDelMapa() {
        List<Overlay> toRemove = new ArrayList<>();
        for (Overlay o : map.getOverlays()) if (o instanceof Polyline) toRemove.add(o);
        map.getOverlays().removeAll(toRemove);
        rutasBackend.clear();
    }

    /* ═══════════ CARD DE RUTA ═══════════ */

    private View crearCardRuta(RutaInfo ruta) {
        View view = getLayoutInflater().inflate(R.layout.item_ruta_opcion, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 12);
        view.setLayoutParams(lp);
        MaterialCardView card   = view.findViewById(R.id.card_ruta);
        View indicadorColor     = view.findViewById(R.id.indicador_color);
        android.widget.ImageView iconTransport = view.findViewById(R.id.icon_transporte);
        if (indicadorColor != null) indicadorColor.setBackgroundColor(ruta.colorInt);
        if (iconTransport  != null)
            iconTransport.setImageResource(tipoTransporte.equals("motorcycle") ? R.drawable.ic_moto : R.drawable.ic_car);
        setTextoSeguro(view, R.id.txt_tipo_ruta,   ruta.tipo);
        setTextoSeguro(view, R.id.txt_distancia,   String.format("%.1f km", ruta.distancia));
        setTextoSeguro(view, R.id.txt_duracion,    String.format("%.0f min", ruta.duracion));
        setTextoSeguro(view, R.id.txt_combustible, "⏳ calculando...");
        setTextoSeguro(view, R.id.txt_costo,       "⏳");
        setTextoSeguro(view, R.id.txt_descripcion, "📡 " + ruta.fuente + " · " + ruta.puntos.size() + " puntos");
        if (card != null) card.setOnClickListener(v -> seleccionarRuta(ruta));
        view.setOnClickListener(v -> seleccionarRuta(ruta));
        if (ruta.indice == 0) {
            TextView badgeMejor = view.findViewById(R.id.badge_mejor);
            if (badgeMejor != null) badgeMejor.setVisibility(View.VISIBLE);
        }
        return view;
    }

    private void seleccionarRuta(RutaInfo ruta) {
        if (ruta == null) return;
        rutaSeleccionada = ruta;
        for (int i = 0; i < containerRutas.getChildCount(); i++) {
            View v = containerRutas.getChildAt(i);
            MaterialCardView card = v.findViewById(R.id.card_ruta);
            if (card == null) continue;
            if (i == ruta.indice) {
                card.setCardElevation(dp(8));
                card.setStrokeWidth(dp(3));
                card.setStrokeColor(ruta.colorInt);
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(card, "scaleX", 1f, 1.02f, 1f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(card, "scaleY", 1f, 1.02f, 1f);
                AnimatorSet set = new AnimatorSet();
                set.playTogether(scaleX, scaleY);
                set.setDuration(300).setInterpolator(new OvershootInterpolator());
                set.start();
            } else {
                card.setCardElevation(dp(2));
                card.setStrokeWidth(1);
                card.setStrokeColor(0xFFE0E0E0);
            }
        }
        limpiarRutasDelMapa();
        for (RutaInfo r : listaRutas) if (r.indice != ruta.indice) dibujarRutaAtenuada(r);
        dibujarRutaEnMapa(ruta, true);
        rePinMarkers();
        map.invalidate();
        String vehiculo = tipoTransporte.equals("motorcycle") ? "🏍 Moto" : "🚗 Carro";
        StringBuilder info = new StringBuilder();
        info.append(ruta.tipo).append("  ·  ").append(vehiculo).append("\n");
        info.append(String.format("📏 %.1f km  ·  ⏱ %.0f min", ruta.distancia, ruta.duracion));
        if (ruta.fuelCostCop > 0)
            info.append(String.format("  ·  ⛽ %.2f L  ·  💰 $%,.0f", ruta.fuelLiters, ruta.fuelCostCop));
        txtInfoRuta.setText(info.toString());
        ajustarVistaRuta(ruta);
    }

    /* ═══════════ POLYLINES ═══════════ */

    private void dibujarRutaEnMapa(RutaInfo ruta, boolean esSeleccionada) {
        if (ruta.puntos == null || ruta.puntos.size() < 2) return;
        float anchoSombra = esSeleccionada ? 24f : 16f;
        float anchoBorde  = esSeleccionada ? 20f : 13f;
        float anchoLinea  = esSeleccionada ? 13f : 8f;
        Polyline sombra = new Polyline(map);
        sombra.setPoints(ruta.puntos); sombra.setColor(Color.argb(60,0,0,0)); sombra.setWidth(anchoSombra);
        map.getOverlays().add(sombra);
        Polyline borde = new Polyline(map);
        borde.setPoints(ruta.puntos); borde.setColor(Color.WHITE); borde.setWidth(anchoBorde);
        map.getOverlays().add(borde);
        Polyline linea = new Polyline(map);
        linea.setPoints(ruta.puntos); linea.setColor(ruta.colorInt); linea.setWidth(anchoLinea);
        linea.setOnClickListener((poly, mapView, point) -> { seleccionarRuta(ruta); return true; });
        map.getOverlays().add(linea);
        ruta.polyline = linea;
    }

    private void dibujarRutaAtenuada(RutaInfo ruta) {
        if (ruta.puntos == null || ruta.puntos.size() < 2) return;
        Polyline linea = new Polyline(map);
        linea.setPoints(ruta.puntos);
        linea.setColor(Color.argb(60, Color.red(ruta.colorInt), Color.green(ruta.colorInt), Color.blue(ruta.colorInt)));
        linea.setWidth(6f);
        linea.setOnClickListener((poly, mapView, point) -> { seleccionarRuta(ruta); return true; });
        map.getOverlays().add(linea);
        ruta.polyline = linea;
    }

    private Polyline geojsonAPolyline(Map<String, Object> geojson, String hexColor, float ancho, int alpha) {
        try {
            JSONObject geo = new JSONObject(geojson);
            JSONArray coordsArray = geo.optJSONArray("coordinates");
            if (coordsArray == null) return null;
            ArrayList<GeoPoint> puntos = new ArrayList<>();
            for (int i = 0; i < coordsArray.length(); i++) {
                JSONArray c = coordsArray.getJSONArray(i);
                puntos.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
            }
            if (puntos.size() < 2) return null;
            int base = Color.parseColor(hexColor);
            Polyline poly = new Polyline(map);
            poly.setPoints(puntos);
            poly.setColor(Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)));
            poly.setWidth(ancho);
            return poly;
        } catch (Exception e) { Log.e(TAG, "Error parsing GeoJSON polyline", e); return null; }
    }

    private void rePinMarkers() {
        if (marcadorOrigen  != null) { map.getOverlays().remove(marcadorOrigen);  map.getOverlays().add(marcadorOrigen);  }
        if (marcadorDestino != null) { map.getOverlays().remove(marcadorDestino); map.getOverlays().add(marcadorDestino); }
    }

    /* ═══════════ AJUSTE VISTA ═══════════ */

    private void ajustarVistaTodasLasRutas() {
        if (listaRutas.isEmpty()) return;
        double minLat=Double.MAX_VALUE, maxLat=-Double.MAX_VALUE, minLon=Double.MAX_VALUE, maxLon=-Double.MAX_VALUE;
        for (RutaInfo r : listaRutas) {
            if (r.puntos == null) continue;
            for (GeoPoint p : r.puntos) {
                minLat=Math.min(minLat,p.getLatitude()); maxLat=Math.max(maxLat,p.getLatitude());
                minLon=Math.min(minLon,p.getLongitude()); maxLon=Math.max(maxLon,p.getLongitude());
            }
        }
        double padLat=Math.max((maxLat-minLat)*0.2,0.008), padLon=Math.max((maxLon-minLon)*0.2,0.008);
        BoundingBox bbox = new BoundingBox(maxLat+padLat, maxLon+padLon, minLat-padLat, minLon-padLon);
        map.post(() -> { try { map.zoomToBoundingBox(bbox, true, 80); } catch (Exception ignored) {} });
    }

    private void ajustarVistaRuta(RutaInfo ruta) {
        if (ruta.puntos == null || ruta.puntos.isEmpty()) return;
        double minLat=Double.MAX_VALUE, maxLat=-Double.MAX_VALUE, minLon=Double.MAX_VALUE, maxLon=-Double.MAX_VALUE;
        for (GeoPoint p : ruta.puntos) {
            minLat=Math.min(minLat,p.getLatitude()); maxLat=Math.max(maxLat,p.getLatitude());
            minLon=Math.min(minLon,p.getLongitude()); maxLon=Math.max(maxLon,p.getLongitude());
        }
        double padLat=Math.max((maxLat-minLat)*0.25,0.006), padLon=Math.max((maxLon-minLon)*0.25,0.006);
        BoundingBox bbox = new BoundingBox(maxLat+padLat, maxLon+padLon, minLat-padLat, minLon-padLon);
        map.post(() -> { try { map.zoomToBoundingBox(bbox, true, 60); } catch (Exception ignored) {} });
    }

    /* ═══════════ PUBLICAR ═══════════ */

    private void crearRuta() {
        if (rutaSeleccionada == null || destinoPoint == null) {
            mostrarSnackbar("⚠️ Primero selecciona una ruta del mapa", true); return;
        }
        if (origenPoint == null) { mostrarSnackbar("⚠️ GPS no disponible", true); return; }
        String origenTxt  = editOrigen.getText()  != null ? editOrigen.getText().toString().trim()  : "";
        String destinoTxt = editDestino.getText() != null ? editDestino.getText().toString().trim() : "";
        if (origenTxt.isEmpty() || destinoTxt.isEmpty()) {
            mostrarSnackbar("⚠️ Revisa que el origen y destino estén definidos", true); return;
        }
        if (!coordenadasValidas(origenPoint) || !coordenadasValidas(destinoPoint)) {
            mostrarSnackbar("⚠️ Coordenadas inválidas, intenta de nuevo", true); return;
        }
        if (rutaSeleccionada.distancia < MIN_DISTANCIA_KM) {
            mostrarSnackbar("⚠️ La ruta es demasiado corta", true); return;
        }
        btnPublicar.setEnabled(false);
        btnPublicar.setText("Publicando...");
        final double  _distanciaKm      = rutaSeleccionada.distancia;
        final double  _duracionMin      = rutaSeleccionada.duracion;
        final double  _fuelLitros       = rutaSeleccionada.fuelLiters;
        final double  _costoCombustible = rutaSeleccionada.fuelCostCop;
        final String  _origen           = origenTxt;
        final String  _destino          = destinoTxt;
        final String  _fuente           = rutaSeleccionada.fuente;
        final int     _indiceRuta       = rutaSeleccionada.indice;
        try {
            JSONObject body = new JSONObject();
            body.put("nombre",         _origen + " → " + _destino);
            body.put("descripcion",    String.format("Ruta de %.1f km en %s (%.0f min)", _distanciaKm, tipoTransporte.equals("motorcycle") ? "Moto" : "Carro", _duracionMin));
            body.put("origen",         _origen);
            body.put("destino",        _destino);
            body.put("latOrigen",      origenPoint.getLatitude());
            body.put("lngOrigen",      origenPoint.getLongitude());
            body.put("latDestino",     destinoPoint.getLatitude());
            body.put("lngDestino",     destinoPoint.getLongitude());
            body.put("distancia",      _distanciaKm);
            body.put("duracion",       _duracionMin);
            body.put("tipoTransporte", tipoTransporte);
            body.put("estado",         "DISPONIBLE");
            body.put("fuente",         _fuente);
            body.put("indiceRuta",     _indiceRuta);
            if (_costoCombustible > 0) {
                body.put("combustibleLitros", _fuelLitros);
                body.put("costoCombustible",  _costoCombustible);
            }
            ConexionApi.getInstance(this).post(Constantes.RUTAS, body,
                    response -> {
                        int idRuta = 0;
                        if (response.has("idRuta"))        idRuta = response.optInt("idRuta");
                        else if (response.has("id"))       idRuta = response.optInt("id");
                        else if (response.has("idrutas"))  idRuta = response.optInt("idrutas");
                        if (idRuta == 0) {
                            JSONObject rutaObj = response.optJSONObject("ruta");
                            if (rutaObj != null) idRuta = rutaObj.optInt("idRuta", rutaObj.optInt("id", 0));
                        }
                        if (idRuta == 0) {
                            btnPublicar.setEnabled(true); btnPublicar.setText("Publicar viaje");
                            mostrarSnackbar("❌ No se pudo obtener ID de ruta del servidor", true);
                            return;
                        }
                        final int idRutaFinal = idRuta;
                        guardarParadasDeRuta(idRutaFinal, _origen, _destino, () -> {
                            btnPublicar.setEnabled(true); btnPublicar.setText("Publicar viaje");
                            mostrarSnackbar("✅ ¡Ruta publicada con paradas!", false);
                            Intent intent = new Intent(PublicarRuta.this, PublicarViaje.class);
                            intent.putExtra("ID_RUTA_CREADA",    idRutaFinal);
                            intent.putExtra("ORIGEN_RUTA",       _origen);
                            intent.putExtra("DESTINO_RUTA",      _destino);
                            intent.putExtra("TIPO_TRANSPORTE",   tipoTransporte);
                            intent.putExtra("COSTO_COMBUSTIBLE", _costoCombustible);
                            intent.putExtra("FUEL_LITROS",       _fuelLitros);
                            intent.putExtra("DISTANCIA_KM",      _distanciaKm);
                            intent.putExtra("DURACION_MIN",      _duracionMin);
                            intent.putExtra("INDICE_RUTA",       _indiceRuta);
                            // ── usa animación slide de BaseActivity ──
                            startActivity(intent);
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                            finish();
                        });
                    },
                    error -> {
                        btnPublicar.setEnabled(true); btnPublicar.setText("Publicar viaje");
                        String msg = "❌ Error al publicar ruta";
                        if (error != null && error.networkResponse != null) {
                            switch (error.networkResponse.statusCode) {
                                case 400: msg = "❌ Datos inválidos (400)"; break;
                                case 401: msg = "❌ Sesión expirada (401)"; break;
                                case 403: msg = "❌ Sin permisos (403)"; break;
                                case 422: msg = "❌ Distancia o duración no válidas (422)"; break;
                                case 500: msg = "❌ Error en el servidor (500)"; break;
                            }
                        }
                        mostrarSnackbar(msg, true);
                    }
            );
        } catch (Exception e) {
            btnPublicar.setEnabled(true); btnPublicar.setText("Publicar viaje");
            mostrarSnackbar("❌ Error inesperado al publicar", true);
        }
    }

    private void guardarParadasDeRuta(int idRuta, String nombreOrigen, String nombreDestino, Runnable callback) {
        if (idRuta <= 0) { mainHandler.post(callback); return; }
        if (rutaSeleccionada == null || rutaSeleccionada.puntos == null || rutaSeleccionada.puntos.size() < 2) {
            guardarParadaOrigen(idRuta, nombreOrigen, callback); return;
        }
        ArrayList<GeoPoint> todos = rutaSeleccionada.puntos;
        int total = todos.size();
        GeoPoint pOrigen = todos.get(0), pDestino = todos.get(total - 1);
        int maxIntermedias = 6;
        List<GeoPoint> intermedios = new ArrayList<>();
        if (total > 2) {
            int numIntermedios = Math.min(maxIntermedias, total - 2);
            double paso = (double)(total - 2) / (numIntermedios + 1);
            for (int i = 1; i <= numIntermedios; i++) {
                int idx = 1 + (int)(i * paso);
                if (idx < total - 1) intermedios.add(todos.get(idx));
            }
        }
        List<JSONObject> todasLasParadas = new ArrayList<>();
        try {
            JSONObject pOrig = new JSONObject();
            pOrig.put("idRuta",0); pOrig.put("nombre",nombreOrigen);
            pOrig.put("lat",pOrigen.getLatitude()); pOrig.put("lng",pOrigen.getLongitude());
            pOrig.put("orden",0); pOrig.put("kmAcumulado",0.0); pOrig.put("tipo","SUBIDA");
            pOrig.put("idRuta", idRuta);
            todasLasParadas.add(pOrig);
        } catch (Exception e) { Log.e(TAG, "Error creando parada origen", e); }
        for (int i = 0; i < intermedios.size(); i++) {
            try {
                GeoPoint p = intermedios.get(i);
                double kmAcum = 0;
                for (int j = 0; j < i && j < intermedios.size()-1; j++)
                    kmAcum += distanciaEnMetros(intermedios.get(j), intermedios.get(j+1)) / 1000.0;
                JSONObject pInt = new JSONObject();
                pInt.put("idRuta",idRuta); pInt.put("nombre","Parada "+(i+1));
                pInt.put("lat",p.getLatitude()); pInt.put("lng",p.getLongitude());
                pInt.put("orden",i+1); pInt.put("kmAcumulado",Math.round(kmAcum*100.0)/100.0); pInt.put("tipo","AMBAS");
                todasLasParadas.add(pInt);
            } catch (Exception e) { Log.e(TAG, "Error parada intermedia", e); }
        }
        try {
            double distTotal = distanciaEnMetros(pOrigen, pDestino) / 1000.0;
            JSONObject pDest = new JSONObject();
            pDest.put("idRuta",idRuta); pDest.put("nombre",nombreDestino);
            pDest.put("lat",pDestino.getLatitude()); pDest.put("lng",pDestino.getLongitude());
            pDest.put("orden",intermedios.size()+1); pDest.put("kmAcumulado",Math.round(distTotal*100.0)/100.0); pDest.put("tipo","BAJADA");
            todasLasParadas.add(pDest);
        } catch (Exception e) { Log.e(TAG, "Error parada destino", e); }
        final int[] pendientes = {todasLasParadas.size()};
        for (int i = 0; i < todasLasParadas.size(); i++) {
            final int orden = i;
            ConexionApi.getInstance(this).post(Constantes.PARADAS, todasLasParadas.get(i),
                    r -> { synchronized(pendientes) { if (--pendientes[0] <= 0) mainHandler.post(callback); } },
                    e -> { synchronized(pendientes) { if (--pendientes[0] <= 0) mainHandler.post(callback); } }
            );
        }
    }

    private void guardarParadaOrigen(int idRuta, String nombreOrigen, Runnable callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("idRuta",idRuta); body.put("nombre",nombreOrigen);
            body.put("lat",origenPoint.getLatitude()); body.put("lng",origenPoint.getLongitude());
            body.put("orden",0); body.put("kmAcumulado",0.0); body.put("tipo","SUBIDA");
            ConexionApi.getInstance(this).post(Constantes.PARADAS, body,
                    r -> {
                        try {
                            JSONObject bodyD = new JSONObject();
                            bodyD.put("idRuta",idRuta); bodyD.put("nombre",editDestino.getText().toString().trim());
                            bodyD.put("lat",destinoPoint.getLatitude()); bodyD.put("lng",destinoPoint.getLongitude());
                            bodyD.put("orden",1); bodyD.put("kmAcumulado",rutaSeleccionada!=null?rutaSeleccionada.distancia:0); bodyD.put("tipo","BAJADA");
                            ConexionApi.getInstance(this).post(Constantes.PARADAS, bodyD,
                                    r2 -> mainHandler.post(callback), e2 -> mainHandler.post(callback));
                        } catch (Exception e) { mainHandler.post(callback); }
                    },
                    e -> mainHandler.post(callback));
        } catch (Exception e) { mainHandler.post(callback); }
    }

    /* ═══════════ UTILIDADES UI ═══════════ */

    private void setLoadingState(boolean loading) {
        if (loaderContainer != null) loaderContainer.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnCalcular.setEnabled(!loading);
        btnCalcular.setText(loading ? "Buscando rutas..." : "Calcular rutas");
        btnCalcular.setAlpha(loading ? 0.7f : 1.0f);
        if (loading) {
            cardRutasOpciones.setVisibility(View.GONE);
            containerRutas.removeAllViews();
            if (cardContadorHeader != null) cardContadorHeader.setVisibility(View.GONE);
        }
    }

    private void actualizarMensajeLoader(String msg) {
        if (loaderContainer == null) return;
        TextView tv = loaderContainer.findViewWithTag("loader_msg");
        if (tv != null) tv.setText(msg);
    }

    private void mostrarSnackbar(String mensaje, boolean esError) {
        Snackbar sb = Snackbar.make(rootView, mensaje, esError ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT);
        View sbView = sb.getView();
        sbView.setBackgroundColor(esError ? 0xFFB00020 : 0xFF1A2422);
        TextView tv = sbView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (tv != null) { tv.setTextColor(Color.WHITE); tv.setTypeface(null, Typeface.BOLD); }
        sb.show();
    }

    private void animarFab(View fab) {
        ObjectAnimator rot = ObjectAnimator.ofFloat(fab, "rotation", 0f, 15f, -15f, 0f);
        rot.setDuration(300); rot.start();
    }

    private void animarEntradaCard(View card, long delay) {
        card.setAlpha(0f); card.setTranslationY(40f);
        card.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(350)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void setTextoSeguro(View parent, int id, String text) {
        if (parent == null) return;
        TextView tv = parent.findViewById(id);
        if (tv != null) tv.setText(text);
    }

    private int dp(float dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    private boolean coordenadasValidas(GeoPoint punto) {
        if (punto == null) return false;
        return punto.getLatitude() >= MIN_LAT && punto.getLatitude() <= MAX_LAT
                && punto.getLongitude() >= MIN_LNG && punto.getLongitude() <= MAX_LNG;
    }

    private String peticionHttp(String urlStr) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestProperty("User-Agent", "Moviflexx/2.0 (Android)");
            c.setRequestProperty("Accept", "application/json");
            c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return "";
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder b = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) b.append(line);
            return b.toString();
        } finally { if (c != null) c.disconnect(); }
    }

    private String obtenerDireccion(GeoPoint punto) {
        try {
            Geocoder g = new Geocoder(this, Locale.getDefault());
            List<Address> list = g.getFromLocation(punto.getLatitude(), punto.getLongitude(), 1);
            if (list != null && !list.isEmpty()) {
                String linea = list.get(0).getAddressLine(0);
                return linea != null ? linea : "Ubicación actual";
            }
        } catch (Exception ignored) {}
        return "Ubicación actual";
    }

    private double distanciaEnMetros(GeoPoint a, GeoPoint b) {
        double lat1=Math.toRadians(a.getLatitude()), lat2=Math.toRadians(b.getLatitude());
        double dLat=Math.toRadians(b.getLatitude()-a.getLatitude());
        double dLon=Math.toRadians(b.getLongitude()-a.getLongitude());
        double h=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(lat1)*Math.cos(lat2)*Math.sin(dLon/2)*Math.sin(dLon/2);
        return 6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));
    }

    /* ═══════════ CLASE INTERNA ═══════════ */

    private static class RutaInfo {
        ArrayList<GeoPoint> puntos;
        double distancia=0, duracion=0, fuelLiters=0, fuelCostCop=0;
        String tipo="", colorHex="#26C6B0", descripcion="", fuente="";
        int colorInt=0xFF009B8D, indice=0;
        Polyline polyline;
    }
}