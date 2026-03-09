package com.arlys.moviflexx.controller;

import android.animation.ValueAnimator;
import android.content.Intent;
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

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;

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

public class MapaPasajero extends AppCompatActivity {

    private static final String TAG = "MapaPasajero";

    private static final String OSRM_PROPIO  =
            "https://optimizacionofrutas-production.up.railway.app";
    private static final String OSRM_PUBLICO =
            "https://router.project-osrm.org";

    private static final int COL_RUTA    = 0xFF009B8D;  // verde-teal: ruta completa del conductor
    private static final int COL_TRAMO   = 0xFFFF6F00;  // naranja: tramo conductor → pasajero
    private static final int COL_CONDUCT = 0xFF1565C0;  // azul: icono del conductor

    // ── Vistas ──────────────────────────────────────────────────────────────
    private MapView  map;
    private TextView tvEta;
    private TextView tvDistEta;

    // ── Datos del viaje ──────────────────────────────────────────────────────
    private int     idViaje       = 0;
    private boolean pasajeroRecogido = false;

    // Puntos de la ruta del conductor
    private GeoPoint pOrigen  = null;   // inicio de la ruta del conductor
    private GeoPoint pDestino = null;   // fin de la ruta del conductor

    // Puntos propios del pasajero
    private GeoPoint pSubida  = null;   // donde el conductor lo recoge
    private GeoPoint pBajada  = null;   // donde el conductor lo deja

    private String nomSubida     = "";
    private String nomBajada     = "";
    private String nomConductor  = "";
    private String nomDestinoRuta = "";

    private final ArrayList<GeoPoint> waypointsRuta = new ArrayList<>();

    // ── Marcadores ──────────────────────────────────────────────────────────
    private Marker marcadorSubida   = null;
    private Marker marcadorBajada   = null;
    private Marker marcadorConductorRT  = null;

    // ── Polylines ───────────────────────────────────────────────────────────
    private final List<Polyline> lineasPintadas = new ArrayList<>();
    private final List<Polyline> lineasEta      = new ArrayList<>();

    // ── Polling GPS conductor ────────────────────────────────────────────────
    private final Handler  hPoll = new Handler(Looper.getMainLooper());
    private Runnable       rPoll = null;
    private GeoPoint       posAnteriorConductor = null;
    private float          rumboConductor       = 0f;

    // ── Session ──────────────────────────────────────────────────────────────
    private SessionManager session;

