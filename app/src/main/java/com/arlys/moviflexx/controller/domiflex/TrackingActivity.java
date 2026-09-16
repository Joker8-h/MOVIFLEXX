package com.arlys.moviflexx.controller.domiflex;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class TrackingActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);
        int pedidoId = getIntent().getIntExtra("pedidoId", 0);
        ((TextView)findViewById(R.id.tvInfo)).setText("Tracking Pedido #"+pedidoId+"\nEstados: CREADO → ASIGNADO → RECOGIENDO → EN_CAMINO → ENTREGADO");
        // Aquí se integraría OSRM para mostrar ruta recogida→entrega con OSRM_BASE_URL
    }
}
