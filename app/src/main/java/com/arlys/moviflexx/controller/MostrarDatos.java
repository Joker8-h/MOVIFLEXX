package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

import java.util.ArrayList;

public class MostrarDatos extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mostrar_datos);

        ListView listView = findViewById(R.id.listDatos);
        ArrayList<String> datos = new ArrayList<>();

        SharedPreferences prefs =
                getSharedPreferences("userData", MODE_PRIVATE);

        String nombre = prefs.getString("nombre", "");
        String phone = prefs.getString("phone", "");
        String email = prefs.getString("email", "");
        String rol = prefs.getString("rol", "");

        datos.add("Nombre: " + nombre);
        datos.add("Teléfono: " + phone);
        datos.add("Email: " + email);
        datos.add("Rol: " + rol);

        if (rol.equalsIgnoreCase("Conductor")) {
            datos.add("Placa: " + prefs.getString("placa", ""));
            datos.add("Modelo: " + prefs.getString("modelo", ""));
            datos.add("Color: " + prefs.getString("color", ""));
        }

        listView.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                datos
        ));
    }

    public void irHome(View view) {
        startActivity(new Intent(this, HomeConductor.class));
        finish();
    }
}
