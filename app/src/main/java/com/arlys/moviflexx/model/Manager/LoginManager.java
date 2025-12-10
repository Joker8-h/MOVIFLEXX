package com.arlys.moviflexx.model.Manager;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;

public class LoginManager {

    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public LoginManager(Context context) {
        conexionBd = new ConexionBd(context);
    }

    public void openBdRd() {
        db = conexionBd.getReadableDatabase();
    }

    public void closeBd() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    // -------------------------------
    // MÉTODO PARA VALIDAR LOGIN
    // -------------------------------
    public boolean validarUsuario(String correo, String password) {

        openBdRd();

        Cursor cursor = db.rawQuery(
                "SELECT * FROM RegisterDatos WHERE CORREO = ? AND PASSWORD = ?",
                new String[]{correo, password}
        );

        boolean existe = cursor.moveToFirst();

        cursor.close();
        closeBd();

        return existe;
    }
}
