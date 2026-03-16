package com.arlys.moviflexx.controller;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Manager.RouteManager;
import com.arlys.moviflexx.model.pojo.RouteOption;
import com.arlys.moviflexx.model.pojo.RouteOptionsResponse;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * VerRutaPasajeroActivity
 *
 * Muestra la ruta del conductor en el mapa, localiza la parada que
 * escribió el pasajero y permite enviarla al backend con
 * Constantes.rutaParadas(idRuta)  →  POST /api/rutas/{idRuta}/paradas
 */
public class VerRutaPasajero extends AppCompatActivity {

    private static final String TAG = "VerRutaPasajero";

    // ── Datos de Intent ──
    private int    idViaje, idRuta;
    private String origenRuta, destinoRuta, tipoTransporte;
    private double latOrigen, lngOrigen, latDestino, lngDestino;
    private double distanciaKm, duracionMin, costoRuta;
    private String paradaPasajero;
    private double latPasajero, lngPasajero;

    // ── GeoPoints ──
    private GeoPoint origenPoint, destinoPoint, pasajeroPoint, paradaPoint;
    private String   paradaDireccionFinal;

    // ── UI ──
    private MapView        map;
    private TextView       txtOrigenInfo, txtDestinoInfo, txtDistanciaInfo, txtDuracionInfo;
    private TextView       txtParadaPasajero, txtEstadoParada, txtInfoExtra;
    private MaterialButton btnConfirmarParada, btnSolicitarViaje;
    private ProgressBar    loaderParada;
    private View           rootView;

    // ── Marcadores ──
    private Marker marcadorOrigen, marcadorDestino, marcadorPasajero, marcadorParada;

    // ── Polylines ──
    private final List<Polyline> polylinesRuta = new ArrayList<>();

    // ── Threading ──
    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private RouteManager routeManager;

