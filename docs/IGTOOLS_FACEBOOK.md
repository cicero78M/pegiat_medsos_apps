# Integrasi Facebook di Halaman IG Tools

Dokumen ini menjelaskan alur memuat foto profil dan nama akun Facebook di halaman **Instagram Tools** apabila sesi login masih ada.

## 1. Pengecekan Sesi

Saat `InstagramToolsFragment` dibuat atau kembali aktif, fungsi `updateFacebookStatus()` dijalankan. Fungsi ini memanggil `FacebookSessionManager.loadCookies()` untuk membaca cookie yang tersimpan secara terenkripsi. Jika cookie ditemukan, fragment akan mencoba mengambil halaman `https://m.facebook.com/me` menggunakan OkHttp.

## 2. Mendapatkan Nama dan Foto Profil

Respon HTML dari `/me` dipindai menggunakan regex untuk mencari tag `<title>` sebagai nama pengguna dan URL gambar profil. Nilai tersebut lalu ditampilkan pada `image_facebook` dan `text_facebook_username`.

## 3. Proses Login

Bila belum ada cookie, pengguna dapat menekan ikon Facebook pada halaman IG Tools. Hal ini membuka `FacebookLoginActivity` yang menampilkan WebView login dari `m.facebook.com`. Setelah kredensial valid dan halaman mengarah ke `/me`, cookie disimpan lewat `FacebookSessionManager.saveCookies()` dan activity ditutup.

## 4. Penyimpanan Sesi

Cookie disimpan di `EncryptedSharedPreferences` sehingga tetap aman dan dapat digunakan kembali ketika aplikasi dibuka ulang. Setiap kali `InstagramToolsFragment` muncul, cookie ini otomatis dipasang ke `CookieManager` sehingga status login tidak hilang.

Dengan alur ini, halaman IG Tools dapat langsung menampilkan nama dan foto profil Facebook tanpa login ulang selama cookie masih valid.
