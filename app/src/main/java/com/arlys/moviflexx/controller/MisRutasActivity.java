package com.arlys.moviflexx.controller;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.adapter.RutasAdapter;
import com.arlys.moviflexx.adapter.ViajesAdapter;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Manager.CalificacionesManager;
import com.arlys.moviflexx.model.SessionManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MisRutasActivity extends AppCompatActivity {

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private TextView     tabRutas, tabHistorial;
    private View         indicadorRutas, indicadorHistorial;
    private LinearLayout panelRutas, panelHistorial;

    // ── Rutas ─────────────────────────────────────────────────────────────────
    private RecyclerView recyclerRutas;
    private RutasAdapter adapterRutas;
    private LinearLayout layoutEmptyRutas;
    private TextView     txtContadorRutas;
    private final List<JSONObject> rutas = new ArrayList<>();

    // ── Historial ─────────────────────────────────────────────────────────────
    private RecyclerView  recyclerPendientes, recyclerCalificados;
    private ViajesAdapter adapterPendientes,  adapterCalificados;
    private LinearLayout  layoutEmptyHistorial;
    private TextView      txtContadorPendientes, txtContadorCalificados;
    private LinearLayout  sectionPendientes, sectionCalificados;

    private final List<JSONObject> viajesPendientes  = new ArrayList<>();
    private final List<JSONObject> viajesCalificados = new ArrayList<>();

    private int idUsuario;

    // =========================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_rutas);

        idUsuario = new SessionManager(this).getIdUsuario();

        // ── Botón atrás ──────────────────────────────────────────────────────
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> onBackPressed());

        // ── Tabs ─────────────────────────────────────────────────────────────
        tabRutas           = findViewById(R.id.tabRutas);
        tabHistorial       = findViewById(R.id.tabHistorial);
        indicadorRutas     = findViewById(R.id.indicadorRutas);
        indicadorHistorial = findViewById(R.id.indicadorHistorial);
        panelRutas         = findViewById(R.id.panelRutas);
        panelHistorial     = findViewById(R.id.panelHistorial);

        tabRutas.setOnClickListener(v     -> mostrarTab(true));
        tabHistorial.setOnClickListener(v -> mostrarTab(false));

        // ── Rutas ─────────────────────────────────────────────────────────────
        recyclerRutas    = findViewById(R.id.recyclerRutas);
        layoutEmptyRutas = findViewById(R.id.layoutEmpty);
        txtContadorRutas = findViewById(R.id.txtContadorRutas);

        recyclerRutas.setLayoutManager(new LinearLayoutManager(this));
        adapterRutas = new RutasAdapter(this, rutas);
        recyclerRutas.setAdapter(adapterRutas);
        recyclerRutas.setNestedScrollingEnabled(false);

        // ── Historial — pendientes ────────────────────────────────────────────
        recyclerPendientes     = findViewById(R.id.recyclerPendientes);
        txtContadorPendientes  = findViewById(R.id.txtContadorPendientes);
        sectionPendientes      = findViewById(R.id.sectionPendientes);
        layoutEmptyHistorial   = findViewById(R.id.layoutEmptyHistorial);

        recyclerPendientes.setLayoutManager(new LinearLayoutManager(this));
        adapterPendientes = new ViajesAdapter(this, viajesPendientes);
        recyclerPendientes.setAdapter(adapterPendientes);
        recyclerPendientes.setNestedScrollingEnabled(false);

        // ── Historial — calificados ───────────────────────────────────────────
        recyclerCalificados    = findViewById(R.id.recyclerCalificados);
        txtContadorCalificados = findViewById(R.id.txtContadorCalificados);
        sectionCalificados     = findViewById(R.id.sectionCalificados);

        recyclerCalificados.setLayoutManager(new LinearLayoutManager(this));
        adapterCalificados = new ViajesAdapter(this, viajesCalificados);
        recyclerCalificados.setAdapter(adapterCalificados);
        recyclerCalificados.setNestedScrollingEnabled(false);

        mostrarTab(true);   // empezar en Mis Rutas
        cargarRutas();
        cargarHistorial();
    }

    // =========================================================================
    //  TABS
    // =========================================================================
    private void mostrarTab(boolean esRutas) {
        panelRutas.setVisibility(    esRutas ? View.VISIBLE : View.GONE);
        panelHistorial.setVisibility(esRutas ? View.GONE    : View.VISIBLE);

        // Tab activo
        tabRutas.setTypeface(null,     esRutas ? Typeface.BOLD : Typeface.NORMAL);
        tabHistorial.setTypeface(null, esRutas ? Typeface.NORMAL : Typeface.BOLD);
        tabRutas.setTextColor(    Color.parseColor(esRutas ? "#00897B" : "#9CA3AF"));
        tabHistorial.setTextColor(Color.parseColor(esRutas ? "#9CA3AF" : "#00897B"));

        // Indicador
        if (indicadorRutas != null)
            indicadorRutas.setVisibility(esRutas ? View.VISIBLE : View.INVISIBLE);
        if (indicadorHistorial != null)
            indicadorHistorial.setVisibility(esRutas ? View.INVISIBLE : View.VISIBLE);
    }

    // =========================================================================
    //  RUTAS
    // =========================================================================
    private void cargarRutas() {
        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_RUTAS,
                response -> {
                    rutas.clear();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject r = response.optJSONObject(i);
                        if (r != null) rutas.add(r);
                    }
                    adapterRutas.notifyDataSetChanged();
                    int total = rutas.size();
                    if (txtContadorRutas != null)
                        txtContadorRutas.setText(total + (total == 1 ? " ruta" : " rutas"));
                    boolean vacio = rutas.isEmpty();
                    if (layoutEmptyRutas != null)
                        layoutEmptyRutas.setVisibility(vacio ? View.VISIBLE : View.GONE);
                    recyclerRutas.setVisibility(vacio ? View.GONE : View.VISIBLE);
                },
                error -> Toast.makeText(this, "Error cargando rutas", Toast.LENGTH_SHORT).show()
        );
    }

    // =========================================================================
    //  HISTORIAL — ordena: pendientes primero, calificados al final
    // =========================================================================
    private void cargarHistorial() {
        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_VIAJES,
                response -> {
                    // Recoger solo finalizados
                    List<JSONObject> todos = new ArrayList<>();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject v = response.optJSONObject(i);
                        if (v == null) continue;
                        String estado = v.optString("estado", "").trim().toUpperCase();
                        if ("FINALIZADO".equals(estado) || "COMPLETADO".equals(estado))
                            todos.add(v);
                    }

                    if (todos.isEmpty()) {
                        runOnUiThread(() -> {
                            if (layoutEmptyHistorial != null)
                                layoutEmptyHistorial.setVisibility(View.VISIBLE);
                            if (sectionPendientes  != null) sectionPendientes.setVisibility(View.GONE);
                            if (sectionCalificados != null) sectionCalificados.setVisibility(View.GONE);
                        });
                        return;
                    }

                    // Clasificar cada viaje verificando si ya fue calificado
                    viajesPendientes.clear();
                    viajesCalificados.clear();

                    AtomicInteger pendingChecks = new AtomicInteger(todos.size());

                    for (JSONObject viaje : todos) {
                        int viajeId = viaje.optInt("idViajes", viaje.optInt("id", 0));

                        // Para conductor: buscar primer pasajero; para pasajero: buscar conductor
                        boolean esConductor = new SessionManager(this).isConductor();

                        if (esConductor) {
                            // Verificar con idCalificado = 0 → usa el cache o solo verifica
                            // Si no hay pasajeros todavía, va a pendientes por defecto
                            obtenerIdCalificadoYVerificar(viaje, viajeId, esConductor,
                                    pendingChecks, todos.size());
                        } else {
                            // Pasajero califica al conductor
                            int idCond = viaje.optInt("idConductor",
                                    viaje.optInt("conductorId", -1));
                            verificarYClasificar(viaje, viajeId, idUsuario, idCond,
                                    pendingChecks, todos.size());
                        }
                    }
                },
                error -> Toast.makeText(this, "Error cargando historial", Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Para conductores: obtiene el primer pasajero del viaje y verifica la calificación.
     */
    private void obtenerIdCalificadoYVerificar(JSONObject viaje, int viajeId,
                                               boolean esConductor,
                                               AtomicInteger pendingChecks, int total) {
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) viajeId),
                viajeObj -> {
                    org.json.JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    int idPasajero = -1;
                    if (usuarios != null && usuarios.length() > 0) {
                        for (int i = 0; i < usuarios.length(); i++) {
                            JSONObject u = usuarios.optJSONObject(i);
                            if (u == null) continue;
                            String est = u.optString("estado", "").toUpperCase();
                            if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;
                            JSONObject usuObj = u.optJSONObject("usuario");
                            if (usuObj != null) {
                                for (String k : new String[]{"idUsuarios", "id", "idUsuario"}) {
                                    int id = usuObj.optInt(k, -1);
                                    if (id > 0 && id != idUsuario) { idPasajero = id; break; }
                                }
                            }
                            if (idPasajero > 0) break;
                        }
                    }
                    verificarYClasificar(viaje, viajeId, idUsuario, idPasajero,
                            pendingChecks, total);
                },
                err -> {
                    // Si no se puede obtener, va a pendientes (lado seguro)
                    synchronized (viajesPendientes) { viajesPendientes.add(viaje); }
                    checkAndRefresh(pendingChecks, total);
                }
        );
    }

    /**
     * Verifica si idCalificador ya calificó a idCalificado en este viaje.
     * Si sí → va a calificados. Si no (o error) → va a pendientes.
     */
    private void verificarYClasificar(JSONObject viaje, int viajeId,
                                      int idCalificador, int idCalificado,
                                      AtomicInteger pendingChecks, int total) {
        if (idCalificado <= 0) {
            // Sin calificado conocido → pendiente
            synchronized (viajesPendientes) { viajesPendientes.add(viaje); }
            checkAndRefresh(pendingChecks, total);
            return;
        }

        new CalificacionesManager(this).verificarCalificacion(
                viajeId, idCalificador, idCalificado,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override
                    public void onDebeCalificar() {
                        synchronized (viajesPendientes) { viajesPendientes.add(viaje); }
                        checkAndRefresh(pendingChecks, total);
                    }

                    @Override
                    public void onYaCalifico(int puntos, String comentario) {
                        synchronized (viajesCalificados) { viajesCalificados.add(viaje); }
                        checkAndRefresh(pendingChecks, total);
                    }
                }
        );
    }

    /**
     * Cuando todos los viajes fueron clasificados, actualiza la UI.
     */
    private void checkAndRefresh(AtomicInteger pendingChecks, int total) {
        if (pendingChecks.decrementAndGet() == 0) {
            runOnUiThread(this::actualizarPanelHistorial);
        }
    }

    private void actualizarPanelHistorial() {
        boolean hayPendientes  = !viajesPendientes.isEmpty();
        boolean hayCalificados = !viajesCalificados.isEmpty();
        boolean hayAlgo        = hayPendientes || hayCalificados;

        if (layoutEmptyHistorial != null)
            layoutEmptyHistorial.setVisibility(hayAlgo ? View.GONE : View.VISIBLE);

        // ── Sección: Por calificar ────────────────────────────────────────────
        if (sectionPendientes != null)
            sectionPendientes.setVisibility(hayPendientes ? View.VISIBLE : View.GONE);
        adapterPendientes.notifyDataSetChanged();
        if (txtContadorPendientes != null)
            txtContadorPendientes.setText(viajesPendientes.size()
                    + (viajesPendientes.size() == 1 ? " viaje" : " viajes"));

        // ── Sección: Ya calificados ───────────────────────────────────────────
        if (sectionCalificados != null)
            sectionCalificados.setVisibility(hayCalificados ? View.VISIBLE : View.GONE);
        adapterCalificados.notifyDataSetChanged();
        if (txtContadorCalificados != null)
            txtContadorCalificados.setText(viajesCalificados.size()
                    + (viajesCalificados.size() == 1 ? " viaje" : " viajes"));
    }
}