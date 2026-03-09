package com.arlys.moviflexx.controller;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.RadioGroup;
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

/**
 * PagoActivity — Flujo completo de pago pasajero ↔ conductor.
 *
 * ─ Cómo lanzar desde DetalleViajeActivity ────────────────────────────────────
 *
 *   Intent intent = new Intent(this, PagoActivity.class);
 *   intent.putExtra(PagoActivity.EXTRA_ID_VIAJE,   viajeId);
 *   intent.putExtra(PagoActivity.EXTRA_MONTO,      precioViaje);
 *   intent.putExtra(PagoActivity.EXTRA_ORIGEN,     origenActual);
 *   intent.putExtra(PagoActivity.EXTRA_DESTINO,    destinoActual);
 *   intent.putExtra(PagoActivity.EXTRA_CONDUCTOR,  nombreConductorViaje);
 *   startActivity(intent);
 *
 * ─ Flujo ─────────────────────────────────────────────────────────────────────
 *   1. Pasajero elige Efectivo / Transferencia  → POST /api/pagos
 *   2. Pasajero pulsa "CONFIRMÉ QUE PAGUÉ"      → PUT  /api/pagos/confirmarPasajero/{id}
 *   3. Conductor pulsa "PAGO RECIBIDO"          → PUT  /api/pagos/confirmarConductor/{id}
 *   4. Polling cada 5 s detecta estado = completado → muestra pantalla de éxito
 */
public class PagoActivity extends AppCompatActivity {

    private static final String TAG        = "PagoActivity";
    private static final int    POLLING_MS = 5000;

    // ── Extras de entrada ─────────────────────────────────────────────────────
    public static final String EXTRA_ID_VIAJE  = "ID_VIAJE";   // int    → viajeId
    public static final String EXTRA_MONTO     = "MONTO";      // double → precioViaje
    public static final String EXTRA_ORIGEN    = "ORIGEN";     // String → origenActual
    public static final String EXTRA_DESTINO   = "DESTINO";    // String → destinoActual
    public static final String EXTRA_CONDUCTOR = "CONDUCTOR";  // String → nombreConductorViaje
    public static final String EXTRA_ID_PAGO   = "ID_PAGO";    // long   (opcional, si ya existe)

    // ── Vistas ────────────────────────────────────────────────────────────────
    private TextView         txtMonto, txtMetodoElegido;
    private TextView         txtEstadoPago, txtMensajeEspera;
    private RadioGroup       rgMetodoPago;
    private MaterialButton   btnConfirmarPago, btnConfirmePague;
    private MaterialCardView cardDatosViaje, cardMetodo, cardEstado, cardConfirmacion;
    private View             checkIconPasajero, checkIconConductor;
    private TextView         txtCheckPasajero, txtCheckConductor;
    private View             loaderPago;

