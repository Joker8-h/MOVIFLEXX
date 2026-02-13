package com.arlys.moviflexx.controller;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Mensaje;
import com.arlys.moviflexx.model.MensajeAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Chat extends AppCompatActivity {

    private RecyclerView recycler;
    private MensajeAdapter adapter;
    private List<Mensaje> mensajes = new ArrayList<>();

    private EditText etMensaje;
    private ImageButton btnEnviar;
    private TextView tvNombre;

    private String idConversacion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // 📥 DATOS
        idConversacion = getIntent().getStringExtra("idConversacion");
        String nombre = getIntent().getStringExtra("nombre");

        // 🔗 UI
        recycler = findViewById(R.id.rvMensajes);
        etMensaje = findViewById(R.id.etMensaje);
        btnEnviar = findViewById(R.id.btnEnviar);
        tvNombre = findViewById(R.id.tvNombreChat);

        tvNombre.setText(nombre);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MensajeAdapter(mensajes);
        recycler.setAdapter(adapter);

        cargarMensajes();

        btnEnviar.setOnClickListener(v -> enviarMensaje());
    }

    // 📡 CARGAR MENSAJES
    private void cargarMensajes() {
        String url = Constantes.CHAT_MENSAJES
                + idConversacion + "/mensajes";

        ConexionApi.getInstance(this).getArray(
                url,
                this::procesarMensajes,
                error -> error.printStackTrace()
        );
    }

    private void procesarMensajes(JSONArray arr) {
        mensajes.clear();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            mensajes.add(Mensaje.fromJson(obj));
        }

        adapter.notifyDataSetChanged();
        recycler.scrollToPosition(mensajes.size() - 1);
    }

    // ✉️ ENVIAR MENSAJE
    private void enviarMensaje() {
        String texto = etMensaje.getText().toString().trim();
        if (texto.isEmpty()) return;

        JSONObject body = new JSONObject();
        try {
            body.put("conversacionId", idConversacion);
            body.put("mensaje", texto);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ConexionApi.getInstance(this).post(
                Constantes.CHAT_MENSAJES,
                body,
                res -> {
                    etMensaje.setText("");
                    cargarMensajes();
                },
                err -> err.printStackTrace()
        );
    }
}