    /* ═══════════════════════════════════════════════════════
       CICLO DE VIDA
    ═══════════════════════════════════════════════════════ */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_ver_ruta_pasajero);

        rootView     = findViewById(android.R.id.content);
        routeManager = new RouteManager();

        leerIntent();
        initViews();
        configurarMapa();
        poblarInfoRuta();
        dibujarRutaDelConductor();   // dibuja ruta del conductor
        mostrarUbicacionPasajero();  // pone marcador del pasajero
        geocodificarParadaPasajero(); // busca coords de la parada escrita
    }

    @Override protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override protected void onPause()  { super.onPause();  if (map != null) map.onPause();  }
    @Override protected void onDestroy(){ super.onDestroy(); executor.shutdown();             }

    /* ═══════════════════════════════════════════════════════
       LEER INTENT
    ═══════════════════════════════════════════════════════ */
    private void leerIntent() {
        Intent i = getIntent();
        idViaje        = i.getIntExtra("ID_VIAJE",        0);
        idRuta         = i.getIntExtra("ID_RUTA",         0);
        origenRuta     = i.getStringExtra("ORIGEN_RUTA");
        destinoRuta    = i.getStringExtra("DESTINO_RUTA");
        latOrigen      = i.getDoubleExtra("LAT_ORIGEN",    2.4448);
        lngOrigen      = i.getDoubleExtra("LNG_ORIGEN",  -76.6147);
        latDestino     = i.getDoubleExtra("LAT_DESTINO",   2.4550);
        lngDestino     = i.getDoubleExtra("LNG_DESTINO", -76.5980);
        distanciaKm    = i.getDoubleExtra("DISTANCIA_KM",  0);
        duracionMin    = i.getDoubleExtra("DURACION_MIN",  0);
        costoRuta      = i.getDoubleExtra("COSTO_RUTA",    0);
        tipoTransporte = i.getStringExtra("TIPO_TRANSPORTE");
        paradaPasajero = i.getStringExtra("PARADA_PASAJERO");
        latPasajero    = i.getDoubleExtra("LAT_PASAJERO",  0);
        lngPasajero    = i.getDoubleExtra("LNG_PASAJERO",  0);

        if (origenRuta     == null) origenRuta     = "Origen";
        if (destinoRuta    == null) destinoRuta    = "Destino";
        if (paradaPasajero == null) paradaPasajero = "";
        if (tipoTransporte == null) tipoTransporte = "driving";

        origenPoint  = new GeoPoint(latOrigen,   lngOrigen);
        destinoPoint = new GeoPoint(latDestino,  lngDestino);
        if (latPasajero != 0 && lngPasajero != 0)
            pasajeroPoint = new GeoPoint(latPasajero, lngPasajero);
    }

    /* ═══════════════════════════════════════════════════════
       INIT VIEWS
    ═══════════════════════════════════════════════════════ */
    private void initViews() {
        map                = findViewById(R.id.map_ver_ruta);
        txtOrigenInfo      = findViewById(R.id.txt_origen_info);
        txtDestinoInfo     = findViewById(R.id.txt_destino_info);
        txtDistanciaInfo   = findViewById(R.id.txt_distancia_info);
        txtDuracionInfo    = findViewById(R.id.txt_duracion_info);
        txtParadaPasajero  = findViewById(R.id.txt_parada_pasajero);
        txtEstadoParada    = findViewById(R.id.txt_estado_parada);
        txtInfoExtra       = findViewById(R.id.txt_info_extra);
        btnConfirmarParada = findViewById(R.id.btn_confirmar_parada);
        btnSolicitarViaje  = findViewById(R.id.btn_solicitar_viaje);
        loaderParada       = findViewById(R.id.loader_parada);

        btnConfirmarParada.setOnClickListener(v -> confirmarParada());
        btnSolicitarViaje.setOnClickListener(v -> solicitarViaje());
        btnSolicitarViaje.setEnabled(false);

        ImageButton btnBack = findViewById(R.id.btn_back_ver_ruta);
        if (btnBack != null) btnBack.setOnClickListener(v -> onBackPressed());

        View btnZoomIn  = findViewById(R.id.fab_zoom_in_pasajero);
        View btnZoomOut = findViewById(R.id.fab_zoom_out_pasajero);
        if (btnZoomIn  != null) btnZoomIn .setOnClickListener(v -> map.getController().zoomIn());
        if (btnZoomOut != null) btnZoomOut.setOnClickListener(v -> map.getController().zoomOut());
    }

    /* ═══════════════════════════════════════════════════════
       MAPA
    ═══════════════════════════════════════════════════════ */
    private void configurarMapa() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.getController().setZoom(13.0);
        map.getController().setCenter(origenPoint);
        map.setFlingEnabled(true);
        Configuration.getInstance().setOsmdroidTileCache(
                new File(getCacheDir(), "osmdroid_tiles"));
    }

    /* ═══════════════════════════════════════════════════════
       POBLAR DATOS
    ═══════════════════════════════════════════════════════ */
    private void poblarInfoRuta() {
        if (txtOrigenInfo     != null) txtOrigenInfo    .setText(origenRuta);
        if (txtDestinoInfo    != null) txtDestinoInfo   .setText(destinoRuta);
        if (txtDistanciaInfo  != null) txtDistanciaInfo .setText(String.format("%.1f km", distanciaKm));
        if (txtDuracionInfo   != null) txtDuracionInfo  .setText(String.format("%.0f min", duracionMin));
        if (txtParadaPasajero != null && !paradaPasajero.isEmpty())
            txtParadaPasajero.setText("Tu parada: " + paradaPasajero);

        if (txtInfoExtra != null && costoRuta > 0)
            txtInfoExtra.setText(String.format("💰 $%,.0f COP  ·  %s",
                    costoRuta, tipoTransporte.equals("motorcycle") ? "🏍 Moto" : "🚗 Carro"));
    }

    /* ═══════════════════════════════════════════════════════
       DIBUJAR RUTA DEL CONDUCTOR
       Prioridad: RouteManager (FastAPI) → OSRM Popayán → línea recta
    ═══════════════════════════════════════════════════════ */
    private void dibujarRutaDelConductor() {
        // Marcadores A y B del conductor
        marcadorOrigen  = crearMarcador(origenPoint,  "🚗 Origen del conductor",  0xFF009B8D, "A");
        marcadorDestino = crearMarcador(destinoPoint, "🏁 Destino del conductor", 0xFFEF4444, "B");

        routeManager.fetchRoutes(
                latOrigen, lngOrigen, latDestino, lngDestino, "FASTEST",
                new RouteManager.RouteCallback() {
                    @Override
                    public void onSuccess(RouteOptionsResponse response) {
                        if (response != null && response.routes != null && !response.routes.isEmpty()) {
                            RouteOption mejor = response.routes.get(0);
                            mainHandler.post(() -> {
                                if (mejor.geojson != null)
                                    dibujarPolylineDesdeGeojson(mejor.geojson);
                                else
                                    obtenerRutaOSRM();
                                ajustarVistaRuta();
                            });
                        } else {
                            mainHandler.post(() -> { obtenerRutaOSRM(); ajustarVistaRuta(); });
                        }
                    }
                    @Override
                    public void onError(String err) {
                        Log.w(TAG, "RouteManager error: " + err);
                        mainHandler.post(VerRutaPasajero.this::obtenerRutaOSRM);
                    }
                }
        );
    }

    /** OSRM Popayán directo */
    private void obtenerRutaOSRM() {
        executor.execute(() -> {
            try {
                String coords = lngOrigen + "," + latOrigen
                        + ";" + lngDestino + "," + latDestino;
                String urlStr = "https://osrm-popayan-production.up.railway.app"
                        + "/route/v1/driving/" + coords
                        + "?overview=full&geometries=geojson";

                String json = peticionHttp(urlStr);
                if (json == null || json.isEmpty()) throw new Exception("OSRM vacío");

                JSONObject res = new JSONObject(json);
                if (!"Ok".equals(res.optString("code"))) throw new Exception("OSRM error code");

                JSONObject route    = res.getJSONArray("routes").getJSONObject(0);
                JSONArray  coordsJs = route.getJSONObject("geometry").getJSONArray("coordinates");

                ArrayList<GeoPoint> puntos = new ArrayList<>();
                for (int i = 0; i < coordsJs.length(); i++) {
                    JSONArray c = coordsJs.getJSONArray(i);
                    puntos.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                }
                mainHandler.post(() -> {
                    dibujarPolylineConPuntos(puntos, 0xFF009B8D, 10f, 255);
                    ajustarVistaRuta();
                });
            } catch (Exception e) {
                Log.e(TAG, "OSRM directo falló: " + e.getMessage());
                mainHandler.post(() -> {
                    dibujarLineaDirecta();
                    ajustarVistaRuta();
                });
            }
        });
    }

    private void dibujarPolylineDesdeGeojson(Map<String, Object> geojson) {
        try {
            JSONObject geo = new JSONObject(geojson);
            JSONArray  coordsArr = geo.optJSONArray("coordinates");
            if (coordsArr == null) { obtenerRutaOSRM(); return; }

            ArrayList<GeoPoint> puntos = new ArrayList<>();
            for (int i = 0; i < coordsArr.length(); i++) {
                JSONArray c = coordsArr.getJSONArray(i);
                puntos.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
            }
            dibujarPolylineConPuntos(puntos, 0xFF009B8D, 10f, 255);
        } catch (Exception e) {
            Log.e(TAG, "GeoJSON parse error", e);
            obtenerRutaOSRM();
        }
    }

    private void dibujarLineaDirecta() {
        ArrayList<GeoPoint> puntos = new ArrayList<>();
        puntos.add(origenPoint);
        puntos.add(destinoPoint);
        dibujarPolylineConPuntos(puntos, 0xFF009B8D, 8f, 180);
    }

    private void dibujarPolylineConPuntos(List<GeoPoint> puntos,
                                          int colorInt, float ancho, int alpha) {
        // Sombra
        Polyline sombra = new Polyline(map);
        sombra.setPoints(puntos);
        sombra.setColor(Color.argb(50, 0, 0, 0));
        sombra.setWidth(ancho + 8f);
        map.getOverlays().add(sombra);
        polylinesRuta.add(sombra);

        // Borde blanco
        Polyline borde = new Polyline(map);
        borde.setPoints(puntos);
        borde.setColor(Color.WHITE);
        borde.setWidth(ancho + 4f);
        map.getOverlays().add(borde);
        polylinesRuta.add(borde);

        // Línea principal
        Polyline linea = new Polyline(map);
        linea.setPoints(puntos);
        linea.setColor(Color.argb(alpha,
                Color.red(colorInt), Color.green(colorInt), Color.blue(colorInt)));
        linea.setWidth(ancho);
        map.getOverlays().add(linea);
        polylinesRuta.add(linea);

        rePinMarkers();
        map.invalidate();
    }

    /* ═══════════════════════════════════════════════════════
       MARCADOR DE UBICACIÓN DEL PASAJERO
    ═══════════════════════════════════════════════════════ */
    private void mostrarUbicacionPasajero() {
        if (pasajeroPoint == null) return;
        marcadorPasajero = crearMarcador(pasajeroPoint,
                "📍 Tu ubicación actual", 0xFF6366F1, "Tú");
    }

    /* ═══════════════════════════════════════════════════════
       GEOCODIFICAR PARADA DEL PASAJERO
    ═══════════════════════════════════════════════════════ */
    private void geocodificarParadaPasajero() {
        if (paradaPasajero.isEmpty()) {
            if (txtEstadoParada != null)
                txtEstadoParada.setText("⚠️ No ingresaste una parada");
            return;
        }

        if (txtEstadoParada != null) {
            txtEstadoParada.setText("🔍 Buscando tu parada...");
            txtEstadoParada.setTextColor(Color.parseColor("#F59E0B"));
        }
        if (loaderParada != null) loaderParada.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                String geoUrl = "https://nominatim.openstreetmap.org/search?q="
                        + java.net.URLEncoder.encode(paradaPasajero + " Popayan Colombia", "UTF-8")
                        + "&format=json&limit=1";

                String resp = peticionHttp(geoUrl);
                if (resp == null || resp.isEmpty()) throw new Exception("Nominatim vacío");

                JSONArray arr = new JSONArray(resp);
                if (arr.length() == 0) throw new Exception("Parada no encontrada");

                JSONObject obj = arr.getJSONObject(0);
                double lat = obj.getDouble("lat");
                double lon = obj.getDouble("lon");
                paradaDireccionFinal = obj.optString("display_name", paradaPasajero);
                paradaPoint = new GeoPoint(lat, lon);

                mainHandler.post(() -> {
                    if (loaderParada != null) loaderParada.setVisibility(View.GONE);
                    ponerMarcadorParada(paradaPoint);
                    verificarParadaEnRuta();
                    ajustarVistaCompleta();
                });

            } catch (Exception e) {
                Log.e(TAG, "Geocoding error: " + e.getMessage());
                mainHandler.post(() -> {
                    if (loaderParada != null) loaderParada.setVisibility(View.GONE);
                    if (txtEstadoParada != null) {
                        txtEstadoParada.setText("⚠️ No se encontró \"" + paradaPasajero + "\"");
                        txtEstadoParada.setTextColor(Color.parseColor("#EF4444"));
                    }
                    mostrarSnackbar("No se encontró la parada. Verifica el nombre.", true);
                    // Igual habilitamos confirmar por si quiere proceder manualmente
                    btnConfirmarParada.setEnabled(true);
                });
            }
        });
    }

    private void ponerMarcadorParada(GeoPoint punto) {
        if (marcadorParada != null) map.getOverlays().remove(marcadorParada);
        marcadorParada = new Marker(map);
        marcadorParada.setPosition(punto);
        marcadorParada.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marcadorParada.setTitle("🚏 Tu parada: " + paradaPasajero);
        marcadorParada.setIcon(new BitmapDrawable(getResources(),
                crearIconoParada(0xFF8B5CF6)));
        map.getOverlays().add(marcadorParada);
        map.invalidate();

        // Animar mapa hacia la parada
        map.getController().animateTo(punto);
    }

    /* ═══════════════════════════════════════════════════════
       VERIFICAR SI LA PARADA ESTÁ EN EL CAMINO
    ═══════════════════════════════════════════════════════ */
    private void verificarParadaEnRuta() {
        if (paradaPoint == null) return;

        double distAlRuta = distanciaPuntoASegmento(paradaPoint, origenPoint, destinoPoint);
        boolean enRuta = distAlRuta < 2500; // 2.5 km de tolerancia

        if (txtEstadoParada != null) {
            if (enRuta) {
                txtEstadoParada.setText("✅ Tu parada está cerca de la ruta del conductor");
                txtEstadoParada.setTextColor(Color.parseColor("#10B981"));
            } else {
                txtEstadoParada.setText(String.format(
                        "⚠️ Tu parada está %.1f km de la ruta", distAlRuta / 1000.0));
                txtEstadoParada.setTextColor(Color.parseColor("#F59E0B"));
            }
        }
        btnConfirmarParada.setEnabled(true);
    }

    /* ═══════════════════════════════════════════════════════
       CONFIRMAR PARADA
    ═══════════════════════════════════════════════════════ */
    private void confirmarParada() {
        // Animar botón
        ObjectAnimator scX = ObjectAnimator.ofFloat(btnConfirmarParada, "scaleX", 1f, 0.95f, 1f);
        ObjectAnimator scY = ObjectAnimator.ofFloat(btnConfirmarParada, "scaleY", 1f, 0.95f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scX, scY);
        set.setDuration(200).start();

        btnConfirmarParada.setText("✅ Parada confirmada");
        btnConfirmarParada.setEnabled(false);

        // Mostrar y animar botón solicitar
        btnSolicitarViaje.setEnabled(true);
        btnSolicitarViaje.setAlpha(0f);
        btnSolicitarViaje.animate().alpha(1f).setDuration(400)
                .setInterpolator(new OvershootInterpolator()).start();

        mostrarSnackbar("✅ Parada confirmada: " + paradaPasajero, false);
        ajustarVistaCompleta();
    }

    /* ═══════════════════════════════════════════════════════
       SOLICITAR VIAJE
       POST /api/rutas/{idRuta}/paradas  (Constantes.rutaParadas)
    ═══════════════════════════════════════════════════════ */
    private void solicitarViaje() {
        btnSolicitarViaje.setEnabled(false);
        btnSolicitarViaje.setText("Enviando solicitud...");

        try {
            JSONObject body = new JSONObject();
            body.put("idRuta",       idRuta);
            body.put("idViaje",      idViaje);
            body.put("parada",       paradaPasajero);
            body.put("estado",       "PENDIENTE");

            // Coordenadas de la parada (si se geocodificó bien)
            if (paradaPoint != null) {
                body.put("latParada", paradaPoint.getLatitude());
                body.put("lngParada", paradaPoint.getLongitude());
            }
            // Coordenadas del pasajero
            if (pasajeroPoint != null) {
                body.put("latPasajero", pasajeroPoint.getLatitude());
                body.put("lngPasajero", pasajeroPoint.getLongitude());
            }

            // Usar Constantes.rutaParadas(idRuta) → /api/rutas/{idRuta}/paradas
            String endpoint = Constantes.rutaParadas((long) idRuta);

            ConexionApi.getInstance(this).post(endpoint, body,
                    response -> {
                        mostrarDialogoExito();
                    },
                    error -> {
                        Log.e(TAG, "Error paradas: " + error.toString());
                        // Intentar con RESERVAS como fallback alineado a segment-fares
                        solicitarViajeConReservas();
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error construyendo petición", e);
            btnSolicitarViaje.setEnabled(true);
            btnSolicitarViaje.setText("SOLICITAR VIAJE");
        }
    }

    /** Fallback: POST /api/reservas si el endpoint de paradas no existe.
     *  Aquí intentamos también enviar idParadaSubida / idParadaBajada
     *  para que el backend pueda usar segment-fares.
     */
    private void solicitarViajeConReservas() {
        // 1) Traer paradas reales de la ruta para mapear origen/bajada a idParada*
        String urlParadas = Constantes.paradasPorRuta((long) idRuta);

        ConexionApi.getInstance(this).getArrayNoCache(
                urlParadas,
                arr -> {
                    int idParadaSubida = 0;
                    int idParadaBajada = 0;

                    try {
                        // Buscar origen como parada de inicio (por nombre o tipo)
                        if (origenPoint != null) {
                            double bestDist = Double.MAX_VALUE;
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject p = arr.optJSONObject(i);
                                if (p == null) continue;
                                int idP = p.optInt("idParada", p.optInt("id", 0));
                                if (idP <= 0) continue;
                                String nom = p.optString("nombre", "").trim();
                                String tipo = p.optString("tipo", "");
                                double lat = p.optDouble("lat", 0);
                                double lng = p.optDouble("lng", 0);

                                boolean esOrigenNombre = !nom.isEmpty() && nom.equalsIgnoreCase(origenRuta);
                                boolean esOrigenTipo   = tipo != null && tipo.toUpperCase(Locale.ROOT).contains("ORIGEN");

                                double dist = distanciaSimple(origenPoint.getLatitude(), origenPoint.getLongitude(), lat, lng);

                                if ((esOrigenNombre || esOrigenTipo || dist < bestDist) && lat != 0) {
                                    bestDist = dist;
                                    idParadaSubida = idP;
                                }
                            }
                        }

                        // Buscar parada de bajada como la más cercana al punto de parada
                        if (paradaPoint != null) {
                            double bestDistB = Double.MAX_VALUE;
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject p = arr.optJSONObject(i);
                                if (p == null) continue;
                                int idP = p.optInt("idParada", p.optInt("id", 0));
                                if (idP <= 0) continue;
                                double lat = p.optDouble("lat", 0);
                                double lng = p.optDouble("lng", 0);
                                if (lat == 0) continue;
                                double dist = distanciaSimple(paradaPoint.getLatitude(), paradaPoint.getLongitude(), lat, lng);
                                if (dist < bestDistB) {
                                    bestDistB = dist;
                                    idParadaBajada = idP;
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error mapeando paradas a IDs: " + e.getMessage());
                    }

                    enviarReservaFallback(idParadaSubida, idParadaBajada);
                },
                error -> {
                    Log.e(TAG, "Error obteniendo paradas para fallback: " + error.toString());
                    // Enviar reserva sin IDs de paradas (backend usará Haversine)
                    enviarReservaFallback(0, 0);
                }
        );
    }

    /** Construye y envía el body de reserva usando, si es posible, idParadaSubida/Bajada. */
    private void enviarReservaFallback(int idParadaSubida, int idParadaBajada) {
        try {
            JSONObject body = new JSONObject();
            body.put("idViajes",      idViaje);
            body.put("idViaje",       idViaje);
            body.put("idRuta",        idRuta);
            body.put("paradaTexto",   paradaPasajero);
            body.put("estado",        "PENDIENTE");

            // Subida: usamos el origen de la ruta del viaje como punto de inicio
            if (origenPoint != null) {
                body.put("latSubida",          origenPoint.getLatitude());
                body.put("lngSubida",          origenPoint.getLongitude());
                body.put("nombreParadaSubida", origenRuta != null ? origenRuta : "Origen");
                body.put("latOrigen",          origenPoint.getLatitude());
                body.put("lngOrigen",          origenPoint.getLongitude());
                body.put("latInicio",          origenPoint.getLatitude());
                body.put("lngInicio",          origenPoint.getLongitude());
                body.put("nombreParadaInicio", origenRuta != null ? origenRuta : "Origen");
                body.put("origenLat",          origenPoint.getLatitude());
                body.put("origenLng",          origenPoint.getLongitude());
            }

            // Bajada: la parada que el pasajero escribió/geocodificó
            if (paradaPoint != null) {
                body.put("latBajada",          paradaPoint.getLatitude());
                body.put("lngBajada",          paradaPoint.getLongitude());
                body.put("nombreParadaBajada", paradaPasajero);
                body.put("latParada",          paradaPoint.getLatitude());
                body.put("lngParada",          paradaPoint.getLongitude());
                body.put("nombreParada",       paradaPasajero);
                body.put("latDestino",         paradaPoint.getLatitude());
                body.put("lngDestino",         paradaPoint.getLongitude());
            }

            // IDs de paradas si los pudimos resolver
            if (idParadaSubida > 0) {
                body.put("idParadaSubida", idParadaSubida);
                body.put("idParadaInicio", idParadaSubida);
            }
            if (idParadaBajada > 0) {
                body.put("idParadaBajada", idParadaBajada);
                body.put("idParadaFin",    idParadaBajada);
                body.put("idParada",       idParadaBajada);
            }

            ConexionApi.getInstance(this).post(Constantes.RESERVAS, body,
                    response -> {
                        mostrarDialogoExito();
                        btnSolicitarViaje.setEnabled(true);
                        btnSolicitarViaje.setText("SOLICITAR VIAJE");
                    },
                    error -> {
                        Log.e(TAG, "Error reservas: " + error.toString());
                        btnSolicitarViaje.setEnabled(true);
                        btnSolicitarViaje.setText("SOLICITAR VIAJE");
                        mostrarSnackbar("❌ Error al enviar solicitud. Intenta de nuevo.", true);
                    }
            );
        } catch (Exception e) {
            btnSolicitarViaje.setEnabled(true);
            btnSolicitarViaje.setText("SOLICITAR VIAJE");
            mostrarSnackbar("❌ Error inesperado", true);
        }
    }

    /** Distancia aproximada en metros entre dos puntos (Haversine simplificado). */
    private double distanciaSimple(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }


    /* ═══════════════════════════════════════════════════════
       DIÁLOGO DE ÉXITO
    ═══════════════════════════════════════════════════════ */
    private void mostrarDialogoExito() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setCancelable(false);

        // ── Contenedor raíz ──
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.WHITE);
        int p = dp(24);
        root.setPadding(p, dp(16), p, dp(32));

        // Handle
        View handle = new View(this);
        LinearLayout.LayoutParams lpHandle = new LinearLayout.LayoutParams(dp(40), dp(4));
        lpHandle.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lpHandle.bottomMargin = dp(20);
        handle.setLayoutParams(lpHandle);
        handle.setBackgroundColor(Color.parseColor("#CBD5E1"));
        root.addView(handle);

        // Emoji de éxito
        TextView emoji = new TextView(this);
        emoji.setText("✅");
        emoji.setTextSize(42f);
        emoji.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpEmoji = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpEmoji.bottomMargin = dp(12);
        lpEmoji.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        emoji.setLayoutParams(lpEmoji);
        root.addView(emoji);

        // Título
        TextView titulo = new TextView(this);
        titulo.setText("¡Parada enviada!");
        titulo.setTextSize(22f);
        titulo.setTypeface(null, Typeface.BOLD);
        titulo.setTextColor(Color.parseColor("#1A2422"));
        titulo.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpTit = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTit.bottomMargin = dp(10);
        titulo.setLayoutParams(lpTit);
        root.addView(titulo);

        // Mensaje con la parada
        TextView mensaje = new TextView(this);
        mensaje.setText("Tu parada en \"" + paradaPasajero
                + "\" fue enviada al conductor.\n¡Espera su confirmación!");
        mensaje.setTextSize(14f);
        mensaje.setTextColor(Color.parseColor("#64748B"));
        mensaje.setGravity(android.view.Gravity.CENTER);
        mensaje.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams lpMsg = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpMsg.bottomMargin = dp(24);
        mensaje.setLayoutParams(lpMsg);
        root.addView(mensaje);

        // Fila de íconos informativos
        LinearLayout filaIconos = new LinearLayout(this);
        filaIconos.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpFila = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFila.bottomMargin = dp(24);
        filaIconos.setLayoutParams(lpFila);

        String[][] infoItems = {{"📱", "Recibirás\nnotificación"}, {"⏱️", "Espera\nconfirmación"}, {"🚗", "El conductor\nte recogerá"}};
        for (String[] item : infoItems) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams lpCol = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            col.setLayoutParams(lpCol);

            TextView ic = new TextView(this); ic.setText(item[0]); ic.setTextSize(24f);
            ic.setGravity(android.view.Gravity.CENTER);
            col.addView(ic);

            TextView lb = new TextView(this); lb.setText(item[1]); lb.setTextSize(11f);
            lb.setTextColor(Color.parseColor("#94A3B8")); lb.setGravity(android.view.Gravity.CENTER);
            col.addView(lb);
            filaIconos.addView(col);
        }
        root.addView(filaIconos);

        // Botón ENTENDIDO
        MaterialButton btnOk = new MaterialButton(this);
        btnOk.setText("ENTENDIDO");
        btnOk.setTextSize(15f);
        btnOk.setTypeface(null, Typeface.BOLD);
        btnOk.setTextColor(Color.WHITE);
        btnOk.setBackgroundColor(Color.parseColor("#009B8D"));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        btnOk.setLayoutParams(lpBtn);
        btnOk.setOnClickListener(v -> { dialog.dismiss(); finish(); });
        root.addView(btnOk);

        dialog.setContentView(root);
        dialog.show();
    }

    /** Convierte dp a píxeles */
    private int dp(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /* ═══════════════════════════════════════════════════════
       MARCADORES
    ═══════════════════════════════════════════════════════ */
    private Marker crearMarcador(GeoPoint punto, String titulo, int colorInt, String letra) {
        Marker m = new Marker(map);
        m.setPosition(punto);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle(titulo);
        m.setIcon(new BitmapDrawable(getResources(), crearIconoMarcador(colorInt, letra)));
        map.getOverlays().add(m);
        map.invalidate();
        return m;
    }

    private void rePinMarkers() {
        // Mantener marcadores siempre por encima de las polylines
        Marker[] markers = {marcadorOrigen, marcadorDestino, marcadorPasajero, marcadorParada};
        for (Marker mk : markers) {
            if (mk != null) {
                map.getOverlays().remove(mk);
                map.getOverlays().add(mk);
            }
        }
        map.invalidate();
    }

    /* ═══════════════════════════════════════════════════════
       AJUSTE DE VISTA
    ═══════════════════════════════════════════════════════ */
    private void ajustarVistaRuta() {
        double minLat = Math.min(latOrigen, latDestino);
        double maxLat = Math.max(latOrigen, latDestino);
        double minLon = Math.min(lngOrigen, lngDestino);
        double maxLon = Math.max(lngOrigen, lngDestino);
        double pLat   = Math.max((maxLat - minLat) * 0.3, 0.01);
        double pLon   = Math.max((maxLon - minLon) * 0.3, 0.01);
        BoundingBox bb = new BoundingBox(maxLat + pLat, maxLon + pLon, minLat - pLat, minLon - pLon);
        map.post(() -> { try { map.zoomToBoundingBox(bb, true, 80); } catch (Exception ignored) {} });
    }

    private void ajustarVistaCompleta() {
        List<GeoPoint> pts = new ArrayList<>();
        pts.add(origenPoint); pts.add(destinoPoint);
        if (pasajeroPoint != null) pts.add(pasajeroPoint);
        if (paradaPoint   != null) pts.add(paradaPoint);

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (GeoPoint p : pts) {
            minLat = Math.min(minLat, p.getLatitude()); maxLat = Math.max(maxLat, p.getLatitude());
            minLon = Math.min(minLon, p.getLongitude()); maxLon = Math.max(maxLon, p.getLongitude());
        }
        double pLat = Math.max((maxLat - minLat) * 0.3, 0.01);
        double pLon = Math.max((maxLon - minLon) * 0.3, 0.01);
        BoundingBox bb = new BoundingBox(maxLat + pLat, maxLon + pLon, minLat - pLat, minLon - pLon);
        map.post(() -> { try { map.zoomToBoundingBox(bb, true, 80); } catch (Exception ignored) {} });
    }

    /* ═══════════════════════════════════════════════════════
       GEOMETRÍA
    ═══════════════════════════════════════════════════════ */
    private double distanciaPuntoASegmento(GeoPoint p, GeoPoint a, GeoPoint b) {
        double ax = a.getLongitude(), ay = a.getLatitude();
        double bx = b.getLongitude(), by = b.getLatitude();
        double px = p.getLongitude(), py = p.getLatitude();
        double abx = bx - ax, aby = by - ay;
        double apx = px - ax, apy = py - ay;
        double ab2 = abx * abx + aby * aby;
        double t   = ab2 == 0 ? 0 : Math.max(0, Math.min(1, (apx * abx + apy * aby) / ab2));
        GeoPoint near = new GeoPoint(ay + t * aby, ax + t * abx);
        double dLat = Math.toRadians(p.getLatitude()  - near.getLatitude());
        double dLon = Math.toRadians(p.getLongitude() - near.getLongitude());
        double h = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(p.getLatitude()))
                * Math.cos(Math.toRadians(near.getLatitude()))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    /* ═══════════════════════════════════════════════════════
       UTILIDADES
    ═══════════════════════════════════════════════════════ */
    private void mostrarSnackbar(String msg, boolean esError) {
        Snackbar sb = Snackbar.make(rootView, msg,
                esError ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT);
        sb.getView().setBackgroundColor(esError ? 0xFFB00020 : 0xFF1A2422);
        TextView tv = sb.getView().findViewById(
                com.google.android.material.R.id.snackbar_text);
        if (tv != null) { tv.setTextColor(Color.WHITE); tv.setTypeface(null, Typeface.BOLD); }
        sb.show();
    }

    private String peticionHttp(String urlStr) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestProperty("User-Agent", "Moviflexx/2.0 (Android)");
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            if (c.getResponseCode() != 200) return "";
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally { if (c != null) c.disconnect(); }
    }

    private Bitmap crearIconoMarcador(int colorInt, String letra) {
        int size = 96;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        // Sombra
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setColor(Color.argb(80, 0, 0, 0));
        c.drawCircle(size / 2f + 3, size / 2f + 5, size / 2f - 6, s);
        // Círculo de color
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(colorInt);
        c.drawCircle(size / 2f, size / 2f - 4, size / 2f - 8, p);
        // Borde blanco
        p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4f);
        c.drawCircle(size / 2f, size / 2f - 4, size / 2f - 8, p);
        // Letra
        p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE);
        p.setTextSize(letra.length() > 2 ? 20f : 34f);
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER);
        c.drawText(letra, size / 2f, size / 2f + 10, p);
        return bmp;
    }

    private Bitmap crearIconoParada(int colorInt) {
        int size = 96;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        // Fondo redondeado
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(colorInt);
        c.drawRoundRect(8, 6, size - 8, size - 22, 18, 18, bg);
        // Borde blanco
        Paint br = new Paint(Paint.ANTI_ALIAS_FLAG);
        br.setColor(Color.WHITE); br.setStyle(Paint.Style.STROKE); br.setStrokeWidth(4f);
        c.drawRoundRect(8, 6, size - 8, size - 22, 18, 18, br);
        // Punta triangular
        Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG); tri.setColor(colorInt);
        Path path = new Path();
        path.moveTo(size / 2f - 10, size - 22);
        path.lineTo(size / 2f + 10, size - 22);
        path.lineTo(size / 2f, size - 4);
        path.close();
        c.drawPath(path, tri);
        // Icono de parada
        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(Color.WHITE); txt.setTextSize(30f);
        txt.setTypeface(Typeface.DEFAULT_BOLD); txt.setTextAlign(Paint.Align.CENTER);
        c.drawText("P", size / 2f, (size - 22) / 2f + 12, txt);
        return bmp;
    }
}