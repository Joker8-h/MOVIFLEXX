package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class EditarPerfil extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_editar_perfil);


    }
    public void irPerfil(View view) {
        startActivity(new Intent(this, PerfilUsuario.class));
    }


}