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
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Manager.CalificacionesManager;
import com.arlys.moviflexx.model.ViajeAlertaManager;
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


public class DetalleViajeActivity extends BaseActivity {

    // ── Constantes ────────────────────────────────────────────────────────────
    private static final String TAG        = "DetalleViaje";
    private static final int    POLLING_MS = 5000;
    private static final int    GPS_CONDUCTOR_POLLING_MS = 3000; // ← cada 3 seg para pasajero
    private static final int    LOCATION_PERMISSION_REQUEST = 1001;
    private static final int REQUEST_MAPA_SUBIDA = 2001;


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
    private MaterialCardView cardInfoConductor;
    private static final long TIEMPO_CAMBIO_MS = 60_000L; // 1 minuto
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    // ── Modelo de asiento (nuevo sistema visual) ─────────────────────────────────
    private static final int SEAT_LIBRE      = 0;
    private static final int SEAT_OCUPADO    = 1;
    private static final int SEAT_SELECCIONADO = 2;
    private static final int SEAT_CONDUCTOR  = 3;
    private static final int SEAT_RESERVANDO = 4;
    private static final int SEAT_RESERVADO = 5;
    private static final int COLOR_SEAT_RESERVADO       = 0xFF7B1FA2; // morado
    private static final int COLOR_SEAT_BORDER_RESERVADO= 0xFF4A0072;
    private static final int COLOR_SEAT_LIBRE       = 0xFF1DB87A;
    private static final int COLOR_SEAT_OCUPADO     = 0xFFE74C3C;
    private static final int COLOR_SEAT_SELECCIONADO= 0xFFF39C12;
    private static final int COLOR_SEAT_CONDUCTOR   = 0xFF2C3E50;
    private static final int COLOR_SEAT_RESERVANDO  = 0xFF9B59B6;
    private static final int COLOR_SEAT_BORDER_LIBRE       = 0xFF0fa060;
    private static final int COLOR_SEAT_BORDER_OCUPADO     = 0xFFc0392b;
    private static final int COLOR_SEAT_BORDER_SELECCIONADO= 0xFFd68910;
    private static final int COLOR_SEAT_BORDER_CONDUCTOR   = 0xFF1a252f;
    private static final int COLOR_SEAT_BORDER_RESERVANDO  = 0xFF7d3c98;

    // IDs de marcadores
    private static final String MID_ORIGEN    = "m_origen";
    private static final String MID_DESTINO   = "m_destino";
    private static final String MID_PARADA    = "m_parada";    // bajada
    private static final String MID_SUBIDA    = "m_subida";    // ← punto de recogida
    private static final String MID_CONDUCTOR = "m_conductor";

    private static final List<String> ESTADOS_RESERVABLES = Arrays.asList(
            "EN_CURSO", "INICIADO", "DISPONIBLE", "PROGRAMADO", "CREADO"
    );

    private static final List<String> ESTADOS_RESERVA_ACTIVA = Arrays.asList(
            "ACTIVA", "CONFIRMADA", "PENDIENTE",
            "ESPERANDO_RECOGIDA", "RECOGIDO", "EN_CURSO", "INICIADO"
    );

    private static final java.util.Set<String> ESTADOS_CANCELADOS = new java.util.HashSet<>(
            Arrays.asList("CANCELADO", "CANCELADA")
    );

