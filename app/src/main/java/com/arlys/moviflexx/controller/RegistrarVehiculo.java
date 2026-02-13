package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONObject;

public class RegistrarVehiculo extends AppCompatActivity {

    private AutoCompleteTextView edtMarca, edtModelo;
    private TextInputEditText edtPlaca, edtCapacidad;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registrar_vehiculo);

        sessionManager = new SessionManager(this);

        // ⚡ QUITAMOS LA VALIDACIÓN DE SESIÓN AQUÍ
        // La validación ya se hizo en HomeConductor

        edtMarca = findViewById(R.id.edt_marca);
        edtModelo = findViewById(R.id.edt_modelo);
        edtPlaca = findViewById(R.id.edt_placa);
        edtCapacidad = findViewById(R.id.edt_capacidad);
    }

    public void registrarVehiculo(View view) {
        String marca = edtMarca.getText().toString().trim();
        String modelo = edtModelo.getText().toString().trim();
        String placa = edtPlaca.getText().toString().trim().toUpperCase();
        String capacidad = edtCapacidad.getText().toString().trim();

        if (marca.isEmpty() || modelo.isEmpty() || placa.isEmpty() || capacidad.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("marca", marca);
            body.put("modelo", modelo);
            body.put("placa", placa);
            body.put("capacidad", Integer.parseInt(capacidad));

            ConexionApi.getInstance(this).postJson(
                    Constantes.VEHICULOS,
                    body,
                    response -> {
                        // Extraer ID de la respuesta
                        int id = response.optInt("idVehiculos", response.optInt("id", -1));

                        // Guardar permanentemente
                        sessionManager.saveVehiculo(id, marca + " " + modelo, placa, capacidad);

                        Toast.makeText(this, "✅ Vehículo registrado correctamente", Toast.LENGTH_SHORT).show();

                        // Volver a HomeConductor
                        finish();
                    },
                    error -> Toast.makeText(this, "Error en el servidor", Toast.LENGTH_SHORT).show()
            );
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al procesar", Toast.LENGTH_SHORT).show();
        }
    }
}