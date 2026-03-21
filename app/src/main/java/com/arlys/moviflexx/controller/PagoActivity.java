package com.arlys.moviflexx.controller;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.button.MaterialButton;

import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

public class PagoActivity extends AppCompatActivity {

    private static final String TAG        = "PagoActivity";
    private static final int    POLLING_MS = 5_000;

    // ── Extras de entrada ─────────────────────────────────────────────────────
    public static final String EXTRA_ID_VIAJE  = "ID_VIAJE";
    public static final String EXTRA_MONTO     = "MONTO";
    public static final String EXTRA_ORIGEN    = "ORIGEN";
    public static final String EXTRA_DESTINO   = "DESTINO";
    public static final String EXTRA_CONDUCTOR = "CONDUCTOR";
    public static final String EXTRA_ID_PAGO   = "ID_PAGO";

    // ── Vistas ────────────────────────────────────────────────────────────────
    private TextView         txtMonto, txtMetodoElegido;
    private TextView         txtEstadoPago, txtMensajeEspera;
    private TextView         txtErrorBanner;
    private MaterialButton   btnConfirmarPago, btnConfirmePague;
    private MaterialCardView cardDatosViaje, cardMetodo, cardEstado, cardConfirmacion;
    private View             checkIconPasajero, checkIconConductor;
    private TextView         txtCheckPasajero, txtCheckConductor;
    private View             loaderPago;

    // ── Botones de método de pago ─────────────────────────────────────────────
    private LinearLayout btnOpcionEfectivo;
    private LinearLayout btnOpcionTransferencia;
    private String       metodoSeleccionado = "efectivo";

    // ── Datos ─────────────────────────────────────────────────────────────────
    private int    idViaje;
    private double monto;
    private String origen, destino, conductor;
    private long   idPago         = -1;
    private String estadoPago     = "";
    private String tipoPagoActual = "";
    private SessionManager session;

    // ── Polling ───────────────────────────────────────────────────────────────
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private Runnable      pollingRunnable;
    private boolean       pollingActivo  = false;


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
        setContentView(R.layout.activity_pago);

        session   = new SessionManager(this);
        idViaje   = getIntent().getIntExtra(EXTRA_ID_VIAJE, 0);
        monto     = getIntent().getDoubleExtra(EXTRA_MONTO, 0);
        origen    = getIntent().getStringExtra(EXTRA_ORIGEN);
        destino   = getIntent().getStringExtra(EXTRA_DESTINO);
        conductor = getIntent().getStringExtra(EXTRA_CONDUCTOR);
        idPago    = getIntent().getLongExtra(EXTRA_ID_PAGO, -1);

