package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;


public class ResumenViajeActivity extends AppCompatActivity {

    private static final String TAG        = "ResumenViaje";
    private static final int    POLLING_MS = 8_000;

    // ── Extras ────────────────────────────────────────────────────────────────
    public static final String EXTRA_ID_VIAJE = "ID_VIAJE";

    // ── Vistas ────────────────────────────────────────────────────────────────
    private TextView         txtTotalGanado;
    private TextView         txtCountPagados, txtCountPendientes, txtCountTotal;
    private TextView         chipTotalTabla;
    private LinearLayout     layoutFilasPagos, layoutEmptyTabla;
    private MaterialCardView cardDesglose, cardCalificarNota;
    private TextView         txtEfectivoCount, txtEfectivoTotal;
    private TextView         txtTransferenciaCount, txtTransferenciaTotal;
    private MaterialButton   btnCalificarPasajeros, btnVolverInicio;
    private ProgressBar      loaderResumen;
    private View             btnBack;

    // ── Datos ─────────────────────────────────────────────────────────────────
    private int     idViaje;
    private boolean cargaInicial   = true;
    private boolean todosCompletos = false;
    private SessionManager session;

    // ── Contador de filas reales (sin separadores) ────────────────────────────
    private int filaRealCount = 0;

    // ── Polling ───────────────────────────────────────────────────────────────
    private final Handler pollingHandler  = new Handler(Looper.getMainLooper());
    private Runnable      pollingRunnable;
    private boolean       pollingActivo   = false;

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
        setContentView(R.layout.activity_resumen_viaje);

        session  = new SessionManager(this);
        idViaje  = getIntent().getIntExtra(EXTRA_ID_VIAJE, 0);

        if (idViaje == 0) {
            Toast.makeText(this, "ID de viaje inválido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        configurarListeners();

        cargaInicial = true;
        cargarPagosDelViaje();
        iniciarPolling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!todosCompletos) iniciarPolling();
    }

    @Override protected void onPause()   { super.onPause();   detenerPolling(); }
    @Override protected void onDestroy() { super.onDestroy(); detenerPolling(); }

    // =========================================================================
    //  BIND
    // =========================================================================

    private void bindViews() {
        txtTotalGanado        = findViewById(R.id.txt_total_ganado);
        txtCountPagados       = findViewById(R.id.txt_count_pagados);
        txtCountPendientes    = findViewById(R.id.txt_count_pendientes);
        txtCountTotal         = findViewById(R.id.txt_count_total);
        chipTotalTabla        = findViewById(R.id.chip_total_tabla);
        layoutFilasPagos      = findViewById(R.id.layout_filas_pagos);
        layoutEmptyTabla      = findViewById(R.id.layout_empty_tabla);
        cardDesglose          = findViewById(R.id.card_desglose);
        cardCalificarNota     = findViewById(R.id.card_calificar_nota);
        txtEfectivoCount      = findViewById(R.id.txt_efectivo_count);
        txtEfectivoTotal      = findViewById(R.id.txt_efectivo_total);
        txtTransferenciaCount = findViewById(R.id.txt_transferencia_count);
        txtTransferenciaTotal = findViewById(R.id.txt_transferencia_total);
        btnCalificarPasajeros = findViewById(R.id.btn_calificar_pasajeros);
        btnVolverInicio       = findViewById(R.id.btn_volver_inicio);
        loaderResumen         = findViewById(R.id.loader_resumen);
        btnBack               = findViewById(R.id.btn_back_resumen);
    }

    // =========================================================================
    //  LISTENERS
    // =========================================================================

