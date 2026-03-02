package com.arlys.moviflexx.controller;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Manager.CalificacionesManager;
import com.arlys.moviflexx.model.Manager.RouteManager;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.pojo.RouteOption;
import com.arlys.moviflexx.model.pojo.RouteOptionsResponse;
import com.android.volley.VolleyError;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;


public class DetalleViajeActivity extends AppCompatActivity {

    // ── Constantes ────────────────────────────────────────────────────────────
    private static final String TAG        = "DetalleViaje";
    private static final int    POLLING_MS = 5000;
    private static final int    LOCATION_PERMISSION_REQUEST = 1001;

    private static final int    COLOR_RUTA          = 0xFF009B8D;
    private static final int    COLOR_RUTA_WAYPOINT = 0xFF7B1FA2;
    private static final int    COLOR_CONDUCTOR     = 0xFF1565C0;
    // Color para la línea del segmento conductor→parada/destino (se dibuja ENCIMA de la ruta base)
    private static final int    COLOR_SEGMENTO_ACTIVO = 0xFFFF6F00; // naranja oscuro

    private static final String COLOR_LIBRE = "#4CAF50";
    private static final String COLOR_OCUP  = "#EF5350";
    private static final String COLOR_SEL   = "#FF9800";

    private static final String OSRM_URL        =
            "https://optimizacionofrutas-production.up.railway.app";
    private static final String OSRM_URL_PUBLIC =
            "https://router.project-osrm.org";

    // ── Estados de reserva ────────────────────────────────────────────────────
    private static final String EST_PENDIENTE          = "PENDIENTE";
    private static final String EST_CONFIRMADA         = "CONFIRMADA";
    private static final String EST_ESPERANDO_RECOGIDA = "ESPERANDO_RECOGIDA";
    private static final String EST_RECOGIDO           = "RECOGIDO";
    private static final String EST_COMPLETADO         = "COMPLETADO";
    private static final String EST_CANCELADO          = "CANCELADO";

    private static final List<String> ESTADOS_RESERVABLES = Arrays.asList(
            "EN_CURSO", "INICIADO"
    );

    private static final List<String> ESTADOS_RESERVA_ACTIVA = Arrays.asList(
            "ACTIVA", "CONFIRMADA", "PENDIENTE",
            "ESPERANDO_RECOGIDA", "RECOGIDO", "EN_CURSO", "INICIADO"
    );

    private static final java.util.Set<String> ESTADOS_CANCELADOS = new java.util.HashSet<>(
            Arrays.asList("CANCELADO", "CANCELADA")
    );

    // ── IDs de marcadores ─────────────────────────────────────────────────────
    private static final String MID_ORIGEN    = "m_origen";
    private static final String MID_DESTINO   = "m_destino";
    private static final String MID_PARADA    = "m_parada";
    private static final String MID_CONDUCTOR = "m_conductor";

    private static final int[] COLORES_PASAJEROS = {
            0xFFFF9800, 0xFF9C27B0, 0xFF2196F3, 0xFFE91E63, 0xFF009688, 0xFFFF5722
    };
    private static final String[] COLORES_PASAJEROS_HEX = {
            "#FF9800", "#9C27B0", "#2196F3", "#E91E63", "#009688", "#FF5722"
    };

    // ── UI ────────────────────────────────────────────────────────────────────
    private MapView          map;
    private TextView         txtRuta, txtEstado, txtConductor, txtVehiculo;
    private TextView         txtDistancia, txtDuracion, txtPrecio, txtFechaHora;
    private LinearLayout     layoutCupos;
    private MaterialButton   btnAccionPrincipal;
    private MaterialButton   btnIniciar, btnFinalizar, btnMensajeConductor;
    private MaterialButton   btnRecoger;
    private RecyclerView     rvHistorialParadas;
    private MaterialCardView cardHistorial, cardAcciones;
    private ProgressBar      loaderDetalle;
    private MaterialCardView cardMiReserva;
    private TextView         txtMiReservaInfo;
    private MaterialCardView cardPasajeros;
    private LinearLayout     layoutListaPasajeros;
    private TextView         txtTotalPasajeros;

    // ── Datos del viaje ───────────────────────────────────────────────────────
    private int     viajeId, rutaId;
    private boolean esConductor;
    private String  origenActual = "", destinoActual = "", estadoViaje = "";
    private double  latOrigen, lngOrigen, latDestino, lngDestino;
    private int     cuposTotales = 0, cuposDisponibles = 0;
    private double  precioViaje  = 0;
    private double  distanciaKm  = 0, duracionMin = 0;
    private int     indiceRuta   = 0;
    private SessionManager session;

    // ── Chat ──────────────────────────────────────────────────────────────────
    private int    idPasajeroViaje      = -1;
    private int    idConductorViaje     = -1;
    private String nombreConductorViaje = "";
    private String nombrePasajeroViaje  = "";

    // ── Paradas ───────────────────────────────────────────────────────────────
    private final ArrayList<JSONObject>     paradasRuta = new ArrayList<>();
    private final ArrayList<ParadaDinamica> paradasDin  = new ArrayList<>();

    // ── Geometría de rutas ────────────────────────────────────────────────────
    private final List<List<GeoPoint>> todasLasRutas    = new ArrayList<>();
    private       List<GeoPoint>       rutaActiva        = null;
    private       int                  indiceRutaActiva  = 0;
    private final List<RouteOption>    listaRouteOptions = new ArrayList<>();
    private String geojsonRuta = "";
    private ArrayList<GeoPoint> puntosRutaWaypoint  = new ArrayList<>();

    // ── Posiciones clave ──────────────────────────────────────────────────────
    private GeoPoint gpOrigen  = null;
    private GeoPoint gpDestino = null;
    private GeoPoint gpParada  = null;
    private String   nombreParada = "";
    private final ArrayList<GeoPoint> paradasPasajeros        = new ArrayList<>();
    private final ArrayList<String>   nombresPasajerosParadas = new ArrayList<>();

    // ── Estado de la reserva ──────────────────────────────────────────────────
    private boolean yaReservo        = false;
    private int     cupoSeleccionado = -1;
    private int     idReservaActual  = -1;
    private String  estadoReserva    = "";

    // ── Flags coord ───────────────────────────────────────────────────────────
    private boolean coordsOrigenInvalidas  = false;
    private boolean coordsDestinoInvalidas = false;

    // ── Polling ───────────────────────────────────────────────────────────────
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private Runnable      pollingRunnable;
    private boolean       pollingActivo = false;

    // =========================================================================
    //  ── GPS EN TIEMPO REAL ───────────────────────────────────────────────────
    // =========================================================================
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback            locationCallback;
    private boolean                     gpsActivo = false;
    private GeoPoint                    gpConductorActual = null;

    private void iniciarGPSConductor() {
        if (!esConductor) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }
        if (gpsActivo) return;
        gpsActivo = true;

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(4000);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                android.location.Location location = locationResult.getLastLocation();
                if (location == null) return;

                double lat = location.getLatitude();
                double lng = location.getLongitude();

