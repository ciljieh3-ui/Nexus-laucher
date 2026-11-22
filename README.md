# Nexus Launcher — Nexus Micro Architecture

Native Android Home Launcher (`ACTION_MAIN`, `CATEGORY_HOME`, `CATEGORY_DEFAULT`) yang dibangun dengan strategi **Zero-Dependency Native Android Engineering** menggunakan Kotlin murni, Android Views, `Canvas`, `Drawable`, `PackageManager`/`LauncherApps`, `AppWidgetHost`, `NotificationListenerService`, `DevicePolicyManager`, dan `SharedPreferences`/`org.json`.

Aplikasi ini bersifat **universal dan adaptif** — tidak mengunci logika pada merek, model, kapasitas RAM, atau resolusi layar tertentu, dan berjalan **100% offline tanpa izin `INTERNET`**.

---

## Arsitektur & Sistem Adaptif Universal

1. **Zero Runtime External Dependencies**
   - Seluruh UI Home, Dock, Folder, App Drawer, Smart Search, Pratinjau Tampilan, dan 8 kategori Pengaturan dibangun memakai Android SDK native (`android.app.Activity`, `View`, `ViewGroup`, `Canvas`, `GridView`/`ListView` dengan `ViewHolder` recycling).
   - **Dihapus**: Jetpack Compose, Material3, Material Icons Extended, AppCompat, Lifecycle/ViewModel Compose, Navigation Compose, Coroutines/Flow, Google Fonts/TTF eksternal, dan aset wallpaper JPG statis.
   - 3 wallpaper bawaan (Obsidian, Aurora, Dune) digambar secara prosedural menggunakan `Canvas` (`LinearGradient`, `RadialGradient`, `Path`) dengan biaya aset ~0 KB.
2. **Universal Adaptive Device Engine (`DeviceProfile`)**
   - Mendeteksi secara runtime: `ActivityManager.isLowRamDevice`, total RAM (`MemoryInfo.totalMem`), batas heap JVM (`Runtime.maxMemory`), jumlah core CPU, `DisplayMetrics` (`widthPixels`, `heightPixels`, `densityDpi`), status `PowerManager.isPowerSaveMode`, dan `Settings.Global.ANIMATOR_DURATION_SCALE`.
   - Menentukan tier adaptif (`CONSTRAINED`, `STANDARD`, `HIGH`) serta mengkalibrasi otomatis kapasitas `LruCache` ikon, ukuran raster ikon (`56px–192px`), jumlah thread worker (`1–3`), durasi animasi (`0ms–220ms`), dan layer translusen.
   - Mendukung mode **Auto Adaptif** (bawaan), **Hemat Ultra**, **Seimbang**, dan **Halus**.
3. **Custom Rendering & Manajemen Memori**
   - `NexusIconView`: menggambar ikon, klip bentuk (`System`, `Squircle`, `Circle`, `Rounded Square`, `Teardrop`), pratinjau folder 2x2, lencana pintasan, badge notifikasi, dan label dalam satu pass `Canvas` tanpa view anak tambahan maupun alokasi objek di `onDraw()`.
   - `NexusWallpaperView`: pratinjau wallpaper dengan `inSampleSize` sampling berbasis resolusi layar nyata agar bitmap galeri tidak membebani heap.
   - `NexusLauncherApp`: menangani `onTrimMemory` dan `onLowMemory` untuk memangkas/membersihkan cache bitmap & cache wallpaper sementara secara langsung.
4. **Event-Driven & Proof-Based Telemetry (`PerformanceMonitor`)**
   - Jam, baterai, perubahan package (install/uninstall/update), dan power-save sepenuhnya digerakkan oleh `BroadcastReceiver` sistem (tanpa polling atau background service permanen).
   - `PerformanceMonitor` mencatat latensi frame pertama (`SystemClock`), penggunaan memori (`Debug.MemoryInfo.totalPss`, Native & JVM heap), serta persentase frame jank (>16.67 ms via `Window.OnFrameMetricsAvailableListener`), yang dapat dilihat langsung di **Pengaturan > Performa & Telemetri**.

---

## Audit Dependency & Pengukuran Build (`apkanalyzer` / `dexdump`)

Seluruh angka di bawah ini diukur langsung dari artefak build lokal menggunakan Gradle, Android SDK `apkanalyzer`, dan `dexdump`:

| Metrik Terukur | Sebelum (Compose Baseline) | Sesudah (Nexus Micro Debug) | Sesudah (Nexus Micro R8 Release) | Penurunan |
| --- | ---: | ---: | ---: | ---: |
| **Ukuran APK Debug** | `17,322,964` B (~17.32 MB) | `1,594,635` B (~1.59 MB) | *Lihat catatan signing* | **-90.8%** (`10.86x` lebih kecil) |
| **Download Size (Debug)** | — | `1,431,246` B (~1.43 MB) | — | — |
| **Defined DEX Methods** | `111,029` metode | `12,609` metode | `1,396` metode | **-88.6%** (Debug) / **-98.7%** (R8) |
| **Ukuran File DEX** | `23,524,353` B (data DEX) | `1,260,549` B (data DEX) | `211,540` B (`classes.dex` tunggal) | **-94.6%** (Debug) / **-99.1%** (R8) |
| **Shrunk & Optimized Res** | — | — | `255,431` B (`resources-release-optimize.ap_`) | — |
| **External Runtime Libs** | Compose, Material3, Icons, Lifecycle, Navigation, Coroutines, Core-KTX | **0** (Kotlin stdlib + Android SDK) | **0** (Kotlin stdlib + Android SDK) | **100% dihilangkan** |

