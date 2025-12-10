package com.arlys.moviflexx.controller;

import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class Success extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_success);

        // Botón de cerrar
        ImageButton btnClose = findViewById(R.id.btnClose);
        btnClose.setOnClickListener(v -> finish());

        // Texto "¿No recibiste el correo?"
        TextView tvDidntReceive = findViewById(R.id.tvDidntReceive);

        tvDidntReceive.setText(
                Html.fromHtml(getString(R.string.didnt_receive), Html.FROM_HTML_MODE_LEGACY)
        );

        // Permitir que el texto HTML sea clickeable
        tvDidntReceive.setMovementMethod(LinkMovementMethod.getInstance());

        // Listener para reenviar el correo
        tvDidntReceive.setOnClickListener(v -> resendConfirmationEmail());
    }

    private void resendConfirmationEmail() {
        // TODO: Tu lógica real de reenvío
        // Ejemplo temporal:
        // Toast.makeText(this, "Reenviando correo...", Toast.LENGTH_SHORT).show();
    }
}