                gpConductorActual = new GeoPoint(lat, lng);
                runOnUiThread(() -> actualizarMarcadorConductor(gpConductorActual));
                enviarUbicacionAlBackend(lat, lng);
            }
        };

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
        );

        Log.d(TAG, "✅ GPS conductor iniciado");
    }

    private void detenerGPS() {
        if (!gpsActivo || fusedLocationClient == null || locationCallback == null) return;
        fusedLocationClient.removeLocationUpdates(locationCallback);
        gpsActivo = false;
        Log.d(TAG, "⏹️ GPS conductor detenido");
    }

    /**
     * Actualiza solo el marcador del conductor en el mapa sin redibujar todo.
     */
    private void actualizarMarcadorConductor(GeoPoint nuevaPos) {
        if (map == null || nuevaPos == null) return;

        List<Overlay> overlays = map.getOverlays();
        for (int i = overlays.size() - 1; i >= 0; i--) {
            if (overlays.get(i) instanceof Marker) {
                Marker m = (Marker) overlays.get(i);
                if (MID_CONDUCTOR.equals(m.getId())) {
                    overlays.remove(i);
                    break;
                }
            }
        }

        agregarMarcador(MID_CONDUCTOR, nuevaPos, COLOR_CONDUCTOR, "🚗", "📍 Tu posición actual");
        map.invalidate();
    }

    private void enviarUbicacionAlBackend(double lat, double lng) {
        Log.d(TAG, "📡 Ubicación conductor: " + lat + ", " + lng);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarGPSConductor();
            } else {
                Toast.makeText(this,
                        "⚠️ Se necesita permiso de ubicación para el seguimiento en tiempo real",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // =========================================================================
    //  LIFECYCLE
    // =========================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_detalle_viaje2);

        session     = new SessionManager(this);
        viajeId     = getIntent().getIntExtra("ID_VIAJE", 0);
        esConductor = session.isConductor();

        if (viajeId == 0) {
            Toast.makeText(this, "ID de viaje inválido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        cargarDetalleViaje();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
        if (esConductor && "INICIADO".equals(estadoViaje)) iniciarGPSConductor();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
        detenerTodo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detenerTodo();
    }

    private void detenerTodo() {
        pollingActivo = false;
        if (pollingRunnable != null) pollingHandler.removeCallbacks(pollingRunnable);
        detenerGPS();
    }

    // =========================================================================
    //  INIT VIEWS
    // =========================================================================
    private void initViews() {
        map                 = findViewById(R.id.map_mini);
        txtRuta             = findViewById(R.id.txt_info_ruta);
        txtEstado           = findViewById(R.id.txt_estado);
        txtConductor        = findViewById(R.id.txt_conductor);
        txtVehiculo         = findViewById(R.id.txt_vehiculo);
        txtDistancia        = findViewById(R.id.txt_distancia_ruta);
        txtDuracion         = findViewById(R.id.txt_duracion_ruta);
        txtPrecio           = findViewById(R.id.txt_precio);
        txtFechaHora        = findViewById(R.id.txt_fecha_hora);
        layoutCupos         = findViewById(R.id.layout_cupos);
        btnAccionPrincipal  = findViewById(R.id.btn_reservar);
        btnIniciar          = findViewById(R.id.btn_iniciar);
        btnFinalizar        = findViewById(R.id.btn_finalizar);
        btnMensajeConductor = findViewById(R.id.btn_mensaje_conductor);
        rvHistorialParadas  = findViewById(R.id.rv_historial_paradas);
        cardHistorial       = findViewById(R.id.card_historial);
        cardAcciones        = findViewById(R.id.card_acciones);
        loaderDetalle       = findViewById(R.id.loader_detalle);

        rvHistorialParadas.setLayoutManager(new LinearLayoutManager(this));

        if (btnAccionPrincipal != null)
            btnAccionPrincipal.setOnClickListener(v -> mostrarBottomSheetParada());

        btnIniciar.setOnClickListener(v -> cambiarEstadoViaje("iniciar"));
        btnFinalizar.setOnClickListener(v -> confirmarFinalizar());
        if (btnMensajeConductor != null)
            btnMensajeConductor.setOnClickListener(v -> abrirOCrearChat());

        configurarMapaBase();
        crearBotonRecoger();
        crearCardMiReserva();
        crearCardPasajeros();
    }

    private void configurarMapaBase() {
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.setFlingEnabled(true);
        map.setTilesScaledToDpi(true);
        map.setUseDataConnection(true);
        map.setHorizontalMapRepetitionEnabled(false);
        map.setVerticalMapRepetitionEnabled(false);
        map.setMinZoomLevel(5.0);
        map.setMaxZoomLevel(20.0);
        map.getController().setZoom(13.0);
        map.getController().setCenter(new GeoPoint(2.4419, -76.6063));
        org.osmdroid.config.Configuration.getInstance().setOsmdroidTileCache(
                new java.io.File(getCacheDir(), "osmdroid_tiles"));
        org.osmdroid.config.Configuration.getInstance()
                .setTileFileSystemCacheMaxBytes(100L * 1024 * 1024);
        org.osmdroid.config.Configuration.getInstance()
                .setTileFileSystemCacheTrimBytes(80L * 1024 * 1024);
    }

    private void crearBotonRecoger() {
        if (cardAcciones == null) return;
        LinearLayout inner = (LinearLayout) cardAcciones.getChildAt(0);
        if (inner == null) return;
        float d = getResources().getDisplayMetrics().density;
        btnRecoger = new MaterialButton(this);
        btnRecoger.setText("✅  RECOGER PASAJERO");
        btnRecoger.setTextSize(14f);
        btnRecoger.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(56*d));
        lp.topMargin = (int)(10*d);
        btnRecoger.setLayoutParams(lp);
        btnRecoger.setCornerRadius((int)(14*d));
        btnRecoger.setBackgroundColor(Color.parseColor("#1976D2"));
        btnRecoger.setVisibility(View.GONE);
        btnRecoger.setOnClickListener(v -> confirmarRecogida());
        inner.addView(btnRecoger, 0);
    }

    private void crearCardMiReserva() {
        ViewGroup c = buscarScrollContent();
        if (c == null) return;
        float d = getResources().getDisplayMetrics().density;
        int p16=(int)(16*d), p12=(int)(12*d), p8=(int)(8*d);

        cardMiReserva = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(p16, p8, p16, p8);
        cardMiReserva.setLayoutParams(lp);
        cardMiReserva.setRadius(16*d);
        cardMiReserva.setCardElevation(4*d);
        cardMiReserva.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
        cardMiReserva.setVisibility(View.GONE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p16, p12, p16, p12);

        TextView titulo = new TextView(this);
        titulo.setText("🎫 Tu reserva");
        titulo.setTextSize(13f);
        titulo.setTypeface(null, Typeface.BOLD);
        titulo.setTextColor(Color.parseColor("#2E7D32"));
        inner.addView(titulo);

        txtMiReservaInfo = new TextView(this);
        txtMiReservaInfo.setTextSize(14f);
        txtMiReservaInfo.setTextColor(Color.parseColor("#004D40"));
        LinearLayout.LayoutParams lpt = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpt.topMargin = p8;
        txtMiReservaInfo.setLayoutParams(lpt);
        inner.addView(txtMiReservaInfo);

        MaterialButton btnCancelar = new MaterialButton(this);
        btnCancelar.setText("❌  Cancelar mi reserva");
        btnCancelar.setTextSize(13f);
        btnCancelar.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lpCan = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(44*d));
        lpCan.topMargin = (int)(10*d);
        btnCancelar.setLayoutParams(lpCan);
        btnCancelar.setCornerRadius((int)(12*d));
        btnCancelar.setBackgroundColor(Color.parseColor("#EF5350"));
        btnCancelar.setOnClickListener(v -> cancelarMiReserva());
        inner.addView(btnCancelar);

        cardMiReserva.addView(inner);
        c.addView(cardMiReserva, 0);
    }

    private void crearCardPasajeros() {
        ViewGroup c = buscarScrollContent();
        if (c == null) return;
        float d = getResources().getDisplayMetrics().density;
        int p16=(int)(16*d), p12=(int)(12*d), p8=(int)(8*d);

        cardPasajeros = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(p16, p8, p16, p8);
        cardPasajeros.setLayoutParams(lp);
        cardPasajeros.setRadius(16*d);
        cardPasajeros.setCardElevation(4*d);
        cardPasajeros.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
        cardPasajeros.setVisibility(View.GONE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p16, p12, p16, p12);

        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView titulo = new TextView(this);
        titulo.setText("🧑‍🤝‍🧑 Pasajeros reservados");
        titulo.setTextSize(14f);
        titulo.setTypeface(null, Typeface.BOLD);
        titulo.setTextColor(Color.parseColor("#1565C0"));
        titulo.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fila.addView(titulo);

        txtTotalPasajeros = new TextView(this);
        txtTotalPasajeros.setTextSize(12f);
        txtTotalPasajeros.setTextColor(Color.parseColor("#1565C0"));
        fila.addView(txtTotalPasajeros);
        inner.addView(fila);

        View sep = new View(this);
        LinearLayout.LayoutParams ls = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1*d));
        ls.setMargins(0, p8, 0, p8);
        sep.setLayoutParams(ls);
        sep.setBackgroundColor(Color.parseColor("#BBDEFB"));
        inner.addView(sep);

        layoutListaPasajeros = new LinearLayout(this);
        layoutListaPasajeros.setOrientation(LinearLayout.VERTICAL);
        inner.addView(layoutListaPasajeros);

        cardPasajeros.addView(inner);
        c.addView(cardPasajeros, 0);
    }

    private ViewGroup buscarScrollContent() {
        View raiz = findViewById(android.R.id.content);
        if (!(raiz instanceof ViewGroup)) return null;
        return buscarEn((ViewGroup) raiz);
    }

    private ViewGroup buscarEn(ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View ch = vg.getChildAt(i);
            if (ch instanceof androidx.core.widget.NestedScrollView) {
                androidx.core.widget.NestedScrollView sv = (androidx.core.widget.NestedScrollView) ch;
                if (sv.getChildCount() > 0 && sv.getChildAt(0) instanceof ViewGroup)
                    return (ViewGroup) sv.getChildAt(0);
            }
            if (ch instanceof android.widget.ScrollView) {
                android.widget.ScrollView sv = (android.widget.ScrollView) ch;
                if (sv.getChildCount() > 0 && sv.getChildAt(0) instanceof ViewGroup)
                    return (ViewGroup) sv.getChildAt(0);
            }
            if (ch instanceof LinearLayout) return (ViewGroup) ch;
        }
        return vg;
    }

    // =========================================================================
    //  CARGA PRINCIPAL
    // =========================================================================
    private void cargarDetalleViaje() {
        loaderDetalle.setVisibility(View.VISIBLE);
        geojsonRuta = "";
        todasLasRutas.clear();
        listaRouteOptions.clear();
        rutaActiva = null;
        ConexionApi.getInstance(this).getObject(
                Constantes.viajePorId((long) viajeId),
                response -> {
                    loaderDetalle.setVisibility(View.GONE);
                    try { procesarViaje(response); }
                    catch (Exception e) { Log.e(TAG, "Error procesando viaje", e); }
                },
                error -> {
                    loaderDetalle.setVisibility(View.GONE);
                    Toast.makeText(this, "Error de conexión", Toast.LENGTH_LONG).show();
                }
        );
    }

    // =========================================================================
    //  PROCESAMIENTO DEL JSON
    // =========================================================================
    private void procesarViaje(JSONObject r) throws Exception {
        try { Log.d(TAG, "═══ JSON VIAJE COMPLETO ═══\n" + r.toString(2)); }
        catch (Exception ignored) {}

        estadoViaje      = r.optString("estado", "DESCONOCIDO").trim().toUpperCase();
        cuposTotales     = r.optInt("cuposTotales", 0);
        cuposDisponibles = r.optInt("cuposDisponibles", 0);
        indiceRuta = r.optInt("indiceRuta", -1);
        if (indiceRuta < 0) {
            JSONObject rutaTmp = r.optJSONObject("ruta");
            if (rutaTmp != null) indiceRuta = rutaTmp.optInt("indiceRuta", 0);
            else indiceRuta = 0;
        }
        paradasPasajeros.clear();
        nombresPasajerosParadas.clear();

        JSONObject ruta = r.optJSONObject("ruta");
        extraerCoordsYNombres(r, ruta);
        extraerMetricas(r, ruta);
        gpOrigen  = new GeoPoint(latOrigen,  lngOrigen);
        gpDestino = new GeoPoint(latDestino, lngDestino);

        geojsonRuta = "";
        if (ruta != null)
            geojsonRuta = ruta.optString("geojson", ruta.optString("geojsonRuta", "")).trim();
        if (geojsonRuta.isEmpty())
            geojsonRuta = r.optString("geojson", r.optString("geojsonRuta", "")).trim();
        if ("null".equalsIgnoreCase(geojsonRuta)) geojsonRuta = "";

        double pr = r.optDouble("precio", -1);
        if (pr < 0) {
            try { pr = Double.parseDouble(r.optString("precio", "0")); }
            catch (Exception ex) { pr = 0; }
        }
        precioViaje = pr;
        if (txtPrecio != null)
            txtPrecio.setText(precioViaje > 0
                    ? "$ " + String.format(Locale.getDefault(), "%,.0f", precioViaje)
                    : "Precio no definido");

        mostrarFechaHora(r.optString("fechaHoraSalida",
                r.optString("fechaSalida", r.optString("fecha", ""))));
        extraerConductor(r);
        if (idConductorViaje <= 0 && esConductor) {
            idConductorViaje     = session.getIdUsuario();
            nombreConductorViaje = session.getNombre();
        }

        JSONObject pas = r.optJSONObject("pasajero");
        if (pas != null) {
            idPasajeroViaje     = extractId(pas);
            nombrePasajeroViaje = extractNombre(pas);
        }
        JSONObject veh = r.optJSONObject("vehiculo");
        if (veh != null && txtVehiculo != null)
            txtVehiculo.setText("🚘 " + veh.optString("marca","") + " "
                    + veh.optString("modelo","") + " • " + veh.optString("placa",""));

        actualizarNombreConductorUI();
        txtRuta.setText("📍 " + origenActual + " → " + destinoActual);
        txtEstado.setText(etiquetaEstado(estadoViaje));
        actualizarChipsCupos(cuposTotales, cuposDisponibles);
        configurarBotones();
        cargarParadasRuta();
        iniciarPolling();

        if (esConductor && "INICIADO".equals(estadoViaje)) {
            iniciarGPSConductor();
        }

        if (esConductor) cargarReservasConductor();
        else             cargarMiReservaPasajero();
    }

    // =========================================================================
    //  EXTRACCIÓN DE COORDS Y NOMBRES
    // =========================================================================
    private void extraerCoordsYNombres(JSONObject r, JSONObject ruta) {
        JSONObject src = ruta != null ? ruta : r;
        rutaId = ruta != null
                ? ruta.optInt("idRuta", ruta.optInt("id", 0))
                : r.optInt("idRuta", 0);

        latOrigen = Double.NaN; lngOrigen = Double.NaN;
        latDestino = Double.NaN; lngDestino = Double.NaN;
        origenActual = ""; destinoActual = "";

        JSONArray paradas = null;
        if (ruta != null)  paradas = ruta.optJSONArray("paradas");
        if (paradas == null) paradas = r.optJSONArray("paradas");

        if (paradas != null && paradas.length() >= 2) {
            JSONObject pOrig = null, pDest = null;
            int maxOrden = -1;
            for (int i = 0; i < paradas.length(); i++) {
                JSONObject p = paradas.optJSONObject(i);
                if (p == null) continue;
                int    orden = p.optInt("orden", -1);
                String tipo  = p.optString("tipo", "").toUpperCase();
                double pLat  = p.optDouble("lat", Double.NaN);
                double pLng  = p.optDouble("lng", Double.NaN);
                if ((orden == 0 || "SUBIDA".equals(tipo)) && pOrig == null)
                    if (!Double.isNaN(pLat) && pLat != 0) pOrig = p;
                if ("BAJADA".equals(tipo)) {
                    if (!Double.isNaN(pLat) && pLat != 0) pDest = p;
                } else if (orden > maxOrden && !Double.isNaN(pLat) && pLat != 0) {
                    maxOrden = orden;
                    if (pOrig == null || orden != 0) pDest = p;
                }
            }
            if (pOrig == null)
                for (int i = 0; i < paradas.length(); i++) {
                    JSONObject p = paradas.optJSONObject(i);
                    if (p != null && !Double.isNaN(p.optDouble("lat", Double.NaN))
                            && p.optDouble("lat", 0) != 0) { pOrig = p; break; }
                }
            if (pDest == null || pDest == pOrig)
                for (int i = paradas.length() - 1; i >= 0; i--) {
                    JSONObject p = paradas.optJSONObject(i);
                    if (p != null && p != pOrig
                            && !Double.isNaN(p.optDouble("lat", Double.NaN))
                            && p.optDouble("lat", 0) != 0) { pDest = p; break; }
                }
            if (pOrig != null) {
                latOrigen = pOrig.optDouble("lat", Double.NaN);
                lngOrigen = pOrig.optDouble("lng", Double.NaN);
                String nom = pOrig.optString("nombre","").trim();
                if (!nom.isEmpty() && !nom.equals("null")) origenActual = nom;
            }
            if (pDest != null) {
                double ld = pDest.optDouble("lat", Double.NaN);
                double lg = pDest.optDouble("lng", Double.NaN);
                if (!Double.isNaN(ld) && ld != 0
                        && !sonIguales(Double.isNaN(latOrigen)?0:latOrigen,
                        Double.isNaN(lngOrigen)?0:lngOrigen, ld, lg)) {
                    latDestino = ld; lngDestino = lg;
                    String nom = pDest.optString("nombre","").trim();
                    if (!nom.isEmpty() && !nom.equals("null")) destinoActual = nom;
                }
            }
        }

        if (Double.isNaN(latOrigen) || latOrigen == 0) {
            latOrigen = primeraCoord(src, new String[]{"latOrigen","latitudOrigen","latInicio"}, Double.NaN);
            lngOrigen = primeraCoord(src, new String[]{"lngOrigen","longitudOrigen","lngInicio"}, Double.NaN);
            if ((Double.isNaN(latOrigen) || latOrigen == 0) && ruta != null) {
                latOrigen = primeraCoord(r, new String[]{"latOrigen","latitudOrigen"}, Double.NaN);
                lngOrigen = primeraCoord(r, new String[]{"lngOrigen","longitudOrigen"}, Double.NaN);
            }
        }
        if (Double.isNaN(latDestino) || latDestino == 0) {
            latDestino = primeraCoordDistinta(src, new String[]{"latDestino","latitudDestino","latFin"},
                    Double.isNaN(latOrigen)?0:latOrigen, Double.NaN);
            lngDestino = primeraCoordDistinta(src, new String[]{"lngDestino","longitudDestino","lngFin"},
                    Double.isNaN(lngOrigen)?0:lngOrigen, Double.NaN);
        }

        if (Double.isNaN(latOrigen) || latOrigen == 0) {
            coordsOrigenInvalidas = true; latOrigen = 2.4419; lngOrigen = -76.6063;
        }
        if (Double.isNaN(latDestino) || latDestino == 0
                || sonIguales(latOrigen, lngOrigen, latDestino, lngDestino)) {
            coordsDestinoInvalidas = true; latDestino = 2.4550; lngDestino = -76.5980;
        }

        if (origenActual.isEmpty())
            origenActual = primeraStr(src, new String[]{"origen","puntoOrigen","inicio","nombreOrigen","lugarOrigen","direccionOrigen","origenNombre"});
        if (origenActual.isEmpty() && ruta != null)
            origenActual = primeraStr(r, new String[]{"origen","puntoOrigen","inicio","nombreOrigen"});
        if (origenActual.isEmpty()) origenActual = extraerOrigenDeNombre(src);
        if (origenActual.isEmpty() && ruta != null) origenActual = extraerOrigenDeNombre(r);
        if (origenActual.isEmpty()) origenActual = "Origen";

        if (destinoActual.isEmpty())
            destinoActual = primeraStr(src, new String[]{"destino","puntoDestino","fin","nombreDestino","lugarDestino","direccionDestino","destinoNombre"});
        if (destinoActual.isEmpty() && ruta != null)
            destinoActual = primeraStr(r, new String[]{"destino","puntoDestino","fin","nombreDestino"});
        if (destinoActual.isEmpty()) destinoActual = extraerDestinoDeNombre(src);
        if (destinoActual.isEmpty() && ruta != null) destinoActual = extraerDestinoDeNombre(r);
        if (destinoActual.isEmpty()) destinoActual = "Destino";
    }

    private String extraerOrigenDeNombre(JSONObject obj) {
        String n = obj.optString("nombre","").trim();
        if (n.isEmpty() || n.equals("null")) return "";
        if (n.contains("→"))  return n.split("→")[0].trim();
        if (n.contains("->")) return n.split("->")[0].trim();
        if (n.contains(" - ")) return n.split(" - ")[0].trim();
        return "";
    }

    private String extraerDestinoDeNombre(JSONObject obj) {
        String n = obj.optString("nombre","").trim();
        if (n.isEmpty() || n.equals("null")) return "";
        String[] p = null;
        if (n.contains("→"))       p = n.split("→",  2);
        else if (n.contains("->")) p = n.split("->", 2);
        else if (n.contains(" - ")) p = n.split(" - ", 2);
        if (p != null && p.length > 1) return p[1].trim();
        return "";
    }

    private void extraerMetricas(JSONObject r, JSONObject ruta) {
        JSONObject src = ruta != null ? ruta : r;
        distanciaKm = src.optDouble("distanciaKm", src.optDouble("distancia", 0));
        duracionMin = src.optDouble("duracionMin",  src.optDouble("duracion",  0));
        if (txtDistancia != null && distanciaKm > 0)
            txtDistancia.setText(String.format("%.1f km", distanciaKm));
        if (txtDuracion != null && duracionMin > 0)
            txtDuracion.setText(String.format("%.0f min", duracionMin));
    }

    // =========================================================================
    //  MAPA
    // =========================================================================
    private void renderizarMapa() {
        limpiarOverlays();

        boolean viajeIniciado   = "INICIADO".equals(estadoViaje) || "EN_CURSO".equals(estadoViaje);
        boolean viajeFinalizado = "FINALIZADO".equals(estadoViaje) || "COMPLETADO".equals(estadoViaje);

        if (viajeFinalizado) {
            if (rutaActiva != null && rutaActiva.size() >= 2)
                dibujarPolilinea(rutaActiva, COLOR_RUTA);
            agregarMarcador(MID_ORIGEN,  gpOrigen,  0xFF4CAF50, "A", "🟢 " + origenActual);
            agregarMarcador(MID_DESTINO, gpDestino, 0xFFEF5350, "B", "🔴 " + destinoActual);
            ajustarCamara();
            map.invalidate();
            return;
        }

        if (!esConductor) {
            renderizarMapaPasajero(viajeIniciado);
        } else {
            if (viajeIniciado) {
                renderizarMapaConductorIniciado();
            } else {
                // Conductor ve su ruta base antes de iniciar
                if (rutaActiva != null && rutaActiva.size() >= 2)
                    dibujarPolilinea(rutaActiva, COLOR_RUTA);
                agregarMarcador(MID_ORIGEN,  gpOrigen,  0xFF4CAF50, "A", "🟢 " + origenActual);
                agregarMarcador(MID_DESTINO, gpDestino, 0xFFEF5350, "B", "🔴 " + destinoActual);
                for (int i = 0; i < paradasPasajeros.size(); i++) {
                    GeoPoint pp = paradasPasajeros.get(i);
                    if (pp == null) continue;
                    int color = COLORES_PASAJEROS[i % COLORES_PASAJEROS.length];
                    String etq = i < nombresPasajerosParadas.size() ? nombresPasajerosParadas.get(i) : "Pasajero "+(i+1);
                    agregarMarcador("parada_pas_"+i, pp, color, "P"+(i+1), "🚏 "+etq);
                }
            }
        }

        ajustarCamara();
        map.invalidate();
    }

    /**
     * MAPA PASAJERO
     */
    private void renderizarMapaPasajero(boolean viajeIniciado) {

        boolean pasajeroRecogido = EST_RECOGIDO.equals(estadoReserva)
                || EST_COMPLETADO.equals(estadoReserva);

        if (pasajeroRecogido) {
            agregarMarcador(MID_ORIGEN,  gpOrigen,  0xFF4CAF50, "A", "🟢 " + origenActual);
            agregarMarcador(MID_DESTINO, gpDestino, 0xFFEF5350, "B", "🏁 Tu destino: " + destinoActual);
            if (!puntosRutaWaypoint.isEmpty()) {
                dibujarPolilineaWaypoint(puntosRutaWaypoint);
            } else {
                if (rutaActiva != null && rutaActiva.size() >= 2)
                    dibujarPolilinea(rutaActiva, COLOR_RUTA);
                pedirRutaConWaypoint();
            }
            return;
        }

        // Siempre mostrar la ruta base del conductor (A→B)
        if (rutaActiva != null && rutaActiva.size() >= 2) {
            dibujarPolilinea(rutaActiva, COLOR_RUTA);
        }
        agregarMarcador(MID_ORIGEN,  gpOrigen,  0xFF4CAF50, "A", "🟢 " + origenActual);
        agregarMarcador(MID_DESTINO, gpDestino, 0xFFEF5350, "B", "🔴 " + destinoActual);

        if (yaReservo && gpParada != null) {
            boolean esperando = EST_ESPERANDO_RECOGIDA.equals(estadoReserva)
                    || (viajeIniciado && !estadoReserva.isEmpty()
                    && !EST_COMPLETADO.equals(estadoReserva)
                    && !EST_CANCELADO.equals(estadoReserva));

            int colorParada = esperando ? 0xFFFF6F00 : 0xFFFF9800;
            String textoParada = esperando
                    ? "⏳ Aquí te recogen: " + nombreParada
                    : "🚏 Tu parada: " + nombreParada;

            agregarMarcador(MID_PARADA, gpParada, colorParada, "P", textoParada);
        }
    }

    /**
     * MAPA CONDUCTOR — Viaje INICIADO
     *
     * ✅ FIX: Siempre muestra la ruta completa A→B como base.
     * El segmento activo (conductor→parada o conductor→destino) se dibuja ENCIMA
     * con color diferente, SIN borrar la ruta base.
     */
    private void renderizarMapaConductorIniciado() {
        GeoPoint posConductor = (gpConductorActual != null) ? gpConductorActual : gpOrigen;

        // ── 1. Ruta base A→B SIEMPRE visible ─────────────────────────────────
        if (rutaActiva != null && rutaActiva.size() >= 2) {
            dibujarPolilinea(rutaActiva, COLOR_RUTA);
        }

        // ── 2. Marcadores fijos origen y destino ──────────────────────────────
        agregarMarcador(MID_ORIGEN,  gpOrigen,  0xFF4CAF50, "A", "🟢 " + origenActual);
        agregarMarcador(MID_DESTINO, gpDestino, 0xFFEF5350, "B", "🔴 " + destinoActual);

        // ── 3. Marcador GPS del conductor ─────────────────────────────────────
        agregarMarcador(MID_CONDUCTOR, posConductor, COLOR_CONDUCTOR, "🚗", "📍 Tu posición");

        boolean algunRecogido = EST_RECOGIDO.equals(estadoReserva)
                || EST_COMPLETADO.equals(estadoReserva);

        if (algunRecogido) {
            // POST-RECOGIDA: pedir ruta conductor→destino y dibujarla ENCIMA (sin borrar base)
            pedirSegmentoConductorADestino(posConductor);

        } else if (!paradasPasajeros.isEmpty()) {
            // RECOGIDA PENDIENTE: mostrar marcadores P de cada pasajero
            GeoPoint primeraParada = null;
            for (int i = 0; i < paradasPasajeros.size(); i++) {
                GeoPoint pp = paradasPasajeros.get(i);
                if (pp == null) continue;
                int color = COLORES_PASAJEROS[i % COLORES_PASAJEROS.length];
                String etq = i < nombresPasajerosParadas.size()
                        ? nombresPasajerosParadas.get(i) : "Pasajero " + (i + 1);
                agregarMarcador("parada_pas_" + i, pp, color, "P" + (i + 1), "🚏 " + etq);
                if (primeraParada == null) primeraParada = pp;
            }
            // Pedir segmento conductor→primera parada y dibujarlo ENCIMA (sin borrar base)
            if (primeraParada != null) {
                pedirSegmentoConductorAParada(posConductor, primeraParada);
            }
        }
        // Si no hay pasajeros: solo ruta base + A + B + 🚗
    }

    // =========================================================================
    //  ✅ FIX: pedirSegmentoConductorAParada
    //  Antes se llamaba "pedirRutaConductorAParada" y borraba toda la ruta base
    //  con limpiarPolilineas() antes de dibujar el segmento.
    //  Ahora agrega el segmento ENCIMA sin borrar nada.
    // =========================================================================
    private void pedirSegmentoConductorAParada(GeoPoint desde, GeoPoint hasta) {
        if (desde == null || hasta == null) return;
        new Thread(() -> {
            try {
                String segmento = desde.getLongitude() + "," + desde.getLatitude() + ";"
                        + hasta.getLongitude() + "," + hasta.getLatitude()
                        + "?overview=full&geometries=geojson";

                ArrayList<GeoPoint> pts = null;

                // Intento 1: OSRM propio
                try {
                    pts = parsearRutaSimpleOSRM(peticionHttp(OSRM_URL + "/route/v1/driving/" + segmento));
                } catch (Exception e1) {
                    Log.w(TAG, "OSRM propio falló en pedirSegmentoAParada: " + e1.getMessage());
                }

                // Intento 2: OSRM público
                if (pts == null || pts.size() < 2) {
                    try {
                        pts = parsearRutaSimpleOSRM(peticionHttp(OSRM_URL_PUBLIC + "/route/v1/driving/" + segmento));
                    } catch (Exception e2) {
                        Log.w(TAG, "OSRM público también falló: " + e2.getMessage());
                    }
                }

                if (pts != null && pts.size() >= 2) {
                    final ArrayList<GeoPoint> fPts = pts;
                    runOnUiThread(() -> {
                        // ✅ NO se llama limpiarPolilineas() — solo se agrega encima
                        dibujarPolilineaSegmento(fPts);
                        map.invalidate();
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "pedirSegmentoConductorAParada error general: " + e.getMessage());
            }
        }).start();
    }

    // =========================================================================
    //  ✅ FIX: pedirSegmentoConductorADestino
    //  Antes se llamaba "pedirRutaConductorADestino" y borraba toda la ruta base.
    //  Ahora agrega el segmento ENCIMA sin borrar nada.
    // =========================================================================
    private void pedirSegmentoConductorADestino(GeoPoint desde) {
        if (desde == null || gpDestino == null) return;
        new Thread(() -> {
            try {
                String segmento = desde.getLongitude() + "," + desde.getLatitude() + ";"
                        + lngDestino + "," + latDestino
                        + "?overview=full&geometries=geojson";

                ArrayList<GeoPoint> pts = null;

                // Intento 1: OSRM propio
                try {
                    pts = parsearRutaSimpleOSRM(peticionHttp(OSRM_URL + "/route/v1/driving/" + segmento));
                } catch (Exception e1) {
                    Log.w(TAG, "OSRM propio falló en pedirSegmentoADestino: " + e1.getMessage());
                }

                // Intento 2: OSRM público
                if (pts == null || pts.size() < 2) {
                    try {
                        pts = parsearRutaSimpleOSRM(peticionHttp(OSRM_URL_PUBLIC + "/route/v1/driving/" + segmento));
                    } catch (Exception e2) {
                        Log.w(TAG, "OSRM público también falló: " + e2.getMessage());
                    }
                }

                if (pts != null && pts.size() >= 2) {
                    puntosRutaWaypoint = pts;
                    final ArrayList<GeoPoint> fPts = pts;
                    runOnUiThread(() -> {
                        // ✅ NO se llama limpiarPolilineas() — solo se agrega encima
                        dibujarPolilineaSegmento(fPts);
                        map.invalidate();
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "pedirSegmentoConductorADestino falló: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Parsea una respuesta OSRM simple y retorna los puntos de la primera ruta.
     */
    private ArrayList<GeoPoint> parsearRutaSimpleOSRM(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(json);
            if (!"Ok".equals(obj.optString("code", ""))) return null;
            JSONArray routes = obj.optJSONArray("routes");
            if (routes == null || routes.length() == 0) return null;
            JSONArray coords = routes.getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONArray("coordinates");
            ArrayList<GeoPoint> pts = new ArrayList<>();
            for (int i = 0; i < coords.length(); i++) {
                JSONArray pair = coords.getJSONArray(i);
                pts.add(new GeoPoint(pair.getDouble(1), pair.getDouble(0)));
            }
            return pts.size() >= 2 ? pts : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Limpia solo las polilíneas del mapa (sin tocar los marcadores).
     * Se usa únicamente en casos específicos donde se necesita redibujar la ruta.
     */
    private void limpiarPolilineas() {
        if (map == null) return;
        List<Overlay> overlays = map.getOverlays();
        for (int i = overlays.size() - 1; i >= 0; i--) {
            if (overlays.get(i) instanceof Polyline) overlays.remove(i);
        }
    }

    private void seleccionarRuta(int index, @Nullable RouteOption routeOption) {
        if (index < 0 || index >= todasLasRutas.size()) return;
        List<GeoPoint> fuenteRuta = todasLasRutas.get(index);
        if (fuenteRuta == null || fuenteRuta.size() < 2) {
            Toast.makeText(this,"Ruta no disponible",Toast.LENGTH_SHORT).show(); return;
        }
        indiceRutaActiva = index;
        rutaActiva = java.util.Collections.unmodifiableList(new ArrayList<>(fuenteRuta));
        if (routeOption != null) {
            if (routeOption.distanceKm > 0) { distanciaKm = routeOption.distanceKm; if (txtDistancia!=null) txtDistancia.setText(String.format("%.1f km",distanciaKm)); }
            if (routeOption.durationMin > 0) { duracionMin = routeOption.durationMin; if (txtDuracion!=null) txtDuracion.setText(String.format("%.0f min",duracionMin)); }
        }
        generarParadasDinamicas();
        renderizarMapa();
    }

    private void limpiarOverlays() {
        if (map == null) return;
        List<Overlay> overlays = map.getOverlays();
        for (int i = overlays.size()-1; i >= 0; i--) {
            Overlay o = overlays.get(i);
            if (o instanceof Marker || o instanceof Polyline) overlays.remove(i);
        }
    }

    /**
     * Dibuja la ruta base (color principal, línea gruesa).
     */
    private void dibujarPolilinea(List<GeoPoint> pts, int color) {
        if (pts == null || pts.size() < 2) return;
        final ArrayList<GeoPoint> copia = new ArrayList<>(pts);
        Polyline sombra = new Polyline(map); sombra.setPoints(copia); sombra.setColor(Color.argb(60,0,0,0)); sombra.setWidth(24f); map.getOverlays().add(sombra);
        Polyline borde  = new Polyline(map); borde.setPoints(copia);  borde.setColor(Color.WHITE);              borde.setWidth(20f);  map.getOverlays().add(borde);
        Polyline linea  = new Polyline(map); linea.setPoints(copia);  linea.setColor(color);                    linea.setWidth(13f);  map.getOverlays().add(linea);
    }

    /**
     * ✅ NUEVO: Dibuja el segmento activo del conductor (encima de la ruta base).
     * Usa color naranja oscuro y línea discontinua/resaltada para distinguirla.
     * Se llama DESPUÉS de dibujar la ruta base, sin borrarla.
     */
    private void dibujarPolilineaSegmento(List<GeoPoint> pts) {
        if (pts == null || pts.size() < 2) return;
        final ArrayList<GeoPoint> copia = new ArrayList<>(pts);
        // Línea de borde blanca más delgada
        Polyline borde = new Polyline(map);
        borde.setPoints(copia);
        borde.setColor(Color.WHITE);
        borde.setWidth(14f);
        map.getOverlays().add(borde);
        // Línea de segmento activo en naranja oscuro
        Polyline linea = new Polyline(map);
        linea.setPoints(copia);
        linea.setColor(COLOR_SEGMENTO_ACTIVO);
        linea.setWidth(9f);
        map.getOverlays().add(linea);
    }

    private void dibujarPolilineaWaypoint(List<GeoPoint> pts) {
        if (pts == null || pts.size() < 2) return;
        final ArrayList<GeoPoint> copia = new ArrayList<>(pts);
        Polyline sombra = new Polyline(map); sombra.setPoints(copia); sombra.setColor(Color.argb(50,0,0,0)); sombra.setWidth(18f); map.getOverlays().add(sombra);
        Polyline borde  = new Polyline(map); borde.setPoints(copia);  borde.setColor(Color.WHITE);              borde.setWidth(14f); map.getOverlays().add(borde);
        Polyline linea  = new Polyline(map); linea.setPoints(copia);  linea.setColor(COLOR_RUTA_WAYPOINT);      linea.setWidth(9f);  map.getOverlays().add(linea);
    }

    private void agregarMarcador(String id, GeoPoint pos, int color, String letra, String titulo) {
        if (pos == null) return;
        Marker m = new Marker(map);
        m.setId(id); m.setPosition(pos);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle(titulo);
        m.setIcon(new BitmapDrawable(getResources(), crearBitmapMarcador(color, letra)));
        map.getOverlays().add(m);
    }

    private void ajustarCamara() {
        ArrayList<GeoPoint> todos = new ArrayList<>();

        boolean viajeIniciado    = "INICIADO".equals(estadoViaje) || "EN_CURSO".equals(estadoViaje);
        boolean pasajeroRecogido = EST_RECOGIDO.equals(estadoReserva) || EST_COMPLETADO.equals(estadoReserva);

        if (esConductor && viajeIniciado) {
            if (gpConductorActual != null) todos.add(gpConductorActual);
            else if (gpOrigen != null) todos.add(gpOrigen);
            if (pasajeroRecogido) {
                if (gpDestino != null) todos.add(gpDestino);
                if (!puntosRutaWaypoint.isEmpty()) todos.addAll(puntosRutaWaypoint);
            } else {
                for (GeoPoint pp : paradasPasajeros) if (pp != null) todos.add(pp);
            }
            // Siempre incluir A y B en el encuadre del conductor
            if (gpOrigen != null) todos.add(gpOrigen);
            if (gpDestino != null) todos.add(gpDestino);
        } else if (!esConductor && pasajeroRecogido) {
            if (!puntosRutaWaypoint.isEmpty()) todos.addAll(puntosRutaWaypoint);
            else {
                if (gpOrigen != null) todos.add(gpOrigen);
                if (gpDestino != null) todos.add(gpDestino);
            }
        } else {
            if (rutaActiva != null && !rutaActiva.isEmpty()) todos.addAll(rutaActiva);
            if (!esConductor && gpParada != null) todos.add(gpParada);
        }

        if (todos.size() < 2) {
            GeoPoint centro = gpConductorActual != null ? gpConductorActual
                    : gpOrigen != null ? gpOrigen
                    : new GeoPoint(2.4419, -76.6063);
            map.getController().animateTo(centro);
            map.getController().setZoom(14.0);
            return;
        }
        double mnLat=Double.MAX_VALUE, mxLat=-Double.MAX_VALUE;
        double mnLng=Double.MAX_VALUE, mxLng=-Double.MAX_VALUE;
        for (GeoPoint p : todos) {
            mnLat=Math.min(mnLat,p.getLatitude());  mxLat=Math.max(mxLat,p.getLatitude());
            mnLng=Math.min(mnLng,p.getLongitude()); mxLng=Math.max(mxLng,p.getLongitude());
        }
        double pLat=Math.max((mxLat-mnLat)*0.25, 0.006);
        double pLng=Math.max((mxLng-mnLng)*0.25, 0.006);
        final double fMxLat=mxLat, fMnLat=mnLat, fMxLng=mxLng, fMnLng=mnLng;
        final double fPLat=pLat, fPLng=pLng;
        map.post(() -> {
            try {
                map.zoomToBoundingBox(
                        new BoundingBox(fMxLat+fPLat, fMxLng+fPLng, fMnLat-fPLat, fMnLng-fPLng),
                        true, 100);
            } catch (Exception ignored) {}
        });
    }

    // =========================================================================
    //  OSRM
    // =========================================================================
    private void cargarParadasRuta() {
        if (rutaId == 0) { map.postDelayed(this::iniciarTrazadoRuta, 800); return; }
        ConexionApi.getInstance(this).getArrayNoCache(Constantes.paradasPorRuta((long)rutaId),
                arr -> {
                    try {
                        paradasRuta.clear();
                        ArrayList<String> nombres = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject p = arr.getJSONObject(i);
                            paradasRuta.add(p);
                            nombres.add(p.optString("nombre","Parada "+(i+1)));
                        }
                        rvHistorialParadas.setAdapter(new ParadaAdapter(nombres, origenActual, destinoActual));
                        cardHistorial.setVisibility(nombres.isEmpty() ? View.GONE : View.VISIBLE);
                        if ((coordsOrigenInvalidas || coordsDestinoInvalidas) && arr.length() >= 2)
                            recuperarCoordsDesdeParadas(arr);
                    } catch (Exception e) { Log.e(TAG,"Error paradas",e); }
                    map.postDelayed(this::iniciarTrazadoRuta, 800);
                },
                error -> { cardHistorial.setVisibility(View.GONE); map.postDelayed(this::iniciarTrazadoRuta,800); }
        );
    }

    private void recuperarCoordsDesdeParadas(JSONArray arr) {
        try {
            JSONObject pOrig=null, pDest=null; int maxOrden=-1;
            for (int i=0;i<arr.length();i++) {
                JSONObject p=arr.optJSONObject(i); if(p==null) continue;
                int orden=p.optInt("orden",-1); String tipo=p.optString("tipo","").toUpperCase();
                double pLat=p.optDouble("lat",Double.NaN); if(Double.isNaN(pLat)||pLat==0) continue;
                if((orden==0||"SUBIDA".equals(tipo))&&pOrig==null) pOrig=p;
                if("BAJADA".equals(tipo)) pDest=p;
                else if(orden>maxOrden){maxOrden=orden; if(pOrig==null||orden!=0) pDest=p;}
            }
            if(pOrig==null) pOrig=arr.optJSONObject(0);
            if(pDest==null||pDest==pOrig) pDest=arr.optJSONObject(arr.length()-1);
            if(pOrig!=null&&coordsOrigenInvalidas){double lo=pOrig.optDouble("lat",Double.NaN),ln=pOrig.optDouble("lng",Double.NaN);if(!Double.isNaN(lo)&&lo!=0){latOrigen=lo;lngOrigen=ln;gpOrigen=new GeoPoint(latOrigen,lngOrigen);coordsOrigenInvalidas=false;String nom=pOrig.optString("nombre","").trim();if(!nom.isEmpty()&&!nom.equals("null")&&origenActual.equals("Origen"))origenActual=nom;}}
            if(pDest!=null&&pDest!=pOrig&&coordsDestinoInvalidas){double ld=pDest.optDouble("lat",Double.NaN),lg=pDest.optDouble("lng",Double.NaN);if(!Double.isNaN(ld)&&ld!=0&&!sonIguales(latOrigen,lngOrigen,ld,lg)){latDestino=ld;lngDestino=lg;gpDestino=new GeoPoint(latDestino,lngDestino);coordsDestinoInvalidas=false;String nom=pDest.optString("nombre","").trim();if(!nom.isEmpty()&&!nom.equals("null")&&destinoActual.equals("Destino"))destinoActual=nom;}}
            if(txtRuta!=null) runOnUiThread(()->txtRuta.setText("📍 "+origenActual+" → "+destinoActual));
        } catch(Exception e){Log.e(TAG,"Error recuperando coords",e);}
    }

    private void iniciarTrazadoRuta() {
        if (!geojsonRuta.isEmpty()) {
            ArrayList<GeoPoint> pts = parsearGeoJsonString(geojsonRuta);
            if (pts != null && pts.size() >= 2) {
                todasLasRutas.clear(); listaRouteOptions.clear();
                todasLasRutas.add(pts); indiceRutaActiva = 0;
                rutaActiva = java.util.Collections.unmodifiableList(new ArrayList<>(pts));
                runOnUiThread(() -> { generarParadasDinamicas(); renderizarMapa(); });
                return;
            }
        }
        if (coordsOrigenInvalidas || coordsDestinoInvalidas) {
            new Thread(() -> {
                if (coordsOrigenInvalidas) { double[] c=geocodificarTexto(origenActual); if(c!=null){latOrigen=c[0];lngOrigen=c[1];coordsOrigenInvalidas=false;gpOrigen=new GeoPoint(latOrigen,lngOrigen);} }
                if (coordsDestinoInvalidas||sonIguales(latOrigen,lngOrigen,latDestino,lngDestino)) { double[] c=geocodificarTexto(destinoActual); if(c!=null){latDestino=c[0];lngDestino=c[1];coordsDestinoInvalidas=false;gpDestino=new GeoPoint(latDestino,lngDestino);} }
                runOnUiThread(this::pedirRutaPrincipal);
            }).start();
        } else {
            pedirRutaPrincipal();
        }
    }

    private void pedirRutaPrincipal() {
        new Thread(() -> {
            try {
                StringBuilder wpsB = new StringBuilder();
                for (JSONObject p : paradasRuta) {
                    double pLat=p.optDouble("lat",0), pLng=p.optDouble("lng",0);
                    if(pLat!=0) wpsB.append(";").append(pLng).append(",").append(pLat);
                }
                String params = "?overview=full&geometries=geojson&alternatives=true";
                String segmento = lngOrigen+","+latOrigen+wpsB+";"+lngDestino+","+latDestino+params;
                ArrayList<ArrayList<GeoPoint>> rutasEncontradas = new ArrayList<>();
                ArrayList<double[]> metricas = new ArrayList<>();
                try { parsearRutasOSRM(peticionHttp(OSRM_URL+"/route/v1/driving/"+segmento), rutasEncontradas, metricas); } catch(Exception e){Log.w(TAG,"OSRM propio no disponible");}
                if (rutasEncontradas.isEmpty()) { try { parsearRutasOSRM(peticionHttp(OSRM_URL_PUBLIC+"/route/v1/driving/"+segmento), rutasEncontradas, metricas); } catch(Exception e){Log.w(TAG,"OSRM público no disponible");} }
                if (rutasEncontradas.isEmpty()) { runOnUiThread(this::usarLineaRecta); return; }
                todasLasRutas.clear(); listaRouteOptions.clear();
                for (int ri=0;ri<rutasEncontradas.size();ri++) {
                    todasLasRutas.add(rutasEncontradas.get(ri));
                    double[] met=metricas.get(ri); RouteOption ro=new RouteOption(); ro.distanceKm=met[0]; ro.durationMin=met[1]; listaRouteOptions.add(ro);
                }
                final int idx=(indiceRuta>=0&&indiceRuta<todasLasRutas.size())?indiceRuta:0;
                runOnUiThread(() -> seleccionarRuta(idx, listaRouteOptions.get(idx)));
            } catch(Exception e) { Log.e(TAG,"pedirRutaPrincipal error",e); runOnUiThread(this::usarLineaRecta); }
        }).start();
    }

    private void parsearRutasOSRM(String json, ArrayList<ArrayList<GeoPoint>> dest, ArrayList<double[]> metricas) throws Exception {
        if (json==null||json.isEmpty()) return;
        JSONObject obj=new JSONObject(json);
        if(!"Ok".equals(obj.optString("code",""))) return;
        JSONArray routes=obj.optJSONArray("routes"); if(routes==null) return;
        for(int ri=0;ri<routes.length();ri++){
            JSONObject route=routes.getJSONObject(ri);
            JSONArray coords=route.getJSONObject("geometry").getJSONArray("coordinates");
            ArrayList<GeoPoint> pts=new ArrayList<>();
            for(int ci=0;ci<coords.length();ci++){JSONArray pair=coords.getJSONArray(ci);pts.add(new GeoPoint(pair.getDouble(1),pair.getDouble(0)));}
            if(pts.size()<2) continue;
            dest.add(pts); metricas.add(new double[]{route.optDouble("distance",0)/1000.0, route.optDouble("duration",0)/60.0});
        }
    }

    private void usarLineaRecta() {
        if (gpOrigen==null||gpDestino==null) return;
        ArrayList<GeoPoint> linea=new ArrayList<>(); linea.add(gpOrigen); linea.add(gpDestino);
        todasLasRutas.clear(); todasLasRutas.add(linea); listaRouteOptions.clear();
        RouteOption ro=new RouteOption(); ro.distanceKm=distanciaKm; ro.durationMin=duracionMin; listaRouteOptions.add(ro);
        rutaActiva=java.util.Collections.unmodifiableList(new ArrayList<>(linea)); indiceRutaActiva=0;
        generarParadasDinamicas(); renderizarMapa();
    }

    private void pedirRutaConWaypoint() {
        if (gpParada==null) return;
        new Thread(() -> {
            try {
                String url=OSRM_URL+"/route/v1/driving/"+lngOrigen+","+latOrigen+";"+gpParada.getLongitude()+","+gpParada.getLatitude()+";"+lngDestino+","+latDestino+"?overview=full&geometries=geojson";
                String json=peticionHttp(url);
                JSONObject obj=new JSONObject(json);
                if(!"Ok".equals(obj.optString("code"))) return;
                JSONArray coords=obj.getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
                ArrayList<GeoPoint> pts=new ArrayList<>();
                for(int i=0;i<coords.length();i++){JSONArray c=coords.getJSONArray(i);pts.add(new GeoPoint(c.getDouble(1),c.getDouble(0)));}
                if(pts.size()>=2){puntosRutaWaypoint=pts; runOnUiThread(this::renderizarMapa);}
            } catch(Exception e){Log.w(TAG,"Ruta waypoint falló",e); fallbackOsrmWaypoint();}
        }).start();
    }

    private void fallbackOsrmWaypoint() {
        if(gpOrigen==null||gpDestino==null) return;
        ArrayList<GeoPoint> linea=new ArrayList<>(); linea.add(gpOrigen); if(gpParada!=null) linea.add(gpParada); linea.add(gpDestino);
        puntosRutaWaypoint=linea; runOnUiThread(this::renderizarMapa);
    }

    // =========================================================================
    //  MI RESERVA — pasajero
    // =========================================================================
    private void cargarMiReservaPasajero() {
        if (esConductor || cardMiReserva == null) return;
        int miId = session.getIdUsuario();

        Log.d(TAG, "[MiReserva] Buscando reserva del pasajero " + miId + " en viaje " + viajeId);

        ConexionApi.getInstance(this).getObjectNoCache(Constantes.viajePorId((long) viajeId),
                viajeObj -> {
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    Log.d(TAG, "[MiReserva] usuarios[] len=" + (usuarios != null ? usuarios.length() : "null"));

                    if (usuarios != null && usuarios.length() > 0) {
                        for (int i = 0; i < usuarios.length(); i++) {
                            JSONObject u = usuarios.optJSONObject(i);
                            if (u == null) continue;

                            int idU = -1;
                            JSONObject usuObj = u.optJSONObject("usuario");
                            if (usuObj != null) {
                                for (String k : new String[]{"idUsuarios","id","idUsuario"}) {
                                    int id = usuObj.optInt(k, -1); if (id > 0) { idU = id; break; }
                                }
                            }
                            if (idU <= 0) {
                                for (String k : new String[]{"idUsuarios","idUsuario","idPasajero"}) {
                                    int id = u.optInt(k, -1); if (id > 0) { idU = id; break; }
                                }
                            }

                            Log.d(TAG, "[MiReserva] usuarios[" + i + "] idU=" + idU + " miId=" + miId);

                            if (idU != miId) continue;

                            final String est = u.optString("estado", "CONFIRMADA").toUpperCase();
                            final int    idR = u.optInt("idUsuarioViaje", u.optInt("idReserva", u.optInt("id", -1)));

                            double latP = u.optDouble("latBajada",  u.optDouble("latParada",  0));
                            double lngP = u.optDouble("lngBajada",  u.optDouble("lngParada",  0));
                            String nomP = u.optString("nombreParadaBajada", u.optString("nombreParada", ""));

                            if (latP == 0 || lngP == 0) {
                                JSONObject paradaObj = u.optJSONObject("parada");
                                if (paradaObj != null) {
                                    latP = paradaObj.optDouble("lat", paradaObj.optDouble("latitud", 0));
                                    lngP = paradaObj.optDouble("lng", paradaObj.optDouble("longitud", 0));
                                    if (nomP.isEmpty()) nomP = paradaObj.optString("nombre", "");
                                }
                            }

                            final double fLatP = latP;
                            final double fLngP = lngP;
                            final String fNomP = nomP.isEmpty() ? destinoActual : nomP;
                            final int    fIdR  = idR;
                            final JSONObject uFinal = u;

                            Log.d(TAG, "[MiReserva] ✅ Encontrada: estado=" + est
                                    + " parada=(" + latP + "," + lngP + ") nombre=" + fNomP);

                            runOnUiThread(() -> {
                                yaReservo       = true;
                                idReservaActual = fIdR;
                                estadoReserva   = est;
                                if (fLatP != 0) {
                                    gpParada     = new GeoPoint(fLatP, fLngP);
                                    nombreParada = fNomP;
                                }
                                mostrarCardMiReserva(construirReservaJson(uFinal, est, fIdR, fLatP, fLngP, fNomP));
                                actualizarBotonPasajero();
                                actualizarChipsCupos(cuposTotales, cuposDisponibles);
                                if (EST_RECOGIDO.equals(est) || EST_COMPLETADO.equals(est))
                                    pedirRutaConWaypoint();
                                else
                                    renderizarMapa();
                            });
                            return;
                        }
                        Log.d(TAG, "[MiReserva] Mi id no está en usuarios[], fallback a MIS_RESERVAS");
                        cargarMiReservaFallback(miId);
                    } else {
                        Log.d(TAG, "[MiReserva] usuarios[] vacío, fallback a MIS_RESERVAS");
                        cargarMiReservaFallback(miId);
                    }
                },
                err -> {
                    Log.e(TAG, "[MiReserva] Error viajePorId, fallback a MIS_RESERVAS");
                    cargarMiReservaFallback(miId);
                }
        );
    }

    private JSONObject construirReservaJson(JSONObject u, String estado, int idR,
                                            double latP, double lngP, String nomP) {
        JSONObject r = new JSONObject();
        try {
            r.put("estado",         estado);
            r.put("idReserva",      idR);
            r.put("latParada",      latP);
            r.put("lngParada",      lngP);
            r.put("nombreParada",   nomP);
            r.put("numeroAsientos", u.optInt("numeroAsientos", u.optInt("asientos", 1)));
            r.put("asientos",       u.optInt("numeroAsientos", u.optInt("asientos", 1)));
            r.put("precio",         u.optDouble("precio", precioViaje));
            r.put("codigoReserva",  u.optString("codigoReserva", u.optString("codigo", "")));
        } catch (Exception e) {
            Log.e(TAG, "construirReservaJson: " + e.getMessage());
        }
        return r;
    }

    private void cargarMiReservaFallback(int miId) {
        ConexionApi.getInstance(this).getArray(Constantes.MIS_RESERVAS,
                response -> {
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject res = response.optJSONObject(i);
                        if (res == null) continue;
                        int idVR = res.optInt("idViaje", 0);
                        if (idVR == 0) {
                            JSONObject vo = res.optJSONObject("viaje");
                            if (vo != null) idVR = vo.optInt("idViaje", vo.optInt("id", 0));
                        }
                        if (idVR != viajeId) continue;

                        String est = res.optString("estado","").toUpperCase();
                        int    idR = res.optInt("idReserva", res.optInt("id", -1));
                        double latP = res.optDouble("latParada", res.optDouble("latBajada", 0));
                        double lngP = res.optDouble("lngParada", res.optDouble("lngBajada", 0));
                        String nomP = res.optString("nombreParada",
                                res.optString("nombreParadaBajada", destinoActual));

                        Log.d(TAG, "[MiReserva fallback] ✅ Encontrada: estado=" + est
                                + " parada=(" + latP + "," + lngP + ")");

                        final double fLatP = latP;
                        final double fLngP = lngP;
                        final String fNomP = nomP;
                        final String fEst  = est;
                        final JSONObject mr = res;

                        runOnUiThread(() -> {
                            yaReservo       = true;
                            idReservaActual = idR;
                            estadoReserva   = fEst;
                            if (fLatP != 0) {
                                gpParada     = new GeoPoint(fLatP, fLngP);
                                nombreParada = fNomP;
                            }
                            mostrarCardMiReserva(mr);
                            actualizarBotonPasajero();
                            actualizarChipsCupos(cuposTotales, cuposDisponibles);
                            if (EST_RECOGIDO.equals(fEst) || EST_COMPLETADO.equals(fEst))
                                pedirRutaConWaypoint();
                            else
                                renderizarMapa();
                        });
                        return;
                    }
                    runOnUiThread(() -> {
                        if (cardMiReserva != null) cardMiReserva.setVisibility(View.GONE);
                        actualizarBotonPasajero();
                        renderizarMapa();
                    });
                },
                error -> {
                    Log.e(TAG, "[MiReserva fallback] Error MIS_RESERVAS");
                    runOnUiThread(this::renderizarMapa);
                }
        );
    }

    private void mostrarCardMiReserva(JSONObject reserva) {
        if (cardMiReserva == null || txtMiReservaInfo == null) return;
        String estado = reserva.optString("estado","").toUpperCase();
        if (EST_CANCELADO.equals(estado)) { cardMiReserva.setVisibility(View.GONE); yaReservo=false; return; }

        String np  = reserva.optString("nombreParada", destinoActual);
        int    asi = reserva.optInt("numeroAsientos", reserva.optInt("asientos", 1));
        String cod = reserva.optString("codigoReserva", reserva.optString("codigo",""));
        double pre = reserva.optDouble("precio", precioViaje);

        String nc;
        if (!nombreConductorViaje.isEmpty() && !nombreConductorViaje.startsWith("Conductor")) {
            nc = nombreConductorViaje;
            if (idConductorViaje > 0) nc += "  (ID: " + idConductorViaje + ")";
        } else if (idConductorViaje > 0) {
            nc = "Conductor #" + idConductorViaje;
            if (!nombreConductorViaje.isEmpty()) nc = nombreConductorViaje;
            if (nombreConductorViaje.isEmpty() || nombreConductorViaje.startsWith("Conductor #")) {
                cargarConductorDelViaje();
            }
        } else {
            nc = "Cargando...";
            cargarConductorDelViaje();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🚗 Conductor: ").append(nc).append(" ");
        sb.append("💺 Asientos: ").append(asi).append(" ");
        sb.append("🚏 Bajarás en: ").append(np).append(" ");
        sb.append(pre > 0 ? "💰 $"+String.format(Locale.getDefault(),"%,.0f",pre)+" COP" : "💰 Precio: no definido");
        if (!cod.isEmpty()) sb.append("📋 Código: ").append(cod);

        String etiqueta; int bgColor;
        switch (estado) {
            case EST_ESPERANDO_RECOGIDA: etiqueta="⏳ Esperando recogida"; bgColor=Color.parseColor("#FFF9C4"); break;
            case EST_RECOGIDO:           etiqueta="🚗 ¡Ya te recogieron!"; bgColor=Color.parseColor("#E3F2FD"); break;
            case EST_COMPLETADO:         etiqueta="🏁 Viaje completado";   bgColor=Color.parseColor("#E8F5E9"); break;
            default:                     etiqueta="✅ Reserva activa";      bgColor=Color.parseColor("#E8F5E9"); break;
        }
        sb.append("   ").append(etiqueta);
        txtMiReservaInfo.setText(sb.toString());
        cardMiReserva.setCardBackgroundColor(bgColor);
        cardMiReserva.setVisibility(View.VISIBLE);
    }

    // =========================================================================
    //  RESERVAS — FLUJO COMPLETO
    // =========================================================================
    private void publicarParadaYReservar(ParadaDinamica pd) {
        loaderDetalle.setVisibility(View.VISIBLE);
        if (pd.idParadaBD > 0) { hacerReserva(pd); return; }
        JSONObject body = new JSONObject();
        try {
            body.put("nombre", pd.nombre); body.put("lat", pd.lat); body.put("lng", pd.lng);
            if (rutaId > 0) body.put("idRuta", rutaId);
            body.put("orden", paradasDin.indexOf(pd) + 1); body.put("kmAcumulado", 0); body.put("tipo","AMBAS");
        } catch (JSONException e) { hacerReserva(pd); return; }
        ConexionApi.getInstance(this).post(Constantes.PARADAS, body,
                response -> { pd.idParadaBD = response.optInt("idParada", response.optInt("id", 0)); hacerReserva(pd); },
                error   -> hacerReserva(pd));
    }

    private void hacerReserva(ParadaDinamica pd) {
        int idUsuario = session.getIdUsuario();
        if (idUsuario <= 0 || viajeId <= 0 || cuposDisponibles <= 0
                || !ESTADOS_RESERVABLES.contains(estadoViaje)) {
            loaderDetalle.setVisibility(View.GONE);
            Toast.makeText(this, "No se puede reservar en este momento", Toast.LENGTH_LONG).show();
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("latSubida",          latOrigen);   body.put("lngSubida",          lngOrigen);
            body.put("nombreParadaSubida", origenActual);body.put("latOrigen",           latOrigen);
            body.put("lngOrigen",          lngOrigen);   body.put("latInicio",           latOrigen);
            body.put("lngInicio",          lngOrigen);   body.put("nombreParadaInicio",  origenActual);
            body.put("origenLat",          latOrigen);   body.put("origenLng",           lngOrigen);
            body.put("latBajada",          pd.lat);      body.put("lngBajada",           pd.lng);
            body.put("nombreParadaBajada", pd.nombre);   body.put("latParada",           pd.lat);
            body.put("lngParada",          pd.lng);      body.put("nombreParada",        pd.nombre);
            body.put("latDestino",         pd.lat);      body.put("lngDestino",          pd.lng);
            if (pd.idParadaBD > 0) { body.put("idParadaBajada", pd.idParadaBD); body.put("idParadaFin", pd.idParadaBD); body.put("idParada", pd.idParadaBD); }
            body.put("idUsuarios",         idUsuario);   body.put("idViajes",            viajeId);
            body.put("asientosReservados", 1);           body.put("precioFinal",         precioViaje > 0 ? precioViaje : 0);
            body.put("estado",             "CONFIRMADA");body.put("idUsuario",           idUsuario);
            body.put("idViaje",            viajeId);     body.put("asientos",            1);
            body.put("precio",             precioViaje > 0 ? precioViaje : 0);
        } catch (JSONException e) { loaderDetalle.setVisibility(View.GONE); return; }

        ConexionApi.getInstance(this).post(Constantes.RESERVAS, body,
                response -> {
                    loaderDetalle.setVisibility(View.GONE);
                    idReservaActual = response.optInt("idUsuarioViaje", response.optInt("idReserva", response.optInt("id", response.optInt("reservaId", -1))));
                    estadoReserva   = response.optString("estado", EST_CONFIRMADA).toUpperCase();
                    yaReservo       = true; gpParada = pd.toGeoPoint(); nombreParada = pd.nombre;
                    runOnUiThread(() -> { cuposDisponibles = Math.max(0, cuposDisponibles-1); actualizarChipsCupos(cuposTotales,cuposDisponibles); actualizarBotonPasajero(); actualizarBotonChat(); renderizarMapa(); mostrarCardMiReserva(response); });
                    cargarMiReservaPasajero();
                },
                error -> { loaderDetalle.setVisibility(View.GONE); manejarErrorReserva(error); }
        );
    }

    private void manejarErrorReserva(VolleyError error) {
        String mensaje = "❌ Error al reservar."; int statusCode = -1;
        if (error != null && error.networkResponse != null) {
            statusCode = error.networkResponse.statusCode;
            String bodyError = "";
            try { bodyError = new String(error.networkResponse.data, "UTF-8"); } catch (Exception ignored) {}
            try { JSONObject errJson=new JSONObject(bodyError); String bm=errJson.optString("message",errJson.optString("error",errJson.optString("mensaje",""))); if(!bm.isEmpty()&&!bm.equals("null")) mensaje="HTTP "+statusCode+": "+bm; else mensaje="HTTP "+statusCode+": "+bodyError.substring(0,Math.min(bodyError.length(),200)); } catch(Exception e){ mensaje="HTTP "+statusCode+": "+bodyError.substring(0,Math.min(bodyError.length(),200)); }
            if (statusCode==409) { yaReservo=true; runOnUiThread(()->{ actualizarBotonPasajero(); cargarMiReservaPasajero(); }); Toast.makeText(this,"⚠️ Ya tienes una reserva en este viaje.",Toast.LENGTH_LONG).show(); return; }
            if (statusCode==401) mensaje="❌ Sesión expirada.";
        } else if (error instanceof com.android.volley.NoConnectionError) { mensaje="❌ Sin conexión."; }
        else if (error instanceof com.android.volley.TimeoutError) { mensaje="❌ El servidor tardó mucho."; }
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
    }

    private void cancelarMiReserva() {
        if (idReservaActual <= 0) { Toast.makeText(this,"No tienes una reserva activa.",Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("Cancelar reserva").setMessage("¿Estás seguro?")
                .setPositiveButton("Sí, cancelar",(d,w)->{
                    loaderDetalle.setVisibility(View.VISIBLE);
                    ConexionApi.getInstance(this).post(Constantes.reservaCancelar((long)idReservaActual),null,
                            response->{ loaderDetalle.setVisibility(View.GONE); yaReservo=false; idReservaActual=-1; estadoReserva=EST_CANCELADO; gpParada=null; nombreParada=""; Toast.makeText(this,"✅ Reserva cancelada.",Toast.LENGTH_LONG).show(); runOnUiThread(()->{cuposDisponibles=Math.min(cuposTotales,cuposDisponibles+1);actualizarChipsCupos(cuposTotales,cuposDisponibles);actualizarBotonPasajero();if(cardMiReserva!=null)cardMiReserva.setVisibility(View.GONE);renderizarMapa();});},
                            error->{ loaderDetalle.setVisibility(View.GONE); Toast.makeText(this,"Error al cancelar.",Toast.LENGTH_LONG).show();});
                }).setNegativeButton("No",null).show();
    }

    private void cambiarParada(ParadaDinamica pd) {
        loaderDetalle.setVisibility(View.VISIBLE);
        if (pd.idParadaBD > 0) { actualizarReservaParada(pd); return; }
        try { JSONObject body=new JSONObject(); body.put("idRuta",rutaId); body.put("nombre",pd.nombre); body.put("lat",pd.lat); body.put("lng",pd.lng); body.put("tipo","AMBAS"); ConexionApi.getInstance(this).post(Constantes.PARADAS,body,r->{pd.idParadaBD=r.optInt("idParada",r.optInt("id",0));actualizarReservaParada(pd);},e->actualizarReservaParada(pd)); }
        catch(Exception e) { actualizarReservaParada(pd); }
    }

    private void actualizarReservaParada(ParadaDinamica pd) {
        if (idReservaActual<=0){loaderDetalle.setVisibility(View.GONE);gpParada=pd.toGeoPoint();nombreParada=pd.nombre;renderizarMapa();actualizarBotonPasajero();return;}
        try { JSONObject body=new JSONObject(); body.put("nombreParada",pd.nombre); body.put("latParada",pd.lat); body.put("lngParada",pd.lng); if(pd.idParadaBD>0)body.put("idParada",pd.idParadaBD); ConexionApi.getInstance(this).put(Constantes.RESERVAS+"/"+idReservaActual,body,r->{loaderDetalle.setVisibility(View.GONE);gpParada=pd.toGeoPoint();nombreParada=pd.nombre;renderizarMapa();actualizarBotonPasajero();Toast.makeText(this,"✅ Parada cambiada.",Toast.LENGTH_SHORT).show();cargarMiReservaPasajero();},e->{loaderDetalle.setVisibility(View.GONE);gpParada=pd.toGeoPoint();nombreParada=pd.nombre;renderizarMapa();}); }
        catch(Exception e){loaderDetalle.setVisibility(View.GONE);}
    }

    // =========================================================================
    //  RESERVAS CONDUCTOR
    // =========================================================================
    private void cargarReservasConductor() {
        if (!esConductor || cardPasajeros == null) return;

        Log.d(TAG, "[ReservasConductor] Cargando desde viajePorId → campo usuarios");

        ConexionApi.getInstance(this).getObjectNoCache(Constantes.viajePorId((long) viajeId),
                viajeObj -> {
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    Log.d(TAG, "[ReservasConductor] usuarios[] len=" +
                            (usuarios != null ? usuarios.length() : "null"));

                    if (usuarios != null && usuarios.length() > 0) {
                        JSONArray reservasNorm = normalizarUsuariosDeViaje(usuarios);
                        procesarReservasConductor(reservasNorm);
                    } else {
                        Log.d(TAG, "[ReservasConductor] usuarios vacío, intentando /api/reservas/viaje/");
                        String urlReservas = Constantes.BASE_URL + "/api/reservas/viaje/" + viajeId;
                        ConexionApi.getInstance(this).getArrayNoCache(urlReservas,
                                reservas -> {
                                    Log.d(TAG, "[ReservasConductor] reservas[] len=" +
                                            (reservas != null ? reservas.length() : "null"));
                                    if (reservas != null && reservas.length() > 0) {
                                        procesarReservasConductor(reservas);
                                    } else {
                                        runOnUiThread(this::mostrarSinPasajeros);
                                    }
                                },
                                err2 -> {
                                    Log.e(TAG, "[ReservasConductor] Ambos endpoints fallaron");
                                    runOnUiThread(this::mostrarSinPasajeros);
                                }
                        );
                    }
                },
                err -> {
                    Log.e(TAG, "[ReservasConductor] Error en viajePorId: " + err.toString());
                    String urlReservas = Constantes.BASE_URL + "/api/reservas/viaje/" + viajeId;
                    ConexionApi.getInstance(this).getArrayNoCache(urlReservas,
                            reservas -> {
                                if (reservas != null && reservas.length() > 0) {
                                    procesarReservasConductor(reservas);
                                } else {
                                    runOnUiThread(this::mostrarSinPasajeros);
                                }
                            },
                            err2 -> runOnUiThread(this::mostrarSinPasajeros)
                    );
                }
        );
    }

    private JSONArray normalizarUsuariosDeViaje(JSONArray usuarios) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < usuarios.length(); i++) {
            try {
                JSONObject u = usuarios.getJSONObject(i);
                JSONObject reserva = new JSONObject();

                reserva.put("estado",         u.optString("estado", "CONFIRMADA"));

                int asi = u.optInt("numeroAsientos", u.optInt("asientos", 1));
                reserva.put("numeroAsientos", asi);
                reserva.put("asientos",       asi);

                int idRes = u.optInt("idUsuarioViaje", u.optInt("idReserva", u.optInt("id", -1)));
                if (idRes > 0) reserva.put("idReserva", idRes);

                double latP = 0, lngP = 0;
                String nomP = "";

                latP = u.optDouble("latBajada",  0);
                lngP = u.optDouble("lngBajada",  0);
                nomP = u.optString("nombreParadaBajada", "");

                if (latP == 0) latP = u.optDouble("latParada",  0);
                if (lngP == 0) lngP = u.optDouble("lngParada",  0);
                if (nomP.isEmpty()) nomP = u.optString("nombreParada", "");

                if (latP == 0 || lngP == 0) {
                    JSONObject po = u.optJSONObject("parada");
                    if (po == null) po = u.optJSONObject("paradaBajada");
                    if (po != null) {
                        if (latP == 0) latP = po.optDouble("lat",  po.optDouble("latitud",  0));
                        if (lngP == 0) lngP = po.optDouble("lng",  po.optDouble("longitud", 0));
                        if (nomP.isEmpty()) nomP = po.optString("nombre", "");
                    }
                }

                if (latP == 0 || lngP == 0) {
                    for (String k : new String[]{"paradaSubida","paradaFin","paradaDestino"}) {
                        JSONObject po2 = u.optJSONObject(k);
                        if (po2 != null) {
                            double tL = po2.optDouble("lat", po2.optDouble("latitud", 0));
                            double tG = po2.optDouble("lng", po2.optDouble("longitud", 0));
                            if (tL != 0) { latP = tL; lngP = tG;
                                if (nomP.isEmpty()) nomP = po2.optString("nombre","");
                                break; }
                        }
                    }
                }

                Log.d(TAG, "  Normalizar usuario[" + i + "]: parada=(" + latP + "," + lngP + ") nombre=" + nomP);

                if (latP != 0) { reserva.put("latParada",   latP); reserva.put("lngParada", lngP); }
                if (!nomP.isEmpty()) reserva.put("nombreParada", nomP);

                JSONObject usuarioObj = u.optJSONObject("usuario");
                if (usuarioObj != null) {
                    int idU = usuarioObj.optInt("idUsuarios", usuarioObj.optInt("id", 0));
                    if (idU > 0) usuarioObj.put("id", idU);
                    reserva.put("pasajero", usuarioObj);
                    reserva.put("usuario",  usuarioObj);
                } else {
                    int idU = u.optInt("idUsuarios", u.optInt("idPasajero", 0));
                    JSONObject fallback = new JSONObject();
                    fallback.put("id",         idU);
                    fallback.put("idUsuarios", idU);
                    fallback.put("nombre",     u.optString("nombrePasajero", u.optString("nombre", "Pasajero")));
                    reserva.put("pasajero", fallback);
                    reserva.put("usuario",  fallback);
                }

                result.put(reserva);
            } catch (Exception e) {
                Log.e(TAG, "normalizarUsuariosDeViaje[" + i + "]: " + e.getMessage());
            }
        }
        Log.d(TAG, "normalizarUsuariosDeViaje: " + result.length() + " reservas normalizadas");
        return result;
    }

    private void procesarReservasConductor(JSONArray reservas) {
        runOnUiThread(() -> {
            try {
                paradasPasajeros.clear();
                nombresPasajerosParadas.clear();
                layoutListaPasajeros.removeAllViews();

                if (reservas == null || reservas.length() == 0) {
                    mostrarSinPasajeros();
                    return;
                }

                int     total        = 0;
                boolean hayEsperando = false;
                int     colorIdx     = 0;

                for (int i = 0; i < reservas.length(); i++) {
                    JSONObject res = reservas.getJSONObject(i);
                    String est = res.optString("estado", "").toUpperCase().trim();

                    Log.d(TAG, "  Reserva[" + i + "] estado='" + est + "'");

                    if (ESTADOS_CANCELADOS.contains(est)) continue;

                    String np = "";
                    JSONObject po = null;
                    for (String k : new String[]{"pasajero","usuario","user","passenger"}) {
                        JSONObject c = res.optJSONObject(k);
                        if (c != null) { po = c; break; }
                    }
                    if (po != null) {
                        for (String k : new String[]{"nombre","nombreCompleto","name","nombres"}) {
                            String n = po.optString(k, "");
                            if (!n.isEmpty() && !n.equals("null")) { np = n; break; }
                        }
                        if (np.isEmpty()) {
                            String n = po.optString("nombres",""), a = po.optString("apellidos","");
                            if (!n.isEmpty() || !a.isEmpty()) np = (n + " " + a).trim();
                        }
                    }
                    if (np.isEmpty())
                        np = res.optString("nombrePasajero", "Pasajero " + (colorIdx + 1));

                    int asi = res.optInt("numeroAsientos", res.optInt("asientos", 1));
                    total += asi;

                    String par   = res.optString("nombreParada", "");
                    int    idRes = res.optInt("idReserva", res.optInt("id", -1));
                    double latP  = res.optDouble("latParada",  0);
                    double lngP  = res.optDouble("lngParada",  0);

                    if (latP == 0 || lngP == 0) {
                        JSONObject paradaObj = res.optJSONObject("parada");
                        if (paradaObj != null) {
                            latP = paradaObj.optDouble("lat", paradaObj.optDouble("latitud", 0));
                            lngP = paradaObj.optDouble("lng", paradaObj.optDouble("longitud", 0));
                            if (par.isEmpty()) par = paradaObj.optString("nombre", "");
                        }
                    }

                    boolean tieneParada = latP != 0 && lngP != 0;
                    int     pasColor    = tieneParada
                            ? COLORES_PASAJEROS[colorIdx % COLORES_PASAJEROS.length]
                            : Color.parseColor("#607D8B");
                    String  pasColorHex = tieneParada
                            ? COLORES_PASAJEROS_HEX[colorIdx % COLORES_PASAJEROS_HEX.length]
                            : "#607D8B";

                    if (tieneParada && par.isEmpty()) {
                        par = String.format("%.4f, %.4f", latP, lngP);
                        final double fLat = latP, fLng = lngP;
                        final int    fIdx  = colorIdx;
                        new Thread(() -> {
                            try {
                                String urlGeo = "https://nominatim.openstreetmap.org/reverse?lat="
                                        + fLat + "&lon=" + fLng
                                        + "&format=json&zoom=16&accept-language=es";
                                String resp = peticionHttp(urlGeo);
                                if (resp != null && !resp.isEmpty()) {
                                    JSONObject geo = new JSONObject(resp);
                                    String nomGeo = extraerNombreNominatim(geo.optJSONObject("address"), geo);
                                    if (!nomGeo.isEmpty() && !nomGeo.equals("Parada")) {
                                        runOnUiThread(() -> {
                                            if (fIdx < nombresPasajerosParadas.size()) {
                                                String actual = nombresPasajerosParadas.get(fIdx);
                                                String parteNombre = actual.contains(" → ") ? actual.split(" → ")[0] : actual;
                                                nombresPasajerosParadas.set(fIdx, parteNombre + " → " + nomGeo);
                                            }
                                        });
                                    }
                                }
                            } catch (Exception ignored) {}
                        }).start();
                    }

                    if (tieneParada) {
                        paradasPasajeros.add(new GeoPoint(latP, lngP));
                        nombresPasajerosParadas.add(np + (par.isEmpty() ? "" : " → " + par));
                    }

                    agregarFilaPasajeroColoreado(
                            np, asi,
                            par.isEmpty()
                                    ? (tieneParada ? "📍 Parada marcada" : "Sin parada asignada")
                                    : par,
                            est, idRes,
                            tieneParada ? "P" + (colorIdx + 1) : "?",
                            pasColor, pasColorHex, tieneParada
                    );

                    if (tieneParada) colorIdx++;

                    if (EST_ESPERANDO_RECOGIDA.equals(est)) {
                        hayEsperando = true;
                        if (gpParada == null && tieneParada) {
                            gpParada     = new GeoPoint(latP, lngP);
                            nombreParada = par.isEmpty() ? np : par;
                        }
                    }
                }

                if (layoutListaPasajeros.getChildCount() == 0) {
                    mostrarSinPasajeros();
                    return;
                }

                txtTotalPasajeros.setText(
                        layoutListaPasajeros.getChildCount()
                                + " pasajero(s) · " + total + " asiento(s)");
                cardPasajeros.setVisibility(View.VISIBLE);

                if (btnRecoger != null)
                    btnRecoger.setVisibility(hayEsperando ? View.VISIBLE : View.GONE);

                renderizarMapa();

            } catch (Exception e) {
                Log.e(TAG, "Error procesando reservas conductor", e);
                mostrarSinPasajeros();
            }
        });
    }

    private void mostrarSinPasajeros() {
        if (layoutListaPasajeros == null) return;
        layoutListaPasajeros.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText("Sin pasajeros reservados aún");
        tv.setTextColor(Color.parseColor("#546E7A"));
        tv.setTextSize(13f);
        tv.setPadding(0, 8, 0, 8);
        layoutListaPasajeros.addView(tv);
        if (txtTotalPasajeros != null) txtTotalPasajeros.setText("0 pasajeros");
        if (cardPasajeros     != null) cardPasajeros.setVisibility(View.VISIBLE);
    }

    private void agregarFilaPasajeroColoreado(String nombre, int asientos, String parada,
                                              String estado, int idRes, String etiqMarcador,
                                              int colorMarcador, String colorHex,
                                              boolean tieneParada) {
        float d = getResources().getDisplayMetrics().density;
        int p14=(int)(14*d), p10=(int)(10*d), p8=(int)(8*d), p6=(int)(6*d), p4=(int)(4*d);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, p4, 0, p4);
        card.setLayoutParams(lpCard);
        card.setPadding(p14, p10, p14, p10);

        GradientDrawable bgCard = new GradientDrawable();
        bgCard.setShape(GradientDrawable.RECTANGLE);
        bgCard.setCornerRadius(16*d);
        bgCard.setColor(Color.WHITE);
        bgCard.setStroke((int)(2.5f*d), tieneParada ? colorMarcador : Color.parseColor("#CFD8DC"));
        card.setBackground(bgCard);
        card.setElevation(3*d);

        LinearLayout fila1 = new LinearLayout(this);
        fila1.setOrientation(LinearLayout.HORIZONTAL);
        fila1.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView avatar = new TextView(this);
        avatar.setText(nombre.isEmpty() ? "P" : nombre.substring(0,1).toUpperCase());
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(15f);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpAv = new LinearLayout.LayoutParams((int)(36*d), (int)(36*d));
        lpAv.setMargins(0, 0, p10, 0);
        avatar.setLayoutParams(lpAv);
        GradientDrawable bgAv = new GradientDrawable();
        bgAv.setShape(GradientDrawable.OVAL);
        bgAv.setColor(tieneParada ? colorMarcador : Color.parseColor("#90A4AE"));
        avatar.setBackground(bgAv);
        fila1.addView(avatar);

        TextView tvNombre = new TextView(this);
        tvNombre.setText(nombre);
        tvNombre.setTextSize(14.5f);
        tvNombre.setTypeface(null, Typeface.BOLD);
        tvNombre.setTextColor(Color.parseColor("#1A2035"));
        tvNombre.setMaxLines(1);
        tvNombre.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvNombre.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fila1.addView(tvNombre);

        TextView badge = new TextView(this);
        badge.setText(badgeEstado(estado));
        badge.setTextSize(10f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setTextColor(Color.WHITE);
        badge.setPadding(p8, (int)(3*d), p8, (int)(3*d));
        GradientDrawable bgBadge = new GradientDrawable();
        bgBadge.setShape(GradientDrawable.RECTANGLE);
        bgBadge.setCornerRadius(20*d);
        bgBadge.setColor(colorBadgeEstado(estado));
        badge.setBackground(bgBadge);
        fila1.addView(badge);
        card.addView(fila1);

        TextView tvAsientos = new TextView(this);
        tvAsientos.setText("💺 " + asientos + (asientos == 1 ? " asiento" : " asientos"));
        tvAsientos.setTextSize(12f);
        tvAsientos.setTextColor(Color.parseColor("#546E7A"));
        LinearLayout.LayoutParams lpAs = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpAs.topMargin = (int)(5*d);
        tvAsientos.setLayoutParams(lpAs);
        card.addView(tvAsientos);

        LinearLayout filaParada = new LinearLayout(this);
        filaParada.setOrientation(LinearLayout.HORIZONTAL);
        filaParada.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpFP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFP.topMargin = p6;
        filaParada.setLayoutParams(lpFP);

        if (tieneParada) {
            TextView chipNum = new TextView(this);
            chipNum.setText(etiqMarcador);
            chipNum.setTextSize(10f);
            chipNum.setTypeface(null, Typeface.BOLD);
            chipNum.setTextColor(Color.WHITE);
            chipNum.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lpCh = new LinearLayout.LayoutParams((int)(26*d), (int)(26*d));
            lpCh.setMargins(0, 0, p6, 0);
            chipNum.setLayoutParams(lpCh);
            GradientDrawable bgCh = new GradientDrawable();
            bgCh.setShape(GradientDrawable.OVAL);
            bgCh.setColor(colorMarcador);
            chipNum.setBackground(bgCh);
            filaParada.addView(chipNum);

            TextView tvParada = new TextView(this);
            tvParada.setText("🚏 " + parada);
            tvParada.setTextSize(12f);
            tvParada.setTextColor(Color.parseColor(colorHex));
            tvParada.setTypeface(null, Typeface.BOLD);
            tvParada.setMaxLines(2);
            tvParada.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvParada.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvParada.setPadding(p8, p4, p8, p4);
            GradientDrawable bgPar = new GradientDrawable();
            bgPar.setShape(GradientDrawable.RECTANGLE);
            bgPar.setCornerRadius(10*d);
            bgPar.setColor(Color.parseColor("#F0F4F8"));
            bgPar.setStroke((int)(1*d), colorMarcador);
            tvParada.setBackground(bgPar);
            filaParada.addView(tvParada);
        } else {
            TextView tvSinParada = new TextView(this);
            tvSinParada.setText("⚠️  El pasajero aún no ha marcado su parada");
            tvSinParada.setTextSize(11.5f);
            tvSinParada.setTextColor(Color.parseColor("#9E9E9E"));
            tvSinParada.setTypeface(null, Typeface.ITALIC);
            tvSinParada.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            filaParada.addView(tvSinParada);
        }
        card.addView(filaParada);

        if (EST_ESPERANDO_RECOGIDA.equals(estado)) {
            MaterialButton btnR = new MaterialButton(this);
            btnR.setText("✅  Confirmar recogida");
            btnR.setTextSize(13f);
            btnR.setTextColor(Color.WHITE);
            btnR.setCornerRadius((int)(12*d));
            btnR.setBackgroundColor(tieneParada ? colorMarcador : Color.parseColor("#1976D2"));
            LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int)(44*d));
            lpBtn.topMargin = p8;
            btnR.setLayoutParams(lpBtn);
            btnR.setOnClickListener(v -> { idReservaActual = idRes; confirmarRecogida(); });
            card.addView(btnR);
        }

        layoutListaPasajeros.addView(card);
    }

    private String badgeEstado(String e) {
        if (e == null || e.isEmpty()) return "✓ Activa";
        switch (e.toUpperCase()) {
            case "ACTIVA":
            case "CONFIRMADA":
            case "RESERVADO":    return "✓ Confirmada";
            case "PENDIENTE":    return "⏳ Pendiente";
            case "EN_CURSO":
            case "INICIADO":     return "🚗 A bordo";
            case "ESPERANDO_RECOGIDA": return "⏳ Esperando";
            case "RECOGIDO":     return "✅ Recogido";
            case "COMPLETADO":
            case "FINALIZADO":   return "🏁 Completado";
            case "CANCELADO":
            case "CANCELADA":    return "✕ Cancelada";
            default:             return "📋 " + e;
        }
    }

    private int colorBadgeEstado(String e) {
        if (e == null || e.isEmpty()) return Color.parseColor("#2E7D32");
        switch (e.toUpperCase()) {
            case "ACTIVA":
            case "CONFIRMADA":
            case "RESERVADO":    return Color.parseColor("#2E7D32");
            case "PENDIENTE":    return Color.parseColor("#F57F17");
            case "EN_CURSO":
            case "INICIADO":
            case "RECOGIDO":     return Color.parseColor("#00838F");
            case "ESPERANDO_RECOGIDA": return Color.parseColor("#E65100");
            case "CANCELADO":
            case "CANCELADA":    return Color.parseColor("#B71C1C");
            case "COMPLETADO":
            case "FINALIZADO":   return Color.parseColor("#1565C0");
            default:             return Color.parseColor("#607D8B");
        }
    }

    // =========================================================================
    //  CONFIRMAR RECOGIDA
    // =========================================================================
    private void confirmarRecogida() {
        if(idReservaActual<=0){Toast.makeText(this,"Sin reserva activa",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(this).setTitle("Confirmar recogida").setMessage("¿Confirmas que recogiste al pasajero?")
                .setPositiveButton("Sí, lo recogí",(d,w)->ejecutarRecogida()).setNegativeButton("Cancelar",null).show();
    }

    private void ejecutarRecogida() {
        loaderDetalle.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).put(Constantes.RESERVAS+"/"+idReservaActual+"/recoger",null,
                response->{
                    loaderDetalle.setVisibility(View.GONE);
                    estadoReserva = EST_RECOGIDO;
                    Toast.makeText(this,"✅ Pasajero recogido",Toast.LENGTH_SHORT).show();
                    runOnUiThread(()->{
                        if(btnRecoger!=null) btnRecoger.setVisibility(View.GONE);
                        puntosRutaWaypoint.clear();
                        GeoPoint posConductor = gpConductorActual != null ? gpConductorActual : gpOrigen;
                        // Pedir segmento conductor→destino (se dibujará ENCIMA de la ruta base)
                        pedirSegmentoConductorADestino(posConductor);
                        renderizarMapa();
                        cargarReservasConductor();
                    });
                },
                error->{loaderDetalle.setVisibility(View.GONE);Toast.makeText(this,"Error al confirmar recogida",Toast.LENGTH_LONG).show();});
    }

    // =========================================================================
    //  BOTTOM SHEET
    // =========================================================================
    private void mostrarBottomSheetParada() {
        if(paradasDin.isEmpty()) generarParadasDinamicas();
        BottomSheetDialog sheet=new BottomSheetDialog(this,R.style.BottomSheetTheme);
        float dp=getResources().getDisplayMetrics().density;
        int p16=(int)(16*dp),p12=(int)(12*dp),p8=(int)(8*dp),p4=(int)(4*dp),p24=(int)(24*dp);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(p16,p16,p16,p24);
        View tiron=new View(this);LinearLayout.LayoutParams lpT=new LinearLayout.LayoutParams((int)(40*dp),(int)(4*dp));lpT.gravity=android.view.Gravity.CENTER_HORIZONTAL;lpT.bottomMargin=p12;tiron.setLayoutParams(lpT);GradientDrawable tGd=new GradientDrawable();tGd.setShape(GradientDrawable.RECTANGLE);tGd.setCornerRadius(4*dp);tGd.setColor(Color.parseColor("#BDBDBD"));tiron.setBackground(tGd);root.addView(tiron);
        TextView tTitulo=new TextView(this);tTitulo.setText(yaReservo?"🔄 Cambiar parada de bajada":"🚏 ¿Dónde te vas a bajar?");tTitulo.setTextSize(18f);tTitulo.setTypeface(null,Typeface.BOLD);tTitulo.setTextColor(Color.parseColor("#004D40"));LinearLayout.LayoutParams lpTit=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpTit.bottomMargin=p12;tTitulo.setLayoutParams(lpTit);root.addView(tTitulo);
        TextView tvElegida=new TextView(this);if(!nombreParada.isEmpty()){tvElegida.setText("🚏 "+nombreParada);tvElegida.setVisibility(View.VISIBLE);}else tvElegida.setVisibility(View.GONE);tvElegida.setTextSize(13f);tvElegida.setTextColor(Color.WHITE);tvElegida.setTypeface(null,Typeface.BOLD);tvElegida.setPadding(p12,p8,p12,p8);GradientDrawable bgC=new GradientDrawable();bgC.setShape(GradientDrawable.RECTANGLE);bgC.setCornerRadius(20*dp);bgC.setColor(Color.parseColor("#FF9800"));tvElegida.setBackground(bgC);LinearLayout.LayoutParams lpChip=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpChip.bottomMargin=p8;tvElegida.setLayoutParams(lpChip);root.addView(tvElegida);
        android.widget.EditText editBuscar=new android.widget.EditText(this);editBuscar.setHint("✏️  Escribe un barrio o lugar...");editBuscar.setTextSize(14f);editBuscar.setTextColor(Color.parseColor("#212121"));editBuscar.setHintTextColor(Color.parseColor("#9E9E9E"));editBuscar.setSingleLine(true);editBuscar.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);editBuscar.setPadding(p12,p12,p12,p12);GradientDrawable bgB=new GradientDrawable();bgB.setShape(GradientDrawable.RECTANGLE);bgB.setCornerRadius(12*dp);bgB.setColor(Color.parseColor("#F5F5F5"));bgB.setStroke((int)(1.5f*dp),Color.parseColor("#B2DFDB"));editBuscar.setBackground(bgB);LinearLayout.LayoutParams lpEdit=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpEdit.bottomMargin=p8;editBuscar.setLayoutParams(lpEdit);root.addView(editBuscar);
        LinearLayout lista=new LinearLayout(this);lista.setOrientation(LinearLayout.VERTICAL);root.addView(lista);
        final ParadaDinamica[] elegida={null};final MaterialButton[] btnRef={null};
        poblarLista(lista,paradasDin,"",dp,p8,p4,pE->{elegida[0]=pE;tvElegida.setText("🚏 "+pE.nombre);tvElegida.setVisibility(View.VISIBLE);gpParada=pE.toGeoPoint();nombreParada=pE.nombre;renderizarMapa();if(btnRef[0]!=null)habilitarBtn(btnRef[0],pE.nombre);});
        MaterialButton btnC=new MaterialButton(this);btnC.setText(yaReservo?"🔄 CAMBIAR PARADA":"🚏 ELEGIR PARADA");btnC.setTextSize(15f);btnC.setEnabled(false);btnC.setAlpha(0.5f);btnC.setBackgroundColor(Color.parseColor("#B0BEC5"));btnC.setTextColor(Color.WHITE);LinearLayout.LayoutParams lpBtn=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(52*dp));lpBtn.topMargin=p4;btnC.setLayoutParams(lpBtn);btnRef[0]=btnC;
        btnC.setOnClickListener(v->{if(elegida[0]==null){Toast.makeText(this,"Selecciona una parada primero",Toast.LENGTH_SHORT).show();return;}sheet.dismiss();if(yaReservo)cambiarParada(elegida[0]);else publicarParadaYReservar(elegida[0]);});
        root.addView(btnC);
        Handler autoH=new Handler(Looper.getMainLooper());Runnable[] autoR={null};
        editBuscar.addTextChangedListener(new android.text.TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}@Override public void afterTextChanged(android.text.Editable s){}@Override public void onTextChanged(CharSequence s,int st,int b,int c){if(autoR[0]!=null)autoH.removeCallbacks(autoR[0]);String txt=s.toString().trim();autoR[0]=()->{ArrayList<ParadaDinamica> f=new ArrayList<>();for(ParadaDinamica pd:paradasDin)if(txt.isEmpty()||pd.nombre.toLowerCase().contains(txt.toLowerCase()))f.add(pd);runOnUiThread(()->poblarLista(lista,f,txt,dp,p8,p4,pE->{elegida[0]=pE;tvElegida.setText("🚏 "+pE.nombre);tvElegida.setVisibility(View.VISIBLE);gpParada=pE.toGeoPoint();nombreParada=pE.nombre;renderizarMapa();if(btnRef[0]!=null)habilitarBtn(btnRef[0],pE.nombre);}));};autoH.postDelayed(autoR[0],txt.isEmpty()?0:400);}});
        android.widget.ScrollView sv=new android.widget.ScrollView(this);sv.addView(root);sheet.setContentView(sv);sheet.show();
    }

    private void habilitarBtn(MaterialButton btn,String nombre){btn.setEnabled(true);btn.setAlpha(1f);btn.setBackgroundColor(Color.parseColor(yaReservo?"#1565C0":"#00897B"));btn.setText(yaReservo?"🔄 CAMBIAR A: "+nombre:"🚏 RESERVAR EN: "+nombre);}

    // =========================================================================
    //  CUPOS
    // =========================================================================
    private void actualizarChipsCupos(int total,int disponibles){
        if(layoutCupos==null||total>8||total<=0)return;
        runOnUiThread(()->{layoutCupos.removeAllViews();float d=getResources().getDisplayMetrics().density;int s=(int)(32*d),m=(int)(6*d);for(int i=0;i<total;i++){final int idx=i;boolean libre=i<disponibles;boolean esMio=yaReservo&&cupoSeleccionado==i;View circle=new View(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(s,s);lp.setMargins(m,0,m,0);circle.setLayoutParams(lp);GradientDrawable sh=new GradientDrawable();sh.setShape(GradientDrawable.OVAL);if(esMio){sh.setColor(Color.parseColor(COLOR_SEL));sh.setStroke((int)(3*d),Color.parseColor("#E65100"));}else if(libre){sh.setColor(Color.parseColor(COLOR_LIBRE));sh.setStroke((int)(2*d),Color.parseColor("#388E3C"));}else{sh.setColor(Color.parseColor(COLOR_OCUP));sh.setStroke((int)(2*d),Color.parseColor("#C62828"));}circle.setBackground(sh);if(!esConductor){if(esMio){circle.setClickable(true);circle.setFocusable(true);circle.setOnClickListener(v->mostrarBottomSheetParada());}else if(libre&&!yaReservo){circle.setClickable(true);circle.setFocusable(true);circle.setOnClickListener(v->{cupoSeleccionado=idx;mostrarBottomSheetParada();});}}layoutCupos.addView(circle);}});
    }

    // =========================================================================
    //  POLLING
    // =========================================================================
    private void iniciarPolling(){
        if(pollingActivo)return;
        pollingActivo=true;
        pollingRunnable=new Runnable(){@Override public void run(){if(!pollingActivo)return;refrescar();pollingHandler.postDelayed(this,POLLING_MS);}};
        pollingHandler.postDelayed(pollingRunnable,POLLING_MS);
    }

    private void refrescar(){
        ConexionApi.getInstance(this).getObject(Constantes.viajePorId((long)viajeId),
                response->{
                    int nd=response.optInt("cuposDisponibles",cuposDisponibles),nt=response.optInt("cuposTotales",cuposTotales);
                    String ne=response.optString("estado",estadoViaje).trim().toUpperCase();
                    if(nd!=cuposDisponibles||nt!=cuposTotales){cuposDisponibles=nd;cuposTotales=nt;actualizarChipsCupos(cuposTotales,cuposDisponibles);if(esConductor)cargarReservasConductor();runOnUiThread(this::actualizarBotonPasajero);}
                    if(!ne.equals(estadoViaje)){
                        estadoViaje=ne;
                        runOnUiThread(()->txtEstado.setText(etiquetaEstado(estadoViaje)));
                        if(("INICIADO".equals(ne)||"EN_CURSO".equals(ne))&&esConductor){
                            iniciarGPSConductor();
                        }
                        if(ne.equals("INICIADO")||ne.equals("FINALIZADO"))cargarDetalleViaje();
                    }
                    if(!esConductor&&yaReservo&&idReservaActual>0) actualizarEstadoReservaPorPolling();
                },
                error->{}
        );
    }

    private void actualizarEstadoReservaPorPolling() {
        ConexionApi.getInstance(this).getObject(Constantes.RESERVAS + "/" + idReservaActual,
                response -> {
                    String nuevo = response.optString("estado", "").toUpperCase();
                    if (!nuevo.equals(estadoReserva)) {
                        String anterior = estadoReserva;
                        estadoReserva = nuevo;
                        runOnUiThread(() -> {
                            mostrarCardMiReserva(response);
                            if (EST_RECOGIDO.equals(estadoReserva) || EST_COMPLETADO.equals(estadoReserva))
                                pedirRutaConWaypoint();
                            else
                                renderizarMapa();
                            if (EST_COMPLETADO.equals(estadoReserva) && !EST_COMPLETADO.equals(anterior))
                                new Handler(Looper.getMainLooper()).postDelayed(this::dispararCalificacionAlConductor, 1200);
                        });
                    }
                },
                error -> {}
        );
    }

    private void dispararCalificacionAlConductor() {
        if (idConductorViaje <= 0) {
            cargarConductorDelViaje();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (idConductorViaje > 0) dispararCalificacionAlConductor();
            }, 1500);
            return;
        }
        int    idPasajero   = session.getIdUsuario();
        String nomConductor = nombreConductorViaje.isEmpty() ? "el conductor" : nombreConductorViaje;
        new CalificacionesManager(this).verificarCalificacion(viajeId, idPasajero, idConductorViaje,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override public void onDebeCalificar() {
                        CalificacionController.mostrarBottomSheetCalificar(DetalleViajeActivity.this, viajeId, idConductorViaje, nomConductor, idPasajero, false, (p, c) -> Log.d(TAG, "Calificado conductor: " + p + "⭐"));
                    }
                    @Override public void onYaCalifico(int p, String e) {
                        Toast.makeText(DetalleViajeActivity.this, "Ya calificaste al conductor " + e, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // =========================================================================
    //  FINALIZAR + CALIFICACIONES
    // =========================================================================
    private void finalizarViaje() {
        loaderDetalle.setVisibility(View.VISIBLE);
        detenerTodo();
        ConexionApi.getInstance(this).post(Constantes.viajePorId((long) viajeId) + "/pasajeros-bajaron", null,
                r  -> paso2FinalizarYCalificar(),
                err -> paso2FinalizarYCalificar()
        );
    }

    private void paso2FinalizarYCalificar() {
        ConexionApi.getInstance(this).post(Constantes.viajeFinalizar((long) viajeId), null,
                r2 -> {
                    loaderDetalle.setVisibility(View.GONE);
                    Toast.makeText(this, "✅ Viaje finalizado.", Toast.LENGTH_LONG).show();
                    cargarDetalleViaje();
                    new Handler(Looper.getMainLooper()).postDelayed(this::paso3BuscarPasajerosParaCalificar, 1500);
                },
                e2 -> { loaderDetalle.setVisibility(View.GONE); Toast.makeText(this, "Error finalizando", Toast.LENGTH_LONG).show(); }
        );
    }

    private void paso3BuscarPasajerosParaCalificar() {
        String url = Constantes.BASE_URL + "/api/reservas/viaje/" + viajeId;
        ConexionApi.getInstance(this).getArrayNoCache(url,
                reservas -> {
                    try {
                        if (reservas == null || reservas.length() == 0) return;
                        ArrayList<Integer> ids     = new ArrayList<>();
                        ArrayList<String>  nombres = new ArrayList<>();
                        for (int i = 0; i < reservas.length(); i++) {
                            JSONObject res = reservas.optJSONObject(i);
                            if (res == null) continue;
                            String est = res.optString("estado","").toUpperCase();
                            if (ESTADOS_CANCELADOS.contains(est)) continue;
                            int idP = -1; String nomP = "";
                            JSONObject po = res.optJSONObject("pasajero");
                            if (po == null) po = res.optJSONObject("usuario");
                            if (po != null) {
                                for (String k:new String[]{"id","idUsuarios","idUsuario","userId"}){int id=po.optInt(k,-1);if(id>0){idP=id;break;}}
                                for (String k:new String[]{"nombre","nombreCompleto","name"}){String n=po.optString(k,"");if(!n.isEmpty()&&!n.equals("null")){nomP=n;break;}}
                            }
                            if (idP <= 0) for(String k:new String[]{"idPasajero","idUsuarioPasajero","pasajeroId"}){int id=res.optInt(k,-1);if(id>0){idP=id;break;}}
                            if (idP > 0) { ids.add(idP); nombres.add(nomP.isEmpty()?"Pasajero":nomP); }
                        }
                        if (!ids.isEmpty()) {
                            final int idCal = session.getIdUsuario();
                            new Handler(Looper.getMainLooper()).post(() ->
                                    mostrarCalificacionesEncadenadas(ids, nombres, idCal, 0));
                        }
                    } catch (Exception e) { Log.e(TAG,"Error buscando pasajeros para calificar",e); }
                },
                error -> Log.w(TAG, "No se pudieron cargar reservas para calificación")
        );
    }

    private void mostrarCalificacionesEncadenadas(ArrayList<Integer> ids, ArrayList<String> nombres, int idCalificador, int indice) {
        if (indice >= ids.size()) return;
        int idP=ids.get(indice); String nomP=nombres.get(indice); int sig=indice+1;
        new CalificacionesManager(this).verificarCalificacion(viajeId, idCalificador, idP,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override public void onDebeCalificar() {
                        CalificacionController.mostrarBottomSheetCalificar(DetalleViajeActivity.this, viajeId, idP, nomP, idCalificador, true,
                                (p, c) -> new Handler(Looper.getMainLooper()).postDelayed(() -> mostrarCalificacionesEncadenadas(ids, nombres, idCalificador, sig), 700));
                    }
                    @Override public void onYaCalifico(int p, String e) { mostrarCalificacionesEncadenadas(ids, nombres, idCalificador, sig); }
                });
    }

    // =========================================================================
    //  BOTONES
    // =========================================================================
    private void configurarBotones() {
        if(btnAccionPrincipal!=null)btnAccionPrincipal.setVisibility(View.GONE);
        btnIniciar.setVisibility(View.GONE);btnFinalizar.setVisibility(View.GONE);
        if(btnMensajeConductor!=null)btnMensajeConductor.setVisibility(View.GONE);
        if(btnRecoger!=null)btnRecoger.setVisibility(View.GONE);
        if(esConductor){
            if(estadoViaje.equals("CREADO")||estadoViaje.equals("PROGRAMADO")||estadoViaje.equals("DISPONIBLE"))btnIniciar.setVisibility(View.VISIBLE);
            if(estadoViaje.equals("EN_CURSO")||estadoViaje.equals("INICIADO"))btnFinalizar.setVisibility(View.VISIBLE);
        }else{
            if(ESTADOS_RESERVABLES.contains(estadoViaje))verificarReservaActivaYMostrarBoton();
            else if(estadoViaje.equals("CREADO")||estadoViaje.equals("PROGRAMADO")||estadoViaje.equals("DISPONIBLE"))mostrarBannerEsperaInicio();
        }
        actualizarBotonChat();
    }

    private void verificarReservaActivaYMostrarBoton(){
        ConexionApi.getInstance(this).getArray(Constantes.MIS_RESERVAS,
                response->{boolean tieneOtraReservaActiva=false;for(int i=0;i<response.length();i++){JSONObject r=response.optJSONObject(i);if(r==null)continue;String est=r.optString("estado","").toUpperCase();int idV=r.optInt("idViaje",0);if(idV==0){JSONObject vo=r.optJSONObject("viaje");if(vo!=null)idV=vo.optInt("idViaje",vo.optInt("id",0));}if(ESTADOS_RESERVA_ACTIVA.contains(est)&&idV!=viajeId){tieneOtraReservaActiva=true;break;}}final boolean bloqueado=tieneOtraReservaActiva;runOnUiThread(()->{if(bloqueado)mostrarBannerReservaActiva();else actualizarBotonPasajero();});},
                error->runOnUiThread(this::actualizarBotonPasajero));
    }

    private void mostrarBannerEsperaInicio(){if(btnAccionPrincipal==null)return;btnAccionPrincipal.setText("⏳ El conductor aún no inició el viaje");btnAccionPrincipal.setEnabled(false);btnAccionPrincipal.setAlpha(0.65f);btnAccionPrincipal.setBackgroundColor(Color.parseColor("#B0BEC5"));btnAccionPrincipal.setVisibility(View.VISIBLE);}
    private void mostrarBannerReservaActiva(){if(btnAccionPrincipal==null)return;btnAccionPrincipal.setText("🔒 Ya tienes un viaje activo");btnAccionPrincipal.setEnabled(false);btnAccionPrincipal.setAlpha(0.75f);btnAccionPrincipal.setBackgroundColor(Color.parseColor("#EF5350"));btnAccionPrincipal.setVisibility(View.VISIBLE);}

    private void actualizarBotonPasajero(){
        if(esConductor||btnAccionPrincipal==null)return;
        if(yaReservo){btnAccionPrincipal.setText("🔄 CAMBIAR PARADA");btnAccionPrincipal.setEnabled(true);btnAccionPrincipal.setAlpha(1f);btnAccionPrincipal.setVisibility(View.VISIBLE);btnAccionPrincipal.setBackgroundColor(Color.parseColor("#1565C0"));}
        else if(cuposDisponibles>0&&ESTADOS_RESERVABLES.contains(estadoViaje)){btnAccionPrincipal.setText("🚏 ELEGIR PARADA");btnAccionPrincipal.setEnabled(true);btnAccionPrincipal.setAlpha(1f);btnAccionPrincipal.setVisibility(View.VISIBLE);btnAccionPrincipal.setBackgroundColor(Color.parseColor("#00897B"));}
        else{btnAccionPrincipal.setVisibility(View.GONE);}
    }

    private void actualizarBotonChat(){
        if(btnMensajeConductor==null)return;
        if(esConductor){String l=nombrePasajeroViaje.isEmpty()?"Pasajero":nombrePasajeroViaje;btnMensajeConductor.setText("💬  Chat con "+l);btnMensajeConductor.setVisibility(idPasajeroViaje>0?View.VISIBLE:View.GONE);}
        else{if(idConductorViaje>0){btnMensajeConductor.setText("💬  Chat con "+(nombreConductorViaje.isEmpty()?"Conductor":nombreConductorViaje));btnMensajeConductor.setVisibility(View.VISIBLE);btnMensajeConductor.setEnabled(true);btnMensajeConductor.setAlpha(1f);}else{btnMensajeConductor.setText("💬  Chat con Conductor");btnMensajeConductor.setVisibility(View.VISIBLE);btnMensajeConductor.setEnabled(false);btnMensajeConductor.setAlpha(0.5f);new Handler(Looper.getMainLooper()).postDelayed(this::cargarConductorDelViaje,1500);}}
    }

    // =========================================================================
    //  ACCIONES CONDUCTOR
    // =========================================================================
    private void cambiarEstadoViaje(String accion){
        loaderDetalle.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).post(Constantes.viajePorId((long)viajeId)+"/"+accion,null,
                response->{loaderDetalle.setVisibility(View.GONE);Toast.makeText(this,"✅ Viaje "+accion+"do",Toast.LENGTH_SHORT).show();cargarDetalleViaje();},
                error->{loaderDetalle.setVisibility(View.GONE);Toast.makeText(this,"Error al "+accion,Toast.LENGTH_LONG).show();});
    }

    private void confirmarFinalizar(){
        new AlertDialog.Builder(this).setTitle("Finalizar viaje").setMessage("¿Finalizar? Se liberarán todos los cupos.")
                .setPositiveButton("Finalizar",(d,w)->finalizarViaje()).setNegativeButton("Cancelar",null).show();
    }

    // =========================================================================
    //  CHAT
    // =========================================================================
    private void abrirOCrearChat(){
        if(!esConductor&&idConductorViaje<=0){Toast.makeText(this,"Cargando datos del conductor...",Toast.LENGTH_SHORT).show();cargarConductorDelViaje();new Handler(Looper.getMainLooper()).postDelayed(()->{if(idConductorViaje>0)iniciarConversacion();else Toast.makeText(this,"No se pudo identificar al conductor.",Toast.LENGTH_LONG).show();},1500);return;}
        iniciarConversacion();
    }

    private void iniciarConversacion(){
        if(!esConductor&&idConductorViaje<=0){Toast.makeText(this,"No se pudo identificar al conductor.",Toast.LENGTH_LONG).show();return;}
        int miId=session.getIdUsuario(); int idP,idC;String nom;
        if(esConductor){idP=idPasajeroViaje>0?idPasajeroViaje:miId;idC=miId;nom=nombrePasajeroViaje.isEmpty()?"Pasajero":nombrePasajeroViaje;}
        else{idP=miId;idC=idConductorViaje;nom=nombreConductorViaje.isEmpty()?"Conductor":nombreConductorViaje;}
        JSONObject body=new JSONObject();
        try{body.put("idViaje",viajeId);body.put("idPasajero",idP);body.put("idConductor",idC);}catch(JSONException e){return;}
        final String nf=nom;final int pf=idP;final int cf=idC;
        loaderDetalle.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).post(Constantes.CHAT_CONVERSACIONES,body,
                response->{loaderDetalle.setVisibility(View.GONE);long ic=extraerIdConversacion(response);if(ic>0)navegarAlChat(ic,nf);else buscarConversacion(pf,cf,nf);},
                error->{loaderDetalle.setVisibility(View.GONE);buscarConversacion(pf,cf,nf);});
    }

    private void buscarConversacion(int idP,int idC,String nom){
        String url=Constantes.CHAT_CONVERSACIONES+"?idViaje="+viajeId+"&idPasajero="+idP+"&idConductor="+idC;
        ConexionApi.getInstance(this).getObject(url,response->{long ic=extraerIdConversacion(response);if(ic<=0){for(String k:new String[]{"content","conversaciones","data"}){JSONArray a=response.optJSONArray(k);if(a!=null&&a.length()>0){ic=extraerIdConversacion(a.optJSONObject(0));if(ic>0)break;}}}if(ic>0)navegarAlChat(ic,nom);else Toast.makeText(this,"No se pudo abrir el chat",Toast.LENGTH_LONG).show();},error->Toast.makeText(this,"Error al abrir chat",Toast.LENGTH_SHORT).show());
    }

    private long extraerIdConversacion(JSONObject r){if(r==null)return -1;long id=r.optLong("id",-1);if(id>0)return id;id=r.optLong("idConversacion",-1);if(id>0)return id;JSONObject d=r.optJSONObject("data");if(d!=null){id=d.optLong("id",-1);if(id>0)return id;}return -1;}
    private void navegarAlChat(long idC,String nom){Intent i=new Intent(this,Chat.class);i.putExtra("idConversacion",idC);i.putExtra("nombre",nom);startActivity(i);}

    private void cargarConductorDelViaje(){
        if (idConductorViaje > 0 && !nombreConductorViaje.isEmpty()
                && !nombreConductorViaje.startsWith("Conductor #")
                && !nombreConductorViaje.equals("Conductor")) return;

        ConexionApi.getInstance(this).getObject(Constantes.viajePorId((long) viajeId),
                response -> {
                    try {
                        extraerConductor(response);
                        if (idConductorViaje <= 0) {
                            JSONObject v = response.optJSONObject("vehiculo");
                            if (v != null) {
                                int iv = v.optInt("idUsuario", -1);
                                if (iv > 0) {
                                    idConductorViaje     = iv;
                                    nombreConductorViaje = "Conductor #" + iv;
                                }
                            }
                        }
                        if (idConductorViaje > 0 && (nombreConductorViaje.isEmpty()
                                || nombreConductorViaje.startsWith("Conductor #"))) {
                            ConexionApi.getInstance(this).getObject(
                                    Constantes.USUARIOS + "/" + idConductorViaje,
                                    perfil -> {
                                        String nom = extractNombre(perfil);
                                        if (!nom.isEmpty()) nombreConductorViaje = nom;
                                        runOnUiThread(() -> {
                                            actualizarNombreConductorUI();
                                            actualizarBotonChat();
                                            if (!esConductor && cardMiReserva != null
                                                    && cardMiReserva.getVisibility() == View.VISIBLE) {
                                                cargarMiReservaPasajero();
                                            }
                                        });
                                    },
                                    err -> runOnUiThread(() -> {
                                        actualizarNombreConductorUI();
                                        actualizarBotonChat();
                                    })
                            );
                        } else {
                            runOnUiThread(() -> {
                                actualizarNombreConductorUI();
                                actualizarBotonChat();
                                if (!esConductor && cardMiReserva != null
                                        && cardMiReserva.getVisibility() == View.VISIBLE) {
                                    cargarMiReservaPasajero();
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error extrayendo conductor", e);
                    }
                },
                error -> Log.e(TAG, "Error recargando viaje para conductor"));
    }

    // =========================================================================
    //  PARADAS DINÁMICAS
    // =========================================================================
    private void generarParadasDinamicas(){
        paradasDin.clear();
        final List<GeoPoint> base=(rutaActiva!=null&&rutaActiva.size()>=2)?new ArrayList<>(rutaActiva):null;
        if(base==null||base.size()<2){for(JSONObject p:paradasRuta){double lat=p.optDouble("lat",0),lng=p.optDouble("lng",0);if(lat!=0){ParadaDinamica pd=new ParadaDinamica(p.optString("nombre","Parada"),lat,lng,0);pd.idParadaBD=p.optInt("idParada",p.optInt("id",0));paradasDin.add(pd);}}paradasDin.add(new ParadaDinamica(destinoActual,latDestino,lngDestino,-1));return;}
        int total=base.size(),num=Math.min(6,Math.max(3,total/20));
        List<Integer> indices=new ArrayList<>();double paso=(double)(total-2)/(num+1);for(int i=1;i<=num;i++){int idx=1+(int)(i*paso);if(idx<total-1)indices.add(idx);}
        for(int i=0;i<indices.size();i++){int idx=indices.get(i);GeoPoint gp=base.get(idx);double pct=(double)idx/(total-1)*100;paradasDin.add(new ParadaDinamica(String.format("Punto %.0f%% de la ruta",pct),gp.getLatitude(),gp.getLongitude(),idx));}
        paradasDin.add(new ParadaDinamica(destinoActual,latDestino,lngDestino,total-1));
        geocodificarEnBackground(indices,base);
    }

    private void geocodificarEnBackground(List<Integer> indices,List<GeoPoint> base){
        new Thread(()->{for(int i=0;i<indices.size()&&i<paradasDin.size()-1;i++){GeoPoint gp=base.get(indices.get(i));try{String url="https://nominatim.openstreetmap.org/reverse?lat="+gp.getLatitude()+"&lon="+gp.getLongitude()+"&format=json&addressdetails=1&zoom=16&accept-language=es";String resp=peticionHttp(url);if(resp!=null&&!resp.isEmpty()){JSONObject geo=new JSONObject(resp);String nombre=extraerNombreNominatim(geo.optJSONObject("address"),geo);final int fi=i;final String fn=nombre;runOnUiThread(()->{if(fi<paradasDin.size()-1)paradasDin.get(fi).nombre=fn;});}Thread.sleep(500);}catch(Exception ignored){}}}).start();
    }

    private String extraerNombreNominatim(JSONObject addr,JSONObject geo){if(addr==null)return geo.optString("display_name","Parada");for(String c:new String[]{"neighbourhood","suburb","quarter","city_district","road"}){String v=addr.optString(c,"");if(!v.isEmpty()&&!v.equals("null"))return v;}String d=geo.optString("display_name","");return d.isEmpty()?"Parada":d.split(",")[0].trim();}

    private void poblarLista(LinearLayout container,ArrayList<ParadaDinamica> paradas,String filtro,float dp,int p8,int p4,java.util.function.Consumer<ParadaDinamica> onSelect){
        container.removeAllViews();
        if(paradas.isEmpty()){TextView tv=new TextView(this);tv.setText("Sin paradas disponibles");tv.setTextSize(13f);tv.setTextColor(Color.parseColor("#9E9E9E"));tv.setPadding(p8,p8,p8,p8);container.addView(tv);return;}
        container.addView(crearFilaParada("🟢  "+origenActual+"  (Inicio)",null,false,dp,p8,p4,onSelect));
        for(ParadaDinamica pd:paradas){boolean esD=pd.idxEnRuta==-1;container.addView(crearFilaParada((esD?"🔴":"🔵")+"  "+pd.nombre+(esD?"  (Destino final)":""),pd,true,dp,p8,p4,onSelect));}
    }

    private View crearFilaParada(String label,ParadaDinamica pd,boolean sel,float dp,int p8,int p4,java.util.function.Consumer<ParadaDinamica> onSel){
        LinearLayout fila=new LinearLayout(this);fila.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams lpF=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpF.setMargins(0,(int)(2*dp),0,(int)(2*dp));fila.setLayoutParams(lpF);GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.RECTANGLE);bg.setCornerRadius(10*dp);bg.setColor(sel?Color.parseColor("#F9FAFB"):Color.parseColor("#F0F4F8"));bg.setStroke((int)(1*dp),sel?Color.parseColor("#B2DFDB"):Color.parseColor("#E0E0E0"));fila.setBackground(bg);fila.setPadding(p8,p8,p8,p8);
        TextView tv=new TextView(this);tv.setText(label);tv.setTextSize(14f);tv.setTextColor(sel?Color.parseColor("#004D40"):Color.parseColor("#9E9E9E"));if(!sel)tv.setTypeface(null,Typeface.ITALIC);tv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));fila.addView(tv);
        if(sel&&pd!=null){fila.setClickable(true);fila.setFocusable(true);fila.setOnClickListener(v->{GradientDrawable bgS=new GradientDrawable();bgS.setShape(GradientDrawable.RECTANGLE);bgS.setCornerRadius(10*dp);bgS.setColor(Color.parseColor("#E0F7FA"));bgS.setStroke((int)(2*dp),Color.parseColor("#00897B"));fila.setBackground(bgS);onSel.accept(pd);});}
        return fila;
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private void extraerConductor(JSONObject r){
        JSONObject co=r.optJSONObject("conductor");
        if(co!=null){idConductorViaje=extractId(co);nombreConductorViaje=extractNombre(co);if(idConductorViaje<=0||nombreConductorViaje.isEmpty()){JSONObject u=co.optJSONObject("usuario");if(u!=null){if(idConductorViaje<=0)idConductorViaje=extractId(u);if(nombreConductorViaje.isEmpty())nombreConductorViaje=extractNombre(u);}}}
        if(idConductorViaje<=0)for(String c:new String[]{"idConductor","conductorId","idUsuarioConductor"}){int v=r.optInt(c,-1);if(v>0){idConductorViaje=v;break;}}
        if(nombreConductorViaje.isEmpty())for(String c:new String[]{"nombreConductor","conductorNombre"}){String v=r.optString(c,"");if(!v.isEmpty()&&!v.equals("null")){nombreConductorViaje=v;break;}}
        if(idConductorViaje>0&&nombreConductorViaje.isEmpty())nombreConductorViaje="Conductor #"+idConductorViaje;
    }

    private void actualizarNombreConductorUI(){if(txtConductor==null)return;String n=nombreConductorViaje.isEmpty()?"Sin asignar":nombreConductorViaje;txtConductor.setText(esConductor&&idConductorViaje==session.getIdUsuario()?"🚗 Tú ("+n+")":"🚗 "+n);}

    private void mostrarFechaHora(String fh){
        if(txtFechaHora==null)return;
        if(!fh.isEmpty()&&!fh.equals("null")){try{String fl=fh.replace("T"," ").replaceAll("\\.\\d{3}Z$","");SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault());SimpleDateFormat out=new SimpleDateFormat("EEE dd/MM/yyyy  🕐 HH:mm",new Locale("es","CO"));Date date=in.parse(fl);txtFechaHora.setText("📅 "+out.format(date));}catch(Exception ex){txtFechaHora.setText("📅 "+fh.replace("T"," ").replaceAll("\\.\\d{3}Z$",""));}txtFechaHora.setVisibility(View.VISIBLE);}else txtFechaHora.setVisibility(View.GONE);
    }

    private String etiquetaEstado(String e){switch(e){case "CREADO":return "📋 Estado: Disponible";case "PROGRAMADO":return "📅 Estado: Programado";case "DISPONIBLE":return "✅ Estado: Disponible";case "EN_CURSO":case "INICIADO":return "🚗 Estado: En curso";case "FINALIZADO":return "🏁 Estado: Finalizado";case "CANCELADO":return "❌ Estado: Cancelado";default:return "📌 Estado: "+e;}}

    private int extractId(JSONObject obj){if(obj==null)return -1;for(String k:new String[]{"id","idUsuarios","idUsuario","userId","conductorId","idConductor","pasajeroId","idPasajero"}){int v=obj.optInt(k,-1);if(v>0)return v;}return -1;}
    private String extractNombre(JSONObject obj){if(obj==null)return "";for(String k:new String[]{"nombre","nombreCompleto","name","fullName","nombreUsuario","displayName"}){String v=obj.optString(k,"");if(!v.isEmpty()&&!v.equals("null"))return v;}String n=obj.optString("nombres",""),a=obj.optString("apellidos","");if(!n.isEmpty()||!a.isEmpty())return (n+" "+a).trim();return "";}

    private double primeraCoord(JSONObject o,String[] cs,double def){for(String c:cs){double v=o.optDouble(c,Double.NaN);if(!Double.isNaN(v)&&v!=0)return v;}return def;}
    private double primeraCoordDistinta(JSONObject o,String[] cs,double ref,double def){for(String c:cs){double v=o.optDouble(c,Double.NaN);if(!Double.isNaN(v)&&v!=0&&Math.abs(v-ref)>0.0001)return v;}return def;}
    private String primeraStr(JSONObject o,String[] cs){for(String c:cs){String v=o.optString(c,"").trim();if(!v.isEmpty()&&!v.equals("null"))return v;}return "";}
    private boolean sonIguales(double la,double ln,double lb,double lm){return Math.abs(la-lb)<0.0001&&Math.abs(ln-lm)<0.0001;}

    private double[] geocodificarTexto(String dir){try{String q=dir.toLowerCase().contains("popay")?dir:dir+", Popayan, Colombia";String url="https://nominatim.openstreetmap.org/search?q="+java.net.URLEncoder.encode(q,"UTF-8")+"&format=json&limit=1&countrycodes=co";String resp=peticionHttp(url);if(resp==null||resp.isEmpty()||resp.equals("[]"))return null;JSONArray arr=new JSONArray(resp);if(arr.length()==0)return null;JSONObject obj=arr.getJSONObject(0);return new double[]{obj.getDouble("lat"),obj.getDouble("lon")};}catch(Exception e){return null;}}

    private String peticionHttp(String urlStr) throws Exception {
        HttpURLConnection c=null;
        try{c=(HttpURLConnection)new URL(urlStr).openConnection();c.setRequestProperty("User-Agent","Moviflexx-App/1.0");c.setConnectTimeout(15000);c.setReadTimeout(15000);BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()));StringBuilder sb=new StringBuilder();String l;while((l=r.readLine())!=null)sb.append(l);r.close();return sb.toString();}
        finally{if(c!=null)c.disconnect();}
    }

    private ArrayList<GeoPoint> parsearGeoJsonString(String geojsonStr){
        if(geojsonStr==null||geojsonStr.trim().isEmpty())return null;
        try{geojsonStr=geojsonStr.trim();if(geojsonStr.startsWith("{"))return parsearGeoJsonObj(new JSONObject(geojsonStr));else if(geojsonStr.startsWith("[")){JSONArray arr=new JSONArray(geojsonStr);ArrayList<GeoPoint> pts=new ArrayList<>();for(int i=0;i<arr.length();i++){JSONArray pair=arr.optJSONArray(i);if(pair!=null&&pair.length()>=2)pts.add(new GeoPoint(pair.getDouble(1),pair.getDouble(0)));}return pts.size()>=2?pts:null;}}catch(Exception e){Log.e(TAG,"Error parseando geojsonString: "+e.getMessage());}return null;
    }

    private ArrayList<GeoPoint> parsearGeoJsonObj(JSONObject obj){
        if(obj==null)return null;
        try{String tipo=obj.optString("type","");JSONObject geometry=null;if("Feature".equals(tipo))geometry=obj.optJSONObject("geometry");else if("LineString".equals(tipo))geometry=obj;else if("FeatureCollection".equals(tipo)){JSONArray features=obj.optJSONArray("features");if(features!=null&&features.length()>0){JSONObject feat=features.optJSONObject(0);if(feat!=null)geometry=feat.optJSONObject("geometry");}}else{geometry=obj.optJSONObject("geometry");if(geometry==null)geometry=obj;}if(geometry==null)return null;JSONArray coords=geometry.optJSONArray("coordinates");if(coords==null||coords.length()==0)return null;ArrayList<GeoPoint> pts=new ArrayList<>();for(int i=0;i<coords.length();i++){JSONArray pair=coords.optJSONArray(i);if(pair!=null&&pair.length()>=2)pts.add(new GeoPoint(pair.getDouble(1),pair.getDouble(0)));}return pts.size()>=2?pts:null;}catch(Exception e){Log.e(TAG,"Error parsearGeoJsonObj: "+e.getMessage());return null;}
    }

    private Bitmap crearBitmapMarcador(int colorInt,String letra){int size=96;Bitmap bmp=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bmp);Paint pS=new Paint(Paint.ANTI_ALIAS_FLAG);pS.setColor(Color.argb(80,0,0,0));c.drawCircle(size/2f+3,size/2f+5,size/2f-6,pS);Paint pC=new Paint(Paint.ANTI_ALIAS_FLAG);pC.setColor(colorInt);c.drawCircle(size/2f,size/2f-4,size/2f-8,pC);Paint pB=new Paint(Paint.ANTI_ALIAS_FLAG);pB.setColor(Color.WHITE);pB.setStyle(Paint.Style.STROKE);pB.setStrokeWidth(5f);c.drawCircle(size/2f,size/2f-4,size/2f-8,pB);Paint pT=new Paint(Paint.ANTI_ALIAS_FLAG);pT.setColor(Color.WHITE);pT.setTextSize(letra.length()>1?26f:36f);pT.setTypeface(Typeface.DEFAULT_BOLD);pT.setTextAlign(Paint.Align.CENTER);c.drawText(letra,size/2f,size/2f+9,pT);return bmp;}

    // =========================================================================
    //  MODELOS INTERNOS
    // =========================================================================
    private static class ParadaDinamica {
        String nombre; double lat, lng; int idxEnRuta; int idParadaBD = 0;
        ParadaDinamica(String nombre,double lat,double lng,int idx){this.nombre=nombre;this.lat=lat;this.lng=lng;this.idxEnRuta=idx;}
        GeoPoint toGeoPoint(){return new GeoPoint(lat,lng);}
    }

    static class ParadaAdapter extends RecyclerView.Adapter<ParadaAdapter.VH> {
        private final ArrayList<String> items = new ArrayList<>();
        ParadaAdapter(ArrayList<String> paradas,String origen,String destino){items.add("🟢 "+origen+"  (Inicio)");for(String p:paradas)items.add("🔵 "+p);items.add("🔴 "+destino+"  (Destino)");}
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent,int vt){TextView tv=new TextView(parent.getContext());tv.setPadding(32,20,32,20);tv.setTextSize(14f);tv.setTextColor(Color.parseColor("#004D40"));return new VH(tv);}
        @Override public void onBindViewHolder(@NonNull VH h,int pos){((TextView)h.itemView).setText(items.get(pos));}
        @Override public int getItemCount(){return items.size();}
        static class VH extends RecyclerView.ViewHolder{VH(@NonNull View v){super(v);}}
    }
}