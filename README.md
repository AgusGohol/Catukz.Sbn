# Catukz.Sbn

Dashboard desktop untuk memantau **semua distributor musik dalam satu aplikasi** —
DistroKid, Amuse, SoundOn, OffStep, dan lainnya. Pantau pendapatan, stream,
status rilisan, dan payout dari satu tempat.

Dibangun dengan **Java 17 + JavaFX + SQLite**.

## Fitur (V1)

- 🔐 **Master password** — kredensial dikunci & dienkripsi (AES-256-GCM, kunci dari PBKDF2).
- 👤 **Account Manager** — simpan akun tiap distributor (email + password terenkripsi).
- 📥 **Import CSV** — importer fleksibel dengan *column-mapper* (format tiap distributor berbeda, petakan sekali lalu tersimpan).
- 📊 **Dashboard** — kartu ringkasan (pendapatan, stream, rilisan) + grafik per bulan, bisa difilter per distributor.
- 💰 **Rilisan & status** — tabel rilisan (Live/Pending/Rejected).
- 🔄 **Auto-update via GitHub Releases** — cek versi terbaru dari menu Settings.

> Aplikasi terisi **data contoh** saat pertama dijalankan supaya langsung bisa dilihat.
> Hapus file `~/.catukz-sbn/catukz.db` untuk mulai bersih.

## Menjalankan (dev)

Butuh **Java 17+**. Maven tidak perlu diinstal — pakai wrapper:

```bash
./mvnw javafx:run
```

(Windows: `mvnw.cmd javafx:run`)

## Build jar

```bash
./mvnw clean package
```

Menghasilkan `target/Catukz.Sbn.jar`.

## Auto-update (GitHub)

Aplikasi mengecek rilis terbaru di:

```
https://github.com/AgusGohol/Catukz.Sbn/releases/latest
```

Alurnya: naikkan `version` di `pom.xml` dan `App.VERSION`, buat GitHub Release
dengan tag `vX.Y.Z` dan lampirkan jar-nya. Aplikasi versi lama akan mendeteksi
versi baru dari menu **Settings → Cek update**.

## Roadmap

| Versi | Isi |
|-------|-----|
| **V1** ✅ | Import CSV, dashboard, account manager, cek update |
| V2 | Parser presisi per distributor (dari file laporan asli) |
| V3 | Payout reminder, export laporan gabungan |
| V4 | Auto-sync terjadwal (scraping login tersimpan, jadwal jarang & acak) + unduh-pasang update otomatis |

## Keamanan

- Password akun **tidak pernah** disimpan sebagai teks biasa — selalu terenkripsi dengan kunci turunan master password.
- Database lokal di `~/.catukz-sbn/catukz.db`, tidak dikirim ke mana pun.
