package com.arlys.moviflexx.controller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.*;
import org.osmdroid.views.overlay.mylocation.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class PublicarRuta extends AppCompatActivity {

    private static final int REQ_LOCATION = 1001;

    // ── UI ──
    private TextInputEditText    editOrigen, editDestino;
    private TextView             txtInfoRuta;
    private MapView              map;
    private ProgressBar          loader;
    private MaterialButton       btnCalcular, btnPublicar;
    private MaterialCardView     cardRutasOpciones;
    private LinearLayout         containerRutas;
    private ChipGroup            chipGroupTransporte;
    private Chip                 chipCarro, chipMoto;
    private FloatingActionButton btnZoomIn, btnZoomOut, btnMiUbicacion;

    // ── Datos ──
    private GeoPoint             origenPoint, destinoPoint;
    private MyLocationNewOverlay myLocationOverlay;
    private Marker               marcadorOrigen, marcadorDestino;
    private List<RutaInfo>       listaRutas     = new ArrayList<>();
    private RutaInfo             rutaSeleccionada;
    private String               tipoTransporte = "driving";

    private static final String COLOR_RAPIDA = "#6C3BFF";
    private static final String COLOR_MEDIA  = "#FF9800";
    private static final String COLOR_LARGA  = "#F44336";

    /* ═══════════ CICLO DE VIDA ═══════════ */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_publicar_ruta);

        initViews();
        configurarMapa();
        configurarZoomButtons();
        configurarTransporte();
        verificarPermisosUbicacion();

        btnCalcular.setOnClickListener(v -> buscarRutasMultiples());
        btnPublicar.setOnClickListener(v -> crearRuta());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }

    /* ═══════════ INIT ═══════════ */

    private void initViews() {
        editOrigen          = findViewById(R.id.edit_origen);
        editDestino         = findViewById(R.id.edit_destino);
        txtInfoRuta         = findViewById(R.id.txt_info_ruta);
        map                 = findViewById(R.id.map_mini);
        loader              = findViewById(R.id.loader_ruta);
        btnCalcular         = findViewById(R.id.btn_calcular_ruta);
        btnPublicar         = findViewById(R.id.btn_publicar_ruta);
        cardRutasOpciones   = findViewById(R.id.card_rutas_opciones);
        containerRutas      = findViewById(R.id.container_rutas);
        chipGroupTransporte = findViewById(R.id.chip_group_transporte);
        chipCarro           = findViewById(R.id.chip_carro);
        chipMoto            = findViewById(R.id.chip_moto);
        btnZoomIn           = findViewById(R.id.fab_zoom_in);
        btnZoomOut          = findViewById(R.id.fab_zoom_out);
        btnMiUbicacion      = findViewById(R.id.fab_mi_ubicacion);
    }

    /* ═══════════ MAPA ═══════════ */

    private void configurarMapa() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.getController().setZoom(17.0);
        map.setMinZoomLevel(5.0);
        map.setMaxZoomLevel(20.0);
        map.setFlingEnabled(true);
        map.setTilesScaledToDpi(true);
        map.setHorizontalMapRepetitionEnabled(false);
        map.setVerticalMapRepetitionEnabled(false);
        map.setUseDataConnection(true);
        Configuration.getInstance().setOsmdroidTileCache(
                new java.io.File(getCacheDir(), "osmdroid_tiles"));
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(80L * 1024 * 1024);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(60L * 1024 * 1024);
    }

    /* ═══════════ ZOOM ═══════════ */

    private void configurarZoomButtons() {
        btnZoomIn.setOnClickListener(v -> {
            if (map.getZoomLevelDouble() < map.getMaxZoomLevel())
                map.getController().zoomIn();
        });
        btnZoomOut.setOnClickListener(v -> {
            if (map.getZoomLevelDouble() > map.getMinZoomLevel())
                map.getController().zoomOut();
        });
        btnMiUbicacion.setOnClickListener(v -> {
            if (origenPoint != null) {
                map.getController().animateTo(origenPoint);
                map.getController().setZoom(17.0);
            } else {
                Toast.makeText(this, "Esperando GPS…", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /* ═══════════ TRANSPORTE ═══════════ */

    private void configurarTransporte() {
        chipCarro.setChecked(true);
        chipGroupTransporte.setOnCheckedChangeListener((group, checkedId) -> {
            tipoTransporte = (checkedId == R.id.chip_moto) ? "motorcycle" : "driving";
            if (!listaRutas.isEmpty() && destinoPoint != null) buscarRutasMultiples();
        });
    }

    /* ═══════════ GPS ═══════════ */

    private void verificarPermisosUbicacion() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
        } else {
            activarUbicacion();
        }
    }

    private void activarUbicacion() {
        myLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(this), map);
        myLocationOverlay.enableMyLocation();
        map.getOverlays().add(myLocationOverlay);

        myLocationOverlay.runOnFirstFix(() ->
                runOnUiThread(() -> {
                    origenPoint = myLocationOverlay.getMyLocation();
                    if (origenPoint != null) {
                        editOrigen.setText(obtenerDireccion(origenPoint));
                        if (listaRutas.isEmpty())
                            map.getController().animateTo(origenPoint);
                        agregarMarcadorOrigen();
                    }
                }));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            activarUbicacion();
        }
    }

    /* ═══════════ MARCADORES ═══════════ */

    private void agregarMarcadorOrigen() {
        if (marcadorOrigen != null) map.getOverlays().remove(marcadorOrigen);
        marcadorOrigen = new Marker(map);
        marcadorOrigen.setPosition(origenPoint);
        marcadorOrigen.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marcadorOrigen.setTitle("Origen");
        marcadorOrigen.setSnippet(editOrigen.getText().toString());
        marcadorOrigen.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_mylocation));
        map.getOverlays().add(marcadorOrigen);
        map.invalidate();
    }

    private void agregarMarcadorDestino() {
        if (marcadorDestino != null) map.getOverlays().remove(marcadorDestino);
        marcadorDestino = new Marker(map);
        marcadorDestino.setPosition(destinoPoint);
        marcadorDestino.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marcadorDestino.setTitle("Destino");
        marcadorDestino.setSnippet(editDestino.getText().toString());
        marcadorDestino.setIcon(getResources().getDrawable(android.R.drawable.ic_dialog_map));
        map.getOverlays().add(marcadorDestino);
        map.invalidate();
    }

    /* ═══════════ RUTAS ═══════════ */

    private void buscarRutasMultiples() {
        if (origenPoint == null) {
            Toast.makeText(this, "Esperando GPS…", Toast.LENGTH_SHORT).show();
            return;
        }
        String destinoTxt = editDestino.getText().toString().trim();
        if (destinoTxt.isEmpty()) {
            Toast.makeText(this, "Ingrese destino", Toast.LENGTH_SHORT).show();
            return;
        }

        loader.setVisibility(View.VISIBLE);
        cardRutasOpciones.setVisibility(View.GONE);
        containerRutas.removeAllViews();
        limpiarRutasDelMapa();

        new Thread(() -> {
            try {
                String geoUrl = "https://nominatim.openstreetmap.org/search?q="
                        + java.net.URLEncoder.encode(destinoTxt, "UTF-8")
                        + ",Popayan&format=json&limit=1";
                JSONArray geoArr = new JSONArray(peticionHttp(geoUrl));

                if (geoArr.length() == 0) {
                    runOnUiThread(() -> {
                        loader.setVisibility(View.GONE);
                        Toast.makeText(this, "Destino no encontrado", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                JSONObject obj = geoArr.getJSONObject(0);
                destinoPoint = new GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"));

                String osrmUrl = "https://router.project-osrm.org/route/v1/"
                        + tipoTransporte + "/"
                        + origenPoint.getLongitude() + "," + origenPoint.getLatitude() + ";"
                        + destinoPoint.getLongitude() + "," + destinoPoint.getLatitude()
                        + "?overview=full&geometries=geojson&alternatives=true&steps=true";

                JSONObject res    = new JSONObject(peticionHttp(osrmUrl));
                JSONArray  routes = res.getJSONArray("routes");
                listaRutas.clear();

                for (int i = 0; i < Math.min(routes.length(), 3); i++) {
                    JSONObject route     = routes.getJSONObject(i);
                    double     distancia = route.getDouble("distance") / 1000;
                    double     duracion  = route.getDouble("duration") / 60;
                    JSONArray  coords    = route.getJSONObject("geometry").getJSONArray("coordinates");

                    ArrayList<GeoPoint> puntos = new ArrayList<>();
                    for (int j = 0; j < coords.length(); j++) {
                        puntos.add(new GeoPoint(
                                coords.getJSONArray(j).getDouble(1),
                                coords.getJSONArray(j).getDouble(0)
                        ));
                    }

                    RutaInfo info  = new RutaInfo();
                    info.puntos    = puntos;
                    info.distancia = distancia;
                    info.duracion  = duracion;
                    info.indice    = i;

                    switch (i) {
                        case 0:
                            info.tipo = "Ruta Rápida"; info.color = COLOR_RAPIDA;
                            info.descripcion = "La ruta más rápida"; break;
                        case 1:
                            info.tipo = "Ruta Media";  info.color = COLOR_MEDIA;
                            info.descripcion = "Ruta alternativa"; break;
                        default:
                            info.tipo = "Ruta Larga";  info.color = COLOR_LARGA;
                            info.descripcion = "Ruta más larga";
                    }
                    listaRutas.add(info);
                }

                runOnUiThread(this::mostrarRutas);

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    loader.setVisibility(View.GONE);
                    Toast.makeText(this, "Error calculando rutas", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void mostrarRutas() {
        loader.setVisibility(View.GONE);
        if (listaRutas.isEmpty()) {
            Toast.makeText(this, "No se encontraron rutas", Toast.LENGTH_SHORT).show();
            return;
        }

        limpiarRutasDelMapa();
        for (RutaInfo ruta : listaRutas) dibujarRutaEnMapa(ruta, false);
        agregarMarcadorDestino();
        ajustarVistaRuta(listaRutas.get(0).puntos);

        containerRutas.removeAllViews();
        for (RutaInfo ruta : listaRutas) containerRutas.addView(crearCardRuta(ruta));

        cardRutasOpciones.setVisibility(View.VISIBLE);
        rutaSeleccionada = null;
        txtInfoRuta.setText("Selecciona una ruta para continuar");
    }

    private void limpiarRutasDelMapa() {
        List<Overlay> toRemove = new ArrayList<>();
        for (Overlay o : map.getOverlays())
            if (o instanceof Polyline) toRemove.add(o);
        map.getOverlays().removeAll(toRemove);
    }

    private View crearCardRuta(RutaInfo ruta) {
        View view = getLayoutInflater().inflate(R.layout.item_ruta_opcion, null);

        MaterialCardView card    = view.findViewById(R.id.card_ruta);
        View  indicadorColor     = view.findViewById(R.id.indicador_color);
        TextView txtTipo         = view.findViewById(R.id.txt_tipo_ruta);
        TextView txtDistancia    = view.findViewById(R.id.txt_distancia);
        TextView txtDuracion     = view.findViewById(R.id.txt_duracion);
        TextView txtDescripcion  = view.findViewById(R.id.txt_descripcion);
        android.widget.ImageView iconTransporte = view.findViewById(R.id.icon_transporte);

        indicadorColor.setBackgroundColor(Color.parseColor(ruta.color));
        txtTipo.setText(ruta.tipo);
        txtDistancia.setText(String.format("%.1f km", ruta.distancia));
        txtDuracion.setText(String.format("%.0f min", ruta.duracion));
        txtDescripcion.setText(ruta.descripcion);
        iconTransporte.setImageResource(
                tipoTransporte.equals("motorcycle") ? R.drawable.ic_moto : R.drawable.ic_car);

        card.setOnClickListener(v -> seleccionarRuta(ruta));
        return view;
    }

    private void seleccionarRuta(RutaInfo ruta) {
        rutaSeleccionada = ruta;

        for (int i = 0; i < containerRutas.getChildCount(); i++) {
            MaterialCardView card = containerRutas.getChildAt(i).findViewById(R.id.card_ruta);
            if (card == null) continue;
            if (i == ruta.indice) {
                card.setCardElevation(16f);
                card.setStrokeWidth(4);
                card.setStrokeColor(Color.parseColor(ruta.color));
            } else {
                card.setCardElevation(4f);
                card.setStrokeWidth(1);
                card.setStrokeColor(Color.parseColor("#E0E0E0"));
            }
        }

        limpiarRutasDelMapa();
        dibujarRutaEnMapa(ruta, true);
        if (marcadorOrigen != null) map.getOverlays().add(marcadorOrigen);
        agregarMarcadorDestino();
        map.invalidate();

        String vehiculo = tipoTransporte.equals("motorcycle") ? "Moto" : "Carro";
        txtInfoRuta.setText(String.format("%s • %s • %.1f km • %.0f min",
                ruta.tipo, vehiculo, ruta.distancia, ruta.duracion));
    }

    /* ═══════════ DIBUJAR RUTAS ═══════════ */

    private void dibujarRutaEnMapa(RutaInfo ruta, boolean esSeleccionada) {
        Polyline sombra = new Polyline();
        sombra.setPoints(ruta.puntos);
        sombra.setColor(Color.parseColor("#55000000"));
        sombra.setWidth(esSeleccionada ? 24f : 16f);
        map.getOverlays().add(sombra);

        Polyline borde = new Polyline();
        borde.setPoints(ruta.puntos);
        borde.setColor(Color.WHITE);
        borde.setWidth(esSeleccionada ? 20f : 13f);
        map.getOverlays().add(borde);

        Polyline linea = new Polyline();
        linea.setPoints(ruta.puntos);
        linea.setColor(Color.parseColor(ruta.color));
        linea.setWidth(esSeleccionada ? 14f : 9f);
        map.getOverlays().add(linea);
        ruta.polyline = linea;
    }

    /* ═══════════ FIT VISTA ═══════════ */

    private void ajustarVistaRuta(ArrayList<GeoPoint> puntos) {
        if (puntos == null || puntos.isEmpty()) return;

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

        for (GeoPoint p : puntos) {
            if (p.getLatitude()  < minLat) minLat = p.getLatitude();
            if (p.getLatitude()  > maxLat) maxLat = p.getLatitude();
            if (p.getLongitude() < minLon) minLon = p.getLongitude();
            if (p.getLongitude() > maxLon) maxLon = p.getLongitude();
        }

        double padLat = Math.max((maxLat - minLat) * 0.15, 0.001);
        double padLon = Math.max((maxLon - minLon) * 0.15, 0.001);

        BoundingBox bbox = new BoundingBox(
                maxLat + padLat, maxLon + padLon,
                minLat - padLat, minLon - padLon);

        map.post(() -> map.zoomToBoundingBox(bbox, true, 80));
    }

    /* ═══════════ PUBLICAR ═══════════ */

    private void crearRuta() {
        if (rutaSeleccionada == null || destinoPoint == null) {
            Toast.makeText(this, "Primero selecciona una ruta", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("nombre",         rutaSeleccionada.tipo);
            body.put("descripcion",    String.format("Ruta de %.1f km en %s",
                    rutaSeleccionada.distancia,
                    tipoTransporte.equals("motorcycle") ? "Moto" : "Carro"));
            body.put("origen",         editOrigen.getText().toString());
            body.put("destino",        editDestino.getText().toString());
            body.put("latOrigen",      origenPoint.getLatitude());
            body.put("lngOrigen",      origenPoint.getLongitude());
            body.put("latDestino",     destinoPoint.getLatitude());
            body.put("lngDestino",     destinoPoint.getLongitude());
            body.put("distancia",      rutaSeleccionada.distancia);
            body.put("duracion",       rutaSeleccionada.duracion);
            body.put("tipoTransporte", tipoTransporte);
            body.put("estado",         "DISPONIBLE");

            ConexionApi.getInstance(this).post(
                    Constantes.RUTAS,
                    body,

                    // SUCCESS
                    response -> {

                        int idRuta = 0;

                        // Intentar obtener ID desde distintas posibles respuestas
                        if (response.has("idRuta")) {
                            idRuta = response.optInt("idRuta");
                        } else if (response.has("id")) {
                            idRuta = response.optInt("id");
                        }

                        if (idRuta == 0) {
                            Toast.makeText(this,
                                    "No se pudo obtener el ID de la ruta",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        // ───────────── AGREGADO IMPORTANTE ─────────────
                        Intent intent = new Intent(PublicarRuta.this, PublicarViaje.class);

                        intent.putExtra("ID_RUTA_CREADA", idRuta);
                        intent.putExtra("DESTINO_RUTA", editDestino.getText().toString());
                        intent.putExtra("ORIGEN_RUTA", editOrigen.getText().toString());
                        intent.putExtra("TIPO_TRANSPORTE", tipoTransporte);

                        startActivity(intent);
                        finish();
                        // ───────────────────────────────────────────────
                    },

                    // ERROR
                    error -> Toast.makeText(this,
                            "Error creando ruta",
                            Toast.LENGTH_LONG).show()
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /* ═══════════ UTILIDADES ═══════════ */

    private String peticionHttp(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "Moviflexx");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder b = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) b.append(l);
        return b.toString();
    }

    private String obtenerDireccion(GeoPoint punto) {
        try {
            Geocoder g = new Geocoder(this, Locale.getDefault());
            List<Address> list = g.getFromLocation(
                    punto.getLatitude(), punto.getLongitude(), 1);
            if (list != null && !list.isEmpty())
                return list.get(0).getAddressLine(0);
        } catch (Exception ignored) {}
        return "Ubicación actual";
    }

    /* ═══════════ CLASE INTERNA ═══════════ */

    private static class RutaInfo {
        ArrayList<GeoPoint> puntos;
        double   distancia, duracion;
        String   tipo, color, descripcion;
        int      indice;
        Polyline polyline;
    }
}