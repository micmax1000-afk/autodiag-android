# AutoDiag Android

App Android nativa (Kotlin + Jetpack Compose) per diagnosi OBD2 via adattatore ELM327 Bluetooth classico e gestione scadenze di manutenzione.

## Funzionalità

- Connessione Bluetooth a ELM327 già accoppiato
- Lettura e cancellazione codici errore (DTC)
- Database locale descrizioni codici generici
- Tracking chilometraggio e intervalli di manutenzione

## Requisiti

- Android 7.0+ (API 24)
- Adattatore ELM327 Bluetooth classico (non BLE-only)
- Dispositivo già accoppiato dalle impostazioni di sistema

## Build

Apri il progetto in Android Studio (Ladybug o più recente) e sincronizza Gradle, oppure:

```bash
gradle assembleDebug
```

L’APK di debug si trova in `app/build/outputs/apk/debug/`.

La CI di GitHub Actions genera l’APK a ogni push su `main`.
