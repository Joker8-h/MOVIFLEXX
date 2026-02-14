package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Login extends AppCompatActivity {

    private static final String TAG = "LOGIN_DEBUG";

    private FirebaseAuth      mAuth;
    private GoogleSignInClient googleSignInClient;
    private TextInputEditText  edtEmail, edtPassword;
    private SessionManager     sessionManager;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getData() == null) return;
                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        if (task.isSuccessful()) {
                            firebaseAuthWithGoogle(task.getResult().getIdToken());
                        } else {
                            Toast.makeText(this,
                                    "Error Google Sign-In", Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth          = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);
        edtEmail       = findViewById(R.id.edtEmail);
        edtPassword    = findViewById(R.id.edtPassword);

        configurarGoogle();
    }

    // ─── Login email + contraseña ─────────────────────────────────────────────
    public void login(View view) {
        String email    = edtEmail.getText() != null
                ? edtEmail.getText().toString().trim() : "";
        String password = edtPassword.getText() != null
                ? edtPassword.getText().toString().trim() : "";

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this,
                    "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject json = new JSONObject();
        try {
            json.put("email",    email);
            json.put("password", password);
        } catch (JSONException e) {
            return;
        }

        Log.d(TAG, "Login → " + Constantes.LOGIN);

        final String emailFinal = email;

        client.newCall(new Request.Builder()
                .url(Constantes.LOGIN)
                .post(RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json; charset=utf-8")))
                .build()
        ).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Fallo: " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(Login.this,
                                "Sin conexión: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null
                        ? response.body().string() : "";
                Log.d(TAG, "HTTP " + response.code() + " | " + body);

                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        procesarRespuestaLogin(body, emailFinal);
                    } else {
                        String msg;
                        switch (response.code()) {
                            case 401: msg = "Email o contraseña incorrectos"; break;
                            case 404: msg = "Usuario no encontrado";          break;
                            case 500: msg = "Error del servidor";             break;
                            default:  msg = "Error " + response.code() + ": " + body;
                        }
                        Toast.makeText(Login.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    // ─── Procesar JSON de respuesta ───────────────────────────────────────────
    private void procesarRespuestaLogin(String responseBody, String emailFallback) {
        try {
            JSONObject resp     = new JSONObject(responseBody);
            String     token    = null;
            int        idUsuario= -1;
            int        idRol    = -1;
            String     nombre   = "";
            String     email    = "";
            String     telefono = "";

            if (resp.has("token"))
                token = resp.getString("token");
            else if (resp.has("accessToken"))
                token = resp.getString("accessToken");

            if (resp.has("usuario")) {
                JSONObject u = resp.getJSONObject("usuario");
                idUsuario = u.optInt("idUsuarios", u.optInt("id", -1));
                nombre    = u.optString("nombre",   "Usuario");
                email     = u.optString("email",    emailFallback);
                telefono  = u.optString("telefono", "Sin teléfono");

                if (u.has("idRol")) {
                    idRol = u.getInt("idRol");
                } else if (u.has("rol")) {
                    idRol = u.getJSONObject("rol").optInt("idRol", -1);
                }
            }

            if (token == null || idUsuario == -1 || idRol == -1) {
                Toast.makeText(this,
                        "Error de credenciales", Toast.LENGTH_LONG).show();
                return;
            }

            sessionManager.saveToken(token);
            SesionUsuario.setToken(token);
            sessionManager.saveUser(nombre, email, telefono, idRol, idUsuario);
            SesionUsuario.setIdUsuario(idUsuario);
            SesionUsuario.setIdRol(idRol);
            sessionManager.setLoggedIn(true);
            sessionManager.loadSessionToMemory();

            Toast.makeText(this, "Bienvenido " + nombre, Toast.LENGTH_SHORT).show();

            startActivity(new Intent(this,
                    idRol == 2 ? HomeConductor.class : HomePasajero.class));
            finish();

        } catch (JSONException e) {
            Log.e(TAG, "Error parseando: " + e.getMessage());
            Toast.makeText(this,
                    "Error en respuesta del servidor", Toast.LENGTH_SHORT).show();
        }
    }

    // ─── Login facial — abre FaceLogin ───────────────────────────────────────
    public void irFaceLogin(View view) {
        startActivity(new Intent(this, FaceLogin.class));
    }

    // ─── Google ──────────────────────────────────────────────────────────────
    private void configurarGoogle() {
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        findViewById(R.id.btnGoogle).setOnClickListener(v ->
                googleLauncher.launch(googleSignInClient.getSignInIntent()));
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        sessionManager.setLoggedIn(true);
                        startActivity(new Intent(Login.this, HomePasajero.class));
                        finish();
                    } else {
                        Toast.makeText(this,
                                "Error autenticando con Google",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ─── Ir a registro ───────────────────────────────────────────────────────
    public void irRegister(View view) {
        startActivity(new Intent(this, Register.class));
    }
}