# ⚡ APK Builder for Android

<p align="center">
  <img src="app/src/main/res/drawable/app_icon_fg.png" width="128" height="128" alt="APK Builder Logo" style="border-radius: 24px;" />
</p>

<p align="center">
  <b>On-Device Android Project Compiler, Terminal Verifier & APK Packager</b><br>
  <i>Build, verify, package, sign, and install Android APK applications directly on your Android device from project ZIP archives.</i>
</p>

<p align="center">
  <a href="APK-Builder-v1.0.apk">
    <img src="https://img.shields.io/badge/Download%20APK-v1.0.0%20(Release)-00E5FF?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" />
  </a>
  <a href="releases/APK-Builder-v1.0.apk">
    <img src="https://img.shields.io/badge/Direct%20Download-23%20MB-10B981?style=for-the-badge&logo=googleplay&logoColor=white" alt="Direct Download" />
  </a>
  <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2024%2B)-38BDF8?style=for-the-badge&logo=android" alt="Android Version" />
</p>

---

## 📥 Direkter Download der fertigen APK (Release)

Du kannst die fertige Version der APK direkt hier von der Startseite des GitHub Repositories herunterladen und installieren:

👉 **[Hier klicken: APK-Builder-v1.0.apk herunterladen](APK-Builder-v1.0.apk)** *(Größe: ~23 MB, Signiert & Installationsbereit)*  
👉 **[Alternativer Download-Pfad: releases/APK-Builder-v1.0.apk](releases/APK-Builder-v1.0.apk)**

---

## 🌟 Hauptfunktionen

* **📦 Projekt-Import & ZIP-Extraktion:**
  * Importiere beliebige `.zip`-Archive von Android-Projekten über das sichere Android Storage Access Framework (SAF).
  * Automatische Extraktion und Prüfung aller Quellcodes, Ressourcen, Manifeste und Gradle-Konfigurationen.
  * 3 integrierte Starter-Projekte zum Sofort-Testen:
    * **Neon Counter App** (Material 3 Counter mit dynamischem State)
    * **Hardware Sensor Suite** (Live-Telemetrie mit Beschleunigungssensor)
    * **Web App Wrapper** (Sicherer WebView-Wrapper für Webanwendungen)

* **💻 Echtzeit-Entwicklerterminal:**
  * Streamt alle Compiler- und Packaging-Logs in Echtzeit mit Monospace-Terminalausgabe.
  * Farbcodierte Statusmeldungen (`[AXML]`, `[DEX]`, `[SIGN]`, `[VERIFY]`, `[PKG]`).
  * Interaktive Kommandozeile (`build`, `verify`, `download`, `info`, `files`, `cache`, `clean`, `help`).
  * Export- und Kopierfunktion für Fehleranalysen.

* **🔍 Automatischer Integritäts-Check & Paket-Downloader:**
  * Prüft vor jedem Build die Integrität des Projekts (`AndroidManifest.xml`, `classes.dex`, Layouts, String-Tabellen).
  * Erkennt fehlende SDK-Plattformen (`android.jar` API 34) und Bibliotheken (`androidx.core`, `appcompat`, `material`) automatisch.
  * Lädt fehlende Toolchain-Pakete selbstständig in den lokalen App-Cache herunter.

* **⚙️ On-Device APK Compiler & Signer:**
  * Kompiliert binäres Android-XML (AXML) und Ressourcen-Tabellen (`resources.arsc`).
  * Integriert Dalvik Executable Bytecode (`classes.dex`).
  * Signiert die fertige APK mit kryptografischen Schlüsseln (**v1 JAR Scheme** & **v2 Signature Scheme**).
  * Verifiziert jede gebaute APK vorab mit Androids internem `PackageParser`, um Installationsfehler wie *"There was a problem parsing the package"* vollständig auszuschließen.

* **📲 Installation & Export:**
  * Fragt die Android-Berechtigung *"Unbekannte Apps installieren"* (`REQUEST_INSTALL_PACKAGES`) an und leitet direkt zu den Systemeinstellungen.
  * Ein-Klick-Installation über den Android PackageInstaller via `FileProvider`.
  * Direktes Teilen oder Speichern der gebauten APK im Download-Ordner.

---

## 🚀 Installation & Erste Schritte

1. **APK herunterladen:** Lade [`APK-Builder-v1.0.apk`](APK-Builder-v1.0.apk) auf dein Android-Smartphone oder öffne es im Emulator.
2. **Installation erlauben:** Falls dein Gerät fragt, erlaube in den Einstellungen die Installation aus dieser Quelle (*Unbekannte Apps installieren*).
3. **App starten:** Wähle eine der Vorlagen oder lade dein eigenes Android-Projekt als `.zip` hoch.
4. **Bauen & Installieren:** Tippe auf **"BUILD INSTALLABLE APK"** – die App prüft alle Abhängigkeiten, lädt fehlende Komponenten im Terminal herunter und startet direkt die Installation.

---

## 🛠️ Build-Pipeline im Detail

```text
[1/6] Inspect ZIP Archive       ──► Entpackt Manifest, Quellcode, Gradle & Ressourcen
[2/6] Project Integrity Check  ──► Validiert XML-Syntax, Activity-Klassen & Symbole
[3/6] Package Resolution       ──► Erkennt und lädt fehlende SDK-/Maven-Pakete herunter
[4/6] Resource Compilation     ──► Erzeugt binäres AndroidManifest.xml & ARSC
[5/6] Bytecode DEX Assembly    ──► Bindet Dalvik Executable (classes.dex) ein
[6/6] Cryptographic Signing    ──► Signiert APK mit v1 (JAR) & v2 Signature Scheme
                               └──► Übergabe an Android PackageInstaller via FileProvider
```

---

## 📄 Lizenz & Hinweise
Entwickelt mit Jetpack Compose, Kotlin Coroutines, OkHttp und Material Design 3.
Kompatibel mit Android 8.0 (API 24) bis einschließlich Android 15/16.
