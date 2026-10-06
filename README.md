# ARK-näppäimistö

[![Lataukset](https://img.shields.io/github/downloads/jrs8205/ARK-nappaimisto/total?label=lataukset)](https://github.com/jrs8205/ARK-nappaimisto/releases)
[![Uusin versio](https://img.shields.io/github/v/release/jrs8205/ARK-nappaimisto?label=uusin%20versio)](https://github.com/jrs8205/ARK-nappaimisto/releases/latest)

Suomalainen Android-näppäimistö, joka oppii sinun sanasi ja kirjoitustapasi
– kokonaan laitteella. Mukana sanelu, leikepöytä, emojit ja Google Kääntäjän
kaltainen käännösnäkymä.

<p>
  <img src="docs/kuvat/ehdotukset.png" width="200" alt="Sanaehdotukset kirjoitettaessa">
  <img src="docs/kuvat/pitka-painallus.png" width="200" alt="Pitkän painalluksen merkkivalikko">
  <img src="docs/kuvat/kaannos.png" width="200" alt="Käännösnäkymä ja AI-käännös">
  <img src="docs/kuvat/leikepoyta.png" width="200" alt="Leikepöytä ja kiinnitetyt leikkeet">
</p>

## Lataus ja asennus

1. Lataa `ark-nappaimisto-vX.Y.Z.apk` [Releases-sivulta](https://github.com/jrs8205/ARK-nappaimisto/releases/latest)
   puhelimella ja avaa se. Salli asennus kysyttäessä.
2. Avaa ARK-näppäimistö-sovellus. Ensimmäinen avaus opastaa käyttöönoton
   (näppäimistön salliminen ja valinta) ja esittelee ominaisuudet.

Päivitykset asentuvat vanhan päälle, ja opitut sanat säilyvät. Laitteelta
toiselle tiedot siirtyvät varmuuskopiolla (Asetukset → Varmuuskopio).

## Ominaisuudet

**Kirjoittaminen**

* Suomalainen QWERTY (Å, Ä, Ö). Numerorivin voi piilottaa, jolloin
  numerot ja erikoismerkit löytyvät kirjainten pitkällä painalluksella.
* Pitkä painallus avaa merkkivalikon, josta merkki valitaan napauttamalla.
  Napautus muualle sulkee valikon.
* Oppii omat sanasi, sanaparisi ja sanaketjusi (myös rivinvaihtojen yli) ja
  ennustaa seuraavan sanan. Yleissanastossa on 170 100 sanaa.
* Ehdotusrivillä on 5–8 vieritettävää ehdotusta. Pitkä painallus ehdotukseen
  kiinnittää, poistaa tai estää sanan.
* Varovainen automaattikorjaus: askelpalautin heti korjauksen jälkeen peruu
  sen, ja ARK oppii sanan.
* Välimerkkien jälkiväli, kaksoisvälilyönti pisteeksi ja iso alkukirjain
  lauseen alussa. Osoitteet (jarsi.org) ja desimaalit (3,14) säilyvät
  ehjinä.
* Välilyönnin liu'utus siirtää kursoria. Pohjassa pidetty askelpalautin
  poistaa sana kerrallaan.
* Kenttäkohtaiset näppäimet (@ sähköpostissa, / osoitteessa, Enterin
  toiminto kentän mukaan).
* Korkeus on säädettävissä. Vaakasuunnassa näppäimistö vie enintään 65 %
  ikkunasta.
* Täyttöpalvelujen (esim. Googlen salasanojen hallinta, Bitwarden)
  ehdotukset näkyvät ehdotusrivillä (Android 11+).

**Työkalurivi**

* Kursoritila, verkko-osoitteet, sanelu, emojit, leikepöytä, käännös,
  peruutus ja asetukset. Järjestyksen ja näkyvyyden voi muokata.
* Leikepöytä: tekstit ja kuvat, kiinnitetyt leikkeet ja oma uusi leike.
  Kiinnittämättömät leikkeet vanhenevat tunnissa, eikä arkaluonteisiksi
  merkittyjä kopioita tallenneta.

**Käännös**

* Kirjoitusalue ylhäällä, käännös alla livenä. Käännös tehdään laitteella
  (Google ML Kit), ja kielimallit (~30 Mt/kieli) ladataan kerran luvallasi.
* Lähdekieli tunnistetaan tekstistä. Käännöstä voi korjata paikallaan, ja
  sen voi kopioida tai viedä kenttään.
* ✨ hakee laadukkaamman AI-käännöksen (ks. alla). AI- ja käsin korjatut
  käännökset muistetaan, joten samasta tekstistä ei makseta kahdesti.
* Teksti säilyy näkymässä sovelluksesta toiseen vaihtaessa (5–120 min,
  säädettävissä).

**Sanelu**

* Oletuksena käytetään laitteen omaa puheentunnistusta (ilmainen).
* Vaihtoehtona on OpenAI-puheentunnistus. Se tunnistaa suomea tarkemmin
  eikä katkea melussa, ja teksti ilmestyy puheen tahdissa. Omat sanasi
  menevät tunnistukselle vihjeinä.

**Muuta**

* Opittujen sanojen hallinta: haku, kiinnitys, eston purku, poisto ja
  tyhjennys.
* Varmuuskopio: sanat, sanaketjut, kiinnitetyt leikkeet ja asetukset
  (ei API-avaimia).
* Erikoismerkkien järjestys on muokattavissa raahaamalla.
* Teema seuraa järjestelmän tummaa tilaa ja Material You -värejä. Värit
  täyttävät WCAG AAA -kontrastivaatimukset.

## AI-käännös ja sanelu: API-avaimet ja kirjautuminen

Näppäimistö toimii kokonaan ilman tilejä ja avaimia. Vain kaksi valinnaista
toimintoa käyttää ulkopuolista palvelua:

| Toiminto | Mitä tarvitaan | Hinta |
|---|---|---|
| Live-käännös (laitteella) | ei mitään | ilmainen |
| ✨ AI-käännös | Claude- tai OpenAI-API-avain | käytön mukaan, viestin käännös maksaa sentin murto-osan |
| Sanelu, laitteen tunnistus | ei mitään | ilmainen |
| Sanelu, OpenAI | OpenAI-API-avain | käytön mukaan (minuuttihinta) |

**API-avain ei ole sama asia kuin tilaus.** Claude Pro/Max- tai ChatGPT
Plus/Pro -tilaus ei sisällä API-käyttöä. API-avain luodaan erikseen
osoitteessa [platform.claude.com](https://platform.claude.com/) tai
[platform.openai.com](https://platform.openai.com/), ja sen käyttö
laskutetaan tililtä käytön mukaan. Avain syötetään ARKin asetuksiin
(Tekoäly), ja se säilyy laitteella Android Keystorella salattuna.

**Claude-tilauksella kirjautuminen ei ole mahdollista.** Anthropic ei salli
muiden sovellusten kirjautumista Claude-tilillä eikä tilauksen käyttöä
niiden kautta ([Anthropicin ehdot](https://code.claude.com/docs/en/legal-and-compliance),
tilanne 10/2026). Claude toimii ARKissa vain API-avaimella.

**ChatGPT-tilauksella kirjautuminen on suunnitteilla.** OpenAI tarjoaa
avoimen lähdekoodin sovelluksille
[Sign in with ChatGPT](https://developers.openai.com/siwc) -kirjautumisen.
Sen avulla AI-käännös voisi kuluttaa ChatGPT-tilauksen kiintiötä
API-saldon sijaan. Kirjautuminen kattaa vain tekstipyynnöt, joten
OpenAI-sanelu vaatii jatkossakin API-avaimen.

## Yksityisyys

* Oppiminen ja ehdotukset ovat täysin paikallisia. Sovelluksessa ei ole
  analytiikkaa eikä mainoksia.
* Teksti lähtee laitteelta vain, kun itse painat ✨-AI-käännöstä. Puhe
  lähtee vain, kun olet valinnut OpenAI-sanelun ja sanelu on käynnissä
  (enintään viisi minuuttia kerrallaan). Molemmat vaativat oman avaimesi.
* Verkkoa käytetään lisäksi käännösmallien kertalataukseen (Google ML Kit)
  ja AI-mallilistan hakuun omalla avaimellasi.
* Salasana- ja muissa arkaluonteisissa kentissä ei opita mitään.

## Kääntäminen lähdekoodista

```
./gradlew :app:assembleDebug
```

Vaatimukset: JDK 17 tai uudempi ja Android SDK (compileSdk 36).

## Lisenssi

[GPL-3.0](LICENSE). Yleinen suomen sanalista on muodostettu Kotimaisten
kielten keskuksen Parole-taajuuslistasta ja Nykysuomen sanalistasta
([CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.fi)), ks.
[docs/sanalista.md](docs/sanalista.md).
