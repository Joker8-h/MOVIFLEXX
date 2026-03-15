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
import android.view.MotionEvent;
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
import com.arlys.moviflexx.model.Manager.PrecioTramoPasajeroManager;

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
    private static final int    GPS_CONDUCTOR_POLLING_MS = 3000; // ← cada 3 seg para pasajero
    private static final int    LOCATION_PERMISSION_REQUEST = 1001;


    private static final int    COLOR_RUTA            = 0xFF009B8D;
    private static final int    COLOR_RUTA_WAYPOINT   = 0xFF7B1FA2;
    private static final int    COLOR_CONDUCTOR       = 0xFF1565C0;
    private static final int    COLOR_SEGMENTO_ACTIVO = 0xFFFF6F00;

    private static final String COLOR_LIBRE = "#4CAF50";
    private static final String COLOR_OCUP  = "#EF5350";
    private static final String COLOR_SEL   = "#FF9800";

    private static final String OSRM_URL        =
            "https://optimizacionofrutas-production.up.railway.app";
    private static final String OSRM_URL_PUBLIC =
            "https://router.project-osrm.org";

    private static final String EST_PENDIENTE          = "PENDIENTE";
    private static final String EST_CONFIRMADA         = "CONFIRMADA";
    private static final String EST_ESPERANDO_RECOGIDA = "ESPERANDO_RECOGIDA";
    private static final String EST_RECOGIDO           = "RECOGIDO";
    private static final String EST_COMPLETADO         = "COMPLETADO";
    private static final String EST_CANCELADO          = "CANCELADO";
    private long tiempoReserva = 0L; // timestamp cuando reservó

    private MaterialButton btnPagarViaje;
    private static final long TIEMPO_CAMBIO_MS = 60_000L; // 1 minuto
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    // IDs de marcadores
    private static final String MID_ORIGEN    = "m_origen";
    private static final String MID_DESTINO   = "m_destino";
    private static final String MID_PARADA    = "m_parada";    // bajada
    private static final String MID_SUBIDA    = "m_subida";    // ← punto de recogida
    private static final String MID_CONDUCTOR = "m_conductor";

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

    private static final int ROW_ID_ASIENTOS = 0x7F010002;
    private static final int ROW_ID_BAJADA   = 0x7F010003;
    private static final int ROW_ID_PRECIO   = 0x7F010004;

    // ── Datos del viaje ───────────────────────────────────────────────────────
    private int     viajeId, rutaId;
    private boolean esConductor;
    private String  origenActual = "", destinoActual = "", estadoViaje = "";
    private double  latOrigen, lngOrigen, latDestino, lngDestino;
    private int     cuposTotales = 0, cuposDisponibles = 0;
    private double  precioViaje  = 0;

    private double precioCalculadoPasajero = 0;
    private double  distanciaKm  = 0, duracionMin = 0;
    private int     indiceRuta   = 0;
    private String  miParadaBajada = "";
    private SessionManager session;

    private int    idPasajeroViaje      = -1;
    private int    idConductorViaje     = -1;
    private String nombreConductorViaje = "";
    private String nombrePasajeroViaje  = "";

    private final ArrayList<JSONObject>     paradasRuta = new ArrayList<>();
    private final ArrayList<ParadaDinamica> paradasDin  = new ArrayList<>();

    private final List<List<GeoPoint>> todasLasRutas    = new ArrayList<>();
    private       List<GeoPoint>       rutaActiva        = null;
    private       int                  indiceRutaActiva  = 0;
    private final List<RouteOption>    listaRouteOptions = new ArrayList<>();
    private String geojsonRuta = "";
    private ArrayList<GeoPoint> puntosRutaWaypoint = new ArrayList<>();

    private GeoPoint gpOrigen  = null;
    private GeoPoint gpDestino = null;
    private GeoPoint gpParada  = null;   // punto de BAJADA del pasajero
    private String   nombreParada = "";

    private void actualizarMarcadorConductor(GeoPoint nuevaPos) {
        actualizarMarcadorConductorSuave(nuevaPos);
    }



    // ── NUEVAS: punto de RECOGIDA (subida) del pasajero ──────────────────────
    private GeoPoint gpSubida             = null;
    private String   nombreSubidaPasajero = "";
    // ─────────────────────────────────────────────────────────────────────────

    private final ArrayList<GeoPoint> paradasPasajeros        = new ArrayList<>();
    private final ArrayList<String>   nombresPasajerosParadas = new ArrayList<>();

    private boolean yaReservo        = false;
    private int     cupoSeleccionado = -1;
    private int     idReservaActual  = -1;
    private String  estadoReserva    = "";

    private boolean coordsOrigenInvalidas  = false;
    private boolean coordsDestinoInvalidas = false;

    private boolean calificacionYaDisparada = false;  // ← AGREGAR AQUÍ

    // ── Polling general (estado del viaje) ───────────────────────────────────
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private Runnable      pollingRunnable;
    private boolean       pollingActivo = false;

    // ── Polling GPS conductor para el pasajero (cada 3 seg) ──────────────────
    private final Handler  gpsPollingHandler  = new Handler(Looper.getMainLooper());
    private Runnable       gpsPollingRunnable;
    private boolean        gpsPollingActivo   = false;

    // ── GPS propio del conductor ──────────────────────────────────────────────
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback            locationCallback;
    private boolean                     gpsActivo = false;
    private GeoPoint                    gpConductorActual = null;
    private Marker marcadorConductor = null;
    private GeoPoint gpConductorAnterior = null;

    // =========================================================================
    //  GPS CONDUCTOR (envío de posición desde el dispositivo del conductor)
    // =========================================================================
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
                runOnUiThread(() -> actualizarMarcadorConductorSuave(gpConductorActual));
                enviarUbicacionAlBackend(lat, lng);
            }
        };
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        Log.d(TAG, "✅ GPS conductor iniciado");
    }

    private void detenerGPS() {
        if (!gpsActivo || fusedLocationClient == null || locationCallback == null) return;
        fusedLocationClient.removeLocationUpdates(locationCallback);
        gpsActivo = false;
    }

    /**
     * Actualiza el marcador del conductor en el mapa.
     * Si soy pasajero, también actualiza el segmento activo (conductor→subida o conductor→bajada).
     */
    private void actualizarMarcadorConductorSuave(GeoPoint nuevaPos) {
        if (map == null || nuevaPos == null) return;

        // Si el marcador ya existe: moverlo suavemente con animación
        if (marcadorConductor != null && map.getOverlays().contains(marcadorConductor)) {
            animarMarcador(marcadorConductor, nuevaPos);
        } else {
            // Primera vez: crear el marcador
            marcadorConductor = new Marker(map);
            marcadorConductor.setId(MID_CONDUCTOR);
            marcadorConductor.setPosition(nuevaPos);
            marcadorConductor.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marcadorConductor.setTitle(esConductor ? "Tu posición actual" : "Conductor");
            marcadorConductor.setIcon(new BitmapDrawable(getResources(),
                    crearBitmapMarcador(COLOR_CONDUCTOR, "")));
            map.getOverlays().add(marcadorConductor);
        }

        // Actualizar segmento activo (línea naranja conductor → destino)
        if (!esConductor) {
            // Quitar polilíneas naranjas viejas
            List<Overlay> overlays = map.getOverlays();
            for (int i = overlays.size() - 1; i >= 0; i--) {
                if (overlays.get(i) instanceof Polyline) {
                    Polyline pl = (Polyline) overlays.get(i);
                    if (pl.getColor() == COLOR_SEGMENTO_ACTIVO) overlays.remove(i);
                }
            }
            boolean pasajeroRecogido = EST_RECOGIDO.equals(estadoReserva)
                    || EST_COMPLETADO.equals(estadoReserva);

            if (pasajeroRecogido && gpParada != null) {
                pedirSegmentoConductorAParada(nuevaPos, gpParada);
            } else if (!pasajeroRecogido) {
                GeoPoint puntoSubida = gpSubida != null ? gpSubida : gpOrigen;
                if (puntoSubida != null)
                    pedirSegmentoConductorAParada(nuevaPos, puntoSubida);
            }
        }

        gpConductorAnterior = nuevaPos;
        map.invalidate();
    }

    private void mostrarBotonPago(double monto, String conductor) {
        if (btnPagarViaje == null) return;
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("es", "CO"));
        btnPagarViaje.setText("Pagar  $" + nf.format(monto));
        btnPagarViaje.setVisibility(View.VISIBLE);
        btnPagarViaje.setEnabled(true);
        btnPagarViaje.setAlpha(1f);
        btnPagarViaje.setOnClickListener(v -> {
            Intent intent = new Intent(this, PagoActivity.class);
            intent.putExtra(PagoActivity.EXTRA_ID_VIAJE,   viajeId);
            intent.putExtra(PagoActivity.EXTRA_MONTO,      monto);
            intent.putExtra(PagoActivity.EXTRA_ORIGEN,     origenActual);
            intent.putExtra(PagoActivity.EXTRA_DESTINO,    destinoActual);
            intent.putExtra(PagoActivity.EXTRA_CONDUCTOR,  conductor != null ? conductor : "");
            startActivity(intent);
        });
    }

    private void confirmarPagoComoCondutor() {
        // Buscar el pago del viaje con confirmacionPasajero=true y confirmacionConductor=false
        String url = com.arlys.moviflexx.model.Constantes.pagosPorViaje((long) viajeId);
        com.arlys.moviflexx.model.ConexionApi.getInstance(this).getArrayNoCache(url,
                pagos -> {
                    if (pagos == null || pagos.length() == 0) {
                        Toast.makeText(this, "No hay pagos pendientes", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Buscar primer pago donde el pasajero ya confirmó pero el conductor no
                    for (int i = 0; i < pagos.length(); i++) {
                        org.json.JSONObject p = pagos.optJSONObject(i);
                        if (p == null) continue;
                        boolean cp  = p.optBoolean("confirmacionPasajero", false);
                        boolean cc  = p.optBoolean("confirmacionConductor", false);
                        String  est = p.optString("estado", "").toLowerCase();
                        long    id  = p.optLong("idPago", p.optLong("id", -1));
                        if (cp && !cc && !est.equals("completado") && id > 0) {
                            com.arlys.moviflexx.model.ConexionApi.getInstance(this).put(
                                    com.arlys.moviflexx.model.Constantes.pagoConfirmarConductor(id),
                                    new org.json.JSONObject(),
                                    resp -> {
                                        Toast.makeText(this, "✅ ¡Pago confirmado!", Toast.LENGTH_SHORT).show();
                                        // Ir a calificar a los pasajeros del viaje
                                        new android.os.Handler(android.os.Looper.getMainLooper())
                                                .postDelayed(this::paso3BuscarPasajerosParaCalificar, 1200);
                                    },
                                    err -> Toast.makeText(this, "Error al confirmar", Toast.LENGTH_LONG).show()
                            );
                            return;
                        }
                    }
                    Toast.makeText(this, "El pasajero aún no ha confirmado el pago", Toast.LENGTH_LONG).show();
                },
                error -> Toast.makeText(this, "Error consultando pagos", Toast.LENGTH_LONG).show()
        );
    }


    // NUEVO MÉTODO — animación suave entre dos posiciones GPS:
    private void animarMarcador(Marker marcador, GeoPoint destino) {
        if (marcador == null || destino == null) return;
        GeoPoint inicio = marcador.getPosition();
        if (inicio == null) { marcador.setPosition(destino); map.invalidate(); return; }

        // Si el movimiento es muy pequeño (<5m aprox), mover directo sin animar
        double dLat = Math.abs(destino.getLatitude()  - inicio.getLatitude());
        double dLng = Math.abs(destino.getLongitude() - inicio.getLongitude());
        if (dLat < 0.00005 && dLng < 0.00005) {
            marcador.setPosition(destino);
            map.invalidate();
            return;
        }

        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1500); // 1.5 segundos de animación suave
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            double lat = inicio.getLatitude()  + t * (destino.getLatitude()  - inicio.getLatitude());
            double lng = inicio.getLongitude() + t * (destino.getLongitude() - inicio.getLongitude());
            marcador.setPosition(new GeoPoint(lat, lng));
            map.invalidate();
        });
        animator.start();
    }
    private void enviarUbicacionAlBackend(double lat, double lng) {
        try {
            JSONObject body = new JSONObject();
            body.put("lat",      lat);
            body.put("lng",      lng);
            body.put("latitud",  lat);
            body.put("longitud", lng);
            String url = Constantes.BASE_URL + "/api/viajes/" + viajeId + "/ubicacion-conductor";
            ConexionApi.getInstance(this).post(url, body,
                    response -> Log.d(TAG, "✅ Ubicación conductor enviada"),
                    error    -> Log.w(TAG, "⚠️ Error enviando ubicación conductor"));
        } catch (Exception e) {
            Log.e(TAG, "enviarUbicacionAlBackend: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarGPSConductor();
        } else {
            Toast.makeText(this,
                    "⚠️ Se necesita permiso de ubicación para el seguimiento en tiempo real",
                    Toast.LENGTH_LONG).show();
        }
    }

    // =========================================================================
    //  POLLING GPS CONDUCTOR → para que el pasajero vea al conductor en tiempo real
    // =========================================================================

    /** Inicia el polling de ubicación del conductor (solo para pasajeros, cada 3 segundos). */
    private void iniciarPollingUbicacionConductor() {
        if (esConductor || gpsPollingActivo) return;
        gpsPollingActivo = true;
        gpsPollingRunnable = new Runnable() {
            @Override public void run() {
                if (!gpsPollingActivo) return;
                if ("INICIADO".equals(estadoViaje) || "EN_CURSO".equals(estadoViaje)) {
                    obtenerUbicacionConductorDesdeBackend();
                }
                gpsPollingHandler.postDelayed(this, GPS_CONDUCTOR_POLLING_MS);
            }
        };
        gpsPollingHandler.postDelayed(gpsPollingRunnable, GPS_CONDUCTOR_POLLING_MS);
        Log.d(TAG, "✅ Polling GPS conductor iniciado (pasajero) cada " + GPS_CONDUCTOR_POLLING_MS + " ms");
    }

    /** Detiene el polling de ubicación del conductor. */
    private void detenerPollingUbicacionConductor() {
        gpsPollingActivo = false;
        if (gpsPollingRunnable != null)
            gpsPollingHandler.removeCallbacks(gpsPollingRunnable);
    }

    /**
     * Consulta al backend la última posición del conductor y actualiza el mapa.
     * Endpoint esperado: GET /api/viajes/{id}/ubicacion-conductor
     * Respuesta esperada: { "lat": x.xxx, "lng": y.yyy }
     */
    // REEMPLAZA este método completo:
    private void obtenerUbicacionConductorDesdeBackend() {
        // ← Agregar timestamp para evitar cache de Volley
        String url = Constantes.BASE_URL + "/api/viajes/" + viajeId
                + "/ubicacion-conductor?t=" + System.currentTimeMillis();

        ConexionApi.getInstance(this).getObjectNoCache(url,
                response -> {
                    double lat = response.optDouble("lat",
                            response.optDouble("latitud",
                                    response.optDouble("latitude", Double.NaN)));
                    double lng = response.optDouble("lng",
                            response.optDouble("longitud",
                                    response.optDouble("longitude", Double.NaN)));
                    if (!Double.isNaN(lat) && lat != 0 && !Double.isNaN(lng) && lng != 0) {
                        GeoPoint nuevaPos = new GeoPoint(lat, lng);
                        gpConductorActual = nuevaPos;
                        runOnUiThread(() -> actualizarMarcadorConductorSuave(nuevaPos));
                    }
                },
                error -> Log.w(TAG, "No se pudo obtener ubicación del conductor")
        );
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
        // Reanudar polling GPS para el pasajero si el viaje estaba en curso
        if (!esConductor && ("INICIADO".equals(estadoViaje) || "EN_CURSO".equals(estadoViaje))) {
            iniciarPollingUbicacionConductor();
        }
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
        detenerPollingUbicacionConductor(); // ← detener también el polling del conductor
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
        btnPagarViaje = findViewById(R.id.btn_pagar_viaje);

        rvHistorialParadas.setLayoutManager(new LinearLayoutManager(this));

        if (btnAccionPrincipal != null)
            btnAccionPrincipal.setOnClickListener(v -> mostrarBottomSheetParada());

        btnIniciar.setOnClickListener(v -> cambiarEstadoViaje("iniciar"));
        btnFinalizar.setOnClickListener(v -> confirmarFinalizar());
        if (btnMensajeConductor != null)
            btnMensajeConductor.setOnClickListener(v -> abrirOCrearChat());

        configurarMapaBase();
        configurarMapaSinScrollInterferencia();
        crearBotonRecoger();
        crearCardMiReserva();
        crearCardPasajeros();
    }

    private void configurarMapaSinScrollInterferencia() {
        if (map == null) return;
        map.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    // Si estamos en modo seleccion de subida: capturar la coordenada
                    if (modoSeleccionMapaActivo) {
                        // Convertir pixel -> coordenada geografica
                        org.osmdroid.views.Projection proj = map.getProjection();
                        GeoPoint geoTocado = (GeoPoint) proj.fromPixels((int) event.getX(), (int) event.getY());
                        if (geoTocado != null) {
                            // Snap al punto mas cercano de la ruta activa
                            GeoPoint masProximo = puntoMasCercanoEnRuta(geoTocado);
                            final GeoPoint geoFinal = masProximo != null ? masProximo : geoTocado;
                            // Geocodificar y notificar al sheet (actualiza chip + boton)
                            geocodificarPuntoYNotificar(geoFinal);
                            // Llamar callback si existe (para reabrir el sheet despues)
                            if (onMapaTocadoCallback != null) {
                                Runnable cb = onMapaTocadoCallback;
                                onMapaTocadoCallback = null;
                                new Handler(Looper.getMainLooper()).postDelayed(cb, 1200);
                            }
                        }
                        return true; // consumir el evento para no mover el mapa
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        });
    }

    /**
     * Activa el modo de seleccion en el mapa.
     * Muestra un banner flotante y espera que el usuario toque el mapa.
     * Cuando toca, llama a onSeleccion con el punto elegido y el nombre geocodificado.
     */
    private void activarModoSeleccionMapa(android.widget.TextView chipRef, MaterialButton[] btnRef) {
        modoSeleccionMapaActivo = true;
        mostrarBannerSeleccionMapa();
        // El callback se ejecuta cuando se toca el mapa (ver configurarMapaSinScrollInterferencia)
        onMapaTocadoCallback = () -> {
            modoSeleccionMapaActivo = false;
            ocultarBannerSeleccionMapa();
            // chipRef y btnRef se actualizan en geocodificarPuntoYNotificar
        };
    }

    private void desactivarModoSeleccionMapa() {
        modoSeleccionMapaActivo = false;
        onMapaTocadoCallback = null;
        ocultarBannerSeleccionMapa();
    }

    // Referencias compartidas para actualizar el sheet desde el toque del mapa
    private android.widget.TextView chipSubidaRef = null;
    private MaterialButton[] btnSubidaRef = null;
    private ParadaDinamica[] subidaElegidaRef = null;

    private void mostrarBannerSeleccionMapa() {
        if (map == null) return;
        ocultarBannerSeleccionMapa(); // limpiar anterior si hay

        float dp = getResources().getDisplayMetrics().density;
        android.widget.FrameLayout.LayoutParams lpBanner = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        lpBanner.gravity = android.view.Gravity.TOP;

        bannerSeleccionMapa = new LinearLayout(this);
        ((LinearLayout) bannerSeleccionMapa).setOrientation(LinearLayout.HORIZONTAL);
        ((LinearLayout) bannerSeleccionMapa).setGravity(android.view.Gravity.CENTER);
        bannerSeleccionMapa.setPadding((int)(12*dp), (int)(10*dp), (int)(12*dp), (int)(10*dp));
        bannerSeleccionMapa.setLayoutParams(lpBanner);

        GradientDrawable bgBanner = new GradientDrawable();
        bgBanner.setShape(GradientDrawable.RECTANGLE);
        bgBanner.setColor(Color.argb(230, 0, 137, 123)); // turquesa semitransparente
        bgBanner.setCornerRadii(new float[]{0,0,0,0, (int)(12*dp),(int)(12*dp),(int)(12*dp),(int)(12*dp)});
        bannerSeleccionMapa.setBackground(bgBanner);

        TextView tvBanner = new TextView(this);
        tvBanner.setText("👆  Toca el mapa para marcar donde te subes");
        tvBanner.setTextSize(13.5f); tvBanner.setTextColor(Color.WHITE);
        tvBanner.setTypeface(null, Typeface.BOLD);
        tvBanner.setGravity(android.view.Gravity.CENTER);
        tvBanner.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ((LinearLayout) bannerSeleccionMapa).addView(tvBanner);

        // Boton cancelar
        TextView tvCancelar = new TextView(this);
        tvCancelar.setText("✕");
        tvCancelar.setTextSize(16f); tvCancelar.setTextColor(Color.WHITE);
        tvCancelar.setPadding((int)(8*dp), 0, 0, 0);
        tvCancelar.setOnClickListener(v -> desactivarModoSeleccionMapa());
        ((LinearLayout) bannerSeleccionMapa).addView(tvCancelar);

        // Insertar como overlay encima del mapa
        android.view.ViewParent parent = map.getParent();
        if (parent instanceof android.widget.FrameLayout) {
            ((android.widget.FrameLayout) parent).addView(bannerSeleccionMapa);
        } else {
            // Buscar el contenedor del mapa y envolver si es necesario
            runOnUiThread(() -> {
                try {
                    android.view.ViewGroup mapParent = (android.view.ViewGroup) map.getParent();
                    if (mapParent != null) {
                        int idx = mapParent.indexOfChild(map);
                        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(this);
                        android.view.ViewGroup.LayoutParams mapLp = map.getLayoutParams();
                        mapParent.removeView(map);
                        wrapper.addView(map, new android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
                        wrapper.addView(bannerSeleccionMapa);
                        wrapper.setLayoutParams(mapLp);
                        mapParent.addView(wrapper, idx);
                    }
                } catch (Exception e) { Log.w(TAG, "No se pudo agregar banner: " + e.getMessage()); }
            });
        }
    }

    private void ocultarBannerSeleccionMapa() {
        if (bannerSeleccionMapa != null) {
            android.view.ViewParent parent = bannerSeleccionMapa.getParent();
            if (parent instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) parent).removeView(bannerSeleccionMapa);
            bannerSeleccionMapa = null;
        }
    }

    /**
     * Encuentra el punto de la ruta activa mas cercano al punto tocado.
     * Esto asegura que la subida siempre quede sobre o cerca de la ruta.
     */
    private GeoPoint puntoMasCercanoEnRuta(GeoPoint tocado) {
        if (rutaActiva == null || rutaActiva.isEmpty()) return null;
        GeoPoint mejor = null; double menorDist = Double.MAX_VALUE;
        for (GeoPoint rp : rutaActiva) {
            double dLat = rp.getLatitude()  - tocado.getLatitude();
            double dLng = rp.getLongitude() - tocado.getLongitude();
            double dist = dLat*dLat + dLng*dLng;
            if (dist < menorDist) { menorDist = dist; mejor = rp; }
        }
        // Solo usar el mas cercano si esta a menos de ~1.5km (0.014 grados)
        return (menorDist < 0.0002) ? mejor : tocado;
    }

    /**
     * Dado un GeoPoint, obtiene el nombre del lugar via Nominatim (en background)
     * y actualiza el marcador en el mapa + chip del sheet.
     */
    private void geocodificarPuntoYNotificar(GeoPoint gp) {
        // Mostrar marcador provisional inmediatamente
        runOnUiThread(() -> {
            gpSubida = gp;
            nombreSubidaPasajero = String.format("%.4f, %.4f", gp.getLatitude(), gp.getLongitude());
            // Actualizar chip provisional
            if (chipSubidaRef != null) {
                chipSubidaRef.setText("📍 Cargando nombre...");
                chipSubidaRef.setVisibility(android.view.View.VISIBLE);
            }
            // Habilitar boton provisional
            if (btnSubidaRef != null && btnSubidaRef[0] != null) {
                btnSubidaRef[0].setEnabled(true); btnSubidaRef[0].setAlpha(1f);
                btnSubidaRef[0].setBackgroundColor(Color.parseColor("#00897B"));
                btnSubidaRef[0].setText("✅  CONFIRMAR SUBIDA → ELEGIR BAJADA");
            }
            // Crear parada temporal
            ParadaDinamica pdTemp = new ParadaDinamica(nombreSubidaPasajero, gp.getLatitude(), gp.getLongitude(), -99);
            if (subidaElegidaRef != null) subidaElegidaRef[0] = pdTemp;
            renderizarMapa();
        });

        // Geocodificar en background
        new Thread(() -> {
            try {
                String url = "https://nominatim.openstreetmap.org/reverse?lat=" + gp.getLatitude()
                        + "&lon=" + gp.getLongitude() + "&format=json&addressdetails=1&zoom=18&accept-language=es";
                String resp = peticionHttp(url);
                if (resp == null || resp.isEmpty()) return;
                JSONObject geo = new JSONObject(resp);
                String nombre = extraerNombreNominatim(geo.optJSONObject("address"), geo);
                final String nomFinal = (nombre == null || nombre.isEmpty())
                        ? String.format(Locale.getDefault(), "%.4f, %.4f", gp.getLatitude(), gp.getLongitude())
                        : nombre;
                runOnUiThread(() -> {
                    nombreSubidaPasajero = nomFinal;
                    if (chipSubidaRef != null) {
                        chipSubidaRef.setText("🙋 " + nomFinal);
                        chipSubidaRef.setVisibility(android.view.View.VISIBLE);
                    }
                    ParadaDinamica pdFinal = new ParadaDinamica(nomFinal, gp.getLatitude(), gp.getLongitude(), -99);
                    if (subidaElegidaRef != null) subidaElegidaRef[0] = pdFinal;
                    if (btnSubidaRef != null && btnSubidaRef[0] != null)
                        btnSubidaRef[0].setText("✅  CONFIRMAR: " + nomFinal);
                });
            } catch (Exception e) {
                Log.w(TAG, "geocodificarPuntoYNotificar error: " + e.getMessage());
            }
        }).start();
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
        LinearLayout container = findViewById(R.id.container_mi_reserva);
        if (container == null) return;

        float d  = getResources().getDisplayMetrics().density;
        int p16  = (int)(16 * d), p14 = (int)(14 * d);
        int p12  = (int)(12 * d), p6  = (int)(6  * d);

        View divisor = new View(this);
        LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d));
        lpDiv.setMargins(0, p14, 0, p14);
        divisor.setLayoutParams(lpDiv);
        divisor.setBackgroundColor(Color.parseColor("#E0F2F1"));
        container.addView(divisor);

        cardMiReserva = new MaterialCardView(this);
        cardMiReserva.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        cardMiReserva.setRadius(16 * d);
        cardMiReserva.setCardElevation(0);
        cardMiReserva.setCardBackgroundColor(Color.parseColor("#F0FAFA"));
        cardMiReserva.setStrokeWidth((int)(1.5f * d));
        cardMiReserva.setStrokeColor(Color.parseColor("#80CBC4"));
        cardMiReserva.setVisibility(View.GONE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p16, p14, p16, p14);

        // Encabezado
        LinearLayout encabezado = new LinearLayout(this);
        encabezado.setOrientation(LinearLayout.HORIZONTAL);
        encabezado.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpEnc = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpEnc.setMargins(0, 0, 0, p14);
        encabezado.setLayoutParams(lpEnc);

        View accentBar = new View(this);
        LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams((int)(4*d), (int)(18*d));
        lpBar.setMargins(0, 0, (int)(10*d), 0);
        accentBar.setLayoutParams(lpBar);
        android.graphics.drawable.GradientDrawable barShape = new android.graphics.drawable.GradientDrawable();
        barShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        barShape.setCornerRadius(4 * d);
        barShape.setColors(new int[]{Color.parseColor("#00BFA0"), Color.parseColor("#00897B")});
        barShape.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
        accentBar.setBackground(barShape);
        encabezado.addView(accentBar);

        TextView etiqueta = new TextView(this);
        etiqueta.setText("TU RESERVA");
        etiqueta.setTextSize(10f);
        etiqueta.setTypeface(null, Typeface.BOLD);
        etiqueta.setTextColor(Color.parseColor("#00897B"));
        etiqueta.setLetterSpacing(0.16f);
        etiqueta.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        encabezado.addView(etiqueta);

        TextView icono = new TextView(this);
        icono.setText("");
        icono.setTextSize(20f);
        encabezado.addView(icono);
        inner.addView(encabezado);

        inner.addView(crearFilaReserva(ROW_ID_ASIENTOS, "💺", "Asientos reservados", "—", d, 0, p6));
        inner.addView(crearSeparadorFila(d, p6));
        inner.addView(crearFilaReserva(ROW_ID_BAJADA,   "🚏", "Bajarás en",          "—", d, 0, p6));
        inner.addView(crearSeparadorFila(d, p6));
        inner.addView(crearFilaReserva(ROW_ID_PRECIO,   "", "Precio total",        "—", d, 0, 0));

        txtMiReservaInfo = new TextView(this);
        txtMiReservaInfo.setVisibility(View.GONE);
        inner.addView(txtMiReservaInfo);

        View sepBtn = new View(this);
        LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d));
        lpSep.setMargins(0, p14, 0, p12);
        sepBtn.setLayoutParams(lpSep);
        sepBtn.setBackgroundColor(Color.parseColor("#E0F2F1"));
        inner.addView(sepBtn);

        MaterialButton btnCancelar = new MaterialButton(this);
        btnCancelar.setText("✕  Cancelar mi reserva");
        btnCancelar.setTextSize(13f);
        btnCancelar.setTextColor(Color.WHITE);
        btnCancelar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(48 * d)));
        btnCancelar.setCornerRadius((int)(12 * d));
        btnCancelar.setBackgroundColor(Color.parseColor("#EF5350"));
        btnCancelar.setOnClickListener(v -> cancelarMiReserva());
        inner.addView(btnCancelar);

        cardMiReserva.addView(inner);
        container.addView(cardMiReserva);
    }

    private LinearLayout crearFilaReserva(int rowId, String icono, String etiqueta,
                                          String valorInicial, float d,
                                          int marginTop, int marginBottom) {
        int p10 = (int)(10 * d);
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(android.view.Gravity.CENTER_VERTICAL);
        fila.setTag(rowId);
        LinearLayout.LayoutParams lpFila = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFila.setMargins(0, marginTop, 0, marginBottom);
        fila.setLayoutParams(lpFila);

        LinearLayout iconBox = new LinearLayout(this);
        iconBox.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpIB = new LinearLayout.LayoutParams((int)(36*d),(int)(36*d));
        lpIB.setMargins(0, 0, p10, 0);
        iconBox.setLayoutParams(lpIB);
        android.graphics.drawable.GradientDrawable iBg = new android.graphics.drawable.GradientDrawable();
        iBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        iBg.setColor(Color.parseColor("#E0F7FA"));
        iBg.setStroke((int)(1f*d), Color.parseColor("#B2EBF2"));
        iconBox.setBackground(iBg);

        TextView tvIcon = new TextView(this);
        tvIcon.setText(icono); tvIcon.setTextSize(17f);
        tvIcon.setGravity(android.view.Gravity.CENTER);
        iconBox.addView(tvIcon);
        fila.addView(iconBox);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvEtiq = new TextView(this);
        tvEtiq.setText(etiqueta); tvEtiq.setTextSize(9f);
        tvEtiq.setTextColor(Color.parseColor("#80CBC4")); tvEtiq.setAllCaps(true);
        tvEtiq.setLetterSpacing(0.1f);
        col.addView(tvEtiq);

        TextView tvVal = new TextView(this);
        tvVal.setText(valorInicial); tvVal.setTextSize(14f);
        tvVal.setTextColor(Color.parseColor("#004D40")); tvVal.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lpV = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpV.topMargin = (int)(2*d);
        tvVal.setLayoutParams(lpV);
        col.addView(tvVal);

        fila.addView(col);
        return fila;
    }

    private View crearSeparadorFila(float d, int marginVertical) {
        View sep = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d));
        lp.setMargins((int)(46 * d), marginVertical, 0, marginVertical);
        sep.setLayoutParams(lp);
        sep.setBackgroundColor(Color.parseColor("#E0F2F1"));
        return sep;
    }

    private void actualizarValorFila(int rowId, String nuevoValor) {
        if (cardMiReserva == null) return;
        LinearLayout inner = (LinearLayout) cardMiReserva.getChildAt(0);
        if (inner == null) return;
        for (int i = 0; i < inner.getChildCount(); i++) {
            View child = inner.getChildAt(i);
            if (child instanceof LinearLayout
                    && child.getTag() instanceof Integer
                    && (int) child.getTag() == rowId) {
                LinearLayout fila = (LinearLayout) child;
                if (fila.getChildCount() >= 2) {
                    View col = fila.getChildAt(1);
                    if (col instanceof LinearLayout) {
                        LinearLayout colLayout = (LinearLayout) col;
                        if (colLayout.getChildCount() >= 2) {
                            View tv = colLayout.getChildAt(1);
                            if (tv instanceof TextView) ((TextView) tv).setText(nuevoValor);
                        }
                    }
                }
                break;
            }
        }
    }

    // =========================================================================
    //  crearCardPasajeros
    // =========================================================================
    private void crearCardPasajeros() {
        // Insertar dentro de container_mi_reserva (que está en la card de Información del Viaje)
        LinearLayout container = findViewById(R.id.container_mi_reserva);
        if (container == null) return;

        float d = getResources().getDisplayMetrics().density;
        int p16=(int)(16*d), p12=(int)(12*d), p8=(int)(8*d);

        // Separador visual antes de la sección de pasajeros
        View divisor = new View(this);
        LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d));
        lpDiv.setMargins(0, p12, 0, p12);
        divisor.setLayoutParams(lpDiv);
        divisor.setBackgroundColor(Color.parseColor("#E0F2F1"));

        cardPasajeros = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardPasajeros.setLayoutParams(lp);
        cardPasajeros.setRadius(16 * d);
        cardPasajeros.setCardElevation(0);
        cardPasajeros.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
        cardPasajeros.setStrokeWidth((int)(1.5f * d));
        cardPasajeros.setStrokeColor(Color.parseColor("#BBDEFB"));
        cardPasajeros.setVisibility(View.GONE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p16, p12, p16, p12);

        // Encabezado con acento turquesa igual al resto de la card
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Barra de acento
        View accentBar = new View(this);
        LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams((int)(4*d), (int)(18*d));
        lpBar.setMargins(0, 0, (int)(10*d), 0);
        accentBar.setLayoutParams(lpBar);
        android.graphics.drawable.GradientDrawable barShape = new android.graphics.drawable.GradientDrawable();
        barShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        barShape.setCornerRadius(4 * d);
        barShape.setColors(new int[]{Color.parseColor("#1565C0"), Color.parseColor("#1976D2")});
        barShape.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
        accentBar.setBackground(barShape);
        fila.addView(accentBar);

        TextView titulo = new TextView(this);
        titulo.setText("PASAJEROS RESERVADOS");
        titulo.setTextSize(10f);
        titulo.setTypeface(null, Typeface.BOLD);
        titulo.setTextColor(Color.parseColor("#1565C0"));
        titulo.setLetterSpacing(0.16f);
        titulo.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fila.addView(titulo);

        txtTotalPasajeros = new TextView(this);
        txtTotalPasajeros.setTextSize(11f);
        txtTotalPasajeros.setTextColor(Color.parseColor("#1565C0"));
        txtTotalPasajeros.setTypeface(null, Typeface.BOLD);
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

        // Agregar al container de la card de información
        container.addView(divisor);
        container.addView(cardPasajeros);
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
        marcadorConductor = null; // ← limpiar para que se recree correctamente
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

        // ── Extraer km y min desde "descripcion" si extraerMetricas no encontró nada ──
        if ((distanciaKm <= 0 || duracionMin <= 0) && ruta != null) {
            String desc = ruta.optString("descripcion", "");
            // Ejemplo: "Ruta de 12.5 km en Carro (20 min)"
            try {
                java.util.regex.Matcher mKm = java.util.regex.Pattern
                        .compile("([\\d.]+)\\s*km").matcher(desc);
                if (mKm.find() && distanciaKm <= 0)
                    distanciaKm = Double.parseDouble(mKm.group(1));
            } catch (Exception ignored) {}
            try {
                java.util.regex.Matcher mMin = java.util.regex.Pattern
                        .compile("([\\d.]+)\\s*min").matcher(desc);
                if (mMin.find() && duracionMin <= 0)
                    duracionMin = Double.parseDouble(mMin.group(1));
            } catch (Exception ignored) {}

            if (txtDistancia != null && distanciaKm > 0)
                txtDistancia.setText(String.format(Locale.getDefault(), "%.1f km", distanciaKm));
            if (txtDuracion != null && duracionMin > 0)
                txtDuracion.setText(String.format(Locale.getDefault(), "%.0f min", duracionMin));
        }

        gpOrigen  = new GeoPoint(latOrigen,  lngOrigen);
        gpDestino = new GeoPoint(latDestino, lngDestino);

        geojsonRuta = "";
        if (ruta != null)
            geojsonRuta = ruta.optString("geojson",
                    ruta.optString("geojsonRuta", "")).trim();
        if (geojsonRuta.isEmpty())
            geojsonRuta = r.optString("geojson",
                    r.optString("geojsonRuta", "")).trim();
        if ("null".equalsIgnoreCase(geojsonRuta)) geojsonRuta = "";

        mostrarFechaHora(r.optString("fechaHoraSalida",
                r.optString("fechaSalida", r.optString("fecha", ""))));

        extraerConductor(r);
        if (idConductorViaje <= 0 && esConductor) {
            idConductorViaje     = session.getIdUsuario();
            nombreConductorViaje = session.getNombre();
        }
        if (idConductorViaje > 0
                && (nombreConductorViaje.isEmpty()
                || nombreConductorViaje.startsWith("Conductor #"))) {
            cargarNombreConductorPorId(idConductorViaje);
        }

        JSONObject pas = r.optJSONObject("pasajero");
        if (pas != null) {
            idPasajeroViaje     = extractId(pas);
            nombrePasajeroViaje = extractNombre(pas);
        }
        JSONObject veh = r.optJSONObject("vehiculo");
        if (veh != null && txtVehiculo != null)
            txtVehiculo.setText("" + veh.optString("marca","") + " "
                    + veh.optString("modelo","") + " • " + veh.optString("placa",""));

        // ── Precio — distanciaKm ya disponible ──
        double pr = r.optDouble("precio", -1);
        if (pr < 0) {
            try { pr = Double.parseDouble(r.optString("precio", "0")); }
            catch (Exception ex) { pr = 0; }
        }
        if (pr <= 0 && ruta != null) {
            pr = ruta.optDouble("precio", ruta.optDouble("costoPorPasajero",
                    ruta.optDouble("precioSugerido",
                            ruta.optDouble("costoCombustible", 0))));
        }
        if (pr <= 0 && distanciaKm > 0)
            pr = Math.ceil((distanciaKm * 700.0) / 100.0) * 100.0;

        precioViaje = pr;
        if (txtPrecio != null) {
            txtPrecio.setText(precioViaje > 0
                    ? "$ " + String.format(Locale.getDefault(), "%,.0f", precioViaje)
                    : "Precio no definido");
        }

        actualizarNombreConductorUI();
        txtRuta.setText("" + origenActual + " → " + destinoActual);
        txtEstado.setText(etiquetaEstado(estadoViaje));
        actualizarChipsCupos(cuposTotales, cuposDisponibles);
        configurarBotones();
        cargarParadasRuta();
        iniciarPolling();

        if (esConductor && "INICIADO".equals(estadoViaje)) iniciarGPSConductor();
        if (!esConductor && ("INICIADO".equals(estadoViaje) || "EN_CURSO".equals(estadoViaje)))
            iniciarPollingUbicacionConductor();

        if (esConductor) cargarReservasConductor();
        else             cargarMiReservaPasajero();
    }


    private void cargarNombreConductorPorId(int id) {
        if (id <= 0) return;  // ← AGREGAR
        ConexionApi.getInstance(this).getObject(Constantes.USUARIOS + "/" + id,
                perfil -> {
                    String nom = extractNombre(perfil);
                    if (!nom.isEmpty()) {
                        nombreConductorViaje = nom;
                        runOnUiThread(() -> {
                            actualizarNombreConductorUI();
                            actualizarBotonChat();
                        });
                    }
                },
                err -> {
                    // ← AGREGAR: si es 404, asignar nombre genérico y NO reintentar
                    int code = (err != null && err.networkResponse != null)
                            ? err.networkResponse.statusCode : 0;
                    if (code == 404) {
                        nombreConductorViaje = "Conductor";
                        runOnUiThread(() -> actualizarNombreConductorUI());
                    }
                    Log.w(TAG, "No se pudo cargar nombre del conductor #" + id + " (código " + code + ")");
                }
        );
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
            latOrigen = primeraCoord(src,
                    new String[]{"latOrigen","latitudOrigen","latInicio"}, Double.NaN);
            lngOrigen = primeraCoord(src,
                    new String[]{"lngOrigen","longitudOrigen","lngInicio"}, Double.NaN);
            if ((Double.isNaN(latOrigen) || latOrigen == 0) && ruta != null) {
                latOrigen = primeraCoord(r,
                        new String[]{"latOrigen","latitudOrigen"}, Double.NaN);
                lngOrigen = primeraCoord(r,
                        new String[]{"lngOrigen","longitudOrigen"}, Double.NaN);
            }
        }
        if (Double.isNaN(latDestino) || latDestino == 0) {
            latDestino = primeraCoordDistinta(src,
                    new String[]{"latDestino","latitudDestino","latFin"},
                    Double.isNaN(latOrigen)?0:latOrigen, Double.NaN);
            lngDestino = primeraCoordDistinta(src,
                    new String[]{"lngDestino","longitudDestino","lngFin"},
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
            origenActual = primeraStr(src, new String[]{"origen","puntoOrigen","inicio",
                    "nombreOrigen","lugarOrigen","direccionOrigen","origenNombre"});
        if (origenActual.isEmpty() && ruta != null)
            origenActual = primeraStr(r, new String[]{"origen","puntoOrigen","inicio","nombreOrigen"});
        if (origenActual.isEmpty()) origenActual = extraerOrigenDeNombre(src);
        if (origenActual.isEmpty() && ruta != null) origenActual = extraerOrigenDeNombre(r);
        if (origenActual.isEmpty()) origenActual = "Origen";

        if (destinoActual.isEmpty())
            destinoActual = primeraStr(src, new String[]{"destino","puntoDestino","fin",
                    "nombreDestino","lugarDestino","direccionDestino","destinoNombre"});
        if (destinoActual.isEmpty() && ruta != null)
            destinoActual = primeraStr(r, new String[]{"destino","puntoDestino","fin","nombreDestino"});
        if (destinoActual.isEmpty()) destinoActual = extraerDestinoDeNombre(src);
        if (destinoActual.isEmpty() && ruta != null) destinoActual = extraerDestinoDeNombre(r);
        if (destinoActual.isEmpty()) destinoActual = "Destino";
    }

    private String extraerOrigenDeNombre(JSONObject obj) {
        String n = obj.optString("nombre","").trim();
        if (n.isEmpty() || n.equals("null")) return "";
        if (n.contains("→"))   return n.split("→")[0].trim();
        if (n.contains("->"))  return n.split("->")[0].trim();
        if (n.contains(" - ")) return n.split(" - ")[0].trim();
        return "";
    }

    private String extraerDestinoDeNombre(JSONObject obj) {
        String n = obj.optString("nombre","").trim();
        if (n.isEmpty() || n.equals("null")) return "";
        String[] p = null;
        if (n.contains("→"))        p = n.split("→",  2);
        else if (n.contains("->"))  p = n.split("->", 2);
        else if (n.contains(" - ")) p = n.split(" - ", 2);
        if (p != null && p.length > 1) return p[1].trim();
        return "";
    }

    private void extraerMetricas(JSONObject r, JSONObject ruta) {
        double km  = r.optDouble("distanciaKm",  r.optDouble("distancia", -1));
        double min = r.optDouble("duracionMin",   r.optDouble("duracion",  -1));

        if (ruta != null) {
            if (km  < 0) km  = ruta.optDouble("distanciaKm",  ruta.optDouble("distancia", -1));
            if (min < 0) min = ruta.optDouble("duracionMin",   ruta.optDouble("duracion",  -1));
        }

        if (km >= 0) {
            distanciaKm = km;
            if (txtDistancia != null)
                txtDistancia.setText(String.format(Locale.getDefault(), "%.1f km", distanciaKm));
        }
        if (min >= 0) {
            duracionMin = min;
            if (txtDuracion != null)
                txtDuracion.setText(String.format(Locale.getDefault(), "%.0f min", duracionMin));
        }

        if ((km < 0 || min < 0) && !coordsOrigenInvalidas && !coordsDestinoInvalidas) {
            pedirMetricasOSRMEnBackground();
        }
    }

    private void pedirMetricasOSRMEnBackground() {
        new Thread(() -> {
            try {
                String seg = lngOrigen + "," + latOrigen + ";"
                        + lngDestino + "," + latDestino + "?overview=false";
                String json = null;
                try { json = peticionHttp(OSRM_URL + "/route/v1/driving/" + seg); }
                catch (Exception e1) { }
                if (json == null || json.isEmpty())
                    json = peticionHttp(OSRM_URL_PUBLIC + "/route/v1/driving/" + seg);

                JSONObject obj = new JSONObject(json);
                if (!"Ok".equals(obj.optString("code"))) return;
                JSONObject route = obj.getJSONArray("routes").getJSONObject(0);
                final double km  = route.optDouble("distance", 0) / 1000.0;
                final double min = route.optDouble("duration",  0) / 60.0;
                if (km > 0) distanciaKm = km;
                if (min > 0) duracionMin = min;
                runOnUiThread(() -> {
                    if (txtDistancia != null && km > 0)
                        txtDistancia.setText(String.format(Locale.getDefault(), "%.1f km", km));
                    if (txtDuracion != null && min > 0)
                        txtDuracion.setText(String.format(Locale.getDefault(), "%.0f min", min));
                });
            } catch (Exception e) {
                Log.w(TAG, "pedirMetricasOSRMEnBackground: " + e.getMessage());
            }
        }).start();
    }

    // =========================================================================
    //  MAPA — renderizado principal
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
            ajustarCamara(); map.invalidate(); return;
        }

        if (!esConductor) {
            renderizarMapaPasajero(viajeIniciado);
        } else {
            if (viajeIniciado) {
                renderizarMapaConductorIniciado();
            } else {
                if (rutaActiva != null && rutaActiva.size() >= 2)
                    dibujarPolilinea(rutaActiva, COLOR_RUTA);
                agregarMarcador(MID_ORIGEN,  gpOrigen,  0xFF4CAF50, "A", "🟢 " + origenActual);
                agregarMarcador(MID_DESTINO, gpDestino, 0xFFEF5350, "B", "🔴 " + destinoActual);
                for (int i = 0; i < paradasPasajeros.size(); i++) {
                    GeoPoint pp = paradasPasajeros.get(i);
                    if (pp == null) continue;
                    int color = COLORES_PASAJEROS[i % COLORES_PASAJEROS.length];
                    String etq = i < nombresPasajerosParadas.size()
                            ? nombresPasajerosParadas.get(i) : "Pasajero "+(i+1);
                    agregarMarcador("parada_pas_"+i, pp, color, "P"+(i+1), "🚏 "+etq);
                }
            }
        }
        ajustarCamara(); map.invalidate();
    }

    // =========================================================================
    //  MAPA — PASAJERO  ← LÓGICA COMPLETA NUEVA
    // =========================================================================


    private void renderizarMapaPasajero(boolean viajeIniciado) {
        boolean pasajeroRecogido = EST_RECOGIDO.equals(estadoReserva)
                || EST_COMPLETADO.equals(estadoReserva);

        // ── Ruta base A→B siempre visible ──
        if (rutaActiva != null && rutaActiva.size() >= 2)
            dibujarPolilinea(rutaActiva, COLOR_RUTA);

        // ── Marcador A (inicio de la ruta) ──
        agregarMarcador(MID_ORIGEN, gpOrigen, 0xFF4CAF50, "A", "🟢 " + origenActual);

        if (pasajeroRecogido) {
            // ══ YA FUE RECOGIDO ══════════════════════════════════════════════
            agregarMarcador(MID_DESTINO, gpDestino, 0xFFEF5350, "B",
                    "Destino final: " + destinoActual);

            if (gpParada != null) {
                agregarMarcador(MID_PARADA, gpParada, 0xFFFF6F00, "🚏",
                        "🚏 Tu bajada: " + nombreParada);
            }

            if (!puntosRutaWaypoint.isEmpty()) {
                dibujarPolilineaWaypoint(puntosRutaWaypoint);
            } else {
                pedirRutaConWaypoint();
            }

            // ── Conductor: mover suavemente, no recrear ──
            if (gpConductorActual != null) {
                if (marcadorConductor == null || !map.getOverlays().contains(marcadorConductor)) {
                    actualizarMarcadorConductorSuave(gpConductorActual);
                }
                if (gpParada != null)
                    pedirSegmentoConductorAParada(gpConductorActual, gpParada);
            }
            return;
        }

        // ══ AÚN NO HA SIDO RECOGIDO ══════════════════════════════════════════

        agregarMarcador(MID_DESTINO, gpDestino, 0xFFEF5350, "B", "🔴 " + destinoActual);

        if (yaReservo) {
            GeoPoint puntoSubida = gpSubida != null ? gpSubida : gpOrigen;
            String   nomSubida   = (nombreSubidaPasajero != null && !nombreSubidaPasajero.isEmpty())
                    ? nombreSubidaPasajero : origenActual;

            boolean subidaEsDistintaDeOrigen = gpSubida != null
                    && !sonIguales(gpSubida.getLatitude(), gpSubida.getLongitude(),
                    latOrigen, lngOrigen);

            if (subidaEsDistintaDeOrigen) {
                agregarMarcador(MID_SUBIDA, gpSubida, 0xFF00C853, "🙋",
                        "Aquí te recogen: " + nomSubida);
            }

            if (gpParada != null) {
                boolean esperandoRecogida = EST_ESPERANDO_RECOGIDA.equals(estadoReserva)
                        || (viajeIniciado
                        && !estadoReserva.isEmpty()
                        && !EST_COMPLETADO.equals(estadoReserva)
                        && !EST_CANCELADO.equals(estadoReserva)
                        && !EST_RECOGIDO.equals(estadoReserva));

                int colorBajada = esperandoRecogida ? 0xFFFF6F00 : 0xFF00897B;
                agregarMarcador(MID_PARADA, gpParada, colorBajada, "🚏",
                        "🚏 Tu bajada: " + nombreParada);
            }
        }

        // ── Conductor en tiempo real: mover suavemente, no recrear ──
        if (viajeIniciado && gpConductorActual != null) {
            if (marcadorConductor == null || !map.getOverlays().contains(marcadorConductor)) {
                actualizarMarcadorConductorSuave(gpConductorActual);
            }
            GeoPoint puntoSubida = gpSubida != null ? gpSubida : gpOrigen;
            if (puntoSubida != null)
                pedirSegmentoConductorAParada(gpConductorActual, puntoSubida);
        }
    }

    private void renderizarMapaConductorIniciado() {
        GeoPoint posConductor = (gpConductorActual != null) ? gpConductorActual : gpOrigen;

        if (rutaActiva != null && rutaActiva.size() >= 2)
            dibujarPolilinea(rutaActiva, COLOR_RUTA);

        agregarMarcador(MID_ORIGEN,  gpOrigen,  0xFF4CAF50, "A", "🟢 " + origenActual);
        agregarMarcador(MID_DESTINO, gpDestino, 0xFFEF5350, "B", "🔴 " + destinoActual);

        // ── Conductor: mover suavemente, no recrear ──
        if (marcadorConductor == null || !map.getOverlays().contains(marcadorConductor)) {
            actualizarMarcadorConductorSuave(posConductor);
        }

        boolean algunRecogido = EST_RECOGIDO.equals(estadoReserva)
                || EST_COMPLETADO.equals(estadoReserva);

        if (algunRecogido) {
            pedirSegmentoConductorADestino(posConductor);
        } else if (!paradasPasajeros.isEmpty()) {
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
            if (primeraParada != null)
                pedirSegmentoConductorAParada(posConductor, primeraParada);
        }
    }
    private void pedirSegmentoConductorAParada(GeoPoint desde, GeoPoint hasta) {
        if (desde == null || hasta == null) return;

        // Extraer waypoints intermedios de la ruta activa que estén
        // entre la posición del conductor y el punto de recogida
        final ArrayList<GeoPoint> waypoints = extraerWaypointsEntrePuntos(desde, hasta);

        new Thread(() -> {
            try {
                // Construir URL con waypoints: conductor ; wp1 ; wp2 ; ... ; subida
                StringBuilder coordsB = new StringBuilder();
                coordsB.append(desde.getLongitude()).append(",").append(desde.getLatitude());
                for (GeoPoint wp : waypoints) {
                    coordsB.append(";").append(wp.getLongitude()).append(",").append(wp.getLatitude());
                }
                coordsB.append(";").append(hasta.getLongitude()).append(",").append(hasta.getLatitude());
                String params = "?overview=full&geometries=geojson";

                ArrayList<GeoPoint> pts = null;

                // Intentar con waypoints primero
                if (!waypoints.isEmpty()) {
                    String urlConWp = OSRM_URL + "/route/v1/driving/" + coordsB + params;
                    try { pts = parsearRutaSimpleOSRM(peticionHttp(urlConWp)); } catch (Exception ignored) {}
                    if (pts == null || pts.size() < 2) {
                        String urlConWpPub = OSRM_URL_PUBLIC + "/route/v1/driving/" + coordsB + params;
                        try { pts = parsearRutaSimpleOSRM(peticionHttp(urlConWpPub)); } catch (Exception ignored) {}
                    }
                }

                // Fallback: directo sin waypoints
                if (pts == null || pts.size() < 2) {
                    String seg = desde.getLongitude() + "," + desde.getLatitude() + ";"
                            + hasta.getLongitude() + "," + hasta.getLatitude() + params;
                    try { pts = parsearRutaSimpleOSRM(peticionHttp(OSRM_URL + "/route/v1/driving/" + seg)); } catch (Exception ignored) {}
                    if (pts == null || pts.size() < 2)
                        try { pts = parsearRutaSimpleOSRM(peticionHttp(OSRM_URL_PUBLIC + "/route/v1/driving/" + seg)); } catch (Exception ignored) {}
                }

                if (pts != null && pts.size() >= 2) {
                    final ArrayList<GeoPoint> fPts = pts;
                    runOnUiThread(() -> { dibujarPolilineaSegmento(fPts); map.invalidate(); });
                }
            } catch (Exception e) {
                Log.w(TAG, "pedirSegmentoConductorAParada: " + e.getMessage());
            }
        }).start();
    }
    private ArrayList<GeoPoint> extraerWaypointsEntrePuntos(GeoPoint desde, GeoPoint hasta) {
        ArrayList<GeoPoint> resultado = new ArrayList<>();
        if (rutaActiva == null || rutaActiva.size() < 3) return resultado;

        // Encontrar el índice más cercano a `desde` en la ruta
        int idxDesde = indiceMasCercano(rutaActiva, desde);
        // Encontrar el índice más cercano a `hasta` en la ruta
        int idxHasta = indiceMasCercano(rutaActiva, hasta);

        // Asegurarse que desde < hasta en la ruta
        if (idxDesde >= idxHasta) return resultado;

        // Extraer hasta 4 waypoints intermedios equiespaciados
        int rango = idxHasta - idxDesde;
        if (rango <= 2) return resultado; // muy cerca, no hace falta

        int numWp = Math.min(4, rango - 1);
        double paso = (double) rango / (numWp + 1);
        for (int i = 1; i <= numWp; i++) {
            int idx = idxDesde + (int)(i * paso);
            if (idx > idxDesde && idx < idxHasta) {
                resultado.add(rutaActiva.get(idx));
            }
        }
        return resultado;
    }

    private int indiceMasCercano(List<GeoPoint> ruta, GeoPoint punto) {
        int mejor = 0;
        double menorDist = Double.MAX_VALUE;
        for (int i = 0; i < ruta.size(); i++) {
            GeoPoint p = ruta.get(i);
            double dLat = p.getLatitude()  - punto.getLatitude();
            double dLng = p.getLongitude() - punto.getLongitude();
            double dist = dLat * dLat + dLng * dLng;
            if (dist < menorDist) { menorDist = dist; mejor = i; }
        }
        return mejor;
    }

    private void pedirSegmentoConductorADestino(GeoPoint desde) {
        if (desde == null || gpDestino == null) return;
        new Thread(() -> {
            try {
                String seg = desde.getLongitude() + "," + desde.getLatitude() + ";"
                        + lngDestino + "," + latDestino
                        + "?overview=full&geometries=geojson";
                ArrayList<GeoPoint> pts = null;
                try { pts = parsearRutaSimpleOSRM(peticionHttp(OSRM_URL + "/route/v1/driving/" + seg)); }
                catch (Exception ignored) {}
                if (pts == null || pts.size() < 2)
                    try { pts = parsearRutaSimpleOSRM(peticionHttp(OSRM_URL_PUBLIC + "/route/v1/driving/" + seg)); }
                    catch (Exception ignored) {}
                if (pts != null && pts.size() >= 2) {
                    puntosRutaWaypoint = pts;
                    final ArrayList<GeoPoint> fPts = pts;
                    runOnUiThread(() -> { dibujarPolilineaSegmento(fPts); map.invalidate(); });
                }
            } catch (Exception e) { Log.w(TAG, "segmento conductor→destino: " + e.getMessage()); }
        }).start();
    }

    private ArrayList<GeoPoint> parsearRutaSimpleOSRM(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(json);
            if (!"Ok".equals(obj.optString("code", ""))) return null;
            JSONArray routes = obj.optJSONArray("routes");
            if (routes == null || routes.length() == 0) return null;
            JSONArray coords = routes.getJSONObject(0)
                    .getJSONObject("geometry").getJSONArray("coordinates");
            ArrayList<GeoPoint> pts = new ArrayList<>();
            for (int i = 0; i < coords.length(); i++) {
                JSONArray pair = coords.getJSONArray(i);
                pts.add(new GeoPoint(pair.getDouble(1), pair.getDouble(0)));
            }
            return pts.size() >= 2 ? pts : null;
        } catch (Exception e) { return null; }
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
            if (routeOption.distanceKm > 0) {
                distanciaKm = routeOption.distanceKm;
                if (txtDistancia != null)
                    txtDistancia.setText(String.format("%.1f km", distanciaKm));
            }
            if (routeOption.durationMin > 0) {
                duracionMin = routeOption.durationMin;
                if (txtDuracion != null)
                    txtDuracion.setText(String.format("%.0f min", duracionMin));
            }
        }
        generarParadasDinamicas();
        renderizarMapa();
    }

    // REEMPLAZA limpiarOverlays() completo:
    private void limpiarOverlays() {
        if (map == null) return;
        List<Overlay> overlays = map.getOverlays();
        for (int i = overlays.size() - 1; i >= 0; i--) {
            Overlay o = overlays.get(i);
            if (o instanceof Polyline) {
                overlays.remove(i);
            } else if (o instanceof Marker) {
                Marker m = (Marker) o;
                // ← NO borrar el marcador del conductor: se mueve suavemente
                if (!MID_CONDUCTOR.equals(m.getId())) {
                    overlays.remove(i);
                }
            }
        }
    }

    private void dibujarPolilinea(List<GeoPoint> pts, int color) {
        if (pts == null || pts.size() < 2) return;
        final ArrayList<GeoPoint> copia = new ArrayList<>(pts);
        Polyline sombra = new Polyline(map); sombra.setPoints(copia);
        sombra.setColor(Color.argb(60,0,0,0)); sombra.setWidth(24f); map.getOverlays().add(sombra);
        Polyline borde = new Polyline(map); borde.setPoints(copia);
        borde.setColor(Color.WHITE); borde.setWidth(20f); map.getOverlays().add(borde);
        Polyline linea = new Polyline(map); linea.setPoints(copia);
        linea.setColor(color); linea.setWidth(13f); map.getOverlays().add(linea);
    }

    private void dibujarPolilineaSegmento(List<GeoPoint> pts) {
        if (pts == null || pts.size() < 2) return;
        final ArrayList<GeoPoint> copia = new ArrayList<>(pts);
        Polyline borde = new Polyline(map); borde.setPoints(copia);
        borde.setColor(Color.WHITE); borde.setWidth(14f); map.getOverlays().add(borde);
        Polyline linea = new Polyline(map); linea.setPoints(copia);
        linea.setColor(COLOR_SEGMENTO_ACTIVO); linea.setWidth(9f); map.getOverlays().add(linea);
    }

    private void dibujarPolilineaWaypoint(List<GeoPoint> pts) {
        if (pts == null || pts.size() < 2) return;
        final ArrayList<GeoPoint> copia = new ArrayList<>(pts);
        Polyline sombra = new Polyline(map); sombra.setPoints(copia);
        sombra.setColor(Color.argb(50,0,0,0)); sombra.setWidth(18f); map.getOverlays().add(sombra);
        Polyline borde = new Polyline(map); borde.setPoints(copia);
        borde.setColor(Color.WHITE); borde.setWidth(14f); map.getOverlays().add(borde);
        Polyline linea = new Polyline(map); linea.setPoints(copia);
        linea.setColor(COLOR_RUTA_WAYPOINT); linea.setWidth(9f); map.getOverlays().add(linea);
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
        boolean pasajeroRecogido = EST_RECOGIDO.equals(estadoReserva)
                || EST_COMPLETADO.equals(estadoReserva);

        if (esConductor && viajeIniciado) {
            if (gpConductorActual != null) todos.add(gpConductorActual);
            else if (gpOrigen != null) todos.add(gpOrigen);
            if (pasajeroRecogido) {
                if (gpDestino != null) todos.add(gpDestino);
                if (!puntosRutaWaypoint.isEmpty()) todos.addAll(puntosRutaWaypoint);
            } else {
                for (GeoPoint pp : paradasPasajeros) if (pp != null) todos.add(pp);
            }
            if (gpOrigen != null) todos.add(gpOrigen);
            if (gpDestino != null) todos.add(gpDestino);
        } else if (!esConductor && viajeIniciado) {
            // Para el pasajero: enfocar conductor + destino relevante
            if (gpConductorActual != null) todos.add(gpConductorActual);
            if (pasajeroRecogido) {
                if (gpParada  != null) todos.add(gpParada);
                if (gpDestino != null) todos.add(gpDestino);
            } else {
                // Mostrar conductor + punto de subida
                GeoPoint puntoSubida = gpSubida != null ? gpSubida : gpOrigen;
                if (puntoSubida != null) todos.add(puntoSubida);
                if (gpParada   != null) todos.add(gpParada);
            }
            if (gpOrigen  != null) todos.add(gpOrigen);
            if (gpDestino != null) todos.add(gpDestino);
        } else if (!esConductor && pasajeroRecogido) {
            if (!puntosRutaWaypoint.isEmpty()) todos.addAll(puntosRutaWaypoint);
            else {
                if (gpOrigen  != null) todos.add(gpOrigen);
                if (gpDestino != null) todos.add(gpDestino);
            }
        } else {
            if (rutaActiva != null && !rutaActiva.isEmpty()) todos.addAll(rutaActiva);
            if (!esConductor && gpParada  != null) todos.add(gpParada);
            if (!esConductor && gpSubida  != null) todos.add(gpSubida);
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
    //  OSRM — carga de paradas y trazado de ruta
    // =========================================================================
    private void cargarParadasRuta() {
        if (rutaId == 0) { map.postDelayed(this::iniciarTrazadoRuta, 800); return; }
        ConexionApi.getInstance(this).getArrayNoCache(Constantes.paradasPorRuta((long)rutaId),
                arr -> {
                    try {
                        paradasRuta.clear();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject p = arr.getJSONObject(i);
                            paradasRuta.add(p);
                        }
                        if (!esConductor) {
                            mostrarParadaPasajero();
                        } else {
                            ArrayList<String> nombresReales = new ArrayList<>();
                            nombresReales.add(origenActual);
                            for (JSONObject p : paradasRuta) {
                                String nom = p.optString("nombre", "").trim();
                                if (!nom.isEmpty()
                                        && !nom.equals("null")
                                        && !nom.matches("(?i)parada\\s*\\d+")
                                        && !nom.equalsIgnoreCase(origenActual)
                                        && !nom.equalsIgnoreCase(destinoActual)) {
                                    nombresReales.add(nom);
                                }
                            }
                            nombresReales.add(destinoActual);
                            rvHistorialParadas.setAdapter(
                                    new ParadaAdapter(nombresReales, origenActual, destinoActual));
                            cardHistorial.setVisibility(
                                    nombresReales.size() > 2 ? View.VISIBLE : View.GONE);
                        }
                        if ((coordsOrigenInvalidas || coordsDestinoInvalidas) && arr.length() >= 2)
                            recuperarCoordsDesdeParadas(arr);
                    } catch (Exception e) { Log.e(TAG, "Error paradas", e); }
                    map.postDelayed(this::iniciarTrazadoRuta, 800);
                },
                error -> {
                    cardHistorial.setVisibility(View.GONE);
                    map.postDelayed(this::iniciarTrazadoRuta, 800);
                }
        );
    }

    private void mostrarParadaPasajero() {
        runOnUiThread(() -> {
            if (cardHistorial == null) return;
            try {
                String bajada = obtenerNombreBajadaPasajero();
                boolean tieneBajada = bajada != null && !bajada.isEmpty()
                        && !bajada.equals(destinoActual);

                if (!yaReservo && !tieneBajada) {
                    cardHistorial.setVisibility(View.GONE);
                    return;
                }

                ArrayList<String> mis = new ArrayList<>();
                mis.add(origenActual);

                // Paradas intermedias: primero intentar las reales del backend
                boolean hayParadasReales = false;
                for (JSONObject p : paradasRuta) {
                    String nom = p.optString("nombre", "").trim();
                    if (!nom.isEmpty()
                            && !nom.equals("null")
                            && !nom.matches("(?i)parada\\s*\\d+")
                            && !nom.equalsIgnoreCase(origenActual)
                            && !nom.equalsIgnoreCase(destinoActual)
                            && (bajada == null || !nom.equalsIgnoreCase(bajada))) {
                        mis.add(nom);
                        hayParadasReales = true;
                    }
                }

                // Si no hay reales, usar las geocodificadas de paradasDin
                if (!hayParadasReales) {
                    for (ParadaDinamica pd : paradasDin) {
                        if (pd.idxEnRuta == -1) continue; // saltar el destino final
                        if (pd.nombre.equalsIgnoreCase(origenActual)) continue;
                        if (pd.nombre.equalsIgnoreCase(destinoActual)) continue;
                        if (bajada != null && pd.nombre.equalsIgnoreCase(bajada)) continue;
                        mis.add(pd.nombre);
                    }
                }

                if (tieneBajada) mis.add(bajada);
                mis.add(destinoActual);

                rvHistorialParadas.setAdapter(new ParadaPasajeroAdapter(mis, bajada));
                cardHistorial.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                Log.e(TAG, "Error mostrarParadaPasajero", e);
                cardHistorial.setVisibility(View.GONE);
            }
        });
    }

    private String obtenerNombreBajadaPasajero() {
        if (miParadaBajada != null && !miParadaBajada.isEmpty()) return miParadaBajada;
        return null;
    }

    private void recuperarCoordsDesdeParadas(JSONArray arr) {
        try {
            JSONObject pOrig=null, pDest=null; int maxOrden=-1;
            for (int i=0;i<arr.length();i++) {
                JSONObject p=arr.optJSONObject(i); if(p==null) continue;
                int orden=p.optInt("orden",-1);
                String tipo=p.optString("tipo","").toUpperCase();
                double pLat=p.optDouble("lat",Double.NaN);
                if(Double.isNaN(pLat)||pLat==0) continue;
                if((orden==0||"SUBIDA".equals(tipo))&&pOrig==null) pOrig=p;
                if("BAJADA".equals(tipo)) pDest=p;
                else if(orden>maxOrden){maxOrden=orden;if(pOrig==null||orden!=0) pDest=p;}
            }
            if(pOrig==null) pOrig=arr.optJSONObject(0);
            if(pDest==null||pDest==pOrig) pDest=arr.optJSONObject(arr.length()-1);
            if(pOrig!=null&&coordsOrigenInvalidas){
                double lo=pOrig.optDouble("lat",Double.NaN), ln=pOrig.optDouble("lng",Double.NaN);
                if(!Double.isNaN(lo)&&lo!=0){latOrigen=lo;lngOrigen=ln;
                    coordsOrigenInvalidas=false;gpOrigen=new GeoPoint(latOrigen,lngOrigen);
                    String nom=pOrig.optString("nombre","").trim();
                    if(!nom.isEmpty()&&!nom.equals("null")&&origenActual.equals("Origen")) origenActual=nom;}}
            if(pDest!=null&&pDest!=pOrig&&coordsDestinoInvalidas){
                double ld=pDest.optDouble("lat",Double.NaN), lg=pDest.optDouble("lng",Double.NaN);
                if(!Double.isNaN(ld)&&ld!=0&&!sonIguales(latOrigen,lngOrigen,ld,lg)){
                    latDestino=ld;lngDestino=lg;gpDestino=new GeoPoint(latDestino,lngDestino);
                    coordsDestinoInvalidas=false;
                    String nom=pDest.optString("nombre","").trim();
                    if(!nom.isEmpty()&&!nom.equals("null")&&destinoActual.equals("Destino")) destinoActual=nom;}}
            if(txtRuta!=null)
                runOnUiThread(()->txtRuta.setText(""+origenActual+" → "+destinoActual));
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
                if (coordsOrigenInvalidas) {
                    double[] c=geocodificarTexto(origenActual);
                    if(c!=null){latOrigen=c[0];lngOrigen=c[1];
                        coordsOrigenInvalidas=false;gpOrigen=new GeoPoint(latOrigen,lngOrigen);}
                }
                if (coordsDestinoInvalidas||sonIguales(latOrigen,lngOrigen,latDestino,lngDestino)) {
                    double[] c=geocodificarTexto(destinoActual);
                    if(c!=null){latDestino=c[0];lngDestino=c[1];
                        coordsDestinoInvalidas=false;gpDestino=new GeoPoint(latDestino,lngDestino);}
                }
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
                try { parsearRutasOSRM(peticionHttp(OSRM_URL+"/route/v1/driving/"+segmento), rutasEncontradas, metricas); }
                catch(Exception ignored){}
                if (rutasEncontradas.isEmpty()) {
                    try { parsearRutasOSRM(peticionHttp(OSRM_URL_PUBLIC+"/route/v1/driving/"+segmento), rutasEncontradas, metricas); }
                    catch(Exception ignored){}
                }
                if (rutasEncontradas.isEmpty()) { runOnUiThread(this::usarLineaRecta); return; }
                todasLasRutas.clear(); listaRouteOptions.clear();
                for (int ri=0;ri<rutasEncontradas.size();ri++) {
                    todasLasRutas.add(rutasEncontradas.get(ri));
                    double[] met=metricas.get(ri);
                    RouteOption ro=new RouteOption();
                    ro.distanceKm=met[0]; ro.durationMin=met[1];
                    listaRouteOptions.add(ro);
                }
                final int idx=(indiceRuta>=0&&indiceRuta<todasLasRutas.size())?indiceRuta:0;
                runOnUiThread(() -> seleccionarRuta(idx, listaRouteOptions.get(idx)));
            } catch(Exception e) {
                Log.e(TAG,"pedirRutaPrincipal error",e);
                runOnUiThread(this::usarLineaRecta);
            }
        }).start();
    }

    private void parsearRutasOSRM(String json, ArrayList<ArrayList<GeoPoint>> dest,
                                  ArrayList<double[]> metricas) throws Exception {
        if (json==null||json.isEmpty()) return;
        JSONObject obj=new JSONObject(json);
        if(!"Ok".equals(obj.optString("code",""))) return;
        JSONArray routes=obj.optJSONArray("routes"); if(routes==null) return;
        for(int ri=0;ri<routes.length();ri++){
            JSONObject route=routes.getJSONObject(ri);
            JSONArray coords=route.getJSONObject("geometry").getJSONArray("coordinates");
            ArrayList<GeoPoint> pts=new ArrayList<>();
            for(int ci=0;ci<coords.length();ci++){
                JSONArray pair=coords.getJSONArray(ci);
                pts.add(new GeoPoint(pair.getDouble(1),pair.getDouble(0)));}
            if(pts.size()<2) continue;
            dest.add(pts);
            metricas.add(new double[]{route.optDouble("distance",0)/1000.0,route.optDouble("duration",0)/60.0});
        }
    }

    private void usarLineaRecta() {
        if (gpOrigen==null||gpDestino==null) return;
        ArrayList<GeoPoint> linea=new ArrayList<>();
        linea.add(gpOrigen); linea.add(gpDestino);
        todasLasRutas.clear(); todasLasRutas.add(linea); listaRouteOptions.clear();
        RouteOption ro=new RouteOption(); ro.distanceKm=distanciaKm; ro.durationMin=duracionMin;
        listaRouteOptions.add(ro);
        rutaActiva=java.util.Collections.unmodifiableList(new ArrayList<>(linea));
        indiceRutaActiva=0; generarParadasDinamicas(); renderizarMapa();
    }

    private void pedirRutaConWaypoint() {
        if (gpParada==null) return;
        new Thread(() -> {
            try {
                String url=OSRM_URL+"/route/v1/driving/"
                        +lngOrigen+","+latOrigen+";"
                        +gpParada.getLongitude()+","+gpParada.getLatitude()+";"
                        +lngDestino+","+latDestino+"?overview=full&geometries=geojson";
                String json=peticionHttp(url);
                JSONObject obj=new JSONObject(json);
                if(!"Ok".equals(obj.optString("code"))) return;
                JSONArray coords=obj.getJSONArray("routes").getJSONObject(0)
                        .getJSONObject("geometry").getJSONArray("coordinates");
                ArrayList<GeoPoint> pts=new ArrayList<>();
                for(int i=0;i<coords.length();i++){
                    JSONArray c=coords.getJSONArray(i);
                    pts.add(new GeoPoint(c.getDouble(1),c.getDouble(0)));}
                if(pts.size()>=2){puntosRutaWaypoint=pts; runOnUiThread(this::renderizarMapa);}
            } catch(Exception e){Log.w(TAG,"Ruta waypoint falló",e);fallbackOsrmWaypoint();}
        }).start();
    }

    private void fallbackOsrmWaypoint() {
        if(gpOrigen==null||gpDestino==null) return;
        ArrayList<GeoPoint> linea=new ArrayList<>();
        linea.add(gpOrigen); if(gpParada!=null) linea.add(gpParada); linea.add(gpDestino);
        puntosRutaWaypoint=linea; runOnUiThread(this::renderizarMapa);
    }

    // =========================================================================
    //  MI RESERVA — pasajero
    // =========================================================================
    private void cargarMiReservaPasajero() {
        if (esConductor || cardMiReserva == null) return;
        int miId = session.getIdUsuario();

        ConexionApi.getInstance(this).getObjectNoCache(Constantes.viajePorId((long) viajeId),
                viajeObj -> {
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
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
                            if (idU != miId) continue;

                            final String est = u.optString("estado", "CONFIRMADA").toUpperCase();
                            final int idR = u.optInt("idUsuarioViaje",
                                    u.optInt("idReserva", u.optInt("id", -1)));

                            // ── Bajada ──
                            double latP = u.optDouble("latBajada", u.optDouble("latParada", 0));
                            double lngP = u.optDouble("lngBajada", u.optDouble("lngParada", 0));
                            String nomP = u.optString("nombreParadaBajada", u.optString("nombreParada", ""));
                            if (latP == 0 || lngP == 0) {
                                JSONObject po = u.optJSONObject("parada");
                                if (po != null) {
                                    latP = po.optDouble("lat", po.optDouble("latitud", 0));
                                    lngP = po.optDouble("lng", po.optDouble("longitud", 0));
                                    if (nomP.isEmpty()) nomP = po.optString("nombre", "");
                                }
                            }

                            // ── Subida (punto de recogida) ──
                            double latS = u.optDouble("latSubida",
                                    u.optDouble("latOrigen", u.optDouble("latInicio", 0)));
                            double lngS = u.optDouble("lngSubida",
                                    u.optDouble("lngOrigen", u.optDouble("lngInicio", 0)));
                            String nomS = u.optString("nombreParadaSubida",
                                    u.optString("nombreParadaInicio", ""));

                            final double fLatP = latP, fLngP = lngP;
                            final String fNomP = nomP.isEmpty() ? destinoActual : nomP;
                            final double fLatS = latS, fLngS = lngS;
                            final String fNomS = nomS;
                            final int fIdR = idR;
                            final JSONObject uFinal = u;

                            runOnUiThread(() -> {
                                yaReservo       = true;
                                idReservaActual = fIdR;
                                estadoReserva   = est;

                                // Asignar punto de bajada
                                if (fLatP != 0) {
                                    gpParada    = new GeoPoint(fLatP, fLngP);
                                    nombreParada = fNomP;
                                }

                                // Asignar punto de subida (recogida)
                                if (fLatS != 0 && !sonIguales(fLatS, fLngS, latOrigen, lngOrigen)) {
                                    gpSubida             = new GeoPoint(fLatS, fLngS);
                                    nombreSubidaPasajero = fNomS.isEmpty() ? origenActual : fNomS;
                                } else {
                                    gpSubida             = null;
                                    nombreSubidaPasajero = "";
                                }

                                mostrarCardMiReserva(construirReservaJson(uFinal, est, fIdR, fLatP, fLngP, fNomP));
                                actualizarBotonPasajero();
                                actualizarChipsCupos(cuposTotales, cuposDisponibles);
                                if (EST_RECOGIDO.equals(est) || EST_COMPLETADO.equals(est))
                                    pedirRutaConWaypoint();
                                else renderizarMapa();
                            });
                            return;
                        }
                        cargarMiReservaFallback(miId);
                    } else {
                        cargarMiReservaFallback(miId);
                    }
                },
                err -> cargarMiReservaFallback(miId)
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
        } catch (Exception e) { Log.e(TAG, "construirReservaJson: " + e.getMessage()); }
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
                        String est  = res.optString("estado","").toUpperCase();
                        int    idR  = res.optInt("idReserva", res.optInt("id", -1));

                        // Bajada
                        double latP = res.optDouble("latParada", res.optDouble("latBajada", 0));
                        double lngP = res.optDouble("lngParada", res.optDouble("lngBajada", 0));
                        String nomP = res.optString("nombreParada", res.optString("nombreParadaBajada", destinoActual));

                        // Subida
                        double latS = res.optDouble("latSubida", res.optDouble("latOrigen", 0));
                        double lngS = res.optDouble("lngSubida", res.optDouble("lngOrigen", 0));
                        String nomS = res.optString("nombreParadaSubida", "");

                        final double fLatP = latP, fLngP = lngP;
                        final String fNomP = nomP, fEst = est;
                        final double fLatS = latS, fLngS = lngS;
                        final String fNomS = nomS;
                        final JSONObject mr = res;

                        runOnUiThread(() -> {
                            yaReservo=true; idReservaActual=idR; estadoReserva=fEst;

                            if (fLatP != 0) { gpParada=new GeoPoint(fLatP,fLngP); nombreParada=fNomP; }

                            // Asignar punto de subida
                            if (fLatS != 0 && !sonIguales(fLatS, fLngS, latOrigen, lngOrigen)) {
                                gpSubida             = new GeoPoint(fLatS, fLngS);
                                nombreSubidaPasajero = fNomS.isEmpty() ? origenActual : fNomS;
                            } else {
                                gpSubida             = null;
                                nombreSubidaPasajero = "";
                            }

                            mostrarCardMiReserva(mr);
                            actualizarBotonPasajero();
                            actualizarChipsCupos(cuposTotales, cuposDisponibles);
                            if (EST_RECOGIDO.equals(fEst)||EST_COMPLETADO.equals(fEst)) pedirRutaConWaypoint();
                            else renderizarMapa();
                        });
                        return;
                    }
                    runOnUiThread(() -> {
                        if (cardMiReserva != null) cardMiReserva.setVisibility(View.GONE);
                        actualizarBotonPasajero(); renderizarMapa();
                    });
                },
                error -> { Log.e(TAG,"[MiReserva fallback] Error"); runOnUiThread(this::renderizarMapa); }
        );
    }

    private void mostrarCardMiReserva(JSONObject reserva) {
        if (cardMiReserva == null || txtMiReservaInfo == null) return;
        String estado = reserva.optString("estado", "").toUpperCase();
        if (EST_CANCELADO.equals(estado)) {
            cardMiReserva.setVisibility(View.GONE);
            yaReservo = false;
            return;
        }
        String np = reserva.optString("nombreParada", destinoActual);
        int asi = reserva.optInt("numeroAsientos", reserva.optInt("asientos", 1));

        // ── Precio con fallbacks ──
        double pre = reserva.optDouble("precio",
                reserva.optDouble("precioFinal",
                        reserva.optDouble("costoPorPasajero", 0)));
        if (pre <= 0) pre = precioViaje;
        if (pre <= 0 && distanciaKm > 0)
            pre = Math.ceil((distanciaKm * 700.0) / 100.0) * 100.0;

        miParadaBajada = np.isEmpty() ? destinoActual : np;
        actualizarValorFila(ROW_ID_ASIENTOS, asi + (asi == 1 ? " asiento" : " asientos"));
        actualizarValorFila(ROW_ID_BAJADA, miParadaBajada);
        double precioMostrar = precioCalculadoPasajero > 0 ? precioCalculadoPasajero : pre;
        actualizarValorFila(ROW_ID_PRECIO, precioMostrar > 0
                ? String.format(Locale.getDefault(), "%.0f COP", precioMostrar)
                : "No definido");

        int bgColor, strokeColor;
        switch (estado) {
            case EST_ESPERANDO_RECOGIDA:
                bgColor = Color.parseColor("#FFF8E1");
                strokeColor = Color.parseColor("#FFE082");
                break;
            case EST_RECOGIDO:
                bgColor = Color.parseColor("#E0F7FA");
                strokeColor = Color.parseColor("#80DEEA");
                break;
            case EST_COMPLETADO:
                bgColor = Color.parseColor("#E8F5E9");
                strokeColor = Color.parseColor("#A5D6A7");
                break;
            default:
                bgColor = Color.parseColor("#F0FAFA");
                strokeColor = Color.parseColor("#80CBC4");
                break;
        }
        cardMiReserva.setCardBackgroundColor(bgColor);
        cardMiReserva.setStrokeColor(strokeColor);
        cardMiReserva.setVisibility(View.VISIBLE);

        if (!esConductor && cardHistorial != null) mostrarParadaPasajero();

        // ── Mostrar/ocultar botón de pago según estado ────────────────────────────
        // ── Mostrar/ocultar botón de pago según estado ──
        if (!esConductor) {
            boolean viajeTerminado = "FINALIZADO".equals(estadoViaje)
                    || "COMPLETADO".equals(estadoViaje);

            if (viajeTerminado) {
                final double montoFinal = precioCalculadoPasajero > 0 ? precioCalculadoPasajero : pre;
                mostrarBotonPago(montoFinal, nombreConductorViaje);
                View divider = findViewById(R.id.divider_pago);
                if (divider != null) divider.setVisibility(View.VISIBLE);
            } else {
                if (btnPagarViaje != null) btnPagarViaje.setVisibility(View.GONE);
                View divider = findViewById(R.id.divider_pago);
                if (divider != null) divider.setVisibility(View.GONE);
            }
        }
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
            body.put("orden", paradasDin.indexOf(pd) + 1);
            body.put("kmAcumulado", 0); body.put("tipo","AMBAS");
        } catch (JSONException e) { hacerReserva(pd); return; }
        ConexionApi.getInstance(this).post(Constantes.PARADAS, body,
                response -> { pd.idParadaBD = response.optInt("idParada", response.optInt("id", 0)); hacerReserva(pd); },
                error -> hacerReserva(pd));
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
            body.put("latSubida",          latOrigen);
            body.put("lngSubida",          lngOrigen);
            body.put("nombreParadaSubida", origenActual);
            body.put("latOrigen",          latOrigen);
            body.put("lngOrigen",          lngOrigen);
            body.put("latInicio",          latOrigen);
            body.put("lngInicio",          lngOrigen);
            body.put("nombreParadaInicio", origenActual);
            body.put("origenLat",          latOrigen);
            body.put("origenLng",          lngOrigen);
            body.put("latBajada",          pd.lat);
            body.put("lngBajada",          pd.lng);
            body.put("nombreParadaBajada", pd.nombre);
            body.put("latParada",          pd.lat);
            body.put("lngParada",          pd.lng);
            body.put("nombreParada",       pd.nombre);
            body.put("latDestino",         pd.lat);
            body.put("lngDestino",         pd.lng);

            // Intentar asociar la parada de SUBIDA al origen real de la ruta
            // para que el backend pueda usar la lógica de segment-fares (idParadaSubida + idParadaBajada)
            int idParadaSubidaBD = 0;
            if (!paradasRuta.isEmpty()) {
                for (JSONObject p : paradasRuta) {
                    String nom = p.optString("nombre", "").trim();
                    String tipo = p.optString("tipo", "");
                    int idP = p.optInt("idParada", p.optInt("id", 0));

                    boolean esOrigenNombre = !nom.isEmpty() && nom.equalsIgnoreCase(origenActual);
                    boolean esOrigenTipo = tipo != null && tipo.toUpperCase(Locale.ROOT).contains("ORIGEN");

                    if (idP > 0 && (esOrigenNombre || esOrigenTipo)) {
                        idParadaSubidaBD = idP;
                        break;
                    }
                }
            }
            if (idParadaSubidaBD > 0) {
                body.put("idParadaSubida", idParadaSubidaBD);
                body.put("idParadaInicio", idParadaSubidaBD);
            }

            if (pd.idParadaBD > 0) {
                // Parada de BAJADA
                body.put("idParadaBajada", pd.idParadaBD);
                body.put("idParadaFin",    pd.idParadaBD);
                body.put("idParada",       pd.idParadaBD);
            }
            body.put("idUsuarios",         idUsuario);
            body.put("idViajes",           viajeId);
            body.put("asientosReservados", 1);
            body.put("precioFinal",        precioViaje > 0 ? precioViaje : 0);
            body.put("estado",             "CONFIRMADA");
            body.put("idUsuario",          idUsuario);
            body.put("idViaje",            viajeId);
            body.put("asientos",           1);
            body.put("precio",             precioViaje > 0 ? precioViaje : 0);
        } catch (JSONException e) { loaderDetalle.setVisibility(View.GONE); return; }

        ConexionApi.getInstance(this).post(Constantes.RESERVAS, body,
                response -> {
                    loaderDetalle.setVisibility(View.GONE);
                    idReservaActual = response.optInt("idUsuarioViaje",
                            response.optInt("idReserva", response.optInt("id", response.optInt("reservaId", -1))));
                    estadoReserva = response.optString("estado", EST_CONFIRMADA).toUpperCase();
                    yaReservo = true;
                    gpParada = pd.toGeoPoint();
                    nombreParada = pd.nombre;
                    gpSubida = null;
                    nombreSubidaPasajero = "";
                    iniciarTimerCambioReserva();
                    runOnUiThread(() -> {
                        cuposDisponibles = Math.max(0, cuposDisponibles - 1);
                        actualizarChipsCupos(cuposTotales, cuposDisponibles);
                        actualizarBotonPasajero();
                        actualizarBotonChat();
                        renderizarMapa();
                        mostrarCardMiReserva(response);
                    });
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
            try {
                JSONObject errJson=new JSONObject(bodyError);
                String bm=errJson.optString("message",errJson.optString("error",errJson.optString("mensaje","")));
                if(!bm.isEmpty()&&!bm.equals("null")) mensaje="HTTP "+statusCode+": "+bm;
                else mensaje="HTTP "+statusCode+": "+bodyError.substring(0,Math.min(bodyError.length(),200));
            } catch(Exception e){
                mensaje="HTTP "+statusCode+": "+bodyError.substring(0,Math.min(bodyError.length(),200));
            }
            if (statusCode==409) {
                yaReservo=true;
                runOnUiThread(()-> { actualizarBotonPasajero(); cargarMiReservaPasajero(); });
                Toast.makeText(this,"⚠️ Ya tienes una reserva.",Toast.LENGTH_LONG).show(); return;
            }
            if (statusCode==401) mensaje="❌ Sesión expirada.";
        } else if (error instanceof com.android.volley.NoConnectionError) {
            mensaje="❌ Sin conexión.";
        } else if (error instanceof com.android.volley.TimeoutError) {
            mensaje="❌ El servidor tardó mucho.";
        }
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
    }

    private void cancelarMiReserva() {
        if (idReservaActual <= 0) {
            Toast.makeText(this,"Sin reserva activa.",Toast.LENGTH_SHORT).show(); return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Cancelar reserva").setMessage("¿Estás seguro?")
                .setPositiveButton("Sí, cancelar",(d,w) -> {
                    loaderDetalle.setVisibility(View.VISIBLE);
                    ConexionApi.getInstance(this).post(Constantes.reservaCancelar((long)idReservaActual), null,
                            response -> {
                                loaderDetalle.setVisibility(View.GONE);
                                yaReservo=false; idReservaActual=-1; estadoReserva=EST_CANCELADO;
                                gpParada=null; nombreParada="";
                                gpSubida=null; nombreSubidaPasajero="";
                                Toast.makeText(this,"✅ Reserva cancelada.",Toast.LENGTH_LONG).show();
                                runOnUiThread(() -> {
                                    cuposDisponibles=Math.min(cuposTotales,cuposDisponibles+1);
                                    actualizarChipsCupos(cuposTotales,cuposDisponibles);
                                    actualizarBotonPasajero();
                                    if(cardMiReserva!=null) cardMiReserva.setVisibility(View.GONE);
                                    renderizarMapa();
                                });
                            },
                            error -> { loaderDetalle.setVisibility(View.GONE);
                                Toast.makeText(this,"Error al cancelar.",Toast.LENGTH_LONG).show(); });
                }).setNegativeButton("No",null).show();
    }

    private void cambiarParada(ParadaDinamica pd) {
        loaderDetalle.setVisibility(View.VISIBLE);
        if (pd.idParadaBD > 0) { actualizarReservaParada(pd); return; }
        try {
            JSONObject body=new JSONObject();
            body.put("idRuta",rutaId); body.put("nombre",pd.nombre);
            body.put("lat",pd.lat); body.put("lng",pd.lng); body.put("tipo","AMBAS");
            ConexionApi.getInstance(this).post(Constantes.PARADAS, body,
                    r->{pd.idParadaBD=r.optInt("idParada",r.optInt("id",0)); actualizarReservaParada(pd);},
                    e->actualizarReservaParada(pd));
        } catch(Exception e) { actualizarReservaParada(pd); }
    }

    private void actualizarReservaParada(ParadaDinamica pd) {
        if (idReservaActual<=0){
            loaderDetalle.setVisibility(View.GONE);
            gpParada=pd.toGeoPoint(); nombreParada=pd.nombre;
            renderizarMapa(); actualizarBotonPasajero(); return;
        }
        try {
            JSONObject body=new JSONObject();
            body.put("nombreParada",pd.nombre);
            body.put("latParada",pd.lat); body.put("lngParada",pd.lng);
            if(pd.idParadaBD>0) body.put("idParada",pd.idParadaBD);
            ConexionApi.getInstance(this).put(Constantes.RESERVAS+"/"+idReservaActual, body,
                    r->{loaderDetalle.setVisibility(View.GONE);
                        gpParada=pd.toGeoPoint(); nombreParada=pd.nombre;
                        renderizarMapa(); actualizarBotonPasajero();
                        Toast.makeText(this,"✅ Parada cambiada.",Toast.LENGTH_SHORT).show();
                        cargarMiReservaPasajero();},
                    e->{loaderDetalle.setVisibility(View.GONE);
                        gpParada=pd.toGeoPoint(); nombreParada=pd.nombre; renderizarMapa();});
        } catch(Exception e){loaderDetalle.setVisibility(View.GONE);}
    }

    // =========================================================================
    //  RESERVAS CONDUCTOR
    // =========================================================================
    private void cargarReservasConductor() {
        if (!esConductor || cardPasajeros == null) return;

        ConexionApi.getInstance(this).getObjectNoCache(Constantes.viajePorId((long) viajeId),
                viajeObj -> {
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    Log.d(TAG, "usuarios[] en viaje " + viajeId + ": "
                            + (usuarios != null ? usuarios.length() : "null"));

                    if (usuarios != null && usuarios.length() > 0) {
                        // Normalizar al mismo formato que espera procesarReservasConductor
                        JSONArray reservasNormalizadas = normalizarUsuariosParaConductor(usuarios);
                        procesarReservasConductor(reservasNormalizadas);
                    } else {
                        // Fallback: endpoint directo de reservas
                        String urlReservas = Constantes.BASE_URL + "/api/reservas/viaje/" + viajeId;
                        ConexionApi.getInstance(this).getArrayNoCache(urlReservas,
                                reservas -> {
                                    if (reservas != null && reservas.length() > 0)
                                        procesarReservasConductor(reservas);
                                    else
                                        runOnUiThread(this::mostrarSinPasajeros);
                                },
                                err -> runOnUiThread(this::mostrarSinPasajeros));
                    }
                },
                err -> {
                    String urlReservas = Constantes.BASE_URL + "/api/reservas/viaje/" + viajeId;
                    ConexionApi.getInstance(this).getArrayNoCache(urlReservas,
                            reservas -> {
                                if (reservas != null && reservas.length() > 0)
                                    procesarReservasConductor(reservas);
                                else
                                    runOnUiThread(this::mostrarSinPasajeros);
                            },
                            err2 -> runOnUiThread(this::mostrarSinPasajeros));
                }
        );
    }

    // NUEVO método normalizador específico para DetalleViajeActivity
    private JSONArray normalizarUsuariosParaConductor(JSONArray usuarios) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < usuarios.length(); i++) {
            try {
                JSONObject u = usuarios.getJSONObject(i);
                JSONObject reserva = new JSONObject();

                reserva.put("estado", u.optString("estado", "CONFIRMADA"));
                int asi = u.optInt("numeroAsientos", u.optInt("asientos", 1));
                reserva.put("numeroAsientos", asi);
                reserva.put("asientos", asi);

                int idRes = u.optInt("idUsuarioViaje",
                        u.optInt("idReserva", u.optInt("id", -1)));
                if (idRes > 0) reserva.put("idReserva", idRes);

                // Parada de bajada
                double latP = u.optDouble("latBajada", u.optDouble("latParada", 0));
                double lngP = u.optDouble("lngBajada", u.optDouble("lngParada", 0));
                String nomP = u.optString("nombreParadaBajada",
                        u.optString("nombreParada", ""));
                if (latP == 0 || lngP == 0) {
                    JSONObject po = u.optJSONObject("parada");
                    if (po == null) po = u.optJSONObject("paradaBajada");
                    if (po != null) {
                        if (latP == 0) latP = po.optDouble("lat", po.optDouble("latitud", 0));
                        if (lngP == 0) lngP = po.optDouble("lng", po.optDouble("longitud", 0));
                        if (nomP.isEmpty()) nomP = po.optString("nombre", "");
                    }
                }
                if (latP != 0) {
                    reserva.put("latParada", latP);
                    reserva.put("lngParada", lngP);
                }
                if (!nomP.isEmpty()) reserva.put("nombreParada", nomP);

                // Objeto pasajero/usuario
                JSONObject usuarioObj = u.optJSONObject("usuario");
                if (usuarioObj != null) {
                    int idU = usuarioObj.optInt("idUsuarios",
                            usuarioObj.optInt("id", 0));
                    if (idU > 0) usuarioObj.put("id", idU);
                    reserva.put("pasajero", usuarioObj);
                    reserva.put("usuario", usuarioObj);
                } else {
                    int idU = u.optInt("idUsuarios", u.optInt("idPasajero", 0));
                    JSONObject fallback = new JSONObject();
                    fallback.put("id", idU);
                    fallback.put("idUsuarios", idU);
                    fallback.put("nombre",
                            u.optString("nombrePasajero",
                                    u.optString("nombre", "Pasajero")));
                    reserva.put("pasajero", fallback);
                    reserva.put("usuario", fallback);
                }

                result.put(reserva);
            } catch (Exception e) {
                Log.e(TAG, "normalizarUsuariosParaConductor[" + i + "]: " + e.getMessage());
            }
        }
        return result;
    }
    private JSONArray normalizarUsuariosDeViaje(JSONArray usuarios) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < usuarios.length(); i++) {
            try {
                JSONObject u = usuarios.getJSONObject(i);
                JSONObject reserva = new JSONObject();
                reserva.put("estado", u.optString("estado", "CONFIRMADA"));
                int asi = u.optInt("numeroAsientos", u.optInt("asientos", 1));
                reserva.put("numeroAsientos", asi); reserva.put("asientos", asi);
                int idRes = u.optInt("idUsuarioViaje", u.optInt("idReserva", u.optInt("id", -1)));
                if (idRes > 0) reserva.put("idReserva", idRes);

                double latP = u.optDouble("latBajada", 0);
                double lngP = u.optDouble("lngBajada", 0);
                String nomP = u.optString("nombreParadaBajada", "");
                if (latP == 0) latP = u.optDouble("latParada", 0);
                if (lngP == 0) lngP = u.optDouble("lngParada", 0);
                if (nomP.isEmpty()) nomP = u.optString("nombreParada", "");
                if (latP == 0 || lngP == 0) {
                    JSONObject po = u.optJSONObject("parada");
                    if (po == null) po = u.optJSONObject("paradaBajada");
                    if (po != null) {
                        if (latP == 0) latP = po.optDouble("lat", po.optDouble("latitud", 0));
                        if (lngP == 0) lngP = po.optDouble("lng", po.optDouble("longitud", 0));
                        if (nomP.isEmpty()) nomP = po.optString("nombre", "");
                    }
                }
                if (latP != 0) { reserva.put("latParada", latP); reserva.put("lngParada", lngP); }
                if (!nomP.isEmpty()) reserva.put("nombreParada", nomP);

                JSONObject usuarioObj = u.optJSONObject("usuario");
                if (usuarioObj != null) {
                    int idU = usuarioObj.optInt("idUsuarios", usuarioObj.optInt("id", 0));
                    if (idU > 0) usuarioObj.put("id", idU);
                    reserva.put("pasajero", usuarioObj); reserva.put("usuario", usuarioObj);
                } else {
                    int idU = u.optInt("idUsuarios", u.optInt("idPasajero", 0));
                    JSONObject fallback = new JSONObject();
                    fallback.put("id", idU); fallback.put("idUsuarios", idU);
                    fallback.put("nombre", u.optString("nombrePasajero", u.optString("nombre","Pasajero")));
                    reserva.put("pasajero", fallback); reserva.put("usuario", fallback);
                }
                result.put(reserva);
            } catch (Exception e) { Log.e(TAG, "normalizarUsuariosDeViaje[" + i + "]: " + e.getMessage()); }
        }
        return result;
    }

    private void procesarReservasConductor(JSONArray reservas) {
        runOnUiThread(() -> {
            try {
                paradasPasajeros.clear(); nombresPasajerosParadas.clear();
                gpSubida = null; nombreSubidaPasajero = "";
                layoutListaPasajeros.removeAllViews();
                if (reservas == null || reservas.length() == 0) { mostrarSinPasajeros(); return; }

                int total = 0; boolean hayEsperando = false; int colorIdx = 0;
                final int[] totalFinal = {0};

                for (int i = 0; i < reservas.length(); i++) {
                    JSONObject res = reservas.getJSONObject(i);
                    String est = res.optString("estado","").toUpperCase().trim();
                    if (ESTADOS_CANCELADOS.contains(est)) continue;

                    String np = "";
                    JSONObject po = null;
                    for (String k : new String[]{"pasajero","usuario","user","passenger"}) {
                        JSONObject cc = res.optJSONObject(k); if (cc != null) { po = cc; break; }
                    }
                    if (po != null) {
                        for (String k : new String[]{"nombre","nombreCompleto","name","nombres"}) {
                            String n = po.optString(k,""); if (!n.isEmpty() && !n.equals("null")) { np = n; break; }
                        }
                        if (np.isEmpty()) {
                            String n=po.optString("nombres",""),a=po.optString("apellidos","");
                            if(!n.isEmpty()||!a.isEmpty()) np=(n+" "+a).trim();
                        }
                    }
                    if (np.isEmpty()) np = res.optString("nombrePasajero","Pasajero "+(colorIdx+1));

                    int asi = res.optInt("numeroAsientos", res.optInt("asientos", 1));
                    total += asi;
                    totalFinal[0] += asi;

                    String par   = res.optString("nombreParada","");
                    int    idRes = res.optInt("idReserva", res.optInt("id", -1));
                    double latP  = res.optDouble("latParada", 0);
                    double lngP  = res.optDouble("lngParada", 0);
                    if (latP == 0 || lngP == 0) {
                        JSONObject paradaObj = res.optJSONObject("parada");
                        if (paradaObj != null) {
                            latP = paradaObj.optDouble("lat", paradaObj.optDouble("latitud", 0));
                            lngP = paradaObj.optDouble("lng", paradaObj.optDouble("longitud", 0));
                            if (par.isEmpty()) par = paradaObj.optString("nombre","");
                        }
                    }

                    // Punto de SUBIDA del pasajero
                    double latS = res.optDouble("latSubida",
                            res.optDouble("latOrigen", res.optDouble("latInicio", 0)));
                    double lngS = res.optDouble("lngSubida",
                            res.optDouble("lngOrigen", res.optDouble("lngInicio", 0)));
                    String nomS = res.optString("nombreParadaSubida",
                            res.optString("nombreParadaInicio", ""));

                    if (gpSubida == null && latS != 0 && lngS != 0
                            && !sonIguales(latS, lngS, latOrigen, lngOrigen)) {
                        gpSubida = new GeoPoint(latS, lngS);
                        nombreSubidaPasajero = nomS.isEmpty() ? np : nomS;
                    }

                    boolean tieneParada = latP != 0 && lngP != 0;
                    int    pasColor    = tieneParada ? COLORES_PASAJEROS[colorIdx % COLORES_PASAJEROS.length] : Color.parseColor("#607D8B");
                    String pasColorHex = tieneParada ? COLORES_PASAJEROS_HEX[colorIdx % COLORES_PASAJEROS_HEX.length] : "#607D8B";

                    if (tieneParada) {
                        paradasPasajeros.add(new GeoPoint(latP, lngP));
                        nombresPasajerosParadas.add(np+(par.isEmpty()?"":" → "+par));
                    }

                    resolverNombreParada(latP, lngP, np, asi, par, est, idRes,
                            tieneParada ? "P"+(colorIdx+1) : "?",
                            pasColor, pasColorHex, tieneParada);

                    if (tieneParada) colorIdx++;
                    if (EST_ESPERANDO_RECOGIDA.equals(est)) {
                        hayEsperando = true;
                        if (gpParada == null && tieneParada) {
                            gpParada = new GeoPoint(latP,lngP);
                            nombreParada = par.isEmpty() ? np : par;
                        }
                    }
                }

                // ── Actualizar contador con delay para esperar geocodificación ──
                final boolean fHayEsperando = hayEsperando;
                final int fTotal = total;

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    int countFilas = layoutListaPasajeros.getChildCount();
                    if (countFilas == 0 && fTotal > 0) {
                        // Geocodificación aún en progreso, reintentar
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            int c2 = layoutListaPasajeros.getChildCount();
                            if (c2 == 0) {
                                mostrarSinPasajeros();
                            } else {
                                txtTotalPasajeros.setText(c2 + " pasajero(s) · " + fTotal + " asiento(s)");
                                cardPasajeros.setVisibility(View.VISIBLE);
                            }
                        }, 1500);
                    } else if (countFilas == 0) {
                        mostrarSinPasajeros();
                    } else {
                        txtTotalPasajeros.setText(countFilas + " pasajero(s) · " + fTotal + " asiento(s)");
                        cardPasajeros.setVisibility(View.VISIBLE);
                    }
                    if (btnRecoger != null) btnRecoger.setVisibility(fHayEsperando ? View.VISIBLE : View.GONE);
                    renderizarMapa();
                }, 800);

            } catch (Exception e) {
                Log.e(TAG,"Error procesando reservas conductor",e);
                mostrarSinPasajeros();
            }
        });
    }

    private void resolverNombreParada(double latP, double lngP, String np, int asi,
                                      String par, String est, int idRes, String etiqMarcador,
                                      int colorMarcador, String colorHex, boolean tieneParada) {
        if (!par.isEmpty()) {
            agregarFilaPasajeroColoreado(np, asi, par, est, idRes, etiqMarcador, colorMarcador, colorHex, tieneParada);
            return;
        }
        if (!tieneParada) {
            agregarFilaPasajeroColoreado(np, asi, "Sin parada asignada", est, idRes, etiqMarcador, colorMarcador, colorHex, false);
            return;
        }
        // Geocodificar en background
        new Thread(() -> {
            String nombre = "";
            try {
                String url = "https://nominatim.openstreetmap.org/reverse?lat=" + latP
                        + "&lon=" + lngP + "&format=json&addressdetails=1&zoom=16&accept-language=es";
                String resp = peticionHttp(url);
                if (resp != null && !resp.isEmpty()) {
                    JSONObject geo = new JSONObject(resp);
                    nombre = extraerNombreNominatim(geo.optJSONObject("address"), geo);
                }
            } catch (Exception ignored) {}
            final String nomFinal = (nombre == null || nombre.isEmpty())
                    ? String.format(Locale.getDefault(), "%.4f, %.4f", latP, lngP)
                    : nombre;
            runOnUiThread(() -> agregarFilaPasajeroColoreado(np, asi, nomFinal, est, idRes,
                    etiqMarcador, colorMarcador, colorHex, tieneParada));
        }).start();
    }

    private void mostrarSinPasajeros() {
        if (layoutListaPasajeros == null) return;
        layoutListaPasajeros.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText("Sin pasajeros reservados aún");
        tv.setTextColor(Color.parseColor("#546E7A"));
        tv.setTextSize(13f); tv.setPadding(0,8,0,8);
        layoutListaPasajeros.addView(tv);
        if (txtTotalPasajeros != null) txtTotalPasajeros.setText("0 pasajeros");
        if (cardPasajeros != null) cardPasajeros.setVisibility(View.VISIBLE);
    }

    private void agregarFilaPasajeroColoreado(String nombre, int asientos, String parada,
                                              String estado, int idRes, String etiqMarcador,
                                              int colorMarcador, String colorHex, boolean tieneParada) {
        float d = getResources().getDisplayMetrics().density;
        int p14=(int)(14*d),p10=(int)(10*d),p8=(int)(8*d),p6=(int)(6*d),p4=(int)(4*d);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, p4, 0, p4); card.setLayoutParams(lpCard);
        card.setPadding(p14, p10, p14, p10);
        GradientDrawable bgCard = new GradientDrawable();
        bgCard.setShape(GradientDrawable.RECTANGLE); bgCard.setCornerRadius(16*d);
        bgCard.setColor(Color.WHITE);
        bgCard.setStroke((int)(2.5f*d), tieneParada ? colorMarcador : Color.parseColor("#CFD8DC"));
        card.setBackground(bgCard); card.setElevation(3*d);

        LinearLayout fila1 = new LinearLayout(this);
        fila1.setOrientation(LinearLayout.HORIZONTAL);
        fila1.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView avatar = new TextView(this);
        avatar.setText(nombre.isEmpty() ? "P" : nombre.substring(0,1).toUpperCase());
        avatar.setTextColor(Color.WHITE); avatar.setTextSize(15f);
        avatar.setTypeface(null, Typeface.BOLD); avatar.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpAv = new LinearLayout.LayoutParams((int)(36*d),(int)(36*d));
        lpAv.setMargins(0,0,p10,0); avatar.setLayoutParams(lpAv);
        GradientDrawable bgAv = new GradientDrawable(); bgAv.setShape(GradientDrawable.OVAL);
        bgAv.setColor(tieneParada ? colorMarcador : Color.parseColor("#90A4AE"));
        avatar.setBackground(bgAv); fila1.addView(avatar);

        TextView tvNombre = new TextView(this);
        tvNombre.setText(nombre); tvNombre.setTextSize(14.5f);
        tvNombre.setTypeface(null, Typeface.BOLD); tvNombre.setTextColor(Color.parseColor("#1A2035"));
        tvNombre.setMaxLines(1); tvNombre.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvNombre.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fila1.addView(tvNombre);

        TextView badge = new TextView(this);
        badge.setText(badgeEstado(estado)); badge.setTextSize(10f);
        badge.setTypeface(null, Typeface.BOLD); badge.setTextColor(Color.WHITE);
        badge.setPadding(p8,(int)(3*d),p8,(int)(3*d));
        GradientDrawable bgBadge = new GradientDrawable();
        bgBadge.setShape(GradientDrawable.RECTANGLE); bgBadge.setCornerRadius(20*d);
        bgBadge.setColor(colorBadgeEstado(estado)); badge.setBackground(bgBadge);
        fila1.addView(badge); card.addView(fila1);

        TextView tvAsientos = new TextView(this);
        tvAsientos.setText("💺 " + asientos + (asientos==1?" asiento":" asientos"));
        tvAsientos.setTextSize(12f); tvAsientos.setTextColor(Color.parseColor("#546E7A"));
        LinearLayout.LayoutParams lpAs = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpAs.topMargin=(int)(5*d); tvAsientos.setLayoutParams(lpAs); card.addView(tvAsientos);

        LinearLayout filaParada = new LinearLayout(this);
        filaParada.setOrientation(LinearLayout.HORIZONTAL);
        filaParada.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpFP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFP.topMargin=p6; filaParada.setLayoutParams(lpFP);

        if (tieneParada) {
            TextView chipNum = new TextView(this);
            chipNum.setText(etiqMarcador); chipNum.setTextSize(10f);
            chipNum.setTypeface(null, Typeface.BOLD); chipNum.setTextColor(Color.WHITE);
            chipNum.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lpCh = new LinearLayout.LayoutParams((int)(26*d),(int)(26*d));
            lpCh.setMargins(0,0,p6,0); chipNum.setLayoutParams(lpCh);
            GradientDrawable bgCh = new GradientDrawable(); bgCh.setShape(GradientDrawable.OVAL); bgCh.setColor(colorMarcador);
            chipNum.setBackground(bgCh); filaParada.addView(chipNum);

            TextView tvParada = new TextView(this);
            tvParada.setText("🚏 " + parada); tvParada.setTextSize(12f);
            tvParada.setTextColor(Color.parseColor(colorHex)); tvParada.setTypeface(null, Typeface.BOLD);
            tvParada.setMaxLines(2); tvParada.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvParada.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvParada.setPadding(p8,p4,p8,p4);
            GradientDrawable bgPar = new GradientDrawable();
            bgPar.setShape(GradientDrawable.RECTANGLE); bgPar.setCornerRadius(10*d);
            bgPar.setColor(Color.parseColor("#F0F4F8")); bgPar.setStroke((int)(1*d), colorMarcador);
            tvParada.setBackground(bgPar); filaParada.addView(tvParada);
        } else {
            TextView tvSinParada = new TextView(this);
            tvSinParada.setText("⚠️  El pasajero aún no ha marcado su parada");
            tvSinParada.setTextSize(11.5f); tvSinParada.setTextColor(Color.parseColor("#9E9E9E"));
            tvSinParada.setTypeface(null, Typeface.ITALIC);
            tvSinParada.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            filaParada.addView(tvSinParada);
        }
        card.addView(filaParada);

        if (EST_ESPERANDO_RECOGIDA.equals(estado)) {
            MaterialButton btnR = new MaterialButton(this);
            btnR.setText("✅  Confirmar recogida"); btnR.setTextSize(13f);
            btnR.setTextColor(Color.WHITE); btnR.setCornerRadius((int)(12*d));
            btnR.setBackgroundColor(tieneParada ? colorMarcador : Color.parseColor("#1976D2"));
            LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,(int)(44*d));
            lpBtn.topMargin=p8; btnR.setLayoutParams(lpBtn);
            btnR.setOnClickListener(v -> { idReservaActual=idRes; confirmarRecogida(); });
            card.addView(btnR);
        }
        layoutListaPasajeros.addView(card);
    }

    private String badgeEstado(String e) {
        if (e==null||e.isEmpty()) return "✓ Activa";
        switch (e.toUpperCase()) {
            case "ACTIVA": case "CONFIRMADA": case "RESERVADO": return "✓ Confirmada";
            case "PENDIENTE":    return "⏳ Pendiente";
            case "EN_CURSO": case "INICIADO": return "🚗 A bordo";
            case "ESPERANDO_RECOGIDA": return "⏳ Esperando";
            case "RECOGIDO":     return "✅ Recogido";
            case "COMPLETADO": case "FINALIZADO": return "🏁 Completado";
            case "CANCELADO": case "CANCELADA": return "✕ Cancelada";
            default: return "📋 " + e;
        }
    }

    private int colorBadgeEstado(String e) {
        if (e==null||e.isEmpty()) return Color.parseColor("#2E7D32");
        switch (e.toUpperCase()) {
            case "ACTIVA": case "CONFIRMADA": case "RESERVADO": return Color.parseColor("#2E7D32");
            case "PENDIENTE":    return Color.parseColor("#F57F17");
            case "EN_CURSO": case "INICIADO": case "RECOGIDO": return Color.parseColor("#00838F");
            case "ESPERANDO_RECOGIDA": return Color.parseColor("#E65100");
            case "CANCELADO": case "CANCELADA": return Color.parseColor("#B71C1C");
            case "COMPLETADO": case "FINALIZADO": return Color.parseColor("#1565C0");
            default: return Color.parseColor("#607D8B");
        }
    }

    // =========================================================================
    //  CONFIRMAR RECOGIDA
    // =========================================================================
    private void confirmarRecogida() {
        if(idReservaActual<=0){ Toast.makeText(this,"Sin reserva activa",Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this)
                .setTitle("Confirmar recogida").setMessage("¿Confirmas que recogiste al pasajero?")
                .setPositiveButton("Sí, lo recogí",(d,w)->ejecutarRecogida())
                .setNegativeButton("Cancelar",null).show();
    }

    private void ejecutarRecogida() {
        loaderDetalle.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).put(Constantes.RESERVAS+"/"+idReservaActual+"/recoger", null,
                response -> {
                    loaderDetalle.setVisibility(View.GONE); estadoReserva = EST_RECOGIDO;
                    Toast.makeText(this,"✅ Pasajero recogido",Toast.LENGTH_SHORT).show();
                    runOnUiThread(() -> {
                        if(btnRecoger!=null) btnRecoger.setVisibility(View.GONE);
                        puntosRutaWaypoint.clear();
                        GeoPoint pos = gpConductorActual != null ? gpConductorActual : gpOrigen;
                        pedirSegmentoConductorADestino(pos); renderizarMapa(); cargarReservasConductor();
                    });
                },
                error -> { loaderDetalle.setVisibility(View.GONE); Toast.makeText(this,"Error al confirmar recogida",Toast.LENGTH_LONG).show(); });
    }

    // =========================================================================
    //  BOTTOM SHEET — FLUJO 2 PASOS: subida → bajada
    // =========================================================================

    // Almacena temporalmente la subida elegida en el step 1 antes de confirmar
    private ParadaDinamica subidaTemp = null;

    // Modo de toque en mapa para elegir punto de subida
    private boolean modoSeleccionMapaActivo = false;
    private android.view.View bannerSeleccionMapa = null;
    private Runnable onMapaTocadoCallback = null;

    /**
     * Punto de entrada principal.
     * Si ya reservó → solo puede cambiar la bajada (paso 2 directo).
     * Si no ha reservado → muestra paso 1 (subida) → paso 2 (bajada) → reserva.
     */
    private void mostrarBottomSheetParada() {
        if (paradasDin.isEmpty()) generarParadasDinamicas();
        if (yaReservo) {
            // Ya reservó: solo cambiar bajada
            mostrarSheetBajada(null, null);
        } else {
            // Primera vez: comenzar por la subida
            mostrarSheetSubida();
        }
    }

    // ── PASO 1: ¿Dónde te subes? ─────────────────────────────────────────────
    private void mostrarSheetSubida() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.BottomSheetTheme);
        float dp = getResources().getDisplayMetrics().density;
        int p16=(int)(16*dp), p12=(int)(12*dp), p8=(int)(8*dp), p4=(int)(4*dp), p24=(int)(24*dp);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p16, p16, p16, p24);

        root.addView(crearTiron(dp, p12));
        root.addView(crearIndicadorPasos(dp, p8, 1));

        // ── Título ──
        TextView tTitulo = new TextView(this);
        tTitulo.setText("🙋 ¿Dónde te vas a subir?");
        tTitulo.setTextSize(18f); tTitulo.setTypeface(null, Typeface.BOLD);
        tTitulo.setTextColor(Color.parseColor("#004D40"));
        LinearLayout.LayoutParams lpTit = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTit.setMargins(0, p8, 0, p4); tTitulo.setLayoutParams(lpTit);
        root.addView(tTitulo);

        // ── Chip de selección actual ──
        TextView tvElegida = new TextView(this);
        tvElegida.setVisibility(View.GONE);
        tvElegida.setTextSize(13f); tvElegida.setTextColor(Color.WHITE);
        tvElegida.setTypeface(null, Typeface.BOLD); tvElegida.setPadding(p12, p8, p12, p8);
        GradientDrawable bgChip = new GradientDrawable();
        bgChip.setShape(GradientDrawable.RECTANGLE); bgChip.setCornerRadius(20*dp);
        bgChip.setColor(Color.parseColor("#00897B")); tvElegida.setBackground(bgChip);
        LinearLayout.LayoutParams lpChip = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpChip.setMargins(0, 0, 0, p8); tvElegida.setLayoutParams(lpChip);
        root.addView(tvElegida);

        // Conectar referencias para que el toque del mapa actualice este chip
        chipSubidaRef    = tvElegida;
        final MaterialButton[] btnRef = {null};
        btnSubidaRef     = btnRef;
        final ParadaDinamica[] elegida = {null};
        subidaElegidaRef = elegida;

        // ── Botón: tocar en el mapa ──
        MaterialButton btnTocarMapa = new MaterialButton(this);
        btnTocarMapa.setText("🗺️  TOCAR EN EL MAPA");
        btnTocarMapa.setTextSize(13f); btnTocarMapa.setTextColor(Color.parseColor("#00695C"));
        btnTocarMapa.setBackgroundColor(Color.TRANSPARENT);
        btnTocarMapa.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#00897B")));
        btnTocarMapa.setStrokeWidth((int)(2*dp)); btnTocarMapa.setCornerRadius((int)(14*dp));
        LinearLayout.LayoutParams lpTM = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(46*dp));
        lpTM.setMargins(0, 0, 0, p8); btnTocarMapa.setLayoutParams(lpTM);
        btnTocarMapa.setOnClickListener(v -> {
            // Cerrar el sheet y activar modo de toque en el mapa
            sheet.dismiss();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                activarModoSeleccionMapa(tvElegida, btnRef);
                // Cuando el usuario toque el mapa, reabrir el sheet con la selección
                onMapaTocadoCallback = () -> {
                    modoSeleccionMapaActivo = false;
                    ocultarBannerSeleccionMapa();
                    new Handler(Looper.getMainLooper()).postDelayed(this::mostrarSheetSubida, 200);
                };
            }, 300);
        });
        root.addView(btnTocarMapa);

        // ── Separador "o" ──
        root.addView(crearSeparadorO(dp, p8));

        // ── Campo de búsqueda ──
        android.widget.EditText editBuscar = crearEditBuscar(dp, p12, p8, "Escribe tu barrio de subida...");
        root.addView(editBuscar);

        // ── Lista de paradas ──
        LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        root.addView(lista);

        ArrayList<ParadaDinamica> paradasConOrigen = new ArrayList<>();
        ParadaDinamica pdOrigen = new ParadaDinamica(origenActual, latOrigen, lngOrigen, 0);
        paradasConOrigen.add(pdOrigen);
        for (ParadaDinamica pd : paradasDin) {
            if (pd.idxEnRuta != -1) paradasConOrigen.add(pd);
        }

        // Si hay una subida previa (tocada en el mapa), mostrarla ya seleccionada
        if (gpSubida != null && nombreSubidaPasajero != null && !nombreSubidaPasajero.isEmpty()) {
            elegida[0] = new ParadaDinamica(nombreSubidaPasajero, gpSubida.getLatitude(), gpSubida.getLongitude(), -99);
            tvElegida.setText("🙋 " + nombreSubidaPasajero);
            tvElegida.setVisibility(View.VISIBLE);
        }

        poblarListaSubida(lista, paradasConOrigen, "", dp, p8, p4, pE -> {
            elegida[0] = pE;
            tvElegida.setText("🙋 " + pE.nombre); tvElegida.setVisibility(View.VISIBLE);
            gpSubida = pE.toGeoPoint(); nombreSubidaPasajero = pE.nombre;
            renderizarMapa();
            if (btnRef[0] != null) {
                btnRef[0].setEnabled(true); btnRef[0].setAlpha(1f);
                btnRef[0].setBackgroundColor(Color.parseColor("#00897B"));
                btnRef[0].setText("✅  CONFIRMAR SUBIDA → ELEGIR BAJADA");
            }
        });

        // ── Botón continuar ──
        MaterialButton btnSiguiente = new MaterialButton(this);
        btnSiguiente.setTextSize(14f); btnSiguiente.setTextColor(Color.WHITE);
        btnSiguiente.setCornerRadius((int)(14*dp));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(52*dp));
        lpBtn.setMargins(0, p8, 0, 0); btnSiguiente.setLayoutParams(lpBtn);
        btnRef[0] = btnSiguiente;

        // Si ya hay subida previa (del mapa), habilitar el botón
        if (elegida[0] != null) {
            btnSiguiente.setEnabled(true); btnSiguiente.setAlpha(1f);
            btnSiguiente.setBackgroundColor(Color.parseColor("#00897B"));
            btnSiguiente.setText("✅  CONFIRMAR: " + elegida[0].nombre);
        } else {
            btnSiguiente.setEnabled(false); btnSiguiente.setAlpha(0.5f);
            btnSiguiente.setBackgroundColor(Color.parseColor("#B0BEC5"));
            btnSiguiente.setText("✅  CONFIRMAR SUBIDA → ELEGIR BAJADA");
        }

        btnSiguiente.setOnClickListener(v -> {
            if (elegida[0] == null) {
                Toast.makeText(this, "Selecciona o toca el mapa para marcar tu subida", Toast.LENGTH_SHORT).show(); return;
            }
            subidaTemp = elegida[0];
            desactivarModoSeleccionMapa();
            sheet.dismiss();
            new Handler(Looper.getMainLooper()).postDelayed(() -> mostrarSheetBajada(subidaTemp, null), 250);
        });
        root.addView(btnSiguiente);

        // Si hay seleccion previa del mapa: al cerrar el sheet sin confirmar, limpiar modo
        sheet.setOnDismissListener(d -> {
            if (modoSeleccionMapaActivo) desactivarModoSeleccionMapa();
            // Limpiar referencias para no filtrar eventos fuera de contexto
            chipSubidaRef = null; btnSubidaRef = null; subidaElegidaRef = null;
        });

        configurarBusquedaDinamica(editBuscar, paradasConOrigen, lista, dp, p8, p4, tvElegida, btnRef, true);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(root); sheet.setContentView(sv); sheet.show();
    }

    /** Crea un separador visual "─── o ───" entre opciones */
    private LinearLayout crearSeparadorO(float dp, int margin) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpRow.setMargins(0, margin, 0, margin); row.setLayoutParams(lpRow);

        View linea1 = new View(this);
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(0, (int)(1*dp), 1f);
        linea1.setLayoutParams(lpL); linea1.setBackgroundColor(Color.parseColor("#CFD8DC")); row.addView(linea1);

        TextView tvO = new TextView(this);
        tvO.setText("  o  "); tvO.setTextSize(12f); tvO.setTextColor(Color.parseColor("#90A4AE"));
        tvO.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(tvO);

        View linea2 = new View(this);
        linea2.setLayoutParams(new LinearLayout.LayoutParams(0, (int)(1*dp), 1f));
        linea2.setBackgroundColor(Color.parseColor("#CFD8DC")); row.addView(linea2);
        return row;
    }

    // ── PASO 2: ¿Dónde te bajas? ─────────────────────────────────────────────
    private void mostrarSheetBajada(ParadaDinamica subidaElegida, BottomSheetDialog sheetAnterior) {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.BottomSheetTheme);
        float dp = getResources().getDisplayMetrics().density;
        int p16=(int)(16*dp), p12=(int)(12*dp), p8=(int)(8*dp), p4=(int)(4*dp), p24=(int)(24*dp);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p16, p16, p16, p24);

        // ── Tirón decorativo ──
        root.addView(crearTiron(dp, p12));

        // ── Indicador de pasos (solo si es primera vez) ──
        if (!yaReservo) root.addView(crearIndicadorPasos(dp, p8, 2));

        // ── Fila superior: botón volver (si aplica) + título ──
        if (!yaReservo && subidaElegida != null) {
            LinearLayout filaTitulo = new LinearLayout(this);
            filaTitulo.setOrientation(LinearLayout.HORIZONTAL);
            filaTitulo.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpFila = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpFila.setMargins(0, p4, 0, p4); filaTitulo.setLayoutParams(lpFila);

            // Botón "← Cambiar subida"
            MaterialButton btnVolver = new MaterialButton(this);
            btnVolver.setText("← Subida");
            btnVolver.setTextSize(11f); btnVolver.setTextColor(Color.parseColor("#00897B"));
            btnVolver.setBackgroundColor(Color.TRANSPARENT);
            btnVolver.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#00897B")));
            btnVolver.setStrokeWidth((int)(1.5f*dp));
            btnVolver.setCornerRadius((int)(20*dp));
            LinearLayout.LayoutParams lpVolver = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (int)(36*dp));
            lpVolver.setMargins(0, 0, p12, 0); btnVolver.setLayoutParams(lpVolver);
            btnVolver.setOnClickListener(v -> {
                sheet.dismiss();
                new Handler(Looper.getMainLooper()).postDelayed(this::mostrarSheetSubida, 200);
            });
            filaTitulo.addView(btnVolver);

            // Título
            TextView tTitulo = new TextView(this);
            tTitulo.setText("🚏 ¿Dónde te bajas?");
            tTitulo.setTextSize(17f); tTitulo.setTypeface(null, Typeface.BOLD);
            tTitulo.setTextColor(Color.parseColor("#004D40"));
            tTitulo.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            filaTitulo.addView(tTitulo);
            root.addView(filaTitulo);
        } else {
            // Cambio de parada (ya reservó)
            TextView tTitulo = new TextView(this);
            tTitulo.setText(yaReservo ? "🔄 Cambiar parada de bajada" : "🚏 ¿Dónde te bajas?");
            tTitulo.setTextSize(18f); tTitulo.setTypeface(null, Typeface.BOLD);
            tTitulo.setTextColor(Color.parseColor("#004D40"));
            LinearLayout.LayoutParams lpTit = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpTit.setMargins(0, p8, 0, p4); tTitulo.setLayoutParams(lpTit);
            root.addView(tTitulo);
        }

        // ── Chip de subida confirmada (informativo) ──
        if (subidaElegida != null && !yaReservo) {
            LinearLayout filaSubida = new LinearLayout(this);
            filaSubida.setOrientation(LinearLayout.HORIZONTAL);
            filaSubida.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpFS = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpFS.setMargins(0, p4, 0, p8); filaSubida.setLayoutParams(lpFS);

            TextView tvSubidaLabel = new TextView(this);
            tvSubidaLabel.setText("Subida: ");
            tvSubidaLabel.setTextSize(12f); tvSubidaLabel.setTextColor(Color.parseColor("#78909C"));
            filaSubida.addView(tvSubidaLabel);

            TextView tvSubidaChip = new TextView(this);
            tvSubidaChip.setText("🙋 " + subidaElegida.nombre);
            tvSubidaChip.setTextSize(12f); tvSubidaChip.setTextColor(Color.WHITE);
            tvSubidaChip.setTypeface(null, Typeface.BOLD); tvSubidaChip.setPadding(p8, (int)(4*dp), p8, (int)(4*dp));
            GradientDrawable bgSub = new GradientDrawable(); bgSub.setShape(GradientDrawable.RECTANGLE);
            bgSub.setCornerRadius(16*dp); bgSub.setColor(Color.parseColor("#00897B")); tvSubidaChip.setBackground(bgSub);
            filaSubida.addView(tvSubidaChip);
            root.addView(filaSubida);
        }

        // ── Chip de bajada seleccionada ──
        TextView tvElegida = new TextView(this);
        if (!nombreParada.isEmpty()) { tvElegida.setText("🚏 " + nombreParada); tvElegida.setVisibility(View.VISIBLE); }
        else tvElegida.setVisibility(View.GONE);
        tvElegida.setTextSize(13f); tvElegida.setTextColor(Color.WHITE);
        tvElegida.setTypeface(null, Typeface.BOLD); tvElegida.setPadding(p12, p8, p12, p8);
        GradientDrawable bgC = new GradientDrawable(); bgC.setShape(GradientDrawable.RECTANGLE);
        bgC.setCornerRadius(20*dp); bgC.setColor(Color.parseColor("#FF9800")); tvElegida.setBackground(bgC);
        LinearLayout.LayoutParams lpChip = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpChip.setMargins(0, 0, 0, p8); tvElegida.setLayoutParams(lpChip);
        root.addView(tvElegida);

        // ── Campo de búsqueda ──
        android.widget.EditText editBuscar = crearEditBuscar(dp, p12, p8, "Escribe un barrio o lugar de bajada...");
        root.addView(editBuscar);

        // ── Lista de paradas ──
        LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        root.addView(lista);

        final ParadaDinamica[] elegida = {null};
        final MaterialButton[] btnRef = {null};

        poblarLista(lista, paradasDin, "", dp, p8, p4, pE -> {
            elegida[0] = pE;
            tvElegida.setText("🚏 " + pE.nombre); tvElegida.setVisibility(View.VISIBLE);
            gpParada = pE.toGeoPoint(); nombreParada = pE.nombre; renderizarMapa();
            if (btnRef[0] != null) habilitarBtnBajada(btnRef[0], pE.nombre);
        });

        // ── Botón confirmar ──
        MaterialButton btnConfirmar = new MaterialButton(this);
        btnConfirmar.setTextSize(15f); btnConfirmar.setEnabled(false); btnConfirmar.setAlpha(0.5f);
        btnConfirmar.setBackgroundColor(Color.parseColor("#B0BEC5")); btnConfirmar.setTextColor(Color.WHITE);
        btnConfirmar.setCornerRadius((int)(14*dp));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(52*dp));
        lpBtn.setMargins(0, p8, 0, 0); btnConfirmar.setLayoutParams(lpBtn);
        btnConfirmar.setText(yaReservo ? "🔄 CAMBIAR PARADA" : "🚏 RESERVAR");
        btnRef[0] = btnConfirmar;
        btnConfirmar.setOnClickListener(v -> {
            if (elegida[0] == null) {
                Toast.makeText(this, "Selecciona dónde te vas a bajar", Toast.LENGTH_SHORT).show(); return;
            }
            sheet.dismiss();
            // Guardar la subida elegida antes de reservar
            if (subidaElegida != null && !yaReservo) {
                gpSubida = subidaElegida.toGeoPoint();
                nombreSubidaPasajero = subidaElegida.nombre;
            }
            if (yaReservo) cambiarParada(elegida[0]);
            else           publicarParadaYReservarConSubida(subidaElegida, elegida[0]);
        });
        root.addView(btnConfirmar);

        // ── Búsqueda dinámica ──
        configurarBusquedaDinamica(editBuscar, paradasDin, lista, dp, p8, p4, tvElegida, btnRef, false);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(root); sheet.setContentView(sv); sheet.show();
    }

    // ── Helpers del sheet ─────────────────────────────────────────────────────

    private View crearTiron(float dp, int marginBottom) {
        View tiron = new View(this);
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams((int)(40*dp), (int)(4*dp));
        lpT.gravity = android.view.Gravity.CENTER_HORIZONTAL; lpT.setMargins(0, 0, 0, marginBottom);
        tiron.setLayoutParams(lpT);
        GradientDrawable tGd = new GradientDrawable(); tGd.setShape(GradientDrawable.RECTANGLE);
        tGd.setCornerRadius(4*dp); tGd.setColor(Color.parseColor("#BDBDBD")); tiron.setBackground(tGd);
        return tiron;
    }

    /** Muestra "● SUBIDA  ○ BAJADA" o "○ SUBIDA  ● BAJADA" según el paso activo */
    private View crearIndicadorPasos(float dp, int margin, int pasoActivo) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpRow.setMargins(0, 0, 0, margin); row.setLayoutParams(lpRow);

        String[] labels = {"1  Subida", "2  Bajada"};
        String[] icons  = {"🙋", "🚏"};
        String[] coloresActivo  = {"#00897B", "#FF6F00"};
        String[] coloresInactivo = {"#B2DFDB", "#FFE0B2"};

        for (int i = 0; i < 2; i++) {
            boolean activo = (i + 1) == pasoActivo;
            boolean completado = (i + 1) < pasoActivo;

            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
            chip.setPadding((int)(10*dp), (int)(6*dp), (int)(10*dp), (int)(6*dp));
            LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpC.setMargins((int)(4*dp), 0, (int)(4*dp), 0); chip.setLayoutParams(lpC);
            GradientDrawable bgChip = new GradientDrawable(); bgChip.setShape(GradientDrawable.RECTANGLE);
            bgChip.setCornerRadius(20*dp);
            bgChip.setColor(Color.parseColor(activo ? coloresActivo[i] : coloresInactivo[i]));
            chip.setBackground(bgChip);

            TextView tvIcon = new TextView(this);
            tvIcon.setText(completado ? "✓" : icons[i]);
            tvIcon.setTextSize(activo ? 14f : 12f);
            LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpIcon.setMargins(0, 0, (int)(4*dp), 0); tvIcon.setLayoutParams(lpIcon);
            chip.addView(tvIcon);

            TextView tvLabel = new TextView(this);
            tvLabel.setText(labels[i]); tvLabel.setTextSize(activo ? 13f : 11f);
            tvLabel.setTextColor(activo ? Color.WHITE : Color.parseColor("#78909C"));
            if (activo) tvLabel.setTypeface(null, Typeface.BOLD);
            chip.addView(tvLabel);
            row.addView(chip);

            // Línea conectora entre pasos
            if (i < 1) {
                View linea = new View(this);
                LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams((int)(24*dp), (int)(2*dp));
                lpL.gravity = android.view.Gravity.CENTER_VERTICAL;
                linea.setLayoutParams(lpL);
                linea.setBackgroundColor(Color.parseColor(completado ? "#00897B" : "#CFD8DC"));
                row.addView(linea);
            }
        }
        return row;
    }

    private android.widget.EditText crearEditBuscar(float dp, int p12, int p8, String hint) {
        android.widget.EditText edit = new android.widget.EditText(this);
        edit.setHint(hint); edit.setTextSize(14f);
        edit.setTextColor(Color.parseColor("#212121")); edit.setHintTextColor(Color.parseColor("#9E9E9E"));
        edit.setSingleLine(true);
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        edit.setPadding(p12, p12, p12, p12);
        GradientDrawable bgB = new GradientDrawable(); bgB.setShape(GradientDrawable.RECTANGLE);
        bgB.setCornerRadius(12*dp); bgB.setColor(Color.parseColor("#F5F5F5"));
        bgB.setStroke((int)(1.5f*dp), Color.parseColor("#B2DFDB")); edit.setBackground(bgB);
        LinearLayout.LayoutParams lpEdit = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpEdit.setMargins(0, 0, 0, p8); edit.setLayoutParams(lpEdit);
        return edit;
    }

    private void configurarBusquedaDinamica(android.widget.EditText edit,
                                            ArrayList<ParadaDinamica> fuente,
                                            LinearLayout lista, float dp, int p8, int p4,
                                            TextView tvChip, MaterialButton[] btnRef,
                                            boolean esSubida) {
        Handler autoH = new Handler(Looper.getMainLooper());
        Runnable[] autoR = {null};
        edit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (autoR[0] != null) autoH.removeCallbacks(autoR[0]);
                String txt = s.toString().trim();
                autoR[0] = () -> {
                    ArrayList<ParadaDinamica> f = new ArrayList<>();
                    for (ParadaDinamica pd : fuente)
                        if (txt.isEmpty() || pd.nombre.toLowerCase().contains(txt.toLowerCase())) f.add(pd);
                    runOnUiThread(() -> {
                        if (esSubida) {
                            poblarListaSubida(lista, f, txt, dp, p8, p4, pE -> {
                                tvChip.setText("🙋 " + pE.nombre); tvChip.setVisibility(View.VISIBLE);
                                gpSubida = pE.toGeoPoint(); nombreSubidaPasajero = pE.nombre; renderizarMapa();
                                if (btnRef[0] != null) {
                                    btnRef[0].setEnabled(true); btnRef[0].setAlpha(1f);
                                    btnRef[0].setBackgroundColor(Color.parseColor("#00897B"));
                                    btnRef[0].setText("✅  CONFIRMAR SUBIDA → ELEGIR BAJADA");
                                }
                            });
                        } else {
                            poblarLista(lista, f, txt, dp, p8, p4, pE -> {
                                tvChip.setText("🚏 " + pE.nombre); tvChip.setVisibility(View.VISIBLE);
                                gpParada = pE.toGeoPoint(); nombreParada = pE.nombre; renderizarMapa();
                                if (btnRef[0] != null) habilitarBtnBajada(btnRef[0], pE.nombre);
                            });
                        }
                    });
                };
                autoH.postDelayed(autoR[0], txt.isEmpty() ? 0 : 350);
            }
        });
    }

    /** Lista de subida: incluye el origen como opción resaltada */
    private void poblarListaSubida(LinearLayout container, ArrayList<ParadaDinamica> paradas,
                                   String filtro, float dp, int p8, int p4,
                                   java.util.function.Consumer<ParadaDinamica> onSelect) {
        container.removeAllViews();
        if (paradas.isEmpty()) {
            TextView tv = new TextView(this); tv.setText("Sin puntos disponibles");
            tv.setTextSize(13f); tv.setTextColor(Color.parseColor("#9E9E9E")); tv.setPadding(p8, p8, p8, p8);
            container.addView(tv); return;
        }
        for (int i = 0; i < paradas.size(); i++) {
            ParadaDinamica pd = paradas.get(i);
            boolean esOrigen = i == 0;
            String icon = esOrigen ? "🟢" : "🔵";
            String label = icon + "  " + pd.nombre + (esOrigen ? "  (Inicio)" : "");
            container.addView(crearFilaParada(label, pd, true, dp, p8, p4, onSelect));
        }
    }

    private void habilitarBtnBajada(MaterialButton btn, String nombre) {
        btn.setEnabled(true); btn.setAlpha(1f);
        btn.setBackgroundColor(Color.parseColor(yaReservo ? "#1565C0" : "#FF6F00"));
        btn.setText(yaReservo ? "🔄 CAMBIAR A: " + nombre : "🚏 RESERVAR EN: " + nombre);
    }

    private void habilitarBtn(MaterialButton btn, String nombre) {
        habilitarBtnBajada(btn, nombre);
    }

    /**
     * Variante de hacerReserva que incluye el punto de subida elegido por el pasajero.
     */
    private void publicarParadaYReservarConSubida(ParadaDinamica subida, ParadaDinamica bajada) {
        loaderDetalle.setVisibility(View.VISIBLE);
        if (bajada.idParadaBD > 0) { hacerReservaConSubida(subida, bajada); return; }
        JSONObject body = new JSONObject();
        try {
            body.put("nombre", bajada.nombre); body.put("lat", bajada.lat); body.put("lng", bajada.lng);
            if (rutaId > 0) body.put("idRuta", rutaId);
            body.put("orden", paradasDin.indexOf(bajada) + 1);
            body.put("kmAcumulado", 0); body.put("tipo", "AMBAS");
        } catch (JSONException e) { hacerReservaConSubida(subida, bajada); return; }
        ConexionApi.getInstance(this).post(Constantes.PARADAS, body,
                response -> { bajada.idParadaBD = response.optInt("idParada", response.optInt("id", 0)); hacerReservaConSubida(subida, bajada); },
                error -> hacerReservaConSubida(subida, bajada));
    }

    private void hacerReservaConSubida(ParadaDinamica subida, ParadaDinamica bajada) {
        int idUsuario = session.getIdUsuario();
        if (idUsuario <= 0 || viajeId <= 0 || cuposDisponibles <= 0
                || !ESTADOS_RESERVABLES.contains(estadoViaje)) {
            loaderDetalle.setVisibility(View.GONE);
            Toast.makeText(this, "No se puede reservar en este momento", Toast.LENGTH_LONG).show(); return;
        }

        // Determinar coords de subida
        double latS = (subida != null) ? subida.lat : latOrigen;
        double lngS = (subida != null) ? subida.lng : lngOrigen;
        String nomS = (subida != null) ? subida.nombre : origenActual;

        JSONObject body = new JSONObject();
        try {
            body.put("latSubida",          latS);
            body.put("lngSubida",          lngS);
            body.put("nombreParadaSubida", nomS);
            body.put("latOrigen",          latS);
            body.put("lngOrigen",          lngS);
            body.put("latInicio",          latS);
            body.put("lngInicio",          lngS);
            body.put("nombreParadaInicio", nomS);
            body.put("origenLat",          latS);
            body.put("origenLng",          lngS);
            body.put("latBajada",          bajada.lat);
            body.put("lngBajada",          bajada.lng);
            body.put("nombreParadaBajada", bajada.nombre);
            body.put("latParada",          bajada.lat);
            body.put("lngParada",          bajada.lng);
            body.put("nombreParada",       bajada.nombre);
            body.put("latDestino",         bajada.lat);
            body.put("lngDestino",         bajada.lng);
            if (bajada.idParadaBD > 0) {
                body.put("idParadaBajada", bajada.idParadaBD);
                body.put("idParadaFin",    bajada.idParadaBD);
                body.put("idParada",       bajada.idParadaBD);
            }
            body.put("idUsuarios",         idUsuario);
            body.put("idViajes",           viajeId);
            body.put("asientosReservados", 1);
            body.put("precioFinal",        precioViaje > 0 ? precioViaje : 0);
            body.put("estado",             "CONFIRMADA");
            body.put("idUsuario",          idUsuario);
            body.put("idViaje",            viajeId);
            body.put("asientos",           1);
            body.put("precio",             precioViaje > 0 ? precioViaje : 0);
        } catch (JSONException e) { loaderDetalle.setVisibility(View.GONE); return; }

        ConexionApi.getInstance(this).post(Constantes.RESERVAS, body,
                response -> {
                    loaderDetalle.setVisibility(View.GONE);
                    idReservaActual = response.optInt("idUsuarioViaje",
                            response.optInt("idReserva", response.optInt("id", response.optInt("reservaId", -1))));
                    estadoReserva = response.optString("estado", EST_CONFIRMADA).toUpperCase();
                    yaReservo = true;
                    gpParada = bajada.toGeoPoint(); nombreParada = bajada.nombre;

                    calcularYMostrarPrecioTramo(); // ← línea nueva
                    DetalleViajeActivity activity = this;
                    // Guardar subida definitiva
                    if (subida != null && !sonIguales(latS, lngS, latOrigen, lngOrigen)) {
                        gpSubida = subida.toGeoPoint(); nombreSubidaPasajero = subida.nombre;
                    } else {
                        gpSubida = null; nombreSubidaPasajero = "";
                    }
                    runOnUiThread(() -> {
                        cuposDisponibles = Math.max(0, cuposDisponibles - 1);
                        actualizarChipsCupos(cuposTotales, cuposDisponibles);
                        actualizarBotonPasajero(); actualizarBotonChat();
                        renderizarMapa(); mostrarCardMiReserva(response);
                    });
                    cargarMiReservaPasajero();
                },
                error -> { loaderDetalle.setVisibility(View.GONE); manejarErrorReserva(error); }
        );
    }

    // =========================================================================
    //  CUPOS
    // =========================================================================
    private void actualizarChipsCupos(int total, int disponibles){
        if(layoutCupos==null||total>8||total<=0) return;
        runOnUiThread(()->{
            layoutCupos.removeAllViews();
            float d=getResources().getDisplayMetrics().density;
            int p8=(int)(8*d), p4=(int)(4*d), p6=(int)(6*d);

            LinearLayout filaAsientos = new LinearLayout(this);
            filaAsientos.setOrientation(LinearLayout.HORIZONTAL);
            filaAsientos.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams filaLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            filaLp.setMargins(0, 0, 0, (int)(12*d));
            filaAsientos.setLayoutParams(filaLp);

            int seatW=(int)(44*d), seatH=(int)(48*d), seatM=(int)(5*d);
            for(int i=0;i<total;i++){
                final int idx=i;
                boolean libre=i<disponibles;
                boolean esMio=yaReservo&&cupoSeleccionado==i;

                LinearLayout seatBox = new LinearLayout(this);
                seatBox.setOrientation(LinearLayout.VERTICAL);
                seatBox.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(seatW, LinearLayout.LayoutParams.WRAP_CONTENT);
                sbLp.setMargins(seatM,0,seatM,0);
                seatBox.setLayoutParams(sbLp);

                TextView tvIcon = new TextView(this);
                tvIcon.setTextSize(26f);
                tvIcon.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                tvIcon.setLayoutParams(iconLp);

                if(esMio){
                    tvIcon.setText("🟠");
                } else if(libre){
                    tvIcon.setText("🟢");
                } else {
                    tvIcon.setText("🔴");
                }

                TextView tvLabel = new TextView(this);
                tvLabel.setTextSize(9f);
                tvLabel.setGravity(android.view.Gravity.CENTER);
                tvLabel.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                if(esMio){
                    tvLabel.setText("Tuyo");
                    tvLabel.setTextColor(Color.parseColor("#E65100"));
                    tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
                } else if(libre){
                    tvLabel.setText("Libre");
                    tvLabel.setTextColor(Color.parseColor("#2E7D32"));
                } else {
                    tvLabel.setText("Lleno");
                    tvLabel.setTextColor(Color.parseColor("#C62828"));
                }

                seatBox.addView(tvIcon);
                seatBox.addView(tvLabel);

                if(!esConductor){
                    if(esMio){
                        seatBox.setClickable(true); seatBox.setFocusable(true);
                        seatBox.setOnClickListener(v->mostrarBottomSheetParada());
                    } else if(libre&&!yaReservo){
                        seatBox.setClickable(true); seatBox.setFocusable(true);
                        seatBox.setOnClickListener(v->{ cupoSeleccionado=idx; mostrarBottomSheetParada(); });
                    }
                }
                filaAsientos.addView(seatBox);
            }
            layoutCupos.addView(filaAsientos);

            int ocupados = total - disponibles;

            LinearLayout barContainer = new LinearLayout(this);
            barContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams barContLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            barContLp.setMargins((int)(4*d), 0, (int)(4*d), (int)(6*d));
            barContainer.setLayoutParams(barContLp);

            LinearLayout rowCount = new LinearLayout(this);
            rowCount.setOrientation(LinearLayout.HORIZONTAL);
            rowCount.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rcLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rcLp.setMargins(0,0,0,(int)(6*d));
            rowCount.setLayoutParams(rcLp);

            TextView tvDisp = new TextView(this);
            tvDisp.setText("✅ " + disponibles + " disponible" + (disponibles!=1?"s":""));
            tvDisp.setTextSize(12f);
            tvDisp.setTextColor(Color.parseColor("#2E7D32"));
            tvDisp.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams tvDispLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvDisp.setLayoutParams(tvDispLp);

            TextView tvOcup = new TextView(this);
            tvOcup.setText(ocupados + " ocupado" + (ocupados!=1?"s":"") + " 🔒");
            tvOcup.setTextSize(12f);
            tvOcup.setTextColor(Color.parseColor("#B71C1C"));
            tvOcup.setGravity(android.view.Gravity.END);
            LinearLayout.LayoutParams tvOcupLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvOcup.setLayoutParams(tvOcupLp);

            rowCount.addView(tvDisp);
            rowCount.addView(tvOcup);

            android.widget.FrameLayout barFrame = new android.widget.FrameLayout(this);
            LinearLayout.LayoutParams bfLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int)(8*d));
            barFrame.setLayoutParams(bfLp);

            View barBg = new View(this);
            barBg.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
            GradientDrawable bgShape = new GradientDrawable();
            bgShape.setShape(GradientDrawable.RECTANGLE);
            bgShape.setCornerRadius((int)(4*d));
            bgShape.setColor(Color.parseColor("#E0E0E0"));
            barBg.setBackground(bgShape);

            View barFill = new View(this);
            int fillPct = total > 0 ? (int)((disponibles * 100f / total)) : 0;
            android.widget.FrameLayout.LayoutParams fillLp =
                    new android.widget.FrameLayout.LayoutParams(0,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
            barFill.setLayoutParams(fillLp);
            GradientDrawable fillShape = new GradientDrawable();
            fillShape.setShape(GradientDrawable.RECTANGLE);
            fillShape.setCornerRadius((int)(4*d));
            int c1 = disponibles > total/2 ? Color.parseColor("#43A047") : Color.parseColor("#FB8C00");
            fillShape.setColor(c1);
            barFill.setBackground(fillShape);

            barFrame.addView(barBg);
            barFrame.addView(barFill);

            barFrame.post(()->{
                int totalW = barFrame.getWidth();
                android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(0, totalW * fillPct / 100);
                anim.setDuration(600);
                anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
                anim.addUpdateListener(a -> {
                    android.widget.FrameLayout.LayoutParams lp2 =
                            (android.widget.FrameLayout.LayoutParams) barFill.getLayoutParams();
                    lp2.width = (int) a.getAnimatedValue();
                    barFill.setLayoutParams(lp2);
                });
                anim.start();
            });

            barContainer.addView(rowCount);
            barContainer.addView(barFrame);
            layoutCupos.addView(barContainer);

            LinearLayout leyenda = new LinearLayout(this);
            leyenda.setOrientation(LinearLayout.HORIZONTAL);
            leyenda.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams leyLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            leyLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            leyenda.setLayoutParams(leyLp);

            String[][] leyItems = {{"🟢","Libre"},{"🔴","Ocupado"}, esMioCheck() ? new String[]{"🟠","Tuyo"} : null};
            for(String[] item: leyItems){
                if(item==null) continue;
                TextView tv=new TextView(this);
                tv.setText(item[0]+" "+item[1]);
                tv.setTextSize(11f);
                tv.setTextColor(Color.parseColor("#607D8B"));
                LinearLayout.LayoutParams tvLp=new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                tvLp.setMargins((int)(8*d),0,(int)(8*d),0);
                tv.setLayoutParams(tvLp);
                leyenda.addView(tv);
            }
            layoutCupos.addView(leyenda);
        });
    }

    private boolean esMioCheck(){ return yaReservo && cupoSeleccionado >= 0; }

    // =========================================================================
    //  POLLING GENERAL (estado del viaje)
    // =========================================================================
    private void iniciarPolling(){
        if(pollingActivo) return;
        pollingActivo=true;
        pollingRunnable=new Runnable(){
            @Override public void run(){
                if(!pollingActivo) return;
                refrescar();
                pollingHandler.postDelayed(this, POLLING_MS);
            }
        };
        pollingHandler.postDelayed(pollingRunnable, POLLING_MS);
    }

    private void refrescar() {
        ConexionApi.getInstance(this).getObject(Constantes.viajePorId((long) viajeId),
                response -> {
                    int    nd = response.optInt("cuposDisponibles", cuposDisponibles);
                    int    nt = response.optInt("cuposTotales",     cuposTotales);
                    String ne = response.optString("estado", estadoViaje).trim().toUpperCase();
                    if (esConductor && ("FINALIZADO".equals(estadoViaje) || "COMPLETADO".equals(estadoViaje)))
                        verificarYMostrarBotonPagoRecibido();
                    if (nd != cuposDisponibles || nt != cuposTotales) {
                        cuposDisponibles = nd;
                        cuposTotales     = nt;
                        actualizarChipsCupos(cuposTotales, cuposDisponibles);
                        if (esConductor) cargarReservasConductor();
                        runOnUiThread(this::actualizarBotonPasajero);
                    }

                    // EN refrescar() — ya tienes este bloque, solo verifica que esté así:

                    if (!ne.equals(estadoViaje)) {
                        String estadoAnterior = estadoViaje;
                        estadoViaje = ne;
                        runOnUiThread(() -> txtEstado.setText(etiquetaEstado(estadoViaje)));

                        boolean ahora_finalizado = "FINALIZADO".equals(ne) || "COMPLETADO".equals(ne);
                        boolean antes_no_era = !"FINALIZADO".equals(estadoAnterior)
                                && !"COMPLETADO".equals(estadoAnterior);

                        if (ahora_finalizado && antes_no_era && !esConductor) {
                            // ← Solo para pasajero: mostrar botón de pago directo
                            // sin esperar a que cargarDetalleViaje() lo dispare
                            runOnUiThread(() -> {
                                mostrarBotonPago(precioViaje, nombreConductorViaje);
                                View divider = findViewById(R.id.divider_pago);
                                if (divider != null) divider.setVisibility(View.VISIBLE);
                            });
                        }

                        if (ahora_finalizado && antes_no_era) {
                            cargarDetalleViaje();
                            new Handler(Looper.getMainLooper())
                                    .postDelayed(this::dispararCalificacionSegunRol, 1500);
                            return;
                        }

                        if ("INICIADO".equals(ne) || "FINALIZADO".equals(ne) || "COMPLETADO".equals(ne))
                            cargarDetalleViaje();
                    }

                    // ★ Pasajero: también verifica el estado de su RESERVA en cada polling
                    if (!esConductor && yaReservo && idReservaActual > 0)
                        actualizarEstadoReservaPorPolling();
                },
                error -> {}
        );
    }
    private void dispararCalificacionSegunRol() {
        if (calificacionYaDisparada) return;  // ← AGREGAR
        calificacionYaDisparada = true;        // ← AGREGAR
        if (esConductor) {
            paso3BuscarPasajerosParaCalificar();
        } else {
            dispararCalificacionAlConductor();
        }
    }

    /**
     * Consulta el estado de la reserva del pasajero y actualiza el mapa si cambió.
     * Cuando el estado pasa a RECOGIDO: elimina el marcador de subida del mapa.
     */
    private void actualizarEstadoReservaPorPolling() {
        ConexionApi.getInstance(this).getObject(Constantes.RESERVAS + "/" + idReservaActual,
                response -> {
                    String nuevo = response.optString("estado", "").toUpperCase();
                    if (!nuevo.equals(estadoReserva)) {
                        String anterior = estadoReserva;
                        estadoReserva   = nuevo;
                        runOnUiThread(() -> {
                            mostrarCardMiReserva(response);

                            // ── Pasajero recogido: quitar marcador de subida ──────
                            if (EST_RECOGIDO.equals(estadoReserva) && !EST_RECOGIDO.equals(anterior)) {
                                java.util.List<org.osmdroid.views.overlay.Overlay> overlays = map.getOverlays();
                                for (int i = overlays.size() - 1; i >= 0; i--) {
                                    if (overlays.get(i) instanceof org.osmdroid.views.overlay.Marker) {
                                        org.osmdroid.views.overlay.Marker m =
                                                (org.osmdroid.views.overlay.Marker) overlays.get(i);
                                        if (MID_SUBIDA.equals(m.getId())) { overlays.remove(i); break; }
                                    }
                                }
                                for (int i = overlays.size() - 1; i >= 0; i--) {
                                    if (overlays.get(i) instanceof org.osmdroid.views.overlay.Polyline) {
                                        org.osmdroid.views.overlay.Polyline pl =
                                                (org.osmdroid.views.overlay.Polyline) overlays.get(i);
                                        if (pl.getColor() == COLOR_SEGMENTO_ACTIVO) overlays.remove(i);
                                    }
                                }
                                map.invalidate();
                                puntosRutaWaypoint.clear();
                                GeoPoint posConductor = gpConductorActual != null ? gpConductorActual : gpOrigen;
                                if (gpParada != null) pedirSegmentoConductorAParada(posConductor, gpParada);
                                android.widget.Toast.makeText(this,
                                        "✅ ¡El conductor te recogió! Ahora en camino a tu parada.",
                                        android.widget.Toast.LENGTH_LONG).show();
                            }

                            if (EST_RECOGIDO.equals(estadoReserva) || EST_COMPLETADO.equals(estadoReserva))
                                pedirRutaConWaypoint();
                            else
                                renderizarMapa();

                            // ★ COMPLETADO en reserva → calificar al conductor
                            if (EST_COMPLETADO.equals(estadoReserva) && !EST_COMPLETADO.equals(anterior)) {
                                new android.os.Handler(android.os.Looper.getMainLooper())
                                        .postDelayed(this::dispararCalificacionAlConductor, 1200);
                            }
                        });
                    }
                },
                error -> {}
        );
    }

    // =========================================================================
    //  FINALIZAR + CALIFICACIONES
    // =========================================================================

    private void paso2FinalizarYCalificar() {
        ConexionApi.getInstance(this).post(
                Constantes.viajeFinalizar((long) viajeId), null,
                r2 -> {
                    loaderDetalle.setVisibility(View.GONE);
                    Toast.makeText(this, "✅ Viaje finalizado.", Toast.LENGTH_LONG).show();
                    estadoViaje = "FINALIZADO";
                    cargarDetalleViaje();
                    new Handler(Looper.getMainLooper())
                            .postDelayed(this::paso3BuscarPasajerosParaCalificar, 1500);
                },
                e2 -> {
                    loaderDetalle.setVisibility(View.GONE);
                    Toast.makeText(this, "Error finalizando", Toast.LENGTH_LONG).show();
                }
        );
    }

    private void finalizarViaje() {
        loaderDetalle.setVisibility(View.VISIBLE);
        detenerTodo();
        ConexionApi.getInstance(this).post(
                Constantes.viajePorId((long) viajeId) + "/pasajeros-bajaron", null,
                r   -> paso2FinalizarYCalificar(),
                err -> paso2FinalizarYCalificar()
        );
    }

    private void paso3BuscarPasajerosParaCalificar() {
        // Solo aplica si el viaje está finalizado
        if (!"FINALIZADO".equals(estadoViaje) && !"COMPLETADO".equals(estadoViaje)) return;

        ConexionApi.getInstance(this).getObjectNoCache(Constantes.viajePorId((long) viajeId),
                viajeObj -> {
                    try {
                        java.util.ArrayList<Integer> ids     = new java.util.ArrayList<>();
                        java.util.ArrayList<String>  nombres = new java.util.ArrayList<>();
                        int idConductor = session.getIdUsuario();

                        // ── Fuente primaria: campo "usuarios" del viaje ──────────
                        org.json.JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                        if (usuarios != null && usuarios.length() > 0) {
                            for (int i = 0; i < usuarios.length(); i++) {
                                org.json.JSONObject u = usuarios.optJSONObject(i);
                                if (u == null) continue;

                                String est = u.optString("estado", "").toUpperCase().trim();
                                if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;

                                int    idP  = -1;
                                String nomP = "";

                                org.json.JSONObject usuObj = u.optJSONObject("usuario");
                                if (usuObj != null) {
                                    for (String k : new String[]{"idUsuarios","id","idUsuario"}) {
                                        int id = usuObj.optInt(k, -1); if (id > 0) { idP = id; break; }
                                    }
                                    for (String k : new String[]{"nombre","nombreCompleto","name","nombres"}) {
                                        String n = usuObj.optString(k, "");
                                        if (!n.isEmpty() && !n.equals("null")) { nomP = n; break; }
                                    }
                                    if (nomP.isEmpty()) {
                                        String n = usuObj.optString("nombres","");
                                        String a = usuObj.optString("apellidos","");
                                        if (!n.isEmpty() || !a.isEmpty()) nomP = (n + " " + a).trim();
                                    }
                                }
                                // Fallback: el id puede estar directo en el objeto usuario-viaje
                                if (idP <= 0) {
                                    for (String k : new String[]{"idUsuarios","idUsuario","idPasajero"}) {
                                        int id = u.optInt(k, -1); if (id > 0) { idP = id; break; }
                                    }
                                }
                                if (idP > 0 && idP != idConductor) {
                                    ids.add(idP);
                                    nombres.add(nomP.isEmpty() ? "Pasajero" : nomP);
                                }
                            }
                        }

                        // ── Fallback: endpoint /api/reservas/viaje/{id} ──────────
                        if (ids.isEmpty()) {
                            buscarPasajerosParaCalificarFallback(idConductor);
                            return;
                        }

                        // ── Mostrar calificaciones encadenadas ───────────────────
                        final java.util.ArrayList<Integer> fIds     = ids;
                        final java.util.ArrayList<String>  fNombres = nombres;
                        final int fIdCond = idConductor;
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                                mostrarCalificacionesEncadenadas(fIds, fNombres, fIdCond, 0));

                    } catch (Exception e) {
                        android.util.Log.e("DetalleViaje", "paso3BuscarPasajerosParaCalificar", e);
                        buscarPasajerosParaCalificarFallback(session.getIdUsuario());
                    }
                },
                err -> buscarPasajerosParaCalificarFallback(session.getIdUsuario())
        );
    }

    private void buscarPasajerosParaCalificarFallback(int idConductor) {
        String url = Constantes.BASE_URL + "/api/reservas/viaje/" + viajeId;
        ConexionApi.getInstance(this).getArrayNoCache(url,
                reservas -> {
                    try {
                        java.util.ArrayList<Integer> ids     = new java.util.ArrayList<>();
                        java.util.ArrayList<String>  nombres = new java.util.ArrayList<>();

                        for (int i = 0; i < reservas.length(); i++) {
                            org.json.JSONObject res = reservas.optJSONObject(i);
                            if (res == null) continue;
                            String est = res.optString("estado", "").toUpperCase();
                            if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;

                            int    idP  = -1;
                            String nomP = "";
                            org.json.JSONObject po = null;
                            for (String k : new String[]{"pasajero","usuario","user"}) {
                                org.json.JSONObject c = res.optJSONObject(k);
                                if (c != null) { po = c; break; }
                            }
                            if (po != null) {
                                for (String k : new String[]{"id","idUsuarios","idUsuario"}) {
                                    int id = po.optInt(k, -1); if (id > 0) { idP = id; break; }
                                }
                                for (String k : new String[]{"nombre","nombreCompleto","name"}) {
                                    String n = po.optString(k, "");
                                    if (!n.isEmpty() && !n.equals("null")) { nomP = n; break; }
                                }
                            }
                            if (idP <= 0) {
                                for (String k : new String[]{"idUsuarios","idUsuario","idPasajero"}) {
                                    int id = res.optInt(k, -1); if (id > 0) { idP = id; break; }
                                }
                            }
                            if (idP > 0 && idP != idConductor) {
                                ids.add(idP);
                                nombres.add(nomP.isEmpty() ? "Pasajero" : nomP);
                            }
                        }

                        if (!ids.isEmpty()) {
                            final java.util.ArrayList<Integer> fIds     = ids;
                            final java.util.ArrayList<String>  fNombres = nombres;
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                                    mostrarCalificacionesEncadenadas(fIds, fNombres, idConductor, 0));
                        } else {
                            android.util.Log.d("DetalleViaje",
                                    "buscarPasajerosParaCalificarFallback: sin pasajeros activos");
                        }
                    } catch (Exception e) {
                        android.util.Log.e("DetalleViaje",
                                "buscarPasajerosParaCalificarFallback", e);
                    }
                },
                err -> android.util.Log.w("DetalleViaje",
                        "buscarPasajerosParaCalificarFallback: error en el endpoint")
        );
    }


    private void mostrarCalificacionesEncadenadas(ArrayList<Integer> ids, ArrayList<String> nombres,
                                                  int idCalificador, int indice) {
        if (indice >= ids.size()) return;
        int idP=ids.get(indice); String nomP=nombres.get(indice); int sig=indice+1;
        new CalificacionesManager(this).verificarCalificacion(viajeId, idCalificador, idP,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override public void onDebeCalificar() {
                        CalificacionController.mostrarBottomSheetCalificar(DetalleViajeActivity.this,
                                viajeId, idP, nomP, idCalificador, true,
                                (p, c) -> new Handler(Looper.getMainLooper()).postDelayed(
                                        () -> mostrarCalificacionesEncadenadas(ids, nombres, idCalificador, sig), 700));
                    }
                    @Override public void onYaCalifico(int p, String e) {
                        mostrarCalificacionesEncadenadas(ids, nombres, idCalificador, sig);
                    }
                });
    }

    private void dispararCalificacionAlConductor() {
        if (calificacionYaDisparada) return;  // ← AGREGAR
        if (!"FINALIZADO".equals(estadoViaje) && !"COMPLETADO".equals(estadoViaje)) return;

        if (idConductorViaje <= 0) {
            ConexionApi.getInstance(this).getObject(Constantes.viajePorId((long) viajeId),
                    viajeObj -> {
                        extraerConductor(viajeObj);
                        if (idConductorViaje > 0) {
                            new Handler(Looper.getMainLooper())
                                    .postDelayed(this::dispararCalificacionAlConductor, 500);
                        } else {
                            Log.w(TAG, "No se pudo obtener idConductor — abortando calificación");
                            // ← No reintentar, conductor inválido
                        }
                    },
                    err -> Log.w(TAG, "dispararCalificacionAlConductor: error cargando viaje")
            );
            return;
        }

        int    idPasajero   = session.getIdUsuario();
        String nomConductor = (nombreConductorViaje == null || nombreConductorViaje.isEmpty()
                || nombreConductorViaje.startsWith("Conductor #"))
                ? "el conductor"
                : nombreConductorViaje;

        new CalificacionesManager(this).verificarCalificacion(
                viajeId, idPasajero, idConductorViaje,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override
                    public void onDebeCalificar() {
                        // Asegurarse de que el nombre esté disponible antes de abrir el sheet
                        if (nombreConductorViaje == null || nombreConductorViaje.isEmpty()
                                || nombreConductorViaje.startsWith("Conductor #")) {
                            // Cargar nombre y reabrir
                            cargarNombreConductorPorId(idConductorViaje);
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(() -> dispararCalificacionAlConductor(), 1000);
                            return;
                        }
                        CalificacionController.mostrarBottomSheetCalificar(
                                DetalleViajeActivity.this,
                                viajeId,
                                idConductorViaje,
                                nombreConductorViaje,
                                idPasajero,
                                false,    // false = el pasajero está calificando
                                (p, c) -> android.util.Log.d("DetalleViaje",
                                        "Pasajero calificó al conductor: " + p + "⭐")
                        );
                    }

                    @Override
                    public void onYaCalifico(int p, String e) {
                        android.util.Log.d("DetalleViaje",
                                "Pasajero ya calificó al conductor (" + e + ")");
                    }
                }
        );
    }


    // =========================================================================
    //  BOTONES
    // =========================================================================
    private void configurarBotones() {
        if(btnAccionPrincipal!=null) btnAccionPrincipal.setVisibility(View.GONE);
        btnIniciar.setVisibility(View.GONE); btnFinalizar.setVisibility(View.GONE);
        if(btnMensajeConductor!=null) btnMensajeConductor.setVisibility(View.GONE);

        if(btnRecoger!=null) btnRecoger.setVisibility(View.GONE);
        if ("FINALIZADO".equals(estadoViaje) || "COMPLETADO".equals(estadoViaje)) {
            verificarYMostrarBotonPagoRecibido();
        }
        if(esConductor){
            if(estadoViaje.equals("CREADO")||estadoViaje.equals("PROGRAMADO")||estadoViaje.equals("DISPONIBLE"))
                btnIniciar.setVisibility(View.VISIBLE);
            if(estadoViaje.equals("EN_CURSO")||estadoViaje.equals("INICIADO"))
                btnFinalizar.setVisibility(View.VISIBLE);
        } else {
            if(ESTADOS_RESERVABLES.contains(estadoViaje)) verificarReservaActivaYMostrarBoton();
            else if(estadoViaje.equals("CREADO")||estadoViaje.equals("PROGRAMADO")||estadoViaje.equals("DISPONIBLE"))
                mostrarBannerEsperaInicio();
        }
        actualizarBotonChat();
    }

    private void verificarReservaActivaYMostrarBoton(){
        ConexionApi.getInstance(this).getArray(Constantes.MIS_RESERVAS,
                response->{
                    boolean tieneOtraReservaActiva=false;
                    for(int i=0;i<response.length();i++){
                        JSONObject r=response.optJSONObject(i); if(r==null) continue;
                        String est=r.optString("estado","").toUpperCase();
                        int idV=r.optInt("idViaje",0);
                        if(idV==0){JSONObject vo=r.optJSONObject("viaje");if(vo!=null) idV=vo.optInt("idViaje",vo.optInt("id",0));}
                        if(ESTADOS_RESERVA_ACTIVA.contains(est)&&idV!=viajeId){tieneOtraReservaActiva=true;break;}
                    }
                    final boolean bloqueado=tieneOtraReservaActiva;
                    runOnUiThread(()->{ if(bloqueado) mostrarBannerReservaActiva(); else actualizarBotonPasajero(); });
                },
                error->runOnUiThread(this::actualizarBotonPasajero));
    }

    private void verificarYMostrarBotonPagoRecibido() {
        String url = Constantes.pagosPorViaje(viajeId);
        ConexionApi.getInstance(this).getArrayNoCache(url,
                pagos -> {
                    for (int i = 0; i < pagos.length(); i++) {
                        JSONObject p = pagos.optJSONObject(i);
                        if (p == null) continue;
                        boolean cp = p.optBoolean("confirmacionPasajero", false);
                        boolean cc = p.optBoolean("confirmacionConductor", false);
                        if (cp && !cc) {
                            runOnUiThread(() -> mostrarBotonPagoRecibido(p));
                            return;
                        }
                    }
                },
                err -> {}
        );
    }

    private void mostrarBotonPagoRecibido(JSONObject pago) {
        if (btnPagarViaje == null) return;
        double monto = pago.optDouble("monto", precioViaje);
        String modo  = pago.optString("metodoPago", "");
        java.text.NumberFormat nf = java.text.NumberFormat
                .getNumberInstance(new java.util.Locale("es", "CO"));
        btnPagarViaje.setText("✅  Pago recibido — $" + nf.format(monto)
                + (modo.isEmpty() ? "" : "  (" + modo + ")"));
        btnPagarViaje.setVisibility(View.VISIBLE);
        btnPagarViaje.setEnabled(true);
        btnPagarViaje.setBackgroundColor(Color.parseColor("#2E7D32"));
        btnPagarViaje.setOnClickListener(v -> confirmarPagoComoCondutor());
    }

    private void mostrarBannerEsperaInicio() {
        if (btnAccionPrincipal == null) return;
        btnAccionPrincipal.setText("⏳  El conductor aún no inició el viaje");
        btnAccionPrincipal.setEnabled(false); btnAccionPrincipal.setAlpha(0.65f);
        btnAccionPrincipal.setBackgroundColor(Color.parseColor("#B2DFDB"));
        btnAccionPrincipal.setTextColor(Color.WHITE);
        btnAccionPrincipal.setVisibility(View.VISIBLE);
    }

    private void mostrarBannerReservaActiva() {
        if (btnAccionPrincipal == null) return;
        btnAccionPrincipal.setText("🔒  Ya tienes un viaje activo");
        btnAccionPrincipal.setEnabled(false); btnAccionPrincipal.setAlpha(0.75f);
        btnAccionPrincipal.setBackgroundColor(Color.parseColor("#EF5350"));
        btnAccionPrincipal.setTextColor(Color.WHITE);
        btnAccionPrincipal.setVisibility(View.VISIBLE);
    }

    private void actualizarBotonPasajero() {
        if (esConductor || btnAccionPrincipal == null) return;
        if (yaReservo) {
            long transcurrido = tiempoReserva > 0
                    ? System.currentTimeMillis() - tiempoReserva : TIEMPO_CAMBIO_MS + 1;
            if (transcurrido < TIEMPO_CAMBIO_MS) {
                // Dentro del minuto: mostrar con cuenta regresiva
                btnAccionPrincipal.setVisibility(View.VISIBLE);
                btnAccionPrincipal.setEnabled(true);
                btnAccionPrincipal.setAlpha(1f);
                btnAccionPrincipal.setBackgroundColor(Color.parseColor("#0097A7"));
                btnAccionPrincipal.setTextColor(Color.WHITE);
                // El texto lo actualiza el timer
            } else {
                // Pasó el minuto: ocultar
                btnAccionPrincipal.setVisibility(View.GONE);
            }
        } else if (cuposDisponibles > 0 && ESTADOS_RESERVABLES.contains(estadoViaje)) {
            btnAccionPrincipal.setText("🚏  ELEGIR SUBIDA Y BAJADA");
            btnAccionPrincipal.setEnabled(true); btnAccionPrincipal.setAlpha(1f);
            btnAccionPrincipal.setVisibility(View.VISIBLE);
            btnAccionPrincipal.setBackgroundColor(Color.parseColor("#00897B"));
            btnAccionPrincipal.setTextColor(Color.WHITE);
        } else {
            btnAccionPrincipal.setVisibility(View.GONE);
        }
    }

    private void actualizarBotonChat(){
        if(btnMensajeConductor==null) return;
        if(esConductor){
            String l=nombrePasajeroViaje.isEmpty()?"Pasajero":nombrePasajeroViaje;
            btnMensajeConductor.setText("💬  Chat con "+l);
            btnMensajeConductor.setVisibility(idPasajeroViaje>0?View.VISIBLE:View.GONE);
        } else {
            if(idConductorViaje>0){
                btnMensajeConductor.setText("💬  Chat con "+(nombreConductorViaje.isEmpty()?"Conductor":nombreConductorViaje));
                btnMensajeConductor.setVisibility(View.VISIBLE);
                btnMensajeConductor.setEnabled(true); btnMensajeConductor.setAlpha(1f);
            } else {
                btnMensajeConductor.setText("💬  Chat con Conductor");
                btnMensajeConductor.setVisibility(View.VISIBLE);
                btnMensajeConductor.setEnabled(false); btnMensajeConductor.setAlpha(0.5f);
                new Handler(Looper.getMainLooper()).postDelayed(this::cargarConductorDelViaje, 1500);
            }
        }
    }

    // =========================================================================
    //  ACCIONES CONDUCTOR
    // =========================================================================
    private void cambiarEstadoViaje(String accion){
        loaderDetalle.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).post(Constantes.viajePorId((long)viajeId)+"/"+accion, null,
                response->{ loaderDetalle.setVisibility(View.GONE); Toast.makeText(this,"✅ Viaje "+accion+"do",Toast.LENGTH_SHORT).show(); cargarDetalleViaje(); },
                error->{ loaderDetalle.setVisibility(View.GONE); Toast.makeText(this,"Error al "+accion,Toast.LENGTH_LONG).show(); });
    }

    private void confirmarFinalizar(){
        new AlertDialog.Builder(this)
                .setTitle("Finalizar viaje").setMessage("¿Finalizar? Se liberarán todos los cupos.")
                .setPositiveButton("Finalizar",(d,w)->finalizarViaje())
                .setNegativeButton("Cancelar",null).show();
    }

    // =========================================================================
    //  CHAT
    // =========================================================================
    private void abrirOCrearChat() {
        Log.d(TAG, "abrirOCrearChat → esConductor=" + esConductor
                + " idConductorViaje=" + idConductorViaje
                + " nombreConductorViaje=" + nombreConductorViaje
                + " idPasajeroViaje=" + idPasajeroViaje);

        if (!esConductor && idConductorViaje <= 0) {
            Toast.makeText(this, "Cargando datos del conductor...", Toast.LENGTH_SHORT).show();
            cargarConductorDelViaje();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d(TAG, "Después de cargar → idConductorViaje=" + idConductorViaje
                        + " nombre=" + nombreConductorViaje);
                if (idConductorViaje > 0) iniciarConversacion();
                else Toast.makeText(this, "No se pudo identificar al conductor.", Toast.LENGTH_LONG).show();
            }, 1500);
            return;
        }
        iniciarConversacion();
    }

    private void iniciarConversacion() {
        if (!esConductor && idConductorViaje <= 0) {
            Toast.makeText(this, "No se pudo identificar al conductor.", Toast.LENGTH_LONG).show();
            return;
        }
        int miId = session.getIdUsuario();
        int idP, idC;
        String nom;
        if (esConductor) {
            idP = idPasajeroViaje > 0 ? idPasajeroViaje : miId;
            idC = miId;
            nom = nombrePasajeroViaje.isEmpty() ? "Pasajero" : nombrePasajeroViaje;
        } else {
            idP = miId;
            idC = idConductorViaje;
            nom = nombreConductorViaje.isEmpty() ? "Conductor" : nombreConductorViaje;
        }

        // ── LOG para diagnosticar qué IDs se están usando ──
        Log.d(TAG, "iniciarConversacion → esConductor=" + esConductor
                + " | miId=" + miId
                + " | idP=" + idP
                + " | idC=" + idC
                + " | nom=" + nom
                + " | idConductorViaje=" + idConductorViaje
                + " | nombreConductorViaje=" + nombreConductorViaje
                + " | idPasajeroViaje=" + idPasajeroViaje
                + " | nombrePasajeroViaje=" + nombrePasajeroViaje);

        JSONObject body = new JSONObject();
        try {
            body.put("idViaje",    viajeId);
            body.put("idPasajero", idP);
            body.put("idConductor", idC);
        } catch (JSONException e) {
            return;
        }
        final String nf = nom;
        final int pf = idP;
        final int cf = idC;
        loaderDetalle.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).post(Constantes.CHAT_CONVERSACIONES, body,
                response -> {
                    loaderDetalle.setVisibility(View.GONE);
                    long ic = extraerIdConversacion(response);
                    Log.d(TAG, "POST conversacion → response=" + response.toString()
                            + " | idConversacion=" + ic);
                    if (ic > 0) navegarAlChat(ic, nf);
                    else buscarConversacion(pf, cf, nf);
                },
                error -> {
                    loaderDetalle.setVisibility(View.GONE);
                    Log.w(TAG, "POST conversacion falló → buscando conversacion existente");
                    buscarConversacion(pf, cf, nf);
                });
    }

    private void buscarConversacion(int idP, int idC, String nom){
        String url=Constantes.CHAT_CONVERSACIONES+"?idViaje="+viajeId+"&idPasajero="+idP+"&idConductor="+idC;
        ConexionApi.getInstance(this).getObject(url,
                response->{
                    long ic=extraerIdConversacion(response);
                    if(ic<=0){
                        for(String k:new String[]{"content","conversaciones","data"}){
                            JSONArray a=response.optJSONArray(k);
                            if(a!=null&&a.length()>0){ic=extraerIdConversacion(a.optJSONObject(0));if(ic>0) break;}
                        }
                    }
                    if(ic>0) navegarAlChat(ic,nom);
                    else Toast.makeText(this,"No se pudo abrir el chat",Toast.LENGTH_LONG).show();
                },
                error->Toast.makeText(this,"Error al abrir chat",Toast.LENGTH_SHORT).show());
    }

    private long extraerIdConversacion(JSONObject r){
        if(r==null) return -1;
        long id=r.optLong("id",-1); if(id>0) return id;
        id=r.optLong("idConversacion",-1); if(id>0) return id;
        JSONObject d=r.optJSONObject("data"); if(d!=null){id=d.optLong("id",-1);if(id>0) return id;}
        return -1;
    }

    private void navegarAlChat(long idC, String nom) {
        String nombreFinal;

        if (esConductor) {
            // Soy conductor → el contacto es el PASAJERO
            nombreFinal = (nombrePasajeroViaje != null && !nombrePasajeroViaje.isEmpty())
                    ? nombrePasajeroViaje
                    : (nom != null && !nom.isEmpty() ? nom : "Pasajero");
        } else {
            // Soy pasajero → el contacto es el CONDUCTOR
            boolean nomGenerico = nom == null || nom.isEmpty()
                    || nom.equals("Conductor") || nom.equals("Pasajero")
                    || nom.startsWith("Conductor #") || nom.startsWith("Pasajero #");

            nombreFinal = nomGenerico
                    ? (nombreConductorViaje != null
                    && !nombreConductorViaje.isEmpty()
                    && !nombreConductorViaje.startsWith("Conductor #")
                    ? nombreConductorViaje
                    : (nom != null ? nom : "Conductor"))
                    : nom;
        }

        Log.d(TAG, "navegarAlChat → idConversacion=" + idC
                + " nombreFinal=" + nombreFinal
                + " esConductor=" + esConductor);

        Intent i = new Intent(this, Chat.class);
        i.putExtra("idConversacion", idC);
        i.putExtra("nombre", nombreFinal);
        startActivity(i);
    }

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
                                if (iv > 0) { idConductorViaje = iv; nombreConductorViaje = "Conductor #" + iv; }
                            }
                        }
                        if (idConductorViaje > 0 && (nombreConductorViaje.isEmpty() || nombreConductorViaje.startsWith("Conductor #"))) {
                            cargarNombreConductorPorId(idConductorViaje);
                        } else {
                            runOnUiThread(() -> {
                                actualizarNombreConductorUI(); actualizarBotonChat();
                                if (!esConductor && cardMiReserva != null && cardMiReserva.getVisibility() == View.VISIBLE)
                                    cargarMiReservaPasajero();
                            });
                        }
                    } catch (Exception e) { Log.e(TAG,"Error extrayendo conductor",e); }
                },
                error -> Log.e(TAG,"Error recargando viaje para conductor"));
    }

    // =========================================================================
    //  PARADAS DINÁMICAS
    // =========================================================================
    private void generarParadasDinamicas() {
        paradasDin.clear();

        // Si hay paradas reales del backend con nombres válidos, usarlas
        if (!paradasRuta.isEmpty()) {
            for (JSONObject p : paradasRuta) {
                double lat = p.optDouble("lat", 0), lng = p.optDouble("lng", 0);
                if (lat == 0) continue;
                String nombre = p.optString("nombre", "").trim();
                // Saltar genéricas, origen y destino
                if (nombre.isEmpty() || nombre.equals("null")
                        || nombre.matches("(?i)parada\\s*\\d+")
                        || nombre.equalsIgnoreCase(origenActual)
                        || nombre.equalsIgnoreCase(destinoActual)) continue;
                ParadaDinamica pd = new ParadaDinamica(nombre, lat, lng, 0);
                pd.idParadaBD = p.optInt("idParada", p.optInt("id", 0));
                paradasDin.add(pd);
            }
        }

        // Si no hay paradas reales válidas, generar desde la ruta activa con geocodificación
        if (paradasDin.isEmpty() && rutaActiva != null && rutaActiva.size() >= 2) {
            final List<GeoPoint> base = new ArrayList<>(rutaActiva);
            int total = base.size();
            int num = Math.min(6, Math.max(3, total / 20));
            List<Integer> indices = new ArrayList<>();
            double paso = (double)(total - 2) / (num + 1);
            for (int i = 1; i <= num; i++) {
                int idx = 1 + (int)(i * paso);
                if (idx < total - 1) indices.add(idx);
            }
            for (int i = 0; i < indices.size(); i++) {
                int idx = indices.get(i);
                GeoPoint gp = base.get(idx);
                double pct = (double) idx / (total - 1) * 100;
                // Nombre provisional mientras geocodifica
                paradasDin.add(new ParadaDinamica(
                        String.format("📍 Cargando... (%.0f%% ruta)", pct),
                        gp.getLatitude(), gp.getLongitude(), idx));
            }
            // Geocodificar en background y actualizar nombres
            geocodificarEnBackground(indices, base);
        }

        // Siempre agregar destino al final
        paradasDin.add(new ParadaDinamica(destinoActual, latDestino, lngDestino, -1));
    }

    private void geocodificarEnBackground(List<Integer> indices, List<GeoPoint> base){
        new Thread(()->{
            for(int i=0;i<indices.size()&&i<paradasDin.size()-1;i++){
                GeoPoint gp=base.get(indices.get(i));
                try{
                    String url="https://nominatim.openstreetmap.org/reverse?lat="+gp.getLatitude()
                            +"&lon="+gp.getLongitude()+"&format=json&addressdetails=1&zoom=16&accept-language=es";
                    String resp=peticionHttp(url);
                    if(resp!=null&&!resp.isEmpty()){
                        JSONObject geo=new JSONObject(resp);
                        String nombre=extraerNombreNominatim(geo.optJSONObject("address"),geo);
                        final int fi=i; final String fn=nombre;
                        runOnUiThread(()->{if(fi<paradasDin.size()-1) paradasDin.get(fi).nombre=fn;});
                    }
                    Thread.sleep(500);
                } catch(Exception ignored){}
            }
        }).start();
    }

    private String extraerNombreNominatim(JSONObject addr, JSONObject geo){
        if(addr==null) return geo.optString("display_name","Parada");
        for(String c:new String[]{"neighbourhood","suburb","quarter","city_district","road"}){
            String v=addr.optString(c,""); if(!v.isEmpty()&&!v.equals("null")) return v;
        }
        String d=geo.optString("display_name","");
        return d.isEmpty()?"Parada":d.split(",")[0].trim();
    }

    private void poblarLista(LinearLayout container, ArrayList<ParadaDinamica> paradas,
                             String filtro, float dp, int p8, int p4,
                             java.util.function.Consumer<ParadaDinamica> onSelect){
        container.removeAllViews();
        if(paradas.isEmpty()){
            TextView tv=new TextView(this); tv.setText("Sin paradas disponibles");
            tv.setTextSize(13f); tv.setTextColor(Color.parseColor("#9E9E9E")); tv.setPadding(p8,p8,p8,p8);
            container.addView(tv); return;
        }
        container.addView(crearFilaParada("🟢  "+origenActual+"  (Inicio)",null,false,dp,p8,p4,onSelect));
        for(ParadaDinamica pd:paradas){
            boolean esD=pd.idxEnRuta==-1;
            container.addView(crearFilaParada((esD?"🔴":"🔵")+"  "+pd.nombre+(esD?"  (Destino final)":""),pd,true,dp,p8,p4,onSelect));
        }
    }

    private View crearFilaParada(String label, ParadaDinamica pd, boolean sel,
                                 float dp, int p8, int p4,
                                 java.util.function.Consumer<ParadaDinamica> onSel){
        LinearLayout fila=new LinearLayout(this); fila.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpF=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lpF.setMargins(0,(int)(2*dp),0,(int)(2*dp)); fila.setLayoutParams(lpF);
        GradientDrawable bg=new GradientDrawable(); bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(10*dp);
        bg.setColor(sel?Color.parseColor("#F9FAFB"):Color.parseColor("#F0F4F8"));
        bg.setStroke((int)(1*dp),sel?Color.parseColor("#B2DFDB"):Color.parseColor("#E0E0E0"));
        fila.setBackground(bg); fila.setPadding(p8,p8,p8,p8);
        TextView tv=new TextView(this); tv.setText(label); tv.setTextSize(14f);
        tv.setTextColor(sel?Color.parseColor("#004D40"):Color.parseColor("#9E9E9E"));
        if(!sel) tv.setTypeface(null,Typeface.ITALIC);
        tv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        fila.addView(tv);
        if(sel&&pd!=null){
            fila.setClickable(true); fila.setFocusable(true);
            fila.setOnClickListener(v->{
                GradientDrawable bgS=new GradientDrawable(); bgS.setShape(GradientDrawable.RECTANGLE);
                bgS.setCornerRadius(10*dp); bgS.setColor(Color.parseColor("#E0F7FA"));
                bgS.setStroke((int)(2*dp),Color.parseColor("#00897B")); fila.setBackground(bgS);
                onSel.accept(pd);
            });
        }
        return fila;
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private void extraerConductor(JSONObject r) {
        JSONObject co = r.optJSONObject("conductor");
        if (co != null) {
            idConductorViaje    = extractId(co);
            nombreConductorViaje = extractNombre(co);
            if (idConductorViaje <= 0 || nombreConductorViaje.isEmpty()) {
                JSONObject u = co.optJSONObject("usuario");
                if (u != null) {
                    if (idConductorViaje <= 0)       idConductorViaje    = extractId(u);
                    if (nombreConductorViaje.isEmpty()) nombreConductorViaje = extractNombre(u);
                }
            }
        }
        if (idConductorViaje <= 0)
            for (String c : new String[]{"idConductor","conductorId","idUsuarioConductor","idUsuario"}) {
                int v = r.optInt(c, -1); if (v > 0) { idConductorViaje = v; break; }
            }
        if (idConductorViaje <= 0) {
            JSONObject veh = r.optJSONObject("vehiculo");
            if (veh != null) {
                for (String c : new String[]{"idUsuario","idConductor","conductorId"}) {
                    int v = veh.optInt(c, -1); if (v > 0) { idConductorViaje = v; break; }
                }
                JSONObject condEnVeh = veh.optJSONObject("conductor");
                if (condEnVeh != null) {
                    if (idConductorViaje <= 0)       idConductorViaje    = extractId(condEnVeh);
                    if (nombreConductorViaje.isEmpty()) nombreConductorViaje = extractNombre(condEnVeh);
                }
            }
        }
        if (nombreConductorViaje.isEmpty())
            for (String c : new String[]{"nombreConductor","conductorNombre","nombre"}) {
                String v = r.optString(c, "");
                if (!v.isEmpty() && !v.equals("null")) { nombreConductorViaje = v; break; }
            }
        if (idConductorViaje > 0 && nombreConductorViaje.isEmpty())
            nombreConductorViaje = "Conductor #" + idConductorViaje;
        Log.d(TAG, "extraerConductor → id=" + idConductorViaje + " nombre=" + nombreConductorViaje);
    }

    private void actualizarNombreConductorUI(){
        if(txtConductor==null) return;
        String n=nombreConductorViaje.isEmpty()?"Sin asignar":nombreConductorViaje;
        txtConductor.setText(esConductor&&idConductorViaje==session.getIdUsuario()?"🚗 Tú ("+n+")":"🚗 "+n);
    }
    private void calcularYMostrarPrecioTramo() {
        if (gpParada == null) return;

        double latSubida = gpSubida != null ? gpSubida.getLatitude()  : latOrigen;
        double lngSubida = gpSubida != null ? gpSubida.getLongitude() : lngOrigen;

        // Si no tenemos distancia total de la ruta, no podemos calcular proporcionalmente
        if (distanciaKm <= 0) {
            Log.w(TAG, "calcularYMostrarPrecioTramo: distanciaKm no disponible");
            return;
        }

        PrecioTramoPasajeroManager.calcular(
                latSubida,               lngSubida,
                gpParada.getLatitude(),  gpParada.getLongitude(),
                distanciaKm,
                precioViaje,
                resultado -> {
                    // ← ya estamos en el hilo UI
                    precioCalculadoPasajero = resultado.precioFinal;

                    // Actualizar fila "Precio total" en la card Tu Reserva
                    actualizarValorFila(ROW_ID_PRECIO, resultado.precioFormateado);

                    // Actualizar el TextView principal de precio
                    if (txtPrecio != null)
                        txtPrecio.setText(resultado.precioFormateado);

                    // Toast informativo para el pasajero
                    String info = "💰 Tu precio: " + resultado.precioFormateado
                            + "  (" + String.format(Locale.getDefault(),
                            "%.1f km", resultado.distanciaKm) + ")";
                    Toast.makeText(this, info, Toast.LENGTH_LONG).show();

                    Log.d(TAG, "Precio tramo calculado: " + resultado);
                }
        );
    }

    private void mostrarFechaHora(String fh){
        if(txtFechaHora==null) return;
        if(!fh.isEmpty()&&!fh.equals("null")){
            try{
                String fl=fh.replace("T"," ").replaceAll("\\.\\d{3}Z$","");
                SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault());
                SimpleDateFormat out=new SimpleDateFormat("EEE dd/MM/yyyy  🕐 HH:mm",new Locale("es","CO"));
                Date date=in.parse(fl); txtFechaHora.setText("📅 "+out.format(date));
            }catch(Exception ex){
                txtFechaHora.setText("📅 "+fh.replace("T"," ").replaceAll("\\.\\d{3}Z$",""));
            }
            txtFechaHora.setVisibility(View.VISIBLE);
        } else { txtFechaHora.setVisibility(View.GONE); }
    }



    private String etiquetaEstado(String e){
        switch(e){
            case "CREADO":    return "📋 Estado: Disponible";
            case "PROGRAMADO":return "📅 Estado: Programado";
            case "DISPONIBLE":return "✅ Estado: Disponible";
            case "EN_CURSO": case "INICIADO": return "🚗 Estado: En curso";
            case "FINALIZADO":return "🏁 Estado: Finalizado";
            case "CANCELADO": return "❌ Estado: Cancelado";
            default:          return "📌 Estado: "+e;
        }
    }

    private int extractId(JSONObject obj){
        if(obj==null) return -1;
        for(String k:new String[]{"id","idUsuarios","idUsuario","userId","conductorId","idConductor","pasajeroId","idPasajero"}){
            int v=obj.optInt(k,-1); if(v>0) return v;}
        return -1;
    }

    private String extractNombre(JSONObject obj){
        if(obj==null) return "";
        for(String k:new String[]{"nombre","nombreCompleto","name","fullName","nombreUsuario","displayName"}){
            String v=obj.optString(k,""); if(!v.isEmpty()&&!v.equals("null")) return v;}
        String n=obj.optString("nombres",""),a=obj.optString("apellidos","");
        if(!n.isEmpty()||!a.isEmpty()) return (n+" "+a).trim();
        return "";
    }

    private double primeraCoord(JSONObject o, String[] cs, double def){
        for(String c:cs){double v=o.optDouble(c,Double.NaN);if(!Double.isNaN(v)&&v!=0) return v;}
        return def;
    }

    private double primeraCoordDistinta(JSONObject o, String[] cs, double ref, double def){
        for(String c:cs){double v=o.optDouble(c,Double.NaN);if(!Double.isNaN(v)&&v!=0&&Math.abs(v-ref)>0.0001) return v;}
        return def;
    }

    private String primeraStr(JSONObject o, String[] cs){
        for(String c:cs){String v=o.optString(c,"").trim();if(!v.isEmpty()&&!v.equals("null")) return v;}
        return "";
    }

    private boolean sonIguales(double la, double ln, double lb, double lm){
        return Math.abs(la-lb)<0.0001&&Math.abs(ln-lm)<0.0001;
    }

    private double[] geocodificarTexto(String dir){
        try{
            String q=dir.toLowerCase().contains("popay")?dir:dir+", Popayan, Colombia";
            String url="https://nominatim.openstreetmap.org/search?q="
                    +java.net.URLEncoder.encode(q,"UTF-8")+"&format=json&limit=1&countrycodes=co";
            String resp=peticionHttp(url);
            if(resp==null||resp.isEmpty()||resp.equals("[]")) return null;
            JSONArray arr=new JSONArray(resp); if(arr.length()==0) return null;
            JSONObject obj=arr.getJSONObject(0);
            return new double[]{obj.getDouble("lat"),obj.getDouble("lon")};
        }catch(Exception e){return null;}
    }

    private String peticionHttp(String urlStr) throws Exception {
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(urlStr).openConnection();
            c.setRequestProperty("User-Agent","Moviflexx-App/1.0");
            c.setConnectTimeout(15000); c.setReadTimeout(15000);
            BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb=new StringBuilder(); String l;
            while((l=r.readLine())!=null) sb.append(l);
            r.close(); return sb.toString();
        } finally{if(c!=null) c.disconnect();}
    }

    private ArrayList<GeoPoint> parsearGeoJsonString(String geojsonStr){
        if(geojsonStr==null||geojsonStr.trim().isEmpty()) return null;
        try{
            geojsonStr=geojsonStr.trim();
            if(geojsonStr.startsWith("{")) return parsearGeoJsonObj(new JSONObject(geojsonStr));
            else if(geojsonStr.startsWith("[")){
                JSONArray arr=new JSONArray(geojsonStr);
                ArrayList<GeoPoint> pts=new ArrayList<>();
                for(int i=0;i<arr.length();i++){
                    JSONArray pair=arr.optJSONArray(i);
                    if(pair!=null&&pair.length()>=2) pts.add(new GeoPoint(pair.getDouble(1),pair.getDouble(0)));
                }
                return pts.size()>=2?pts:null;
            }
        }catch(Exception e){Log.e(TAG,"Error parseando geojsonString: "+e.getMessage());}
        return null;
    }

    private ArrayList<GeoPoint> parsearGeoJsonObj(JSONObject obj){
        if(obj==null) return null;
        try{
            String tipo=obj.optString("type",""); JSONObject geometry=null;
            if("Feature".equals(tipo)) geometry=obj.optJSONObject("geometry");
            else if("LineString".equals(tipo)) geometry=obj;
            else if("FeatureCollection".equals(tipo)){
                JSONArray features=obj.optJSONArray("features");
                if(features!=null&&features.length()>0){JSONObject feat=features.optJSONObject(0);if(feat!=null) geometry=feat.optJSONObject("geometry");}
            } else{ geometry=obj.optJSONObject("geometry"); if(geometry==null) geometry=obj; }
            if(geometry==null) return null;
            JSONArray coords=geometry.optJSONArray("coordinates");
            if(coords==null||coords.length()==0) return null;
            ArrayList<GeoPoint> pts=new ArrayList<>();
            for(int i=0;i<coords.length();i++){
                JSONArray pair=coords.optJSONArray(i);
                if(pair!=null&&pair.length()>=2) pts.add(new GeoPoint(pair.getDouble(1),pair.getDouble(0)));
            }
            return pts.size()>=2?pts:null;
        }catch(Exception e){Log.e(TAG,"Error parsearGeoJsonObj: "+e.getMessage());return null;}
    }


    private Bitmap crearBitmapMarcador(int colorInt, String letra){
        int size=96;
        Bitmap bmp=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bmp);
        Paint pS=new Paint(Paint.ANTI_ALIAS_FLAG); pS.setColor(Color.argb(80,0,0,0));
        c.drawCircle(size/2f+3,size/2f+5,size/2f-6,pS);
        Paint pC=new Paint(Paint.ANTI_ALIAS_FLAG); pC.setColor(colorInt);
        c.drawCircle(size/2f,size/2f-4,size/2f-8,pC);
        Paint pB=new Paint(Paint.ANTI_ALIAS_FLAG); pB.setColor(Color.WHITE);
        pB.setStyle(Paint.Style.STROKE); pB.setStrokeWidth(5f);
        c.drawCircle(size/2f,size/2f-4,size/2f-8,pB);
        Paint pT=new Paint(Paint.ANTI_ALIAS_FLAG); pT.setColor(Color.WHITE);
        pT.setTextSize(letra.length()>1?26f:36f); pT.setTypeface(Typeface.DEFAULT_BOLD);
        pT.setTextAlign(Paint.Align.CENTER); c.drawText(letra,size/2f,size/2f+9,pT);
        return bmp;
    }

    // =========================================================================
    //  MODELOS INTERNOS
    // =========================================================================
    private static class ParadaDinamica {
        String nombre; double lat, lng; int idxEnRuta; int idParadaBD = 0;
        ParadaDinamica(String nombre, double lat, double lng, int idx){
            this.nombre=nombre; this.lat=lat; this.lng=lng; this.idxEnRuta=idx;
        }
        GeoPoint toGeoPoint(){ return new GeoPoint(lat,lng); }
    }

    private void iniciarTimerCambioReserva() {
        tiempoReserva = System.currentTimeMillis();
        // Actualizar el botón cada segundo mostrando cuenta regresiva
        timerRunnable = new Runnable() {
            @Override public void run() {
                long transcurrido = System.currentTimeMillis() - tiempoReserva;
                long restante = TIEMPO_CAMBIO_MS - transcurrido;
                if (restante > 0) {
                    long segs = restante / 1000;
                    if (btnAccionPrincipal != null) {
                        btnAccionPrincipal.setText("🔄  CAMBIAR  (" + segs + "s)");
                        btnAccionPrincipal.setEnabled(true);
                        btnAccionPrincipal.setAlpha(1f);
                        btnAccionPrincipal.setBackgroundColor(Color.parseColor("#0097A7"));
                    }
                    timerHandler.postDelayed(this, 1000);
                } else {
                    // Tiempo agotado: ocultar botón
                    if (btnAccionPrincipal != null) {
                        btnAccionPrincipal.setVisibility(View.GONE);
                    }
                }
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void detenerTimerCambio() {
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
    }
    // =========================================================================
    //  PARADA ADAPTER
    // =========================================================================
    static class ParadaAdapter extends RecyclerView.Adapter<ParadaAdapter.VH> {
        private final ArrayList<String> items = new ArrayList<>();
        private final String origen, destino;

        ParadaAdapter(ArrayList<String> paradas, String origen, String destino) {
            this.origen = origen; this.destino = destino; this.items.addAll(paradas);
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.content.Context ctx = parent.getContext();
            float d = ctx.getResources().getDisplayMetrics().density;
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding((int)(4*d),(int)(10*d),(int)(4*d),(int)(10*d));
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            LinearLayout colLeft = new LinearLayout(ctx);
            colLeft.setOrientation(LinearLayout.VERTICAL);
            colLeft.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            colLeft.setLayoutParams(new LinearLayout.LayoutParams((int)(28*d), LinearLayout.LayoutParams.MATCH_PARENT));
            TextView tvDot = new TextView(ctx); tvDot.setTextSize(13f); tvDot.setGravity(android.view.Gravity.CENTER); tvDot.setTag("dot"); colLeft.addView(tvDot);
            View linea = new View(ctx); LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams((int)(2*d),(int)(20*d)); llp.gravity=android.view.Gravity.CENTER_HORIZONTAL; llp.topMargin=(int)(2*d); linea.setLayoutParams(llp); linea.setTag("linea"); colLeft.addView(linea);
            row.addView(colLeft);
            TextView tvNombre = new TextView(ctx); tvNombre.setTextSize(13.5f); tvNombre.setTag("nombre");
            LinearLayout.LayoutParams tnlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); tnlp.setMarginStart((int)(10*d)); tvNombre.setLayoutParams(tnlp);
            row.addView(tvNombre);
            return new VH(row);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            String nombre = items.get(pos);
            boolean isFirst = pos==0, isLast = pos==items.size()-1;
            TextView tvDot = h.root.findViewWithTag("dot");
            View linea = h.root.findViewWithTag("linea");
            TextView tvN = h.root.findViewWithTag("nombre");
            if (isFirst) { tvDot.setText("🟢"); tvN.setText(nombre.isEmpty()?origen:nombre); tvN.setTextColor(Color.parseColor("#00695C")); tvN.setTypeface(null,android.graphics.Typeface.BOLD); linea.setBackgroundColor(Color.parseColor("#80CBC4")); }
            else if (isLast) { tvDot.setText("🔴"); tvN.setText(nombre.isEmpty()?destino:nombre); tvN.setTextColor(Color.parseColor("#546E7A")); tvN.setTypeface(null,android.graphics.Typeface.NORMAL); linea.setBackgroundColor(Color.parseColor("#B2DFDB")); }
            else { tvDot.setText("🔵"); tvN.setText(nombre); tvN.setTextColor(Color.parseColor("#00838F")); tvN.setTypeface(null,android.graphics.Typeface.NORMAL); linea.setBackgroundColor(Color.parseColor("#80CBC4")); }
            linea.setVisibility(isLast ? View.INVISIBLE : View.VISIBLE);
        }

        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder { final LinearLayout root; VH(View v){super(v);root=(LinearLayout)v;} }
    }

    @Override
    public void onBackPressed() {
        if (esConductor) {
            Intent intent = new Intent(this, HomeConductor.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, HomePasajero.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        finish();
    }

    // =========================================================================
    //  PARADA PASAJERO ADAPTER
    // =========================================================================
    static class ParadaPasajeroAdapter extends RecyclerView.Adapter<ParadaPasajeroAdapter.VH> {
        private final ArrayList<String> items;
        private final String bajada;

        ParadaPasajeroAdapter(ArrayList<String> items, String bajada) {
            this.items = items; this.bajada = bajada;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.content.Context ctx = parent.getContext();
            float d = ctx.getResources().getDisplayMetrics().density;

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0,(int)(8*d),0,(int)(8*d));
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

            LinearLayout colLeft = new LinearLayout(ctx);
            colLeft.setOrientation(LinearLayout.VERTICAL);
            colLeft.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            colLeft.setLayoutParams(new LinearLayout.LayoutParams((int)(32*d), LinearLayout.LayoutParams.MATCH_PARENT));

            TextView tvDot = new TextView(ctx); tvDot.setTextSize(18f);
            tvDot.setGravity(android.view.Gravity.CENTER); tvDot.setTag("dot"); colLeft.addView(tvDot);

            View linea = new View(ctx);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams((int)(2.5f*d),(int)(28*d));
            llp.gravity=android.view.Gravity.CENTER_HORIZONTAL; llp.topMargin=(int)(3*d);
            linea.setLayoutParams(llp); linea.setTag("linea"); colLeft.addView(linea);
            row.addView(colLeft);

            LinearLayout colRight = new LinearLayout(ctx);
            colRight.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams crlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            crlp.setMarginStart((int)(10*d)); colRight.setLayoutParams(crlp);

            TextView tvLabel = new TextView(ctx); tvLabel.setTextSize(9f);
            tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            tvLabel.setLetterSpacing(0.12f); tvLabel.setTag("label"); colRight.addView(tvLabel);

            TextView tvNombre = new TextView(ctx); tvNombre.setTextSize(14.5f);
            tvNombre.setTypeface(null, android.graphics.Typeface.BOLD); tvNombre.setTag("nombre");
            LinearLayout.LayoutParams tnlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tnlp.topMargin=(int)(3*d); tnlp.bottomMargin=(int)(4*d); tvNombre.setLayoutParams(tnlp);
            colRight.addView(tvNombre);

            row.addView(colRight);
            return new VH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            float d = holder.root.getContext().getResources().getDisplayMetrics().density;
            int n = items.size();
            String nombre = items.get(position);
            boolean esOrigen  = position == 0;
            boolean esDestino = position == n - 1;
            boolean esBajada  = !esOrigen && !esDestino;

            TextView tvDot   = holder.root.findViewWithTag("dot");
            View     linea   = holder.root.findViewWithTag("linea");
            TextView tvLabel = holder.root.findViewWithTag("label");
            TextView tvNombre= holder.root.findViewWithTag("nombre");
            ViewGroup colRight = (ViewGroup) tvLabel.getParent();

            if (esOrigen) {
                tvDot.setText("🟢");
                tvLabel.setText("ORIGEN"); tvLabel.setTextColor(Color.parseColor("#00897B"));
                tvNombre.setText(nombre); tvNombre.setTextColor(Color.parseColor("#004D40"));
                linea.setBackgroundColor(Color.parseColor("#80CBC4"));
                if (colRight != null) { colRight.setBackground(null); colRight.setPadding(0,(int)(4*d),0,(int)(4*d)); }
            } else if (esBajada) {
                tvDot.setText("🚏");
                tvLabel.setText("📍  TU BAJADA"); tvLabel.setTextColor(Color.parseColor("#004D40"));
                tvNombre.setText(nombre); tvNombre.setTextColor(Color.parseColor("#00695C"));
                linea.setBackgroundColor(Color.parseColor("#26A69A"));
                if (colRight != null) {
                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                    bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(12 * d);
                    bg.setColor(Color.parseColor("#E0F7FA"));
                    bg.setStroke((int)(2*d), Color.parseColor("#00BCD4"));
                    colRight.setBackground(bg);
                    colRight.setPadding((int)(12*d),(int)(10*d),(int)(12*d),(int)(10*d));
                }
            } else {
                tvDot.setText("🔴");
                tvLabel.setText("DESTINO FINAL"); tvLabel.setTextColor(Color.parseColor("#90A4AE"));
                tvNombre.setText(nombre); tvNombre.setTextColor(Color.parseColor("#546E7A"));
                tvNombre.setTypeface(null, android.graphics.Typeface.NORMAL);
                linea.setBackgroundColor(Color.parseColor("#B2DFDB"));
                if (colRight != null) { colRight.setBackground(null); colRight.setPadding(0,(int)(4*d),0,(int)(4*d)); }
            }
            linea.setVisibility(esDestino ? View.INVISIBLE : View.VISIBLE);
        }


        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder { final LinearLayout root; VH(View v){super(v);root=(LinearLayout)v;} }
    }

} // ← Única llave de cierre de DetalleViajeActivity