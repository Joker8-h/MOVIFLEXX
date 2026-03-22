package com.arlys.moviflexx.controller;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Conversacion;
import com.arlys.moviflexx.model.ConversacionAdapter;
import com.arlys.moviflexx.model.Manager.FavoritosManager;
import com.arlys.moviflexx.model.NotificacionesHelper;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Mensajes extends BaseActivity {

    private static final String TAG              = "MENSAJES";
    private static final long   POLLING_INTERVAL = 5_000L;
    private static final int    MAX_REINTENTOS   = 3;

    private static final int FILTRO_TODOS     = 0;
    private static final int FILTRO_NO_LEIDOS = 1;
    private static final int FILTRO_FAVORITOS = 2;
    private int filtroActivo = FILTRO_TODOS;

    // ── UI ────────────────────────────────────────────────────────
    private RecyclerView        rvConversaciones;
    private ConversacionAdapter adapter;
    private EditText            etBuscar;
    private LinearLayout        tvSinResultados;
    private TextView            txtEmptySubtitle;
    private SwipeRefreshLayout  swipeRefresh;
    private MaterialCardView    badgeNoLeidos;
    private TextView            txtNoLeidos;
    private FrameLayout         btnNotificaciones;
    private View                dotNotificacion;
    private TextView            chipTodos;
    private TextView            chipNoLeidos;
    private TextView            chipFavoritos;
    private LinearLayout        loadingOverlay;
    private ImageView           ivLoadingAnim;
    private ObjectAnimator      loadingRotator;

    // ── Datos ──────────────────────────────────────────────────────
    private final List<Conversacion>     listaCompleta     = new ArrayList<>();
    private final List<Conversacion>     listaFiltrada     = new ArrayList<>();
    private final java.util.Set<Integer> idsConFotoFallida = new java.util.HashSet<>();
    private final java.util.Set<Long>    idsMensajeCargado = new java.util.HashSet<>();

    private FavoritosManager favoritosManager;
    private int     idUsuarioActual;
    private boolean esConductor;
    private int     intentosFallidos = 0;
    private boolean primeraCarga     = true;

    // ── Polling ────────────────────────────────────────────────────
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;
    private boolean  pollingActivo = false;

    // ── Animación campana ─────────────────────────────────────────
    private Runnable campanada;

    // ═════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(
                android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_mensajes);

        SessionManager sm = new SessionManager(this);
        idUsuarioActual  = sm.getIdUsuario();
        esConductor      = sm.isConductor();
        favoritosManager = new FavoritosManager(this);

        bindViews();
        configurarRecycler();
        configurarSwipe();
        configurarBuscador();
        configurarChips();
        configurarNotificaciones();
        configurarSwipeRefresh();
        configurarBottomNav();

        mostrarLoading(true);
        cargarConversaciones();
        iniciarPolling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav != null) nav.setSelectedItemId(R.id.nav_mensajes);
        if (!pollingActivo) iniciarPolling();
        NotificacionesHelper.configurar(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        detenerPolling();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detenerPolling();
        pararAnimacionLoading();
        if (campanada != null) handler.removeCallbacks(campanada);
    }

    // ═════════════════════════════════════════════════════════════
    //  BIND
    // ═════════════════════════════════════════════════════════════
    private void bindViews() {
        rvConversaciones  = findViewById(R.id.rvConversaciones);
        etBuscar          = findViewById(R.id.etBuscar);
        tvSinResultados   = findViewById(R.id.tvSinResultados);
        txtEmptySubtitle  = findViewById(R.id.txtEmptySubtitle);
        swipeRefresh      = findViewById(R.id.swipeRefresh);
        badgeNoLeidos     = findViewById(R.id.badgeNoLeidos);
        txtNoLeidos       = findViewById(R.id.txtNoLeidos);
        btnNotificaciones = findViewById(R.id.btnNotificaciones);
        dotNotificacion   = findViewById(R.id.dotNotificacion);
        chipTodos         = findViewById(R.id.chipTodos);
        chipNoLeidos      = findViewById(R.id.chipNoLeidos);
        chipFavoritos     = findViewById(R.id.chipFavoritos);
        loadingOverlay    = findViewById(R.id.loadingOverlay);
        ivLoadingAnim     = findViewById(R.id.ivLoadingAnim);
    }

    // ═════════════════════════════════════════════════════════════
    //  LOADING
    // ═════════════════════════════════════════════════════════════
    private void mostrarLoading(boolean mostrar) {
        if (loadingOverlay == null) return;
        if (mostrar) {
            loadingOverlay.setVisibility(View.VISIBLE);
            loadingOverlay.setAlpha(1f);
            arrancarAnimacionLoading();
        } else {
            loadingOverlay.animate().alpha(0f).setDuration(350).withEndAction(() -> {
                loadingOverlay.setVisibility(View.GONE);
                loadingOverlay.setAlpha(1f);
                pararAnimacionLoading();
            }).start();
        }
    }

    private void arrancarAnimacionLoading() {
        if (ivLoadingAnim == null) return;
        pararAnimacionLoading();
        loadingRotator = ObjectAnimator.ofFloat(ivLoadingAnim, View.ROTATION, 0f, 360f);
        loadingRotator.setDuration(900);
        loadingRotator.setInterpolator(new LinearInterpolator());
        loadingRotator.setRepeatCount(ObjectAnimator.INFINITE);
        loadingRotator.start();
    }

    private void pararAnimacionLoading() {
        if (loadingRotator != null) { loadingRotator.cancel(); loadingRotator = null; }
        if (ivLoadingAnim  != null) ivLoadingAnim.setRotation(0f);
    }

    // ═════════════════════════════════════════════════════════════
    //  CARGAR ÚLTIMO MENSAJE — solo una vez por conversación
    // ═════════════════════════════════════════════════════════════
    private void cargarUltimoMensaje(Conversacion conv) {
        if (idsMensajeCargado.contains(conv.getId())) return;
        idsMensajeCargado.add(conv.getId());

        String url = Constantes.chatMensajesPorConversacion(conv.getId());
        ConexionApi.getInstance(this).getArray(url,
                arr -> {
                    if (arr == null || arr.length() == 0) return;
                    JSONObject ultimo = arr.optJSONObject(arr.length() - 1);
                    if (ultimo == null) return;
                    String contenido = ultimo.optString("mensaje",
                            ultimo.optString("contenido",
                                    ultimo.optString("message", "")));
                    if (contenido.isEmpty()) return;
                    if (contenido.equals(conv.getUltimoMensaje())) return;

                    conv.setUltimoMensaje(contenido);

                    String fechaMensaje = ultimo.optString("fechaEnvio",
                            ultimo.optString("createdAt", ""));
                    if (!fechaMensaje.isEmpty()) {
                        long ts = parsearTimestamp(fechaMensaje);
                        if (ts > 0) conv.setTimestampOrden(ts);
                    }

                    idsMensajeCargado.remove(conv.getId());
                    runOnUiThread(this::ordenarYActualizar);
                },
                error -> idsMensajeCargado.remove(conv.getId())
        );
    }

    // ═════════════════════════════════════════════════════════════
    //  CAMPANA
    // ═════════════════════════════════════════════════════════════
    private void configurarNotificaciones() {
        if (btnNotificaciones == null) return;
        btnNotificaciones.post(this::arrancarAnimacionCampana);
        btnNotificaciones.setOnClickListener(v -> {
            ImageView campana = obtenerImageViewCampana();
            if (campana != null) animarCampanaToque(campana);
            startActivity(new Intent(this, Notificaciones.class));
        });
    }

    private void arrancarAnimacionCampana() {
        ImageView campana = obtenerImageViewCampana();
        if (campana == null) return;
        campana.post(() -> { campana.setPivotX(campana.getWidth() / 2f); campana.setPivotY(0f); });
        campanada = new Runnable() {
            @Override public void run() {
                if (isFinishing() || isDestroyed()) return;
                ImageView c = obtenerImageViewCampana();
                if (c == null) return;
                c.animate().rotation(18f).setDuration(90)
                        .withEndAction(() -> c.animate().rotation(-18f).setDuration(90)
                                .withEndAction(() -> c.animate().rotation(12f).setDuration(75)
                                        .withEndAction(() -> c.animate().rotation(-12f).setDuration(75)
                                                .withEndAction(() -> c.animate().rotation(6f).setDuration(60)
                                                        .withEndAction(() -> c.animate().rotation(0f).setDuration(60)
                                                                .withEndAction(() -> handler.postDelayed(campanada, 4_000L))
                                                                .start()).start()).start()).start()).start()).start();
            }
        };
        handler.postDelayed(campanada, 1_500L);
    }

    private void animarCampanaToque(ImageView c) {
        c.animate().rotation(28f).setDuration(65)
                .withEndAction(() -> c.animate().rotation(-28f).setDuration(65)
                        .withEndAction(() -> c.animate().rotation(18f).setDuration(55)
                                .withEndAction(() -> c.animate().rotation(-18f).setDuration(55)
                                        .withEndAction(() -> c.animate().rotation(8f).setDuration(45)
                                                .withEndAction(() -> c.animate().rotation(0f).setDuration(45)
                                                        .start()).start()).start()).start()).start()).start();
    }

    private ImageView obtenerImageViewCampana() {
        if (btnNotificaciones == null) return null;
        for (int i = 0; i < btnNotificaciones.getChildCount(); i++) {
            View hijo = btnNotificaciones.getChildAt(i);
            if (hijo instanceof ImageView) return (ImageView) hijo;
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════
    //  BADGE
    // ═════════════════════════════════════════════════════════════
    private void actualizarBadgeYCampana(int totalNoLeidos) {
        runOnUiThread(() -> {
            if (badgeNoLeidos != null && txtNoLeidos != null) {
                badgeNoLeidos.setVisibility(totalNoLeidos > 0 ? View.VISIBLE : View.GONE);
                if (totalNoLeidos > 0)
                    txtNoLeidos.setText(totalNoLeidos > 99 ? "99+" : String.valueOf(totalNoLeidos));
            }
            if (dotNotificacion != null)
                dotNotificacion.setVisibility(totalNoLeidos > 0 ? View.VISIBLE : View.GONE);
            if (chipNoLeidos != null)
                chipNoLeidos.setText(totalNoLeidos > 0 ? "No leídos  " + totalNoLeidos : "No leídos");
        });
    }

    // ═════════════════════════════════════════════════════════════
    //  SWIPE → FAVORITO
    // ═════════════════════════════════════════════════════════════
    private void configurarSwipe() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh,
                                  RecyclerView.ViewHolder target) { return false; }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos < 0 || pos >= listaFiltrada.size()) return;
                Conversacion conv = listaFiltrada.get(pos);
                boolean ahoraEsFav = favoritosManager.toggleFavorito(conv.getId());
                Toast.makeText(Mensajes.this,
                        ahoraEsFav ? "⭐ Agregado a favoritos" : "Quitado de favoritos",
                        Toast.LENGTH_SHORT).show();
                if (filtroActivo == FILTRO_FAVORITOS && !ahoraEsFav) {
                    listaFiltrada.remove(pos);
                    adapter.notifyItemRemoved(pos);
                    actualizarEstadoVacio("");
                } else {
                    adapter.notifyItemChanged(pos);
                }
            }

            @Override
            public void onChildDraw(android.graphics.Canvas c, RecyclerView rv,
                                    RecyclerView.ViewHolder vh,
                                    float dX, float dY, int state, boolean active) {
                View item = vh.itemView;
                if (dX > 0) {
                    android.graphics.Paint paint = new android.graphics.Paint();
                    paint.setColor(0xFFFFC107);
                    c.drawRect(item.getLeft(), item.getTop(), item.getLeft() + dX, item.getBottom(), paint);
                    paint.setColor(0xFFFFFFFF); paint.setTextSize(48f);
                    paint.setTextAlign(android.graphics.Paint.Align.LEFT);
                    c.drawText("⭐", item.getLeft() + 32f,
                            item.getTop() + (item.getHeight() / 2f) + 16f, paint);
                }
                super.onChildDraw(c, rv, vh, dX, dY, state, active);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(rvConversaciones);
    }

    // ═════════════════════════════════════════════════════════════
    //  CHIPS
    // ═════════════════════════════════════════════════════════════
    private void configurarChips() {
        if (chipTodos == null) return;
        chipTodos.setOnClickListener(v     -> seleccionarChip(FILTRO_TODOS));
        chipNoLeidos.setOnClickListener(v  -> seleccionarChip(FILTRO_NO_LEIDOS));
        chipFavoritos.setOnClickListener(v -> seleccionarChip(FILTRO_FAVORITOS));
        aplicarEstiloChip(chipTodos, true);
        aplicarEstiloChip(chipNoLeidos, false);
        aplicarEstiloChip(chipFavoritos, false);
    }

    private void seleccionarChip(int filtro) {
        filtroActivo = filtro;
        aplicarEstiloChip(chipTodos,     filtro == FILTRO_TODOS);
        aplicarEstiloChip(chipNoLeidos,  filtro == FILTRO_NO_LEIDOS);
        aplicarEstiloChip(chipFavoritos, filtro == FILTRO_FAVORITOS);
        String query = etBuscar != null ? etBuscar.getText().toString().toLowerCase().trim() : "";
        aplicarFiltroCompleto(query);
    }

    private void aplicarEstiloChip(TextView chip, boolean seleccionado) {
        if (chip == null) return;
        chip.setBackgroundResource(seleccionado ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        chip.setTextColor(seleccionado ? 0xFFFFFFFF : 0xFF555555);
        chip.setTypeface(null, seleccionado
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    // ═════════════════════════════════════════════════════════════
    //  RECYCLER
    // ═════════════════════════════════════════════════════════════
    private void configurarRecycler() {
        rvConversaciones.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConversacionAdapter(listaFiltrada, this::abrirChat);
        rvConversaciones.setAdapter(adapter);
        rvConversaciones.setItemAnimator(null);
    }

    private void configurarSwipeRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setColorSchemeResources(R.color.teal_500, R.color.teal_300);
        swipeRefresh.setProgressBackgroundColorSchemeColor(0xFFFFFFFF);
        swipeRefresh.setOnRefreshListener(() -> {
            intentosFallidos = 0;
            idsConFotoFallida.clear();
            idsMensajeCargado.clear();
            listaCompleta.clear();
            adapter.resetAnimations();
            cargarConversaciones();
        });
    }

    // ═════════════════════════════════════════════════════════════
    //  BOTTOM NAV
    // ═════════════════════════════════════════════════════════════
    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav == null) return;
        nav.setSelectedItemId(R.id.nav_mensajes);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_mensajes) return true;
            if (esConductor) {
                if      (id == R.id.nav_inicio)     goTo(HomeConductor.class,       Transition.NONE);
                else if (id == R.id.nav_mis_viajes) goTo(PublicarRuta.class,        Transition.NONE);
                else if (id == R.id.nav_mapa)       goTo(Mapa.class,                Transition.NONE);
                else if (id == R.id.nav_perfil)     goTo(PerfilUsuario.class,       Transition.NONE);
            } else {
                if      (id == R.id.nav_inicio)     goTo(HomePasajero.class,        Transition.NONE);
                else if (id == R.id.nav_mis_viajes) goTo(MisReservasActivity.class, Transition.NONE);
                else if (id == R.id.nav_mapa)       goTo(Mapa.class,                Transition.NONE);
                else if (id == R.id.nav_perfil)     goTo(PerfilUsuario.class,       Transition.NONE);
            }
            finish();
            return true;
        });
    }

    // ═════════════════════════════════════════════════════════════
    //  CARGAR CONVERSACIONES
    // ═════════════════════════════════════════════════════════════
    private void cargarConversaciones() {
        String url = Constantes.chatConversacionesPorUsuario(idUsuarioActual);
        ConexionApi.getInstance(this).getArray(
                url,
                arr -> { intentosFallidos = 0; procesarArray(arr); },
                error -> ConexionApi.getInstance(this).getObject(
                        url,
                        response -> { intentosFallidos = 0; procesarArray(extraerArray(response)); },
                        err2 -> runOnUiThread(() -> {
                            intentosFallidos++;
                            stopRefresh();
                            mostrarLoading(false);
                            if (listaCompleta.isEmpty())
                                mostrarVacio(intentosFallidos >= MAX_REINTENTOS
                                        ? "Sin conexión. Desliza para reintentar."
                                        : "Cargando conversaciones...");
                        })
                )
        );
    }

    // ═════════════════════════════════════════════════════════════
    //  PROCESAR ARRAY — deduplicar por idContacto (fix duplicados)
    // ═════════════════════════════════════════════════════════════
    private void procesarArray(JSONArray arr) {
        try {
            // ── Paso 1: parsear todas las conversaciones del backend ───────────
            List<Conversacion> todasLasConversaciones = new ArrayList<>();

            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    Conversacion c = Conversacion.fromJson(obj, idUsuarioActual);
                    if (c.getId() <= 0) continue;
                    todasLasConversaciones.add(c);
                }
            }

            // ── Paso 2: deduplicar por idContacto ────────────────────────────
            // Clave = idContacto. Si el mismo usuario tiene múltiples
            // conversaciones (un viaje distinto cada una), se fusionan en una sola
            // mostrando siempre el mensaje más reciente y acumulando no leídos.
            //
            // Clave negativa = conversaciones sin contacto identificable
            // (se conservan individualmente para no perder datos).
            Map<Integer, Conversacion> mapaPorContacto = new LinkedHashMap<>();

            for (Conversacion c : todasLasConversaciones) {
                int idContacto = c.getIdContacto();

                if (idContacto <= 0) {
                    // Sin contacto identificable → clave única basada en id de conversación
                    mapaPorContacto.put(-(int) c.getId(), c);
                    continue;
                }

                Conversacion existente = mapaPorContacto.get(idContacto);

                if (existente == null) {
                    // Primera vez que vemos este contacto
                    mapaPorContacto.put(idContacto, c);
                } else {
                    // Ya teníamos una conversación con este contacto:
                    // conservar la más reciente y acumular no leídos
                    boolean cEsMasReciente =
                            c.getTimestampOrden() > existente.getTimestampOrden();

                    if (cEsMasReciente) {
                        // La nueva es más reciente: usarla como principal
                        // heredar foto si la existente ya la tenía cargada
                        if (!existente.getFotoContacto().isEmpty()
                                && c.getFotoContacto().isEmpty()) {
                            c.setFotoContacto(existente.getFotoContacto());
                        }
                        // heredar mensaje si la nueva no tiene pero la existente sí
                        if (c.getUltimoMensaje().isEmpty()
                                && !existente.getUltimoMensaje().isEmpty()) {
                            c.setUltimoMensaje(existente.getUltimoMensaje());
                        }
                        // acumular no leídos de ambas conversaciones
                        c.setMensajesNoLeidos(
                                c.getMensajesNoLeidos() + existente.getMensajesNoLeidos());
                        mapaPorContacto.put(idContacto, c);
                    } else {
                        // La existente sigue siendo más reciente: conservarla
                        // acumular no leídos de la que descartamos
                        existente.setMensajesNoLeidos(
                                existente.getMensajesNoLeidos() + c.getMensajesNoLeidos());
                        // si la existente no tiene mensaje pero la nueva sí, heredar
                        if (existente.getUltimoMensaje().isEmpty()
                                && !c.getUltimoMensaje().isEmpty()) {
                            existente.setUltimoMensaje(c.getUltimoMensaje());
                        }
                    }
                }
            }

            List<Conversacion> nuevaCompleta = new ArrayList<>(mapaPorContacto.values());

            // ── Paso 3: propagar datos ya cargados (foto, mensaje, timestamp) ─
            for (Conversacion c : nuevaCompleta) {
                Conversacion anterior = buscarEnListaCompleta(c.getIdContacto());
                if (anterior == null) {
                    // Es nueva: lanzar cargas
                    cargarFotoContacto(c);
                    if (c.getUltimoMensaje().isEmpty()) cargarUltimoMensaje(c);
                } else {
                    // Ya la teníamos: propagar datos ya cargados
                    if (!anterior.getFotoContacto().isEmpty() && c.getFotoContacto().isEmpty())
                        c.setFotoContacto(anterior.getFotoContacto());
                    if (!anterior.getUltimoMensaje().isEmpty() && c.getUltimoMensaje().isEmpty())
                        c.setUltimoMensaje(anterior.getUltimoMensaje());
                    if (anterior.getTimestampOrden() > c.getTimestampOrden())
                        c.setTimestampOrden(anterior.getTimestampOrden());
                    if (c.getUltimoMensaje().isEmpty()) cargarUltimoMensaje(c);
                }
            }

            // ── Paso 4: calcular badge total ──────────────────────────────────
            int totalNoLeidos = 0;
            for (Conversacion c : nuevaCompleta) totalNoLeidos += c.getMensajesNoLeidos();

            listaCompleta.clear();
            listaCompleta.addAll(nuevaCompleta);

            final int badge = totalNoLeidos;
            runOnUiThread(() -> {
                stopRefresh();
                if (primeraCarga) { primeraCarga = false; mostrarLoading(false); }
                String query = etBuscar != null
                        ? etBuscar.getText().toString().toLowerCase().trim() : "";
                aplicarFiltroCompleto(query);
                actualizarBadgeYCampana(badge);
            });

        } catch (Exception e) {
            Log.e(TAG, "procesarArray error", e);
            runOnUiThread(() -> {
                stopRefresh();
                mostrarLoading(false);
                if (listaCompleta.isEmpty()) mostrarVacio("Error al cargar datos.");
            });
        }
    }

    /** Busca en listaCompleta por idContacto (para propagar datos entre ciclos de polling) */
    private Conversacion buscarEnListaCompleta(int idContacto) {
        if (idContacto <= 0) return null;
        for (Conversacion c : listaCompleta) {
            if (c.getIdContacto() == idContacto) return c;
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════
    //  ORDENAR Y ACTUALIZAR
    // ═════════════════════════════════════════════════════════════
    private void ordenarYActualizar() {
        String query = etBuscar != null
                ? etBuscar.getText().toString().toLowerCase().trim() : "";
        aplicarFiltroCompleto(query);
    }

    // ═════════════════════════════════════════════════════════════
    //  FILTRO — más reciente primero + no redibujar si no cambió
    // ═════════════════════════════════════════════════════════════
    @SuppressLint("NotifyDataSetChanged")
    private void aplicarFiltroCompleto(String query) {
        List<Conversacion> nuevaLista = new ArrayList<>();
        for (Conversacion c : listaCompleta) {
            if (filtroActivo == FILTRO_NO_LEIDOS && c.getMensajesNoLeidos() == 0) continue;
            if (filtroActivo == FILTRO_FAVORITOS && !favoritosManager.esFavorito(c.getId())) continue;
            if (!query.isEmpty()) {
                String nombre = c.getNombreContacto() != null
                        ? c.getNombreContacto().toLowerCase() : "";
                String ultimo = c.getUltimoMensaje() != null
                        ? c.getUltimoMensaje().toLowerCase() : "";
                if (!nombre.contains(query) && !ultimo.contains(query)) continue;
            }
            nuevaLista.add(c);
        }

        // Ordenar más reciente primero (igual que WhatsApp)
        Collections.sort(nuevaLista, (a, b) ->
                Long.compare(b.getTimestampOrden(), a.getTimestampOrden()));

        // No redibujar si los datos son idénticos
        if (!listaCambio(listaFiltrada, nuevaLista)) {
            actualizarEstadoVacio(query);
            return;
        }

        listaFiltrada.clear();
        listaFiltrada.addAll(nuevaLista);
        adapter.notifyDataSetChanged();
        actualizarEstadoVacio(query);
    }

    private boolean listaCambio(List<Conversacion> vieja, List<Conversacion> nueva) {
        if (vieja.size() != nueva.size()) return true;
        for (int i = 0; i < vieja.size(); i++) {
            Conversacion v = vieja.get(i);
            Conversacion n = nueva.get(i);
            if (v.getId() != n.getId()) return true;
            if (v.getMensajesNoLeidos() != n.getMensajesNoLeidos()) return true;
            if (!v.getUltimoMensaje().equals(n.getUltimoMensaje())) return true;
            if (!v.getNombreContacto().equals(n.getNombreContacto())) return true;
            if (v.getTimestampOrden() != n.getTimestampOrden()) return true;
        }
        return false;
    }

    // ═════════════════════════════════════════════════════════════
    //  BUSCADOR
    // ═════════════════════════════════════════════════════════════
    private void configurarBuscador() {
        if (etBuscar == null) return;
        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                aplicarFiltroCompleto(s.toString().toLowerCase().trim());
            }
        });
    }

    // ═════════════════════════════════════════════════════════════
    //  FOTO CONTACTO
    // ═════════════════════════════════════════════════════════════
    private void cargarFotoContacto(Conversacion conv) {
        if (conv.getIdContacto() <= 0) return;
        if (conv.getFotoContacto() != null && !conv.getFotoContacto().isEmpty()) return;
        if (idsConFotoFallida.contains(conv.getIdContacto())) return;

        String url = Constantes.authPorId((long) conv.getIdContacto());
        ConexionApi.getInstance(this).getObject(url,
                perfil -> {
                    String foto = extraerFotoDeJson(perfil);
                    if (!foto.isEmpty()) {
                        conv.setFotoContacto(foto);
                        runOnUiThread(() -> {
                            int idx = listaFiltrada.indexOf(conv);
                            if (idx >= 0) adapter.notifyItemChanged(idx);
                        });
                    } else {
                        idsConFotoFallida.add(conv.getIdContacto());
                    }
                },
                error -> {
                    idsConFotoFallida.add(conv.getIdContacto());
                    Log.w(TAG, "cargarFotoContacto → sin foto id=" + conv.getIdContacto());
                }
        );
    }

    private String extraerFotoDeJson(JSONObject perfil) {
        if (perfil == null) return "";
        for (String campo : new String[]{"fotoPerfi","fotoPerfil","foto",
                "photoUrl","profilePicture","avatar","imagenPerfil","urlFoto"}) {
            String v = perfil.optString(campo, "");
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        JSONObject nested = perfil.optJSONObject("usuario");
        if (nested != null) {
            for (String campo : new String[]{"fotoPerfi","fotoPerfil","foto","photoUrl","avatar"}) {
                String v = nested.optString(campo, "");
                if (!v.isEmpty() && !v.equals("null")) return v;
            }
        }
        return "";
    }

    // ═════════════════════════════════════════════════════════════
    //  ABRIR CHAT
    // ═════════════════════════════════════════════════════════════
    private void abrirChat(Conversacion c) {
        Intent i = new Intent(this, Chat.class);
        i.putExtra("idConversacion", c.getId());
        i.putExtra("nombre",         c.getNombreContacto());
        startActivity(i);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    // ═════════════════════════════════════════════════════════════
    //  POLLING
    // ═════════════════════════════════════════════════════════════
    private void iniciarPolling() {
        if (pollingActivo) return;
        pollingActivo = true;
        pollingRunnable = new Runnable() {
            @Override public void run() {
                if (!pollingActivo) return;
                cargarConversaciones();
                handler.postDelayed(this, POLLING_INTERVAL);
            }
        };
        handler.postDelayed(pollingRunnable, POLLING_INTERVAL);
    }

    private void detenerPolling() {
        pollingActivo = false;
        if (pollingRunnable != null) handler.removeCallbacks(pollingRunnable);
    }

    // ═════════════════════════════════════════════════════════════
    //  HELPERS UI
    // ═════════════════════════════════════════════════════════════
    private void stopRefresh() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
    }

    private void actualizarEstadoVacio(String query) {
        boolean vacio = listaFiltrada.isEmpty();
        rvConversaciones.setVisibility(vacio ? View.GONE : View.VISIBLE);
        if (vacio) {
            if (filtroActivo == FILTRO_FAVORITOS)
                mostrarVacio("No tienes conversaciones favoritas.\nDesliza un chat a la derecha para marcarlo ⭐");
            else if (filtroActivo == FILTRO_NO_LEIDOS)
                mostrarVacio("No tienes mensajes sin leer.");
            else if (!query.isEmpty())
                mostrarVacio("No se encontraron chats con \"" + query + "\"");
            else
                mostrarVacioSegunRol();
        } else {
            ocultarVacio();
        }
    }

    private void mostrarVacioSegunRol() {
        mostrarVacio(esConductor
                ? "Aún no tienes mensajes de pasajeros."
                : "Aún no tienes conversaciones.\nLos chats con conductores aparecerán aquí.");
    }

    private void mostrarVacio(String msg) {
        runOnUiThread(() -> {
            if (tvSinResultados  != null) tvSinResultados.setVisibility(View.VISIBLE);
            if (txtEmptySubtitle != null) txtEmptySubtitle.setText(msg);
            rvConversaciones.setVisibility(View.GONE);
        });
    }

    private void ocultarVacio() {
        if (tvSinResultados != null) tvSinResultados.setVisibility(View.GONE);
        rvConversaciones.setVisibility(View.VISIBLE);
    }

    private JSONArray extraerArray(JSONObject response) {
        if (response == null) return null;
        if (response.has("content"))        return response.optJSONArray("content");
        if (response.has("conversaciones")) return response.optJSONArray("conversaciones");
        if (response.has("data"))           return response.optJSONArray("data");
        try {
            if (response.toString().startsWith("[")) return new JSONArray(response.toString());
        } catch (Exception ignored) {}
        return null;
    }

    private long parsearTimestamp(String fecha) {
        if (fecha == null || fecha.isEmpty()) return 0L;
        try {
            long n = Long.parseLong(fecha);
            return n < 10_000_000_000L ? n * 1000L : n;
        } catch (NumberFormatException ignored) {}
        for (String f : new String[]{
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss"}) {
            try {
                java.util.Date d = new java.text.SimpleDateFormat(
                        f, java.util.Locale.getDefault()).parse(fecha);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return 0L;
    }
}