package com.example.bleproject;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.os.Build;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // VARIABLES PARTAGÉES (static = accessible depuis SecondActivity)


    // Tag pour les logs (visible dans Logcat d'Android Studio)
    private static final String TAG = "SmartHouseBT";

    // UUID unique qui identifie notre application Bluetooth
    // Le serveur et le client DOIVENT avoir le même UUID pour se reconnaître
    private static final UUID MY_UUID = UUID.fromString("b8f4e210-3c1a-4d92-a7f3-9876543210cd"); // UUID standard SPP
    private static final String NAME = "SmartHouse_AymenIbrahim";    // Nom lisible du service Bluetooth (utilisé uniquement pour l'enregistrement)

    private BluetoothAdapter bluetoothAdapter;    // Adaptateur Bluetooth du téléphone (interface avec le hardware BT)

    // Éléments graphiques récupérés depuis activity_main.xml
    private Button btnServer, btnClient;
    private TextView statusText, waitingText;


    public static BluetoothSocket bluetoothSocket; //socket partagé avec SecondActivit
    public static boolean isServer = false; // Flag indiquant le rôle du téléphone : true = Serveur, false = Client
    // Déclaré static pour être accessible depuis SecondActivity


    // CRÉATION DE L'ÉCRAN
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); //charge activity_main.xml
        // Récupère l'adaptateur Bluetooth du téléphone
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth non supporté", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // Récupère les boutons et textes depuis le XML via leur id
        btnServer = findViewById(R.id.btn_server);
        btnClient = findViewById(R.id.btn_client);
        statusText = findViewById(R.id.status_text);
        waitingText = findViewById(R.id.waiting_text);
        // Associe les clics aux méthodes correspondantes
        btnServer.setOnClickListener(v -> startServerMode());
        btnClient.setOnClickListener(v -> startClientMode());
        // Demande les permissions Bluetooth nécessaires selon la version Android
        checkPermissions();
    }


    // /////////////////GESTION DES PERMISSIONS BLUETOOTH////////////////

    // Demande les permissions à l'utilisateur
    // Android 12+ (SDK 31+) : il faut demander BLUETOOTH_CONNECT explicitement
    // Android 10/11        : les permissions dans le Manifest suffisent
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, 1);
            }
        } else {
            // Sur Android 11 et moins, ACCESS_FINE_LOCATION est souvent nécessaire pour le BT
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }


    // Vérifie si la permission Bluetooth est accordée au moment de l'exécution
    // Retourne true si on peut utiliser le Bluetooth, false sinon
    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        // Sur Android 10, les permissions normales du manifest suffisent
        return true;
    }



    // ///////////////////MODE SERVEUR////////////////
    private void startServerMode() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth non disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Veuillez activer le Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        isServer = true; //Càd ce telephone est SERVEUR
        // Mise à jour de l'interface
        btnServer.setText("SERVEUR EN ATTENTE");
        btnServer.setEnabled(false);
        btnClient.setVisibility(View.GONE);
        waitingText.setText("*Attente de connexion d'un client*");
        waitingText.setVisibility(View.VISIBLE);

        try {
            new AcceptThread().start(); //lancer le thread d'ecoute
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du lancement du thread serveur", e);
            Toast.makeText(this, "Erreur lancement serveur", Toast.LENGTH_SHORT).show();
            resetUI();
        }
    }




    // //////////MODE CLIENT ///////////
    private void startClientMode() {
        if (bluetoothAdapter == null) return;
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Veuillez activer le Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        isServer = false; // CE TÉLÉPHONE EST CLIENT
        // Mise à jour de l'interface
        btnClient.setText("CLIENT EN ATTENTE");
        btnClient.setEnabled(false);
        btnServer.setVisibility(View.GONE);
        waitingText.setText("*Attente de connexion au serveur*");
        waitingText.setVisibility(View.VISIBLE);

        if (!hasBluetoothPermission()) return;

        try {
            // Cherche les téléphones appairés
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices != null && !pairedDevices.isEmpty()) {
                // Prend le premier appareil appairé
                // (s'assurer que seul le téléphone serveur est appairé, ou qu'il est le premier)
                BluetoothDevice targetDevice = pairedDevices.iterator().next();
                Toast.makeText(this, "Connexion à : " + targetDevice.getName(), Toast.LENGTH_SHORT).show();
                new ConnectThread(targetDevice).start();
            } else {
                Toast.makeText(this, "Aucun appareil appairé trouvé", Toast.LENGTH_SHORT).show();
                resetUI();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException lors de la recherche d'appareils", e);
            Toast.makeText(this, "Permission Bluetooth manquante", Toast.LENGTH_SHORT).show();
            resetUI();
        }
    }



    // RÉINITIALISATION DE L'INTERFACE
    private void resetUI() {
        btnServer.setEnabled(true);
        btnServer.setText("LANCER LE SERVEUR");
        btnClient.setEnabled(true);
        btnClient.setText("LANCER LE CLIENT");
        btnClient.setVisibility(View.VISIBLE);
        btnServer.setVisibility(View.VISIBLE);
        waitingText.setVisibility(View.GONE);
    }


    // POINT DE JONCTION SERVEUR + CLIENT
    // Appelée par les DEUX une fois la connexion BT établie
    private void manageConnectedSocket(BluetoothSocket socket) {
        // Sauvegarde le socket dans une variable statique
        // pour que SecondActivity puisse y accéder directement
        bluetoothSocket = socket;
        Log.d(TAG, "Socket connecté, lancement de SecondActivity");

        // Lance SecondActivity via un Intent explicite
        // (on sait exactement quelle activité lancer → Intent explicite)
        Intent intent = new Intent(this, SecondActivity.class);
        startActivity(intent);
    }

    // THREAD SERVEUR — attend une connexion entrante
    // Tourne dans un thread séparé car accept() est bloquant
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                if (hasBluetoothPermission() && bluetoothAdapter != null) {
                    // Ouvre un port d'écoute Bluetooth identifié par notre UUID
                    // Seul un client avec le MÊME UUID pourra se connecter
                    tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("SmartHouse", MY_UUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket listen() failed", e);
            } catch (SecurityException e) {
                Log.e(TAG, "Permission Bluetooth manquante", e);
            }
            serverSocket = tmp;
        }

        public void run() {
            if (serverSocket == null) {
                Log.e(TAG, "Serveur non initialisé (socket null)");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Erreur d'initialisation du serveur", Toast.LENGTH_SHORT).show();
                    resetUI();
                });
                return;
            }

            BluetoothSocket socket = null;
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket accept() failed", e);
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Erreur inattendue dans AcceptThread", e);
                    break;
                }

                if (socket != null) {
                    final BluetoothSocket finalSocket = socket;
                    // Retour sur le thread UI pour lancer SecondActivity
                    runOnUiThread(() -> manageConnectedSocket(finalSocket));
                    try {
                        // Ferme le port d'écoute : on n'accepte qu'un seul client
                        serverSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Could not close server socket", e);
                    }
                    break;
                }
            }
        }
    }

    // THREAD CLIENT — initie la connexion vers le serveur
    // Tourne dans un thread séparé car connect() est bloquant
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice; // le téléphone serveur cible
        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                if (hasBluetoothPermission()) {
                    // Crée un socket vers le serveur en utilisant le MÊME UUID
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            if (mmSocket == null) return;
            try {
                if (hasBluetoothPermission()) {
                    // BLOQUANT : tente de se connecter au serveur
                    // Réussit si le serveur fait accept() avec le même UUID
                    mmSocket.connect();
                }
            } catch (IOException connectException) {
                // Connexion échouée → ferme le socket et réinitialise l'interface
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Connexion échouée", Toast.LENGTH_SHORT).show();
                    resetUI();
                });
                return;
            }
            // Connexion réussie → sauvegarde le socket et lance SecondActivity
            manageConnectedSocket(mmSocket);
        }
    }
}