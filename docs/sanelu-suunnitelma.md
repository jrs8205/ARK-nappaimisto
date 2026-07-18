# Sanelu – puhesyöttö työkaluriviltä

Tämä dokumentti kattaa sanelun (kokonaissuunnitelman kohta 34, aikaistettu
käyttäjän toiveesta). Sanelun pitää toimia kaikilla puhelinmerkeillä, ei
vain Pixelillä.

## 1. Käyttö

* Työkaluriville tulee **mikrofoninappi** (järjestys: kursori → www →
  mikrofoni → asetukset). Napautus aloittaa sanelun; nappi korostuu
  kuuntelun ajaksi.
* Puhe ilmestyy kenttään sitä mukaa kun käyttäjä puhuu: osittaistulokset
  keskeneräisenä (alleviivattuna) tekstinä, valmistunut lause vahvistuu
  lopulliseksi.
* **Jatkuva sanelu**: lauseen valmistuttua kuuntelu jatkuu välittömästi
  — miettimistauko ei katkaise sanelua. Sanelu päättyy vasta kun:
  1. käyttäjä napauttaa mikrofoninappia uudelleen,
  2. yhtäjaksoista hiljaisuutta on kertynyt noin **15 sekuntia**, tai
  3. tekstikenttä vaihtuu tai tyhjenee — eli esimerkiksi kun käyttäjä
     painaa sovelluksen omaa lähetysnappia (ChatGPT, viestisovellus tms.),
     sanelu katkeaa siihen automaattisesti.
* Virhetilanteesta (lupa evätty, tunnistin ei käytettävissä) näytetään
  lyhyt ilmoitus.

## 2. Tekninen reitti (kaikki valmistajat)

* Vakiorajapinta `SpeechRecognizer` suomeksi (fi-FI).
* **Android 13+**: ensin laitteella tapahtuva tunnistus
  (`createOnDeviceSpeechRecognizer` + kielituen tarkistus). Jos suomen
  laitemallia ei ole tai Android on vanhempi, käytetään **järjestelmän
  tavallista tunnistuspalvelua** (Samsungilla ja muilla niiden oma tai
  Googlen palvelu; voi käyttää verkkoa).
* Jatkuvuus toteutetaan käynnistämällä tunnistin uudelleen jokaisen
  valmistuneen tuloksen ja tyhjän jakson jälkeen; hiljaisuusbudjetti
  (~15 s) lasketaan tyhjistä jaksoista ja nollautuu puheesta.
* **Mikrofonilupa** (RECORD_AUDIO) pyydetään pienellä läpinäkyvällä
  lupa-aktiviteetilla ensimmäisellä käytöllä, koska näppäimistö ei voi
  kysyä lupia suoraan.
* Sanellun tekstin sanat syötetään oppimiseen (sanat ja ketjut) samoin
  säännöin kuin kirjoitetut; salasanakentissä mikrofoni on pois käytöstä.

## 3. Yksityisyys

* Sovellus ei pyydä internet-oikeutta — puhe käsitellään laitteen
  puheentunnistuspalvelussa.
* README kertoo rehellisesti: tunnistus tapahtuu ensisijaisesti
  laitteella; jos laitemallia ei ole, laitteen tunnistuspalvelu voi
  käyttää verkkoa.
* Linjaus 18.7.2026: oppiminen ja ehdotukset pysyvät aina paikallisina,
  mutta internet-oikeus on sallittu ominaisuuksille, jotka sitä aidosti
  tarvitsevat, avoimesti dokumentoituna.

## 4. Testaus

* Yksikkötestit: sanellun tekstin oppimissyöttö (sanat ja ketjut
  final-tuloksesta), hiljaisuusbudjetin laskenta jos logiikka eriytetään.
* Laitetesti (Pixel 8a): jatkuvuus miettimistauon yli, katkaisu
  lähetysnapista, 15 sekunnin hiljaisuuskatkaisu, lupakysely ensimmäisellä
  kerralla, osittaistulosten sujuvuus, salasanakentän esto ja sanellun
  sanan nouseminen ehdotuksiin.

## 5. Ulkopuolelle jää

Saneluasetukset (ei lisätä kytkimiä), komentojen tunnistus ("uusi rivi",
"pilkku" tms. sanasta merkiksi), jälkikäteinen välimerkkien lisäys ja
muiden kielten sanelu. Näitä harkitaan vasta käyttökokemuksen perusteella.
