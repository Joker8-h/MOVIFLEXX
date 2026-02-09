package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Constantes;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

public class Register extends AppCompatActivity {

    private TextInputEditText edtNombre, edtEmail, edtTelefono, edtPassword;
    private RadioGroup rgRol;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        edtNombre = findViewById(R.id.edt_nombre);
        edtEmail = findViewById(R.id.edt_email);
        edtTelefono = findViewById(R.id.edt_telefono);
        edtPassword = findViewById(R.id.edt_password);
        rgRol = findViewById(R.id.rgRol);
    }

    public void registrarUsuario(View view) {

        String nombre = edtNombre.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String telefono = edtTelefono.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (nombre.isEmpty() || email.isEmpty() || telefono.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        int checkedId = rgRol.getCheckedRadioButtonId();
        if (checkedId == -1) {
            Toast.makeText(this, "Selecciona un rol", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton rb = findViewById(checkedId);
        String rol = rb.getText().toString().toUpperCase(); // PASAJERO o CONDUCTOR

        JSONObject json = new JSONObject();
        try {
            json.put("nombre", nombre);
            json.put("email", email);
            json.put("telefono", telefono);
            json.put("password", password);
            json.put("rol", rol); // 🔥 CLAVE
        } catch (JSONException e) {
            Toast.makeText(this, "Error creando datos", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constantes.REGISTER,
                json,
                response -> {
                    Toast.makeText(this,
                            "Registro exitoso, inicia sesión",
                            Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, Login.class));
                    finish();
                },
                error -> {
                    if (error.networkResponse != null) {
                        Toast.makeText(this,
                                new String(error.networkResponse.data),
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                "Error de conexión",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );

        queue.add(request);
    }

    public void irLogin(View view) {
        startActivity(new Intent(this, Login.class));
        finish();
    }
}
