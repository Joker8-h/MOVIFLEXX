package com.arlys.moviflexx.controller;

import android.app.AlertDialog;
import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import android.widget.ScrollView;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Manager.CalificacionesManager;
import com.arlys.moviflexx.model.Manager.RouteManager;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.NotificacionesHelper;
import com.arlys.moviflexx.model.VoiceAssistantManager;
import com.arlys.moviflexx.model.pojo.RouteOption;
import com.arlys.moviflexx.model.pojo.RouteOptionsResponse;
import com.arlys.moviflexx.utils.GeoJsonHelper;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
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

import io.socket.client.IO;
import io.socket.client.Socket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Mapa extends BaseActivity {

    // ── Interfaz interna ──────────────────────────────────────────────────────
    private interface MarkerCallback { void onMarker(Marker marker); }

    // ── Constantes ────────────────────────────────────────────────────────────
    private static final String TAG           = "Mapa";
    private static final int    LOCATION_PERM = 1;

    private static final double UMBRAL_RECOGIDA_M = 60.0;
    private static final double UMBRAL_DESTINO_M  = 60.0;
    private static final double UMBRAL_BAJADA_M   = 80.0;

    // ── OSRM: 3 endpoints en cascada (igual que PublicarRuta) ────────────────
    private static final String OSRM_POPAYAN =
            "https://osrm-popayan-production.up.railway.app";
    private static final String OSRM_PUBLICO =
            "https://router.project-osrm.org";
    private static final String OSRM_PROPIO  =
            "https://optimizacionofrutas-production.up.railway.app";

    private static final int COL_RUTA    = 0xFF009B8D;
    private static final int COL_TRAMO   = 0xFFFF6F00;
    private static final int COL_CONDUCT = 0xFF1565C0;
    private static final int COL_PROPIO  = 0xFF1976D2;

    // ── Vistas ────────────────────────────────────────────────────────────────
    private MapView  map;
    private Marker   marcadorGpsPropio;
    private TextView tvEta;
    private TextView tvDistEta;
    private TextView tvMontoHud;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private DatabaseReference refFirebase;
    private DatabaseReference refEstadoViaje;
    private com.google.firebase.database.ValueEventListener listenerEstadoViaje;

    // ── Fused Location ────────────────────────────────────────────────────────
    private FusedLocationProviderClient fusedClient;
    private LocationCallback            locationCallback;
    private GeoPoint                    miUltimaPosicion = null;
    // Flag para saber si ya se dibujó la ruta con GPS real (evitar re-dibujos múltiples)
    private boolean rutaDibujaConGps = false;

    // ── Polilíneas ────────────────────────────────────────────────────────────
    private final List<Polyline> lineasPintadas = new ArrayList<>();
    private final List<Polyline> lineasEta      = new ArrayList<>();
    private boolean rutaSolicitada = false;

    // ── Datos del viaje ───────────────────────────────────────────────────────
    private double  destinoLat = 0, destinoLng = 0;
    private int     idViaje = 0;
    private boolean desdeViajeActivo = false;

    private GeoPoint pOrigen  = null;
    private GeoPoint pDestino = null;
    private GeoPoint pSubida  = null;
    private GeoPoint pBajada  = null;

    private String nomSubida      = "";
    private String nomBajada      = "";
    private String nomConductor   = "";
    private String nomDestinoRuta = "";

    private final ArrayList<GeoPoint> waypointsRuta = new ArrayList<>();

    // ── Marcadores en tiempo real ─────────────────────────────────────────────
    private Marker   marcadorConductorRT  = null;
    private GeoPoint posAnteriorConductor = null;
    private float    rumboConductor       = 0f;

    // ── Polling / fallback ────────────────────────────────────────────────────
    private final Handler hPoll = new Handler(Looper.getMainLooper());
    private Runnable      rPoll = null;

    // ── Estado lógico simple ──────────────────────────────────────────────────
    private boolean pasajeroRecogido  = false;
    private boolean viajeYaFinalizado = false;
    private boolean pagoYaLanzado     = false;
    private Marker  marcadorSubida    = null;
    private Marker  marcadorBajada    = null;

    // ── MULTIPASAJERO ────────────────────────────────────────────────────────
    private static class PasajeroInfo {
        int      idUsuario;
        String   nombre;
        String   fotoUrl;
        GeoPoint pSubida;
        GeoPoint pBajada;
        String   nomSubida;
        String   nomBajada;
        double   latSubida;
        double   lngSubida;
        double   monto;
        boolean  recogido;
        boolean  yaLlegoABajada;
        Marker   marcadorSubida;
        Marker   marcadorBajada;
        long     idReserva;
    }

    private final List<PasajeroInfo> pasajeros           = new ArrayList<>();
    private int                      indicePasajeroActual = 0;

    // ── Máquina de estados — conductor ───────────────────────────────────────
    private enum EstadoViaje {
        EN_CAMINO_A_PASAJERO,
        LLEGANDO_A_PASAJERO,
        PASAJERO_A_BORDO,
        EN_CAMINO_A_DESTINO,
        VIAJE_FINALIZADO
    }
    private EstadoViaje estadoViaje              = EstadoViaje.EN_CAMINO_A_PASAJERO;
    private boolean     alertaProximidadMostrada = false;
    private ValueAnimator pulsoAnimator          = null;

    // ── Managers ──────────────────────────────────────────────────────────────
    private SessionManager session;
    private RouteManager   routeManager;

    // ── Socket.IO ─────────────────────────────────────────────────────────────
    private Socket  mSocket;
    private boolean isSocketConnected = false;

    // ── Guía de Voz ──────────────────────────────────────────────────────────
    private VoiceAssistantManager voiceAssistant;
    private JSONArray currentSteps   = null;
    private int       nextStepIndex  = 0;
    private long      lastVoiceTime  = 0;

    // =========================================================================
    //  LIFECYCLE
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_mapa);

        session      = new SessionManager(this);
        fusedClient  = LocationServices.getFusedLocationProviderClient(this);
        routeManager = new RouteManager();

        refFirebase = FirebaseDatabase.getInstance()
                .getReference("ubicaciones")
                .child("conductor_" + session.getIdUsuario());

        destinoLat       = getIntent().getDoubleExtra("DESTINO_LAT",  0);
        destinoLng       = getIntent().getDoubleExtra("DESTINO_LNG",  0);
        idViaje          = getIntent().getIntExtra("ID_VIAJE",         0);
        desdeViajeActivo = getIntent().getBooleanExtra("DESDE_VIAJE", false);

        String estadoReserva = getIntent().getStringExtra("ESTADO_RESERVA");
        if ("RECOGIDO".equals(estadoReserva) || "COMPLETADO".equals(estadoReserva))
            pasajeroRecogido = true;

        String nc = getIntent().getStringExtra("NOM_CONDUCTOR");
        nomConductor = nc != null ? nc : "Conductor";

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.setTilesScaledToDpi(true);
        map.setUseDataConnection(true);
        map.getController().setZoom(15.0);
        map.getController().setCenter(new GeoPoint(2.4419, -76.6063));

        if (session.isConductor() && desdeViajeActivo) {
            activarModoViajeConductor();
        } else {
            configurarBottomNav();
            crearChipsEta();
        }

        boolean tieneViajeInfo = desdeViajeActivo || idViaje > 0 || destinoLat != 0
                || getIntent().getDoubleExtra("LAT_BAJADA", 0) != 0;

        if (tieneViajeInfo) {
            desdeViajeActivo = true;
            cargarExtrasViajeYDibujar();
        }

        configurarGpsPropio();
        voiceAssistant = VoiceAssistantManager.getInstance(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        if (desdeViajeActivo && idViaje > 0)
            sincronizarEstadoDesdeServidor();
        if (desdeViajeActivo && !session.isConductor() && rPoll != null)
            hPoll.postDelayed(rPoll, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
        if (fusedClient != null && locationCallback != null)
            fusedClient.removeLocationUpdates(locationCallback);
        if (rPoll != null) hPoll.removeCallbacks(rPoll);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pulsoAnimator != null) { pulsoAnimator.cancel(); pulsoAnimator = null; }
        if (rPoll != null) hPoll.removeCallbacks(rPoll);
        desconectarSocket();
        if (refEstadoViaje != null && listenerEstadoViaje != null)
            refEstadoViaje.removeEventListener(listenerEstadoViaje);
    }

    // =========================================================================
    //  BACK PRESSED
    // =========================================================================

    @Override
    public void onBackPressed() {
        if (session.isConductor() && desdeViajeActivo && !viajeYaFinalizado) {
            Toast.makeText(this, "Finaliza el viaje antes de salir", Toast.LENGTH_SHORT).show();
        } else {
            restaurarUiNormal();
            super.onBackPressed();
        }
    }

    // =========================================================================
    //  SINCRONIZAR ESTADO AL REANUDAR
    // =========================================================================

    private void sincronizarEstadoDesdeServidor() {
        if (idViaje <= 0) return;
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viajeObj -> {
                    String estadoSrv = viajeObj.optString("estado", "").toUpperCase();
                    if ("FINALIZADO".equals(estadoSrv) || "COMPLETADO".equals(estadoSrv))
                        viajeYaFinalizado = true;

                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    if (usuarios != null && !pasajeros.isEmpty()) {
                        for (int i = 0; i < usuarios.length(); i++) {
                            JSONObject u = usuarios.optJSONObject(i);
                            if (u == null) continue;
                            int idU  = extraerIdUsuarioDeObj(u);
                            String est = u.optString("estado","").toUpperCase();
                            boolean rec = "RECOGIDO".equals(est) || "COMPLETADO".equals(est)
                                    || "EN_CURSO".equals(est);
                            for (PasajeroInfo p : pasajeros) {
                                if (p.idUsuario == idU && rec && !p.recogido) {
                                    p.recogido = true;
                                    runOnUiThread(() -> {
                                        quitarMarcadorSubida(p);
                                        actualizarEstadoHud();
                                    });
                                }
                                if (p.idUsuario == idU && p.pSubida == null) {
                                    double latS = u.optDouble("latSubida",
                                            u.optDouble("latOrigen",
                                                    u.optDouble("latInicio", 0)));
                                    double lngS = u.optDouble("lngSubida",
                                            u.optDouble("lngOrigen",
                                                    u.optDouble("lngInicio", 0)));
                                    if (latS != 0) {
                                        p.pSubida   = new GeoPoint(latS, lngS);
                                        p.latSubida = latS;
                                        p.lngSubida = lngS;
                                        String nomS = u.optString("nombreParadaSubida",
                                                u.optString("nombreParadaInicio",""));
                                        p.nomSubida = nomS.isEmpty() ? "Punto de recogida" : nomS;
                                    }
                                }
                            }
                        }
                        indicePasajeroActual = pasajeros.size();
                        for (int i = 0; i < pasajeros.size(); i++) {
                            if (!pasajeros.get(i).recogido) {
                                indicePasajeroActual = i;
                                break;
                            }
                        }
                    }

                    boolean todosRec = true;
                    for (PasajeroInfo p : pasajeros) { if (!p.recogido) { todosRec = false; break; } }
                    if (todosRec && !pasajeros.isEmpty()
                            && estadoViaje != EstadoViaje.VIAJE_FINALIZADO)
                        estadoViaje = EstadoViaje.EN_CAMINO_A_DESTINO;

                    runOnUiThread(() -> pintarMarcadoresPasajeros());
                },
                err -> Log.w(TAG, "sincronizarEstado error: " + err));
    }

    // =========================================================================
    //  MODO VIAJE CONDUCTOR
    // =========================================================================

    private void activarModoViajeConductor() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav != null) nav.setVisibility(View.GONE);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);


    }




    // =========================================================================
    //  RESTAURAR UI NORMAL
    // =========================================================================

    private void restaurarUiNormal() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav != null) nav.setVisibility(View.VISIBLE);
        if (getSupportActionBar() != null) getSupportActionBar().show();
    }

    // =========================================================================
    //  CARGA PRINCIPAL DEL VIAJE
    // =========================================================================

    private void cargarExtrasViajeYDibujar() {
        boolean esConductor = session.isConductor();

        double latO = getIntent().getDoubleExtra("ORIGEN_LAT",  0);
        double lngO = getIntent().getDoubleExtra("ORIGEN_LNG",  0);
        double latD = getIntent().getDoubleExtra("DESTINO_LAT", 0);
        double lngD = getIntent().getDoubleExtra("DESTINO_LNG", 0);
        double latS = getIntent().getDoubleExtra("LAT_SUBIDA",  0);
        double lngS = getIntent().getDoubleExtra("LNG_SUBIDA",  0);
        double latB = getIntent().getDoubleExtra("LAT_BAJADA",  0);
        double lngB = getIntent().getDoubleExtra("LNG_BAJADA",  0);

        String ns = getIntent().getStringExtra("NOM_SUBIDA");
        String nb = getIntent().getStringExtra("NOM_BAJADA");
        nomSubida = ns != null ? ns : "";
        nomBajada = nb != null ? nb : "";

        if (latO != 0) pOrigen = new GeoPoint(latO, lngO);

        if (latD != 0) {
            pDestino = new GeoPoint(latD, lngD);
        } else if (destinoLat != 0 && !esConductor) {
            pDestino = new GeoPoint(destinoLat, destinoLng);
        }
        if (pDestino == null) pDestino = extraerDestinoDeParadasJson();

        if (latS != 0) pSubida = new GeoPoint(latS, lngS);
        if (latB != 0) pBajada = new GeoPoint(latB, lngB);

        leerWaypointsDelIntent();
        inflarBannerConMonto(esConductor);

        if (!esConductor) {
            arrancarPollingConductor();
            escucharFinViajeComoPasajero();
        }

        if ((pOrigen == null || pDestino == null) && idViaje > 0) {
            cargarCoordsDesdeApi(esConductor);
            return;
        }

        if (esConductor && idViaje > 0) cargarSubidaBajadaPasajero();
        if (!esConductor && idViaje > 0 && pSubida == null && pBajada == null)
            cargarParadasPasajeroDesdeApi();

        if (esConductor && idViaje > 0)
            cargarDatosMultipasajeroDesdeApi();

        if (pOrigen != null && pDestino != null)
            map.post(() -> dibujarRutaViaje(esConductor));
        else if (pDestino != null)
            usarGpsComoOrigen(esConductor);
        else
            agregarMarcadores(esConductor);

        if (idViaje > 0) inicializarSocket();
    }

    // =========================================================================
    //  MULTIPASAJERO
    // =========================================================================

    private void cargarDatosMultipasajeroDesdeApi() {
        if (idViaje <= 0) return;
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viajeJson -> {
                    double precioViaje = viajeJson.optDouble("precio", 0);
                    JSONObject ruta    = viajeJson.optJSONObject("ruta");
                    if (precioViaje == 0 && ruta != null)
                        precioViaje = ruta.optDouble("precio", ruta.optDouble("costoCombustible", 0));
                    JSONArray usuarios = viajeJson.optJSONArray("usuarios");
                    if (usuarios != null)
                        procesarUsuariosMultipasajero(usuarios, precioViaje);
                },
                err -> Log.w(TAG, "cargarDatosMultipasajero: " + err));
    }

    private void procesarUsuariosMultipasajero(JSONArray usuarios, double precioViaje) {
        pasajeros.clear();
        indicePasajeroActual = 0;

        for (int i = 0; i < usuarios.length(); i++) {
            JSONObject u = usuarios.optJSONObject(i);
            if (u == null) continue;
            String est = u.optString("estado", "").toUpperCase();
            if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;

            PasajeroInfo p = new PasajeroInfo();
            p.idReserva = u.optLong("idReserva", u.optLong("id", -1));

            JSONObject usuObj = u.optJSONObject("usuario");
            if (usuObj == null) usuObj = u.optJSONObject("pasajero");
            if (usuObj != null) {
                for (String k : new String[]{"idUsuarios","id","idUsuario"}) {
                    int id = usuObj.optInt(k,-1); if (id>0) { p.idUsuario=id; break; }
                }
                p.nombre = usuObj.optString("nombre", usuObj.optString("nombres",""));
                String ape = usuObj.optString("apellidos","");
                if (!ape.isEmpty()) p.nombre = (p.nombre + " " + ape).trim();
                p.fotoUrl = usuObj.optString("fotoPerfil",
                        usuObj.optString("fotoPerfi",
                                usuObj.optString("foto",
                                        usuObj.optString("photoUrl",
                                                usuObj.optString("avatar","")))));
            }
            if (p.nombre.isEmpty()) p.nombre = u.optString("nombrePasajero","Pasajero");

            double latSub = 0, lngSub = 0;
            JSONObject subObj = u.optJSONObject("puntoSubida");
            if (subObj == null) subObj = u.optJSONObject("subida");
            if (subObj != null) {
                latSub = subObj.optDouble("lat", subObj.optDouble("latitud",0));
                lngSub = subObj.optDouble("lng", subObj.optDouble("longitud",0));
                p.nomSubida = subObj.optString("nombre","");
            }
            if (latSub == 0) {
                latSub = u.optDouble("latSubida", u.optDouble("latOrigen", u.optDouble("latInicio",0)));
                lngSub = u.optDouble("lngSubida", u.optDouble("lngOrigen", u.optDouble("lngInicio",0)));
                p.nomSubida = u.optString("nombreParadaSubida", u.optString("nombreParadaInicio",""));
            }
            if (latSub != 0) {
                p.pSubida  = new GeoPoint(latSub, lngSub);
                p.latSubida = latSub;
                p.lngSubida = lngSub;
            }
            if (p.nomSubida == null || p.nomSubida.isEmpty()) p.nomSubida = "Punto de recogida";

            double latBaj = 0, lngBaj = 0;
            JSONObject bajObj = u.optJSONObject("puntoBajada");
            if (bajObj == null) bajObj = u.optJSONObject("bajada");
            if (bajObj != null) {
                latBaj = bajObj.optDouble("lat", bajObj.optDouble("latitud",0));
                lngBaj = bajObj.optDouble("lng", bajObj.optDouble("longitud",0));
                p.nomBajada = bajObj.optString("nombre","");
            }
            if (latBaj == 0) {
                latBaj = u.optDouble("latBajada", u.optDouble("latParada",0));
                lngBaj = u.optDouble("lngBajada", u.optDouble("lngParada",0));
                p.nomBajada = u.optString("nombreParadaBajada", u.optString("nombreParada",""));
            }
            if (latBaj != 0) p.pBajada = new GeoPoint(latBaj, lngBaj);
            if (p.nomBajada == null || p.nomBajada.isEmpty()) p.nomBajada = "Parada del pasajero";

            p.monto = u.optDouble("monto",
                    u.optDouble("precioFinal",
                            u.optDouble("precioTramo",
                                    u.optDouble("costo", precioViaje))));
            if (p.monto == 0) p.monto = precioViaje;
            p.recogido = "RECOGIDO".equals(est) || "COMPLETADO".equals(est) || "EN_CURSO".equals(est);
            p.yaLlegoABajada = "COMPLETADO".equals(est);

            pasajeros.add(p);
        }

        indicePasajeroActual = pasajeros.size();
        for (int i = 0; i < pasajeros.size(); i++) {
            if (!pasajeros.get(i).recogido) { indicePasajeroActual = i; break; }
        }
        boolean todosRec = indicePasajeroActual >= pasajeros.size();
        if (todosRec && !pasajeros.isEmpty() && estadoViaje != EstadoViaje.VIAJE_FINALIZADO)
            estadoViaje = EstadoViaje.EN_CAMINO_A_DESTINO;

        runOnUiThread(() -> {
            pintarMarcadoresPasajeros();
            actualizarTextoRutaHud();
        });
    }

    @Nullable
    private PasajeroInfo pasajeroActual() {
        if (indicePasajeroActual < pasajeros.size())
            return pasajeros.get(indicePasajeroActual);
        return null;
    }

    private void pintarMarcadoresPasajeros() {
        for (PasajeroInfo p : pasajeros) {
            if (p.marcadorSubida != null) map.getOverlays().remove(p.marcadorSubida);
            if (p.marcadorBajada != null) map.getOverlays().remove(p.marcadorBajada);
            p.marcadorSubida = null;
            p.marcadorBajada = null;
        }

        for (PasajeroInfo p : pasajeros) {
            if (p.pSubida != null && !p.recogido) {
                boolean subidaEsDistintaDeOrigen = pOrigen == null
                        || !coordsIguales(p.pSubida.getLatitude(), p.pSubida.getLongitude(),
                        pOrigen.getLatitude(), pOrigen.getLongitude());
                String titulo = subidaEsDistintaDeOrigen
                        ? " " + p.nombre + "\n Subida: " + p.nomSubida
                        : " " + p.nombre + "\n Sube en origen";
                crearMarcadorConFoto(p.pSubida, titulo, p.fotoUrl, p.nombre, marker -> {
                    p.marcadorSubida = marker;
                    map.getOverlays().add(marker);
                    reordenarMarcadorGps();
                    map.invalidate();
                });
            }

            if (p.pBajada != null) {
                p.marcadorBajada = new Marker(map);
                p.marcadorBajada.setPosition(p.pBajada);
                p.marcadorBajada.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                p.marcadorBajada.setTitle(" Bajar a " + p.nombre + "\n " + p.nomBajada);
                int color = p.recogido ? 0xFFFF7043 : COL_CONDUCT;
                p.marcadorBajada.setIcon(new BitmapDrawable(getResources(), pinConCola(color, "")));
                map.getOverlays().add(p.marcadorBajada);
            }
        }
        reordenarMarcadorGps();
        map.invalidate();
    }

    private void quitarMarcadorSubida(PasajeroInfo p) {
        if (p.marcadorSubida != null) {
            map.getOverlays().remove(p.marcadorSubida);
            p.marcadorSubida = null;
        }
        map.invalidate();
    }

    private void reordenarMarcadorGps() {
        if (marcadorGpsPropio != null) {
            map.getOverlays().remove(marcadorGpsPropio);
            map.getOverlays().add(marcadorGpsPropio);
        }
        if (marcadorConductorRT != null) {
            map.getOverlays().remove(marcadorConductorRT);
            map.getOverlays().add(marcadorConductorRT);
        }
    }

    // =========================================================================
    //  HUD SUPERIOR
    // =========================================================================

    private void inflarBannerConMonto(boolean esConductor) {
        float dp = density();
        FrameLayout root = findViewById(android.R.id.content);

        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(px(12), (int) (36 * dp), px(12), px(8));
        banner.setTag("hud_banner");

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF0003D31, 0xE8004D40});
        bg.setCornerRadii(new float[]{0, 0, 0, 0, px(18), px(18), px(18), px(18)});
        banner.setBackground(bg);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        banner.setLayoutParams(lp);

        LinearLayout filaEstado = new LinearLayout(this);
        filaEstado.setOrientation(LinearLayout.HORIZONTAL);
        filaEstado.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvBadge = new TextView(this);
        tvBadge.setText(esConductor ? "  VIAJE EN CURSO  •  GPS activo ●" : "  VIAJE EN CURSO");
        tvBadge.setTextColor(0xFF80CBC4);
        tvBadge.setTextSize(10f);
        tvBadge.setTypeface(null, Typeface.BOLD);
        tvBadge.setLetterSpacing(0.1f);
        filaEstado.addView(tvBadge);

        if (esConductor) {
            View punto = new View(this);
            GradientDrawable oval = new GradientDrawable();
            oval.setShape(GradientDrawable.OVAL);
            oval.setColor(0xFF4CAF50);
            punto.setBackground(oval);
            LinearLayout.LayoutParams lpP = new LinearLayout.LayoutParams(px(7), px(7));
            lpP.leftMargin = px(7);
            punto.setLayoutParams(lpP);
            filaEstado.addView(punto);
            ValueAnimator pulse = ValueAnimator.ofFloat(1f, 0.25f, 1f);
            pulse.setDuration(1200);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.addUpdateListener(a -> punto.setAlpha((float) a.getAnimatedValue()));
            pulse.start();

            if (tvMontoHud == null) {
                tvMontoHud = new TextView(this);
                tvMontoHud.setVisibility(View.GONE);
                tvMontoHud.setTextColor(0xFFFFD700);
                tvMontoHud.setTextSize(13f);
                tvMontoHud.setTypeface(null, Typeface.BOLD);
                tvMontoHud.setPadding(px(10), px(4), px(10), px(4));
                GradientDrawable bgMonto = new GradientDrawable();
                bgMonto.setShape(GradientDrawable.RECTANGLE);
                bgMonto.setColor(Color.argb(120, 0, 0, 0));
                bgMonto.setCornerRadius(px(12));
                tvMontoHud.setBackground(bgMonto);
                LinearLayout.LayoutParams lpM = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lpM.leftMargin = px(10);
                tvMontoHud.setLayoutParams(lpM);
                filaEstado.addView(tvMontoHud);
            }
        }
        banner.addView(filaEstado);

        TextView tvRuta = new TextView(this);
        tvRuta.setTag("tv_ruta_hud");
        tvRuta.setText(" " + (nomSubida.isEmpty() ? "Origen" : nomSubida)
                + "  →  " + (!nomDestinoRuta.isEmpty() ? nomDestinoRuta
                : (nomBajada.isEmpty() ? "Destino" : nomBajada)));
        tvRuta.setTextColor(Color.WHITE);
        tvRuta.setTextSize(14f);
        tvRuta.setTypeface(null, Typeface.BOLD);
        tvRuta.setMaxLines(1);
        tvRuta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lpR = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpR.topMargin = px(4);
        tvRuta.setLayoutParams(lpR);
        banner.addView(tvRuta);

        {
            LinearLayout ley = new LinearLayout(this);
            ley.setOrientation(LinearLayout.HORIZONTAL);
            ley.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpL.topMargin = px(5);
            ley.setLayoutParams(lpL);
            ley.addView(chipLeyenda("━━ Ruta", 0xFF4DD0E1));
            if (!esConductor) ley.addView(chipLeyenda("━━ Conductor", 0xFFFFB74D));
            else ley.addView(chipLeyenda(" Mi pos.", 0xFF1976D2));
            ley.addView(chipLeyenda(" Subida", 0xFF4CAF50));
            ley.addView(chipLeyenda(" Bajada", 0xFFFF7043));
            banner.addView(ley);
        }

        {
            TextView tvP = new TextView(this);
            tvP.setTag("tv_paradas_hud");
            String lblSub = nomSubida.isEmpty() ? "Cargando…" : nomSubida;
            String lblBaj = nomBajada.isEmpty() ? "Cargando…" : nomBajada;
            tvP.setText(" " + lblSub + "    " + lblBaj);
            tvP.setTextColor(Color.parseColor("#E0F7FA"));
            tvP.setTextSize(11f);
            tvP.setMaxLines(1);
            tvP.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lpPP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpPP.topMargin = px(4);
            lpPP.bottomMargin = px(3);
            tvP.setLayoutParams(lpPP);
            banner.addView(tvP);
        }

        // Al final de inflarBannerConMonto(), reemplazar la sección del botón por esta:
        if (esConductor && desdeViajeActivo && idViaje > 0) {
            // Separador
            View separador = new View(this);
            LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, px(1));
            lpSep.topMargin = px(6);
            lpSep.bottomMargin = px(6);
            separador.setBackgroundColor(Color.argb(60, 255, 255, 255));
            separador.setLayoutParams(lpSep);
            banner.addView(separador);

            android.widget.Button btnFin = new android.widget.Button(this);
            LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, px(44));
            lpBtn.bottomMargin = px(6);
            // ── Centrar el botón horizontalmente ──
            lpBtn.gravity = Gravity.CENTER_HORIZONTAL;
            btnFin.setLayoutParams(lpBtn);
            btnFin.setText("Finalizar viaje");
            btnFin.setTextSize(13f);
            btnFin.setTypeface(null, Typeface.BOLD);
            btnFin.setTextColor(Color.WHITE);
            btnFin.setPadding(px(28), 0, px(28), 0);
            btnFin.setAllCaps(false);
            GradientDrawable bgBtn = new GradientDrawable();
            bgBtn.setShape(GradientDrawable.RECTANGLE);
            bgBtn.setCornerRadius(px(22));
            bgBtn.setColor(0xFFB71C1C);
            bgBtn.setStroke(px(1), 0xFFEF9A9A);
            btnFin.setBackground(bgBtn);
            btnFin.setOnClickListener(v -> confirmarFinalizarManual());
            banner.addView(btnFin);
        }

        root.addView(banner);