> **Catatan Build Release (`packageRelease`)**: Tahap optimasi R8 (`minifyReleaseWithR8`), pengecilan resource (`convertShrunkResourcesToBinaryRelease`), `optimizeReleaseResources`, dan `lintVitalRelease` telah **berhasil dijalankan**. Tahap penandatanganan akhir (`:app:packageRelease`) memerlukan file keystore `my-upload-key.jks` dan environment variable `STORE_PASSWORD`/`KEY_PASSWORD` saat proses export APK/AAB di AI Studio atau mesin lokal Anda.

---

## Fitur Aktif

- **Home Screen**: Jam digital & tanggal, status baterai real-time, search bar, halaman multi-page swipe horizontal, dock aplikasi dengan transparansi adaptif, folder 2x2 (drag-and-drop ikon untuk memindah atau menggabungkan menjadi folder), pintasan aplikasi (`LauncherApps`), mode edit layar utama, serta dukungan portrait & landscape responsif.
- **App Drawer**: Daftar aplikasi terinstal nyata dari `LauncherApps`/`PackageManager` dengan `GridView`/`ListView` + `ViewHolder` recycling, pencarian langsung, urut A–Z / Z–A, strip *Terakhir Digunakan*, sembunyikan aplikasi, dan pembersihan otomatis saat aplikasi di-uninstall.
- **Smart Search**: Pencarian lokal super cepat (aplikasi, pintasan aplikasi Android 7.1+, dan aksi sistem seperti Wi-Fi, Bluetooth, Layar, Baterai, Home Default).
- **Gestur**: Swipe up (Drawer / Search), Swipe down (Search / Panel Notifikasi), Swipe horizontal (pindah halaman), Double tap area kosong (Kunci layar via opsional `DevicePolicyManager`), Long press ikon (pintasan, pin ke Home/Dock, gabung folder, Info Aplikasi, Uninstall), dan Long press ruang kosong (Mode Edit).
- **Widget Android Asli (`AppWidgetHost`)**: Tambah widget terpasang, ubah ukuran `colSpan × rowSpan` saat mode edit, dan hapus widget dengan pembersihan `allocateAppWidgetId` / `deleteAppWidgetId`.
- **Notification Dots Lokal**: Menggunakan `NotificationListenerService` di RAM untuk menghitung jumlah badge per paket setelah diizinkan pengguna.
- **Backup & Restore JSON**: Ekspor dan impor konfigurasi + tata letak ke file `.json` melalui Storage Access Framework, serta opsi reset ke bawaan awal.

---

## Petunjuk Build & Uji Lokal

1. **Menjalankan seluruh Unit Test & Robolectric Test**:
   ```bash
   gradle :app:testDebugUnitTest
   ```
2. **Build Debug APK** (`app/build/outputs/apk/debug/app-debug.apk`):
   ```bash
   gradle :app:assembleDebug
   ```
3. **Build Release APK Teroptimasi R8** (memerlukan keystore release Anda via `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`):
   ```bash
   gradle :app:assembleRelease
   ```

---

## Instalasi, Menetapkan Default Home & Mengembalikan Launcher Bawaan

1. **Instalasi**: Pasang APK hasil build ke perangkat Android (Android 7.0+ / API 24–36).
2. **Menjadikan Default Home**: Ketuk banner **"Jadikan Nexus Launcher Utama"** di Home atau masuk ke **Pengaturan Nexus > Umum & Izin Sistem > Pilih Nexus sebagai Home Default** (menggunakan `RoleManager.ROLE_HOME` pada Android 10+ atau Pengaturan Aplikasi Default sistem).
3. **Kembali ke Launcher Bawaan**: Buka **Pengaturan Nexus > Tentang & Privasi > Buka Setelan Aplikasi Home Default** (atau **Setelan Android > Aplikasi > Aplikasi Default > Layar Utama**) lalu pilih launcher bawaan perangkat.
   - *Catatan Uninstall*: Jika Anda mengaktifkan **Device Admin** untuk gestur *Ketuk Dua Kali Kunci Layar*, nonaktifkan terlebih dahulu di **Pengaturan Nexus > Kontrol Gestur > Nonaktifkan Izin Device Admin** sebelum mencopot aplikasi.

---

## Ringkasan Verifikasi

- **Teruji Dieksekusi (12/12 PASS)**: `gradle :app:testDebugUnitTest` (Robolectric SDK 34 & 36 meliputi `LayoutEngineTest`, `LauncherStorageTest`, `ExampleUnitTest`, `ExampleRobolectricTest`, dan `NexusMicroRobolectricTest` untuk manifest HOME, nol izin `INTERNET`, `DeviceProfile`, tampilan native `MainActivity`, serta navigasi `SettingsActivity`), `gradle :app:assembleDebug`, `minifyReleaseWithR8`, `optimizeReleaseResources`, `lintVitalRelease`, serta analisis `apkanalyzer` & `dexdump`.
- **Butuh Uji Perangkat Fisik / Emulator Nyata**: Binding `AppWidgetHost` dari widget aplikasi pihak ketiga nyata, `NotificationListenerService` dengan notifikasi sistem langsung, `DevicePolicyManager.lockNow()`, dan pengukuran PSS RAM / baterai / `FrameMetrics` jank jangka panjang di perangkat nyata.