    private void configurarListeners() {
        if (btnBack != null)
            btnBack.setOnClickListener(v -> onBackPressed());

        if (btnVolverInicio != null)
            btnVolverInicio.setOnClickListener(v -> {
                Intent intent = new Intent(this, HomeConductor.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });

        if (btnCalificarPasajeros != null)
            btnCalificarPasajeros.setOnClickListener(v ->
                    Toast.makeText(this, "Función de calificación próximamente",
                            Toast.LENGTH_SHORT).show()
            );
    }

    // =========================================================================
    //  CARGAR PAGOS
    // =========================================================================

    private void cargarPagosDelViaje() {
        if (cargaInicial) mostrarLoader(true);

        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.viajePorId((long) idViaje),
                viajeResponse -> {
                    Log.d(TAG, "✅ viajePorId OK → " + viajeResponse.toString());

                    ConexionApi.getInstance(this).getObjectNoCache(
                            Constantes.pagosPorViaje((long) idViaje),
                            pagosResponse -> {
                                mostrarLoader(false);
                                cargaInicial = false;

                                JSONArray usuarios = viajeResponse.optJSONArray("usuarios");
                                JSONArray pagos    = extraerArrayPagos(pagosResponse);
                                double totalConf   = pagosResponse.optDouble("totalConfirmado", -1);

                                Log.d(TAG, "usuarios: " + (usuarios != null ? usuarios.length() : "NULL"));
                                Log.d(TAG, "pagos: "    + (pagos    != null ? pagos.length()    : "NULL"));
                                Log.d(TAG, "totalConfirmado backend: " + totalConf);

                                JSONArray resultado = cruzarUsuariosConPagos(usuarios, pagos, viajeResponse);
                                runOnUiThread(() -> poblarUI(resultado, totalConf, viajeResponse.optDouble("precio", 0)));
                            },
                            errorPagos -> {
                                // Fallback: intentar como array directo
                                ConexionApi.getInstance(this).getArrayNoCache(
                                        Constantes.pagosPorViaje((long) idViaje),
                                        pagosArr -> {
                                            mostrarLoader(false);
                                            cargaInicial = false;
                                            JSONArray usuarios = viajeResponse.optJSONArray("usuarios");
                                            JSONArray resultado = cruzarUsuariosConPagos(usuarios, pagosArr, viajeResponse);
                                            runOnUiThread(() -> poblarUI(resultado, -1, viajeResponse.optDouble("precio", 0)));
                                        },
                                        err2 -> {
                                            mostrarLoader(false);
                                            cargaInicial = false;
                                            JSONArray usuarios = viajeResponse.optJSONArray("usuarios");
                                            JSONArray resultado = cruzarUsuariosConPagos(usuarios, null, viajeResponse);
                                            runOnUiThread(() -> poblarUI(resultado, -1, viajeResponse.optDouble("precio", 0)));
                                        }
                                );
                            }
                    );
                },
                errorViaje -> {
                    mostrarLoader(false);
                    cargaInicial = false;
                    Log.e(TAG, "❌ viajePorId error: " + errorViaje);
                    runOnUiThread(this::mostrarEstadoVacio);
                }
        );
    }

    /**
     * Extrae el array de pagos de cualquier formato que devuelva el backend:
     * - { pagos: [...] }
     * - { data: [...] }
     * - { items: [...] }
     * - { results: [...] }
     * - array directo (no aplica aquí, pero por si acaso)
     */
    private JSONArray extraerArrayPagos(JSONObject response) {
        if (response == null) return null;
        for (String k : new String[]{"pagos", "data", "items", "results", "list"}) {
            JSONArray arr = response.optJSONArray(k);
            if (arr != null) return arr;
        }
        return null;
    }

    // =========================================================================
    //  CRUZAR USUARIOS CON PAGOS
    // =========================================================================

