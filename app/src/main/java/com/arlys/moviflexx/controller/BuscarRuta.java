package com.arlys.moviflexx.controller;

import android.Manifest;
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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class BuscarRuta extends BaseActivity {

    private static final String TAG          = "BuscarRutaActivity";
    private static final int    REQ_LOCATION = 2001;

    // UI
    private TextView          txtMiUbicacion;
    private TextInputEditText editParada;
    private MaterialButton    btnBuscar;
    private LinearLayout      containerResultados;
    private ProgressBar       progressBuscar;
    private TextView          txtSinResultados;
    private MapView           miniMap;
    private TextView          txtEstadoGps;
    private View              rootView;

    // GPS
    private MyLocationNewOverlay myLocationOverlay;
    private GeoPoint             miUbicacion;
    private Marker               marcadorMiUbicacion;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /* ═══════════════════════════════════════════════════════
       CICLO DE VIDA
    ═══════════════════════════════════════════════════════ */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_buscar_ruta);

        rootView = findViewById(android.R.id.content);
        initViews();
        configurarMiniMapa();
        verificarPermisos();
        configurarBotones();
    }

    @Override protected void onResume() { super.onResume(); if (miniMap != null) miniMap.onResume(); }
    @Override protected void onPause()  { super.onPause();  if (miniMap != null) miniMap.onPause();  }

    /* ═══════════════════════════════════════════════════════
       INIT
    ═══════════════════════════════════════════════════════ */
    private void initViews() {
        txtMiUbicacion      = findViewById(R.id.txt_mi_ubicacion);
        editParada          = findViewById(R.id.edit_parada);
        btnBuscar           = findViewById(R.id.btn_buscar_rutas);
        containerResultados = findViewById(R.id.container_resultados);
        progressBuscar      = findViewById(R.id.progress_buscar);
        txtSinResultados    = findViewById(R.id.txt_sin_resultados);
        miniMap             = findViewById(R.id.mini_map_pasajero);
        txtEstadoGps        = findViewById(R.id.txt_estado_gps_pasajero);

        txtMiUbicacion.setText("⏳ Detectando tu ubicación...");

        ImageButton btnBack = findViewById(R.id.btn_back_buscar);
        if (btnBack != null) btnBack.setOnClickListener(v -> onBackPressed());
    }

    /* ═══════════════════════════════════════════════════════
       MINI MAPA (referencia visual)
    ═══════════════════════════════════════════════════════ */
    private void configurarMiniMapa() {
        if (miniMap == null) return;
        miniMap.setTileSource(TileSourceFactory.MAPNIK);
        miniMap.setMultiTouchControls(false);
        miniMap.setBuiltInZoomControls(false);
        miniMap.getController().setZoom(14.0);
        miniMap.getController().setCenter(new GeoPoint(2.4448, -76.6147));
        Configuration.getInstance().setOsmdroidTileCache(
                new File(getCacheDir(), "osmdroid_tiles"));
    }

    /* ═══════════════════════════════════════════════════════
       GPS — DETECTA UBICACIÓN AUTOMÁTICAMENTE
    ═══════════════════════════════════════════════════════ */
    private void verificarPermisos() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
        } else {
            activarGps();
        }
    }

    private void activarGps() {
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), miniMap);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        if (miniMap != null) miniMap.getOverlays().add(myLocationOverlay);

        myLocationOverlay.runOnFirstFix(() -> mainHandler.post(() -> {
            miUbicacion = myLocationOverlay.getMyLocation();
            if (miUbicacion == null) return;

            myLocationOverlay.disableFollowLocation();
            txtMiUbicacion.setText("" + obtenerDireccion(miUbicacion));

            if (txtEstadoGps != null) {
                txtEstadoGps.setText("GPS ✓");
                txtEstadoGps.setTextColor(Color.parseColor("#10B981"));
            }
            if (miniMap != null) {
                miniMap.getController().animateTo(miUbicacion);
                miniMap.getController().setZoom(15.0);
                ponerMarcadorMiUbicacion();
            }
            mostrarSnackbar("Ubicación detectada", false);
        }));
    }

    private void ponerMarcadorMiUbicacion() {
        if (miniMap == null || miUbicacion == null) return;
        if (marcadorMiUbicacion != null) miniMap.getOverlays().remove(marcadorMiUbicacion);
        marcadorMiUbicacion = new Marker(miniMap);
        marcadorMiUbicacion.setPosition(miUbicacion);
        marcadorMiUbicacion.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marcadorMiUbicacion.setTitle("Tu ubicación");
        marcadorMiUbicacion.setIcon(new BitmapDrawable(getResources(),
                crearIconoMarcador(0xFF009B8D, "Tú")));
        miniMap.getOverlays().add(marcadorMiUbicacion);
        miniMap.invalidate();
    }

    @Override
    public void onRequestPermissionsResult(int code,
                                           @NonNull String[] perms,
                                           @NonNull int[] grants) {
        super.onRequestPermissionsResult(code, perms, grants);
        if (code == REQ_LOCATION && grants.length > 0
                && grants[0] == PackageManager.PERMISSION_GRANTED) {
            activarGps();
        } else {
            txtMiUbicacion.setText("⚠️ Permiso de ubicación denegado");
            mostrarSnackbar("Sin permiso de ubicación", true);
        }
    }

    /* ═══════════════════════════════════════════════════════
       BOTONES
    ═══════════════════════════════════════════════════════ */
    private void configurarBotones() {
        btnBuscar.setOnClickListener(v -> buscarViajes());
    }

    /* ═══════════════════════════════════════════════════════
       BUSCAR VIAJES — usa Constantes.BUSCAR_VIAJES con fallback
    ═══════════════════════════════════════════════════════ */
    private void buscarViajes() {
        String parada = editParada.getText() != null
                ? editParada.getText().toString().trim() : "";

        if (parada.isEmpty()) {
            mostrarSnackbar(" Escribe dónde quieres subir o bajar", true);
            return;
        }
        if (miUbicacion == null) {
            mostrarSnackbar("⏳ Esperando GPS... intenta de nuevo", true);
            return;
        }

        setLoading(true);
        containerResultados.removeAllViews();
        txtSinResultados.setVisibility(View.GONE);

        // GET /api/viajes/buscar?estado=DISPONIBLE&latPasajero=...&lngPasajero=...&parada=...
        String url = Constantes.buscarViajes()
                + "?estado=DISPONIBLE"
                + "&latPasajero=" + miUbicacion.getLatitude()
                + "&lngPasajero=" + miUbicacion.getLongitude()
                + "&parada=" + android.net.Uri.encode(parada)
                + "&radioKm=5";


        // Intentar como array primero; si el backend devuelve objeto (Spring Page), usar getObject
        ConexionApi.getInstance(this).getArray(url,
                arrayResp -> {
                    setLoading(false);
                    procesarRespuesta(arrayResp, parada);
                },
                arrayErr -> {
                    ConexionApi.getInstance(this).getObject(url,
                            objResp -> {
                                setLoading(false);
                                procesarRespuestaObjeto(objResp, parada);
                            },
                            objErr -> {
                                Log.w(TAG, "BUSCAR_VIAJES falló, usando VIAJES: " + objErr.toString());
                                buscarFallback(parada);
                            }
                    );
                }
        );
    }

    /** Fallback: GET /api/viajes?estado=DISPONIBLE (sin filtro de parada) */
    private void buscarFallback(String parada) {
        ConexionApi.getInstance(this).getArray(
                Constantes.VIAJES + "?estado=DISPONIBLE",
                arrayResp -> {
                    setLoading(false);
                    procesarRespuesta(arrayResp, parada);
                },
                arrayErr -> {
                    ConexionApi.getInstance(this).getObject(
                            Constantes.VIAJES + "?estado=DISPONIBLE",
                            objResp -> {
                                setLoading(false);
                                procesarRespuestaObjeto(objResp, parada);
                            },
                            objErr -> {
                                setLoading(false);
                                Log.e(TAG, "Fallback falló: " + objErr.toString());
                                mostrarSnackbar("❌ Sin conexión. Verifica el servidor.", true);
                            }
                    );
                }
        );
    }

    /** El backend devolvió directamente un JSONArray */
    private void procesarRespuesta(JSONArray arr, String parada) {
        mostrarResultados(arr, parada);
    }

    /** El backend devolvió un JSONObject (Spring Page con "content", o wrapper) */
    private void procesarRespuestaObjeto(JSONObject obj, String parada) {
        try {
            JSONArray arr = null;
            if (obj.has("content"))  arr = obj.getJSONArray("content");
            else if (obj.has("viajes")) arr = obj.getJSONArray("viajes");
            else if (obj.has("rutas"))  arr = obj.getJSONArray("rutas");
            mostrarResultados(arr, parada);
        } catch (Exception e) {
            Log.e(TAG, "Error parseando objeto respuesta", e);
            mostrarSnackbar("❌ Error al procesar los datos", true);
        }
    }

    /* ═══════════════════════════════════════════════════════
       MOSTRAR LISTA DE RESULTADOS
    ═══════════════════════════════════════════════════════ */
    private void mostrarResultados(JSONArray items, String parada) {
        containerResultados.removeAllViews();

        if (items == null || items.length() == 0) {
            txtSinResultados.setVisibility(View.VISIBLE);
            txtSinResultados.setText("😔 No hay viajes disponibles cerca de\n\"" + parada + "\"");
            return;
        }
        txtSinResultados.setVisibility(View.GONE);

        for (int i = 0; i < items.length(); i++) {
            try {
                View card = crearCardViaje(items.getJSONObject(i), parada, i);
                containerResultados.addView(card);
                // animación de entrada escalonada
                card.setAlpha(0f);
                card.setTranslationY(30f);
                card.animate().alpha(1f).translationY(0f)
                        .setStartDelay(i * 80L).setDuration(300).start();
            } catch (Exception e) {
                Log.e(TAG, "Error card " + i, e);
            }
        }
    }

    private View crearCardViaje(JSONObject item, String paradaPasajero, int indice) {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.item_ruta_pasajero, containerResultados, false);

        // ── Leer campos (cubre naming de viaje y ruta del backend) ──
        int    idViaje     = item.optInt("idViaje",  item.optInt("id", 0));
        int    idRuta      = item.optInt("idRuta",   item.optInt("id", 0));
        String origen      = item.optString("origen",  item.optString("puntoOrigen",  "—"));
        String destino     = item.optString("destino", item.optString("puntoDestino", "—"));
        double distanciaKm = item.optDouble("distancia",  item.optDouble("distanciaKm", 0));
        double duracionMin = item.optDouble("duracion",   item.optDouble("duracionMin", 0));
        String tipo        = item.optString("tipoTransporte", "driving");
        double latO        = item.optDouble("latOrigen",  0);
        double lngO        = item.optDouble("lngOrigen",  0);
        double latD        = item.optDouble("latDestino", 0);
        double lngD        = item.optDouble("lngDestino", 0);
        double costo       = item.optDouble("precio", item.optDouble("costoCombustible", 0));
        int    asientos    = item.optInt("asientosDisponibles", item.optInt("cupos", 0));
        String hora        = item.optString("horaSalida", item.optString("fechaHora", ""));

        // ── Asignar textos ──
        setTxt(view, R.id.txt_origen_conductor,  "" + origen);
        setTxt(view, R.id.txt_destino_conductor, "" + destino);
        setTxt(view, R.id.txt_distancia_ruta,    String.format("%.1f km", distanciaKm));
        setTxt(view, R.id.txt_duracion_ruta,     String.format("%.0f min", duracionMin));
        setTxt(view, R.id.txt_vehiculo,          tipo.equals("motorcycle") ? "Moto" : "Carro");

        if (costo > 0)
            setTxt(view, R.id.txt_costo_ruta, String.format("$%,.0f COP", costo));
        if (asientos > 0)
            setTxt(view, R.id.txt_asientos, "" + asientos + " cupos");
        if (!hora.isEmpty()) {
            String horaCorta = hora.replace("T", " ");
            if (horaCorta.length() > 16) horaCorta = horaCorta.substring(0, 16);
            setTxt(view, R.id.txt_hora_salida, "" + horaCorta);
        }

        // ── Borde de color alternado ──
        int[] colores = {0xFF009B8D, 0xFF3B82F6, 0xFFF59E0B, 0xFF10B981, 0xFFEF4444};
        MaterialCardView card = view.findViewById(R.id.card_ruta_pasajero);
        if (card != null) {
            card.setStrokeColor(colores[indice % colores.length]);
            card.setStrokeWidth(3);
        }

        // ── Click → DetalleViajeActivity (ya tiene mapa, reserva, chat, paradas) ──
        final int fIdViaje = idViaje; // capturar como final para el lambda
        view.setOnClickListener(v -> {
            Intent i = new Intent(this, DetalleViajeActivity.class);
            // DetalleViajeActivity solo necesita ID_VIAJE — carga todo desde el backend
            i.putExtra("ID_VIAJE", fIdViaje);
            startActivity(i);
        });

        return view;
    }

    /* ═══════════════════════════════════════════════════════
       UTILIDADES
    ═══════════════════════════════════════════════════════ */
    private void setLoading(boolean loading) {
        progressBuscar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnBuscar.setEnabled(!loading);
        btnBuscar.setText(loading ? "Buscando..." : "BUSCAR RUTAS");
        btnBuscar.setAlpha(loading ? 0.7f : 1f);
    }

    private void setTxt(View parent, int id, String text) {
        TextView tv = parent.findViewById(id);
        if (tv != null) tv.setText(text);
    }

    private void mostrarSnackbar(String msg, boolean esError) {
        Snackbar sb = Snackbar.make(rootView, msg,
                esError ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT);
        sb.getView().setBackgroundColor(esError ? 0xFFB00020 : 0xFF1A2422);
        TextView tv = sb.getView().findViewById(
                com.google.android.material.R.id.snackbar_text);
        if (tv != null) { tv.setTextColor(Color.WHITE); tv.setTypeface(null, Typeface.BOLD); }
        sb.show();
    }

    private String obtenerDireccion(GeoPoint p) {
        try {
            Geocoder g = new Geocoder(this, Locale.getDefault());
            List<Address> list = g.getFromLocation(p.getLatitude(), p.getLongitude(), 1);
            if (list != null && !list.isEmpty()) {
                String linea = list.get(0).getAddressLine(0);
                return linea != null ? linea : "Tu ubicación actual";
            }
        } catch (Exception ignored) {}
        return "Tu ubicación actual";
    }

    private Bitmap crearIconoMarcador(int colorInt, String letra) {
        int size = 80;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(colorInt);
        c.drawCircle(size / 2f, size / 2f - 4, size / 2f - 8, p);
        p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4f);
        c.drawCircle(size / 2f, size / 2f - 4, size / 2f - 8, p);
        p.setStyle(Paint.Style.FILL); p.setTextSize(22f);
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER);
        c.drawText(letra, size / 2f, size / 2f + 7, p);
        return bmp;
    }

    // ─── SCREEN DESCRIPTOR ────────────────────────────────────────────────────

    @Override
    public String getNombrePantalla() {
        return "Búsqueda de Rutas";
    }

    @Override
    public String getDescripcionPantalla() {
        return "Hay un campo para escribir tu destino o parada. "
                + "También hay un mini mapa que muestra tu ubicación actual. "
                + "Abajo aparecerán los viajes disponibles cuando busques.";
    }

    @Override
    public String getOpcionesPantalla() {
        return "Puedes decir: quiero ir al centro, buscar viaje, "
                + "o decir el nombre de un destino. También puedes decir: ir atrás.";
    }
}