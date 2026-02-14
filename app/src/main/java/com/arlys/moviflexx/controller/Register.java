package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Constantes;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Register extends AppCompatActivity {

    private static final String TAG = "REGISTER_DEBUG";

    private TextInputEditText edtNombre, edtEmail, edtTelefono, edtPassword;
    private RadioGroup        rgRol;
    private MaterialButton    btnRegistrar;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        edtNombre    = findViewById(R.id.edt_nombre);
        edtEmail     = findViewById(R.id.edt_email);
        edtTelefono  = findViewById(R.id.edt_telefono);
        edtPassword  = findViewById(R.id.edt_password);
        rgRol        = findViewById(R.id.rgRol);
        btnRegistrar = findViewById(R.id.btn_register);

        btnRegistrar.setOnClickListener(v -> registrarUsuario());
    }

    // ─── Registrar usuario ────────────────────────────────────────────────────
    private void registrarUsuario() {

        String nombre   = edtNombre.getText().toString().trim();
        String email    = edtEmail.getText().toString().trim();
        String telefono = edtTelefono.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (nombre.isEmpty() || email.isEmpty()
                || telefono.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        int checkedId = rgRol.getCheckedRadioButtonId();
        if (checkedId == -1) {
            Toast.makeText(this, "Selecciona un rol", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton rb  = findViewById(checkedId);
        String rol = rb.getText().toString().toUpperCase();

        btnRegistrar.setEnabled(false);
        btnRegistrar.setAlpha(0.7f);
        btnRegistrar.setText("Registrando...");

        JSONObject json = new JSONObject();
        try {
            json.put("nombre",   nombre);
            json.put("email",    email);
            json.put("telefono", telefono);
            json.put("password", password);
            json.put("rol",      rol);
        } catch (JSONException e) {
            Toast.makeText(this, "Error creando datos", Toast.LENGTH_SHORT).show();
            resetBoton();
            return;
        }

        Log.d(TAG, "Registrando usuario → " + Constantes.REGISTER);
        Log.d(TAG, "JSON: " + json);

        final String emailFinal = email;

        client.newCall(new Request.Builder()
                .url(Constantes.REGISTER)
                .post(RequestBody.create(json.toString(),
                        MediaType.parse("application/json; charset=utf-8")))
                .build()
        ).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Fallo: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(Register.this,
                            "Sin conexión: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    resetBoton();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "HTTP " + response.code() + " | " + body);

                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(Register.this,
                                "Usuario creado. Registra tu rostro...",
                                Toast.LENGTH_SHORT).show();

                        // Abrir FaceRegister para escaneo automático
                        Intent intent = new Intent(Register.this, FaceRegister.class);
                        intent.putExtra("email", emailFinal);
                        startActivity(intent);
                        finish();
                    });
                } else {
                    String msg;
                    switch (response.code()) {
                        case 400: msg = "Datos inválidos: " + body;    break;
                        case 409: msg = "El email ya está registrado"; break;
                        case 500: msg = "Error del servidor";          break;
                        default:  msg = "Error " + response.code() + ": " + body;
                    }
                    runOnUiThread(() -> {
                        Toast.makeText(Register.this, msg, Toast.LENGTH_LONG).show();
                        resetBoton();
                    });
                }
            }
        });
    }

    // ─── Utilidades ───────────────────────────────────────────────────────────
    private void resetBoton() {
        btnRegistrar.setEnabled(true);
        btnRegistrar.setAlpha(1.0f);
        btnRegistrar.setText(getString(R.string.register_btn));
    }

    private void irLogin() {
        startActivity(new Intent(this, Login.class));
        finish();
    }

    public void irLoginView(View view) {
        irLogin();
    }
}

