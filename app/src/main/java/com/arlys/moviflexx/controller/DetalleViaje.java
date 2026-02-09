package com.arlys.moviflexx.controller;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class DetalleViaje extends AppCompatActivity {

    private TextView txtRuta, txtFecha, txtEstado, txtPrecio, txtCupos;
    private TextView txtConductor, txtTelefono, txtVehiculo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalle_viaje);

        inicializarVistas();

        // 🔥 DATOS TEMPORALES (para que NO falle)
        txtRuta.setText("Ciudad A → Ciudad B");
        txtFecha.setText("🕒 2026-02-10 08:00");
        txtEstado.setText("Estado: Programado");
        txtPrecio.setText("💰 Precio: $25");
        txtCupos.setText("👥 Cupos disponibles: 3");
        txtConductor.setText("👤 Juan Pérez");
        txtTelefono.setText("📞 999-888-777");
        txtVehiculo.setText("Toyota Corolla (ABC-123)");
    }

    private void inicializarVistas() {
        txtRuta = findViewById(R.id.txtRuta);
        txtFecha = findViewById(R.id.txtFecha);
        txtEstado = findViewById(R.id.txtEstado);
        txtPrecio = findViewById(R.id.txtPrecio);
        txtCupos = findViewById(R.id.txtCupos);
        txtConductor = findViewById(R.id.txtConductor);
        txtTelefono = findViewById(R.id.txtTelefono);
        txtVehiculo = findViewById(R.id.txtVehiculo);
    }
}
