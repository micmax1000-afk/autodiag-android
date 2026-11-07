# AutoDiag Android

App Android nativa (Kotlin + Jetpack Compose) per diagnosi OBD2 via adattatore ELM327 Bluetooth classico e gestione scadenze di manutenzione.

## Funzionalità

- Connessione Bluetooth a ELM327 già accoppiato (non BLE)
- Parametri in diretta Mode 01 (giri, velocità, temperature, carico, tensione, trim, …) solo se la centralina li supporta
- Lettura separata dei DTC memorizzati (03), pending (07) e permanenti (0A)
- Cancellazione Mode 04, con esito reale: cosa è stato tolto e cosa resta (i permanenti non si cancellano)
- Database locale descrizioni codici generici
- Promemoria chilometraggio e intervalli di manutenzione (solo sul telefono: non resetta la spia service)

## Requisiti

- Android 7.0+ (API 24)
- Adattatore ELM327 Bluetooth classico (non BLE-only)
- Dispositivo già accoppiato dalle impostazioni di sistema
- Quadro acceso. Per cancellare i codici il motore va spento

## Build

Apri il progetto in Android Studio (Ladybug o più recente) e sincronizza Gradle, oppure:

```bash
gradle assembleDebug
```

L’APK di debug si trova in `app/build/outputs/apk/debug/`.

La CI di GitHub Actions genera l’APK a ogni push su `main`.
