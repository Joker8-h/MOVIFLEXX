package com.arlys.moviflexx.controller;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.SesionUsuario;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class PublicarViaje extends AppCompatActivity {

    private static final String TAG = "PublicarViaje";

    // ─── VISTAS ───────────────────────────────────────────────────────────────
    private TextInputEditText   editFechaHora;
    private TextInputEditText   editPrecio;
    private Spinner             spinnerCupos;
    private CardView            mainCard;
    private CardView            loaderContainer;
    private View                overlayBackground;
    private MaterialButton      btnPublicar;
    private BottomNavigationView bottomNavigation;

    private TextView txtDestinoInfo;
    private TextView txtOrigenInfo;
    private TextView txtVehiculoInfo;
    private TextView txtConductorInfo;
    private TextView txtPrecioSugerido;   // badge de desglose de precio

    // ─── SESIÓN ───────────────────────────────────────────────────────────────
    private SessionManager session;

    // ─── DATOS DEL INTENT (vienen de PublicarRuta) ────────────────────────────
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

    // ─── FECHA SELECCIONADA ───────────────────────────────────────────────────
    private final Calendar calendarioSalida = Calendar.getInstance();
    private boolean fechaSeleccionada = false;

    // ─── LIFECYCLE ───────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publicar_viaje);

        session = new SessionManager(this);

        validarSesion();
        obtenerDatosIntent();
        enlazarVistas();
        configurarUI();
    }

    // ─── VALIDAR SESIÓN ──────────────────────────────────────────────────────
    private void validarSesion() {
        SesionUsuario.init(this);

        if (!session.isLoggedIn()) {
            irAlLogin();
            return;
        }

        conductorIdInt = session.getIdUsuario();
        vehiculoIdInt  = session.getVehiculoId();

        if (conductorIdInt == -1 || vehiculoIdInt == -1) {
            Toast.makeText(this,
                    "Debes registrar un vehículo antes de publicar viajes.",
                    Toast.LENGTH_LONG).show();
            irAlLogin();
        }
    }

    // ─── DATOS DEL INTENT ────────────────────────────────────────────────────
    private void obtenerDatosIntent() {
        Intent i = getIntent();

        rutaId           = i.getIntExtra("ID_RUTA_CREADA", 0);
        destinoRuta      = i.getStringExtra("DESTINO_RUTA");
        origenRuta       = i.getStringExtra("ORIGEN_RUTA");
        distanciaKm      = i.getDoubleExtra("DISTANCIA_KM", 0);
        duracionMin      = i.getDoubleExtra("DURACION_MIN", 0);
        fuelLitros       = i.getDoubleExtra("FUEL_LITROS", 0);
        costoCombustible = i.getDoubleExtra("COSTO_COMBUSTIBLE", 0);
        tipoTransporte   = i.getStringExtra("TIPO_TRANSPORTE") != null
                ? i.getStringExtra("TIPO_TRANSPORTE") : "driving";

        if (destinoRuta == null) destinoRuta = "Destino";
        if (origenRuta  == null) origenRuta  = "Origen";

        if (rutaId == 0) {
            Toast.makeText(this, "Ruta inválida. Vuelve a calcular.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // ─── ENLAZAR VISTAS ──────────────────────────────────────────────────────
    private void enlazarVistas() {
        editFechaHora     = findViewById(R.id.edit_fecha_hora);
        editPrecio        = findViewById(R.id.edit_precio);
        spinnerCupos      = findViewById(R.id.spinnerCupo);
        mainCard          = findViewById(R.id.main_card);
        loaderContainer   = findViewById(R.id.loader_container);
        overlayBackground = findViewById(R.id.overlay_background);
        btnPublicar       = findViewById(R.id.btn_publicar_viaje);
        bottomNavigation  = findViewById(R.id.bottom_navigation);

        txtDestinoInfo    = findViewById(R.id.txt_destino_info);
        txtOrigenInfo     = findViewById(R.id.txt_origen_info);
        txtVehiculoInfo   = findViewById(R.id.txt_vehiculo_info);
        txtConductorInfo  = findViewById(R.id.txt_conductor_info);
        txtPrecioSugerido = findViewById(R.id.txt_precio_sugerido);
    }

    // ─── CONFIGURAR UI ───────────────────────────────────────────────────────
    private void configurarUI() {
        // Ruta
        txtDestinoInfo.setText("🏁 " + destinoRuta);
        txtOrigenInfo.setText("📍 " + origenRuta);

        // Vehículo y conductor desde SessionManager
        String vehiculoNombre = session.getVehiculoNombre();
        String vehiculoPlaca  = session.getVehiculoPlaca();
        if (vehiculoNombre == null) vehiculoNombre = "Vehículo";
        if (vehiculoPlaca  == null) vehiculoPlaca  = "Sin placa";
        txtVehiculoInfo.setText(vehiculoNombre + " • " + vehiculoPlaca);
        txtConductorInfo.setText("🚗 Conductor: " + session.getNombre());

        // Orden de configuración
        configurarSpinnerConCapacidad();   // 1. cupos según el vehículo registrado
        colocarPrecioAutomatico();         // 2. precio sugerido basado en ruta
        configurarSelectorFechaHora();     // 3. DatePicker + TimePicker
        configurarBotonPublicar();         // 4. listener del botón
        configurarBottomNavigation();      // 5. barra inferior
        animarEntradaMainCard();           // 6. animación de entrada
    }

    // ─── SPINNER: CUPOS SEGÚN CAPACIDAD DEL VEHÍCULO ─────────────────────────
    /**
     * Lee la capacidad registrada en SessionManager y construye
     * el spinner de 1 hasta esa capacidad (máximo 8).
     * El spinner queda pre-seleccionado en el máximo disponible.
     */
    private void configurarSpinnerConCapacidad() {
        String capacidadStr = session.getVCapacidad();
        int capacidad = 4; // valor por defecto si no hay dato

        if (capacidadStr != null && !capacidadStr.trim().isEmpty()) {
            try {
                capacidad = Integer.parseInt(capacidadStr.trim());
            } catch (NumberFormatException e) {
                Log.w(TAG, "Capacidad no parseable: " + capacidadStr + " → usando 4");
            }
        }

        // Limitar entre 1 y 8 cupos
        int maxCupos = Math.min(Math.max(capacidad, 1), 8);

        String[] opciones = new String[maxCupos];
        for (int i = 0; i < maxCupos; i++) {
            opciones[i] = String.valueOf(i + 1);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                opciones
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCupos.setAdapter(adapter);

        // Seleccionar el máximo por defecto (todos los cupos del carro)
        spinnerCupos.setSelection(maxCupos - 1);

        Log.d(TAG, "Cupos configurados: 1–" + maxCupos + " (capacidad registrada: " + capacidad + ")");
    }

    // ─── PRECIO AUTOMÁTICO ───────────────────────────────────────────────────
    /**
     * Usa el precio EXACTO de la ruta seleccionada ($COP de combustible calculado
     * por el backend). El conductor puede editarlo antes de publicar.
     *
     * Prioridad:
     *   1. costoCombustible > 0  → precio = valor exacto de la ruta (sin margen extra)
     *   2. distanciaKm > 0       → fallback: $700 COP/km (backend no respondió)
     *   3. sin datos             → mínimo $5.000
     *
     * El resultado se redondea al centenar superior para un precio presentable.
     */
    private void colocarPrecioAutomatico() {
        double precioFinal;
        String origenPrecio;

        if (costoCombustible > 0) {
            // ✅ Precio EXACTO de la ruta elegida — el que muestra la card seleccionada
            precioFinal  = costoCombustible;
            origenPrecio = "ruta";
        } else if (distanciaKm > 0) {
            // Fallback: $700 COP/km si el backend no devolvió costo de combustible
            precioFinal  = distanciaKm * 700.0;
            origenPrecio = "km";
        } else {
            precioFinal  = 5000.0;
            origenPrecio = "base";
        }

        // Redondear al centenar superior (ej: 1.592 → 1.600)
        precioFinal = Math.ceil(precioFinal / 100.0) * 100.0;

        // Cargar en el campo editable — el conductor puede ajustarlo
        editPrecio.setText(String.format(Locale.getDefault(), "%.0f", precioFinal));

        // Badge de desglose debajo del campo de precio
        if (txtPrecioSugerido != null) {
            StringBuilder detalle = new StringBuilder();

            if (origenPrecio.equals("ruta")) {
                detalle.append("💡 Precio de la ruta seleccionada");
                detalle.append(String.format(Locale.getDefault(),
                        "  ·  ⛽ $%,.0f COP combustible", costoCombustible));
            } else if (origenPrecio.equals("km")) {
                detalle.append("💡 Estimado: $700/km");
            } else {
                detalle.append("💡 Precio mínimo base");
            }

            if (distanciaKm > 0) {
                detalle.append(String.format(Locale.getDefault(),
                        "  ·  📏 %.1f km", distanciaKm));
            }
            if (duracionMin > 0) {
                detalle.append(String.format(Locale.getDefault(),
                        "  ·  ⏱ %.0f min", duracionMin));
            }
            detalle.append("  ·  editable ✏️");

            txtPrecioSugerido.setText(detalle.toString());
            txtPrecioSugerido.setVisibility(View.VISIBLE);
        }

        Log.d(TAG, "Precio de la ruta: $" + precioFinal + " COP"
                + "  (combustible=" + costoCombustible
                + ", km=" + distanciaKm
                + ", origen=" + origenPrecio + ")");
    }

    // ─── SELECTOR DE FECHA Y HORA ─────────────────────────────────────────────
    private void configurarSelectorFechaHora() {
        // Pre-cargar la fecha actual como placeholder
        actualizarCampoFechaHora();

        editFechaHora.setFocusable(false);
        editFechaHora.setClickable(true);
        editFechaHora.setOnClickListener(v -> mostrarDialogoFecha());
    }

    private void mostrarDialogoFecha() {
        Calendar hoy = Calendar.getInstance();
        new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    calendarioSalida.set(Calendar.YEAR,         year);
                    calendarioSalida.set(Calendar.MONTH,        month);
                    calendarioSalida.set(Calendar.DAY_OF_MONTH, day);
                    mostrarDialogoHora();
                },
                hoy.get(Calendar.YEAR),
                hoy.get(Calendar.MONTH),
                hoy.get(Calendar.DAY_OF_MONTH)
        ) {{
            // No permitir fechas pasadas
            getDatePicker().setMinDate(hoy.getTimeInMillis());
        }}.show();
    }

    private void mostrarDialogoHora() {
        Calendar hoy = Calendar.getInstance();
        new TimePickerDialog(
                this,
                (view, hora, minuto) -> {
                    calendarioSalida.set(Calendar.HOUR_OF_DAY, hora);
                    calendarioSalida.set(Calendar.MINUTE,      minuto);
                    calendarioSalida.set(Calendar.SECOND,      0);
                    fechaSeleccionada = true;
                    actualizarCampoFechaHora();
                    editFechaHora.setError(null);
                },
                hoy.get(Calendar.HOUR_OF_DAY),
                hoy.get(Calendar.MINUTE),
                false  // 12h con AM/PM
        ).show();
    }

    private void actualizarCampoFechaHora() {
        // Formato que espera el backend (mismo que usaba la versión original)
        SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        editFechaHora.setText(sdf.format(calendarioSalida.getTime()));
    }

    // ─── BOTÓN PUBLICAR ───────────────────────────────────────────────────────
    private void configurarBotonPublicar() {
        btnPublicar.setOnClickListener(v -> {
            animarBoton(btnPublicar);
            if (validarFormulario()) {
                publicarViaje();
            }
        });
    }

    // ─── VALIDACIONES ────────────────────────────────────────────────────────
    private boolean validarFormulario() {

        // Fecha/hora: debe haber seleccionado una fecha futura
        if (!fechaSeleccionada) {
            editFechaHora.setError("Selecciona fecha y hora de salida");
            animarError(mainCard);
            Toast.makeText(this, "📅 Selecciona la fecha y hora de salida", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (calendarioSalida.getTimeInMillis() <= System.currentTimeMillis()) {
            editFechaHora.setError("La hora de salida debe ser futura");
            animarError(mainCard);
            Toast.makeText(this, "⏰ La hora de salida debe ser futura", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Precio
        String precioTxt = editPrecio.getText() != null
                ? editPrecio.getText().toString().trim() : "";

        if (precioTxt.isEmpty()) {
            editPrecio.setError("Ingresa el precio por pasajero");
            animarError(mainCard);
            return false;
        }

        try {
            double precio = Double.parseDouble(precioTxt);
            if (precio <= 0) {
                editPrecio.setError("El precio debe ser mayor a $0");
                animarError(mainCard);
                return false;
            }
        } catch (NumberFormatException e) {
            editPrecio.setError("Precio inválido");
            animarError(mainCard);
            return false;
        }

        return true;
    }

    // ─── PUBLICAR VIAJE VÍA API REST ─────────────────────────────────────────
    private void publicarViaje() {
        String token = SesionUsuario.getToken();
        if (token == null || token.isEmpty()) {
            irAlLogin();
            return;
        }

        mostrarLoader(true);

        try {
            int    cupos    = Integer.parseInt(spinnerCupos.getSelectedItem().toString());
            double precio   = Double.parseDouble(
                    editPrecio.getText().toString().trim());
            String fechaHora = editFechaHora.getText().toString().trim();

            JSONObject body = new JSONObject();
            body.put("idRuta",           rutaId);
            body.put("idVehiculos",      vehiculoIdInt);
            body.put("idConductor",      conductorIdInt);
            body.put("fechaHoraSalida",  fechaHora);
            body.put("cuposTotales",     cupos);
            body.put("cuposDisponibles", cupos);
            body.put("precio",           precio);
            body.put("estado",           "PROGRAMADO");

            // Datos extra de la ruta (para referencia del backend)
            if (distanciaKm > 0)      body.put("distanciaKm",      distanciaKm);
            if (duracionMin > 0)      body.put("duracionMin",       duracionMin);
            if (costoCombustible > 0) body.put("costoCombustible",  costoCombustible);
            if (fuelLitros > 0)       body.put("combustibleLitros", fuelLitros);

            ConexionApi.getInstance(this).post(
                    Constantes.VIAJES,
                    body,
                    response -> {
                        mostrarLoader(false);
                        Toast.makeText(this,
                                "✅ ¡Viaje publicado correctamente!",
                                Toast.LENGTH_LONG).show();
                        startActivity(new Intent(this, HomeConductor.class));
                        finish();
                    },
                    error -> {
                        mostrarLoader(false);
                        animarError(mainCard);
                        Toast.makeText(this,
                                "❌ Error al publicar el viaje. Intenta de nuevo.",
                                Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Error Volley: " + error.toString());
                    }
            );

        } catch (Exception e) {
            mostrarLoader(false);
            Log.e(TAG, "Error construyendo JSON: " + e.getMessage());
            Toast.makeText(this, "Error inesperado. Intenta de nuevo.", Toast.LENGTH_SHORT).show();
        }
    }

    // ─── LOADER ───────────────────────────────────────────────────────────────
    private void mostrarLoader(boolean mostrar) {
        overlayBackground.setVisibility(mostrar ? View.VISIBLE : View.GONE);
        loaderContainer.setVisibility(mostrar  ? View.VISIBLE : View.GONE);
        btnPublicar.setEnabled(!mostrar);
        btnPublicar.setText(mostrar ? "Publicando..." : "PUBLICAR VIAJE EN POPAYÁN");
    }

    // ─── BOTTOM NAVIGATION ───────────────────────────────────────────────────
    private void configurarBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            startActivity(new Intent(this, HomeConductor.class));
            finish();
            return true;
        });
    }

    // ─── NAVEGACIÓN ───────────────────────────────────────────────────────────
    private void irAlLogin() {
        SesionUsuario.cerrarSesion();
        startActivity(new Intent(this, Login.class));
        finish();
    }

    // ─── ANIMACIONES ─────────────────────────────────────────────────────────

    /** Anima la main_card con fade-in + slide-up al entrar */
    private void animarEntradaMainCard() {
        mainCard.setAlpha(0f);
        mainCard.setTranslationY(60f);
        mainCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(150)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /** Animación de shake horizontal cuando hay error */
    private void animarError(View view) {
        ObjectAnimator.ofFloat(view,
                        "translationX",
                        0f, -14f, 14f, -10f, 10f, -6f, 6f, 0f)
                .setDuration(450)
                .start();
    }

    /** Animación de escala al presionar un botón */
    private void animarBoton(View btn) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(btn, "scaleX", 1f, 0.94f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(btn, "scaleY", 1f, 0.94f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(200);
        set.setInterpolator(new OvershootInterpolator());
        set.start();
    }
}