package com.arlys.moviflexx.controller;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DetalleViajeActivity extends AppCompatActivity {

    private static final String TAG              = "DetalleViaje";
    private static final int    REQ_LOCATION     = 1002;
    private static final int    POLLING_CUPOS_MS = 5000;
    private static final int    POLLING_UBIC_MS  = 8000;

    private static final String COLOR_RUTA       = "#00BCD4";
    private static final String COLOR_SEGMENTO   = "#FF6F00";
    private static final String COLOR_CUPO_LIBRE = "#4CAF50";
    private static final String COLOR_CUPO_OCUP  = "#EF5350";

    // ── UI principal ──────────────────────────────────────────────────────────
    private MapView          map;
    private TextView         txtRuta, txtEstado, txtConductor, txtVehiculo;
    private TextView         txtDistancia, txtDuracion, txtPrecio;
    private LinearLayout     layoutCupos;
    private MaterialButton   btnReservar, btnIniciar, btnFinalizar, btnAgregarParada;
    private MaterialButton   btnMensajeConductor;
    private RecyclerView     rvHistorialParadas;
    private MaterialCardView cardHistorial, cardAcciones;
    private ProgressBar      loaderDetalle;
    private MyLocationNewOverlay myLocationOverlay;

    // ── Card "Mi reserva" (pasajero) ──────────────────────────────────────────
    // Se crea dinámicamente si no existe en el layout
    private MaterialCardView cardMiReserva;
    private TextView         txtMiReservaInfo;

    // ── Card "Pasajeros reservados" (conductor) ───────────────────────────────
    // Se crea dinámicamente
    private MaterialCardView cardPasajeros;
    private LinearLayout     layoutListaPasajeros;
    private TextView         txtTotalPasajeros;

    // ── Datos viaje ───────────────────────────────────────────────────────────
    private int     viajeId, rutaId;
    private boolean esConductor;
    private String  origenActual = "", destinoActual = "", estadoViaje = "";
    private double  latOrigen, lngOrigen, latDestino, lngDestino;
    private int     cuposTotales = 0, cuposDisponibles = 0;
    private SessionManager session;

    // ── Chat ──────────────────────────────────────────────────────────────────
    private int    idContactoChat     = -1;
    private int    idPasajeroViaje    = -1;
    private int    idConductorViaje   = -1;
    private String nombreContactoChat = "";
    private String nombreConductorViaje = "";
    private String nombrePasajeroViaje  = "";

    // ── Paradas ───────────────────────────────────────────────────────────────
    private final ArrayList<JSONObject> paradasRuta               = new ArrayList<>();
    private JSONObject                  paradaSeleccionadaPasajero = null;
    private GeoPoint                    paradaPersonalizadaPoint   = null;
    private String                      paradaPersonalizadaNombre  = "";

    private ArrayList<GeoPoint> puntosRutaConductor = new ArrayList<>();
    private List<Marker>        marcadoresPasajeros  = new ArrayList<>();

    // ── Polling ───────────────────────────────────────────────────────────────
    private final Handler cuposHandler = new Handler(Looper.getMainLooper());
    private Runnable      cuposRunnable;
    private boolean       cuposActivo  = false;

    private final Handler ubicHandler = new Handler(Looper.getMainLooper());
    private Runnable      ubicRunnable;
    private boolean       ubicActivo  = false;

    private boolean mapaListo = false;

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

        Log.d(TAG, "viajeId=" + viajeId + " esConductor=" + esConductor);

        if (viajeId == 0) {
            Toast.makeText(this, "ID de viaje inválido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        configurarMapa();
        verificarPermisos();
        cargarDetalleViaje();
    }

    @Override protected void onResume()  { super.onResume();  if (map != null) map.onResume(); }
    @Override protected void onPause()   { super.onPause();   if (map != null) map.onPause();  detenerTodoPolling(); }
    @Override protected void onDestroy() { super.onDestroy(); detenerTodoPolling(); }

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
        layoutCupos         = findViewById(R.id.layout_cupos);
        btnReservar         = findViewById(R.id.btn_reservar);
        btnIniciar          = findViewById(R.id.btn_iniciar);
        btnFinalizar        = findViewById(R.id.btn_finalizar);
        btnAgregarParada    = findViewById(R.id.btn_parada);
        btnMensajeConductor = findViewById(R.id.btn_mensaje_conductor);
        rvHistorialParadas  = findViewById(R.id.rv_historial_paradas);
        cardHistorial       = findViewById(R.id.card_historial);
        cardAcciones        = findViewById(R.id.card_acciones);
        loaderDetalle       = findViewById(R.id.loader_detalle);

        rvHistorialParadas.setLayoutManager(new LinearLayoutManager(this));

        btnReservar.setOnClickListener(v -> mostrarBottomSheetReserva());
        btnIniciar.setOnClickListener(v -> cambiarEstado("iniciar"));
        btnFinalizar.setOnClickListener(v -> confirmarFinalizar());
        btnAgregarParada.setOnClickListener(v -> mostrarDialogoAgregarParada());
        if (btnMensajeConductor != null)
            btnMensajeConductor.setOnClickListener(v -> abrirOCrearChat());

        // Crear las cards dinámicas si el layout no las tiene
        crearCardMiReservaSiNoExiste();
        crearCardPasajerosSiNoExiste();
    }

    // ── Card "Mi reserva" (pasajero ve al conductor) ──────────────────────────
    private void crearCardMiReservaSiNoExiste() {
        // Siempre se crea dinámicamente (no depende de IDs del layout)
        View raiz = findViewById(android.R.id.content);
        if (!(raiz instanceof ViewGroup)) return;
        ViewGroup contenedor = buscarScrollContent((ViewGroup) raiz);
        if (contenedor == null) return;

        float d   = getResources().getDisplayMetrics().density;
        int   p16 = (int)(16 * d);
        int   p12 = (int)(12 * d);
        int   p8  = (int)(8  * d);

        cardMiReserva = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(p16, p8, p16, p8);
        cardMiReserva.setLayoutParams(lpCard);
        cardMiReserva.setRadius(16 * d);
        cardMiReserva.setCardElevation(4 * d);
        cardMiReserva.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
        cardMiReserva.setVisibility(View.GONE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p16, p12, p16, p12);

        TextView titulo = new TextView(this);
        titulo.setText("🎫 Tu reserva");
        titulo.setTextSize(13f);
        titulo.setTypeface(null, android.graphics.Typeface.BOLD);
        titulo.setTextColor(Color.parseColor("#2E7D32"));
        inner.addView(titulo);

        txtMiReservaInfo = new TextView(this);
        txtMiReservaInfo.setTextSize(14f);
        txtMiReservaInfo.setTextColor(Color.parseColor("#004D40"));
        LinearLayout.LayoutParams lpTxt = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTxt.topMargin = p8;
        txtMiReservaInfo.setLayoutParams(lpTxt);
        inner.addView(txtMiReservaInfo);

        cardMiReserva.addView(inner);
        contenedor.addView(cardMiReserva, 0); // al principio
    }

    // ── Card "Pasajeros reservados" (conductor ve sus pasajeros) ─────────────
    private void crearCardPasajerosSiNoExiste() {
        // Siempre se crea dinámicamente (no depende de IDs del layout)
        View raiz = findViewById(android.R.id.content);
        if (!(raiz instanceof ViewGroup)) return;
        ViewGroup contenedor = buscarScrollContent((ViewGroup) raiz);
        if (contenedor == null) return;

        float d   = getResources().getDisplayMetrics().density;
        int   p16 = (int)(16 * d);
        int   p12 = (int)(12 * d);
        int   p8  = (int)(8  * d);

        cardPasajeros = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(p16, p8, p16, p8);
        cardPasajeros.setLayoutParams(lpCard);
        cardPasajeros.setRadius(16 * d);
        cardPasajeros.setCardElevation(4 * d);
        cardPasajeros.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
        cardPasajeros.setVisibility(View.GONE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p16, p12, p16, p12);

        // Fila título + contador
        LinearLayout filaTitulo = new LinearLayout(this);
        filaTitulo.setOrientation(LinearLayout.HORIZONTAL);
        filaTitulo.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView titulo = new TextView(this);
        titulo.setText("🧑‍🤝‍🧑 Pasajeros reservados");
        titulo.setTextSize(14f);
        titulo.setTypeface(null, android.graphics.Typeface.BOLD);
        titulo.setTextColor(Color.parseColor("#1565C0"));
        titulo.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        filaTitulo.addView(titulo);

        txtTotalPasajeros = new TextView(this);
        txtTotalPasajeros.setTextSize(12f);
        txtTotalPasajeros.setTextColor(Color.parseColor("#1565C0"));
        filaTitulo.addView(txtTotalPasajeros);

        inner.addView(filaTitulo);

        // Separador
        View sep = new View(this);
        LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d));
        lpSep.setMargins(0, p8, 0, p8);
        sep.setLayoutParams(lpSep);
        sep.setBackgroundColor(Color.parseColor("#BBDEFB"));
        inner.addView(sep);

        // Layout donde se agregan las filas de pasajeros
        layoutListaPasajeros = new LinearLayout(this);
        layoutListaPasajeros.setOrientation(LinearLayout.VERTICAL);
        inner.addView(layoutListaPasajeros);

        cardPasajeros.addView(inner);
        contenedor.addView(cardPasajeros, 0);
    }

    private ViewGroup buscarScrollContent(ViewGroup vg) {
        // Busca el primer LinearLayout o ScrollView > LinearLayout en el árbol
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child instanceof android.widget.ScrollView) {
                android.widget.ScrollView sv = (android.widget.ScrollView) child;
                if (sv.getChildCount() > 0 && sv.getChildAt(0) instanceof ViewGroup)
                    return (ViewGroup) sv.getChildAt(0);
            }
            if (child instanceof LinearLayout) return (ViewGroup) child;
        }
        return vg;
    }

    private void configurarMapa() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.setUseDataConnection(true);
        map.setTilesScaledToDpi(true);
        map.setHorizontalMapRepetitionEnabled(false);
        map.setVerticalMapRepetitionEnabled(false);
        map.getController().setZoom(14.0);
        map.getController().setCenter(new GeoPoint(2.4419, -76.6063));
        map.addMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent e) { mapaListo = true; return false; }
            @Override public boolean onZoom(ZoomEvent e)     { mapaListo = true; return false; }
        });
        map.post(() -> { map.invalidate(); mapaListo = true; });
    }

    // =========================================================================
    //  GPS
    // =========================================================================
    private void verificarPermisos() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
        } else {
            activarGPS();
        }
    }

    private void activarGPS() {
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        myLocationOverlay.enableMyLocation();
        map.getOverlays().add(myLocationOverlay);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_LOCATION && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED)
            activarGPS();
    }

    // =========================================================================
    //  CARGA DEL VIAJE
    // =========================================================================
    private void cargarDetalleViaje() {
        loaderDetalle.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).getObject(
                Constantes.viajePorId((long) viajeId),
                response -> {
                    loaderDetalle.setVisibility(View.GONE);
                    Log.d(TAG, "JSON VIAJE: " + response.toString());
                    try { procesarRespuestaViaje(response); }
                    catch (Exception e) {
                        Log.e(TAG, "Error procesando viaje", e);
                        Toast.makeText(this, "Error cargando datos", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    loaderDetalle.setVisibility(View.GONE);
                    Toast.makeText(this, "Error de conexión", Toast.LENGTH_LONG).show();
                }
        );
    }

    private void procesarRespuestaViaje(JSONObject r) throws Exception {
        estadoViaje      = r.optString("estado", "DESCONOCIDO").trim().toUpperCase();
        cuposTotales     = r.optInt("cuposTotales",     0);
        cuposDisponibles = r.optInt("cuposDisponibles", 0);

        // ── Ruta ─────────────────────────────────────────────────────────────
        JSONObject ruta = r.optJSONObject("ruta");
        if (ruta != null) {
            rutaId        = ruta.optInt("idRuta", 0);
            origenActual  = ruta.optString("origen",  "Origen");
            destinoActual = ruta.optString("destino", "Destino");
            latOrigen     = ruta.optDouble("latOrigen",  2.4419);
            lngOrigen     = ruta.optDouble("lngOrigen",  -76.6063);
            latDestino    = ruta.optDouble("latDestino", 2.4419);
            lngDestino    = ruta.optDouble("lngDestino", -76.6063);
            if (txtDistancia != null)
                txtDistancia.setText(String.format("%.1f km", ruta.optDouble("distancia", 0)));
            if (txtDuracion != null)
                txtDuracion.setText(String.format("%.0f min", ruta.optDouble("duracion", 0)));
        } else {
            rutaId        = r.optInt("idRuta", 0);
            origenActual  = r.optString("origen",  "Origen");
            destinoActual = r.optString("destino", "Destino");
            latOrigen     = r.optDouble("latOrigen",  2.4419);
            lngOrigen     = r.optDouble("lngOrigen",  -76.6063);
            latDestino    = r.optDouble("latDestino", 2.4419);
            lngDestino    = r.optDouble("lngDestino", -76.6063);
        }

        if (txtPrecio != null)
            txtPrecio.setText("$ " + String.format("%.0f", r.optDouble("precio", 0)));

        // ── Conductor ────────────────────────────────────────────────────────
        JSONObject conductor = r.optJSONObject("conductor");
        if (conductor != null) {
            idConductorViaje      = extractId(conductor);
            nombreConductorViaje  = extractNombre(conductor);
            if (nombreConductorViaje.isEmpty()) {
                JSONObject usuario = conductor.optJSONObject("usuario");
                if (usuario != null) nombreConductorViaje = extractNombre(usuario);
            }
            if (nombreConductorViaje.isEmpty() && idConductorViaje > 0)
                cargarNombreConductorDesdeAPI(idConductorViaje);
        } else {
            idConductorViaje     = r.optInt("idConductor", r.optInt("conductorId", -1));
            nombreConductorViaje = r.optString("nombreConductor", "");
            if (nombreConductorViaje.isEmpty() && idConductorViaje > 0)
                cargarNombreConductorDesdeAPI(idConductorViaje);
        }
        nombreContactoChat = nombreConductorViaje;

        // ── Pasajero ─────────────────────────────────────────────────────────
        JSONObject pasajero = r.optJSONObject("pasajero");
        if (pasajero != null) {
            idPasajeroViaje     = extractId(pasajero);
            nombrePasajeroViaje = extractNombre(pasajero);
        }

        idContactoChat = esConductor ? idPasajeroViaje : idConductorViaje;

        // ── Vehículo ─────────────────────────────────────────────────────────
        JSONObject vehiculo = r.optJSONObject("vehiculo");
        if (vehiculo != null && txtVehiculo != null)
            txtVehiculo.setText("🚘 " + vehiculo.optString("marca", "") + " "
                    + vehiculo.optString("modelo", "") + " • "
                    + vehiculo.optString("placa",  ""));

        // ── Mostrar nombre conductor en UI ───────────────────────────────────
        actualizarNombreConductorUI();

        txtRuta.setText("📍 " + origenActual + " → " + destinoActual);
        txtEstado.setText(obtenerEtiquetaEstado(estadoViaje));
        actualizarChipsCupos(cuposTotales, cuposDisponibles);
        configurarBotonesYFlujo(r);
        cargarParadasRuta();
        iniciarPollingCupos();

        // ── Cargar reservas (conductor) o info mi reserva (pasajero) ─────────
        if (esConductor) {
            cargarReservasParaConductor();
        } else {
            cargarMiReservaParaPasajero();
        }
    }

    /** Muestra el nombre del conductor en la UI con formato claro */
    private void actualizarNombreConductorUI() {
        if (txtConductor == null) return;
        String nombre = nombreConductorViaje.isEmpty() ? "Conductor" : nombreConductorViaje;
        // Si el usuario actual ES el conductor, lo indica
        if (esConductor && idConductorViaje == session.getIdUsuario()) {
            txtConductor.setText("🚗 Conductor: Tú (" + nombre + ")");
        } else {
            txtConductor.setText("🚗 Conductor: " + nombre);
        }
    }

    private void cargarNombreConductorDesdeAPI(int idConductor) {
        if (idConductor <= 0) return;
        ConexionApi.getInstance(this).getObject(
                Constantes.USUARIOS + "/" + idConductor,
                response -> {
                    String nombre = extractNombre(response);
                    if (!nombre.isEmpty()) {
                        nombreConductorViaje = nombre;
                        nombreContactoChat   = nombre;
                        runOnUiThread(this::actualizarNombreConductorUI);
                    }
                },
                error -> Log.e(TAG, "Error cargando nombre conductor")
        );
    }

    // =========================================================================
    //  CARD PASAJEROS — visible solo para el CONDUCTOR
    // =========================================================================

    /**
     * Llama a GET /api/viajes/{id}/reservas-detalle y muestra
     * cada pasajero con su nombre, cupos y parada de bajada.
     */
    private void cargarReservasParaConductor() {
        if (!esConductor || cardPasajeros == null) return;

        ConexionApi.getInstance(this).getObject(
                Constantes.viajeReservasDetalle((long) viajeId),
                response -> runOnUiThread(() -> {
                    try {
                        // El backend puede devolver array directo o dentro de "reservas"
                        JSONArray reservas = response.optJSONArray("reservas");
                        if (reservas == null) reservas = response.optJSONArray("content");
                        if (reservas == null) reservas = response.optJSONArray("data");
                        // Si el response mismo es un array (JSONArray a JSONObject workaround)
                        if (reservas == null && response.has("length")) {
                            // edge case: no array key
                        }

                        if (reservas == null || reservas.length() == 0) {
                            cardPasajeros.setVisibility(View.GONE);
                            return;
                        }

                        layoutListaPasajeros.removeAllViews();
                        int totalAsientos = 0;

                        for (int i = 0; i < reservas.length(); i++) {
                            JSONObject res = reservas.getJSONObject(i);
                            String estado  = res.optString("estado", "").toUpperCase();
                            // Solo mostrar reservas activas o confirmadas
                            if (estado.equals("CANCELADA") || estado.equals("CANCELADO")) continue;

                            // Nombre del pasajero
                            String nombrePas = "";
                            JSONObject pasObj = res.optJSONObject("pasajero");
                            if (pasObj != null) nombrePas = extractNombre(pasObj);
                            if (nombrePas.isEmpty()) nombrePas = res.optString("nombrePasajero", "");
                            if (nombrePas.isEmpty()) nombrePas = res.optString("nombre", "Pasajero " + (i + 1));

                            // Cupos / asientos
                            int asientos = res.optInt("numeroAsientos",
                                    res.optInt("asientos",
                                            res.optInt("cantidadAsientos", 1)));
                            totalAsientos += asientos;

                            // Parada de bajada
                            String nombreParada = res.optString("nombreParada", destinoActual);

                            agregarFilaPasajero(nombrePas, asientos, nombreParada, estado);
                        }

                        if (layoutListaPasajeros.getChildCount() == 0) {
                            cardPasajeros.setVisibility(View.GONE);
                        } else {
                            txtTotalPasajeros.setText(
                                    layoutListaPasajeros.getChildCount() + " pasajero(s) · "
                                            + totalAsientos + " asiento(s)");
                            cardPasajeros.setVisibility(View.VISIBLE);
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error mostrando reservas conductor", e);
                    }
                }),
                error -> Log.e(TAG, "Error cargando reservas conductor")
        );
    }

    /** Agrega una fila de pasajero a la card del conductor */
    private void agregarFilaPasajero(String nombre, int asientos, String parada, String estado) {
        float d   = getResources().getDisplayMetrics().density;
        int   p12 = (int)(12 * d);
        int   p8  = (int)(8  * d);
        int   p6  = (int)(6  * d);
        int   p4  = (int)(4  * d);

        MaterialCardView fila = new MaterialCardView(this);
        LinearLayout.LayoutParams lpFila = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFila.setMargins(0, p4, 0, p4);
        fila.setLayoutParams(lpFila);
        fila.setRadius(12 * d);
        fila.setCardElevation(2 * d);
        fila.setCardBackgroundColor(Color.WHITE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p12, p8, p12, p8);

        // Fila 1: nombre + badge asientos
        LinearLayout fila1 = new LinearLayout(this);
        fila1.setOrientation(LinearLayout.HORIZONTAL);
        fila1.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tvNombre = new TextView(this);
        tvNombre.setText("🧑 " + nombre);
        tvNombre.setTextSize(14f);
        tvNombre.setTypeface(null, android.graphics.Typeface.BOLD);
        tvNombre.setTextColor(Color.parseColor("#004D40"));
        tvNombre.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fila1.addView(tvNombre);

        // Badge de asientos
        TextView tvAsientos = new TextView(this);
        tvAsientos.setText("💺 " + asientos + (asientos == 1 ? " asiento" : " asientos"));
        tvAsientos.setTextSize(11f);
        tvAsientos.setTypeface(null, android.graphics.Typeface.BOLD);
        tvAsientos.setTextColor(Color.parseColor("#1565C0"));
        tvAsientos.setPadding(p8, p4, p8, p4);
        GradientDrawable bgAsientos = new GradientDrawable();
        bgAsientos.setShape(GradientDrawable.RECTANGLE);
        bgAsientos.setCornerRadius(20 * d);
        bgAsientos.setColor(Color.parseColor("#E3F2FD"));
        tvAsientos.setBackground(bgAsientos);
        fila1.addView(tvAsientos);

        inner.addView(fila1);

        // Fila 2: parada de bajada
        TextView tvParada = new TextView(this);
        tvParada.setText("🔵 Baja en: " + parada);
        tvParada.setTextSize(12f);
        tvParada.setTextColor(Color.parseColor("#00695C"));
        LinearLayout.LayoutParams lpParada = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpParada.topMargin = p4;
        tvParada.setLayoutParams(lpParada);
        inner.addView(tvParada);

        // Badge de estado
        TextView tvEstado = new TextView(this);
        String etiqueta;
        int colorEst;
        switch (estado) {
            case "ACTIVA": case "CONFIRMADA": etiqueta = "✅ Confirmada"; colorEst = 0xFF2E7D32; break;
            case "PENDIENTE": etiqueta = "⏳ Pendiente"; colorEst = 0xFFE65100; break;
            case "EN_CURSO": case "INICIADO": etiqueta = "🚗 En curso"; colorEst = 0xFF1565C0; break;
            default: etiqueta = "📌 " + estado; colorEst = 0xFF546E7A;
        }
        tvEstado.setText(etiqueta);
        tvEstado.setTextSize(11f);
        tvEstado.setTextColor(colorEst);
        LinearLayout.LayoutParams lpEst = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpEst.topMargin = p4;
        tvEstado.setLayoutParams(lpEst);
        inner.addView(tvEstado);

        fila.addView(inner);
        layoutListaPasajeros.addView(fila);
    }

    // =========================================================================
    //  CARD "MI RESERVA" — visible solo para el PASAJERO
    // =========================================================================

    /**
     * Llama a GET /api/reservas/mis-reservas y busca la reserva
     * de este viaje para mostrarla al pasajero con el nombre del conductor.
     */
    private void cargarMiReservaParaPasajero() {
        if (esConductor || cardMiReserva == null) return;

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_RESERVAS,
                response -> {
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject reserva = response.optJSONObject(i);
                        if (reserva == null) continue;

                        // Buscar la reserva de este viaje
                        int idViajeRes = reserva.optInt("idViaje", 0);
                        if (idViajeRes == 0) {
                            JSONObject viajeObj = reserva.optJSONObject("viaje");
                            if (viajeObj != null)
                                idViajeRes = viajeObj.optInt("idViaje", viajeObj.optInt("id", 0));
                        }
                        if (idViajeRes != viajeId) continue;

                        // Encontramos la reserva de este viaje
                        final JSONObject miReserva = reserva;
                        runOnUiThread(() -> mostrarCardMiReserva(miReserva));
                        return;
                    }
                    // Si no encontró reserva activa, ocultar la card
                    runOnUiThread(() -> {
                        if (cardMiReserva != null) cardMiReserva.setVisibility(View.GONE);
                    });
                },
                error -> Log.e(TAG, "Error cargando mis reservas")
        );
    }

    /** Rellena y muestra la card "Mi reserva" para el pasajero */
    private void mostrarCardMiReserva(JSONObject reserva) {
        if (cardMiReserva == null || txtMiReservaInfo == null) return;

        String estado       = reserva.optString("estado", "").toUpperCase();
        String nombreParada = reserva.optString("nombreParada", destinoActual);
        int    asientos     = reserva.optInt("numeroAsientos", reserva.optInt("asientos", 1));
        String codigo       = reserva.optString("codigoReserva", reserva.optString("codigo", ""));

        // Nombre del conductor
        String nombreCond = nombreConductorViaje.isEmpty() ? "Conductor" : nombreConductorViaje;

        StringBuilder sb = new StringBuilder();
        sb.append("🚗 Conductor: ").append(nombreCond).append("\n");
        sb.append("💺 Asientos reservados: ").append(asientos).append("\n");
        sb.append("🔵 Bajarás en: ").append(nombreParada);
        if (!codigo.isEmpty()) sb.append("\n📋 Código: ").append(codigo);

        txtMiReservaInfo.setText(sb.toString());

        // Color según estado
        switch (estado) {
            case "ACTIVA": case "CONFIRMADA":
                cardMiReserva.setCardBackgroundColor(Color.parseColor("#E8F5E9")); break;
            case "EN_CURSO": case "INICIADO":
                cardMiReserva.setCardBackgroundColor(Color.parseColor("#E3F2FD")); break;
            case "CANCELADA": case "CANCELADO":
                cardMiReserva.setVisibility(View.GONE);
                return;
            default:
                cardMiReserva.setCardBackgroundColor(Color.parseColor("#FFF9C4"));
        }
        cardMiReserva.setVisibility(View.VISIBLE);
    }

    // =========================================================================
    //  HELPERS ID / NOMBRE
    // =========================================================================
    private int extractId(JSONObject obj) {
        if (obj == null) return -1;
        for (String key : new String[]{"id","idUsuarios","idUsuario","userId","conductorId",
                "pasajeroId","idConductor","idPasajero","user_id"}) {
            int val = obj.optInt(key, -1);
            if (val > 0) return val;
        }
        return -1;
    }

    private String extractNombre(JSONObject obj) {
        if (obj == null) return "";
        for (String key : new String[]{"nombre","nombreCompleto","name","fullName","nombreUsuario",
                "displayName","userName","nombres","primerNombre","firstName",
                "apellido","apellidos","lastName","nombreConductor","nombrePasajero"}) {
            String val = obj.optString(key, "");
            if (!val.isEmpty() && !val.equals("null")) return val;
        }
        String n = obj.optString("nombres",""), a = obj.optString("apellidos","");
        if (!n.isEmpty() || !a.isEmpty()) return (n + " " + a).trim();
        String fn = obj.optString("firstName",""), ln = obj.optString("lastName","");
        if (!fn.isEmpty() || !ln.isEmpty()) return (fn + " " + ln).trim();
        return "";
    }

    // =========================================================================
    //  BOTONES
    // =========================================================================
    private void configurarBotonesYFlujo(JSONObject r) {
        btnReservar.setVisibility(View.GONE);
        btnIniciar.setVisibility(View.GONE);
        btnFinalizar.setVisibility(View.GONE);
        btnAgregarParada.setVisibility(View.GONE);
        if (btnMensajeConductor != null) btnMensajeConductor.setVisibility(View.GONE);

        if (esConductor) {
            int totalRes = r.optInt("totalReservas", 0);
            boolean iniciable = estadoViaje.equals("CREADO")
                    || estadoViaje.equals("PROGRAMADO")
                    || estadoViaje.equals("DISPONIBLE");
            if (iniciable && totalRes > 0) btnIniciar.setVisibility(View.VISIBLE);
            if (estadoViaje.equals("INICIADO")) {
                btnFinalizar.setVisibility(View.VISIBLE);
                btnAgregarParada.setVisibility(View.VISIBLE);
                iniciarPollingUbicacionPasajeros();
            }
            if (btnMensajeConductor != null && idPasajeroViaje > 0) {
                String labelPas = nombrePasajeroViaje.isEmpty() ? "el Pasajero" : nombrePasajeroViaje;
                btnMensajeConductor.setText("💬  Chat con " + labelPas);
                btnMensajeConductor.setVisibility(View.VISIBLE);
            }
        } else {
            boolean puedeReservar = (estadoViaje.equals("CREADO")
                    || estadoViaje.equals("PROGRAMADO")
                    || estadoViaje.equals("DISPONIBLE"))
                    && cuposDisponibles > 0;
            if (puedeReservar) btnReservar.setVisibility(View.VISIBLE);
            if (estadoViaje.equals("INICIADO")) verificarReservaPasajero();
            if (btnMensajeConductor != null) {
                String labelCond = nombreConductorViaje.isEmpty() ? "el Conductor" : nombreConductorViaje;
                btnMensajeConductor.setText("💬  Chat con " + labelCond);
                btnMensajeConductor.setVisibility(View.VISIBLE);
            }
        }
    }

    // =========================================================================
    //  CHAT
    // =========================================================================
    private void abrirOCrearChat() {
        if (idContactoChat <= 0) {
            loaderDetalle.setVisibility(View.VISIBLE);
            ConexionApi.getInstance(this).getObject(Constantes.viajePorId((long) viajeId),
                    response -> {
                        loaderDetalle.setVisibility(View.GONE);
                        JSONObject cond = response.optJSONObject("conductor");
                        JSONObject pas  = response.optJSONObject("pasajero");
                        if (cond != null) {
                            idConductorViaje     = extractId(cond);
                            nombreConductorViaje = extractNombre(cond);
                            nombreContactoChat   = nombreConductorViaje;
                        }
                        if (pas != null) {
                            idPasajeroViaje     = extractId(pas);
                            nombrePasajeroViaje = extractNombre(pas);
                        }
                        idContactoChat = esConductor ? idPasajeroViaje : idConductorViaje;
                        if (idContactoChat > 0) iniciarConversacion();
                        else Toast.makeText(this, "No se pudo identificar al contacto", Toast.LENGTH_LONG).show();
                    },
                    error -> { loaderDetalle.setVisibility(View.GONE); Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show(); }
            );
            return;
        }
        iniciarConversacion();
    }

    private void iniciarConversacion() {
        int    miId        = session.getIdUsuario();
        int    idPasajero  = esConductor ? miId           : idContactoChat;
        int    idCond      = esConductor ? idContactoChat : idConductorViaje;
        String nombre      = esConductor
                ? (nombrePasajeroViaje.isEmpty()  ? "Pasajero"  : nombrePasajeroViaje)
                : (nombreConductorViaje.isEmpty() ? "Conductor" : nombreConductorViaje);

        loaderDetalle.setVisibility(View.VISIBLE);
        JSONObject body = new JSONObject();
        try {
            body.put("idViaje",     viajeId);
            body.put("idPasajero",  idPasajero);
            body.put("idConductor", idCond);
        } catch (JSONException e) { loaderDetalle.setVisibility(View.GONE); return; }

        final String nombreFinal = nombre;
        ConexionApi.getInstance(this).post(Constantes.CHAT_CONVERSACIONES, body,
                response -> {
                    loaderDetalle.setVisibility(View.GONE);
                    long idConv = extraerIdConversacion(response);
                    if (idConv > 0) navegarAlChat(idConv, nombreFinal);
                    else Toast.makeText(this, "Error al crear conversación", Toast.LENGTH_SHORT).show();
                },
                error -> { loaderDetalle.setVisibility(View.GONE); buscarConversacionExistente(idPasajero, idCond, nombreFinal); }
        );
    }

    private void buscarConversacionExistente(int idPasajero, int idConductor, String nombreFinal) {
        String url = Constantes.CHAT_CONVERSACIONES
                + "?idViaje=" + viajeId + "&idPasajero=" + idPasajero + "&idConductor=" + idConductor;
        ConexionApi.getInstance(this).getObject(url,
                response -> {
                    long idConv = extraerIdConversacion(response);
                    if (idConv <= 0) {
                        for (String key : new String[]{"content","conversaciones","data"}) {
                            JSONArray arr = response.optJSONArray(key);
                            if (arr != null && arr.length() > 0) {
                                idConv = extraerIdConversacion(arr.optJSONObject(0));
                                if (idConv > 0) break;
                            }
                        }
                    }
                    if (idConv > 0) navegarAlChat(idConv, nombreFinal);
                    else Toast.makeText(this, "No se pudo abrir el chat", Toast.LENGTH_LONG).show();
                },
                error -> Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show()
        );
    }

    private long extraerIdConversacion(JSONObject r) {
        if (r == null) return -1;
        long id;
        id = r.optLong("id",             -1); if (id > 0) return id;
        id = r.optLong("idConversacion", -1); if (id > 0) return id;
        id = r.optLong("conversacionId", -1); if (id > 0) return id;
        JSONObject data = r.optJSONObject("data");
        if (data != null) { id = data.optLong("id", -1); if (id > 0) return id; }
        JSONObject conv = r.optJSONObject("conversacion");
        if (conv != null) { id = conv.optLong("id", -1); if (id > 0) return id; }
        return -1;
    }

    private void navegarAlChat(long idConversacion, String nombreContacto) {
        Intent i = new Intent(this, Chat.class);
        i.putExtra("idConversacion", idConversacion);
        i.putExtra("nombre",         nombreContacto);
        startActivity(i);
    }

    // =========================================================================
    //  CUPOS
    // =========================================================================
    private void actualizarChipsCupos(int total, int disponibles) {
        if (layoutCupos == null) return;
        runOnUiThread(() -> {
            layoutCupos.removeAllViews();
            float d      = getResources().getDisplayMetrics().density;
            int dpSize   = (int)(28 * d);
            int dpMargin = (int)(6  * d);
            for (int i = 0; i < total; i++) {
                boolean libre  = i < disponibles;
                View    circle = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpSize, dpSize);
                lp.setMargins(dpMargin, 0, dpMargin, 0);
                circle.setLayoutParams(lp);
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.OVAL);
                shape.setColor(Color.parseColor(libre ? COLOR_CUPO_LIBRE : COLOR_CUPO_OCUP));
                shape.setStroke((int)(2 * d), Color.parseColor(libre ? "#388E3C" : "#C62828"));
                circle.setBackground(shape);
                layoutCupos.addView(circle);
            }
        });
    }

    private void iniciarPollingCupos() {
        if (cuposActivo) return;
        cuposActivo   = true;
        cuposRunnable = new Runnable() {
            @Override public void run() {
                if (!cuposActivo) return;
                refrescarCuposYEstado();
                cuposHandler.postDelayed(this, POLLING_CUPOS_MS);
            }
        };
        cuposHandler.postDelayed(cuposRunnable, POLLING_CUPOS_MS);
    }

    private void refrescarCuposYEstado() {
        ConexionApi.getInstance(this).getObject(Constantes.viajePorId((long) viajeId),
                response -> {
                    int    nd = response.optInt("cuposDisponibles", cuposDisponibles);
                    int    nt = response.optInt("cuposTotales",     cuposTotales);
                    String ne = response.optString("estado", estadoViaje).trim().toUpperCase();
                    if (nd != cuposDisponibles || nt != cuposTotales) {
                        cuposDisponibles = nd; cuposTotales = nt;
                        actualizarChipsCupos(cuposTotales, cuposDisponibles);
                        if (esConductor) cargarReservasParaConductor(); // refrescar lista pasajeros
                        runOnUiThread(() -> {
                            if (!esConductor)
                                btnReservar.setVisibility(cuposDisponibles > 0 ? View.VISIBLE : View.GONE);
                        });
                    }
                    if (!ne.equals(estadoViaje)) {
                        estadoViaje = ne;
                        runOnUiThread(() -> txtEstado.setText(obtenerEtiquetaEstado(estadoViaje)));
                        if (ne.equals("INICIADO") || ne.equals("FINALIZADO")) cargarDetalleViaje();
                    }
                },
                error -> {}
        );
    }

    // =========================================================================
    //  PARADAS DE LA RUTA
    // =========================================================================
    private void cargarParadasRuta() {
        if (rutaId == 0) { map.postDelayed(this::dibujarRutaConductor, 800); return; }
        ConexionApi.getInstance(this).getObject(Constantes.paradasPorRuta((long) rutaId),
                response -> {
                    try {
                        JSONArray paradas = response.optJSONArray("paradas");
                        paradasRuta.clear();
                        ArrayList<String> nombres = new ArrayList<>();
                        if (paradas != null)
                            for (int i = 0; i < paradas.length(); i++) {
                                JSONObject p = paradas.getJSONObject(i);
                                paradasRuta.add(p);
                                nombres.add(p.optString("nombre", "Parada " + (i + 1)));
                            }
                        rvHistorialParadas.setAdapter(new ParadaAdapter(nombres, origenActual, destinoActual));
                        cardHistorial.setVisibility(nombres.isEmpty() ? View.GONE : View.VISIBLE);
                    } catch (Exception e) { Log.e(TAG, "Error paradas", e); }
                    map.postDelayed(this::dibujarRutaConductor, 800);
                },
                error -> { cardHistorial.setVisibility(View.GONE); map.postDelayed(this::dibujarRutaConductor, 800); }
        );
    }

    // =========================================================================
    //  BOTTOM SHEET RESERVA
    // =========================================================================
    private void mostrarBottomSheetReserva() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.BottomSheetTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_reserva, null);
        sheet.setContentView(view);

        RecyclerView   rvParadas    = view.findViewById(R.id.rv_paradas_sheet);
        MaterialButton btnConfirmar = view.findViewById(R.id.btn_confirmar_reserva);
        TextView       txtTitulo    = view.findViewById(R.id.txt_titulo_sheet);
        TextView       txtRutaSheet = view.findViewById(R.id.txt_ruta_sheet);

        // Mostrar conductor al pasajero en el sheet
        String nombreCond = nombreConductorViaje.isEmpty() ? "Conductor" : nombreConductorViaje;
        txtTitulo.setText("Reservar viaje con " + nombreCond);
        txtRutaSheet.setText("📍 " + origenActual + " → " + destinoActual
                + "\n💺 " + cuposDisponibles + " cupo(s) disponible(s)");

        ArrayList<String> opciones = new ArrayList<>();
        for (JSONObject p : paradasRuta) opciones.add("🔵 " + p.optString("nombre", "Parada"));
        opciones.add("🏁 " + destinoActual + " (Destino final)");

        final int[] sel = {-1};
        ParadaSeleccionAdapter adapter = new ParadaSeleccionAdapter(opciones, idx -> {
            sel[0] = idx;
            paradaPersonalizadaPoint  = null;
            paradaPersonalizadaNombre = "";
            btnConfirmar.setEnabled(true);
            btnConfirmar.setAlpha(1f);
        });
        rvParadas.setLayoutManager(new LinearLayoutManager(this));
        rvParadas.setAdapter(adapter);

        ViewGroup contenedor = encontrarContenedorPrincipal(view);
        if (contenedor != null) agregarCampoParadaEscrita(contenedor, btnConfirmar);

        btnConfirmar.setEnabled(false);
        btnConfirmar.setAlpha(0.5f);
        btnConfirmar.setOnClickListener(v -> {
            if (paradaPersonalizadaPoint != null && !paradaPersonalizadaNombre.isEmpty()) {
                sheet.dismiss();
                confirmarReservaPersonalizada(paradaPersonalizadaNombre, paradaPersonalizadaPoint);
            } else if (sel[0] >= 0) {
                paradaSeleccionadaPasajero = sel[0] < paradasRuta.size()
                        ? paradasRuta.get(sel[0]) : null;
                sheet.dismiss();
                confirmarReserva(opciones.get(sel[0]));
            }
        });
        sheet.show();
    }

    private ViewGroup encontrarContenedorPrincipal(View rootView) {
        if (rootView instanceof LinearLayout) return (ViewGroup) rootView;
        if (rootView instanceof android.widget.ScrollView) {
            android.widget.ScrollView sv = (android.widget.ScrollView) rootView;
            if (sv.getChildCount() > 0 && sv.getChildAt(0) instanceof ViewGroup)
                return (ViewGroup) sv.getChildAt(0);
        }
        if (rootView instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) rootView;
            for (int i = 0; i < vg.getChildCount(); i++)
                if (vg.getChildAt(i) instanceof LinearLayout) return (ViewGroup) vg.getChildAt(i);
            return vg;
        }
        return null;
    }

    private void agregarCampoParadaEscrita(ViewGroup contenedor, MaterialButton btnConfirmar) {
        float d   = getResources().getDisplayMetrics().density;
        int   p16 = (int)(16 * d);
        int   p8  = (int)(8  * d);
        int   p4  = (int)(4  * d);

        TextView lblSep = new TextView(this);
        lblSep.setText("─── o escribe tu parada ───");
        lblSep.setTextColor(0xFF90A4AE);
        lblSep.setTextSize(12f);
        lblSep.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSep.setMargins(p16, p8, p16, p4);
        lblSep.setLayoutParams(lpSep);

        EditText editParada = new EditText(this);
        editParada.setHint("Ej: Carrera 5 con Calle 10");
        editParada.setTextSize(14f);
        editParada.setTextColor(0xFF004D40);
        editParada.setHintTextColor(0xFF90A4AE);
        editParada.setSingleLine(true);
        LinearLayout.LayoutParams lpEdit = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpEdit.setMargins(p16, p4, p16, p8);
        editParada.setLayoutParams(lpEdit);
        editParada.setPadding(p16, p8, p16, p8);
        GradientDrawable bgEdit = new GradientDrawable();
        bgEdit.setShape(GradientDrawable.RECTANGLE);
        bgEdit.setCornerRadius(12 * d);
        bgEdit.setStroke((int)(1.5f * d), 0xFF00897B);
        bgEdit.setColor(0xFFF0FFFE);
        editParada.setBackground(bgEdit);

        Button btnBuscar = new Button(this);
        btnBuscar.setText("🔍  Buscar y usar esta parada");
        btnBuscar.setAllCaps(false);
        btnBuscar.setTextColor(Color.WHITE);
        btnBuscar.setTextSize(13f);
        GradientDrawable bgBtn = new GradientDrawable();
        bgBtn.setShape(GradientDrawable.RECTANGLE);
        bgBtn.setCornerRadius(12 * d);
        bgBtn.setColor(0xFF00897B);
        btnBuscar.setBackground(bgBtn);
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(48 * d));
        lpBtn.setMargins(p16, 0, p16, p4);
        btnBuscar.setLayoutParams(lpBtn);

        TextView tvResultado = new TextView(this);
        tvResultado.setTextSize(12f);
        tvResultado.setTextColor(0xFF00897B);
        tvResultado.setVisibility(View.GONE);
        LinearLayout.LayoutParams lpRes = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpRes.setMargins(p16, 0, p16, p8);
        tvResultado.setLayoutParams(lpRes);

        contenedor.addView(lblSep);
        contenedor.addView(editParada);
        contenedor.addView(btnBuscar);
        contenedor.addView(tvResultado);

        btnBuscar.setOnClickListener(v -> {
            String texto = editParada.getText().toString().trim();
            if (texto.isEmpty()) { Toast.makeText(this, "Escribe una dirección primero", Toast.LENGTH_SHORT).show(); return; }
            tvResultado.setText("🔄 Buscando...");
            tvResultado.setTextColor(0xFF546E7A);
            tvResultado.setVisibility(View.VISIBLE);
            btnBuscar.setEnabled(false);
            new Thread(() -> {
                try {
                    GeoPoint pt = geocodificar(texto);
                    paradaPersonalizadaPoint  = pt;
                    paradaPersonalizadaNombre = texto;
                    runOnUiThread(() -> {
                        tvResultado.setText("✅ Parada encontrada: " + texto);
                        tvResultado.setTextColor(0xFF2E7D32);
                        btnConfirmar.setEnabled(true);
                        btnConfirmar.setAlpha(1f);
                        btnBuscar.setEnabled(true);
                        bgEdit.setStroke((int)(2 * d), 0xFF2E7D32);
                        editParada.setBackground(bgEdit);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvResultado.setText("❌ No se encontró. Intenta con más detalle.");
                        tvResultado.setTextColor(0xFFC62828);
                        btnBuscar.setEnabled(true);
                        paradaPersonalizadaPoint  = null;
                        paradaPersonalizadaNombre = "";
                    });
                }
            }).start();
        });
    }

    // =========================================================================
    //  CONFIRMAR Y HACER RESERVA
    // =========================================================================
    private void confirmarReserva(String nombreParada) {
        String nombreCond = nombreConductorViaje.isEmpty() ? "el conductor" : nombreConductorViaje;
        new AlertDialog.Builder(this)
                .setTitle("Confirmar reserva")
                .setMessage("¿Reservar cupo con " + nombreCond + "?\n\nBajarás en: " + nombreParada)
                .setPositiveButton("Reservar", (d, w) -> hacerReserva(nombreParada, null))
                .setNegativeButton("Cancelar", null).show();
    }

    private void confirmarReservaPersonalizada(String nombreParada, GeoPoint punto) {
        String nombreCond = nombreConductorViaje.isEmpty() ? "el conductor" : nombreConductorViaje;
        new AlertDialog.Builder(this)
                .setTitle("Confirmar reserva")
                .setMessage("¿Reservar cupo con " + nombreCond + "?\n\nBajarás en: " + nombreParada)
                .setPositiveButton("Reservar", (d, w) -> hacerReserva(nombreParada, punto))
                .setNegativeButton("Cancelar", null).show();
    }

    private void hacerReserva(String nombreParada, GeoPoint puntoPersonalizado) {
        loaderDetalle.setVisibility(View.VISIBLE);
        try {
            JSONObject body = new JSONObject();
            body.put("idViaje",   viajeId);
            body.put("idUsuario", session.getIdUsuario());

            if (puntoPersonalizado != null) {
                body.put("nombreParada", nombreParada);
                body.put("latParada",    puntoPersonalizado.getLatitude());
                body.put("lngParada",    puntoPersonalizado.getLongitude());
            } else if (paradaSeleccionadaPasajero != null) {
                body.put("idParada",     paradaSeleccionadaPasajero.optInt("idParada", 0));
                body.put("nombreParada", paradaSeleccionadaPasajero.optString("nombre", nombreParada));
                body.put("latParada",    paradaSeleccionadaPasajero.optDouble("latitud",  latDestino));
                body.put("lngParada",    paradaSeleccionadaPasajero.optDouble("longitud", lngDestino));
            } else {
                body.put("nombreParada", destinoActual);
                body.put("latParada",    latDestino);
                body.put("lngParada",    lngDestino);
            }

            ConexionApi.getInstance(this).post(Constantes.RESERVAS, body,
                    response -> {
                        loaderDetalle.setVisibility(View.GONE);
                        double latP, lonP;
                        if (puntoPersonalizado != null) { latP = puntoPersonalizado.getLatitude(); lonP = puntoPersonalizado.getLongitude(); }
                        else if (paradaSeleccionadaPasajero != null) { latP = paradaSeleccionadaPasajero.optDouble("latitud", latDestino); lonP = paradaSeleccionadaPasajero.optDouble("longitud", lngDestino); }
                        else { latP = latDestino; lonP = lngDestino; }

                        String nombreCond = nombreConductorViaje.isEmpty() ? "el conductor" : nombreConductorViaje;
                        Toast.makeText(this,
                                "✅ Reservado con " + nombreCond + "\nBajarás en: " + nombreParada,
                                Toast.LENGTH_LONG).show();

                        trazarSegmentoHastaParada(latP, lonP, nombreParada);
                        refrescarCuposYEstado();
                        cargarMiReservaParaPasajero(); // actualizar card mi reserva
                        runOnUiThread(() -> btnReservar.setVisibility(View.GONE));
                    },
                    error -> {
                        loaderDetalle.setVisibility(View.GONE);
                        String msg = "Error al reservar";
                        if (error != null && error.networkResponse != null) {
                            int code = error.networkResponse.statusCode;
                            if      (code == 400) msg = "Datos inválidos o sin cupos";
                            else if (code == 409) msg = "Ya tienes una reserva en este viaje";
                            else if (code == 403) msg = "Sin permiso para reservar";
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
            );
        } catch (Exception e) { loaderDetalle.setVisibility(View.GONE); Log.e(TAG, "Excepción hacerReserva", e); }
    }

    private void verificarReservaPasajero() {
        ConexionApi.getInstance(this).getObject(
                Constantes.RESERVAS + "/pasajero/" + session.getIdUsuario() + "/viaje/" + viajeId,
                response -> {
                    String est = response.optString("estado", "");
                    if (est.equals("ACTIVA") || est.equals("CONFIRMADA")) {
                        String nomParada = response.optString("nombreParada", destinoActual);
                        double latP = response.optDouble("latParada", latDestino);
                        double lonP = response.optDouble("lngParada", lngDestino);
                        int idParada = response.optInt("idParada", -1);
                        for (JSONObject p : paradasRuta)
                            if (p.optInt("idParada", -2) == idParada) {
                                paradaSeleccionadaPasajero = p;
                                latP = p.optDouble("latitud",  latDestino);
                                lonP = p.optDouble("longitud", lngDestino);
                                break;
                            }
                        final double fLat = latP, fLon = lonP;
                        final String fNom = nomParada;
                        runOnUiThread(() -> {
                            trazarSegmentoHastaParada(fLat, fLon, fNom);
                            mostrarCardMiReserva(response);
                        });
                    }
                },
                error -> {}
        );
    }

    // =========================================================================
    //  PASAJEROS EN MAPA (conductor)
    // =========================================================================
    private void cargarReservasPasajeros() {
        if (!esConductor) return;
        ConexionApi.getInstance(this).getObject(
                Constantes.viajeReservasDetalle((long) viajeId),
                response -> {
                    try {
                        JSONArray reservas = response.optJSONArray("reservas");
                        if (reservas == null) return;
                        limpiarMarcadoresPasajeros();
                        for (int i = 0; i < reservas.length(); i++) {
                            JSONObject res = reservas.getJSONObject(i);
                            double lat = res.optDouble("latitud",  0);
                            double lon = res.optDouble("longitud", 0);
                            if (lat != 0 && lon != 0) {
                                String nombrePas = res.optString("nombrePasajero", "");
                                if (nombrePas.isEmpty()) {
                                    JSONObject pasObj = res.optJSONObject("pasajero");
                                    if (pasObj != null) nombrePas = extractNombre(pasObj);
                                }
                                if (nombrePas.isEmpty()) nombrePas = "Pasajero";
                                agregarMarcadorReservaPasajero(lat, lon, nombrePas,
                                        res.optString("nombreParada", "Destino"));
                            }
                        }
                        map.invalidate();
                    } catch (Exception e) { Log.e(TAG, "Error reservas mapa", e); }
                },
                error -> {}
        );
    }

    private void limpiarMarcadoresPasajeros() {
        for (Marker m : marcadoresPasajeros) map.getOverlays().remove(m);
        marcadoresPasajeros.clear();
    }

    private void agregarMarcadorReservaPasajero(double lat, double lon, String nombre, String parada) {
        runOnUiThread(() -> {
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(lat, lon));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle("🧑 " + nombre);
            m.setSnippet("Baja en: " + parada);
            m.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_myplaces));
            map.getOverlays().add(m);
            marcadoresPasajeros.add(m);
        });
    }

    private void iniciarPollingUbicacionPasajeros() {
        if (ubicActivo) return;
        ubicActivo   = true;
        ubicRunnable = new Runnable() {
            @Override public void run() {
                if (!ubicActivo) return;
                consultarUbicacionPasajeros();
                ubicHandler.postDelayed(this, POLLING_UBIC_MS);
            }
        };
        ubicHandler.post(ubicRunnable);
    }

    private void consultarUbicacionPasajeros() {
        ConexionApi.getInstance(this).getObject(
                Constantes.viajePorId((long) viajeId) + "/pasajeros-ubicacion",
                response -> {
                    try {
                        JSONArray pasajeros = response.optJSONArray("pasajeros");
                        if (pasajeros == null) return;
                        for (int i = 0; i < pasajeros.length(); i++) {
                            JSONObject p = pasajeros.getJSONObject(i);
                            double lat   = p.optDouble("latitud",  0);
                            double lon   = p.optDouble("longitud", 0);
                            if (lat != 0 && lon != 0)
                                agregarMarcadorPasajero(lat, lon,
                                        p.optString("nombre",       "Pasajero"),
                                        p.optString("nombreParada", ""));
                        }
                    } catch (Exception e) { Log.e(TAG, "Error ubicacion", e); }
                },
                error -> {}
        );
    }

    private void agregarMarcadorPasajero(double lat, double lon, String nombre, String parada) {
        runOnUiThread(() -> {
            Marker existing = null;
            for (Marker m : marcadoresPasajeros)
                if (m.getTitle() != null && m.getTitle().contains(nombre)) { existing = m; break; }
            if (existing != null) { existing.setPosition(new GeoPoint(lat, lon)); }
            else {
                Marker m = new Marker(map);
                m.setPosition(new GeoPoint(lat, lon));
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                m.setTitle("🧑 " + nombre);
                m.setSnippet("Baja en: " + parada);
                m.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_myplaces));
                map.getOverlays().add(m);
                marcadoresPasajeros.add(m);
            }
            map.invalidate();
        });
    }

    private void detenerTodoPolling() {
        cuposActivo = false; ubicActivo = false;
        if (cuposRunnable != null) cuposHandler.removeCallbacks(cuposRunnable);
        if (ubicRunnable  != null) ubicHandler.removeCallbacks(ubicRunnable);
    }

    // =========================================================================
    //  ACCIONES CONDUCTOR
    // =========================================================================
    private void cambiarEstado(String accion) {
        loaderDetalle.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).post(
                Constantes.viajePorId((long) viajeId) + "/" + accion, null,
                response -> { loaderDetalle.setVisibility(View.GONE); Toast.makeText(this, "✅ Viaje " + accion + "do", Toast.LENGTH_SHORT).show(); cargarDetalleViaje(); },
                error   -> { loaderDetalle.setVisibility(View.GONE); Toast.makeText(this, "Error al " + accion, Toast.LENGTH_LONG).show(); }
        );
    }

    private void confirmarFinalizar() {
        new AlertDialog.Builder(this)
                .setTitle("Finalizar viaje")
                .setMessage("¿Finalizar? Se liberarán todos los cupos.")
                .setPositiveButton("Finalizar", (d, w) -> finalizarViaje())
                .setNegativeButton("Cancelar", null).show();
    }

    private void finalizarViaje() {
        loaderDetalle.setVisibility(View.VISIBLE);
        detenerTodoPolling();
        ConexionApi.getInstance(this).post(
                Constantes.viajePorId((long) viajeId) + "/pasajeros-bajaron", null,
                r -> ConexionApi.getInstance(this).post(Constantes.viajeFinalizar((long) viajeId), null,
                        r2 -> { loaderDetalle.setVisibility(View.GONE); Toast.makeText(this, "✅ Viaje finalizado.", Toast.LENGTH_LONG).show(); cargarDetalleViaje(); },
                        e2 -> { loaderDetalle.setVisibility(View.GONE); Toast.makeText(this, "Error finalizando", Toast.LENGTH_LONG).show(); }),
                error -> cambiarEstado("finalizar")
        );
    }

    private void mostrarDialogoAgregarParada() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("➕ Agregar Parada");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        EditText inputNombre    = new EditText(this); inputNombre.setHint("Nombre");       layout.addView(inputNombre);
        EditText inputDireccion = new EditText(this); inputDireccion.setHint("Dirección"); layout.addView(inputDireccion);
        b.setView(layout);
        b.setPositiveButton("Agregar", (d, w) -> {
            String nom = inputNombre.getText().toString().trim();
            String dir = inputDireccion.getText().toString().trim();
            if (nom.isEmpty() || dir.isEmpty()) { Toast.makeText(this, "Complete todos los campos", Toast.LENGTH_SHORT).show(); return; }
            new Thread(() -> {
                try {
                    GeoPoint pt = geocodificar(dir);
                    runOnUiThread(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("nombre",   nom);
                            body.put("latitud",  pt.getLatitude());
                            body.put("longitud", pt.getLongitude());
                            ConexionApi.getInstance(this).post(Constantes.rutaParadas((long) rutaId), body,
                                    r -> { Toast.makeText(this, "✅ Parada agregada", Toast.LENGTH_SHORT).show(); cargarParadasRuta(); },
                                    e -> Toast.makeText(this, "Error agregando parada", Toast.LENGTH_LONG).show());
                        } catch (Exception ex) { Log.e(TAG, "Error body parada", ex); }
                    });
                } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Dirección no encontrada", Toast.LENGTH_LONG).show()); }
            }).start();
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    // =========================================================================
    //  MAPA
    // =========================================================================
    private void dibujarRutaConductor() {
        new Thread(() -> {
            try {
                GeoPoint origen  = new GeoPoint(latOrigen,  lngOrigen);
                GeoPoint destino = new GeoPoint(latDestino, lngDestino);
                ArrayList<GeoPoint> wp = new ArrayList<>();
                ArrayList<String>   nm = new ArrayList<>();
                for (JSONObject p : paradasRuta) {
                    wp.add(new GeoPoint(p.optDouble("latitud",  latOrigen),
                            p.optDouble("longitud", lngOrigen)));
                    nm.add(p.optString("nombre", "Parada"));
                }
                puntosRutaConductor = obtenerPuntosOsrm(buildOsrmUrl(origen, destino, wp));
                runOnUiThread(() -> dibujarEnMapa(puntosRutaConductor, origen, destino, wp, nm));
            } catch (Exception e) { Log.e(TAG, "Error dibujando ruta", e); }
        }).start();
    }

    private void trazarSegmentoHastaParada(double latP, double lonP, String nombre) {
        new Thread(() -> {
            try {
                String url = "https://router.project-osrm.org/route/v1/driving/"
                        + lngOrigen + "," + latOrigen + ";" + lonP + "," + latP
                        + "?overview=full&geometries=geojson";
                JSONObject res    = new JSONObject(peticionHttp(url));
                JSONArray  coords = res.getJSONArray("routes").getJSONObject(0)
                        .getJSONObject("geometry").getJSONArray("coordinates");
                ArrayList<GeoPoint> puntos = new ArrayList<>();
                for (int i = 0; i < coords.length(); i++) {
                    JSONArray c = coords.getJSONArray(i);
                    puntos.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                }
                runOnUiThread(() -> {
                    Polyline seg = new Polyline();
                    seg.setPoints(puntos); seg.setColor(Color.parseColor(COLOR_SEGMENTO)); seg.setWidth(9f);
                    map.getOverlays().add(seg);
                    Marker m = new Marker(map);
                    m.setPosition(new GeoPoint(latP, lonP));
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    m.setTitle("📍 Tu parada: " + nombre);
                    m.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_compass));
                    map.getOverlays().add(m);
                    map.getController().animateTo(new GeoPoint(latP, lonP));
                    map.invalidate();
                });
            } catch (Exception e) { Log.e(TAG, "Error segmento", e); }
        }).start();
    }

    private String buildOsrmUrl(GeoPoint o, GeoPoint d, ArrayList<GeoPoint> wp) {
        StringBuilder sb = new StringBuilder("https://router.project-osrm.org/route/v1/driving/");
        sb.append(o.getLongitude()).append(",").append(o.getLatitude());
        for (GeoPoint w : wp) sb.append(";").append(w.getLongitude()).append(",").append(w.getLatitude());
        sb.append(";").append(d.getLongitude()).append(",").append(d.getLatitude());
        return sb.append("?overview=full&geometries=geojson").toString();
    }

    private ArrayList<GeoPoint> obtenerPuntosOsrm(String url) throws Exception {
        JSONObject res    = new JSONObject(peticionHttp(url));
        JSONArray  coords = res.getJSONArray("routes").getJSONObject(0)
                .getJSONObject("geometry").getJSONArray("coordinates");
        ArrayList<GeoPoint> puntos = new ArrayList<>();
        for (int i = 0; i < coords.length(); i++) {
            JSONArray c = coords.getJSONArray(i);
            puntos.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
        }
        return puntos;
    }

    private void dibujarEnMapa(ArrayList<GeoPoint> puntos, GeoPoint origen, GeoPoint destino,
                               ArrayList<GeoPoint> paradas, ArrayList<String> nombres) {
        List<Overlay> overlays = map.getOverlays();
        for (int i = overlays.size() - 1; i >= 0; i--) {
            Overlay o = overlays.get(i);
            if (o instanceof Marker || o instanceof Polyline) overlays.remove(i);
        }
        if (myLocationOverlay != null && !overlays.contains(myLocationOverlay))
            overlays.add(myLocationOverlay);
        Polyline sombra = new Polyline(); sombra.setPoints(puntos); sombra.setColor(Color.parseColor("#33000000")); sombra.setWidth(18f); overlays.add(sombra);
        Polyline borde  = new Polyline(); borde.setPoints(puntos);  borde.setColor(Color.WHITE);                   borde.setWidth(14f); overlays.add(borde);
        Polyline linea  = new Polyline(); linea.setPoints(puntos);  linea.setColor(Color.parseColor(COLOR_RUTA));  linea.setWidth(10f); overlays.add(linea);
        Marker mO = new Marker(map); mO.setPosition(origen); mO.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); mO.setTitle("🟢 " + origenActual); mO.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_mylocation)); overlays.add(mO);
        for (int i = 0; i < paradas.size(); i++) {
            Marker m = new Marker(map); m.setPosition(paradas.get(i)); m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle("🔵 " + (i < nombres.size() ? nombres.get(i) : "Parada " + (i + 1)));
            m.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_add)); overlays.add(m);
        }
        Marker mD = new Marker(map); mD.setPosition(destino); mD.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); mD.setTitle("🔴 " + destinoActual); mD.setIcon(getResources().getDrawable(android.R.drawable.ic_dialog_map)); overlays.add(mD);
        map.post(() -> {
            try { map.zoomToBoundingBox(linea.getBounds(), true, 150); } catch (Exception ignored) {}
            map.invalidate();
            if (esConductor) map.postDelayed(this::cargarReservasPasajeros, 500);
        });
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private String obtenerEtiquetaEstado(String e) {
        switch (e) {
            case "CREADO":     return "📋 Estado: Disponible";
            case "PROGRAMADO": return "📅 Estado: Programado";
            case "DISPONIBLE": return "✅ Estado: Disponible";
            case "INICIADO":   return "🚗 Estado: En curso";
            case "FINALIZADO": return "🏁 Estado: Finalizado";
            case "CANCELADO":  return "❌ Estado: Cancelado";
            default:           return "📌 Estado: " + e;
        }
    }

    private GeoPoint geocodificar(String dir) throws Exception {
        String url = "https://nominatim.openstreetmap.org/search?q="
                + java.net.URLEncoder.encode(dir + ", Popayan, Colombia", "UTF-8")
                + "&format=json&limit=1";
        JSONArray arr = new JSONArray(peticionHttp(url));
        if (arr.length() == 0) {
            url = "https://nominatim.openstreetmap.org/search?q="
                    + java.net.URLEncoder.encode(dir + ", Colombia", "UTF-8") + "&format=json&limit=1";
            arr = new JSONArray(peticionHttp(url));
        }
        if (arr.length() == 0) throw new Exception("Dirección no encontrada: " + dir);
        JSONObject o = arr.getJSONObject(0);
        return new GeoPoint(o.getDouble("lat"), o.getDouble("lon"));
    }

    private String peticionHttp(String urlString) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Moviflexx-App/1.0");
            conn.setConnectTimeout(15_000); conn.setReadTimeout(15_000);
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close(); return sb.toString();
        } finally { if (conn != null) conn.disconnect(); }
    }

    // =========================================================================
    //  ADAPTERS
    // =========================================================================
    static class ParadaAdapter extends RecyclerView.Adapter<ParadaAdapter.VH> {
        private final ArrayList<String> items = new ArrayList<>();
        ParadaAdapter(ArrayList<String> paradas, String origen, String destino) {
            items.add("🟢 " + origen  + "  (Inicio)");
            for (String p : paradas) items.add("🔵 " + p);
            items.add("🔴 " + destino + "  (Destino)");
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(32, 20, 32, 20); tv.setTextSize(14f); tv.setTextColor(Color.parseColor("#004D40"));
            return new VH(tv);
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) { ((TextView) holder.itemView).setText(items.get(position)); }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder { VH(@NonNull View v) { super(v); } }
    }

    static class ParadaSeleccionAdapter extends RecyclerView.Adapter<ParadaSeleccionAdapter.VH> {
        interface OnSelect { void onSelect(int index); }
        private final ArrayList<String> items;
        private final OnSelect          callback;
        private       int               seleccionado = -1;
        ParadaSeleccionAdapter(ArrayList<String> items, OnSelect cb) { this.items = items; this.callback = cb; }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(parent.getContext());
            card.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            card.setCardElevation(4f); card.setRadius(16f); card.setUseCompatPadding(true); card.setClickable(true); card.setFocusable(true);
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(40, 32, 40, 32); tv.setTextSize(15f); tv.setTextColor(Color.parseColor("#004D40"));
            card.addView(tv); return new VH(card);
        }
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            MaterialCardView card = (MaterialCardView) holder.itemView;
            ((TextView) card.getChildAt(0)).setText(items.get(position));
            boolean sel = position == seleccionado;
            card.setCardBackgroundColor(sel ? Color.parseColor("#E0F2F1") : Color.WHITE);
            card.setStrokeColor(sel ? Color.parseColor("#00897B") : Color.parseColor("#E0E0E0"));
            card.setStrokeWidth(sel ? 4 : 1);
            final int pos = position;
            card.setOnClickListener(v -> { seleccionado = pos; notifyDataSetChanged(); callback.onSelect(pos); });
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder { VH(@NonNull View v) { super(v); } }
    }
}