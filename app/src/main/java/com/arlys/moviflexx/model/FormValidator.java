package com.arlys.moviflexx.model;

import com.google.android.material.textfield.TextInputLayout;

/**
 * FormValidator — Validaciones inline estilo Google
 *
 * Muestra el error debajo del campo directamente (como Gmail al crear cuenta).
 * Llama a validarTodo() antes de enviar el formulario.
 *
 * Uso en Register.java:
 *
 *   FormValidator.configurarValidaciones(
 *       tilNombre, tilEmail, tilTelefono, tilPassword
 *   );
 *
 *   // Al hacer clic en Registrar:
 *   if (!FormValidator.validarTodo(tilNombre, edtNombre,
 *                                  tilEmail, edtEmail,
 *                                  tilTelefono, edtTelefono,
 *                                  tilPassword, edtPassword)) return;
 */
public class FormValidator {

    // ── Validar nombre ────────────────────────────────────────────────────────

    public static boolean validarNombre(String valor, TextInputLayout til) {
        valor = valor.trim();

        if (valor.isEmpty()) {
            til.setError("El nombre es obligatorio");
            return false;
        }
        if (valor.length() < 3) {
            til.setError("Mínimo 3 caracteres");
            return false;
        }
        if (!Character.isUpperCase(valor.charAt(0))) {
            til.setError("Debe comenzar con mayúscula");
            return false;
        }
        if (!valor.matches("[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+")) {
            til.setError("Solo se permiten letras");
            return false;
        }

        til.setError(null);
        til.setErrorEnabled(false);
        return true;
    }

    // ── Validar email ─────────────────────────────────────────────────────────

    public static boolean validarEmail(String valor, TextInputLayout til) {
        valor = valor.trim();

        if (valor.isEmpty()) {
            til.setError("El correo es obligatorio");
            return false;
        }
        if (!valor.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            til.setError("Ingresa un correo válido (ej: usuario@correo.com)");
            return false;
        }

        til.setError(null);
        til.setErrorEnabled(false);
        return true;
    }

    // ── Validar teléfono ──────────────────────────────────────────────────────

    public static boolean validarTelefono(String valor, TextInputLayout til) {
        valor = valor.trim();

        if (valor.isEmpty()) {
            til.setError("El teléfono es obligatorio");
            return false;
        }
        if (!valor.matches("[0-9]+")) {
            til.setError("Solo se permiten números");
            return false;
        }
        if (valor.length() < 7) {
            til.setError("Mínimo 7 dígitos");
            return false;
        }
        if (valor.length() > 15) {
            til.setError("Máximo 15 dígitos");
            return false;
        }

        til.setError(null);
        til.setErrorEnabled(false);
        return true;
    }

    // ── Validar contraseña (igual que Google) ─────────────────────────────────

    public static boolean validarPassword(String valor, TextInputLayout til) {
        if (valor.isEmpty()) {
            til.setError("La contraseña es obligatoria");
            return false;
        }
        if (valor.length() < 8) {
            til.setError("Mínimo 8 caracteres");
            return false;
        }
        if (!valor.matches(".*[A-Z].*")) {
            til.setError("Debe incluir al menos una letra mayúscula");
            return false;
        }
        if (!valor.matches(".*[a-z].*")) {
            til.setError("Debe incluir al menos una letra minúscula");
            return false;
        }
        if (!valor.matches(".*[0-9].*")) {
            til.setError("Debe incluir al menos un número");
            return false;
        }
        if (!valor.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            til.setError("Debe incluir al menos un carácter especial (!@#$...)");
            return false;
        }

        til.setError(null);
        til.setErrorEnabled(false);
        return true;
    }

    // ── Configurar listeners automáticos (valida al escribir) ─────────────────
    // Llama esto en onCreate() para que los errores desaparezcan solos al corregir

    public static void configurarListeners(
            TextInputLayout tilNombre,   android.widget.EditText edtNombre,
            TextInputLayout tilEmail,    android.widget.EditText edtEmail,
            TextInputLayout tilTelefono, android.widget.EditText edtTelefono,
            TextInputLayout tilPassword, android.widget.EditText edtPassword) {

        // Nombre — valida al perder foco
        edtNombre.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus)
                validarNombre(edtNombre.getText().toString(), tilNombre);
        });

        // Email — valida al perder foco
        edtEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus)
                validarEmail(edtEmail.getText().toString(), tilEmail);
        });

        // Teléfono — valida al perder foco
        edtTelefono.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus)
                validarTelefono(edtTelefono.getText().toString(), tilTelefono);
        });

        // Password — valida mientras escribe (para mostrar requisitos al instante)
        edtPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                // Solo muestra error si ya hay algo escrito (no molesta desde el inicio)
                if (s.length() > 0) {
                    validarPassword(s.toString(), tilPassword);
                } else {
                    tilPassword.setError(null);
                    tilPassword.setErrorEnabled(false);
                }
            }
        });
    }

    // ── Validar todo de golpe antes de enviar ─────────────────────────────────

    public static boolean validarTodo(
            TextInputLayout tilNombre,   android.widget.EditText edtNombre,
            TextInputLayout tilEmail,    android.widget.EditText edtEmail,
            TextInputLayout tilTelefono, android.widget.EditText edtTelefono,
            TextInputLayout tilPassword, android.widget.EditText edtPassword) {

        // Valida todos y acumula resultados (no corta en el primer error)
        boolean ok = true;

        if (!validarNombre(edtNombre.getText().toString(), tilNombre))     ok = false;
        if (!validarEmail(edtEmail.getText().toString(), tilEmail))         ok = false;
        if (!validarTelefono(edtTelefono.getText().toString(), tilTelefono)) ok = false;
        if (!validarPassword(edtPassword.getText().toString(), tilPassword)) ok = false;

        return ok;
    }

    // ── Versión solo para Login (email + password) ────────────────────────────

    public static boolean validarLogin(
            TextInputLayout tilEmail,    android.widget.EditText edtEmail,
            TextInputLayout tilPassword, android.widget.EditText edtPassword) {

        boolean ok = true;
        if (!validarEmail(edtEmail.getText().toString(), tilEmail))          ok = false;
        if (!validarPassword(edtPassword.getText().toString(), tilPassword)) ok = false;
        return ok;
    }

    public static void configurarListenersLogin(
            TextInputLayout tilEmail,    android.widget.EditText edtEmail,
            TextInputLayout tilPassword, android.widget.EditText edtPassword) {

        edtEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus)
                validarEmail(edtEmail.getText().toString(), tilEmail);
        });

        edtPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (s.length() > 0) {
                    validarPassword(s.toString(), tilPassword);
                } else {
                    tilPassword.setError(null);
                    tilPassword.setErrorEnabled(false);
                }
            }
        });
    }
}