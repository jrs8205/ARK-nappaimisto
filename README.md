# ARK-näppäimistö

[![Lataukset](https://img.shields.io/github/downloads/jrs8205/ARK-nappaimisto/total?label=lataukset)](https://github.com/jrs8205/ARK-nappaimisto/releases)
[![Uusin versio](https://img.shields.io/github/v/release/jrs8205/ARK-nappaimisto?label=uusin%20versio)](https://github.com/jrs8205/ARK-nappaimisto/releases/latest)

Suomalainen Android-näppäimistö, joka oppii sinun sanasi ja kirjoitustapasi
– kokonaan laitteella.

<p>
  <img src="docs/kuvat/ehdotukset.png" width="200" alt="Sanaehdotukset kirjoitettaessa">
  <img src="docs/kuvat/pitka-painallus.png" width="200" alt="Pitkän painalluksen merkkivalikko">
  <img src="docs/kuvat/kaannos.png" width="200" alt="Käännösnäkymä ja AI-käännös">
  <img src="docs/kuvat/leikepoyta.png" width="200" alt="Leikepöytä ja kiinnitetyt leikkeet">
</p>

## Asennus

Lataa `ark-nappaimisto-vX.Y.Z.apk` [Releases-sivulta](https://github.com/jrs8205/ARK-nappaimisto/releases/latest)
puhelimella, asenna se ja avaa sovellus. Ensimmäinen avaus opastaa
käyttöönoton. Päivitykset asentuvat vanhan päälle, ja opitut sanat säilyvät.
Toiselle laitteelle tiedot siirtyvät varmuuskopiolla (Asetukset → Varmuuskopio).

## Ominaisuudet

* **Oppiva ennustus:** omat sanat, sanaparit ja sanaketjut (myös
  rivinvaihtojen yli), seuraavan sanan ennustus ja 170 100 sanan
  yleissanasto. Ehdotusrivillä on 5–8 ehdotusta, ja pitkä painallus
  kiinnittää, poistaa tai estää sanan.
* **Kirjoittaminen:** suomalainen QWERTY, varovainen automaattikorjaus
  (askelpalautin peruu), välimerkkien jälkiväli, kaksoisvälilyönti
  pisteeksi, kursorin siirto välilyönnillä ja kenttäkohtaiset näppäimet.
* **Merkit pitkällä painalluksella:** valikosta valitaan napauttamalla.
  Kun numerorivi on piilossa, numerot ja erikoismerkit löytyvät
  kirjainten alta.
* **Työkalurivi:** kursoritila, verkko-osoitteet, sanelu, emojit,
  leikepöytä, käännös, peruutus ja asetukset. Järjestys on muokattavissa.
* **Leikepöytä:** tekstit ja kuvat sekä kiinnitetyt leikkeet.
  Kiinnittämättömät vanhenevat tunnissa, eikä arkaluonteisia kopioita
  tallenneta.
* **Käännös:** Google Kääntäjän kaltainen näkymä, jossa live-käännös
  tehdään laitteella. Lähdekieli tunnistetaan, käännöstä voi korjata
  paikallaan, ja ✨-nappi hakee AI-käännöksen.
* **Sanelu:** laitteen oma puheentunnistus tai tarkempi OpenAI-tunnistus,
  joka ei katkea melussa.
* **Muuta:** täyttöpalvelujen (esim. salasanojen hallinta, Bitwarden)
  ehdotukset ehdotusrivillä, säädettävä korkeus, opittujen sanojen
  hallinta, varmuuskopio sekä Material You -teema järjestelmän mukaan.

## AI-käännös ja sanelu: mitä tarvitaan

Näppäimistö toimii kokonaan ilman tilejä ja avaimia. Kaksi valinnaista
toimintoa käyttää ulkopuolista palvelua omalla API-avaimellasi:

| Toiminto | Tarvitaan | Hinta |
|---|---|---|
| Live-käännös | – | ilmainen, laitteella |
| ✨ AI-käännös | Claude- tai OpenAI-API-avain | käytön mukaan, viesti maksaa sentin murto-osan |
| Sanelu, laitteen tunnistus | – | ilmainen |
| Sanelu, OpenAI | OpenAI-API-avain | käytön mukaan (minuuttihinta) |

* **API-avain ei ole sama asia kuin tilaus.** Claude Pro/Max- ja ChatGPT
  Plus/Pro -tilaus eivät sisällä API-käyttöä. Avain luodaan erikseen
  osoitteessa [platform.claude.com](https://platform.claude.com/) tai
  [platform.openai.com](https://platform.openai.com/), ja se syötetään
  ARKin asetuksiin (Tekoäly).
* **Claude-tilauksella ei voi kirjautua.** Anthropic ei salli sitä muille
  sovelluksille ([ehdot](https://code.claude.com/docs/en/legal-and-compliance),
  tilanne 10/2026).
* **ChatGPT-tilauksella kirjautuminen on suunnitteilla.** OpenAI tarjoaa
  avoimen lähdekoodin sovelluksille
  [Sign in with ChatGPT](https://developers.openai.com/siwc) -kirjautumisen,
  jolla AI-käännös kuluttaisi tilauksen kiintiötä. Se kattaa vain
  tekstipyynnöt, joten OpenAI-sanelu vaatii jatkossakin API-avaimen.
* AI- ja käsin korjatut käännökset muistetaan, joten samasta tekstistä ei
  makseta kahdesti.

## Yksityisyys

Oppiminen ja ehdotukset ovat paikallisia, eikä sovelluksessa ole
analytiikkaa tai mainoksia. Teksti lähtee laitteelta vain, kun itse painat
✨, ja puhe vain OpenAI-sanelun ollessa käynnissä. Muuten verkkoa käytetään
vain käännösmallien kertalataukseen (~30 Mt/kieli) ja AI-mallilistan hakuun.
API-avaimet säilyvät laitteella Android Keystorella salattuina, eivätkä ne
kulje varmuuskopiossa. Salasanakentissä ei opita mitään.

## Kääntäminen lähdekoodista

```
./gradlew :app:assembleDebug
```

Vaatimukset: JDK 17+ ja Android SDK (compileSdk 36).

## Lisenssi

[GPL-3.0](LICENSE). Sanalista: Kotimaisten kielten keskuksen
Parole-taajuuslista ja Nykysuomen sanalista
([CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.fi)), ks.
[docs/sanalista.md](docs/sanalista.md).
