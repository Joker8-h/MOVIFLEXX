/*package com.arlys.moviflexx.controller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Constantes;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

public class FaceRegister extends AppCompatActivity {

    private static final int REQUEST_IMAGE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private String email;
    private Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_register);

        email = getIntent().getStringExtra("email");

        verificarPermisoCamara();
    }

    private void verificarPermisoCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            abrirCamara();
        }
    }

    private void abrirCamara() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(takePictureIntent, REQUEST_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                abrirCamara();
            } else {
                Toast.makeText(this,
                        "Permiso de cámara requerido",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE
                && resultCode == RESULT_OK
                && data != null) {

            Bitmap original = (Bitmap) data.getExtras().get("data");
            bitmap = Bitmap.createScaledBitmap(original, 300, 300, true);

            enviarRostro();
        } else {
            finish();
        }
    }

    private void enviarRostro() {

        if (bitmap == null || email == null) return;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 25, baos);

        String imageBase64 = Base64.encodeToString(
                baos.toByteArray(),
                Base64.NO_WRAP
        );

        JSONObject json = new JSONObject();
        try {
            json.put("email", email);
            json.put("image", imageBase64);
        } catch (JSONException e) {
            return;
        }

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constantes.FACE_REGISTER,
                json,
                response -> {
                    Toast.makeText(this,
                            "Rostro registrado correctamente",
                            Toast.LENGTH_LONG).show();

                    startActivity(new Intent(this, Login.class));
                    finish();
                },
                error -> {
                    String mensaje = "Error registrando rostro";

                    if (error.networkResponse != null) {
                        mensaje += " | Código: " + error.networkResponse.statusCode;
                    }

                    Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
                }
        );

        queue.add(request);
    }
}*/