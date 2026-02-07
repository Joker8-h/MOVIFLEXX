package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Spinner;
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
    private Spinner spinnerRol;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Referencias del XML
        edtNombre = findViewById(R.id.edt_nombre);
        edtEmail = findViewById(R.id.edt_email);
        edtTelefono = findViewById(R.id.edt_telefono);
        edtPassword = findViewById(R.id.edt_password);
        spinnerRol = findViewById(R.id.spinner_rol);
    }

    // MÉTODO LLAMADO DESDE EL XML
    public void registrarUsuario(View view) {

        String nombre = edtNombre.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String telefono = edtTelefono.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();
        String rolTexto = spinnerRol.getSelectedItem().toString();

        // Validaciones
        if (nombre.isEmpty() || email.isEmpty() || telefono.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Complete todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔑 MAPEO DE ROL (SEGÚN TU BD)
        int idRol;
        switch (rolTexto) {
            case "ADMIN":
                idRol = 1;
                break;
            case "CONDUCTOR":
                idRol = 2;
                break;
            default:
                idRol = 3; // USUARIO
                break;
        }

        // JSON EXACTO QUE ESPERA EL BACKEND
        JSONObject json = new JSONObject();
        try {
            json.put("nombre", nombre);
            json.put("email", email);       // 👈 coincide con la BD
            json.put("telefono", telefono);
            json.put("password", password);
            json.put("idRol", idRol);       // 👈 CLAVE
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constantes.REGISTER,
                json,
                response -> {
                    Toast.makeText(this, "Usuario registrado correctamente", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, Login.class));
                    finish();
                },
                error -> {
                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        String body = "";

                        if (error.networkResponse.data != null) {
                            body = new String(error.networkResponse.data);
                        }

                        Toast.makeText(
                                this,
                                "HTTP " + statusCode + " → " + body,
                                Toast.LENGTH_LONG
                        ).show();

                        android.util.Log.e("REGISTER_ERROR", "HTTP " + statusCode + " → " + body);

                    } else {
                        Toast.makeText(this, "Sin respuesta del servidor", Toast.LENGTH_LONG).show();
                    }
                }

        );

        queue.add(request);
    }

    // BOTÓN IR A LOGIN
    public void irLogin(View view) {
        startActivity(new Intent(this, Login.class));
        finish();
    }
}
