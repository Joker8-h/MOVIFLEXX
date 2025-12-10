package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.RegisterDatos;

import java.util.ArrayList;

public class RegisterManager {

    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public RegisterManager(Context context) {
        conexionBd = new ConexionBd(context);
    }

    public void openBdWr() {
        db = conexionBd.getWritableDatabase();
    }

    public void openBdRd() {
        db = conexionBd.getReadableDatabase();
    }

    public void closeBd() {db.close();}

    // -------------------------------------------
    //  INSERTAR USUARIO
    // -------------------------------------------
    public long insertData(RegisterDatos registerDatos) {
        openBdWr();

        ContentValues values = new ContentValues();
        values.put("NOMBRE", registerDatos.getNombre());
        values.put("TELEFONO", registerDatos.getTelefono());
        values.put("CORREO", registerDatos.getCorreo());
        values.put("PASSWORD", registerDatos.getPassword());
        values.put("CONFIRMACION", registerDatos.getConfirmacion());
        values.put("ROL", registerDatos.getRol());

        long id = db.insert("RegisterDatos", null, values);

        closeBd();
        return id;
    }

    public ArrayList<RegisterDatos>listarData() {
        openBdRd();
        ArrayList<RegisterDatos> lista = new ArrayList<>();

        String sql = "SELECT * FROM Datos";
        Cursor cursor = db.rawQuery(sql, null);

        if (cursor.moveToFirst()) {
            do {
                RegisterDatos registerDatos = new RegisterDatos();

                registerDatos.setNombre(cursor.getString(0));
                registerDatos.setTelefono(cursor.getInt(1));
                registerDatos.setCorreo(cursor.getString(2));
                registerDatos.setPassword(cursor.getString(3));
                registerDatos.setConfirmacion(cursor.getString(4));
                registerDatos.setRol(cursor.getColumnName(5));

            } while (cursor.moveToNext());
        }
        return lista;
    }
}
