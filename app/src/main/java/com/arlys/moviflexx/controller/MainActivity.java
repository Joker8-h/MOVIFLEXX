package com.arlys.moviflexx.controller;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.arlys.moviflexx.R;

public class MainActivity extends AppCompatActivity {

    // Variables para el menú de perfil
    private LinearLayout profileMenu;
    private boolean isMenuVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Mantén EdgeToEdge (tu código original)
        EdgeToEdge.enable(this);

        // 2. Cambia SOLO esta línea: de activity_main a activity_home_screen
        setContentView(R.layout.activity_home_screen);

        // 3. Mantén el código EdgeToEdge original pero con el ID correcto
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 4. INICIALIZAR COMPONENTES DE LA PANTALLA DE VIAJES
        initTravelComponents();

        // 5. CONFIGURAR TEXTOS
        setupTexts();
    }

    // MÉTODOS PARA LA PANTALLA DE VIAJES
    private void initTravelComponents() {
        // A. Botón de perfil
        LinearLayout profileButton = findViewById(R.id.profile_button);
        if (profileButton != null) {
            profileButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleProfileMenu();
                }
            });
        }

        // B. Menú de perfil (inicialmente oculto)
        profileMenu = findViewById(R.id.profile_menu);
        if (profileMenu != null) {
            profileMenu.setVisibility(View.GONE);
        }

        // C. Items del menú
        setupMenuItems();

        // D. Panel de ubicación - ESTO ES LO QUE TENÍAS CON ERROR
        setupLocationPanel();

        // E. Mapa placeholder
        setupMapPlaceholder();
    }

    private void setupTexts() {
        try {
            // Toolbar title
            TextView toolbarTitle = findViewById(R.id.toolbar_title);
            if (toolbarTitle != null) {
                toolbarTitle.setText("TuViajes"); // Texto directo por ahora
            }

            // Ubicación actual
            TextView currentLocationTitle = findViewById(R.id.current_location_title);
            if (currentLocationTitle != null) {
                currentLocationTitle.setText("Tu ubicación");
            }

            TextView currentLocationAddress = findViewById(R.id.current_location_address);
            if (currentLocationAddress != null) {
                currentLocationAddress.setText("Calle Principal #123");
            }

            // Destino
            TextView destinationTitle = findViewById(R.id.destination_title);
            if (destinationTitle != null) {
                destinationTitle.setText("¿A dónde vas?");
            }

            TextView destinationHint = findViewById(R.id.destination_hint);
            if (destinationHint != null) {
                destinationHint.setText("Toca para buscar destino");
            }

            // Items del menú - TEXTOS
            TextView menuProfileText = findViewById(R.id.menu_profile_text);
            if (menuProfileText != null) menuProfileText.setText("Perfil");

            TextView menuHistoryText = findViewById(R.id.menu_history_text);
            if (menuHistoryText != null) menuHistoryText.setText("Historial");

            TextView menuHelpText = findViewById(R.id.menu_help_text);
            if (menuHelpText != null) menuHelpText.setText("Ayuda");

            TextView menuSettingsText = findViewById(R.id.menu_settings_text);
            if (menuSettingsText != null) menuSettingsText.setText("Configuración");

            TextView menuMessagesText = findViewById(R.id.menu_messages_text);
            if (menuMessagesText != null) menuMessagesText.setText("Mensajes");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupMapPlaceholder() {
        ImageView mapImage = findViewById(R.id.map_image);
        TextView mapPlaceholder = findViewById(R.id.map_placeholder);

        // Si no hay imagen, mostrar texto temporal
        if (mapImage != null && mapPlaceholder != null) {
            if (mapImage.getDrawable() == null) {
                mapPlaceholder.setVisibility(View.VISIBLE);
            }
        }
    }

    private void toggleProfileMenu() {
        if (profileMenu != null) {
            if (isMenuVisible) {
                profileMenu.setVisibility(View.GONE);
            } else {
                profileMenu.setVisibility(View.VISIBLE);
            }
            isMenuVisible = !isMenuVisible;
        }
    }

    private void setupMenuItems() {
        // Perfil
        LinearLayout menuProfile = findViewById(R.id.menu_profile);
        if (menuProfile != null) {
            menuProfile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(MainActivity.this, "Perfil", Toast.LENGTH_SHORT).show();
                    if (profileMenu != null) {
                        profileMenu.setVisibility(View.GONE);
                    }
                    isMenuVisible = false;
                }
            });
        }

        // Historial
        LinearLayout menuHistory = findViewById(R.id.menu_history);
        if (menuHistory != null) {
            menuHistory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(MainActivity.this, "Historial", Toast.LENGTH_SHORT).show();
                    if (profileMenu != null) {
                        profileMenu.setVisibility(View.GONE);
                    }
                    isMenuVisible = false;
                }
            });
        }

        // Ayuda
        LinearLayout menuHelp = findViewById(R.id.menu_help);
        if (menuHelp != null) {
            menuHelp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(MainActivity.this, "Ayuda", Toast.LENGTH_SHORT).show();
                    if (profileMenu != null) {
                        profileMenu.setVisibility(View.GONE);
                    }
                    isMenuVisible = false;
                }
            });
        }

        // Configuración
        LinearLayout menuSettings = findViewById(R.id.menu_settings);
        if (menuSettings != null) {
            menuSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(MainActivity.this, "Configuración", Toast.LENGTH_SHORT).show();
                    if (profileMenu != null) {
                        profileMenu.setVisibility(View.GONE);
                    }
                    isMenuVisible = false;
                }
            });
        }

        // Mensajes
        LinearLayout menuMessages = findViewById(R.id.menu_messages);
        if (menuMessages != null) {
            menuMessages.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(MainActivity.this, "Mensajes", Toast.LENGTH_SHORT).show();
                    if (profileMenu != null) {
                        profileMenu.setVisibility(View.GONE);
                    }
                    isMenuVisible = false;
                }
            });
        }
    }

    private void setupLocationPanel() {
        LinearLayout destinationCard = findViewById(R.id.destination_card);

        if (destinationCard != null) {
            destinationCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(MainActivity.this, "Buscando destino...", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(this, "Error: destination_card no encontrado", Toast.LENGTH_SHORT).show();
        }
    }
}