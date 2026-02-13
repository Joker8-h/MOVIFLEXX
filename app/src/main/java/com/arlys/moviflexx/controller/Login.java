package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SesionUsuario;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import org.json.JSONException;
import org.json.JSONObject;

public class Login extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;
    private TextInputEditText edtEmail, edtPassword;
    private SessionManager sessionManager;

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getData() == null) return;
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        if (task.isSuccessful()) {
                            GoogleSignInAccount account = task.getResult();
                            firebaseAuthWithGoogle(account.getIdToken());
                        } else {
                            Toast.makeText(this, "Error Google Sign-In", Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);

        configurarGoogle();
    }

    public void login(View view) {
        String email = edtEmail.getText() != null ? edtEmail.getText().toString().trim() : "";
        String password = edtPassword.getText() != null ? edtPassword.getText().toString().trim() : "";

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Complete todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject json = new JSONObject();
        try {
            json.put("email", email);
            json.put("password", password);
        } catch (JSONException e) {
            return;
        }

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constantes.LOGIN,
                json,
                response -> {
                    try {
                        String token = null;
                        int idUsuario = -1;
                        int idRol = -1;
                        String nombre = "";
                        String emailReal = "";
                        String telefono = "";

                        // BUSCAR TOKEN
                        if (response.has("token")) token = response.getString("token");
                        else if (response.has("accessToken")) token = response.getString("accessToken");

                        // BUSCAR DATOS DEL USUARIO
                        if (response.has("usuario")) {
                            JSONObject usuario = response.getJSONObject("usuario");

                            idUsuario = usuario.optInt("idUsuarios", usuario.optInt("id", -1));
                            nombre = usuario.optString("nombre", "Usuario");
                            emailReal = usuario.optString("email", email);
                            telefono = usuario.optString("telefono", "Sin teléfono");

                            // Lógica de Rol
                            if (usuario.has("idRol")) {
                                idRol = usuario.getInt("idRol");
                            } else if (usuario.has("rol")) {
                                JSONObject rolObj = usuario.getJSONObject("rol");
                                idRol = rolObj.optInt("idRol", -1);
                            }
                        }

                        if (token == null || idUsuario == -1 || idRol == -1) {
                            Toast.makeText(this, "Error de credenciales", Toast.LENGTH_LONG).show();
                            return;
                        }

                        // ⚡ CRÍTICO: Guardar TODO en el orden correcto

                        // 1. Guardar token
                        sessionManager.saveToken(token);
                        SesionUsuario.setToken(token);

                        // 2. Guardar datos de usuario
                        sessionManager.saveUser(nombre, emailReal, telefono, idRol, idUsuario);
                        SesionUsuario.setIdUsuario(idUsuario);
                        SesionUsuario.setIdRol(idRol);

                        // 3. Marcar como logged in
                        sessionManager.setLoggedIn(true);

                        // 4. Cargar a memoria
                        sessionManager.loadSessionToMemory();

                        Toast.makeText(this, "Bienvenido " + nombre, Toast.LENGTH_SHORT).show();

                        // Navegar según rol
                        Intent intent = (idRol == 2) ?
                                new Intent(this, HomeConductor.class) :
                                new Intent(this, HomePasajero.class);

                        startActivity(intent);
                        finish();

                    } catch (JSONException e) {
                        Toast.makeText(this, "Error en respuesta del servidor", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                },
                error -> {
                    String mensaje = "Error de conexión";
                    if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                        mensaje = "Email o contraseña incorrectos";
                    }
                    Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
                }
        );
        queue.add(request);
    }

    private void configurarGoogle() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail().build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        findViewById(R.id.btnGoogle).setOnClickListener(v -> googleLauncher.launch(googleSignInClient.getSignInIntent()));
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                // ⚡ IMPORTANTE: Marcar como logged in
                sessionManager.setLoggedIn(true);
                startActivity(new Intent(Login.this, HomePasajero.class));
                finish();
            }
        });
    }

    public void irRegister(View view) {
        startActivity(new Intent(Login.this, Register.class));
    }
}