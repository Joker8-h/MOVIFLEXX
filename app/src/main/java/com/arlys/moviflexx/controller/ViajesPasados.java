package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class ViajesPasados extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_viajes_pasados);


    }
    public void irDetalle(View view) {
        Intent siguiente = new Intent(ViajesPasados.this, DetalleViajePasado.class);
        startActivity(siguiente);
    }
}