    private JSONArray cruzarUsuariosConPagos(JSONArray usuarios, JSONArray pagos, JSONObject viajeObj) {
        JSONArray resultado = new JSONArray();
        if (usuarios == null || usuarios.length() == 0) return resultado;

        double precioViaje = viajeObj.optDouble("precio", 0);
        if (precioViaje == 0) {
            JSONObject ruta = viajeObj.optJSONObject("ruta");
            if (ruta != null) precioViaje = ruta.optDouble("precio",
                    ruta.optDouble("costoCombustible", 0));
        }

        // Mapa de pagos por idUsuario — busca en múltiples campos
        java.util.Map<Integer, JSONObject> mapaPagos = new java.util.HashMap<>();
        if (pagos != null) {
            for (int i = 0; i < pagos.length(); i++) {
                JSONObject p = pagos.optJSONObject(i);
                if (p == null) continue;

                int idU = 0;
                for (String k : new String[]{"idUsuario", "idPasajero", "usuarioId", "pasajeroId"}) {
                    idU = p.optInt(k, 0);
                    if (idU > 0) break;
                }
                if (idU == 0) {
                    JSONObject pas = p.optJSONObject("pasajero");
                    if (pas == null) pas = p.optJSONObject("usuario");
                    if (pas != null) {
                        for (String k : new String[]{"id", "idUsuarios", "idUsuario"}) {
                            idU = pas.optInt(k, 0);
                            if (idU > 0) break;
                        }
                    }
                }
                if (idU > 0) {
                    mapaPagos.put(idU, p);
                    Log.d(TAG, "pago mapeado → idU=" + idU
                            + " cp=" + p.optBoolean("confirmacionPasajero")
                            + " cc=" + p.optBoolean("confirmacionConductor")
                            + " estado=" + p.optString("estado"));
                }
            }
        }

        for (int i = 0; i < usuarios.length(); i++) {
            JSONObject u = usuarios.optJSONObject(i);
            if (u == null) continue;

            String est = u.optString("estado", "").toUpperCase();
            if ("CANCELADO".equals(est) || "CANCELADA".equals(est)) continue;

            JSONObject usuObj = u.optJSONObject("usuario");
            if (usuObj == null) usuObj = u.optJSONObject("pasajero");

            int idUsuario = 0;
            String nombre = "";

            if (usuObj != null) {
                for (String k : new String[]{"idUsuarios", "id", "idUsuario"}) {
                    idUsuario = usuObj.optInt(k, 0);
                    if (idUsuario > 0) break;
                }
                for (String k : new String[]{"nombre", "nombreCompleto", "name", "nombres"}) {
                    String n = usuObj.optString(k, "");
                    if (!n.isEmpty() && !n.equals("null")) { nombre = n; break; }
                }
                if (nombre.isEmpty()) {
                    String n = usuObj.optString("nombres", "");
                    String a = usuObj.optString("apellidos", "");
                    nombre = (n + " " + a).trim();
                }
            }

            if (idUsuario == 0)
                idUsuario = u.optInt("idUsuarios", u.optInt("idUsuario", u.optInt("id", 0)));
            if (nombre.isEmpty())
                nombre = u.optString("nombre", u.optString("nombrePasajero", "Pasajero"));

            Log.d(TAG, "Usuario[" + i + "] id=" + idUsuario + " nombre='" + nombre + "' estado=" + est);

            JSONObject pagoExistente = (idUsuario > 0) ? mapaPagos.get(idUsuario) : null;

            try {
                JSONObject item = new JSONObject();
                item.put("pasajero", nombre.isEmpty() ? "Pasajero" : nombre);

                if (pagoExistente != null) {
                    // Determinar estado real a partir de confirmaciones
                    boolean cp = pagoExistente.optBoolean("confirmacionPasajero", false);
                    boolean cc = pagoExistente.optBoolean("confirmacionConductor", false);
                    String estadoPago = pagoExistente.optString("estado", "pendiente");

                    // Si ambos confirmaron pero el estado no lo refleja, corregirlo localmente
                    if (cp && cc && !estadoPago.equalsIgnoreCase("completado")) {
                        estadoPago = "completado";
                    } else if (cc && !cp) {
                        // Conductor confirmó pero pasajero no — mostrar como confirmado_conductor
                        estadoPago = "confirmado_conductor";
                    } else if (cp && !cc) {
                        estadoPago = "confirmado_pasajero";
                    }

                    String tipoPago = pagoExistente.optString("tipoPago",
                            pagoExistente.optString("metodoPago", "—"));

                    item.put("metodoPago", tipoPago);
                    item.put("monto",  pagoExistente.optDouble("monto", precioViaje));
                    item.put("estado", estadoPago);
                } else {
                    item.put("metodoPago", "—");
                    item.put("monto",  precioViaje);
                    item.put("estado", "sin_pago");
                }
                resultado.put(item);
            } catch (Exception e) {
                Log.e(TAG, "cruzarUsuariosConPagos: " + e.getMessage());
            }
        }

        Log.d(TAG, "resultado final: " + resultado.length() + " pasajeros");
        return resultado;
    }

    // =========================================================================
    //  POBLAR UI
    // =========================================================================

