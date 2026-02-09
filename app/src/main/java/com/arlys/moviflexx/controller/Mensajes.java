package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Conversacion;
import com.arlys.moviflexx.model.ConversacionAdapter;
import com.arlys.moviflexx.model.Constantes;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class Mensajes extends AppCompatActivity {

    private RecyclerView recycler;
    private ConversacionAdapter adapter;
    private final List<Conversacion> lista = new ArrayList<>();
    private final List<Conversacion> filtrada = new ArrayList<>();
    private EditText etBuscar;
    private TextView tvSinResultados;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mensajes);

        recycler = findViewById(R.id.rvConversaciones);
        etBuscar = findViewById(R.id.etBuscar);
        tvSinResultados = findViewById(R.id.tvSinResultados);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConversacionAdapter(filtrada, this::abrirChat);
        recycler.setAdapter(adapter);

        configurarBuscador();
        configurarBottomNav();
        cargarConversaciones();
    }

    // 📡 CARGAR CONVERSACIONES (CHAT)
    private void cargarConversaciones() {
        ConexionApi.getInstance(this).getArray(
                Constantes.CHAT_CONVERSACIONES,
                this::procesarConversaciones,
                error -> tvSinResultados.setVisibility(View.VISIBLE)
        );
    }

    private void procesarConversaciones(JSONArray arr) {
        lista.clear();
        filtrada.clear();

        for (int i = 0; i < arr.length(); i++) {
            Conversacion c = Conversacion.fromJson(arr.optJSONObject(i));
            if (c != null) lista.add(c);
        }

        filtrada.addAll(lista);
        adapter.notifyDataSetChanged();
        tvSinResultados.setVisibility(lista.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // 🔎 BUSCADOR
    private void configurarBuscador() {
        etBuscar.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {}

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                filtrada.clear();
                for (Conversacion conv : lista) {
                    if (conv.getNombre().toLowerCase()
                            .contains(s.toString().toLowerCase())) {
                        filtrada.add(conv);
                    }
                }
                tvSinResultados.setVisibility(
                        filtrada.isEmpty() ? View.VISIBLE : View.GONE);
                adapter.notifyDataSetChanged();
            }
        });
    }

    // 💬 ABRIR CHAT
    private void abrirChat(Conversacion c) {
        Intent i = new Intent(this, Chat.class);
        i.putExtra("idConversacion", c.getId());
        i.putExtra("nombre", c.getNombre());
        startActivity(i);
    }

    // 🧭 NAV
    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_mensajes);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_inicio)
                startActivity(new Intent(this, Home.class));
            else if (item.getItemId() == R.id.nav_mapa)
                startActivity(new Intent(this, Mapa.class));
            else if (item.getItemId() == R.id.nav_perfil)
                startActivity(new Intent(this, PerfilUsuario.class));
            else return true;
            finish();
            return true;
        });
    }
}
