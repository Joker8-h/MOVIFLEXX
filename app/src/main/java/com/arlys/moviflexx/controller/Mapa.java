package com.arlys.moviflexx.controller;

import android.app.AlertDialog;
import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Manager.CalificacionesManager;
import com.arlys.moviflexx.model.Manager.RouteManager;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.pojo.RouteOption;
import com.arlys.moviflexx.model.pojo.RouteOptionsResponse;
import com.arlys.moviflexx.utils.GeoJsonHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Mapa extends AppCompatActivity {

    private static final String TAG           = "Mapa";
    private static final int    LOCATION_PERM = 1;

    private static final String OSRM_PROPIO  =
            "https://optimizacionofrutas-production.up.railway.app";
    private static final String OSRM_PUBLICO =
            "https://router.project-osrm.org";

    private static final int COL_RUTA    = 0xFF009B8D;
    private static final int COL_TRAMO   = 0xFFFF6F00;
    private static final int COL_CONDUCT = 0xFF1565C0;

    private MapView  map;
    private Marker   marcadorGpsPropio;
    private TextView tvEta;
    private TextView tvDistEta;
    private DatabaseReference refEstadoViaje;
    private com.google.firebase.database.ValueEventListener listenerEstadoViaje;

    private FusedLocationProviderClient fusedClient;
    private LocationCallback            locationCallback;
    private GeoPoint miUltimaPosicion = null;

    private final List<Polyline> lineasPintadas = new ArrayList<>();
    private final List<Polyline> lineasEta      = new ArrayList<>();
    private boolean rutaSolicitada = false;

    private double  destinoLat = 0, destinoLng = 0;
    private int     idViaje = 0;
    private boolean desdeViajeActivo = false;

    // pOrigen/pDestino = extremos de la ruta del CONDUCTOR
    // pSubida/pBajada  = puntos del PASAJERO
    private GeoPoint pOrigen  = null;
    private GeoPoint pDestino = null;
    private GeoPoint pSubida  = null;
    private GeoPoint pBajada  = null;
    private String   nomSubida = "", nomBajada = "", nomConductor = "";
    private String   nomDestinoRuta = ""; // nombre del destino FINAL de la ruta del conductor

    private final ArrayList<GeoPoint> waypointsRuta = new ArrayList<>();

    private Marker   marcadorConductorRT  = null;
    private GeoPoint posAnteriorConductor = null;
    private float    rumboConductor       = 0f;
    private final Handler hPoll = new Handler(Looper.getMainLooper());
    private Runnable      rPoll = null;

    private boolean pasajeroRecogido  = false;
    private boolean viajeYaFinalizado = false;
    private Marker  marcadorSubida    = null;
    private Marker  marcadorBajada    = null;

    private SessionManager    session;
    private DatabaseReference refFirebase;
    private RouteManager      routeManager;

    // =========================================================================
    //  LIFECYCLE
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_mapa);

        session      = new SessionManager(this);
        fusedClient  = LocationServices.getFusedLocationProviderClient(this);
        routeManager = new RouteManager();
        refFirebase  = FirebaseDatabase.getInstance()
                .getReference("ubicaciones").child("conductor_" + session.getIdUsuario());

        destinoLat       = getIntent().getDoubleExtra("DESTINO_LAT",  0);
        destinoLng       = getIntent().getDoubleExtra("DESTINO_LNG",  0);
        idViaje          = getIntent().getIntExtra("ID_VIAJE",         0);
        desdeViajeActivo = getIntent().getBooleanExtra("DESDE_VIAJE", false);

        String estadoReserva = getIntent().getStringExtra("ESTADO_RESERVA");
        if ("RECOGIDO".equals(estadoReserva) || "COMPLETADO".equals(estadoReserva)) {
            pasajeroRecogido = true;
        }

        Log.d(TAG, "=== INTENT RECIBIDO ===");
        Log.d(TAG, "  DESDE_VIAJE    = " + desdeViajeActivo);
        Log.d(TAG, "  ID_VIAJE       = " + idViaje);
        Log.d(TAG, "  DESTINO_LAT    = " + destinoLat);
        Log.d(TAG, "  DESTINO_LNG    = " + destinoLng);
        Log.d(TAG, "  ESTADO_RESERVA = " + estadoReserva);
        Log.d(TAG, "  PARADAS_JSON   = " + (getIntent().getStringExtra("PARADAS_JSON") != null
                ? getIntent().getStringExtra("PARADAS_JSON").length() + " chars" : "null"));
        Log.d(TAG, "======================");

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(true);
        map.setTilesScaledToDpi(true);
        map.setUseDataConnection(true);
        map.getController().setZoom(15.0);
        map.getController().setCenter(new GeoPoint(2.4419, -76.6063));

        configurarBottomNav();
        crearChipsEta();

        boolean tieneDestino   = destinoLat != 0
                || getIntent().getDoubleExtra("LAT_BAJADA", 0) != 0;
        boolean tieneViajeInfo = desdeViajeActivo || idViaje > 0 || tieneDestino;

        if (tieneViajeInfo) {
            if (!desdeViajeActivo) desdeViajeActivo = true;
            cargarExtrasViajeYDibujar();
        }

        configurarGpsPropio();
    }

    @Override protected void onResume() {
        super.onResume();
        map.onResume();
        if (desdeViajeActivo && !session.isConductor() && rPoll != null)
            hPoll.postDelayed(rPoll, 1000);
    }

    @Override protected void onPause() {
        super.onPause();
        map.onPause();
        if (fusedClient != null && locationCallback != null)
            fusedClient.removeLocationUpdates(locationCallback);
        if (rPoll != null) hPoll.removeCallbacks(rPoll);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (rPoll != null) hPoll.removeCallbacks(rPoll);
    }

    // =========================================================================
    //  VIAJE ACTIVO
    // =========================================================================

    private void cargarExtrasViajeYDibujar() {
        boolean esConductor = session.isConductor();

        double latO = getIntent().getDoubleExtra("ORIGEN_LAT",  0);
        double lngO = getIntent().getDoubleExtra("ORIGEN_LNG",  0);
        double latD = getIntent().getDoubleExtra("DESTINO_LAT", 0);   // destino REAL ruta conductor
        double lngD = getIntent().getDoubleExtra("DESTINO_LNG", 0);
        double latS = getIntent().getDoubleExtra("LAT_SUBIDA",  0);   // subida pasajero
        double lngS = getIntent().getDoubleExtra("LNG_SUBIDA",  0);
        double latB = getIntent().getDoubleExtra("LAT_BAJADA",  0);   // bajada pasajero
        double lngB = getIntent().getDoubleExtra("LNG_BAJADA",  0);

        String ns = getIntent().getStringExtra("NOM_SUBIDA");
        String nb = getIntent().getStringExtra("NOM_BAJADA");
        String nc = getIntent().getStringExtra("NOM_CONDUCTOR");
        nomSubida    = ns != null ? ns : "";
        nomBajada    = nb != null ? nb : "";
        nomConductor = nc != null ? nc : "Conductor";

        // ── Origen de la ruta del conductor ──────────────────────────────────
        if (latO != 0) pOrigen = new GeoPoint(latO, lngO);

        // ── Destino FINAL de la ruta del conductor ───────────────────────────
        // Prioridad: DESTINO_LAT → destinoLat (onCreate) → última parada de PARADAS_JSON
        // ⚠️  LAT_BAJADA = parada del PASAJERO — NUNCA se usa como pDestino
        if (latD != 0) {
            pDestino = new GeoPoint(latD, lngD);
        } else if (destinoLat != 0) {
            pDestino = new GeoPoint(destinoLat, destinoLng);
        }
        if (pDestino == null) {
            pDestino = extraerDestinoDeParadasJson();
        }

        // ── Subida y bajada del PASAJERO ─────────────────────────────────────
        if (latS != 0) pSubida = new GeoPoint(latS, lngS);
        if (latB != 0) pBajada = new GeoPoint(latB, lngB);

        Log.d(TAG, "=== PUNTOS RESUELTOS ===");
        Log.d(TAG, "  pOrigen  = " + pOrigen  + "  (inicio ruta conductor)");
        Log.d(TAG, "  pDestino = " + pDestino + "  (fin ruta conductor)");
        Log.d(TAG, "  pSubida  = " + pSubida  + "  (recogida pasajero)");
        Log.d(TAG, "  pBajada  = " + pBajada  + "  (bajada pasajero)");
        Log.d(TAG, "=======================");

        leerWaypointsDelIntent();
        Log.d(TAG, "  waypoints intermedios = " + waypointsRuta.size());

        inflarBanner(esConductor);
        if (!esConductor) arrancarPollingConductor();
        if (!esConductor) escucharFinViajeComoPasajero();

        // Si faltan coords → cargar desde la API antes de dibujar
        if ((pOrigen == null || pDestino == null) && idViaje > 0) {
            Log.w(TAG, "Coords incompletas → cargando viaje " + idViaje + " desde API");
            cargarCoordsDesdeApi(esConductor);
            return;
        }

        // Conductor: carga los puntos del pasajero (P1/🚏)
        if (esConductor && idViaje > 0) cargarSubidaBajadaPasajero();

        // Pasajero: si no le mandaron sus paradas por Intent, las carga desde la API
        if (!esConductor && idViaje > 0 && pSubida == null && pBajada == null) {
            cargarParadasPasajeroDesdeApi();
        }

        if (pOrigen != null && pDestino != null) {
            map.post(() -> dibujarRutaViaje(esConductor));
        } else if (pDestino != null) {
            usarGpsComorOrigen(esConductor);
        } else {
            Log.w(TAG, "Sin coordenadas suficientes para dibujar ruta");
            agregarMarcadores(esConductor);
        }
    }

    /**
     * Cuando HomeConductor no manda DESTINO_LAT/ORIGEN_LAT en el Intent,
     * carga el viaje completo desde /api/viajes/{id} y extrae las coords.
     *
     * ESTRATEGIA para encontrar el destino REAL del conductor:
     * 1. ruta.latDestino / ruta.lngDestino  (campo directo en el objeto ruta)
     * 2. ruta.latFin / lngFin
     * 3. Campo "nombre" de la ruta → parte después de "→"  → geocodificar
     * 4. Paradas: la de mayor orden CON tipo distinto de BAJADA/SUBIDA
     * 5. Paradas: la última entrada del array que NO sea tipo BAJADA
     *
     * ⚠️ NUNCA usar la parada con tipo==BAJADA como destino del conductor.
     * ⚠️ NUNCA usar LAT_BAJADA del Intent como pDestino.
     */
    private void cargarCoordsDesdeApi(boolean esConductor) {
        String url = Constantes.BASE_URL + "/api/viajes/" + idViaje;
        ConexionApi.getInstance(this).getObject(url,
                viajeJson -> {
                    Log.d(TAG, "=== VIAJE API ===\n" + viajeJson.toString());
                    try {
                        JSONObject ruta = viajeJson.optJSONObject("ruta");
                        JSONObject src  = ruta != null ? ruta : viajeJson;

                        // ── 1. Origen ──────────────────────────────────────────
                        if (pOrigen == null) {
                            double la = primerDouble(src,"latOrigen","latitudOrigen","latInicio","lat_origen");
                            double lo = primerDouble(src,"lngOrigen","longitudOrigen","lngInicio","lng_origen");
                            if (la == 0 && ruta != null) {
                                la = primerDouble(viajeJson,"latOrigen","latitudOrigen");
                                lo = primerDouble(viajeJson,"lngOrigen","longitudOrigen");
                            }
                            if (la != 0) pOrigen = new GeoPoint(la, lo);
                        }

                        // ── 2. Destino del conductor — múltiples estrategias ───

                        // Estrategia A: campo directo en ruta (el más confiable)
                        if (pDestino == null && ruta != null) {
                            double la = primerDouble(ruta,"latDestino","latitudDestino","latFin","lat_destino");
                            double lo = primerDouble(ruta,"lngDestino","longitudDestino","lngFin","lng_destino");
                            if (la != 0) {
                                pDestino = new GeoPoint(la, lo);
                                Log.d(TAG, "pDestino desde ruta.latDestino: " + pDestino);
                            }
                        }
                        // Estrategia B: campo directo en viajeJson
                        if (pDestino == null) {
                            double la = primerDouble(viajeJson,"latDestino","latitudDestino","latFin");
                            double lo = primerDouble(viajeJson,"lngDestino","longitudDestino","lngFin");
                            if (la != 0) {
                                pDestino = new GeoPoint(la, lo);
                                Log.d(TAG, "pDestino desde viaje.latDestino: " + pDestino);
                            }
                        }

                        // Estrategia C: paradas — mayor orden con tipo ≠ BAJADA y ≠ SUBIDA
                        JSONArray paradas = null;
                        if (ruta != null) paradas = ruta.optJSONArray("paradas");
                        if (paradas == null) paradas = viajeJson.optJSONArray("paradas");

                        if (paradas != null && paradas.length() >= 2) {
                            JSONObject mejorOrigen = null, mejorDestino = null;
                            int maxOrden = -1;
                            int minOrden = Integer.MAX_VALUE;

                            for (int i = 0; i < paradas.length(); i++) {
                                JSONObject p = paradas.optJSONObject(i);
                                if (p == null) continue;
                                double pLat = p.optDouble("lat", Double.NaN);
                                if (Double.isNaN(pLat) || pLat == 0) continue;

                                int    orden = p.optInt("orden", i); // usar índice si no hay orden
                                String tipo  = p.optString("tipo","").toUpperCase().trim();

                                // Saltar paradas de pasajero: BAJADA sola
                                // SUBIDA puede ser el inicio de la ruta (orden 0)
                                if ("BAJADA".equals(tipo)) {
                                    Log.d(TAG, "  parada["+i+"] BAJADA ignorada para destino: lat="+pLat);
                                    continue;
                                }

                                // Origen: el de menor orden
                                if (orden < minOrden) {
                                    minOrden = orden;
                                    mejorOrigen = p;
                                }
                                // Destino: el de mayor orden
                                if (orden > maxOrden) {
                                    maxOrden = orden;
                                    mejorDestino = p;
                                }
                            }

                            // Asignar origen si falta
                            if (pOrigen == null && mejorOrigen != null) {
                                double la = mejorOrigen.optDouble("lat",0);
                                double lo = mejorOrigen.optDouble("lng",0);
                                if (la != 0) {
                                    pOrigen = new GeoPoint(la, lo);
                                    String nom = mejorOrigen.optString("nombre","").trim();
                                    if (!nom.isEmpty() && !nom.equals("null") && nomSubida.isEmpty())
                                        nomSubida = nom;
                                    Log.d(TAG, "pOrigen desde paradas: " + pOrigen);
                                }
                            }

                            // Asignar destino solo si aún no está definido
                            // Y verificar que sea DISTINTO al origen
                            if (pDestino == null && mejorDestino != null && mejorDestino != mejorOrigen) {
                                double la = mejorDestino.optDouble("lat",0);
                                double lo = mejorDestino.optDouble("lng",0);
                                if (la != 0 && !coordsIguales(la, lo,
                                        pOrigen != null ? pOrigen.getLatitude()  : 0,
                                        pOrigen != null ? pOrigen.getLongitude() : 0)) {
                                    pDestino = new GeoPoint(la, lo);
                                    String nom = mejorDestino.optString("nombre","").trim();
                                    if (!nom.isEmpty() && !nom.equals("null") && nomBajada.isEmpty())
                                        nomDestinoRuta = nom;
                                    Log.d(TAG, "pDestino desde paradas (mayor orden): " + pDestino);
                                }
                            }

                            // Waypoints intermedios: todo lo que NO es bajada ni el origen ni el destino
                            if (waypointsRuta.isEmpty()) {
                                for (int i = 0; i < paradas.length(); i++) {
                                    JSONObject p = paradas.optJSONObject(i);
                                    if (p == null) continue;
                                    String tipo = p.optString("tipo","").toUpperCase().trim();
                                    if ("BAJADA".equals(tipo) || "SUBIDA".equals(tipo)) continue;
                                    int orden = p.optInt("orden", i);
                                    if (orden == minOrden || orden == maxOrden) continue;
                                    double la = p.optDouble("lat",0);
                                    double lo = p.optDouble("lng",0);
                                    if (la != 0) waypointsRuta.add(new GeoPoint(la, lo));
                                }
                            }
                        }

                        // Estrategia D: si pDestino sigue null, intentar geocodificar
                        // el nombre de la ruta (parte después de →)
                        if (pDestino == null && ruta != null) {
                            String nomRuta = ruta.optString("nombre","");
                            if (nomRuta.contains("→") || nomRuta.contains("->")) {
                                String[] parts = nomRuta.split("→|->", 2);
                                if (parts.length > 1) {
                                    final String destNom = parts[1].trim();
                                    if (!destNom.isEmpty() && !nomBajada.isEmpty())
                                        nomDestinoRuta = destNom;
                                    // Geocodificar en background
                                    new Thread(() -> {
                                        try {
                                            String q = destNom + ", Popayan, Colombia";
                                            String geoUrl = "https://nominatim.openstreetmap.org/search?q="
                                                    + java.net.URLEncoder.encode(q,"UTF-8")
                                                    + "&format=json&limit=1&countrycodes=co";
                                            String resp = http(geoUrl);
                                            if (resp != null && !resp.isEmpty() && !resp.equals("[]")) {
                                                JSONArray arr = new JSONArray(resp);
                                                if (arr.length() > 0) {
                                                    JSONObject geo = arr.getJSONObject(0);
                                                    double la = geo.getDouble("lat");
                                                    double lo = geo.getDouble("lon");
                                                    pDestino = new GeoPoint(la, lo);
                                                    if (nomDestinoRuta.isEmpty()) nomDestinoRuta = destNom;
                                                    Log.d(TAG,"pDestino geocodificado: "+pDestino);
                                                    runOnUiThread(() -> map.post(() -> dibujarRutaViaje(esConductor)));
                                                }
                                            }
                                        } catch (Exception e) {
                                            Log.w(TAG,"geocodificar destino: "+e.getMessage());
                                        }
                                    }).start();
                                }
                            }
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "cargarCoordsDesdeApi parse: " + e.getMessage());
                    }

                    Log.d(TAG, "FINAL → pOrigen=" + pOrigen + " pDestino=" + pDestino);

                    // Continuar con el flujo normal
                    if (esConductor && idViaje > 0) cargarSubidaBajadaPasajero();
                    if (!esConductor && idViaje > 0 && pSubida == null && pBajada == null)
                        cargarParadasPasajeroDesdeApi();
                    if (pOrigen != null && pDestino != null)
                        map.post(() -> dibujarRutaViaje(esConductor));
                    else if (pDestino != null)
                        usarGpsComorOrigen(esConductor);
                    else {
                        Log.w(TAG, "API tampoco tiene coords completas");
                        agregarMarcadores(esConductor);
                    }
                },
                err -> {
                    Log.e(TAG, "cargarCoordsDesdeApi error: " + err);
                    if (esConductor && idViaje > 0) cargarSubidaBajadaPasajero();
                    agregarMarcadores(esConductor);
                }
        );
    }

    private boolean coordsIguales(double la1, double lo1, double la2, double lo2) {
        return Math.abs(la1-la2) < 0.0002 && Math.abs(lo1-lo2) < 0.0002;
    }

    /** Intenta varios nombres de campo y devuelve el primero != 0. */
    private double primerDouble(JSONObject obj, String... campos) {
        for (String c : campos) {
            double v = obj.optDouble(c, 0);
            if (v != 0) return v;
        }
        return 0;
    }

    /**
     * Extrae el destino final desde PARADAS_JSON.
     * La ÚLTIMA entrada del array = destino final del conductor.
     */
    private GeoPoint extraerDestinoDeParadasJson() {
        String paradasJson = getIntent().getStringExtra("PARADAS_JSON");
        if (paradasJson == null || paradasJson.isEmpty()) return null;
        try {
            JSONArray arr = new JSONArray(paradasJson);
            if (arr.length() == 0) return null;
            JSONObject ultima = arr.getJSONObject(arr.length() - 1);
            double lat = ultima.optDouble("lat", ultima.optDouble("latitud", 0));
            double lng = ultima.optDouble("lng", ultima.optDouble("longitud", 0));
            if (lat != 0 && lng != 0) {
                Log.d(TAG, "pDestino extraído de PARADAS_JSON: " + lat + "," + lng);
                return new GeoPoint(lat, lng);
            }
        } catch (Exception e) {
            Log.w(TAG, "extraerDestinoDeParadasJson: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lee PARADAS_JSON y extrae solo los waypoints INTERMEDIOS
     * (descarta primera = origen y última = destino).
     */
    private void leerWaypointsDelIntent() {
        waypointsRuta.clear();
        String paradasJson = getIntent().getStringExtra("PARADAS_JSON");
        if (paradasJson == null || paradasJson.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(paradasJson);
            for (int i = 1; i < arr.length() - 1; i++) {
                JSONObject p = arr.getJSONObject(i);
                double lat = p.optDouble("lat", p.optDouble("latitud", 0));
                double lng = p.optDouble("lng", p.optDouble("longitud", 0));
                if (lat != 0 && lng != 0) waypointsRuta.add(new GeoPoint(lat, lng));
            }
        } catch (Exception e) {
            Log.w(TAG, "leerWaypointsDelIntent: " + e.getMessage());
        }
    }

    private void usarGpsComorOrigen(boolean esConductor) {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            map.post(() -> dibujarRutaViaje(esConductor));
            return;
        }
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final boolean[] yaResolvio = {false};
        Runnable timeout = () -> {
            if (!yaResolvio[0]) {
                yaResolvio[0] = true;
                map.post(() -> dibujarRutaViaje(esConductor));
            }
        };
        timeoutHandler.postDelayed(timeout, 4000);
        fusedClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (!yaResolvio[0]) {
                        yaResolvio[0] = true;
                        timeoutHandler.removeCallbacks(timeout);
                        if (location != null)
                            pOrigen = new GeoPoint(location.getLatitude(), location.getLongitude());
                        map.post(() -> dibujarRutaViaje(esConductor));
                    }
                })
                .addOnFailureListener(e -> {
                    if (!yaResolvio[0]) {
                        yaResolvio[0] = true;
                        timeoutHandler.removeCallbacks(timeout);
                        map.post(() -> dibujarRutaViaje(esConductor));
                    }
                });
    }

    // =========================================================================
    //  DIBUJO DE RUTA
    // =========================================================================

    private void dibujarRutaViaje(boolean esConductor) {
        GeoPoint desde = primerNoNulo(pOrigen, pSubida);
        GeoPoint hasta = primerNoNulo(pDestino, pBajada);

        Log.d(TAG, "dibujarRutaViaje esConductor=" + esConductor
                + " desde=" + desde + " hasta=" + hasta
                + " waypoints=" + waypointsRuta.size());

        if (desde == null && hasta == null) return;
        if (desde == null || hasta == null) {
            agregarMarcadores(esConductor);
            GeoPoint punto = desde != null ? desde : hasta;
            map.getController().animateTo(punto);
            map.getController().setZoom(16.0);
            return;
        }

        final GeoPoint             fDesde     = desde;
        final GeoPoint             fHasta     = hasta;
        final ArrayList<GeoPoint>  fWaypoints = new ArrayList<>(waypointsRuta);

        new Thread(() -> {
            ArrayList<GeoPoint> rutaTotal = osrmRutaConWaypoints(fDesde, fHasta, fWaypoints);
            if (rutaTotal == null) rutaTotal = rectaEntre(fDesde, fHasta);

            final ArrayList<GeoPoint> rcFinal = rutaTotal;
            runOnUiThread(() -> {
                for (Polyline p : lineasPintadas) map.getOverlays().remove(p);
                lineasPintadas.clear();

                agregarPolilinea(rcFinal, COL_RUTA, 13f);
                agregarMarcadores(esConductor);

                ArrayList<GeoPoint> todosPuntos = new ArrayList<>(rcFinal);
                if (pSubida  != null) todosPuntos.add(pSubida);
                if (pBajada  != null) todosPuntos.add(pBajada);
                zoomBoundingBox(todosPuntos);
                map.invalidate();
                Log.d(TAG, "Ruta dibujada: " + rcFinal.size() + " pts");
            });
        }).start();
    }

    // =========================================================================
    //  OSRM
    // =========================================================================

    private ArrayList<GeoPoint> osrmRutaConWaypoints(GeoPoint desde, GeoPoint hasta,
                                                     ArrayList<GeoPoint> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) return osrmRuta(desde, hasta);

        StringBuilder coords = new StringBuilder();
        coords.append(desde.getLongitude()).append(",").append(desde.getLatitude());
        for (GeoPoint wp : waypoints)
            coords.append(";").append(wp.getLongitude()).append(",").append(wp.getLatitude());
        coords.append(";").append(hasta.getLongitude()).append(",").append(hasta.getLatitude());

        String params    = "?overview=full&geometries=geojson";
        String urlPropio = OSRM_PROPIO  + "/route/v1/driving/" + coords + params;
        String urlPubl   = OSRM_PUBLICO + "/route/v1/driving/" + coords + params;

        ArrayList<GeoPoint> pts = null;
        try { pts = osrmParsear(http(urlPropio)); }
        catch (Exception e) { Log.w(TAG, "OSRM propio waypoints: " + e.getMessage()); }
        if (pts == null || pts.size() < 2) {
            try { pts = osrmParsear(http(urlPubl)); }
            catch (Exception e) { Log.w(TAG, "OSRM publico waypoints: " + e.getMessage()); }
        }
        if (pts == null || pts.size() < 2) return osrmRuta(desde, hasta);
        return pts;
    }

    private ArrayList<GeoPoint> osrmRuta(GeoPoint desde, GeoPoint hasta) {
        String coords = desde.getLongitude() + "," + desde.getLatitude() + ";"
                + hasta.getLongitude() + "," + hasta.getLatitude()
                + "?overview=full&geometries=geojson";
        ArrayList<GeoPoint> pts = null;
        try { pts = osrmParsear(http(OSRM_PROPIO  + "/route/v1/driving/" + coords)); }
        catch (Exception e) { Log.w(TAG, "OSRM propio: " + e.getMessage()); }
        if (pts == null || pts.size() < 2) {
            try { pts = osrmParsear(http(OSRM_PUBLICO + "/route/v1/driving/" + coords)); }
            catch (Exception e) { Log.w(TAG, "OSRM publico: " + e.getMessage()); }
        }
        return (pts != null && pts.size() >= 2) ? pts : null;
    }

    private ArrayList<GeoPoint> osrmParsear(String json) {
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
                JSONArray par = coords.getJSONArray(i);
                pts.add(new GeoPoint(par.getDouble(1), par.getDouble(0)));
            }
            return pts.size() >= 2 ? pts : null;
        } catch (Exception e) {
            Log.w(TAG, "osrmParsear: " + e.getMessage());
            return null;
        }
    }

    private String http(String urlStr) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestProperty("User-Agent", "Moviflexx/1.0");
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = r.readLine()) != null) sb.append(ln);
            r.close();
            return sb.toString();
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // =========================================================================
    //  POLILÍNEAS
    // =========================================================================

    private void agregarPolilinea(List<GeoPoint> pts, int color, float ancho) {
        if (pts == null || pts.size() < 2) return;
        ArrayList<GeoPoint> copia = new ArrayList<>(pts);

        Polyline sombra = new Polyline(map);
        sombra.setPoints(copia);
        sombra.setColor(Color.argb(55, 0, 0, 0));
        sombra.setWidth(ancho + 10f);
        map.getOverlays().add(sombra);
        lineasPintadas.add(sombra);

        Polyline borde = new Polyline(map);
        borde.setPoints(copia);
        borde.setColor(Color.WHITE);
        borde.setWidth(ancho + 6f);
        map.getOverlays().add(borde);
        lineasPintadas.add(borde);

        Polyline linea = new Polyline(map);
        linea.setPoints(copia);
        linea.setColor(color);
        linea.setWidth(ancho);
        map.getOverlays().add(linea);
        lineasPintadas.add(linea);
    }

    // =========================================================================
    //  MARCADORES
    // =========================================================================

    private void agregarMarcadores(boolean esConductor) {
        if (esConductor) {
            // ── A: Origen de la ruta del conductor ───────────────────────────
            if (pOrigen != null) {
                Marker m = new Marker(map);
                m.setPosition(pOrigen);
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                m.setTitle("🏁 Inicio: " + (nomSubida.isEmpty() ? "Origen" : nomSubida));
                m.setIcon(new BitmapDrawable(getResources(), circuloMarcador(0xFF4CAF50, "A")));
                map.getOverlays().add(m);
            }
            // ── B: Destino FINAL de la ruta del conductor ─────────────────────
            if (pDestino != null) {
                Marker m = new Marker(map);
                m.setPosition(pDestino);
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                m.setTitle("🏁 Destino: " + (nomDestinoRuta.isEmpty() ? (nomBajada.isEmpty() ? "Destino" : nomBajada) : nomDestinoRuta));
                m.setIcon(new BitmapDrawable(getResources(), circuloMarcador(0xFFEF5350, "B")));
                map.getOverlays().add(m);
            }
            // ── Pines del pasajero los agrega cargarSubidaBajadaPasajero() ────
            // (P1 naranja = subida, 🚏 azul/naranja = bajada)

        } else {
            // ── PASAJERO: ve A (origen), B (destino), su subida y su bajada ───
            if (pOrigen != null) {
                Marker m = new Marker(map);
                m.setPosition(pOrigen);
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                m.setTitle("🏁 Inicio de ruta");
                m.setIcon(new BitmapDrawable(getResources(), circuloMarcador(0xFF4CAF50, "A")));
                map.getOverlays().add(m);
            }
            if (pDestino != null) {
                Marker m = new Marker(map);
                m.setPosition(pDestino);
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                m.setTitle("🏁 Final de ruta");
                m.setIcon(new BitmapDrawable(getResources(), circuloMarcador(0xFFEF5350, "B")));
                map.getOverlays().add(m);
            }
            // Pin de recogida (P1 naranja — igual que DetalleViajeActivity)
            if (pSubida != null && !pasajeroRecogido) {
                marcadorSubida = new Marker(map);
                marcadorSubida.setPosition(pSubida);
                marcadorSubida.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marcadorSubida.setTitle("🙋 Mi punto de recogida\n📍 " + nomSubida);
                marcadorSubida.setIcon(new BitmapDrawable(getResources(),
                        circuloMarcadorP1(0xFFFF6F00, "P1")));
                map.getOverlays().add(marcadorSubida);
            }
            // Pin de bajada del pasajero
            if (pBajada != null) {
                marcadorBajada = new Marker(map);
                marcadorBajada.setPosition(pBajada);
                marcadorBajada.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marcadorBajada.setTitle("🚏 Mi parada\n📍 " + nomBajada);
                marcadorBajada.setIcon(new BitmapDrawable(getResources(),
                        circuloMarcador(0xFFFF6F00, "🚏")));
                map.getOverlays().add(marcadorBajada);
            }
        }
        map.invalidate();
    }

    private void cargarSubidaBajadaPasajero() {
        if (idViaje <= 0) return;

        // Intentar primero desde el viaje completo (más confiable)
        String urlViaje = Constantes.BASE_URL + "/api/viajes/" + idViaje;
        ConexionApi.getInstance(this).getObject(urlViaje,
                viajeJson -> {
                    JSONArray usuarios = viajeJson.optJSONArray("usuarios");
                    if (usuarios != null && usuarios.length() > 0) {
                        procesarReservasParaMapa(usuarios);
                    } else {
                        // Fallback al endpoint de reservas
                        cargarSubidaBajadaDesdReservas();
                    }
                },
                err -> cargarSubidaBajadaDesdReservas()
        );
    }

    /**
     * Extrae subida y bajada del pasajero desde el array "usuarios" del viaje.
     * Misma lógica que DetalleViajeActivity.procesarReservasConductor()
     */
    private void procesarReservasParaMapa(JSONArray usuarios) {
        double latS = 0, lngS = 0, latB = 0, lngB = 0;
        String nomS = "", nomB = "", nomP = "";

        for (int i = 0; i < usuarios.length(); i++) {
            JSONObject u = usuarios.optJSONObject(i);
            if (u == null) continue;
            String est = u.optString("estado","").toUpperCase().trim();
            if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;

            // Bajada del pasajero
            double lb = u.optDouble("latBajada", u.optDouble("latParada", 0));
            double lgb = u.optDouble("lngBajada", u.optDouble("lngParada", 0));
            String nb = u.optString("nombreParadaBajada", u.optString("nombreParada",""));
            if (lb == 0) {
                JSONObject po = u.optJSONObject("parada");
                if (po != null) { lb = po.optDouble("lat",0); lgb = po.optDouble("lng",0);
                    if (nb.isEmpty()) nb = po.optString("nombre",""); }
            }

            // Subida del pasajero
            double ls = u.optDouble("latSubida", u.optDouble("latOrigen", u.optDouble("latInicio",0)));
            double lgs = u.optDouble("lngSubida", u.optDouble("lngOrigen", u.optDouble("lngInicio",0)));
            String ns = u.optString("nombreParadaSubida", u.optString("nombreParadaInicio",""));

            // Nombre del pasajero
            String np = "";
            JSONObject po = u.optJSONObject("usuario");
            if (po == null) po = u.optJSONObject("pasajero");
            if (po != null) {
                np = po.optString("nombre", po.optString("nombres",""));
                String ape = po.optString("apellidos","");
                if (!ape.isEmpty()) np = (np + " " + ape).trim();
            }
            if (np.isEmpty()) np = u.optString("nombrePasajero","Pasajero");

            if (lb != 0 || ls != 0) {
                latS = ls; lngS = lgs; nomS = ns.isEmpty() ? "Punto de recogida" : ns;
                latB = lb; lngB = lgb; nomB = nb.isEmpty() ? "Parada del pasajero" : nb;
                nomP = np;
                break;
            }
        }

        if (latS != 0 || latB != 0) {
            if (latS != 0) pSubida = new GeoPoint(latS, lngS);
            if (latB != 0) pBajada = new GeoPoint(latB, lngB);
            final double fLS=latS,fLGS=lngS,fLB=latB,fLGB=lngB;
            final String fNS=nomS,fNB=nomB,fNP=nomP;
            runOnUiThread(() -> actualizarPinesPasajero(fLS,fLGS,fLB,fLGB,fNS,fNB,fNP));
        } else {
            // Si el viaje no tiene subida/bajada en usuarios, probar endpoint reservas
            cargarSubidaBajadaDesdReservas();
        }
    }

    /** Fallback: endpoint /api/viajes/{id}/reservas */
    private void cargarSubidaBajadaDesdReservas() {
        String url = Constantes.BASE_URL + "/api/viajes/" + idViaje + "/reservas";
        ConexionApi.getInstance(this).getArray(url,
                reservas -> {
                    if (reservas == null || reservas.length() == 0) return;
                    for (int i = 0; i < reservas.length(); i++) {
                        JSONObject r = reservas.optJSONObject(i);
                        if (r == null) continue;
                        String est = r.optString("estado", "").toUpperCase();
                        if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;

                        double latS = 0, lngS = 0, latB = 0, lngB = 0;
                        String nomS = "", nomB = "";

                        JSONObject subObj = r.optJSONObject("puntoSubida");
                        if (subObj == null) subObj = r.optJSONObject("subida");
                        if (subObj != null) {
                            latS = subObj.optDouble("lat", subObj.optDouble("latitud", 0));
                            lngS = subObj.optDouble("lng", subObj.optDouble("longitud", 0));
                            nomS = subObj.optString("nombre", "");
                        }
                        if (latS == 0) {
                            latS = r.optDouble("latSubida", r.optDouble("latOrigen",
                                    r.optDouble("latInicio", 0)));
                            lngS = r.optDouble("lngSubida", r.optDouble("lngOrigen",
                                    r.optDouble("lngInicio", 0)));
                            nomS = r.optString("nombreParadaSubida",
                                    r.optString("nombreParadaInicio", ""));
                        }

                        JSONObject bajObj = r.optJSONObject("puntoBajada");
                        if (bajObj == null) bajObj = r.optJSONObject("bajada");
                        if (bajObj != null) {
                            latB = bajObj.optDouble("lat", bajObj.optDouble("latitud", 0));
                            lngB = bajObj.optDouble("lng", bajObj.optDouble("longitud", 0));
                            nomB = bajObj.optString("nombre", "");
                        }
                        if (latB == 0) {
                            latB = r.optDouble("latBajada", r.optDouble("latParada", 0));
                            lngB = r.optDouble("lngBajada", r.optDouble("lngParada", 0));
                            nomB = r.optString("nombreParadaBajada",
                                    r.optString("nombreParada", ""));
                        }

                        String nomPasajero = "";
                        JSONObject po = r.optJSONObject("pasajero");
                        if (po == null) po = r.optJSONObject("usuario");
                        if (po != null) {
                            nomPasajero = po.optString("nombre",
                                    po.optString("nombres", po.optString("name", "")));
                            String ape = po.optString("apellidos", "");
                            if (!ape.isEmpty()) nomPasajero = (nomPasajero + " " + ape).trim();
                        }
                        if (nomPasajero.isEmpty())
                            nomPasajero = r.optString("nombrePasajero", "Pasajero");

                        if (latS != 0 || latB != 0) {
                            if (latS != 0) pSubida = new GeoPoint(latS, lngS);
                            if (latB != 0) pBajada = new GeoPoint(latB, lngB);
                            final double fLS=latS,fLGS=lngS,fLB=latB,fLGB=lngB;
                            final String fNS=nomS.isEmpty()?"Punto de recogida":nomS;
                            final String fNB=nomB.isEmpty()?"Parada del pasajero":nomB;
                            final String fNP=nomPasajero;
                            runOnUiThread(() ->
                                    actualizarPinesPasajero(fLS,fLGS,fLB,fLGB,fNS,fNB,fNP));
                            break;
                        }
                    }
                },
                err -> Log.w(TAG, "cargarSubidaBajada reservas: " + err));
    }

    // =========================================================================
    //  CARGA DE PARADAS DEL PASAJERO (vista pasajero)
    // =========================================================================

    /**
     * Cuando el pasajero abre el mapa y no recibió LAT_SUBIDA/LAT_BAJADA por Intent,
     * busca su reserva activa en el viaje y extrae los puntos de subida y bajada.
     * Luego pinta los pins y redibujar la ruta incluyendo esos puntos.
     */
    private void cargarParadasPasajeroDesdeApi() {
        int idUsuario = session.getIdUsuario();
        Log.d(TAG, "cargarParadasPasajeroDesdeApi → idViaje=" + idViaje + " idUsuario=" + idUsuario);

        // Primero intentamos /api/viajes/{id} — viene el array "usuarios" con la info de reserva
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viajeObj -> {
                    // Extraer ruta para pOrigen/pDestino si aún faltan
                    if (pOrigen == null || pDestino == null) {
                        JSONObject rutaObj = viajeObj.optJSONObject("ruta");
                        if (rutaObj != null) {
                            JSONArray paradas = rutaObj.optJSONArray("paradas");
                            resolverOrigenDestinoDeParadas(paradas);
                        }
                    }

                    // Buscar la reserva del pasajero actual en "usuarios"
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    if (usuarios != null) {
                        for (int i = 0; i < usuarios.length(); i++) {
                            JSONObject u = usuarios.optJSONObject(i);
                            if (u == null) continue;
                            String est = u.optString("estado","").toUpperCase().trim();
                            if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;

                            // Verificar que sea la reserva del usuario actual
                            int idU = -1;
                            JSONObject usuObj = u.optJSONObject("usuario");
                            if (usuObj != null) {
                                for (String k : new String[]{"idUsuarios","id","idUsuario"}) {
                                    int id = usuObj.optInt(k,-1); if (id>0){idU=id;break;}
                                }
                            }
                            if (idU <= 0) idU = u.optInt("idUsuarios", u.optInt("idUsuario", -1));
                            if (idU != idUsuario) continue;

                            // Extraer coordenadas de subida
                            double latS=0, lngS=0, latB=0, lngB=0;
                            String nomS="", nomB="";

                            JSONObject subObj = u.optJSONObject("puntoSubida");
                            if (subObj == null) subObj = u.optJSONObject("subida");
                            if (subObj != null) {
                                latS = subObj.optDouble("lat", subObj.optDouble("latitud",0));
                                lngS = subObj.optDouble("lng", subObj.optDouble("longitud",0));
                                nomS = subObj.optString("nombre","");
                            }
                            if (latS == 0) {
                                latS = u.optDouble("latSubida", u.optDouble("latOrigen", u.optDouble("latInicio",0)));
                                lngS = u.optDouble("lngSubida", u.optDouble("lngOrigen", u.optDouble("lngInicio",0)));
                                nomS = u.optString("nombreParadaSubida", u.optString("nombreParadaInicio",""));
                            }

                            // Extraer coordenadas de bajada
                            JSONObject bajObj = u.optJSONObject("puntoBajada");
                            if (bajObj == null) bajObj = u.optJSONObject("bajada");
                            if (bajObj != null) {
                                latB = bajObj.optDouble("lat", bajObj.optDouble("latitud",0));
                                lngB = bajObj.optDouble("lng", bajObj.optDouble("longitud",0));
                                nomB = bajObj.optString("nombre","");
                            }
                            if (latB == 0) {
                                latB = u.optDouble("latBajada", u.optDouble("latParada",0));
                                lngB = u.optDouble("lngBajada", u.optDouble("lngParada",0));
                                nomB = u.optString("nombreParadaBajada", u.optString("nombreParada",""));
                            }

                            final double fLS=latS, fLGS=lngS, fLB=latB, fLGB=lngB;
                            final String fNS = nomS.isEmpty() ? "Tu punto de recogida" : nomS;
                            final String fNB = nomB.isEmpty() ? "Tu parada de bajada"  : nomB;

                            if (latS != 0 || latB != 0) {
                                if (latS != 0) pSubida = new GeoPoint(latS, lngS);
                                if (latB != 0) pBajada = new GeoPoint(latB, lngB);
                                runOnUiThread(() -> {
                                    pintarPinesPasajeroEnMapa(fLS, fLGS, fLB, fLGB, fNS, fNB);
                                    // Re-dibujar ruta con los nuevos puntos
                                    if (pOrigen != null && pDestino != null)
                                        map.post(() -> dibujarRutaViaje(false));
                                });
                                Log.d(TAG, "Paradas pasajero encontradas en usuarios[]: subida="+latS+","+lngS+" bajada="+latB+","+lngB);
                                return;
                            }
                        }
                    }

                    // Fallback: buscar en /api/reservas/mis-reservas o /api/reservas?idViaje=
                    cargarParadasPasajeroDesdeReservas();
                },
                err -> cargarParadasPasajeroDesdeReservas()
        );
    }

    /**
     * Fallback: intenta obtener la reserva activa del pasajero desde el endpoint de reservas.
     */
    private void cargarParadasPasajeroDesdeReservas() {
        String[] urls = {
                Constantes.MIS_RESERVAS,
                Constantes.BASE_URL + "/api/reservas/viaje/" + idViaje,
                Constantes.BASE_URL + "/api/reservas?idViaje=" + idViaje,
        };
        intentarReservasPasajero(urls, 0);
    }

    private void intentarReservasPasajero(String[] urls, int index) {
        if (index >= urls.length) {
            Log.w(TAG, "No se encontraron paradas del pasajero en ningún endpoint");
            // Dibujar ruta sin paradas personales al menos
            runOnUiThread(() -> {
                if (pOrigen != null && pDestino != null)
                    map.post(() -> dibujarRutaViaje(false));
            });
            return;
        }
        ConexionApi.getInstance(this).getArrayNoCache(urls[index],
                reservas -> {
                    if (reservas == null || reservas.length() == 0) {
                        intentarReservasPasajero(urls, index + 1); return;
                    }
                    for (int i = 0; i < reservas.length(); i++) {
                        JSONObject r = reservas.optJSONObject(i);
                        if (r == null) continue;
                        // Filtrar por idViaje si aplica
                        int idV = r.optInt("idViajes", r.optInt("idViaje", r.optInt("viajeId", -1)));
                        if (idV > 0 && idV != idViaje) continue;
                        String est = r.optString("estado","").toUpperCase().trim();
                        if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;

                        double latS=0, lngS=0, latB=0, lngB=0;
                        String nomS="", nomB="";

                        JSONObject subObj = r.optJSONObject("puntoSubida");
                        if (subObj == null) subObj = r.optJSONObject("subida");
                        if (subObj != null) {
                            latS = subObj.optDouble("lat", subObj.optDouble("latitud",0));
                            lngS = subObj.optDouble("lng", subObj.optDouble("longitud",0));
                            nomS = subObj.optString("nombre","");
                        }
                        if (latS == 0) {
                            latS = r.optDouble("latSubida", r.optDouble("latOrigen",0));
                            lngS = r.optDouble("lngSubida", r.optDouble("lngOrigen",0));
                            nomS = r.optString("nombreParadaSubida","");
                        }
                        JSONObject bajObj = r.optJSONObject("puntoBajada");
                        if (bajObj == null) bajObj = r.optJSONObject("bajada");
                        if (bajObj != null) {
                            latB = bajObj.optDouble("lat", bajObj.optDouble("latitud",0));
                            lngB = bajObj.optDouble("lng", bajObj.optDouble("longitud",0));
                            nomB = bajObj.optString("nombre","");
                        }
                        if (latB == 0) {
                            latB = r.optDouble("latBajada", r.optDouble("latParada",0));
                            lngB = r.optDouble("lngBajada", r.optDouble("lngParada",0));
                            nomB = r.optString("nombreParadaBajada","");
                        }

                        if (latS != 0 || latB != 0) {
                            if (latS != 0) pSubida = new GeoPoint(latS, lngS);
                            if (latB != 0) pBajada = new GeoPoint(latB, lngB);
                            final double fLS=latS,fLGS=lngS,fLB=latB,fLGB=lngB;
                            final String fNS=nomS.isEmpty()?"Tu punto de recogida":nomS;
                            final String fNB=nomB.isEmpty()?"Tu parada de bajada":nomB;
                            runOnUiThread(() -> {
                                pintarPinesPasajeroEnMapa(fLS,fLGS,fLB,fLGB,fNS,fNB);
                                if (pOrigen != null && pDestino != null)
                                    map.post(() -> dibujarRutaViaje(false));
                            });
                            return;
                        }
                    }
                    intentarReservasPasajero(urls, index + 1);
                },
                err -> {
                    // Si falla como array, intentar como objeto
                    ConexionApi.getInstance(this).getObjectNoCache(urls[index],
                            obj -> {
                                // Si el viaje devuelve el objeto con paradas, redibujar
                                runOnUiThread(() -> {
                                    if (pOrigen != null && pDestino != null)
                                        map.post(() -> dibujarRutaViaje(false));
                                });
                                intentarReservasPasajero(urls, index + 1);
                            },
                            err2 -> intentarReservasPasajero(urls, index + 1));
                }
        );
    }

    /**
     * Extrae origen/destino de la ruta de un viaje desde el array de paradas.
     * Usado cuando el pasajero carga el viaje y pOrigen/pDestino son null.
     */
    private void resolverOrigenDestinoDeParadas(JSONArray paradas) {
        if (paradas == null || paradas.length() < 2) return;
        JSONObject mejorO = null, mejorD = null;
        int minOrden = Integer.MAX_VALUE, maxOrden = -1;
        for (int i = 0; i < paradas.length(); i++) {
            JSONObject p = paradas.optJSONObject(i);
            if (p == null) continue;
            double la = p.optDouble("lat",0); if (la == 0) continue;
            String tipo = p.optString("tipo","").toUpperCase().trim();
            if ("BAJADA".equals(tipo)) continue;
            int orden = p.optInt("orden", i);
            if (orden < minOrden) { minOrden=orden; mejorO=p; }
            if (orden > maxOrden) { maxOrden=orden; mejorD=p; }
        }
        if (pOrigen == null && mejorO != null) {
            double la=mejorO.optDouble("lat",0), lo=mejorO.optDouble("lng",0);
            if (la!=0) { pOrigen = new GeoPoint(la,lo); if(nomSubida.isEmpty()) nomSubida=mejorO.optString("nombre",""); }
        }
        if (pDestino == null && mejorD != null && mejorD != mejorO) {
            double la=mejorD.optDouble("lat",0), lo=mejorD.optDouble("lng",0);
            if (la!=0) { pDestino = new GeoPoint(la,lo); if(nomDestinoRuta.isEmpty()) nomDestinoRuta=mejorD.optString("nombre",""); }
        }
    }

    /**
     * Pinta los pins de subida/bajada del PASAJERO en su propia vista del mapa.
     * Pin verde "🙋 Subida" y pin naranja "🚏 Bajada".
     */
    private void pintarPinesPasajeroEnMapa(double latS, double lngS,
                                           double latB, double lngB,
                                           String nomS, String nomB) {
        if (latS != 0) {
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(latS, lngS));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle("🙋 Tu punto de recogida\n📍 " + nomS);
            m.setIcon(new BitmapDrawable(getResources(), circuloMarcadorP1(0xFF2E7D32, "🙋")));
            map.getOverlays().add(m);
        }
        if (latB != 0) {
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(latB, lngB));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle("🚏 Tu parada de bajada\n📍 " + nomB);
            m.setIcon(new BitmapDrawable(getResources(), circuloMarcadorP1(0xFFFF6F00, "🚏")));
            map.getOverlays().add(m);
        }
        map.invalidate();

        // Zoom para mostrar ambos puntos
        ArrayList<GeoPoint> pts = new ArrayList<>();
        if (pOrigen  != null) pts.add(pOrigen);
        if (pDestino != null) pts.add(pDestino);
        if (latS != 0) pts.add(new GeoPoint(latS, lngS));
        if (latB != 0) pts.add(new GeoPoint(latB, lngB));
        if (pts.size() > 1) zoomBoundingBox(pts);
    }

    private void actualizarPinesPasajero(double latS, double lngS,
                                         double latB, double lngB,
                                         String nomS, String nomB, String nomP) {
        // ── Pin de SUBIDA (P1 naranja) — igual que en DetalleViajeActivity ───
        if (latS != 0 && !pasajeroRecogido) {
            if (marcadorSubida != null) map.getOverlays().remove(marcadorSubida);
            marcadorSubida = new Marker(map);
            marcadorSubida.setPosition(new GeoPoint(latS, lngS));
            marcadorSubida.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marcadorSubida.setTitle("🙋 Recoger a " + nomP + "\n📍 " + nomS);
            marcadorSubida.setIcon(new BitmapDrawable(getResources(),
                    circuloMarcadorP1(0xFFFF6F00, "P1")));
            map.getOverlays().add(marcadorSubida);
        } else if (pasajeroRecogido && marcadorSubida != null) {
            map.getOverlays().remove(marcadorSubida);
            marcadorSubida = null;
        }

        // ── Pin de BAJADA (🚏) ────────────────────────────────────────────────
        // Antes de recoger: azul pequeño (referencia)
        // Tras recoger: naranja grande (destino activo)
        if (latB != 0) {
            if (marcadorBajada != null) map.getOverlays().remove(marcadorBajada);
            marcadorBajada = new Marker(map);
            marcadorBajada.setPosition(new GeoPoint(latB, lngB));
            marcadorBajada.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marcadorBajada.setTitle("🚏 Bajar a " + nomP + "\n📍 " + nomB);
            marcadorBajada.setIcon(new BitmapDrawable(getResources(), pasajeroRecogido
                    ? circuloMarcadorGrande(0xFFFF6F00, "🚏")
                    : circuloMarcador(0xFF1565C0, "🚏")));
            map.getOverlays().add(marcadorBajada);
        }

        if (marcadorGpsPropio != null) {
            map.getOverlays().remove(marcadorGpsPropio);
            map.getOverlays().add(marcadorGpsPropio);
        }
        map.invalidate();

        ArrayList<GeoPoint> pts = new ArrayList<>();
        if (pOrigen  != null) pts.add(pOrigen);
        if (latS != 0)        pts.add(new GeoPoint(latS, lngS));
        if (latB != 0)        pts.add(new GeoPoint(latB, lngB));
        if (pDestino != null) pts.add(pDestino);
        if (pts.size() > 1)   zoomBoundingBox(pts);
    }

    private void verificarRecogidaPasajero(GeoPoint posConductor) {
        if (pasajeroRecogido || marcadorSubida == null || pSubida == null) return;
        double distMetros = calcularDistanciaMetros(posConductor, pSubida);
        if (distMetros <= 50) {
            pasajeroRecogido = true;
            map.getOverlays().remove(marcadorSubida);
            marcadorSubida = null;
            map.invalidate();
            Toast.makeText(this, "✅ Pasajero recogido — llévalo a su parada",
                    Toast.LENGTH_LONG).show();
            // Pin de bajada pasa a naranja grande (destino activo)
            if (pBajada != null && marcadorBajada != null) {
                map.getOverlays().remove(marcadorBajada);
                marcadorBajada = new Marker(map);
                marcadorBajada.setPosition(pBajada);
                marcadorBajada.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marcadorBajada.setTitle("🚏 Llevar al pasajero aquí");
                marcadorBajada.setIcon(new BitmapDrawable(getResources(),
                        circuloMarcadorP1(0xFFFF6F00, "🚏")));
                map.getOverlays().add(marcadorBajada);
                map.invalidate();
            }
            if (pBajada != null && miUltimaPosicion != null) {
                // Actualizar la línea naranja hacia la bajada SIN borrar la ruta completa
                actualizarLineaNaranja(miUltimaPosicion);

            }
            Log.d(TAG, "Pasajero recogido a " + distMetros + " m");
        }
    }

    // =========================================================================
    //  FINALIZAR VIAJE — auto GPS + botón manual
    // =========================================================================

    /** Llamado en cada tick GPS. Si el conductor está a ≤50 m del destino, confirma. */
    private void verificarLlegadaAlDestino(GeoPoint pos) {
        if (viajeYaFinalizado || !desdeViajeActivo || idViaje <= 0 || pDestino == null) return;
        if (!session.isConductor()) return; // solo el conductor dispara esto

        double distancia = calcularDistanciaMetros(pos, pDestino);
        Log.d(TAG, "Distancia al destino: " + (int)distancia + " m");

        if (distancia <= 50) {
            viajeYaFinalizado = true;
            Log.d(TAG, "✅ Conductor llegó al destino → finalizando automáticamente");
            runOnUiThread(() -> {
                Toast.makeText(this, "🏁 Llegaste al destino — finalizando viaje...",
                        Toast.LENGTH_SHORT).show();
                ejecutarFinalizarViaje();
            });
        }
    }

    private void mostrarDialogoLlegada() {}
    /**   new AlertDialog.Builder(this)
     .setTitle("🏁 ¡Llegaste al destino!")
     .setMessage("Estás a menos de 50 m del punto B. ¿Deseas finalizar el viaje ahora?")
     .setCancelable(false)
     .setPositiveButton("✅ Finalizar viaje", (d, w) -> ejecutarFinalizarViaje())
     .setNegativeButton("Continuar", (d, w) -> viajeYaFinalizado = false)
     .show();
     }*/

    /** Botón manual en el banner — pregunta antes de finalizar. */
    private void confirmarFinalizarManual() {
        if (viajeYaFinalizado) {
            Toast.makeText(this, "El viaje ya fue finalizado", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Finalizar viaje")
                .setMessage("¿Deseas finalizar el viaje? Se notificará a los pasajeros.")
                .setPositiveButton("Finalizar", (d, w) -> {
                    viajeYaFinalizado = true;
                    ejecutarFinalizarViaje();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Paso 1 → pasajeros-bajaron
     * Paso 2 → finalizar
     * Paso 3 → flujo de calificaciones → HomeConductor
     */
    private void ejecutarFinalizarViaje() {
        if (fusedClient != null && locationCallback != null)
            fusedClient.removeLocationUpdates(locationCallback);
        if (rPoll != null) hPoll.removeCallbacks(rPoll);

        Toast.makeText(this, "⏳ Finalizando viaje...", Toast.LENGTH_SHORT).show();

        String urlBajaron = Constantes.BASE_URL + "/api/viajes/" + idViaje + "/pasajeros-bajaron";
        ConexionApi.getInstance(this).post(urlBajaron, null,
                r1 -> paso2FinalizarMapa(),
                e1 -> paso2FinalizarMapa());
    }

    private void paso2FinalizarMapa() {
        ConexionApi.getInstance(this).post(Constantes.viajeFinalizar((long) idViaje), null,
                r2 -> runOnUiThread(() -> {
                    Toast.makeText(this, "✅ Viaje finalizado", Toast.LENGTH_LONG).show();
                    // Notificar al pasajero vía Firebase que el viaje terminó
                    notificarFinViajeAFirebase();
                    new Handler(Looper.getMainLooper())
                            .postDelayed(this::buscarPasajerosYCalificarMapa, 1200);
                }),
                e2 -> runOnUiThread(() -> {
                    Toast.makeText(this, "❌ Error al finalizar. Inténtalo de nuevo.",
                            Toast.LENGTH_LONG).show();
                    viajeYaFinalizado = false;
                }));
    }
    private void notificarFinViajeAFirebase() {
        try {
            DatabaseReference refViaje = FirebaseDatabase.getInstance()
                    .getReference("viajes_estado")
                    .child("viaje_" + idViaje);
            refViaje.child("estado").setValue("FINALIZADO");
            refViaje.child("timestamp").setValue(System.currentTimeMillis());
            Log.d(TAG, "Firebase: viaje_" + idViaje + " marcado como FINALIZADO");
        } catch (Exception e) {
            Log.w(TAG, "notificarFinViajeAFirebase: " + e.getMessage());
        }
    }

    /** Replica la lógica de ViajesAdapter.buscarPasajerosYCalificar adaptada para Activity. */
    private void buscarPasajerosYCalificarMapa() {
        int idConductor = session.getIdUsuario();
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viajeObj -> {
                    java.util.ArrayList<Integer> ids     = new java.util.ArrayList<>();
                    java.util.ArrayList<String>  nombres = new java.util.ArrayList<>();

                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    if (usuarios != null) {
                        for (int i = 0; i < usuarios.length(); i++) {
                            JSONObject u = usuarios.optJSONObject(i);
                            if (u == null) continue;
                            String est = u.optString("estado", "").toUpperCase().trim();
                            if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;

                            int    idP  = -1;
                            String nomP = "";
                            JSONObject usuObj = u.optJSONObject("usuario");
                            if (usuObj != null) {
                                for (String k : new String[]{"idUsuarios","id","idUsuario"}) {
                                    int id = usuObj.optInt(k, -1); if (id > 0) { idP = id; break; }
                                }
                                for (String k : new String[]{"nombre","nombreCompleto","name","nombres"}) {
                                    String n = usuObj.optString(k, "");
                                    if (!n.isEmpty() && !n.equals("null")) { nomP = n; break; }
                                }
                                if (nomP.isEmpty()) {
                                    String n = usuObj.optString("nombres", "");
                                    String a = usuObj.optString("apellidos", "");
                                    if (!n.isEmpty() || !a.isEmpty()) nomP = (n + " " + a).trim();
                                }
                            }
                            if (idP <= 0) for (String k : new String[]{"idUsuarios","idUsuario","idPasajero"}) {
                                int id = u.optInt(k, -1); if (id > 0) { idP = id; break; }
                            }
                            if (idP > 0 && idP != idConductor) {
                                ids.add(idP);
                                nombres.add(nomP.isEmpty() ? "Pasajero" : nomP);
                            }
                        }
                    }

                    runOnUiThread(() -> {
                        if (!ids.isEmpty()) {
                            calificarEncadenadoMapa(ids, nombres, idConductor, 0);
                        } else {
                            irAHomeConductor();
                        }
                    });
                },
                err -> runOnUiThread(this::irAHomeConductor));
    }

    private void calificarEncadenadoMapa(java.util.ArrayList<Integer> ids,
                                         java.util.ArrayList<String>  nombres,
                                         int idConductor, int indice) {
        if (indice >= ids.size()) { irAHomeConductor(); return; }
        int    idP  = ids.get(indice);
        String nomP = nombres.get(indice);
        int    sig  = indice + 1;

        new com.arlys.moviflexx.model.Manager.CalificacionesManager(this)
                .verificarCalificacion(idViaje, idConductor, idP,
                        new com.arlys.moviflexx.model.Manager.CalificacionesManager.OnVerificacionListener() {
                            @Override public void onDebeCalificar() {
                                com.arlys.moviflexx.controller.CalificacionController
                                        .mostrarBottomSheetCalificar(
                                                Mapa.this, idViaje, idP, nomP, idConductor, true,
                                                (pun, com2) -> new Handler(Looper.getMainLooper())
                                                        .postDelayed(() -> calificarEncadenadoMapa(
                                                                ids, nombres, idConductor, sig), 600));
                            }
                            @Override public void onYaCalifico(int p, String e) {
                                calificarEncadenadoMapa(ids, nombres, idConductor, sig);
                            }
                        });
    }

    private void irAHomeConductor() {
        Intent i = new Intent(this, HomeConductor.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    private double calcularDistanciaMetros(GeoPoint a, GeoPoint b) {
        double R    = 6371000;
        double dLat = Math.toRadians(b.getLatitude()  - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double c = sinLat * sinLat
                + Math.cos(Math.toRadians(a.getLatitude()))
                * Math.cos(Math.toRadians(b.getLatitude()))
                * sinLon * sinLon;
        return R * 2 * Math.atan2(Math.sqrt(c), Math.sqrt(1 - c));
    }

    // =========================================================================
    //  BITMAPS
    // =========================================================================

    private Bitmap circuloMarcador(int colorFondo, String texto) {
        int sz = 96;
        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cv  = new Canvas(bmp);

        Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG);
        ps.setColor(Color.argb(80, 0, 0, 0));
        cv.drawCircle(sz / 2f + 3, sz / 2f + 5, sz / 2f - 8, ps);

        Paint pf = new Paint(Paint.ANTI_ALIAS_FLAG);
        pf.setColor(colorFondo);
        cv.drawCircle(sz / 2f, sz / 2f - 3, sz / 2f - 9, pf);

        Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG);
        pb.setColor(Color.WHITE);
        pb.setStyle(Paint.Style.STROKE);
        pb.setStrokeWidth(5f);
        cv.drawCircle(sz / 2f, sz / 2f - 3, sz / 2f - 9, pb);

        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pt.setColor(Color.WHITE);
        pt.setTextSize(texto.length() > 1 ? 26f : 38f);
        pt.setTypeface(Typeface.DEFAULT_BOLD);
        pt.setTextAlign(Paint.Align.CENTER);
        cv.drawText(texto, sz / 2f, sz / 2f + 11, pt);

        return bmp;
    }

    private Bitmap circuloMarcadorGrande(int colorFondo, String texto) {
        int sz = 120;
        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cv  = new Canvas(bmp);

        Paint ph = new Paint(Paint.ANTI_ALIAS_FLAG);
        ph.setColor(Color.argb(120, 255, 255, 255));
        cv.drawCircle(sz / 2f, sz / 2f - 3, sz / 2f - 4, ph);

        Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG);
        ps.setColor(Color.argb(80, 0, 0, 0));
        cv.drawCircle(sz / 2f + 3, sz / 2f + 6, sz / 2f - 14, ps);

        Paint pf = new Paint(Paint.ANTI_ALIAS_FLAG);
        pf.setColor(colorFondo);
        cv.drawCircle(sz / 2f, sz / 2f - 3, sz / 2f - 14, pf);

        Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG);
        pb.setColor(Color.WHITE);
        pb.setStyle(Paint.Style.STROKE);
        pb.setStrokeWidth(6f);
        cv.drawCircle(sz / 2f, sz / 2f - 3, sz / 2f - 14, pb);

        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pt.setColor(Color.WHITE);
        pt.setTextSize(texto.length() > 1 ? 32f : 46f);
        pt.setTypeface(Typeface.DEFAULT_BOLD);
        pt.setTextAlign(Paint.Align.CENTER);
        cv.drawText(texto, sz / 2f, sz / 2f + 13, pt);

        return bmp;
    }

    /**
     * Pin estilo P1/P2 — círculo naranja con texto blanco en negrita,
     * igual al que usa DetalleViajeActivity para las paradas de pasajero.
     * Incluye una pequeña cola triangular abajo para indicar el punto exacto.
     */
    private Bitmap circuloMarcadorP1(int colorFondo, String texto) {
        int sz  = 108;
        int r   = 42;         // radio del círculo
        int cx  = sz / 2;
        int cy  = r + 6;      // centro del círculo (deja espacio para la cola)
        int colaH = 18;       // altura de la cola triangular

        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cv  = new Canvas(bmp);

        // Sombra
        Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG);
        ps.setColor(Color.argb(70, 0, 0, 0));
        cv.drawCircle(cx + 3, cy + 5, r - 2, ps);

        // Relleno del círculo
        Paint pf = new Paint(Paint.ANTI_ALIAS_FLAG);
        pf.setColor(colorFondo);
        cv.drawCircle(cx, cy, r, pf);

        // Borde blanco
        Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG);
        pb.setColor(Color.WHITE);
        pb.setStyle(Paint.Style.STROKE);
        pb.setStrokeWidth(5f);
        cv.drawCircle(cx, cy, r, pb);

        // Cola triangular (apunta hacia abajo)
        Paint pc = new Paint(Paint.ANTI_ALIAS_FLAG);
        pc.setColor(colorFondo);
        Path cola = new Path();
        cola.moveTo(cx - 10, cy + r - 4);
        cola.lineTo(cx + 10, cy + r - 4);
        cola.lineTo(cx, cy + r + colaH);
        cola.close();
        cv.drawPath(cola, pc);

        // Borde de la cola (solo los lados, no la base)
        Paint pbc = new Paint(Paint.ANTI_ALIAS_FLAG);
        pbc.setColor(Color.WHITE);
        pbc.setStyle(Paint.Style.STROKE);
        pbc.setStrokeWidth(4f);
        Path colaBorde = new Path();
        colaBorde.moveTo(cx - 10, cy + r - 2);
        colaBorde.lineTo(cx, cy + r + colaH);
        colaBorde.lineTo(cx + 10, cy + r - 2);
        cv.drawPath(colaBorde, pbc);

        // Texto (P1, P2, etc.)
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pt.setColor(Color.WHITE);
        pt.setTextSize(texto.length() > 2 ? 22f : 28f);
        pt.setTypeface(Typeface.DEFAULT_BOLD);
        pt.setTextAlign(Paint.Align.CENTER);
        // Centrar verticalmente en el círculo
        float textY = cy - (pt.descent() + pt.ascent()) / 2;
        cv.drawText(texto, cx, textY, pt);

        return bmp;
    }

    // =========================================================================
    //  ZOOM
    // =========================================================================

    private void zoomBoundingBox(List<GeoPoint> puntos) {
        if (puntos == null || puntos.isEmpty()) return;
        if (puntos.size() == 1) {
            map.getController().animateTo(puntos.get(0));
            map.getController().setZoom(16.0);
            return;
        }
        double mnLat = Double.MAX_VALUE, mxLat = -Double.MAX_VALUE;
        double mnLng = Double.MAX_VALUE, mxLng = -Double.MAX_VALUE;
        for (GeoPoint p : puntos) {
            mnLat = Math.min(mnLat, p.getLatitude());
            mxLat = Math.max(mxLat, p.getLatitude());
            mnLng = Math.min(mnLng, p.getLongitude());
            mxLng = Math.max(mxLng, p.getLongitude());
        }
        double pLat = Math.max((mxLat - mnLat) * 0.25, 0.006);
        double pLng = Math.max((mxLng - mnLng) * 0.25, 0.006);
        BoundingBox bb = new BoundingBox(
                mxLat + pLat, mxLng + pLng,
                mnLat - pLat, mnLng - pLng);
        map.post(() -> {
            try { map.zoomToBoundingBox(bb, true, 100); }
            catch (Exception ignored) {}
        });
    }

    // =========================================================================
    //  POLLING GPS CONDUCTOR
    // =========================================================================

    private void arrancarPollingConductor() {
        rPoll = new Runnable() {
            @Override public void run() {
                fetchUbicacionConductor();
                hPoll.postDelayed(this, 2000);
            }
        };
        hPoll.postDelayed(rPoll, 800);
    }

    private void fetchUbicacionConductor() {
        String url = Constantes.BASE_URL + "/api/viajes/" + idViaje
                + "/ubicacion-conductor?t=" + System.currentTimeMillis();
        ConexionApi.getInstance(this).getObjectNoCache(url,
                resp -> {
                    double lat = resp.optDouble("lat",
                            resp.optDouble("latitud",  Double.NaN));
                    double lng = resp.optDouble("lng",
                            resp.optDouble("longitud", Double.NaN));
                    if (!Double.isNaN(lat) && lat != 0 && !Double.isNaN(lng) && lng != 0) {
                        GeoPoint nuevaPos = new GeoPoint(lat, lng);
                        runOnUiThread(() -> {
                            animarMarcadorConductor(nuevaPos);
                            actualizarLineaNaranja(nuevaPos);
                        });
                        calcularEtaConductor(nuevaPos);
                    }
                },
                err -> Log.w(TAG, "GPS conductor no disponible"));
    }

    private void animarMarcadorConductor(GeoPoint nuevaPos) {
        if (posAnteriorConductor != null) {
            double dLat = nuevaPos.getLatitude()  - posAnteriorConductor.getLatitude();
            double dLng = nuevaPos.getLongitude() - posAnteriorConductor.getLongitude();
            if (Math.abs(dLat) > 0.000018 || Math.abs(dLng) > 0.000018)
                rumboConductor = calcularRumbo(posAnteriorConductor, nuevaPos);
        }
        posAnteriorConductor = nuevaPos;

        if (marcadorConductorRT == null) {
            marcadorConductorRT = new Marker(map);
            marcadorConductorRT.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marcadorConductorRT.setTitle("🚗 " + nomConductor);
            marcadorConductorRT.setIcon(new BitmapDrawable(getResources(),
                    crearIconoCarro(COL_CONDUCT, rumboConductor)));
            marcadorConductorRT.setPosition(nuevaPos);
            map.getOverlays().add(marcadorConductorRT);
            map.invalidate();
            return;
        }

        GeoPoint ini = marcadorConductorRT.getPosition();
        if (ini == null) {
            marcadorConductorRT.setPosition(nuevaPos);
            marcadorConductorRT.setIcon(new BitmapDrawable(getResources(),
                    crearIconoCarro(COL_CONDUCT, rumboConductor)));
            map.invalidate();
            return;
        }

        if (Math.abs(nuevaPos.getLatitude()  - ini.getLatitude())  < 0.000018
                && Math.abs(nuevaPos.getLongitude() - ini.getLongitude()) < 0.000018) {
            marcadorConductorRT.setPosition(nuevaPos);
            marcadorConductorRT.setIcon(new BitmapDrawable(getResources(),
                    crearIconoCarro(COL_CONDUCT, rumboConductor)));
            map.invalidate();
            return;
        }

        final float rumboFinal = rumboConductor;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1800);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            marcadorConductorRT.setPosition(new GeoPoint(
                    ini.getLatitude()  + t * (nuevaPos.getLatitude()  - ini.getLatitude()),
                    ini.getLongitude() + t * (nuevaPos.getLongitude() - ini.getLongitude())));
            if (Math.round(t * 100) % 10 == 0)
                marcadorConductorRT.setIcon(new BitmapDrawable(getResources(),
                        crearIconoCarro(COL_CONDUCT, rumboFinal)));
            map.invalidate();
        });
        anim.start();
    }

    private void actualizarLineaNaranja(GeoPoint posConductor) {
        GeoPoint destino = (!pasajeroRecogido && pSubida != null) ? pSubida : pBajada;
        if (destino == null) destino = pOrigen;
        if (destino == null) return;

        final GeoPoint fPc  = posConductor;
        final GeoPoint fDst = destino;

        new Thread(() -> {
            ArrayList<GeoPoint> pts = osrmRuta(fPc, fDst);
            if (pts == null || pts.size() < 2) {
                pts = new ArrayList<>();
                pts.add(fPc);
                pts.add(fDst);
            }
            final ArrayList<GeoPoint> fPts = pts;
            runOnUiThread(() -> {
                for (Polyline p : lineasEta) map.getOverlays().remove(p);
                lineasEta.clear();

                Polyline borde = new Polyline(map);
                borde.setPoints(new ArrayList<>(fPts));
                borde.setColor(Color.WHITE);
                borde.setWidth(12f);
                map.getOverlays().add(borde);
                lineasEta.add(borde);

                Polyline linea = new Polyline(map);
                linea.setPoints(new ArrayList<>(fPts));
                linea.setColor(COL_TRAMO);
                linea.setWidth(7f);
                map.getOverlays().add(linea);
                lineasEta.add(linea);

                if (marcadorConductorRT != null) {
                    map.getOverlays().remove(marcadorConductorRT);
                    map.getOverlays().add(marcadorConductorRT);
                }
                map.invalidate();
            });
        }).start();
    }

    private void calcularEtaConductor(GeoPoint posConductor) {
        GeoPoint destino = (!pasajeroRecogido && pSubida != null) ? pSubida : pBajada;
        if (destino == null) return;

        final GeoPoint fPc  = posConductor;
        final GeoPoint fDst = destino;

        new Thread(() -> {
            try {
                String coords = fPc.getLongitude() + "," + fPc.getLatitude() + ";"
                        + fDst.getLongitude() + "," + fDst.getLatitude() + "?overview=false";
                String json = null;
                try { json = http(OSRM_PROPIO  + "/route/v1/driving/" + coords); }
                catch (Exception ignored) {}
                if (json == null || json.isEmpty())
                    json = http(OSRM_PUBLICO + "/route/v1/driving/" + coords);
                if (json == null) return;

                JSONObject obj  = new JSONObject(json);
                if (!"Ok".equals(obj.optString("code"))) return;
                JSONObject ruta = obj.getJSONArray("routes").getJSONObject(0);
                double durSeg   = ruta.optDouble("duration", 0);
                double distMet  = ruta.optDouble("distance", 0);
                int    minutos  = Math.max(1, (int) Math.ceil(durSeg / 60.0));
                double distKm   = distMet / 1000.0;

                String textoEta;
                if (durSeg < 60)       textoEta = "🚗 El conductor ya está llegando";
                else if (minutos < 60) textoEta = "🚗 Conductor llega en " + minutos + " min";
                else {
                    int h = minutos / 60, m = minutos % 60;
                    textoEta = "🚗 Conductor llega en " + h + "h " + m + "min";
                }
                String textoDist = distKm < 1.0
                        ? String.format("📍 %.0f m de ti", distMet)
                        : String.format("📍 %.1f km de ti", distKm);

                final String fEta  = textoEta;
                final String fDist = textoDist;
                runOnUiThread(() -> {
                    if (tvEta != null) {
                        tvEta.setText(fEta);
                        tvEta.setVisibility(android.view.View.VISIBLE);
                    }
                    if (tvDistEta != null) {
                        tvDistEta.setText(fDist);
                        tvDistEta.setVisibility(android.view.View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "calcularEtaConductor: " + e.getMessage());
            }
        }).start();
    }

    // =========================================================================
    //  GPS PROPIO DEL CONDUCTOR
    // =========================================================================

    private void configurarGpsPropio() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERM);
            return;
        }
        LocationRequest req = LocationRequest.create();
        req.setInterval(2000);
        req.setFastestInterval(1000);
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult res) {
                if (res.getLastLocation() == null) return;
                double lat   = res.getLastLocation().getLatitude();
                double lon   = res.getLastLocation().getLongitude();
                float  rumbo = res.getLastLocation().getBearing();

                GeoPoint pos = new GeoPoint(lat, lon);
                if (miUltimaPosicion != null) {
                    double dLat = Math.abs(pos.getLatitude()  - miUltimaPosicion.getLatitude());
                    double dLon = Math.abs(pos.getLongitude() - miUltimaPosicion.getLongitude());
                    if (dLat < 0.000013 && dLon < 0.000013) return;
                }
                if (rumbo == 0f && miUltimaPosicion != null)
                    rumbo = calcularRumbo(miUltimaPosicion, pos);
                miUltimaPosicion = pos;

                final float fRumbo = rumbo;
                runOnUiThread(() -> {
                    actualizarMarcadorPropio(pos, fRumbo);
                    if (session.isConductor()) {
                        verificarRecogidaPasajero(pos);
                        verificarLlegadaAlDestino(pos);
                    }
                });

                if (session.isConductor() && desdeViajeActivo && idViaje > 0)
                    enviarUbicacionAlBackend(lat, lon);

                refFirebase.child("lat").setValue(lat);
                refFirebase.child("lng").setValue(lon);
                refFirebase.child("rumbo").setValue(rumbo);
                refFirebase.child("ts").setValue(System.currentTimeMillis());

                if (!desdeViajeActivo && destinoLat != 0 && !rutaSolicitada) {
                    rutaSolicitada = true;
                    pedirRutasBackend(lat, lon);
                }
            }
        };
        fusedClient.requestLocationUpdates(req, locationCallback, getMainLooper());
    }

    private void actualizarMarcadorPropio(GeoPoint pos, float rumbo) {
        if (marcadorGpsPropio == null) {
            marcadorGpsPropio = new Marker(map);
            marcadorGpsPropio.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marcadorGpsPropio.setTitle("📍 Mi posición");
            marcadorGpsPropio.setIcon(new BitmapDrawable(getResources(),
                    crearIconoCarro(0xFF1976D2, rumbo)));
            marcadorGpsPropio.setPosition(pos);
            map.getOverlays().add(marcadorGpsPropio);
            map.getController().animateTo(pos);
            map.invalidate();
            return;
        }
        GeoPoint ini = marcadorGpsPropio.getPosition();
        if (ini == null) {
            marcadorGpsPropio.setPosition(pos);
            marcadorGpsPropio.setIcon(new BitmapDrawable(getResources(),
                    crearIconoCarro(0xFF1976D2, rumbo)));
            map.invalidate();
            return;
        }
        final float fRumbo = rumbo;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1800);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            marcadorGpsPropio.setPosition(new GeoPoint(
                    ini.getLatitude()  + t * (pos.getLatitude()  - ini.getLatitude()),
                    ini.getLongitude() + t * (pos.getLongitude() - ini.getLongitude())));
            if (Math.round(t * 100) % 10 == 0)
                marcadorGpsPropio.setIcon(new BitmapDrawable(getResources(),
                        crearIconoCarro(0xFF1976D2, fRumbo)));
            map.invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (session.isConductor()) map.getController().animateTo(pos);
            }
        });
        anim.start();
    }

    private void enviarUbicacionAlBackend(double lat, double lon) {
        try {
            JSONObject body = new JSONObject();
            body.put("lat",      lat);
            body.put("lng",      lon);
            body.put("latitud",  lat);
            body.put("longitud", lon);
            String url = Constantes.BASE_URL + "/api/viajes/" + idViaje + "/ubicacion-conductor";
            ConexionApi.getInstance(this).post(url, body,
                    r   -> { /* OK */ },
                    err -> Log.w(TAG, "enviarUbicacion: " + err));
        } catch (Exception e) {
            Log.w(TAG, "enviarUbicacion ex: " + e.getMessage());
        }
    }

    // =========================================================================
    //  ICONO CARRO
    // =========================================================================

    private Bitmap crearIconoCarro(int color, float rumbo) {
        int sz = 84;
        Bitmap base = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cv   = new Canvas(base);

        Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG);
        ps.setColor(Color.argb(55, 0, 0, 0));
        cv.drawCircle(sz / 2f + 3, sz / 2f + 5, sz / 2f - 7, ps);

        Paint pbg = new Paint(Paint.ANTI_ALIAS_FLAG);
        pbg.setColor(Color.WHITE);
        cv.drawCircle(sz / 2f, sz / 2f, sz / 2f - 6, pbg);

        Paint pc = new Paint(Paint.ANTI_ALIAS_FLAG);
        pc.setColor(color);
        cv.drawCircle(sz / 2f, sz / 2f, sz / 2f - 9, pc);

        Paint pw = new Paint(Paint.ANTI_ALIAS_FLAG);
        pw.setColor(Color.WHITE);
        pw.setStyle(Paint.Style.FILL);

        float cx   = sz / 2f;
        float cy   = sz / 2f;
        float semi = sz * 0.17f;
        float nose = sz * 0.28f;
        float cola = sz * 0.20f;

        android.graphics.RectF cuerpo = new android.graphics.RectF(
                cx - semi, cy - cola, cx + semi, cy + nose * 0.55f);
        cv.drawRoundRect(cuerpo, semi * 0.5f, semi * 0.5f, pw);

        Path morro = new Path();
        morro.moveTo(cx,        cy - nose);
        morro.lineTo(cx - semi, cy - cola * 0.15f);
        morro.lineTo(cx + semi, cy - cola * 0.15f);
        morro.close();
        cv.drawPath(morro, pw);

        Bitmap rotado = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cvR    = new Canvas(rotado);
        Matrix matrix = new Matrix();
        matrix.setRotate(rumbo, sz / 2f, sz / 2f);
        cvR.drawBitmap(base, matrix, null);
        base.recycle();
        return rotado;
    }

    // =========================================================================
    //  CHIPS ETA
    // =========================================================================

    private void crearChipsEta() {
        float dp = getResources().getDisplayMetrics().density;
        FrameLayout root = (FrameLayout) getWindow().getDecorView()
                .findViewById(android.R.id.content);

        tvEta = new TextView(this);
        tvEta.setTextColor(Color.WHITE);
        tvEta.setTextSize(13f);
        tvEta.setTypeface(null, Typeface.BOLD);
        tvEta.setPadding((int)(14*dp), (int)(8*dp), (int)(14*dp), (int)(8*dp));
        tvEta.setVisibility(android.view.View.GONE);
        android.graphics.drawable.GradientDrawable bg1 =
                new android.graphics.drawable.GradientDrawable();
        bg1.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg1.setCornerRadius(30 * dp);
        bg1.setColor(Color.parseColor("#CC1565C0"));
        bg1.setStroke((int)(1.5f * dp), Color.WHITE);
        tvEta.setBackground(bg1);
        FrameLayout.LayoutParams lp1 = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp1.gravity     = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp1.bottomMargin = (int)(80 * dp);
        root.addView(tvEta, lp1);

        tvDistEta = new TextView(this);
        tvDistEta.setTextColor(Color.WHITE);
        tvDistEta.setTextSize(11f);
        tvDistEta.setTypeface(null, Typeface.BOLD);
        tvDistEta.setPadding((int)(10*dp), (int)(6*dp), (int)(10*dp), (int)(6*dp));
        tvDistEta.setVisibility(android.view.View.GONE);
        android.graphics.drawable.GradientDrawable bg2 =
                new android.graphics.drawable.GradientDrawable();
        bg2.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg2.setCornerRadius(20 * dp);
        bg2.setColor(Color.parseColor("#CCFF6F00"));
        tvDistEta.setBackground(bg2);
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp2.gravity     = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp2.bottomMargin = (int)(122 * dp);
        root.addView(tvDistEta, lp2);
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================

    private float calcularRumbo(GeoPoint desde, GeoPoint hasta) {
        double lat1 = Math.toRadians(desde.getLatitude());
        double lat2 = Math.toRadians(hasta.getLatitude());
        double dLon = Math.toRadians(hasta.getLongitude() - desde.getLongitude());
        double x    = Math.sin(dLon) * Math.cos(lat2);
        double y    = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        return (float) ((Math.toDegrees(Math.atan2(x, y)) + 360) % 360);
    }

    private void pedirRutasBackend(double oLat, double oLng) {
        routeManager.fetchRoutes(oLat, oLng, destinoLat, destinoLng, "FASTEST",
                new RouteManager.RouteCallback() {
                    @Override public void onSuccess(RouteOptionsResponse r) {
                        runOnUiThread(() -> pintarRutasBackend(r));
                    }
                    @Override public void onError(String msg) {
                        Log.e(TAG, "fetchRoutes error: " + msg);
                    }
                });
    }

    private void pintarRutasBackend(RouteOptionsResponse response) {
        if (response.routes == null || response.routes.isEmpty()) return;
        for (Polyline p : lineasPintadas) map.getOverlays().remove(p);
        lineasPintadas.clear();

        List<GeoJsonHelper.RutaSimple> rutas = new ArrayList<>();
        for (RouteOption r : response.routes)
            rutas.add(new GeoJsonHelper.RutaSimple(
                    r.id, r.geojson, r.distanceKm, r.durationMin, r.fuelCostCop));

        List<Polyline> pintadas = GeoJsonHelper.pintarRutas(rutas, map);
        lineasPintadas.addAll(pintadas);
        GeoJsonHelper.zoomARutas(lineasPintadas, map);

        if (marcadorGpsPropio != null) {
            map.getOverlays().remove(marcadorGpsPropio);
            map.getOverlays().add(marcadorGpsPropio);
        }
        map.invalidate();

        RouteOption m = response.routes.get(0);
        Toast.makeText(this,
                "Ruta: " + m.distanceKm + " km | "
                        + (int) m.durationMin + " min | $"
                        + (int) m.fuelCostCop + " COP",
                Toast.LENGTH_LONG).show();
    }


    private void escucharFinViajeComoPasajero() {
        if (idViaje <= 0 || session.isConductor()) return;

        refEstadoViaje = FirebaseDatabase.getInstance()
                .getReference("viajes_estado")
                .child("viaje_" + idViaje);

        listenerEstadoViaje = new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                String estado = snapshot.child("estado").getValue(String.class);
                Log.d(TAG, "Firebase viaje estado → " + estado);
                if ("FINALIZADO".equals(estado)) {
                    runOnUiThread(() -> mostrarPantallaViajeTerminado());
                }
            }
            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                Log.w(TAG, "escucharFinViaje cancelled: " + error.getMessage());
            }
        };
        refEstadoViaje.addValueEventListener(listenerEstadoViaje);
        Log.d(TAG, "Pasajero escuchando Firebase viaje_" + idViaje);
    }

    private void mostrarPantallaViajeTerminado() {
        if (isFinishing() || isDestroyed()) return;
        if (rPoll != null) hPoll.removeCallbacks(rPoll);

        new AlertDialog.Builder(this)
                .setTitle("Viaje finalizado")
                .setMessage("El conductor llegó al destino. ¡Gracias por usar MoviFlex!\n\nAhora puedes registrar tu pago.")
                .setCancelable(false)
                .setPositiveButton("Pagar viaje", (d, w) -> irAPagarDesdeMapa())
                .setNegativeButton("Cerrar mapa", (d, w) -> {
                    Intent intent = new Intent(this, HomePasajero.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    private void irAPagarDesdeMapa() {
        // Obtener el precio del viaje para pasarlo a PagoActivity
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viajeObj -> {
                    double monto = viajeObj.optDouble("precio", 0);
                    if (monto <= 0) {
                        JSONObject ruta = viajeObj.optJSONObject("ruta");
                        if (ruta != null)
                            monto = ruta.optDouble("precio",
                                    ruta.optDouble("costoCombustible", 0));
                    }
                    final double fMonto = monto;
                    runOnUiThread(() -> {
                        Intent intent = new Intent(this, PagoActivity.class);
                        intent.putExtra(PagoActivity.EXTRA_ID_VIAJE, idViaje);
                        intent.putExtra(PagoActivity.EXTRA_MONTO,    fMonto);
                        intent.putExtra(PagoActivity.EXTRA_CONDUCTOR, nomConductor);
                        startActivity(intent);
                        // NO hacemos finish() aquí para que pueda volver al mapa si cancela
                    });
                },
                err -> runOnUiThread(() -> {
                    // Sin precio → abrir PagoActivity con monto 0 (el pasajero verá "Consultar con conductor")
                    Intent intent = new Intent(this, PagoActivity.class);
                    intent.putExtra(PagoActivity.EXTRA_ID_VIAJE, idViaje);
                    intent.putExtra(PagoActivity.EXTRA_MONTO,    0.0);
                    intent.putExtra(PagoActivity.EXTRA_CONDUCTOR, nomConductor);
                    startActivity(intent);
                })
        );
    }

    private void verificarAccesoAlMapa() {
        boolean esConductor = session.isConductor();

        if (esConductor) {
            // Conductor: debe tener un viaje INICIADO o EN_CURSO
            ConexionApi.getInstance(this).getArrayNoCache(
                    Constantes.BASE_URL + "/api/viajes/mis-viajes",
                    response -> {
                        boolean tieneViajeActivo = false;
                        if (response != null) {
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject v = response.optJSONObject(i);
                                if (v == null) continue;
                                String est = v.optString("estado", "").toUpperCase().trim();
                                if ("INICIADO".equals(est) || "EN_CURSO".equals(est)
                                        || "ACTIVO".equals(est)) {
                                    tieneViajeActivo = true;
                                    break;
                                }
                            }
                        }
                        final boolean fActivo = tieneViajeActivo;
                        runOnUiThread(() -> {
                            if (!fActivo && !desdeViajeActivo && idViaje <= 0)
                                mostrarMapaBloqueado(true);
                        });
                    },
                    err -> runOnUiThread(() -> {
                        if (!desdeViajeActivo && idViaje <= 0)
                            mostrarMapaBloqueado(true);
                    })
            );
        } else {
            // Pasajero: debe tener una reserva activa
            ConexionApi.getInstance(this).getArrayNoCache(
                    Constantes.MIS_RESERVAS,
                    response -> {
                        boolean tieneReservaActiva = false;
                        if (response != null) {
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject r = response.optJSONObject(i);
                                if (r == null) continue;
                                String est = r.optString("estado", "").toUpperCase().trim();
                                if ("RESERVADO".equals(est)
                                        || "CONFIRMADO".equals(est) || "CONFIRMADA".equals(est)
                                        || "RECOGIDO".equals(est)
                                        || "ESPERANDO_RECOGIDA".equals(est)
                                        || "EN_CURSO".equals(est)) {

                                    // Verificar que el viaje también esté activo
                                    JSONObject viaje = r.optJSONObject("viaje");
                                    if (viaje != null) {
                                        String estViaje = viaje.optString("estado", "").toUpperCase().trim();
                                        if ("INICIADO".equals(estViaje) || "EN_CURSO".equals(estViaje)
                                                || "ACTIVO".equals(estViaje) || "CREADO".equals(estViaje)
                                                || "DISPONIBLE".equals(estViaje)
                                                || "PROGRAMADO".equals(estViaje)) {
                                            tieneReservaActiva = true;
                                            break;
                                        }
                                    } else {
                                        // Si no viene el objeto viaje, confiar en el estado de la reserva
                                        tieneReservaActiva = true;
                                        break;
                                    }
                                }
                            }
                        }
                        final boolean fActivo = tieneReservaActiva;
                        runOnUiThread(() -> {
                            if (!fActivo && !desdeViajeActivo && idViaje <= 0)
                                mostrarMapaBloqueado(false);
                        });
                    },
                    err -> runOnUiThread(() -> {
                        if (!desdeViajeActivo && idViaje <= 0)
                            mostrarMapaBloqueado(false);
                    })
            );
        }
    }

    private void mostrarMapaBloqueado(boolean esConductor) {
        if (isFinishing() || isDestroyed()) return;

        new AlertDialog.Builder(this)
                .setTitle(esConductor ? "🚗 Sin viaje activo" : "🗺️ Sin reserva activa")
                .setMessage(esConductor
                        ? "Primero debes publicar e iniciar un viaje para acceder al mapa en tiempo real."
                        : "Primero debes hacer una reserva en un viaje para acceder al mapa en tiempo real.")
                .setCancelable(false)
                .setPositiveButton(esConductor ? "📋 Publicar viaje" : "🔍 Buscar viajes", (d, w) -> {
                    Intent intent = esConductor
                            ? new Intent(this, PublicarRuta.class)
                            : new Intent(this, HomePasajero.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("← Volver", (d, w) -> {
                    Intent intent = esConductor
                            ? new Intent(this, HomeConductor.class)
                            : new Intent(this, HomePasajero.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    private void irACalificarConductorDesdeMapa() {
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viajeObj -> {
                    int    idCond  = -1;
                    String nomCond = "";

                    JSONObject condObj = viajeObj.optJSONObject("conductor");
                    if (condObj != null) {
                        // Extraer ID
                        for (String k : new String[]{"id", "idUsuarios", "idUsuario"}) {
                            int id = condObj.optInt(k, -1);
                            if (id > 0) { idCond = id; break; }
                        }
                        // Extraer nombre
                        for (String k : new String[]{"nombre", "nombres", "nombreCompleto", "name"}) {
                            String n = condObj.optString(k, "");
                            if (!n.isEmpty() && !n.equals("null")) { nomCond = n; break; }
                        }
                        if (nomCond.isEmpty()) {
                            String n = condObj.optString("nombres", "");
                            String a = condObj.optString("apellidos", "");
                            if (!n.isEmpty() || !a.isEmpty()) nomCond = (n + " " + a).trim();
                        }
                    }
                    // Fallback si no viene en objeto conductor
                    if (idCond <= 0)
                        idCond = viajeObj.optInt("idConductor",
                                viajeObj.optInt("conductorId", -1));
                    if (nomCond.isEmpty())
                        nomCond = nomConductor.isEmpty() ? "el conductor" : nomConductor;

                    Log.d(TAG, "Calificar conductor → id=" + idCond + " nom=" + nomCond);

                    final int    fIdCond  = idCond;
                    final String fNomCond = nomCond;
                    final int    fIdPas   = session.getIdUsuario();

                    if (fIdCond > 0) {
                        runOnUiThread(() ->
                                CalificacionController.mostrarBottomSheetCalificar(
                                        this,
                                        idViaje,
                                        fIdCond,
                                        fNomCond,
                                        fIdPas,
                                        false,   // el pasajero califica al conductor
                                        (puntuacion, comentario) -> {
                                            if (refEstadoViaje != null && listenerEstadoViaje != null)
                                                refEstadoViaje.removeEventListener(listenerEstadoViaje);
                                            Intent intent = new Intent(this, HomePasajero.class);
                                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                    | Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(intent);
                                            finish();
                                        }
                                )
                        );
                    } else {
                        // No se encontró el conductor → ir directo a home
                        Log.w(TAG, "idConductor no encontrado en viaje " + idViaje);
                        runOnUiThread(() -> {
                            if (refEstadoViaje != null && listenerEstadoViaje != null)
                                refEstadoViaje.removeEventListener(listenerEstadoViaje);
                            Intent intent = new Intent(this, HomePasajero.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        });
                    }
                },
                err -> {
                    Log.e(TAG, "irACalificarConductor error: " + err);
                    runOnUiThread(() -> {
                        Intent intent = new Intent(this, HomePasajero.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    });
                }
        );
    }

    private void inflarBanner(boolean esConductor) {
        float dp  = getResources().getDisplayMetrics().density;
        int   p12 = (int)(12 * dp), p8 = (int)(8 * dp);

        FrameLayout root = (FrameLayout) findViewById(android.R.id.content);
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(p12, (int)(36 * dp), p12, p8);

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.argb(220, 0, 77, 64));
        banner.setBackground(bg);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        banner.setLayoutParams(lp);

        TextView tvE = new TextView(this);
        tvE.setText(esConductor ? "🚗  VIAJE EN CURSO  •  GPS activo ●" : "🚗  VIAJE EN CURSO");
        tvE.setTextColor(Color.parseColor("#A8EEE6"));
        tvE.setTextSize(10f);
        tvE.setTypeface(null, Typeface.BOLD);
        tvE.setLetterSpacing(0.1f);
        banner.addView(tvE);

        TextView tvR = new TextView(this);
        tvR.setText("📍 " + (nomSubida.isEmpty() ? "Origen" : nomSubida)
                + "  →  " + (nomDestinoRuta.isEmpty() ? (nomBajada.isEmpty() ? "Destino" : nomBajada) : nomDestinoRuta));
        tvR.setTextColor(Color.WHITE);
        tvR.setTextSize(14f);
        tvR.setTypeface(null, Typeface.BOLD);
        tvR.setMaxLines(1);
        tvR.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lpR = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpR.topMargin = (int)(4 * dp);
        tvR.setLayoutParams(lpR);
        banner.addView(tvR);

        if (!esConductor) {
            LinearLayout ley = new LinearLayout(this);
            ley.setOrientation(LinearLayout.HORIZONTAL);
            ley.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpL.topMargin = (int)(5 * dp);
            ley.setLayoutParams(lpL);

            TextView tv1 = new TextView(this);
            tv1.setText("━━ Ruta completa");
            tv1.setTextColor(Color.parseColor("#4DD0E1"));
            tv1.setTextSize(10f);
            tv1.setTypeface(null, Typeface.BOLD);
            ley.addView(tv1);

            TextView tvSp = new TextView(this);
            tvSp.setText("   ");
            ley.addView(tvSp);

            TextView tv2 = new TextView(this);
            tv2.setText("━━ Conductor");
            tv2.setTextColor(Color.parseColor("#FFB74D"));
            tv2.setTextSize(10f);
            tv2.setTypeface(null, Typeface.BOLD);
            ley.addView(tv2);
            banner.addView(ley);

            TextView tvP = new TextView(this);
            tvP.setText("🙋 " + (nomSubida.isEmpty() ? "—" : nomSubida)
                    + "   🚏 " + (nomBajada.isEmpty() ? "—" : nomBajada));
            tvP.setTextColor(Color.parseColor("#E0F7FA"));
            tvP.setTextSize(11f);
            tvP.setMaxLines(1);
            tvP.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lpP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpP.topMargin    = (int)(4 * dp);
            lpP.bottomMargin = (int)(3 * dp);
            tvP.setLayoutParams(lpP);
            banner.addView(tvP);
        }

        // ── Botón FINALIZAR VIAJE (solo conductor, viaje activo) ─────────────
        if (esConductor && desdeViajeActivo && idViaje > 0) {
            android.widget.Button btnFin = new android.widget.Button(this);
            LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (int)(36 * dp));
            lpBtn.topMargin    = (int)(8 * dp);
            lpBtn.bottomMargin = (int)(4 * dp);
            btnFin.setLayoutParams(lpBtn);
            btnFin.setText("🏁 Finalizar viaje");
            btnFin.setTextSize(12f);
            btnFin.setTypeface(null, Typeface.BOLD);
            btnFin.setTextColor(Color.WHITE);
            btnFin.setPadding((int)(16*dp), 0, (int)(16*dp), 0);
            android.graphics.drawable.GradientDrawable bgBtn =
                    new android.graphics.drawable.GradientDrawable();
            bgBtn.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bgBtn.setCornerRadius(20 * dp);
            bgBtn.setColor(Color.parseColor("#C62828"));
            btnFin.setBackground(bgBtn);
            btnFin.setOnClickListener(v -> confirmarFinalizarManual());
            banner.addView(btnFin);
        }

        root.addView(banner);
    }

    // =========================================================================
    //  BOTTOM NAV
    // =========================================================================

    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav == null) return;
        nav.setSelectedItemId(R.id.nav_mapa);

        boolean esConductor = session.isConductor();

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_mapa) return true;   // ya estamos aquí

            if (esConductor) {
                // ── Nav del CONDUCTOR ────────────────────────────────────
                if      (id == R.id.nav_inicio)     startActivity(new Intent(this, HomeConductor.class));
                else if (id == R.id.nav_mis_viajes) startActivity(new Intent(this, PublicarRuta.class));
                else if (id == R.id.nav_mensajes)   startActivity(new Intent(this, Mensajes.class));
                else if (id == R.id.nav_perfil)     startActivity(new Intent(this, PerfilUsuario.class));
                else return true;
            } else {
                // ── Nav del PASAJERO ─────────────────────────────────────
                if      (id == R.id.nav_inicio)     startActivity(new Intent(this, HomePasajero.class));
                else if (id == R.id.nav_mis_viajes) startActivity(new Intent(this, MisReservasActivity.class));
                else if (id == R.id.nav_mensajes)   startActivity(new Intent(this, Mensajes.class));
                else if (id == R.id.nav_perfil)     startActivity(new Intent(this, PerfilUsuario.class));
                else return true;
            }
            finish();
            return true;
        });
    }
    @Override
    public void onRequestPermissionsResult(int req,
                                           @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == LOCATION_PERM && grants.length > 0
                && grants[0] == PackageManager.PERMISSION_GRANTED) {
            configurarGpsPropio();
        } else {
            Toast.makeText(this, "Permiso de ubicación requerido",
                    Toast.LENGTH_LONG).show();
        }
    }

    // =========================================================================
    //  MICRO-HELPERS
    // =========================================================================

    private GeoPoint primerNoNulo(GeoPoint a, GeoPoint b) {
        return a != null ? a : b;
    }

    private ArrayList<GeoPoint> rectaEntre(GeoPoint a, GeoPoint b) {
        ArrayList<GeoPoint> l = new ArrayList<>();
        l.add(a);
        l.add(b);
        return l;
    }
}