    private void poblarUI(JSONArray pagos, double totalConfirmadoBackend, double precioViaje) {
        if (layoutFilasPagos == null) return;

        layoutFilasPagos.removeAllViews();
        filaRealCount = 0;
        if (cardDesglose      != null) cardDesglose.setVisibility(View.GONE);
        if (cardCalificarNota != null) cardCalificarNota.setVisibility(View.GONE);
        if (btnCalificarPasajeros != null) btnCalificarPasajeros.setVisibility(View.GONE);

        if (pagos == null || pagos.length() == 0) {
            mostrarEstadoVacio();
            return;
        }

        if (layoutEmptyTabla != null) layoutEmptyTabla.setVisibility(View.GONE);

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "CO"));

        int    countPagados    = 0;
        int    countPendientes = 0;
        double totalEfectivo   = 0;
        double totalTransf     = 0;
        int    cntEfectivo     = 0;
        int    cntTransf       = 0;
        double totalCalculado  = 0;

        for (int i = 0; i < pagos.length(); i++) {
            JSONObject pago = pagos.optJSONObject(i);
            if (pago == null) continue;

            String pasajero = pago.optString("pasajero", "—");
            String metodo   = pago.optString("metodoPago",
                    pago.optString("tipoPago", "—"));
            double monto    = pago.optDouble("monto", 0);
            String estado   = pago.optString("estado", "pendiente").toLowerCase();

            // Acumuladores desglose por método
            if (metodo.equalsIgnoreCase("efectivo")) {
                totalEfectivo += monto; cntEfectivo++;
            } else if (metodo.equalsIgnoreCase("transferencia")) {
                totalTransf   += monto; cntTransf++;
            }

            boolean esPagado = estado.equals("completado")
                    || estado.equals("confirmado_pasajero")
                    || estado.equals("confirmado_conductor")
                    || estado.equals("pagado");

            boolean esPendiente = estado.equals("pendiente")
                    || estado.equals("expirado")
                    || estado.equals("sin_pago");

            if (esPagado) {
                countPagados++;
                totalCalculado += monto;
            } else if (esPendiente) {
                countPendientes++;
            }

            agregarFilaTabla(pasajero, metodo, monto, estado, nf, i < pagos.length() - 1);
        }

        // Preferir totalConfirmado del backend; solo usar local como fallback
        double totalMostrar = (totalConfirmadoBackend >= 0) ? totalConfirmadoBackend : totalCalculado;

        if (txtTotalGanado     != null) txtTotalGanado.setText(nf.format(totalMostrar));
        if (txtCountPagados    != null) txtCountPagados.setText(String.valueOf(countPagados));
        if (txtCountPendientes != null) txtCountPendientes.setText(String.valueOf(countPendientes));
        if (txtCountTotal      != null) txtCountTotal.setText(String.valueOf(pagos.length()));
        if (chipTotalTabla     != null) chipTotalTabla.setText(pagos.length() + " pagos");

        // Desglose por método
        if (cntEfectivo > 0 || cntTransf > 0) {
            if (cardDesglose          != null) cardDesglose.setVisibility(View.VISIBLE);
            if (txtEfectivoCount      != null) txtEfectivoCount.setText(
                    cntEfectivo + " pasajero" + (cntEfectivo != 1 ? "s" : ""));
            if (txtEfectivoTotal      != null) txtEfectivoTotal.setText("$" + nf.format(totalEfectivo));
            if (txtTransferenciaCount != null) txtTransferenciaCount.setText(
                    cntTransf + " pasajero" + (cntTransf != 1 ? "s" : ""));
            if (txtTransferenciaTotal != null) txtTransferenciaTotal.setText("$" + nf.format(totalTransf));
        }

        // Botón calificar
        if (countPagados > 0) {
            if (cardCalificarNota     != null) cardCalificarNota.setVisibility(View.VISIBLE);
            if (btnCalificarPasajeros != null) btnCalificarPasajeros.setVisibility(View.VISIBLE);
        }

        // Detener polling solo cuando no haya pendientes
        todosCompletos = countPendientes == 0 && pagos.length() > 0;
        if (todosCompletos) {
            detenerPolling();
            Log.d(TAG, "Todos los pagos completados — polling detenido");
        }
    }

    // =========================================================================
    //  AGREGAR FILA A LA TABLA
    // =========================================================================

    private void agregarFilaTabla(String pasajero, String metodo,
                                  double monto, String estado,
                                  NumberFormat nf, boolean agregarSeparador) {
        if (layoutFilasPagos == null) return;
        float d = getResources().getDisplayMetrics().density;

        int    colorEstado;
        String labelEstado;
        switch (estado) {
            case "completado":
            case "pagado":
                colorEstado = Color.parseColor("#2E7D32"); labelEstado = "✅ Pagado";      break;
            case "confirmado_pasajero":
                colorEstado = Color.parseColor("#1565C0"); labelEstado = "⏳ Pas. confirmó"; break;
            case "confirmado_conductor":
                colorEstado = Color.parseColor("#F57F17"); labelEstado = "⏳ Cond. confirmó"; break;
            case "cancelado":
                colorEstado = Color.parseColor("#B71C1C"); labelEstado = "❌ Cancelado";   break;
            case "expirado":
                colorEstado = Color.parseColor("#795548"); labelEstado = "⏰ Expirado";    break;
            case "sin_pago":
                colorEstado = Color.parseColor("#E65100"); labelEstado = "❗ Sin pagar";   break;
            default:
                colorEstado = Color.parseColor("#757575"); labelEstado = "⌛ Pendiente";   break;
        }

        String metodoLabel = "—";
        if (!metodo.equals("—") && !metodo.isEmpty()) {
            metodoLabel = (metodo.equalsIgnoreCase("efectivo") ? "💵 " : "📲 ")
                    + Character.toUpperCase(metodo.charAt(0))
                    + metodo.substring(1).toLowerCase();
        }

        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpFila = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFila.bottomMargin = (int) (1 * d);
        fila.setLayoutParams(lpFila);
        fila.setPadding((int) (12 * d), (int) (14 * d), (int) (12 * d), (int) (14 * d));

        fila.setBackgroundColor(filaRealCount % 2 == 0
                ? Color.WHITE
                : Color.parseColor("#F5FFFE"));
        filaRealCount++;

        fila.addView(buildCell(pasajero,               Color.parseColor("#1A2035"), 12.5f, 2.5f, true));
        fila.addView(buildCell(metodoLabel,             Color.parseColor("#546E7A"), 11f,   1.5f, false));
        fila.addView(buildCell("$" + nf.format(monto), Color.parseColor("#00695C"), 11f,   1.5f, true));
        fila.addView(buildCell(labelEstado,             colorEstado,                10f,   1.5f, true));

        layoutFilasPagos.addView(fila);

        if (agregarSeparador) {
            View sep = new View(this);
            LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (0.8f * d));
            sep.setLayoutParams(lpSep);
            sep.setBackgroundColor(Color.parseColor("#E8F5E9"));
            layoutFilasPagos.addView(sep);
        }
    }

    private TextView buildCell(String texto, int color, float size,
                               float peso, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(texto);
        tv.setTextColor(color);
        tv.setTextSize(size);
        tv.setMaxLines(2);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, peso));
        return tv;
    }

    // =========================================================================
    //  ESTADO VACÍO
    // =========================================================================

    private void mostrarEstadoVacio() {
        if (layoutEmptyTabla      != null) layoutEmptyTabla.setVisibility(View.VISIBLE);
        if (cardDesglose          != null) cardDesglose.setVisibility(View.GONE);
        if (cardCalificarNota     != null) cardCalificarNota.setVisibility(View.GONE);
        if (btnCalificarPasajeros != null) btnCalificarPasajeros.setVisibility(View.GONE);
        if (chipTotalTabla        != null) chipTotalTabla.setText("0 pagos");
        if (txtTotalGanado        != null) txtTotalGanado.setText("0");
        if (txtCountPagados       != null) txtCountPagados.setText("0");
        if (txtCountPendientes    != null) txtCountPendientes.setText("0");
        if (txtCountTotal         != null) txtCountTotal.setText("0");
    }

    // =========================================================================
    //  POLLING
    // =========================================================================

    private void iniciarPolling() {
        if (pollingActivo || todosCompletos) return;
        pollingActivo   = true;
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!pollingActivo) return;
                cargarPagosDelViaje();
                if (!todosCompletos) {
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
    //  HELPERS
    // =========================================================================

    private void mostrarLoader(boolean show) {
        if (loaderResumen != null)
            loaderResumen.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}