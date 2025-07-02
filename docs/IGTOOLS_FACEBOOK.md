# Integrasi Facebook di Halaman IG Tools

Dokumen ini menjelaskan alur memuat foto profil dan nama akun Facebook di halaman **Instagram Tools** apabila sesi login masih ada.

## 1. Pengecekan Sesi

Ketika `InstagramToolsFragment` aktif, fungsi `updateFacebookStatus()` dipanggil. Fungsi ini memeriksa `AccessToken.getCurrentAccessToken()` dari Facebook SDK. Bila token masih valid maka profil pengguna dimuat lewat Graph API.

## 2. Mendapatkan Nama dan Foto Profil

Permintaan `GraphRequest.newMeRequest()` digunakan dengan parameter `fields=name,picture.type(large)` untuk memperoleh nama dan URL foto profil. Nilai tersebut kemudian ditampilkan pada `image_facebook` dan `text_facebook_username`.

## 3. Proses Login

Apabila token belum ada, menekan ikon Facebook memicu `LoginManager.logInWithReadPermissions()` dengan izin `public_profile`. Setelah berhasil, callback `onSuccess` menyimpan token bawaan SDK dan memanggil kembali `updateFacebookStatus()`.

## 4. Penyimpanan Sesi

Facebook SDK secara otomatis menyimpan token di `SharedPreferences` sehingga sesi tetap ada saat aplikasi dibuka ulang. Selama token masih berlaku, halaman IG Tools akan langsung menampilkan nama dan foto profil pengguna tanpa login ulang.
