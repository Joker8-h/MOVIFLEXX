package com.arlys.moviflexx;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import android.util.Log;
import org.mockito.MockedStatic;

import com.arlys.moviflexx.model.SessionManager;

public class SessionManagerTest {

    @Mock
    Context mockContext;
    @Mock
    SharedPreferences mockPrefs;
    @Mock
    SharedPreferences.Editor mockEditor;

    private SessionManager sessionManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs);
        when(mockPrefs.edit()).thenReturn(mockEditor);

        sessionManager = new SessionManager(mockContext);
    }

    @Test
    public void testSetLoggedIn() {
        sessionManager.setLoggedIn(true);
        verify(mockEditor).putBoolean("IS_LOGGED_IN", true);
        verify(mockEditor).apply();
    }

    @Test
    public void testIsLoggedIn() {
        when(mockPrefs.getBoolean("IS_LOGGED_IN", false)).thenReturn(true);
        assertTrue(sessionManager.isLoggedIn());
    }

    @Test
    public void testLogout() {
        when(mockEditor.clear()).thenReturn(mockEditor);
        
        // SesionUsuario.cerrarSesion() llama a Log.d, el cual NO existe en JUnit.
        // Lo simulamos temporalmente durante este test:
        try (MockedStatic<Log> mockedLog = mockStatic(Log.class)) {
            mockedLog.when(() -> Log.d(anyString(), anyString())).thenReturn(0);
            
            sessionManager.logout();
            verify(mockEditor).clear();
            verify(mockEditor).apply();
        }
    }
}
