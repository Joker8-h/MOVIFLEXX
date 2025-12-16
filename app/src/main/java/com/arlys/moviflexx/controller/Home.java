package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class Home extends AppCompatActivity {

    private VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Inicializar VideoView
        videoView = findViewById(R.id.backgroundVideo);

        // Cargar video
        Uri video = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.movilflexrelax);
        videoView.setVideoURI(video);

        // Listener para iniciar en loop
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(true);      // Repite infinito
                mp.setVolume(1f, 1f);     // Silencio
                videoView.start();        // Inicia video
            }
        });
    }

    public void irLogin(View view) {
        Intent siguiente = new Intent(Home.this, Login.class);
        startActivity(siguiente);
    }
}