// ── NO agregar ningún botón flotante adicional aquí ──
    }
    private TextView chipLeyenda(String texto, int color) {
        TextView tv = new TextView(this);
        tv.setText(texto); tv.setTextColor(color);
        tv.setTextSize(10f); tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = px(10);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void mostrarMontoEnHud(double monto, String nombrePasajero) {
        if (tvMontoHud == null || !session.isConductor()) return;
        runOnUiThread(() -> {
            if (monto > 0) {
                java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(
                        new java.util.Locale("es","CO"));
                tvMontoHud.setText("$" + nf.format(monto));
                tvMontoHud.setVisibility(View.VISIBLE);
                tvMontoHud.setAlpha(0f);
                tvMontoHud.animate().alpha(1f).setDuration(400).start();
            } else {
                tvMontoHud.setVisibility(View.GONE);
            }
        });
    }

    private void actualizarTextoRutaHud() {
        FrameLayout root = findViewById(android.R.id.content);
        View hud = root.findViewWithTag("hud_banner");
        if (hud == null) return;

        TextView tvRuta = hud.findViewWithTag("tv_ruta_hud");
        if (tvRuta != null) {
            String origenText  = nomSubida.isEmpty() ? "Origen" : nomSubida;
            String destinoText = !nomDestinoRuta.isEmpty() ? nomDestinoRuta
                    : (nomBajada.isEmpty() ? "Destino" : nomBajada);
            runOnUiThread(() -> tvRuta.setText("" + origenText + "  →  " + destinoText));
        }

        TextView tvParadas = hud.findViewWithTag("tv_paradas_hud");
        if (tvParadas != null) {
            String lblSub, lblBaj;
            if (!pasajeros.isEmpty()) {
                PasajeroInfo pri = pasajeros.get(0);
                lblSub = pri.nomSubida != null && !pri.nomSubida.isEmpty() ? pri.nomSubida : nomSubida;
                lblBaj = pri.nomBajada != null && !pri.nomBajada.isEmpty() ? pri.nomBajada : nomBajada;
            } else {
                lblSub = nomSubida.isEmpty() ? "—" : nomSubida;
                lblBaj = nomBajada.isEmpty() ? "—" : nomBajada;
            }
            final String fSub = lblSub, fBaj = lblBaj;
            runOnUiThread(() -> tvParadas.setText("" + fSub + "" + fBaj));
        }
    }

    private void actualizarEstadoHud() {
        if (!session.isConductor()) return;
        int recogidos=0, pendientes=0;
        for (PasajeroInfo p : pasajeros) { if (p.recogido) recogidos++; else pendientes++; }
        if (pasajeros.isEmpty()) return;
        String msg;
        if (pendientes > 0) {
            PasajeroInfo sig = pasajeroActual();
            msg = sig != null
                    ? "Recogiendo a " + sig.nombre + " (" + recogidos+"/"+pasajeros.size()+")"
                    : "En camino al destino";
        } else {
            msg = "Todos a bordo → al destino";
        }
        mostrarBannerEstado(msg, pendientes > 0 ? 0xFF2E7D32 : 0xFF1565C0);
    }

    // =========================================================================
    //  CHIPS ETA INFERIORES
    // =========================================================================

    private void crearChipsEta() {
        float dp = density();
        FrameLayout root = (FrameLayout) getWindow().getDecorView()
                .findViewById(android.R.id.content);

        tvEta = new TextView(this);
        tvEta.setTextColor(Color.WHITE);
        tvEta.setTextSize(13f);
        tvEta.setTypeface(null, Typeface.BOLD);
        tvEta.setPadding(px(14),px(8),px(14),px(8));
        tvEta.setVisibility(View.GONE);
        GradientDrawable bg1 = new GradientDrawable();
        bg1.setShape(GradientDrawable.RECTANGLE);
        bg1.setCornerRadius(30*dp);
        bg1.setColor(Color.parseColor("#CC1565C0"));
        bg1.setStroke((int)(1.5f*dp), Color.WHITE);
        tvEta.setBackground(bg1);
        FrameLayout.LayoutParams lp1 = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp1.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp1.bottomMargin = (int)(80*dp);
        root.addView(tvEta, lp1);

        tvDistEta = new TextView(this);
        tvDistEta.setTextColor(Color.WHITE);
        tvDistEta.setTextSize(11f);
        tvDistEta.setTypeface(null, Typeface.BOLD);
        tvDistEta.setPadding(px(10),px(6),px(10),px(6));
        tvDistEta.setVisibility(View.GONE);
        GradientDrawable bg2 = new GradientDrawable();
        bg2.setShape(GradientDrawable.RECTANGLE);
        bg2.setCornerRadius(20*dp);
        bg2.setColor(Color.parseColor("#CCFF6F00"));
        tvDistEta.setBackground(bg2);
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp2.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp2.bottomMargin = (int)(122*dp);
        root.addView(tvDistEta, lp2);
    }

    // =========================================================================
    //  DIBUJO DE RUTA — Sistema de 3 capas igual que PublicarRuta
    // =========================================================================

    private void dibujarRutaViaje(boolean esConductor) {
        GeoPoint desde = esConductor ? pOrigen : primerNoNulo(pOrigen, pSubida);
        GeoPoint hasta = esConductor ? pDestino : primerNoNulo(pDestino, pBajada);

        if (desde == null && miUltimaPosicion != null) desde = miUltimaPosicion;

        if (desde == null || hasta == null) {
            agregarMarcadores(esConductor);
            if (hasta != null) { map.getController().animateTo(hasta); map.getController().setZoom(15.0); }
            return;
        }

        final GeoPoint fDesde = desde;
        final GeoPoint fHasta = hasta;
        // ── Solo waypoints de la ruta original, NO subidas de pasajeros ──
        final ArrayList<GeoPoint> fWaypoints = new ArrayList<>(waypointsRuta);

        new Thread(() -> {
            ArrayList<GeoPoint> rutaTotal = osrmRutaConWaypoints(fDesde, fHasta, fWaypoints);

            if (rutaTotal == null || rutaTotal.size() < 2)
                rutaTotal = rectaEntre(fDesde, fHasta);

            final ArrayList<GeoPoint> rutaFinal = rutaTotal;
            runOnUiThread(() -> {
                limpiarLineas(lineasPintadas);
                dibujarPolilineaTresCapas(lineasPintadas, rutaFinal, COL_RUTA, 13f);
                agregarMarcadores(esConductor);
                pintarMarcadoresPasajeros();

                ArrayList<GeoPoint> todos = new ArrayList<>(rutaFinal);
                for (PasajeroInfo pp : pasajeros) {
                    if (pp.pSubida != null && !pp.recogido) todos.add(pp.pSubida);
                    if (pp.pBajada != null) todos.add(pp.pBajada);
                }
                zoomBoundingBox(todos);
                map.invalidate();
            });
        }).start();
    }
    /**
     * Dibuja una polilínea con 3 capas como en PublicarRuta:
     * 1. Sombra negra translúcida (más gruesa)
     * 2. Borde blanco
     * 3. Línea de color principal
     */
    private void dibujarPolilineaTresCapas(List<Polyline> lista, List<GeoPoint> pts,
                                           int color, float ancho) {
        if (pts == null || pts.size() < 2) return;
        ArrayList<GeoPoint> copia = new ArrayList<>(pts);

        // Capa 1: sombra
        Polyline sombra = new Polyline(map);
        sombra.setPoints(copia);
        sombra.setColor(Color.argb(60, 0, 0, 0));
        sombra.setWidth(ancho + 10f);
        map.getOverlays().add(sombra);
        lista.add(sombra);

        // Capa 2: borde blanco
        Polyline borde = new Polyline(map);
        borde.setPoints(copia);
        borde.setColor(Color.WHITE);
        borde.setWidth(ancho + 6f);
        map.getOverlays().add(borde);
        lista.add(borde);

        // Capa 3: línea de color
        Polyline linea = new Polyline(map);
        linea.setPoints(copia);
        linea.setColor(color);
        linea.setWidth(ancho);
        map.getOverlays().add(linea);
        lista.add(linea);
    }

    // =========================================================================
    //  OSRM — 3 endpoints en cascada igual que PublicarRuta
    // =========================================================================


    // =========================================================================
//  OSRM — 3 endpoints en cascada
// =========================================================================

    private ArrayList<GeoPoint> osrmRuta(GeoPoint desde, GeoPoint hasta) {
        if (desde == null || hasta == null) return null;

        String coords = desde.getLongitude() + "," + desde.getLatitude() + ";"
                + hasta.getLongitude() + "," + hasta.getLatitude()
                + "?overview=full&geometries=geojson&steps=true";

        String[] urls = {
                OSRM_POPAYAN + "/route/v1/driving/" + coords,
                OSRM_PUBLICO + "/route/v1/driving/" + coords,
                OSRM_PROPIO  + "/route/v1/driving/" + coords
        };

        for (String url : urls) {
            try {
                String json = http(url);
                if (json == null || json.isEmpty()) continue;
                ArrayList<GeoPoint> pts = parsearRutaYPasos(json);
                if (pts != null && pts.size() >= 2) return pts;
            } catch (Exception e) {
                Log.w(TAG, "osrmRuta falló: " + e.getMessage());
            }
        }
        return null;
    }

    private ArrayList<GeoPoint> osrmRutaConWaypoints(GeoPoint desde, GeoPoint hasta,
                                                     ArrayList<GeoPoint> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) return osrmRuta(desde, hasta);

        // Filtrar waypoints que no alarguen demasiado la ruta
        ArrayList<GeoPoint> wpFiltrados = new ArrayList<>();
        for (GeoPoint wp : waypoints) {
            double distDesde = calcularDistanciaMetros(desde, wp);
            double distHasta = calcularDistanciaMetros(hasta, wp);
            double distTotal = calcularDistanciaMetros(desde, hasta);
            if (distDesde + distHasta < distTotal * 2.5) {
                wpFiltrados.add(wp);
            }
        }

        if (wpFiltrados.isEmpty()) return osrmRuta(desde, hasta);

        StringBuilder coords = new StringBuilder();
        coords.append(desde.getLongitude()).append(",").append(desde.getLatitude());
        for (GeoPoint wp : wpFiltrados)
            coords.append(";").append(wp.getLongitude()).append(",").append(wp.getLatitude());
        coords.append(";").append(hasta.getLongitude()).append(",").append(hasta.getLatitude());
        String params = "?overview=full&geometries=geojson&steps=true";

        String[] urls = {
                OSRM_POPAYAN + "/route/v1/driving/" + coords + params,
                OSRM_PUBLICO + "/route/v1/driving/" + coords + params,
                OSRM_PROPIO  + "/route/v1/driving/" + coords + params
        };

        for (String url : urls) {
            try {
                String json = http(url);
                ArrayList<GeoPoint> pts = parsearRutaYPasos(json);
                if (pts != null && pts.size() >= 2) return pts;
            } catch (Exception e) {
                Log.w(TAG, "osrmRutaConWaypoints falló: " + e.getMessage());
            }
        }

        return osrmRuta(desde, hasta);
    }
    // =========================================================================
    //  POLILÍNEAS (método legacy para lineasEta — tramo naranja)
    // =========================================================================

    private void agregarPolilinea(List<Polyline> lista, List<GeoPoint> pts,
                                  int color, float ancho) {
        if (pts == null || pts.size() < 2) return;
        ArrayList<GeoPoint> copia = new ArrayList<>(pts);

        Polyline sombra = new Polyline(map);
        sombra.setPoints(copia); sombra.setColor(Color.argb(55,0,0,0)); sombra.setWidth(ancho+10f);
        map.getOverlays().add(sombra); lista.add(sombra);

        Polyline borde = new Polyline(map);
        borde.setPoints(copia); borde.setColor(Color.WHITE); borde.setWidth(ancho+6f);
        map.getOverlays().add(borde); lista.add(borde);

        Polyline linea = new Polyline(map);
        linea.setPoints(copia); linea.setColor(color); linea.setWidth(ancho);
        map.getOverlays().add(linea); lista.add(linea);
    }

    private void limpiarLineas(List<Polyline> lista) {
        for (Polyline p : lista) map.getOverlays().remove(p);
        lista.clear();
    }



    // =========================================================================
    //  PARSEO OSRM
    // =========================================================================

    private ArrayList<GeoPoint> parsearRutaYPasos(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(json);
            if (!"Ok".equals(obj.optString("code",""))) return null;
            JSONArray routes = obj.optJSONArray("routes");
            if (routes == null || routes.length() == 0) return null;
            JSONObject route = routes.getJSONObject(0);

            if (session.isConductor()) {
                JSONArray legs = route.optJSONArray("legs");
                if (legs != null && legs.length() > 0) {
                    currentSteps  = legs.getJSONObject(0).optJSONArray("steps");
                    nextStepIndex = 0;
                }
            }

            JSONArray coordsArr = route.getJSONObject("geometry").getJSONArray("coordinates");
            ArrayList<GeoPoint> pts = new ArrayList<>();
            for (int i = 0; i < coordsArr.length(); i++) {
                JSONArray par = coordsArr.getJSONArray(i);
                pts.add(new GeoPoint(par.getDouble(1), par.getDouble(0)));
            }
            return pts.size() >= 2 ? pts : null;
        } catch (Exception e) { Log.w(TAG,"parsearRutaYPasos: "+e.getMessage()); return null; }
    }

    private String http(String urlStr) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestProperty("User-Agent","Moviflexx/1.0");
            c.setConnectTimeout(12000); c.setReadTimeout(12000);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder(); String ln;
            while ((ln = r.readLine()) != null) sb.append(ln);
            r.close(); return sb.toString();
        } finally { if (c != null) c.disconnect(); }
    }

    // =========================================================================
    //  MARCADORES BASE
    // =========================================================================

    private void agregarMarcadores(boolean esConductor) {
        if (esConductor) {
            if (pOrigen != null) {
                String nomOrig = nomSubida.isEmpty() ? "Inicio de ruta" : nomSubida;
                agregarPin(pOrigen, "" + nomOrig, 0xFF43A047, "A");
            }
            if (pDestino != null) {
                boolean esBajadaPasajero = pBajada != null
                        && coordsIguales(pDestino.getLatitude(), pDestino.getLongitude(),
                        pBajada.getLatitude(),  pBajada.getLongitude());
                if (!esBajadaPasajero) {
                    boolean coincideConBajadaMulti = false;
                    for (PasajeroInfo pp : pasajeros) {
                        if (pp.pBajada != null && coordsIguales(
                                pDestino.getLatitude(), pDestino.getLongitude(),
                                pp.pBajada.getLatitude(), pp.pBajada.getLongitude())) {
                            coincideConBajadaMulti = true; break;
                        }
                    }
                    if (!coincideConBajadaMulti) {
                        String nomDest = !nomDestinoRuta.isEmpty() ? nomDestinoRuta : "Destino final";
                        agregarPin(pDestino, "" + nomDest, 0xFFE53935, "B");
                    }
                }
            }
        } else {
            if (pOrigen  != null) agregarPin(pOrigen, "Inicio de ruta", 0xFF4CAF50,"A");
            if (pDestino != null) agregarPin(pDestino,"Final de ruta",  0xFFEF5350,"B");
            if (pSubida  != null && !pasajeroRecogido) {
                marcadorSubida = new Marker(map);
                marcadorSubida.setPosition(pSubida);
                marcadorSubida.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marcadorSubida.setTitle("Mi punto de recogida\n"+nomSubida);
                marcadorSubida.setIcon(new BitmapDrawable(getResources(), circuloMarcadorP1(0xFFFF6F00,"P1")));
                map.getOverlays().add(marcadorSubida);
            }
            if (pBajada != null) {
                marcadorBajada = new Marker(map);
                marcadorBajada.setPosition(pBajada);
                marcadorBajada.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marcadorBajada.setTitle("Mi parada\n"+nomBajada);
                marcadorBajada.setIcon(new BitmapDrawable(getResources(),
                        circuloMarcador(0xFFFF6F00,"")));
                map.getOverlays().add(marcadorBajada);
            }
        }
        map.invalidate();
    }

    private void agregarPin(GeoPoint pos, String titulo, int color, String letra) {
        if (pos == null) return;
        Marker m = new Marker(map);
        m.setPosition(pos); m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle(titulo); m.setIcon(new BitmapDrawable(getResources(), circuloMarcador(color,letra)));
        map.getOverlays().add(m);
    }

    // =========================================================================
    //  MÁQUINA DE ESTADOS — CONDUCTOR
    // =========================================================================

    private void actualizarEstadoViaje(GeoPoint posConductor) {
        if (!session.isConductor() || !desdeViajeActivo || viajeYaFinalizado) return;

        switch (estadoViaje) {
            case EN_CAMINO_A_PASAJERO: {
                PasajeroInfo sig = pasajeroActual();
                if (sig == null || sig.pSubida == null) {
                    estadoViaje = EstadoViaje.EN_CAMINO_A_DESTINO; return;
                }
                double dist = calcularDistanciaMetros(posConductor, sig.pSubida);
                if (dist <= UMBRAL_RECOGIDA_M && !alertaProximidadMostrada) {
                    alertaProximidadMostrada = true;
                    transicionarA(EstadoViaje.LLEGANDO_A_PASAJERO, posConductor);
                }
                break;
            }
            case LLEGANDO_A_PASAJERO: {
                PasajeroInfo sig = pasajeroActual();
                if (sig == null || sig.pSubida == null) {
                    estadoViaje = EstadoViaje.EN_CAMINO_A_DESTINO; return;
                }
                double dist = calcularDistanciaMetros(posConductor, sig.pSubida);
                if (dist <= 35) transicionarA(EstadoViaje.PASAJERO_A_BORDO, posConductor);
                break;
            }
            case PASAJERO_A_BORDO:
                break;
            case EN_CAMINO_A_DESTINO: {
                actualizarBajadasPasajeros(posConductor);
                if (pDestino == null) break;
                double dist = calcularDistanciaMetros(posConductor, pDestino);
                if (dist <= UMBRAL_DESTINO_M)
                    transicionarA(EstadoViaje.VIAJE_FINALIZADO, posConductor);
                break;
            }
            case VIAJE_FINALIZADO:
                break;
        }
    }

    private void actualizarBajadasPasajeros(GeoPoint posConductor) {
        for (PasajeroInfo p : pasajeros) {
            if (!p.recogido || p.yaLlegoABajada || p.pBajada == null) continue;
            double dist = calcularDistanciaMetros(posConductor, p.pBajada);
            if (dist <= UMBRAL_BAJADA_M) {
                p.yaLlegoABajada = true;
                final String nom = p.nombre;
                runOnUiThread(() -> mostrarBannerEstado("🚏 Llegaste a la parada de "+nom, 0xFF2E7D32));
                notificarLlegadaBajadaPasajero(p);
            }
        }
    }

    private void notificarLlegadaBajadaPasajero(PasajeroInfo p) {
        try {
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("viajes_estado").child("viaje_"+idViaje);
            ref.child("bajada_pasajero_"+p.idUsuario).setValue("LLEGADO");
            ref.child("timestamp_bajada_"+p.idUsuario).setValue(System.currentTimeMillis());
        } catch (Exception e) { Log.w(TAG,"notificarBajada: "+e.getMessage()); }
    }

    private void transicionarA(EstadoViaje nuevoEstado, GeoPoint posConductor) {
        Log.d(TAG, "Estado: "+estadoViaje+" → "+nuevoEstado);
        estadoViaje = nuevoEstado;

        switch (nuevoEstado) {
            case LLEGANDO_A_PASAJERO: {
                PasajeroInfo sig = pasajeroActual();
                String nomP = sig != null ? sig.nombre : "el pasajero";
                runOnUiThread(() -> {
                    mostrarBannerEstado("Estás llegando a "+nomP, 0xFFFF7043);
                    if (sig != null && sig.marcadorSubida != null)
                        iniciarPulsoMarcador(sig.marcadorSubida);
                });
                break;
            }
            case PASAJERO_A_BORDO: {
                PasajeroInfo sig = pasajeroActual();
                if (sig == null) { estadoViaje = EstadoViaje.EN_CAMINO_A_DESTINO; break; }
                sig.recogido = true;
                mostrarMontoEnHud(sig.monto, sig.nombre);
                final PasajeroInfo fSig = sig;
                runOnUiThread(() -> {
                    animarDesaparicionMarcador(fSig.marcadorSubida, () -> {
                        fSig.marcadorSubida = null;
                        if (fSig.pBajada != null) {
                            if (fSig.marcadorBajada != null)
                                map.getOverlays().remove(fSig.marcadorBajada);
                            fSig.marcadorBajada = new Marker(map);
                            fSig.marcadorBajada.setPosition(fSig.pBajada);
                            fSig.marcadorBajada.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            fSig.marcadorBajada.setTitle("Bajar a " + fSig.nombre + "\n" + fSig.nomBajada);
                            fSig.marcadorBajada.setIcon(
                                    new BitmapDrawable(getResources(), pinConCola(0xFFFF7043, "")));
                            map.getOverlays().add(fSig.marcadorBajada);
                            reordenarMarcadorGps();
                            map.invalidate();
                        }
                    });
                    mostrarBannerEstado("¡" + fSig.nombre + " a bordo!", 0xFF2E7D32);
                });
                notificarRecogidaAlBackend(sig);
                indicePasajeroActual++;
                alertaProximidadMostrada = false;

                PasajeroInfo siguiente = pasajeroActual();
                if (siguiente != null) {
                    estadoViaje = EstadoViaje.EN_CAMINO_A_PASAJERO;
                    runOnUiThread(() -> {
                        actualizarEstadoHud();
                        if (miUltimaPosicion != null) actualizarLineaNaranja(miUltimaPosicion);
                    });
                } else {
                    estadoViaje = EstadoViaje.EN_CAMINO_A_DESTINO;
                    runOnUiThread(() -> {
                        mostrarBannerEstado("Todos a bordo → al destino", 0xFF1565C0);
                        if (tvEta != null) tvEta.setVisibility(View.GONE);
                        if (tvDistEta != null) tvDistEta.setVisibility(View.GONE);
                        if (miUltimaPosicion != null) actualizarLineaNaranja(miUltimaPosicion);
                    });
                }
                break;
            }
            case VIAJE_FINALIZADO:
                viajeYaFinalizado = true;
                runOnUiThread(() -> {
                    mostrarBannerEstado("¡Llegaste al destino!", 0xFF1565C0);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        ejecutarFinalizarViaje(); // esto llama paso2FinalizarMapa → sheet
                    }, 1200);
                });
                break;
        }
    }

    // =========================================================================
    //  NOTIFICAR RECOGIDA
    // =========================================================================

    private void notificarRecogidaAlBackend(PasajeroInfo p) {
        if (idViaje <= 0) return;
        try {
            JSONObject body = new JSONObject();
            body.put("estado","RECOGIDO");
            body.put("idUsuario", p.idUsuario);
            ConexionApi.getInstance(this).post(
                    Constantes.BASE_URL+"/api/viajes/"+idViaje+"/pasajero-recogido",
                    body,
                    r -> Log.d(TAG,"Recogida notificada OK"),
                    e -> Log.w(TAG,"Error notificando recogida: "+e));

            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("viajes_estado").child("viaje_"+idViaje);
            ref.child("estado").setValue("PASAJERO_RECOGIDO");
            ref.child("pasajero_recogido_"+p.idUsuario).setValue(true);
            ref.child("timestamp").setValue(System.currentTimeMillis());

            if (mSocket != null && mSocket.connected()) {
                JSONObject data = new JSONObject();
                data.put("idViaje", idViaje);
                data.put("evento","pasajero_recogido");
                data.put("idPasajero", p.idUsuario);
                mSocket.emit("trip_event", data);
            }
        } catch (Exception e) { Log.w(TAG,"notificarRecogida: "+e.getMessage()); }
    }

    // =========================================================================
    //  VERIFICAR LLEGADA A BAJADA (vista pasajero)
    // =========================================================================

    private void verificarLlegadaBajadaPasajero(GeoPoint posConductor) {
        if (session.isConductor() || pagoYaLanzado) return;

        GeoPoint miPBajada = null;
        for (PasajeroInfo p : pasajeros) {
            if (p.idUsuario == session.getIdUsuario() && p.pBajada != null) {
                miPBajada = p.pBajada;
                break;
            }
        }
        if (miPBajada == null && pBajada != null) miPBajada = pBajada;
        if (miPBajada == null) return;

        double dist = calcularDistanciaMetros(posConductor, miPBajada);
        if (dist <= UMBRAL_BAJADA_M) {
            pagoYaLanzado = true;
            runOnUiThread(() -> {
                if (tvEta != null)     tvEta.setVisibility(View.GONE);
                if (tvDistEta != null) tvDistEta.setVisibility(View.GONE);
                mostrarBannerEstado(
                        "Llegaste a tu parada — ¡Registra tu pago!", 0xFF2E7D32);
                // Mostrar sheet de pago del pasajero
                new Handler(Looper.getMainLooper()).postDelayed(
                        this::mostrarSheetPagoPasajeroDesdeProximidad, 1800);
            });
        }
    }

    private void mostrarSheetPagoPasajeroDesdeProximidad() {
        if (isFinishing() || isDestroyed() || idViaje <= 0) return;

        // Buscar el idConductor del viaje
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viajeObj -> {
                    int idCond = viajeObj.optInt("idConductor",
                            viajeObj.optInt("conductorId", -1));
                    if (idCond <= 0) {
                        JSONObject condObj = viajeObj.optJSONObject("conductor");
                        if (condObj != null)
                            for (String k : new String[]{"id", "idUsuarios", "idUsuario"}) {
                                int id = condObj.optInt(k, -1);
                                if (id > 0) { idCond = id; break; }
                            }
                    }

                    double montoBase = viajeObj.optDouble("precio", 0);
                    if (montoBase <= 0) {
                        JSONObject ruta = viajeObj.optJSONObject("ruta");
                        if (ruta != null)
                            montoBase = ruta.optDouble("precio",
                                    ruta.optDouble("costoCombustible", 0));
                    }

                    // Intentar obtener el monto del tramo del pasajero
                    double montoTramo = 0;
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    int miId = session.getIdUsuario();
                    if (usuarios != null) {
                        for (int i = 0; i < usuarios.length(); i++) {
                            JSONObject u = usuarios.optJSONObject(i);
                            if (u == null) continue;
                            int idU = -1;
                            JSONObject uo = u.optJSONObject("usuario");
                            if (uo != null)
                                for (String k : new String[]{"idUsuarios", "id", "idUsuario"}) {
                                    int id = uo.optInt(k, -1);
                                    if (id > 0) { idU = id; break; }
                                }
                            if (idU != miId) continue;
                            montoTramo = u.optDouble("precioFinal",
                                    u.optDouble("precio",
                                            u.optDouble("costoPorPasajero", 0)));
                            break;
                        }
                    }

                    final double fMonto = montoTramo > 0 ? montoTramo : montoBase;
                    final int    fIdCond = idCond;

                    runOnUiThread(() ->
                            construirSheetPagoPasajeroMapa(fMonto, fIdCond));
                },
                err -> runOnUiThread(() -> construirSheetPagoPasajeroMapa(0, -1))
        );
    }

    private void construirSheetPagoPasajeroMapa(double montoBase, int idConductor) {
        if (isFinishing() || isDestroyed()) return;

        BottomSheetDialog sheet = new BottomSheetDialog(this,
                com.google.android.material.R.style.Theme_MaterialComponents_BottomSheetDialog);

        float d = density();
        int p16 = px(16), p12 = px(12), p8 = px(8), p6 = px(6), p4 = px(4);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p16, p12, p16, px(32));
        sv.addView(root);

        // Tirón
        View tiron = new View(this);
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(px(40), px(4));
        lpT.gravity = Gravity.CENTER_HORIZONTAL;
        lpT.bottomMargin = p12;
        tiron.setLayoutParams(lpT);
        GradientDrawable tBg = new GradientDrawable();
        tBg.setShape(GradientDrawable.RECTANGLE);
        tBg.setCornerRadius(px(4));
        tBg.setColor(Color.parseColor("#BDBDBD"));
        tiron.setBackground(tBg);
        root.addView(tiron);

        // Título
        TextView tvTit = new TextView(this);
        tvTit.setText("Pagar tu viaje");
        tvTit.setTextSize(20f);
        tvTit.setTypeface(null, Typeface.BOLD);
        tvTit.setTextColor(Color.parseColor("#004D40"));
        LinearLayout.LayoutParams lpTit = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTit.bottomMargin = p4;
        tvTit.setLayoutParams(lpTit);
        root.addView(tvTit);

        TextView tvSub = new TextView(this);
        tvSub.setText("Elige cómo le pagas al conductor");
        tvSub.setTextSize(13f);
        tvSub.setTextColor(Color.parseColor("#546E7A"));
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSub.bottomMargin = p16;
        tvSub.setLayoutParams(lpSub);
        root.addView(tvSub);

        // Card monto
        MaterialCardView cardMonto = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCM = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCM.bottomMargin = p16;
        cardMonto.setLayoutParams(lpCM);
        cardMonto.setRadius(px(16));
        cardMonto.setCardElevation(0);
        cardMonto.setCardBackgroundColor(Color.parseColor("#E0F7FA"));
        cardMonto.setStrokeWidth(px(1));
        cardMonto.setStrokeColor(Color.parseColor("#80DEEA"));

        LinearLayout innerMonto = new LinearLayout(this);
        innerMonto.setOrientation(LinearLayout.HORIZONTAL);
        innerMonto.setGravity(Gravity.CENTER_VERTICAL);
        innerMonto.setPadding(p16, p12, p16, p12);

        TextView tvMontoLbl = new TextView(this);
        tvMontoLbl.setText("Total a pagar");
        tvMontoLbl.setTextSize(14f);
        tvMontoLbl.setTextColor(Color.parseColor("#00695C"));
        tvMontoLbl.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        innerMonto.addView(tvMontoLbl);

        java.text.NumberFormat nf =
                java.text.NumberFormat.getNumberInstance(new java.util.Locale("es", "CO"));
        TextView tvMontoVal = new TextView(this);
        tvMontoVal.setText(montoBase > 0
                ? "$" + nf.format(montoBase) + " COP"
                : "Consultar con conductor");
        tvMontoVal.setTextSize(16f);
        tvMontoVal.setTypeface(null, Typeface.BOLD);
        tvMontoVal.setTextColor(Color.parseColor("#004D40"));
        innerMonto.addView(tvMontoVal);
        cardMonto.addView(innerMonto);
        root.addView(cardMonto);

        // Método pago
        TextView tvMetLbl = new TextView(this);
        tvMetLbl.setText("¿Cómo vas a pagar?");
        tvMetLbl.setTextSize(14f);
        tvMetLbl.setTypeface(null, Typeface.BOLD);
        tvMetLbl.setTextColor(Color.parseColor("#1A2035"));
        LinearLayout.LayoutParams lpML = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpML.bottomMargin = p8;
        tvMetLbl.setLayoutParams(lpML);
        root.addView(tvMetLbl);

        final String[] metodo = {""};
        LinearLayout filaM = new LinearLayout(this);
        filaM.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpFM = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFM.bottomMargin = p16;
        filaM.setLayoutParams(lpFM);

        MaterialButton btnEf = new MaterialButton(this);
        btnEf.setText("Efectivo");
        btnEf.setTextSize(13f);
        btnEf.setTextColor(Color.parseColor("#004D40"));
        btnEf.setCornerRadius(px(12));
        btnEf.setBackgroundColor(Color.parseColor("#E0F2F1"));
        LinearLayout.LayoutParams lpBE = new LinearLayout.LayoutParams(0, px(52), 1f);
        lpBE.rightMargin = p8;
        btnEf.setLayoutParams(lpBE);

        MaterialButton btnTr = new MaterialButton(this);
        btnTr.setText("Transferencia");
        btnTr.setTextSize(13f);
        btnTr.setTextColor(Color.parseColor("#004D40"));
        btnTr.setCornerRadius(px(12));
        btnTr.setBackgroundColor(Color.parseColor("#E0F2F1"));
        btnTr.setLayoutParams(new LinearLayout.LayoutParams(0, px(52), 1f));
        filaM.addView(btnEf);
        filaM.addView(btnTr);
        root.addView(filaM);

        MaterialButton btnConf = new MaterialButton(this);
        btnConf.setText("CONFIRMAR QUE PAGUÉ");
        btnConf.setTextSize(15f);
        btnConf.setTextColor(Color.WHITE);
        btnConf.setEnabled(false);
        btnConf.setAlpha(0.5f);
        btnConf.setBackgroundColor(Color.parseColor("#B0BEC5"));
        btnConf.setCornerRadius(px(14));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(54));
        lpBtn.bottomMargin = p4;
        btnConf.setLayoutParams(lpBtn);
        root.addView(btnConf);

        MaterialButton btnSaltar = new MaterialButton(this);
        btnSaltar.setText("Saltar por ahora");
        btnSaltar.setTextSize(12f);
        btnSaltar.setTextColor(Color.parseColor("#90A4AE"));
        btnSaltar.setBackgroundColor(Color.TRANSPARENT);
        btnSaltar.setStrokeWidth(0);
        btnSaltar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(40)));
        root.addView(btnSaltar);

        Runnable actualizarUI = () -> {
            boolean esEf = "efectivo".equals(metodo[0]);
            boolean esTr = "transferencia".equals(metodo[0]);
            btnEf.setBackgroundColor(
                    esEf ? Color.parseColor("#00897B") : Color.parseColor("#E0F2F1"));
            btnEf.setTextColor(esEf ? Color.WHITE : Color.parseColor("#004D40"));
            btnTr.setBackgroundColor(
                    esTr ? Color.parseColor("#00897B") : Color.parseColor("#E0F2F1"));
            btnTr.setTextColor(esTr ? Color.WHITE : Color.parseColor("#004D40"));
            boolean hay = !metodo[0].isEmpty();
            btnConf.setEnabled(hay);
            btnConf.setAlpha(hay ? 1f : 0.5f);
            btnConf.setBackgroundColor(
                    hay ? Color.parseColor("#00897B") : Color.parseColor("#B0BEC5"));
        };

        btnEf.setOnClickListener(v -> { metodo[0] = "efectivo";      actualizarUI.run(); });
        btnTr.setOnClickListener(v -> { metodo[0] = "transferencia"; actualizarUI.run(); });

        int idPasajero  = session.getIdUsuario();
        String nomPas   = session.getNombre();
        double fMonto   = montoBase;
        final BottomSheetDialog fSheet = sheet;

        btnSaltar.setOnClickListener(v -> fSheet.dismiss());

        btnConf.setOnClickListener(v -> {
            if (metodo[0].isEmpty()) return;
            btnConf.setEnabled(false);
            btnConf.setText("Registrando pago...");

            JSONObject body = new JSONObject();
            try {
                body.put("idViaje",              idViaje);
                body.put("idUsuario",            idPasajero);
                body.put("monto",                fMonto);
                body.put("tipoPago",             metodo[0].toUpperCase());
                body.put("estado",               "PENDIENTE");
                body.put("confirmacionPasajero", false);
                body.put("confirmacionConductor",false);
            } catch (Exception ignored) {}

            // Verificar si ya existe un pago previo
            ConexionApi.getInstance(this).getObjectNoCache(
                    Constantes.pagoDeUsuarioEnViaje(idViaje, idPasajero),
                    pagoExist -> {
                        long idPago = pagoExist.optLong("idPago",
                                pagoExist.optLong("id", -1));
                        if (idPago > 0) {
                            // Confirmar el existente
                            JSONObject bc = new JSONObject();
                            try { bc.put("confirmacionPasajero", true); }
                            catch (Exception ignored) {}
                            ConexionApi.getInstance(this).put(
                                    Constantes.pagoConfirmarPasajero(idPago), bc,
                                    r -> runOnUiThread(() -> {
                                        fSheet.dismiss();
                                        Toast.makeText(this,
                                                "✅ Pago confirmado",
                                                Toast.LENGTH_LONG).show();
                                    }),
                                    err -> runOnUiThread(() -> {
                                        fSheet.dismiss();
                                        Toast.makeText(this,
                                                "Pago registrado",
                                                Toast.LENGTH_SHORT).show();
                                    })
                            );
                        } else {
                            // Crear pago nuevo
                            ConexionApi.getInstance(this).post(Constantes.PAGOS, body,
                                    resp -> {
                                        long newId = resp.optLong("idPago",
                                                resp.optLong("id", -1));
                                        if (newId > 0) {
                                            JSONObject bc = new JSONObject();
                                            try { bc.put("confirmacionPasajero", true); }
                                            catch (Exception ignored) {}
                                            ConexionApi.getInstance(this).put(
                                                    Constantes.pagoConfirmarPasajero(newId), bc,
                                                    r -> runOnUiThread(() -> {
                                                        fSheet.dismiss();
                                                        Toast.makeText(this,
                                                                "✅ Pago confirmado — el conductor lo verá pronto",
                                                                Toast.LENGTH_LONG).show();
                                                    }),
                                                    err -> runOnUiThread(() -> {
                                                        fSheet.dismiss();
                                                        Toast.makeText(this,
                                                                "Pago registrado",
                                                                Toast.LENGTH_SHORT).show();
                                                    })
                                            );
                                        } else {
                                            runOnUiThread(() -> {
                                                fSheet.dismiss();
                                                Toast.makeText(this, "Pago registrado",
                                                        Toast.LENGTH_SHORT).show();
                                            });
                                        }
                                    },
                                    errCreate -> runOnUiThread(() -> {
                                        btnConf.setEnabled(true);
                                        btnConf.setText("CONFIRMAR QUE PAGUÉ");
                                        Toast.makeText(this,
                                                "Error al registrar el pago",
                                                Toast.LENGTH_SHORT).show();
                                    })
                            );
                        }
                    },
                    errVerif -> {
                        // No existe → crear
                        ConexionApi.getInstance(this).post(Constantes.PAGOS, body,
                                resp -> {
                                    long newId = resp.optLong("idPago",
                                            resp.optLong("id", -1));
                                    if (newId > 0) {
                                        JSONObject bc = new JSONObject();
                                        try { bc.put("confirmacionPasajero", true); }
                                        catch (Exception ignored) {}
                                        ConexionApi.getInstance(this).put(
                                                Constantes.pagoConfirmarPasajero(newId), bc,
                                                r -> runOnUiThread(() -> {
                                                    fSheet.dismiss();
                                                    Toast.makeText(this,
                                                            "✅ Pago confirmado",
                                                            Toast.LENGTH_LONG).show();
                                                }),
                                                err -> runOnUiThread(() -> {
                                                    fSheet.dismiss();
                                                    Toast.makeText(this, "Pago registrado",
                                                            Toast.LENGTH_SHORT).show();
                                                })
                                        );
                                    } else {
                                        runOnUiThread(() -> {
                                            fSheet.dismiss();
                                            Toast.makeText(this, "Pago registrado",
                                                    Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                },
                                errCreate -> runOnUiThread(() -> {
                                    btnConf.setEnabled(true);
                                    btnConf.setText("CONFIRMAR QUE PAGUÉ");
                                    Toast.makeText(this, "Error al registrar el pago",
                                            Toast.LENGTH_SHORT).show();
                                })
                        );
                    }
            );
        });

        sheet.setContentView(sv);
        sheet.show();
    }

    // =========================================================================
//  SHEET COBRO DEL VIAJE — llamado desde el mapa al finalizar
// =========================================================================

    private void mostrarSheetCobroViaje() {
        if (isFinishing() || isDestroyed() || idViaje <= 0) return;

        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viajeObj -> {
                    double montoFallback = viajeObj.optDouble("precio", 0);
                    if (montoFallback <= 0) {
                        JSONObject ruta = viajeObj.optJSONObject("ruta");
                        if (ruta != null)
                            montoFallback = ruta.optDouble("precio",
                                    ruta.optDouble("costoCombustible", 0));
                    }
                    final double fMontoFallback = montoFallback;

                    java.util.ArrayList<JSONObject> listaPasajeros = new java.util.ArrayList<>();
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    if (usuarios != null) {
                        for (int i = 0; i < usuarios.length(); i++) {
                            JSONObject u = usuarios.optJSONObject(i);
                            if (u == null) continue;
                            String est = u.optString("estado", "").toUpperCase();
                            if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;
                            listaPasajeros.add(u);
                        }
                    }

                    if (listaPasajeros.isEmpty()) {
                        runOnUiThread(() -> irAResumenViaje());
                        return;
                    }

                    runOnUiThread(() ->
                            construirSheetCobroMapa(listaPasajeros, fMontoFallback));
                },
                err -> runOnUiThread(() -> irAResumenViaje())
        );
    }

    private void construirSheetCobroMapa(java.util.ArrayList<JSONObject> pasajeros,
                                         double montoBase) {
        if (isFinishing() || isDestroyed()) return;

        BottomSheetDialog sheet = new BottomSheetDialog(this,
                com.google.android.material.R.style.Theme_MaterialComponents_BottomSheetDialog);

        float d   = density();
        int p16   = px(16), p12 = px(12), p8 = px(8), p6 = px(6), p4 = px(4);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p16, p12, p16, px(32));
        sv.addView(root);

        // ── Tirón ────────────────────────────────────────────────────────────
        View tiron = new View(this);
        LinearLayout.LayoutParams lpT =
                new LinearLayout.LayoutParams(px(40), px(4));
        lpT.gravity      = Gravity.CENTER_HORIZONTAL;
        lpT.bottomMargin = p12;
        tiron.setLayoutParams(lpT);
        GradientDrawable tBg = new GradientDrawable();
        tBg.setShape(GradientDrawable.RECTANGLE);
        tBg.setCornerRadius(px(4));
        tBg.setColor(Color.parseColor("#BDBDBD"));
        tiron.setBackground(tBg);
        root.addView(tiron);

        // ── Título ───────────────────────────────────────────────────────────
        TextView tvTitulo = new TextView(this);
        tvTitulo.setText("Cobro del viaje");
        tvTitulo.setTextSize(20f);
        tvTitulo.setTypeface(null, Typeface.BOLD);
        tvTitulo.setTextColor(Color.parseColor("#004D40"));
        LinearLayout.LayoutParams lpTit = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTit.bottomMargin = p4;
        tvTitulo.setLayoutParams(lpTit);
        root.addView(tvTitulo);

        // ── Subtítulo ─────────────────────────────────────────────────────────
        TextView tvSub = new TextView(this);
        tvSub.setText("Confirma el pago de cada pasajero antes de calificar");
        tvSub.setTextSize(13f);
        tvSub.setTextColor(Color.parseColor("#546E7A"));
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSub.bottomMargin = p16;
        tvSub.setLayoutParams(lpSub);
        root.addView(tvSub);

        // ── Separador ─────────────────────────────────────────────────────────
        View sep = new View(this);
        LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(1));
        lpSep.bottomMargin = p12;
        sep.setLayoutParams(lpSep);
        sep.setBackgroundColor(Color.parseColor("#E0F2F1"));
        root.addView(sep);

        // ── Botón "Continuar a calificar" (se habilita cuando todos paguen) ──
        MaterialButton btnContinuar = new MaterialButton(this);
        btnContinuar.setText("CONTINUAR A CALIFICAR");
        btnContinuar.setTextSize(15f);
        btnContinuar.setTextColor(Color.WHITE);
        btnContinuar.setEnabled(false);
        btnContinuar.setAlpha(0.5f);
        btnContinuar.setBackgroundColor(Color.parseColor("#B0BEC5"));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(54));
        lpBtn.topMargin = p16;
        btnContinuar.setLayoutParams(lpBtn);
        btnContinuar.setCornerRadius(px(14));

        final int[] pagosConfirmados = {0};
        final int   totalPasajeros   = pasajeros.size();
        java.text.NumberFormat nf    =
                java.text.NumberFormat.getNumberInstance(new java.util.Locale("es", "CO"));

        for (int i = 0; i < pasajeros.size(); i++) {
            JSONObject u           = pasajeros.get(i);
            int        idPasajero  = -1;
            String     nomPasajero = "Pasajero";

            JSONObject usuObj = u.optJSONObject("usuario");
            if (usuObj == null) usuObj = u.optJSONObject("pasajero");
            if (usuObj != null) {
                for (String k : new String[]{"idUsuarios", "id", "idUsuario"}) {
                    int id = usuObj.optInt(k, -1);
                    if (id > 0) { idPasajero = id; break; }
                }
                for (String k : new String[]{"nombre", "nombreCompleto", "nombres"}) {
                    String n = usuObj.optString(k, "");
                    if (!n.isEmpty() && !n.equals("null")) { nomPasajero = n; break; }
                }
                if (nomPasajero.equals("Pasajero")) {
                    String n = usuObj.optString("nombres", ""),
                            a = usuObj.optString("apellidos", "");
                    if (!n.isEmpty() || !a.isEmpty()) nomPasajero = (n + " " + a).trim();
                }
            }
            if (idPasajero <= 0)
                for (String k : new String[]{"idUsuarios", "idUsuario", "idPasajero"}) {
                    int id = u.optInt(k, -1);
                    if (id > 0) { idPasajero = id; break; }
                }

            // Monto del tramo del pasajero
            double montoP = u.optDouble("precioFinal",
                    u.optDouble("precioTramo",
                            u.optDouble("precio",
                                    u.optDouble("costoPorPasajero", montoBase))));
            if (montoP <= 0) montoP = montoBase;
            final double fMontoP     = montoP;
            final int    fIdPasajero = idPasajero;
            final String fNomPasajero = nomPasajero;

            // ── Card por pasajero ─────────────────────────────────────────────
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpCard.bottomMargin = p8;
            card.setLayoutParams(lpCard);
            card.setRadius(px(16));
            card.setCardElevation(0);
            card.setCardBackgroundColor(Color.parseColor("#FAFAFA"));
            card.setStrokeWidth(px(1));
            card.setStrokeColor(Color.parseColor("#E0E0E0"));

            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(p12, p12, p12, p12);

            // Fila avatar + nombre + monto + badge
            LinearLayout filaNom = new LinearLayout(this);
            filaNom.setOrientation(LinearLayout.HORIZONTAL);
            filaNom.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpFN = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpFN.bottomMargin = p8;
            filaNom.setLayoutParams(lpFN);

            // Avatar
            TextView tvAv = new TextView(this);
            LinearLayout.LayoutParams lpAv =
                    new LinearLayout.LayoutParams(px(36), px(36));
            lpAv.rightMargin = p8;
            tvAv.setLayoutParams(lpAv);
            tvAv.setGravity(Gravity.CENTER);
            tvAv.setText(nomPasajero.isEmpty() ? "P"
                    : nomPasajero.substring(0, 1).toUpperCase());
            tvAv.setTextColor(Color.WHITE);
            tvAv.setTextSize(15f);
            tvAv.setTypeface(null, Typeface.BOLD);
            int[] avCols = {0xFF009688, 0xFF1565C0, 0xFFE65100, 0xFF6A1B9A};
            GradientDrawable avBg = new GradientDrawable();
            avBg.setShape(GradientDrawable.OVAL);
            avBg.setColor(avCols[i % avCols.length]);
            tvAv.setBackground(avBg);
            filaNom.addView(tvAv);

            // Col info
            LinearLayout colInfo = new LinearLayout(this);
            colInfo.setOrientation(LinearLayout.VERTICAL);
            colInfo.setLayoutParams(
                    new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView tvNom = new TextView(this);
            tvNom.setText(nomPasajero);
            tvNom.setTextSize(14.5f);
            tvNom.setTypeface(null, Typeface.BOLD);
            tvNom.setTextColor(Color.parseColor("#1A2035"));
            colInfo.addView(tvNom);
            TextView tvMonto = new TextView(this);
            tvMonto.setText("$" + nf.format(fMontoP) + " COP");
            tvMonto.setTextSize(12f);
            tvMonto.setTextColor(Color.parseColor("#00897B"));
            tvMonto.setTypeface(null, Typeface.BOLD);
            colInfo.addView(tvMonto);
            filaNom.addView(colInfo);

            // Badge estado
            TextView tvBadge = new TextView(this);
            tvBadge.setText("Pendiente");
            tvBadge.setTextSize(10f);
            tvBadge.setTextColor(Color.WHITE);
            tvBadge.setTypeface(null, Typeface.BOLD);
            tvBadge.setPadding(p6, px(3), p6, px(3));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            badgeBg.setCornerRadius(px(20));
            badgeBg.setColor(Color.parseColor("#F57F17"));
            tvBadge.setBackground(badgeBg);
            filaNom.addView(tvBadge);
            inner.addView(filaNom);

            // Label método
            TextView tvMetLabel = new TextView(this);
            tvMetLabel.setText("¿Cómo te pagó?");
            tvMetLabel.setTextSize(12f);
            tvMetLabel.setTextColor(Color.parseColor("#546E7A"));
            LinearLayout.LayoutParams lpML = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpML.bottomMargin = p6;
            tvMetLabel.setLayoutParams(lpML);
            inner.addView(tvMetLabel);

            // Botones método
            final String[] metodo = {""};
            LinearLayout filaMetodos = new LinearLayout(this);
            filaMetodos.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lpFM = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpFM.bottomMargin = p8;
            filaMetodos.setLayoutParams(lpFM);

            MaterialButton btnEfectivo = new MaterialButton(this);
            btnEfectivo.setText("Efectivo");
            btnEfectivo.setTextSize(12f);
            btnEfectivo.setTextColor(Color.parseColor("#004D40"));
            btnEfectivo.setCornerRadius(px(10));
            btnEfectivo.setBackgroundColor(Color.parseColor("#E0F2F1"));
            LinearLayout.LayoutParams lpBE =
                    new LinearLayout.LayoutParams(0, px(40), 1f);
            lpBE.rightMargin = p6;
            btnEfectivo.setLayoutParams(lpBE);

            MaterialButton btnTransfer = new MaterialButton(this);
            btnTransfer.setText("Transferencia");
            btnTransfer.setTextSize(12f);
            btnTransfer.setTextColor(Color.parseColor("#004D40"));
            btnTransfer.setCornerRadius(px(10));
            btnTransfer.setBackgroundColor(Color.parseColor("#E0F2F1"));
            btnTransfer.setLayoutParams(
                    new LinearLayout.LayoutParams(0, px(40), 1f));
            filaMetodos.addView(btnEfectivo);
            filaMetodos.addView(btnTransfer);
            inner.addView(filaMetodos);

            // Botón confirmar
            MaterialButton btnConf = new MaterialButton(this);
            btnConf.setText("CONFIRMAR PAGO RECIBIDO");
            btnConf.setTextSize(13f);
            btnConf.setTextColor(Color.WHITE);
            btnConf.setCornerRadius(px(12));
            btnConf.setBackgroundColor(Color.parseColor("#B0BEC5"));
            btnConf.setEnabled(false);
            btnConf.setAlpha(0.5f);
            btnConf.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, px(46)));

            // Lógica selección método
            final MaterialCardView fCard   = card;
            final TextView         fBadge  = tvBadge;
            final GradientDrawable fBgBadge = badgeBg;
            final GradientDrawable fAvBg   = avBg;
            final TextView         fAvTv   = tvAv;

            Runnable actualizarMetodo = () -> {
                boolean esEf = "efectivo".equals(metodo[0]);
                boolean esTr = "transferencia".equals(metodo[0]);
                btnEfectivo.setBackgroundColor(
                        esEf ? Color.parseColor("#00897B") : Color.parseColor("#E0F2F1"));
                btnEfectivo.setTextColor(
                        esEf ? Color.WHITE : Color.parseColor("#004D40"));
                btnTransfer.setBackgroundColor(
                        esTr ? Color.parseColor("#00897B") : Color.parseColor("#E0F2F1"));
                btnTransfer.setTextColor(
                        esTr ? Color.WHITE : Color.parseColor("#004D40"));
                boolean hay = !metodo[0].isEmpty();
                btnConf.setEnabled(hay);
                btnConf.setAlpha(hay ? 1f : 0.5f);
                btnConf.setBackgroundColor(
                        hay ? Color.parseColor("#00897B") : Color.parseColor("#B0BEC5"));
            };
            btnEfectivo.setOnClickListener(v -> { metodo[0] = "efectivo";      actualizarMetodo.run(); });
            btnTransfer.setOnClickListener(v -> { metodo[0] = "transferencia"; actualizarMetodo.run(); });

            btnConf.setOnClickListener(v -> {
                if (metodo[0].isEmpty()) return;
                btnConf.setEnabled(false);
                btnConf.setText("Verificando...");

                // Verificar si el pasajero ya confirmó su pago
                ConexionApi.getInstance(this).getObjectNoCache(
                        Constantes.pagoDeUsuarioEnViaje(idViaje, fIdPasajero),
                        pagoFound -> {
                            long    idPago     = pagoFound.optLong("idPago",
                                    pagoFound.optLong("id", -1));
                            boolean pasConfirmo = pagoFound.optBoolean(
                                    "confirmacionPasajero", false);

                            if (idPago > 0 && pasConfirmo) {
                                // Pasajero ya confirmó → confirmar lado conductor
                                JSONObject bodyC = new JSONObject();
                                try { bodyC.put("confirmacionConductor", true); }
                                catch (Exception ignored) {}
                                ConexionApi.getInstance(this).put(
                                        Constantes.pagoConfirmarConductor(idPago), bodyC,
                                        r -> runOnUiThread(() ->
                                                marcarCardPagadoEnMapa(fCard, fBadge, fBgBadge,
                                                        fAvTv, fAvBg, tvMetLabel,
                                                        btnEfectivo, btnTransfer, btnConf,
                                                        pagosConfirmados, totalPasajeros,
                                                        btnContinuar)),
                                        err -> runOnUiThread(() -> {
                                            btnConf.setEnabled(true);
                                            btnConf.setText("CONFIRMAR PAGO RECIBIDO");
                                            Toast.makeText(this,
                                                    "Error al confirmar el pago",
                                                    Toast.LENGTH_SHORT).show();
                                        })
                                );
                            } else {
                                // Pasajero aún no confirma
                                runOnUiThread(() -> {
                                    btnConf.setEnabled(true);
                                    btnConf.setText("CONFIRMAR PAGO RECIBIDO");
                                    fBadge.setText("Esperando pasajero");
                                    fBgBadge.setColor(Color.parseColor("#F57F17"));
                                    fBadge.setBackground(fBgBadge);
                                    Toast.makeText(this,
                                            "⏳ El pasajero aún no ha confirmado. Pídele que lo confirme desde su app.",
                                            Toast.LENGTH_LONG).show();
                                });
                            }
                        },
                        errVerif -> runOnUiThread(() -> {
                            btnConf.setEnabled(true);
                            btnConf.setText("CONFIRMAR PAGO RECIBIDO");
                            Toast.makeText(this,
                                    "⏳ El pasajero aún no ha registrado su pago.",
                                    Toast.LENGTH_LONG).show();
                        })
                );
            });
            inner.addView(btnConf);
            card.addView(inner);
            root.addView(card);
        }

        // Nota al pie
        TextView tvNota = new TextView(this);
        tvNota.setText("Una vez confirmados los pagos podrás calificar a los pasajeros");
        tvNota.setTextSize(11.5f);
        tvNota.setTextColor(Color.parseColor("#78909C"));
        LinearLayout.LayoutParams lpNota = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpNota.topMargin = p8;
        tvNota.setLayoutParams(lpNota);
        root.addView(tvNota);
        root.addView(btnContinuar);

        // Botón saltar
        MaterialButton btnSaltar = new MaterialButton(this);
        btnSaltar.setText("Saltar y calificar directamente");
        btnSaltar.setTextSize(13f);
        btnSaltar.setTextColor(Color.parseColor("#78909C"));
        btnSaltar.setBackgroundColor(Color.TRANSPARENT);
        btnSaltar.setStrokeWidth(0);
        LinearLayout.LayoutParams lpSalt = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(44));
        lpSalt.topMargin = p4;
        btnSaltar.setLayoutParams(lpSalt);
        root.addView(btnSaltar);

        final BottomSheetDialog fSheet = sheet;
        btnContinuar.setOnClickListener(v -> {
            fSheet.dismiss();
            new Handler(Looper.getMainLooper()).postDelayed(
                    this::buscarPasajerosYCalificarMapa, 400);
        });
        btnSaltar.setOnClickListener(v -> {
            fSheet.dismiss();
            new Handler(Looper.getMainLooper()).postDelayed(
                    this::buscarPasajerosYCalificarMapa, 400);
        });

        sheet.setContentView(sv);
        sheet.show();
    }

    private void marcarCardPagadoEnMapa(MaterialCardView card, TextView tvBadge,
                                        GradientDrawable badgeBg, TextView tvAv,
                                        GradientDrawable avBg, TextView tvMetLabel,
                                        MaterialButton btnEf, MaterialButton btnTr,
                                        MaterialButton btnConf,
                                        int[] pagosConf, int total,
                                        MaterialButton btnContinuar) {
        card.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
        card.setStrokeColor(Color.parseColor("#A5D6A7"));
        tvBadge.setText("Pagado ✓");
        badgeBg.setColor(Color.parseColor("#2E7D32"));
        tvBadge.setBackground(badgeBg);
        avBg.setColor(0xFF2E7D32);
        tvAv.setBackground(avBg);
        btnEf.setVisibility(View.GONE);
        btnTr.setVisibility(View.GONE);
        btnConf.setVisibility(View.GONE);
        tvMetLabel.setVisibility(View.GONE);
        pagosConf[0]++;
        if (pagosConf[0] >= total) {
            btnContinuar.setEnabled(true);
            btnContinuar.setAlpha(1f);
            btnContinuar.setBackgroundColor(Color.parseColor("#00897B"));
        }
    }

    private void abrirPagoDesdeProximidad() {
        if (isFinishing() || isDestroyed()) return;
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viajeObj -> {
                    double monto = viajeObj.optDouble("precio",0);
                    if (monto <= 0) {
                        JSONObject ruta = viajeObj.optJSONObject("ruta");
                        if (ruta != null) monto = ruta.optDouble("precio",ruta.optDouble("costoCombustible",0));
                    }
                    final double fMonto = monto;
                    runOnUiThread(() -> lanzarPago(fMonto));
                },
                err -> runOnUiThread(() -> lanzarPago(0.0)));
    }

    // =========================================================================
    //  BANNER FLOTANTE
    // =========================================================================

    private void mostrarBannerEstado(String mensaje, int colorFondo) {
        FrameLayout root = findViewById(android.R.id.content);
        View anterior = root.findViewWithTag("banner_estado");
        if (anterior != null) root.removeView(anterior);

        LinearLayout banner = new LinearLayout(this);
        banner.setTag("banner_estado");
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(px(20),px(14),px(20),px(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(colorFondo); bg.setCornerRadius(px(28));
        banner.setBackground(bg);
        banner.setElevation(16*density());

        TextView tv = new TextView(this);
        tv.setText(mensaje); tv.setTextColor(Color.WHITE);
        tv.setTextSize(14f); tv.setTypeface(null, Typeface.BOLD);
        banner.addView(tv);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        View hud = root.findViewWithTag("hud_banner");
        int offsetY = hud != null ? hud.getHeight()+px(12) : px(130);
        lp.gravity   = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = offsetY;
        banner.setLayoutParams(lp);

        banner.setAlpha(0f); banner.setScaleX(0.85f); banner.setScaleY(0.85f);
        root.addView(banner);
        banner.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (banner.getParent() == null) return;
            banner.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(240)
                    .withEndAction(() -> { if (banner.getParent() != null)
                        ((FrameLayout)banner.getParent()).removeView(banner); }).start();
        }, 3500);
    }

    // =========================================================================
    //  PULSO ANIMADO EN MARCADOR
    // =========================================================================

    private void iniciarPulsoMarcador(Marker marcador) {
        if (marcador == null) return;
        if (pulsoAnimator != null) pulsoAnimator.cancel();
        pulsoAnimator = ValueAnimator.ofFloat(0.8f,1.3f,0.8f);
        pulsoAnimator.setDuration(900); pulsoAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulsoAnimator.addUpdateListener(a -> {
            float scale = (float)a.getAnimatedValue();
            Bitmap bmp = pinConHaloPulsante(0xFF43A047,"!",scale);
            runOnUiThread(() -> { marcador.setIcon(new BitmapDrawable(getResources(),bmp)); map.invalidate(); });
        });
        pulsoAnimator.start();
    }

    private Bitmap pinConHaloPulsante(int color, String texto, float haloScale) {
        int sz=120,r=36,cx=sz/2,cy=r+14,colaH=18;
        Bitmap bmp = Bitmap.createBitmap(sz,cy+r+colaH+20,Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint ph = new Paint(Paint.ANTI_ALIAS_FLAG); ph.setColor(Color.argb((int)(80*(2f-haloScale)),67,160,71));
        cv.drawCircle(cx,cy,(int)(r*haloScale)+10,ph);
        Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG); ps.setColor(Color.argb(65,0,0,0));
        cv.drawCircle(cx+3,cy+5,r-2,ps);
        Paint pf = new Paint(Paint.ANTI_ALIAS_FLAG); pf.setColor(color);
        cv.drawCircle(cx,cy,r,pf);
        Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG); pb.setColor(Color.WHITE); pb.setStyle(Paint.Style.STROKE); pb.setStrokeWidth(4.5f);
        cv.drawCircle(cx,cy,r,pb);
        Paint pc = new Paint(Paint.ANTI_ALIAS_FLAG); pc.setColor(color);
        Path cola = new Path(); cola.moveTo(cx-10,cy+r-3); cola.lineTo(cx+10,cy+r-3); cola.lineTo(cx,cy+r+colaH); cola.close();
        cv.drawPath(cola,pc);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG); pt.setColor(Color.WHITE); pt.setTextSize(28f); pt.setTypeface(Typeface.DEFAULT_BOLD); pt.setTextAlign(Paint.Align.CENTER);
        cv.drawText(texto,cx,cy-(pt.descent()+pt.ascent())/2,pt);
        return bmp;
    }

    // =========================================================================
    //  ANIMACIÓN DESAPARICIÓN MARCADOR
    // =========================================================================

    private void animarDesaparicionMarcador(Marker marcador, Runnable onFin) {
        if (marcador == null) { if (onFin != null) onFin.run(); return; }
        if (pulsoAnimator != null) { pulsoAnimator.cancel(); pulsoAnimator = null; }
        ValueAnimator anim = ValueAnimator.ofFloat(1f,0f);
        anim.setDuration(500); anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            float alpha = (float)a.getAnimatedValue();
            Bitmap bmp  = pinConCola(0xFF43A047,"✓");
            Bitmap fade = Bitmap.createBitmap(bmp.getWidth(),bmp.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas cv2  = new Canvas(fade); Paint p2 = new Paint(); p2.setAlpha((int)(255*alpha));
            cv2.drawBitmap(bmp,0,0,p2); bmp.recycle();
            runOnUiThread(() -> { marcador.setIcon(new BitmapDrawable(getResources(),fade)); map.invalidate(); });
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                runOnUiThread(() -> { map.getOverlays().remove(marcador); map.invalidate(); if (onFin != null) onFin.run(); });
            }
        });
        anim.start();
    }

    // =========================================================================
    //  FINALIZAR VIAJE — CONDUCTOR
    // =========================================================================

    private void confirmarFinalizarManual() {
        if (viajeYaFinalizado) { Toast.makeText(this,"El viaje ya fue finalizado",Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this)
                .setTitle("¿Finalizar el viaje?")
                .setMessage("Los pasajeros serán notificados y podrán registrar su pago.")
                .setPositiveButton("Finalizar",(d,w)-> { viajeYaFinalizado=true; ejecutarFinalizarViaje(); })
                .setNegativeButton("Cancelar",null).show();
    }

    private void verificarLlegadaAlDestino(GeoPoint pos) {
        if (viajeYaFinalizado || !desdeViajeActivo || idViaje<=0 || pDestino==null) return;
        if (!session.isConductor()) return;
        if (!pasajeros.isEmpty()) return;
        double dist = calcularDistanciaMetros(pos, pDestino);
        if (dist <= 50) {
            viajeYaFinalizado = true;
            runOnUiThread(() -> {
                Toast.makeText(this,"Llegaste al destino — finalizando viaje...",Toast.LENGTH_SHORT).show();
                ejecutarFinalizarViaje();
            });
        }
    }

    private void ejecutarFinalizarViaje() {
        if (fusedClient != null && locationCallback != null)
            fusedClient.removeLocationUpdates(locationCallback);
        if (rPoll != null) hPoll.removeCallbacks(rPoll);
        Toast.makeText(this,"⏳ Finalizando viaje…",Toast.LENGTH_SHORT).show();
        ConexionApi.getInstance(this).post(
                Constantes.BASE_URL+"/api/viajes/"+idViaje+"/pasajeros-bajaron", null,
                r1 -> paso2FinalizarMapa(),
                e1 -> paso2FinalizarMapa());
    }

    private void paso2FinalizarMapa() {
        ConexionApi.getInstance(this).post(Constantes.viajeFinalizar((long) idViaje), null,
                r2 -> runOnUiThread(() -> {
                    Toast.makeText(this, "✅ Viaje finalizado", Toast.LENGTH_LONG).show();
                    notificarFinViajeAFirebase();
                    limpiarLineas(lineasPintadas);
                    limpiarLineas(lineasEta);
                    restaurarUiNormal();
                    // ── Mostrar sheet de cobro antes de calificar ──
                    new Handler(Looper.getMainLooper()).postDelayed(
                            this::mostrarSheetCobroViaje, 800);
                }),
                e2 -> runOnUiThread(() -> {
                    Toast.makeText(this,
                            "❌ Error al finalizar. Inténtalo de nuevo.",
                            Toast.LENGTH_LONG).show();
                    viajeYaFinalizado = false;
                }));
    }

    private void notificarFinViajeAFirebase() {
        try {
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("viajes_estado").child("viaje_"+idViaje);
            ref.child("estado").setValue("FINALIZADO");
            ref.child("timestamp").setValue(System.currentTimeMillis());
        } catch (Exception e) { Log.w(TAG,"firebase fin: "+e.getMessage()); }
    }

    private void buscarPasajerosYCalificarMapa() {
        int idConductor = session.getIdUsuario();
        ConexionApi.getInstance(this).getObjectNoCache(Constantes.viajePorId((long)idViaje),
                viajeObj -> {
                    ArrayList<Integer> ids     = new ArrayList<>();
                    ArrayList<String>  nombres = new ArrayList<>();
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    if (usuarios != null) {
                        for (int i = 0; i < usuarios.length(); i++) {
                            JSONObject u = usuarios.optJSONObject(i); if (u==null) continue;
                            String est = u.optString("estado","").toUpperCase();
                            if ("CANCELADO".equals(est)||"CANCELADA".equals(est)) continue;
                            int idP=-1; String nomP="";
                            JSONObject uo = u.optJSONObject("usuario");
                            if (uo != null) {
                                for (String k : new String[]{"idUsuarios","id","idUsuario"}) { int id=uo.optInt(k,-1); if(id>0){idP=id;break;} }
                                for (String k : new String[]{"nombre","nombreCompleto","name","nombres"}) { String n=uo.optString(k,""); if(!n.isEmpty()&&!n.equals("null")){nomP=n;break;} }
                                if (nomP.isEmpty()) nomP = (uo.optString("nombres","")+" "+uo.optString("apellidos","")).trim();
                            }
                            if (idP<=0) for (String k : new String[]{"idUsuarios","idUsuario","idPasajero"}) { int id=u.optInt(k,-1); if(id>0){idP=id;break;} }
                            if (idP>0&&idP!=idConductor) { ids.add(idP); nombres.add(nomP.isEmpty()?"Pasajero":nomP); }
                        }
                    }
                    runOnUiThread(() -> {
                        if (!ids.isEmpty()) calificarEncadenadoMapa(ids,nombres,idConductor,0);
                        else irAResumenViaje();
                    });
                },
                err -> runOnUiThread(this::irAResumenViaje));
    }

    private void calificarEncadenadoMapa(ArrayList<Integer> ids, ArrayList<String> nombres,
                                         int idConductor, int indice) {
        if (indice >= ids.size()) { irAResumenViaje(); return; }
        int idP=ids.get(indice); String nomP=nombres.get(indice); int sig=indice+1;
        new CalificacionesManager(this).verificarCalificacion(idViaje, idConductor, idP,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override public void onDebeCalificar() {
                        CalificacionController.mostrarBottomSheetCalificar(
                                Mapa.this, idViaje, idP, nomP,
                                "",
                                idConductor, true,
                                (pun,com2) -> new Handler(Looper.getMainLooper())
                                        .postDelayed(() -> calificarEncadenadoMapa(ids,nombres,idConductor,sig), 600));
                    }
                    @Override public void onYaCalifico(int p, String e) {
                        calificarEncadenadoMapa(ids,nombres,idConductor,sig);
                    }
                });
    }

    private void irAResumenViaje() {
        if (isFinishing() || isDestroyed()) return;
        Intent i = new Intent(this, ResumenViajeActivity.class);
        i.putExtra(ResumenViajeActivity.EXTRA_ID_VIAJE, idViaje);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i); finish();
    }

    // =========================================================================
    //  POLLING GPS CONDUCTOR (vista pasajero)
    // =========================================================================

    private void arrancarPollingConductor() {
        rPoll = new Runnable() {
            @Override public void run() {
                if (!isSocketConnected) fetchUbicacionConductor();
                hPoll.postDelayed(this, 3000);
            }
        };
        hPoll.postDelayed(rPoll, 2000);
    }

    private void fetchUbicacionConductor() {
        String url = Constantes.BASE_URL+"/api/viajes/"+idViaje
                +"/ubicacion-conductor?t="+System.currentTimeMillis();
        ConexionApi.getInstance(this).getObjectNoCache(url,
                resp -> {
                    double lat = resp.optDouble("lat", resp.optDouble("latitud",Double.NaN));
                    double lng = resp.optDouble("lng", resp.optDouble("longitud",Double.NaN));
                    if (!Double.isNaN(lat) && lat!=0 && !Double.isNaN(lng) && lng!=0) {
                        GeoPoint nuevaPos = new GeoPoint(lat,lng);
                        runOnUiThread(() -> { animarMarcadorConductor(nuevaPos); actualizarLineaNaranja(nuevaPos); });
                        calcularEtaConductor(nuevaPos);
                        verificarLlegadaBajadaPasajero(nuevaPos);
                    }
                },
                err -> Log.w(TAG,"GPS conductor no disponible"));
    }

    // =========================================================================
    //  ANIMACIÓN MARCADOR CONDUCTOR RT
    // =========================================================================

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
            marcadorConductorRT.setTitle(" "+nomConductor);
            marcadorConductorRT.setIcon(new BitmapDrawable(getResources(),crearIconoCarro(COL_CONDUCT, rumboConductor)));
            marcadorConductorRT.setPosition(nuevaPos);
            map.getOverlays().add(marcadorConductorRT); map.invalidate(); return;
        }

        GeoPoint ini = marcadorConductorRT.getPosition();
        if (ini == null) {
            marcadorConductorRT.setPosition(nuevaPos);
            marcadorConductorRT.setIcon(new BitmapDrawable(getResources(), crearIconoCarro(COL_CONDUCT,rumboConductor)));
            map.invalidate(); return;
        }

        if (Math.abs(nuevaPos.getLatitude()-ini.getLatitude())  < 0.000010
                && Math.abs(nuevaPos.getLongitude()-ini.getLongitude()) < 0.000010) {
            marcadorConductorRT.setIcon(new BitmapDrawable(getResources(), crearIconoCarro(COL_CONDUCT,rumboConductor)));
            map.invalidate(); return;
        }

        final float rumboFinal = rumboConductor;
        ValueAnimator anim = ValueAnimator.ofFloat(0f,1f);
        anim.setDuration(1800); anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            float t = (float)a.getAnimatedValue();
            marcadorConductorRT.setPosition(new GeoPoint(
                    ini.getLatitude()  + t*(nuevaPos.getLatitude() -ini.getLatitude()),
                    ini.getLongitude() + t*(nuevaPos.getLongitude()-ini.getLongitude())));
            if (Math.round(t*100)%15==0)
                marcadorConductorRT.setIcon(new BitmapDrawable(getResources(), crearIconoCarro(COL_CONDUCT,rumboFinal)));
            map.invalidate();
        });
        anim.start();
    }

    private void actualizarLineaNaranja(GeoPoint posConductor) {
        GeoPoint destino = null;

        if (!session.isConductor()) {
            for (PasajeroInfo p : pasajeros) {
                if (p.idUsuario == session.getIdUsuario()) {
                    destino = !p.recogido && p.pSubida != null ? p.pSubida : p.pBajada;
                    break;
                }
            }
            if (destino == null)
                destino = (!pasajeroRecogido && pSubida != null) ? pSubida : pBajada;
        } else {
            PasajeroInfo sig = pasajeroActual();
            if (sig != null && !sig.recogido && sig.pSubida != null)
                destino = sig.pSubida;
            else if (sig != null && sig.recogido && sig.pBajada != null)
                destino = sig.pBajada;
            else
                destino = (pDestino != null ? pDestino : pOrigen);
        }

        if (destino == null) return;

        final GeoPoint fPc = posConductor, fDst = destino;
        new Thread(() -> {
            ArrayList<GeoPoint> pts = osrmRuta(fPc, fDst);
            if (pts == null || pts.size() < 2) {
                pts = new ArrayList<>();
                pts.add(fPc);
                pts.add(fDst);
            }
            final ArrayList<GeoPoint> fPts = pts;
            runOnUiThread(() -> {
                limpiarLineas(lineasEta);
                agregarPolilinea(lineasEta, fPts, COL_TRAMO, 7f);
                reordenarMarcadorGps();
                map.invalidate();
            });
        }).start();
    }

    private void calcularEtaConductor(GeoPoint posConductor) {
        GeoPoint destino = null;
        for (PasajeroInfo p : pasajeros) {
            if (p.idUsuario == session.getIdUsuario()) {
                destino = p.recogido ? p.pBajada : p.pSubida; break;
            }
        }
        if (destino == null) destino = (!pasajeroRecogido && pSubida != null) ? pSubida : pBajada;
        if (destino == null) return;

        final GeoPoint fPc=posConductor, fDst=destino;
        new Thread(() -> {
            try {
                String coords = fPc.getLongitude()+","+fPc.getLatitude()+";"
                        +fDst.getLongitude()+","+fDst.getLatitude()+"?overview=false";
                String json = null;
                try { json = http(OSRM_POPAYAN+"/route/v1/driving/"+coords); } catch (Exception ig) {}
                if (json == null || json.isEmpty())
                    try { json = http(OSRM_PUBLICO+"/route/v1/driving/"+coords); } catch (Exception ig) {}
                if (json == null || json.isEmpty()) json = http(OSRM_PROPIO+"/route/v1/driving/"+coords);
                if (json == null) return;
                JSONObject obj = new JSONObject(json);
                if (!"Ok".equals(obj.optString("code"))) return;
                JSONObject ruta = obj.getJSONArray("routes").getJSONObject(0);
                double durSeg  = ruta.optDouble("duration",0);
                double distMet = ruta.optDouble("distance",0);
                int minutos = Math.max(1,(int)Math.ceil(durSeg/60.0));
                double distKm = distMet/1000.0;

                String textoEta;
                if (durSeg<60)       textoEta = "El conductor ya está llegando";
                else if (minutos<60) textoEta = "Conductor llega en "+minutos+" min";
                else { int h=minutos/60,m=minutos%60; textoEta = "Conductor llega en "+h+"h "+m+"min"; }
                String textoDist = distKm<1.0
                        ? String.format(" %.0f m de ti",distMet)
                        : String.format(" %.1f km de ti",distKm);

                final String fEta=textoEta, fDist=textoDist;
                runOnUiThread(() -> {
                    if (tvEta != null) { tvEta.setText(fEta); tvEta.setVisibility(View.VISIBLE); }
                    if (tvDistEta != null) { tvDistEta.setText(fDist); tvDistEta.setVisibility(View.VISIBLE); }
                });
            } catch (Exception e) { Log.w(TAG,"calcularEta: "+e.getMessage()); }
        }).start();
    }

    // =========================================================================
    //  GPS PROPIO DEL CONDUCTOR — con redibujo de ruta al primer fix
    // =========================================================================

    private void configurarGpsPropio() {
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERM);
            return;
        }
        LocationRequest req = LocationRequest.create();
        req.setInterval(2000); req.setFastestInterval(1000);
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult res) {
                if (res.getLastLocation() == null) return;
                double lat   = res.getLastLocation().getLatitude();
                double lon   = res.getLastLocation().getLongitude();
                float  rumbo = res.getLastLocation().getBearing();
                GeoPoint pos = new GeoPoint(lat,lon);

                if (miUltimaPosicion != null) {
                    double dLat = Math.abs(pos.getLatitude()  - miUltimaPosicion.getLatitude());
                    double dLon = Math.abs(pos.getLongitude() - miUltimaPosicion.getLongitude());
                    if (dLat < 0.000010 && dLon < 0.000010) return;
                }
                if (rumbo == 0f && miUltimaPosicion != null)
                    rumbo = calcularRumbo(miUltimaPosicion, pos);

                // ── FIX: redibujar ruta cuando llega el primer fix GPS ───────
                boolean esConductor = session.isConductor();
                if (!rutaDibujaConGps && desdeViajeActivo) {
                    // Si pOrigen era null, ahora lo tenemos
                    if (pOrigen == null && esConductor) {
                        pOrigen = pos;
                        Log.d(TAG, "GPS primer fix → pOrigen asignado: " + pos);
                    }
                    // Si las líneas están vacías y tenemos destino, redibujar
                    if (lineasPintadas.isEmpty() && pDestino != null) {
                        rutaDibujaConGps = true;
                        Log.d(TAG, "GPS primer fix → redibujar ruta");
                        runOnUiThread(() -> map.post(() -> dibujarRutaViaje(esConductor)));
                    }
                }
                // ─────────────────────────────────────────────────────────────

                miUltimaPosicion = pos;
                final float fRumbo = rumbo;

                runOnUiThread(() -> {
                    actualizarMarcadorPropio(pos, fRumbo);
                    if (esConductor) {
                        actualizarEstadoViaje(pos);
                        if (pasajeros.isEmpty()) {
                            verificarRecogidaPasajero(pos);
                            verificarLlegadaAlDestino(pos);
                        }
                        verificarPasosNavegacion(pos);
                    }
                });

                if (esConductor && desdeViajeActivo && idViaje > 0)
                    enviarUbicacionAlBackend(lat,lon);

                refFirebase.child("lat").setValue(lat);
                refFirebase.child("lng").setValue(lon);
                refFirebase.child("rumbo").setValue(rumbo);
                refFirebase.child("ts").setValue(System.currentTimeMillis());

                if (!desdeViajeActivo && destinoLat != 0 && !rutaSolicitada) {
                    rutaSolicitada = true; pedirRutasBackend(lat,lon);
                }
            }
        };
        fusedClient.requestLocationUpdates(req, locationCallback, getMainLooper());
    }

    private void actualizarMarcadorPropio(GeoPoint pos, float rumbo) {
        if (marcadorGpsPropio == null) {
            marcadorGpsPropio = new Marker(map);
            marcadorGpsPropio.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marcadorGpsPropio.setTitle(" Mi posición");
            marcadorGpsPropio.setIcon(new BitmapDrawable(getResources(), crearIconoCarro(COL_PROPIO,rumbo)));
            marcadorGpsPropio.setPosition(pos);
            map.getOverlays().add(marcadorGpsPropio);
            map.getController().animateTo(pos); map.invalidate(); return;
        }
        GeoPoint ini = marcadorGpsPropio.getPosition();
        if (ini == null) {
            marcadorGpsPropio.setPosition(pos);
            marcadorGpsPropio.setIcon(new BitmapDrawable(getResources(), crearIconoCarro(COL_PROPIO,rumbo)));
            map.invalidate(); return;
        }

        if (Math.abs(pos.getLatitude()-ini.getLatitude())  < 0.000010
                && Math.abs(pos.getLongitude()-ini.getLongitude()) < 0.000010) {
            marcadorGpsPropio.setIcon(new BitmapDrawable(getResources(), crearIconoCarro(COL_PROPIO,rumbo)));
            map.invalidate(); return;
        }

        final float fRumbo = rumbo;
        ValueAnimator anim = ValueAnimator.ofFloat(0f,1f);
        anim.setDuration(1800); anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            float t = (float)a.getAnimatedValue();
            marcadorGpsPropio.setPosition(new GeoPoint(
                    ini.getLatitude()  + t*(pos.getLatitude() -ini.getLatitude()),
                    ini.getLongitude() + t*(pos.getLongitude()-ini.getLongitude())));
            if (Math.round(t*100)%10==0)
                marcadorGpsPropio.setIcon(new BitmapDrawable(getResources(), crearIconoCarro(COL_PROPIO,fRumbo)));
            map.invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (session.isConductor()) map.getController().animateTo(pos);
            }
        });
        anim.start();
    }

    // =========================================================================
    //  GUÍA DE VOZ
    // =========================================================================

    private void verificarPasosNavegacion(GeoPoint miPos) {
        if (!session.isConductor() || currentSteps==null || nextStepIndex>=currentSteps.length()) return;
        try {
            JSONObject step = currentSteps.getJSONObject(nextStepIndex);
            JSONObject maneuver = step.getJSONObject("maneuver");
            JSONArray locArr = maneuver.getJSONArray("location");
            GeoPoint pPaso = new GeoPoint(locArr.getDouble(1), locArr.getDouble(0));
            double dist = calcularDistanciaMetros(miPos, pPaso);
            if (dist < 70 && System.currentTimeMillis() - lastVoiceTime > 15000) {
                String instruction = step.optString("navigation_instruction","");
                if (instruction.isEmpty()) {
                    String type     = maneuver.optString("type");
                    String modifier = maneuver.optString("modifier","");
                    String name     = step.optString("name","");
                    instruction = "En "+(int)dist+" metros ";
                    if (modifier.contains("right"))        instruction += "gira a la derecha";
                    else if (modifier.contains("left"))    instruction += "gira a la izquierda";
                    else if (type.contains("arrive"))      instruction += "llegarás a tu destino";
                    else                                   instruction += "continúa";
                    if (!name.isEmpty()) instruction += " por "+name;
                }
                if (voiceAssistant != null) voiceAssistant.hablar(instruction);
                lastVoiceTime = System.currentTimeMillis();
                nextStepIndex++;
            }
        } catch (Exception e) { Log.e(TAG,"Error guía de voz: "+e.getMessage()); }
    }

    // =========================================================================
    //  SOCKET.IO
    // =========================================================================

    private void inicializarSocket() {
        try {
            IO.Options options = new IO.Options();
            options.query = "token="+session.getToken();
            mSocket = IO.socket(Constantes.BASE_URL, options);

            mSocket.on(Socket.EVENT_CONNECT, args -> {
                Log.d(TAG,"Socket conectado"); isSocketConnected = true;
                try {
                    JSONObject joinData = new JSONObject(); joinData.put("idViaje",idViaje);
                    mSocket.emit("join_trip", joinData);
                } catch (Exception e) { e.printStackTrace(); }
            });

            mSocket.on(Socket.EVENT_DISCONNECT, args -> {
                Log.d(TAG,"Socket desconectado"); isSocketConnected = false;
            });

            mSocket.on("location_updated", args -> {
                if (session.isConductor() || args.length==0) return;
                JSONObject data = (JSONObject) args[0];
                double lat = data.optDouble("lat"), lng = data.optDouble("lng");
                GeoPoint nuevaPos = new GeoPoint(lat,lng);
                runOnUiThread(() -> { animarMarcadorConductor(nuevaPos); actualizarLineaNaranja(nuevaPos); });
                calcularEtaConductor(nuevaPos);
                verificarLlegadaBajadaPasajero(nuevaPos);
            });

            mSocket.on("trip_event", args -> {
                if (args.length == 0) return;
                try {
                    JSONObject data = (JSONObject) args[0];
                    String evento   = data.optString("evento","");
                    int idPasajero  = data.optInt("idPasajero",-1);
                    if ("pasajero_recogido".equals(evento)) {
                        if (!session.isConductor() && idPasajero == session.getIdUsuario()) {
                            runOnUiThread(() -> {
                                mostrarBannerEstado("¡El conductor te ha recogido!", 0xFF2E7D32);
                                for (PasajeroInfo p : pasajeros) {
                                    if (p.idUsuario == session.getIdUsuario()) {
                                        p.recogido = true;
                                        if (p.marcadorSubida != null) {
                                            map.getOverlays().remove(p.marcadorSubida);
                                            p.marcadorSubida = null; map.invalidate();
                                        }
                                        break;
                                    }
                                }
                                if (marcadorSubida != null) {
                                    map.getOverlays().remove(marcadorSubida); marcadorSubida=null;
                                    map.invalidate();
                                }
                            });
                        }
                    }
                } catch (Exception e) { Log.w(TAG,"trip_event: "+e.getMessage()); }
            });

            mSocket.connect();
        } catch (URISyntaxException e) { Log.e(TAG,"socket init: "+e.getMessage()); }
    }

    private void desconectarSocket() {
        if (mSocket != null) {
            try { JSONObject d = new JSONObject(); d.put("idViaje",idViaje); mSocket.emit("leave_trip",d); }
            catch (Exception ignored) {}
            mSocket.disconnect(); mSocket.off(); mSocket = null;
        }
    }

    // =========================================================================
    //  FIN VIAJE — VISTA PASAJERO
    // =========================================================================

    private void escucharFinViajeComoPasajero() {
        if (idViaje<=0 || session.isConductor()) return;
        refEstadoViaje = FirebaseDatabase.getInstance()
                .getReference("viajes_estado").child("viaje_"+idViaje);
        listenerEstadoViaje = new com.google.firebase.database.ValueEventListener() {
            @Override public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snap) {
                String estado = snap.child("estado").getValue(String.class);
                if ("FINALIZADO".equals(estado))
                    runOnUiThread(Mapa.this::mostrarPantallaViajeTerminado);
                else if ("PASAJERO_RECOGIDO".equals(estado)) {
                    int miId = session.getIdUsuario();
                    Object recVal = snap.child("pasajero_recogido_"+miId).getValue();
                    if (Boolean.TRUE.equals(recVal)) {
                        runOnUiThread(() -> {
                            mostrarBannerEstado("¡Estás a bordo!", 0xFF2E7D32);
                            for (PasajeroInfo p : pasajeros)
                                if (p.idUsuario == miId) { p.recogido=true; break; }
                            pasajeroRecogido = true;
                        });
                    }
                }
                int miId = session.getIdUsuario();
                Object bajadaVal = snap.child("bajada_pasajero_"+miId).getValue();
                if ("LLEGADO".equals(bajadaVal) && !pagoYaLanzado) {
                    pagoYaLanzado = true;
                    runOnUiThread(() -> {
                        if (tvEta!=null) tvEta.setVisibility(View.GONE);
                        if (tvDistEta!=null) tvDistEta.setVisibility(View.GONE);
                        mostrarBannerEstado(" Llegaste a tu parada — ¡Registra tu pago!", 0xFF2E7D32);
                        new Handler(Looper.getMainLooper())
                                .postDelayed(Mapa.this::mostrarSheetPagoPasajeroDesdeProximidad, 1800);
                    });
                }
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError e) {
                Log.w(TAG,"listenFinViaje: "+e.getMessage());
            }
        };
        refEstadoViaje.addValueEventListener(listenerEstadoViaje);
    }

    private void mostrarPantallaViajeTerminado() {
        if (isFinishing() || isDestroyed()) return;
        if (pagoYaLanzado) return;
        if (tvEta!=null) tvEta.setVisibility(View.GONE);
        if (tvDistEta!=null) tvDistEta.setVisibility(View.GONE);
        if (rPoll!=null) hPoll.removeCallbacks(rPoll);
        new AlertDialog.Builder(this)
                .setTitle("¡Viaje finalizado!")
                .setMessage("El conductor llegó al destino. ¡Gracias por usar MoviFlex!\n\nAhora puedes registrar tu pago.")
                .setCancelable(false)
                .setPositiveButton("Pagar viaje",(d,w) -> irAPagarDesdeMapa())
                .setNegativeButton("Cerrar mapa",(d,w) -> {
                    Intent intent = new Intent(this, HomePasajero.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent); finish();
                }).show();
    }

    private void irAPagarDesdeMapa() {
        ConexionApi.getInstance(this).getObjectNoCache(Constantes.viajePorId((long)idViaje),
                viajeObj -> {
                    double monto = viajeObj.optDouble("precio",0);
                    if (monto<=0) {
                        JSONObject ruta = viajeObj.optJSONObject("ruta");
                        if (ruta!=null) monto = ruta.optDouble("precio",ruta.optDouble("costoCombustible",0));
                    }
                    final double fMonto = monto;
                    runOnUiThread(() -> lanzarPago(fMonto));
                },
                err -> runOnUiThread(() -> lanzarPago(0.0)));
    }

    private void lanzarPago(double monto) {
        if (isFinishing()||isDestroyed()) return;
        pagoYaLanzado = true;
        Intent intent = new Intent(this, PagoActivity.class);
        intent.putExtra(PagoActivity.EXTRA_ID_VIAJE,  idViaje);
        intent.putExtra(PagoActivity.EXTRA_MONTO,     monto);
        intent.putExtra(PagoActivity.EXTRA_CONDUCTOR, nomConductor);
        startActivity(intent);
    }

    // =========================================================================
    //  VERIFICAR RECOGIDA (modo simple)
    // =========================================================================

    private void verificarRecogidaPasajero(GeoPoint posConductor) {
        if (pasajeroRecogido || marcadorSubida==null || pSubida==null) return;
        double dist = calcularDistanciaMetros(posConductor, pSubida);
        if (dist <= 50) {
            pasajeroRecogido = true;
            map.getOverlays().remove(marcadorSubida); marcadorSubida=null; map.invalidate();
            Toast.makeText(this,"Pasajero recogido — llévalo a su parada",Toast.LENGTH_LONG).show();
            if (pBajada!=null && marcadorBajada!=null) {
                map.getOverlays().remove(marcadorBajada);
                marcadorBajada = new Marker(map);
                marcadorBajada.setPosition(pBajada);
                marcadorBajada.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marcadorBajada.setTitle("Llevar al pasajero aquí");
                marcadorBajada.setIcon(new BitmapDrawable(getResources(), circuloMarcadorP1(0xFFFF6F00,"🚏")));
                map.getOverlays().add(marcadorBajada); map.invalidate();
            }
            if (pBajada!=null && miUltimaPosicion!=null) actualizarLineaNaranja(miUltimaPosicion);
        }
    }

    // =========================================================================
    //  CARGAR COORDS DESDE API
    // =========================================================================

    private void cargarCoordsDesdeApi(boolean esConductor) {
        String url = Constantes.BASE_URL+"/api/viajes/"+idViaje;
        ConexionApi.getInstance(this).getObject(url,
                viajeJson -> {
                    Log.d(TAG,"=== VIAJE API ===\n"+viajeJson.toString());
                    try {
                        JSONObject ruta = viajeJson.optJSONObject("ruta");
                        JSONObject src  = ruta!=null ? ruta : viajeJson;

                        if (pOrigen==null) {
                            double la = primerDouble(src,"latOrigen","latitudOrigen","latInicio","lat_origen");
                            double lo = primerDouble(src,"lngOrigen","longitudOrigen","lngInicio","lng_origen");
                            if (la==0 && ruta!=null) {
                                la = primerDouble(viajeJson,"latOrigen","latitudOrigen");
                                lo = primerDouble(viajeJson,"lngOrigen","longitudOrigen");
                            }
                            if (la!=0) pOrigen = new GeoPoint(la,lo);
                        }

                        if (pDestino==null && ruta!=null) {
                            double la = primerDouble(ruta,"latDestino","latitudDestino","latFin","lat_destino");
                            double lo = primerDouble(ruta,"lngDestino","longitudDestino","lngFin","lng_destino");
                            if (la!=0) { pDestino = new GeoPoint(la,lo); }
                        }
                        if (pDestino==null) {
                            double la = primerDouble(viajeJson,"latDestino","latitudDestino","latFin");
                            double lo = primerDouble(viajeJson,"lngDestino","longitudDestino","lngFin");
                            if (la!=0) { pDestino = new GeoPoint(la,lo); }
                        }

                        JSONArray paradas = null;
                        if (ruta!=null) paradas = ruta.optJSONArray("paradas");
                        if (paradas==null) paradas = viajeJson.optJSONArray("paradas");

                        if (paradas!=null && paradas.length()>=2) {
                            JSONObject mejorO=null, mejorD=null;
                            int maxOrden=-1, minOrden=Integer.MAX_VALUE;
                            for (int i=0;i<paradas.length();i++) {
                                JSONObject p = paradas.optJSONObject(i); if (p==null) continue;
                                double pLat = p.optDouble("lat",Double.NaN);
                                if (Double.isNaN(pLat)||pLat==0) continue;
                                int    orden = p.optInt("orden",i);
                                String tipo  = p.optString("tipo","").toUpperCase().trim();
                                if ("BAJADA".equals(tipo)) continue;
                                if (orden<minOrden) { minOrden=orden; mejorO=p; }
                                if (orden>maxOrden) { maxOrden=orden; mejorD=p; }
                            }
                            if (pOrigen==null && mejorO!=null) {
                                double la=mejorO.optDouble("lat",0), lo=mejorO.optDouble("lng",0);
                                if (la!=0) { pOrigen=new GeoPoint(la,lo);
                                    String nom=mejorO.optString("nombre","").trim();
                                    if (!nom.isEmpty()&&!nom.equals("null")&&nomSubida.isEmpty()) nomSubida=nom; }
                            }
                            if (pDestino==null && mejorD!=null && mejorD!=mejorO) {
                                double la=mejorD.optDouble("lat",0), lo=mejorD.optDouble("lng",0);
                                if (la!=0 && !coordsIguales(la,lo,
                                        pOrigen!=null?pOrigen.getLatitude():0,
                                        pOrigen!=null?pOrigen.getLongitude():0)) {
                                    pDestino=new GeoPoint(la,lo);
                                    String nom=mejorD.optString("nombre","").trim();
                                    if (!nom.isEmpty()&&!nom.equals("null")&&nomDestinoRuta.isEmpty()) nomDestinoRuta=nom;
                                }
                            }
                            if (waypointsRuta.isEmpty()) {
                                for (int i=0;i<paradas.length();i++) {
                                    JSONObject p = paradas.optJSONObject(i); if (p==null) continue;
                                    String tipo = p.optString("tipo","").toUpperCase().trim();
                                    if ("BAJADA".equals(tipo)||"SUBIDA".equals(tipo)) continue;
                                    int orden = p.optInt("orden",i);
                                    if (orden==minOrden||orden==maxOrden) continue;
                                    double la=p.optDouble("lat",0), lo=p.optDouble("lng",0);
                                    if (la!=0) waypointsRuta.add(new GeoPoint(la,lo));
                                }
                            }
                        }

                        if (pDestino==null && ruta!=null) {
                            String nomRuta = ruta.optString("nombre","");
                            if (nomRuta.contains("→")||nomRuta.contains("->")) {
                                String[] parts = nomRuta.split("→|->",2);
                                if (parts.length>1) {
                                    final String destNom = parts[1].trim();
                                    if (!destNom.isEmpty()&&nomDestinoRuta.isEmpty()) nomDestinoRuta=destNom;
                                    new Thread(() -> {
                                        try {
                                            String q = destNom+", Popayan, Colombia";
                                            String geoUrl = "https://nominatim.openstreetmap.org/search?q="
                                                    +java.net.URLEncoder.encode(q,"UTF-8")
                                                    +"&format=json&limit=1&countrycodes=co";
                                            String resp = http(geoUrl);
                                            if (resp!=null&&!resp.isEmpty()&&!resp.equals("[]")) {
                                                JSONArray arr = new JSONArray(resp);
                                                if (arr.length()>0) {
                                                    JSONObject geo = arr.getJSONObject(0);
                                                    double la=geo.getDouble("lat"), lo=geo.getDouble("lon");
                                                    pDestino=new GeoPoint(la,lo);
                                                    if (nomDestinoRuta.isEmpty()) nomDestinoRuta=destNom;
                                                    runOnUiThread(() -> map.post(() -> dibujarRutaViaje(esConductor)));
                                                }
                                            }
                                        } catch (Exception e) { Log.w(TAG,"geocodificar destino: "+e.getMessage()); }
                                    }).start();
                                }
                            }
                        }

                        double precioViaje = viajeJson.optDouble("precio",0);
                        if (precioViaje==0 && ruta!=null)
                            precioViaje = ruta.optDouble("precio",ruta.optDouble("costoCombustible",0));
                        JSONArray usuarios = viajeJson.optJSONArray("usuarios");
                        if (usuarios!=null && esConductor)
                            procesarUsuariosMultipasajero(usuarios, precioViaje);

                    } catch (Exception e) { Log.e(TAG,"cargarCoordsDesdeApi parse: "+e.getMessage()); }

                    if (esConductor && idViaje>0) cargarSubidaBajadaPasajero();
                    if (!esConductor && idViaje>0 && pSubida==null && pBajada==null)
                        cargarParadasPasajeroDesdeApi();
                    if (pOrigen!=null && pDestino!=null)
                        map.post(() -> dibujarRutaViaje(esConductor));
                    else if (pDestino!=null)
                        usarGpsComoOrigen(esConductor);
                    else { agregarMarcadores(esConductor); }
                },
                err -> {
                    Log.e(TAG,"cargarCoordsDesdeApi error: "+err);
                    if (esConductor && idViaje>0) cargarSubidaBajadaPasajero();
                    agregarMarcadores(esConductor);
                });
    }

    // =========================================================================
    //  CARGA SUBIDA/BAJADA DEL PASAJERO (vista conductor)
    // =========================================================================

    private void cargarSubidaBajadaPasajero() {
        if (idViaje<=0) return;
        String urlViaje = Constantes.BASE_URL+"/api/viajes/"+idViaje;
        ConexionApi.getInstance(this).getObject(urlViaje,
                viajeJson -> {
                    JSONArray usuarios = viajeJson.optJSONArray("usuarios");
                    if (usuarios!=null && usuarios.length()>0) procesarReservasParaMapa(usuarios);
                    else cargarSubidaBajadaDesdeReservas();
                },
                err -> cargarSubidaBajadaDesdeReservas());
    }

    private void procesarReservasParaMapa(JSONArray usuarios) {
        double latS=0,lngS=0,latB=0,lngB=0; String nomS="",nomB="",nomP="";
        for (int i=0;i<usuarios.length();i++) {
            JSONObject u = usuarios.optJSONObject(i); if (u==null) continue;
            String est = u.optString("estado","").toUpperCase().trim();
            if ("CANCELADO".equals(est)||"CANCELADA".equals(est)) continue;
            double lb=u.optDouble("latBajada",u.optDouble("latParada",0));
            double lgb=u.optDouble("lngBajada",u.optDouble("lngParada",0));
            String nb=u.optString("nombreParadaBajada",u.optString("nombreParada",""));
            if (lb==0) { JSONObject po=u.optJSONObject("parada"); if(po!=null){lb=po.optDouble("lat",0);lgb=po.optDouble("lng",0); if(nb.isEmpty())nb=po.optString("nombre","");} }
            double ls=u.optDouble("latSubida",u.optDouble("latOrigen",u.optDouble("latInicio",0)));
            double lgs=u.optDouble("lngSubida",u.optDouble("lngOrigen",u.optDouble("lngInicio",0)));
            String ns=u.optString("nombreParadaSubida",u.optString("nombreParadaInicio",""));
            String np="";
            JSONObject po=u.optJSONObject("usuario"); if(po==null) po=u.optJSONObject("pasajero");
            if (po!=null) { np=po.optString("nombre",po.optString("nombres","")); String ape=po.optString("apellidos",""); if(!ape.isEmpty()) np=(np+" "+ape).trim(); }
            if (np.isEmpty()) np=u.optString("nombrePasajero","Pasajero");
            if (lb!=0||ls!=0) { latS=ls;lngS=lgs;nomS=ns.isEmpty()?"Punto de recogida":ns; latB=lb;lngB=lgb;nomB=nb.isEmpty()?"Parada del pasajero":nb; nomP=np; break; }
        }
        if (latS!=0||latB!=0) {
            if (latS!=0) pSubida=new GeoPoint(latS,lngS);
            if (latB!=0) pBajada=new GeoPoint(latB,lngB);
            final double fLS=latS,fLGS=lngS,fLB=latB,fLGB=lngB;
            final String fNS=nomS,fNB=nomB,fNP=nomP;
            runOnUiThread(() -> actualizarPinesPasajero(fLS,fLGS,fLB,fLGB,fNS,fNB,fNP));
        } else { cargarSubidaBajadaDesdeReservas(); }
    }

    private void cargarSubidaBajadaDesdeReservas() {
        String url = Constantes.BASE_URL+"/api/viajes/"+idViaje+"/reservas";
        ConexionApi.getInstance(this).getArray(url,
                reservas -> {
                    if (reservas==null||reservas.length()==0) return;
                    for (int i=0;i<reservas.length();i++) {
                        JSONObject r = reservas.optJSONObject(i); if (r==null) continue;
                        String est = r.optString("estado","").toUpperCase();
                        if ("CANCELADO".equals(est)||"CANCELADA".equals(est)) continue;
                        double latS=0,lngS=0,latB=0,lngB=0; String nomS="",nomB="";
                        JSONObject subObj=r.optJSONObject("puntoSubida"); if(subObj==null) subObj=r.optJSONObject("subida");
                        if (subObj!=null) { latS=subObj.optDouble("lat",subObj.optDouble("latitud",0)); lngS=subObj.optDouble("lng",subObj.optDouble("longitud",0)); nomS=subObj.optString("nombre",""); }
                        if (latS==0) { latS=r.optDouble("latSubida",r.optDouble("latOrigen",r.optDouble("latInicio",0))); lngS=r.optDouble("lngSubida",r.optDouble("lngOrigen",r.optDouble("lngInicio",0))); nomS=r.optString("nombreParadaSubida",r.optString("nombreParadaInicio","")); }
                        JSONObject bajObj=r.optJSONObject("puntoBajada"); if(bajObj==null) bajObj=r.optJSONObject("bajada");
                        if (bajObj!=null) { latB=bajObj.optDouble("lat",bajObj.optDouble("latitud",0)); lngB=bajObj.optDouble("lng",bajObj.optDouble("longitud",0)); nomB=bajObj.optString("nombre",""); }
                        if (latB==0) { latB=r.optDouble("latBajada",r.optDouble("latParada",0)); lngB=r.optDouble("lngBajada",r.optDouble("lngParada",0)); nomB=r.optString("nombreParadaBajada",r.optString("nombreParada","")); }
                        String nomPasajero="";
                        JSONObject po=r.optJSONObject("pasajero"); if(po==null) po=r.optJSONObject("usuario");
                        if (po!=null) { nomPasajero=po.optString("nombre",po.optString("nombres",po.optString("name",""))); String ape=po.optString("apellidos",""); if(!ape.isEmpty()) nomPasajero=(nomPasajero+" "+ape).trim(); }
                        if (nomPasajero.isEmpty()) nomPasajero=r.optString("nombrePasajero","Pasajero");
                        if (latS!=0||latB!=0) {
                            if (latS!=0) pSubida=new GeoPoint(latS,lngS);
                            if (latB!=0) pBajada=new GeoPoint(latB,lngB);
                            final double fLS=latS,fLGS=lngS,fLB=latB,fLGB=lngB;
                            final String fNS=nomS.isEmpty()?"Punto de recogida":nomS;
                            final String fNB=nomB.isEmpty()?"Parada del pasajero":nomB;
                            final String fNP=nomPasajero;
                            runOnUiThread(() -> actualizarPinesPasajero(fLS,fLGS,fLB,fLGB,fNS,fNB,fNP));
                            break;
                        }
                    }
                },
                err -> Log.w(TAG,"cargarSubidaBajada reservas: "+err));
    }

    private void actualizarPinesPasajero(double latS, double lngS,
                                         double latB, double lngB,
                                         String nomS, String nomB, String nomP) {
        if (latS!=0 && !pasajeroRecogido) {
            if (marcadorSubida!=null) map.getOverlays().remove(marcadorSubida);
            marcadorSubida=new Marker(map);
            marcadorSubida.setPosition(new GeoPoint(latS,lngS));
            marcadorSubida.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marcadorSubida.setTitle("Recoger a "+nomP+"\n"+nomS);
            marcadorSubida.setIcon(new BitmapDrawable(getResources(), circuloMarcadorP1(0xFFFF6F00,"P1")));
            map.getOverlays().add(marcadorSubida);
        } else if (pasajeroRecogido && marcadorSubida!=null) {
            map.getOverlays().remove(marcadorSubida); marcadorSubida=null;
        }
        if (latB!=0) {
            if (marcadorBajada!=null) map.getOverlays().remove(marcadorBajada);
            marcadorBajada=new Marker(map);
            marcadorBajada.setPosition(new GeoPoint(latB,lngB));
            marcadorBajada.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marcadorBajada.setTitle("Bajar a "+nomP+"\n"+nomB);
            marcadorBajada.setIcon(new BitmapDrawable(getResources(), pasajeroRecogido
                    ? circuloMarcadorGrande(0xFFFF6F00,"")
                    : circuloMarcador(0xFF1565C0,"")));
            map.getOverlays().add(marcadorBajada);
        }
        if (marcadorGpsPropio!=null) { map.getOverlays().remove(marcadorGpsPropio); map.getOverlays().add(marcadorGpsPropio); }
        map.invalidate();
        ArrayList<GeoPoint> pts = new ArrayList<>();
        if (pOrigen!=null) pts.add(pOrigen);
        if (latS!=0) pts.add(new GeoPoint(latS,lngS));
        if (latB!=0) pts.add(new GeoPoint(latB,lngB));
        if (pDestino!=null) pts.add(pDestino);
        if (pts.size()>1) zoomBoundingBox(pts);
    }

    // =========================================================================
    //  CARGA PARADAS DEL PASAJERO (vista pasajero)
    // =========================================================================

    private void cargarParadasPasajeroDesdeApi() {
        int idUsuario = session.getIdUsuario();
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long)idViaje),
                viajeObj -> {
                    if (pOrigen==null||pDestino==null) {
                        JSONObject rutaObj = viajeObj.optJSONObject("ruta");
                        if (rutaObj!=null) resolverOrigenDestinoDeParadas(rutaObj.optJSONArray("paradas"));
                    }
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    if (usuarios!=null) {
                        for (int i=0;i<usuarios.length();i++) {
                            JSONObject u=usuarios.optJSONObject(i); if(u==null) continue;
                            String est=u.optString("estado","").toUpperCase().trim();
                            if ("CANCELADO".equals(est)||"CANCELADA".equals(est)) continue;
                            int idU=-1;
                            JSONObject usuObj=u.optJSONObject("usuario");
                            if (usuObj!=null) for (String k : new String[]{"idUsuarios","id","idUsuario"}) { int id=usuObj.optInt(k,-1); if(id>0){idU=id;break;} }
                            if (idU<=0) idU=u.optInt("idUsuarios",u.optInt("idUsuario",-1));
                            if (idU!=idUsuario) continue;
                            double latS=0,lngS=0,latB=0,lngB=0; String nomS="",nomB="";
                            JSONObject subObj=u.optJSONObject("puntoSubida"); if(subObj==null) subObj=u.optJSONObject("subida");
                            if (subObj!=null){latS=subObj.optDouble("lat",subObj.optDouble("latitud",0)); lngS=subObj.optDouble("lng",subObj.optDouble("longitud",0)); nomS=subObj.optString("nombre","");}
                            if (latS==0){latS=u.optDouble("latSubida",u.optDouble("latOrigen",u.optDouble("latInicio",0))); lngS=u.optDouble("lngSubida",u.optDouble("lngOrigen",u.optDouble("lngInicio",0))); nomS=u.optString("nombreParadaSubida",u.optString("nombreParadaInicio",""));}
                            JSONObject bajObj=u.optJSONObject("puntoBajada"); if(bajObj==null) bajObj=u.optJSONObject("bajada");
                            if (bajObj!=null){latB=bajObj.optDouble("lat",bajObj.optDouble("latitud",0)); lngB=bajObj.optDouble("lng",bajObj.optDouble("longitud",0)); nomB=bajObj.optString("nombre","");}
                            if (latB==0){latB=u.optDouble("latBajada",u.optDouble("latParada",0)); lngB=u.optDouble("lngBajada",u.optDouble("lngParada",0)); nomB=u.optString("nombreParadaBajada",u.optString("nombreParada",""));}
                            final double fLS=latS,fLGS=lngS,fLB=latB,fLGB=lngB;
                            final String fNS=nomS.isEmpty()?"Tu punto de recogida":nomS;
                            final String fNB=nomB.isEmpty()?"Tu parada de bajada":nomB;
                            if (latS!=0||latB!=0) {
                                if (latS!=0) pSubida=new GeoPoint(latS,lngS);
                                if (latB!=0) pBajada=new GeoPoint(latB,lngB);
                                runOnUiThread(() -> { pintarPinesPasajeroEnMapa(fLS,fLGS,fLB,fLGB,fNS,fNB);
                                    if (pOrigen!=null&&pDestino!=null) map.post(() -> dibujarRutaViaje(false)); });
                                return;
                            }
                        }
                    }
                    cargarParadasPasajeroDesdeReservas();
                },
                err -> cargarParadasPasajeroDesdeReservas());
    }

    private void cargarParadasPasajeroDesdeReservas() {
        String[] urls = {
                Constantes.MIS_RESERVAS,
                Constantes.BASE_URL+"/api/reservas/viaje/"+idViaje,
                Constantes.BASE_URL+"/api/reservas?idViaje="+idViaje,
        };
        intentarReservasPasajero(urls,0);
    }

    private void intentarReservasPasajero(String[] urls, int index) {
        if (index>=urls.length) {
            runOnUiThread(() -> { if (pOrigen!=null&&pDestino!=null) map.post(() -> dibujarRutaViaje(false)); });
            return;
        }
        ConexionApi.getInstance(this).getArrayNoCache(urls[index],
                reservas -> {
                    if (reservas==null||reservas.length()==0) { intentarReservasPasajero(urls,index+1); return; }
                    for (int i=0;i<reservas.length();i++) {
                        JSONObject r=reservas.optJSONObject(i); if(r==null) continue;
                        int idV=r.optInt("idViajes",r.optInt("idViaje",r.optInt("viajeId",-1)));
                        if (idV>0&&idV!=idViaje) continue;
                        String est=r.optString("estado","").toUpperCase().trim();
                        if ("CANCELADO".equals(est)||"CANCELADA".equals(est)) continue;
                        double latS=0,lngS=0,latB=0,lngB=0; String nomS="",nomB="";
                        JSONObject subObj=r.optJSONObject("puntoSubida"); if(subObj==null) subObj=r.optJSONObject("subida");
                        if (subObj!=null){latS=subObj.optDouble("lat",subObj.optDouble("latitud",0)); lngS=subObj.optDouble("lng",subObj.optDouble("longitud",0)); nomS=subObj.optString("nombre","");}
                        if (latS==0){latS=r.optDouble("latSubida",r.optDouble("latOrigen",0)); lngS=r.optDouble("lngSubida",r.optDouble("lngOrigen",0)); nomS=r.optString("nombreParadaSubida","");}
                        JSONObject bajObj=r.optJSONObject("puntoBajada"); if(bajObj==null) bajObj=r.optJSONObject("bajada");
                        if (bajObj!=null){latB=bajObj.optDouble("lat",bajObj.optDouble("latitud",0)); lngB=bajObj.optDouble("lng",bajObj.optDouble("longitud",0)); nomB=bajObj.optString("nombre","");}
                        if (latB==0){latB=r.optDouble("latBajada",r.optDouble("latParada",0)); lngB=r.optDouble("lngBajada",r.optDouble("lngParada",0)); nomB=r.optString("nombreParadaBajada","");}
                        if (latS!=0||latB!=0) {
                            if (latS!=0) pSubida=new GeoPoint(latS,lngS);
                            if (latB!=0) pBajada=new GeoPoint(latB,lngB);
                            final double fLS=latS,fLGS=lngS,fLB=latB,fLGB=lngB;
                            final String fNS=nomS.isEmpty()?"Tu punto de recogida":nomS;
                            final String fNB=nomB.isEmpty()?"Tu parada de bajada":nomB;
                            runOnUiThread(() -> { pintarPinesPasajeroEnMapa(fLS,fLGS,fLB,fLGB,fNS,fNB);
                                if (pOrigen!=null&&pDestino!=null) map.post(() -> dibujarRutaViaje(false)); });
                            return;
                        }
                    }
                    intentarReservasPasajero(urls,index+1);
                },
                err -> { ConexionApi.getInstance(this).getObjectNoCache(urls[index],
                        obj -> intentarReservasPasajero(urls,index+1),
                        err2 -> intentarReservasPasajero(urls,index+1)); });
    }

    private void pintarPinesPasajeroEnMapa(double latS, double lngS,
                                           double latB, double lngB,
                                           String nomS, String nomB) {
        if (latS!=0) {
            Marker m=new Marker(map); m.setPosition(new GeoPoint(latS,lngS));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle("Tu punto de recogida\n"+nomS);
            m.setIcon(new BitmapDrawable(getResources(), circuloMarcadorP1(0xFF2E7D32,"")));
            map.getOverlays().add(m);
        }
        if (latB!=0) {
            Marker m=new Marker(map); m.setPosition(new GeoPoint(latB,lngB));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle("Tu parada de bajada\n"+nomB);
            m.setIcon(new BitmapDrawable(getResources(), circuloMarcadorP1(0xFFFF6F00,"")));
            map.getOverlays().add(m);
        }
        map.invalidate();
        ArrayList<GeoPoint> pts=new ArrayList<>();
        if (pOrigen!=null) pts.add(pOrigen); if (pDestino!=null) pts.add(pDestino);
        if (latS!=0) pts.add(new GeoPoint(latS,lngS)); if (latB!=0) pts.add(new GeoPoint(latB,lngB));
        if (pts.size()>1) zoomBoundingBox(pts);
    }

    private void resolverOrigenDestinoDeParadas(JSONArray paradas) {
        if (paradas==null||paradas.length()<2) return;
        JSONObject mejorO=null,mejorD=null; int minOrden=Integer.MAX_VALUE,maxOrden=-1;
        for (int i=0;i<paradas.length();i++) {
            JSONObject p=paradas.optJSONObject(i); if(p==null) continue;
            double la=p.optDouble("lat",0); if(la==0) continue;
            String tipo=p.optString("tipo","").toUpperCase().trim();
            if ("BAJADA".equals(tipo)) continue;
            int orden=p.optInt("orden",i);
            if (orden<minOrden){minOrden=orden;mejorO=p;} if (orden>maxOrden){maxOrden=orden;mejorD=p;}
        }
        if (pOrigen==null&&mejorO!=null) { double la=mejorO.optDouble("lat",0),lo=mejorO.optDouble("lng",0); if(la!=0){pOrigen=new GeoPoint(la,lo); if(nomSubida.isEmpty()) nomSubida=mejorO.optString("nombre","");} }
        if (pDestino==null&&mejorD!=null&&mejorD!=mejorO) { double la=mejorD.optDouble("lat",0),lo=mejorD.optDouble("lng",0); if(la!=0){pDestino=new GeoPoint(la,lo); if(nomDestinoRuta.isEmpty()) nomDestinoRuta=mejorD.optString("nombre","");} }
    }

    // =========================================================================
    //  MARCADOR CON FOTO
    // =========================================================================

    private void crearMarcadorConFoto(GeoPoint posicion, String titulo,
                                      String fotoUrl, String nombrePasajero,
                                      MarkerCallback callback) {
        int sz=108;
        if (fotoUrl!=null&&!fotoUrl.isEmpty()&&!fotoUrl.equals("null")) {
            Glide.with(this).asBitmap().load(fotoUrl).circleCrop().override(sz,sz)
                    .into(new CustomTarget<Bitmap>() {
                        @Override public void onResourceReady(@NonNull Bitmap bmp, @Nullable com.bumptech.glide.request.transition.Transition<? super Bitmap> t) {
                            Marker m=new Marker(map); m.setPosition(posicion); m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            m.setTitle(titulo); m.setIcon(new BitmapDrawable(getResources(), construirPinConFoto(bmp,sz)));
                            callback.onMarker(m);
                        }
                        @Override public void onLoadCleared(@Nullable android.graphics.drawable.Drawable p) {}
                        @Override public void onLoadFailed(@Nullable android.graphics.drawable.Drawable err) {
                            callback.onMarker(pinFallback(posicion,titulo,nombrePasajero));
                        }
                    });
        } else { callback.onMarker(pinFallback(posicion,titulo,nombrePasajero)); }
    }

    private Marker pinFallback(GeoPoint pos, String titulo, String nombre) {
        String ini = (nombre!=null&&!nombre.isEmpty()) ? nombre.substring(0,1).toUpperCase() : "P";
        Marker m=new Marker(map); m.setPosition(pos); m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle(titulo); m.setIcon(new BitmapDrawable(getResources(), pinConCola(0xFFFF7043,ini)));
        return m;
    }

    private Bitmap construirPinConFoto(Bitmap fotoBmp, int sz) {
        int colaH=20,r=sz/2-8,cx=sz/2,cy=r+8;
        Bitmap resultado=Bitmap.createBitmap(sz,cy+r+colaH+4,Bitmap.Config.ARGB_8888);
        Canvas cv=new Canvas(resultado);
        Paint ps=new Paint(Paint.ANTI_ALIAS_FLAG); ps.setColor(Color.argb(70,0,0,0));
        cv.drawCircle(cx+3,cy+5,r+2,ps);
        Paint pbo=new Paint(Paint.ANTI_ALIAS_FLAG); pbo.setColor(0xFFFF7043);
        cv.drawCircle(cx,cy,r+5,pbo);
        Paint pbw=new Paint(Paint.ANTI_ALIAS_FLAG); pbw.setColor(Color.WHITE);
        cv.drawCircle(cx,cy,r+2,pbw);
        Bitmap fe=Bitmap.createScaledBitmap(fotoBmp,r*2,r*2,true);
        Paint pf=new Paint(Paint.ANTI_ALIAS_FLAG);
        android.graphics.BitmapShader shader=new android.graphics.BitmapShader(fe,android.graphics.Shader.TileMode.CLAMP,android.graphics.Shader.TileMode.CLAMP);
        Matrix matrix=new Matrix(); matrix.setTranslate(cx-r,cy-r); shader.setLocalMatrix(matrix);
        pf.setShader(shader); cv.drawCircle(cx,cy,r,pf);
        Paint pc=new Paint(Paint.ANTI_ALIAS_FLAG); pc.setColor(0xFFFF7043);
        Path cola=new Path(); cola.moveTo(cx-11,cy+r); cola.lineTo(cx+11,cy+r); cola.lineTo(cx,cy+r+colaH); cola.close();
        cv.drawPath(cola,pc);
        return resultado;
    }

    // =========================================================================
    //  BITMAPS DE MARCADORES
    // =========================================================================

    private Bitmap circuloMarcador(int colorFondo, String texto) {
        int sz=96; Bitmap bmp=Bitmap.createBitmap(sz,sz,Bitmap.Config.ARGB_8888); Canvas cv=new Canvas(bmp);
        Paint ps=new Paint(Paint.ANTI_ALIAS_FLAG); ps.setColor(Color.argb(80,0,0,0)); cv.drawCircle(sz/2f+3,sz/2f+5,sz/2f-8,ps);
        Paint pf=new Paint(Paint.ANTI_ALIAS_FLAG); pf.setColor(colorFondo); cv.drawCircle(sz/2f,sz/2f-3,sz/2f-9,pf);
        Paint pb=new Paint(Paint.ANTI_ALIAS_FLAG); pb.setColor(Color.WHITE); pb.setStyle(Paint.Style.STROKE); pb.setStrokeWidth(5f); cv.drawCircle(sz/2f,sz/2f-3,sz/2f-9,pb);
        Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG); pt.setColor(Color.WHITE); pt.setTextSize(texto.length()>1?26f:38f); pt.setTypeface(Typeface.DEFAULT_BOLD); pt.setTextAlign(Paint.Align.CENTER);
        cv.drawText(texto,sz/2f,sz/2f+11,pt);
        return bmp;
    }

    private Bitmap circuloMarcadorGrande(int colorFondo, String texto) {
        int sz=120; Bitmap bmp=Bitmap.createBitmap(sz,sz,Bitmap.Config.ARGB_8888); Canvas cv=new Canvas(bmp);
        Paint ph=new Paint(Paint.ANTI_ALIAS_FLAG); ph.setColor(Color.argb(120,255,255,255)); cv.drawCircle(sz/2f,sz/2f-3,sz/2f-4,ph);
        Paint ps=new Paint(Paint.ANTI_ALIAS_FLAG); ps.setColor(Color.argb(80,0,0,0)); cv.drawCircle(sz/2f+3,sz/2f+6,sz/2f-14,ps);
        Paint pf=new Paint(Paint.ANTI_ALIAS_FLAG); pf.setColor(colorFondo); cv.drawCircle(sz/2f,sz/2f-3,sz/2f-14,pf);
        Paint pb=new Paint(Paint.ANTI_ALIAS_FLAG); pb.setColor(Color.WHITE); pb.setStyle(Paint.Style.STROKE); pb.setStrokeWidth(6f); cv.drawCircle(sz/2f,sz/2f-3,sz/2f-14,pb);
        Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG); pt.setColor(Color.WHITE); pt.setTextSize(texto.length()>1?32f:46f); pt.setTypeface(Typeface.DEFAULT_BOLD); pt.setTextAlign(Paint.Align.CENTER);
        cv.drawText(texto,sz/2f,sz/2f+13,pt);
        return bmp;
    }

    private Bitmap circuloMarcadorP1(int colorFondo, String texto) {
        int sz=108,r=42,cx=sz/2,cy=r+6,colaH=18;
        Bitmap bmp=Bitmap.createBitmap(sz,sz,Bitmap.Config.ARGB_8888); Canvas cv=new Canvas(bmp);
        Paint ps=new Paint(Paint.ANTI_ALIAS_FLAG); ps.setColor(Color.argb(70,0,0,0)); cv.drawCircle(cx+3,cy+5,r-2,ps);
        Paint pf=new Paint(Paint.ANTI_ALIAS_FLAG); pf.setColor(colorFondo); cv.drawCircle(cx,cy,r,pf);
        Paint pb=new Paint(Paint.ANTI_ALIAS_FLAG); pb.setColor(Color.WHITE); pb.setStyle(Paint.Style.STROKE); pb.setStrokeWidth(5f); cv.drawCircle(cx,cy,r,pb);
        Paint pc=new Paint(Paint.ANTI_ALIAS_FLAG); pc.setColor(colorFondo);
        Path cola=new Path(); cola.moveTo(cx-10,cy+r-4); cola.lineTo(cx+10,cy+r-4); cola.lineTo(cx,cy+r+colaH); cola.close(); cv.drawPath(cola,pc);
        Paint pbc=new Paint(Paint.ANTI_ALIAS_FLAG); pbc.setColor(Color.WHITE); pbc.setStyle(Paint.Style.STROKE); pbc.setStrokeWidth(4f);
        Path colaBorde=new Path(); colaBorde.moveTo(cx-10,cy+r-2); colaBorde.lineTo(cx,cy+r+colaH); colaBorde.lineTo(cx+10,cy+r-2); cv.drawPath(colaBorde,pbc);
        Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG); pt.setColor(Color.WHITE); pt.setTextSize(texto.length()>2?22f:28f); pt.setTypeface(Typeface.DEFAULT_BOLD); pt.setTextAlign(Paint.Align.CENTER);
        cv.drawText(texto,cx,cy-(pt.descent()+pt.ascent())/2,pt);
        return bmp;
    }

    private Bitmap pinConCola(int colorFondo, String texto) {
        int sz=100,r=36,cx=sz/2,cy=r+7,colaH=18;
        Bitmap bmp=Bitmap.createBitmap(sz,cy+r+colaH+4,Bitmap.Config.ARGB_8888); Canvas cv=new Canvas(bmp);
        Paint ps=new Paint(Paint.ANTI_ALIAS_FLAG); ps.setColor(Color.argb(65,0,0,0)); cv.drawCircle(cx+3,cy+5,r-2,ps);
        Paint pf=new Paint(Paint.ANTI_ALIAS_FLAG); pf.setColor(colorFondo); cv.drawCircle(cx,cy,r,pf);
        Paint pb=new Paint(Paint.ANTI_ALIAS_FLAG); pb.setColor(Color.WHITE); pb.setStyle(Paint.Style.STROKE); pb.setStrokeWidth(4.5f); cv.drawCircle(cx,cy,r,pb);
        Paint pc=new Paint(Paint.ANTI_ALIAS_FLAG); pc.setColor(colorFondo);
        Path cola=new Path(); cola.moveTo(cx-10,cy+r-3); cola.lineTo(cx+10,cy+r-3); cola.lineTo(cx,cy+r+colaH); cola.close(); cv.drawPath(cola,pc);
        Paint pbc=new Paint(Paint.ANTI_ALIAS_FLAG); pbc.setColor(Color.WHITE); pbc.setStyle(Paint.Style.STROKE); pbc.setStrokeWidth(3.5f);
        Path colaBorde=new Path(); colaBorde.moveTo(cx-10,cy+r-1); colaBorde.lineTo(cx,cy+r+colaH); colaBorde.lineTo(cx+10,cy+r-1); cv.drawPath(colaBorde,pbc);
        Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG); pt.setColor(Color.WHITE); pt.setTextSize(texto.length()>2?20f:26f); pt.setTypeface(Typeface.DEFAULT_BOLD); pt.setTextAlign(Paint.Align.CENTER);
        cv.drawText(texto,cx,cy-(pt.descent()+pt.ascent())/2,pt);
        return bmp;
    }

    private Bitmap crearIconoCarro(int color, float rumbo) {
        int W = 180, H = 90;
        Bitmap base = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(base);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color); p.setStyle(Paint.Style.FILL);

        Path carro = new Path();
        carro.moveTo(15,60); carro.quadTo(20,40,40,40); carro.lineTo(55,25);
        carro.quadTo(75,10,105,15); carro.lineTo(140,35); carro.quadTo(160,40,165,55);
        carro.quadTo(165,65,150,65); carro.lineTo(30,65); carro.quadTo(15,65,15,60); carro.close();
        cv.drawPath(carro, p);

        Paint pVentana = new Paint(Paint.ANTI_ALIAS_FLAG); pVentana.setColor(Color.WHITE);
        Path ventana1 = new Path();
        ventana1.moveTo(60,32); ventana1.quadTo(75,20,90,22); ventana1.lineTo(90,38); ventana1.lineTo(60,38); ventana1.close();
        cv.drawPath(ventana1, pVentana);
        Path ventana2 = new Path();
        ventana2.moveTo(92,22); ventana2.quadTo(110,24,120,35); ventana2.lineTo(120,38); ventana2.lineTo(92,38); ventana2.close();
        cv.drawPath(ventana2, pVentana);

        Paint ruedaNegra = new Paint(Paint.ANTI_ALIAS_FLAG); ruedaNegra.setColor(Color.BLACK);
        Paint aroBlanco  = new Paint(Paint.ANTI_ALIAS_FLAG); aroBlanco.setColor(Color.WHITE);
        cv.drawCircle(55,65,14,ruedaNegra); cv.drawCircle(55,65,9,aroBlanco);
        cv.drawCircle(130,65,14,ruedaNegra); cv.drawCircle(130,65,9,aroBlanco);

        Bitmap rotado = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cvR = new Canvas(rotado);
        Matrix m = new Matrix(); m.setRotate(rumbo, W/2f, H/2f);
        cvR.drawBitmap(base, m, null);
        base.recycle();
        return rotado;
    }

    // =========================================================================
    //  ENVIAR UBICACIÓN AL BACKEND
    // =========================================================================

    private void enviarUbicacionAlBackend(double lat, double lon) {
        if (mSocket!=null && mSocket.connected()) {
            try {
                JSONObject data=new JSONObject(); data.put("idViaje",idViaje); data.put("lat",lat); data.put("lng",lon);
                data.put("rumbo", miUltimaPosicion!=null ? calcularRumbo(miUltimaPosicion,new GeoPoint(lat,lon)) : 0);
                mSocket.emit("driver_location_update",data);
            } catch (Exception e) { Log.e(TAG,"socket emit: "+e.getMessage()); }
        }
        try {
            JSONObject body=new JSONObject(); body.put("lat",lat); body.put("lng",lon);
            body.put("latitud",lat); body.put("longitud",lon);
            ConexionApi.getInstance(this).post(
                    Constantes.BASE_URL+"/api/viajes/"+idViaje+"/ubicacion-conductor",
                    body, r -> {}, err -> Log.w(TAG,"enviarUbicacion: "+err));
        } catch (Exception e) { Log.w(TAG,"enviarUbicacion ex: "+e.getMessage()); }
    }

    // =========================================================================
    //  ZOOM
    // =========================================================================

    private void zoomBoundingBox(List<GeoPoint> puntos) {
        if (puntos==null||puntos.isEmpty()) return;
        if (puntos.size()==1) { map.getController().animateTo(puntos.get(0)); map.getController().setZoom(16.0); return; }
        double mnLat=Double.MAX_VALUE,mxLat=-Double.MAX_VALUE,mnLng=Double.MAX_VALUE,mxLng=-Double.MAX_VALUE;
        for (GeoPoint p : puntos) { mnLat=Math.min(mnLat,p.getLatitude()); mxLat=Math.max(mxLat,p.getLatitude()); mnLng=Math.min(mnLng,p.getLongitude()); mxLng=Math.max(mxLng,p.getLongitude()); }
        double pLat=Math.max((mxLat-mnLat)*0.25,0.006), pLng=Math.max((mxLng-mnLng)*0.25,0.006);
        BoundingBox bb=new BoundingBox(mxLat+pLat,mxLng+pLng,mnLat-pLat,mnLng-pLng);
        map.post(() -> { try { map.zoomToBoundingBox(bb,true,100); } catch (Exception ignored) {} });
    }

    // =========================================================================
    //  RUTAS SIN VIAJE ACTIVO
    // =========================================================================

    private void pedirRutasBackend(double oLat, double oLng) {
        routeManager.fetchRoutes(oLat,oLng,destinoLat,destinoLng,"FASTEST",
                new RouteManager.RouteCallback() {
                    @Override public void onSuccess(RouteOptionsResponse r) { runOnUiThread(() -> pintarRutasBackend(r)); }
                    @Override public void onError(String msg) { Log.e(TAG,"fetchRoutes: "+msg); }
                });
    }

    private void pintarRutasBackend(RouteOptionsResponse response) {
        if (response.routes==null||response.routes.isEmpty()) return;
        limpiarLineas(lineasPintadas);
        List<GeoJsonHelper.RutaSimple> rutas=new ArrayList<>();
        for (RouteOption r : response.routes)
            rutas.add(new GeoJsonHelper.RutaSimple(r.id,r.geojson,r.distanceKm,r.durationMin,r.fuelCostCop));
        List<Polyline> pintadas=GeoJsonHelper.pintarRutas(rutas,map);
        lineasPintadas.addAll(pintadas);
        GeoJsonHelper.zoomARutas(lineasPintadas,map);
        if (marcadorGpsPropio!=null) { map.getOverlays().remove(marcadorGpsPropio); map.getOverlays().add(marcadorGpsPropio); }
        map.invalidate();
        RouteOption m=response.routes.get(0);
        Toast.makeText(this,"Ruta: "+m.distanceKm+" km | "+(int)m.durationMin+" min | $"+(int)m.fuelCostCop+" COP",Toast.LENGTH_LONG).show();
    }

    // =========================================================================
    //  VERIFICAR ACCESO AL MAPA
    // =========================================================================

    private void verificarAccesoAlMapa() {
        boolean esConductor = session.isConductor();
        if (esConductor) {
            ConexionApi.getInstance(this).getArrayNoCache(
                    Constantes.BASE_URL+"/api/viajes/mis-viajes",
                    response -> {
                        boolean tieneActivo=false;
                        if (response!=null) for (int i=0;i<response.length();i++) {
                            JSONObject v=response.optJSONObject(i); if(v==null) continue;
                            String est=v.optString("estado","").toUpperCase().trim();
                            if ("INICIADO".equals(est)||"EN_CURSO".equals(est)||"ACTIVO".equals(est)){tieneActivo=true;break;}
                        }
                        final boolean fActivo=tieneActivo;
                        runOnUiThread(() -> { if (!fActivo&&!desdeViajeActivo&&idViaje<=0) mostrarMapaBloqueado(true); });
                    },
                    err -> runOnUiThread(() -> { if (!desdeViajeActivo&&idViaje<=0) mostrarMapaBloqueado(true); }));
        } else {
            ConexionApi.getInstance(this).getArrayNoCache(Constantes.MIS_RESERVAS,
                    response -> {
                        boolean tieneActiva=false;
                        if (response!=null) for (int i=0;i<response.length();i++) {
                            JSONObject r=response.optJSONObject(i); if(r==null) continue;
                            String est=r.optString("estado","").toUpperCase().trim();
                            if ("RESERVADO".equals(est)||"CONFIRMADO".equals(est)||"CONFIRMADA".equals(est)||"RECOGIDO".equals(est)||"ESPERANDO_RECOGIDA".equals(est)||"EN_CURSO".equals(est)) {
                                JSONObject viaje=r.optJSONObject("viaje");
                                if (viaje!=null) { String estViaje=viaje.optString("estado","").toUpperCase().trim();
                                    if ("INICIADO".equals(estViaje)||"EN_CURSO".equals(estViaje)||"ACTIVO".equals(estViaje)||"CREADO".equals(estViaje)||"DISPONIBLE".equals(estViaje)||"PROGRAMADO".equals(estViaje)){tieneActiva=true;break;}
                                } else { tieneActiva=true; break; }
                            }
                        }
                        final boolean fActivo=tieneActiva;
                        runOnUiThread(() -> { if (!fActivo&&!desdeViajeActivo&&idViaje<=0) mostrarMapaBloqueado(false); });
                    },
                    err -> runOnUiThread(() -> { if (!desdeViajeActivo&&idViaje<=0) mostrarMapaBloqueado(false); }));
        }
    }

    private void mostrarMapaBloqueado(boolean esConductor) {
        if (isFinishing()||isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(esConductor?"Sin viaje activo":"Sin reserva activa")
                .setMessage(esConductor
                        ?"Primero debes publicar e iniciar un viaje para acceder al mapa en tiempo real."
                        :"Primero debes hacer una reserva en un viaje para acceder al mapa en tiempo real.")
                .setCancelable(false)
                .setPositiveButton(esConductor?"Publicar viaje":"Buscar viajes",(d,w) -> {
                    Intent intent=esConductor?new Intent(this,PublicarRuta.class):new Intent(this,HomePasajero.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent); finish();
                })
                .setNegativeButton("← Volver",(d,w) -> {
                    Intent intent=esConductor?new Intent(this,HomeConductor.class):new Intent(this,HomePasajero.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent); finish();
                }).show();
    }

    // =========================================================================
    //  CALIFICAR AL CONDUCTOR (vista pasajero)
    // =========================================================================

    private void irACalificarConductorDesdeMapa() {
        ConexionApi.getInstance(this).getObjectNoCache(Constantes.viajePorId((long)idViaje),
                viajeObj -> {
                    int idCond=-1; String nomCond="";
                    JSONObject condObj=viajeObj.optJSONObject("conductor");
                    if (condObj!=null) {
                        for (String k : new String[]{"id","idUsuarios","idUsuario"}) { int id=condObj.optInt(k,-1); if(id>0){idCond=id;break;} }
                        for (String k : new String[]{"nombre","nombres","nombreCompleto","name"}) { String n=condObj.optString(k,""); if(!n.isEmpty()&&!n.equals("null")){nomCond=n;break;} }
                        if (nomCond.isEmpty()) nomCond=(condObj.optString("nombres","")+" "+condObj.optString("apellidos","")).trim();
                    }
                    if (idCond<=0) idCond=viajeObj.optInt("idConductor",viajeObj.optInt("conductorId",-1));
                    if (nomCond.isEmpty()) nomCond=nomConductor.isEmpty()?"el conductor":nomConductor;
                    final int fIdCond=idCond; final String fNomCond=nomCond; final int fIdPas=session.getIdUsuario();
                    if (fIdCond>0) {
                        runOnUiThread(() ->
                                CalificacionController.mostrarBottomSheetCalificar(
                                        this, idViaje, fIdCond, fNomCond, "",
                                        fIdPas, false,
                                        (pun,com2) -> {
                                            if (refEstadoViaje!=null&&listenerEstadoViaje!=null)
                                                refEstadoViaje.removeEventListener(listenerEstadoViaje);
                                            Intent intent=new Intent(this,HomePasajero.class);
                                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(intent); finish();
                                        }));
                    } else {
                        runOnUiThread(() -> {
                            if (refEstadoViaje!=null&&listenerEstadoViaje!=null)
                                refEstadoViaje.removeEventListener(listenerEstadoViaje);
                            Intent intent=new Intent(this,HomePasajero.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent); finish();
                        });
                    }
                },
                err -> runOnUiThread(() -> {
                    Intent intent=new Intent(this,HomePasajero.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent); finish();
                }));
    }

    // =========================================================================
    //  BOTTOM NAV
    // =========================================================================

    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav==null) return;
        nav.setSelectedItemId(R.id.nav_mapa);
        boolean esConductor = session.isConductor();
        nav.setOnItemSelectedListener(item -> {
            int id=item.getItemId();
            if (id==R.id.nav_mapa) return true;
            if (esConductor) {
                if      (id==R.id.nav_inicio)     startActivity(new Intent(this,HomeConductor.class));
                else if (id==R.id.nav_mis_viajes) startActivity(new Intent(this,PublicarRuta.class));
                else if (id==R.id.nav_mensajes)   startActivity(new Intent(this,Mensajes.class));
                else if (id==R.id.nav_perfil)     startActivity(new Intent(this,PerfilUsuario.class));
                else return true;
            } else {
                if      (id==R.id.nav_inicio)     startActivity(new Intent(this,HomePasajero.class));
                else if (id==R.id.nav_mis_viajes) startActivity(new Intent(this,MisReservasActivity.class));
                else if (id==R.id.nav_mensajes)   startActivity(new Intent(this,Mensajes.class));
                else if (id==R.id.nav_perfil)     startActivity(new Intent(this,PerfilUsuario.class));
                else return true;
            }
            finish(); return true;
        });
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(req,perms,grants);
        if (req==LOCATION_PERM && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)
            configurarGpsPropio();
        else Toast.makeText(this,"Permiso de ubicación requerido para usar el mapa.",Toast.LENGTH_LONG).show();
    }

    // =========================================================================
    //  UTILIDADES
    // =========================================================================

    private boolean coordsIguales(double la1, double lo1, double la2, double lo2) {
        return Math.abs(la1-la2)<0.0002 && Math.abs(lo1-lo2)<0.0002;
    }

    private double primerDouble(JSONObject obj, String... campos) {
        for (String c : campos) { double v=obj.optDouble(c,0); if(v!=0) return v; } return 0;
    }

    private GeoPoint extraerDestinoDeParadasJson() {
        String json = getIntent().getStringExtra("PARADAS_JSON");
        if (json == null || json.isEmpty()) return null;
        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) return null;
            JSONObject mejorDestino = null;
            int maxOrden = -1;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i); if (p == null) continue;
                String tipo = p.optString("tipo", "").toUpperCase().trim();
                if ("BAJADA".equals(tipo)) continue;
                double lat = p.optDouble("lat", p.optDouble("latitud", 0)); if (lat == 0) continue;
                int orden = p.optInt("orden", i);
                if (orden > maxOrden) { maxOrden = orden; mejorDestino = p; }
            }
            if (mejorDestino == null) mejorDestino = arr.getJSONObject(arr.length() - 1);
            double lat = mejorDestino.optDouble("lat", mejorDestino.optDouble("latitud", 0));
            double lng = mejorDestino.optDouble("lng", mejorDestino.optDouble("longitud", 0));
            if (lat != 0 && lng != 0) {
                String nom = mejorDestino.optString("nombre", "").trim();
                if (!nom.isEmpty() && !nom.equals("null") && nomDestinoRuta.isEmpty()) nomDestinoRuta = nom;
                return new GeoPoint(lat, lng);
            }
        } catch (Exception e) { Log.w(TAG, "extraerDestino: " + e.getMessage()); }
        return null;
    }

    private void leerWaypointsDelIntent() {
        waypointsRuta.clear();
        String json = getIntent().getStringExtra("PARADAS_JSON");
        if (json == null || json.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(json);
            int minOrden = Integer.MAX_VALUE, maxOrden = -1;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i); if (p == null) continue;
                String tipo = p.optString("tipo", "").toUpperCase().trim();
                if ("BAJADA".equals(tipo)) continue;
                double lat = p.optDouble("lat", p.optDouble("latitud", 0)); if (lat == 0) continue;
                int orden = p.optInt("orden", i);
                if (orden < minOrden) minOrden = orden;
                if (orden > maxOrden) maxOrden = orden;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i); if (p == null) continue;
                String tipo = p.optString("tipo", "").toUpperCase().trim();
                if ("BAJADA".equals(tipo) || "SUBIDA".equals(tipo)) continue;
                double lat = p.optDouble("lat", p.optDouble("latitud", 0));
                double lng = p.optDouble("lng", p.optDouble("longitud", 0));
                if (lat == 0) continue;
                int orden = p.optInt("orden", i);
                if (orden == minOrden || orden == maxOrden) continue;
                waypointsRuta.add(new GeoPoint(lat, lng));
            }
        } catch (Exception e) { Log.w(TAG, "leerWaypoints: " + e.getMessage()); }
    }

    private void usarGpsComoOrigen(boolean esConductor) {
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            map.post(() -> dibujarRutaViaje(esConductor)); return;
        }
        Handler th=new Handler(Looper.getMainLooper()); boolean[] ok={false};
        Runnable timeout=() -> { if (!ok[0]){ok[0]=true; map.post(() -> dibujarRutaViaje(esConductor));} };
        th.postDelayed(timeout,4000);
        fusedClient.getLastLocation()
                .addOnSuccessListener(loc -> { if (!ok[0]){ok[0]=true;th.removeCallbacks(timeout);
                    if (loc!=null) pOrigen=new GeoPoint(loc.getLatitude(),loc.getLongitude());
                    map.post(() -> dibujarRutaViaje(esConductor));} })
                .addOnFailureListener(e -> { if (!ok[0]){ok[0]=true;th.removeCallbacks(timeout); map.post(() -> dibujarRutaViaje(esConductor));} });
    }

    private int extraerIdUsuarioDeObj(JSONObject u) {
        JSONObject uo=u.optJSONObject("usuario");
        if (uo!=null) for (String k : new String[]{"idUsuarios","id","idUsuario"}) { int id=uo.optInt(k,-1); if(id>0) return id; }
        for (String k : new String[]{"idUsuarios","idUsuario"}) { int id=u.optInt(k,-1); if(id>0) return id; }
        return -1;
    }

    private float calcularRumbo(GeoPoint desde, GeoPoint hasta) {
        double lat1=Math.toRadians(desde.getLatitude()), lat2=Math.toRadians(hasta.getLatitude());
        double dLon=Math.toRadians(hasta.getLongitude()-desde.getLongitude());
        double x=Math.sin(dLon)*Math.cos(lat2);
        double y=Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLon);
        return (float)((Math.toDegrees(Math.atan2(x,y))+360)%360);
    }

    private double calcularDistanciaMetros(GeoPoint a, GeoPoint b) {
        double R=6371000;
        double dLat=Math.toRadians(b.getLatitude()-a.getLatitude());
        double dLon=Math.toRadians(b.getLongitude()-a.getLongitude());
        double sLat=Math.sin(dLat/2), sLon=Math.sin(dLon/2);
        double c=sLat*sLat+Math.cos(Math.toRadians(a.getLatitude()))*Math.cos(Math.toRadians(b.getLatitude()))*sLon*sLon;
        return R*2*Math.atan2(Math.sqrt(c),Math.sqrt(1-c));
    }

    private GeoPoint primerNoNulo(GeoPoint a, GeoPoint b) { return a!=null ? a : b; }

    private ArrayList<GeoPoint> rectaEntre(GeoPoint a, GeoPoint b) {
        ArrayList<GeoPoint> l=new ArrayList<>(); l.add(a); l.add(b); return l;
    }

    private int px(int dp) { return Math.round(dp*density()); }
    private float density() { return getResources().getDisplayMetrics().density; }
}