package com.arlys.moviflexx.controller;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.AnimUtils;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.SesionUsuario;
import com.arlys.moviflexx.model.ViajeAlertaManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class PublicarViaje extends BaseActivity {

    private static final String TAG               = "PublicarViaje";
    private static final int    MAX_DIAS_ADELANTE = 30;
    private static final int    CUPOS_MAXIMO      = 8;

    // ─── VISTAS ───────────────────────────────────────────────────────────────
    private TextInputEditText    editFechaHora;
    private TextInputEditText    editPrecio;
    private Spinner              spinnerCupos;
    private MaterialCardView     mainCard;
    private MaterialCardView     loaderContainer;
    private View                 overlayBackground;
    private MaterialButton       btnPublicar;
    private BottomNavigationView bottomNavigation;

    // Header
    private TextView txtHeaderTitulo;
    private View     chipEstadoHeader;

    // Resumen ruta / vehículo
    private TextView         txtDestinoInfo;
    private TextView         txtOrigenInfo;
    private TextView         txtVehiculoInfo;
    private TextView         txtConductorInfo;
    private TextView         txtPrecioSugerido;
    private MaterialCardView txtPrecioSugeridoCard;

    // Cards extra del XML
    private MaterialCardView cardViajeActivo;
    private MaterialCardView metadataCard;
    private MaterialCardView statsCard;

    // Viaje activo
    private TextView       txtViajeActivoRuta;
    private TextView       txtViajeActivoEstado;
    private TextView       txtViajeActivoCupos;
    private TextView       txtViajeActivoPrecio;
    private TextView       txtViajeActivoHora;
    private MaterialButton btnVerMapaViajeActivo;
    private MaterialButton btnFinalizarViajeActivo;

    // ─── SESIÓN ───────────────────────────────────────────────────────────────
    private SessionManager session;

    // ─── DATOS DEL INTENT ────────────────────────────────────────────────────
    private int    rutaId            = 0;
    private int    vehiculoIdInt     = -1;
    private int    conductorIdInt    = -1;
    private String destinoRuta       = "";
    private String origenRuta        = "";
    private double distanciaKm       = 0;
    private double duracionMin       = 0;
    private double fuelLitros        = 0;
    private double costoCombustible  = 0;
    private String tipoTransporte    = "driving";
    private int    indiceRuta        = 0;

    // ─── PRECIO CALCULADO ────────────────────────────────────────────────────
    private int precioCalculado = 0;

    // ─── FECHA ───────────────────────────────────────────────────────────────
    private final Calendar calendarioSalida = Calendar.getInstance();
    private boolean fechaSeleccionada = false;

    // ─── FECHA COMO STRING (para ViajeAlertaManager) ─────────────────────────
    private String fechaHoraFinal = "";

    /* ════════════════════════════════════════════════════════════════════════
       LIFECYCLE
    ════════════════════════════════════════════════════════════════════════ */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(
                android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_publicar_viaje);
        session = new SessionManager(this);
        validarSesion();
        obtenerDatosIntent();
        enlazarVistas();
        configurarUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Si el usuario sale sin publicar, no hay jobs que cancelar todavía.
        // Los jobs se crean solo al publicar exitosamente.
    }

    /* ════════════════════════════════════════════════════════════════════════
       VALIDAR SESIÓN
    ════════════════════════════════════════════════════════════════════════ */

    private void validarSesion() {
        SesionUsuario.init(this);
        if (!session.isLoggedIn()) { irAlLogin(); return; }
        conductorIdInt = session.getIdUsuario();
        vehiculoIdInt  = session.getVehiculoId();
        if (conductorIdInt <= 0 || vehiculoIdInt <= 0) {
            Toast.makeText(this,
                    "⚠️ Debes registrar un vehículo antes de publicar viajes.",
                    Toast.LENGTH_LONG).show();
            irAlLogin();
        }
    }

    /* ════════════════════════════════════════════════════════════════════════
       DATOS DEL INTENT
    ════════════════════════════════════════════════════════════════════════ */

    private void obtenerDatosIntent() {
        Intent i = getIntent();
        rutaId           = i.getIntExtra("ID_RUTA_CREADA", 0);
        destinoRuta      = i.getStringExtra("DESTINO_RUTA");
        origenRuta       = i.getStringExtra("ORIGEN_RUTA");
        distanciaKm      = i.getDoubleExtra("DISTANCIA_KM", 0);
        duracionMin      = i.getDoubleExtra("DURACION_MIN", 0);
        fuelLitros       = i.getDoubleExtra("FUEL_LITROS", 0);
        costoCombustible = i.getDoubleExtra("COSTO_COMBUSTIBLE", 0);
        indiceRuta       = i.getIntExtra("INDICE_RUTA", 0);
        String tipoIntent = i.getStringExtra("TIPO_TRANSPORTE");
        tipoTransporte = (tipoIntent != null && !tipoIntent.isEmpty()) ? tipoIntent : "driving";

        destinoRuta = limpiarTexto(destinoRuta, "Destino");
        origenRuta  = limpiarTexto(origenRuta,  "Origen");
        destinoRuta = capitalizarTexto(destinoRuta);
        origenRuta  = capitalizarTexto(origenRuta);

        Log.d(TAG, "Intent → rutaId=" + rutaId + " | origen=" + origenRuta
                + " | destino=" + destinoRuta + " | km=" + distanciaKm
                + " | combustible=$" + costoCombustible + " | indiceRuta=" + indiceRuta);

        if (rutaId == 0) {
            Toast.makeText(this, "⚠️ Ruta inválida. Vuelve a calcular.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /* ════════════════════════════════════════════════════════════════════════
       ENLAZAR VISTAS
    ════════════════════════════════════════════════════════════════════════ */

    private void enlazarVistas() {
        editFechaHora         = findViewById(R.id.edit_fecha_hora);
        editPrecio            = findViewById(R.id.edit_precio);
        spinnerCupos          = findViewById(R.id.spinnerCupo);
        mainCard              = findViewById(R.id.main_card);
        loaderContainer       = findViewById(R.id.loader_container);
        overlayBackground     = findViewById(R.id.overlay_background);
        btnPublicar           = findViewById(R.id.btn_publicar_viaje);
        bottomNavigation      = findViewById(R.id.bottom_navigation);

        txtHeaderTitulo  = findViewById(R.id.txt_header_titulo);
        chipEstadoHeader = findViewById(R.id.chip_estado_header);

        txtDestinoInfo        = findViewById(R.id.txt_destino_info);
        txtOrigenInfo         = findViewById(R.id.txt_origen_info);
        txtVehiculoInfo       = findViewById(R.id.txt_vehiculo_info);
        txtConductorInfo      = findViewById(R.id.txt_conductor_info);
        txtPrecioSugerido     = findViewById(R.id.txt_precio_sugerido);
        txtPrecioSugeridoCard = findViewById(R.id.txt_precio_sugerido_card);

        cardViajeActivo = findViewById(R.id.card_viaje_activo);
        metadataCard    = findViewById(R.id.metadata_card);
        statsCard       = findViewById(R.id.stats_card);

        txtViajeActivoRuta      = findViewById(R.id.txt_viaje_activo_ruta);
        txtViajeActivoEstado    = findViewById(R.id.txt_viaje_activo_estado);
        txtViajeActivoCupos     = findViewById(R.id.txt_viaje_activo_cupos);
        txtViajeActivoPrecio    = findViewById(R.id.txt_viaje_activo_precio);
        txtViajeActivoHora      = findViewById(R.id.txt_viaje_activo_hora);
        btnVerMapaViajeActivo   = findViewById(R.id.btn_ver_mapa_viaje_activo);
        btnFinalizarViajeActivo = findViewById(R.id.btn_finalizar_viaje_activo);
    }

    /* ════════════════════════════════════════════════════════════════════════
       CONFIGURAR UI
    ════════════════════════════════════════════════════════════════════════ */

    private void configurarUI() {
        txtDestinoInfo.setText(" " + destinoRuta);
        txtOrigenInfo.setText(" " + origenRuta);

        String vehiculoNombre = limpiarTexto(session.getVehiculoNombre(), "Vehículo");
        String vehiculoPlaca  = limpiarTexto(session.getVehiculoPlaca(),  "Sin placa");
        txtVehiculoInfo.setText(" " + vehiculoNombre + " • " + vehiculoPlaca);

        String nombreConductor = limpiarTexto(session.getNombre(), "Conductor");
        txtConductorInfo.setText(" Conductor: " + nombreConductor);

        configurarSpinnerConCapacidad();
        calcularYMostrarPrecio();
        configurarSelectorFechaHora();

        animateButton(btnPublicar, () -> {
            if (validarFormulario()) publicarViaje();
        });

        if (btnVerMapaViajeActivo != null)
            animateButton(btnVerMapaViajeActivo, () -> goTo(Mapa.class, Transition.SLIDE));
        if (btnFinalizarViajeActivo != null)
            animateButton(btnFinalizarViajeActivo, this::confirmarFinalizarViaje);

        configurarBottomNavigation();
        animarEntradaPantalla();
    }

    /* ════════════════════════════════════════════════════════════════════════
       SPINNER CUPOS
    ════════════════════════════════════════════════════════════════════════ */

    private void configurarSpinnerConCapacidad() {
        String capacidadStr = session.getVCapacidad();
        int capacidad = 4;
        if (capacidadStr != null && !capacidadStr.trim().isEmpty()) {
            try { capacidad = Integer.parseInt(capacidadStr.trim()); }
            catch (NumberFormatException e) { Log.w(TAG, "Capacidad inválida: " + capacidadStr); }
        }
        int maxCupos = Math.min(Math.max(capacidad, 1), CUPOS_MAXIMO);
        String[] opciones = new String[maxCupos];
        for (int i = 0; i < maxCupos; i++) opciones[i] = String.valueOf(i + 1);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, opciones);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCupos.setAdapter(adapter);
        spinnerCupos.setSelection(maxCupos - 1);
    }

    /* ════════════════════════════════════════════════════════════════════════
       CALCULAR Y MOSTRAR PRECIO
    ════════════════════════════════════════════════════════════════════════ */

    private void calcularYMostrarPrecio() {
        double precioDouble;
        String origenPrecio;

        if (costoCombustible > 0) {
            precioDouble = costoCombustible;
            origenPrecio = "ruta";
        } else if (distanciaKm > 0) {
            precioDouble = distanciaKm * 700.0;
            origenPrecio = "km";
        } else {
            precioDouble = 5000.0;
            origenPrecio = "base";
        }

        precioDouble    = Math.ceil(precioDouble / 100.0) * 100.0;
        precioCalculado = Math.max((int) Math.round(precioDouble), 1);

        if (editPrecio != null) {
            editPrecio.setText(String.format(Locale.getDefault(), "%,d", precioCalculado));
            editPrecio.setFocusable(false);
            editPrecio.setFocusableInTouchMode(false);
            editPrecio.setClickable(false);
            editPrecio.setLongClickable(false);
            editPrecio.setCursorVisible(false);
            editPrecio.setKeyListener(null);
        }

        if (txtPrecioSugerido != null) {
            StringBuilder detalle = new StringBuilder();
            switch (origenPrecio) {
                case "ruta":
                    detalle.append(String.format(Locale.getDefault(),
                            "⛽ $%,d COP combustible", (int) Math.round(costoCombustible)));
                    break;
                case "km":  detalle.append(" Estimado: $700/km"); break;
                default:    detalle.append("💡 Precio mínimo base");
            }
            if (distanciaKm > 0)
                detalle.append(String.format(Locale.getDefault(), "  ·   %.1f km", distanciaKm));
            if (duracionMin > 0)
                detalle.append(String.format(Locale.getDefault(), "  ·  ⏱ %.0f min", duracionMin));

            txtPrecioSugerido.setText(detalle.toString());
            txtPrecioSugerido.setVisibility(View.VISIBLE);
            if (txtPrecioSugeridoCard != null) txtPrecioSugeridoCard.setVisibility(View.VISIBLE);
        }
    }

    /* ════════════════════════════════════════════════════════════════════════
       SELECTOR FECHA / HORA
    ════════════════════════════════════════════════════════════════════════ */

    private void configurarSelectorFechaHora() {
        actualizarCampoFechaHora();
        editFechaHora.setFocusable(false);
        editFechaHora.setClickable(true);
        editFechaHora.setOnClickListener(v -> {
            AnimUtils.tapPulse(v);
            v.postDelayed(this::mostrarDialogoFecha, 120);
        });
    }

    private void mostrarDialogoFecha() {
        Calendar hoy = Calendar.getInstance();
        new DatePickerDialog(this,
                (view, year, month, day) -> {
                    calendarioSalida.set(Calendar.YEAR,         year);
                    calendarioSalida.set(Calendar.MONTH,        month);
                    calendarioSalida.set(Calendar.DAY_OF_MONTH, day);
                    mostrarDialogoHora();
                },
                hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH), hoy.get(Calendar.DAY_OF_MONTH)
        ) {{
            getDatePicker().setMinDate(hoy.getTimeInMillis());
            Calendar maxFecha = Calendar.getInstance();
            maxFecha.add(Calendar.DAY_OF_YEAR, MAX_DIAS_ADELANTE);
            getDatePicker().setMaxDate(maxFecha.getTimeInMillis());
        }}.show();
    }

    private void mostrarDialogoHora() {
        Calendar hoy = Calendar.getInstance();
        new TimePickerDialog(this,
                (view, hora, minuto) -> {
                    calendarioSalida.set(Calendar.HOUR_OF_DAY, hora);
                    calendarioSalida.set(Calendar.MINUTE,      minuto);
                    calendarioSalida.set(Calendar.SECOND,      0);
                    fechaSeleccionada = true;
                    actualizarCampoFechaHora();
                    editFechaHora.setError(null);
                    AnimUtils.bounceIn(editFechaHora, 0);
                },
                hoy.get(Calendar.HOUR_OF_DAY), hoy.get(Calendar.MINUTE), false
        ).show();
    }

    private void actualizarCampoFechaHora() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        fechaHoraFinal = sdf.format(calendarioSalida.getTime());
        editFechaHora.setText(fechaHoraFinal);
    }

    /* ════════════════════════════════════════════════════════════════════════
       VALIDACIONES
    ════════════════════════════════════════════════════════════════════════ */

    private boolean validarFormulario() {
        if (!fechaSeleccionada) {
            editFechaHora.setError("Selecciona fecha y hora de salida");
            AnimUtils.shake(editFechaHora);
            Toast.makeText(this, "Selecciona la fecha y hora de salida", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (calendarioSalida.getTimeInMillis() <= System.currentTimeMillis()) {
            editFechaHora.setError("La hora de salida debe ser futura");
            AnimUtils.shake(editFechaHora);
            Toast.makeText(this, "La hora de salida debe ser futura", Toast.LENGTH_SHORT).show();
            return false;
        }
        Calendar maxFecha = Calendar.getInstance();
        maxFecha.add(Calendar.DAY_OF_YEAR, MAX_DIAS_ADELANTE);
        if (calendarioSalida.after(maxFecha)) {
            editFechaHora.setError("La salida no puede ser en más de " + MAX_DIAS_ADELANTE + " días");
            AnimUtils.shake(editFechaHora);
            Toast.makeText(this,
                    "La fecha no puede estar a más de " + MAX_DIAS_ADELANTE + " días",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if (precioCalculado <= 0) {
            AnimUtils.shake(editPrecio);
            Toast.makeText(this, "Error en el precio de la ruta", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /* ════════════════════════════════════════════════════════════════════════
       PUBLICAR VIAJE  ←  AQUÍ ESTÁ LA INTEGRACIÓN CON ViajeAlertaManager
    ════════════════════════════════════════════════════════════════════════ */

    private void publicarViaje() {
        String token = SesionUsuario.getToken();
        if (token == null || token.isEmpty()) { irAlLogin(); return; }

        mostrarLoader(true);

        try {
            int    cupos    = Integer.parseInt(spinnerCupos.getSelectedItem().toString());
            String fechaHora = fechaHoraFinal.isEmpty()
                    ? editFechaHora.getText().toString().trim()
                    : fechaHoraFinal;

            JSONObject body = new JSONObject();
            body.put("idRuta",           rutaId);
            body.put("idVehiculos",      vehiculoIdInt);
            body.put("fechaHoraSalida",  fechaHora);
            body.put("cuposTotales",     cupos);
            body.put("cuposDisponibles", cupos);
            body.put("precio",           precioCalculado);
            body.put("estado",           "DISPONIBLE");

            final String fechaHoraParaAlertas = fechaHora;

            ConexionApi.getInstance(this).post(Constantes.VIAJES, body,

                    // ── ÉXITO ──────────────────────────────────────────────
                    response -> {
                        mostrarLoader(false);

                        // Extraer el id del viaje recién creado
                        int idViajeCreado = response.optInt("idViajes",
                                response.optInt("id",
                                        response.optInt("idViaje", 0)));

                        Log.d(TAG, "Viaje publicado. idViaje=" + idViajeCreado
                                + " | fechaSalida=" + fechaHoraParaAlertas);

                        // ── PROGRAMAR ALERTAS Y INICIO AUTOMÁTICO ──────────
                        // Si el servidor devolvió el id del viaje, programamos:
                        //   · alerta 5 min antes  → modal + notif local
                        //   · alerta 1 min antes  → modal + notif local
                        //   · inicio automático   → POST /viajes/{id}/iniciar
                        //     + notificación push a los pasajeros
                        if (idViajeCreado > 0) {
                            ViajeAlertaManager.programarInicioAutomatico(
                                    PublicarViaje.this,
                                    idViajeCreado,
                                    fechaHoraParaAlertas,
                                    response
                            );
                            Log.d(TAG, "Alertas programadas para viaje " + idViajeCreado);
                        } else {
                            // El servidor no devolvió id: las alertas no se pueden
                            // programar en esta sesión, pero el viaje sí se creó.
                            Log.w(TAG, "Viaje creado pero sin id en la respuesta. "
                                    + "Alertas no programadas.");
                        }

                        Toast.makeText(PublicarViaje.this,
                                "¡Viaje publicado correctamente!", Toast.LENGTH_LONG).show();
                        goTo(HomeConductor.class, Transition.SLIDE, true);
                    },

                    // ── ERROR ──────────────────────────────────────────────
                    error -> {
                        mostrarLoader(false);
                        AnimUtils.shake(mainCard);
                        String msg = "Error al publicar el viaje";
                        if (error != null && error.networkResponse != null) {
                            int status = error.networkResponse.statusCode;
                            try {
                                String errorBody = new String(error.networkResponse.data, "UTF-8");
                                Log.e(TAG, "Error HTTP " + status + " → " + errorBody);
                            } catch (Exception ignored) {}
                            switch (status) {
                                case 400: msg = "Datos inválidos (400)";                    break;
                                case 401: msg = "Sesión expirada (401)";                    break;
                                case 403: msg = "Sin permisos (403)";                       break;
                                case 404: msg = "Ruta no encontrada (404)";                 break;
                                case 409: msg = "Ya existe un viaje con estos datos (409)"; break;
                                case 422: msg = "Validación fallida (422)";                 break;
                                case 429: msg = "Demasiadas solicitudes (429)";             break;
                                case 500: msg = "Error en el servidor (500)";               break;
                            }
                        }
                        Toast.makeText(PublicarViaje.this, msg, Toast.LENGTH_LONG).show();
                    }
            );

        } catch (Exception e) {
            mostrarLoader(false);
            Log.e(TAG, "Error construyendo JSON: " + e.getMessage());
            Toast.makeText(this, "Error inesperado. Intenta de nuevo.", Toast.LENGTH_SHORT).show();
        }
    }

    /* ════════════════════════════════════════════════════════════════════════
       VIAJE ACTIVO
    ════════════════════════════════════════════════════════════════════════ */

    /**
     * Muestra card_viaje_activo y oculta el formulario.
     * Llámalo cuando detectes que el conductor ya tiene un viaje en curso.
     */
    public void mostrarCardViajeActivo(String ruta, String estado,
                                       String cupos, String precio, String hora) {
        if (cardViajeActivo == null) return;
        if (txtViajeActivoRuta   != null) txtViajeActivoRuta.setText(ruta);
        if (txtViajeActivoEstado != null) txtViajeActivoEstado.setText("● " + estado.toUpperCase());
        if (txtViajeActivoCupos  != null) txtViajeActivoCupos.setText(cupos);
        if (txtViajeActivoPrecio != null) txtViajeActivoPrecio.setText(precio);
        if (txtViajeActivoHora   != null) txtViajeActivoHora.setText(hora);

        if (mainCard != null && mainCard.getVisibility() == View.VISIBLE)
            AnimUtils.fadeOut(mainCard, () -> mainCard.setVisibility(View.GONE));

        cardViajeActivo.setVisibility(View.VISIBLE);
        AnimUtils.bounceIn(cardViajeActivo, 100);
    }

    private void confirmarFinalizarViaje() {
        // Lógica de finalizar viaje aquí.
        // Al terminar puedes restaurar el formulario así:
        //   AnimUtils.fadeOut(cardViajeActivo, () -> cardViajeActivo.setVisibility(View.GONE));
        //   mainCard.setVisibility(View.VISIBLE);
        //   AnimUtils.fadeSlideIn(mainCard, 0);
    }

    /* ════════════════════════════════════════════════════════════════════════
       LOADER
    ════════════════════════════════════════════════════════════════════════ */

    private void mostrarLoader(boolean mostrar) {
        if (mostrar) {
            overlayBackground.setVisibility(View.VISIBLE);
            AnimUtils.fadeIn(overlayBackground, 0);
            loaderContainer.setVisibility(View.VISIBLE);
            AnimUtils.bounceIn(loaderContainer, 80);
        } else {
            AnimUtils.fadeOut(overlayBackground, () -> overlayBackground.setVisibility(View.GONE));
            AnimUtils.fadeOut(loaderContainer,   () -> loaderContainer.setVisibility(View.GONE));
        }
        btnPublicar.setEnabled(!mostrar);
        btnPublicar.setText(mostrar ? "Publicando..." : "PUBLICAR VIAJE EN POPAYÁN");
        btnPublicar.setAlpha(mostrar ? 0.6f : 1.0f);
    }

    /* ════════════════════════════════════════════════════════════════════════
       BOTTOM NAVIGATION
    ════════════════════════════════════════════════════════════════════════ */

    private void configurarBottomNavigation() {
        setupNavBar(bottomNavigation, R.id.nav_mis_viajes);
    }

    /* ════════════════════════════════════════════════════════════════════════
       NAVEGACIÓN
    ════════════════════════════════════════════════════════════════════════ */

    private void irAlLogin() {
        SesionUsuario.cerrarSesion();
        goTo(Login.class, Transition.FADE, true);
    }

    /* ════════════════════════════════════════════════════════════════════════
       ANIMACIONES DE ENTRADA
    ════════════════════════════════════════════════════════════════════════ */

    private void animarEntradaPantalla() {
        if (txtHeaderTitulo != null) {
            txtHeaderTitulo.setAlpha(0f);
            txtHeaderTitulo.setTranslationY(-20f);
            txtHeaderTitulo.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(350).setStartDelay(80)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        if (chipEstadoHeader != null) AnimUtils.bounceIn(chipEstadoHeader, 200);

        if (mainCard != null) {
            mainCard.setAlpha(0f);
            mainCard.setTranslationY(50f);
            mainCard.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(420).setStartDelay(120)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
        }

        AnimUtils.fadeSlideIn(txtVehiculoInfo,  220);
        AnimUtils.fadeSlideIn(txtConductorInfo, 280);
        AnimUtils.fadeSlideIn(txtOrigenInfo,    340);
        AnimUtils.fadeSlideIn(txtDestinoInfo,   390);
        AnimUtils.fadeSlideIn(editFechaHora,    440);

        if (txtPrecioSugeridoCard != null) AnimUtils.bounceIn(txtPrecioSugeridoCard, 500);
        if (metadataCard != null) AnimUtils.fadeSlideIn(metadataCard, 560);
        if (statsCard    != null) AnimUtils.fadeSlideIn(statsCard,    640);

        AnimUtils.fadeSlideIn(btnPublicar, 700);
    }

    /* ════════════════════════════════════════════════════════════════════════
       HELPERS
    ════════════════════════════════════════════════════════════════════════ */

    private String limpiarTexto(String valor, String fallback) {
        if (valor == null || valor.trim().isEmpty()) return fallback;
        String v = valor.trim();
        if (v.length() > 30 && !v.contains(" ")) return fallback;
        return v;
    }

    private String capitalizarTexto(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        String[] palabras = texto.trim().toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : palabras) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) sb.append(p.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}