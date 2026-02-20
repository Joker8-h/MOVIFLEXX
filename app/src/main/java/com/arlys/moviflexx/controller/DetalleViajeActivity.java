package com.arlys.moviflexx.controller;

import android.app.AlertDialog;
import android.content.Intent;
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Manager.RouteManager;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.pojo.RouteOption;
import com.arlys.moviflexx.model.pojo.RouteOptionsResponse;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

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
    private static final int    COLOR_RUTA = 0xFF00BCD4;
    private static final String COLOR_LIBRE = "#4CAF50";
    private static final String COLOR_OCUP  = "#EF5350";
    private static final String COLOR_SEL   = "#FF9800";
    private static final String OSRM_URL   =
            "https://osrm-popayan-production.up.railway.app";

    // ── Estados de reserva ────────────────────────────────────────────────────
    private static final String EST_PENDIENTE          = "PENDIENTE";
    private static final String EST_CONFIRMADA         = "CONFIRMADA";
    private static final String EST_ESPERANDO_RECOGIDA = "ESPERANDO_RECOGIDA";
    private static final String EST_RECOGIDO           = "RECOGIDO";
    private static final String EST_COMPLETADO         = "COMPLETADO";
    private static final String EST_CANCELADO          = "CANCELADO";

    // ── Estados de viaje que permiten reservar al pasajero ───────────────────
    private static final List<String> ESTADOS_RESERVABLES = Arrays.asList(
            "EN_CURSO", "INICIADO"
    );

    // ── Estados de reserva activa (bloquean nueva reserva en otro viaje) ─────
    private static final List<String> ESTADOS_RESERVA_ACTIVA = Arrays.asList(
            "ACTIVA", "CONFIRMADA", "PENDIENTE",
            "ESPERANDO_RECOGIDA", "RECOGIDO", "EN_CURSO", "INICIADO"
    );

    // ── IDs de marcadores ─────────────────────────────────────────────────────
    private static final String MID_ORIGEN  = "m_origen";
    private static final String MID_DESTINO = "m_destino";
    private static final String MID_PARADA  = "m_parada";

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

    // ── Datos del viaje ───────────────────────────────────────────────────────
    private int     viajeId, rutaId;
    private boolean esConductor;
    private String  origenActual = "", destinoActual = "", estadoViaje = "";
    private double  latOrigen, lngOrigen, latDestino, lngDestino;
    private int     cuposTotales = 0, cuposDisponibles = 0;
    private double  precioViaje  = 0;
    private double  distanciaKm  = 0, duracionMin = 0;
    private SessionManager session;

    // ── Chat ──────────────────────────────────────────────────────────────────
    private int    idPasajeroViaje      = -1;
    private int    idConductorViaje     = -1;
    private String nombreConductorViaje = "";
    private String nombrePasajeroViaje  = "";

    // ── Paradas ───────────────────────────────────────────────────────────────
    private final ArrayList<JSONObject>     paradasRuta = new ArrayList<>();
    private final ArrayList<ParadaDinamica> paradasDin  = new ArrayList<>();

    // ── Geometría OSRM ────────────────────────────────────────────────────────
    /** Ruta principal A→B — nunca se reemplaza */
    private ArrayList<GeoPoint> puntosRutaPrincipal = new ArrayList<>();
    /** Ruta A→parada→B — se calcula solo cuando el pasajero es RECOGIDO */
    private ArrayList<GeoPoint> puntosRutaWaypoint  = new ArrayList<>();

    // ── Posiciones clave ──────────────────────────────────────────────────────
    private GeoPoint gpOrigen  = null;
    private GeoPoint gpDestino = null;
    // Para pasajero: su parada individual
    private GeoPoint gpParada  = null;
    private String   nombreParada = "";
    // Para conductor: lista de TODAS las paradas de todos los pasajeros activos
    private final ArrayList<GeoPoint> paradasPasajeros        = new ArrayList<>();
    private final ArrayList<String>   nombresPasajerosParadas = new ArrayList<>();

    // Paleta para diferenciar pasajeros visualmente en el mapa y en las cards
    private static final int[] COLORES_PASAJEROS = {
            0xFFFF9800, 0xFF9C27B0, 0xFF2196F3, 0xFFE91E63, 0xFF009688, 0xFFFF5722
    };
    private static final String[] COLORES_PASAJEROS_HEX = {
            "#FF9800", "#9C27B0", "#2196F3", "#E91E63", "#009688", "#FF5722"
    };

    // ── Estado de la reserva ──────────────────────────────────────────────────
    private boolean yaReservo        = false;
    private int     cupoSeleccionado = -1;
    private int     idReservaActual  = -1;
    private String  estadoReserva    = "";

    // ── Flags coord ───────────────────────────────────────────────────────────
    private boolean coordsOrigenInvalidas  = false;
    private boolean coordsDestinoInvalidas = false;

    // ── Polling ───────────────────────────────────────────────────────────────
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private Runnable      pollingRunnable;
    private boolean       pollingActivo = false;

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

    @Override protected void onResume()  { super.onResume();  if (map != null) map.onResume(); }
    @Override protected void onPause()   { super.onPause();   if (map != null) map.onPause(); detenerTodo(); }
    @Override protected void onDestroy() { super.onDestroy(); detenerTodo(); }

    private void detenerTodo() {
        pollingActivo = false;
        if (pollingRunnable != null) pollingHandler.removeCallbacks(pollingRunnable);
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

        rvHistorialParadas.setLayoutManager(new LinearLayoutManager(this));

        if (btnAccionPrincipal != null)
            btnAccionPrincipal.setOnClickListener(v -> mostrarBottomSheetParada());

        btnIniciar.setOnClickListener(v -> cambiarEstadoViaje("iniciar"));
        btnFinalizar.setOnClickListener(v -> confirmarFinalizar());
        if (btnMensajeConductor != null)
            btnMensajeConductor.setOnClickListener(v -> abrirOCrearChat());

        crearBotonRecoger();
        crearCardMiReserva();
        crearCardPasajeros();
    }

    // ── Botón "Recoger pasajero" (solo conductor) ─────────────────────────────
    private void crearBotonRecoger() {
        if (cardAcciones == null) return;
        LinearLayout inner = (LinearLayout) cardAcciones.getChildAt(0);
        if (inner == null) return;
        float d = getResources().getDisplayMetrics().density;
        btnRecoger = new MaterialButton(this);
        btnRecoger.setText("✅  RECOGER PASAJERO");
        btnRecoger.setTextSize(14f); btnRecoger.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(56*d));
        lp.topMargin = (int)(10*d); btnRecoger.setLayoutParams(lp);
        btnRecoger.setCornerRadius((int)(14*d));
        btnRecoger.setBackgroundColor(Color.parseColor("#1976D2"));
        btnRecoger.setVisibility(View.GONE);
        btnRecoger.setOnClickListener(v -> confirmarRecogida());
        inner.addView(btnRecoger, 0);
    }

    // ── Cards dinámicas ────────────────────────────────────────────────────────
    private void crearCardMiReserva() {
        ViewGroup c = buscarScrollContent(); if (c == null) return;
        float d = getResources().getDisplayMetrics().density;
        int p16=(int)(16*d), p12=(int)(12*d), p8=(int)(8*d);
        cardMiReserva = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(p16,p8,p16,p8); cardMiReserva.setLayoutParams(lp);
        cardMiReserva.setRadius(16*d); cardMiReserva.setCardElevation(4*d);
        cardMiReserva.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
        cardMiReserva.setVisibility(View.GONE);
        LinearLayout inner = new LinearLayout(this); inner.setOrientation(LinearLayout.VERTICAL); inner.setPadding(p16,p12,p16,p12);
        TextView titulo = new TextView(this); titulo.setText("🎫 Tu reserva"); titulo.setTextSize(13f); titulo.setTypeface(null,Typeface.BOLD); titulo.setTextColor(Color.parseColor("#2E7D32")); inner.addView(titulo);
        txtMiReservaInfo = new TextView(this); txtMiReservaInfo.setTextSize(14f); txtMiReservaInfo.setTextColor(Color.parseColor("#004D40"));
        LinearLayout.LayoutParams lpt = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lpt.topMargin=p8; txtMiReservaInfo.setLayoutParams(lpt); inner.addView(txtMiReservaInfo);
        cardMiReserva.addView(inner); c.addView(cardMiReserva, 0);
    }

    private void crearCardPasajeros() {
        ViewGroup c = buscarScrollContent(); if (c == null) return;
        float d = getResources().getDisplayMetrics().density;
        int p16=(int)(16*d), p12=(int)(12*d), p8=(int)(8*d);
        cardPasajeros = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(p16,p8,p16,p8); cardPasajeros.setLayoutParams(lp);
        cardPasajeros.setRadius(16*d); cardPasajeros.setCardElevation(4*d);
        cardPasajeros.setCardBackgroundColor(Color.parseColor("#E3F2FD")); cardPasajeros.setVisibility(View.GONE);
        LinearLayout inner = new LinearLayout(this); inner.setOrientation(LinearLayout.VERTICAL); inner.setPadding(p16,p12,p16,p12);
        LinearLayout fila = new LinearLayout(this); fila.setOrientation(LinearLayout.HORIZONTAL); fila.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView titulo = new TextView(this); titulo.setText("🧑‍🤝‍🧑 Pasajeros reservados"); titulo.setTextSize(14f); titulo.setTypeface(null,Typeface.BOLD); titulo.setTextColor(Color.parseColor("#1565C0")); titulo.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f)); fila.addView(titulo);
        txtTotalPasajeros = new TextView(this); txtTotalPasajeros.setTextSize(12f); txtTotalPasajeros.setTextColor(Color.parseColor("#1565C0")); fila.addView(txtTotalPasajeros); inner.addView(fila);
        View sep = new View(this); LinearLayout.LayoutParams ls = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(1*d)); ls.setMargins(0,p8,0,p8); sep.setLayoutParams(ls); sep.setBackgroundColor(Color.parseColor("#BBDEFB")); inner.addView(sep);
        layoutListaPasajeros = new LinearLayout(this); layoutListaPasajeros.setOrientation(LinearLayout.VERTICAL); inner.addView(layoutListaPasajeros);
        cardPasajeros.addView(inner); c.addView(cardPasajeros, 0);
    }

    private ViewGroup buscarScrollContent() {
        View raiz = findViewById(android.R.id.content);
        if (!(raiz instanceof ViewGroup)) return null;
        return buscarEn((ViewGroup) raiz);
    }
    private ViewGroup buscarEn(ViewGroup vg) {
        for (int i=0; i<vg.getChildCount(); i++) {
            View ch = vg.getChildAt(i);
            if (ch instanceof androidx.core.widget.NestedScrollView) { androidx.core.widget.NestedScrollView sv=(androidx.core.widget.NestedScrollView)ch; if(sv.getChildCount()>0&&sv.getChildAt(0)instanceof ViewGroup)return(ViewGroup)sv.getChildAt(0); }
            if (ch instanceof android.widget.ScrollView) { android.widget.ScrollView sv=(android.widget.ScrollView)ch; if(sv.getChildCount()>0&&sv.getChildAt(0)instanceof ViewGroup)return(ViewGroup)sv.getChildAt(0); }
            if (ch instanceof LinearLayout) return (ViewGroup) ch;
        }
        return vg;
    }

    // =========================================================================
    //  CARGA PRINCIPAL
    // =========================================================================
    private void cargarDetalleViaje() {
        loaderDetalle.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).getObject(Constantes.viajePorId((long)viajeId),
                response -> { loaderDetalle.setVisibility(View.GONE); try { procesarViaje(response); } catch (Exception e) { Log.e(TAG,"Error procesando viaje",e); } },
                error   -> { loaderDetalle.setVisibility(View.GONE); Toast.makeText(this,"Error de conexión",Toast.LENGTH_LONG).show(); });
    }

    // =========================================================================
    //  PROCESAMIENTO DEL JSON
    // =========================================================================
    private void procesarViaje(JSONObject r) throws Exception {
        // ── LOG DE DIAGNÓSTICO ────────────────────────────────────────────────
        try {
            Log.d(TAG, "═══ JSON VIAJE ═══\n" + r.toString(2));
            JSONObject rutaLog = r.optJSONObject("ruta");
            if (rutaLog != null) Log.d(TAG, "═══ JSON RUTA ═══\n" + rutaLog.toString(2));
        } catch (Exception ignored) {}
        // ─────────────────────────────────────────────────────────────────────

        estadoViaje      = r.optString("estado","DESCONOCIDO").trim().toUpperCase();
        cuposTotales     = r.optInt("cuposTotales",0);
        cuposDisponibles = r.optInt("cuposDisponibles",0);
        // ✅ Limpiar paradas de pasajeros al recargar
        paradasPasajeros.clear();
        nombresPasajerosParadas.clear();

        JSONObject ruta = r.optJSONObject("ruta");
        extraerCoordsYNombres(r, ruta);
        extraerMetricas(r, ruta);
        gpOrigen  = new GeoPoint(latOrigen,  lngOrigen);
        gpDestino = new GeoPoint(latDestino, lngDestino);

        double pr = r.optDouble("precio",-1);
        if (pr<0) { try{pr=Double.parseDouble(r.optString("precio","0"));}catch(Exception ex){pr=0;} }
        precioViaje = pr;
        if (txtPrecio!=null) txtPrecio.setText(precioViaje>0?"$ "+String.format(Locale.getDefault(),"%,.0f",precioViaje):"Precio no definido");

        mostrarFechaHora(r.optString("fechaHoraSalida",r.optString("fechaSalida",r.optString("fecha",""))));
        extraerConductor(r);
        if (idConductorViaje<=0&&esConductor){idConductorViaje=session.getIdUsuario();nombreConductorViaje=session.getNombre();}

        JSONObject pas=r.optJSONObject("pasajero"); if(pas!=null){idPasajeroViaje=extractId(pas);nombrePasajeroViaje=extractNombre(pas);}
        JSONObject veh=r.optJSONObject("vehiculo"); if(veh!=null&&txtVehiculo!=null)txtVehiculo.setText("🚘 "+veh.optString("marca","")+" "+veh.optString("modelo","")+" • "+veh.optString("placa",""));

        actualizarNombreConductorUI();
        txtRuta.setText("📍 "+origenActual+" → "+destinoActual);
        txtEstado.setText(etiquetaEstado(estadoViaje));
        actualizarChipsCupos(cuposTotales, cuposDisponibles);
        configurarBotones();
        cargarParadasRuta();
        iniciarPolling();

        if (esConductor) cargarReservasConductor();
        else             cargarMiReservaPasajero();
    }

    // =========================================================================
    //  ★ EXTRACCIÓN ROBUSTA DE COORDS Y NOMBRES ★
    // =========================================================================
    private void extraerCoordsYNombres(JSONObject r, JSONObject ruta) {
        // Usamos el objeto ruta si existe, si no el raíz
        JSONObject src = ruta != null ? ruta : r;
        rutaId = ruta != null ? ruta.optInt("idRuta", 0) : r.optInt("idRuta", 0);

        // ── COORDENADAS ──────────────────────────────────────────────────────
        latOrigen  = primeraCoord(src, new String[]{"latOrigen","latitudOrigen","latInicio","lat"}, Double.NaN);
        lngOrigen  = primeraCoord(src, new String[]{"lngOrigen","longitudOrigen","lngInicio","lng"}, Double.NaN);
        latDestino = primeraCoordDistinta(src, new String[]{"latDestino","latitudDestino","latFin"}, Double.isNaN(latOrigen)?0:latOrigen, Double.NaN);
        lngDestino = primeraCoordDistinta(src, new String[]{"lngDestino","longitudDestino","lngFin"}, Double.isNaN(lngOrigen)?0:lngOrigen, Double.NaN);

        // Fallback coords a Popayán centro si son inválidas
        if (Double.isNaN(latOrigen) || latOrigen == 0) { coordsOrigenInvalidas = true; latOrigen = 2.4419; lngOrigen = -76.6063; }
        if (Double.isNaN(latDestino) || latDestino == 0 || sonIguales(latOrigen,lngOrigen,latDestino,lngDestino)) { coordsDestinoInvalidas = true; latDestino = 2.4550; lngDestino = -76.5980; }

        // ── NOMBRE ORIGEN ─────────────────────────────────────────────────────
        // 1) Buscar campo directo en objeto ruta/raíz
        origenActual = primeraStr(src, new String[]{
                "origen", "puntoOrigen", "inicio", "nombreOrigen", "lugarOrigen",
                "direccionOrigen", "origenNombre"
        });
        // 2) Si no, buscar en el objeto raíz del viaje
        if (origenActual.isEmpty() && ruta != null) {
            origenActual = primeraStr(r, new String[]{
                    "origen", "puntoOrigen", "inicio", "nombreOrigen"
            });
        }
        // 3) Si no, parsear del campo "nombre" que tiene formato "A → B"
        if (origenActual.isEmpty()) {
            origenActual = extraerOrigenDeNombre(src);
        }
        if (origenActual.isEmpty() && ruta != null) {
            origenActual = extraerOrigenDeNombre(r);
        }
        if (origenActual.isEmpty()) origenActual = "Origen";

        // ── NOMBRE DESTINO ─────────────────────────────────────────────────────
        // 1) Buscar campo directo en objeto ruta/raíz
        destinoActual = primeraStr(src, new String[]{
                "destino", "puntoDestino", "fin", "nombreDestino", "lugarDestino",
                "direccionDestino", "destinoNombre"
        });
        // 2) Si no, buscar en el objeto raíz del viaje
        if (destinoActual.isEmpty() && ruta != null) {
            destinoActual = primeraStr(r, new String[]{
                    "destino", "puntoDestino", "fin", "nombreDestino"
            });
        }
        // 3) Si no, parsear del campo "nombre" que tiene formato "A → B"
        if (destinoActual.isEmpty()) {
            destinoActual = extraerDestinoDeNombre(src);
        }
        if (destinoActual.isEmpty() && ruta != null) {
            destinoActual = extraerDestinoDeNombre(r);
        }
        // 4) Último fallback: geocodificación reversa (asíncrona si todo falla)
        if (destinoActual.isEmpty()) destinoActual = "Destino";

        Log.d(TAG, "✅ Origen='" + origenActual + "' | Destino='" + destinoActual + "'");
        Log.d(TAG, "✅ Coords: (" + latOrigen + "," + lngOrigen + ") → (" + latDestino + "," + lngDestino + ")");
    }

    /**
     * Extrae la parte de origen del campo "nombre" con formato "Origen → Destino"
     * También soporta " - " y " to " como separadores alternativos.
     */
    private String extraerOrigenDeNombre(JSONObject obj) {
        String nombre = obj.optString("nombre", "").trim();
        if (nombre.isEmpty() || nombre.equals("null")) return "";
        // Separador principal: →
        if (nombre.contains("→")) return nombre.split("→")[0].trim();
        // Separador alternativo: ->
        if (nombre.contains("->")) return nombre.split("->")[0].trim();
        // Separador alternativo: " - "
        if (nombre.contains(" - ")) return nombre.split(" - ")[0].trim();
        return "";
    }

    /**
     * Extrae la parte de destino del campo "nombre" con formato "Origen → Destino"
     */
    private String extraerDestinoDeNombre(JSONObject obj) {
        String nombre = obj.optString("nombre", "").trim();
        if (nombre.isEmpty() || nombre.equals("null")) return "";
        String[] partes = null;
        if (nombre.contains("→"))  partes = nombre.split("→",  2);
        else if (nombre.contains("->")) partes = nombre.split("->", 2);
        else if (nombre.contains(" - ")) partes = nombre.split(" - ", 2);
        if (partes != null && partes.length > 1) return partes[1].trim();
        return "";
    }

    private void extraerMetricas(JSONObject r, JSONObject ruta) {
        JSONObject src=ruta!=null?ruta:r;
        distanciaKm=src.optDouble("distanciaKm",src.optDouble("distancia",0));
        duracionMin=src.optDouble("duracionMin",src.optDouble("duracion",0));
        if(txtDistancia!=null&&distanciaKm>0)txtDistancia.setText(String.format("%.1f km",distanciaKm));
        if(txtDuracion!=null&&duracionMin>0)txtDuracion.setText(String.format("%.0f min",duracionMin));
    }

    // =========================================================================
    //  RENDERIZADO CENTRAL DEL MAPA
    // =========================================================================
    private void renderizarMapa() {
        limpiarOverlays();
        dibujarPolilinea(puntosRutaPrincipal, COLOR_RUTA);
        agregarMarcador(MID_ORIGEN,  gpOrigen,  0xFF4CAF50, "A", "🟢 "+origenActual);
        agregarMarcador(MID_DESTINO, gpDestino, 0xFFEF5350, "B", "🔴 "+destinoActual);

        // Paradas estáticas de la ruta
        for (int i=0; i<paradasRuta.size(); i++) {
            JSONObject p=paradasRuta.get(i); double pLat=p.optDouble("lat",0),pLng=p.optDouble("lng",0);
            if (pLat!=0) agregarMarcador("wp_"+i,new GeoPoint(pLat,pLng),COLOR_RUTA,String.valueOf(i+1),"🔵 "+p.optString("nombre","Parada "+(i+1)));
        }

        if (esConductor) {
            // ✅ Conductor: un marcador por pasajero, cada uno con su color único
            // Solo si realmente tiene coords de parada (latParada != 0)
            for (int i = 0; i < paradasPasajeros.size(); i++) {
                GeoPoint pp = paradasPasajeros.get(i);
                if (pp == null) continue;
                // Color único por índice de pasajero
                int color = COLORES_PASAJEROS[i % COLORES_PASAJEROS.length];
                String etiqueta = i < nombresPasajerosParadas.size()
                        ? nombresPasajerosParadas.get(i) : "Pasajero";
                // Letra del marcador: P1, P2, P3...
                agregarMarcador("parada_pas_"+i, pp, color,
                        "P"+(i+1), "🚏 "+etiqueta);
            }
        } else {
            // Pasajero: solo su propia parada (si la eligió)
            if (gpParada != null)
                agregarMarcador(MID_PARADA, gpParada, 0xFFFF9800, "P", "🚏 Parada: "+nombreParada);
            if ((EST_RECOGIDO.equals(estadoReserva)||EST_COMPLETADO.equals(estadoReserva))
                    && !puntosRutaWaypoint.isEmpty())
                dibujarPolilinea(puntosRutaWaypoint, 0xFF7B1FA2);
        }

        ajustarCamara();
        map.invalidate();
    }

    private void limpiarOverlays() {
        List<Overlay> ol=map.getOverlays(); for(int i=ol.size()-1;i>=0;i--){Overlay o=ol.get(i);if(o instanceof Marker||o instanceof Polyline)ol.remove(i);}
    }

    private void dibujarPolilinea(ArrayList<GeoPoint> pts, int color) {
        if(pts==null||pts.size()<2)return;
        Polyline s=new Polyline(map);s.setPoints(pts);s.setColor(Color.argb(50,0,0,0));s.setWidth(22f);map.getOverlays().add(s);
        Polyline b=new Polyline(map);b.setPoints(pts);b.setColor(Color.WHITE);b.setWidth(17f);map.getOverlays().add(b);
        Polyline l=new Polyline(map);l.setPoints(pts);l.setColor(color);l.setWidth(11f);map.getOverlays().add(l);
    }

    private void agregarMarcador(String id, GeoPoint pos, int color, String letra, String titulo) {
        if(pos==null)return;
        Marker m=new Marker(map); m.setId(id); m.setPosition(pos); m.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM); m.setTitle(titulo); m.setIcon(new BitmapDrawable(getResources(),crearBitmapMarcador(color,letra))); map.getOverlays().add(m);
    }

    private void ajustarCamara() {
        ArrayList<GeoPoint> todos=new ArrayList<>(); if(puntosRutaPrincipal!=null)todos.addAll(puntosRutaPrincipal); if(gpParada!=null)todos.add(gpParada);
        if(todos.size()<2){if(gpOrigen!=null){map.getController().animateTo(gpOrigen);map.getController().setZoom(14.0);}return;}
        double mnLat=Double.MAX_VALUE,mxLat=-Double.MAX_VALUE,mnLng=Double.MAX_VALUE,mxLng=-Double.MAX_VALUE;
        for(GeoPoint p:todos){mnLat=Math.min(mnLat,p.getLatitude());mxLat=Math.max(mxLat,p.getLatitude());mnLng=Math.min(mnLng,p.getLongitude());mxLng=Math.max(mxLng,p.getLongitude());}
        double pLat=Math.max((mxLat-mnLat)*0.25,0.006),pLng=Math.max((mxLng-mnLng)*0.25,0.006);
        final double fMxLat=mxLat,fMnLat=mnLat,fMxLng=mxLng,fMnLng=mnLng,fPLat=pLat,fPLng=pLng;
        map.post(()->{try{map.zoomToBoundingBox(new BoundingBox(fMxLat+fPLat,fMxLng+fPLng,fMnLat-fPLat,fMnLng-fPLng),true,100);}catch(Exception ignored){}});
    }

    // =========================================================================
    //  OSRM — RUTAS
    // =========================================================================
    private void cargarParadasRuta() {
        if(rutaId==0){map.postDelayed(this::iniciarTrazadoRuta,800);return;}
        ConexionApi.getInstance(this).getArrayNoCache(Constantes.paradasPorRuta((long)rutaId),
                arr->{try{paradasRuta.clear();ArrayList<String>nombres=new ArrayList<>();for(int i=0;i<arr.length();i++){JSONObject p=arr.getJSONObject(i);paradasRuta.add(p);nombres.add(p.optString("nombre","Parada "+(i+1)));}rvHistorialParadas.setAdapter(new ParadaAdapter(nombres,origenActual,destinoActual));cardHistorial.setVisibility(nombres.isEmpty()?View.GONE:View.VISIBLE);}catch(Exception e){Log.e(TAG,"Error paradas",e);}map.postDelayed(this::iniciarTrazadoRuta,800);},
                error->{cardHistorial.setVisibility(View.GONE);map.postDelayed(this::iniciarTrazadoRuta,800);});
    }

    private void iniciarTrazadoRuta() {
        if(coordsOrigenInvalidas||coordsDestinoInvalidas){
            new Thread(()->{
                if(coordsOrigenInvalidas){double[]c=geocodificarTexto(origenActual);if(c!=null){latOrigen=c[0];lngOrigen=c[1];coordsOrigenInvalidas=false;gpOrigen=new GeoPoint(latOrigen,lngOrigen);}}
                if(coordsDestinoInvalidas||sonIguales(latOrigen,lngOrigen,latDestino,lngDestino)){double[]c=geocodificarTexto(destinoActual);if(c!=null){latDestino=c[0];lngDestino=c[1];coordsDestinoInvalidas=false;gpDestino=new GeoPoint(latDestino,lngDestino);}}
                runOnUiThread(this::pedirRutaPrincipal);
            }).start();
        } else pedirRutaPrincipal();
    }

    private void pedirRutaPrincipal() {
        ArrayList<GeoPoint> wps=new ArrayList<>(); for(JSONObject p:paradasRuta){double pLat=p.optDouble("lat",0),pLng=p.optDouble("lng",0);if(pLat!=0)wps.add(new GeoPoint(pLat,pLng));}
        new RouteManager().fetchRoutes(latOrigen,lngOrigen,latDestino,lngDestino,"FASTEST",new RouteManager.RouteCallback(){
            @Override public void onSuccess(RouteOptionsResponse resp){
                if(resp.routes==null||resp.routes.isEmpty()){fallbackOsrm(wps,false);return;}
                RouteOption best=resp.routes.get(0);
                if(best.distanceKm>0){distanciaKm=best.distanceKm;runOnUiThread(()->{if(txtDistancia!=null)txtDistancia.setText(String.format("%.1f km",distanciaKm));});}
                if(best.durationMin>0){duracionMin=best.durationMin;runOnUiThread(()->{if(txtDuracion!=null)txtDuracion.setText(String.format("%.0f min",duracionMin));});}
                ArrayList<GeoPoint> pts=parsearGeoJson(best.geojson);
                if(pts==null||pts.size()<2){fallbackOsrm(wps,false);return;}
                puntosRutaPrincipal=pts;
                runOnUiThread(()->{generarParadasDinamicas();renderizarMapa();});
            }
            @Override public void onError(String err){fallbackOsrm(wps,false);}
        });
    }

    private void pedirRutaConWaypoint() {
        if(gpParada==null)return;
        new Thread(()->{
            try{
                String url=OSRM_URL+"/route/v1/driving/"+lngOrigen+","+latOrigen+";"+gpParada.getLongitude()+","+gpParada.getLatitude()+";"+lngDestino+","+latDestino+"?overview=full&geometries=geojson";
                String json=peticionHttp(url); JSONObject obj=new JSONObject(json);
                if(!"Ok".equals(obj.optString("code")))return;
                JSONObject route=obj.getJSONArray("routes").getJSONObject(0);
                JSONArray coords=route.getJSONObject("geometry").getJSONArray("coordinates");
                ArrayList<GeoPoint> pts=new ArrayList<>(); for(int i=0;i<coords.length();i++){JSONArray c=coords.getJSONArray(i);pts.add(new GeoPoint(c.getDouble(1),c.getDouble(0)));}
                if(pts.size()>=2){puntosRutaWaypoint=pts;runOnUiThread(this::renderizarMapa);}
            }catch(Exception e){Log.w(TAG,"Ruta waypoint falló",e);}
        }).start();
    }

    private void fallbackOsrm(ArrayList<GeoPoint> wps, boolean esWaypoint) {
        new Thread(()->{
            try{
                StringBuilder sb=new StringBuilder(OSRM_URL+"/route/v1/driving/");
                sb.append(lngOrigen).append(",").append(latOrigen);
                for(GeoPoint w:wps)sb.append(";").append(w.getLongitude()).append(",").append(w.getLatitude());
                if(esWaypoint&&gpParada!=null)sb.append(";").append(gpParada.getLongitude()).append(",").append(gpParada.getLatitude());
                sb.append(";").append(lngDestino).append(",").append(latDestino).append("?overview=full&geometries=geojson");
                String json=peticionHttp(sb.toString()); JSONObject obj=new JSONObject(json);
                JSONObject route=obj.getJSONArray("routes").getJSONObject(0);
                JSONArray coords=route.getJSONObject("geometry").getJSONArray("coordinates");
                ArrayList<GeoPoint> pts=new ArrayList<>(); for(int i=0;i<coords.length();i++){JSONArray c=coords.getJSONArray(i);pts.add(new GeoPoint(c.getDouble(1),c.getDouble(0)));}
                if(pts.size()>=2){if(esWaypoint)puntosRutaWaypoint=pts;else{puntosRutaPrincipal=pts;runOnUiThread(this::generarParadasDinamicas);}}
            }catch(Exception e){if(!esWaypoint){puntosRutaPrincipal=new ArrayList<>();puntosRutaPrincipal.add(gpOrigen);puntosRutaPrincipal.add(gpDestino);}}
            runOnUiThread(this::renderizarMapa);
        }).start();
    }

    // =========================================================================
    //  MI RESERVA — pasajero
    // =========================================================================
    private void cargarMiReservaPasajero() {
        if(esConductor||cardMiReserva==null)return;
        ConexionApi.getInstance(this).getArray(Constantes.MIS_RESERVAS,response->{
            for(int i=0;i<response.length();i++){
                JSONObject res=response.optJSONObject(i); if(res==null)continue;
                int idVR=res.optInt("idViaje",0); if(idVR==0){JSONObject vo=res.optJSONObject("viaje");if(vo!=null)idVR=vo.optInt("idViaje",vo.optInt("id",0));}
                if(idVR!=viajeId)continue;
                yaReservo=true; idReservaActual=res.optInt("idReserva",res.optInt("id",-1)); estadoReserva=res.optString("estado","").toUpperCase();
                double latP=res.optDouble("latParada",0),lngP=res.optDouble("lngParada",0);
                if(latP!=0){gpParada=new GeoPoint(latP,lngP);nombreParada=res.optString("nombreParada",destinoActual);}
                final JSONObject mr=res;
                runOnUiThread(()->{
                    mostrarCardMiReserva(mr); actualizarBotonPasajero(); actualizarChipsCupos(cuposTotales,cuposDisponibles);
                    if(EST_RECOGIDO.equals(estadoReserva)||EST_COMPLETADO.equals(estadoReserva))pedirRutaConWaypoint();
                    else renderizarMapa();
                });
                return;
            }
            runOnUiThread(()->{if(cardMiReserva!=null)cardMiReserva.setVisibility(View.GONE);actualizarBotonPasajero();renderizarMapa();});
        },error->{Log.e(TAG,"Error cargando mis reservas");runOnUiThread(this::renderizarMapa);});
    }

    private void mostrarCardMiReserva(JSONObject reserva) {
        if(cardMiReserva==null||txtMiReservaInfo==null)return;
        String estado=reserva.optString("estado","").toUpperCase();
        if(EST_CANCELADO.equals(estado)){cardMiReserva.setVisibility(View.GONE);yaReservo=false;return;}
        String np=reserva.optString("nombreParada",destinoActual);
        int asi=reserva.optInt("numeroAsientos",reserva.optInt("asientos",1));
        String cod=reserva.optString("codigoReserva",reserva.optString("codigo",""));
        double pre=reserva.optDouble("precio",precioViaje);
        String nc=nombreConductorViaje.isEmpty()?"Conductor":nombreConductorViaje;
        StringBuilder sb=new StringBuilder();
        sb.append("🚗 Conductor: ").append(nc).append("\n");
        sb.append("💺 Asientos: ").append(asi).append("\n");
        sb.append("🚏 Bajarás en: ").append(np).append("\n");
        sb.append(pre>0?"💰 $"+String.format(Locale.getDefault(),"%,.0f",pre)+" COP":"💰 Precio: no definido");
        if(!cod.isEmpty())sb.append("\n📋 Código: ").append(cod);
        String etiqueta; int bgColor;
        switch(estado){
            case EST_ESPERANDO_RECOGIDA:etiqueta="⏳ Esperando recogida";bgColor=Color.parseColor("#FFF9C4");break;
            case EST_RECOGIDO:etiqueta="🚗 ¡Ya te recogieron!";bgColor=Color.parseColor("#E3F2FD");break;
            case EST_COMPLETADO:etiqueta="🏁 Viaje completado";bgColor=Color.parseColor("#E8F5E9");break;
            default:etiqueta="✅ Reserva activa";bgColor=Color.parseColor("#E8F5E9");break;
        }
        sb.append("\n").append(etiqueta);
        txtMiReservaInfo.setText(sb.toString()); cardMiReserva.setCardBackgroundColor(bgColor); cardMiReserva.setVisibility(View.VISIBLE);
    }

    // =========================================================================
    //  RESERVAS — conductor
    // =========================================================================
    private void cargarReservasConductor() {
        if(!esConductor||cardPasajeros==null)return;
        ConexionApi.getInstance(this).getObject(Constantes.viajeReservasDetalle((long)viajeId),
                response->runOnUiThread(()->{
                    try{
                        JSONArray reservas=extraerArray(response);
                        // Limpiar paradas previas
                        paradasPasajeros.clear();
                        nombresPasajerosParadas.clear();

                        if(reservas==null||reservas.length()==0){mostrarSinPasajeros();return;}
                        layoutListaPasajeros.removeAllViews();
                        int total=0; boolean hayEsperando=false;
                        // indice de color: solo sube cuando un pasajero TIENE parada real
                        int colorIdx=0;

                        for(int i=0;i<reservas.length();i++){
                            JSONObject res=reservas.getJSONObject(i);
                            String est=res.optString("estado","").toUpperCase();
                            if(est.equals(EST_CANCELADO)||est.equals("CANCELADA"))continue;

                            String np="";
                            JSONObject po=res.optJSONObject("pasajero");
                            if(po!=null)np=extractNombre(po);
                            if(np.isEmpty())np=res.optString("nombrePasajero","Pasajero "+(colorIdx+1));

                            int asi=res.optInt("numeroAsientos",res.optInt("asientos",1));
                            total+=asi;
                            String par=res.optString("nombreParada","");
                            int idRes=res.optInt("idReserva",res.optInt("id",-1));

                            // ¿Tiene coordenadas de parada reales?
                            double latP=res.optDouble("latParada",0), lngP=res.optDouble("lngParada",0);
                            boolean tieneParada = latP!=0 && lngP!=0;

                            // Determinar color del pasajero (solo asignar color si tiene parada)
                            int pasajeroColor = tieneParada
                                    ? COLORES_PASAJEROS[colorIdx % COLORES_PASAJEROS.length]
                                    : Color.parseColor("#607D8B"); // gris si no tiene parada
                            String pasajeroColorHex = tieneParada
                                    ? COLORES_PASAJEROS_HEX[colorIdx % COLORES_PASAJEROS_HEX.length]
                                    : "#607D8B";

                            if(tieneParada){
                                paradasPasajeros.add(new GeoPoint(latP,lngP));
                                // Texto del marcador: "P1 · Juan → Campanario"
                                nombresPasajerosParadas.add(np+(par.isEmpty()?"":(" → "+par)));
                            }

                            // Agregar card del pasajero con su color de marcador
                            agregarFilaPasajeroColoreado(
                                    np, asi,
                                    par.isEmpty() ? (tieneParada ? "Parada sin nombre" : "Sin parada asignada") : par,
                                    est, idRes,
                                    tieneParada ? "P"+(colorIdx+1) : "?",
                                    pasajeroColor, pasajeroColorHex, tieneParada);

                            if(tieneParada) colorIdx++;

                            if(EST_ESPERANDO_RECOGIDA.equals(est)){
                                hayEsperando=true;
                                if(gpParada==null&&tieneParada){
                                    gpParada=new GeoPoint(latP,lngP);
                                    nombreParada=par.isEmpty()?np:par;
                                }
                            }
                        }

                        if(layoutListaPasajeros.getChildCount()==0){cardPasajeros.setVisibility(View.GONE);return;}
                        txtTotalPasajeros.setText(layoutListaPasajeros.getChildCount()+" pasajero(s) · "+total+" asiento(s)");
                        cardPasajeros.setVisibility(View.VISIBLE);
                        btnRecoger.setVisibility(hayEsperando?View.VISIBLE:View.GONE);
                        renderizarMapa();
                    }catch(Exception e){Log.e(TAG,"Error reservas conductor",e);}
                }),
                error->runOnUiThread(this::mostrarSinPasajeros));
    }

    private void mostrarSinPasajeros(){if(layoutListaPasajeros==null)return;layoutListaPasajeros.removeAllViews();TextView tv=new TextView(this);tv.setText("Sin pasajeros reservados aún");tv.setTextColor(Color.parseColor("#546E7A"));tv.setTextSize(13f);tv.setPadding(0,8,0,8);layoutListaPasajeros.addView(tv);if(txtTotalPasajeros!=null)txtTotalPasajeros.setText("0 pasajeros");if(cardPasajeros!=null)cardPasajeros.setVisibility(View.VISIBLE);}
    private JSONArray extraerArray(JSONObject r){for(String k:new String[]{"reservas","content","data"}){JSONArray a=r.optJSONArray(k);if(a!=null)return a;}Iterator<String>keys=r.keys();while(keys.hasNext()){Object v=r.opt(keys.next());if(v instanceof JSONArray)return(JSONArray)v;}return null;}

    /**
     * Card del pasajero en la vista del conductor.
     * Muestra: nombre, estado, asientos, parada y un círculo del mismo color
     * que el marcador en el mapa para que el conductor identifique visualmente.
     */
    private void agregarFilaPasajeroColoreado(String nombre, int asientos, String parada,
                                              String estado, int idRes, String etiqMarcador,
                                              int colorMarcador, String colorHex, boolean tieneParada) {

        float d=getResources().getDisplayMetrics().density;
        int p12=(int)(12*d), p8=(int)(8*d), p6=(int)(6*d), p4=(int)(4*d);

        MaterialCardView fila=new MaterialCardView(this);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,p4,0,p4);
        fila.setLayoutParams(lp);
        fila.setRadius(14*d);
        fila.setCardElevation(3*d);
        fila.setCardBackgroundColor(Color.WHITE);
        // Borde izquierdo del color del marcador
        fila.setStrokeColor(tieneParada ? colorMarcador : Color.parseColor("#B0BEC5"));
        fila.setStrokeWidth((int)(3*d));

        LinearLayout inner=new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setPadding(0, p8, p12, p8);

        // ── Barra lateral de color ──────────────────────────────────────
        View barra=new View(this);
        LinearLayout.LayoutParams lpB=new LinearLayout.LayoutParams((int)(6*d), LinearLayout.LayoutParams.MATCH_PARENT);
        lpB.setMargins(0,0,p12,0);
        barra.setLayoutParams(lpB);
        barra.setBackgroundColor(tieneParada ? colorMarcador : Color.parseColor("#B0BEC5"));
        inner.addView(barra);

        LinearLayout contenido=new LinearLayout(this);
        contenido.setOrientation(LinearLayout.VERTICAL);
        contenido.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f));

        // ── Fila 1: Nombre + badge estado ──────────────────────────────
        LinearLayout fila1=new LinearLayout(this);
        fila1.setOrientation(LinearLayout.HORIZONTAL);
        fila1.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tn=new TextView(this);
        tn.setText("🧑 "+nombre);
        tn.setTextSize(14f);
        tn.setTypeface(null, Typeface.BOLD);
        tn.setTextColor(Color.parseColor("#004D40"));
        tn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        fila1.addView(tn);

        TextView tb=new TextView(this);
        tb.setText(badgeEstado(estado));
        tb.setTextSize(10f);
        tb.setTypeface(null,Typeface.BOLD);
        tb.setTextColor(Color.WHITE);
        tb.setPadding(p8,p4,p8,p4);
        GradientDrawable bgb=new GradientDrawable();
        bgb.setShape(GradientDrawable.RECTANGLE);
        bgb.setCornerRadius(20*d);
        bgb.setColor(colorBadge(estado));
        tb.setBackground(bgb);
        fila1.addView(tb);
        contenido.addView(fila1);

        // ── Fila 2: Asientos ───────────────────────────────────────────
        TextView ta=new TextView(this);
        ta.setText("💺 "+asientos+(asientos==1?" asiento":" asientos"));
        ta.setTextSize(12f);
        ta.setTextColor(Color.parseColor("#00695C"));
        LinearLayout.LayoutParams lpA=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpA.topMargin=p4;
        ta.setLayoutParams(lpA);
        contenido.addView(ta);

        // ── Fila 3: Parada con chip de color del marcador ──────────────
        LinearLayout filaParada=new LinearLayout(this);
        filaParada.setOrientation(LinearLayout.HORIZONTAL);
        filaParada.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpFP=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFP.topMargin=p6;
        filaParada.setLayoutParams(lpFP);

        // Chip "P1", "P2"... del color del marcador
        TextView chipMarcador=new TextView(this);
        chipMarcador.setText(etiqMarcador);
        chipMarcador.setTextSize(11f);
        chipMarcador.setTypeface(null,Typeface.BOLD);
        chipMarcador.setTextColor(Color.WHITE);
        chipMarcador.setPadding(p8,p4,p8,p4);
        GradientDrawable bgChip=new GradientDrawable();
        bgChip.setShape(GradientDrawable.OVAL);
        bgChip.setColor(tieneParada ? colorMarcador : Color.parseColor("#B0BEC5"));
        chipMarcador.setBackground(bgChip);
        LinearLayout.LayoutParams lpChip=new LinearLayout.LayoutParams((int)(28*d),(int)(28*d));
        lpChip.setMargins(0,0,p8,0);
        chipMarcador.setLayoutParams(lpChip);
        chipMarcador.setGravity(android.view.Gravity.CENTER);
        filaParada.addView(chipMarcador);

        TextView tParada=new TextView(this);
        if(tieneParada){
            tParada.setText("🚏 "+parada);
            tParada.setTextColor(Color.parseColor(colorHex));
            tParada.setTypeface(null,Typeface.BOLD);
        } else {
            tParada.setText("⚠️ "+parada);
            tParada.setTextColor(Color.parseColor("#90A4AE"));
            tParada.setTypeface(null,Typeface.ITALIC);
        }
        tParada.setTextSize(12f);
        tParada.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        tParada.setMaxLines(2);
        tParada.setEllipsize(android.text.TextUtils.TruncateAt.END);
        filaParada.addView(tParada);
        contenido.addView(filaParada);

        // ── Botón recoger (solo si está esperando) ─────────────────────
        if(EST_ESPERANDO_RECOGIDA.equals(estado)){
            MaterialButton btnR=new MaterialButton(this);
            btnR.setText("✅ Confirmar recogida");
            btnR.setTextSize(12f);
            btnR.setTextColor(Color.WHITE);
            btnR.setCornerRadius((int)(10*d));
            btnR.setBackgroundColor(tieneParada ? colorMarcador : Color.parseColor("#1976D2"));
            LinearLayout.LayoutParams lpb=new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,(int)(42*d));
            lpb.topMargin=p8;
            btnR.setLayoutParams(lpb);
            btnR.setOnClickListener(v->{idReservaActual=idRes;confirmarRecogida();});
            contenido.addView(btnR);
        }

        inner.addView(contenido);
        fila.addView(inner);
        layoutListaPasajeros.addView(fila);
    }

    // ── Versión antigua mantenida por si se llama desde otro lado ──────────────
    private void agregarFilaPasajero(String nombre,int asientos,String parada,String estado,int idRes){
        agregarFilaPasajeroColoreado(nombre,asientos,parada,estado,idRes,"?",
                Color.parseColor("#607D8B"),"#607D8B",false);
    }

    private String badgeEstado(String e){switch(e){case EST_ESPERANDO_RECOGIDA:return"⏳ Esperando";case EST_RECOGIDO:return"✅ Recogido";case EST_COMPLETADO:return"🏁 Completado";default:return"📋 "+e;}}
    private int colorBadge(String e){switch(e){case EST_ESPERANDO_RECOGIDA:return Color.parseColor("#FB8C00");case EST_RECOGIDO:return Color.parseColor("#1976D2");case EST_COMPLETADO:return Color.parseColor("#388E3C");default:return Color.parseColor("#607D8B");}}

    // =========================================================================
    //  CONFIRMAR RECOGIDA — conductor
    // =========================================================================
    private void confirmarRecogida(){if(idReservaActual<=0){Toast.makeText(this,"Sin reserva activa",Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(this).setTitle("Confirmar recogida").setMessage("¿Confirmas que recogiste al pasajero?").setPositiveButton("Sí, lo recogí",(d,w)->ejecutarRecogida()).setNegativeButton("Cancelar",null).show();}

    private void ejecutarRecogida(){
        loaderDetalle.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).put(Constantes.RESERVAS+"/"+idReservaActual+"/recoger",null,
                response->{loaderDetalle.setVisibility(View.GONE);estadoReserva=EST_RECOGIDO;Toast.makeText(this,"✅ Pasajero recogido",Toast.LENGTH_SHORT).show();runOnUiThread(()->{btnRecoger.setVisibility(View.GONE);pedirRutaConWaypoint();cargarReservasConductor();});},
                error->{loaderDetalle.setVisibility(View.GONE);Toast.makeText(this,"Error al confirmar recogida",Toast.LENGTH_LONG).show();});
    }

    // =========================================================================
    //  BOTTOM SHEET — ELEGIR / CAMBIAR PARADA
    // =========================================================================
    private void mostrarBottomSheetParada(){
        if(paradasDin.isEmpty())generarParadasDinamicas();
        BottomSheetDialog sheet=new BottomSheetDialog(this,R.style.BottomSheetTheme);
        float dp=getResources().getDisplayMetrics().density;int p16=(int)(16*dp),p12=(int)(12*dp),p8=(int)(8*dp),p4=(int)(4*dp),p24=(int)(24*dp);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(p16,p16,p16,p24);
        View tiron=new View(this);LinearLayout.LayoutParams lpT=new LinearLayout.LayoutParams((int)(40*dp),(int)(4*dp));lpT.gravity=android.view.Gravity.CENTER_HORIZONTAL;lpT.bottomMargin=p12;tiron.setLayoutParams(lpT);GradientDrawable tGd=new GradientDrawable();tGd.setShape(GradientDrawable.RECTANGLE);tGd.setCornerRadius(4*dp);tGd.setColor(Color.parseColor("#BDBDBD"));tiron.setBackground(tGd);root.addView(tiron);
        TextView tTitulo=new TextView(this);tTitulo.setText(yaReservo?"🔄 Cambiar parada de bajada":"🚏 ¿Dónde te vas a bajar?");tTitulo.setTextSize(18f);tTitulo.setTypeface(null,Typeface.BOLD);tTitulo.setTextColor(Color.parseColor("#004D40"));LinearLayout.LayoutParams lpTit=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpTit.bottomMargin=p12;tTitulo.setLayoutParams(lpTit);root.addView(tTitulo);
        TextView tvElegida=new TextView(this);if(!nombreParada.isEmpty()){tvElegida.setText("🚏 "+nombreParada);tvElegida.setVisibility(View.VISIBLE);}else tvElegida.setVisibility(View.GONE);tvElegida.setTextSize(13f);tvElegida.setTextColor(Color.WHITE);tvElegida.setTypeface(null,Typeface.BOLD);tvElegida.setPadding(p12,p8,p12,p8);GradientDrawable bgC=new GradientDrawable();bgC.setShape(GradientDrawable.RECTANGLE);bgC.setCornerRadius(20*dp);bgC.setColor(Color.parseColor("#FF9800"));tvElegida.setBackground(bgC);LinearLayout.LayoutParams lpChip=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpChip.bottomMargin=p8;tvElegida.setLayoutParams(lpChip);root.addView(tvElegida);
        android.widget.EditText editBuscar=new android.widget.EditText(this);editBuscar.setHint("✏️  Escribe un barrio o lugar...");editBuscar.setTextSize(14f);editBuscar.setTextColor(Color.parseColor("#212121"));editBuscar.setHintTextColor(Color.parseColor("#9E9E9E"));editBuscar.setSingleLine(true);editBuscar.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);editBuscar.setPadding(p12,p12,p12,p12);GradientDrawable bgB=new GradientDrawable();bgB.setShape(GradientDrawable.RECTANGLE);bgB.setCornerRadius(12*dp);bgB.setColor(Color.parseColor("#F5F5F5"));bgB.setStroke((int)(1.5f*dp),Color.parseColor("#B2DFDB"));editBuscar.setBackground(bgB);LinearLayout.LayoutParams lpEdit=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpEdit.bottomMargin=p8;editBuscar.setLayoutParams(lpEdit);root.addView(editBuscar);
        LinearLayout lista=new LinearLayout(this);lista.setOrientation(LinearLayout.VERTICAL);root.addView(lista);
        final ParadaDinamica[]elegida={null};final MaterialButton[]btnRef={null};
        poblarLista(lista,paradasDin,"",dp,p8,p4,pE->{elegida[0]=pE;tvElegida.setText("🚏 "+pE.nombre);tvElegida.setVisibility(View.VISIBLE);gpParada=pE.toGeoPoint();nombreParada=pE.nombre;renderizarMapa();if(btnRef[0]!=null)habilitarBtn(btnRef[0],pE.nombre);});
        MaterialButton btnC=new MaterialButton(this);btnC.setText(yaReservo?"🔄 CAMBIAR PARADA":"🚏 ELEGIR PARADA");btnC.setTextSize(15f);btnC.setEnabled(false);btnC.setAlpha(0.5f);btnC.setBackgroundColor(Color.parseColor("#B0BEC5"));btnC.setTextColor(Color.WHITE);LinearLayout.LayoutParams lpBtn=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(52*dp));lpBtn.topMargin=p4;btnC.setLayoutParams(lpBtn);btnRef[0]=btnC;
        btnC.setOnClickListener(v->{if(elegida[0]==null)return;sheet.dismiss();if(yaReservo)cambiarParada(elegida[0]);else publicarParadaYReservar(elegida[0]);});root.addView(btnC);
        Handler autoH=new Handler(Looper.getMainLooper());Runnable[]autoR={null};
        editBuscar.addTextChangedListener(new android.text.TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}@Override public void afterTextChanged(android.text.Editable s){}@Override public void onTextChanged(CharSequence s,int st,int b,int c){if(autoR[0]!=null)autoH.removeCallbacks(autoR[0]);String txt=s.toString().trim();autoR[0]=()->{ArrayList<ParadaDinamica>f=new ArrayList<>();for(ParadaDinamica pd:paradasDin)if(txt.isEmpty()||pd.nombre.toLowerCase().contains(txt.toLowerCase()))f.add(pd);runOnUiThread(()->poblarLista(lista,f,txt,dp,p8,p4,pE->{elegida[0]=pE;tvElegida.setText("🚏 "+pE.nombre);tvElegida.setVisibility(View.VISIBLE);gpParada=pE.toGeoPoint();nombreParada=pE.nombre;renderizarMapa();if(btnRef[0]!=null)habilitarBtn(btnRef[0],pE.nombre);}));};autoH.postDelayed(autoR[0],txt.isEmpty()?0:400);}});
        android.widget.ScrollView sv=new android.widget.ScrollView(this);sv.addView(root);sheet.setContentView(sv);sheet.show();
    }

    private void habilitarBtn(MaterialButton btn,String nombre){btn.setEnabled(true);btn.setAlpha(1f);btn.setBackgroundColor(Color.parseColor(yaReservo?"#1565C0":"#00897B"));btn.setText(yaReservo?"🔄 CAMBIAR A: "+nombre:"🚏 RESERVAR EN: "+nombre);}

    // =========================================================================
    //  CAMBIAR PARADA EN RESERVA EXISTENTE
    // =========================================================================
    private void cambiarParada(ParadaDinamica pd){loaderDetalle.setVisibility(View.VISIBLE);if(pd.idParadaBD>0){actualizarReservaParada(pd);return;}try{JSONObject body=new JSONObject();body.put("idRuta",rutaId);body.put("nombre",pd.nombre);body.put("lat",pd.lat);body.put("lng",pd.lng);body.put("tipo","AMBAS");ConexionApi.getInstance(this).post(Constantes.PARADAS,body,r->{pd.idParadaBD=r.optInt("idParada",r.optInt("id",0));actualizarReservaParada(pd);},e->actualizarReservaParada(pd));}catch(Exception e){actualizarReservaParada(pd);}}
    private void actualizarReservaParada(ParadaDinamica pd){if(idReservaActual<=0){loaderDetalle.setVisibility(View.GONE);gpParada=pd.toGeoPoint();nombreParada=pd.nombre;renderizarMapa();actualizarBotonPasajero();return;}try{JSONObject body=new JSONObject();body.put("nombreParada",pd.nombre);body.put("latParada",pd.lat);body.put("lngParada",pd.lng);if(pd.idParadaBD>0)body.put("idParada",pd.idParadaBD);ConexionApi.getInstance(this).put(Constantes.RESERVAS+"/"+idReservaActual,body,r->{loaderDetalle.setVisibility(View.GONE);gpParada=pd.toGeoPoint();nombreParada=pd.nombre;renderizarMapa();actualizarBotonPasajero();Toast.makeText(this,"✅ Parada cambiada: "+pd.nombre,Toast.LENGTH_SHORT).show();cargarMiReservaPasajero();},e->{loaderDetalle.setVisibility(View.GONE);gpParada=pd.toGeoPoint();nombreParada=pd.nombre;renderizarMapa();Toast.makeText(this,"Parada actualizada",Toast.LENGTH_SHORT).show();});}catch(Exception e){loaderDetalle.setVisibility(View.GONE);}}

    // =========================================================================
    //  PUBLICAR PARADA + RESERVAR
    // =========================================================================
    private void publicarParadaYReservar(ParadaDinamica pd){loaderDetalle.setVisibility(View.VISIBLE);if(pd.idParadaBD>0){hacerReserva(pd);return;}try{JSONObject body=new JSONObject();body.put("idRuta",rutaId);body.put("nombre",pd.nombre);body.put("lat",pd.lat);body.put("lng",pd.lng);body.put("orden",paradasDin.indexOf(pd)+1);body.put("kmAcumulado",0);body.put("tipo","AMBAS");ConexionApi.getInstance(this).post(Constantes.PARADAS,body,r->{pd.idParadaBD=r.optInt("idParada",r.optInt("id",0));hacerReserva(pd);},e->hacerReserva(pd));}catch(Exception e){hacerReserva(pd);}}

    private void hacerReserva(ParadaDinamica pd){try{JSONObject body=new JSONObject();body.put("idViaje",viajeId);body.put("idUsuario",session.getIdUsuario());body.put("precio",precioViaje>0?precioViaje:0);body.put("nombreParada",pd.nombre);body.put("latParada",pd.lat);body.put("lngParada",pd.lng);if(pd.idParadaBD>0)body.put("idParada",pd.idParadaBD);ConexionApi.getInstance(this).post(Constantes.RESERVAS,body,response->{loaderDetalle.setVisibility(View.GONE);yaReservo=true;idReservaActual=response.optInt("idReserva",response.optInt("id",-1));estadoReserva=response.optString("estado",EST_CONFIRMADA).toUpperCase();gpParada=pd.toGeoPoint();nombreParada=pd.nombre;String nc=nombreConductorViaje.isEmpty()?"el conductor":nombreConductorViaje;Toast.makeText(this,"✅ Reservado con "+nc+"\n🚏 Bajarás en: "+pd.nombre,Toast.LENGTH_LONG).show();runOnUiThread(()->{cuposDisponibles=Math.max(0,cuposDisponibles-1);actualizarChipsCupos(cuposTotales,cuposDisponibles);actualizarBotonPasajero();renderizarMapa();});cargarMiReservaPasajero();},error->{loaderDetalle.setVisibility(View.GONE);String msg="Error al reservar";if(error!=null&&error.networkResponse!=null){int code=error.networkResponse.statusCode;if(code==400)msg="Datos inválidos o sin cupos";else if(code==409){msg="Ya tienes una reserva activa";yaReservo=true;}else if(code==403)msg="Sin permiso";}Toast.makeText(this,msg,Toast.LENGTH_LONG).show();if(yaReservo)cargarMiReservaPasajero();});}catch(Exception e){loaderDetalle.setVisibility(View.GONE);}}

    // =========================================================================
    //  PARADAS DINÁMICAS
    // =========================================================================
    private void generarParadasDinamicas(){
        paradasDin.clear();
        if(puntosRutaPrincipal==null||puntosRutaPrincipal.size()<2){for(JSONObject p:paradasRuta){double lat=p.optDouble("lat",0),lng=p.optDouble("lng",0);if(lat!=0){ParadaDinamica pd=new ParadaDinamica(p.optString("nombre","Parada"),lat,lng,0);pd.idParadaBD=p.optInt("idParada",p.optInt("id",0));paradasDin.add(pd);}}paradasDin.add(new ParadaDinamica(destinoActual,latDestino,lngDestino,-1));return;}
        int total=puntosRutaPrincipal.size(),num=Math.min(6,Math.max(3,total/20));List<Integer>indices=new ArrayList<>();double paso=(double)(total-2)/(num+1);for(int i=1;i<=num;i++){int idx=1+(int)(i*paso);if(idx<total-1)indices.add(idx);}
        for(int i=0;i<indices.size();i++){int idx=indices.get(i);GeoPoint gp=puntosRutaPrincipal.get(idx);double pct=(double)idx/(total-1)*100;paradasDin.add(new ParadaDinamica(String.format("Punto %.0f%% de la ruta",pct),gp.getLatitude(),gp.getLongitude(),idx));}
        paradasDin.add(new ParadaDinamica(destinoActual,latDestino,lngDestino,total-1));
        geocodificarEnBackground(indices);
    }

    private void geocodificarEnBackground(List<Integer>indices){new Thread(()->{for(int i=0;i<indices.size()&&i<paradasDin.size()-1;i++){int idx=indices.get(i);GeoPoint gp=puntosRutaPrincipal.get(idx);try{String url="https://nominatim.openstreetmap.org/reverse?lat="+gp.getLatitude()+"&lon="+gp.getLongitude()+"&format=json&addressdetails=1&zoom=16&accept-language=es";String resp=peticionHttp(url);if(resp!=null&&!resp.isEmpty()){JSONObject geo=new JSONObject(resp);String nombre=extraerNombreNominatim(geo.optJSONObject("address"),geo);final int fi=i;final String fn=nombre;runOnUiThread(()->{if(fi<paradasDin.size()-1)paradasDin.get(fi).nombre=fn;});}Thread.sleep(500);}catch(Exception ignored){}}}).start();}
    private String extraerNombreNominatim(JSONObject addr,JSONObject geo){if(addr==null)return geo.optString("display_name","Parada");for(String c:new String[]{"neighbourhood","suburb","quarter","city_district","road"}){String v=addr.optString(c,"");if(!v.isEmpty()&&!v.equals("null"))return v;}String d=geo.optString("display_name","");return d.isEmpty()?"Parada":d.split(",")[0].trim();}

    private void poblarLista(LinearLayout container,ArrayList<ParadaDinamica>paradas,String filtro,float dp,int p8,int p4,java.util.function.Consumer<ParadaDinamica>onSelect){container.removeAllViews();if(paradas.isEmpty()){TextView tv=new TextView(this);tv.setText("Sin paradas disponibles");tv.setTextSize(13f);tv.setTextColor(Color.parseColor("#9E9E9E"));tv.setPadding(p8,p8,p8,p8);container.addView(tv);return;}container.addView(crearFilaParada("🟢  "+origenActual+"  (Inicio)",null,false,dp,p8,p4,onSelect));for(ParadaDinamica pd:paradas){boolean esD=pd.idxEnRuta==-1;container.addView(crearFilaParada((esD?"🔴":"🔵")+"  "+pd.nombre+(esD?"  (Destino final)":""),pd,true,dp,p8,p4,onSelect));}}
    private View crearFilaParada(String label,ParadaDinamica pd,boolean sel,float dp,int p8,int p4,java.util.function.Consumer<ParadaDinamica>onSel){LinearLayout fila=new LinearLayout(this);fila.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams lpF=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpF.setMargins(0,(int)(2*dp),0,(int)(2*dp));fila.setLayoutParams(lpF);GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.RECTANGLE);bg.setCornerRadius(10*dp);bg.setColor(sel?Color.parseColor("#F9FAFB"):Color.parseColor("#F0F4F8"));bg.setStroke((int)(1*dp),sel?Color.parseColor("#B2DFDB"):Color.parseColor("#E0E0E0"));fila.setBackground(bg);fila.setPadding(p8,p8,p8,p8);TextView tv=new TextView(this);tv.setText(label);tv.setTextSize(14f);tv.setTextColor(sel?Color.parseColor("#004D40"):Color.parseColor("#9E9E9E"));if(!sel)tv.setTypeface(null,Typeface.ITALIC);tv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));fila.addView(tv);if(sel&&pd!=null){fila.setClickable(true);fila.setFocusable(true);fila.setOnClickListener(v->{GradientDrawable bgS=new GradientDrawable();bgS.setShape(GradientDrawable.RECTANGLE);bgS.setCornerRadius(10*dp);bgS.setColor(Color.parseColor("#E0F7FA"));bgS.setStroke((int)(2*dp),Color.parseColor("#00897B"));fila.setBackground(bgS);onSel.accept(pd);});}return fila;}

    // =========================================================================
    //  CUPOS
    // =========================================================================
    private void actualizarChipsCupos(int total,int disponibles){if(layoutCupos==null||total>8||total<=0)return;runOnUiThread(()->{layoutCupos.removeAllViews();float d=getResources().getDisplayMetrics().density;int s=(int)(32*d),m=(int)(6*d);for(int i=0;i<total;i++){final int idx=i;boolean libre=i<disponibles;boolean esMio=yaReservo&&cupoSeleccionado==i;View circle=new View(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(s,s);lp.setMargins(m,0,m,0);circle.setLayoutParams(lp);GradientDrawable sh=new GradientDrawable();sh.setShape(GradientDrawable.OVAL);if(esMio){sh.setColor(Color.parseColor(COLOR_SEL));sh.setStroke((int)(3*d),Color.parseColor("#E65100"));}else if(libre){sh.setColor(Color.parseColor(COLOR_LIBRE));sh.setStroke((int)(2*d),Color.parseColor("#388E3C"));}else{sh.setColor(Color.parseColor(COLOR_OCUP));sh.setStroke((int)(2*d),Color.parseColor("#C62828"));}circle.setBackground(sh);if(!esConductor){if(esMio){circle.setClickable(true);circle.setFocusable(true);circle.setOnClickListener(v->mostrarBottomSheetParada());}else if(libre&&!yaReservo){circle.setClickable(true);circle.setFocusable(true);circle.setOnClickListener(v->{cupoSeleccionado=idx;mostrarBottomSheetParada();});}}layoutCupos.addView(circle);}});}

    // =========================================================================
    //  POLLING
    // =========================================================================
    private void iniciarPolling(){if(pollingActivo)return;pollingActivo=true;pollingRunnable=new Runnable(){@Override public void run(){if(!pollingActivo)return;refrescar();pollingHandler.postDelayed(this,POLLING_MS);}};pollingHandler.postDelayed(pollingRunnable,POLLING_MS);}

    private void refrescar(){ConexionApi.getInstance(this).getObject(Constantes.viajePorId((long)viajeId),response->{int nd=response.optInt("cuposDisponibles",cuposDisponibles),nt=response.optInt("cuposTotales",cuposTotales);String ne=response.optString("estado",estadoViaje).trim().toUpperCase();if(nd!=cuposDisponibles||nt!=cuposTotales){cuposDisponibles=nd;cuposTotales=nt;actualizarChipsCupos(cuposTotales,cuposDisponibles);if(esConductor)cargarReservasConductor();runOnUiThread(this::actualizarBotonPasajero);}if(!ne.equals(estadoViaje)){estadoViaje=ne;runOnUiThread(()->txtEstado.setText(etiquetaEstado(estadoViaje)));if(ne.equals("INICIADO")||ne.equals("FINALIZADO"))cargarDetalleViaje();}if(!esConductor&&yaReservo&&idReservaActual>0)actualizarEstadoReservaPorPolling();},error->{});}

    private void actualizarEstadoReservaPorPolling(){ConexionApi.getInstance(this).getObject(Constantes.RESERVAS+"/"+idReservaActual,response->{String nuevo=response.optString("estado","").toUpperCase();if(!nuevo.equals(estadoReserva)){estadoReserva=nuevo;runOnUiThread(()->{mostrarCardMiReserva(response);if(EST_RECOGIDO.equals(estadoReserva)||EST_COMPLETADO.equals(estadoReserva))pedirRutaConWaypoint();else renderizarMapa();});}},error->{});}

    // =========================================================================
    //  ACCIONES CONDUCTOR
    // =========================================================================
    private void cambiarEstadoViaje(String accion){loaderDetalle.setVisibility(View.VISIBLE);ConexionApi.getInstance(this).post(Constantes.viajePorId((long)viajeId)+"/"+accion,null,response->{loaderDetalle.setVisibility(View.GONE);Toast.makeText(this,"✅ Viaje "+accion+"do",Toast.LENGTH_SHORT).show();cargarDetalleViaje();},error->{loaderDetalle.setVisibility(View.GONE);Toast.makeText(this,"Error al "+accion,Toast.LENGTH_LONG).show();});}
    private void confirmarFinalizar(){new AlertDialog.Builder(this).setTitle("Finalizar viaje").setMessage("¿Finalizar? Se liberarán todos los cupos.").setPositiveButton("Finalizar",(d,w)->finalizarViaje()).setNegativeButton("Cancelar",null).show();}
    private void finalizarViaje(){loaderDetalle.setVisibility(View.VISIBLE);detenerTodo();ConexionApi.getInstance(this).post(Constantes.viajePorId((long)viajeId)+"/pasajeros-bajaron",null,r->ConexionApi.getInstance(this).post(Constantes.viajeFinalizar((long)viajeId),null,r2->{loaderDetalle.setVisibility(View.GONE);Toast.makeText(this,"✅ Viaje finalizado.",Toast.LENGTH_LONG).show();cargarDetalleViaje();},e2->{loaderDetalle.setVisibility(View.GONE);Toast.makeText(this,"Error finalizando",Toast.LENGTH_LONG).show();}),error->cambiarEstadoViaje("finalizar"));}

    // =========================================================================
    //  BOTONES UI
    // =========================================================================
    private void configurarBotones() {
        if (btnAccionPrincipal != null) btnAccionPrincipal.setVisibility(View.GONE);
        btnIniciar.setVisibility(View.GONE);
        btnFinalizar.setVisibility(View.GONE);
        if (btnMensajeConductor != null) btnMensajeConductor.setVisibility(View.GONE);
        if (btnRecoger != null) btnRecoger.setVisibility(View.GONE);

        if (esConductor) {
            if (estadoViaje.equals("CREADO") || estadoViaje.equals("PROGRAMADO")
                    || estadoViaje.equals("DISPONIBLE")) {
                btnIniciar.setVisibility(View.VISIBLE);
            }
            if (estadoViaje.equals("EN_CURSO") || estadoViaje.equals("INICIADO")) {
                btnFinalizar.setVisibility(View.VISIBLE);
            }
        } else {
            if (ESTADOS_RESERVABLES.contains(estadoViaje)) {
                verificarReservaActivaYMostrarBoton();
            } else if (estadoViaje.equals("CREADO") || estadoViaje.equals("PROGRAMADO")
                    || estadoViaje.equals("DISPONIBLE")) {
                mostrarBannerEsperaInicio();
            }
        }
        actualizarBotonChat();
    }

    private void verificarReservaActivaYMostrarBoton() {
        ConexionApi.getInstance(this).getArray(Constantes.MIS_RESERVAS,
                response -> {
                    boolean tieneOtraReservaActiva = false;
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject r = response.optJSONObject(i);
                        if (r == null) continue;
                        String est = r.optString("estado", "").toUpperCase();
                        int idV = r.optInt("idViaje", 0);
                        if (idV == 0) {
                            JSONObject vo = r.optJSONObject("viaje");
                            if (vo != null) idV = vo.optInt("idViaje", vo.optInt("id", 0));
                        }
                        if (ESTADOS_RESERVA_ACTIVA.contains(est) && idV != viajeId) {
                            tieneOtraReservaActiva = true;
                            break;
                        }
                    }
                    final boolean bloqueado = tieneOtraReservaActiva;
                    runOnUiThread(() -> {
                        if (bloqueado) mostrarBannerReservaActiva();
                        else           actualizarBotonPasajero();
                    });
                },
                error -> runOnUiThread(this::actualizarBotonPasajero)
        );
    }

    private void mostrarBannerEsperaInicio() {
        if (btnAccionPrincipal == null) return;
        btnAccionPrincipal.setText("⏳ El conductor aún no inició el viaje");
        btnAccionPrincipal.setEnabled(false);
        btnAccionPrincipal.setAlpha(0.65f);
        btnAccionPrincipal.setBackgroundColor(Color.parseColor("#B0BEC5"));
        btnAccionPrincipal.setVisibility(View.VISIBLE);
    }

    private void mostrarBannerReservaActiva() {
        if (btnAccionPrincipal == null) return;
        btnAccionPrincipal.setText("🔒 Ya tienes un viaje activo");
        btnAccionPrincipal.setEnabled(false);
        btnAccionPrincipal.setAlpha(0.75f);
        btnAccionPrincipal.setBackgroundColor(Color.parseColor("#EF5350"));
        btnAccionPrincipal.setVisibility(View.VISIBLE);
    }

    private void actualizarBotonPasajero(){
        if(esConductor||btnAccionPrincipal==null)return;
        if(yaReservo){
            btnAccionPrincipal.setText("🔄 CAMBIAR PARADA");
            btnAccionPrincipal.setEnabled(true);
            btnAccionPrincipal.setAlpha(1f);
            btnAccionPrincipal.setVisibility(View.VISIBLE);
            btnAccionPrincipal.setBackgroundColor(Color.parseColor("#1565C0"));
        } else if(cuposDisponibles>0 && ESTADOS_RESERVABLES.contains(estadoViaje)){
            btnAccionPrincipal.setText("🚏 ELEGIR PARADA");
            btnAccionPrincipal.setEnabled(true);
            btnAccionPrincipal.setAlpha(1f);
            btnAccionPrincipal.setVisibility(View.VISIBLE);
            btnAccionPrincipal.setBackgroundColor(Color.parseColor("#00897B"));
        } else {
            btnAccionPrincipal.setVisibility(View.GONE);
        }
    }

    private void actualizarBotonChat(){if(btnMensajeConductor==null)return;if(esConductor){String l=nombrePasajeroViaje.isEmpty()?"Pasajero":nombrePasajeroViaje;btnMensajeConductor.setText("💬  Chat con "+l);btnMensajeConductor.setVisibility(idPasajeroViaje>0?View.VISIBLE:View.GONE);}else{if(idConductorViaje>0){btnMensajeConductor.setText("💬  Chat con "+(nombreConductorViaje.isEmpty()?"Conductor":nombreConductorViaje));btnMensajeConductor.setVisibility(View.VISIBLE);btnMensajeConductor.setEnabled(true);btnMensajeConductor.setAlpha(1f);}else{btnMensajeConductor.setText("💬  Chat con Conductor");btnMensajeConductor.setVisibility(View.VISIBLE);btnMensajeConductor.setEnabled(false);btnMensajeConductor.setAlpha(0.5f);new Handler(Looper.getMainLooper()).postDelayed(this::cargarConductorDelViaje,1500);}}}

    // =========================================================================
    //  CHAT
    // =========================================================================
    private void abrirOCrearChat(){if(!esConductor&&idConductorViaje<=0){Toast.makeText(this,"Cargando datos del conductor...",Toast.LENGTH_SHORT).show();cargarConductorDelViaje();new Handler(Looper.getMainLooper()).postDelayed(()->{if(idConductorViaje>0)iniciarConversacion();else Toast.makeText(this,"No se pudo identificar al conductor.",Toast.LENGTH_LONG).show();},1500);return;}iniciarConversacion();}
    private void iniciarConversacion(){if(!esConductor&&idConductorViaje<=0){Toast.makeText(this,"No se pudo identificar al conductor.",Toast.LENGTH_LONG).show();return;}int miId=session.getIdUsuario();int idP,idC;String nom;if(esConductor){idP=idPasajeroViaje>0?idPasajeroViaje:miId;idC=miId;nom=nombrePasajeroViaje.isEmpty()?"Pasajero":nombrePasajeroViaje;}else{idP=miId;idC=idConductorViaje;nom=nombreConductorViaje.isEmpty()?"Conductor":nombreConductorViaje;}JSONObject body=new JSONObject();try{body.put("idViaje",viajeId);body.put("idPasajero",idP);body.put("idConductor",idC);}catch(JSONException e){return;}final String nf=nom;final int pf=idP;final int cf=idC;loaderDetalle.setVisibility(View.VISIBLE);ConexionApi.getInstance(this).post(Constantes.CHAT_CONVERSACIONES,body,response->{loaderDetalle.setVisibility(View.GONE);long ic=extraerIdConversacion(response);if(ic>0)navegarAlChat(ic,nf);else buscarConversacion(pf,cf,nf);},error->{loaderDetalle.setVisibility(View.GONE);buscarConversacion(pf,cf,nf);});}
    private void buscarConversacion(int idP,int idC,String nom){String url=Constantes.CHAT_CONVERSACIONES+"?idViaje="+viajeId+"&idPasajero="+idP+"&idConductor="+idC;ConexionApi.getInstance(this).getObject(url,response->{long ic=extraerIdConversacion(response);if(ic<=0){for(String k:new String[]{"content","conversaciones","data"}){JSONArray a=response.optJSONArray(k);if(a!=null&&a.length()>0){ic=extraerIdConversacion(a.optJSONObject(0));if(ic>0)break;}}}if(ic>0)navegarAlChat(ic,nom);else Toast.makeText(this,"No se pudo abrir el chat",Toast.LENGTH_LONG).show();},error->Toast.makeText(this,"Error al abrir chat",Toast.LENGTH_SHORT).show());}
    private long extraerIdConversacion(JSONObject r){if(r==null)return -1;long id=r.optLong("id",-1);if(id>0)return id;id=r.optLong("idConversacion",-1);if(id>0)return id;JSONObject d=r.optJSONObject("data");if(d!=null){id=d.optLong("id",-1);if(id>0)return id;}return -1;}
    private void navegarAlChat(long idC,String nom){Intent i=new Intent(this,Chat.class);i.putExtra("idConversacion",idC);i.putExtra("nombre",nom);startActivity(i);}
    private void cargarConductorDelViaje(){if(idConductorViaje>0)return;ConexionApi.getInstance(this).getObject(Constantes.viajePorId((long)viajeId),response->{try{extraerConductor(response);if(idConductorViaje<=0){JSONObject v=response.optJSONObject("vehiculo");if(v!=null){int iv=v.optInt("idUsuario",-1);if(iv>0){idConductorViaje=iv;nombreConductorViaje="Conductor #"+iv;}}}runOnUiThread(()->{actualizarNombreConductorUI();actualizarBotonChat();});}catch(Exception e){Log.e(TAG,"Error extrayendo conductor",e);}},error->Log.e(TAG,"Error recargando viaje"));}

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private void extraerConductor(JSONObject r){JSONObject co=r.optJSONObject("conductor");if(co!=null){idConductorViaje=extractId(co);nombreConductorViaje=extractNombre(co);if(idConductorViaje<=0||nombreConductorViaje.isEmpty()){JSONObject u=co.optJSONObject("usuario");if(u!=null){if(idConductorViaje<=0)idConductorViaje=extractId(u);if(nombreConductorViaje.isEmpty())nombreConductorViaje=extractNombre(u);}}}if(idConductorViaje<=0)for(String c:new String[]{"idConductor","conductorId","idUsuarioConductor"}){int v=r.optInt(c,-1);if(v>0){idConductorViaje=v;break;}}if(nombreConductorViaje.isEmpty())for(String c:new String[]{"nombreConductor","conductorNombre"}){String v=r.optString(c,"");if(!v.isEmpty()&&!v.equals("null")){nombreConductorViaje=v;break;}}if(idConductorViaje>0&&nombreConductorViaje.isEmpty())nombreConductorViaje="Conductor #"+idConductorViaje;}
    private void actualizarNombreConductorUI(){if(txtConductor==null)return;String n=nombreConductorViaje.isEmpty()?"Sin asignar":nombreConductorViaje;txtConductor.setText(esConductor&&idConductorViaje==session.getIdUsuario()?"🚗 Tú ("+n+")":"🚗 "+n);}
    private void mostrarFechaHora(String fh){if(txtFechaHora==null)return;if(!fh.isEmpty()&&!fh.equals("null")){try{String fl=fh.replace("T"," ").replaceAll("\\.\\d{3}Z$","");SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault());SimpleDateFormat out=new SimpleDateFormat("EEE dd/MM/yyyy  🕐 HH:mm",new Locale("es","CO"));Date date=in.parse(fl);txtFechaHora.setText("📅 "+out.format(date));}catch(Exception ex){txtFechaHora.setText("📅 "+fh.replace("T"," ").replaceAll("\\.\\d{3}Z$",""));}txtFechaHora.setVisibility(View.VISIBLE);}else txtFechaHora.setVisibility(View.GONE);}
    private String etiquetaEstado(String e){switch(e){case"CREADO":return"📋 Estado: Disponible";case"PROGRAMADO":return"📅 Estado: Programado";case"DISPONIBLE":return"✅ Estado: Disponible";case"EN_CURSO":case"INICIADO":return"🚗 Estado: En curso";case"FINALIZADO":return"🏁 Estado: Finalizado";case"CANCELADO":return"❌ Estado: Cancelado";default:return"📌 Estado: "+e;}}
    private int extractId(JSONObject obj){if(obj==null)return -1;for(String k:new String[]{"id","idUsuarios","idUsuario","userId","conductorId","idConductor","pasajeroId","idPasajero"}){int v=obj.optInt(k,-1);if(v>0)return v;}return -1;}
    private String extractNombre(JSONObject obj){if(obj==null)return"";for(String k:new String[]{"nombre","nombreCompleto","name","fullName","nombreUsuario","displayName"}){String v=obj.optString(k,"");if(!v.isEmpty()&&!v.equals("null"))return v;}String n=obj.optString("nombres",""),a=obj.optString("apellidos","");if(!n.isEmpty()||!a.isEmpty())return(n+" "+a).trim();return"";}
    private double primeraCoord(JSONObject o,String[]cs,double def){for(String c:cs){double v=o.optDouble(c,Double.NaN);if(!Double.isNaN(v)&&v!=0)return v;}return def;}
    private double primeraCoordDistinta(JSONObject o,String[]cs,double ref,double def){for(String c:cs){double v=o.optDouble(c,Double.NaN);if(!Double.isNaN(v)&&v!=0&&Math.abs(v-ref)>0.0001)return v;}return def;}
    private String primeraStr(JSONObject o,String[]cs){for(String c:cs){String v=o.optString(c,"").trim();if(!v.isEmpty()&&!v.equals("null"))return v;}return"";}
    private boolean sonIguales(double la,double ln,double lb,double lm){return Math.abs(la-lb)<0.0001&&Math.abs(ln-lm)<0.0001;}
    private double[] geocodificarTexto(String dir){try{String q=dir.toLowerCase().contains("popay")?dir:dir+", Popayan, Colombia";String url="https://nominatim.openstreetmap.org/search?q="+java.net.URLEncoder.encode(q,"UTF-8")+"&format=json&limit=1&countrycodes=co";String resp=peticionHttp(url);if(resp==null||resp.isEmpty()||resp.equals("[]"))return null;JSONArray arr=new JSONArray(resp);if(arr.length()==0)return null;JSONObject obj=arr.getJSONObject(0);return new double[]{obj.getDouble("lat"),obj.getDouble("lon")};}catch(Exception e){return null;}}
    private String peticionHttp(String urlStr)throws Exception{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(urlStr).openConnection();c.setRequestProperty("User-Agent","Moviflexx-App/1.0");c.setConnectTimeout(15000);c.setReadTimeout(15000);BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()));StringBuilder sb=new StringBuilder();String l;while((l=r.readLine())!=null)sb.append(l);r.close();return sb.toString();}finally{if(c!=null)c.disconnect();}}
    @SuppressWarnings("unchecked")
    private ArrayList<GeoPoint> parsearGeoJson(java.util.Map<String,Object>geojson){if(geojson==null)return null;try{java.util.Map<String,Object>geometry=null;String tipo=String.valueOf(geojson.get("type"));if("Feature".equals(tipo))geometry=(java.util.Map<String,Object>)geojson.get("geometry");else if("LineString".equals(tipo))geometry=geojson;else if("FeatureCollection".equals(tipo)){List<Object>f=(List<Object>)geojson.get("features");if(f!=null&&!f.isEmpty())geometry=(java.util.Map<String,Object>)((java.util.Map<String,Object>)f.get(0)).get("geometry");}else{Object g=geojson.get("geometry");if(g instanceof java.util.Map)geometry=(java.util.Map<String,Object>)g;}if(geometry==null)return null;List<Object>coords=(List<Object>)geometry.get("coordinates");if(coords==null||coords.isEmpty())return null;ArrayList<GeoPoint>pts=new ArrayList<>();for(Object coord:coords){List<Object>pair=(List<Object>)coord;if(pair.size()>=2){double lng=((Number)pair.get(0)).doubleValue();double lat=((Number)pair.get(1)).doubleValue();pts.add(new GeoPoint(lat,lng));}}return pts.size()>=2?pts:null;}catch(Exception e){return null;}}
    private Bitmap crearBitmapMarcador(int colorInt,String letra){int size=96;Bitmap bmp=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bmp);Paint pS=new Paint(Paint.ANTI_ALIAS_FLAG);pS.setColor(Color.argb(80,0,0,0));c.drawCircle(size/2f+3,size/2f+5,size/2f-6,pS);Paint pC=new Paint(Paint.ANTI_ALIAS_FLAG);pC.setColor(colorInt);c.drawCircle(size/2f,size/2f-4,size/2f-8,pC);Paint pB=new Paint(Paint.ANTI_ALIAS_FLAG);pB.setColor(Color.WHITE);pB.setStyle(Paint.Style.STROKE);pB.setStrokeWidth(5f);c.drawCircle(size/2f,size/2f-4,size/2f-8,pB);Paint pT=new Paint(Paint.ANTI_ALIAS_FLAG);pT.setColor(Color.WHITE);pT.setTextSize(letra.length()>1?26f:36f);pT.setTypeface(Typeface.DEFAULT_BOLD);pT.setTextAlign(Paint.Align.CENTER);c.drawText(letra,size/2f,size/2f+9,pT);return bmp;}

    // =========================================================================
    //  MODELO
    // =========================================================================
    private static class ParadaDinamica{String nombre;double lat,lng;int idxEnRuta;int idParadaBD=0;ParadaDinamica(String nombre,double lat,double lng,int idx){this.nombre=nombre;this.lat=lat;this.lng=lng;this.idxEnRuta=idx;}GeoPoint toGeoPoint(){return new GeoPoint(lat,lng);}}

    // =========================================================================
    //  ADAPTER
    // =========================================================================
    static class ParadaAdapter extends RecyclerView.Adapter<ParadaAdapter.VH>{private final ArrayList<String>items=new ArrayList<>();ParadaAdapter(ArrayList<String>paradas,String origen,String destino){items.add("🟢 "+origen+"  (Inicio)");for(String p:paradas)items.add("🔵 "+p);items.add("🔴 "+destino+"  (Destino)");}@NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent,int vt){TextView tv=new TextView(parent.getContext());tv.setPadding(32,20,32,20);tv.setTextSize(14f);tv.setTextColor(Color.parseColor("#004D40"));return new VH(tv);}@Override public void onBindViewHolder(@NonNull VH h,int pos){((TextView)h.itemView).setText(items.get(pos));}@Override public int getItemCount(){return items.size();}static class VH extends RecyclerView.ViewHolder{VH(@NonNull View v){super(v);}}}
}