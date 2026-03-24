package com.arlys.moviflexx;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.arlys.moviflexx.model.FormValidator;
import com.google.android.material.textfield.TextInputLayout;

public class FormValidatorTest {

    @Mock
    TextInputLayout mockTil;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testValidarEmailValido() {
        assertTrue(FormValidator.validarEmail("test@moviflexx.com", mockTil));
    }

    @Test
    public void testValidarEmailInvalido() {
        assertFalse(FormValidator.validarEmail("test.com", mockTil));
        assertFalse(FormValidator.validarEmail("test@", mockTil));
        assertFalse(FormValidator.validarEmail("", mockTil));
    }

    @Test
    public void testValidarContrasenaValida() {
        // Necesita mayúscula, minúscula, número, especial y 8 caracteres
        assertTrue(FormValidator.validarPassword("Password123!", mockTil));
    }

    @Test
    public void testValidarContrasenaInvalida() {
        assertFalse(FormValidator.validarPassword("12345", mockTil));
        assertFalse(FormValidator.validarPassword("", mockTil));
        assertFalse(FormValidator.validarPassword("password123", mockTil)); // Sin mayúscula ni especial
    }
}
