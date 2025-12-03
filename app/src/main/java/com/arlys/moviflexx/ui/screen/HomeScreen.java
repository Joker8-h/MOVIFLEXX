package com.arlys.moviflexx.ui.screen;


import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.ui.theme.Theme;

public class HomeScreen extends AppCompatActivity {

    private LinearLayout profileMenu;
    private boolean isMenuVisible = false;
    private boolean isDarkMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_screen);

        // Configurar tema
        setupTheme();

        // Inicializar componentes
        initComponents();

        // Configurar textos desde strings.xml
        setupTexts();
    }

    private void setupTheme() {
        // Aquí deberías detectar el modo oscuro del sistema
        // Por ahora lo dejamos falso
        isDarkMode = false;

        View rootView = findViewById(android.R.id.content);
        rootView.setBackgroundColor(Theme.getBackgroundColor(isDarkMode));

        // Aplicar gradiente al botón de perfil
        LinearLayout profileButton = findViewById(R.id.profile_button);
        Theme.applyGradientBackground(profileButton, isDarkMode);
    }

    private void initComponents() {
        // Botón de perfil
        LinearLayout profileButton = findViewById(R.id.profile_button);
        profileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleProfileMenu();
            }
        });

        // Menú de perfil (inicialmente oculto)
        profileMenu = findViewById(R.id.profile_menu);
        profileMenu.setVisibility(View.GONE);

        // Items del menú
        setupMenuItems();

        // Panel de ubicación
        setupLocationPanel();

        // Mapa placeholder
        setupMapPlaceholder();
    }

    private void setupTexts() {
        // Toolbar title
        TextView toolbarTitle = findViewById(R.id.toolbar_title);
        toolbarTitle.setText(R.string.home_title);

        // Ubicación actual
        TextView currentLocationTitle = findViewById(R.id.current_location_title);
        currentLocationTitle.setText(R.string.current_location);

        TextView currentLocationAddress = findViewById(R.id.current_location_address);
        currentLocationAddress.setText(R.string.sample_address);

        // Destino
        TextView destinationTitle = findViewById(R.id.destination_title);
        destinationTitle.setText(R.string.destination_title);

        TextView destinationHint = findViewById(R.id.destination_hint);
        destinationHint.setText(R.string.destination_hint);

        // Items del menú
        TextView menuProfileText = findViewById(R.id.menu_profile_text);
        menuProfileText.setText(R.string.menu_profile);

        TextView menuHistoryText = findViewById(R.id.menu_history_text);
        menuHistoryText.setText(R.string.menu_history);

        TextView menuHelpText = findViewById(R.id.menu_help_text);
        menuHelpText.setText(R.string.menu_help);

        TextView menuSettingsText = findViewById(R.id.menu_settings_text);
        menuSettingsText.setText(R.string.menu_settings);

        TextView menuMessagesText = findViewById(R.id.menu_messages_text);
        menuMessagesText.setText(R.string.menu_messages);
    }

    private void setupMapPlaceholder() {
        ImageView mapImage = findViewById(R.id.map_image);
        // Si no hay imagen, mostrar texto temporal
        if (mapImage.getDrawable() == null) {
            TextView mapPlaceholder = findViewById(R.id.map_placeholder);
            mapPlaceholder.setText(R.string.map_placeholder_text);
            mapPlaceholder.setVisibility(View.VISIBLE);
        }
    }

    private void toggleProfileMenu() {
        if (isMenuVisible) {
            profileMenu.setVisibility(View.GONE);
        } else {
            profileMenu.setVisibility(View.VISIBLE);
        }
        isMenuVisible = !isMenuVisible;
    }

    private void setupMenuItems() {
        // Perfil
        LinearLayout menuProfile = findViewById(R.id.menu_profile);
        menuProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(HomeScreen.this, getString(R.string.toast_profile), Toast.LENGTH_SHORT).show();
                // Navegar a pantalla de perfil
                profileMenu.setVisibility(View.GONE);
                isMenuVisible = false;
            }
        });

        // Historial
        LinearLayout menuHistory = findViewById(R.id.menu_history);
        menuHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(HomeScreen.this, getString(R.string.toast_history), Toast.LENGTH_SHORT).show();
                profileMenu.setVisibility(View.GONE);
                isMenuVisible = false;
            }
        });

        // Ayuda
        LinearLayout menuHelp = findViewById(R.id.menu_help);
        menuHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(HomeScreen.this, getString(R.string.toast_help), Toast.LENGTH_SHORT).show();
                profileMenu.setVisibility(View.GONE);
                isMenuVisible = false;
            }
        });

        // Configuración
        LinearLayout menuSettings = findViewById(R.id.menu_settings);
        menuSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(HomeScreen.this, getString(R.string.toast_settings), Toast.LENGTH_SHORT).show();
                profileMenu.setVisibility(View.GONE);
                isMenuVisible = false;
            }
        });

        // Mensajes
        LinearLayout menuMessages = findViewById(R.id.menu_messages);
        menuMessages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(HomeScreen.this, getString(R.string.toast_messages), Toast.LENGTH_SHORT).show();
                profileMenu.setVisibility(View.GONE);
                isMenuVisible = false;
            }
        });
    }

    private void setupLocationPanel() {
        // Destino (clickeable)
        CardView destinationCard = findViewById(R.id.destination_card);
        destinationCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(HomeScreen.this, getString(R.string.toast_search_destination), Toast.LENGTH_SHORT).show();
            }
        });
    }
}