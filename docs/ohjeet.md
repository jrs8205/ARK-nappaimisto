# ARK-näppäimistö – ohjeet

[← Takaisin etusivulle](../README.md)

## Asennus

1. Lataa `ark-nappaimisto-vX.Y.Z.apk` [Releases-sivulta](https://github.com/jrs8205/ARK-nappaimisto/releases/latest)
   puhelimella ja asenna se.
2. Avaa ARK-näppäimistö-sovellus. Ensimmäinen avaus opastaa käyttöönoton.

Päivitykset asentuvat vanhan päälle, ja opitut sanat säilyvät. Toiselle
laitteelle tiedot siirtyvät varmuuskopiolla (Asetukset → Varmuuskopio).
API-avaimet eivät kulje varmuuskopiossa.

## Vinkit

- **Numerorivi pois:** numerot ja erikoismerkit löytyvät silloin
  kirjainten pitkällä painalluksella.
- **Ehdotuksen pitkä painallus:** kiinnitä, poista tai estä sana.
- **Väärä korjaus:** askelpalautin heti korjauksen jälkeen peruu sen.
- **Kursori:** liu'uta sormea välilyönnillä.
- **Sana kerrallaan pois:** pidä askelpalautinta pohjassa.
- **Piste:** kaksi nopeaa välilyöntiä.

## AI-käännös ja sanelu

Näppäimistö toimii kokonaan ilman tilejä ja avaimia. Kaksi valinnaista
toimintoa käyttää ulkopuolista palvelua omalla API-avaimellasi.

| Toiminto | Tarvitaan | Hinta |
|---|---|---|
| Live-käännös | – | ilmainen |
| ✨ AI-käännös | Claude- tai OpenAI-API-avain | sentin murto-osa / viesti |
| Sanelu, laitteen tunnistus | – | ilmainen |
| Sanelu, OpenAI | OpenAI-API-avain | minuuttihinta |

### API-avain ei ole sama asia kuin tilaus

Claude Pro/Max- ja ChatGPT Plus/Pro -tilaus eivät sisällä API-käyttöä.
Avain luodaan erikseen, ja sen käyttö laskutetaan käytön mukaan:

- Claude: [platform.claude.com](https://platform.claude.com/)
- OpenAI: [platform.openai.com](https://platform.openai.com/)

Avain syötetään ARKin asetuksiin kohtaan Tekoäly.

### Kirjautuminen tilauksella

- **Claude-tilauksella ei voi kirjautua.** Anthropic ei salli sitä muille
  sovelluksille ([ehdot](https://code.claude.com/docs/en/legal-and-compliance),
  tilanne 10/2026).
- **ChatGPT-tilauksella kirjautuminen on suunnitteilla.** OpenAI:n
  [Sign in with ChatGPT](https://developers.openai.com/siwc) -kirjautumisella
  AI-käännös kuluttaisi tilauksen kiintiötä. Se kattaa vain tekstin, joten
  OpenAI-sanelu vaatii jatkossakin API-avaimen.

AI- ja käsin korjatut käännökset muistetaan, joten samasta tekstistä ei
makseta kahdesti.

## Yksityisyys

- Oppiminen ja ehdotukset ovat paikallisia. Sovelluksessa ei ole
  analytiikkaa eikä mainoksia.
- Kirjoittamasi teksti lähtee laitteelta vain, kun itse painat ✨.
- OpenAI-sanelussa puhe virtaa OpenAI:lle vain sanelun ollessa käynnissä
  (enintään viisi minuuttia kerrallaan). Mukana lähtevät käytetyimmät
  opitut sanasi tunnistusvihjeinä, jotta omat nimet ja termit tunnistuvat;
  itse sanasto pysyy laitteella.
- Laitteen oma puheentunnistus toimii ensisijaisesti laitteella. Jos
  suomen laitemallia ei ole, puhelimen tunnistuspalvelu voi käyttää verkkoa.
- Lisäksi verkkoa käytetään käännösmallien kertalataukseen (~30 Mt/kieli)
  ja AI-mallilistan hakuun.
- API-avaimet säilyvät laitteella Android Keystorella salattuina.
- Salasanakentissä ei opita mitään.

## Kääntäminen lähdekoodista

```
./gradlew :app:assembleDebug
```

Vaatimukset: JDK 17+ ja Android SDK (compileSdk 36).
