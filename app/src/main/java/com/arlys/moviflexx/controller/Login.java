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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

public class Login extends AppCompatActivity {

    // Firebase
    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;

    // Inputs
    private TextInputEditText edtEmail, edtPassword;

    // Google launcher
    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getData() == null) return;

                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData());

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

        // Firebase
        mAuth = FirebaseAuth.getInstance();

        // Inputs XML
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);

        // Google
        configurarGoogle();
    }

    // =====================================
    // 🔵 LOGIN NORMAL (RAILWAY + MYSQL)
    // =====================================
    public void irSuccess(View view) {

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
            Toast.makeText(this, "Error creando JSON", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constantes.LOGIN,
                json,
                response -> {
                    Toast.makeText(this, "Login exitoso", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(Login.this, Success.class));
                    finish();
                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        String body = new String(error.networkResponse.data);
                        Toast.makeText(this, body, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                "No se pudo conectar con el servidor",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );

        queue.add(request);
    }

    // =====================================
    // 🔵 GOOGLE LOGIN (FIREBASE) — INTACTO
    // =====================================
    private void configurarGoogle() {

        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        MaterialButton btnGoogle = findViewById(R.id.btnGoogle);

        btnGoogle.setOnClickListener(v -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleLauncher.launch(signInIntent);
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {

        AuthCredential credential =
                GoogleAuthProvider.getCredential(idToken, null);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        startActivity(new Intent(Login.this, Success.class));
                        finish();
                    } else {
                        Toast.makeText(this,
                                "Error autenticando con Google",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // =====================================
    // 🔵 IR A REGISTRO
    // =====================================
    public void irRegister(View view) {
        startActivity(new Intent(Login.this, Register.class));
    }
}
