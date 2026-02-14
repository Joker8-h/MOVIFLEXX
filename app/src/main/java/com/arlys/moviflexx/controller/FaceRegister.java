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
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FaceRegister extends AppCompatActivity {

    private static final String TAG = "FACE_REGISTER_DEBUG";
    private static final int REQUEST_CAMERA_PERM = 200;
    private static final long ANALYSIS_INTERVAL_MS = 2000; // Analizar cada 2 segundos

    // ─── Cloudinary ───────────────────────────────────────────────────────────
    private static final String CLOUDINARY_CLOUD_NAME = "davda0bon";
    private static final String CLOUDINARY_UPLOAD_PRESET = "moviflexx_preset"; // ← CAMBIO AQUÍ
    private static final String CLOUDINARY_UPLOAD_URL =
            "https://api.cloudinary.com/v1_1/" + CLOUDINARY_CLOUD_NAME + "/image/upload";

    private PreviewView previewView;
    private TextView txtEstado;
    private ExecutorService cameraExecutor;
    private String email;

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
        setContentView(R.layout.activity_face_register);

        previewView = findViewById(R.id.previewView);
        txtEstado = findViewById(R.id.txtEstado);

        email = getIntent().getStringExtra("email");

        if (email == null || email.isEmpty()) {
            Toast.makeText(this, "Error: email no recibido", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.d(TAG, "Email recibido: " + email);
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
                // Subir a Cloudinary
                subirFotoCloudinary(bitmap);
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

    // ─── Paso 1: Subir foto a Cloudinary ──────────────────────────────────────
    private void subirFotoCloudinary(Bitmap bitmap) {
        try {
            // Convertir Bitmap a bytes JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] imageBytes = baos.toByteArray();

            // Construir multipart/form-data para Cloudinary
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
                    .addFormDataPart("file", "rostro.jpg",
                            RequestBody.create(imageBytes,
                                    MediaType.parse("image/jpeg")))
                    .build();

            Request request = new Request.Builder()
                    .url(CLOUDINARY_UPLOAD_URL)
                    .post(requestBody)
                    .build();

            Log.d(TAG, "Subiendo a Cloudinary: " + CLOUDINARY_UPLOAD_URL);
            Log.d(TAG, "Upload preset: " + CLOUDINARY_UPLOAD_PRESET);

            runOnUiThread(() ->
                    txtEstado.setText("📤 Subiendo imagen a Cloudinary..."));

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Cloudinary fallo: " + e.getMessage());
                    isProcessing.set(false);
                    runOnUiThread(() -> {
                        txtEstado.setText("❌ Error subiendo foto. Reintentando...");
                        Toast.makeText(FaceRegister.this,
                                "Error subiendo foto: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Cloudinary HTTP " + response.code() + " | " + body);

                    if (response.isSuccessful()) {
                        try {
                            // Extraer la URL segura de la respuesta JSON de Cloudinary
                            JSONObject jsonResponse = new JSONObject(body);
                            String imageUrl = jsonResponse.getString("secure_url");
                            Log.d(TAG, "URL Cloudinary: " + imageUrl);

                            runOnUiThread(() -> txtEstado.setText("📥 Descargando de Cloudinary..."));

                            // Paso 2: Descargar de Cloudinary y convertir a Base64
                            descargarYEnviarBase64(imageUrl);

                        } catch (JSONException e) {
                            Log.e(TAG, "Error parseando Cloudinary: " + e.getMessage());
                            isProcessing.set(false);
                            runOnUiThread(() -> {
                                txtEstado.setText("📷 Error procesando. Reintentando...");
                                Toast.makeText(FaceRegister.this,
                                        "Error procesando respuesta de Cloudinary",
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                    } else {
                        Log.e(TAG, "Cloudinary error: " + body);
                        isProcessing.set(false);
                        runOnUiThread(() -> {
                            txtEstado.setText("📷 Error en Cloudinary. Reintentando...");
                            Toast.makeText(FaceRegister.this,
                                    "Error en Cloudinary (" + response.code() + "): " + body,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error preparando imagen: " + e.getMessage());
            isProcessing.set(false);
            runOnUiThread(() ->
                    txtEstado.setText("📷 Error procesando imagen"));
        }
    }

    // ─── Paso 2: Descargar imagen de Cloudinary y convertir a Base64 ──────────
    private void descargarYEnviarBase64(String imageUrl) {
        new Thread(() -> {
            try {
                // Descargar imagen desde Cloudinary
                URL url = new URL(imageUrl);
                InputStream inputStream = url.openConnection().getInputStream();
                Bitmap downloadedBitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                // Convertir a Base64
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                downloadedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                String imageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                Log.d(TAG, "Imagen descargada y convertida a Base64");

                runOnUiThread(() -> txtEstado.setText("🔍 Registrando rostro..."));

                // Enviar al backend
                enviarRostroBase64(imageBase64);

            } catch (Exception e) {
                Log.e(TAG, "Error descargando de Cloudinary: " + e.getMessage());
                isProcessing.set(false);
                runOnUiThread(() -> {
                    txtEstado.setText("❌ Error descargando. Reintentando...");
                    Toast.makeText(FaceRegister.this,
                            "Error descargando imagen de Cloudinary",
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ─── Paso 3: Enviar Base64 al backend ─────────────────────────────────────
    private void enviarRostroBase64(String imageBase64) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("image", imageBase64);  // ← Backend espera "image" con Base64

            Log.d(TAG, "Enviando a: " + Constantes.FACE_REGISTER);
            Log.d(TAG, "Email: " + email);
            Log.d(TAG, "Tamaño Base64: " + imageBase64.length() + " caracteres");

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(Constantes.FACE_REGISTER)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Fallo de red: " + e.getMessage());
                    isProcessing.set(false);
                    runOnUiThread(() -> {
                        txtEstado.setText("❌ Error de conexión. Reintentando...");
                        Toast.makeText(FaceRegister.this,
                                "Sin conexión: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null
                            ? response.body().string() : "";
                    Log.d(TAG, "Backend HTTP " + response.code() + " | " + responseBody);

                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            txtEstado.setText("✅ Rostro registrado exitosamente!");
                            Toast.makeText(FaceRegister.this,
                                    "Rostro registrado correctamente",
                                    Toast.LENGTH_LONG).show();

                            // Ir al login después de 1 segundo
                            previewView.postDelayed(() -> {
                                startActivity(new Intent(FaceRegister.this, Login.class));
                                finish();
                            }, 1000);

                        } else {
                            isProcessing.set(false);
                            txtEstado.setText("📷 Error registrando rostro. Reintentando...");

                            String errorMsg = "Error " + response.code();
                            try {
                                JSONObject errorJson = new JSONObject(responseBody);
                                if (errorJson.has("message")) {
                                    errorMsg = errorJson.getString("message");
                                } else if (errorJson.has("error")) {
                                    errorMsg = errorJson.getString("error");
                                }
                            } catch (Exception e) {
                                errorMsg += ": " + responseBody;
                            }

                            String finalErrorMsg = errorMsg;
                            Toast.makeText(FaceRegister.this,
                                    finalErrorMsg,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error procesando envío: " + e.getMessage());
            isProcessing.set(false);
            runOnUiThread(() ->
                    txtEstado.setText("📷 Error procesando"));
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
