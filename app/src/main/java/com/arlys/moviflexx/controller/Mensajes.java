package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

public class Mensajes extends AppCompatActivity {

    private final List<Conversacion> todasConversaciones = new ArrayList<>();
    private final List<Conversacion> conversacionesFiltradas = new ArrayList<>();

    private RecyclerView recyclerView;
    private ConversacionAdapter adapter;
    private EditText etBuscar;
    private TextView tvSinResultados;
    private BottomNavigationView bottomNavigation;

    private final int COLOR_PRIMARY = Color.parseColor("#6A11CB");
    private final int COLOR_TEXT_SECONDARY = Color.parseColor("#7A7A7A");

    private final int[] bankFotos = {
            R.drawable.foto,
            R.drawable.foto1,
            R.drawable.foto2,
            R.drawable.foto3,
            R.drawable.foto4
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mensajes);

        inicializarVistas();
        configurarRecycler();
        generarConversaciones();
        configurarBuscador();
        configurarBottomNav();
        animarEntrada();
    }

    // ================= VISTAS =================
    private void inicializarVistas() {
        recyclerView = findViewById(R.id.rvConversaciones);
        etBuscar = findViewById(R.id.etBuscar);

        // 🔴 SE CREA POR CÓDIGO (NO XML)
        tvSinResultados = new TextView(this);
        tvSinResultados.setText("No se encontraron conversaciones");
        tvSinResultados.setTextColor(COLOR_TEXT_SECONDARY);
        tvSinResultados.setTextSize(16);
        tvSinResultados.setGravity(Gravity.CENTER);
        tvSinResultados.setVisibility(View.GONE);

        ((ViewGroup) recyclerView.getParent()).addView(tvSinResultados);
    }

    // ================= RECYCLER =================
    private void configurarRecycler() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        adapter = new ConversacionAdapter(conversacionesFiltradas);
        recyclerView.setAdapter(adapter);
    }

    // ================= DATOS =================
    private void generarConversaciones() {
        String[] nombres = {
                "María", "Carlos", "Laura", "Andrés", "Sofía",
                "Julián", "Paula", "Mateo", "Valentina", "Daniel"
        };

        String[] mensajes = {
                "¿Ya vienes?",
                "Voy en camino 🚗",
                "Perfecto 👍",
                "Gracias por el viaje",
                "Llegué",
                "Excelente servicio ⭐"
        };

        Random r = new Random();

        for (int i = 0; i < 30; i++) {
            todasConversaciones.add(new Conversacion(
                    nombres[r.nextInt(nombres.length)],
                    mensajes[r.nextInt(mensajes.length)],
                    obtenerHora(),
                    bankFotos[i % bankFotos.length]
            ));
        }

        conversacionesFiltradas.addAll(todasConversaciones);
        adapter.notifyDataSetChanged();
    }

    private String obtenerHora() {
        Calendar c = Calendar.getInstance();
        return String.format("%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE));
    }

    // ================= BUSCADOR =================
    private void configurarBuscador() {
        etBuscar.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {}

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                conversacionesFiltradas.clear();

                for (Conversacion conv : todasConversaciones) {
                    if (conv.nombre.toLowerCase().contains(s.toString().toLowerCase())) {
                        conversacionesFiltradas.add(conv);
                    }
                }

                boolean vacio = conversacionesFiltradas.isEmpty();
                recyclerView.setVisibility(vacio ? View.GONE : View.VISIBLE);
                tvSinResultados.setVisibility(vacio ? View.VISIBLE : View.GONE);

                adapter.notifyDataSetChanged();
            }
        });
    }

    // ================= BOTTOM NAV =================
    private void configurarBottomNav() {
        bottomNavigation = findViewById(R.id.bottom_navigation);
        bottomNavigation.setSelectedItemId(R.id.nav_mensajes);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_inicio) {
                startActivity(new Intent(this, HomeConductor.class));
            } else if (id == R.id.nav_mis_viajes) {
                startActivity(new Intent(this, PublicarViaje.class));
            } else if (id == R.id.nav_mapa) {
                startActivity(new Intent(this, ViajesPasados.class));
            } else if (id == R.id.nav_mensajes) {
                return true;
            } else if (id == R.id.nav_perfil) {
                startActivity(new Intent(this, PerfilUsuario.class));
            }

            overridePendingTransition(0, 0);
            finish();
            return true;
        });
    }

    // ================= ANIMACIÓN =================
    private void animarEntrada() {
        recyclerView.setAlpha(0f);
        recyclerView.animate()
                .alpha(1f)
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ================= MODELO =================
    static class Conversacion {
        String nombre, mensaje, hora;
        int foto;

        Conversacion(String n, String m, String h, int f) {
            nombre = n;
            mensaje = m;
            hora = h;
            foto = f;
        }
    }

    // ================= ADAPTER =================
    class ConversacionAdapter extends RecyclerView.Adapter<ConversacionAdapter.ViewHolder> {

        private final List<Conversacion> lista;

        ConversacionAdapter(List<Conversacion> l) {
            lista = l;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            LinearLayout card = new LinearLayout(parent.getContext());
            card.setPadding(30, 25, 30, 25);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(30);
            card.setBackground(bg);

            ImageView avatar = new ImageView(parent.getContext());
            avatar.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);

            LinearLayout center = new LinearLayout(parent.getContext());
            center.setOrientation(LinearLayout.VERTICAL);
            center.setPadding(30, 0, 0, 0);
            center.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));

            TextView nombre = new TextView(parent.getContext());
            nombre.setTypeface(null, Typeface.BOLD);
            nombre.setTextSize(16);

            TextView mensaje = new TextView(parent.getContext());
            mensaje.setTextColor(COLOR_TEXT_SECONDARY);

            TextView hora = new TextView(parent.getContext());
            hora.setTextColor(COLOR_PRIMARY);
            hora.setTextSize(12);

            center.addView(nombre);
            center.addView(mensaje);

            card.addView(avatar);
            card.addView(center);
            card.addView(hora);

            return new ViewHolder(card, avatar, nombre, mensaje, hora);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            Conversacion c = lista.get(p);
            h.avatar.setImageResource(c.foto);
            h.nombre.setText(c.nombre);
            h.mensaje.setText(c.mensaje);
            h.hora.setText(c.hora);

            h.itemView.setOnClickListener(v ->
                    Toast.makeText(v.getContext(),
                            "Chat con " + c.nombre,
                            Toast.LENGTH_SHORT).show());
        }

        @Override
        public int getItemCount() {
            return lista.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView avatar;
            TextView nombre, mensaje, hora;

            ViewHolder(View v, ImageView a, TextView n, TextView m, TextView h) {
                super(v);
                avatar = a;
                nombre = n;
                mensaje = m;
                hora = h;
            }
        }
    }
}
