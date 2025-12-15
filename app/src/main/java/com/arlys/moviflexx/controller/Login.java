package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class Login extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
    }

    // Método para redirigir a HomeConductor
    public void irSuccess(View view) {
        Intent siguiente = new Intent(Login.this, Success.class);  // Cambio aquí
        startActivity(siguiente);
    }

    // Método para redirigir a la pantalla de registro
    public void irRegister(View view) {
        Intent siguiente = new Intent(Login.this, Register.class);
        startActivity(siguiente);
    }
}
