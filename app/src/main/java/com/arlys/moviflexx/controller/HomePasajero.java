package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.VoiceAssistantManager;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Manager.CalificacionesManager;
import com.arlys.moviflexx.model.NotificacionesHelper;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomePasajero extends BaseActivity {

    private static final String TAG = "HomePasajero";

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView             tvNombreUsuario;
    private TextView             tvSaludo;
    private ProgressBar          pbRutas;
    private LinearLayout         layoutRutas;
    private MaterialButton       btnBuscarViaje;
    private BottomNavigationView bottomNavigation;

    // ── Carrusel ──────────────────────────────────────────────────────────────
    private RecyclerView        rvCarruselInfo;
    private LinearLayout        layoutDotsInfo;
    private CarruselInfoAdapter carruselAdapter;
    private final Handler       carruselHandler = new Handler(Looper.getMainLooper());
    private Runnable            carruselRunnable;
    private int                 carruselPos     = 0;

    // ── Sección Por Calificar ─────────────────────────────────────────────────
    private LinearLayout sectionCalificar;
    private LinearLayout layoutCalificaciones;
    private View         dividerCalificar;
    private int viajeActivoId = -1;

    private LinearLayout sectionPagosPendientes, layoutPagosPendientes;
    private View dividerPagos;

    // ── Botón "Mis Viajes" ────────────────────────────────────────────────────
    private android.widget.FrameLayout btnMisViajesFrame;

    // ── Data ──────────────────────────────────────────────────────────────────
    private SessionManager session;
    private boolean calificacionPendienteVerificada = false;

    // ═══════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(
                android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_home_pasajero);

        session = new SessionManager(this);
        session.loadSessionToMemory();

        bindViews();
        mostrarNombreUsuario();
        configurarListeners();
        configurarBottomNav();
        iniciarCarruselMoviflexInfo();


        // 🔥 Asistente de Voz (Iniciado automáticamente por BaseActivity)
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarRutasFrecuentesPasajero();
        iniciarAutoScrollCarrusel();
        cargarViajesPorCalificar();
        NotificacionesHelper.configurar(this);

        if (bottomNavigation != null)
            bottomNavigation.setSelectedItemId(R.id.nav_inicio);

        ConexionApi.getInstance(this).getArrayNoCache(
                Constantes.MIS_RESERVAS,
                response -> {
                    boolean tieneReservaActiva = false;
                    if (response != null) {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject r = response.optJSONObject(i);
                            if (r == null) continue;
                            String est = r.optString("estado", "").toUpperCase().trim();
                            if ("RESERVADO".equals(est) || "CONFIRMADO".equals(est)
                                    || "CONFIRMADA".equals(est) || "RECOGIDO".equals(est)
                                    || "ESPERANDO_RECOGIDA".equals(est) || "EN_CURSO".equals(est)
                                    || "ACTIVO".equals(est) || "ACTIVA".equals(est)) {
                                JSONObject viaje = r.optJSONObject("viaje");
                                if (viaje != null) {
                                    String estV = viaje.optString("estado", "").toUpperCase().trim();
                                    if ("INICIADO".equals(estV) || "EN_CURSO".equals(estV)
                                            || "ACTIVO".equals(estV) || "CREADO".equals(estV)
                                            || "DISPONIBLE".equals(estV) || "PROGRAMADO".equals(estV)) {
                                        tieneReservaActiva = true;
                                        break;
                                    }
                                } else {
                                    tieneReservaActiva = true;
                                    break;
                                }
                            }
                        }
                    }
                    final boolean fActivo = tieneReservaActiva;
                    runOnUiThread(() -> {
                        if (bottomNavigation != null)
                            bottomNavigation.getMenu().findItem(R.id.nav_mapa).setEnabled(fActivo);
                    });
                },
                err -> {}
        );

        if (!calificacionPendienteVerificada) {
            calificacionPendienteVerificada = true;
            new Handler(Looper.getMainLooper()).postDelayed(
                    this::verificarCalificacionesPendientes, 1200);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        detenerAutoScrollCarrusel();
        calificacionPendienteVerificada = false;
        pagosPendientesYaVerificados = false; 
    }

    @Override
    protected void onStop() {
        super.onStop();
    }


    // ═══════════════════════════════════════════════════════════════════════════
    //  BIND
    // ═══════════════════════════════════════════════════════════════════════════

    private void bindViews() {
        tvNombreUsuario      = findViewById(R.id.tv_nombre_usuario);
        tvSaludo             = findViewById(R.id.tv_saludo);
        pbRutas              = findViewById(R.id.pb_rutas);
        layoutRutas          = findViewById(R.id.layout_rutas_frecuentes);
        btnBuscarViaje       = findViewById(R.id.btn_buscar_viaje);
        bottomNavigation     = findViewById(R.id.bottom_navigation);
        rvCarruselInfo       = findViewById(R.id.rv_carrusel_info);
        layoutDotsInfo       = findViewById(R.id.layout_dots_info);
        btnMisViajesFrame    = findViewById(R.id.btn_mis_viajes_frame);
        sectionCalificar     = findViewById(R.id.section_por_calificar);
        layoutCalificaciones = findViewById(R.id.layout_calificaciones_pendientes);
        dividerCalificar     = findViewById(R.id.divider_calificar);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  NOMBRE + SALUDO
    // ═══════════════════════════════════════════════════════════════════════════

    private void mostrarNombreUsuario() {
        String nombre = session.getNombre();
        if (nombre != null && !nombre.isEmpty() && !nombre.equals("Usuario")) {
            aplicarSaludo(nombre);
        } else {
            cargarNombreDesdeAPI();
        }
    }

    private void aplicarSaludo(String nombre) {
        int hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String saludo = hora >= 5  && hora < 12 ? "Buenos días "
                : hora >= 12 && hora < 18 ? "Buenas tardes "
                :                           "Buenas noches ";
        if (tvSaludo        != null) tvSaludo.setText(saludo);
        if (tvNombreUsuario != null) tvNombreUsuario.setText(nombre);

        // ── Foto de perfil en el avatar del header ──
        String fotoUrl = session.getFotoPerfil();
        android.widget.ImageView ivAvatar = findViewById(R.id.iv_avatar_header);
        com.google.android.material.card.MaterialCardView cardFoto =
                findViewById(R.id.card_avatar_foto_header);
        com.google.android.material.card.MaterialCardView cardInicial =
                findViewById(R.id.card_avatar_inicial_header);

        if (ivAvatar != null && !fotoUrl.isEmpty() && !fotoUrl.equals("null")) {
            if (cardFoto    != null) cardFoto.setVisibility(View.VISIBLE);
            if (cardInicial != null) cardInicial.setVisibility(View.GONE);
            com.bumptech.glide.Glide.with(this)
                    .load(fotoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.logomo)
                    .error(R.drawable.logomo)
                    .into(ivAvatar);
        } else {
            if (cardFoto    != null) cardFoto.setVisibility(View.GONE);
            if (cardInicial != null) cardInicial.setVisibility(View.VISIBLE);
        }
    }

    private void cargarNombreDesdeAPI() {
        int id = session.getIdUsuario();
        if (id <= 0) return;
        ConexionApi.getInstance(this).getObject(
                Constantes.usuarioDetalle((long) id),
                response -> {
                    String nombre = response.optString("nombre", "");
                    if (nombre.isEmpty()) nombre = response.optString("nombres", "");
                    if (nombre.isEmpty()) nombre = response.optString("nombreCompleto", "");
                    if (nombre.isEmpty()) nombre = "Usuario";
                    String email    = response.optString("email",    "");
                    String telefono = response.optString("telefono", "");
                    int    idRol    = response.optInt("idRol", 1);
                    session.saveUser(nombre, email, telefono, idRol, id);
                    final String n = nombre;
                    runOnUiThread(() -> aplicarSaludo(n));
                },
                error -> {}
        );
    }

    private boolean pagosPendientesYaVerificados = false;

    private void cargarPagosPendientes() {
        // ── Pagos pendientes ya no se muestran en el home ──────────────────────
        // Se muestran como notificaciones en la pantalla de Notificaciones.
        // El badge del campanazo (NotificacionesHelper) ya los cuenta.
        // Este método se conserva vacío para no romper las llamadas existentes.
    }

    private void verificarPagosPendientesYMostrar(List<JSONObject> todos, int idPasajero,
                                                  List<JSONObject> sinPagar, int indice) {
        // No se usa — los pagos se muestran en Notificaciones
    }

    private void mostrarCardsPagosPendientes(List<JSONObject> items) {
        // Ocultar la sección si existe en el layout
        if (sectionPagosPendientes != null)
            sectionPagosPendientes.setVisibility(View.GONE);
        if (dividerPagos != null)
            dividerPagos.setVisibility(View.GONE);
        if (layoutPagosPendientes != null)
            layoutPagosPendientes.removeAllViews();
        // Los pagos pendientes ahora se ven en la pantalla de Notificaciones
    }
    // ═══════════════════════════════════════════════════════════════════════════
    //  LISTENERS + BOTTOM NAV
    // ═══════════════════════════════════════════════════════════════════════════

    private void configurarListeners() {
        if (btnBuscarViaje != null)
            btnBuscarViaje.setOnClickListener(v ->
                    startActivity(new Intent(this, BuscarRuta.class)));

        if (btnMisViajesFrame != null)
            btnMisViajesFrame.setOnClickListener(v -> abrirBottomSheetMisViajes());
    }

    private void configurarBottomNav() {
        if (bottomNavigation == null) return;
        bottomNavigation.setSelectedItemId(R.id.nav_inicio);
        bottomNavigation.getMenu().findItem(R.id.nav_mapa).setEnabled(false);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_inicio)     return true;
            else if (id == R.id.nav_mis_viajes) { startActivity(new Intent(this, MisReservasActivity.class)); return true; }
            else if (id == R.id.nav_mapa)       { verificarViajeActivoYAbrirMapaPasajero(); return true; }
            else if (id == R.id.nav_mensajes)   { startActivity(new Intent(this, Mensajes.class)); return true; }
            else if (id == R.id.nav_perfil)     { startActivity(new Intent(this, PerfilUsuario.class)); return true; }
            return false;
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MAPA
    // ═══════════════════════════════════════════════════════════════════════════

    private void verificarViajeActivoYAbrirMapaPasajero() {
        ConexionApi.getInstance(this).getArrayNoCache(
                Constantes.MIS_RESERVAS,
                response -> {
                    if (response == null || response.length() == 0) {
                        runOnUiThread(() -> startActivity(new Intent(this, Mapa.class)));
                        return;
                    }

                    for (int i = 0; i < response.length(); i++) {
                        try {
                            JSONObject res = response.optJSONObject(i);
                            if (res == null) continue;

                            Log.d(TAG, "─── Reserva[" + i + "] JSON: " + res.toString());

                            String estReserva = res.optString("estado", "").toUpperCase().trim();
                            boolean reservaActiva =
                                    "ESPERANDO_RECOGIDA".equals(estReserva)
                                            || "RECOGIDO".equals(estReserva)
                                            || "CONFIRMADA".equals(estReserva)
                                            || "CONFIRMADO".equals(estReserva)
                                            || "ACTIVA".equals(estReserva)
                                            || "ACTIVO".equals(estReserva)
                                            || "ACEPTADA".equals(estReserva)
                                            || "ACEPTADO".equals(estReserva)
                                            || "PAGADA".equals(estReserva)
                                            || "PAGADO".equals(estReserva)
                                            || "EN_CURSO".equals(estReserva)
                                            || "RESERVADO".equals(estReserva);

                            if (!reservaActiva) continue;

                            JSONObject viaje = res.optJSONObject("viaje");
                            if (viaje == null) continue;

                            String estViaje = viaje.optString("estado", "").toUpperCase().trim();
                            boolean viajeEnCurso =
                                    "INICIADO".equals(estViaje)
                                            || "EN_CURSO".equals(estViaje)
                                            || "ACTIVO".equals(estViaje)
                                            || "CREADO".equals(estViaje)
                                            || "DISPONIBLE".equals(estViaje)
                                            || "PROGRAMADO".equals(estViaje);

                            if (!viajeEnCurso) continue;

                            int idViaje = 0;
                            for (String k : new String[]{"idViajes", "idViaje", "viajeId", "id_viaje"}) {
                                int v = res.optInt(k, 0);
                                if (v > 0) { idViaje = v; break; }
                            }
                            if (idViaje == 0) {
                                for (String k : new String[]{"idViajes", "idViaje", "id", "viajeId"}) {
                                    int v = viaje.optInt(k, 0);
                                    if (v > 0) { idViaje = v; break; }
                                }
                            }

                            double latO = viaje.optDouble("latOrigen", 0);
                            double lngO = viaje.optDouble("lngOrigen", 0);
                            double latD = viaje.optDouble("latDestino", 0);
                            double lngD = viaje.optDouble("lngDestino", 0);
                            String origen  = "";
                            String destino = "";

                            JSONObject ruta = viaje.optJSONObject("ruta");
                            if (ruta != null) {
                                if (latO == 0) latO = ruta.optDouble("latOrigen", ruta.optDouble("latitudOrigen", 0));
                                if (lngO == 0) lngO = ruta.optDouble("lngOrigen", ruta.optDouble("longitudOrigen", 0));
                                if (latD == 0) latD = ruta.optDouble("latDestino", ruta.optDouble("latitudDestino", 0));
                                if (lngD == 0) lngD = ruta.optDouble("lngDestino", ruta.optDouble("longitudDestino", 0));
                                origen  = ruta.optString("origen",  "");
                                destino = ruta.optString("destino", ruta.optString("nombre", ""));
                            }

                            double latS = 0, lngS = 0;
                            String nomS = "";
                            JSONObject subObj = res.optJSONObject("puntoSubida");
                            if (subObj == null) subObj = res.optJSONObject("subida");
                            if (subObj != null) {
                                latS = subObj.optDouble("lat", subObj.optDouble("latitud", 0));
                                lngS = subObj.optDouble("lng", subObj.optDouble("longitud", 0));
                                nomS = subObj.optString("nombre", "");
                            }
                            if (latS == 0) {
                                try { latS = Double.parseDouble(res.optString("latSubida", "0")); } catch (Exception ignored) {}
                                try { lngS = Double.parseDouble(res.optString("lngSubida", "0")); } catch (Exception ignored) {}
                                if (latS == 0) latS = res.optDouble("latSubida", res.optDouble("latOrigen", res.optDouble("latInicio", latO)));
                                if (lngS == 0) lngS = res.optDouble("lngSubida", res.optDouble("lngOrigen", res.optDouble("lngInicio", lngO)));
                                nomS = res.optString("nombreSubida", res.optString("nombreParadaSubida", res.optString("nombreParadaInicio", origen)));
                            }

                            double latB = 0, lngB = 0;
                            String nomB = "";
                            JSONObject bajObj = res.optJSONObject("puntoBajada");
                            if (bajObj == null) bajObj = res.optJSONObject("bajada");
                            if (bajObj != null) {
                                latB = bajObj.optDouble("lat", bajObj.optDouble("latitud", 0));
                                lngB = bajObj.optDouble("lng", bajObj.optDouble("longitud", 0));
                                nomB = bajObj.optString("nombre", "");
                            }
                            if (latB == 0) {
                                try { latB = Double.parseDouble(res.optString("latBajada", "0")); } catch (Exception ignored) {}
                                try { lngB = Double.parseDouble(res.optString("lngBajada", "0")); } catch (Exception ignored) {}
                                if (latB == 0) latB = res.optDouble("latBajada", res.optDouble("latParada", latD));
                                if (lngB == 0) lngB = res.optDouble("lngBajada", res.optDouble("lngParada", lngD));
                                nomB = res.optString("nombreBajada", res.optString("nombreParadaBajada", res.optString("nombreParada", destino)));
                            }

                            String nomCond = "";
                            JSONObject cond = viaje.optJSONObject("conductor");
                            if (cond != null) nomCond = extraerNombreConductor(cond);

                            final int    fId   = idViaje;
                            final double fLatO = latO, fLngO = lngO;
                            final double fLatD = latD, fLngD = lngD;
                            final double fLatS = latS, fLngS = lngS;
                            final double fLatB = latB, fLngB = lngB;
                            final String fNomS = nomS.isEmpty() ? origen  : nomS;
                            final String fNomB = nomB.isEmpty() ? destino : nomB;
                            final String fNomC = nomCond;

                            runOnUiThread(() -> {
                                Intent intent = new Intent(this, Mapa.class);
                                intent.putExtra("ID_VIAJE",      fId);
                                intent.putExtra("DESDE_VIAJE",   true);
                                intent.putExtra("ORIGEN_LAT",    fLatO);
                                intent.putExtra("ORIGEN_LNG",    fLngO);
                                intent.putExtra("DESTINO_LAT",   fLatD);
                                intent.putExtra("DESTINO_LNG",   fLngD);
                                intent.putExtra("LAT_SUBIDA",    fLatS);
                                intent.putExtra("LNG_SUBIDA",    fLngS);
                                intent.putExtra("LAT_BAJADA",    fLatB);
                                intent.putExtra("LNG_BAJADA",    fLngB);
                                intent.putExtra("NOM_SUBIDA",    fNomS);
                                intent.putExtra("NOM_BAJADA",    fNomB);
                                intent.putExtra("NOM_CONDUCTOR", fNomC);
                                startActivity(intent);
                            });
                            return;

                        } catch (Exception e) {
                            Log.e(TAG, "verificarViajeActivo[" + i + "]: " + e.getMessage());
                        }
                    }

                    runOnUiThread(() ->
                            new android.app.AlertDialog.Builder(this)
                                    .setTitle("Sin reserva activa")
                                    .setMessage("Primero debes hacer una reserva en un viaje para acceder al mapa.")
                                    .setCancelable(true)
                                    .setPositiveButton("Buscar viajes", (dlg, w) ->
                                            startActivity(new Intent(this, MisReservasActivity.class)))
                                    .setNegativeButton("Cancelar", null)
                                    .show()
                    );
                },
                error -> runOnUiThread(() -> startActivity(new Intent(this, Mapa.class)))
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BOTTOM SHEET MIS VIAJES
    // ═══════════════════════════════════════════════════════════════════════════

    private void abrirBottomSheetMisViajes() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        float d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F0FAFA")); // era #F5F7FA
        root.setPadding((int)(16*d), (int)(16*d), (int)(16*d), (int)(24*d));

        // Tirón handle
        View tiron = new View(this);
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams((int)(40*d), (int)(4*d));
        lpT.gravity = Gravity.CENTER_HORIZONTAL;
        lpT.bottomMargin = (int)(14*d);
        tiron.setLayoutParams(lpT);
        GradientDrawable gdT = new GradientDrawable();
        gdT.setShape(GradientDrawable.RECTANGLE);
        gdT.setCornerRadius(4*d);
        gdT.setColor(Color.parseColor("#D0ECEC")); // era #BDBDBD
        tiron.setBackground(gdT);
        root.addView(tiron);

        // Título
        TextView tvTitulo = new TextView(this);
        tvTitulo.setText("Mis Viajes Realizados");
        tvTitulo.setTextSize(19f);
        tvTitulo.setTypeface(null, Typeface.BOLD);
        tvTitulo.setTextColor(Color.parseColor("#1A2F4A")); // era #004D40
        LinearLayout.LayoutParams lpTit = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTit.bottomMargin = (int)(16*d);
        tvTitulo.setLayoutParams(lpTit);
        root.addView(tvTitulo);

        // ProgressBar
        ProgressBar pb = new ProgressBar(this);
        LinearLayout.LayoutParams lpPb = new LinearLayout.LayoutParams((int)(36*d), (int)(36*d));
        lpPb.gravity = Gravity.CENTER_HORIZONTAL;
        lpPb.topMargin = (int)(20*d);
        lpPb.bottomMargin = (int)(20*d);
        pb.setLayoutParams(lpPb);
        pb.setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#00CED1")));
        root.addView(pb);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(520*d)));
        scroll.setVisibility(View.GONE);
        LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        scroll.addView(lista);
        root.addView(scroll);

        dialog.setContentView(root);
        dialog.show();

        int idPasajero = session.getIdUsuario();

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_RESERVAS,
                response -> {
                    List<JSONObject> viajes = new ArrayList<>();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject r = response.optJSONObject(i);
                        if (r == null) continue;
                        JSONObject viaje = r.optJSONObject("viaje");
                        if (viaje == null) continue;
                        String estViaje = viaje.optString("estado", "").toUpperCase().trim();
                        boolean finalizado =
                                "FINALIZADO".equals(estViaje) || "COMPLETADO".equals(estViaje) ||
                                        "PAGADO".equals(estViaje) || "REALIZADO".equals(estViaje) ||
                                        "TERMINADO".equals(estViaje);
                        if (!finalizado) continue;
                        try {
                            JSONObject item = new JSONObject();
                            int viajeId = r.optInt("idViajes", viaje.optInt("idViajes", 0));
                            item.put("viajeId", viajeId);
                            int idConductor = -1;
                            String marca = "", modelo = "", placa = "";
                            JSONObject vehiculo = viaje.optJSONObject("vehiculo");
                            if (vehiculo != null) {
                                idConductor = vehiculo.optInt("idUsuario", -1);
                                marca  = vehiculo.optString("marca",  "");
                                modelo = vehiculo.optString("modelo", "");
                                placa  = vehiculo.optString("placa",  "");
                            }
                            item.put("idConductor", idConductor);
                            item.put("marcaVeh",  marca);
                            item.put("modeloVeh", modelo);
                            item.put("placaVeh",  placa);
                            String origen = "", destino = "";
                            JSONObject ruta = viaje.optJSONObject("ruta");
                            if (ruta != null) {
                                origen  = ruta.optString("origen", "");
                                String nombre = ruta.optString("nombre", "");
                                if (!nombre.isEmpty() && nombre.contains("→")) {
                                    String[] partes = nombre.split("→");
                                    if (origen.isEmpty() && partes.length > 0) origen = partes[0].trim();
                                    if (partes.length > 1) destino = partes[partes.length - 1].trim();
                                } else if (!nombre.isEmpty()) {
                                    destino = nombre;
                                }
                            }
                            if (destino.isEmpty()) destino = r.optString("nombreBajada", "");
                            item.put("origen",  origen);
                            item.put("destino", destino);
                            item.put("fecha", viaje.optString("fechaHoraSalida", ""));
                            double precio = -1;
                            String precioStr = r.optString("precioFinal", "");
                            if (!precioStr.isEmpty() && !precioStr.equals("null")) {
                                try { precio = Double.parseDouble(precioStr); } catch (Exception ignored) {}
                            }
                            item.put("precio", precio);
                            viajes.add(item);
                        } catch (Exception e) {
                            Log.e("MisViajes", "Error procesando reserva[" + i + "]", e);
                        }
                    }
                    runOnUiThread(() -> {
                        pb.setVisibility(View.GONE);
                        if (viajes.isEmpty()) {
                            TextView tv = new TextView(this);
                            tv.setText("No se encontraron viajes completados");
                            tv.setTextColor(Color.parseColor("#7A9BAA")); // era #90A4AE
                            tv.setTextSize(13f);
                            tv.setGravity(Gravity.CENTER);
                            tv.setPadding(0, (int)(24*d), 0, (int)(24*d));
                            root.addView(tv);
                        } else {
                            scroll.setVisibility(View.VISIBLE);
                            poblarListaViajes(lista, viajes, idPasajero, d, dialog);
                        }
                    });
                },
                error -> runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    TextView tv = new TextView(this);
                    tv.setText("No se pudo cargar el historial");
                    tv.setTextColor(Color.parseColor("#EF5350")); // mantener rojo error
                    tv.setTextSize(13f);
                    tv.setGravity(Gravity.CENTER);
                    root.addView(tv);
                })
        );
    }

    private void poblarListaViajes(LinearLayout lista, List<JSONObject> viajes,
                                   int idPasajero, float d, BottomSheetDialog dialog) {
        lista.removeAllViews();
        for (JSONObject item : viajes) {
            final int    viajeId     = item.optInt("viajeId", 0);
            final int    idConductor = item.optInt("idConductor", -1);
            final String marcaVeh    = item.optString("marcaVeh",  "");
            final String modeloVeh   = item.optString("modeloVeh", "");
            final String placaVeh    = item.optString("placaVeh",  "");
            final String fecha       = item.optString("fecha", "");
            final String destino     = item.optString("destino", "");
            final String origen      = item.optString("origen",  "");
            final double precio      = item.optDouble("precio", -1);

            String fechaFmt = fecha;
            try {
                String clean = fecha.replaceAll("\\.\\d{3}Z?$", "").replace("Z","");
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
                java.text.SimpleDateFormat out = new java.text.SimpleDateFormat(
                        "dd/MM/yyyy  HH:mm", java.util.Locale.getDefault());
                fechaFmt = out.format(sdf.parse(clean));
            } catch (Exception ignored) {}
            final String fechaFinal = fechaFmt;

            // Card
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bgCard = new GradientDrawable();
            bgCard.setShape(GradientDrawable.RECTANGLE);
            bgCard.setCornerRadius(18*d);
            bgCard.setColor(Color.WHITE);
            bgCard.setStroke((int)(1*d), Color.parseColor("#D0ECEC")); // era #E0F2F1
            card.setBackground(bgCard);
            card.setElevation(5*d);
            LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpCard.bottomMargin = (int)(14*d);
            card.setLayoutParams(lpCard);

            // Cabecera degradado teal — colores de la app
            LinearLayout cabecera = new LinearLayout(this);
            cabecera.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bgCab = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#00CED1"), Color.parseColor("#00868A")}); // era #009B8D/#00695C
            bgCab.setCornerRadii(new float[]{18*d,18*d,18*d,18*d,0,0,0,0});
            cabecera.setBackground(bgCab);
            cabecera.setPadding((int)(14*d), (int)(12*d), (int)(14*d), (int)(12*d));

            String rutaTexto = origen.isEmpty() ? destino : (origen + "  →  " + destino);
            TextView tvRuta = new TextView(this);
            tvRuta.setText(rutaTexto.isEmpty() ? "Viaje completado" : rutaTexto);
            tvRuta.setTextSize(13f);
            tvRuta.setTypeface(null, Typeface.BOLD);
            tvRuta.setTextColor(Color.WHITE);
            tvRuta.setMaxLines(2);
            cabecera.addView(tvRuta);

            if (!fechaFinal.isEmpty()) {
                TextView tvFecha = new TextView(this);
                tvFecha.setText(fechaFinal);
                tvFecha.setTextSize(11.5f);
                tvFecha.setTextColor(Color.argb(210, 255, 255, 255));
                LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lpF.topMargin = (int)(4*d);
                tvFecha.setLayoutParams(lpF);
                cabecera.addView(tvFecha);
            }
            card.addView(cabecera);

            // Cuerpo
            LinearLayout cuerpo = new LinearLayout(this);
            cuerpo.setOrientation(LinearLayout.VERTICAL);
            cuerpo.setPadding((int)(14*d), (int)(14*d), (int)(14*d), (int)(14*d));

            // Fila conductor
            LinearLayout filaCond = new LinearLayout(this);
            filaCond.setOrientation(LinearLayout.HORIZONTAL);
            filaCond.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpFC = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpFC.bottomMargin = (int)(10*d);
            filaCond.setLayoutParams(lpFC);

            // Avatar
            TextView tvAv = new TextView(this);
            tvAv.setText("C");
            tvAv.setTextSize(19f);
            tvAv.setTextColor(Color.WHITE);
            tvAv.setTypeface(null, Typeface.BOLD);
            tvAv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpAv = new LinearLayout.LayoutParams((int)(48*d), (int)(48*d));
            lpAv.rightMargin = (int)(12*d);
            tvAv.setLayoutParams(lpAv);
            GradientDrawable bgAv = new GradientDrawable();
            bgAv.setShape(GradientDrawable.OVAL);
            bgAv.setColors(new int[]{Color.parseColor("#00CED1"), Color.parseColor("#00868A")}); // era #00CED1/#00897B
            bgAv.setOrientation(GradientDrawable.Orientation.TL_BR);
            tvAv.setBackground(bgAv);
            filaCond.addView(tvAv);

            LinearLayout colCond = new LinearLayout(this);
            colCond.setOrientation(LinearLayout.VERTICAL);
            colCond.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvNom = new TextView(this);
            tvNom.setText("Conductor" + (idConductor > 0 ? "  #" + idConductor : ""));
            tvNom.setTextSize(15f);
            tvNom.setTypeface(null, Typeface.BOLD);
            tvNom.setTextColor(Color.parseColor("#1A2F4A")); // era #004D40
            colCond.addView(tvNom);

            String vStr = "";
            if (!marcaVeh.isEmpty())  vStr += marcaVeh;
            if (!modeloVeh.isEmpty()) vStr += " " + modeloVeh;
            if (!placaVeh.isEmpty())  vStr += "  (" + placaVeh + ")";
            if (!vStr.isEmpty()) {
                TextView tvVeh = new TextView(this);
                tvVeh.setText(vStr.trim());
                tvVeh.setTextSize(12f);
                tvVeh.setTextColor(Color.parseColor("#7A9BAA")); // era #546E7A
                LinearLayout.LayoutParams lpV = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lpV.topMargin = (int)(3*d);
                tvVeh.setLayoutParams(lpV);
                colCond.addView(tvVeh);
            }
            filaCond.addView(colCond);

            // Badge completado
            TextView tvBadge = new TextView(this);
            tvBadge.setText("Completado");
            tvBadge.setTextSize(10.5f);
            tvBadge.setTypeface(null, Typeface.BOLD);
            tvBadge.setTextColor(Color.WHITE);
            tvBadge.setPadding((int)(10*d), (int)(4*d), (int)(10*d), (int)(4*d));
            GradientDrawable bgB = new GradientDrawable();
            bgB.setShape(GradientDrawable.RECTANGLE);
            bgB.setCornerRadius(20*d);
            bgB.setColor(Color.parseColor("#00CED1")); // era #00897B
            tvBadge.setBackground(bgB);
            LinearLayout.LayoutParams lpBg = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpBg.leftMargin = (int)(6*d);
            tvBadge.setLayoutParams(lpBg);
            filaCond.addView(tvBadge);
            cuerpo.addView(filaCond);

            // Precio
            if (precio > 0) {
                TextView tvPrecio = new TextView(this);
                tvPrecio.setText("$" + String.format(java.util.Locale.getDefault(), "%,.0f", precio) + " COP");
                tvPrecio.setTextSize(13f);
                tvPrecio.setTextColor(Color.parseColor("#00868A")); // era #00695C
                tvPrecio.setTypeface(null, Typeface.BOLD);
                LinearLayout.LayoutParams lpPr = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lpPr.bottomMargin = (int)(10*d);
                tvPrecio.setLayoutParams(lpPr);
                cuerpo.addView(tvPrecio);
            }

            // Separador
            View sep = new View(this);
            LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int)(1*d));
            lpSep.setMargins(0, (int)(4*d), 0, (int)(12*d));
            sep.setLayoutParams(lpSep);
            sep.setBackgroundColor(Color.parseColor("#EEF5F5")); // era #E0F2F1
            cuerpo.addView(sep);

            // Botones
            LinearLayout filaBtns = new LinearLayout(this);
            filaBtns.setOrientation(LinearLayout.HORIZONTAL);
            filaBtns.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            final MaterialButton btnCal = new MaterialButton(this);
            btnCal.setText("Calificar viaje");
            btnCal.setTextSize(13f);
            btnCal.setTextColor(Color.WHITE);
            btnCal.setCornerRadius((int)(24*d));
            btnCal.setBackgroundColor(Color.parseColor("#00CED1")); // era #9C27B0
            btnCal.setEnabled(false);
            btnCal.setAlpha(0.5f);
            LinearLayout.LayoutParams lpCal = new LinearLayout.LayoutParams(0, (int)(44*d), 1f);
            lpCal.rightMargin = (int)(6*d);
            btnCal.setLayoutParams(lpCal);

            MaterialButton btnDetalle = new MaterialButton(this);
            btnDetalle.setText("Ver detalle");
            btnDetalle.setTextSize(13f);
            btnDetalle.setTextColor(Color.parseColor("#1A2F4A")); // era #004D40
            btnDetalle.setCornerRadius((int)(24*d));
            btnDetalle.setBackgroundColor(Color.parseColor("#EEF5F5")); // era #E0F2F1
            LinearLayout.LayoutParams lpDet = new LinearLayout.LayoutParams(0, (int)(44*d), 1f);
            lpDet.leftMargin = (int)(6*d);
            btnDetalle.setLayoutParams(lpDet);

            btnDetalle.setOnClickListener(v -> {
                if (viajeId > 0) {
                    dialog.dismiss();
                    Intent intent = new Intent(this, DetalleViajeActivity.class);
                    intent.putExtra("ID_VIAJE", viajeId);
                    startActivity(intent);
                } else {
                    Toast.makeText(HomePasajero.this,
                            "No se pudo obtener el ID del viaje", Toast.LENGTH_SHORT).show();
                }
            });

            filaBtns.addView(btnCal);
            filaBtns.addView(btnDetalle);
            cuerpo.addView(filaBtns);
            card.addView(cuerpo);
            lista.addView(card);

            if (idConductor > 0) {
                verificarYConfigurarBtnCalificar(btnCal, viajeId, idConductor,
                        "Conductor #" + idConductor, idPasajero);
            }
        }
    }

    private void verificarYConfigurarBtnCalificar(MaterialButton btn, int viajeId,
                                                  int idConductor, String nomCond, int idPasajero) {
        new CalificacionesManager(this).verificarCalificacion(
                viajeId, idPasajero, idConductor,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override public void onDebeCalificar() {
                        if (!isFinishing()) runOnUiThread(() -> {
                            btn.setEnabled(true);
                            btn.setAlpha(1f);
                            btn.setText("Calificar viaje");
                            btn.setBackgroundColor(Color.parseColor("#00CED1")); // era #9C27B0
                            btn.setOnClickListener(v ->
                                    CalificacionController.mostrarBottomSheetCalificar(
                                            HomePasajero.this, viajeId, idConductor, nomCond,
                                            "",
                                            idPasajero, false,
                                            (puntuacion, comentario) -> {
                                                btn.setText("Ya calificado (" + puntuacion + ")");
                                                btn.setBackgroundColor(Color.parseColor("#7A9BAA")); // era #9E9E9E
                                                btn.setEnabled(false);
                                                cargarViajesPorCalificar();
                                            })
                            );
                        });
                    }
                    @Override public void onYaCalifico(int p, String e) {
                        if (!isFinishing()) runOnUiThread(() -> {
                            btn.setText("Ya calificado (" + e + ")");
                            btn.setBackgroundColor(Color.parseColor("#7A9BAA")); // era #9E9E9E
                            btn.setEnabled(false);
                            btn.setAlpha(1f);
                        });
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CARDS POR CALIFICAR
    // ═══════════════════════════════════════════════════════════════════════════

    private void cargarViajesPorCalificar() {
        if (sectionCalificar == null || layoutCalificaciones == null) return;
        int idPasajero = session.getIdUsuario();
        if (idPasajero <= 0) return;

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_RESERVAS,
                response -> {
                    List<JSONObject> pendientes = new ArrayList<>();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject reserva = response.optJSONObject(i);
                        if (reserva == null) continue;
                        String estReserva = reserva.optString("estado", "").toUpperCase();
                        String estViaje   = "";
                        JSONObject viaje  = reserva.optJSONObject("viaje");
                        if (viaje != null) estViaje = viaje.optString("estado", "").toUpperCase();
                        boolean finalizado = "FINALIZADO".equals(estViaje) || "COMPLETADO".equals(estViaje)
                                || "COMPLETADO".equals(estReserva) || "COMPLETADA".equals(estReserva);
                        if (!finalizado) continue;
                        int viajeId = reserva.optInt("idViaje", 0);
                        if (viajeId == 0 && viaje != null)
                            viajeId = viaje.optInt("idViaje", viaje.optInt("id", 0));
                        if (viajeId == 0) continue;
                        int    idConductor  = -1;
                        String nomConductor = "";
                        String fechaViaje   = "";
                        String destinoViaje = "";
                        if (viaje != null) {
                            idConductor = viaje.optInt("idConductor", viaje.optInt("conductorId", -1));
                            JSONObject condObj = viaje.optJSONObject("conductor");
                            if (condObj != null) {
                                if (idConductor <= 0)
                                    for (String k : new String[]{"id","idUsuarios","idUsuario"}) {
                                        int id2 = condObj.optInt(k, -1);
                                        if (id2 > 0) { idConductor = id2; break; }
                                    }
                                nomConductor = extraerNombreConductor(condObj);
                            }
                            fechaViaje  = viaje.optString("fechaHoraSalida",
                                    viaje.optString("fechaSalida", viaje.optString("fecha", "")));
                            JSONObject ruta = viaje.optJSONObject("ruta");
                            if (ruta != null)
                                destinoViaje = ruta.optString("destino", ruta.optString("nombre", ""));
                        }
                        if (idConductor <= 0) continue;
                        try {
                            JSONObject it = new JSONObject();
                            it.put("viajeId",      viajeId);
                            it.put("idConductor",  idConductor);
                            it.put("nomConductor", nomConductor.isEmpty() ? "el conductor" : nomConductor);
                            it.put("fecha",        fechaViaje);
                            it.put("destino",      destinoViaje);
                            pendientes.add(it);
                        } catch (Exception ignored) {}
                    }
                    if (pendientes.isEmpty()) {
                        runOnUiThread(() -> {
                            if (sectionCalificar != null) sectionCalificar.setVisibility(View.GONE);
                            if (dividerCalificar != null) dividerCalificar.setVisibility(View.GONE);
                        });
                        return;
                    }
                    verificarYMostrarCards(pendientes, idPasajero, new ArrayList<>(), 0);
                },
                error -> Log.w(TAG, "cargarViajesPorCalificar: error=" + error)
        );
    }

    private void verificarYMostrarCards(List<JSONObject> todos, int idPasajero,
                                        List<JSONObject> sinCalificar, int indice) {
        if (indice >= todos.size()) {
            runOnUiThread(() -> mostrarCardsPorCalificar(sinCalificar, idPasajero));
            return;
        }
        JSONObject item = todos.get(indice);
        int viajeId     = item.optInt("viajeId", 0);
        int idConductor = item.optInt("idConductor", -1);
        new CalificacionesManager(this).verificarCalificacion(
                viajeId, idPasajero, idConductor,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override public void onDebeCalificar() {
                        sinCalificar.add(item);
                        verificarYMostrarCards(todos, idPasajero, sinCalificar, indice + 1);
                    }
                    @Override public void onYaCalifico(int p, String e) {
                        verificarYMostrarCards(todos, idPasajero, sinCalificar, indice + 1);
                    }
                });
    }

    private void mostrarCardsPorCalificar(List<JSONObject> items, int idPasajero) {
        if (layoutCalificaciones == null || sectionCalificar == null) return;
        layoutCalificaciones.removeAllViews();
        if (items.isEmpty()) {
            sectionCalificar.setVisibility(View.GONE);
            if (dividerCalificar != null) dividerCalificar.setVisibility(View.GONE);
            return;
        }
        sectionCalificar.setVisibility(View.VISIBLE);
        if (dividerCalificar != null) dividerCalificar.setVisibility(View.VISIBLE);

        float d = getResources().getDisplayMetrics().density;

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpH.bottomMargin = (int)(10*d);
        header.setLayoutParams(lpH);

        View barra = new View(this);
        LinearLayout.LayoutParams lpBarra = new LinearLayout.LayoutParams((int)(4*d),(int)(22*d));
        lpBarra.rightMargin = (int)(10*d);
        barra.setLayoutParams(lpBarra);
        GradientDrawable bgBarra = new GradientDrawable();
        bgBarra.setShape(GradientDrawable.RECTANGLE);
        bgBarra.setCornerRadius(4*d);
        bgBarra.setColor(Color.parseColor("#FF9800")); // naranja — mantener para calificaciones
        barra.setBackground(bgBarra);
        header.addView(barra);

        TextView tvTit = new TextView(this);
        tvTit.setText("Por calificar");
        tvTit.setTextSize(17f);
        tvTit.setTypeface(null, Typeface.BOLD);
        tvTit.setTextColor(Color.parseColor("#1A2F4A")); // era #004D40
        tvTit.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(tvTit);

        TextView tvBadge = new TextView(this);
        tvBadge.setText(items.size() + (items.size() == 1 ? " viaje" : " viajes"));
        tvBadge.setTextSize(11f);
        tvBadge.setTypeface(null, Typeface.BOLD);
        tvBadge.setTextColor(Color.WHITE);
        tvBadge.setPadding((int)(10*d),(int)(4*d),(int)(10*d),(int)(4*d));
        GradientDrawable bgBadge = new GradientDrawable();
        bgBadge.setShape(GradientDrawable.RECTANGLE);
        bgBadge.setCornerRadius(20*d);
        bgBadge.setColor(Color.parseColor("#FF9800")); // naranja — mantener
        tvBadge.setBackground(bgBadge);
        header.addView(tvBadge);
        layoutCalificaciones.addView(header);

        for (JSONObject item : items) {
            int    viajeId     = item.optInt("viajeId", 0);
            int    idConductor = item.optInt("idConductor", -1);
            String nomCond     = item.optString("nomConductor", "el conductor");
            String fecha       = item.optString("fecha", "");
            String destino     = item.optString("destino", "");
            String fechaFmt    = "";
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
                java.text.SimpleDateFormat out = new java.text.SimpleDateFormat(
                        "dd/MM/yyyy  HH:mm", java.util.Locale.getDefault());
                fechaFmt = out.format(sdf.parse(fecha.replaceAll("\\.\\d{3}Z?$", "")));
            } catch (Exception ignored) {}

            // Card calificar
            GradientDrawable bgCard = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#FFF8F0"), Color.WHITE});
            bgCard.setCornerRadius(16*d);
            bgCard.setStroke((int)(1.5f*d), Color.parseColor("#FFE0B2"));
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(bgCard);
            card.setPadding((int)(14*d),(int)(14*d),(int)(14*d),(int)(14*d));
            card.setElevation(5*d);
            LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpCard.bottomMargin = (int)(10*d);
            card.setLayoutParams(lpCard);

            // Avatar naranja
            TextView tvAv = new TextView(this);
            String ini = (!nomCond.isEmpty()) ? nomCond.substring(0,1).toUpperCase() : "C";
            tvAv.setText(ini);
            tvAv.setTextSize(18f);
            tvAv.setTextColor(Color.WHITE);
            tvAv.setTypeface(null, Typeface.BOLD);
            tvAv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpAv = new LinearLayout.LayoutParams((int)(46*d),(int)(46*d));
            lpAv.rightMargin = (int)(12*d);
            tvAv.setLayoutParams(lpAv);
            GradientDrawable bgAv = new GradientDrawable();
            bgAv.setShape(GradientDrawable.OVAL);
            bgAv.setColors(new int[]{Color.parseColor("#FFA726"), Color.parseColor("#FF6F00")}); // mantener naranja
            bgAv.setOrientation(GradientDrawable.Orientation.TL_BR);
            tvAv.setBackground(bgAv);
            card.addView(tvAv);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvNom = new TextView(this);
            tvNom.setText(nomCond);
            tvNom.setTextSize(14f);
            tvNom.setTypeface(null, Typeface.BOLD);
            tvNom.setTextColor(Color.parseColor("#1A2F4A")); // era #1A2035
            col.addView(tvNom);

            if (!destino.isEmpty()) {
                TextView tvDest = new TextView(this);
                tvDest.setText(destino);
                tvDest.setTextSize(12f);
                tvDest.setTextColor(Color.parseColor("#7A9BAA")); // era #546E7A
                LinearLayout.LayoutParams lpD = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lpD.topMargin = (int)(3*d);
                tvDest.setLayoutParams(lpD);
                col.addView(tvDest);
            }
            if (!fechaFmt.isEmpty()) {
                TextView tvFecha = new TextView(this);
                tvFecha.setText(fechaFmt);
                tvFecha.setTextSize(11f);
                tvFecha.setTextColor(Color.parseColor("#7A9BAA")); // era #90A4AE
                LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lpF.topMargin = (int)(2*d);
                tvFecha.setLayoutParams(lpF);
                col.addView(tvFecha);
            }
            card.addView(col);

            // Botón calificar
            MaterialButton btnCal = new MaterialButton(this);
            btnCal.setText("Calificar");
            btnCal.setTextSize(11f);
            btnCal.setTextColor(Color.WHITE);
            btnCal.setCornerRadius((int)(14*d));
            btnCal.setBackgroundColor(Color.parseColor("#FF9800")); // mantener naranja
            btnCal.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams((int)(78*d),(int)(52*d));
            lpBtn.leftMargin = (int)(8*d);
            btnCal.setLayoutParams(lpBtn);
            btnCal.setPadding(0,0,0,0);

            final int    fViajeId     = viajeId;
            final int    fIdConductor = idConductor;
            final String fNomCond     = nomCond;
            final View   fCard        = card;
            final TextView fBadge     = tvBadge;

            btnCal.setOnClickListener(v ->
                    CalificacionController.mostrarBottomSheetCalificar(
                            this, fViajeId, fIdConductor, fNomCond,
                            "",              // ← fotoCalificado
                            idPasajero, false,
                            (puntuacion, comentario) -> {
                                if (fCard.getParent() != null)
                                    ((ViewGroup) fCard.getParent()).removeView(fCard);
                                int restantes = layoutCalificaciones.getChildCount() - 1;
                                if (restantes <= 0) {
                                    sectionCalificar.setVisibility(View.GONE);
                                    if (dividerCalificar != null) dividerCalificar.setVisibility(View.GONE);
                                } else {
                                    fBadge.setText(restantes + (restantes == 1 ? " viaje" : " viajes"));
                                }
                            }
                    )
            );
            card.addView(btnCal);
            layoutCalificaciones.addView(card);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CALIFICACIONES PENDIENTES
    // ═══════════════════════════════════════════════════════════════════════════

    private void verificarCalificacionesPendientes() {
        if (isFinishing() || isDestroyed()) return;
        int idPasajero = session.getIdUsuario();
        if (idPasajero <= 0) return;

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_RESERVAS,
                response -> {
                    List<int[]>  viajeIds   = new ArrayList<>();
                    List<String> nomConduct = new ArrayList<>();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject reserva = response.optJSONObject(i);
                        if (reserva == null) continue;
                        String estReserva = reserva.optString("estado", "").toUpperCase();
                        String estViaje   = "";
                        JSONObject viaje  = reserva.optJSONObject("viaje");
                        if (viaje != null) estViaje = viaje.optString("estado", "").toUpperCase();
                        boolean finalizado = "FINALIZADO".equals(estViaje) || "COMPLETADO".equals(estViaje)
                                || "COMPLETADO".equals(estReserva) || "COMPLETADA".equals(estReserva);
                        if (!finalizado) continue;
                        int viajeId = reserva.optInt("idViaje", 0);
                        if (viajeId == 0 && viaje != null)
                            viajeId = viaje.optInt("idViaje", viaje.optInt("id", 0));
                        if (viajeId == 0) continue;
                        int    idCond  = -1;
                        String nomCond = "";
                        if (viaje != null) {
                            idCond = viaje.optInt("idConductor", viaje.optInt("conductorId", -1));
                            JSONObject co = viaje.optJSONObject("conductor");
                            if (co != null) {
                                if (idCond <= 0)
                                    for (String k : new String[]{"id","idUsuarios","idUsuario"}) {
                                        int id2 = co.optInt(k,-1);
                                        if (id2>0){idCond=id2;break;}
                                    }
                                nomCond = extraerNombreConductor(co);
                            }
                        }
                        if (idCond <= 0) continue;
                        viajeIds.add(new int[]{viajeId, idCond});
                        nomConduct.add(nomCond.isEmpty() ? "el conductor" : nomCond);
                    }
                    if (!viajeIds.isEmpty())
                        new Handler(Looper.getMainLooper()).post(() ->
                                verificarYMostrarSiguiente(viajeIds, nomConduct, idPasajero, 0));
                },
                error -> Log.w(TAG, "verificarCalificacionesPendientes: error")
        );
    }

    private void verificarYMostrarSiguiente(List<int[]> viajeIds, List<String> nombres,
                                            int idPasajero, int indice) {
        if (indice >= viajeIds.size() || isFinishing() || isDestroyed()) return;
        int    viajeId     = viajeIds.get(indice)[0];
        int    idConductor = viajeIds.get(indice)[1];
        String nomCond     = nombres.get(indice);
        int    siguiente   = indice + 1;
        new CalificacionesManager(this).verificarCalificacion(
                viajeId, idPasajero, idConductor,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override public void onDebeCalificar() {
                        runOnUiThread(() ->
                                CalificacionController.mostrarBottomSheetCalificar(
                                        HomePasajero.this, viajeId, idConductor, nomCond,
                                        "",              // ← fotoCalificado
                                        idPasajero, false,
                                        (puntuacion, comentario) -> {
                                            cargarViajesPorCalificar();
                                            new Handler(Looper.getMainLooper()).postDelayed(
                                                    () -> verificarYMostrarSiguiente(
                                                            viajeIds, nombres, idPasajero, siguiente),
                                                    800);
                                        }));
                    }
                    @Override public void onYaCalifico(int p, String e) {
                        verificarYMostrarSiguiente(viajeIds, nombres, idPasajero, siguiente);
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  RUTAS FRECUENTES
    // ═══════════════════════════════════════════════════════════════════════════

    private void cargarRutasFrecuentesPasajero() {
        if (pbRutas == null || layoutRutas == null) return;
        pbRutas.setVisibility(View.VISIBLE);
        layoutRutas.removeAllViews();

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_RESERVAS,
                response -> {
                    Map<String, Integer> frecuencia = new HashMap<>();
                    Map<String, String>  datos      = new HashMap<>();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject r = response.optJSONObject(i);
                        if (r == null) continue;
                        String estadoReserva = r.optString("estado", "").toUpperCase();
                        String estadoViaje   = "";
                        JSONObject viaje     = r.optJSONObject("viaje");
                        if (viaje != null) estadoViaje = viaje.optString("estado", "").toUpperCase();
                        boolean terminado = "FINALIZADO".equals(estadoViaje) || "COMPLETADO".equals(estadoViaje)
                                || "COMPLETADO".equals(estadoReserva) || "COMPLETADA".equals(estadoReserva);
                        if (!terminado) continue;
                        String destino = r.optString("nombreParada", "").trim();
                        String origen  = "";
                        if (viaje != null) {
                            JSONObject ruta = viaje.optJSONObject("ruta");
                            if (ruta != null) {
                                if (destino.isEmpty())
                                    destino = ruta.optString("destino", ruta.optString("nombre", "")).trim();
                                origen = ruta.optString("origen", "").trim();
                            }
                        }
                        if (destino.isEmpty()) continue;
                        String key = destino.toLowerCase();
                        frecuencia.put(key, frecuencia.getOrDefault(key, 0) + 1);
                        if (!datos.containsKey(key)) datos.put(key, destino + "|" + origen);
                    }
                    List<Map.Entry<String, Integer>> lista = new ArrayList<>(frecuencia.entrySet());
                    lista.sort((a, b) -> b.getValue() - a.getValue());
                    List<RutaFrecuenteItem> items = new ArrayList<>();
                    for (int i = 0; i < Math.min(6, lista.size()); i++) {
                        String key  = lista.get(i).getKey();
                        int veces   = lista.get(i).getValue();
                        String raw  = datos.get(key);
                        String dest = raw != null && raw.contains("|") ? raw.split("\\|")[0] : (raw != null ? raw : key);
                        String orig = raw != null && raw.contains("|") ? raw.split("\\|")[1] : "";
                        items.add(new RutaFrecuenteItem(dest, orig, veces));
                    }
                    runOnUiThread(() -> {
                        pbRutas.setVisibility(View.GONE);
                        if (items.isEmpty()) cargarRutasSistemaFallback();
                        else                 mostrarRutasFrecuentesPersonalizadas(items);
                    });
                },
                error -> runOnUiThread(() -> {
                    pbRutas.setVisibility(View.GONE);
                    cargarRutasSistemaFallback();
                })
        );
    }

    private void cargarRutasSistemaFallback() {
        if (layoutRutas == null) return;
        ConexionApi.getInstance(this).getArray(
                Constantes.RUTAS,
                response -> runOnUiThread(() -> {
                    // Colores de la paleta app — variantes de teal
                    int[] colores = {0xFF00CED1, 0xFF00868A, 0xFF0097A7, 0xFF006064, 0xFF26C6DA, 0xFF4DB6AC};
                    String[] labels = {"Ruta A","Ruta B","Ruta C","Ruta D","Ruta E","Ruta F"};
                    int total = Math.min(response.length(), 6);
                    for (int i = 0; i < total; i++) {
                        JSONObject ruta = response.optJSONObject(i);
                        if (ruta != null)
                            agregarCardRutaSistema(ruta, colores[i % colores.length]);
                    }
                    if (total == 0) {
                        TextView empty = new TextView(this);
                        empty.setText("No hay rutas registradas aún");
                        empty.setTextColor(0xFF7A9BAA); // era #90A4AE
                        empty.setTextSize(13f);
                        layoutRutas.addView(empty);
                    }
                }),
                error -> Log.e(TAG, "Error cargando rutas sistema")
        );
    }

    private void mostrarRutasFrecuentesPersonalizadas(List<RutaFrecuenteItem> items) {
        if (layoutRutas == null) return;
        layoutRutas.removeAllViews();
        // Colores de la paleta app — variantes de teal
        int[] colores = {0xFF00CED1, 0xFF00868A, 0xFF0097A7, 0xFF006064, 0xFF26C6DA, 0xFF4DB6AC};
        for (int i = 0; i < items.size(); i++) {
            RutaFrecuenteItem item  = items.get(i);
            int               color = colores[i % colores.length];
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(dp(160), ViewGroup.LayoutParams.WRAP_CONTENT);
            lpCard.rightMargin = dp(12);
            card.setLayoutParams(lpCard);
            card.setRadius(dp(18));
            card.setCardElevation(dp(4));
            card.setCardBackgroundColor(Color.WHITE);
            card.setClickable(true);
            card.setFocusable(true);
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout cabecera = new LinearLayout(this);
            cabecera.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(85)));
            cabecera.setBackgroundColor(color);
            cabecera.setGravity(Gravity.CENTER);
            cabecera.setOrientation(LinearLayout.VERTICAL);
            int logoResId = getResources().getIdentifier("logomo", "drawable", getPackageName());
            if (logoResId != 0) {
                android.widget.ImageView iv = new android.widget.ImageView(this);
                iv.setImageResource(logoResId);
                iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                iv.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
                cabecera.addView(iv);
            } else {
                TextView tvM = new TextView(this);
                tvM.setText("M");
                tvM.setTextSize(26f);
                tvM.setTypeface(null, Typeface.BOLD);
                tvM.setTextColor(Color.WHITE);
                tvM.setGravity(Gravity.CENTER);
                tvM.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
                GradientDrawable cir = new GradientDrawable();
                cir.setShape(GradientDrawable.OVAL);
                cir.setColor(Color.argb(60,255,255,255));
                tvM.setBackground(cir);
                cabecera.addView(tvM);
            }
            if (item.vecesUsada > 1) {
                TextView tvBd = new TextView(this);
                tvBd.setText(item.vecesUsada + " viajes");
                tvBd.setTextSize(10f);
                tvBd.setTypeface(null, Typeface.BOLD);
                tvBd.setTextColor(Color.WHITE);
                tvBd.setPadding(dp(8),dp(2),dp(8),dp(2));
                GradientDrawable bgBd = new GradientDrawable();
                bgBd.setShape(GradientDrawable.RECTANGLE);
                bgBd.setCornerRadius(dp(20));
                bgBd.setColor(Color.argb(60,0,0,0));
                tvBd.setBackground(bgBd);
                LinearLayout.LayoutParams lpBd = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpBd.topMargin = dp(4);
                tvBd.setLayoutParams(lpBd);
                cabecera.addView(tvBd);
            }
            LinearLayout cuerpo = new LinearLayout(this);
            cuerpo.setOrientation(LinearLayout.VERTICAL);
            cuerpo.setPadding(dp(12),dp(10),dp(12),dp(12));
            if (!item.origen.isEmpty()) {
                TextView tvOr = new TextView(this);
                tvOr.setText(truncar(item.origen, 16));
                tvOr.setTextColor(0xFF7A9BAA); // era #78909C
                tvOr.setTextSize(10f);
                cuerpo.addView(tvOr);
                TextView tvAr = new TextView(this);
                tvAr.setText("↓");
                tvAr.setTextColor(0xFFA0BEC0); // era #B0BEC5
                tvAr.setTextSize(10f);
                cuerpo.addView(tvAr);
            }
            TextView tvDest = new TextView(this);
            tvDest.setText(truncar(item.destino, 18));
            tvDest.setTextColor(0xFF1A2F4A); // era #004D40
            tvDest.setTextSize(13f);
            tvDest.setTypeface(null, Typeface.BOLD);
            tvDest.setMaxLines(2);
            LinearLayout.LayoutParams lpDest = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpDest.topMargin = dp(2);
            tvDest.setLayoutParams(lpDest);
            cuerpo.addView(tvDest);
            TextView tvTag = new TextView(this);
            tvTag.setText("Tu ruta");
            tvTag.setTextSize(10f);
            tvTag.setTypeface(null, Typeface.BOLD);
            tvTag.setTextColor(Color.WHITE);
            tvTag.setPadding(dp(6),dp(2),dp(6),dp(2));
            GradientDrawable bgTag = new GradientDrawable();
            bgTag.setShape(GradientDrawable.RECTANGLE);
            bgTag.setCornerRadius(dp(20));
            bgTag.setColor(color);
            tvTag.setBackground(bgTag);
            LinearLayout.LayoutParams lpTag = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpTag.topMargin = dp(6);
            tvTag.setLayoutParams(lpTag);
            cuerpo.addView(tvTag);
            inner.addView(cabecera);
            inner.addView(cuerpo);
            card.addView(inner);
            final String destFinal = item.destino;
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, MisReservasActivity.class);
                intent.putExtra("DESTINO_PRELLENADO", destFinal);
                startActivity(intent);
            });
            layoutRutas.addView(card);
        }
    }

    private void agregarCardRutaSistema(JSONObject ruta, int color) {
        String origen  = ruta.optString("origen",  "Origen");
        String destino = ruta.optString("destino", "");
        if (destino.isEmpty()) destino = ruta.optString("nombre", ruta.optString("descripcion", "Destino"));
        int    idRuta = ruta.optInt("idRuta", ruta.optInt("id", 0));
        double dist   = ruta.optDouble("distancia", 0);
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(dp(156), ViewGroup.LayoutParams.WRAP_CONTENT);
        lpCard.rightMargin = dp(12);
        card.setLayoutParams(lpCard);
        card.setRadius(dp(18));
        card.setCardElevation(dp(4));
        card.setCardBackgroundColor(Color.WHITE);
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        LinearLayout cab = new LinearLayout(this);
        cab.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90)));
        cab.setBackgroundColor(color);
        cab.setGravity(Gravity.CENTER);
        // Ícono brújula en lugar de emoji
        android.widget.ImageView ivIcon = new android.widget.ImageView(this);
        ivIcon.setImageResource(R.drawable.ic_menu_compass);
        ivIcon.setColorFilter(Color.WHITE);
        ivIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        ivIcon.setPadding(dp(4), dp(4), dp(4), dp(4));
        cab.addView(ivIcon);

        LinearLayout cuerpo = new LinearLayout(this);
        cuerpo.setOrientation(LinearLayout.VERTICAL);
        cuerpo.setPadding(dp(12),dp(10),dp(12),dp(12));
        TextView tvOr = new TextView(this);
        tvOr.setText(truncar(origen, 15));
        tvOr.setTextColor(0xFF7A9BAA); // era #78909C
        tvOr.setTextSize(10f);
        cuerpo.addView(tvOr);
        TextView tvAr = new TextView(this);
        tvAr.setText("↓");
        tvAr.setTextColor(0xFFA0BEC0);
        tvAr.setTextSize(10f);
        cuerpo.addView(tvAr);
        TextView tvDest = new TextView(this);
        tvDest.setText(truncar(destino, 15));
        tvDest.setTextColor(0xFF1A2F4A); // era #004D40
        tvDest.setTextSize(13f);
        tvDest.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lpD = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpD.topMargin = dp(2);
        tvDest.setLayoutParams(lpD);
        cuerpo.addView(tvDest);
        if (dist > 0) {
            TextView tvDist = new TextView(this);
            tvDist.setText(String.format("%.1f km", dist));
            tvDist.setTextColor(0xFF00868A); // era #00897B
            tvDist.setTextSize(11f);
            tvDist.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams lpDist = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpDist.topMargin = dp(4);
            tvDist.setLayoutParams(lpDist);
            cuerpo.addView(tvDist);
        }
        inner.addView(cab);
        inner.addView(cuerpo);
        card.addView(inner);
        final String destFinal = destino;
        final int    idRutaF   = idRuta;
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, RutasFrecuentes.class);
            intent.putExtra("destino", destFinal);
            intent.putExtra("idRuta",  idRutaF);
            startActivity(intent);
        });
        layoutRutas.addView(card);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CARRUSEL
    // ═══════════════════════════════════════════════════════════════════════════

    private void iniciarCarruselMoviflexInfo() {
        if (rvCarruselInfo == null) return;
        List<CarruselSlide> slides = crearSlidesMoviflex();
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvCarruselInfo.setLayoutManager(lm);
        carruselAdapter = new CarruselInfoAdapter(slides);
        rvCarruselInfo.setAdapter(carruselAdapter);
        PagerSnapHelper snap = new PagerSnapHelper();
        snap.attachToRecyclerView(rvCarruselInfo);
        crearDots(slides.size());
        rvCarruselInfo.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    View v = snap.findSnapView(lm);
                    if (v != null) {
                        int pos = lm.getPosition(v);
                        carruselPos = pos;
                        actualizarDots(pos, slides.size());
                    }
                }
            }
        });
    }

    private List<CarruselSlide> crearSlidesMoviflex() {
        List<CarruselSlide> s = new ArrayList<>();
        s.add(new CarruselSlide("Viaja más seguro","Conductores verificados con documento y vehículo registrado en MoviFlex.","#00CED1","#00868A","Conductores verificados"));
        s.add(new CarruselSlide("Ahorra en cada viaje","Comparte el trayecto y reduce el costo hasta un 60% vs. taxi tradicional.","#0288D1","#01579B","Hasta 60% más económico"));
        s.add(new CarruselSlide("Elige dónde bajarte","Selecciona tu parada exacta dentro de la ruta. El conductor irá hasta allí.","#7B1FA2","#4A148C","Parada personalizada"));
        s.add(new CarruselSlide("Chat en tiempo real","Comunícate con tu conductor directamente desde la app en cualquier momento.","#F57F17","#E65100","Chat instantáneo"));
        s.add(new CarruselSlide("Movilidad sostenible","Al compartir vehículo reduces tu huella de carbono. MoviFlex cuida el planeta.","#388E3C","#1B5E20","+500 kg CO2 ahorrado/mes"));
        s.add(new CarruselSlide("Califica tu experiencia","Tu opinión mejora el servicio. Califica al conductor al finalizar el viaje.","#C62828","#B71C1C","Comunidad de confianza"));
        return s;
    }

    private void crearDots(int count) {
        if (layoutDotsInfo == null) return;
        layoutDotsInfo.removeAllViews();
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(8));
            lp.setMargins(dp(4),0,dp(4),0);
            dot.setLayoutParams(lp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(i == 0 ? Color.parseColor("#00CED1") : Color.parseColor("#A0E0DC")); // era #009B8D/#B2DFDB
            dot.setBackground(gd);
            layoutDotsInfo.addView(dot);
        }
    }

    private void actualizarDots(int activo, int count) {
        if (layoutDotsInfo == null) return;
        for (int i = 0; i < layoutDotsInfo.getChildCount() && i < count; i++) {
            View dot = layoutDotsInfo.getChildAt(i);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            if (i == activo) {
                gd.setColor(Color.parseColor("#00CED1")); // era #009B8D
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(14), dp(8));
                lp.setMargins(dp(4),0,dp(4),0);
                dot.setLayoutParams(lp);
            } else {
                gd.setColor(Color.parseColor("#A0E0DC")); // era #B2DFDB
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(8));
                lp.setMargins(dp(4),0,dp(4),0);
                dot.setLayoutParams(lp);
            }
            dot.setBackground(gd);
        }
    }

    private void iniciarAutoScrollCarrusel() {
        if (rvCarruselInfo == null || carruselAdapter == null) return;
        detenerAutoScrollCarrusel();
        carruselRunnable = new Runnable() {
            @Override public void run() {
                if (carruselAdapter == null) return;
                int total = carruselAdapter.getItemCount();
                if (total == 0) return;
                carruselPos = (carruselPos + 1) % total;
                rvCarruselInfo.smoothScrollToPosition(carruselPos);
                actualizarDots(carruselPos, total);
                carruselHandler.postDelayed(this, 4000);
            }
        };
        carruselHandler.postDelayed(carruselRunnable, 4000);
    }

    private void detenerAutoScrollCarrusel() {
        if (carruselRunnable != null) carruselHandler.removeCallbacks(carruselRunnable);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    private String truncar(String s, int max) {
        if (s == null || s.isEmpty()) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static String extraerNombreConductor(JSONObject cond) {
        if (cond == null) return "Conductor";
        for (String c : new String[]{"nombre","nombres","nombreCompleto","name",
                "fullName","displayName","nombreUsuario"}) {
            String val = cond.optString(c, "");
            if (!val.isEmpty() && !val.equals("null")) return val;
        }
        String n = cond.optString("nombres",""), a = cond.optString("apellidos","");
        if (!n.isEmpty() || !a.isEmpty()) return (n + " " + a).trim();
        return "Conductor";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MODELOS
    // ═══════════════════════════════════════════════════════════════════════════

    private static class RutaFrecuenteItem {
        String destino, origen;
        int    vecesUsada;
        RutaFrecuenteItem(String destino, String origen, int vecesUsada) {
            this.destino = destino; this.origen = origen; this.vecesUsada = vecesUsada;
        }
    }

    private static class CarruselSlide {
        String titulo, descripcion, colorInicio, colorFin, badge;
        CarruselSlide(String t, String d, String c1, String c2, String b) {
            titulo=t; descripcion=d; colorInicio=c1; colorFin=c2; badge=b;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ADAPTER CARRUSEL
    // ═══════════════════════════════════════════════════════════════════════════

    static class CarruselInfoAdapter extends RecyclerView.Adapter<CarruselInfoAdapter.VH> {
        private final List<CarruselSlide> slides;
        CarruselInfoAdapter(List<CarruselSlide> slides) { this.slides = slides; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.content.Context ctx = parent.getContext();
            float d = ctx.getResources().getDisplayMetrics().density;
            int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    screenW - (int)(32*d), (int)(150*d));
            lp.setMargins((int)(4*d), 0, (int)(4*d), 0);
            root.setLayoutParams(lp);
            root.setPadding((int)(20*d),(int)(16*d),(int)(20*d),(int)(16*d));
            root.setGravity(Gravity.CENTER_VERTICAL);
            TextView tvBadge = new TextView(ctx);
            tvBadge.setTag("badge");
            tvBadge.setTextSize(11f);
            tvBadge.setTypeface(null, Typeface.BOLD);
            tvBadge.setTextColor(Color.WHITE);
            tvBadge.setPadding((int)(10*d),(int)(3*d),(int)(10*d),(int)(3*d));
            GradientDrawable bgB = new GradientDrawable();
            bgB.setShape(GradientDrawable.RECTANGLE);
            bgB.setCornerRadius(20*d);
            bgB.setColor(Color.argb(60,255,255,255));
            tvBadge.setBackground(bgB);
            LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpBadge.bottomMargin = (int)(8*d);
            tvBadge.setLayoutParams(lpBadge);
            root.addView(tvBadge);
            TextView tvTit = new TextView(ctx);
            tvTit.setTag("titulo");
            tvTit.setTextSize(17f);
            tvTit.setTypeface(null, Typeface.BOLD);
            tvTit.setTextColor(Color.WHITE);
            tvTit.setMaxLines(1);
            root.addView(tvTit);
            TextView tvDesc = new TextView(ctx);
            tvDesc.setTag("desc");
            tvDesc.setTextSize(13f);
            tvDesc.setTextColor(Color.argb(220,255,255,255));
            tvDesc.setMaxLines(3);
            LinearLayout.LayoutParams lpDesc = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpDesc.topMargin = (int)(8*d);
            tvDesc.setLayoutParams(lpDesc);
            root.addView(tvDesc);
            return new VH(root);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CarruselSlide slide = slides.get(position);
            LinearLayout  root  = (LinearLayout) holder.itemView;
            float d = root.getContext().getResources().getDisplayMetrics().density;
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.parseColor(slide.colorInicio), Color.parseColor(slide.colorFin)});
            bg.setCornerRadius(20*d);
            root.setBackground(bg);
            ((TextView) root.findViewWithTag("badge")).setText(slide.badge);
            ((TextView) root.findViewWithTag("titulo")).setText(slide.titulo);
            ((TextView) root.findViewWithTag("desc")).setText(slide.descripcion);
        }

        @Override public int getItemCount() { return slides.size(); }
        static class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View v) { super(v); }
        }
    }

    // ─── VOICE ASSISTANT BRIDGE ──────────────────────────────────────────────
    
    /**
     * Muestra los viajes encontrados por voz en un BottomSheet.
     */
    public void abrirResultadosViajesPorVoz(org.json.JSONArray viajes) {
       runOnUiThread(() -> {
           BottomSheetDialog dialog = new BottomSheetDialog(this);
           float d = getResources().getDisplayMetrics().density;
           
           LinearLayout root = new LinearLayout(this);
           root.setOrientation(LinearLayout.VERTICAL);
           root.setBackgroundColor(Color.parseColor("#F0FAFA"));
           root.setPadding((int)(16*d), (int)(16*d), (int)(16*d), (int)(24*d));

           TextView tvTitulo = new TextView(this);
           tvTitulo.setText("Viajes Encontrados");
           tvTitulo.setTextSize(18f); tvTitulo.setTypeface(null, Typeface.BOLD);
           tvTitulo.setTextColor(Color.parseColor("#1A2F4A"));
           root.addView(tvTitulo);

           LinearLayout lista = new LinearLayout(this);
           lista.setOrientation(LinearLayout.VERTICAL);
           lista.setPadding(0, (int)(12*d), 0, 0);
           
           for (int i = 0; i < viajes.length(); i++) {
               org.json.JSONObject v = viajes.optJSONObject(i);
               if (v == null) continue;
               
               int finalI = i;
               MaterialCardView card = new MaterialCardView(this);
               card.setRadius(12*d); card.setCardElevation(2*d);
               LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
               lp.topMargin = (int)(8*d); card.setLayoutParams(lp);
               
               LinearLayout inner = new LinearLayout(this);
               inner.setOrientation(LinearLayout.VERTICAL);
               inner.setPadding((int)(12*d), (int)(12*d), (int)(12*d), (int)(12*d));
               
               String origen = v.optString("origen", "Origen");
               String destino = v.optString("destino", "Destino");
               
               TextView tvRuta = new TextView(this);
               tvRuta.setText((i+1) + ". " + origen + " → " + destino);
               tvRuta.setTypeface(null, Typeface.BOLD);
               inner.addView(tvRuta);
               
               card.addView(inner);
               card.setOnClickListener(view -> {
                   dialog.dismiss();
                   irADetalleViaje(v);
               });
               lista.addView(card);
           }
           
           ScrollView scroll = new ScrollView(this);
           scroll.addView(lista);
           root.addView(scroll);
           
           dialog.setContentView(root);
           dialog.show();
           
           if (voiceAssistant != null) {
               voiceAssistant.hablar("He encontrado " + viajes.length() + " viajes. Dime el número del viaje que te interesa.");
           }
       });
    }

    private void irADetalleViaje(org.json.JSONObject v) {
        int idViaje = v.optInt("idViajes", v.optInt("id", 0));
        if (idViaje > 0) {
            Intent intent = new Intent(this, DetalleViajeActivity.class);
            intent.putExtra("ID_VIAJE", idViaje);
            startActivity(intent);
        }
    }

    // ─── SCREEN DESCRIPTOR ────────────────────────────────────────────────────
    @Override public String getNombrePantalla() { return "Inicio del Pasajero"; }
    @Override public String getDescripcionPantalla() {
        return "Estás en la pantalla principal de pasajero. "
             + "Puedes ver tu información, rutas frecuentes, y opciones de navegación. "
             + "Abajo hay un menú con: Inicio, Mis Viajes, Mapa, Mensajes y Perfil.";
    }
    @Override public String getOpcionesPantalla() {
        return "Puedes decir: buscar viaje, quiero ir al centro, mis reservas, "
             + "perfil, mensajes, mapa, o ayuda.";
    }
}