package com.arlys.moviflexx.model.voice;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Descarga y descomprime el modelo Vosk (ES) en almacenamiento interno.
 * 100% open-source, sin keys. Se hace una vez y luego se reutiliza.
 */
public final class VoskModelManager {
    private static final String TAG = "VoskModelManager";

    // Modelo pequeño en español (zip). Puedes cambiarlo por otro modelo si lo deseas.
    // Nota: este URL apunta a un recurso público; si cambia, solo se actualiza aquí.
    private static final String MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip";

    private static final String MODEL_DIR_NAME = "vosk-model-es";

    private VoskModelManager() {}

    public static File getModelDir(Context ctx) {
        return new File(ctx.getFilesDir(), MODEL_DIR_NAME);
    }

    public static boolean isModelReady(Context ctx) {
        File dir = getModelDir(ctx);
        // Chequeo mínimo: carpeta existe y contiene conf/model.conf (común en modelos Vosk).
        return dir.exists() && dir.isDirectory() && new File(dir, "conf/model.conf").exists();
    }

    /**
     * Descarga y descomprime el modelo si hace falta.
     * Bloqueante: llámalo desde un hilo de background.
     */
    public static void ensureModel(Context ctx) throws Exception {
        if (isModelReady(ctx)) return;

        File targetDir = getModelDir(ctx);
        File tmpZip = new File(ctx.getCacheDir(), "vosk_es_model.zip");

        // Limpiar intento previo incompleto
        if (targetDir.exists()) deleteRecursively(targetDir);
        if (tmpZip.exists()) //noinspection ResultOfMethodCallIgnored
            tmpZip.delete();

        Log.d(TAG, "Descargando modelo Vosk ES...");
        downloadToFile(MODEL_URL, tmpZip);

        Log.d(TAG, "Descomprimiendo modelo...");
        unzipToDir(tmpZip, targetDir);

        // Algunos zips vienen con una carpeta raíz. Alinear a MODEL_DIR_NAME.
        File[] children = targetDir.listFiles();
        if (children != null && children.length == 1 && children[0].isDirectory()) {
            File root = children[0];
            // Mover contenido a targetDir
            File[] rootFiles = root.listFiles();
            if (rootFiles != null) {
                for (File f : rootFiles) {
                    File dst = new File(targetDir, f.getName());
                    if (!f.renameTo(dst)) {
                        // fallback copia
                        copyDirOrFile(f, dst);
                        deleteRecursively(f);
                    }
                }
            }
            deleteRecursively(root);
        }

        //noinspection ResultOfMethodCallIgnored
        tmpZip.delete();

        if (!isModelReady(ctx)) {
            throw new IllegalStateException("Modelo Vosk no quedó listo (conf/model.conf no encontrado).");
        }
        Log.d(TAG, "Modelo Vosk listo en: " + targetDir.getAbsolutePath());
    }

    private static void downloadToFile(String urlStr, File outFile) throws Exception {
        HttpURLConnection c = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Moviflexx/1.0");
            c.connect();
            if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) {
                throw new IllegalStateException("HTTP " + c.getResponseCode() + " descargando modelo");
            }
            in = new BufferedInputStream(c.getInputStream());
            out = new FileOutputStream(outFile);
            byte[] buf = new byte[8192];
            int n;
            long total = c.getContentLength();
            long read = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                read += n;
                if (total > 0 && read % (1024 * 1024) < 8192) { // Cada 1MB aprox
                    Log.d(TAG, "Descargando: " + (read * 100 / total) + "%");
                }
            }
            out.flush();
            Log.d(TAG, "Descarga completada.");
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            if (c != null) c.disconnect();
        }
    }

    private static void unzipToDir(File zipFile, File outDir) throws Exception {
        //noinspection ResultOfMethodCallIgnored
        outDir.mkdirs();
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zipFile)));
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                // Normalizar rutas zip
                if (name.startsWith("/")) name = name.substring(1);
                File outFile = new File(outDir, name);
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) //noinspection ResultOfMethodCallIgnored
                    parent.mkdirs();

                FileOutputStream fos = new FileOutputStream(outFile);
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    fos.write(buffer, 0, count);
                }
                fos.close();
                zis.closeEntry();
            }
        } finally {
            try { if (zis != null) zis.close(); } catch (Exception ignored) {}
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File c : files) deleteRecursively(c);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static void copyDirOrFile(File src, File dst) throws Exception {
        if (src.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            dst.mkdirs();
            File[] children = src.listFiles();
            if (children != null) {
                for (File c : children) copyDirOrFile(c, new File(dst, c.getName()));
            }
            return;
        }
        try (InputStream in = new java.io.FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
}

