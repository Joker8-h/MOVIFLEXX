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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Conversacion;
import com.arlys.moviflexx.model.ConversacionAdapter;
import com.arlys.moviflexx.model.SessionManager;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Conversaciones extends BaseActivity {

    private static final String TAG           = "CONVERSACIONES";
    private static final long   POLL_INTERVAL = 4000;

    // ── UI ───────────────────────────────────────────────────────────────────
    private RecyclerView        rvConversaciones;
    private ConversacionAdapter adapter;
    private SwipeRefreshLayout  swipeRefresh;
    private LinearLayout        tvSinResultados;

    // Loading overlay
    private LinearLayout        loadingOverlay;
    private ImageView           ivLoadingAnim;
    private ObjectAnimator      loadingRotator;

    // Chips de filtro
    private TextView chipTodos;
    private TextView chipNoLeidos;
    private TextView chipFavoritos;

    // Header — notificaciones + avatar
    private FrameLayout      btnNotificaciones;
    private View             dotNotificacion;
    private FrameLayout      badgeNoLeidos;
    private TextView         txtNoLeidos;
    private ImageView        ivAvatarHeader;
    private MaterialCardView cardAvatarFotoHeader;
    private MaterialCardView cardAvatarInicialHeader;

    // Buscador
    private EditText etBuscar;

    // ── Datos ────────────────────────────────────────────────────────────────
    private SessionManager           session;
    private int                      idUsuario;
    private final List<Conversacion> listaCompleta = new ArrayList<>();
    private final List<Conversacion> listaFiltrada = new ArrayList<>();

    private int     filtroActivo = 0;   // 0=Todos 1=NoLeídos 2=Favoritos
    private boolean primeraCarga = true;

    // ── Polling ──────────────────────────────────────────────────────────────
    private final Handler  handler       = new Handler(Looper.getMainLooper());
    private       Runnable pollRunnable;
    private       boolean  pollingActivo = false;

    // ═════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═════════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversaciones);

        session   = new SessionManager(this);
        idUsuario = session.getIdUsuario();
        Log.d(TAG, "ID Usuario: " + idUsuario);

        bindViews();
        cargarFotoPerfilHeader();
        configurarRecycler();
        configurarSwipe();
        configurarBuscador();
        configurarChips();
        configurarNotificaciones();

        mostrarLoading(true);
        cargarConversaciones();
        arrancarPolling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarConversaciones();
        cargarFotoPerfilHeader(); // por si cambió
        if (!pollingActivo) arrancarPolling();
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
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  BIND VIEWS
    // ═════════════════════════════════════════════════════════════════════════
    private void bindViews() {
        rvConversaciones        = findViewById(R.id.rvConversaciones);
        swipeRefresh            = findViewById(R.id.swipeRefresh);
        tvSinResultados         = findViewById(R.id.tvSinResultados);

        loadingOverlay          = findViewById(R.id.loadingOverlay);
        ivLoadingAnim           = findViewById(R.id.ivLoadingAnim);

        chipTodos               = findViewById(R.id.chipTodos);
        chipNoLeidos            = findViewById(R.id.chipNoLeidos);
        chipFavoritos           = findViewById(R.id.chipFavoritos);

        btnNotificaciones       = findViewById(R.id.btnNotificaciones);
        dotNotificacion         = findViewById(R.id.dotNotificacion);
        badgeNoLeidos           = findViewById(R.id.badgeNoLeidos);
        txtNoLeidos             = findViewById(R.id.txtNoLeidos);

        etBuscar                = findViewById(R.id.etBuscar);

        // Avatar del header
        ivAvatarHeader          = findViewById(R.id.iv_avatar_header);
        cardAvatarFotoHeader    = findViewById(R.id.card_avatar_foto_header);
        cardAvatarInicialHeader = findViewById(R.id.card_avatar_inicial_header);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  FOTO DE PERFIL EN EL HEADER
    // ═════════════════════════════════════════════════════════════════════════
    private void cargarFotoPerfilHeader() {
        String fotoUrl = session.getFotoPerfil();

        if (ivAvatarHeader != null && !fotoUrl.isEmpty() && !fotoUrl.equals("null")) {
            if (cardAvatarFotoHeader    != null) cardAvatarFotoHeader.setVisibility(View.VISIBLE);
            if (cardAvatarInicialHeader != null) cardAvatarInicialHeader.setVisibility(View.GONE);
            Glide.with(this)
                    .load(fotoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.logomo)
                    .error(R.drawable.logomo)
                    .into(ivAvatarHeader);
        } else {
            if (cardAvatarFotoHeader    != null) cardAvatarFotoHeader.setVisibility(View.GONE);
            if (cardAvatarInicialHeader != null) cardAvatarInicialHeader.setVisibility(View.VISIBLE);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  LOADING
    // ═════════════════════════════════════════════════════════════════════════
    private void mostrarLoading(boolean mostrar) {
        if (loadingOverlay == null) return;
        if (mostrar) {
            loadingOverlay.setVisibility(View.VISIBLE);
            loadingOverlay.setAlpha(1f);
            arrancarAnimacionLoading();
        } else {
            loadingOverlay.animate()
                    .alpha(0f)
                    .setDuration(350)
                    .withEndAction(() -> {
                        loadingOverlay.setVisibility(View.GONE);
                        loadingOverlay.setAlpha(1f);
                        pararAnimacionLoading();
                    })
                    .start();
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
        if (loadingRotator != null) {
            loadingRotator.cancel();
            loadingRotator = null;
        }
        if (ivLoadingAnim != null) ivLoadingAnim.setRotation(0f);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CHIPS DE FILTRO
    // ═════════════════════════════════════════════════════════════════════════
    private void configurarChips() {
        if (chipTodos == null) return;
        chipTodos.setOnClickListener(v     -> seleccionarChip(0));
        chipNoLeidos.setOnClickListener(v  -> seleccionarChip(1));
        chipFavoritos.setOnClickListener(v -> seleccionarChip(2));
    }

    private void seleccionarChip(int chip) {
        filtroActivo = chip;
        actualizarEstiloChip(chipTodos,     chip == 0);
        actualizarEstiloChip(chipNoLeidos,  chip == 1);
        actualizarEstiloChip(chipFavoritos, chip == 2);
        aplicarFiltro(etBuscar != null ? etBuscar.getText().toString() : "");
    }

    private void actualizarEstiloChip(TextView tv, boolean seleccionado) {
        if (tv == null) return;
        tv.setBackgroundResource(seleccionado
                ? R.drawable.bg_chip_selected
                : R.drawable.bg_chip_unselected);
        tv.setTextColor(seleccionado ? 0xFFFFFFFF : 0xFF555555);
        tv.setTypeface(null, seleccionado
                ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  BUSCADOR
    // ═════════════════════════════════════════════════════════════════════════
    private void configurarBuscador() {
        if (etBuscar == null) return;
        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                aplicarFiltro(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void aplicarFiltro(String query) {
        listaFiltrada.clear();
        String q = query.toLowerCase().trim();

        for (Conversacion c : listaCompleta) {
            if (filtroActivo == 1 && c.getMensajesNoLeidos() == 0) continue;

            if (!q.isEmpty()) {
                String nombre = c.getNombreContacto() != null
                        ? c.getNombreContacto().toLowerCase() : "";
                String ultimo = c.getUltimoMensaje() != null
                        ? c.getUltimoMensaje().toLowerCase() : "";
                if (!nombre.contains(q) && !ultimo.contains(q)) continue;
            }
            listaFiltrada.add(c);
        }

        adapter.notifyDataSetChanged();
        actualizarEstadoVacio();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  NOTIFICACIONES
    // ═════════════════════════════════════════════════════════════════════════
    private void configurarNotificaciones() {
        if (btnNotificaciones == null) return;
        btnNotificaciones.setOnClickListener(v -> {
            // startActivity(new Intent(this, Notificaciones.class));
        });
    }

    private void actualizarBadgeNotificaciones(int totalNoLeidos) {
        if (totalNoLeidos > 0) {
            if (dotNotificacion != null) dotNotificacion.setVisibility(View.VISIBLE);
            if (badgeNoLeidos   != null) badgeNoLeidos.setVisibility(View.VISIBLE);
            if (txtNoLeidos     != null) txtNoLeidos.setText(
                    totalNoLeidos > 99 ? "99+" : String.valueOf(totalNoLeidos));
            if (chipNoLeidos    != null)
                chipNoLeidos.setText("No leídos  " + totalNoLeidos);
        } else {
            if (dotNotificacion != null) dotNotificacion.setVisibility(View.GONE);
            if (badgeNoLeidos   != null) badgeNoLeidos.setVisibility(View.GONE);
            if (chipNoLeidos    != null) chipNoLeidos.setText("No leídos");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  RECYCLER
    // ═════════════════════════════════════════════════════════════════════════
    private void configurarRecycler() {
        rvConversaciones.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConversacionAdapter(listaFiltrada, conv -> {
            Intent intent = new Intent(this, Chat.class);
            intent.putExtra("idConversacion", conv.getId());
            intent.putExtra("nombre", conv.getNombreContacto());
            startActivity(intent);
        });
        rvConversaciones.setAdapter(adapter);
    }

    private void configurarSwipe() {
        if (swipeRefresh == null) return;
        swipeRefresh.setColorSchemeResources(R.color.teal_500);
        swipeRefresh.setOnRefreshListener(this::cargarConversaciones);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CARGAR CONVERSACIONES
    // ═════════════════════════════════════════════════════════════════════════
    private void cargarConversaciones() {
        Log.d(TAG, "GET: " + Constantes.CHAT_CONVERSACIONES);
        ConexionApi.getInstance(this).getArray(
                Constantes.CHAT_CONVERSACIONES,
                this::procesarArray,
                error -> {
                    Log.w(TAG, "getArray falló, intentando getObject");
                    ConexionApi.getInstance(this).getObject(
                            Constantes.CHAT_CONVERSACIONES,
                            response -> procesarArray(extraerArrayDeObjeto(response)),
                            err2 -> runOnUiThread(() -> {
                                Log.e(TAG, "Ambos métodos fallaron: " + err2);
                                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                                mostrarLoading(false);
                                if (listaCompleta.isEmpty())
                                    mostrarVacioConMensaje("Sin conexión. Desliza para reintentar.");
                            })
                    );
                }
        );
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PROCESAR ARRAY
    // ═════════════════════════════════════════════════════════════════════════
    @SuppressLint("NotifyDataSetChanged")
    private void procesarArray(JSONArray arr) {
        try {
            listaCompleta.clear();
            int totalNoLeidos = 0;

            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    Conversacion c = Conversacion.fromJson(obj, idUsuario);
                    if (c.getId() > 0) {
                        listaCompleta.add(c);
                        totalNoLeidos += c.getMensajesNoLeidos();
                        Log.d(TAG, "Conv[" + i + "]: " + c.getNombreContacto()
                                + " foto=" + c.getFotoContacto());
                    }
                }
            }

            final int badge = totalNoLeidos;

            runOnUiThread(() -> {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (primeraCarga) {
                    primeraCarga = false;
                    mostrarLoading(false);
                }
                aplicarFiltro(etBuscar != null ? etBuscar.getText().toString() : "");
                actualizarBadgeNotificaciones(badge);
            });

            Log.d(TAG, listaCompleta.size() + " conversaciones cargadas");

        } catch (Exception e) {
            Log.e(TAG, "procesarArray error", e);
            runOnUiThread(() -> {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                mostrarLoading(false);
                if (listaCompleta.isEmpty())
                    mostrarVacioConMensaje("Error al procesar datos.");
            });
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS UI
    // ═════════════════════════════════════════════════════════════════════════
    private JSONArray extraerArrayDeObjeto(JSONObject response) {
        if (response == null) return null;
        if (response.has("content"))        return response.optJSONArray("content");
        if (response.has("conversaciones")) return response.optJSONArray("conversaciones");
        if (response.has("data"))           return response.optJSONArray("data");
        return null;
    }

    private void actualizarEstadoVacio() {
        boolean listaVacia = listaFiltrada.isEmpty();
        rvConversaciones.setVisibility(listaVacia ? View.GONE : View.VISIBLE);
        if (tvSinResultados != null)
            tvSinResultados.setVisibility(
                    listaVacia && !listaCompleta.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void mostrarVacioConMensaje(String msg) {
        runOnUiThread(() -> {
            if (tvSinResultados != null) tvSinResultados.setVisibility(View.VISIBLE);
            rvConversaciones.setVisibility(View.GONE);
            Log.d(TAG, "Empty state: " + msg);
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  POLLING
    // ═════════════════════════════════════════════════════════════════════════
    private void arrancarPolling() {
        if (pollingActivo) return;
        pollingActivo = true;
        pollRunnable = new Runnable() {
            @Override public void run() {
                if (!pollingActivo) return;
                cargarConversaciones();
                handler.postDelayed(this, POLL_INTERVAL);
            }
        };
        handler.postDelayed(pollRunnable, POLL_INTERVAL);
        Log.d(TAG, "Polling iniciado");
    }

    private void detenerPolling() {
        pollingActivo = false;
        if (pollRunnable != null) handler.removeCallbacks(pollRunnable);
        Log.d(TAG, "Polling detenido");
    }
}