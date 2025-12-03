package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class Login extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText email = findViewById(R.id.inputEmail);
        EditText pass = findViewById(R.id.inputPassword);
        Button login = findViewById(R.id.btnLogin);
        TextView register = findViewById(R.id.txtRegister);

        login.setOnClickListener(v -> {
            // Aquí validas login real
        });

        register.setOnClickListener(v ->
                startActivity(new Intent(Login.this, Register.class))
        );
    }
    public void irRegister(View view) {
        Intent siguiente = new Intent(Login.this, Register.class);
        startActivity(siguiente);
    }
}
