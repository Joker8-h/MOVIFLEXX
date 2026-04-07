package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.adapter.CalificacionesPendientesAdapter;
import com.arlys.moviflexx.adapter.ViajesAdapter;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Manager.CalificacionesManager;
import com.arlys.moviflexx.model.NotificacionesHelper;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeConductor extends BaseActivity {

    private static final String TAG = "HomeConductor";

    // ── Viajes activos ────────────────────────────────────────────────────────
    private RecyclerView  rvViajes;
    private ViajesAdapter adapter;
    private ProgressBar   progress;
    private LinearLayout  layoutEmpty;
    private Chip          chipTotal;

    // ── Header ────────────────────────────────────────────────────────────────
    private TextView txtBienvenida;
    private TextView txtNombreConductor;

    // ── Acciones rápidas ──────────────────────────────────────────────────────
    private android.widget.FrameLayout btnPublicarViaje;
    private MaterialCardView btnMisRutas;
    private MaterialCardView btnMisVehiculos;

    // ── Calificaciones pendientes ─────────────────────────────────────────────
    private LinearLayout                    layoutCalificacionesPendientes;
    private View                            dividerCalificaciones;
    private Chip                            chipCalificacionesPendientes;
    private RecyclerView                    rvCalificacionesPendientes;
    private CalificacionesPendientesAdapter adapterCalificaciones;

    private int viajeActivoId = -1;
    private final List<PasajeroPendiente> listaPendientes = new ArrayList<>();

    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;

    // ── Sesión / datos ────────────────────────────────────────────────────────
    private final List<JSONObject> viajes = new ArrayList<>();
    private SessionManager session;
    private int conductorId = -1;

    private boolean calificacionPendienteVerificada = false;

    // =========================================================================
    //  MODELO INTERNO
    // =========================================================================
    public static class PasajeroPendiente {
        public final int    viajeId;
        public final int    pasajeroId;
        public final String nombrePasajero;

        public PasajeroPendiente(int viajeId, int pasajeroId, String nombrePasajero) {
            this.viajeId        = viajeId;
            this.pasajeroId     = pasajeroId;
            this.nombrePasajero = nombrePasajero;
        }
    }

    // =========================================================================
    //  LIFECYCLE
    // =========================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(
                android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_home_conductor);

        session = new SessionManager(this);

        if (!session.isLoggedIn()) { irAlLogin(); return; }
        session.loadSessionToMemory();

        if (!session.isConductor()) {
            Toast.makeText(this, "Esta pantalla es solo para conductores",
                    Toast.LENGTH_SHORT).show();
            goTo(HomePasajero.class, Transition.FADE, true);
            return;
        }

        conductorId = session.getIdUsuario();


        // Arrancar servicio de notificaciones en tiempo real
        Intent servicioNotif = new Intent(this, NotificacionPollingService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(servicioNotif);
        } else {
            startService(servicioNotif);
        }
        enlazarVistas();
        configurarSaludo();
        configurarRecyclers();
        configurarBotones();
        configurarBottomNav();
        animarEntrada();
    }



    @Override
    protected void onResume() {
        super.onResume();
        resetCalificacionesPendientes();
        cargarMisViajes();
        NotificacionesHelper.configurar(this);

        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav != null) nav.setSelectedItemId(R.id.nav_inicio);

        ConexionApi.getInstance(this).getArrayNoCache(
                Constantes.MIS_VIAJES,
                response -> {
                    boolean tieneViajeActivo = false;
                    if (response != null) {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject v = response.optJSONObject(i);
                            if (v == null) continue;
                            String est = v.optString("estado", "").toUpperCase().trim();
                            if ("INICIADO".equals(est) || "EN_CURSO".equals(est) || "ACTIVO".equals(est)) {
                                tieneViajeActivo = true;
                                break;
                            }
                        }
                    }
                    final boolean fActivo = tieneViajeActivo;
                    runOnUiThread(() -> {
                        BottomNavigationView n = findViewById(R.id.bottom_navigation);
                        if (n != null) n.getMenu().findItem(R.id.nav_mapa).setEnabled(fActivo);
                    });
                },
                err -> {}
        );

        if (!calificacionPendienteVerificada) {
            calificacionPendienteVerificada = true;
            new Handler(Looper.getMainLooper()).postDelayed(
                    this::verificarCalificacionesPendientes, 1500);

            if (viajeActivoId > 0 && adapter != null) {
                adapter.iniciarPollingPagosConductor(viajeActivoId);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        calificacionPendienteVerificada = false;
        if (adapter != null) adapter.detenerPollingPagosConductor();
    }

    @Override
    public void onBackPressed() {
        goTo(HomeConductor.class, Transition.NONE);
        finish();
    }

    // =========================================================================
    //  ENLAZAR VISTAS
    // =========================================================================
    private void enlazarVistas() {
        rvViajes                       = findViewById(R.id.rv_viajes_activos);
        progress                       = findViewById(R.id.progress);
        layoutEmpty                    = findViewById(R.id.layout_empty);
        chipTotal                      = findViewById(R.id.chip_total);
        txtBienvenida                  = findViewById(R.id.txt_bienvenida);
        txtNombreConductor             = findViewById(R.id.txt_nombre_conductor);
        btnPublicarViaje               = findViewById(R.id.btn_publicar_viaje);
        btnMisRutas                    = findViewById(R.id.btn_mis_rutas);
        btnMisVehiculos                = findViewById(R.id.btn_mis_vehiculos);
        layoutCalificacionesPendientes = findViewById(R.id.layout_calificaciones_pendientes);
        dividerCalificaciones          = findViewById(R.id.divider_calificaciones);
        chipCalificacionesPendientes   = findViewById(R.id.chip_calificaciones_pendientes);
        rvCalificacionesPendientes     = findViewById(R.id.rv_calificaciones_pendientes);
        swipeRefresh                   = findViewById(R.id.swipe_refresh);
    }

    // =========================================================================
    //  CONFIGURACIÓN
    // =========================================================================
    private void configurarSaludo() {
        int hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String saludo;
        if      (hora >= 6  && hora < 12) saludo = "Buenos días,";
        else if (hora >= 12 && hora < 18) saludo = "Buenas tardes,";
        else                               saludo = "Buenas noches,";

        if (txtBienvenida      != null) txtBienvenida.setText(saludo);
        if (txtNombreConductor != null) {
            String nombre = session.getNombre();
            if (nombre != null && !nombre.isEmpty())
                txtNombreConductor.setText(nombre);
        }

        String fotoUrl = session.getFotoPerfil();
        android.widget.ImageView ivAvatar  = findViewById(R.id.iv_avatar_header);
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

    private void configurarRecyclers() {
        rvViajes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ViajesAdapter(this, viajes);
        rvViajes.setAdapter(adapter);
        rvViajes.setNestedScrollingEnabled(false);

        if (rvCalificacionesPendientes != null) {
            rvCalificacionesPendientes.setLayoutManager(new LinearLayoutManager(this));
            adapterCalificaciones = new CalificacionesPendientesAdapter(
                    this, listaPendientes, this::onCalificarPasajero);
            rvCalificacionesPendientes.setAdapter(adapterCalificaciones);
            rvCalificacionesPendientes.setNestedScrollingEnabled(false);
        }
    }

    private void configurarBotones() {
        // ── Publicar nuevo viaje (header) ──────────────────────────────────────
        if (btnPublicarViaje != null)
            btnPublicarViaje.setOnClickListener(v -> verificarLimiteYPublicar());

        if (btnMisRutas != null)
            animateButton(btnMisRutas, () -> goTo(MisRutasActivity.class, Transition.SLIDE));
        if (btnMisVehiculos != null)
            animateButton(btnMisVehiculos, () -> goTo(MisVehiculosActivity.class, Transition.SLIDE));

        // ── Publicar ahora (empty state) ───────────────────────────────────────
        com.google.android.material.button.MaterialButton btnPublicarEmpty =
                findViewById(R.id.btn_publicar_empty);
        if (btnPublicarEmpty != null)
            btnPublicarEmpty.setOnClickListener(v -> verificarLimiteYPublicar());

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.teal_500);
            swipeRefresh.setOnRefreshListener(this::cargarMisViajes);
        }
    }

    // =========================================================================
    //  VERIFICAR LÍMITE DE 1 VIAJE ACTIVO ANTES DE PUBLICAR
    // =========================================================================
    private void verificarLimiteYPublicar() {
        mostrarCargando(true);
        ConexionApi.getInstance(this).getArrayNoCache(
                Constantes.MIS_VIAJES,
                response -> {
                    mostrarCargando(false);
                    boolean tieneViajeActivo = false;
                    int idViajeActivo = -1;

                    if (response != null) {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject v = response.optJSONObject(i);
                            if (v == null) continue;
                            String est = v.optString("estado", "").trim().toUpperCase();
                            if ("CREADO".equals(est) || "PROGRAMADO".equals(est)
                                    || "DISPONIBLE".equals(est) || "EN_CURSO".equals(est)
                                    || "INICIADO".equals(est)) {
                                tieneViajeActivo = true;
                                idViajeActivo = v.optInt("idViajes", v.optInt("id", -1));
                                break;
                            }
                        }
                    }

                    if (tieneViajeActivo) {
                        final int fIdViaje = idViajeActivo;
                        runOnUiThread(() -> mostrarAlertaLimiteViaje(fIdViaje));
                    } else {
                        runOnUiThread(() -> goTo(PublicarRuta.class, Transition.SLIDE));
                    }
                },
                error -> {
                    mostrarCargando(false);
                    // Si falla la verificación, dejamos pasar al conductor
                    runOnUiThread(() -> goTo(PublicarRuta.class, Transition.SLIDE));
                }
        );
    }

    private void mostrarAlertaLimiteViaje(int idViajeActivo) {

        // ── Inflar layout personalizado ───────────────────────────────────────────
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
        android.view.View dialogView = inflater.inflate(R.layout.dialog_limite_viaje, null);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this, 0)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        // ── Referencias a vistas del layout ──────────────────────────────────────
        android.widget.TextView  btnVerViaje    = dialogView.findViewById(R.id.btn_dialog_ver_viaje);
        android.widget.TextView  btnCancelar    = dialogView.findViewById(R.id.btn_dialog_cancelar);
        android.widget.TextView  btnCerrar      = dialogView.findViewById(R.id.btn_dialog_cerrar);
        android.widget.ImageView btnClose       = dialogView.findViewById(R.id.btn_dialog_close);

        btnVerViaje.setOnClickListener(v -> {
            dialog.dismiss();
            rvViajes.scrollToPosition(0);
        });

        btnCancelar.setOnClickListener(v -> {
            dialog.dismiss();
            if (idViajeActivo > 0) confirmarCancelarViaje(idViajeActivo);
        });

        btnCerrar.setOnClickListener(v -> dialog.dismiss());
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // Animación de entrada
        if (dialogView.getParent() instanceof android.view.View) {
            android.view.View parent = (android.view.View) dialogView.getParent();
            parent.setScaleX(0.85f);
            parent.setScaleY(0.85f);
            parent.setAlpha(0f);
            parent.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(280)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                    .start();
        }
    }

    private void confirmarCancelarViaje(int viajeId) {
        android.view.View dialogView = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_confirmar_cancelar, null);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this, 0)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        dialogView.findViewById(R.id.btn_dialog_no).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_dialog_si_cancelar).setOnClickListener(v -> {
            dialog.dismiss();
            ejecutarCancelarViaje(viajeId);
        });
        dialogView.findViewById(R.id.btn_dialog_close_cancelar).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void ejecutarCancelarViaje(int viajeId) {
        mostrarCargando(true);
        ConexionApi.getInstance(this).post(
                Constantes.viajeCancelar((long) viajeId),
                null,
                response -> runOnUiThread(() -> {
                    mostrarCargando(false);
                    Toast.makeText(this, "✅ Viaje cancelado", Toast.LENGTH_SHORT).show();
                    viajeActivoId = -1;
                    cargarMisViajes();
                }),
                error -> runOnUiThread(() -> {
                    mostrarCargando(false);
                    int code = (error != null && error.networkResponse != null)
                            ? error.networkResponse.statusCode : 0;
                    String msg = "❌ Error al cancelar el viaje";
                    if (code == 400) msg = "❌ No se puede cancelar (estado inválido)";
                    else if (code == 403) msg = "❌ Sin permisos para cancelar";
                    else if (code == 404) msg = "❌ Viaje no encontrado";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                })
        );
    }

    // =========================================================================
    //  BOTTOM NAV
    // =========================================================================
    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav == null) return;

        nav.setSelectedItemId(R.id.nav_inicio);
        nav.getMenu().findItem(R.id.nav_mapa).setEnabled(false);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_inicio)     return true;
            else if (id == R.id.nav_mis_viajes) { verificarLimiteYPublicar(); return true; }  // ← CAMBIO
            else if (id == R.id.nav_mapa)       { verificarViajeActivoYAbrirMapa(); return true; }
            else if (id == R.id.nav_mensajes)   { goTo(Mensajes.class,      Transition.NONE); finish(); return true; }
            else if (id == R.id.nav_perfil)     { goTo(PerfilUsuario.class, Transition.NONE); finish(); return true; }
            return false;
        });
    }

    private void animarEntrada() {
        if (btnPublicarViaje != null) animateViewEntrance(btnPublicarViaje, 0);
        if (btnMisRutas      != null) animateViewEntrance(btnMisRutas,      80);
        if (btnMisVehiculos  != null) animateViewEntrance(btnMisVehiculos,  80);
        if (rvViajes         != null) animateViewEntrance(rvViajes,         160);
        if (layoutEmpty      != null) animateViewEntrance(layoutEmpty,      160);
    }

    // =========================================================================
    //  MAPA — verificar viaje activo antes de abrir
    // =========================================================================
    private void verificarViajeActivoYAbrirMapa() {
        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_VIAJES,
                response -> {
                    JSONObject viajeActivo = null;
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject v = response.optJSONObject(i);
                        if (v == null) continue;
                        String est = v.optString("estado", "").toUpperCase();
                        if ("INICIADO".equals(est) || "EN_CURSO".equals(est)) {
                            viajeActivo = v;
                            break;
                        }
                    }

                    if (viajeActivo == null) {
                        runOnUiThread(() ->
                                new android.app.AlertDialog.Builder(this)
                                        .setTitle("Sin viaje activo")
                                        .setMessage("Primero debes publicar e iniciar un viaje para acceder al mapa.")
                                        .setCancelable(true)
                                        .setPositiveButton("Publicar viaje", (d, w) -> verificarLimiteYPublicar())
                                        .setNegativeButton("Cancelar", null)
                                        .show()
                        );
                        return;
                    }

                    final int idViaje = viajeActivo.optInt("idViajes",
                            viajeActivo.optInt("idViaje",
                                    viajeActivo.optInt("id", 0)));

                    double latO = viajeActivo.optDouble("latOrigen",  0);
                    double lngO = viajeActivo.optDouble("lngOrigen",  0);
                    double latD = viajeActivo.optDouble("latDestino", 0);
                    double lngD = viajeActivo.optDouble("lngDestino", 0);
                    String origen  = viajeActivo.optString("origen",  "");
                    String destino = viajeActivo.optString("destino", "");

                    JSONObject ruta = viajeActivo.optJSONObject("ruta");
                    if (ruta != null) {
                        if (latO == 0) latO = ruta.optDouble("latOrigen",  ruta.optDouble("latitudOrigen", 0));
                        if (lngO == 0) lngO = ruta.optDouble("lngOrigen",  ruta.optDouble("longitudOrigen", 0));
                        if (latD == 0) latD = ruta.optDouble("latDestino", ruta.optDouble("latitudDestino", 0));
                        if (lngD == 0) lngD = ruta.optDouble("lngDestino", ruta.optDouble("longitudDestino", 0));
                        if (origen.isEmpty())  origen  = ruta.optString("origen",  "");
                        if (destino.isEmpty()) destino = ruta.optString("destino", "");
                        if (origen.isEmpty())  origen  = ruta.optString("nombre",  "");
                    }

                    JSONArray paradasArr = null;
                    if (ruta != null) paradasArr = ruta.optJSONArray("paradas");
                    if (paradasArr == null) paradasArr = viajeActivo.optJSONArray("paradas");

                    String paradasJson = "";
                    if (paradasArr != null && paradasArr.length() > 0)
                        paradasJson = paradasArr.toString();

                    if (latO == 0 || latD == 0) {
                        try {
                            JSONArray paradas = paradasArr;
                            if (paradas != null && paradas.length() >= 2) {
                                JSONObject pOrigen = null, pDestino = null;
                                int maxOrden = -1;
                                for (int j = 0; j < paradas.length(); j++) {
                                    JSONObject p = paradas.optJSONObject(j);
                                    if (p == null) continue;
                                    String tipo = p.optString("tipo", "").toUpperCase().trim();
                                    double pLat = p.optDouble("lat", p.optDouble("latitud", 0));
                                    if (pLat == 0 || "BAJADA".equals(tipo)) continue;
                                    int orden = p.optInt("orden", j);
                                    if (pOrigen == null) pOrigen = p;
                                    if (orden > maxOrden) { maxOrden = orden; pDestino = p; }
                                }
                                if (pOrigen  == null) pOrigen  = paradas.getJSONObject(0);
                                if (pDestino == null || pDestino == pOrigen)
                                    pDestino = paradas.getJSONObject(paradas.length() - 1);
                                if (latO == 0 && pOrigen != null) {
                                    latO = pOrigen.optDouble("lat", pOrigen.optDouble("latitud", 0));
                                    lngO = pOrigen.optDouble("lng", pOrigen.optDouble("longitud", 0));
                                    if (origen.isEmpty()) origen = pOrigen.optString("nombre", "");
                                }
                                if (latD == 0 && pDestino != null) {
                                    latD = pDestino.optDouble("lat", pDestino.optDouble("latitud", 0));
                                    lngD = pDestino.optDouble("lng", pDestino.optDouble("longitud", 0));
                                    if (destino.isEmpty()) destino = pDestino.optString("nombre", "");
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error leyendo paradas: " + e.getMessage());
                        }
                    }

                    if ((latO == 0 || latD == 0) && idViaje > 0) {
                        cargarViajeCompletoYAbrirMapa(idViaje);
                        return;
                    }

                    final double fLatO    = latO, fLngO = lngO;
                    final double fLatD    = latD, fLngD = lngD;
                    final String fOrig    = origen.isEmpty()  ? "Origen"  : origen;
                    final String fDest    = destino.isEmpty() ? "Destino" : destino;
                    final String fParadas = paradasJson;

                    runOnUiThread(() -> {
                        Intent intent = new Intent(this, Mapa.class);
                        intent.putExtra("ID_VIAJE",      idViaje);
                        intent.putExtra("DESDE_VIAJE",   true);
                        intent.putExtra("ORIGEN_LAT",    fLatO);
                        intent.putExtra("ORIGEN_LNG",    fLngO);
                        intent.putExtra("DESTINO_LAT",   fLatD);
                        intent.putExtra("DESTINO_LNG",   fLngD);
                        intent.putExtra("NOM_SUBIDA",    fOrig);
                        intent.putExtra("NOM_BAJADA",    fDest);
                        intent.putExtra("NOM_CONDUCTOR", session.getNombre());
                        intent.putExtra("PARADAS_JSON",  fParadas);
                        startActivity(intent);
                    });
                },
                error -> runOnUiThread(() -> startActivity(new Intent(this, Mapa.class)))
        );
    }

    private void cargarViajeCompletoYAbrirMapa(int idViaje) {
        Log.d(TAG, "Cargando viaje completo id=" + idViaje);
        ConexionApi.getInstance(this).getObject(
                Constantes.viajePorId((long) idViaje),
                viajeCompleto -> {
                    double latO = viajeCompleto.optDouble("latOrigen",  0);
                    double lngO = viajeCompleto.optDouble("lngOrigen",  0);
                    double latD = viajeCompleto.optDouble("latDestino", 0);
                    double lngD = viajeCompleto.optDouble("lngDestino", 0);
                    String origen  = viajeCompleto.optString("origen",  "");
                    String destino = viajeCompleto.optString("destino", "");

                    JSONObject ruta = viajeCompleto.optJSONObject("ruta");
                    if (ruta != null) {
                        if (latO == 0) latO = ruta.optDouble("latOrigen",  ruta.optDouble("latitudOrigen", 0));
                        if (lngO == 0) lngO = ruta.optDouble("lngOrigen",  ruta.optDouble("longitudOrigen", 0));
                        if (latD == 0) latD = ruta.optDouble("latDestino", ruta.optDouble("latitudDestino", 0));
                        if (lngD == 0) lngD = ruta.optDouble("lngDestino", ruta.optDouble("longitudDestino", 0));
                        if (origen.isEmpty())  origen  = ruta.optString("origen",  ruta.optString("nombre", ""));
                        if (destino.isEmpty()) destino = ruta.optString("destino", "");
                    }

                    JSONArray paradasArr = null;
                    if (ruta != null) paradasArr = ruta.optJSONArray("paradas");
                    if (paradasArr == null) paradasArr = viajeCompleto.optJSONArray("paradas");

                    String paradasJson = "";
                    if (paradasArr != null && paradasArr.length() > 0)
                        paradasJson = paradasArr.toString();

                    try {
                        JSONArray paradas = paradasArr;
                        if (paradas != null && paradas.length() >= 2) {
                            JSONObject pOrigen = null, pDestino = null;
                            int maxOrden = -1;
                            for (int j = 0; j < paradas.length(); j++) {
                                JSONObject p = paradas.optJSONObject(j);
                                if (p == null) continue;
                                String tipo = p.optString("tipo", "").toUpperCase().trim();
                                double pLat = p.optDouble("lat", p.optDouble("latitud", 0));
                                if (pLat == 0 || "BAJADA".equals(tipo)) continue;
                                int orden = p.optInt("orden", j);
                                if (pOrigen == null) pOrigen = p;
                                if (orden > maxOrden) { maxOrden = orden; pDestino = p; }
                            }
                            if (pOrigen  == null) pOrigen  = paradas.getJSONObject(0);
                            if (pDestino == null || pDestino == pOrigen)
                                pDestino = paradas.getJSONObject(paradas.length() - 1);
                            if (latO == 0 && pOrigen != null) {
                                latO = pOrigen.optDouble("lat", pOrigen.optDouble("latitud", 0));
                                lngO = pOrigen.optDouble("lng", pOrigen.optDouble("longitud", 0));
                                if (origen.isEmpty()) origen = pOrigen.optString("nombre", "");
                            }
                            if (latD == 0 && pDestino != null) {
                                latD = pDestino.optDouble("lat", pDestino.optDouble("latitud", 0));
                                lngD = pDestino.optDouble("lng", pDestino.optDouble("longitud", 0));
                                if (destino.isEmpty()) destino = pDestino.optString("nombre", "");
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "cargarViajeCompleto paradas: " + e.getMessage());
                    }

                    final double fLatO    = latO, fLngO = lngO;
                    final double fLatD    = latD, fLngD = lngD;
                    final String fOrig    = origen.isEmpty()  ? "Origen"  : origen;
                    final String fDest    = destino.isEmpty() ? "Destino" : destino;
                    final String fParadas = paradasJson;

                    runOnUiThread(() -> {
                        Intent intent = new Intent(this, Mapa.class);
                        intent.putExtra("ID_VIAJE",      idViaje);
                        intent.putExtra("DESDE_VIAJE",   true);
                        intent.putExtra("ORIGEN_LAT",    fLatO);
                        intent.putExtra("ORIGEN_LNG",    fLngO);
                        intent.putExtra("DESTINO_LAT",   fLatD);
                        intent.putExtra("DESTINO_LNG",   fLngD);
                        intent.putExtra("NOM_SUBIDA",    fOrig);
                        intent.putExtra("NOM_BAJADA",    fDest);
                        intent.putExtra("NOM_CONDUCTOR", session.getNombre());
                        intent.putExtra("PARADAS_JSON",  fParadas);
                        startActivity(intent);
                    });
                },
                error -> {
                    Log.e(TAG, "Error cargando viaje completo");
                    runOnUiThread(() -> startActivity(new Intent(this, Mapa.class)));
                }
        );
    }

    // =========================================================================
    //  CARGAR MIS VIAJES
    // =========================================================================
    private void cargarMisViajes() {
        if (conductorId == -1) {
            Toast.makeText(this, "Error de sesión. Vuelve a iniciar sesión.", Toast.LENGTH_LONG).show();
            irAlLogin();
            return;
        }
        mostrarCargando(true);
        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_VIAJES,
                response -> {
                    mostrarCargando(false);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    viajes.clear();
                    viajeActivoId = -1;

                    // Log para diagnosticar estados que llegan del servidor
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject viaje = response.optJSONObject(i);
                        if (viaje == null) continue;
                        String estado = viaje.optString("estado", "").trim().toUpperCase();
                        Log.d(TAG, "Viaje id=" + viaje.optInt("idViajes", viaje.optInt("id", 0))
                                + " estado='" + estado + "'");

                        boolean esActivo = estado.equals("CREADO")
                                || estado.equals("PROGRAMADO")
                                || estado.equals("DISPONIBLE")
                                || estado.equals("EN_CURSO")
                                || estado.equals("INICIADO")
                                || estado.equals("ACTIVO");
                        if (esActivo) viajes.add(viaje);
                        if (viajeActivoId == -1 &&
                                (estado.equals("EN_CURSO") || estado.equals("INICIADO"))) {
                            viajeActivoId = viaje.optInt("idViajes", viaje.optInt("id", -1));
                        }
                    }

                    adapter.notifyDataSetChanged();
                    actualizarContadorViajes();

                    boolean vacio = viajes.isEmpty();
                    if (layoutEmpty != null) layoutEmpty.setVisibility(vacio ? View.VISIBLE : View.GONE);
                    if (rvViajes    != null) rvViajes.setVisibility(vacio ? View.GONE : View.VISIBLE);

                    // ── Mensajes del empty state ───────────────────────────────
                    if (vacio && layoutEmpty != null) {
                        int historial = session.getTotalViajesPublicados();
                        TextView txtTitulo    = layoutEmpty.findViewById(R.id.txt_empty_titulo);
                        TextView txtSubtitulo = layoutEmpty.findViewById(R.id.txt_empty_subtitulo);

                        if (historial > 0) {
                            // Segunda vez en adelante: mostrar contador
                            if (txtTitulo    != null)
                                txtTitulo.setText("¡Sigue así, " + session.getNombre() + "!");
                            if (txtSubtitulo != null)
                                txtSubtitulo.setText(
                                        "Ya tienes " + historial
                                                + (historial == 1 ? " viaje publicado." : " viajes publicados.")
                                                + "\nPublica otro y sigue conectando.");
                        } else {
                            // Primera vez: mensaje de bienvenida
                            if (txtTitulo    != null)
                                txtTitulo.setText("No tienes viajes activos");
                            if (txtSubtitulo != null)
                                txtSubtitulo.setText(
                                        "Publica tu primer viaje y empieza\na conectar con pasajeros");
                        }
                    }

                    if (viajeActivoId > 0 && adapter != null) {
                        adapter.iniciarPollingPagosConductor(viajeActivoId);
                    } else if (adapter != null) {
                        adapter.detenerPollingPagosConductor();
                    }
                },
                error -> {
                    mostrarCargando(false);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, "Error cargando viajes.", Toast.LENGTH_LONG).show();
                }
        );
    }

    private void actualizarContadorViajes() {
        int total = viajes.size();
        if (chipTotal != null)
            chipTotal.setText(total + (total == 1 ? " activo" : " activos"));

        // Guardar historial máximo en SessionManager
        int historial = session.getTotalViajesPublicados();
        if (total > historial) {
            session.setTotalViajesPublicados(total);
        }
    }

    private void mostrarCargando(boolean cargando) {
        if (progress    != null) progress.setVisibility(cargando ? View.VISIBLE : View.GONE);
        if (layoutEmpty != null && cargando) layoutEmpty.setVisibility(View.GONE);
    }

    // =========================================================================
    //  CALIFICACIONES PENDIENTES
    // =========================================================================
    private void resetCalificacionesPendientes() {
        listaPendientes.clear();
        if (adapterCalificaciones != null) adapterCalificaciones.notifyDataSetChanged();
        if (layoutCalificacionesPendientes != null)
            layoutCalificacionesPendientes.setVisibility(View.GONE);
        if (dividerCalificaciones != null)
            dividerCalificaciones.setVisibility(View.GONE);
        actualizarBadgePendientes();
    }

    private void agregarPendienteEnLayout(int viajeId, int pasajeroId, String nombre) {
        for (PasajeroPendiente p : listaPendientes)
            if (p.viajeId == viajeId && p.pasajeroId == pasajeroId) return;
        listaPendientes.add(new PasajeroPendiente(viajeId, pasajeroId, nombre));
        runOnUiThread(() -> {
            if (adapterCalificaciones != null) adapterCalificaciones.notifyDataSetChanged();
            actualizarBadgePendientes();
            if (layoutCalificacionesPendientes != null)
                layoutCalificacionesPendientes.setVisibility(View.VISIBLE);
            if (dividerCalificaciones != null)
                dividerCalificaciones.setVisibility(View.VISIBLE);
        });
    }

    private void quitarPendienteDelLayout(int viajeId, int pasajeroId) {
        listaPendientes.removeIf(p -> p.viajeId == viajeId && p.pasajeroId == pasajeroId);
        runOnUiThread(() -> {
            if (adapterCalificaciones != null) adapterCalificaciones.notifyDataSetChanged();
            actualizarBadgePendientes();
            if (listaPendientes.isEmpty()) {
                if (layoutCalificacionesPendientes != null)
                    layoutCalificacionesPendientes.setVisibility(View.GONE);
                if (dividerCalificaciones != null)
                    dividerCalificaciones.setVisibility(View.GONE);
            }
        });
    }

    private void actualizarBadgePendientes() {
        int total = listaPendientes.size();
        if (chipCalificacionesPendientes != null)
            chipCalificacionesPendientes.setText(
                    total + (total == 1 ? " pendiente" : " pendientes"));
    }

    private void onCalificarPasajero(PasajeroPendiente pendiente) {
        if (isFinishing() || isDestroyed()) return;
        CalificacionController.mostrarBottomSheetCalificar(
                this,
                pendiente.viajeId,
                pendiente.pasajeroId,
                pendiente.nombrePasajero,
                "",
                conductorId,
                true,
                (puntuacion, comentario) -> {
                    Log.d(TAG, "Calificado desde card Home → pasajero="
                            + pendiente.pasajeroId + " " + puntuacion + "⭐");
                    quitarPendienteDelLayout(pendiente.viajeId, pendiente.pasajeroId);
                }
        );
    }

    private void verificarCalificacionesPendientes() {
        if (isFinishing() || isDestroyed()) return;
        if (conductorId <= 0) return;

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_VIAJES,
                response -> {
                    List<Integer> viajesFinalizados = new ArrayList<>();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject viaje = response.optJSONObject(i);
                        if (viaje == null) continue;
                        String estado = viaje.optString("estado", "").trim().toUpperCase();
                        if ("FINALIZADO".equals(estado) || "COMPLETADO".equals(estado)) {
                            int vid = viaje.optInt("idViajes", viaje.optInt("id", 0));
                            if (vid > 0) viajesFinalizados.add(vid);
                        }
                    }
                    if (viajesFinalizados.isEmpty()) return;
                    new Handler(Looper.getMainLooper()).post(() ->
                            buscarPasajerosPendientesEnViaje(viajesFinalizados, 0));
                },
                error -> Log.w(TAG, "No se pudo consultar mis-viajes para calificación")
        );
    }

    private void buscarPasajerosPendientesEnViaje(List<Integer> viajesIds, int indiceViaje) {
        if (indiceViaje >= viajesIds.size()) return;
        if (isFinishing() || isDestroyed()) return;

        int viajeId = viajesIds.get(indiceViaje);

        ConexionApi.getInstance(this).getArrayNoCache(
                Constantes.MIS_RESERVAS,
                reservas -> {
                    JSONArray filtradas = new JSONArray();
                    for (int i = 0; i < reservas.length(); i++) {
                        JSONObject r = reservas.optJSONObject(i);
                        if (r == null) continue;
                        int idV = r.optInt("idViaje", 0);
                        if (idV == 0) {
                            JSONObject vo = r.optJSONObject("viaje");
                            if (vo != null) idV = vo.optInt("idViajes", vo.optInt("id", 0));
                        }
                        if (idV == viajeId) filtradas.put(r);
                    }
                    procesarReservasParaCalificar(filtradas, viajeId, viajesIds, indiceViaje);
                },
                err -> buscarPasajerosPendientesEnViaje(viajesIds, indiceViaje + 1)
        );
    }

    private void procesarReservasParaCalificar(JSONArray reservas, int viajeId,
                                               List<Integer> viajesIds, int indiceViaje) {
        try {
            if (reservas == null || reservas.length() == 0) {
                buscarPasajerosPendientesEnViaje(viajesIds, indiceViaje + 1);
                return;
            }

            List<Integer> ids     = new ArrayList<>();
            List<String>  nombres = new ArrayList<>();

            for (int i = 0; i < reservas.length(); i++) {
                JSONObject res = reservas.optJSONObject(i);
                if (res == null) continue;
                String est = res.optString("estado", "").toUpperCase();
                if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;

                int    idPasajero  = -1;
                String nomPasajero = "";

                JSONObject po = null;
                for (String k : new String[]{"pasajero", "usuario", "user", "passenger"}) {
                    JSONObject c = res.optJSONObject(k);
                    if (c != null) { po = c; break; }
                }
                if (po != null) {
                    for (String k : new String[]{"id", "idUsuarios", "idUsuario", "userId"}) {
                        int id = po.optInt(k, -1);
                        if (id > 0) { idPasajero = id; break; }
                    }
                    if (idPasajero > 0) {
                        for (String k : new String[]{"nombre", "nombreCompleto", "name"}) {
                            String n = po.optString(k, "");
                            if (!n.isEmpty() && !n.equals("null")) { nomPasajero = n; break; }
                        }
                        if (nomPasajero.isEmpty()) {
                            String n = po.optString("nombres", "");
                            String a = po.optString("apellidos", "");
                            if (!n.isEmpty() || !a.isEmpty()) nomPasajero = (n + " " + a).trim();
                        }
                    }
                }
                if (idPasajero <= 0) {
                    for (String k : new String[]{"idUsuarios","idUsuario","idPasajero","pasajeroId"}) {
                        int id = res.optInt(k, -1);
                        if (id > 0) { idPasajero = id; break; }
                    }
                    if (idPasajero > 0 && nomPasajero.isEmpty())
                        nomPasajero = res.optString("nombrePasajero", "");
                }

                if (idPasajero > 0) {
                    String nomFinal = nomPasajero.isEmpty() ? "Pasajero" : nomPasajero;
                    ids.add(idPasajero);
                    nombres.add(nomFinal);
                    final int    idF  = idPasajero;
                    final String nF   = nomFinal;
                    final int    vidF = viajeId;
                    new Handler(Looper.getMainLooper()).post(() ->
                            agregarPendienteEnLayout(vidF, idF, nF));
                }
            }

            if (ids.isEmpty()) {
                buscarPasajerosPendientesEnViaje(viajesIds, indiceViaje + 1);
                return;
            }

            Runnable onTodosListos = () ->
                    buscarPasajerosPendientesEnViaje(viajesIds, indiceViaje + 1);

            new Handler(Looper.getMainLooper()).post(() ->
                    mostrarCalificacionesEncadenadas(
                            ids, nombres, conductorId, viajeId, 0, onTodosListos));

        } catch (Exception e) {
            Log.e(TAG, "Error procesando reservas para calificar", e);
            buscarPasajerosPendientesEnViaje(viajesIds, indiceViaje + 1);
        }
    }

    private void mostrarCalificacionesEncadenadas(List<Integer> ids, List<String> nombres,
                                                  int idCalificador, int viajeId,
                                                  int indice, Runnable onTodosListos) {
        if (indice >= ids.size()) {
            if (onTodosListos != null) onTodosListos.run();
            return;
        }
        if (isFinishing() || isDestroyed()) return;

        int    idPasajero  = ids.get(indice);
        String nomPasajero = nombres.get(indice);
        int    siguiente   = indice + 1;

        new CalificacionesManager(this).verificarCalificacion(
                viajeId, idCalificador, idPasajero,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override public void onDebeCalificar() {
                        runOnUiThread(() ->
                                CalificacionController.mostrarBottomSheetCalificar(
                                        HomeConductor.this,
                                        viajeId, idPasajero, nomPasajero,
                                        "",
                                        idCalificador, true, (puntuacion, comentario) -> {
                                            quitarPendienteDelLayout(viajeId, idPasajero);
                                            new Handler(Looper.getMainLooper()).postDelayed(
                                                    () -> mostrarCalificacionesEncadenadas(
                                                            ids, nombres, idCalificador,
                                                            viajeId, siguiente, onTodosListos),
                                                    700);
                                        }
                                )
                        );
                        new Handler(Looper.getMainLooper()).postDelayed(
                                () -> mostrarCalificacionesEncadenadas(
                                        ids, nombres, idCalificador,
                                        viajeId, siguiente, onTodosListos),
                                3000);
                    }
                    @Override public void onYaCalifico(int puntuacion, String estrellas) {
                        quitarPendienteDelLayout(viajeId, idPasajero);
                        mostrarCalificacionesEncadenadas(
                                ids, nombres, idCalificador,
                                viajeId, siguiente, onTodosListos);
                    }
                }
        );
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private void irAlLogin() {
        goTo(Login.class, Transition.FADE, true);
    }

    // ─── SCREEN DESCRIPTOR ────────────────────────────────────────────────────
    @Override
    public String getNombrePantalla() { return "Inicio del Conductor"; }

    @Override
    public String getDescripcionPantalla() {
        int total = viajes.size();
        return "Estás en la pantalla principal de conductor. "
                + "Tienes " + total + (total == 1 ? " viaje activo." : " viajes activos.")
                + " Puedes publicar viajes, ver tus rutas y vehículos.";
    }

    @Override
    public String getOpcionesPantalla() {
        return "Puedes decir: publicar viaje, mis rutas, mis vehículos, "
                + "perfil, mensajes, mapa, o ayuda.";
    }
}