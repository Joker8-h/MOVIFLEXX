package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.View;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class Register extends AppCompatActivity {

    private EditText edtNombre, edtPhone, edtEmail, edtPassword, edtConfirmPassword;
    private ImageButton btnShowPass, btnShowPassConfirm;
    private Spinner spinnerTipo;
    private CheckBox cbTerms;

    private LinearLayout layoutVehicle;
    private EditText edtPlaca, edtModelo, edtColor;
    private ImageView imgPreview;
    private Uri imagenSeleccionada;

    private static final int PICK_IMAGE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupSpinner();
        setupPasswordToggle();
        setupFotoButton();
    }

    private void initViews() {
        edtNombre = findViewById(R.id.edtnombre);
        edtPhone = findViewById(R.id.edtphone);
        edtEmail = findViewById(R.id.edtemail);
        edtPassword = findViewById(R.id.edtpassword);
        edtConfirmPassword = findViewById(R.id.edtconfirmpassword);

        btnShowPass = findViewById(R.id.btn_show_pass);
        btnShowPassConfirm = findViewById(R.id.btn_show_pass_confirm);

        spinnerTipo = findViewById(R.id.spinner_tipo);
        cbTerms = findViewById(R.id.cb_terms);

        layoutVehicle = findViewById(R.id.layout_vehicle);
        edtPlaca = findViewById(R.id.edtplaca);
        edtModelo = findViewById(R.id.edtmodelo);
        edtColor = findViewById(R.id.edtcolor);
        imgPreview = findViewById(R.id.img_vehicle_preview);
    }

    private void setupSpinner() {
        spinnerTipo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String tipo = spinnerTipo.getSelectedItem().toString();
                layoutVehicle.setVisibility(
                        tipo.equalsIgnoreCase("Conductor") ? View.VISIBLE : View.GONE
                );
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void setupFotoButton() {
        findViewById(R.id.btn_upload_image).setOnClickListener(v -> {
            Intent intent = new Intent(
                    Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            );
            startActivityForResult(intent, PICK_IMAGE);
        });
    }

    private void setupPasswordToggle() {
        btnShowPass.setOnClickListener(v -> togglePassword(edtPassword, btnShowPass));
        btnShowPassConfirm.setOnClickListener(
                v -> togglePassword(edtConfirmPassword, btnShowPassConfirm)
        );
    }

    private void togglePassword(EditText edt, ImageButton btn) {
        if (edt.getInputType() ==
                (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            edt.setInputType(
                    InputType.TYPE_CLASS_TEXT |
                            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            );
            btn.setImageResource(R.drawable.ic_eye_open);
        } else {
            edt.setInputType(
                    InputType.TYPE_CLASS_TEXT |
                            InputType.TYPE_TEXT_VARIATION_PASSWORD
            );
            btn.setImageResource(R.drawable.ic_eye_close);
        }
        edt.setSelection(edt.getText().length());
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_IMAGE && res == RESULT_OK && data != null) {
            imagenSeleccionada = data.getData();
            imgPreview.setImageURI(imagenSeleccionada);
        }
    }

    public void irSuccessRegister(View view) {

        if (!validarCampos()) return;

        String tipo = spinnerTipo.getSelectedItem().toString();

        if (tipo.equalsIgnoreCase("Conductor") &&
                (edtPlaca.getText().toString().trim().isEmpty() ||
                        edtModelo.getText().toString().trim().isEmpty() ||
                        edtColor.getText().toString().trim().isEmpty() ||
                        imagenSeleccionada == null)) {

            Toast.makeText(this,
                    "Complete los datos del vehículo",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔐 GUARDAR DATOS EN SHARED PREFERENCES
        SharedPreferences.Editor editor =
                getSharedPreferences("userData", MODE_PRIVATE).edit();

        editor.putString("nombre", edtNombre.getText().toString().trim());
        editor.putString("phone", edtPhone.getText().toString().trim());
        editor.putString("email", edtEmail.getText().toString().trim());
        editor.putString("rol", tipo);

        if (tipo.equalsIgnoreCase("Conductor")) {
            editor.putString("placa", edtPlaca.getText().toString().trim());
            editor.putString("modelo", edtModelo.getText().toString().trim());
            editor.putString("color", edtColor.getText().toString().trim());
        }

        editor.apply();

        // ✅ MENSAJE DE CONFIRMACIÓN
        Toast.makeText(this,
                "Datos guardados correctamente",
                Toast.LENGTH_SHORT).show();

        // 👉 IR A MOSTRAR DATOS
        startActivity(new Intent(this, MostrarDatos.class));
        finish();
    }

    private boolean validarCampos() {
        if (edtNombre.getText().toString().trim().isEmpty() ||
                edtPhone.getText().toString().trim().isEmpty() ||
                edtEmail.getText().toString().trim().isEmpty() ||
                edtPassword.getText().toString().trim().isEmpty() ||
                edtConfirmPassword.getText().toString().trim().isEmpty()) {

            Toast.makeText(this,
                    "Complete todos los campos",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!edtPassword.getText().toString()
                .equals(edtConfirmPassword.getText().toString())) {

            Toast.makeText(this,
                    "Las contraseñas no coinciden",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!cbTerms.isChecked()) {
            Toast.makeText(this,
                    "Debe aceptar los términos",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }
}
