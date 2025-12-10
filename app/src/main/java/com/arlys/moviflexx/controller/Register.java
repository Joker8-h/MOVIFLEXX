package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Manager.RegisterManager;
import com.arlys.moviflexx.model.pojo.RegisterDatos;

public class Register extends AppCompatActivity {

    RegisterManager registerManager;

    EditText edtnombre, edtphone, edtemail, edtpassword, edtconfirmpassword;
    Spinner spinner_tipo;
    CheckBox cb_terms;
    Button btn_register;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // --- Inicialización de vistas ---
        edtnombre = findViewById(R.id.edtnombre);
        edtphone = findViewById(R.id.edtphone);
        edtemail = findViewById(R.id.edtemail);
        edtpassword = findViewById(R.id.edtpassword);
        edtconfirmpassword = findViewById(R.id.edtconfirmpassword);
        spinner_tipo = findViewById(R.id.spinner_tipo);
        cb_terms = findViewById(R.id.cb_terms);
        btn_register = findViewById(R.id.btn_register); // *** FALTABA ESTO ***

        registerManager = new RegisterManager(Register.this);

        // --- Click en el botón registrar ---
        btn_register.setOnClickListener(v -> {

            String nombre = edtnombre.getText().toString();
            int telefono = Integer.parseInt(edtphone.getText().toString());
            String email = edtemail.getText().toString();
            String password = edtpassword.getText().toString();
            String confirmpassword = edtconfirmpassword.getText().toString();
            String rol = spinner_tipo.getSelectedItem().toString();
            boolean terminos = cb_terms.isChecked();

            RegisterDatos registerDatos =
                    new RegisterDatos(nombre, telefono, email, password, confirmpassword, rol, terminos);

            long resul = registerManager.insertData(registerDatos);

            if(resul > 0){
                Toast.makeText(Register.this,"Datos agregados", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(Register.this,"Error al insertar datos",Toast.LENGTH_SHORT).show();
            }

        });

        Toast.makeText(this,"Base de datos creada", Toast.LENGTH_SHORT).show();
    }

    public void irLogin0(View view){
        startActivity(new Intent(Register.this, Login.class));
    }
}
