package com.arlys.moviflexx.controller.domiflex;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class PerfilActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);
        ((TextView)findViewById(R.id.tvTitle)).setText("Perfil DomiFlex");
    }
}
