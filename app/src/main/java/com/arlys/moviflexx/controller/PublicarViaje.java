package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PublicarViaje extends AppCompatActivity {

    private TextInputEditText editFechaHora, editPrecio;
    private Spinner spinnerCupos;
    private ProgressBar loader;

    private int rutaId;
    private int vehiculoId;

    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publicar_viaje);

        session = new SessionManager(this);

        // 🔥 ID RUTA
        rutaId = getIntent().getIntExtra("ID_RUTA_CREADA", 0);
        if (rutaId == 0) {
            Toast.makeText(this, "Error: no se recibió la ruta", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 🔥 ID VEHÍCULO
        vehiculoId = session.getVehiculoId();
        if (vehiculoId == -1) {
            Toast.makeText(this, "Debes registrar un vehículo primero", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, RegistrarVehiculo.class));
            finish();
            return;
        }

        initViews();
        configurarSpinnerCupos();
        colocarFechaHoraActual();
        configurarBottomNav();

        findViewById(R.id.btn_publicar_viaje)
                .setOnClickListener(v -> publicarViaje());
    }

    private void initViews() {
        editFechaHora = findViewById(R.id.edit_fecha_hora);
        editPrecio = findViewById(R.id.edit_precio);
        spinnerCupos = findViewById(R.id.spinnerCupo);
        loader = findViewById(R.id.loader_viaje);
    }

    // ================= SPINNER =================
    private void configurarSpinnerCupos() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.cupos,
                R.drawable.spinner_item
        );
        adapter.setDropDownViewResource(R.drawable.spinner_dropdown_item);
        spinnerCupos.setAdapter(adapter);
    }

    // ================= FECHA =================
    private void colocarFechaHoraActual() {
        String ahora = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());

        editFechaHora.setText(ahora);
    }

    // ================= PUBLICAR =================
    private void publicarViaje() {

        String precioTxt = editPrecio.getText().toString().trim();
        if (precioTxt.isEmpty()) {
            editPrecio.setError("Ingresa el precio");
            return;
        }

        int cupos = Integer.parseInt(
                spinnerCupos.getSelectedItem().toString()
        );

        loader.setVisibility(View.VISIBLE);

        try {
            JSONObject body = new JSONObject();

            body.put("idRuta", rutaId);
            body.put("idVehiculos", vehiculoId);
            body.put("fechaHoraSalida", editFechaHora.getText().toString());
            body.put("cuposTotales", cupos);
            body.put("cuposDisponibles", cupos); // ✅ EXACTO
            body.put("precio", Double.parseDouble(precioTxt));

            ConexionApi.getInstance(this).post(
                    Constantes.VIAJES,
                    body,
                    response -> {
                        loader.setVisibility(View.GONE);
                        Toast.makeText(this, "Viaje publicado con éxito 🚗", Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(this, HomeConductor.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    },
                    error -> {
                        loader.setVisibility(View.GONE);
                        Toast.makeText(this, "Error al publicar viaje", Toast.LENGTH_LONG).show();
                    }
            );

        } catch (Exception e) {
            loader.setVisibility(View.GONE);
            e.printStackTrace();
        }
    }

    // ================= NAV =================
    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_mis_viajes);

        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_inicio) {
                startActivity(new Intent(this, HomeConductor.class));
                finish();
            }
            return true;
        });
    }
}
