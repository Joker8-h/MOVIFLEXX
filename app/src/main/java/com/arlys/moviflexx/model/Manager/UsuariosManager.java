package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.Usuarios;

public class UsuariosManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public UsuariosManager(Context context) {
        conexionBd = new ConexionBd(context);
    }

    public void openBdWr() {
        db = conexionBd.getWritableDatabase();
    }

    public void openBdRd() {
        db = conexionBd.getReadableDatabase();
    };

    public void closeBd() {
        db.close();
    }

    /**
     * Inserta un nuevo registro en la tabla USUARIOS.
     */
    public long insertData(Usuarios usuario) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("NOMBRE", usuario.getNombre());
        values.put("APELLIDO", usuario.getApellido());
        values.put("CORREO", usuario.getCorreo());
        values.put("TELEFONO", usuario.getTelefono());
        values.put("CONTRASEÑA", usuario.getContrasena());

        long id = db.insert("USUARIOS", null, values);
        closeBd();
        return id;
    }
}