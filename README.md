# SmartHouse-Bluetooth-Android

Une application mobile Android (Java) robuste permettant la supervision et le contrôle en temps réel d'une maison connectée via une architecture Serveur/Client hybride (liaison Bluetooth RFCOMM et API REST).

## Architecture et Fonctionnalités

L'application prend en charge deux rôles distincts configurables dynamiquement :
* **Mode Serveur (Hub Central) :** * Fait la passerelle entre l'environnement local et Internet.
    * Interroge automatiquement une API REST externe pour récupérer l'état de la maison.
    * Diffuse les informations en temps réel aux clients connectés en Bluetooth.
* **Mode Client (Télécommande) :** * Se connecte au Hub en Bluetooth.
    * Reçoit les mises à jour et permet de piloter à distance les équipements de la maison.

## Concepts Techniques Clés

Ce projet met en œuvre plusieurs notions avancées du développement mobile Android :

* **Gestion Multi-Threading et Asynchronisme :** Utilisation d'un thread d'arrière-plan dédié (ConnectedThread) pour l'écoute continue des flux Bluetooth (Input/Output Streams) sans bloquer l'application.
* **Communication Inter-Threads (Handlers) :**
    * bluetoothHandler : Utilisé comme passerelle de sécurité pour transmettre les messages reçus du thread Bluetooth vers le thread principal (UI Thread), évitant tout crash de l'interface graphique.
    * apiHandler : Exploité comme une horloge cyclique asynchrone pour planifier et rafraîchir les requêtes réseau toutes les 10 secondes.
* **Réseau (API REST) :** Intégration de la bibliothèque Volley de Google pour effectuer des requêtes HTTP (GET/POST) asynchrones de monitoring.
* **Optimisation du Cycle de Vie (Lifecycle) :** Gestion propre des connexions et des tâches de fond dans les méthodes onResume() et onPause() pour préserver la batterie et les ressources de l'appareil.

## Technologies Utilisées

* **Langage :** Java (Android SDK)
* **Réseau :** Google Volley (API REST)
* **Protocole Local :** Bluetooth API (Liaison série RFCOMM / UUID standard)
* **IDE :** Android Studio

## Développeurs

* Ibrahim Khelil
* Aymen Achagui
