package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Manager.LoginManager;

public class Login extends AppCompatActivity {

    EditText etCorreo, etPassword;
    Button btnLogin, btnIrRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // ---- REFERENCIAS ----
        etCorreo = findViewById(R.id.et_correo);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnIrRegister = findViewById(R.id.btn_ir_register);

        // ---- IR A REGISTER ----
        btnIrRegister.setOnClickListener(v -> {
            startActivity(new Intent(Login.this, Register.class));
        });

        // ---- LOGIN ----
        btnLogin.setOnClickListener(v -> {

            String correo = etCorreo.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (correo.isEmpty() || password.isEmpty()) {
                Toast.makeText(Login.this, "Ingresa todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }

            LoginManager loginManager = new LoginManager(Login.this);

            boolean existe = loginManager.validarUsuario(correo, password);

            if (existe) {
                Toast.makeText(Login.this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Login.this, Success.class));
                finish();
            } else {
                Toast.makeText(Login.this, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
