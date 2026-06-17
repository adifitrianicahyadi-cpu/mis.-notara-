# NOTARA — Aplikasi Catatan Audio (Android Native)

Aplikasi perekam audio, transkripsi, dan notula untuk lingkungan instansi
(Inspektorat Daerah Kabupaten Tabalong). Kotlin + Jetpack Compose + Room.

Package: `id.go.tabalong.inspektorat.notara`

## Fitur
- Rekam audio mikrofon → berkas **AAC (.m4a)**.
- **Perekaman tetap berjalan saat layar terkunci / aplikasi di latar belakang**
  (foreground service tipe microphone + notifikasi berisi timer + wake lock).
  Rekaman dapat dihentikan & disimpan langsung dari notifikasi.
- **Impor/kelola berkas .aac** (dan format audio lain) sebagai dasar pengolahan.
- Pemutar audio dengan slider posisi.
- Transkrip: ketik manual, **dikte langsung** (SpeechRecognizer on-device),
  atau **transkripsi otomatis** berkas via endpoint STT internal (mis. Whisper).
- **Olah AI**: Ringkasan, Notulen Rapat, Poin Penting, Tindak Lanjut, Rapikan Transkrip.
  Prompt sudah dikunci agar tidak mengarang fakta.
- Metadata audit: Unit/OPD, peserta, klasifikasi (Biasa/Terbatas/Rahasia), tag.
- Pencarian & filter, ekspor Markdown (bagikan via menu sistem).
- Penyimpanan **lokal** (Room + DataStore), tanpa sinkronisasi cloud bawaan.

## Cara build jadi APK

### Opsi A — GitHub Actions (dapat APK TANPA memasang apa pun)
Repositori ini menyertakan workflow `.github/workflows/build-apk.yml` yang membangun
APK di server GitHub (sudah ada Android SDK + internet).
1. Buat akun GitHub (gratis) → buat repositori baru, mis. `notara`.
2. Unggah **isi** folder `NotaraAndroid` sebagai akar repositori
   (sehingga `app/`, `settings.gradle.kts`, dan `.github/` berada di akar repo).
   Bisa lewat tombol "Add file → Upload files" di web GitHub, atau `git push`.
3. Buka tab **Actions** → jalankan workflow **Build APK NOTARA**
   (otomatis jalan saat push; atau klik "Run workflow").
4. Setelah hijau (±5–10 menit), buka run tersebut → bagian **Artifacts** →
   unduh **NOTARA-debug-apk** → ekstrak → pasang `app-debug.apk` di HP
   (aktifkan "Instal dari sumber tidak dikenal").

> Catatan: APK debug cocok untuk pemakaian internal/uji. Untuk distribusi resmi,
> buat APK rilis ber-tanda tangan (lihat bagian bawah workflow).

### Opsi B — Android Studio (paling mudah bila ada PC)
1. Buka **Android Studio** (Koala/Ladybug atau lebih baru).
2. **File → Open**, pilih folder `NotaraAndroid`.
3. Android Studio akan otomatis mengunduh Gradle wrapper, SDK, dan dependensi
   (butuh koneksi internet saat sync pertama). Tunggu **Gradle Sync** selesai.
4. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   APK debug ada di `app/build/outputs/apk/debug/app-debug.apk`.
5. Untuk rilis ber-tanda tangan: **Build → Generate Signed Bundle / APK**.

### Opsi C — Command line
Proyek ini **tidak menyertakan** `gradle-wrapper.jar` (berkas biner).
Hasilkan dulu wrapper-nya bila Gradle terpasang di mesin Anda:
```
cd NotaraAndroid
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`.

> Build memerlukan **JDK 17** dan **Android SDK (API 34)**.

## Konfigurasi (menu Pengaturan di aplikasi)
- **Mesin AI**: kosongkan endpoint untuk memakai format Anthropic default
  (isi API Key). Untuk instansi, arahkan ke **proxy/LLM internal** agar data tidak
  keluar jaringan. Model default: `claude-sonnet-4-6`.
- **Endpoint STT**: isi bila ingin transkripsi otomatis berkas .aac. Aplikasi
  mengirim audio sebagai multipart field `file`; balasan diharapkan JSON `{"text":"…"}`.

## Catatan teknis jujur
- **Perekaman latar belakang** memakai foreground service. Agar tidak terputus pada
  rekaman panjang, beberapa HP (Xiaomi/Redmi, Oppo, Vivo, Samsung) perlu pengaturan:
  matikan *Battery optimization* untuk NOTARA dan izinkan *Autostart/jalan di latar
  belakang*. Tanpa ini, sistem dapat menghentikan perekaman saat layar lama terkunci.
- Izin **Notifikasi** sebaiknya diizinkan agar timer perekaman tampil; bila ditolak,
  perekaman tetap jalan namun tanpa notifikasi yang terlihat.
- **Dikte langsung** memakai `SpeechRecognizer` bawaan Android. Ketersediaan &
  kualitas bergantung pada layanan pengenalan suara perangkat (mis. Google) dan
  model bahasa Indonesia yang terpasang/ter-download. Mode offline diaktifkan bila
  perangkat mendukung (Android 13+).
- Perekaman audio dan dikte langsung **berebut mikrofon**, jadi tidak dijalankan
  bersamaan: alur yang disarankan adalah Rekam → simpan berkas → transkrip
  (dikte/STT/manual) di layar detail.
- Transkripsi **berkas .aac** tidak bisa dilakukan murni di perangkat tanpa mesin
  STT; gunakan endpoint STT internal atau isi transkrip manual.
- APK belum dikompilasi di sini — Anda build sendiri sesuai langkah di atas.

## Versi pustaka utama
AGP 8.5.2 · Kotlin 2.0.20 · Compose BOM 2024.09.02 · Room 2.6.1 ·
OkHttp 4.12.0 · minSdk 26 · targetSdk 34 · JDK 17.
