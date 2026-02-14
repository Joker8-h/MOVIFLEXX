package com.arlys.moviflexx.controller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.SesionUsuario;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FaceLogin extends AppCompatActivity {

    private static final String TAG = "FACE_LOGIN_DEBUG";
    private static final int REQUEST_CAMERA_PERM = 100;
    private static final long ANALYSIS_INTERVAL_MS = 2000; // Analizar cada 2 segundos

    private PreviewView previewView;
    private TextView txtEstado;
    private ExecutorService cameraExecutor;
    private SessionManager sessionManager;

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private long lastAnalysisTime = 0;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_login);

        previewView = findViewById(R.id.previewView);
        txtEstado = findViewById(R.id.txtEstado);
        sessionManager = new SessionManager(this);

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (verificarPermisoCamara()) {
            iniciarCamara();
        } else {
            solicitarPermisoCamara();
        }
    }

    private boolean verificarPermisoCamara() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void solicitarPermisoCamara() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CAMERA_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarCamara();
            } else {
                Toast.makeText(this, "Permiso de cámara requerido", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void iniciarCamara() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (Exception e) {
                Log.e(TAG, "Error iniciando cámara: " + e.getMessage());
                Toast.makeText(this, "Error iniciando cámara", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        // Preview
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image Analysis para detección facial
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analizarImagen);

        // Cámara frontal
        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis);

            runOnUiThread(() ->
                    txtEstado.setText("📷 Coloca tu rostro frente a la cámara..."));

        } catch (Exception e) {
            Log.e(TAG, "Error vinculando cámara: " + e.getMessage());
        }
    }

    private void analizarImagen(@NonNull ImageProxy image) {
        // Control de frecuencia: solo analizar cada ANALYSIS_INTERVAL_MS
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAnalysisTime < ANALYSIS_INTERVAL_MS) {
            image.close();
            return;
        }

        // Evitar múltiples análisis simultáneos
        if (isProcessing.get()) {
            image.close();
            return;
        }

        lastAnalysisTime = currentTime;
        isProcessing.set(true);

        try {
            // Convertir ImageProxy a Bitmap
            Bitmap bitmap = imageProxyToBitmap(image);

            if (bitmap != null) {
                // Enviar para verificación
                verificarRostro(bitmap);
            } else {
                isProcessing.set(false);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error analizando imagen: " + e.getMessage());
            isProcessing.set(false);
        } finally {
            image.close();
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, image.getWidth(), image.getHeight()),
                    80, out);

            byte[] imageBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        } catch (Exception e) {
            Log.e(TAG, "Error convirtiendo ImageProxy: " + e.getMessage());
            return null;
        }
    }

    private void verificarRostro(Bitmap bitmap) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            String imageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

            JSONObject json = new JSONObject();
            json.put("image", imageBase64);

            Log.d(TAG, "Verificando rostro → " + Constantes.FACE_VERIFY);

            runOnUiThread(() ->
                    txtEstado.setText("🔍 Analizando rostro..."));

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(Constantes.FACE_VERIFY)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Fallo de red: " + e.getMessage());
                    isProcessing.set(false);
                    runOnUiThread(() -> {
                        txtEstado.setText("❌ Error de conexión. Reintentando...");
                        Toast.makeText(FaceLogin.this,
                                "Sin conexión: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null
                            ? response.body().string() : "";
                    Log.d(TAG, "HTTP " + response.code() + " | " + responseBody);

                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            procesarRespuestaExitosa(responseBody);
                        } else {
                            isProcessing.set(false);
                            txtEstado.setText("📷 Rostro no reconocido. Intenta de nuevo...");

                            if (response.code() == 404) {
                                Toast.makeText(FaceLogin.this,
                                        "Rostro no registrado",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error procesando imagen: " + e.getMessage());
            isProcessing.set(false);
            runOnUiThread(() ->
                    txtEstado.setText("❌ Error procesando imagen"));
        }
    }

    private void procesarRespuestaExitosa(String responseBody) {
        try {
            JSONObject resp = new JSONObject(responseBody);

            // Extraer datos del usuario
            String token = null;
            int idUsuario = -1;
            int idRol = -1;
            String nombre = "";
            String email = "";
            String telefono = "";

            if (resp.has("token")) {
                token = resp.getString("token");
            } else if (resp.has("accessToken")) {
                token = resp.getString("accessToken");
            }

            if (resp.has("usuario")) {
                JSONObject u = resp.getJSONObject("usuario");
                idUsuario = u.optInt("idUsuarios", u.optInt("id", -1));
                nombre = u.optString("nombre", "Usuario");
                email = u.optString("email", "");
                telefono = u.optString("telefono", "Sin teléfono");

                if (u.has("idRol")) {
                    idRol = u.getInt("idRol");
                } else if (u.has("rol")) {
                    idRol = u.getJSONObject("rol").optInt("idRol", -1);
                }
            }

            // Guardar sesión
            if (token != null && idUsuario != -1 && idRol != -1) {
                sessionManager.saveToken(token);
                SesionUsuario.setToken(token);
                sessionManager.saveUser(nombre, email, telefono, idRol, idUsuario);
                SesionUsuario.setIdUsuario(idUsuario);
                SesionUsuario.setIdRol(idRol);
                sessionManager.setLoggedIn(true);
                sessionManager.loadSessionToMemory();
            }

            txtEstado.setText("✅ Reconocimiento exitoso!");
            Toast.makeText(this, "¡Bienvenido " + nombre + "!", Toast.LENGTH_SHORT).show();

            // Navegar según el rol
            Intent intent = new Intent(this,
                    idRol == 2 ? HomeConductor.class : HomePasajero.class);
            startActivity(intent);
            finish();

        } catch (JSONException e) {
            Log.e(TAG, "Error parseando respuesta: " + e.getMessage());

            // Si no hay estructura completa, asumir éxito básico
            txtEstado.setText("✅ Reconocimiento exitoso!");
            Toast.makeText(this, "Reconocimiento exitoso", Toast.LENGTH_SHORT).show();

            startActivity(new Intent(this, HomePasajero.class));
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}