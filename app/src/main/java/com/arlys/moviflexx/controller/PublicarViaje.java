package com.arlys.moviflexx.controller;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.Viaje;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PublicarViaje extends AppCompatActivity {

    private TextInputEditText editFechaHora, editPrecio;
    private Spinner spinnerCupos;

    private View overlayBackground;
    private CardView loaderContainer;

    private CardView mainCard, metadataCard, statsCard;
    private View headerCard;

    private MaterialButton btnPublicar;

    private TextView txtDestinoInfo;
    private TextView txtOrigenInfo;
    private TextView txtVehiculoInfo;

    private int rutaId;
    private int vehiculoId;
    private String destinoRuta;
    private String origenRuta;
    private String tipoTransporte;

    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publicar_viaje);

        session = new SessionManager(this);

        rutaId = getIntent().getIntExtra("ID_RUTA_CREADA", 0);

        if (rutaId == 0) {

            Toast.makeText(this,
                    "Error: no se recibió la ruta",
                    Toast.LENGTH_SHORT).show();

            finish();
            return;
        }

        destinoRuta = getIntent().getStringExtra("DESTINO_RUTA");
        origenRuta = getIntent().getStringExtra("ORIGEN_RUTA");
        tipoTransporte = getIntent().getStringExtra("TIPO_TRANSPORTE");

        vehiculoId = session.getVehiculoId();

        if (vehiculoId == -1) {

            Toast.makeText(this,
                    "Debes registrar un vehículo primero",
                    Toast.LENGTH_LONG).show();

            startActivity(new Intent(this, RegistrarVehiculo.class));

            finish();
            return;
        }

        initViews();

        cargarInfoRutaVehiculo();

        configurarSpinnerCupos();

        colocarFechaHoraActual();

        configurarBottomNav();

        animarEntradaProfesional();

        btnPublicar.setOnClickListener(v -> {

            animarBotonProfesional(v);

            publicarViaje();

        });

    }

    /* ================= INIT ================= */

    private void initViews() {

        editFechaHora = findViewById(R.id.edit_fecha_hora);
        editPrecio = findViewById(R.id.edit_precio);

        spinnerCupos = findViewById(R.id.spinnerCupo);

        overlayBackground = findViewById(R.id.overlay_background);
        loaderContainer = findViewById(R.id.loader_container);

        mainCard = findViewById(R.id.main_card);
        metadataCard = findViewById(R.id.metadata_card);
        statsCard = findViewById(R.id.stats_card);
        headerCard = findViewById(R.id.header_card);

        btnPublicar = findViewById(R.id.btn_publicar_viaje);

        txtDestinoInfo = findViewById(R.id.txt_destino_info);
        txtOrigenInfo = findViewById(R.id.txt_origen_info);
        txtVehiculoInfo = findViewById(R.id.txt_vehiculo_info);

    }

    /* ================= INFO ================= */

    private void cargarInfoRutaVehiculo() {

        txtDestinoInfo.setText(
                destinoRuta != null ? destinoRuta : "Sin destino"
        );

        txtOrigenInfo.setText(
                origenRuta != null ? origenRuta : "Ubicación actual"
        );

        String nombre = session.getVehiculoNombre();
        String placa = session.getVehiculoPlaca();

        if (nombre != null)
            txtVehiculoInfo.setText(nombre + " • " + placa);
        else
            txtVehiculoInfo.setText("Vehículo registrado");

    }

    /* ================= PUBLICAR ================= */

    private void publicarViaje() {

        String precioTxt = editPrecio.getText().toString().trim();

        if (precioTxt.isEmpty()) {

            editPrecio.setError("Ingrese el precio");

            animarErrorProfesional(mainCard);

            return;
        }

        int cupos = Integer.parseInt(
                spinnerCupos.getSelectedItem().toString()
        );

        mostrarLoaderProfesional();

        try {

            Viaje viaje = new Viaje();

            viaje.setIdRuta(rutaId);
            viaje.setIdVehiculo(vehiculoId);
            viaje.setFechaSalida(editFechaHora.getText().toString());

// ✅ AGREGAR ESTA LÍNEA (FALTABA)
            viaje.setCuposTotales(cupos);

            viaje.setCuposDisponibles(cupos);

            viaje.setPrecio(Double.parseDouble(precioTxt));

            viaje.setEstado("PROGRAMADO");

// ✅ USAR toJson() (MEJOR PRÁCTICA)
            JSONObject body = viaje.toJson();

            body.put("idRuta", viaje.getIdRuta());
            body.put("idVehiculos", viaje.getIdVehiculo());
            body.put("fechaHoraSalida", viaje.getFechaSalida());
            body.put("cuposTotales", cupos);
            body.put("cuposDisponibles", viaje.getCuposDisponibles());
            body.put("precio", viaje.getPrecio());

            ConexionApi.getInstance(this).post(

                    Constantes.VIAJES,
                    body,

                    response -> {

                        ocultarLoaderProfesional();

                        animarExitoProfesional(() -> {

                            Toast.makeText(
                                    this,
                                    "Viaje publicado correctamente",
                                    Toast.LENGTH_LONG
                            ).show();

                            startActivity(
                                    new Intent(
                                            this,
                                            HomeConductor.class
                                    )
                            );

                            finish();

                        });

                    },

                    error -> {

                        ocultarLoaderProfesional();

                        animarErrorProfesional(mainCard);

                        Toast.makeText(
                                this,
                                "Error al publicar",
                                Toast.LENGTH_LONG
                        ).show();

                    }

            );

        } catch (Exception e) {

            ocultarLoaderProfesional();

            e.printStackTrace();

        }

    }

    /* ================= CONFIG ================= */

    private void configurarSpinnerCupos() {

        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(
                        this,
                        R.array.cupos,
                        android.R.layout.simple_spinner_item
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerCupos.setAdapter(adapter);

    }

    private void colocarFechaHoraActual() {

        String fecha = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());

        editFechaHora.setText(fecha);

    }

    private void configurarBottomNav() {

        BottomNavigationView nav =
                findViewById(R.id.bottom_navigation);

        nav.setOnItemSelectedListener(item -> {

            if (item.getItemId() == R.id.nav_inicio) {

                startActivity(
                        new Intent(
                                this,
                                HomeConductor.class
                        )
                );

                finish();

            }

            return true;
        });

    }

    /* ================= ANIMACIONES ================= */

    private void animarEntradaProfesional() {

        if (headerCard != null) {

            headerCard.setAlpha(0f);

            headerCard.animate()
                    .alpha(1f)
                    .setDuration(600)
                    .setInterpolator(
                            new DecelerateInterpolator()
                    )
                    .start();
        }

        if (mainCard != null) {

            mainCard.setAlpha(0f);

            mainCard.animate()
                    .alpha(1f)
                    .setDuration(800)
                    .start();
        }

    }

    private void animarBotonProfesional(View v) {

        v.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction(() ->
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                );

    }

    private void mostrarLoaderProfesional() {

        if (overlayBackground != null)
            overlayBackground.setVisibility(View.VISIBLE);

        if (loaderContainer != null)
            loaderContainer.setVisibility(View.VISIBLE);

    }

    private void ocultarLoaderProfesional() {

        if (overlayBackground != null)
            overlayBackground.setVisibility(View.GONE);

        if (loaderContainer != null)
            loaderContainer.setVisibility(View.GONE);

    }

    private void animarErrorProfesional(View view) {

        ObjectAnimator shake =
                ObjectAnimator.ofFloat(
                        view,
                        "translationX",
                        0, -15, 15, -10, 10, -5, 5, 0
                );

        shake.setDuration(500);

        shake.start();

    }

    private void animarExitoProfesional(Runnable onComplete) {

        mainCard.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(onComplete)
                .start();

    }

}
