# Facebook Login via Facebook SDK

Fragment ini kini menggunakan Facebook SDK resmi sehingga proses login lebih sederhana.

1. Ketika tombol **Login dengan Facebook** ditekan, `LoginManager.logInWithReadPermissions()` dipanggil dengan izin `public_profile`.
2. Setelah autentikasi berhasil, callback `onSuccess` menyimpan token bawaan SDK.
3. Fungsi `updateFacebookStatus()` menjalankan `GraphRequest.newMeRequest()` untuk mendapatkan nama dan foto profil.
4. Selama token tersimpan dan belum kedaluwarsa, fragment otomatis menampilkan informasi pengguna tanpa perlu login ulang.
5. Tombol **Logout** memanggil `LoginManager.logOut()` dan menyembunyikan data profil.
