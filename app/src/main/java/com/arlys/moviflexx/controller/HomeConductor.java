package com.arlys.moviflexx.controller;

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
import com.google.android.material.button.MaterialButton;
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
    private MaterialButton   btnPublicarViaje;
    private MaterialCardView btnMisRutas;
    private MaterialCardView btnMisVehiculos;

    // ── Calificaciones pendientes ─────────────────────────────────────────────
    private LinearLayout                    layoutCalificacionesPendientes;
    private View                            dividerCalificaciones;
    private Chip                            chipCalificacionesPendientes;
    private RecyclerView                    rvCalificacionesPendientes;
    private CalificacionesPendientesAdapter adapterCalificaciones;
    private final List<PasajeroPendiente>   listaPendientes = new ArrayList<>();

    // ── Sesión / datos ────────────────────────────────────────────────────────
    private final List<JSONObject> viajes = new ArrayList<>();
    private SessionManager session;
    private int conductorId = -1;

    /** Evita lanzar la verificación múltiples veces por rotación / backstack. */
    private boolean calificacionPendienteVerificada = false;

    // =========================================================================
    //  MODELO INTERNO
    // =========================================================================

    /**
     * Representa un pasajero de un viaje finalizado que todavía
     * no ha sido calificado por este conductor.
     */
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

        // Limpiar la sección de pendientes para recargar desde cero
        resetCalificacionesPendientes();

        cargarMisViajes();

        // Badge campana de notificaciones
        NotificacionesHelper.configurar(this);

        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav != null) nav.setSelectedItemId(R.id.nav_inicio);

        // Verificar calificaciones pendientes con delay para que la Activity
        // esté completamente visible antes de mostrar cualquier BottomSheet.
        if (!calificacionPendienteVerificada) {
            calificacionPendienteVerificada = true;
            new Handler(Looper.getMainLooper()).postDelayed(
                    this::verificarCalificacionesPendientes, 1500);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Resetear para que al volver se vuelva a verificar
        calificacionPendienteVerificada = false;
    }

    // =========================================================================
    //  ENLAZAR VISTAS
    // =========================================================================

    private void enlazarVistas() {
        // Viajes activos
        rvViajes           = findViewById(R.id.rv_viajes_activos);
        progress           = findViewById(R.id.progress);
        layoutEmpty        = findViewById(R.id.layout_empty);
        chipTotal          = findViewById(R.id.chip_total);

        // Header
        txtBienvenida      = findViewById(R.id.txt_bienvenida);
        txtNombreConductor = findViewById(R.id.txt_nombre_conductor);

        // Acciones rápidas
        btnPublicarViaje   = findViewById(R.id.btn_publicar_viaje);
        btnMisRutas        = findViewById(R.id.btn_mis_rutas);
        btnMisVehiculos    = findViewById(R.id.btn_mis_vehiculos);

        // Calificaciones pendientes
        layoutCalificacionesPendientes = findViewById(R.id.layout_calificaciones_pendientes);
        dividerCalificaciones          = findViewById(R.id.divider_calificaciones);
        chipCalificacionesPendientes   = findViewById(R.id.chip_calificaciones_pendientes);
        rvCalificacionesPendientes     = findViewById(R.id.rv_calificaciones_pendientes);
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

        if (txtBienvenida != null)
            txtBienvenida.setText(saludo);

        if (txtNombreConductor != null) {
            String nombre = session.getNombre();
            if (nombre != null && !nombre.isEmpty())
                txtNombreConductor.setText(nombre);
        }
    }

    private void configurarRecyclers() {
        // RecyclerView viajes activos
        rvViajes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ViajesAdapter(this, viajes);
        rvViajes.setAdapter(adapter);
        rvViajes.setNestedScrollingEnabled(false);

        // RecyclerView calificaciones pendientes
        if (rvCalificacionesPendientes != null) {
            rvCalificacionesPendientes.setLayoutManager(new LinearLayoutManager(this));
            adapterCalificaciones = new CalificacionesPendientesAdapter(
                    this, listaPendientes, this::onCalificarPasajero);
            rvCalificacionesPendientes.setAdapter(adapterCalificaciones);
            rvCalificacionesPendientes.setNestedScrollingEnabled(false);
        }
    }

    private void configurarBotones() {
        if (btnPublicarViaje != null)
            animateButton(btnPublicarViaje, () -> goTo(PublicarRuta.class, Transition.SLIDE));
        if (btnMisRutas != null)
            animateButton(btnMisRutas, () -> goTo(MisRutasActivity.class, Transition.SLIDE));
        if (btnMisVehiculos != null)
            animateButton(btnMisVehiculos, () -> goTo(MisVehiculosActivity.class, Transition.SLIDE));
    }

    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav == null) return;

        nav.setSelectedItemId(R.id.nav_inicio);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_inicio)     return true;
            else if (id == R.id.nav_mis_viajes) goTo(PublicarRuta.class,  Transition.NONE);
            else if (id == R.id.nav_mapa)       goTo(Mapa.class,          Transition.NONE);
            else if (id == R.id.nav_mensajes)   goTo(Mensajes.class,      Transition.NONE);
            else if (id == R.id.nav_perfil)     goTo(PerfilUsuario.class, Transition.NONE);
            finish();
            return true;
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
    //  CARGAR VIAJES ACTIVOS
    // =========================================================================

    private void cargarMisViajes() {
        if (conductorId == -1) {
            Toast.makeText(this, "Error de sesión. Vuelve a iniciar sesión.",
                    Toast.LENGTH_LONG).show();
            irAlLogin();
            return;
        }

        mostrarCargando(true);

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_VIAJES,
                response -> {
                    mostrarCargando(false);
                    viajes.clear();

                    for (int i = 0; i < response.length(); i++) {
                        JSONObject viaje = response.optJSONObject(i);
                        if (viaje == null) continue;

                        String estado = viaje.optString("estado", "").trim().toUpperCase();
                        boolean esActivo = estado.equals("CREADO")
                                || estado.equals("PROGRAMADO")
                                || estado.equals("DISPONIBLE")
                                || estado.equals("EN_CURSO")
                                || estado.equals("INICIADO");

                        if (esActivo) viajes.add(viaje);
                    }

                    adapter.notifyDataSetChanged();
                    actualizarContadorViajes();

                    boolean vacio = viajes.isEmpty();
                    if (layoutEmpty != null)
                        layoutEmpty.setVisibility(vacio ? View.VISIBLE : View.GONE);
                    if (rvViajes != null)
                        rvViajes.setVisibility(vacio ? View.GONE : View.VISIBLE);
                },
                error -> {
                    mostrarCargando(false);
                    Toast.makeText(this, "Error cargando viajes.", Toast.LENGTH_LONG).show();
                }
        );
    }

    private void actualizarContadorViajes() {
        int total = viajes.size();
        if (chipTotal != null)
            chipTotal.setText(total + (total == 1 ? " activo" : " activos"));
    }

    private void mostrarCargando(boolean cargando) {
        if (progress    != null) progress.setVisibility(cargando ? View.VISIBLE : View.GONE);
        if (layoutEmpty != null && cargando) layoutEmpty.setVisibility(View.GONE);
    }

    // =========================================================================
    //  CALIFICACIONES PENDIENTES — SECCIÓN VISUAL EN EL HOME
    // =========================================================================

    /**
     * Limpia la sección de pendientes y la oculta.
     * Se llama al inicio de cada onResume() para evitar datos stale.
     */
    private void resetCalificacionesPendientes() {
        listaPendientes.clear();
        if (adapterCalificaciones != null) adapterCalificaciones.notifyDataSetChanged();
        if (layoutCalificacionesPendientes != null)
            layoutCalificacionesPendientes.setVisibility(View.GONE);
        if (dividerCalificaciones != null)
            dividerCalificaciones.setVisibility(View.GONE);
        actualizarBadgePendientes();
    }

    /**
     * Agrega la card de un pasajero pendiente al Home.
     * Evita duplicados y corre en el UI thread automáticamente.
     */
    private void agregarPendienteEnLayout(int viajeId, int pasajeroId, String nombre) {
        // Evitar duplicados
        for (PasajeroPendiente p : listaPendientes) {
            if (p.viajeId == viajeId && p.pasajeroId == pasajeroId) return;
        }
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

    /**
     * Quita la card del pasajero ya calificado.
     * Oculta la sección entera si ya no quedan pendientes.
     */
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

    /** Actualiza el chip del badge con el total de pendientes. */
    private void actualizarBadgePendientes() {
        int total = listaPendientes.size();
        if (chipCalificacionesPendientes != null)
            chipCalificacionesPendientes.setText(
                    total + (total == 1 ? " pendiente" : " pendientes"));
    }

    /**
     * Callback del adapter cuando el conductor pulsa "Calificar" en una card.
     * Abre el bottom_sheet_calificar.xml a través de CalificacionController
     * y, al confirmar, quita la card del Home.
     */
    private void onCalificarPasajero(PasajeroPendiente pendiente) {
        if (isFinishing() || isDestroyed()) return;

        CalificacionController.mostrarBottomSheetCalificar(
                this,
                pendiente.viajeId,
                pendiente.pasajeroId,    // idCalificado  = pasajero
                pendiente.nombrePasajero,
                conductorId,             // idCalificador = conductor
                true,                    // esConductor   = true → usa CHIPS_PASAJERO
                (puntuacion, comentario) -> {
                    Log.d(TAG, "Calificado desde card Home → pasajero="
                            + pendiente.pasajeroId + " " + puntuacion + "⭐");
                    quitarPendienteDelLayout(pendiente.viajeId, pendiente.pasajeroId);
                }
        );
    }

    // =========================================================================
    //  CALIFICACIONES PENDIENTES — VERIFICACIÓN AUTOMÁTICA AL ABRIR
    //
    //  Flujo completo:
    //   1. GET mis-viajes  →  filtrar estado FINALIZADO / COMPLETADO
    //   2. Por cada viaje  →  GET reservas  →  extraer pasajeros activos
    //   3. Por cada pasajero  →  CalificacionesManager.verificarCalificacion()
    //      (usa caché SharedPreferences sin endpoint extra, clave única por
    //       viaje+calificador+calificado)
    //   4a. onDebeCalificar()  →  agregarPendienteEnLayout() + BottomSheet encadenado
    //   4b. onYaCalifico()     →  asegurar que no aparezca en el Home
    // =========================================================================

    private void verificarCalificacionesPendientes() {
        if (isFinishing() || isDestroyed()) return;
        if (conductorId <= 0) return;

        Log.d(TAG, "Verificando calificaciones pendientes conductor=" + conductorId);

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

                    if (viajesFinalizados.isEmpty()) {
                        Log.d(TAG, "Sin viajes finalizados para calificar");
                        return;
                    }

                    Log.d(TAG, "Viajes finalizados encontrados: " + viajesFinalizados.size());
                    new Handler(Looper.getMainLooper()).post(() ->
                            buscarPasajerosPendientesEnViaje(viajesFinalizados, 0));
                },
                error -> Log.w(TAG, "No se pudo consultar mis-viajes para calificación")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Paso 2: iterar viaje por viaje
    // ─────────────────────────────────────────────────────────────────────────

    private void buscarPasajerosPendientesEnViaje(List<Integer> viajesIds, int indiceViaje) {
        if (indiceViaje >= viajesIds.size()) {
            Log.d(TAG, "Revisión de viajes finalizados completada");
            return;
        }
        if (isFinishing() || isDestroyed()) return;

        int viajeId = viajesIds.get(indiceViaje);
        Log.d(TAG, "Buscando pasajeros en viaje=" + viajeId);

        String url = Constantes.viajeReservasDetalle((long) viajeId);

        ConexionApi.getInstance(this).getArrayNoCache(
                url,
                reservas -> procesarReservasParaCalificar(
                        reservas, viajeId, viajesIds, indiceViaje),
                err -> {
                    // Fallback a endpoint alternativo
                    String urlAlt = Constantes.BASE_URL + "/api/reservas?idViaje=" + viajeId;
                    ConexionApi.getInstance(this).getArrayNoCache(
                            urlAlt,
                            reservas2 -> procesarReservasParaCalificar(
                                    reservas2, viajeId, viajesIds, indiceViaje),
                            err2 -> {
                                Log.w(TAG, "Sin reservas para viaje=" + viajeId);
                                buscarPasajerosPendientesEnViaje(viajesIds, indiceViaje + 1);
                            }
                    );
                }
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Paso 3: extraer pasajeros de la respuesta de reservas
    // ─────────────────────────────────────────────────────────────────────────

    private void procesarReservasParaCalificar(JSONArray reservas,
                                               int viajeId,
                                               List<Integer> viajesIds,
                                               int indiceViaje) {
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

                // Ignorar reservas canceladas
                String est = res.optString("estado", "").toUpperCase();
                if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;

                int    idPasajero  = -1;
                String nomPasajero = "";

                // Buscar el objeto pasajero en campos conocidos del JSON
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
                        for (String k : new String[]{"nombre", "nombreCompleto", "name", "nombres"}) {
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
                // Fallback: IDs directos en el objeto reserva
                if (idPasajero <= 0) {
                    for (String k : new String[]{"idUsuarios", "idUsuario", "idPasajero", "pasajeroId"}) {
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

                    // Mostrar card en el Home de inmediato (antes del BottomSheet)
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

    // ─────────────────────────────────────────────────────────────────────────
    //  Paso 4: BottomSheets encadenados, uno por pasajero
    //
    //  - CalificacionesManager verifica caché SharedPreferences
    //    (clave: "viaje_{id}_cal_{idCalificador}_to_{idCalificado}")
    //  - Si ya calificó → onYaCalifico() → quita card + salta al siguiente
    //  - Si no calificó → onDebeCalificar() → muestra bottom_sheet_calificar.xml
    //    a través de CalificacionController.mostrarBottomSheetCalificar()
    //  - Al confirmar → CalificacionesManager.enviarCalificacion() (POST /api/calificaciones)
    //    maneja 409 como éxito y guarda en caché para no repetir
    //  - Si cierra sin calificar → la card queda en el Home, avanza tras 3s
    // ─────────────────────────────────────────────────────────────────────────

    private void mostrarCalificacionesEncadenadas(List<Integer> ids,
                                                  List<String>  nombres,
                                                  int           idCalificador,
                                                  int           viajeId,
                                                  int           indice,
                                                  Runnable      onTodosListos) {
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

                    @Override
                    public void onDebeCalificar() {
                        Log.d(TAG, "Abriendo BottomSheet → viaje=" + viajeId
                                + " pasajero=" + idPasajero
                                + " [" + (indice + 1) + "/" + ids.size() + "]");

                        runOnUiThread(() ->
                                CalificacionController.mostrarBottomSheetCalificar(
                                        HomeConductor.this,
                                        viajeId,
                                        idPasajero,    // calificado  = pasajero
                                        nomPasajero,
                                        idCalificador, // calificador = conductor
                                        true,          // esConductor = true → CHIPS_PASAJERO
                                        (puntuacion, comentario) -> {
                                            Log.d(TAG, "Calificación enviada "
                                                    + puntuacion + "⭐ pasajero=" + idPasajero);
                                            // Quitar la card del Home al confirmar
                                            quitarPendienteDelLayout(viajeId, idPasajero);
                                            // Siguiente pasajero con pequeño delay
                                            new Handler(Looper.getMainLooper()).postDelayed(
                                                    () -> mostrarCalificacionesEncadenadas(
                                                            ids, nombres, idCalificador,
                                                            viajeId, siguiente, onTodosListos),
                                                    700);
                                        }
                                )
                        );

                        // Si cierra el sheet sin calificar → avanzar tras 3s
                        // La card queda en el Home para calificar después manualmente
                        new Handler(Looper.getMainLooper()).postDelayed(
                                () -> mostrarCalificacionesEncadenadas(
                                        ids, nombres, idCalificador,
                                        viajeId, siguiente, onTodosListos),
                                3000);
                    }

                    @Override
                    public void onYaCalifico(int puntuacion, String estrellas) {
                        Log.d(TAG, "Ya calificó pasajero=" + idPasajero + " " + estrellas);
                        // Asegurar que la card no aparezca en el Home
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
}