# ARK-näppäimistö

[![Lataukset](https://img.shields.io/github/downloads/jrs8205/ARK-nappaimisto/total?label=lataukset)](https://github.com/jrs8205/ARK-nappaimisto/releases)
[![Uusin versio](https://img.shields.io/github/v/release/jrs8205/ARK-nappaimisto?label=uusin%20versio)](https://github.com/jrs8205/ARK-nappaimisto/releases/latest)

Oma suomalainen näppäimistösovellus Androidille (IME, Input Method Editor).

ARK-näppäimistö toimii Androidin järjestelmätason näppäimistönä, jonka voi ottaa
käyttöön puhelimen asetuksista ja käyttää lähes kaikissa sovelluksissa:
viestisovelluksissa, selaimessa, sähköpostissa, muistiinpanoissa ja muissa
tekstikentissä.

## Lataus ja asennus

Uusin versio on [Releases-sivulla](https://github.com/jrs8205/ARK-nappaimisto/releases/latest)
(`ark-nappaimisto-vX.Y.Z.apk`).

1. Lataa APK puhelimella ja avaa se; salli asennus kysyttäessä.
2. Avaa ARK-näppäimistö-sovellus ja valitse **Ota näppäimistö käyttöön**.
3. Valitse **Vaihda näppäimistöksi** ja poimi ARK-näppäimistö listasta.

Päivitykset asentuvat vanhan version päälle ja opitut sanat säilyvät.
Oppimisdatan voi siirtää laitteelta toiselle varmuuskopiolla
(Asetukset → Varmuuskopio).

## Tavoite

Näppäimistön keskeinen tarkoitus on oppia käyttäjän oma kirjoitustapa
tavallisia näppäimistöjä tehokkaammin:

* usein käytetyt sanat ja käyttäjän omat erikoissanat
* usein käytetyt sanaparit ja lauseiden alut
* tietyissä sovelluksissa käytetty sanasto
* hyväksytyt ja hylätyt ehdotukset
* automaattisesti korjatut ja takaisin palautetut sanat
* käyttäjän viimeaikaiset kirjoitusaiheet

## Lupaukset

* oppii käyttäjän omat sanat nopeasti
* ei pakota yleissanakirjan sanoja käyttäjän omien sanojen edelle
* muistaa usein käytetyt sanaparit
* oppii automaattikorjausten peruuttamisesta
* näyttää enemmän kuin kolme ehdotusta (5–8 vieritettävällä rivillä)
* oppii ja ehdottaa täysin paikallisesti
* ei lähetä kirjoitettua tekstiä minnekään ilman käyttäjän omaa,
  tietoista toimintoa
* antaa käyttäjän hallita kaikkea opittua tietoa

## Tila

Projekti on kehitysvaiheessa. Vaiheittainen eteneminen:

| Vaihe | Sisältö | Tila |
|---|---|---|
| 1 | Toimiva perusnäppäimistö: suomalainen QWERTY, numerorivi, teemat, asetussivu | Valmis |
| 2 | Vieritettävä ehdotusrivi ja yleinen suomen sanalista | Valmis |
| 3 | Henkilökohtaisten sanojen oppiminen | Valmis |
| 4 | Sanaparit ja trigrammit, seuraavan sanan ennustus | Valmis |
| 5 | Palautteesta oppiminen (hyväksynnät, hylkäykset, korjausten peruutukset) | Valmis |
| 6 | Sovelluskohtainen oppiminen | Poistettu suunnitelmasta |
| 7 | Oikeinkirjoitus ja varovainen automaattikorjaus | Valmis |
| 8 | Fraasit ja tekstipohjat | Katettu kiinnitetyillä leikkeillä |
| 9 | Paikallinen kielimalli ehdotusten uudelleenjärjestämiseen | — |
| 10 | Edistyneet ominaisuudet | — |

## Ominaisuudet (vaihe 1)

* suomalainen QWERTY-asettelu Å-, Ä- ja Ö-kirjaimilla
* numerorivi aina näkyvissä
* Shift, isot lukkoon toisella napautuksella, automaattinen iso alkukirjain
* pitkä painallus lisämerkeille numerorivillä ja välimerkeissä
* kaksi symbolisivua ja numeronäppäimistö numerokentille
* kenttäkohtainen mukautus: @ sähköpostikentässä, / osoitekentässä,
  Enter-näppäimen toiminto kentän mukaan (Hae, Lähetä, Siirry…)
* vaalea ja tumma teema järjestelmän asetuksen mukaan
* säädettävä näppäimistön korkeus
* näppäinäänet, värinä ja esikatselukupla
* välilyönnin pyyhkäisy siirtää kursoria
* salasanakentissä esikatselu pois käytöstä

## Ominaisuudet (vaihe 2)

* vieritettävä ehdotusrivi: täydennykset yleisestä suomen sanalistasta
  (nykyisin 170 100 sanamuotoa taajuuksineen, ks.
  [docs/sanalista.md](docs/sanalista.md))
* työkalurivi: kursorinsiirtotila, verkko-osoitesivu, sanelu, emojit,
  leikepöytä, korjausnäkymä, käännös ja asetukset
* välimerkki siirtyy ehdotuksen lisäämän välilyönnin eteen
* isot alkukirjaimet ehdotuksissa lauseen alussa
* ehdotusten asetukset: näkyvyys, välilyönti hyväksynnän jälkeen,
  yleisimmät sanat tyhjällä syötteellä
* teema seuraa järjestelmän tummaa tilaa; salasana- ja numerokentissä
  ehdotukset pois käytöstä

## Ominaisuudet (vaihe 3)

* henkilökohtaisten sanojen oppiminen: käsin kirjoitetut sanat (myös
  numeroita sisältävät kuten tuotekoodit sekä verkko-osoitteet) nousevat
  ehdotuksiin heti ensimmäisestä kerrasta
* omat sanat ehdotusrivin kärkeen käyttömäärän ja tuoreuden mukaan,
  alkuperäisessä kirjoitusasussaan
* pitkä painallus ehdotukseen: Poista opittu sana / Älä ehdota tätä
* sanaketjujen tallennus rivinvaihtojen yli seuraavan sanan ennustusta
  varten (vaihe 4)
* kaikki oppimisdata paikallisessa tietokannassa; salasanakentissä ei
  opita mitään

## Ominaisuudet (vaihe 4)

* seuraavan sanan ennustus omista sanaketjuista: sanan päätyttyä rivin
  kärkeen nousevat todennäköisimmät jatkot
* ketjut tallentuvat rivinvaihtojen yli, joten myös listamaiset
  kirjoitusrutiinit (esim. koodi ⏎ määrä ⏎ nouto) ennustuvat — ominaisuus,
  jota yleiset näppäimistöt eivät osaa
* trigramit tarkentavat ennustusta, kun kaksi edeltävää sanaa tunnetaan
* ennustus toimii myös vanhan tekstin perään jatkettaessa

## Ominaisuudet (vaihe 5)

* yhtenäinen pisteytysmalli: yleisyys, oma käyttö, ketjuosumat,
  hyväksynnät, kiinnitys ja ohitukset painottavat ehdotuksia yhdessä
* hyväksytty ehdotus nousee jatkossa korkeammalle; toistuvasti ohitettu
  täydennys laskee hiljalleen
* sanan kiinnitys: kiinnitetty sana nousee aina kärkeen kun sanan alku
  täsmää, ja se merkitään ehdotuksessa pienellä pisteellä
* korjausten peruutuksista oppiminen tulee automaattikorjauksen mukana
  (vaihe 7)

## Ominaisuudet (vaihe 7)

* varovainen automaattikorjaus: välilyönti korjaa tuntemattoman sanan
  lähimpään tunnettuun; omat sanat, koodit ja estetyt jäävät aina rauhaan
* askelpalautin heti korjauksen jälkeen peruu sen — peruttu sana opitaan,
  eikä sitä korjata enää uudestaan
* korjausnäkymä työkaluriviltä: kentän koko teksti sanoina ja
  tuntemattomat sanat alleviivattuna; sanaa napauttamalla ehdotusrivi
  näyttää vaihtoehdot (kirjoitusvirheet ja paikkaan sopivat sanat) ja
  valinta korvaa sanan paikallaan — kätevä myös sanellun tekstin
  oikolukuun

## Muut ominaisuudet

* opittujen sanojen hallinta asetuksissa: haku, kiinnitys, eston purku,
  poisto ja koko oppimishistorian tyhjennys
* yleissanasto 170 100 sanaa: Parole-taajuuslista täydennettynä Kotuksen
  Nykysuomen sanalistalla (ks. [docs/sanalista.md](docs/sanalista.md))
* jatkuva sanelu työkalurivin mikrofonista: miettimistauko ei katkaise,
  ja sanelu päättyy hiljaisuuteen, mikrofonin napautukseen tai kentän
  vaihtumiseen (esim. viestin lähetys). Puhe käsitellään laitteen
  puheentunnistuspalvelussa — ensisijaisesti laitteella; jos suomen
  laitemallia ei ole, laitteen palvelu voi käyttää verkkoa. Sanellut
  sanat oppivat kuten kirjoitetut.
* leikepöytä työkaluriviltä: tekstit ja kuvat kaksisarakkeisessa
  ruudukossa, kiinnitys neulasta, kolmen pisteen valikosta liittäminen,
  haku verkosta ja poisto, sekä oman kiinnitetyn leikkeen luonti
  plus-napista. Kiinnittämättömät vanhenevat tunnissa, ja
  arkaluonteisiksi merkityt kopiot ohitetaan kokonaan.
* varmuuskopio asetuksista: opitut sanat, sanaketjut ja kiinnitetyt
  tekstileikkeet JSON-tiedostoon ja takaisin; tuonti yhdistää tiedot
  turvallisesti, joten saman tiedoston voi tuoda useankin kerran
* erikoismerkkien järjestys: symbolisivujen merkit voi järjestää
  asetuksissa raahaamalla, myös sivujen välillä
* sanelun sanastovihjeet: käytetyimmät omat sanat ohjaavat
  puheentunnistusta (Android 13 tai uudempi)
* emojipaneeli työkaluriviltä: kategoriat, viimeksi käytetyt ja
  laitetuen mukainen valikoima
* käännös työkaluriviltä: kentän tekstin käännös laitteella (Google
  ML Kit), kielipari vaihdettavissa ja suunta käännettävissä;
  kielimallit ladataan ja poistetaan asetuksista (~30 Mt/kieli)

## Yksityisyys

Oppiminen ja ehdotukset ovat kokonaan paikallisia, eikä sovelluksessa ole
analytiikkaa tai mainoksia. Sovelluksella on internet-oikeus yhtä
tarkoitusta varten: käännöskielten mallien kertalataus (Google ML Kit).
Itse käännös tehdään laitteella, eikä kirjoitettua tekstiä lähetetä
minnekään ilman käyttäjän omaa, tietoista toimintoa. Oppiminen kytkeytyy
kokonaan pois salasana- ja muissa arkaluonteisissa kentissä.

## Kääntäminen

Projekti käännetään Android Studiolla tai komentoriviltä:

```
./gradlew :app:assembleDebug
```

Vaatimukset: JDK 17 tai uudempi ja Android SDK (compileSdk 36).

## Lisenssi

Katso [LICENSE](LICENSE).

Yleinen suomen sanalista on muodostettu Kotimaisten kielten keskuksen
Parole-taajuuslistasta ja Nykysuomen sanalistasta
([CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.fi)),
katso [docs/sanalista.md](docs/sanalista.md).
