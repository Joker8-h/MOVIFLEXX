package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class Login extends AppCompatActivity {

    private EditText edtEmail, edtPassword;
    private ImageButton btnShowPass;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnShowPass = findViewById(R.id.btnShowPassword);

        setupShowPassword();
    }

    private void setupShowPassword() {
        btnShowPass.setOnClickListener(v -> {
            if (isPasswordVisible) {
                edtPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnShowPass.setImageResource(R.drawable.ic_eye_close);
                isPasswordVisible = false;
            } else {
                edtPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                btnShowPass.setImageResource(R.drawable.ic_eye_open);
                isPasswordVisible = true;
            }
            edtPassword.setSelection(edtPassword.length());
        });
    }

    public void irRegister(View view) {
        startActivity(new Intent(Login.this, Register.class));
    }

    public void irsuccess(View view) {
        String email = edtEmail.getText().toString().trim();
        String pass = edtPassword.getText().toString().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Complete todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Ingrese un correo válido", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("userData", MODE_PRIVATE);
        String savedEmail = prefs.getString("email", "");
        String savedPassword = prefs.getString("password", "");

        if (email.equals(savedEmail) && pass.equals(savedPassword)) {

            // 🔥 AQUÍ ES DONDE SE HACE LA MAGIA
            Intent siguiente = new Intent(Login.this, Success.class);
            siguiente.putExtra("type", "login");         // Necesario para que Success vaya a Profile
            siguiente.putExtra("userRole", "cliente");   // Enviar rol (cliente o conductor)

            startActivity(siguiente);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        } else {
            Toast.makeText(this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show();
            edtPassword.setText("");
        }
    }
}
