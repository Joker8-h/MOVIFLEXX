package com.arlys.moviflexx.controller;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

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
    private static final int    POLLING_CUPOS_MS = 5000;   // refresco cupos
    private static final int    POLLING_UBIC_MS  = 8000;   // refresco ubicación pasajeros

    // Colores turquesa
    private static final String COLOR_PRIMARIO   = "#00897B";
    private static final String COLOR_RUTA       = "#00BCD4";
    private static final String COLOR_SEGMENTO   = "#FF6F00";   // naranja sobre la ruta
    private static final String COLOR_CUPO_LIBRE = "#4CAF50";
    private static final String COLOR_CUPO_OCUP  = "#EF5350";

    // ── UI ──
    private MapView     map;
    private TextView    txtRuta, txtEstado, txtConductor, txtVehiculo;
    private TextView    txtDistancia, txtDuracion, txtPrecio;
    private LinearLayout layoutCupos;
    private MaterialButton btnReservar, btnIniciar, btnFinalizar, btnAgregarParada;
    private RecyclerView   rvHistorialParadas;
    private MaterialCardView cardHistorial, cardAcciones;
    private ProgressBar    loaderDetalle;
    private MyLocationNewOverlay myLocationOverlay;

    // ── Datos ──
    private int    viajeId, rutaId;
    private boolean esConductor;
    private String  origenActual = "", destinoActual = "", estadoViaje = "";
    private double  latOrigen, lngOrigen, latDestino, lngDestino;
    private int     cuposTotales = 0, cuposDisponibles = 0;
    private SessionManager session;

    // ── Paradas de la ruta ──
    private final ArrayList<JSONObject> paradasRuta = new ArrayList<>();

    // ── Parada elegida por el pasajero ──
    private JSONObject paradaSeleccionadaPasajero = null;

    // ── Puntos de la ruta del conductor (OSRM) ──
    private ArrayList<GeoPoint> puntosRutaConductor = new ArrayList<>();

    // ── Marcadores de pasajeros (para conductor) ──
    private List<Marker> marcadoresPasajeros = new ArrayList<>();

    // ── Polling cupos ──
    private final Handler   cuposHandler  = new Handler(Looper.getMainLooper());
    private Runnable        cuposRunnable;
    private boolean         cuposActivo   = false;

    // ── Polling ubicación pasajeros ──
    private final Handler   ubicHandler   = new Handler(Looper.getMainLooper());
    private Runnable        ubicRunnable;
    private boolean         ubicActivo    = false;

    // ── Flag para saber si el mapa está listo ──
    private boolean mapaListo = false;

    /* ══════════════════════════════════════════
       CICLO DE VIDA
    ══════════════════════════════════════════ */

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
        configurarMapa();
        verificarPermisos();
        cargarDetalleViaje();
    }

    @Override protected void onResume()  { super.onResume();  if (map != null) map.onResume(); }
    @Override protected void onPause()   { super.onPause();   if (map != null) map.onPause();  detenerTodoPolling(); }
    @Override protected void onDestroy() { super.onDestroy(); detenerTodoPolling(); }

    /* ══════════════════════════════════════════
       INIT
    ══════════════════════════════════════════ */

    private void initViews() {
        map                = findViewById(R.id.map_mini);
        txtRuta            = findViewById(R.id.txt_info_ruta);
        txtEstado          = findViewById(R.id.txt_estado);
        txtConductor       = findViewById(R.id.txt_conductor);
        txtVehiculo        = findViewById(R.id.txt_vehiculo);
        txtDistancia       = findViewById(R.id.txt_distancia_ruta);
        txtDuracion        = findViewById(R.id.txt_duracion_ruta);
        txtPrecio          = findViewById(R.id.txt_precio);
        layoutCupos        = findViewById(R.id.layout_cupos);
        btnReservar        = findViewById(R.id.btn_reservar);
        btnIniciar         = findViewById(R.id.btn_iniciar);
        btnFinalizar       = findViewById(R.id.btn_finalizar);
        btnAgregarParada   = findViewById(R.id.btn_parada);
        rvHistorialParadas = findViewById(R.id.rv_historial_paradas);
        cardHistorial      = findViewById(R.id.card_historial);
        cardAcciones       = findViewById(R.id.card_acciones);
        loaderDetalle      = findViewById(R.id.loader_detalle);

        rvHistorialParadas.setLayoutManager(new LinearLayoutManager(this));
        btnReservar.setOnClickListener(v -> mostrarBottomSheetReserva());
        btnIniciar.setOnClickListener(v -> cambiarEstado("iniciar"));
        btnFinalizar.setOnClickListener(v -> confirmarFinalizar());
        btnAgregarParada.setOnClickListener(v -> mostrarDialogoAgregarParada());
    }

    private void configurarMapa() {
        // ⚡ MEJORA: Configuración completa del mapa
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);

        // ⚡ CRÍTICO: Asegurar conexión a internet y carga de tiles
        map.setUseDataConnection(true);
        map.setTilesScaledToDpi(true);
        map.setHorizontalMapRepetitionEnabled(false);
        map.setVerticalMapRepetitionEnabled(false);

        map.getController().setZoom(14.0);
        map.getController().setCenter(new GeoPoint(2.4419, -76.6063));

        // ⚡ NUEVO: Listener para detectar cuando el mapa está listo
        map.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                mapaListo = true;
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                mapaListo = true;
                return false;
            }
        });

        // ⚡ CRÍTICO: Forzar invalidación inicial
        map.post(() -> {
            map.invalidate();
            mapaListo = true;
        });
    }

    /* ══════════════════════════════════════════
       PERMISOS GPS
    ══════════════════════════════════════════ */

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

    /* ══════════════════════════════════════════
       CARGA PRINCIPAL
    ══════════════════════════════════════════ */

    private void cargarDetalleViaje() {
        loaderDetalle.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).getObject(
                Constantes.viajePorId((long) viajeId),
                response -> {
                    loaderDetalle.setVisibility(View.GONE);
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

        // ── Ruta ──
        JSONObject ruta = r.optJSONObject("ruta");
        if (ruta != null) {
            rutaId        = ruta.optInt("idRuta", 0);
            origenActual  = ruta.optString("origen",   "Origen");
            destinoActual = ruta.optString("destino",  "Destino");
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

        // ── Conductor ──
        JSONObject conductor = r.optJSONObject("conductor");
        if (conductor != null && txtConductor != null) {
            String nombre = conductor.optString("nombre",
                    conductor.optString("nombreCompleto", "Conductor"));
            txtConductor.setText("👤 " + nombre);
        }

        // ── Vehículo ──
        JSONObject vehiculo = r.optJSONObject("vehiculo");
        if (vehiculo != null && txtVehiculo != null) {
            txtVehiculo.setText("🚘 "
                    + vehiculo.optString("marca",  "") + " "
                    + vehiculo.optString("modelo", "") + " • "
                    + vehiculo.optString("placa",  ""));
        }

        txtRuta.setText("📍 " + origenActual + " → " + destinoActual);
        txtEstado.setText(obtenerEtiquetaEstado(estadoViaje));

        // ── Cupos visuales ──
        actualizarChipsCupos(cuposTotales, cuposDisponibles);

        // ── Botones ──
        configurarBotonesYFlujo(r);

        // ── Paradas y mapa ──
        cargarParadasRuta();

        // ── Polling cupos ──
        iniciarPollingCupos();
    }

    /* ══════════════════════════════════════════
       CHIPS DE CUPOS  (círculos verde / rojo)
       — cada cupo es un View circular 28×28 dp —
    ══════════════════════════════════════════ */

    private void actualizarChipsCupos(int total, int disponibles) {
        if (layoutCupos == null) return;
        runOnUiThread(() -> {
            layoutCupos.removeAllViews();

            int dpSize   = (int) (28 * getResources().getDisplayMetrics().density);
            int dpMargin = (int) (6  * getResources().getDisplayMetrics().density);

            for (int i = 0; i < total; i++) {
                boolean libre = i < disponibles;

                // Círculo de color
                View circulo = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpSize, dpSize);
                lp.setMargins(dpMargin, 0, dpMargin, 0);
                circulo.setLayoutParams(lp);

                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.OVAL);
                shape.setColor(Color.parseColor(libre ? COLOR_CUPO_LIBRE : COLOR_CUPO_OCUP));
                shape.setStroke((int)(2 * getResources().getDisplayMetrics().density),
                        Color.parseColor(libre ? "#388E3C" : "#C62828"));
                circulo.setBackground(shape);
                circulo.setContentDescription(libre ? "Cupo libre" : "Cupo ocupado");

                layoutCupos.addView(circulo);
            }
        });
    }

    /* ══════════════════════════════════════════
       POLLING CUPOS  (cada 5 s)
    ══════════════════════════════════════════ */

    private void iniciarPollingCupos() {
        if (cuposActivo) return;
        cuposActivo = true;
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
        ConexionApi.getInstance(this).getObject(
                Constantes.viajePorId((long) viajeId),
                response -> {
                    int  nuevosDis = response.optInt("cuposDisponibles", cuposDisponibles);
                    int  nuevosTot = response.optInt("cuposTotales",     cuposTotales);
                    String nuevoEst = response.optString("estado", estadoViaje).trim().toUpperCase();

                    if (nuevosDis != cuposDisponibles || nuevosTot != cuposTotales) {
                        cuposDisponibles = nuevosDis;
                        cuposTotales     = nuevosTot;
                        actualizarChipsCupos(cuposTotales, cuposDisponibles);

                        // ⚡ NUEVO: Recargar reservas cuando cambien los cupos
                        if (esConductor) {
                            cargarReservasPasajeros();
                        }

                        runOnUiThread(() -> {
                            if (!esConductor)
                                btnReservar.setVisibility(
                                        cuposDisponibles > 0 ? View.VISIBLE : View.GONE);
                        });
                    }

                    if (!nuevoEst.equals(estadoViaje)) {
                        estadoViaje = nuevoEst;
                        runOnUiThread(() -> txtEstado.setText(obtenerEtiquetaEstado(estadoViaje)));
                        if (estadoViaje.equals("INICIADO") || estadoViaje.equals("FINALIZADO"))
                            cargarDetalleViaje();
                    }
                },
                error -> Log.w(TAG, "Polling cupos: sin respuesta")
        );
    }

    /* ══════════════════════════════════════════
       PARADAS DE LA RUTA
    ══════════════════════════════════════════ */

    private void cargarParadasRuta() {
        if (rutaId == 0) {
            // ⚡ CAMBIO: Esperar 800ms para que el mapa esté listo
            map.postDelayed(() -> dibujarRutaConductor(), 800);
            return;
        }
        ConexionApi.getInstance(this).getObject(
                Constantes.paradasPorRuta((long) rutaId),
                response -> {
                    try {
                        JSONArray paradas = response.optJSONArray("paradas");
                        paradasRuta.clear();
                        ArrayList<String> nombres = new ArrayList<>();
                        if (paradas != null) {
                            for (int i = 0; i < paradas.length(); i++) {
                                JSONObject p = paradas.getJSONObject(i);
                                paradasRuta.add(p);
                                nombres.add(p.optString("nombre", "Parada " + (i + 1)));
                            }
                        }
                        ParadaAdapter adapter = new ParadaAdapter(
                                nombres, origenActual, destinoActual);
                        rvHistorialParadas.setAdapter(adapter);
                        cardHistorial.setVisibility(nombres.isEmpty() ? View.GONE : View.VISIBLE);
                    } catch (Exception e) { Log.e(TAG, "Error cargando paradas", e); }

                    // ⚡ CAMBIO: Esperar más tiempo antes de dibujar (800ms)
                    map.postDelayed(() -> dibujarRutaConductor(), 800);
                },
                error -> {
                    cardHistorial.setVisibility(View.GONE);
                    // ⚡ CAMBIO: Esperar incluso si hay error
                    map.postDelayed(() -> dibujarRutaConductor(), 800);
                }
        );
    }

    /* ══════════════════════════════════════════
       BOTONES Y FLUJO
    ══════════════════════════════════════════ */

    private void configurarBotonesYFlujo(JSONObject r) {
        btnReservar.setVisibility(View.GONE);
        btnIniciar.setVisibility(View.GONE);
        btnFinalizar.setVisibility(View.GONE);
        btnAgregarParada.setVisibility(View.GONE);

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
        } else {
            boolean puedeReservar = (estadoViaje.equals("CREADO")
                    || estadoViaje.equals("PROGRAMADO")
                    || estadoViaje.equals("DISPONIBLE"))
                    && cuposDisponibles > 0;
            btnReservar.setVisibility(puedeReservar ? View.VISIBLE : View.GONE);
            if (estadoViaje.equals("INICIADO")) verificarReservaPasajero();
        }
    }

    /* ══════════════════════════════════════════
       BOTTOM SHEET RESERVA
    ══════════════════════════════════════════ */

    private void mostrarBottomSheetReserva() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.BottomSheetTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_reserva, null);
        sheet.setContentView(view);

        RecyclerView rvParadas      = view.findViewById(R.id.rv_paradas_sheet);
        MaterialButton btnConfirmar = view.findViewById(R.id.btn_confirmar_reserva);
        TextView txtTitulo          = view.findViewById(R.id.txt_titulo_sheet);
        TextView txtRutaSheet       = view.findViewById(R.id.txt_ruta_sheet);

        txtTitulo.setText("¿Dónde quieres bajarte?");
        txtRutaSheet.setText(origenActual + " → " + destinoActual);

        ArrayList<String> opciones = new ArrayList<>();
        for (JSONObject p : paradasRuta)
            opciones.add("🔵 " + p.optString("nombre", "Parada"));
        opciones.add("🏁 " + destinoActual + " (Destino final)");

        final int[] seleccionado = {-1};
        ParadaSeleccionAdapter adapter = new ParadaSeleccionAdapter(opciones, idx -> {
            seleccionado[0] = idx;
            btnConfirmar.setEnabled(true);
            btnConfirmar.setAlpha(1f);
        });
        rvParadas.setLayoutManager(new LinearLayoutManager(this));
        rvParadas.setAdapter(adapter);
        btnConfirmar.setEnabled(false);
        btnConfirmar.setAlpha(0.5f);

        btnConfirmar.setOnClickListener(v -> {
            if (seleccionado[0] < 0) return;
            if (seleccionado[0] < paradasRuta.size())
                paradaSeleccionadaPasajero = paradasRuta.get(seleccionado[0]);
            else
                paradaSeleccionadaPasajero = null; // destino final
            sheet.dismiss();
            confirmarReserva(opciones.get(seleccionado[0]));
        });
        sheet.show();
    }

    private void confirmarReserva(String nombreParada) {
        new AlertDialog.Builder(this)
                .setTitle("Confirmar reserva")
                .setMessage("¿Reservar cupo?\n\nBajarás en: " + nombreParada)
                .setPositiveButton("Reservar", (d, w) -> hacerReserva(nombreParada))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void hacerReserva(String nombreParada) {
        loaderDetalle.setVisibility(View.VISIBLE);
        try {
            JSONObject body = new JSONObject();
            body.put("idViaje",   viajeId);
            body.put("idUsuario", session.getIdUsuario());
            if (paradaSeleccionadaPasajero != null) {
                body.put("idParada",    paradaSeleccionadaPasajero.optInt("idParada", 0));
                body.put("nombreParada", paradaSeleccionadaPasajero.optString("nombre", nombreParada));
            } else {
                body.put("nombreParada", destinoActual);
            }
            ConexionApi.getInstance(this).post(Constantes.RESERVAS, body,
                    response -> {
                        loaderDetalle.setVisibility(View.GONE);
                        Toast.makeText(this,
                                "✅ Reserva confirmada. Bajarás en: " + nombreParada,
                                Toast.LENGTH_LONG).show();
                        // Trazar segmento naranja sobre la ruta
                        double lat = paradaSeleccionadaPasajero != null
                                ? paradaSeleccionadaPasajero.optDouble("latitud",  latDestino)
                                : latDestino;
                        double lon = paradaSeleccionadaPasajero != null
                                ? paradaSeleccionadaPasajero.optDouble("longitud", lngDestino)
                                : lngDestino;
                        trazarSegmentoHastaParada(lat, lon, nombreParada);
                        refrescarCuposYEstado();
                    },
                    error -> {
                        loaderDetalle.setVisibility(View.GONE);
                        String msg = "Error al reservar";
                        if (error != null && error.networkResponse != null
                                && error.networkResponse.statusCode == 400)
                            msg = "No hay cupos disponibles";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
            );
        } catch (Exception e) {
            loaderDetalle.setVisibility(View.GONE);
            Log.e(TAG, "Error reserva", e);
        }
    }

    /* ══════════════════════════════════════════
       TRAZAR SEGMENTO PASAJERO (naranja)
       Se calcula la ruta OSRM origen → parada
       y se superpone en naranja sobre la ruta morada.
    ══════════════════════════════════════════ */

    private void trazarSegmentoHastaParada(double latParada, double lonParada, String nombre) {
        new Thread(() -> {
            try {
                String url = "https://router.project-osrm.org/route/v1/driving/"
                        + lngOrigen + "," + latOrigen + ";"
                        + lonParada + "," + latParada
                        + "?overview=full&geometries=geojson";

                JSONObject res = new JSONObject(peticionHttp(url));
                JSONArray coords = res.getJSONArray("routes").getJSONObject(0)
                        .getJSONObject("geometry").getJSONArray("coordinates");

                ArrayList<GeoPoint> puntos = new ArrayList<>();
                for (int i = 0; i < coords.length(); i++) {
                    JSONArray c = coords.getJSONArray(i);
                    puntos.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                }
                runOnUiThread(() -> {
                    // Línea naranja encima de la ruta turquesa
                    Polyline seg = new Polyline();
                    seg.setPoints(puntos);
                    seg.setColor(Color.parseColor(COLOR_SEGMENTO));
                    seg.setWidth(9f);
                    map.getOverlays().add(seg);

                    // Marcador parada pasajero
                    Marker m = new Marker(map);
                    m.setPosition(new GeoPoint(latParada, lonParada));
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    m.setTitle("📍 Tu parada: " + nombre);
                    m.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_compass));
                    map.getOverlays().add(m);
                    map.getController().animateTo(new GeoPoint(latParada, lonParada));
                    map.invalidate();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error trazando segmento", e);
            }
        }).start();
    }

    /* ══════════════════════════════════════════
       SEGUIMIENTO PASAJERO (viaje INICIADO)
    ══════════════════════════════════════════ */

    private void verificarReservaPasajero() {
        ConexionApi.getInstance(this).getObject(
                Constantes.RESERVAS + "/pasajero/" + session.getIdUsuario()
                        + "/viaje/" + viajeId,
                response -> {
                    String est = response.optString("estado", "");
                    if (est.equals("ACTIVA") || est.equals("CONFIRMADA")) {
                        String nomParada = response.optString("nombreParada", destinoActual);
                        int    idParada  = response.optInt("idParada", -1);
                        for (JSONObject p : paradasRuta) {
                            if (p.optInt("idParada", -2) == idParada) {
                                paradaSeleccionadaPasajero = p;
                                break;
                            }
                        }
                        double lat = paradaSeleccionadaPasajero != null
                                ? paradaSeleccionadaPasajero.optDouble("latitud",  latDestino)
                                : latDestino;
                        double lon = paradaSeleccionadaPasajero != null
                                ? paradaSeleccionadaPasajero.optDouble("longitud", lngDestino)
                                : lngDestino;
                        trazarSegmentoHastaParada(lat, lon, nomParada);
                        Toast.makeText(this, "🚗 Viaje en curso. Tu parada: " + nomParada,
                                Toast.LENGTH_LONG).show();
                    }
                },
                error -> Log.w(TAG, "Sin reserva activa")
        );
    }

    /* ══════════════════════════════════════════
       ⚡ NUEVO: CARGAR RESERVAS DE PASAJEROS
       (para que el conductor vea las paradas elegidas)
    ══════════════════════════════════════════ */

    private void cargarReservasPasajeros() {
        if (!esConductor) return;

        ConexionApi.getInstance(this).getObject(
                Constantes.viajeReservasDetalle((long) viajeId),
                response -> {
                    try {
                        JSONArray reservas = response.optJSONArray("reservas");
                        if (reservas == null) return;

                        // Limpiar marcadores anteriores de pasajeros
                        limpiarMarcadoresPasajeros();

                        for (int i = 0; i < reservas.length(); i++) {
                            JSONObject reserva = reservas.getJSONObject(i);
                            String nombre = reserva.optString("nombrePasajero", "Pasajero");
                            String parada = reserva.optString("nombreParada", "Destino");
                            double lat = reserva.optDouble("latitud", 0);
                            double lon = reserva.optDouble("longitud", 0);

                            if (lat != 0 && lon != 0) {
                                agregarMarcadorReservaPasajero(lat, lon, nombre, parada);
                            }
                        }

                        map.invalidate();

                    } catch (Exception e) {
                        Log.e(TAG, "Error cargando reservas", e);
                    }
                },
                error -> Log.w(TAG, "Error obteniendo reservas")
        );
    }

    private void limpiarMarcadoresPasajeros() {
        for (Marker m : marcadoresPasajeros) {
            map.getOverlays().remove(m);
        }
        marcadoresPasajeros.clear();
    }

    private void agregarMarcadorReservaPasajero(double lat, double lon,
                                                String nombre, String parada) {
        runOnUiThread(() -> {
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(lat, lon));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle("🧑 " + nombre);
            m.setSnippet("Baja en: " + parada);
            m.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_myplaces));

            map.getOverlays().add(m);
            marcadoresPasajeros.add(m); // ⚡ Guardar referencia
        });
    }

    /* ══════════════════════════════════════════
       POLLING UBICACIÓN PASAJEROS (conductor)
    ══════════════════════════════════════════ */

    private void iniciarPollingUbicacionPasajeros() {
        if (ubicActivo) return;
        ubicActivo = true;
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
                            double lat = p.optDouble("latitud",  0);
                            double lon = p.optDouble("longitud", 0);
                            if (lat != 0 && lon != 0)
                                agregarMarcadorPasajero(lat, lon,
                                        p.optString("nombre",      "Pasajero"),
                                        p.optString("nombreParada", ""));
                        }
                    } catch (Exception e) { Log.w(TAG, "Error ubicaciones", e); }
                },
                error -> {}
        );
    }

    private void agregarMarcadorPasajero(double lat, double lon,
                                         String nombre, String parada) {
        runOnUiThread(() -> {
            // Buscar si ya existe un marcador para este pasajero
            Marker marcadorExistente = null;
            for (Marker m : marcadoresPasajeros) {
                if (m.getTitle() != null && m.getTitle().contains(nombre)) {
                    marcadorExistente = m;
                    break;
                }
            }

            if (marcadorExistente != null) {
                // Actualizar posición
                marcadorExistente.setPosition(new GeoPoint(lat, lon));
            } else {
                // Crear nuevo marcador
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
        cuposActivo = false;
        ubicActivo  = false;
        if (cuposRunnable != null) cuposHandler.removeCallbacks(cuposRunnable);
        if (ubicRunnable  != null) ubicHandler .removeCallbacks(ubicRunnable);
    }

    /* ══════════════════════════════════════════
       CAMBIAR ESTADO
    ══════════════════════════════════════════ */

    private void cambiarEstado(String accion) {
        loaderDetalle.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).post(
                Constantes.viajePorId((long) viajeId) + "/" + accion, null,
                response -> {
                    loaderDetalle.setVisibility(View.GONE);
                    Toast.makeText(this, "✅ Viaje " + accion + "do",
                            Toast.LENGTH_SHORT).show();
                    cargarDetalleViaje();
                },
                error -> {
                    loaderDetalle.setVisibility(View.GONE);
                    Toast.makeText(this, "Error al " + accion, Toast.LENGTH_LONG).show();
                }
        );
    }

    private void confirmarFinalizar() {
        new AlertDialog.Builder(this)
                .setTitle("Finalizar viaje")
                .setMessage("¿Finalizar? Se liberarán todos los cupos.")
                .setPositiveButton("Finalizar", (d, w) -> finalizarViaje())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void finalizarViaje() {
        loaderDetalle.setVisibility(View.VISIBLE);
        detenerTodoPolling();
        ConexionApi.getInstance(this).post(
                Constantes.viajePorId((long) viajeId) + "/pasajeros-bajaron", null,
                r -> ConexionApi.getInstance(this).post(
                        Constantes.viajeFinalizar((long) viajeId), null,
                        r2 -> {
                            loaderDetalle.setVisibility(View.GONE);
                            Toast.makeText(this, "✅ Viaje finalizado. Cupos liberados.",
                                    Toast.LENGTH_LONG).show();
                            cargarDetalleViaje();
                        },
                        e2 -> { loaderDetalle.setVisibility(View.GONE);
                            Toast.makeText(this, "Error finalizando", Toast.LENGTH_LONG).show(); }),
                error -> cambiarEstado("finalizar")
        );
    }

    /* ══════════════════════════════════════════
       AGREGAR PARADA (conductor)
    ══════════════════════════════════════════ */

    private void mostrarDialogoAgregarParada() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("➕ Agregar Parada");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        EditText inputNombre    = new EditText(this); inputNombre.setHint("Nombre");    layout.addView(inputNombre);
        EditText inputDireccion = new EditText(this); inputDireccion.setHint("Dirección"); layout.addView(inputDireccion);
        b.setView(layout);
        b.setPositiveButton("Agregar", (d, w) -> {
            String nom = inputNombre.getText().toString().trim();
            String dir = inputDireccion.getText().toString().trim();
            if (nom.isEmpty() || dir.isEmpty()) {
                Toast.makeText(this, "Complete todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }
            new Thread(() -> {
                try {
                    GeoPoint pt = geocodificar(dir);
                    runOnUiThread(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("nombre",   nom);
                            body.put("latitud",  pt.getLatitude());
                            body.put("longitud", pt.getLongitude());
                            ConexionApi.getInstance(this).post(
                                    Constantes.rutaParadas((long) rutaId), body,
                                    r -> { Toast.makeText(this, "✅ Parada agregada",
                                            Toast.LENGTH_SHORT).show();
                                        cargarParadasRuta(); },
                                    e -> Toast.makeText(this, "Error agregando parada",
                                            Toast.LENGTH_LONG).show()
                            );
                        } catch (Exception ex) { Log.e(TAG, "Error enviando parada", ex); }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Dirección no encontrada",
                            Toast.LENGTH_LONG).show());
                }
            }).start();
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    /* ══════════════════════════════════════════
       DIBUJAR RUTA CONDUCTOR EN MAPA (turquesa)
    ══════════════════════════════════════════ */

    private void dibujarRutaConductor() {
        new Thread(() -> {
            try {
                GeoPoint origen  = new GeoPoint(latOrigen,  lngOrigen);
                GeoPoint destino = new GeoPoint(latDestino, lngDestino);

                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                ArrayList<String>   nombres   = new ArrayList<>();
                for (JSONObject p : paradasRuta) {
                    waypoints.add(new GeoPoint(
                            p.optDouble("latitud",  latOrigen),
                            p.optDouble("longitud", lngOrigen)));
                    nombres.add(p.optString("nombre", "Parada"));
                }

                puntosRutaConductor = obtenerPuntosOsrm(
                        buildOsrmUrl(origen, destino, waypoints));

                runOnUiThread(() ->
                        dibujarEnMapa(puntosRutaConductor, origen, destino, waypoints, nombres));

            } catch (Exception e) {
                Log.e(TAG, "Error dibujando ruta conductor", e);
            }
        }).start();
    }

    private String buildOsrmUrl(GeoPoint o, GeoPoint d, ArrayList<GeoPoint> wp) {
        StringBuilder sb = new StringBuilder(
                "https://router.project-osrm.org/route/v1/driving/");
        sb.append(o.getLongitude()).append(",").append(o.getLatitude());
        for (GeoPoint w : wp)
            sb.append(";").append(w.getLongitude()).append(",").append(w.getLatitude());
        sb.append(";").append(d.getLongitude()).append(",").append(d.getLatitude());
        sb.append("?overview=full&geometries=geojson");
        return sb.toString();
    }

    private ArrayList<GeoPoint> obtenerPuntosOsrm(String url) throws Exception {
        JSONObject res = new JSONObject(peticionHttp(url));
        JSONArray coords = res.getJSONArray("routes").getJSONObject(0)
                .getJSONObject("geometry").getJSONArray("coordinates");
        ArrayList<GeoPoint> puntos = new ArrayList<>();
        for (int i = 0; i < coords.length(); i++) {
            JSONArray c = coords.getJSONArray(i);
            puntos.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
        }
        return puntos;
    }

    /**
     * Dibuja sombra → borde blanco → línea turquesa → marcadores.
     */
    private void dibujarEnMapa(ArrayList<GeoPoint> puntos,
                               GeoPoint origen, GeoPoint destino,
                               ArrayList<GeoPoint> paradas, ArrayList<String> nombres) {

        // 🔥 Eliminar solo Markers y Polylines (mantener GPS)
        List<Overlay> overlays = map.getOverlays();
        for (int i = overlays.size() - 1; i >= 0; i--) {
            Overlay overlay = overlays.get(i);
            if (overlay instanceof Marker || overlay instanceof Polyline) {
                overlays.remove(i);
            }
        }

        // 🔹 Volver a agregar GPS si existe
        if (myLocationOverlay != null && !overlays.contains(myLocationOverlay)) {
            overlays.add(myLocationOverlay);
        }

        // -------- RUTA --------

        Polyline sombra = new Polyline();
        sombra.setPoints(puntos);
        sombra.setColor(Color.parseColor("#33000000"));
        sombra.setWidth(18f);
        overlays.add(sombra);

        Polyline borde = new Polyline();
        borde.setPoints(puntos);
        borde.setColor(Color.WHITE);
        borde.setWidth(14f);
        overlays.add(borde);

        Polyline linea = new Polyline();
        linea.setPoints(puntos);
        linea.setColor(Color.parseColor(COLOR_RUTA));
        linea.setWidth(10f);
        overlays.add(linea);

        // -------- ORIGEN --------

        Marker mO = new Marker(map);
        mO.setPosition(origen);
        mO.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mO.setTitle("🟢 Inicio: " + origenActual);
        mO.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_mylocation));
        overlays.add(mO);

        // -------- PARADAS --------

        for (int i = 0; i < paradas.size(); i++) {
            Marker m = new Marker(map);
            m.setPosition(paradas.get(i));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle("🔵 " + (i < nombres.size() ? nombres.get(i) : "Parada " + (i + 1)));
            m.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_add));
            overlays.add(m);
        }

        // -------- DESTINO --------

        Marker mD = new Marker(map);
        mD.setPosition(destino);
        mD.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mD.setTitle("🔴 Destino: " + destinoActual);
        mD.setIcon(getResources().getDrawable(android.R.drawable.ic_dialog_map));
        overlays.add(mD);

        map.post(() -> {
            try {
                map.zoomToBoundingBox(linea.getBounds(), true, 150);
            } catch (Exception ignored) {}

            map.invalidate();

            // ⚡ NUEVO: Cargar las paradas de los pasajeros después de dibujar
            if (esConductor) {
                map.postDelayed(() -> cargarReservasPasajeros(), 500);
            }
        });
    }

    /* ══════════════════════════════════════════
       UTILIDADES
    ══════════════════════════════════════════ */

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
                + java.net.URLEncoder.encode(dir + ",Popayan,Colombia", "UTF-8")
                + "&format=json&limit=1";
        JSONArray arr = new JSONArray(peticionHttp(url));
        if (arr.length() == 0) throw new Exception("No encontrado: " + dir);
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
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally { if (conn != null) conn.disconnect(); }
    }

    /* ══════════════════════════════════════════
       ADAPTERS
    ══════════════════════════════════════════ */

    static class ParadaAdapter extends RecyclerView.Adapter<ParadaAdapter.VH> {
        private final ArrayList<String> items = new ArrayList<>();
        ParadaAdapter(ArrayList<String> paradas, String origen, String destino) {
            items.add("🟢 " + origen + "  (Inicio)");
            for (String p : paradas) items.add("🔵 " + p);
            items.add("🔴 " + destino + "  (Destino)");
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int vt) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(32, 20, 32, 20);
            tv.setTextSize(14f);
            tv.setTextColor(Color.parseColor("#004D40"));
            return new VH(tv);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            ((TextView) h.itemView).setText(items.get(pos));
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }

    static class ParadaSeleccionAdapter
            extends RecyclerView.Adapter<ParadaSeleccionAdapter.VH> {
        interface OnSelect { void onSelect(int index); }
        private final ArrayList<String> items;
        private final OnSelect          callback;
        private int                     seleccionado = -1;
        ParadaSeleccionAdapter(ArrayList<String> items, OnSelect cb) {
            this.items = items; this.callback = cb;
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int vt) {
            MaterialCardView card = new MaterialCardView(parent.getContext());
            card.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT));
            card.setCardElevation(4f);
            card.setRadius(16f);
            card.setUseCompatPadding(true);
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(40, 32, 40, 32);
            tv.setTextSize(15f);
            tv.setTextColor(Color.parseColor("#004D40"));
            card.addView(tv);
            return new VH(card);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            MaterialCardView card = (MaterialCardView) h.itemView;
            ((TextView) card.getChildAt(0)).setText(items.get(pos));
            boolean sel = pos == seleccionado;
            card.setCardBackgroundColor(sel
                    ? Color.parseColor("#E0F2F1") : Color.WHITE);
            card.setStrokeColor(sel
                    ? Color.parseColor("#00897B") : Color.parseColor("#E0E0E0"));
            card.setStrokeWidth(sel ? 4 : 1);
            card.setOnClickListener(v -> {
                seleccionado = pos; notifyDataSetChanged(); callback.onSelect(pos);
            });
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}