    private static final int[] COLORES_PASAJEROS = {
            0xFF00ACC1, 0xFF00838F, 0xFF006064, 0xFF0097A7, 0xFF00BCD4, 0xFF4DD0E1
    };
    private static final String[] COLORES_PASAJEROS_HEX = {
            "#00ACC1", "#00838F", "#006064", "#0097A7", "#00BCD4", "#4DD0E1"
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
    private double precioCalculadoPersistente = 0;

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

    private boolean calificacionYaDisparada = false;

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
    private long     lastRenderId      = 0; // Para sincronizar hilos de dibujo asíncronos

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
                pedirSegmentoConductorAParada(nuevaPos, gpParada, lastRenderId);
            } else if (!pasajeroRecogido) {
                GeoPoint puntoSubida = gpSubida != null ? gpSubida : gpOrigen;
                if (puntoSubida != null)
                    pedirSegmentoConductorAParada(nuevaPos, puntoSubida, lastRenderId);
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
        getWindow().setStatusBarColor(
                android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

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

        btnIniciar.setOnClickListener(v -> {
            ViajeAlertaManager.cancelarAlertas(viajeId); // cancela jobs pendientes
            cambiarEstadoViaje("iniciar");
        });        btnFinalizar.setOnClickListener(v -> confirmarFinalizar());
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
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        });
    }


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
    private LinearLayout filaSubidaSeleccionada = null;

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

    private void geocodificarPuntoYNotificar(GeoPoint gp) {
        // Mostrar marcador provisional inmediatamente
        runOnUiThread(() -> {
            gpSubida = gp;
            nombreSubidaPasajero = String.format("%.4f, %.4f", gp.getLatitude(), gp.getLongitude());
            // Actualizar chip provisional
            if (chipSubidaRef != null) {
                chipSubidaRef.setText(" Cargando nombre...");
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
                        chipSubidaRef.setText("" + nomFinal);
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
        inner.addView(crearFilaReserva(ROW_ID_PRECIO,   "$", "Precio total",        "—", d, 0, 0));

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
        cardPasajeros.setCardBackgroundColor(Color.parseColor("#E0F7FA"));
        cardPasajeros.setStrokeColor(Color.parseColor("#80DEEA"));
        cardPasajeros.setStrokeWidth((int)(1.5f * d));
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
        barShape.setColors(new int[]{Color.parseColor("#00BFA0"), Color.parseColor("#00897B")});
        barShape.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
        accentBar.setBackground(barShape);
        fila.addView(accentBar);

        TextView titulo = new TextView(this);
        titulo.setText("PASAJEROS RESERVADOS");
        titulo.setTextSize(10f);
        titulo.setTypeface(null, Typeface.BOLD);
        titulo.setTextColor(Color.parseColor("#006064"));
        titulo.setLetterSpacing(0.16f);
        titulo.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fila.addView(titulo);

        txtTotalPasajeros = new TextView(this);
        txtTotalPasajeros.setTextSize(11f);
        txtTotalPasajeros.setTextColor(Color.parseColor("#006064"));
        txtTotalPasajeros.setTypeface(null, Typeface.BOLD);
        fila.addView(txtTotalPasajeros);
        inner.addView(fila);

        View sep = new View(this);
        LinearLayout.LayoutParams ls = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1*d));
        ls.setMargins(0, p8, 0, p8);
        sep.setLayoutParams(ls);
        sep.setBackgroundColor(Color.parseColor("#80DEEA"));
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

        // ── Precio ──
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

        // ── Card info conductor (foto, calificación, teléfono) ──────────────
        // Solo mostrarla al pasajero; el conductor ya se ve a sí mismo
        if (!esConductor) {
            runOnUiThread(this::crearOActualizarCardConductor);
        }
        // Mostrar banner "Conductor en ruta" si el viaje ya inició y el pasajero tiene reserva
        if (!esConductor && ("EN_CURSO".equals(estadoViaje) || "INICIADO".equals(estadoViaje))) {
            mostrarBannerConductorEnRuta();
        }



        if (esConductor) cargarReservasConductor();
        else             cargarMiReservaPasajero();
    }



    private void crearOActualizarCardConductor() {
        LinearLayout container = findViewById(R.id.container_mi_reserva);
        if (container == null) return;

        // Si ya existe la card, solo actualizarla
        if (cardInfoConductor != null) {
            actualizarCardConductorUI();
            return;
        }

        float d  = getResources().getDisplayMetrics().density;
        int p16  = (int)(16 * d), p14 = (int)(14 * d);
        int p12  = (int)(12 * d), p10 = (int)(10 * d);
        int p8   = (int)(8  * d), p6  = (int)(6  * d);
        int p4   = (int)(4  * d);

        // Separador
        View divisorTop = new View(this);
        LinearLayout.LayoutParams lpDivTop = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d));
        lpDivTop.setMargins(0, p14, 0, p14);
        divisorTop.setLayoutParams(lpDivTop);
        divisorTop.setBackgroundColor(android.graphics.Color.parseColor("#E0F2F1"));
        container.addView(divisorTop, 0);

        // ── Card principal ────────────────────────────────────────────────────
        cardInfoConductor = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardInfoConductor.setLayoutParams(lpCard);
        cardInfoConductor.setRadius(18 * d);
        cardInfoConductor.setCardElevation(4 * d);
        cardInfoConductor.setCardBackgroundColor(android.graphics.Color.WHITE);
        cardInfoConductor.setStrokeWidth((int)(1.5f * d));
        cardInfoConductor.setStrokeColor(android.graphics.Color.parseColor("#E0F2F1"));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p16, p16, p16, p16);

        // ── Encabezado con barra acento ───────────────────────────────────────
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
        android.graphics.drawable.GradientDrawable barShape =
                new android.graphics.drawable.GradientDrawable();
        barShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        barShape.setCornerRadius(4 * d);
        barShape.setColors(new int[]{
                android.graphics.Color.parseColor("#00BFA0"),
                android.graphics.Color.parseColor("#00897B")});
        barShape.setOrientation(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
        accentBar.setBackground(barShape);
        encabezado.addView(accentBar);

        TextView tvTitulo = new TextView(this);
        tvTitulo.setText("CONDUCTOR DEL VIAJE");
        tvTitulo.setTextSize(10f);
        tvTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitulo.setTextColor(android.graphics.Color.parseColor("#00897B"));
        tvTitulo.setLetterSpacing(0.14f);
        tvTitulo.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        encabezado.addView(tvTitulo);

        TextView tvIcono = new TextView(this);
        tvIcono.setText("");
        tvIcono.setTextSize(18f);
        encabezado.addView(tvIcono);
        inner.addView(encabezado);

        // ── Fila avatar + nombre + calificación ───────────────────────────────
        LinearLayout filaAvatar = new LinearLayout(this);
        filaAvatar.setOrientation(LinearLayout.HORIZONTAL);
        filaAvatar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpFA = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFA.setMargins(0, 0, 0, p12);
        filaAvatar.setLayoutParams(lpFA);

        // ── Avatar (foto o inicial) ───────────────────────────────────────────
        android.widget.FrameLayout frameAvatar = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams lpFrame = new LinearLayout.LayoutParams(
                (int)(64*d), (int)(64*d));
        lpFrame.setMargins(0, 0, p14, 0);
        frameAvatar.setLayoutParams(lpFrame);

        // Card foto
        MaterialCardView cardFoto = new MaterialCardView(this);
        cardFoto.setTag("card_foto_conductor");
        android.widget.FrameLayout.LayoutParams lpCF =
                new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        cardFoto.setLayoutParams(lpCF);
        cardFoto.setRadius((int)(32*d));
        cardFoto.setCardElevation(3 * d);
        cardFoto.setStrokeWidth((int)(2*d));
        cardFoto.setStrokeColor(android.graphics.Color.parseColor("#00CED1"));

        android.widget.ImageView ivFoto = new android.widget.ImageView(this);
        ivFoto.setTag("iv_foto_conductor");
        ivFoto.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        cardFoto.addView(ivFoto);
        cardFoto.setVisibility(View.GONE);
        frameAvatar.addView(cardFoto);

        // Card inicial (fallback)
        MaterialCardView cardInicial = new MaterialCardView(this);
        cardInicial.setTag("card_inicial_conductor");
        cardInicial.setLayoutParams(lpCF);
        cardInicial.setRadius((int)(32*d));
        cardInicial.setCardElevation(3 * d);
        android.graphics.drawable.GradientDrawable bgInicial =
                new android.graphics.drawable.GradientDrawable();
        bgInicial.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bgInicial.setColors(new int[]{
                android.graphics.Color.parseColor("#00CED1"),
                android.graphics.Color.parseColor("#00897B")});
        bgInicial.setOrientation(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR);

        TextView tvInicial = new TextView(this);
        tvInicial.setTag("tv_inicial_conductor");
        tvInicial.setTextSize(24f);
        tvInicial.setTypeface(null, android.graphics.Typeface.BOLD);
        tvInicial.setTextColor(android.graphics.Color.WHITE);
        tvInicial.setGravity(android.view.Gravity.CENTER);
        tvInicial.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        // Determinar inicial
        String nombreInit = nombreConductorViaje != null && !nombreConductorViaje.isEmpty()
                && !nombreConductorViaje.startsWith("Conductor #")
                ? String.valueOf(nombreConductorViaje.charAt(0)).toUpperCase()
                : "C";
        tvInicial.setText(nombreInit);
        cardInicial.setBackground(bgInicial);
        cardInicial.addView(tvInicial);
        frameAvatar.addView(cardInicial);
        filaAvatar.addView(frameAvatar);

        // ── Columna nombre + calificación + estado ────────────────────────────
        LinearLayout colDatos = new LinearLayout(this);
        colDatos.setOrientation(LinearLayout.VERTICAL);
        colDatos.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Nombre
        TextView tvNombreCond = new TextView(this);
        tvNombreCond.setTag("tv_nombre_conductor_card");
        String nomMostrar = (nombreConductorViaje != null && !nombreConductorViaje.isEmpty()
                && !nombreConductorViaje.startsWith("Conductor #"))
                ? nombreConductorViaje : "Cargando...";
        tvNombreCond.setText(nomMostrar);
        tvNombreCond.setTextSize(16f);
        tvNombreCond.setTypeface(null, android.graphics.Typeface.BOLD);
        tvNombreCond.setTextColor(android.graphics.Color.parseColor("#004D40"));
        tvNombreCond.setMaxLines(1);
        tvNombreCond.setEllipsize(android.text.TextUtils.TruncateAt.END);
        colDatos.addView(tvNombreCond);

        // Chip "Conductor"
        TextView chipRol = new TextView(this);
        chipRol.setText("Conductor");
        chipRol.setTextSize(11f);
        chipRol.setTextColor(android.graphics.Color.parseColor("#00897B"));
        chipRol.setTypeface(null, android.graphics.Typeface.BOLD);
        chipRol.setPadding(p8, (int)(3*d), p8, (int)(3*d));
        android.graphics.drawable.GradientDrawable bgChip =
                new android.graphics.drawable.GradientDrawable();
        bgChip.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bgChip.setCornerRadius(20 * d);
        bgChip.setColor(android.graphics.Color.parseColor("#E0F7FA"));
        bgChip.setStroke((int)(1*d), android.graphics.Color.parseColor("#80CBC4"));
        chipRol.setBackground(bgChip);
        LinearLayout.LayoutParams lpChip = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpChip.topMargin = (int)(4*d);
        chipRol.setLayoutParams(lpChip);
        colDatos.addView(chipRol);

        // Estrellas + promedio
        LinearLayout filaEstrellas = new LinearLayout(this);
        filaEstrellas.setOrientation(LinearLayout.HORIZONTAL);
        filaEstrellas.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpFE = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFE.topMargin = (int)(6*d);
        filaEstrellas.setLayoutParams(lpFE);
        filaEstrellas.setTag("fila_estrellas_conductor");

        // Placeholder estrellas (se actualizan al cargar)
        for (int i = 0; i < 5; i++) {
            TextView tvStar = new TextView(this);
            tvStar.setText("★");
            tvStar.setTextSize(14f);
            tvStar.setTextColor(android.graphics.Color.parseColor("#CFD8DC"));
            filaEstrellas.addView(tvStar);
        }

        TextView tvPromedio = new TextView(this);
        tvPromedio.setTag("tv_promedio_conductor");
        tvPromedio.setText("  —");
        tvPromedio.setTextSize(13f);
        tvPromedio.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPromedio.setTextColor(android.graphics.Color.parseColor("#546E7A"));
        filaEstrellas.addView(tvPromedio);
        colDatos.addView(filaEstrellas);

        filaAvatar.addView(colDatos);
        inner.addView(filaAvatar);

        // ── Separador ─────────────────────────────────────────────────────────
        View sep = new View(this);
        LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1*d));
        lpSep.setMargins(0, 0, 0, p12);
        sep.setLayoutParams(lpSep);
        sep.setBackgroundColor(android.graphics.Color.parseColor("#E0F2F1"));
        inner.addView(sep);

        // ── Fila teléfono ─────────────────────────────────────────────────────
        LinearLayout filaTelefono = new LinearLayout(this);
        filaTelefono.setOrientation(LinearLayout.HORIZONTAL);
        filaTelefono.setGravity(android.view.Gravity.CENTER_VERTICAL);
        filaTelefono.setTag("fila_telefono_conductor");
        filaTelefono.setVisibility(View.GONE);  // oculta hasta que cargue
        LinearLayout.LayoutParams lpFT = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFT.setMargins(0, 0, 0, p8);
        filaTelefono.setLayoutParams(lpFT);

        android.widget.FrameLayout iconTelFrame = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams lpIconFrame = new LinearLayout.LayoutParams(
                (int)(38*d), (int)(38*d));
        lpIconFrame.setMargins(0, 0, p12, 0);
        iconTelFrame.setLayoutParams(lpIconFrame);
        android.graphics.drawable.GradientDrawable bgIconTel =
                new android.graphics.drawable.GradientDrawable();
        bgIconTel.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bgIconTel.setColor(android.graphics.Color.parseColor("#E0F7FA"));
        bgIconTel.setStroke((int)(1*d), android.graphics.Color.parseColor("#B2EBF2"));
        iconTelFrame.setBackground(bgIconTel);

        TextView tvIconTel = new TextView(this);
        tvIconTel.setText("📞");
        tvIconTel.setTextSize(16f);
        tvIconTel.setGravity(android.view.Gravity.CENTER);
        tvIconTel.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        iconTelFrame.addView(tvIconTel);
        filaTelefono.addView(iconTelFrame);

        LinearLayout colTel = new LinearLayout(this);
        colTel.setOrientation(LinearLayout.VERTICAL);
        colTel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvLabelTel = new TextView(this);
        tvLabelTel.setText("TELÉFONO");
        tvLabelTel.setTextSize(9f);
        tvLabelTel.setTextColor(android.graphics.Color.parseColor("#80CBC4"));
        tvLabelTel.setAllCaps(true);
        tvLabelTel.setLetterSpacing(0.1f);
        colTel.addView(tvLabelTel);

        TextView tvTelefonoCond = new TextView(this);
        tvTelefonoCond.setTag("tv_telefono_conductor_card");
        tvTelefonoCond.setText("—");
        tvTelefonoCond.setTextSize(14f);
        tvTelefonoCond.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTelefonoCond.setTextColor(android.graphics.Color.parseColor("#004D40"));
        LinearLayout.LayoutParams lpTV = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTV.topMargin = (int)(2*d);
        tvTelefonoCond.setLayoutParams(lpTV);
        colTel.addView(tvTelefonoCond);
        filaTelefono.addView(colTel);

        // Botón llamar
        com.google.android.material.button.MaterialButton btnLlamar =
                new com.google.android.material.button.MaterialButton(this);
        btnLlamar.setTag("btn_llamar_conductor");
        btnLlamar.setText("Llamar");
        btnLlamar.setTextSize(12f);
        btnLlamar.setTextColor(android.graphics.Color.WHITE);
        btnLlamar.setCornerRadius((int)(20*d));
        btnLlamar.setBackgroundColor(android.graphics.Color.parseColor("#00897B"));
        LinearLayout.LayoutParams lpLlamar = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (int)(36*d));
        lpLlamar.setMargins(p8, 0, 0, 0);
        btnLlamar.setLayoutParams(lpLlamar);
        btnLlamar.setInsetTop(0); btnLlamar.setInsetBottom(0);
        filaTelefono.addView(btnLlamar);
        inner.addView(filaTelefono);

        // ── Fila calificaciones detalle ───────────────────────────────────────
        LinearLayout filaCalif = new LinearLayout(this);
        filaCalif.setOrientation(LinearLayout.HORIZONTAL);
        filaCalif.setGravity(android.view.Gravity.CENTER_VERTICAL);
        filaCalif.setTag("fila_calif_conductor");
        filaCalif.setVisibility(View.GONE);
        LinearLayout.LayoutParams lpFC = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        filaCalif.setLayoutParams(lpFC);

        android.widget.FrameLayout iconCalFrame = new android.widget.FrameLayout(this);
        iconCalFrame.setLayoutParams(lpIconFrame);
        android.graphics.drawable.GradientDrawable bgIconCal =
                new android.graphics.drawable.GradientDrawable();
        bgIconCal.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bgIconCal.setColor(android.graphics.Color.parseColor("#FFF8E1"));
        bgIconCal.setStroke((int)(1*d), android.graphics.Color.parseColor("#FFD54F"));
        iconCalFrame.setBackground(bgIconCal);

        TextView tvIconCal = new TextView(this);
        tvIconCal.setText("⭐");
        tvIconCal.setTextSize(16f);
        tvIconCal.setGravity(android.view.Gravity.CENTER);
        tvIconCal.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        iconCalFrame.addView(tvIconCal);
        filaCalif.addView(iconCalFrame);

        LinearLayout colCal = new LinearLayout(this);
        colCal.setOrientation(LinearLayout.VERTICAL);
        colCal.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvLabelCal = new TextView(this);
        tvLabelCal.setText("CALIFICACIÓN");
        tvLabelCal.setTextSize(9f);
        tvLabelCal.setTextColor(android.graphics.Color.parseColor("#80CBC4"));
        tvLabelCal.setAllCaps(true);
        tvLabelCal.setLetterSpacing(0.1f);
        colCal.addView(tvLabelCal);

        TextView tvCalDetalle = new TextView(this);
        tvCalDetalle.setTag("tv_calif_detalle_conductor");
        tvCalDetalle.setText("Sin calificaciones aún");
        tvCalDetalle.setTextSize(13f);
        tvCalDetalle.setTextColor(android.graphics.Color.parseColor("#546E7A"));
        LinearLayout.LayoutParams lpTVCal = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTVCal.topMargin = (int)(2*d);
        tvCalDetalle.setLayoutParams(lpTVCal);
        colCal.addView(tvCalDetalle);
        filaCalif.addView(colCal);
        inner.addView(filaCalif);

        cardInfoConductor.addView(inner);
        container.addView(cardInfoConductor, 0);

        // Cargar datos del conductor via API
        if (idConductorViaje > 0) {
            cargarDatosCompletosDelConductor(idConductorViaje);
        }
    }

    private void mostrarBannerConductorEnRuta() {
        // Buscar si ya existe el banner
        LinearLayout container = findViewById(R.id.container_mi_reserva);
        if (container == null) return;

        // Evitar duplicados
        if (container.findViewWithTag("banner_en_ruta") != null) return;

        float d = getResources().getDisplayMetrics().density;
        int p12 = (int)(12*d), p10 = (int)(10*d), p8 = (int)(8*d);

        com.google.android.material.card.MaterialCardView banner =
                new com.google.android.material.card.MaterialCardView(this);
        banner.setTag("banner_en_ruta");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, p12);
        banner.setLayoutParams(lp);
        banner.setRadius(14 * d);
        banner.setCardElevation(3 * d);
        banner.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"));
        banner.setStrokeWidth((int)(1.5f * d));
        banner.setStrokeColor(android.graphics.Color.parseColor("#A5D6A7"));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(android.view.Gravity.CENTER_VERTICAL);
        inner.setPadding(p12, p10, p12, p10);

        // Punto verde pulsante
        android.widget.FrameLayout dotFrame = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams lpDot = new LinearLayout.LayoutParams(
                (int)(12*d), (int)(12*d));
        lpDot.setMargins(0, 0, p10, 0);
        dotFrame.setLayoutParams(lpDot);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(android.graphics.Color.parseColor("#4CAF50"));
        dotFrame.setBackground(dot);

        // Animación pulso
        android.animation.ObjectAnimator pulso = android.animation.ObjectAnimator
                .ofFloat(dotFrame, "alpha", 1f, 0.2f);
        pulso.setDuration(800);
        pulso.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        pulso.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
        pulso.start();
        inner.addView(dotFrame);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.TextView tvTitulo = new android.widget.TextView(this);
        tvTitulo.setText("Conductor en ruta");
        tvTitulo.setTextSize(14f);
        tvTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitulo.setTextColor(android.graphics.Color.parseColor("#1B5E20"));
        col.addView(tvTitulo);

        android.widget.TextView tvSub = new android.widget.TextView(this);
        tvSub.setText("Prepárate en tu punto de recogida");
        tvSub.setTextSize(12f);
        tvSub.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSub.topMargin = (int)(2*d);
        tvSub.setLayoutParams(lpSub);
        col.addView(tvSub);
        inner.addView(col);

        banner.addView(inner);
        container.addView(banner, 0); // agregar al inicio
    }

    /**
     * Actualiza los TextViews de la card del conductor cuando ya existe.
     */
    private void actualizarCardConductorUI() {
        if (cardInfoConductor == null) return;

        // Actualizar nombre
        View tvNom = cardInfoConductor.findViewWithTag("tv_nombre_conductor_card");
        if (tvNom instanceof TextView) {
            String nom = (nombreConductorViaje != null && !nombreConductorViaje.isEmpty()
                    && !nombreConductorViaje.startsWith("Conductor #"))
                    ? nombreConductorViaje : "—";
            ((TextView) tvNom).setText(nom);

            // Actualizar inicial del avatar
            View tvIni = cardInfoConductor.findViewWithTag("tv_inicial_conductor");
            if (tvIni instanceof TextView && !nom.equals("—"))
                ((TextView) tvIni).setText(String.valueOf(nom.charAt(0)).toUpperCase());
        }

        // Recargar datos si tenemos id
        if (idConductorViaje > 0)
            cargarDatosCompletosDelConductor(idConductorViaje);
    }

    /**
     * Carga foto, teléfono y calificación del conductor desde el backend
     * y actualiza la card dinámicamente.
     */
    private void cargarDatosCompletosDelConductor(int idCond) {
        if (idCond <= 0 || cardInfoConductor == null) return;

        // ── 1. Cargar perfil (foto + teléfono) ───────────────────────────────
        String[] endpointsPerfil = {
                Constantes.authPorId((long) idCond),
                Constantes.USUARIOS + "/" + idCond,
                Constantes.BASE_URL + "/api/usuarios/" + idCond
        };
        cargarPerfilConductorDesdeEndpoints(endpointsPerfil, 0);

        // ── 2. Cargar calificación promedio ───────────────────────────────────
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.calificacionPromedio((long) idCond),
                promedioObj -> {
                    double prom = 0;
                    int total   = 0;
                    JSONObject anidado = promedioObj.optJSONObject("promedio");
                    if (anidado != null) {
                        prom  = anidado.optDouble("promedio", 0);
                        total = anidado.optInt("total", 0);
                    } else {
                        prom  = promedioObj.optDouble("promedio",
                                promedioObj.optDouble("average",
                                        promedioObj.optDouble("calificacionPromedio", 0)));
                        total = promedioObj.optInt("total",
                                promedioObj.optInt("count",
                                        promedioObj.optInt("totalCalificaciones", 0)));
                    }
                    final double fProm  = prom;
                    final int    fTotal = total;
                    runOnUiThread(() -> actualizarEstrellasConductor(fProm, fTotal));
                },
                err -> android.util.Log.w(TAG, "No se pudo cargar calificación conductor id=" + idCond)
        );
    }

    private void cargarPerfilConductorDesdeEndpoints(String[] endpoints, int idx) {
        if (idx >= endpoints.length || cardInfoConductor == null) return;

        ConexionApi.getInstance(this).getObject(endpoints[idx],
                perfil -> {
                    android.util.Log.d(TAG, "Perfil conductor endpoint[" + idx + "]: "
                            + perfil.toString().substring(0, Math.min(300, perfil.toString().length())));

                    // Extraer nombre
                    String nom = extraerNombreDePerfil(perfil);
                    if (!nom.isEmpty() && (nombreConductorViaje == null
                            || nombreConductorViaje.isEmpty()
                            || nombreConductorViaje.startsWith("Conductor #"))) {
                        nombreConductorViaje = nom;
                        runOnUiThread(() -> {
                            actualizarNombreConductorUI();
                            actualizarBotonChat();
                            View tvNom = cardInfoConductor.findViewWithTag("tv_nombre_conductor_card");
                            if (tvNom instanceof TextView) ((TextView) tvNom).setText(nom);
                            View tvIni = cardInfoConductor.findViewWithTag("tv_inicial_conductor");
                            if (tvIni instanceof TextView && !nom.isEmpty())
                                ((TextView) tvIni).setText(String.valueOf(nom.charAt(0)).toUpperCase());
                        });
                    }

                    // Extraer foto
                    String foto = extraerFotoDePerfil(perfil);

                    // Extraer teléfono
                    String tel = extraerTelefonoDePerfil(perfil);

                    runOnUiThread(() -> {
                        if (!foto.isEmpty()) aplicarFotoCondcutorEnCard(foto);
                        if (!tel.isEmpty()) mostrarTelefonoEnCard(tel);
                        else if (idx + 1 < new String[]{}.length) {
                            // si no encontró datos, intenta siguiente endpoint
                        }
                    });

                    // Si no encontró teléfono ni foto, intentar siguiente endpoint
                    if (foto.isEmpty() && tel.isEmpty() && idx + 1 < endpoints.length) {
                        cargarPerfilConductorDesdeEndpoints(endpoints, idx + 1);
                    }
                },
                err -> {
                    int code = (err != null && err.networkResponse != null)
                            ? err.networkResponse.statusCode : 0;
                    android.util.Log.w(TAG, "Perfil conductor endpoint[" + idx
                            + "] falló código=" + code);
                    cargarPerfilConductorDesdeEndpoints(endpoints, idx + 1);
                }
        );
    }

    private String extraerNombreDePerfil(JSONObject o) {
        if (o == null) return "";
        for (String k : new String[]{"nombre","nombreCompleto","name","fullName","nombreUsuario","displayName"}) {
            String v = o.optString(k, "");
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        String n = o.optString("nombres", o.optString("primerNombre", ""));
        String a = o.optString("apellidos", o.optString("primerApellido", ""));
        if (!n.isEmpty() || !a.isEmpty()) return (n + " " + a).trim();
        for (String sub : new String[]{"usuario","persona","perfil","data"}) {
            JSONObject obj = o.optJSONObject(sub);
            if (obj != null) {
                for (String k : new String[]{"nombre","nombreCompleto","name"}) {
                    String v = obj.optString(k, "");
                    if (!v.isEmpty() && !v.equals("null")) return v;
                }
                String sn = obj.optString("nombres","");
                String sa = obj.optString("apellidos","");
                if (!sn.isEmpty() || !sa.isEmpty()) return (sn + " " + sa).trim();
            }
        }
        return "";
    }

    private String extraerFotoDePerfil(JSONObject o) {
        if (o == null) return "";
        for (String k : new String[]{"fotoPerfi","fotoPerfil","foto","photoUrl","profilePicture","avatar","imagenPerfil","urlFoto"}) {
            String v = o.optString(k, "");
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        for (String sub : new String[]{"usuario","persona","perfil"}) {
            JSONObject obj = o.optJSONObject(sub);
            if (obj != null) {
                for (String k : new String[]{"fotoPerfi","fotoPerfil","foto","photoUrl","avatar"}) {
                    String v = obj.optString(k, "");
                    if (!v.isEmpty() && !v.equals("null")) return v;
                }
            }
        }
        return "";
    }

    private String extraerTelefonoDePerfil(JSONObject o) {
        if (o == null) return "";
        for (String k : new String[]{"telefono","celular","phone","phoneNumber","numeroTelefono","movil","cel"}) {
            String v = o.optString(k, "");
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        for (String sub : new String[]{"usuario","persona","perfil"}) {
            JSONObject obj = o.optJSONObject(sub);
            if (obj != null) {
                for (String k : new String[]{"telefono","celular","phone","phoneNumber","movil"}) {
                    String v = obj.optString(k, "");
                    if (!v.isEmpty() && !v.equals("null")) return v;
                }
            }
        }
        return "";
    }

    private void aplicarFotoCondcutorEnCard(String fotoUrl) {
        if (cardInfoConductor == null || fotoUrl == null || fotoUrl.isEmpty()) return;

        View cardFotoV   = cardInfoConductor.findViewWithTag("card_foto_conductor");
        View cardInicialV= cardInfoConductor.findViewWithTag("card_inicial_conductor");
        View ivFotoV     = cardInfoConductor.findViewWithTag("iv_foto_conductor");

        if (cardFotoV instanceof MaterialCardView && ivFotoV instanceof android.widget.ImageView) {
            MaterialCardView cardFoto = (MaterialCardView) cardFotoV;
            android.widget.ImageView ivFoto = (android.widget.ImageView) ivFotoV;
            cardFoto.setVisibility(View.VISIBLE);
            if (cardInicialV != null) cardInicialV.setVisibility(View.GONE);
            com.bumptech.glide.Glide.with(this)
                    .load(fotoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.logomo)
                    .error(R.drawable.logomo)
                    .into(ivFoto);
        }
    }

    private void mostrarTelefonoEnCard(String telefono) {
        if (cardInfoConductor == null) return;
        View filaV   = cardInfoConductor.findViewWithTag("fila_telefono_conductor");
        View tvTelV  = cardInfoConductor.findViewWithTag("tv_telefono_conductor_card");
        View btnLlamV= cardInfoConductor.findViewWithTag("btn_llamar_conductor");

        if (filaV != null) filaV.setVisibility(View.VISIBLE);
        if (tvTelV instanceof TextView) ((TextView) tvTelV).setText(telefono);
        if (btnLlamV instanceof com.google.android.material.button.MaterialButton) {
            ((com.google.android.material.button.MaterialButton) btnLlamV)
                    .setOnClickListener(v -> {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL,
                                android.net.Uri.parse("tel:" + telefono));
                        startActivity(callIntent);
                    });
        }

        // Mostrar también la fila de calificación junto al teléfono
        View filaCalV = cardInfoConductor.findViewWithTag("fila_calif_conductor");
        if (filaCalV != null) filaCalV.setVisibility(View.VISIBLE);
    }

    private void actualizarEstrellasConductor(double promedio, int total) {
        if (cardInfoConductor == null) return;

        // Actualizar texto promedio
        View tvPromV = cardInfoConductor.findViewWithTag("tv_promedio_conductor");
        if (tvPromV instanceof TextView) {
            String texto = promedio > 0
                    ? String.format("  %.1f (%d %s)", promedio, total,
                    total == 1 ? "cal." : "cals.")
                    : "  Sin calificaciones";
            ((TextView) tvPromV).setText(texto);
        }

        // Actualizar detalle en fila calificación
        View tvCalV = cardInfoConductor.findViewWithTag("tv_calif_detalle_conductor");
        if (tvCalV instanceof TextView) {
            ((TextView) tvCalV).setText(promedio > 0
                    ? String.format("%.1f / 5.0  ·  %d calificaciones", promedio, total)
                    : "Sin calificaciones aún");
        }

        // Actualizar estrellas en fila
        View filaE = cardInfoConductor.findViewWithTag("fila_estrellas_conductor");
        if (!(filaE instanceof LinearLayout)) return;
        LinearLayout filaEstrellas = (LinearLayout) filaE;

        int llenas   = (int) promedio;
        boolean media = (promedio - llenas) >= 0.25 && (promedio - llenas) < 0.75;
        int totalL   = (promedio - llenas) >= 0.75 ? llenas + 1 : llenas;

        // Actualizar colores de las 5 estrellas (los primeros 5 hijos son las estrellas)
        int starCount = 0;
        for (int i = 0; i < filaEstrellas.getChildCount() && starCount < 5; i++) {
            View child = filaEstrellas.getChildAt(i);
            if (child instanceof TextView) {
                starCount++;
                int color = starCount <= totalL
                        ? android.graphics.Color.parseColor("#FFC107")
                        : android.graphics.Color.parseColor("#CFD8DC");
                ((TextView) child).setTextColor(color);
            }
        }

        // Mostrar fila calificación
        View filaCalV = cardInfoConductor.findViewWithTag("fila_calif_conductor");
        if (filaCalV != null) filaCalV.setVisibility(View.VISIBLE);
    }

    private void cargarNombreConductorPorId(int id) {
        if (id <= 0) return;

        // Intentar primero con el endpoint de perfil/auth
        String[] endpoints = {
                Constantes.USUARIOS + "/" + id,
                Constantes.BASE_URL + "/api/auth/usuarios/" + id,
                Constantes.BASE_URL + "/api/usuarios/" + id,
                Constantes.authPorId((long) id)
        };

        intentarCargarNombreDesdeEndpoints(endpoints, 0);
    }

    private void intentarCargarNombreDesdeEndpoints(String[] endpoints, int indice) {
        if (indice >= endpoints.length) {
            // Todos fallaron: mostrar id como fallback
            nombreConductorViaje = "Conductor #" + (idConductorViaje > 0 ? idConductorViaje : "?");
            runOnUiThread(() -> { actualizarNombreConductorUI(); actualizarBotonChat(); });
            return;
        }

        ConexionApi.getInstance(this).getObject(endpoints[indice],
                perfil -> {
                    // Log para ver qué llega
                    Log.d(TAG, "cargarNombre endpoint[" + indice + "]: " + perfil.toString().substring(0, Math.min(300, perfil.toString().length())));

                    String nom = extraerNombreDePerfilCompleto(perfil);
                    if (!nom.isEmpty()) {
                        nombreConductorViaje = nom;
                        runOnUiThread(() -> { actualizarNombreConductorUI(); actualizarBotonChat(); });
                    } else {
                        // Intentar el siguiente endpoint
                        intentarCargarNombreDesdeEndpoints(endpoints, indice + 1);
                    }
                },
                err -> {
                    int code = (err != null && err.networkResponse != null) ? err.networkResponse.statusCode : 0;
                    Log.w(TAG, "cargarNombre endpoint[" + indice + "] falló, código=" + code);
                    intentarCargarNombreDesdeEndpoints(endpoints, indice + 1);
                }
        );
    }

    private String extraerNombreDePerfilCompleto(JSONObject perfil) {
        if (perfil == null) return "";

        // Buscar directo
        for (String k : new String[]{"nombre","nombreCompleto","name","fullName","nombreUsuario","displayName"}) {
            String v = perfil.optString(k, "");
            if (!v.isEmpty() && !v.equals("null")) return v;
        }

        // nombres + apellidos
        String n = perfil.optString("nombres", perfil.optString("primerNombre", ""));
        String a = perfil.optString("apellidos", perfil.optString("primerApellido", ""));
        if (!n.isEmpty() || !a.isEmpty()) return (n + " " + a).trim();

        // Sub-objeto usuario
        for (String sub : new String[]{"usuario", "persona", "perfil", "conductor", "data"}) {
            JSONObject obj = perfil.optJSONObject(sub);
            if (obj != null) {
                for (String k : new String[]{"nombre","nombreCompleto","name"}) {
                    String v = obj.optString(k, "");
                    if (!v.isEmpty() && !v.equals("null")) return v;
                }
                String sn = obj.optString("nombres","");
                String sa = obj.optString("apellidos","");
                if (!sn.isEmpty() || !sa.isEmpty()) return (sn + " " + sa).trim();
            }
        }
        return "";
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
        lastRenderId++;
        final long currentId = lastRenderId;

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
            renderizarMapaPasajero(viajeIniciado, currentId);
        } else {
            if (viajeIniciado) {
                renderizarMapaConductorIniciado(currentId);
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


    private void renderizarMapaPasajero(boolean viajeIniciado, long requestId) {
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
                pedirRutaConWaypoint(requestId);
            }

            // ── Conductor: mover suavemente, no recrear ──
            if (gpConductorActual != null) {
                if (marcadorConductor == null || !map.getOverlays().contains(marcadorConductor)) {
                    actualizarMarcadorConductorSuave(gpConductorActual);
                }
                if (gpParada != null)
                    pedirSegmentoConductorAParada(gpConductorActual, gpParada, requestId);
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
            if (gpSubida != null)
                pedirSegmentoConductorAParada(gpConductorActual, gpSubida, requestId);
            else if (gpParada != null)
                pedirSegmentoConductorAParada(gpConductorActual, gpParada, requestId);
        }
    }

    private void pedirRutaConWaypoint(long requestId) {
        if (gpOrigen == null || gpDestino == null) return;
        new Thread(() -> {
            try {
                String seg = lngOrigen + "," + latOrigen + ";"
                        + lngDestino + "," + latDestino
                        + "?overview=full&geometries=geojson";
                ArrayList<GeoPoint> pts = null;
                try { pts = parsearRutaSimpleOSRM(peticionHttp(OSRM_URL + "/route/v1/driving/" + seg)); }
                catch (Exception ignored) {}
                if (pts == null || pts.size() < 2)
                    try { pts = parsearRutaSimpleOSRM(peticionHttp(OSRM_URL_PUBLIC + "/route/v1/driving/" + seg)); }
                    catch (Exception ignored) {}
                if (pts != null && pts.size() >= 2) {
                    if (lastRenderId != requestId) return; // Validación de hilo
                    puntosRutaWaypoint = pts;
                    final ArrayList<GeoPoint> fPts = pts;
                    runOnUiThread(() -> {
                        if (lastRenderId == requestId) {
                            dibujarPolilineaWaypoint(fPts);
                            map.invalidate();
                        }
                    });
                }
            } catch (Exception e) { Log.w(TAG, "pedirRutaConWaypoint: " + e.getMessage()); }
        }).start();
    }

    private void renderizarMapaConductorIniciado(long requestId) {
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
            pedirSegmentoConductorADestino(posConductor, requestId);
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
                pedirSegmentoConductorAParada(posConductor, primeraParada, requestId);
        }
    }
    private void pedirSegmentoConductorAParada(GeoPoint desde, GeoPoint hasta, long requestId) {
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
                    if (lastRenderId != requestId) return; // Validación de hilo
                    final ArrayList<GeoPoint> fPts = pts;
                    runOnUiThread(() -> {
                        if (lastRenderId == requestId) {
                            dibujarPolilineaSegmento(fPts);
                            map.invalidate();
                        }
                    });
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

        // Si están invertidos u ocurren en el mismo punto, intentar forzarlos a avanzar
        if (idxDesde >= idxHasta) {
            // Buscamos un índice válido para "hasta" más adelante en la ruta
            int newIdxHasta = indiceMasCercanoDespuesDe(rutaActiva, hasta, idxDesde);
            if (newIdxHasta > idxDesde) {
                idxHasta = newIdxHasta;
            } else {
                return resultado; // No hay forma lógica de avanzar
            }
        }

        // Extraer hasta 8 waypoints intermedios equiespaciados para asegurar un calcado perfecto
        int rango = idxHasta - idxDesde;
        if (rango <= 2) return resultado; // muy cerca, no hace falta

        int numWp = Math.min(8, rango - 1); // Aumentado a 8 para mejor precisión
        double paso = (double) rango / (numWp + 1);
        for (int i = 1; i <= numWp; i++) {
            int idx = idxDesde + (int)(i * paso);
            if (idx > idxDesde && idx < idxHasta) {
                resultado.add(rutaActiva.get(idx));
            }
        }
        return resultado;
    }

    private int indiceMasCercanoDespuesDe(List<GeoPoint> ruta, GeoPoint punto, int idxMinimo) {
        int mejor = -1;
        double menorDist = Double.MAX_VALUE;
        for (int i = idxMinimo; i < ruta.size(); i++) {
            GeoPoint p = ruta.get(i);
            double dLat = p.getLatitude()  - punto.getLatitude();
            double dLng = p.getLongitude() - punto.getLongitude();
            double dist = dLat * dLat + dLng * dLng;
            if (dist < menorDist) { menorDist = dist; mejor = i; }
        }
        return mejor;
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

    private void pedirSegmentoConductorADestino(GeoPoint desde, long requestId) {
        if (desde == null || gpDestino == null) return;
        
        final ArrayList<GeoPoint> waypoints = extraerWaypointsEntrePuntos(desde, gpDestino);
        
        new Thread(() -> {
            try {
                StringBuilder coordsB = new StringBuilder();
                coordsB.append(desde.getLongitude()).append(",").append(desde.getLatitude());
                for (GeoPoint wp : waypoints) {
                    coordsB.append(";").append(wp.getLongitude()).append(",").append(wp.getLatitude());
                }
                coordsB.append(";").append(lngDestino).append(",").append(latDestino);
                String params = "?overview=full&geometries=geojson";
                
                ArrayList<GeoPoint> pts = null;
                
                // Fallback directo si no hay waypoints
                if (waypoints.isEmpty()) {
                    String seg = desde.getLongitude() + "," + desde.getLatitude() + ";"
                            + lngDestino + "," + latDestino + params;
                    try { pts = parsearRutaSimpleOSRM(peticionHttp(OSRM_URL + "/route/v1/driving/" + seg)); } catch (Exception ignored) {}
                    if (pts == null || pts.size() < 2)
                        try { pts = parsearRutaSimpleOSRM(peticionHttp(OSRM_URL_PUBLIC + "/route/v1/driving/" + seg)); } catch (Exception ignored) {}
                } else {
                    String urlConWp = OSRM_URL + "/route/v1/driving/" + coordsB + params;
                    try { pts = parsearRutaSimpleOSRM(peticionHttp(urlConWp)); } catch (Exception ignored) {}
                    if (pts == null || pts.size() < 2) {
                        String urlConWpPub = OSRM_URL_PUBLIC + "/route/v1/driving/" + coordsB + params;
                        try { pts = parsearRutaSimpleOSRM(peticionHttp(urlConWpPub)); } catch (Exception ignored) {}
                    }
                }

                if (pts != null && pts.size() >= 2) {
                    if (lastRenderId != requestId) return; // Validación de hilo
                    puntosRutaWaypoint = pts;
                    final ArrayList<GeoPoint> fPts = pts;
                    runOnUiThread(() -> {
                        if (lastRenderId == requestId) {
                            dibujarPolilineaSegmento(fPts);
                            map.invalidate();
                        }
                    });
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

    private void pedirSegmentoConductorAParada(GeoPoint desde, GeoPoint hasta) {
        pedirSegmentoConductorAParada(desde, hasta, lastRenderId);
    }

    private void pedirSegmentoConductorADestino(GeoPoint desde) {
        pedirSegmentoConductorADestino(desde, lastRenderId);
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

                            // ── Precio guardado en la reserva ──
                            double precioGuardado = u.optDouble("precioFinal",
                                    u.optDouble("precio",
                                            u.optDouble("costoPorPasajero", 0)));

                            final double fLatP = latP, fLngP = lngP;
                            final String fNomP = nomP.isEmpty() ? destinoActual : nomP;
                            final double fLatS = latS, fLngS = lngS;
                            final String fNomS = nomS;
                            final int    fIdR  = idR;
                            final double fPrecioGuardado = precioGuardado;
                            final JSONObject uFinal = u;

                            runOnUiThread(() -> {
                                yaReservo       = true;
                                idReservaActual = fIdR;
                                estadoReserva   = est;

                                // Asignar punto de bajada
                                if (fLatP != 0) {
                                    gpParada     = new GeoPoint(fLatP, fLngP);
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

                                // ── Restaurar precio calculado persistente ──────────
                                // Prioridad: 1) ya calculado en esta sesión
                                //            2) precio guardado en la reserva del backend
                                //            3) recalcular desde tramo
                                if (precioCalculadoPersistente > 0) {
                                    // Ya se calculó antes en esta sesión, reusar
                                    precioCalculadoPasajero = precioCalculadoPersistente;
                                    actualizarValorFila(ROW_ID_PRECIO,
                                            String.format(java.util.Locale.getDefault(),
                                                    "$ %,.0f COP", precioCalculadoPersistente));
                                } else if (fPrecioGuardado > 0) {
                                    // Precio que vino del backend en la reserva
                                    precioCalculadoPersistente = fPrecioGuardado;
                                    precioCalculadoPasajero    = fPrecioGuardado;
                                    actualizarValorFila(ROW_ID_PRECIO,
                                            String.format(java.util.Locale.getDefault(),
                                                    "$ %,.0f COP", fPrecioGuardado));
                                } else if (gpParada != null) {
                                    // Sin precio guardado: recalcular desde el tramo
                                    calcularYMostrarPrecioTramo();
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
        double precioMostrar = precioCalculadoPersistente > 0
                ? precioCalculadoPersistente
                : (precioCalculadoPasajero > 0 ? precioCalculadoPasajero : pre);        actualizarValorFila(ROW_ID_PRECIO, precioMostrar > 0
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
                double montoFinal = precioViaje;
                if (precioCalculadoPasajero > 0) montoFinal = precioCalculadoPasajero;
                else if (precioCalculadoPersistente > 0) montoFinal = precioCalculadoPersistente;
                
                final double finalM = montoFinal;
                mostrarBotonPago(finalM, nombreConductorViaje);
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

        // ── Referencia final para usar en lambda ──────────────────────────────
        final ParadaDinamica fPd = pd;  // ← ESTA LÍNEA

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
            if (pd.idParadaBD > 0) {
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
                            response.optInt("idReserva",
                                    response.optInt("id",
                                            response.optInt("reservaId", -1))));
                    estadoReserva = response.optString("estado", EST_CONFIRMADA).toUpperCase();
                    yaReservo     = true;

                    // Usar fPd (final) en lugar de pd
                    gpParada     = fPd.toGeoPoint();
                    nombreParada = fPd.nombre;
                    gpSubida     = null;
                    nombreSubidaPasajero = "";

                    calcularYMostrarPrecioTramo();

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
                error -> {
                    loaderDetalle.setVisibility(View.GONE);
                    manejarErrorReserva(error);
                }
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

                // ── Parada de bajada ─────────────────────────────────────────────
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

                // ── Punto de subida (recogida) ───────────────────────────────────
                double latSub = u.optDouble("latSubida",
                        u.optDouble("latOrigen",
                                u.optDouble("latInicio", 0)));
                double lngSub = u.optDouble("lngSubida",
                        u.optDouble("lngOrigen",
                                u.optDouble("lngInicio", 0)));
                String nomSub = u.optString("nombreParadaSubida",
                        u.optString("nombreParadaInicio",
                                u.optString("nombreOrigen", "")));
                if (latSub != 0) {
                    reserva.put("latSubida",          latSub);
                    reserva.put("lngSubida",          lngSub);
                    reserva.put("latOrigen",          latSub);
                    reserva.put("lngOrigen",          lngSub);
                    reserva.put("nombreParadaSubida", nomSub);
                    reserva.put("nombreParadaInicio", nomSub);
                }

                // ── Precio individual del pasajero ───────────────────────────────
                double precio = u.optDouble("precioFinal",
                        u.optDouble("precio",
                                u.optDouble("costoPorPasajero",
                                        u.optDouble("precioTramo", 0))));
                // Buscar en sub-objeto pago si existe
                if (precio <= 0) {
                    JSONObject pagoObj = u.optJSONObject("pago");
                    if (pagoObj != null) precio = pagoObj.optDouble("monto", 0);
                }
                if (precio > 0) {
                    reserva.put("precioFinal",        precio);
                    reserva.put("precio",             precio);
                    reserva.put("costoPorPasajero",   precio);
                }

                // ── Objeto pasajero/usuario ──────────────────────────────────────
                JSONObject usuarioObj = u.optJSONObject("usuario");
                if (usuarioObj != null) {
                    int idU = usuarioObj.optInt("idUsuarios",
                            usuarioObj.optInt("id", 0));
                    if (idU > 0) usuarioObj.put("id", idU);
                    reserva.put("pasajero", usuarioObj);
                    reserva.put("usuario",  usuarioObj);
                } else {
                    int idU = u.optInt("idUsuarios", u.optInt("idPasajero", 0));
                    JSONObject fallback = new JSONObject();
                    fallback.put("id",         idU);
                    fallback.put("idUsuarios", idU);
                    fallback.put("nombre",
                            u.optString("nombrePasajero",
                                    u.optString("nombre", "Pasajero")));
                    reserva.put("pasajero", fallback);
                    reserva.put("usuario",  fallback);
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

                    // ── Nombre del pasajero ───────────────────────────────────
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
                            String n = po.optString("nombres",""), a = po.optString("apellidos","");
                            if (!n.isEmpty() || !a.isEmpty()) np = (n + " " + a).trim();
                        }
                    }
                    if (np.isEmpty()) np = res.optString("nombrePasajero","Pasajero " + (colorIdx + 1));

                    // ── Foto del pasajero ─────────────────────────────────────
                    String fotoUrl = "";
                    if (po != null) {
                        for (String k : new String[]{"fotoPerfi","fotoPerfil","foto","photoUrl",
                                "avatar","imagenPerfil","urlFoto","profilePicture"}) {
                            String f = po.optString(k, "");
                            if (!f.isEmpty() && !f.equals("null")) { fotoUrl = f; break; }
                        }
                    }
                    if (fotoUrl.isEmpty()) {
                        for (String k : new String[]{"fotoPasajero","fotoPerfil","fotoUsuario"}) {
                            String f = res.optString(k, "");
                            if (!f.isEmpty() && !f.equals("null")) { fotoUrl = f; break; }
                        }
                    }

                    // ── Coords subida/bajada ──────────────────────────────────
                    double latS = res.optDouble("latSubida",
                            res.optDouble("latOrigen", res.optDouble("latInicio", 0)));
                    double lngS = res.optDouble("lngSubida",
                            res.optDouble("lngOrigen", res.optDouble("lngInicio", 0)));
                    String nomSubida = res.optString("nombreParadaSubida",
                            res.optString("nombreParadaInicio", ""));
                    if (nomSubida.isEmpty()) nomSubida = origenActual;

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

                    if (gpSubida == null && latS != 0 && lngS != 0
                            && !sonIguales(latS, lngS, latOrigen, lngOrigen)) {
                        gpSubida = new GeoPoint(latS, lngS);
                        nombreSubidaPasajero = nomSubida.isEmpty() ? np : nomSubida;
                    }

                    boolean tieneParada = latP != 0 && lngP != 0;
                    int     pasColor    = tieneParada
                            ? COLORES_PASAJEROS[colorIdx % COLORES_PASAJEROS.length]
                            : Color.parseColor("#607D8B");
                    String  pasColorHex = tieneParada
                            ? COLORES_PASAJEROS_HEX[colorIdx % COLORES_PASAJEROS_HEX.length]
                            : "#607D8B";

                    if (tieneParada) {
                        paradasPasajeros.add(new GeoPoint(latP, lngP));
                        nombresPasajerosParadas.add(np + (par.isEmpty() ? "" : " → " + par));
                    }

                    // ── Variables finales para lambdas ────────────────────────
                    final String  fFoto       = fotoUrl;
                    final String  fNomSubida  = nomSubida;
                    final String  fNp         = np;
                    final int     fAsi        = asi;
                    final String  fPar        = par;
                    final String  fEst        = est;
                    final int     fIdRes      = idRes;
                    final int     fColorIdx   = colorIdx;
                    final int     fPasColor   = pasColor;
                    final String  fPasColorHex= pasColorHex;
                    final boolean fTieneParada= tieneParada;
                    final double  fLatP       = latP;
                    final double  fLngP       = lngP;
                    final double  fLatS       = latS;
                    final double  fLngS       = lngS;

                    // ── Precio: leer TODOS los campos posibles del backend ──
                    double precioGuardado = 0;
// 1. Campos directos del objeto usuario-viaje
                    for (String k : new String[]{"precioFinal","precioTramo","costoPorPasajero","precio","monto"}) {
                        double v = res.optDouble(k, 0);
                        if (v > 0) { precioGuardado = v; break; }
                    }
// 2. Sub-objeto "pago" si existe
                    if (precioGuardado <= 0) {
                        JSONObject pagoObj = res.optJSONObject("pago");
                        if (pagoObj != null) precioGuardado = pagoObj.optDouble("monto", 0);
                    }

                    if (precioGuardado <= 0) {
                        JSONObject usuObj = res.optJSONObject("usuario");
                        if (usuObj == null) usuObj = res.optJSONObject("pasajero");
                        if (usuObj != null) {
                            for (String k : new String[]{"precioFinal","precio","costoPorPasajero"}) {
                                double v = usuObj.optDouble(k, 0);
                                if (v > 0) { precioGuardado = v; break; }
                            }
                        }
                    }

                    if (precioGuardado > 0) {
                        // ── 1. Precio ya guardado en la reserva (Forzar redondeo para consistencia) ──
                        final double fPrecio = com.arlys.moviflexx.model.Manager.PrecioTramoPasajeroManager.redondear(precioGuardado);
                        resolverNombreParada(fLatP, fLngP, fNp, fAsi, fPar, fEst, fIdRes,
                                fTieneParada ? "P" + (fColorIdx + 1) : "?",
                                fPasColor, fPasColorHex, fTieneParada,
                                fFoto, fPrecio, fNomSubida);

                    } else if (fTieneParada && distanciaKm > 0 && precioViaje > 0) {


                        // ── 2. Calcular precio del tramo igual que el pasajero ──
                        double latSubidaFinal = fLatS != 0 ? fLatS : latOrigen;
                        double lngSubidaFinal = fLngS != 0 ? fLngS : lngOrigen;

                        PrecioTramoPasajeroManager.calcular(
                                this, viajeId,
                                latSubidaFinal, lngSubidaFinal,
                                fLatP, fLngP,
                                distanciaKm,
                                precioViaje,
                                resultado -> {
                                    Log.d(TAG, "Precio tramo conductor→pasajero "
                                            + fNp + ": $" + resultado.precioFinal
                                            + " | tramo=" + resultado.distanciaKm + "km"
                                            + " | total=" + distanciaKm + "km");
                                    resolverNombreParada(fLatP, fLngP, fNp, fAsi, fPar, fEst, fIdRes,
                                            fTieneParada ? "P" + (fColorIdx + 1) : "?",
                                            fPasColor, fPasColorHex, fTieneParada,
                                            fFoto, resultado.precioFinal, fNomSubida);
                                }
                        );
                    } else {
                        // ── 3. Fallback: precio global del viaje ──────────────
                        resolverNombreParada(fLatP, fLngP, fNp, fAsi, fPar, fEst, fIdRes,
                                fTieneParada ? "P" + (fColorIdx + 1) : "?",
                                fPasColor, fPasColorHex, fTieneParada,
                                fFoto, precioViaje, fNomSubida);
                    }

                    if (tieneParada) colorIdx++;
                    if (EST_ESPERANDO_RECOGIDA.equals(est)) {
                        hayEsperando = true;
                        if (gpParada == null && tieneParada) {
                            gpParada     = new GeoPoint(latP, lngP);
                            nombreParada = par.isEmpty() ? np : par;
                        }
                    }
                }

                // ── Actualizar contador ───────────────────────────────────────
                final boolean fHayEsperando = hayEsperando;
                final int     fTotal        = total;

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    int countFilas = layoutListaPasajeros.getChildCount();
                    if (countFilas == 0 && fTotal > 0) {
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
                    if (btnRecoger != null)
                        btnRecoger.setVisibility(fHayEsperando ? View.VISIBLE : View.GONE);
                    renderizarMapa();
                }, 800);

            } catch (Exception e) {
                Log.e(TAG, "Error procesando reservas conductor", e);
                mostrarSinPasajeros();
            }
        });
    }
    // ── resolverNombreParada actualizado con foto, precio y subida ────────────
    private void resolverNombreParada(double latP, double lngP, String np, int asi,
                                      String par, String est, int idRes,
                                      String etiqMarcador, int colorMarcador,
                                      String colorHex, boolean tieneParada,
                                      String fotoUrl, double precioPasajero,
                                      String nomSubida) {
        // ── SIN RECALCULAR: solo mostrar precio que vino del backend ──────────
        if (!tieneParada) {
            agregarFilaPasajeroColoreado(np, asi, "Sin parada asignada", est, idRes,
                    etiqMarcador, colorMarcador, colorHex, false,
                    fotoUrl, precioPasajero, nomSubida);
            return;
        }
        if (!par.isEmpty()) {
            agregarFilaPasajeroColoreado(np, asi, par, est, idRes,
                    etiqMarcador, colorMarcador, colorHex, tieneParada,
                    fotoUrl, precioPasajero, nomSubida);
            return;
        }
        // Geocodificar nombre en background, precio no se toca
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
                    etiqMarcador, colorMarcador, colorHex, tieneParada,
                    fotoUrl, precioPasajero, nomSubida));
        }).start();
    }

    // ── agregarFilaPasajeroColoreado con foto + precio + mini ruta ────────────
    private void agregarFilaPasajeroColoreado(String nombre, int asientos, String parada,
                                              String estado, int idRes,
                                              String etiqMarcador, int colorMarcador,
                                              String colorHex, boolean tieneParada,
                                              String fotoUrl, double precioPasajero,
                                              String nomSubida) {
        float d = getResources().getDisplayMetrics().density;
        int p14=(int)(14*d), p10=(int)(10*d), p8=(int)(8*d),
                p6 =(int)(6 *d), p4 =(int)(4 *d);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, p4, 0, p4);
        card.setLayoutParams(lpCard);
        card.setPadding(p14, p10, p14, p10);
        GradientDrawable bgCard = new GradientDrawable();
        bgCard.setShape(GradientDrawable.RECTANGLE);
        bgCard.setCornerRadius(16 * d);
        bgCard.setColor(android.graphics.Color.WHITE);
        bgCard.setStroke((int)(2.5f * d), tieneParada ? colorMarcador
                : android.graphics.Color.parseColor("#CFD8DC"));
        card.setBackground(bgCard);
        card.setElevation(3 * d);

        // ── Fila 1: Avatar/foto + nombre + badge estado ───────────────────────
        LinearLayout fila1 = new LinearLayout(this);
        fila1.setOrientation(LinearLayout.HORIZONTAL);
        fila1.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpF1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpF1.setMargins(0, 0, 0, p6);
        fila1.setLayoutParams(lpF1);

        // ── Avatar circular (foto o letra) ────────────────────────────────────
        android.widget.FrameLayout avatarFrame = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams lpAv = new LinearLayout.LayoutParams(
                (int)(42*d), (int)(42*d));
        lpAv.setMargins(0, 0, p10, 0);
        avatarFrame.setLayoutParams(lpAv);

        // Letra de fallback
        TextView tvLetra = new TextView(this);
        tvLetra.setText(nombre.isEmpty() ? "P" : nombre.substring(0,1).toUpperCase());
        tvLetra.setTextColor(android.graphics.Color.WHITE);
        tvLetra.setTextSize(15f);
        tvLetra.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLetra.setGravity(android.view.Gravity.CENTER);
        tvLetra.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        GradientDrawable bgLetra = new GradientDrawable();
        bgLetra.setShape(GradientDrawable.OVAL);
        bgLetra.setColor(tieneParada ? colorMarcador
                : android.graphics.Color.parseColor("#90A4AE"));
        tvLetra.setBackground(bgLetra);
        avatarFrame.addView(tvLetra);

        // Foto encima si existe
        if (fotoUrl != null && !fotoUrl.isEmpty()) {
            android.widget.ImageView ivFoto = new android.widget.ImageView(this);
            ivFoto.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            ivFoto.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
            GradientDrawable clipOval = new GradientDrawable();
            clipOval.setShape(GradientDrawable.OVAL);
            ivFoto.setBackground(clipOval);
            avatarFrame.addView(ivFoto);
            com.bumptech.glide.Glide.with(this)
                    .load(fotoUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_myplaces)
                    .error(android.R.drawable.ic_menu_myplaces)
                    .into(ivFoto);
        }
        fila1.addView(avatarFrame);

        // ── Columna nombre + badge ────────────────────────────────────────────
        LinearLayout colNombre = new LinearLayout(this);
        colNombre.setOrientation(LinearLayout.VERTICAL);
        colNombre.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvNombre = new TextView(this);
        tvNombre.setText(nombre);
        tvNombre.setTextSize(14.5f);
        tvNombre.setTypeface(null, android.graphics.Typeface.BOLD);
        tvNombre.setTextColor(android.graphics.Color.parseColor("#1A2035"));
        tvNombre.setMaxLines(1);
        tvNombre.setEllipsize(android.text.TextUtils.TruncateAt.END);
        colNombre.addView(tvNombre);

        // Asientos
        TextView tvAsi = new TextView(this);
        tvAsi.setText("💺 " + asientos + (asientos == 1 ? " asiento" : " asientos"));
        tvAsi.setTextSize(11.5f);
        tvAsi.setTextColor(android.graphics.Color.parseColor("#546E7A"));
        LinearLayout.LayoutParams lpAsi = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpAsi.topMargin = (int)(3*d);
        tvAsi.setLayoutParams(lpAsi);
        colNombre.addView(tvAsi);
        fila1.addView(colNombre);

        // Badge estado
        TextView badge = new TextView(this);
        badge.setText(badgeEstado(estado));
        badge.setTextSize(10f);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setTextColor(android.graphics.Color.WHITE);
        badge.setPadding(p8, (int)(3*d), p8, (int)(3*d));
        GradientDrawable bgBadge = new GradientDrawable();
        bgBadge.setShape(GradientDrawable.RECTANGLE);
        bgBadge.setCornerRadius(20*d);
        bgBadge.setColor(colorBadgeEstado(estado));
        badge.setBackground(bgBadge);
        fila1.addView(badge);
        card.addView(fila1);

        // ── Badge precio por tramo ────────────────────────────────────────────
        // ── Badge precio por tramo ────────────────────────────────────────────────
        if (precioPasajero > 0) {
            java.text.NumberFormat nf = java.text.NumberFormat
                    .getNumberInstance(new java.util.Locale("es", "CO"));

            TextView tvPrecio = new TextView(this);
            tvPrecio.setText("$" + nf.format(precioPasajero));
            tvPrecio.setTextSize(11f);
            tvPrecio.setTypeface(null, Typeface.BOLD);
            tvPrecio.setTextColor(Color.parseColor("#004D40"));
            tvPrecio.setPadding(p6, (int)(2*d), p6, (int)(2*d));

            LinearLayout.LayoutParams lpPrecio = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpPrecio.topMargin = (int)(3*d);
            tvPrecio.setLayoutParams(lpPrecio);

            GradientDrawable bgPrecio = new GradientDrawable();
            bgPrecio.setShape(GradientDrawable.RECTANGLE);
            bgPrecio.setCornerRadius(20*d);
            bgPrecio.setColor(Color.parseColor("#E0F7FA"));
            bgPrecio.setStroke((int)(1*d), Color.parseColor("#00897B"));
            tvPrecio.setBackground(bgPrecio);
            card.addView(tvPrecio);
        }

        // ── Mini ruta: subida → bajada ────────────────────────────────────────
        if (tieneParada) {
            LinearLayout miniRuta = new LinearLayout(this);
            miniRuta.setOrientation(LinearLayout.HORIZONTAL);
            miniRuta.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpMR = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpMR.setMargins(0, 0, 0, p6);
            miniRuta.setLayoutParams(lpMR);
            miniRuta.setPadding(p8, p6, p8, p6);
            GradientDrawable bgMR = new GradientDrawable();
            bgMR.setShape(GradientDrawable.RECTANGLE);
            bgMR.setCornerRadius(10*d);
            bgMR.setColor(android.graphics.Color.parseColor("#F5F5F5"));
            bgMR.setStroke((int)(1*d), android.graphics.Color.parseColor("#E0E0E0"));
            miniRuta.setBackground(bgMR);

            TextView tvSubida = new TextView(this);
            tvSubida.setText("🟢 " + (nomSubida.isEmpty() ? origenActual : nomSubida));
            tvSubida.setTextSize(11f);
            tvSubida.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
            tvSubida.setMaxLines(1);
            tvSubida.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvSubida.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            miniRuta.addView(tvSubida);

            TextView tvFlecha = new TextView(this);
            tvFlecha.setText("  →  ");
            tvFlecha.setTextSize(12f);
            tvFlecha.setTextColor(android.graphics.Color.parseColor("#90A4AE"));
            miniRuta.addView(tvFlecha);

            TextView tvBajada = new TextView(this);
            tvBajada.setText("🔴 " + parada);
            tvBajada.setTextSize(11f);
            tvBajada.setTextColor(android.graphics.Color.parseColor("#B71C1C"));
            tvBajada.setMaxLines(1);
            tvBajada.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvBajada.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            miniRuta.addView(tvBajada);
            card.addView(miniRuta);
        }

        // ── Fila parada (bajada destacada) ────────────────────────────────────
        LinearLayout filaParada = new LinearLayout(this);
        filaParada.setOrientation(LinearLayout.HORIZONTAL);
        filaParada.setGravity(android.view.Gravity.CENTER_VERTICAL);
        filaParada.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (tieneParada) {
            TextView chipNum = new TextView(this);
            chipNum.setText(etiqMarcador);
            chipNum.setTextSize(10f);
            chipNum.setTypeface(null, android.graphics.Typeface.BOLD);
            chipNum.setTextColor(android.graphics.Color.WHITE);
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
            tvParada.setTextColor(android.graphics.Color.parseColor(colorHex));
            tvParada.setTypeface(null, android.graphics.Typeface.BOLD);
            tvParada.setMaxLines(2);
            tvParada.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvParada.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvParada.setPadding(p8, p4, p8, p4);
            GradientDrawable bgPar = new GradientDrawable();
            bgPar.setShape(GradientDrawable.RECTANGLE);
            bgPar.setCornerRadius(10*d);
            bgPar.setColor(android.graphics.Color.parseColor("#F0F4F8"));
            bgPar.setStroke((int)(1*d), colorMarcador);
            tvParada.setBackground(bgPar);
            filaParada.addView(tvParada);
        } else {
            TextView tvSin = new TextView(this);
            tvSin.setText("⚠️  El pasajero aún no ha marcado su parada");
            tvSin.setTextSize(11.5f);
            tvSin.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
            tvSin.setTypeface(null, android.graphics.Typeface.ITALIC);
            tvSin.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            filaParada.addView(tvSin);
        }
        card.addView(filaParada);



        // ── Botón confirmar recogida (si espera) ──────────────────────────────
        if (EST_ESPERANDO_RECOGIDA.equals(estado)) {
            com.google.android.material.button.MaterialButton btnR =
                    new com.google.android.material.button.MaterialButton(this);
            btnR.setText("✅  Confirmar recogida");
            btnR.setTextSize(13f);
            btnR.setTextColor(android.graphics.Color.WHITE);
            btnR.setCornerRadius((int)(12*d));
            btnR.setBackgroundColor(tieneParada ? colorMarcador
                    : android.graphics.Color.parseColor("#1976D2"));
            LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int)(44*d));
            lpBtn.topMargin = p8;
            btnR.setLayoutParams(lpBtn);
            btnR.setOnClickListener(v -> { idReservaActual = idRes; confirmarRecogida(); });
            card.addView(btnR);
        }

        layoutListaPasajeros.addView(card);
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
                                              String estado, int idRes,
                                              String etiqMarcador, int colorMarcador,
                                              String colorHex, boolean tieneParada) {
        float d = getResources().getDisplayMetrics().density;
        int p14=(int)(14*d), p10=(int)(10*d), p8=(int)(8*d),
                p6 =(int)(6 *d), p4 =(int)(4 *d);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, p4, 0, p4);
        card.setLayoutParams(lpCard);
        card.setPadding(p14, p10, p14, p10);
        GradientDrawable bgCard = new GradientDrawable();
        bgCard.setShape(GradientDrawable.RECTANGLE);
        bgCard.setCornerRadius(16 * d);
        bgCard.setColor(Color.WHITE);
        bgCard.setStroke((int)(2.5f * d), tieneParada ? colorMarcador
                : Color.parseColor("#CFD8DC"));
        card.setBackground(bgCard);
        card.setElevation(3 * d);

        // ── Fila 1: avatar + nombre + badge estado ────────────────────────────
        LinearLayout fila1 = new LinearLayout(this);
        fila1.setOrientation(LinearLayout.HORIZONTAL);
        fila1.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpF1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpF1.setMargins(0, 0, 0, p6);
        fila1.setLayoutParams(lpF1);

        // Avatar letra
        TextView avatar = new TextView(this);
        LinearLayout.LayoutParams lpAv = new LinearLayout.LayoutParams((int)(36*d), (int)(36*d));
        lpAv.setMargins(0, 0, p8, 0);
        avatar.setLayoutParams(lpAv);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(15f);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setText(nombre.isEmpty() ? "P" : nombre.substring(0,1).toUpperCase());
        GradientDrawable bgAv = new GradientDrawable();
        bgAv.setShape(GradientDrawable.OVAL);
        bgAv.setColor(tieneParada ? colorMarcador : Color.parseColor("#90A4AE"));
        avatar.setBackground(bgAv);
        fila1.addView(avatar);

        // Columna nombre + asientos
        LinearLayout colNombre = new LinearLayout(this);
        colNombre.setOrientation(LinearLayout.VERTICAL);
        colNombre.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvNombre = new TextView(this);
        tvNombre.setText(nombre);
        tvNombre.setTextSize(14.5f);
        tvNombre.setTypeface(null, Typeface.BOLD);
        tvNombre.setTextColor(Color.parseColor("#1A2035"));
        tvNombre.setMaxLines(1);
        tvNombre.setEllipsize(android.text.TextUtils.TruncateAt.END);
        colNombre.addView(tvNombre);

        TextView tvAsi = new TextView(this);
        tvAsi.setText("💺 " + asientos + (asientos == 1 ? " asiento" : " asientos"));
        tvAsi.setTextSize(11.5f);
        tvAsi.setTextColor(Color.parseColor("#546E7A"));
        LinearLayout.LayoutParams lpAsi = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpAsi.topMargin = (int)(3*d);
        tvAsi.setLayoutParams(lpAsi);
        colNombre.addView(tvAsi);
        fila1.addView(colNombre);

        // Badge estado
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

        // ── Fila parada (bajada) ──────────────────────────────────────────────
        LinearLayout filaParada = new LinearLayout(this);
        filaParada.setOrientation(LinearLayout.HORIZONTAL);
        filaParada.setGravity(Gravity.CENTER_VERTICAL);
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
            chipNum.setGravity(Gravity.CENTER);
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
            tvParada.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvParada.setPadding(p8, p4, p8, p4);
            GradientDrawable bgPar = new GradientDrawable();
            bgPar.setShape(GradientDrawable.RECTANGLE);
            bgPar.setCornerRadius(10*d);
            bgPar.setColor(Color.parseColor("#F0F4F8"));
            bgPar.setStroke((int)(1*d), colorMarcador);
            tvParada.setBackground(bgPar);
            filaParada.addView(tvParada);
        } else {
            TextView tvSin = new TextView(this);
            tvSin.setText("⚠️  El pasajero aún no ha marcado su parada");
            tvSin.setTextSize(11.5f);
            tvSin.setTextColor(Color.parseColor("#9E9E9E"));
            tvSin.setTypeface(null, Typeface.ITALIC);
            tvSin.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            filaParada.addView(tvSin);
        }
        card.addView(filaParada);

        // ── Mini ruta: subida → bajada ────────────────────────────────────────
        String subidaNombre = origenActual; // fallback al origen de la ruta
        if (tieneParada && !subidaNombre.isEmpty()) {
            LinearLayout miniRuta = new LinearLayout(this);
            miniRuta.setOrientation(LinearLayout.HORIZONTAL);
            miniRuta.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpMR = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpMR.topMargin = (int)(6*d);
            miniRuta.setLayoutParams(lpMR);
            miniRuta.setPadding(p6, p4, p6, p4);

            GradientDrawable bgMR = new GradientDrawable();
            bgMR.setShape(GradientDrawable.RECTANGLE);
            bgMR.setCornerRadius(8*d);
            bgMR.setColor(Color.parseColor("#F5F5F5"));
            bgMR.setStroke((int)(1*d), Color.parseColor("#E0E0E0"));
            miniRuta.setBackground(bgMR);

            TextView tvSubida = new TextView(this);
            tvSubida.setText("🟢 " + subidaNombre);
            tvSubida.setTextSize(11f);
            tvSubida.setTextColor(Color.parseColor("#2E7D32"));
            tvSubida.setMaxLines(1);
            tvSubida.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvSubida.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            miniRuta.addView(tvSubida);

            TextView tvFlecha = new TextView(this);
            tvFlecha.setText("  →  ");
            tvFlecha.setTextSize(12f);
            tvFlecha.setTextColor(Color.parseColor("#90A4AE"));
            miniRuta.addView(tvFlecha);

            TextView tvBajada = new TextView(this);
            tvBajada.setText("🔴 " + parada);
            tvBajada.setTextSize(11f);
            tvBajada.setTextColor(Color.parseColor("#B71C1C"));
            tvBajada.setMaxLines(1);
            tvBajada.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvBajada.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            miniRuta.addView(tvBajada);

            card.addView(miniRuta);
        }

        // ── Botón confirmar recogida (si espera) ──────────────────────────────
        if (EST_ESPERANDO_RECOGIDA.equals(estado)) {
            com.google.android.material.button.MaterialButton btnR =
                    new com.google.android.material.button.MaterialButton(this);
            btnR.setText("✅  Confirmar recogida");
            btnR.setTextSize(13f);
            btnR.setTextColor(Color.WHITE);
            btnR.setCornerRadius((int)(12*d));
            btnR.setBackgroundColor(tieneParada ? colorMarcador
                    : Color.parseColor("#1976D2"));
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
        if (e==null||e.isEmpty()) return "✓ Activa";
        switch (e.toUpperCase()) {
            case "ACTIVA": case "CONFIRMADA": case "RESERVADO": return "✓ Confirmada";
            case "PENDIENTE":    return "⏳ Pendiente";
            case "EN_CURSO": case "INICIADO": return " A bordo";
            case "ESPERANDO_RECOGIDA": return "⏳ Esperando";
            case "RECOGIDO":     return "✅ Recogido";
            case "COMPLETADO": case "FINALIZADO": return "Completado";
            case "CANCELADO": case "CANCELADA": return "✕ Cancelada";
            default: return " " + e;
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
        tTitulo.setText("¿Dónde te vas a subir?");
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
        // ── Botón: tocar en el mapa ──
        MaterialButton btnTocarMapa = new MaterialButton(this);
        btnTocarMapa.setText("TOCAR EN EL MAPA");
        btnTocarMapa.setTextSize(13f);
        btnTocarMapa.setTextColor(Color.parseColor("#00695C"));
        btnTocarMapa.setBackgroundColor(Color.TRANSPARENT);
        btnTocarMapa.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#00897B")));
        btnTocarMapa.setStrokeWidth((int)(2*dp));
        btnTocarMapa.setCornerRadius((int)(14*dp));
        btnTocarMapa.setVisibility(View.VISIBLE);  // ← asegurarlo explícito
        LinearLayout.LayoutParams lpTM = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(46*dp));
        lpTM.setMargins(0, 0, 0, p8);
        btnTocarMapa.setLayoutParams(lpTM);
        // En mostrarSheetSubida(), REEMPLAZA el btnTocarMapa.setOnClickListener:
        btnTocarMapa.setOnClickListener(v -> {
            sheet.dismiss();
            Intent intentMapa = new Intent(this, MapaSeleccionActivity.class);
            if (gpOrigen != null) {
                intentMapa.putExtra("lat_centro", gpOrigen.getLatitude());
                intentMapa.putExtra("lng_centro", gpOrigen.getLongitude());
            }
            if (gpSubida != null) {
                intentMapa.putExtra("lat_previa", gpSubida.getLatitude());
                intentMapa.putExtra("lng_previa", gpSubida.getLongitude());
            }
            if (gpDestino != null) {
                intentMapa.putExtra("lat_destino", gpDestino.getLatitude());
                intentMapa.putExtra("lng_destino", gpDestino.getLongitude());
            }

            // Intentar pasar geojsonRuta; si está vacío, construirlo desde rutaActiva
            String geojsonParaMapa = geojsonRuta;
            if ((geojsonParaMapa == null || geojsonParaMapa.isEmpty()) && rutaActiva != null && rutaActiva.size() >= 2) {
                try {
                    org.json.JSONArray coords = new org.json.JSONArray();
                    for (GeoPoint gp : rutaActiva) {
                        org.json.JSONArray par = new org.json.JSONArray();
                        par.put(gp.getLongitude());
                        par.put(gp.getLatitude());
                        coords.put(par);
                    }
                    org.json.JSONObject geo = new org.json.JSONObject();
                    geo.put("type", "LineString");
                    geo.put("coordinates", coords);
                    geojsonParaMapa = geo.toString();
                    Log.d("DEBUG_RUTA", "GeoJSON construido desde rutaActiva: " + rutaActiva.size() + " puntos");
                } catch (Exception ex) {
                    Log.w("DEBUG_RUTA", "Error construyendo GeoJSON: " + ex.getMessage());
                }
            }

            Log.d("DEBUG_RUTA", "geojsonParaMapa length=" + (geojsonParaMapa != null ? geojsonParaMapa.length() : "null"));

            if (geojsonParaMapa != null && !geojsonParaMapa.isEmpty()) {
                intentMapa.putExtra("geojson_ruta", geojsonParaMapa);
            }

            startActivityForResult(intentMapa, REQUEST_MAPA_SUBIDA);
        });
        root.addView(btnTocarMapa);  // ← debe estar ANTES del crearSeparadorO

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
            tvElegida.setText("" + nombreSubidaPasajero);
            tvElegida.setVisibility(View.VISIBLE);
        }

        poblarListaSubida(lista, paradasConOrigen, "", dp, p8, p4, pE -> {
            elegida[0] = pE;
            tvElegida.setText(" " + pE.nombre); tvElegida.setVisibility(View.VISIBLE);
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
        String[] icons  = {"", ""};
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

    private void poblarListaSubida(LinearLayout container,
                                   ArrayList<ParadaDinamica> paradas,
                                   String filtro,
                                   float dp, int p8, int p4,
                                   java.util.function.Consumer<ParadaDinamica> onSelect) {
        container.removeAllViews();

        if (paradas.isEmpty()) {
            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setText("Sin puntos disponibles");
            tv.setTextSize(13f);
            tv.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
            tv.setPadding(p8, p8, p8, p8);
            container.addView(tv);
            return;
        }

        // ── Paleta ───────────────────────────────────────────────────────────
        final int colorNormalBg    = android.graphics.Color.parseColor("#F9FAFB");
        final int colorNormalBorde = android.graphics.Color.parseColor("#B2DFDB");
        final int colorSelBg       = android.graphics.Color.parseColor("#E0F7FA");
        final int colorSelBorde    = android.graphics.Color.parseColor("#00897B");
        final int colorNormalTxt   = android.graphics.Color.parseColor("#004D40");
        final int colorSelTxt      = android.graphics.Color.parseColor("#00695C");

        for (int i = 0; i < paradas.size(); i++) {
            ParadaDinamica pd  = paradas.get(i);
            boolean esOrigen   = (i == 0);
            String  icon       = esOrigen ? "🟢" : "🔵";
            String  labelTexto = icon + "  " + pd.nombre + (esOrigen ? "  (Inicio)" : "");

            // ¿Esta fila coincide con la subida ya guardada?
            boolean estaActiva = (gpSubida != null)
                    && Math.abs(pd.lat - gpSubida.getLatitude())  < 0.00005
                    && Math.abs(pd.lng - gpSubida.getLongitude()) < 0.00005;

            // ── Contenedor de fila ────────────────────────────────────────────
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpF.setMargins(0, (int)(3*dp), 0, (int)(3*dp));
            fila.setLayoutParams(lpF);
            fila.setPadding(p8, p8, p8, p8);
            fila.setClickable(true);
            fila.setFocusable(true);

            // ── Icono de check (posición 0) ───────────────────────────────────
            android.widget.TextView tvCheck = new android.widget.TextView(this);
            tvCheck.setTextSize(15f);
            tvCheck.setText(estaActiva ? "✅" : "");
            tvCheck.setWidth((int)(30*dp));
            tvCheck.setGravity(android.view.Gravity.CENTER);
            fila.addView(tvCheck);   // child 0

            // ── Texto de la parada (posición 1) ───────────────────────────────
            android.widget.TextView tvLabel = new android.widget.TextView(this);
            tvLabel.setText(labelTexto);
            tvLabel.setTextSize(14f);
            tvLabel.setTextColor(estaActiva ? colorSelTxt : colorNormalTxt);
            tvLabel.setTypeface(null, estaActiva
                    ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
            tvLabel.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            fila.addView(tvLabel);   // child 1

            // ── Background inicial ────────────────────────────────────────────
            aplicarEstiloFila(fila, estaActiva, colorSelBg, colorSelBorde,
                    colorNormalBg, colorNormalBorde, dp);

            // Si ya está activa, registrarla
            if (estaActiva) filaSubidaSeleccionada = fila;

            // ── Click: RESET total del container → marcar solo esta fila ─────
            final LinearLayout filaFinal  = fila;
            final android.widget.TextView tvCheckFinal = tvCheck;
            final android.widget.TextView tvLabelFinal = tvLabel;

            fila.setOnClickListener(v -> {

                // 1. Recorrer TODOS los hijos del container y resetear
                for (int ci = 0; ci < container.getChildCount(); ci++) {
                    android.view.View hijo = container.getChildAt(ci);
                    if (!(hijo instanceof LinearLayout)) continue;
                    LinearLayout filaHija = (LinearLayout) hijo;

                    // Fondo limpio — siempre nuevo drawable para no mutar
                    aplicarEstiloFila(filaHija, false,
                            colorSelBg, colorSelBorde,
                            colorNormalBg, colorNormalBorde, dp);

                    // Resetear check e texto
                    android.view.View c0 = filaHija.getChildAt(0);
                    android.view.View c1 = filaHija.getChildAt(1);
                    if (c0 instanceof android.widget.TextView)
                        ((android.widget.TextView) c0).setText("");
                    if (c1 instanceof android.widget.TextView) {
                        android.widget.TextView t = (android.widget.TextView) c1;
                        t.setTextColor(colorNormalTxt);
                        t.setTypeface(null, android.graphics.Typeface.NORMAL);
                    }
                }

                // 2. Marcar la fila tocada
                aplicarEstiloFila(filaFinal, true,
                        colorSelBg, colorSelBorde,
                        colorNormalBg, colorNormalBorde, dp);
                tvCheckFinal.setText("✅");
                tvLabelFinal.setTextColor(colorSelTxt);
                tvLabelFinal.setTypeface(null, android.graphics.Typeface.BOLD);
                filaSubidaSeleccionada = filaFinal;

                // 3. Animación tap
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70)
                        .withEndAction(() ->
                                v.animate().scaleX(1f).scaleY(1f).setDuration(110).start())
                        .start();

                // 4. Notificar selección
                onSelect.accept(pd);
            });

            container.addView(fila);
        }
    }

    // ── Helper: aplica un drawable NUEVO a la fila según su estado ─────────────
// (Evita mutar drawables compartidos — esa era la causa raíz del bug)
    private void aplicarEstiloFila(LinearLayout fila, boolean seleccionada,
                                   int colorSelBg, int colorSelBorde,
                                   int colorNormalBg, int colorNormalBorde,
                                   float dp) {
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(10 * dp);
        if (seleccionada) {
            bg.setColor(colorSelBg);
            bg.setStroke((int)(2*dp), colorSelBorde);
        } else {
            bg.setColor(colorNormalBg);
            bg.setStroke((int)(1*dp), colorNormalBorde);
        }
        fila.setBackground(bg);
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
            Toast.makeText(this, "No se puede reservar en este momento", Toast.LENGTH_LONG).show();
            return;
        }

        // ── Coords de subida — declaradas final para usar en lambda ──────────
        final double latS = (subida != null) ? subida.lat : latOrigen;
        final double lngS = (subida != null) ? subida.lng : lngOrigen;
        final String nomS = (subida != null) ? subida.nombre : origenActual;

        // ── Referencias finales para el lambda ───────────────────────────────
        final ParadaDinamica fSubida = subida;
        final ParadaDinamica fBajada = bajada;

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
        } catch (JSONException e) {
            loaderDetalle.setVisibility(View.GONE);
            return;
        }

        ConexionApi.getInstance(this).post(Constantes.RESERVAS, body,
                response -> {
                    loaderDetalle.setVisibility(View.GONE);
                    idReservaActual = response.optInt("idUsuarioViaje",
                            response.optInt("idReserva",
                                    response.optInt("id",
                                            response.optInt("reservaId", -1))));
                    estadoReserva = response.optString("estado", EST_CONFIRMADA).toUpperCase();
                    yaReservo     = true;

                    // Usar fBajada (final) en lugar de bajada
                    gpParada     = fBajada.toGeoPoint();
                    nombreParada = fBajada.nombre;

                    // Guardar subida definitiva — usar fSubida y latS/lngS (final)
                    if (fSubida != null && !sonIguales(latS, lngS, latOrigen, lngOrigen)) {
                        gpSubida             = fSubida.toGeoPoint();
                        nombreSubidaPasajero = fSubida.nombre;
                    } else {
                        gpSubida             = null;
                        nombreSubidaPasajero = "";
                    }

                    // Calcular precio persistente ANTES del runOnUiThread
                    // para que precioCalculadoPersistente ya esté listo
                    // cuando mostrarCardMiReserva() lo consuma
                    calcularYMostrarPrecioTramo();

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
                error -> {
                    loaderDetalle.setVisibility(View.GONE);
                    manejarErrorReserva(error);
                }
        );
    }

    private void actualizarChipsCupos(int total, int disponibles) {
        if (layoutCupos == null || total <= 0 || total > 8) return;
        runOnUiThread(() -> {
            layoutCupos.removeAllViews();
            float d = getResources().getDisplayMetrics().density;

            // ── Detectar tipo de vehículo ─────────────────────────────────────
            // Si tienes el campo tipoVehiculo del backend, úsalo aquí.
            // Por ahora inferimos: si cuposTotales == 1 → moto.
            boolean esMoto = (total == 1);

            if (esMoto) {
                renderizarAsientosMoto(d);
            } else {
                renderizarAsientosCarro(total, disponibles, d);
            }

            // ── Barra de disponibilidad ───────────────────────────────────────
            agregarBarraDisponibilidad(total, disponibles, d);

            // ── Leyenda ───────────────────────────────────────────────────────
            agregarLeyendaAsientos(d);
        });
    }

    /** Layout de MOTO: conductor arriba, pasajero abajo */
    private void renderizarAsientosMoto(float d) {
        LinearLayout colMoto = new LinearLayout(this);
        colMoto.setOrientation(LinearLayout.VERTICAL);
        colMoto.setGravity(android.view.Gravity.CENTER);
        colMoto.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Marco visual de la moto
        LinearLayout marco = new LinearLayout(this);
        marco.setOrientation(LinearLayout.VERTICAL);
        marco.setGravity(android.view.Gravity.CENTER);
        int marcoW = (int)(130 * d);
        LinearLayout.LayoutParams lpMarco = new LinearLayout.LayoutParams(marcoW, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpMarco.setMargins(0, 0, 0, (int)(8*d));
        marco.setLayoutParams(lpMarco);
        marco.setPadding((int)(20*d), (int)(18*d), (int)(20*d), (int)(16*d));
        GradientDrawable bgMarco = new GradientDrawable();
        bgMarco.setShape(GradientDrawable.RECTANGLE);
        bgMarco.setCornerRadius(40*d);
        bgMarco.setColor(Color.parseColor("#F0F4F8"));
        bgMarco.setStroke((int)(2*d), Color.parseColor("#D0D8E0"));
        marco.setBackground(bgMarco);

        // Label MOTO
        TextView tvLabel = new TextView(this);
        tvLabel.setText("MOTO");
        tvLabel.setTextSize(9f); tvLabel.setTextColor(Color.parseColor("#8a95a0"));
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setLetterSpacing(0.1f);
        tvLabel.setGravity(android.view.Gravity.CENTER);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvLabel.setPadding(0, 0, 0, (int)(10*d));
        marco.addView(tvLabel);

        // Asiento conductor (bloqueado)
        marco.addView(crearAsientoView(SEAT_CONDUCTOR, "Conductor", false, d, null));

        // Separador
        View sep = new View(this);
        LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams((int)(1*d), (int)(14*d));
        lpSep.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lpSep.setMargins(0, (int)(4*d), 0, (int)(4*d));
        sep.setLayoutParams(lpSep);
        sep.setBackgroundColor(Color.parseColor("#c0c8d0"));
        marco.addView(sep);

        // Asiento pasajero moto
        boolean libreP = cuposDisponibles > 0;
        int estadoP = libreP ? SEAT_LIBRE : SEAT_OCUPADO;
        if (yaReservo && cupoSeleccionado >= 0) estadoP = SEAT_SELECCIONADO;
        marco.addView(crearAsientoView(estadoP, libreP ? "Libre" : "Ocupado", !esConductor && libreP && !yaReservo, d,
                v -> { cupoSeleccionado = 0; mostrarBottomSheetParada(); }));
        colMoto.addView(marco);
        layoutCupos.addView(colMoto);
    }

    /** Layout de CARRO: distribución [Conductor][Copiloto] + [T1][T2][T3] */
    private void renderizarAsientosCarro(int total, int disponibles, float d) {
        // Wrapper centrado con forma de carro
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpW = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpW.setMargins(0, 0, 0, (int)(8*d));
        wrapper.setLayoutParams(lpW);
        wrapper.setPadding((int)(16*d), (int)(16*d), (int)(16*d), (int)(20*d));
        GradientDrawable bgWrapper = new GradientDrawable();
        bgWrapper.setShape(GradientDrawable.RECTANGLE);
        // Parte delantera redondeada, trasera menos
        bgWrapper.setCornerRadii(new float[]{28*d,28*d,28*d,28*d, 18*d,18*d,18*d,18*d});
        bgWrapper.setColor(Color.parseColor("#F0F4F8"));
        bgWrapper.setStroke((int)(2*d), Color.parseColor("#D0D8E0"));
        wrapper.setBackground(bgWrapper);

        // Label PARTE DELANTERA
        TextView tvDelantero = new TextView(this);
        tvDelantero.setText("PARTE DELANTERA");
        tvDelantero.setTextSize(8f); tvDelantero.setTextColor(Color.parseColor("#8a95a0"));
        tvDelantero.setTypeface(null, android.graphics.Typeface.BOLD);
        tvDelantero.setLetterSpacing(0.1f);
        tvDelantero.setGravity(android.view.Gravity.CENTER);
        tvDelantero.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvDelantero.setPadding(0, 0, 0, (int)(8*d));
        wrapper.addView(tvDelantero);

        // Indicador parabrisas
        View parabrisas = new View(this);
        LinearLayout.LayoutParams lpP = new LinearLayout.LayoutParams((int)(60*d), (int)(3*d));
        lpP.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lpP.setMargins(0, 0, 0, (int)(10*d));
        parabrisas.setLayoutParams(lpP);
        GradientDrawable bgPB = new GradientDrawable();
        bgPB.setShape(GradientDrawable.RECTANGLE); bgPB.setCornerRadius(2*d);
        bgPB.setColor(Color.parseColor("#C5CDD6")); parabrisas.setBackground(bgPB);
        wrapper.addView(parabrisas);

        // ── FILA DELANTERA: Conductor + Copiloto ─────────────────────────────
        LinearLayout filaDelantera = new LinearLayout(this);
        filaDelantera.setOrientation(LinearLayout.HORIZONTAL);
        filaDelantera.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpFD = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFD.setMargins(0, 0, 0, (int)(8*d));
        filaDelantera.setLayoutParams(lpFD);

        // Conductor (siempre bloqueado)
        filaDelantera.addView(crearAsientoView(SEAT_CONDUCTOR, "Conductor", false, d, null));

        // Espacio central (simula consola del carro)
        View consolaCentral = new View(this);
        LinearLayout.LayoutParams lpCC = new LinearLayout.LayoutParams((int)(20*d), (int)(40*d));
        consolaCentral.setLayoutParams(lpCC);
        filaDelantera.addView(consolaCentral);

        // Copiloto (asiento índice 0)
        if (total >= 2) {
            int estCopiloto = obtenerEstadoAsiento(0, disponibles);
            boolean clicCopiloto = !esConductor && estCopiloto == SEAT_LIBRE && !yaReservo;
            boolean clicCopilotoReserva = !esConductor && yaReservo && cupoSeleccionado == 0;
            if (clicCopilotoReserva) estCopiloto = SEAT_SELECCIONADO;
            final int idxC = 0;
            filaDelantera.addView(crearAsientoView(estCopiloto,
                    estCopiloto == SEAT_SELECCIONADO ? "Tuyo" : (estCopiloto == SEAT_LIBRE ? "Libre" : "Ocupado"),
                    clicCopiloto, d,
                    v -> { cupoSeleccionado = idxC; mostrarBottomSheetParada(); }));
        }
        wrapper.addView(filaDelantera);

        // Separador central (simula "piso" del carro)
        View pisoCarro = new View(this);
        LinearLayout.LayoutParams lpPiso = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1.5f*d));
        lpPiso.setMargins(0, (int)(4*d), 0, (int)(8*d));
        pisoCarro.setLayoutParams(lpPiso);
        pisoCarro.setBackgroundColor(Color.parseColor("#C5CDD680"));
        wrapper.addView(pisoCarro);

        // ── FILA TRASERA ─────────────────────────────────────────────────────
        int numTraseros = Math.min(total - 1, 3); // Máx 3 traseros
        if (numTraseros > 0) {
            LinearLayout filaTrasera = new LinearLayout(this);
            filaTrasera.setOrientation(LinearLayout.HORIZONTAL);
            filaTrasera.setGravity(android.view.Gravity.CENTER);
            filaTrasera.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            for (int i = 0; i < numTraseros; i++) {
                int idxAsiento = i + 1; // índice 1,2,3 (el 0 es copiloto)
                int estadoT = obtenerEstadoAsiento(idxAsiento, disponibles);
                boolean clic = !esConductor && estadoT == SEAT_LIBRE && !yaReservo;
                boolean esElMio = yaReservo && cupoSeleccionado == idxAsiento;
                if (esElMio) estadoT = SEAT_SELECCIONADO;
                final int idxFinal = idxAsiento;
                filaTrasera.addView(crearAsientoView(estadoT,
                        esElMio ? "Tuyo" : (estadoT == SEAT_LIBRE ? "Libre" : "Ocupado"),
                        clic, d,
                        v -> { cupoSeleccionado = idxFinal; mostrarBottomSheetParada(); }));

                // Pequeño espacio entre asientos traseros
                if (i < numTraseros - 1) {
                    View gap = new View(this);
                    gap.setLayoutParams(new LinearLayout.LayoutParams((int)(6*d), 1));
                    filaTrasera.addView(gap);
                }
            }
            wrapper.addView(filaTrasera);
        }

        // Label PARTE TRASERA
        View separadorTrasero = new View(this);
        LinearLayout.LayoutParams lpST = new LinearLayout.LayoutParams((int)(60*d), (int)(3*d));
        lpST.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lpST.setMargins(0, (int)(10*d), 0, (int)(6*d));
        separadorTrasero.setLayoutParams(lpST);
        GradientDrawable bgST = new GradientDrawable();
        bgST.setShape(GradientDrawable.RECTANGLE); bgST.setCornerRadius(2*d);
        bgST.setColor(Color.parseColor("#C5CDD6")); separadorTrasero.setBackground(bgST);
        wrapper.addView(separadorTrasero);

        TextView tvTrasero = new TextView(this);
        tvTrasero.setText("PARTE TRASERA");
        tvTrasero.setTextSize(8f); tvTrasero.setTextColor(Color.parseColor("#8a95a0"));
        tvTrasero.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTrasero.setLetterSpacing(0.1f);
        tvTrasero.setGravity(android.view.Gravity.CENTER);
        tvTrasero.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        wrapper.addView(tvTrasero);

        layoutCupos.addView(wrapper);
    }

    /**
     * Calcula el estado visual de un asiento según su índice y cuántos están disponibles.
     * Simple heurística: los primeros `disponibles` asientos están libres.
     */
    private int obtenerEstadoAsiento(int idx, int disponibles) {
        // Si el viaje ya inició → ocupado (rojo)
        boolean viajeIniciado = "EN_CURSO".equals(estadoViaje) || "INICIADO".equals(estadoViaje);

        if (idx < (cuposTotales - disponibles)) {
            // Este asiento está tomado
            return viajeIniciado ? SEAT_OCUPADO : SEAT_RESERVADO;
        }
        return SEAT_LIBRE;
    }

    private View crearAsientoView(int estado, String etiqueta, boolean clickable, float d,
                                  View.OnClickListener onClick) {
        int seatSize = (int)(52 * d);
        int colorFondo, colorBorde;
        switch (estado) {
            case SEAT_OCUPADO:
                colorFondo = COLOR_SEAT_OCUPADO; colorBorde = COLOR_SEAT_BORDER_OCUPADO; break;
            case SEAT_RESERVADO:
                colorFondo = COLOR_SEAT_RESERVADO; colorBorde = COLOR_SEAT_BORDER_RESERVADO; break;
            case SEAT_SELECCIONADO:
                colorFondo = COLOR_SEAT_SELECCIONADO; colorBorde = COLOR_SEAT_BORDER_SELECCIONADO; break;
            case SEAT_CONDUCTOR:
                colorFondo = COLOR_SEAT_CONDUCTOR; colorBorde = COLOR_SEAT_BORDER_CONDUCTOR; break;
            case SEAT_RESERVANDO:
                colorFondo = COLOR_SEAT_RESERVANDO; colorBorde = COLOR_SEAT_BORDER_RESERVANDO; break;
            default:
                colorFondo = COLOR_SEAT_LIBRE; colorBorde = COLOR_SEAT_BORDER_LIBRE; break;
        }

        LinearLayout asientoBox = new LinearLayout(this);
        asientoBox.setOrientation(LinearLayout.VERTICAL);
        asientoBox.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpBox = new LinearLayout.LayoutParams(
                (int)(64*d), LinearLayout.LayoutParams.WRAP_CONTENT);
        lpBox.setMargins((int)(4*d), 0, (int)(4*d), 0);
        asientoBox.setLayoutParams(lpBox);

        // ── Ícono del asiento ─────────────────────────────────────────────────
        android.widget.FrameLayout iconFrame = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams lpIF = new LinearLayout.LayoutParams(seatSize, seatSize);
        lpIF.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        iconFrame.setLayoutParams(lpIF);

        // Fondo del asiento
        GradientDrawable bgAsiento = new GradientDrawable();
        bgAsiento.setShape(GradientDrawable.RECTANGLE);
        bgAsiento.setCornerRadius(10 * d);
        bgAsiento.setColor(colorFondo);
        bgAsiento.setStroke((int)(2.5f * d), colorBorde);
        iconFrame.setBackground(bgAsiento);

        // SVG → Bitmap del ícono de silla
        android.graphics.Bitmap bmpSilla = crearBitmapSilla(colorFondo, d);
        android.widget.ImageView ivSilla = new android.widget.ImageView(this);
        ivSilla.setImageBitmap(bmpSilla);
        ivSilla.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        iconFrame.addView(ivSilla);

        // ── Check dorado para seleccionado ────────────────────────────────────
        if (estado == SEAT_SELECCIONADO) {
            View checkCircle = new View(this);
            int checkSize = (int)(16 * d);
            android.widget.FrameLayout.LayoutParams lpCheck =
                    new android.widget.FrameLayout.LayoutParams(checkSize, checkSize);
            lpCheck.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            lpCheck.setMargins(0, (int)(3*d), (int)(3*d), 0);
            checkCircle.setLayoutParams(lpCheck);
            GradientDrawable bgCheck = new GradientDrawable();
            bgCheck.setShape(GradientDrawable.OVAL);
            bgCheck.setColor(Color.WHITE);
            checkCircle.setBackground(bgCheck);
            // Dibujar ✓ encima
            TextView tvCheck = new TextView(this);
            tvCheck.setText("✓");
            tvCheck.setTextSize(8f);
            tvCheck.setTextColor(COLOR_SEAT_SELECCIONADO);
            tvCheck.setTypeface(null, android.graphics.Typeface.BOLD);
            tvCheck.setGravity(android.view.Gravity.CENTER);
            tvCheck.setLayoutParams(lpCheck);
            iconFrame.addView(tvCheck);
        }

        // ── Ícono de persona para OCUPADO ─────────────────────────────────────
        if (estado == SEAT_OCUPADO) {
            TextView tvPersona = new TextView(this);
            tvPersona.setText("👤");
            tvPersona.setTextSize(14f);
            tvPersona.setGravity(android.view.Gravity.CENTER);
            android.widget.FrameLayout.LayoutParams lpPer =
                    new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
            tvPersona.setLayoutParams(lpPer);
            iconFrame.addView(tvPersona);
        }

        asientoBox.addView(iconFrame);

        // ── Etiqueta debajo ───────────────────────────────────────────────────
        TextView tvEtiqueta = new TextView(this);
        tvEtiqueta.setText(etiqueta);
        tvEtiqueta.setTextSize(9f);
        tvEtiqueta.setTypeface(null, android.graphics.Typeface.BOLD);
        tvEtiqueta.setTextColor(colorFondo);
        tvEtiqueta.setGravity(android.view.Gravity.CENTER);
        tvEtiqueta.setMaxLines(1);
        tvEtiqueta.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams lpEtq = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpEtq.topMargin = (int)(4 * d);
        tvEtiqueta.setLayoutParams(lpEtq);
        asientoBox.addView(tvEtiqueta);

        // ── Click listener ────────────────────────────────────────────────────
        if (clickable && onClick != null) {
            asientoBox.setClickable(true);
            asientoBox.setFocusable(true);
            android.util.TypedValue tv2 = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv2, true);
            asientoBox.setForeground(getResources().getDrawable(tv2.resourceId, getTheme()));
            asientoBox.setOnClickListener(v -> {
                // Animación de tap
                v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                        .start();
                onClick.onClick(v);
            });
        }

        return asientoBox;
    }

    /** Genera el bitmap del ícono de silla de carro (vista superior) */
    private android.graphics.Bitmap crearBitmapSilla(int colorFondo, float d) {
        int size = (int)(48 * d);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setAlpha(230);

        float cx = size / 2f;
        // Respaldo
        canvas.drawRoundRect(cx - size*0.33f, size*0.08f, cx + size*0.33f, size*0.50f,
                size*0.18f, size*0.18f, paint);
        // Asiento
        canvas.drawRoundRect(cx - size*0.37f, size*0.50f, cx + size*0.37f, size*0.80f,
                size*0.12f, size*0.12f, paint);
        // Pata izquierda
        canvas.drawRoundRect(cx - size*0.37f, size*0.78f, cx - size*0.20f, size*0.95f,
                size*0.08f, size*0.08f, paint);
        // Pata derecha
        canvas.drawRoundRect(cx + size*0.20f, size*0.78f, cx + size*0.37f, size*0.95f,
                size*0.08f, size*0.08f, paint);
        return bmp;
    }

    /** Barra de progreso disponibles/ocupados con animación */
    private void agregarBarraDisponibilidad(int total, int disponibles, float d) {
        int ocupados = total - disponibles;
        int p4 = (int)(4*d), p6 = (int)(6*d), p8 = (int)(8*d), p14 = (int)(14*d);

        // Fila de contadores
        LinearLayout rowCount = new LinearLayout(this);
        rowCount.setOrientation(LinearLayout.HORIZONTAL);
        rowCount.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpRow.setMargins(p4, p8, p4, p6); rowCount.setLayoutParams(lpRow);

        TextView tvDisp = new TextView(this);
        tvDisp.setText("✅ " + disponibles + " disponible" + (disponibles != 1 ? "s" : ""));
        tvDisp.setTextSize(12f); tvDisp.setTypeface(null, android.graphics.Typeface.BOLD);
        tvDisp.setTextColor(Color.parseColor("#2E7D32"));
        tvDisp.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        rowCount.addView(tvDisp);

        TextView tvOcup = new TextView(this);
        tvOcup.setText(ocupados + " ocupado" + (ocupados != 1 ? "s" : "") + " 🔒");
        tvOcup.setTextSize(12f); tvOcup.setTypeface(null, android.graphics.Typeface.BOLD);
        tvOcup.setTextColor(Color.parseColor("#B71C1C"));
        tvOcup.setGravity(android.view.Gravity.END);
        tvOcup.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        rowCount.addView(tvOcup);
        layoutCupos.addView(rowCount);

        // Barra animada
        android.widget.FrameLayout barFrame = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(8*d));
        lpBar.setMargins(p4, 0, p4, p6); barFrame.setLayoutParams(lpBar);

        View barBg = new View(this);
        barBg.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setShape(GradientDrawable.RECTANGLE); bgShape.setCornerRadius(4*d);
        bgShape.setColor(Color.parseColor("#E0E0E0")); barBg.setBackground(bgShape);

        View barFill = new View(this);
        android.widget.FrameLayout.LayoutParams fillLp =
                new android.widget.FrameLayout.LayoutParams(0,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        barFill.setLayoutParams(fillLp);
        GradientDrawable fillShape = new GradientDrawable();
        fillShape.setShape(GradientDrawable.RECTANGLE); fillShape.setCornerRadius(4*d);
        int colorBarra = disponibles > total / 2
                ? Color.parseColor("#43A047") : Color.parseColor("#FB8C00");
        fillShape.setColor(colorBarra); barFill.setBackground(fillShape);

        barFrame.addView(barBg); barFrame.addView(barFill);
        layoutCupos.addView(barFrame);

        // Animación con post
        final int fTotal = total, fDisp = disponibles;
        barFrame.post(() -> {
            int totalW = barFrame.getWidth();
            int targetW = totalW * fDisp / fTotal;
            android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(0, targetW);
            anim.setDuration(700);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addUpdateListener(a -> {
                android.widget.FrameLayout.LayoutParams lp2 =
                        (android.widget.FrameLayout.LayoutParams) barFill.getLayoutParams();
                lp2.width = (int) a.getAnimatedValue(); barFill.setLayoutParams(lp2);
            });
            anim.start();
        });
    }

    /** Leyenda de colores */
    private void agregarLeyendaAsientos(float d) {
        LinearLayout leyenda = new LinearLayout(this);
        leyenda.setOrientation(LinearLayout.HORIZONTAL);
        leyenda.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpL.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lpL.topMargin = (int)(4*d);
        leyenda.setLayoutParams(lpL);

        boolean viajeIniciado = "EN_CURSO".equals(estadoViaje) || "INICIADO".equals(estadoViaje);

        if (!esConductor && yaReservo && cupoSeleccionado >= 0) {
            leyenda.addView(crearItemLeyenda(COLOR_SEAT_LIBRE,       "Libre",      d));
            leyenda.addView(crearItemLeyenda(
                    viajeIniciado ? COLOR_SEAT_OCUPADO : COLOR_SEAT_RESERVADO,
                    viajeIniciado ? "Ocupado" : "Reservado",           d));
            leyenda.addView(crearItemLeyenda(COLOR_SEAT_SELECCIONADO, "Tuyo",       d));
            leyenda.addView(crearItemLeyenda(COLOR_SEAT_CONDUCTOR,    "Conductor",  d));
        } else {
            leyenda.addView(crearItemLeyenda(COLOR_SEAT_LIBRE,     "Libre",     d));
            leyenda.addView(crearItemLeyenda(
                    viajeIniciado ? COLOR_SEAT_OCUPADO : COLOR_SEAT_RESERVADO,
                    viajeIniciado ? "Ocupado" : "Reservado",       d));
            leyenda.addView(crearItemLeyenda(COLOR_SEAT_CONDUCTOR, "Conductor", d));
        }
        layoutCupos.addView(leyenda);
    }

    private View crearItemLeyenda(int color, String label, float d) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpItem = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpItem.setMargins((int)(6*d), 0, (int)(6*d), 0);
        item.setLayoutParams(lpItem);

        View dot = new View(this);
        LinearLayout.LayoutParams lpDot = new LinearLayout.LayoutParams((int)(10*d), (int)(10*d));
        lpDot.setMargins(0, 0, (int)(4*d), 0);
        dot.setLayoutParams(lpDot);
        GradientDrawable bgDot = new GradientDrawable();
        bgDot.setShape(GradientDrawable.RECTANGLE); bgDot.setCornerRadius(3*d);
        bgDot.setColor(color); dot.setBackground(bgDot);
        item.addView(dot);

        TextView tv = new TextView(this);
        tv.setText(label); tv.setTextSize(11f);
        tv.setTextColor(Color.parseColor("#607D8B"));
        item.addView(tv);
        return item;
    }

    private boolean esMioCheck(){ return yaReservo && cupoSeleccionado >= 0; }

    // =========================================================================
    //  POLLING GENERAL (estado del viaje)
    // =========================================================================
    private void iniciarPolling(){
        if(pollingActivo) return;
        // Pulsar el dot del badge EN VIVO
        View dotVivo = findViewById(R.id.dot_en_vivo);
        if (dotVivo != null) {
            android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator
                    .ofFloat(dotVivo, "alpha", 1f, 0.2f);
            pulse.setDuration(800);
            pulse.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            pulse.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
            pulse.start();
        }
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
                            // ← Usar precio calculado del tramo si existe, sino el base
                            double montoF = precioViaje;
                            if (precioCalculadoPasajero > 0) montoF = precioCalculadoPasajero;
                            else if (precioCalculadoPersistente > 0) montoF = precioCalculadoPersistente;

                            final double finalM = montoF;
                            runOnUiThread(() -> {
                                mostrarBotonPago(finalM, nombreConductorViaje);
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

    private void paso2FinalizarYCalificar() {
        ConexionApi.getInstance(this).post(
                Constantes.viajeFinalizar((long) viajeId), null,
                r2 -> {
                    loaderDetalle.setVisibility(View.GONE);
                    Toast.makeText(this, "✅ Viaje finalizado.", Toast.LENGTH_LONG).show();
                    estadoViaje = "FINALIZADO";
                    cargarDetalleViaje();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (esConductor) mostrarBottomSheetCobro();
                        // Pasajero: el botón Pagar aparece en la UI automáticamente
                    }, 1500);
                },
                e2 -> {
                    loaderDetalle.setVisibility(View.GONE);
                    Toast.makeText(this, "Error finalizando", Toast.LENGTH_LONG).show();
                }
        );
    }

    private void dispararCalificacionSegunRol() {
        if (calificacionYaDisparada) return;
        calificacionYaDisparada = true;
        if (esConductor) {
            mostrarBottomSheetCobro(); // Paso 1: cobro
        } else {
            dispararCalificacionAlConductor(); // Pasajero: califica al conductor
        }
    }

    private void mostrarBottomSheetCobro() {
        if (!esConductor) return;
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) viajeId),
                viajeObj -> {
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    ArrayList<JSONObject> pasajerosActivos = new ArrayList<>();
                    if (usuarios != null) {
                        for (int i = 0; i < usuarios.length(); i++) {
                            JSONObject u = usuarios.optJSONObject(i);
                            if (u == null) continue;
                            String est = u.optString("estado", "").toUpperCase().trim();
                            if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;
                            pasajerosActivos.add(u);
                        }
                    }
                    runOnUiThread(() -> mostrarBottomSheetCobroConPasajeros(pasajerosActivos));
                },
                err -> runOnUiThread(() -> mostrarBottomSheetCobroConPasajeros(new ArrayList<>()))
        );
    }

    private void mostrarBottomSheetCobroConPasajeros(ArrayList<JSONObject> pasajeros) {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.BottomSheetTheme);
        float dp = getResources().getDisplayMetrics().density;
        int p16 = (int)(16*dp), p12 = (int)(12*dp), p8 = (int)(8*dp), p24 = (int)(24*dp);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p16, p16, p16, p24);

        root.addView(crearTiron(dp, p12));

        // Título
        TextView tvTitulo = new TextView(this);
        tvTitulo.setText("Cobro del viaje");
        tvTitulo.setTextSize(22f);
        tvTitulo.setTypeface(null, Typeface.BOLD);
        tvTitulo.setTextColor(Color.parseColor("#004D40"));
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpT.setMargins(0, 0, 0, (int)(4*dp));
        tvTitulo.setLayoutParams(lpT);
        root.addView(tvTitulo);

        TextView tvSub = new TextView(this);
        tvSub.setText("Confirma el pago de cada pasajero antes de calificar");
        tvSub.setTextSize(13f);
        tvSub.setTextColor(Color.parseColor("#546E7A"));
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSub.setMargins(0, 0, 0, p16);
        tvSub.setLayoutParams(lpSub);
        root.addView(tvSub);

        View sep0 = new View(this);
        LinearLayout.LayoutParams lpS0 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1*dp));
        lpS0.setMargins(0, 0, 0, p12);
        sep0.setLayoutParams(lpS0);
        sep0.setBackgroundColor(Color.parseColor("#E0F2F1"));
        root.addView(sep0);

        final int[] pagosConfirmados = {0};
        final int totalPasajeros = pasajeros.isEmpty() ? 1 : pasajeros.size();

        // Botón continuar (se crea primero para pasarlo como referencia)
        MaterialButton btnContinuar = new MaterialButton(this);
        btnContinuar.setText("CONTINUAR A CALIFICAR");
        btnContinuar.setTextSize(14f);
        btnContinuar.setTextColor(Color.WHITE);
        btnContinuar.setCornerRadius((int)(14*dp));
        btnContinuar.setEnabled(false);
        btnContinuar.setAlpha(0.5f);
        btnContinuar.setBackgroundColor(Color.parseColor("#B0BEC5"));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(52*dp));
        lpBtn.setMargins(0, p8, 0, p8);
        btnContinuar.setLayoutParams(lpBtn);

        if (pasajeros.isEmpty()) {
            crearCardCobroPasajeroConVerificacion(root, "Pasajero", -1, precioViaje,
                    pagosConfirmados, totalPasajeros, btnContinuar, dp);
        } else {
            for (JSONObject u : pasajeros) {
                String nombre = "";
                JSONObject usuObj = u.optJSONObject("usuario");
                if (usuObj != null) {
                    for (String k : new String[]{"nombre","nombreCompleto","name","nombres"}) {
                        String v = usuObj.optString(k, "");
                        if (!v.isEmpty() && !v.equals("null")) { nombre = v; break; }
                    }
                }
                if (nombre.isEmpty()) nombre = u.optString("nombrePasajero",
                        u.optString("nombre", "Pasajero"));

                double precio = u.optDouble("precioFinal",
                        u.optDouble("precioTramo",
                                u.optDouble("precio",
                                        u.optDouble("costoPorPasajero",
                                                u.optDouble("monto", 0)))));
                if (precio <= 0) precio = precioViaje;

                int idPas = -1;
                if (usuObj != null) {
                    for (String k : new String[]{"idUsuarios","id","idUsuario"}) {
                        int id = usuObj.optInt(k, -1);
                        if (id > 0) { idPas = id; break; }
                    }
                }
                if (idPas <= 0) {
                    for (String k : new String[]{"idUsuarios","idUsuario","idPasajero"}) {
                        int id = u.optInt(k, -1);
                        if (id > 0) { idPas = id; break; }
                    }
                }

                crearCardCobroPasajeroConVerificacion(root, nombre, idPas, precio,
                        pagosConfirmados, totalPasajeros, btnContinuar, dp);
            }
        }

        // Nota
        TextView tvNota = new TextView(this);
        tvNota.setText("Una vez confirmados los pagos podrás calificar a los pasajeros");
        tvNota.setTextSize(12f);
        tvNota.setTextColor(Color.parseColor("#78909C"));
        tvNota.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpN = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpN.setMargins(0, p12, 0, p8);
        tvNota.setLayoutParams(lpN);
        root.addView(tvNota);

        final BottomSheetDialog fSheet = sheet;
        btnContinuar.setOnClickListener(v -> {
            fSheet.dismiss();
            new Handler(Looper.getMainLooper()).postDelayed(
                    this::paso3BuscarPasajerosParaCalificar, 400);
        });
        root.addView(btnContinuar);

        // Saltar
        TextView tvSaltar = new TextView(this);
        tvSaltar.setText("Saltar y calificar directamente");
        tvSaltar.setTextSize(13f);
        tvSaltar.setTextColor(Color.parseColor("#00897B"));
        tvSaltar.setGravity(android.view.Gravity.CENTER);
        tvSaltar.setPadding(0, p8, 0, p8);
        tvSaltar.setClickable(true);
        tvSaltar.setFocusable(true);
        tvSaltar.setOnClickListener(v -> {
            fSheet.dismiss();
            new Handler(Looper.getMainLooper()).postDelayed(
                    this::paso3BuscarPasajerosParaCalificar, 400);
        });
        root.addView(tvSaltar);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(root);
        sheet.setContentView(sv);
        sheet.show();
    }

    private void crearCardCobroPasajeroConVerificacion(LinearLayout root, String nombre,
                                                       int idPasajero, double precio,
                                                       int[] pagosConfirmados, int total,
                                                       MaterialButton btnContinuar, float dp) {
        int p12=(int)(12*dp), p8=(int)(8*dp), p6=(int)(6*dp), p4=(int)(4*dp);
        java.text.NumberFormat nf = java.text.NumberFormat
                .getNumberInstance(new java.util.Locale("es", "CO"));

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, 0, 0, p8);
        card.setLayoutParams(lpCard);
        card.setRadius(16*dp);
        card.setCardElevation(0);
        card.setCardBackgroundColor(Color.parseColor("#FAFAFA"));
        card.setStrokeWidth((int)(1.5f*dp));
        card.setStrokeColor(Color.parseColor("#E0E0E0"));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p12, p12, p12, p12);

        LinearLayout fila1 = new LinearLayout(this);
        fila1.setOrientation(LinearLayout.HORIZONTAL);
        fila1.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpF1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpF1.setMargins(0, 0, 0, p8);
        fila1.setLayoutParams(lpF1);

        TextView avatar = new TextView(this);
        LinearLayout.LayoutParams lpAv = new LinearLayout.LayoutParams((int)(40*dp),(int)(40*dp));
        lpAv.setMargins(0, 0, p8, 0);
        avatar.setLayoutParams(lpAv);
        avatar.setGravity(android.view.Gravity.CENTER);
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(16f);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setText(nombre.isEmpty() ? "P" : nombre.substring(0,1).toUpperCase());
        GradientDrawable bgAv = new GradientDrawable();
        bgAv.setShape(GradientDrawable.OVAL);
        bgAv.setColor(Color.parseColor("#00897B"));
        avatar.setBackground(bgAv);
        fila1.addView(avatar);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvNom = new TextView(this);
        tvNom.setText(nombre);
        tvNom.setTextSize(15f);
        tvNom.setTypeface(null, Typeface.BOLD);
        tvNom.setTextColor(Color.parseColor("#004D40"));
        col.addView(tvNom);

        TextView tvPrecio = new TextView(this);
        tvPrecio.setText("$" + nf.format(precio) + " COP");
        tvPrecio.setTextSize(13f);
        tvPrecio.setTextColor(Color.parseColor("#00897B"));
        tvPrecio.setTypeface(null, Typeface.BOLD);
        col.addView(tvPrecio);
        fila1.addView(col);

        final TextView badge = new TextView(this);
        badge.setText("Verificando...");
        badge.setTextSize(11f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setTextColor(Color.WHITE);
        badge.setPadding(p8, (int)(4*dp), p8, (int)(4*dp));
        GradientDrawable bgBadge = new GradientDrawable();
        bgBadge.setShape(GradientDrawable.RECTANGLE);
        bgBadge.setCornerRadius(20*dp);
        bgBadge.setColor(Color.parseColor("#90A4AE"));
        badge.setBackground(bgBadge);
        fila1.addView(badge);
        inner.addView(fila1);

        LinearLayout areaAccion = new LinearLayout(this);
        areaAccion.setOrientation(LinearLayout.VERTICAL);
        areaAccion.setVisibility(View.GONE);
        inner.addView(areaAccion);

        card.addView(inner);
        root.addView(card);

        if (idPasajero <= 0) {
            runOnUiThread(() -> configurarCardPendiente(areaAccion, badge, bgBadge));
            return;
        }

        // ── Verificar usando el endpoint de pagos del viaje (más confiable) ──
        String urlPagos = Constantes.pagosPorViaje((long) viajeId);
        ConexionApi.getInstance(this).getArrayNoCache(urlPagos,
                pagosArr -> {
                    // Buscar el pago de este pasajero específico
                    JSONObject pagoEncontrado = null;
                    for (int i = 0; i < pagosArr.length(); i++) {
                        JSONObject p = pagosArr.optJSONObject(i);
                        if (p == null) continue;
                        int idU = -1;
                        for (String k : new String[]{"idUsuario","idPasajero","usuarioId","pasajeroId"}) {
                            int id = p.optInt(k, -1);
                            if (id > 0) { idU = id; break; }
                        }
                        if (idU <= 0) {
                            JSONObject uObj = p.optJSONObject("usuario");
                            if (uObj == null) uObj = p.optJSONObject("pasajero");
                            if (uObj != null) {
                                for (String k : new String[]{"id","idUsuarios","idUsuario"}) {
                                    int id = uObj.optInt(k, -1);
                                    if (id > 0) { idU = id; break; }
                                }
                            }
                        }
                        if (idU == idPasajero) {
                            pagoEncontrado = p;
                            break;
                        }
                    }

                    if (pagoEncontrado == null) {
                        // Intentar fallback con endpoint individual
                        buscarPagoIndividual(idPasajero, precio, nf, tvPrecio,
                                badge, bgBadge, bgAv, avatar, card, areaAccion,
                                pagosConfirmados, total, btnContinuar, dp);
                        return;
                    }

                    final JSONObject fPago = pagoEncontrado;
                    long   idPagoFound = fPago.optLong("idPago", fPago.optLong("id", -1));
                    boolean cp = fPago.optBoolean("confirmacionPasajero", false);
                    boolean cc = fPago.optBoolean("confirmacionConductor", false);
                    double montoReal = fPago.optDouble("monto", precio);
                    if (montoReal <= 0) montoReal = precio;
                    final double fMonto = montoReal;

                    runOnUiThread(() -> tvPrecio.setText("$" + nf.format(fMonto) + " COP"));

                    if (cc) {
                        runOnUiThread(() -> marcarCardPagadaDetalle(
                                card, badge, bgBadge, bgAv, avatar,
                                areaAccion, pagosConfirmados, total, btnContinuar, dp));
                    } else if (cp) {
                        runOnUiThread(() -> {
                            badge.setText("Pasajero confirmó ✓");
                            bgBadge.setColor(Color.parseColor("#1565C0"));
                            badge.setBackground(bgBadge);
                            configurarBtnConfirmarConductor(areaAccion, badge, bgBadge,
                                    card, bgAv, avatar, idPagoFound,
                                    pagosConfirmados, total, btnContinuar, dp);
                        });
                    } else {
                        runOnUiThread(() -> configurarCardPendiente(areaAccion, badge, bgBadge));
                    }
                },
                // Fallback si el array falla
                errArr -> buscarPagoIndividual(idPasajero, precio, nf, tvPrecio,
                        badge, bgBadge, bgAv, avatar, card, areaAccion,
                        pagosConfirmados, total, btnContinuar, dp)
        );
    }

    // Fallback: buscar pago por endpoint individual del pasajero
    private void buscarPagoIndividual(int idPasajero, double precioBase,
                                      java.text.NumberFormat nf, TextView tvPrecio,
                                      TextView badge, GradientDrawable bgBadge,
                                      GradientDrawable bgAv, TextView avatar,
                                      MaterialCardView card, LinearLayout areaAccion,
                                      int[] pagosConfirmados, int total,
                                      MaterialButton btnContinuar, float dp) {

        String url = Constantes.pagoDeUsuarioEnViaje((long) viajeId, (long) idPasajero);
        ConexionApi.getInstance(this).getObjectNoCache(url,
                pagoFound -> {
                    long   idPagoFound = pagoFound.optLong("idPago", pagoFound.optLong("id", -1));
                    boolean cp = pagoFound.optBoolean("confirmacionPasajero", false);
                    boolean cc = pagoFound.optBoolean("confirmacionConductor", false);
                    double montoReal = pagoFound.optDouble("monto", 0);
                    if (montoReal <= 0) montoReal = precioBase;
                    final double fMonto = montoReal;

                    runOnUiThread(() -> tvPrecio.setText("$" + nf.format(fMonto) + " COP"));

                    if (cc) {
                        runOnUiThread(() -> marcarCardPagadaDetalle(
                                card, badge, bgBadge, bgAv, avatar,
                                areaAccion, pagosConfirmados, total, btnContinuar, dp));
                    } else if (cp) {
                        runOnUiThread(() -> {
                            badge.setText("Pasajero confirmó ✓");
                            bgBadge.setColor(Color.parseColor("#1565C0"));
                            badge.setBackground(bgBadge);
                            configurarBtnConfirmarConductor(areaAccion, badge, bgBadge,
                                    card, bgAv, avatar, idPagoFound,
                                    pagosConfirmados, total, btnContinuar, dp);
                        });
                    } else {
                        runOnUiThread(() -> configurarCardPendiente(areaAccion, badge, bgBadge));
                    }
                },
                error -> {
                    int code = (error != null && error.networkResponse != null)
                            ? error.networkResponse.statusCode : 0;
                    // Sin importar el código de error: mostrar pendiente sin calcular nada
                    runOnUiThread(() -> configurarCardPendiente(areaAccion, badge, bgBadge));
                }
        );
    }


    private void configurarCardPendiente(LinearLayout areaAccion, TextView badge,
                                         GradientDrawable bgBadge) {
        badge.setText("Pendiente");
        bgBadge.setColor(Color.parseColor("#F57F17"));
        badge.setBackground(bgBadge);
        TextView tvEspera = new TextView(this);
        tvEspera.setText("⏳ El pasajero aún no ha confirmado. Pídele que confirme desde su app.");
        tvEspera.setTextSize(12f);
        tvEspera.setTextColor(Color.parseColor("#546E7A"));
        areaAccion.addView(tvEspera);
        areaAccion.setVisibility(View.VISIBLE);
    }

    private void configurarBtnConfirmarConductor(LinearLayout areaAccion, TextView badge,
                                                 GradientDrawable bgBadge, MaterialCardView card,
                                                 GradientDrawable bgAv, TextView avatar,
                                                 long idPago, int[] pagosConfirmados, int total,
                                                 MaterialButton btnContinuar, float dp) {
        int p6=(int)(6*dp), p8=(int)(8*dp);
        areaAccion.removeAllViews();

        TextView tvLabel = new TextView(this);
        tvLabel.setText("¿Cómo te pagó?");
        tvLabel.setTextSize(12f);
        tvLabel.setTextColor(Color.parseColor("#546E7A"));
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpL.setMargins(0, 0, 0, p6);
        tvLabel.setLayoutParams(lpL);
        areaAccion.addView(tvLabel);

        LinearLayout filaBotones = new LinearLayout(this);
        filaBotones.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpFB = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFB.setMargins(0, 0, 0, p8);
        filaBotones.setLayoutParams(lpFB);

        final String[] metodo = {""};

        MaterialButton btnEf = new MaterialButton(this);
        btnEf.setText("Efectivo");
        btnEf.setTextSize(12f);
        btnEf.setTextColor(Color.parseColor("#004D40"));
        btnEf.setCornerRadius((int)(10*dp));
        btnEf.setStrokeColor(android.content.res.ColorStateList
                .valueOf(Color.parseColor("#80CBC4")));
        btnEf.setStrokeWidth((int)(1.5f*dp));
        btnEf.setBackgroundColor(Color.parseColor("#E0F2F1"));
        LinearLayout.LayoutParams lpBE = new LinearLayout.LayoutParams(0, (int)(40*dp), 1f);
        lpBE.setMargins(0, 0, p6, 0);
        btnEf.setLayoutParams(lpBE);
        filaBotones.addView(btnEf);

        MaterialButton btnTr = new MaterialButton(this);
        btnTr.setText("Transferencia");
        btnTr.setTextSize(12f);
        btnTr.setTextColor(Color.parseColor("#004D40"));
        btnTr.setCornerRadius((int)(10*dp));
        btnTr.setStrokeColor(android.content.res.ColorStateList
                .valueOf(Color.parseColor("#80CBC4")));
        btnTr.setStrokeWidth((int)(1.5f*dp));
        btnTr.setBackgroundColor(Color.parseColor("#E0F2F1"));
        btnTr.setLayoutParams(new LinearLayout.LayoutParams(0, (int)(40*dp), 1f));
        filaBotones.addView(btnTr);
        areaAccion.addView(filaBotones);

        MaterialButton btnConf = new MaterialButton(this);
        btnConf.setText("CONFIRMAR PAGO RECIBIDO");
        btnConf.setTextSize(13f);
        btnConf.setTextColor(Color.WHITE);
        btnConf.setCornerRadius((int)(12*dp));
        btnConf.setBackgroundColor(Color.parseColor("#B0BEC5"));
        btnConf.setEnabled(false);
        btnConf.setAlpha(0.5f);
        btnConf.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(46*dp)));
        areaAccion.addView(btnConf);
        areaAccion.setVisibility(View.VISIBLE);

        Runnable actualizarSeleccion = () -> {
            boolean esEf = "efectivo".equals(metodo[0]);
            boolean esTr = "transferencia".equals(metodo[0]);
            btnEf.setBackgroundColor(esEf
                    ? Color.parseColor("#00897B") : Color.parseColor("#E0F2F1"));
            btnEf.setTextColor(esEf ? Color.WHITE : Color.parseColor("#004D40"));
            btnTr.setBackgroundColor(esTr
                    ? Color.parseColor("#00897B") : Color.parseColor("#E0F2F1"));
            btnTr.setTextColor(esTr ? Color.WHITE : Color.parseColor("#004D40"));
            boolean hay = !metodo[0].isEmpty();
            btnConf.setEnabled(hay);
            btnConf.setAlpha(hay ? 1f : 0.5f);
            btnConf.setBackgroundColor(hay
                    ? Color.parseColor("#00897B") : Color.parseColor("#B0BEC5"));
        };

        btnEf.setOnClickListener(v -> { metodo[0] = "efectivo";      actualizarSeleccion.run(); });
        btnTr.setOnClickListener(v -> { metodo[0] = "transferencia"; actualizarSeleccion.run(); });

        btnConf.setOnClickListener(v -> {
            if (metodo[0].isEmpty() || idPago <= 0) return;
            btnConf.setEnabled(false);
            btnConf.setText("Confirmando...");

            JSONObject bodyConf = new JSONObject();
            try { bodyConf.put("confirmacionConductor", true); } catch (Exception ignored) {}

            ConexionApi.getInstance(this).put(
                    Constantes.pagoConfirmarConductor(idPago), bodyConf,
                    resp -> runOnUiThread(() ->
                            marcarCardPagadaDetalle(card, badge, bgBadge, bgAv, avatar,
                                    areaAccion, pagosConfirmados, total, btnContinuar, dp)),
                    err -> {
                        int code = (err != null && err.networkResponse != null)
                                ? err.networkResponse.statusCode : 0;
                        runOnUiThread(() -> {
                            if (code == 409) {
                                marcarCardPagadaDetalle(card, badge, bgBadge, bgAv, avatar,
                                        areaAccion, pagosConfirmados, total, btnContinuar, dp);
                            } else {
                                btnConf.setEnabled(true);
                                btnConf.setText("CONFIRMAR PAGO RECIBIDO");
                                Toast.makeText(this,
                                        "Error al confirmar (código " + code + ")",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
            );
        });
    }

    private void marcarCardPagadaDetalle(MaterialCardView card, TextView badge,
                                         GradientDrawable bgBadge, GradientDrawable bgAv,
                                         TextView avatar, LinearLayout areaAccion,
                                         int[] pagosConfirmados, int total,
                                         MaterialButton btnContinuar, float dp) {
        card.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
        card.setStrokeColor(Color.parseColor("#A5D6A7"));
        badge.setText("Pagado ✓");
        bgBadge.setColor(Color.parseColor("#2E7D32"));
        badge.setBackground(bgBadge);
        bgAv.setColor(0xFF2E7D32);
        avatar.setBackground(bgAv);
        areaAccion.setVisibility(View.GONE);
        pagosConfirmados[0]++;
        if (pagosConfirmados[0] >= total) {
            btnContinuar.setEnabled(true);
            btnContinuar.setAlpha(1f);
            btnContinuar.setBackgroundColor(Color.parseColor("#00897B"));
        }
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
        if (indice >= ids.size()) {
            // ── Conductor: al terminar calificaciones → abrir ResumenViajeActivity ──
            if (esConductor) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Intent intent = new Intent(this, ResumenViajeActivity.class);
                        intent.putExtra(ResumenViajeActivity.EXTRA_ID_VIAJE, viajeId);
                        startActivity(intent);
                    }
                }, 500);
            }
            return;
        }
        int idP = ids.get(indice);
        String nomP = nombres.get(indice);
        int sig = indice + 1;
        new CalificacionesManager(this).verificarCalificacion(viajeId, idCalificador, idP,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override public void onDebeCalificar() {
                        CalificacionController.mostrarBottomSheetCalificar(
                                DetalleViajeActivity.this,
                                viajeId, idP, nomP,
                                "",              // ← fotoCalificado
                                idCalificador, true,
                                (p, c) -> new Handler(Looper.getMainLooper()).postDelayed(
                                        () -> mostrarCalificacionesEncadenadas(
                                                ids, nombres, idCalificador, sig), 700));
                    }
                    @Override public void onYaCalifico(int p, String e) {
                        mostrarCalificacionesEncadenadas(ids, nombres, idCalificador, sig);
                    }
                });
    }

    private void abrirResumenViaje() {
        if (isFinishing() || isDestroyed()) return;
        Intent intent = new Intent(this, ResumenViajeActivity.class);
        intent.putExtra(ResumenViajeActivity.EXTRA_ID_VIAJE, viajeId);
        startActivity(intent);
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
                                "",
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
            if (ESTADOS_RESERVABLES.contains(estadoViaje)
                    || "DISPONIBLE".equals(estadoViaje)
                    || "PROGRAMADO".equals(estadoViaje)
                    || "CREADO".equals(estadoViaje)) {
                verificarReservaActivaYMostrarBoton();
            }
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
        
        // Priorizar el precio acordado (Bruto) sobre el monto del pago (que puede traer comisión/Neto)
        double monto = 0;
        // Buscar primero en el objeto de pago (si trae la reserva anidada)
        for (String k : new String[]{"precioFinal", "precioTramo", "costoPorPasajero", "precio"}) {
            monto = pago.optDouble(k, 0);
            if (monto > 0) break;
        }
        // Fallback al monto del pago o al precio base del viaje
        if (monto <= 0) monto = pago.optDouble("monto", precioViaje);

        // Aplicar redondeo para consistencia visual (múltiplos de 100, min 500)
        if (monto > 0) {
            monto = Math.ceil(monto / 100.0) * 100.0;
            if (monto < 500) monto = 500;
        }
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

        enviarParadasAlAsistente();
    }

    private void enviarParadasAlAsistente() {
        if (voiceAssistant != null && voiceAssistant.getFlowManager() != null) {
            ArrayList<String> nombres = new ArrayList<>();
            for (ParadaDinamica pd : paradasDin) {
                nombres.add(pd.nombre);
            }
            voiceAssistant.getFlowManager().setNombresParadas(nombres);
        }
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

        if (distanciaKm <= 0) {
            Log.w(TAG, "calcularYMostrarPrecioTramo: distanciaKm no disponible");
            return;
        }

        PrecioTramoPasajeroManager.calcular(
                this, viajeId,
                latSubida,               lngSubida,
                gpParada.getLatitude(),  gpParada.getLongitude(),
                distanciaKm,
                precioViaje,
                resultado -> {
                    // ── GUARDAR PERSISTENTE (nunca se sobrescribe al recargar) ──
                    precioCalculadoPersistente = resultado.precioFinal;
                    precioCalculadoPasajero    = resultado.precioFinal;

                    // Actualizar fila "Precio total" en la card Tu Reserva
                    actualizarValorFila(ROW_ID_PRECIO, resultado.precioFormateado);

                    // Actualizar el TextView principal de precio
                    if (txtPrecio != null)
                        txtPrecio.setText(resultado.precioFormateado);

                    // Toast informativo para el pasajero
                    String info = "$ Tu precio: " + resultado.precioFormateado
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
            case "CREADO":    return "Estado: Disponible";
            case "PROGRAMADO":return "📅 Estado: Programado";
            case "DISPONIBLE":return "✅ Estado: Disponible";
            case "EN_CURSO": case "INICIADO": return "Estado: En curso";
            case "FINALIZADO":return "Estado: Finalizado";
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

    // ← PEGA AQUÍ onActivityResult
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MAPA_SUBIDA
                && resultCode == RESULT_OK
                && data != null) {
            double lat = data.getDoubleExtra("lat_subida", Double.NaN);
            double lng = data.getDoubleExtra("lng_subida", Double.NaN);
            String nom = data.getStringExtra("nombre_subida");
            if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                gpSubida             = new GeoPoint(lat, lng);
                nombreSubidaPasajero = (nom != null && !nom.isEmpty())
                        ? nom : "Punto en el mapa";
                filaSubidaSeleccionada = null;
                renderizarMapa();
                ParadaDinamica pdSubida = new ParadaDinamica(
                        nombreSubidaPasajero, lat, lng, -99);
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> mostrarSheetBajada(pdSubida, null), 350);
            }
        }
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
                tvLabel.setText("TU BAJADA"); tvLabel.setTextColor(Color.parseColor("#004D40"));
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

    // ─── SCREEN DESCRIPTOR ────────────────────────────────────────────────────
    @Override public String getNombrePantalla() { return "Detalle del Viaje"; }
    @Override public String getDescripcionPantalla() {
        return "Estás en el detalle de un viaje. "
             + "Puedes ver el mapa con la ruta, información del conductor, "
             + "las paradas, el precio y los pasajeros reservados. "
             + "Si es un viaje disponible puedes reservar.";
    }
    @Override public String getOpcionesPantalla() {
        return "Puedes decir: reservar, confirmar, ir atrás, "
             + "o preguntar: ¿cuánto cuesta?, ¿cuántos cupos hay?";
    }

    // ─── VOICE ASSISTANT BRIDGE ──────────────────────────────────────────────
    
    /**
     * Devuelve una lista con los nombres de todas las paradas (puntos de subida y bajada).
     */
    public java.util.List<String> getNombresParadas() {
        java.util.ArrayList<String> nombres = new java.util.ArrayList<>();
        if (paradasDin != null) {
            for (ParadaDinamica pd : paradasDin) {
                nombres.add(pd.nombre);
            }
        }
        return nombres;
    }

    /**
     * Llamado por VoiceFlowManager cuando el usuario selecciona una parada por voz.
     */
    public void seleccionarParadaPorVoz(int index, boolean esSubida) {
        if (index < 0 || index >= paradasDin.size()) return;
        ParadaDinamica pd = paradasDin.get(index);
        if (esSubida) {
            gpSubida = pd.toGeoPoint();
            nombreSubidaPasajero = pd.nombre;
            subidaTemp = pd;
        } else {
            // Nota: en el flujo normal, gpParada es la bajada
            gpParada = pd.toGeoPoint();
            nombreParada = pd.nombre;
        }
    }

    /**
     * Llamado por VoiceFlowManager cuando el usuario confirma la reserva por voz.
     */
    public void confirmarReservaPorVoz() {
        if (subidaTemp == null) {
            subidaTemp = new ParadaDinamica(origenActual, latOrigen, lngOrigen, 0);
        }
        // Buscar la bajada seleccionada (gpParada/nombreParada)
        ParadaDinamica bajadaElegida = null;
        if (gpParada != null) {
            bajadaElegida = new ParadaDinamica(nombreParada, gpParada.getLatitude(), gpParada.getLongitude(), -99);
        } else {
            // Fallback: usar el destino
            bajadaElegida = paradasDin.get(paradasDin.size() - 1);
        }

        publicarParadaYReservarConSubida(subidaTemp, bajadaElegida);
    }

} // ← Única llave de cierre de DetalleViajeActivity