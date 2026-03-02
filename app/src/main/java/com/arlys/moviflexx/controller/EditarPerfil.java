package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

/**
 * EditarPerfil — extends BaseActivity.
 * Navbar idéntico al de HomeConductor: goTo() + Transition.NONE.
 */
public class EditarPerfil extends BaseActivity {

    private SessionManager session;

    private TextInputEditText editNombre, editTelefono, editEmail,
            editModelo, editAnio, editColor, editMatricula;

    private MaterialButton       btnGuardarCambios;
    private BottomNavigationView bottomNavigation;

    // ═════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editar_perfil);

        session = new SessionManager(this);

        bindViews();
        cargarDatosActuales();
        configurarWatcher();
        configurarBotones();
        configurarBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Marcar item activo igual que HomeConductor
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav != null) nav.setSelectedItemId(R.id.nav_perfil);
    }

    // ═════════════════════════════════════════════════════════════
    //  BIND
    // ═════════════════════════════════════════════════════════════
    private void bindViews() {
        editNombre        = findViewById(R.id.edit_nombre);
        editTelefono      = findViewById(R.id.edit_telefono);
        editEmail         = findViewById(R.id.edit_email);
        editModelo        = findViewById(R.id.edit_modelo);
        editAnio          = findViewById(R.id.edit_anio);
        editColor         = findViewById(R.id.edit_color);
        editMatricula     = findViewById(R.id.edit_matricula);
        btnGuardarCambios = findViewById(R.id.btn_guardar_cambios);
        bottomNavigation  = findViewById(R.id.bottom_navigation);
    }

    // ═════════════════════════════════════════════════════════════
    //  DATOS ACTUALES
    // ═════════════════════════════════════════════════════════════
    private void cargarDatosActuales() {
        if (editNombre   != null) editNombre.setText(session.getNombre());
        if (editTelefono != null) editTelefono.setText(session.getTelefono());

        bloquearCampo(editEmail,     session.getEmail());
        bloquearCampo(editModelo,    session.getVModelo());
        bloquearCampo(editMatricula, session.getVPlaca());
        bloquearCampo(editAnio,      "");
        bloquearCampo(editColor,     "");

        deshabilitarBoton();
    }

    private void bloquearCampo(TextInputEditText campo, String texto) {
        if (campo == null) return;
        campo.setText(texto);
        campo.setEnabled(false);
        campo.setFocusable(false);
        campo.setFocusableInTouchMode(false);
        campo.setAlpha(0.5f);
    }

    // ═════════════════════════════════════════════════════════════
    //  WATCHER
    // ═════════════════════════════════════════════════════════════
    private void configurarWatcher() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                verificarCambios();
            }
        };
        if (editNombre   != null) editNombre.addTextChangedListener(watcher);
        if (editTelefono != null) editTelefono.addTextChangedListener(watcher);
    }

    private void verificarCambios() {
        String nuevoNombre   = editNombre   != null ? editNombre.getText().toString().trim()  : "";
        String nuevoTelefono = editTelefono != null ? editTelefono.getText().toString().trim() : "";
        boolean hayCambio = !nuevoNombre.equals(session.getNombre())
                || !nuevoTelefono.equals(session.getTelefono());
        if (hayCambio) habilitarBoton(); else deshabilitarBoton();
    }

    private void habilitarBoton() {
        if (btnGuardarCambios == null) return;
        btnGuardarCambios.setEnabled(true);
        btnGuardarCambios.setAlpha(1f);
    }

    private void deshabilitarBoton() {
        if (btnGuardarCambios == null) return;
        btnGuardarCambios.setEnabled(false);
        btnGuardarCambios.setAlpha(0.5f);
    }

    // ═════════════════════════════════════════════════════════════
    //  BOTONES — animateButton igual que HomeConductor
    // ═════════════════════════════════════════════════════════════
    private void configurarBotones() {
        if (btnGuardarCambios != null)
            animateButton(btnGuardarCambios, this::guardarCambios);

        View btnCancelar = findViewById(R.id.btn_cancelar);
        if (btnCancelar != null)
            btnCancelar.setOnClickListener(v -> finish());

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null)
            btnBack.setOnClickListener(v -> finish());
    }

    // ═════════════════════════════════════════════════════════════
    //  GUARDAR
    // ═════════════════════════════════════════════════════════════
    private void guardarCambios() {
        String nuevoNombre   = editNombre   != null ? editNombre.getText().toString().trim()  : "";
        String nuevoTelefono = editTelefono != null ? editTelefono.getText().toString().trim() : "";

        if (nuevoNombre.isEmpty()) {
            Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show();
            return;
        }

        deshabilitarBoton();

        try {
            JSONObject body = new JSONObject();
            body.put("nombre",   nuevoNombre);
            body.put("telefono", nuevoTelefono);

            ConexionApi.getInstance(this).put(
                    Constantes.usuarioDetalle((long) session.getIdUsuario()),
                    body,
                    response -> {
                        session.actualizarPerfil(nuevoNombre, nuevoTelefono);
                        Toast.makeText(this, "✅ Perfil actualizado", Toast.LENGTH_SHORT).show();
                        // Volver al perfil con slide
                        goTo(PerfilUsuario.class, Transition.SLIDE, true);
                    },
                    error -> {
                        habilitarBoton();
                        String msg = "Error al actualizar perfil";
                        if (error != null && error.networkResponse != null) {
                            int code = error.networkResponse.statusCode;
                            if      (code == 400) msg = "Datos inválidos";
                            else if (code == 403) msg = "Sin permiso para editar";
                            else if (code == 404) msg = "Usuario no encontrado";
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
            );
        } catch (Exception e) {
            habilitarBoton();
            Toast.makeText(this, "Error interno", Toast.LENGTH_SHORT).show();
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  NAVBAR — mismo patrón que HomeConductor
    // ═════════════════════════════════════════════════════════════
    private void configurarBottomNav() {
        if (bottomNavigation == null) return;

        bottomNavigation.setSelectedItemId(R.id.nav_perfil);
        boolean esConductor = session.getIdRol() == 2;

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_perfil) return true;   // ya estamos aquí

            if (esConductor) {
                if      (id == R.id.nav_inicio)     goTo(HomeConductor.class,  Transition.NONE);
                else if (id == R.id.nav_mis_viajes) goTo(PublicarRuta.class,   Transition.NONE);
                else if (id == R.id.nav_mapa)       goTo(Mapa.class,           Transition.NONE);
                else if (id == R.id.nav_mensajes)   goTo(Mensajes.class,       Transition.NONE);
            } else {
                if      (id == R.id.nav_inicio)     goTo(HomePasajero.class,        Transition.NONE);
                else if (id == R.id.nav_mis_viajes) goTo(MisReservasActivity.class, Transition.NONE);
                else if (id == R.id.nav_mapa)       goTo(Mapa.class,               Transition.NONE);
                else if (id == R.id.nav_mensajes)   goTo(Mensajes.class,           Transition.NONE);
            }

            finish();
            return true;
        });
    }

    // ═════════════════════════════════════════════════════════════
    //  onClick desde XML
    // ═════════════════════════════════════════════════════════════
    public void irPerfil(View view) {
        goTo(PerfilUsuario.class, Transition.SLIDE, true);
    }
}