    // ── Datos ─────────────────────────────────────────────────────────────────
    private int    idViaje;
    private double monto;
    private String origen, destino, conductor;
    private long   idPago     = -1;
    private String estadoPago = "";
    private SessionManager session;

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
            finish(); return;
        }

        bindViews();
        poblarDatosViaje();
        configurarListeners();

        if (idPago > 0) cargarPagoExistente();
        else             verificarPagoExistente();
    }

    @Override protected void onResume()  { super.onResume();  if (idPago > 0 && !estadoEsFinal(estadoPago)) iniciarPolling(); }
    @Override protected void onPause()   { super.onPause();   detenerPolling(); }
    @Override protected void onDestroy() { super.onDestroy(); detenerPolling(); }

    // =========================================================================
    //  BIND
    // =========================================================================

    private void bindViews() {
        txtMonto           = findViewById(R.id.txt_pago_monto);
        txtMetodoElegido   = findViewById(R.id.txt_metodo_elegido);   // ← nuevo
        txtEstadoPago      = findViewById(R.id.txt_estado_pago);
        txtMensajeEspera   = findViewById(R.id.txt_mensaje_espera);
        rgMetodoPago       = findViewById(R.id.rg_metodo_pago);
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

        // ── Listener método de pago: actualiza el resumen en tiempo real ──────
        if (rgMetodoPago != null) {
            rgMetodoPago.setOnCheckedChangeListener((group, checkedId) -> {
                if (txtMetodoElegido == null) return;
                if (checkedId == R.id.rb_efectivo) {
                    txtMetodoElegido.setText("💵  Pagarás en efectivo al conductor");
                    txtMetodoElegido.setVisibility(View.VISIBLE);
                } else if (checkedId == R.id.rb_transferencia) {
                    txtMetodoElegido.setText("📲  Pagarás por Nequi, Daviplata o transferencia");
                    txtMetodoElegido.setVisibility(View.VISIBLE);
                }
            });
        }

        if (btnConfirmarPago != null) {
            btnConfirmarPago.setOnClickListener(v -> {
                if (rgMetodoPago == null || rgMetodoPago.getCheckedRadioButtonId() == -1) {
                    Toast.makeText(this, "Selecciona un método de pago", Toast.LENGTH_SHORT).show();
                    return;
                }
                crearPago();
            });
        }

        if (btnConfirmePague != null)
            btnConfirmePague.setOnClickListener(v -> confirmarPasajero());
    }

    // =========================================================================
    //  VERIFICAR PAGO EXISTENTE
    //  GET /api/pagos/viaje/{idViaje}/usuario/{idUsuario}
    // =========================================================================

    private void verificarPagoExistente() {
        mostrarLoader(true);
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.pagoDeUsuarioEnViaje(idViaje, session.getIdUsuario()),
                response -> {
                    mostrarLoader(false);
                    long id = response.optLong("idPago", response.optLong("id", -1));
                    if (id > 0) { idPago = id; estadoPago = response.optString("estado", "pendiente"); procesarEstadoPago(response); }
                    else          mostrarPantallaSeleccion();
                },
                error -> { mostrarLoader(false); mostrarPantallaSeleccion(); } // 404 = no existe
        );
    }

    private void cargarPagoExistente() {
        mostrarLoader(true);
        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.pagoPorId(idPago),
                response -> { mostrarLoader(false); estadoPago = response.optString("estado","pendiente"); procesarEstadoPago(response); },
                error   -> { mostrarLoader(false); mostrarPantallaSeleccion(); }
        );
    }

    // =========================================================================
    //  POST /api/pagos
    // =========================================================================

    private void crearPago() {
        mostrarLoader(true);
        if (btnConfirmarPago != null) btnConfirmarPago.setEnabled(false);

        String tipoPago = (rgMetodoPago.getCheckedRadioButtonId() == R.id.rb_efectivo)
                ? "efectivo" : "transferencia";

        JSONObject body = new JSONObject();
        try {
            body.put("idUsuario", session.getIdUsuario());
            body.put("idViaje",   idViaje);
            body.put("monto",     monto);
            body.put("tipoPago",  tipoPago);
            body.put("estado",    "pendiente");
        } catch (Exception e) {
            Log.e(TAG, "crearPago: " + e.getMessage());
            mostrarLoader(false);
            if (btnConfirmarPago != null) btnConfirmarPago.setEnabled(true);
            return;
        }

        ConexionApi.getInstance(this).post(Constantes.PAGOS, body,
                response -> {
                    mostrarLoader(false);
                    idPago     = response.optLong("idPago", response.optLong("id", -1));
                    estadoPago = "pendiente";
                    Toast.makeText(this, "Pago registrado", Toast.LENGTH_SHORT).show();
                    mostrarPantallaPendiente(tipoPago, false, false);
                    iniciarPolling();
                },
                error -> {
                    mostrarLoader(false);
                    if (btnConfirmarPago != null) btnConfirmarPago.setEnabled(true);
                    String msg = "Error al registrar el pago";
                    if (error != null && error.networkResponse != null) {
                        try {
                            String bs = new String(error.networkResponse.data, "UTF-8");
                            String m = new JSONObject(bs).optString("message",
                                    new JSONObject(bs).optString("error", ""));
                            if (!m.isEmpty()) msg = m;
                        } catch (Exception ignored) {}
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
        );
    }

    // =========================================================================
    //  PUT /api/pagos/confirmarPasajero/{idPago}
    // =========================================================================

    private void confirmarPasajero() {
        if (idPago <= 0) { Toast.makeText(this, "No hay un pago activo", Toast.LENGTH_SHORT).show(); return; }
        mostrarLoader(true);
        if (btnConfirmePague != null) btnConfirmePague.setEnabled(false);

        ConexionApi.getInstance(this).put(
                Constantes.pagoConfirmarPasajero(idPago), new JSONObject(),
                response -> {
                    mostrarLoader(false);
                    estadoPago = response.optString("estado", "confirmado_pasajero");
                    boolean cp = response.optBoolean("confirmacionPasajero", true);
                    boolean cc = response.optBoolean("confirmacionConductor", false);
                    String  tp = response.optString("tipoPago", obtenerTipoSeleccionado());
                    Toast.makeText(this, "¡Confirmado! Esperando al conductor…", Toast.LENGTH_SHORT).show();
                    mostrarPantallaPendiente(tp, cp, cc);
                    iniciarPolling();
                },
                error -> {
                    mostrarLoader(false);
                    if (btnConfirmePague != null) btnConfirmePague.setEnabled(true);
                    Toast.makeText(this, "Error al confirmar pago", Toast.LENGTH_LONG).show();
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
            @Override public void run() {
                if (!pollingActivo || idPago <= 0) return;
                ConexionApi.getInstance(PagoActivity.this).getObjectNoCache(
                        Constantes.pagoPorId(idPago),
                        PagoActivity.this::procesarEstadoPago,
                        error -> Log.w(TAG, "Polling error: " + error)
                );
                if (!estadoEsFinal(estadoPago))
                    pollingHandler.postDelayed(this, POLLING_MS);
            }
        };
        pollingHandler.postDelayed(pollingRunnable, POLLING_MS);
    }

    private void detenerPolling() {
        pollingActivo = false;
        if (pollingRunnable != null) pollingHandler.removeCallbacks(pollingRunnable);
    }

    private void procesarEstadoPago(JSONObject response) {
        if (response == null) return;
        estadoPago  = response.optString("estado", "pendiente");
        boolean cp  = response.optBoolean("confirmacionPasajero", false);
        boolean cc  = response.optBoolean("confirmacionConductor", false);
        String  tp  = response.optString("tipoPago", "efectivo");

        runOnUiThread(() -> {
            switch (estadoPago.toLowerCase()) {
                case "completado": detenerPolling(); mostrarPagoCompletado(tp); break;
                case "cancelado":  detenerPolling(); mostrarPagoCancelado();    break;
                default:                             mostrarPantallaPendiente(tp, cp, cc); break;
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
        // Ocultar resumen de método si se regresa a selección
        if (txtMetodoElegido != null) txtMetodoElegido.setVisibility(View.GONE);
    }

    private void mostrarPantallaPendiente(String tipoPago, boolean cp, boolean cc) {
        setVisible(cardDatosViaje,   true);
        setVisible(cardMetodo,       false);
        setVisible(btnConfirmarPago, false);
        setVisible(cardEstado,       true);
        setVisible(cardConfirmacion, true);

        // Mostrar el método elegido en el resumen
        if (txtMetodoElegido != null && tipoPago != null && !tipoPago.isEmpty()) {
            String icono = tipoPago.equalsIgnoreCase("efectivo") ? "💵" : "📲";
            String texto = tipoPago.equalsIgnoreCase("efectivo")
                    ? "💵  Pagaste en efectivo al conductor"
                    : "📲  Pagaste por Nequi, Daviplata o transferencia";
            txtMetodoElegido.setText(texto);
            txtMetodoElegido.setVisibility(View.VISIBLE);
        }

        if (txtEstadoPago != null) {
            if      (cp && cc) txtEstadoPago.setText("🏁 Verificando pago…");
            else if (cp)       txtEstadoPago.setText("⏳ Esperando confirmación del conductor");
            else                txtEstadoPago.setText("✅ Pago registrado — confirma que pagaste");
        }

        actualizarCheck(checkIconPasajero,  txtCheckPasajero,  cp, "Tú confirmaste el pago",          "Tú — pendiente de confirmar");
        actualizarCheck(checkIconConductor, txtCheckConductor, cc, "Conductor confirmó la recepción", "Conductor — esperando confirmación");

        setVisible(btnConfirmePague, !cp);
        if (btnConfirmePague != null) { btnConfirmePague.setEnabled(!cp); btnConfirmePague.setAlpha(!cp ? 1f : 0.5f); }

        if (txtMensajeEspera != null && tipoPago != null && !tipoPago.isEmpty()) {
            String t = Character.toUpperCase(tipoPago.charAt(0)) + tipoPago.substring(1);
            txtMensajeEspera.setText("Método elegido: " + t);
        }
    }

    private void mostrarPagoCompletado(String tipoPago) {
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

        if (txtEstadoPago    != null) txtEstadoPago.setText("🎉 ¡Pago completado!");
        if (txtMensajeEspera != null) txtMensajeEspera.setText("Gracias por usar MoviFlex  •  " + tipoPago);

        actualizarCheck(checkIconPasajero,  txtCheckPasajero,  true, "Tú confirmaste el pago",          "");
        actualizarCheck(checkIconConductor, txtCheckConductor, true, "Conductor confirmó la recepción", "");
        if (cardEstado != null) cardEstado.setStrokeColor(0xFF4CAF50);
    }

    private void mostrarPagoCancelado() {
        setVisible(cardEstado, true);
        if (txtEstadoPago != null) txtEstadoPago.setText("❌ Pago cancelado");
        if (cardEstado    != null) cardEstado.setStrokeColor(0xFFEF5350);
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================

    private void actualizarCheck(View icon, TextView txt, boolean done, String si, String no) {
        if (icon != null) icon.setBackgroundResource(done ? R.drawable.bg_check_done : R.drawable.bg_check_pending);
        if (txt  != null) { txt.setText(done ? si : no); txt.setTextColor(done ? 0xFF2E7D32 : 0xFF757575); }
    }

    private void setVisible(View v, boolean visible) {
        if (v != null) v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void mostrarLoader(boolean show) {
        if (loaderPago != null) loaderPago.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private boolean estadoEsFinal(String e) {
        return e != null && (e.equalsIgnoreCase("completado") || e.equalsIgnoreCase("cancelado"));
    }

    private String obtenerTipoSeleccionado() {
        if (rgMetodoPago == null) return "efectivo";
        return rgMetodoPago.getCheckedRadioButtonId() == R.id.rb_transferencia ? "transferencia" : "efectivo";
    }
}