    // =========================================================================
    //  LIFECYCLE
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_mapa_pasajero);

        session = new SessionManager(this);

        // ── Leer Intent ──────────────────────────────────────────────────────
        idViaje = getIntent().getIntExtra("ID_VIAJE", 0);

        if (idViaje == 0) {
            Toast.makeText(this, "Viaje inválido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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
        String nc = getIntent().getStringExtra("NOM_CONDUCTOR");
        nomSubida   = ns != null ? ns : "";
        nomBajada   = nb != null ? nb : "";
        nomConductor = nc != null ? nc : "Conductor";

        String estadoReserva = getIntent().getStringExtra("ESTADO_RESERVA");
        if ("RECOGIDO".equals(estadoReserva) || "COMPLETADO".equals(estadoReserva)) {
            pasajeroRecogido = true;
        }

        if (latO != 0) pOrigen  = new GeoPoint(latO, lngO);
        if (latD != 0) pDestino = new GeoPoint(latD, lngD);
        if (latS != 0) pSubida  = new GeoPoint(latS, lngS);
        if (latB != 0) pBajada  = new GeoPoint(latB, lngB);

        Log.d(TAG, "=== INTENT RECIBIDO ===");
        Log.d(TAG, "  ID_VIAJE       = " + idViaje);
        Log.d(TAG, "  pOrigen        = " + pOrigen);
        Log.d(TAG, "  pDestino       = " + pDestino);
        Log.d(TAG, "  pSubida        = " + pSubida);
        Log.d(TAG, "  pBajada        = " + pBajada);
        Log.d(TAG, "  ESTADO_RESERVA = " + estadoReserva);
        Log.d(TAG, "======================");

        // ── Mapa ─────────────────────────────────────────────────────────────
        map = findViewById(R.id.map_pasajero);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(true);
        map.setTilesScaledToDpi(true);
        map.setUseDataConnection(true);
        map.getController().setZoom(15.0);
        map.getController().setCenter(new GeoPoint(2.4419, -76.6063));

        // ── UI overlay ───────────────────────────────────────────────────────
        crearChipsEta();
        inflarBanner();

        // ── Datos ─────────────────────────────────────────────────────────────
        leerWaypointsDelIntent();
        resolverDatosYDibujar();

        // ── Polling GPS conductor ─────────────────────────────────────────────
        arrancarPollingConductor();
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        if (rPoll != null) hPoll.postDelayed(rPoll, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
        if (rPoll != null) hPoll.removeCallbacks(rPoll);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rPoll != null) hPoll.removeCallbacks(rPoll);
    }

    // =========================================================================
    //  RESOLVER DATOS Y DIBUJAR
    // =========================================================================

    /**
     * Si el Intent ya traía coords completas → dibuja directo.
     * Si faltan pOrigen/pDestino → los carga desde la API del viaje.
     * Si faltan pSubida/pBajada  → los carga desde la reserva del pasajero.
     */
    private void resolverDatosYDibujar() {
        boolean faltanRuta    = (pOrigen == null || pDestino == null);
        boolean faltanParadas = (pSubida == null && pBajada == null);

        if (faltanRuta || faltanParadas) {
            cargarDatosDesdeApi(faltanRuta, faltanParadas);
        } else {
            map.post(() -> dibujarRutaViaje());
        }
    }

    // =========================================================================
    //  CARGA DESDE API
    // =========================================================================

    private void cargarDatosDesdeApi(boolean faltanRuta, boolean faltanParadas) {
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viajeObj -> {
                    Log.d(TAG, "=== VIAJE API ===\n" + viajeObj.toString());

                    // ── 1. Origen / Destino de la ruta del conductor ──────────
                    if (faltanRuta) {
                        JSONObject rutaObj = viajeObj.optJSONObject("ruta");
                        JSONObject src     = rutaObj != null ? rutaObj : viajeObj;

                        if (pOrigen == null) {
                            double la = primerDouble(src,"latOrigen","latitudOrigen","latInicio","lat_origen");
                            double lo = primerDouble(src,"lngOrigen","longitudOrigen","lngInicio","lng_origen");
                            if (la != 0) pOrigen = new GeoPoint(la, lo);
                        }
                        if (pDestino == null) {
                            double la = primerDouble(src,"latDestino","latitudDestino","latFin","lat_destino");
                            double lo = primerDouble(src,"lngDestino","longitudDestino","lngFin","lng_destino");
                            if (la != 0) pDestino = new GeoPoint(la, lo);
                        }

                        // Fallback: paradas de la ruta
                        if (pOrigen == null || pDestino == null) {
                            JSONObject rutaFb = viajeObj.optJSONObject("ruta");
                            JSONArray paradasFb = rutaFb != null
                                    ? rutaFb.optJSONArray("paradas")
                                    : viajeObj.optJSONArray("paradas");
                            resolverOrigenDestinoDeParadas(paradasFb);
                        }

                        // Waypoints intermedios si aún no los tenemos
                        if (waypointsRuta.isEmpty()) {
                            JSONObject r2 = viajeObj.optJSONObject("ruta");
                            JSONArray  pw = r2 != null ? r2.optJSONArray("paradas") : null;
                            if (pw == null) pw = viajeObj.optJSONArray("paradas");
                            extraerWaypointsDeParadas(pw);
                        }
                    }

                    // ── 2. Subida / Bajada del pasajero ───────────────────────
                    if (faltanParadas) {
                        int idUsuario = session.getIdUsuario();
                        JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                        if (usuarios != null) {
                            extraerParadasPasajero(usuarios, idUsuario);
                        }
                    }

                    Log.d(TAG, "FINAL → pOrigen=" + pOrigen + " pDestino=" + pDestino
                            + " pSubida=" + pSubida + " pBajada=" + pBajada);

                    // Si las paradas del pasajero aún faltan, intentar endpoint reservas
                    if (pSubida == null && pBajada == null) {
                        cargarParadasPasajeroDesdeReservas();
                    }

                    map.post(() -> dibujarRutaViaje());
                },
                err -> {
                    Log.e(TAG, "cargarDatosDesdeApi error: " + err);
                    if (pSubida == null && pBajada == null) cargarParadasPasajeroDesdeReservas();
                    map.post(() -> dibujarRutaViaje());
                }
        );
    }

    /** Recorre el array "usuarios" del viaje para extraer subida/bajada del pasajero actual. */
    private void extraerParadasPasajero(JSONArray usuarios, int idUsuario) {
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
                    int id = usuObj.optInt(k,-1); if (id > 0) { idU = id; break; }
                }
            }
            if (idU <= 0) idU = u.optInt("idUsuarios", u.optInt("idUsuario", -1));
            if (idU != idUsuario) continue;

            double latS = 0, lngS = 0, latB = 0, lngB = 0;
            String nomS = "", nomB = "";

            // Subida
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

            // Bajada
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

            if (latS != 0 || latB != 0) {
                if (latS != 0) { pSubida = new GeoPoint(latS, lngS); nomSubida = nomS.isEmpty() ? "Tu punto de recogida" : nomS; }
                if (latB != 0) { pBajada = new GeoPoint(latB, lngB); nomBajada = nomB.isEmpty() ? "Tu parada de bajada"  : nomB; }
                Log.d(TAG, "Paradas pasajero desde usuarios[]: subida=" + pSubida + " bajada=" + pBajada);
                return;
            }
        }
    }

    /** Fallback: endpoint /api/viajes/{id}/reservas */
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
            Log.w(TAG, "No se encontraron paradas en ningún endpoint");
            runOnUiThread(() -> map.post(() -> dibujarRutaViaje()));
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
                            if (latS != 0) { pSubida = new GeoPoint(latS, lngS); nomSubida = nomS.isEmpty() ? "Tu punto de recogida" : nomS; }
                            if (latB != 0) { pBajada = new GeoPoint(latB, lngB); nomBajada = nomB.isEmpty() ? "Tu parada de bajada"  : nomB; }
                            runOnUiThread(() -> map.post(() -> dibujarRutaViaje()));
                            return;
                        }
                    }
                    intentarReservasPasajero(urls, index + 1);
                },
                err -> intentarReservasPasajero(urls, index + 1)
        );
    }

    // =========================================================================
    //  DIBUJO DE RUTA
    // =========================================================================

    private void dibujarRutaViaje() {
        GeoPoint desde = pOrigen  != null ? pOrigen  : pSubida;
        GeoPoint hasta = pDestino != null ? pDestino : pBajada;

        Log.d(TAG, "dibujarRutaViaje desde=" + desde + " hasta=" + hasta
                + " waypoints=" + waypointsRuta.size());

        if (desde == null && hasta == null) {
            agregarMarcadores();
            return;
        }
        if (desde == null || hasta == null) {
            agregarMarcadores();
            GeoPoint punto = desde != null ? desde : hasta;
            map.getController().animateTo(punto);
            map.getController().setZoom(16.0);
            return;
        }

        final GeoPoint            fDesde     = desde;
        final GeoPoint            fHasta     = hasta;
        final ArrayList<GeoPoint> fWaypoints = new ArrayList<>(waypointsRuta);

        new Thread(() -> {
            ArrayList<GeoPoint> ruta = osrmRutaConWaypoints(fDesde, fHasta, fWaypoints);
            if (ruta == null) ruta = rectaEntre(fDesde, fHasta);

            final ArrayList<GeoPoint> rutaFinal = ruta;
            runOnUiThread(() -> {
                for (Polyline p : lineasPintadas) map.getOverlays().remove(p);
                lineasPintadas.clear();

                agregarPolilinea(rutaFinal, COL_RUTA, 13f);
                agregarMarcadores();

                ArrayList<GeoPoint> todos = new ArrayList<>(rutaFinal);
                if (pSubida != null) todos.add(pSubida);
                if (pBajada != null) todos.add(pBajada);
                zoomBoundingBox(todos);
                map.invalidate();
                Log.d(TAG, "Ruta dibujada: " + rutaFinal.size() + " pts");
            });
        }).start();
    }

    // =========================================================================
    //  MARCADORES
    // =========================================================================

    private void agregarMarcadores() {
        // A: inicio ruta conductor
        if (pOrigen != null) {
            Marker m = new Marker(map);
            m.setPosition(pOrigen);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle("🏁 Inicio de ruta");
            m.setIcon(new BitmapDrawable(getResources(), circuloMarcador(0xFF4CAF50, "A")));
            map.getOverlays().add(m);
        }
        // B: fin ruta conductor
        if (pDestino != null) {
            Marker m = new Marker(map);
            m.setPosition(pDestino);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle("🏁 Final de ruta: " + (nomDestinoRuta.isEmpty() ? "Destino" : nomDestinoRuta));
            m.setIcon(new BitmapDrawable(getResources(), circuloMarcador(0xFFEF5350, "B")));
            map.getOverlays().add(m);
        }
        // P1: recogida (solo si aún no fue recogido)
        if (pSubida != null && !pasajeroRecogido) {
            if (marcadorSubida != null) map.getOverlays().remove(marcadorSubida);
            marcadorSubida = new Marker(map);
            marcadorSubida.setPosition(pSubida);
            marcadorSubida.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marcadorSubida.setTitle("🙋 Mi punto de recogida\n📍 " + nomSubida);
            marcadorSubida.setIcon(new BitmapDrawable(getResources(),
                    circuloMarcadorP1(0xFF2E7D32, "🙋")));
            map.getOverlays().add(marcadorSubida);
        }
        // Bajada
        if (pBajada != null) {
            if (marcadorBajada != null) map.getOverlays().remove(marcadorBajada);
            marcadorBajada = new Marker(map);
            marcadorBajada.setPosition(pBajada);
            marcadorBajada.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marcadorBajada.setTitle("🚏 Mi parada de bajada\n📍 " + nomBajada);
            // Naranja grande si ya fue recogido, azul pequeño si aún no
            marcadorBajada.setIcon(new BitmapDrawable(getResources(), pasajeroRecogido
                    ? circuloMarcadorGrande(0xFFFF6F00, "🚏")
                    : circuloMarcador(0xFF1565C0, "🚏")));
            map.getOverlays().add(marcadorBajada);
        }
        map.invalidate();
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
                            verificarRecogidaPorConductor(nuevaPos);
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

    /** Dibuja la línea naranja del conductor hacia el próximo punto del pasajero. */
    private void actualizarLineaNaranja(GeoPoint posConductor) {
        // Antes de recoger → va hacia pSubida; después → va hacia pBajada
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

                // El marcador del conductor siempre encima
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
                double durSeg  = ruta.optDouble("duration", 0);
                double distMet = ruta.optDouble("distance", 0);
                int    minutos = Math.max(1, (int) Math.ceil(durSeg / 60.0));
                double distKm  = distMet / 1000.0;

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

    /**
     * Cuando el conductor se acerca al punto de subida (<= 50 m),
     * el pasajero ve su marcador de subida desaparecer y la bajada
     * se vuelve el destino activo (naranja grande).
     */
    private void verificarRecogidaPorConductor(GeoPoint posConductor) {
        if (pasajeroRecogido || pSubida == null) return;
        if (calcularDistanciaMetros(posConductor, pSubida) <= 50) {
            pasajeroRecogido = true;

            if (marcadorSubida != null) {
                map.getOverlays().remove(marcadorSubida);
                marcadorSubida = null;
            }
            // Pin de bajada: naranja grande (destino activo)
            if (pBajada != null && marcadorBajada != null) {
                map.getOverlays().remove(marcadorBajada);
                marcadorBajada = new Marker(map);
                marcadorBajada.setPosition(pBajada);
                marcadorBajada.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marcadorBajada.setTitle("🚏 Tu parada de bajada\n📍 " + nomBajada);
                marcadorBajada.setIcon(new BitmapDrawable(getResources(),
                        circuloMarcadorGrande(0xFFFF6F00, "🚏")));
                map.getOverlays().add(marcadorBajada);
            }
            map.invalidate();
            Toast.makeText(this, "✅ ¡El conductor ya te recogió!", Toast.LENGTH_LONG).show();
            Log.d(TAG, "Pasajero recogido detectado en mapa pasajero");
        }
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

        String params  = "?overview=full&geometries=geojson";
        ArrayList<GeoPoint> pts = null;
        try { pts = osrmParsear(http(OSRM_PROPIO  + "/route/v1/driving/" + coords + params)); }
        catch (Exception e) { Log.w(TAG, "OSRM propio wp: " + e.getMessage()); }
        if (pts == null || pts.size() < 2) {
            try { pts = osrmParsear(http(OSRM_PUBLICO + "/route/v1/driving/" + coords + params)); }
            catch (Exception e) { Log.w(TAG, "OSRM publico wp: " + e.getMessage()); }
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
    //  BITMAPS
    // =========================================================================

    private Bitmap circuloMarcador(int colorFondo, String texto) {
        int sz = 96;
        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cv  = new Canvas(bmp);
        Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG); ps.setColor(Color.argb(80,0,0,0));
        cv.drawCircle(sz/2f+3, sz/2f+5, sz/2f-8, ps);
        Paint pf = new Paint(Paint.ANTI_ALIAS_FLAG); pf.setColor(colorFondo);
        cv.drawCircle(sz/2f, sz/2f-3, sz/2f-9, pf);
        Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG); pb.setColor(Color.WHITE);
        pb.setStyle(Paint.Style.STROKE); pb.setStrokeWidth(5f);
        cv.drawCircle(sz/2f, sz/2f-3, sz/2f-9, pb);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG); pt.setColor(Color.WHITE);
        pt.setTextSize(texto.length()>1?26f:38f); pt.setTypeface(Typeface.DEFAULT_BOLD);
        pt.setTextAlign(Paint.Align.CENTER);
        cv.drawText(texto, sz/2f, sz/2f+11, pt);
        return bmp;
    }

    private Bitmap circuloMarcadorGrande(int colorFondo, String texto) {
        int sz = 120;
        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cv  = new Canvas(bmp);
        Paint ph = new Paint(Paint.ANTI_ALIAS_FLAG); ph.setColor(Color.argb(120,255,255,255));
        cv.drawCircle(sz/2f, sz/2f-3, sz/2f-4, ph);
        Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG); ps.setColor(Color.argb(80,0,0,0));
        cv.drawCircle(sz/2f+3, sz/2f+6, sz/2f-14, ps);
        Paint pf = new Paint(Paint.ANTI_ALIAS_FLAG); pf.setColor(colorFondo);
        cv.drawCircle(sz/2f, sz/2f-3, sz/2f-14, pf);
        Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG); pb.setColor(Color.WHITE);
        pb.setStyle(Paint.Style.STROKE); pb.setStrokeWidth(6f);
        cv.drawCircle(sz/2f, sz/2f-3, sz/2f-14, pb);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG); pt.setColor(Color.WHITE);
        pt.setTextSize(texto.length()>1?32f:46f); pt.setTypeface(Typeface.DEFAULT_BOLD);
        pt.setTextAlign(Paint.Align.CENTER);
        cv.drawText(texto, sz/2f, sz/2f+13, pt);
        return bmp;
    }

    private Bitmap circuloMarcadorP1(int colorFondo, String texto) {
        int sz=108, r=42, cx=sz/2, cy=r+6, colaH=18;
        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cv  = new Canvas(bmp);
        Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG); ps.setColor(Color.argb(70,0,0,0));
        cv.drawCircle(cx+3, cy+5, r-2, ps);
        Paint pf = new Paint(Paint.ANTI_ALIAS_FLAG); pf.setColor(colorFondo);
        cv.drawCircle(cx, cy, r, pf);
        Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG); pb.setColor(Color.WHITE);
        pb.setStyle(Paint.Style.STROKE); pb.setStrokeWidth(5f);
        cv.drawCircle(cx, cy, r, pb);
        Paint pc = new Paint(Paint.ANTI_ALIAS_FLAG); pc.setColor(colorFondo);
        Path cola = new Path();
        cola.moveTo(cx-10, cy+r-4); cola.lineTo(cx+10, cy+r-4); cola.lineTo(cx, cy+r+colaH); cola.close();
        cv.drawPath(cola, pc);
        Paint pbc = new Paint(Paint.ANTI_ALIAS_FLAG); pbc.setColor(Color.WHITE);
        pbc.setStyle(Paint.Style.STROKE); pbc.setStrokeWidth(4f);
        Path colaBorde = new Path();
        colaBorde.moveTo(cx-10, cy+r-2); colaBorde.lineTo(cx, cy+r+colaH); colaBorde.lineTo(cx+10, cy+r-2);
        cv.drawPath(colaBorde, pbc);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG); pt.setColor(Color.WHITE);
        pt.setTextSize(texto.length()>2?22f:28f); pt.setTypeface(Typeface.DEFAULT_BOLD);
        pt.setTextAlign(Paint.Align.CENTER);
        cv.drawText(texto, cx, cy-(pt.descent()+pt.ascent())/2, pt);
        return bmp;
    }

    private Bitmap crearIconoCarro(int color, float rumbo) {
        int sz = 84;
        Bitmap base = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cv   = new Canvas(base);
        Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG); ps.setColor(Color.argb(55,0,0,0));
        cv.drawCircle(sz/2f+3, sz/2f+5, sz/2f-7, ps);
        Paint pbg = new Paint(Paint.ANTI_ALIAS_FLAG); pbg.setColor(Color.WHITE);
        cv.drawCircle(sz/2f, sz/2f, sz/2f-6, pbg);
        Paint pc = new Paint(Paint.ANTI_ALIAS_FLAG); pc.setColor(color);
        cv.drawCircle(sz/2f, sz/2f, sz/2f-9, pc);
        Paint pw = new Paint(Paint.ANTI_ALIAS_FLAG); pw.setColor(Color.WHITE); pw.setStyle(Paint.Style.FILL);
        float cx=sz/2f, cy=sz/2f, semi=sz*0.17f, nose=sz*0.28f, cola=sz*0.20f;
        android.graphics.RectF cuerpo = new android.graphics.RectF(cx-semi, cy-cola, cx+semi, cy+nose*0.55f);
        cv.drawRoundRect(cuerpo, semi*0.5f, semi*0.5f, pw);
        Path morro = new Path();
        morro.moveTo(cx, cy-nose); morro.lineTo(cx-semi, cy-cola*0.15f);
        morro.lineTo(cx+semi, cy-cola*0.15f); morro.close();
        cv.drawPath(morro, pw);
        Bitmap rotado = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cvR = new Canvas(rotado);
        Matrix matrix = new Matrix(); matrix.setRotate(rumbo, sz/2f, sz/2f);
        cvR.drawBitmap(base, matrix, null); base.recycle();
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
        tvEta.setPadding((int)(14*dp),(int)(8*dp),(int)(14*dp),(int)(8*dp));
        tvEta.setVisibility(android.view.View.GONE);
        android.graphics.drawable.GradientDrawable bg1 = new android.graphics.drawable.GradientDrawable();
        bg1.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg1.setCornerRadius(30*dp); bg1.setColor(Color.parseColor("#CC1565C0"));
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
        tvDistEta.setPadding((int)(10*dp),(int)(6*dp),(int)(10*dp),(int)(6*dp));
        tvDistEta.setVisibility(android.view.View.GONE);
        android.graphics.drawable.GradientDrawable bg2 = new android.graphics.drawable.GradientDrawable();
        bg2.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg2.setCornerRadius(20*dp); bg2.setColor(Color.parseColor("#CCFF6F00"));
        tvDistEta.setBackground(bg2);
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp2.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp2.bottomMargin = (int)(122*dp);
        root.addView(tvDistEta, lp2);
    }

    // =========================================================================
    //  BANNER SUPERIOR
    // =========================================================================

    private void inflarBanner() {
        float dp  = getResources().getDisplayMetrics().density;
        int   p12 = (int)(12*dp), p8 = (int)(8*dp);

        FrameLayout root = (FrameLayout) findViewById(android.R.id.content);
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(p12, (int)(36*dp), p12, p8);

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.argb(220, 0, 77, 64));
        banner.setBackground(bg);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        banner.setLayoutParams(lp);

        // Etiqueta "VIAJE EN CURSO"
        TextView tvE = new TextView(this);
        tvE.setText("🚗  VIAJE EN CURSO");
        tvE.setTextColor(Color.parseColor("#A8EEE6"));
        tvE.setTextSize(10f);
        tvE.setTypeface(null, Typeface.BOLD);
        tvE.setLetterSpacing(0.1f);
        banner.addView(tvE);

        // Ruta: origen → destino
        TextView tvR = new TextView(this);
        tvR.setText("📍 " + (nomSubida.isEmpty() ? "Origen" : nomSubida)
                + "  →  " + (nomDestinoRuta.isEmpty() ? (nomBajada.isEmpty() ? "Destino" : nomBajada) : nomDestinoRuta));
        tvR.setTextColor(Color.WHITE);
        tvR.setTextSize(14f);
        tvR.setTypeface(null, Typeface.BOLD);
        tvR.setMaxLines(1);
        tvR.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lpR = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpR.topMargin = (int)(4*dp);
        tvR.setLayoutParams(lpR);
        banner.addView(tvR);

        // Leyenda de colores
        LinearLayout ley = new LinearLayout(this);
        ley.setOrientation(LinearLayout.HORIZONTAL);
        ley.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpL.topMargin = (int)(5*dp);
        ley.setLayoutParams(lpL);

        TextView tv1 = new TextView(this);
        tv1.setText("━━ Ruta completa");
        tv1.setTextColor(Color.parseColor("#4DD0E1"));
        tv1.setTextSize(10f); tv1.setTypeface(null, Typeface.BOLD);
        ley.addView(tv1);
        TextView tvSp = new TextView(this); tvSp.setText("   "); ley.addView(tvSp);
        TextView tv2 = new TextView(this);
        tv2.setText("━━ Conductor");
        tv2.setTextColor(Color.parseColor("#FFB74D"));
        tv2.setTextSize(10f); tv2.setTypeface(null, Typeface.BOLD);
        ley.addView(tv2);
        banner.addView(ley);

        // Mis paradas
        TextView tvP = new TextView(this);
        tvP.setText("🙋 " + (nomSubida.isEmpty() ? "—" : nomSubida)
                + "   🚏 " + (nomBajada.isEmpty() ? "—" : nomBajada));
        tvP.setTextColor(Color.parseColor("#E0F7FA"));
        tvP.setTextSize(11f);
        tvP.setMaxLines(1);
        tvP.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lpP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpP.topMargin = (int)(4*dp); lpP.bottomMargin = (int)(3*dp);
        tvP.setLayoutParams(lpP);
        banner.addView(tvP);

        root.addView(banner);
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
        double mnLat=Double.MAX_VALUE, mxLat=-Double.MAX_VALUE;
        double mnLng=Double.MAX_VALUE, mxLng=-Double.MAX_VALUE;
        for (GeoPoint p : puntos) {
            mnLat = Math.min(mnLat, p.getLatitude());  mxLat = Math.max(mxLat, p.getLatitude());
            mnLng = Math.min(mnLng, p.getLongitude()); mxLng = Math.max(mxLng, p.getLongitude());
        }
        double pLat = Math.max((mxLat-mnLat)*0.25, 0.006);
        double pLng = Math.max((mxLng-mnLng)*0.25, 0.006);
        BoundingBox bb = new BoundingBox(mxLat+pLat, mxLng+pLng, mnLat-pLat, mnLng-pLng);
        map.post(() -> { try { map.zoomToBoundingBox(bb, true, 100); } catch (Exception ignored) {} });
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================

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
            if (la!=0) { pOrigen=new GeoPoint(la,lo);
                if (nomSubida.isEmpty()) nomSubida=mejorO.optString("nombre",""); }
        }
        if (pDestino == null && mejorD != null && mejorD != mejorO) {
            double la=mejorD.optDouble("lat",0), lo=mejorD.optDouble("lng",0);
            if (la!=0) { pDestino=new GeoPoint(la,lo);
                if (nomDestinoRuta.isEmpty()) nomDestinoRuta=mejorD.optString("nombre",""); }
        }
    }

    private void extraerWaypointsDeParadas(JSONArray paradas) {
        if (paradas == null) return;
        int minOrden = Integer.MAX_VALUE, maxOrden = -1;
        for (int i = 0; i < paradas.length(); i++) {
            JSONObject p = paradas.optJSONObject(i); if (p==null) continue;
            String tipo = p.optString("tipo","").toUpperCase().trim();
            if ("BAJADA".equals(tipo) || "SUBIDA".equals(tipo)) continue;
            int orden = p.optInt("orden",i);
            minOrden = Math.min(minOrden, orden);
            maxOrden = Math.max(maxOrden, orden);

        }
        for (int i = 0; i < paradas.length(); i++) {
            JSONObject p = paradas.optJSONObject(i); if (p==null) continue;
            String tipo = p.optString("tipo","").toUpperCase().trim();
            if ("BAJADA".equals(tipo) || "SUBIDA".equals(tipo)) continue;
            int orden = p.optInt("orden",i);
            if (orden == minOrden || orden == maxOrden) continue;
            double la=p.optDouble("lat",0), lo=p.optDouble("lng",0);
            if (la!=0) waypointsRuta.add(new GeoPoint(la, lo));
        }
    }

    private void leerWaypointsDelIntent() {
        waypointsRuta.clear();
        String paradasJson = getIntent().getStringExtra("PARADAS_JSON");
        if (paradasJson == null || paradasJson.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(paradasJson);
            for (int i = 1; i < arr.length()-1; i++) {
                JSONObject p = arr.getJSONObject(i);
                double lat = p.optDouble("lat", p.optDouble("latitud",0));
                double lng = p.optDouble("lng", p.optDouble("longitud",0));
                if (lat != 0) waypointsRuta.add(new GeoPoint(lat, lng));
            }
        } catch (Exception e) { Log.w(TAG, "leerWaypointsDelIntent: " + e.getMessage()); }
    }

    private double primerDouble(JSONObject obj, String... campos) {
        for (String c : campos) { double v=obj.optDouble(c,0); if (v!=0) return v; }
        return 0;
    }

    private float calcularRumbo(GeoPoint desde, GeoPoint hasta) {
        double lat1=Math.toRadians(desde.getLatitude());
        double lat2=Math.toRadians(hasta.getLatitude());
        double dLon=Math.toRadians(hasta.getLongitude()-desde.getLongitude());
        double x=Math.sin(dLon)*Math.cos(lat2);
        double y=Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLon);
        return (float)((Math.toDegrees(Math.atan2(x,y))+360)%360);
    }

    private double calcularDistanciaMetros(GeoPoint a, GeoPoint b) {
        double R=6371000;
        double dLat=Math.toRadians(b.getLatitude()-a.getLatitude());
        double dLon=Math.toRadians(b.getLongitude()-a.getLongitude());
        double sinLat=Math.sin(dLat/2), sinLon=Math.sin(dLon/2);
        double c=sinLat*sinLat+Math.cos(Math.toRadians(a.getLatitude()))
                *Math.cos(Math.toRadians(b.getLatitude()))*sinLon*sinLon;
        return R*2*Math.atan2(Math.sqrt(c),Math.sqrt(1-c));
    }

    private ArrayList<GeoPoint> rectaEntre(GeoPoint a, GeoPoint b) {
        ArrayList<GeoPoint> l = new ArrayList<>(); l.add(a); l.add(b); return l;
    }

    private String http(String urlStr) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestProperty("User-Agent", "Moviflexx/1.0");
            c.setConnectTimeout(12000); c.setReadTimeout(12000);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder(); String ln;
            while ((ln = r.readLine()) != null) sb.append(ln);
            r.close(); return sb.toString();
        } finally { if (c != null) c.disconnect(); }
    }
}