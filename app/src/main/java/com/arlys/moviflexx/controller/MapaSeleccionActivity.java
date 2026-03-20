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
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class MapaSeleccionActivity extends AppCompatActivity {

    private static final String TAG           = "MapaSeleccion";
    private static final int    PERM_LOCATION = 3001;

    // ── Umbral: si el dedo se mueve más de esto, es pan (no tap) ─────────
    private static final float UMBRAL_MOVIMIENTO_DP = 10f;

    // ── Vistas ────────────────────────────────────────────────────────────
    private MapView        map;
    private Marker         marcadorS;
    private TextView       tvDireccion;
    private MaterialButton btnConfirmar;
    private MaterialButton btnMiUbi;
    private ProgressBar    loader;

    // ── Estado selección ──────────────────────────────────────────────────
    private double latSel = Double.NaN;
    private double lngSel = Double.NaN;
    private String nomSel = "";

    // ── Puntos de la ruta (para snap) ─────────────────────────────────────
    private final ArrayList<GeoPoint> puntosRuta = new ArrayList<>();

    private FusedLocationProviderClient fusedClient;

    // ── Touch tracking ────────────────────────────────────────────────────
    private float touchDownX = 0, touchDownY = 0;
    private float umbralPx = 0;

    // =========================================================================
    //  LIFECYCLE
    // =========================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        umbralPx = UMBRAL_MOVIMIENTO_DP * getResources().getDisplayMetrics().density;

        construirUI();

        // Centrar mapa
        double latC = getIntent().getDoubleExtra("lat_centro", 2.4419);
        double lngC = getIntent().getDoubleExtra("lng_centro", -76.6063);
        map.getController().setZoom(15.5);
        map.getController().setCenter(new GeoPoint(latC, lngC));

        // Dibujar ruta PRIMERO
        final String geojson = getIntent().getStringExtra("geojson_ruta");
        if (geojson != null && !geojson.isEmpty()) {
            map.post(() -> dibujarRutaEnMapa(geojson));
        }

        // Marcadores A y B
        final double latOrigen  = getIntent().getDoubleExtra("lat_centro",  Double.NaN);
        final double lngOrigen  = getIntent().getDoubleExtra("lng_centro",  Double.NaN);
        final double latDestino = getIntent().getDoubleExtra("lat_destino", Double.NaN);
        final double lngDestino = getIntent().getDoubleExtra("lng_destino", Double.NaN);
        map.post(() -> {
            if (!Double.isNaN(latOrigen))  agregarMarcadorFijo(latOrigen,  lngOrigen,  "A", Color.parseColor("#4CAF50"));
            if (!Double.isNaN(latDestino)) agregarMarcadorFijo(latDestino, lngDestino, "B", Color.parseColor("#EF5350"));
        });

        // Punto previo
        double latP = getIntent().getDoubleExtra("lat_previa", Double.NaN);
        double lngP = getIntent().getDoubleExtra("lng_previa", Double.NaN);
        if (!Double.isNaN(latP) && !Double.isNaN(lngP)) {
            map.post(() -> ponerMarcador(new GeoPoint(latP, lngP)));
        }

        configurarToqueMapa();
    }

    @Override protected void onResume()  { super.onResume();  if (map != null) map.onResume();  }
    @Override protected void onPause()   { super.onPause();   if (map != null) map.onPause();   }
    @Override protected void onDestroy() { super.onDestroy(); }

    // =========================================================================
    //  CONSTRUIR UI
    // =========================================================================
    private void construirUI() {
        float d   = getResources().getDisplayMetrics().density;
        int   p16 = (int)(16*d), p12 = (int)(12*d), p8 = (int)(8*d);

        FrameLayout root = new FrameLayout(this);
        setContentView(root);

        map = new MapView(this);
        map.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.setFlingEnabled(true);
        map.setMinZoomLevel(4.0);
        map.setMaxZoomLevel(20.0);
        root.addView(map);

        // Banner superior
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams lpBanner = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lpBanner.gravity = Gravity.TOP;
        banner.setLayoutParams(lpBanner);
        banner.setPadding(p12, (int)(44*d), p12, p12);
        banner.setBackgroundColor(Color.argb(220, 0, 137, 123));

        MaterialButton btnAtras = new MaterialButton(this);
        btnAtras.setText("←");
        btnAtras.setTextSize(20f);
        btnAtras.setTextColor(Color.WHITE);
        btnAtras.setBackgroundColor(Color.TRANSPARENT);
        btnAtras.setInsetTop(0); btnAtras.setInsetBottom(0);
        LinearLayout.LayoutParams lpAt = new LinearLayout.LayoutParams((int)(46*d), (int)(46*d));
        lpAt.setMargins(0, 0, p8, 0);
        btnAtras.setLayoutParams(lpAt);
        btnAtras.setOnClickListener(v -> finish());
        banner.addView(btnAtras);

        TextView tvIns = new TextView(this);
        tvIns.setText("Toca la ruta para marcar tu punto de subida");
        tvIns.setTextSize(13.5f);
        tvIns.setTextColor(Color.WHITE);
        tvIns.setTypeface(null, Typeface.BOLD);
        tvIns.setGravity(Gravity.CENTER);
        tvIns.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        banner.addView(tvIns);
        root.addView(banner);

        // Card inferior
        MaterialCardView card = new MaterialCardView(this);
        FrameLayout.LayoutParams lpCard = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lpCard.gravity = Gravity.BOTTOM;
        card.setLayoutParams(lpCard);
        card.setRadius(24*d);
        card.setCardElevation(16*d);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p16, (int)(14*d), p16, (int)(28*d));

        View handle = new View(this);
        LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams((int)(40*d), (int)(4*d));
        lpH.gravity = Gravity.CENTER_HORIZONTAL;
        lpH.setMargins(0, 0, 0, p12);
        handle.setLayoutParams(lpH);
        GradientDrawable bgH = new GradientDrawable();
        bgH.setShape(GradientDrawable.RECTANGLE);
        bgH.setCornerRadius(4*d);
        bgH.setColor(Color.parseColor("#BDBDBD"));
        handle.setBackground(bgH);
        inner.addView(handle);

        TextView tvEtiq = new TextView(this);
        tvEtiq.setText("PUNTO DE SUBIDA SELECCIONADO");
        tvEtiq.setTextSize(9f);
        tvEtiq.setTypeface(null, Typeface.BOLD);
        tvEtiq.setTextColor(Color.parseColor("#80CBC4"));
        tvEtiq.setLetterSpacing(0.1f);
        tvEtiq.setPadding(0, 0, 0, (int)(6*d));
        tvEtiq.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        inner.addView(tvEtiq);

        loader = new ProgressBar(this);
        loader.setVisibility(View.GONE);
        LinearLayout.LayoutParams lpLdr = new LinearLayout.LayoutParams((int)(22*d), (int)(22*d));
        lpLdr.setMargins(0, 0, 0, (int)(4*d));
        loader.setLayoutParams(lpLdr);
        inner.addView(loader);

        tvDireccion = new TextView(this);
        tvDireccion.setText("Toca sobre la ruta para elegir tu punto");
        tvDireccion.setTextSize(15f);
        tvDireccion.setTextColor(Color.parseColor("#546E7A"));
        tvDireccion.setLineSpacing(0f, 1.3f);
        LinearLayout.LayoutParams lpDir = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpDir.setMargins(0, 0, 0, p16);
        tvDireccion.setLayoutParams(lpDir);
        inner.addView(tvDireccion);

        LinearLayout filaBtns = new LinearLayout(this);
        filaBtns.setOrientation(LinearLayout.HORIZONTAL);
        filaBtns.setGravity(Gravity.CENTER_VERTICAL);
        filaBtns.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        btnMiUbi = new MaterialButton(this);
        btnMiUbi.setText("📍 Mi ubicación");
        btnMiUbi.setTextSize(12f);
        btnMiUbi.setTextColor(Color.parseColor("#00897B"));
        btnMiUbi.setBackgroundColor(Color.TRANSPARENT);
        btnMiUbi.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#00897B")));
        btnMiUbi.setStrokeWidth((int)(1.5f*d));
        btnMiUbi.setCornerRadius((int)(12*d));
        LinearLayout.LayoutParams lpMiUbi = new LinearLayout.LayoutParams(0, (int)(50*d), 1f);
        lpMiUbi.setMargins(0, 0, (int)(8*d), 0);
        btnMiUbi.setLayoutParams(lpMiUbi);
        btnMiUbi.setInsetTop(0); btnMiUbi.setInsetBottom(0);
        btnMiUbi.setOnClickListener(v -> usarMiUbicacion());
        filaBtns.addView(btnMiUbi);

        btnConfirmar = new MaterialButton(this);
        btnConfirmar.setText("Confirmar subida");
        btnConfirmar.setTextSize(14f);
        btnConfirmar.setTextColor(Color.WHITE);
        btnConfirmar.setTypeface(null, Typeface.BOLD);
        btnConfirmar.setBackgroundColor(Color.parseColor("#B0BEC5"));
        btnConfirmar.setCornerRadius((int)(12*d));
        btnConfirmar.setEnabled(false);
        LinearLayout.LayoutParams lpConf = new LinearLayout.LayoutParams(0, (int)(50*d), 2f);
        btnConfirmar.setLayoutParams(lpConf);
        btnConfirmar.setInsetTop(0); btnConfirmar.setInsetBottom(0);
        btnConfirmar.setOnClickListener(v -> confirmarSeleccion());
        filaBtns.addView(btnConfirmar);

        inner.addView(filaBtns);
        card.addView(inner);
        root.addView(card);
    }

    // =========================================================================
    //  DIBUJAR RUTA + guardar puntos para snap
    // =========================================================================
    private void dibujarRutaEnMapa(String geojson) {
        if (geojson == null || geojson.isEmpty()) return;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(geojson);
            org.json.JSONArray coords = obj.optJSONArray("coordinates");
            if (coords == null) {
                org.json.JSONObject geom = obj.optJSONObject("geometry");
                if (geom != null) coords = geom.optJSONArray("coordinates");
            }
            if (coords == null) {
                org.json.JSONArray features = obj.optJSONArray("features");
                if (features != null && features.length() > 0) {
                    org.json.JSONObject feat = features.optJSONObject(0);
                    if (feat != null) {
                        org.json.JSONObject geom = feat.optJSONObject("geometry");
                        if (geom != null) coords = geom.optJSONArray("coordinates");
                    }
                }
            }
            if (coords == null || coords.length() < 2) return;

            puntosRuta.clear();
            for (int i = 0; i < coords.length(); i++) {
                org.json.JSONArray par = coords.getJSONArray(i);
                puntosRuta.add(new GeoPoint(par.getDouble(1), par.getDouble(0)));
            }

            // Sombra → borde → línea (en ese orden, al final del stack)
            org.osmdroid.views.overlay.Polyline sombra = new org.osmdroid.views.overlay.Polyline(map);
            sombra.setPoints(new ArrayList<>(puntosRuta));
            sombra.setColor(Color.argb(55, 0, 0, 0)); sombra.setWidth(23f);
            map.getOverlays().add(sombra);

            org.osmdroid.views.overlay.Polyline borde = new org.osmdroid.views.overlay.Polyline(map);
            borde.setPoints(new ArrayList<>(puntosRuta));
            borde.setColor(Color.WHITE); borde.setWidth(19f);
            map.getOverlays().add(borde);

            org.osmdroid.views.overlay.Polyline linea = new org.osmdroid.views.overlay.Polyline(map);
            linea.setPoints(new ArrayList<>(puntosRuta));
            linea.setColor(Color.parseColor("#009B8D")); linea.setWidth(13f);
            map.getOverlays().add(linea);

            // Zoom
            double mnLat = Double.MAX_VALUE, mxLat = -Double.MAX_VALUE;
            double mnLng = Double.MAX_VALUE, mxLng = -Double.MAX_VALUE;
            for (GeoPoint p : puntosRuta) {
                mnLat = Math.min(mnLat, p.getLatitude());  mxLat = Math.max(mxLat, p.getLatitude());
                mnLng = Math.min(mnLng, p.getLongitude()); mxLng = Math.max(mxLng, p.getLongitude());
            }
            double pLat = Math.max((mxLat-mnLat)*0.25, 0.006);
            double pLng = Math.max((mxLng-mnLng)*0.25, 0.006);
            final org.osmdroid.util.BoundingBox bb = new org.osmdroid.util.BoundingBox(
                    mxLat+pLat, mxLng+pLng, mnLat-pLat, mnLng-pLng);
            map.post(() -> { try { map.zoomToBoundingBox(bb, true, 100); } catch (Exception ignored) {} });
            map.invalidate();
        } catch (Exception e) {
            Log.w(TAG, "dibujarRutaEnMapa error: " + e.getMessage());
        }
    }

    // =========================================================================
    //  MARCADORES A / B
    // =========================================================================
    private void agregarMarcadorFijo(double lat, double lng, String letra, int color) {
        Marker m = new Marker(map);
        m.setPosition(new GeoPoint(lat, lng));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle(letra.equals("A") ? "Origen" : "Destino");
        m.setIcon(new BitmapDrawable(getResources(), crearBitmapLetra(letra, color)));
        map.getOverlays().add(m);
        map.invalidate();
    }

    private Bitmap crearBitmapLetra(String letra, int color) {
        float d  = getResources().getDisplayMetrics().density;
        int   sz = (int)(44 * d);
        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas c   = new Canvas(bmp);
        Paint pC = new Paint(Paint.ANTI_ALIAS_FLAG); pC.setColor(color);
        c.drawCircle(sz/2f, sz/2f-4, sz/2f-8, pC);
        Paint pB = new Paint(Paint.ANTI_ALIAS_FLAG); pB.setColor(Color.WHITE);
        pB.setStyle(Paint.Style.STROKE); pB.setStrokeWidth(5f);
        c.drawCircle(sz/2f, sz/2f-4, sz/2f-8, pB);
        Paint pT = new Paint(Paint.ANTI_ALIAS_FLAG); pT.setColor(Color.WHITE);
        pT.setTextSize(sz * 0.36f); pT.setTypeface(Typeface.DEFAULT_BOLD);
        pT.setTextAlign(Paint.Align.CENTER);
        c.drawText(letra, sz/2f, sz/2f + sz*0.14f, pT);
        return bmp;
    }

    // =========================================================================
    //  FIX TOQUE: distingue TAP de PAN usando umbral de movimiento
    // =========================================================================
    private void configurarToqueMapa() {
        map.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Guardar posición inicial del toque
                    touchDownX = event.getX();
                    touchDownY = event.getY();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;

                case MotionEvent.ACTION_MOVE:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;

                case MotionEvent.ACTION_UP:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    float dx = Math.abs(event.getX() - touchDownX);
                    float dy = Math.abs(event.getY() - touchDownY);
                    // Solo es un TAP si el dedo apenas se movió
                    if (dx < umbralPx && dy < umbralPx) {
                        org.osmdroid.views.Projection proj = map.getProjection();
                        GeoPoint tocado = (GeoPoint) proj.fromPixels((int) event.getX(), (int) event.getY());
                        if (tocado != null) {
                            // Snap al punto más cercano de la ruta
                            GeoPoint snap = puntoMasCercanoEnRuta(tocado);
                            ponerMarcador(snap != null ? snap : tocado);
                        }
                    }
                    break;

                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false; // false = el MapView sigue procesando zoom/pan
        });
    }

    // =========================================================================
    //  SNAP AL PUNTO MÁS CERCANO DE LA RUTA
    // =========================================================================
    private GeoPoint puntoMasCercanoEnRuta(GeoPoint tocado) {
        if (puntosRuta.isEmpty()) return null;
        GeoPoint mejor = null;
        double menorDist = Double.MAX_VALUE;
        for (GeoPoint rp : puntosRuta) {
            double dLat = rp.getLatitude()  - tocado.getLatitude();
            double dLng = rp.getLongitude() - tocado.getLongitude();
            double dist = dLat*dLat + dLng*dLng;
            if (dist < menorDist) { menorDist = dist; mejor = rp; }
        }
        // Solo hacer snap si está a menos de ~2km del punto más cercano
        return (menorDist < 0.0004) ? mejor : tocado;
    }

    // =========================================================================
    //  PONER MARCADOR S
    // =========================================================================
    private void ponerMarcador(GeoPoint punto) {
        if (marcadorS != null) { map.getOverlays().remove(marcadorS); marcadorS = null; }
        latSel = punto.getLatitude();
        lngSel = punto.getLongitude();

        marcadorS = new Marker(map);
        marcadorS.setPosition(punto);
        marcadorS.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marcadorS.setTitle("Tu subida");
        marcadorS.setIcon(new BitmapDrawable(getResources(), crearBitmapMarcadorS()));
        map.getOverlays().add(marcadorS);
        map.getController().animateTo(punto);
        map.invalidate();

        tvDireccion.setText("📍 Obteniendo dirección...");
        tvDireccion.setTextColor(Color.parseColor("#546E7A"));
        tvDireccion.setTypeface(null, Typeface.NORMAL);
        loader.setVisibility(View.VISIBLE);
        btnConfirmar.setEnabled(false);
        btnConfirmar.setBackgroundColor(Color.parseColor("#B0BEC5"));
        btnConfirmar.setText("Confirmar subida");

        geocodificarInverso(punto);
    }

    private Bitmap crearBitmapMarcadorS() {
        float d  = getResources().getDisplayMetrics().density;
        int   sz = (int)(44 * d);
        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas c   = new Canvas(bmp);
        Paint pS = new Paint(Paint.ANTI_ALIAS_FLAG); pS.setColor(Color.argb(55,0,0,0));
        c.drawCircle(sz/2f+3, sz/2f+5, sz/2f-8, pS);
        Paint pC = new Paint(Paint.ANTI_ALIAS_FLAG); pC.setColor(Color.parseColor("#00C853"));
        c.drawCircle(sz/2f, sz/2f-4, sz/2f-8, pC);
        Paint pB = new Paint(Paint.ANTI_ALIAS_FLAG); pB.setColor(Color.WHITE);
        pB.setStyle(Paint.Style.STROKE); pB.setStrokeWidth(5f);
        c.drawCircle(sz/2f, sz/2f-4, sz/2f-8, pB);
        Paint pT = new Paint(Paint.ANTI_ALIAS_FLAG); pT.setColor(Color.WHITE);
        pT.setTextSize(sz * 0.36f); pT.setTypeface(Typeface.DEFAULT_BOLD);
        pT.setTextAlign(Paint.Align.CENTER);
        c.drawText("S", sz/2f, sz/2f + sz*0.14f, pT);
        return bmp;
    }

    // =========================================================================
    //  GEOCODING INVERSO — con User-Agent correcto para Nominatim
    // =========================================================================
    private void geocodificarInverso(GeoPoint gp) {
        final double lat = gp.getLatitude();
        final double lng = gp.getLongitude();
        new Thread(() -> {
            String resultado = "";
            try {
                String url = "https://nominatim.openstreetmap.org/reverse"
                        + "?lat=" + lat + "&lon=" + lng
                        + "&format=json&addressdetails=1&zoom=18&accept-language=es";
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent", "Moviflexx-App/1.0 (contact@moviflexx.com)");
                conn.setConnectTimeout(12_000);
                conn.setReadTimeout(12_000);
                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    JSONObject geo  = new JSONObject(sb.toString());
                    JSONObject addr = geo.optJSONObject("address");
                    resultado = extraerNombre(addr, geo);
                }
            } catch (Exception e) {
                Log.w(TAG, "Geocoding error: " + e.getMessage());
            }
            final String nomFinal = (resultado == null || resultado.isEmpty())
                    ? String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng)
                    : resultado;
            runOnUiThread(() -> {
                nomSel = nomFinal;
                tvDireccion.setText("🙋  " + nomFinal);
                tvDireccion.setTextColor(Color.parseColor("#004D40"));
                tvDireccion.setTypeface(null, Typeface.BOLD);
                loader.setVisibility(View.GONE);
                btnConfirmar.setEnabled(true);
                btnConfirmar.setBackgroundColor(Color.parseColor("#00897B"));
                String etiq = nomFinal.length() > 26 ? nomFinal.substring(0, 26) + "…" : nomFinal;
                btnConfirmar.setText("✅  Confirmar: " + etiq);
            });
        }).start();
    }

    /** Devuelve true si el valor contiene "perímetro" o "perimetro" (case-insensitive) */
    private boolean esPerimetro(String v) {
        if (v == null || v.isEmpty()) return false;
        String lo = v.toLowerCase();
        return lo.contains("per\u00edmetro") || lo.contains("perimetro");
    }

    private String extraerNombre(JSONObject addr, JSONObject geo) {
        if (addr != null) {
            String quarter       = limpiar(addr.optString("quarter",       ""));
            String neighbourhood = limpiar(addr.optString("neighbourhood", ""));
            String suburb        = limpiar(addr.optString("suburb",        ""));
            String cityDistrict  = limpiar(addr.optString("city_district", ""));
            String hamlet        = limpiar(addr.optString("hamlet",        ""));
            String road          = limpiar(addr.optString("road",          ""));
            String houseNumber   = limpiar(addr.optString("house_number",  ""));

            // Ciudad real (descartando Perímetro)
            String ciudad = "";
            for (String k : new String[]{"city","town","municipality","county","state_district"}) {
                String v = addr.optString(k, "");
                if (!v.isEmpty() && !v.equals("null") && !esPerimetro(v)) { ciudad = v; break; }
            }

            // Nombre de barrio/sector: quarter > neighbourhood > suburb > city_district > hamlet
            // Estos dan nombres como "La María", "El Edén", "Kennedy", etc.
            String barrio = !quarter.isEmpty()       ? quarter
                    : !neighbourhood.isEmpty() ? neighbourhood
                    : !suburb.isEmpty()        ? suburb
                    : !cityDistrict.isEmpty()  ? cityDistrict
                    : !hamlet.isEmpty()        ? hamlet : "";

            // 1. Barrio + ciudad  →  "La María, Popayán"  /  "El Edén, Popayán"
            if (!barrio.isEmpty() && !ciudad.isEmpty() && !barrio.equalsIgnoreCase(ciudad))
                return barrio + ", " + ciudad;

            // 2. Solo barrio (sin ciudad conocida)
            if (!barrio.isEmpty()) return barrio;

            // 3. Calle # número + ciudad  →  "Calle 5 # 48-12, Popayán"
            if (!road.isEmpty() && !houseNumber.isEmpty())
                return road + " # " + houseNumber + (ciudad.isEmpty() ? "" : ", " + ciudad);

            // 4. Calle + ciudad  →  "Calle 5, Popayán"
            if (!road.isEmpty())
                return road + (ciudad.isEmpty() ? "" : ", " + ciudad);

            // 5. Solo ciudad
            if (!ciudad.isEmpty()) return ciudad;
        }

        // Fallback: recorrer display_name descartando partes con "Perímetro" y números sueltos
        if (geo != null) {
            String disp = geo.optString("display_name", "");
            if (!disp.isEmpty()) {
                for (String parte : disp.split(",")) {
                    String p = parte.trim();
                    if (!p.isEmpty() && !esPerimetro(p) && !p.matches("\\d+"))
                        return p;
                }
            }
        }
        return "";
    }

    private String limpiar(String v) {
        if (v == null || v.isEmpty() || v.equals("null")) return "";
        return esPerimetro(v) ? "" : v.trim();
    }

    // =========================================================================
    //  CONFIRMAR
    // =========================================================================
    private void confirmarSeleccion() {
        if (Double.isNaN(latSel)) {
            Toast.makeText(this, "Toca la ruta para seleccionar un punto", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent result = new Intent();
        result.putExtra("lat_subida",    latSel);
        result.putExtra("lng_subida",    lngSel);
        result.putExtra("nombre_subida", nomSel);
        setResult(RESULT_OK, result);
        finish();
    }

    // =========================================================================
    //  MI UBICACIÓN
    // =========================================================================
    private void usarMiUbicacion() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERM_LOCATION);
            return;
        }
        btnMiUbi.setEnabled(false);
        btnMiUbi.setText("Buscando...");
        fusedClient.getLastLocation().addOnSuccessListener(loc -> {
            btnMiUbi.setEnabled(true);
            btnMiUbi.setText("📍 Mi ubicación");
            if (loc != null) {
                map.getController().setZoom(17.0);
                ponerMarcador(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
            } else {
                Toast.makeText(this, "No se pudo obtener tu ubicación", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == PERM_LOCATION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            usarMiUbicacion();
    }
}