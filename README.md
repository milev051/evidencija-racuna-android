# Računi — offline evidencija

Mala Android aplikacija za lokalnu evidenciju kupovina.

## Preuzimanje

Najnoviji instalacioni APK nalazi se na stranici
[GitHub Releases](https://github.com/milev051/evidencija-racuna-android/releases/latest).

- Fiskalni QR se dekodira na telefonu i odmah upisuje u SQLite bazu.
- Iz galerije može da se izabere jedna ili više fotografija; slike bez QR koda
  Poreske uprave se preskaču. Ugrađeni ML Kit model radi lokalno i pouzdanije
  čita male i rotirane QR kodove sa fotografija celog računa. Ako skeniranje
  cele fotografije ne uspe, automatski se proveravaju preklapajući delovi slike
  kako bi mali QR zauzeo veći deo kadra.
- Interni, reklamni i numerički kodovi sa računa se ne čuvaju kao fiskalni
  računi, jer iz njih nije moguće dobiti prodavnicu, iznos i stavke.
- Isti QR račun se čuva samo jednom, bez obzira da li je ponovo skeniran ili
  izabran više puta iz galerije.
- Ako QR sadrži internet adresu digitalnog računa, WorkManager čeka mrežu,
  preuzima čitljiv tekst i dopunjava isti lokalni zapis.
- Za linkove `suf.purs.gov.rs` čuvaju se posebna polja: PIB, preduzeće,
  prodajno mesto, adresa, grad, opština, vreme računa, ukupan iznos i broj
  računa. Svaka stavka se čuva zasebno sa količinom, cenom, osnovicom i PDV-om,
  pa podaci kasnije mogu da se sortiraju i sabiraju bez ponovnog preuzimanja.
- Ako nema mreže ili obrada ne uspe, originalni QR ostaje sačuvan i pokušaj se
  automatski ponavlja.
- Ručni unos je jedno slobodno tekstualno polje.
- Svaki zapis dobija tačan trenutak unosa i prikazuje se od najnovijeg ka
  najstarijem.

## Izgradnja

```sh
./gradlew testDebugUnitTest lintDebug assembleRelease
```

Release APK se nalazi u `app/build/outputs/apk/release/app-release.apk`.
