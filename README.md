# Računi, offline evidencija

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
- Tokom uvoza iz galerije na ekranu stoji prozor sa tokom posla: koja je slika
  na redu, traka napretka i broj do sada pronađenih računa. Traženje QR koda po
  velikoj fotografiji traje, pa se vidi da aplikacija radi.
- U spisku stoje samo najbitniji podaci: vreme, radnja, mesto, iznos i broj
  stavki. Klik na račun otvara prikaz redom vreme, lokacija, stavke, ukupno.
- Dugme „Prikaži detalje" otkriva ostalo: PIB, broj računa, poreze po
  stavkama, QR sadržaj i poslednju grešku. Dugme „Otvori digitalni račun"
  vodi na zvaničnu stranicu Poreske uprave.
- Skener ima kvadratni okvir na sredini ekrana, bez crvene linije i bez
  teksta. Okvir je tačno ona oblast koju kamera dekodira.
- Zapis se briše dugim pritiskom na karticu ili dugmetom u prozoru računa,
  uvek uz potvrdu.
- Uvoz iz galerije ne zaustavlja rad: tok stoji na vrhu ekrana i u traci
  obaveštenja, pa spisak može da se pregleda dok skeniranje traje. Obaveštenje
  radi i kada se izađe iz aplikacije.
- „Pregled kupovina" računa sve iz već sačuvanih računa, bez ijednog novog
  zahteva prema mreži: stavke sa vremenom kupovine, zbir po radnjama i zbir po
  vrstama radnji (marketi, pekare, apoteke, benzinske stanice i slično).
  Filteri za mesec, radnju i vrstu se slažu.
- Kodovi koji nisu fiskalni računi (interne nalepnice radnje) nemaju odakle
  da dobiju prodavnicu, iznos i stavke. Oni se prepoznaju, označavaju kao
  „Kôd bez podataka" i mogu da se obrišu odjednom, jednim dugmetom iznad
  spiska. Starije verzije aplikacije su takve kodove čuvale kao račune.

## Izgradnja

```sh
./gradlew testDebugUnitTest lintDebug assembleRelease
```

Release APK se nalazi u `app/build/outputs/apk/release/app-release.apk`.
