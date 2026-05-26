package com.example.bleproject;

import android.bluetooth.BluetoothSocket;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import androidx.appcompat.app.AppCompatActivity;




public class SecondActivity extends AppCompatActivity {
    // //////// VARIABLES/////////
    public static final String TAG = "SmartHouseBT";

    // Handler pour lancer refreshData() toutes les 10 secondes (SERVEUR seulement)
    private Handler apiHandler;
    private Runnable runnableCode;

    // File de requêtes Volley (SERVEUR seulement)
    private RequestQueue queue;

    // ID de notre maison connectée sur le serveur
    private final String houseId = "21";
    // URL de base de l'API REST
    private String baseUrl = "http://happyresto.enseeiht.fr/smartHouse/api/v1/devices/";//on a jouté houseId a url en bas dans refreshData
    // Bluetooth
    // Thread qui lit/écrit les données Bluetooth en continu (les DEUX)
    private static ConnectedThread bluetoothThread;
    // Handler pour recevoir les messages du ConnectedThread dans le thread UI
    private Handler bluetoothHandler;




    // //////CRÉATION DE L'ÉCRAN///////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        apiHandler = new Handler(Looper.getMainLooper());
        // Ce Handler tourne sur le thread UI
        // Il reçoit les messages envoyés par ConnectedThread
        // et peut modifier l'interface graphique

        bluetoothHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 1) { // signifie "message BT reçu"
                    String readMessage = (String) msg.obj;
                    handleBluetoothMessage(readMessage);
                }
            }
        };
        // Lance le thread BT — les DEUX (serveur et client) lisent le socket
        if (MainActivity.bluetoothSocket != null) {
            bluetoothThread = new ConnectedThread(MainActivity.bluetoothSocket);
            bluetoothThread.start();
        }
        // Bouton retour → ferme SecondActivity et revient à MainActivity
        Button backButton = findViewById(R.id.back_bt);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (MainActivity.isServer) {
            // SERVEUR : initialise Volley et prépare le rafraîchissement périodique
            queue = Volley.newRequestQueue(this);
            runnableCode = new Runnable() {
                @Override
                public void run() {
                    refreshData();// appel API REST + envoie résultat au client BT
                    apiHandler.postDelayed(this, 10000);//recommence dans 10s
                }
            };
        } else {
            // CLIENT : rien à faire ici
            // Il attend passivement les données JSON envoyées par le serveur via BT

            // Le client n'a pas besoin de rafraîchir l'API, il attend le Bluetooth
            TextView titleText = findViewById(R.id.header).findViewById(new View(this).generateViewId());
            // On peut changer le titre pour indiquer le mode
            Log.d(TAG, "Mode Client activé");
        }
    }




    // TRAITEMENT DES MESSAGES BLUETOOTH REÇUS
    // Appelé par bluetoothHandler quand ConnectedThread reçoit un message
    private void handleBluetoothMessage(String message) {
        try {
            String cleanMessage = message.trim();
            if (MainActivity.isServer) {
                // SERVEUR reçoit une commande du CLIENT
                // Format du message : "TOGGLE:42" (éteindre/allumer l'appareil ID=42)
                if (cleanMessage.startsWith("TOGGLE:")) {
                    int deviceIdToToggle = Integer.parseInt(cleanMessage.substring(7));//extrait 42
                    toggleDevice(deviceIdToToggle, false);// fait le POST à l'API REST
                }
            } else {
                // CLIENT reçoit le JSON complet du SERVEUR
                // C'est toute la liste des appareils mise à jour
                JSONArray response = new JSONArray(cleanMessage);
                runOnUiThread(() -> updateUI(response));
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur JSON/BT: " + e.getMessage());
        }
    }


    // //////CYCLE DE VIE/////
    @Override
    protected void onResume() {
        super.onResume();
        // Quand l'écran devient visible → SERVEUR démarre le rafraîchissement
        if (MainActivity.isServer && apiHandler != null && runnableCode != null) {
            apiHandler.post(runnableCode);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Quand l'écran passe en arrière-plan (appel téléphonique, etc...)
        // → SERVEUR arrête les requêtes pour économiser les ressources
        if (MainActivity.isServer && apiHandler != null && runnableCode != null) {
            apiHandler.removeCallbacks(runnableCode);
        }
    }



    // REQUÊTE GET — SERVEUR SEULEMENT
    // Récupère la liste des appareils depuis l'API REST
    private void refreshData() {
        if (!MainActivity.isServer) return;

        String requestUrl = baseUrl + houseId;
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, requestUrl, null,
                response -> {
                    // Succès : response = tableau JSON des appareils
                    updateUI(response);
                    // Envoie aussi le JSON brut au CLIENT via Bluetooth avec un délimiteur(/n)
                    if (bluetoothThread != null) {
                        String dataToSend = response.toString() + "\n";
                        bluetoothThread.write(dataToSend.getBytes(StandardCharsets.UTF_8));
                    }
                }, this::handleError);  //en cas d'erreur réseau
        queue.add(jsonArrayRequest);// Volley exécute la requête en arrière-plan
    }


    // UPDATE
    // MET À JOUR L'AFFICHAGE
    // Vide la liste et recrée une ligne par appareil
    // Utilisé par les DEUX (serveur via REST, client via BT)
    private void updateUI(JSONArray response) {
        LinearLayout linearlayout = findViewById(R.id.linearlayout);//Le conteneur du xml
        if (linearlayout == null) return;

        linearlayout.removeAllViews();  // vide tout l'affichage précédent
        try {
            for (int i = 0; i < response.length(); i++) {
                JSONObject device = response.getJSONObject(i);
                // Extrait les champs JSON de chaque appareil
                int id = device.getInt("ID");
                String name = device.optString("NAME", "Unknown Device");
                String brand = device.optString("BRAND", "");
                String model = device.optString("MODEL", "");
                String data = device.optString("DATA", "");
                int autonomy = device.optInt("AUTONOMY", -1);
                int state = device.optInt("STATE", 0); // 1=ON, 0=OFF

                // Crée une ligne graphique pour cet appareil et l'ajoute à la liste
                linearlayout.addView(createDeviceView(id, brand, model, name, autonomy, data, state == 1));
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON Parsing error", e);
        }
    }

    private void handleError(com.android.volley.VolleyError error) {
        String errMsg = "Erreur API";
        if (error.networkResponse != null) errMsg += " (" + error.networkResponse.statusCode + ")";
        Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show();
        Log.e(TAG, "Volley Error: " + error.getMessage());
    }



    // CRÉE UNE LIGNE GRAPHIQUE POUR UN APPAREIL
    // Retourne une View contenant : [nom + info] [bouton ON/OFF]
    public View createDeviceView(int id, String brand, String model, String name,
                                 int autonomy, String data, boolean status) {

        // Conteneur principal
        LinearLayout outerLayout = new LinearLayout(this);
        LinearLayout.LayoutParams outerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        outerParams.setMargins(16, 8, 16, 8); // marges latérales + espacement vertical
        outerLayout.setLayoutParams(outerParams);
        outerLayout.setOrientation(LinearLayout.HORIZONTAL);
        outerLayout.setBackgroundColor(Color.parseColor("#16213E")); // bleu nuit
        outerLayout.setPadding(32, 24, 32, 24);
        outerLayout.setGravity(Gravity.CENTER_VERTICAL);

        // Partie texte à gauche
        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));


        TextView titleTextView = new TextView(this);
        String title = (brand.isEmpty() && model.isEmpty())
                ? name : "[" + brand + " " + model + "] " + name;
        titleTextView.setText(title);
        titleTextView.setTextSize(15);
        titleTextView.setTextColor(Color.WHITE);
        titleTextView.setTypeface(null, Typeface.BOLD);

        // Ligne info : autonomie + data
        TextView infoTextView = new TextView(this);
        String infoText = "";
        if (autonomy != -1) infoText +=  autonomy + "%";
        if (!data.isEmpty()) infoText += (infoText.isEmpty() ? "" : "  ") + data;
        if (infoText.isEmpty()) infoText = "Aucune donnée";
        infoTextView.setText(infoText);
        infoTextView.setTextSize(12);
        infoTextView.setTextColor(Color.parseColor("#AAAAAA")); // gris clair

        textLayout.addView(titleTextView);
        textLayout.addView(infoTextView);

        // Bouton ON/OFF avec couleur rouge/verte
        Button button = new Button(this);
        button.setText(status ? "ON" : "OFF");
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackgroundColor(status
                ? Color.parseColor("#4CAF50")   // vert si ON
                : Color.parseColor("#E94560")); // rouge/rose si OFF

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(16, 0, 0, 0);
        button.setLayoutParams(btnParams);

        button.setOnClickListener(v -> {
            if (MainActivity.isServer) {
                toggleDevice(id, status);
            } else {
                String cmd = "TOGGLE:" + id + "\n";
                if (bluetoothThread != null) {
                    bluetoothThread.write(cmd.getBytes(StandardCharsets.UTF_8));
                }
            }
        });

        outerLayout.addView(textLayout);
        outerLayout.addView(button);
        return outerLayout;
    }




    // REQUÊTE POST — SERVEUR SEULEMENT
    // Allume ou éteint un appareil via l'API REST
    private void toggleDevice(int id, boolean currentStatus) {
        if (!MainActivity.isServer) return;

        StringRequest sr = new StringRequest(Request.Method.POST, baseUrl,
                response -> {
                    refreshData(); // après action → récupère la liste mise à jour
                                     // + la renvoie automatiquement au client via BT
                }, this::handleError) {
            @Override
            protected Map<String, String> getParams() {
                // Paramètres du POST envoyés au serveur REST
                Map<String, String> params = new HashMap<>();
                params.put("deviceId", String.valueOf(id)); //quel appareil
                params.put("houseId", String.valueOf(houseId));//quel maison
                params.put("action", "turnOnOff");//quel action
                return params;
            }
        };
        queue.add(sr);
    }


    // THREAD BLUETOOTH (les DEUX l'utilisent)
    // Lit en continu ce qui arrive sur le socket BT
    // et écrit quand on appelle write()

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;// pour LIRE les données reçues
        private final OutputStream mmOutStream; // pour ÉCRIRE les données à envoyer

        public ConnectedThread(android.bluetooth.BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Erreur flux", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // tampon de lecture
            int bytes;
            StringBuilder sb = new StringBuilder();
            while (true) { //écoute en permanence
                try {
                    bytes = mmInStream.read(buffer);
                    String part = new String(buffer, 0, bytes, StandardCharsets.UTF_8);
                    sb.append(part);

                    int index;
                    // Les messages sont séparés par "\n"
                    while ((index = sb.indexOf("\n")) != -1) {
                        String fullMessage = sb.substring(0, index); //extrait un msg complet
                        sb.delete(0, index + 1);//on le retire du buffer
                        // Envoie le message au bluetoothHandler (thread UI)
                        // msg.what = 1 = "message BT reçu"
                        bluetoothHandler.obtainMessage(1, fullMessage).sendToTarget();
                    }
                } catch (IOException e) {
                    Log.d(TAG, "Socket déconnecté");
                    break;
                }
            }
        }



        // Envoie des données via Bluetooth
        // Appelé par :
        //   - SERVEUR : pour envoyer le JSON au client (dans refreshData)
        //   - CLIENT  : pour envoyer "TOGGLE:id" au serveur (dans createDeviceView)
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Erreur écriture", e);
            }
        }
    }
}