        if (idViaje == 0) {
            Toast.makeText(this, "ID de viaje inválido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        poblarDatosViaje();
        configurarListeners();

        if (idPago > 0) cargarPagoExistente();
        else            verificarPagoExistente();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (idPago > 0 && !estadoEsFinal(estadoPago)) iniciarPolling();
    }

    @Override protected void onPause()   { super.onPause();   detenerPolling(); }
    @Override protected void onDestroy() { super.onDestroy(); detenerPolling(); }


    // =========================================================================
    //  BIND
    // =========================================================================

    private void bindViews() {
        txtMonto           = findViewById(R.id.txt_pago_monto);
        txtMetodoElegido   = findViewById(R.id.txt_metodo_elegido);
        txtEstadoPago      = findViewById(R.id.txt_estado_pago);
        txtMensajeEspera   = findViewById(R.id.txt_mensaje_espera);
        txtErrorBanner     = findViewById(R.id.txt_error_banner);
        btnConfirmarPago   = findViewById(R.id.btn_confirmar_pago);
        btnConfirmePague   = findViewById(R.id.btn_confirme_pague);
        cardDatosViaje     = findViewById(R.id.card_datos_viaje);
        cardMetodo         = findViewById(R.id.card_metodo_pago);
        cardEstado         = findViewById(R.id.card_estado_pago);
        cardConfirmacion   = findViewById(R.id.card_confirmacion);
        checkIconPasajero  = findViewById(R.id.check_icon_pasajero);
        checkIconConductor = findViewById(R.id.check_icon_conductor);
        txtCheckPasajero   = findViewById(R.id.txt_check_pasajero);
        txtCheckConductor  = findViewById(R.id.txt_check_conductor);
        loaderPago         = findViewById(R.id.loader_pago);
        btnOpcionEfectivo      = findViewById(R.id.btn_opcion_efectivo);
        btnOpcionTransferencia = findViewById(R.id.btn_opcion_transferencia);
    }


    // =========================================================================
    //  DATOS
    // =========================================================================

    private void poblarDatosViaje() {
        if (txtMonto != null) {
            NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "CO"));
            txtMonto.setText("$ " + nf.format(monto));
        }
        mostrarPantallaSeleccion();
    }


    // =========================================================================
    //  LISTENERS
    // =========================================================================

    private void configurarListeners() {
        View btnBack = findViewById(R.id.btn_back_pago);
        if (btnBack != null) btnBack.setOnClickListener(v -> onBackPressed());

        if (btnOpcionEfectivo != null)
            btnOpcionEfectivo.setOnClickListener(v -> seleccionarMetodo("efectivo"));

        if (btnOpcionTransferencia != null)
            btnOpcionTransferencia.setOnClickListener(v -> seleccionarMetodo("transferencia"));

        if (btnConfirmarPago != null)
            btnConfirmarPago.setOnClickListener(v -> {
                ocultarErrorBanner();
                crearPago();
            });

        if (btnConfirmePague != null)
            btnConfirmePague.setOnClickListener(v -> {
                ocultarErrorBanner();
                confirmarPasajero();
            });
    }


    // =========================================================================
    //  SELECCIÓN DE MÉTODO
    // =========================================================================

    private void seleccionarMetodo(String metodo) {
        metodoSeleccionado = metodo;
        if (btnOpcionEfectivo == null || btnOpcionTransferencia == null) return;

        if ("efectivo".equals(metodo)) {
            btnOpcionEfectivo.setBackgroundResource(R.drawable.bg_metodo_seleccionado);
            btnOpcionTransferencia.setBackgroundResource(R.drawable.bg_metodo_normal);
            if (txtMetodoElegido != null) {
                txtMetodoElegido.setText("💵  Pagarás en efectivo al conductor");
                txtMetodoElegido.setVisibility(View.VISIBLE);
            }
        } else {
            btnOpcionTransferencia.setBackgroundResource(R.drawable.bg_metodo_seleccionado);
            btnOpcionEfectivo.setBackgroundResource(R.drawable.bg_metodo_normal);
            if (txtMetodoElegido != null) {
                txtMetodoElegido.setText("📲  Pagarás por Nequi, Daviplata o transferencia");
                txtMetodoElegido.setVisibility(View.VISIBLE);
            }
        }
    }


    // =========================================================================
    //  VERIFICAR / CARGAR PAGO EXISTENTE
    // =========================================================================

    private void verificarPagoExistente() {
        mostrarLoader(true);
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.pagoDeUsuarioEnViaje((long) idViaje, (long) session.getIdUsuario()),
                response -> {
                    mostrarLoader(false);
                    long id = extraerIdPago(response);
                    if (id > 0) {
                        idPago         = id;
                        estadoPago     = response.optString("estado", "PENDIENTE");
                        tipoPagoActual = response.optString("tipoPago", "");
                        procesarEstadoPago(response);
                    } else {
                        mostrarPantallaSeleccion();
                    }
                },
                error -> {
                    mostrarLoader(false);
                    if (error != null && error.networkResponse != null
                            && error.networkResponse.statusCode == 404) {
                        mostrarPantallaSeleccion();
                    } else {
                        mostrarPantallaSeleccion();
                        mostrarErrorBanner("Sin conexión. Verifica tu red e intenta de nuevo.");
                    }
                }
        );
    }

    private void cargarPagoExistente() {
        mostrarLoader(true);
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.pagoPorId(idPago),
                response -> {
                    mostrarLoader(false);
                    estadoPago     = response.optString("estado", "PENDIENTE");
                    tipoPagoActual = response.optString("tipoPago", "");
                    procesarEstadoPago(response);
                },
                error -> {
                    mostrarLoader(false);
                    if (error != null && error.networkResponse != null
                            && error.networkResponse.statusCode == 404) {
                        idPago = -1;
                        mostrarPantallaSeleccion();
                    } else {
                        mostrarPantallaSeleccion();
                        mostrarErrorBanner("Error al cargar el pago. Intenta de nuevo.");
                    }
                }
        );
    }


    // =========================================================================
    //  CREAR PAGO — el pasajero siempre crea con su propio token
    // =========================================================================

    private void crearPago() {
        mostrarLoader(true);
        setBotonConfirmarHabilitado(false);

        String tipoPago = obtenerTipoSeleccionado();

        JSONObject body = new JSONObject();
        try {
            body.put("idViaje",               idViaje);
            body.put("idUsuario",             session.getIdUsuario());
            body.put("monto",                 monto);
            body.put("tipoPago",              tipoPago.toUpperCase());
            body.put("estado",                "PENDIENTE");
            body.put("confirmacionPasajero",  false);
            body.put("confirmacionConductor", false);
        } catch (Exception e) {
            Log.e(TAG, "crearPago JSON: " + e.getMessage());
            mostrarLoader(false);
            setBotonConfirmarHabilitado(true);
            return;
        }

        ConexionApi.getInstance(this).post(Constantes.PAGOS, body,
                response -> {
                    Log.d(TAG, "✅ Respuesta crear pago: " + response.toString());

                    long idNuevo = extraerIdPago(response);
                    Log.d(TAG, "✅ idPago extraído: " + idNuevo);

                    if (idNuevo > 0) {
                        idPago = idNuevo;
                        tipoPagoActual = tipoPago;
                        estadoPago = "PENDIENTE";
                        confirmarPasajeroAutomatico(tipoPago);
                    } else {
                        // El backend no devolvió id — intentar buscar el pago existente
                        Log.w(TAG, "⚠️ No se obtuvo idPago del POST, buscando pago existente...");
                        buscarYConfirmarPagoExistente(tipoPago);
                    }
                },
                error -> {
                    mostrarLoader(false);
                    setBotonConfirmarHabilitado(true);
                    if (error != null && error.networkResponse != null
                            && error.networkResponse.statusCode == 409) {
                        // Pago ya existe → buscarlo y confirmar
                        Log.d(TAG, "409 → pago ya existe, buscando para confirmar...");

                        // Intentar extraer idPago del body del error 409
                        try {
                            String bodyErr = new String(error.networkResponse.data, "UTF-8");
                            JSONObject jsonErr = new JSONObject(bodyErr);
                            long idExistente = extraerIdPago(jsonErr);
                            if (idExistente > 0) {
                                idPago = idExistente;
                                tipoPagoActual = tipoPago;
                                confirmarPasajeroAutomatico(tipoPago);
                                return;
                            }
                        } catch (Exception ignored) {}

                        // Si no pudo extraerlo del error, buscar en el endpoint
                        buscarYConfirmarPagoExistente(tipoPago);
                    } else {
                        mostrarErrorBanner(extraerMensajeError(error, "Error al registrar el pago"));
                    }
                }
        );
    }

    /**
     * Busca el pago del usuario para este viaje y luego confirma el lado del pasajero.
     * Se usa como fallback cuando el POST no devuelve idPago o devuelve 409.
     */
    private void buscarYConfirmarPagoExistente(String tipoPago) {
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.pagoDeUsuarioEnViaje((long) idViaje, (long) session.getIdUsuario()),
                response -> {
                    long id = extraerIdPago(response);
                    Log.d(TAG, "buscarYConfirmar → idPago=" + id + " resp=" + response);
                    if (id > 0) {
                        idPago = id;
                        tipoPagoActual = tipoPago;
                        boolean yaConfirmo = response.optBoolean("confirmacionPasajero", false);
                        if (yaConfirmo) {
                            // Ya confirmó → mostrar pantalla pendiente directamente
                            mostrarLoader(false);
                            setBotonConfirmarHabilitado(true);
                            boolean cc = response.optBoolean("confirmacionConductor", false);
                            estadoPago = response.optString("estado", "CONFIRMADO_PASAJERO");
                            mostrarPantallaPendiente(tipoPago, true, cc);
                            iniciarPolling();
                        } else {
                            confirmarPasajeroAutomatico(tipoPago);
                        }
                    } else {
                        mostrarLoader(false);
                        setBotonConfirmarHabilitado(true);
                        mostrarErrorBanner("No se pudo registrar el pago. Intenta de nuevo.");
                    }
                },
                error -> {
                    mostrarLoader(false);
                    setBotonConfirmarHabilitado(true);
                    mostrarErrorBanner("No se pudo verificar el pago. Intenta de nuevo.");
                }
        );
    }


    // =========================================================================
    //  CONFIRMAR PASAJERO — automático tras crear pago
    // =========================================================================

    private void confirmarPasajeroAutomatico(String tipoPago) {
        if (idPago <= 0) {
            mostrarLoader(false);
            setBotonConfirmarHabilitado(true);
            mostrarErrorBanner("No se obtuvo ID de pago. Intenta de nuevo.");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("confirmacionPasajero", true);
        } catch (Exception ignored) {}

        ConexionApi.getInstance(this).put(
                Constantes.pagoConfirmarPasajero(idPago), body,
                response -> {
                    mostrarLoader(false);
                    setBotonConfirmarHabilitado(true);
                    estadoPago     = response.optString("estado", "CONFIRMADO_PASAJERO");
                    tipoPagoActual = response.optString("tipoPago", tipoPago);
                    boolean cp     = response.optBoolean("confirmacionPasajero",  true);
                    boolean cc     = response.optBoolean("confirmacionConductor", false);

                    Log.d(TAG, "✅ Pasajero confirmó pago — estado=" + estadoPago);
                    Toast.makeText(this,
                            "✅ Pago confirmado — el conductor lo verá pronto",
                            Toast.LENGTH_SHORT).show();

                    mostrarPantallaPendiente(tipoPagoActual, cp, cc);
                    iniciarPolling();
                },
                error -> {
                    mostrarLoader(false);
                    setBotonConfirmarHabilitado(true);
                    int code = (error != null && error.networkResponse != null)
                            ? error.networkResponse.statusCode : 0;
                    Log.w(TAG, "confirmarPasajeroAutomatico error code=" + code);
                    if (code == 409) {
                        // Ya estaba confirmado → tratar como éxito
                        mostrarPantallaPendiente(tipoPago, true, false);
                        iniciarPolling();
                    } else {
                        // Pago creado pero confirmación falló → mostrar botón manual
                        mostrarPantallaPendiente(tipoPago, false, false);
                        iniciarPolling();
                        mostrarErrorBanner("Pago registrado. Toca 'Confirmar que pagué' para confirmar.");
                    }
                }
        );
    }


    // =========================================================================
    //  CONFIRMAR PASAJERO — manual (botón "Confirmar que pagué")
    // =========================================================================

    private void confirmarPasajero() {
        if (idPago <= 0) {
            mostrarErrorBanner("No hay un pago activo para confirmar");
            return;
        }
        mostrarLoader(true);
        setBotonConfirmePagueHabilitado(false);

        JSONObject body = new JSONObject();
        try {
            body.put("confirmacionPasajero", true);
        } catch (Exception e) {
            Log.e(TAG, "confirmarPasajero JSON: " + e.getMessage());
            mostrarLoader(false);
            setBotonConfirmePagueHabilitado(true);
            return;
        }

        ConexionApi.getInstance(this).put(
                Constantes.pagoConfirmarPasajero(idPago), body,
                response -> {
                    mostrarLoader(false);
                    estadoPago     = response.optString("estado", "CONFIRMADO_PASAJERO");
                    tipoPagoActual = response.optString("tipoPago", tipoPagoActual);
                    boolean cp     = response.optBoolean("confirmacionPasajero",  true);
                    boolean cc     = response.optBoolean("confirmacionConductor", false);

                    Toast.makeText(this,
                            "✅ Confirmado. Esperando que el conductor reciba el pago…",
                            Toast.LENGTH_SHORT).show();

                    mostrarPantallaPendiente(tipoPagoActual, cp, cc);
                    iniciarPolling();
                },
                error -> {
                    mostrarLoader(false);
                    int statusCode = (error != null && error.networkResponse != null)
                            ? error.networkResponse.statusCode : 0;
                    if (statusCode == 409) {
                        mostrarErrorBanner("Ya confirmaste este pago. Esperando al conductor…");
                        if (idPago > 0) cargarPagoExistente();
                    } else {
                        setBotonConfirmePagueHabilitado(true);
                        mostrarErrorBanner(extraerMensajeError(error, "Error al confirmar el pago"));
                    }
                }
        );
    }


    // =========================================================================
    //  POLLING — GET /api/pagos/{idPago} cada 5 s
    // =========================================================================

    private void iniciarPolling() {
        if (pollingActivo) return;
        pollingActivo   = true;
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!pollingActivo || idPago <= 0) {
                    pollingActivo = false;
                    return;
                }
                if (estadoEsFinal(estadoPago)) {
                    detenerPolling();
                    return;
                }
                ConexionApi.getInstance(PagoActivity.this).getObjectNoCache(
                        Constantes.pagoPorId(idPago),
                        PagoActivity.this::procesarEstadoPago,
                        error -> Log.w(TAG, "Polling error (se reintentará): " + error)
                );
                if (!estadoEsFinal(estadoPago)) {
                    pollingHandler.postDelayed(this, POLLING_MS);
                }
            }
        };
        pollingHandler.postDelayed(pollingRunnable, POLLING_MS);
    }

    private void detenerPolling() {
        pollingActivo = false;
        if (pollingRunnable != null) pollingHandler.removeCallbacks(pollingRunnable);
    }


    // =========================================================================
    //  PROCESAR RESPUESTA DEL SERVIDOR
    // =========================================================================

    private void procesarEstadoPago(JSONObject response) {
        if (response == null) return;
        estadoPago     = response.optString("estado", "pendiente").toUpperCase();
        tipoPagoActual = response.optString("tipoPago", tipoPagoActual);
        boolean cp     = response.optBoolean("confirmacionPasajero",  false);
        boolean cc     = response.optBoolean("confirmacionConductor", false);

        runOnUiThread(() -> {
            switch (estadoPago) {
                case "COMPLETADO":
                    detenerPolling();
                    mostrarPagoCompletado(tipoPagoActual);
                    // ← Igual que ViajesAdapter: esperar 2s y calificar al conductor
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        buscarConductorParaCalificar();
                    }, 2000);
                    break;

                case "CANCELADO":
                case "EXPIRADO":
                    detenerPolling();
                    mostrarPagoCancelado(estadoPago);
                    break;
                default:
                    mostrarPantallaPendiente(tipoPagoActual, cp, cc);
                    break;
            }
        });
    }

    private void buscarConductorParaCalificar() {
        int idPasajero = session.getIdUsuario();

        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viajeObj -> {
                    int idC = -1;
                    String nomC = "";

                    // Intentar extraer del objeto conductor
                    JSONObject co = viajeObj.optJSONObject("conductor");
                    if (co != null) {
                        for (String k : new String[]{"id", "idUsuarios", "idUsuario", "conductorId"}) {
                            int id = co.optInt(k, -1);
                            if (id > 0) { idC = id; break; }
                        }
                        for (String k : new String[]{"nombre", "nombreCompleto", "name"}) {
                            String n = co.optString(k, "");
                            if (!n.isEmpty() && !n.equals("null")) { nomC = n; break; }
                        }
                        // Buscar también en el objeto usuario dentro del conductor
                        if (idC <= 0) {
                            JSONObject u = co.optJSONObject("usuario");
                            if (u != null) {
                                for (String k : new String[]{"id", "idUsuarios", "idUsuario"}) {
                                    int id = u.optInt(k, -1);
                                    if (id > 0) { idC = id; break; }
                                }
                                if (nomC.isEmpty()) {
                                    for (String k : new String[]{"nombre", "nombreCompleto", "name"}) {
                                        String n = u.optString(k, "");
                                        if (!n.isEmpty() && !n.equals("null")) { nomC = n; break; }
                                    }
                                }
                            }
                        }
                    }

                    // Fallback: campos directos del viaje
                    if (idC <= 0) {
                        for (String k : new String[]{"idConductor", "conductorId", "idUsuarioConductor"}) {
                            int id = viajeObj.optInt(k, -1);
                            if (id > 0) { idC = id; break; }
                        }
                    }

                    // Fallback: vehiculo
                    if (idC <= 0) {
                        JSONObject veh = viajeObj.optJSONObject("vehiculo");
                        if (veh != null) {
                            for (String k : new String[]{"idUsuario", "idConductor", "conductorId"}) {
                                int id = veh.optInt(k, -1);
                                if (id > 0) { idC = id; break; }
                            }
                        }
                    }

                    Log.d(TAG, "buscarConductorParaCalificar → idC=" + idC + " nom=" + nomC);

                    if (idC <= 0) {
                        Log.w(TAG, "No se encontró conductor — no se puede calificar");
                        return;
                    }

                    final int fId = idC;
                    final String fNom = nomC.isEmpty() ? "el conductor" : nomC;

                    new Handler(Looper.getMainLooper()).post(() ->
                            abrirBottomSheetCalificacion(idViaje, idPasajero, fId, fNom)
                    );
                },
                err -> Log.w(TAG, "Error cargando viaje para calificación: " + err)
        );
    }

    private void abrirBottomSheetCalificacion(int viajeId, int idCalificador,
                                              int idCalificado, String nomCalificado) {
        if (isFinishing() || isDestroyed()) return;

        new com.arlys.moviflexx.model.Manager.CalificacionesManager(this)
                .verificarCalificacion(viajeId, idCalificador, idCalificado,
                        new com.arlys.moviflexx.model.Manager.CalificacionesManager
                                .OnVerificacionListener() {
                            @Override
                            public void onDebeCalificar() {
                                runOnUiThread(() ->
                                        com.arlys.moviflexx.controller.
                                                CalificacionController.mostrarBottomSheetCalificar(
                                                        PagoActivity.this,
                                                        viajeId,
                                                        idCalificado,
                                                        nomCalificado,
                                                        "",              // ← fotoCalificado
                                                        idCalificador,
                                                        false,
                                                        (p, c) -> Log.d(TAG, "Calificó: " + p + "⭐")
                                                )
                                );

                            }
                            @Override
                            public void onYaCalifico(int p, String e) {
                                Log.d(TAG, "Ya calificó al conductor");
                            }
                        });
    }

    // =========================================================================
    //  ESTADOS DE PANTALLA
    // =========================================================================

    private void mostrarPantallaSeleccion() {
        setVisible(cardDatosViaje,   true);
        setVisible(cardMetodo,       true);
        setVisible(btnConfirmarPago, true);
        setVisible(cardEstado,       false);
        setVisible(cardConfirmacion, false);
        setVisible(btnConfirmePague, false);

        if (!tipoPagoActual.isEmpty()) {
            seleccionarMetodo(tipoPagoActual.toLowerCase());
        } else {
            seleccionarMetodo("efectivo");
        }

        if (txtMetodoElegido != null) txtMetodoElegido.setVisibility(View.GONE);
        setBotonConfirmarHabilitado(true);
    }

    private void mostrarPantallaPendiente(String tipoPago, boolean cp, boolean cc) {
        setVisible(cardDatosViaje,   true);
        setVisible(cardMetodo,       false);
        setVisible(btnConfirmarPago, false);
        setVisible(cardEstado,       true);
        setVisible(cardConfirmacion, true);

        if (txtMetodoElegido != null && tipoPago != null && !tipoPago.isEmpty()) {
            String texto = tipoPago.equalsIgnoreCase("efectivo")
                    ? "💵  Pagaste en efectivo al conductor"
                    : "📲  Pagaste por Nequi, Daviplata o transferencia";
            txtMetodoElegido.setText(texto);
            txtMetodoElegido.setVisibility(View.VISIBLE);
        }

        if (txtEstadoPago != null) {
            if (cp && cc)  txtEstadoPago.setText("🏁 Verificando pago…");
            else if (cp)   txtEstadoPago.setText("⏳ El conductor aún no confirma la recepción");
            else           txtEstadoPago.setText("💳 Pago registrado — confirma que ya pagaste");
        }

        actualizarCheck(checkIconPasajero,  txtCheckPasajero,
                cp, "Tú confirmaste el pago",          "Tú — pendiente de confirmar");
        actualizarCheck(checkIconConductor, txtCheckConductor,
                cc, "Conductor confirmó la recepción", "Conductor — esperando confirmación");

        setVisible(btnConfirmePague, !cp);
        setBotonConfirmePagueHabilitado(!cp);

        if (txtMensajeEspera != null && tipoPago != null && !tipoPago.isEmpty()) {
            String label = Character.toUpperCase(tipoPago.charAt(0))
                    + tipoPago.substring(1).toLowerCase();
            txtMensajeEspera.setText("Método: " + label);
        }
    }

    private void mostrarPagoCompletado(String tipoPago) {
        ocultarErrorBanner();
        setVisible(cardDatosViaje,   true);
        setVisible(cardMetodo,       false);
        setVisible(btnConfirmarPago, false);
        setVisible(cardEstado,       true);
        setVisible(cardConfirmacion, true);
        setVisible(btnConfirmePague, false);

        if (txtMetodoElegido != null && tipoPago != null) {
            String texto = tipoPago.equalsIgnoreCase("efectivo")
                    ? "💵  Pagaste en efectivo al conductor"
                    : "📲  Pagaste por Nequi, Daviplata o transferencia";
            txtMetodoElegido.setText(texto);
            txtMetodoElegido.setVisibility(View.VISIBLE);
        }

        if (txtEstadoPago    != null) txtEstadoPago.setText("🎉 ¡Pago completado con éxito!");
        if (txtMensajeEspera != null) txtMensajeEspera.setText("Gracias por usar Moviflexx  •  " + tipoPago);

        actualizarCheck(checkIconPasajero,  txtCheckPasajero,  true, "Tú confirmaste el pago",          "");
        actualizarCheck(checkIconConductor, txtCheckConductor, true, "Conductor confirmó la recepción", "");

        if (cardEstado != null) cardEstado.setStrokeColor(0xFF4CAF50);
    }

    private void mostrarPagoCancelado(String estado) {
        ocultarErrorBanner();
        setVisible(cardEstado,       true);
        setVisible(btnConfirmePague, false);
        setVisible(cardMetodo,       false);
        setVisible(btnConfirmarPago, false);

        String etiqueta = estado.equalsIgnoreCase("EXPIRADO")
                ? "⏰ Pago expirado — vuelve a intentarlo"
                : "❌ Pago cancelado";

        if (txtEstadoPago != null) txtEstadoPago.setText(etiqueta);
        if (cardEstado    != null) cardEstado.setStrokeColor(0xFFEF5350);
    }


    // =========================================================================
    //  HELPERS — BANNER DE ERROR
    // =========================================================================

    private void mostrarErrorBanner(String mensaje) {
        if (txtErrorBanner == null) {
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
            return;
        }
        txtErrorBanner.setText(mensaje);
        txtErrorBanner.setVisibility(View.VISIBLE);
        txtErrorBanner.postDelayed(this::ocultarErrorBanner, 4_000);
    }

    private void ocultarErrorBanner() {
        if (txtErrorBanner != null) txtErrorBanner.setVisibility(View.GONE);
    }


    // =========================================================================
    //  HELPERS — VISTAS
    // =========================================================================

    private void actualizarCheck(View icon, TextView txt, boolean done, String si, String no) {
        if (icon != null)
            icon.setBackgroundResource(done ? R.drawable.bg_check_done : R.drawable.bg_check_pending);
        if (txt != null) {
            txt.setText(done ? si : no);
            txt.setTextColor(done ? 0xFF2E7D32 : 0xFF757575);
        }
    }

    private void setVisible(View v, boolean visible) {
        if (v != null) v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void mostrarLoader(boolean show) {
        if (loaderPago != null)
            loaderPago.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setBotonConfirmarHabilitado(boolean enabled) {
        if (btnConfirmarPago != null) {
            btnConfirmarPago.setEnabled(enabled);
            btnConfirmarPago.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    private void setBotonConfirmePagueHabilitado(boolean enabled) {
        if (btnConfirmePague != null) {
            btnConfirmePague.setEnabled(enabled);
            btnConfirmePague.setAlpha(enabled ? 1f : 0.5f);
        }
    }


    // =========================================================================
    //  HELPERS — LÓGICA
    // =========================================================================

    /**
     * Extrae el ID del pago de cualquier respuesta JSON del backend.
     * Prisma puede devolverlo como idPago, id, idpago o pago_id.
     */
    private long extraerIdPago(JSONObject obj) {
        if (obj == null) return -1;
        for (String k : new String[]{"idPago", "id", "idpago", "pago_id", "pagoId"}) {
            long val = obj.optLong(k, -1);
            if (val > 0) return val;
        }
        return -1;
    }

    private boolean estadoEsFinal(String e) {
        if (e == null) return false;
        String upper = e.toUpperCase();
        return upper.equals("COMPLETADO")
                || upper.equals("CANCELADO")
                || upper.equals("EXPIRADO");
    }

    private String obtenerTipoSeleccionado() {
        return metodoSeleccionado != null ? metodoSeleccionado : "efectivo";
    }

    private String extraerMensajeError(com.android.volley.VolleyError error, String defecto) {
        if (error == null || error.networkResponse == null) return defecto;
        try {
            String body = new String(error.networkResponse.data, "UTF-8");
            JSONObject json = new JSONObject(body);
            String msg = json.optString("error", json.optString("message", ""));
            return msg.isEmpty() ? defecto : msg;
        } catch (Exception ignored) {
            return defecto;
        }
    }
}