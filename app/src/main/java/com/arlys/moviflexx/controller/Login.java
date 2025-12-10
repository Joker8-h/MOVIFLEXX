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
    public void irRegister(View view) {
        Intent siguiente = new Intent(Login.this, Register.class);
        startActivity(siguiente);
    }
    public void irsuccess(View view) {
        Intent siguiente = new Intent(Login.this, Success.class);
        startActivity(siguiente);
    }
}