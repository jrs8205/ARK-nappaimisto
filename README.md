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
2. Avaa ARK-näppäimistö-sovellus: ensimmäinen avaus opastaa
   käyttöönoton (näppäimistön salliminen ja valinta oletukseksi) ja
   esittelee tärkeimmät ominaisuudet. Esittelyn voi katsoa uudelleen
   asetuksista.

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
* numerorivi, jonka voi piilottaa asetuksista; piilotettuna numerot
  löytyvät ylärivin pitkällä painalluksella
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
  leikepöytä, korjausnäkymä, käännös, peruutus ja asetukset; nappien
  järjestyksen ja näkyvyyden voi muokata asetuksista
* välimerkki siirtyy ehdotuksen lisäämän välilyönnin eteen
* älykäs jälkiväli: välimerkin perään kirjoitettu kirjain saa välin
  eteensä ja uusi lause alkaa isolla; askelpalautin peruu välin, joten
  osoitteet (jarsi.org) ja desimaalit (3.14) säilyvät ehjinä
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
  poisto ja koko oppimishistorian tyhjennys; käyttömäärät laskevat myös
  ehdotusriviltä valitut kerrat
* peruutusnappi työkalurivillä: peruu viimeisimmän näppäimistön
  toimenpiteen (esim. liittämisen tai automaattikorjauksen); muissa
  tilanteissa pyytää kentän omaa peruutusta
* välitön jälkiväli: välimerkki sanan perässä saa välin heti peräänsä
  ("sana," → "sana, ") sekä kentässä että käännösrivillä; numeroiden
  välissä (3,14) sääntö ei laukea, ja osoitteen (jarsi.org) välin saa
  pois yhdellä askelpalauttimella. Osoite-, sähköposti- ja
  salasanakentissä sääntö ei ole käytössä. Säännöt toimivat myös
  monirivisten kenttien rivien lopussa.
* askelpalautin pohjassa poistaa sanan kerrallaan; napautus poistaa
  merkin kerrallaan
* kaksoisvälilyönti lisää pisteen: kaksi nopeaa välilyöntiä sanan
  perässä muuttuu muotoon ". " (kytkettävissä pois asetuksista)
* yleissanasto 170 100 sanaa: Parole-taajuuslista täydennettynä Kotuksen
  Nykysuomen sanalistalla (ks. [docs/sanalista.md](docs/sanalista.md))
* jatkuva sanelu työkalurivin mikrofonista: Android 13:sta alkaen
  mikrofoni pysyy auki tulosten välillä, joten puhetta ei katoa
  taukojen katveisiin. Sanelu alkaa aina isolla kirjaimella ja päättyy
  hiljaisuuteen (raja säädettävissä asetuksista 2–10 sekuntia),
  mikrofonin napautukseen tai kentän vaihtumiseen. Puhe käsitellään
  laitteen puheentunnistuspalvelussa — ensisijaisesti laitteella; jos
  suomen laitemallia ei ole, laitteen palvelu voi käyttää verkkoa.
  Sanellut sanat oppivat kuten kirjoitetut.
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
* käännösnäkymä työkaluriviltä: oma kääntäjä näppäimistön sisällä
  Google Kääntäjän tapaan. Ylhäällä on monirivinen kirjoitusalue, joka
  kasvaa tekstin mukana, ja sen alla käännös päivittyy livenä
  laitteella (Google ML Kit) — mitään ei kirjoiteta kenttään
  itsestään. Käännöksen voi kopioida leikepöydälle tai viedä
  Lisää-napilla kenttään, jolloin uusi vienti korvaa edellisen.
  ✨-nappi hakee laadukkaamman käännöksen valitulta AI-palvelulta
  (vaatii oman API-avaimen). Kielipari ja suunta ovat vaihdettavissa;
  kielimallit ladataan vasta käyttäjän luvalla (~30 Mt/kieli, kertalataus)
  ja niitä hallitaan asetuksista.
  Kirjoitusalueella toimivat sanaehdotukset, kursorin siirto, valinta
  kahvoineen, Kopioi/Liitä sekä välimerkkisäännöt, ja teksti säilyy
  kunnes sen itse tyhjentää — myös sovelluksesta toiseen vaihtaessa.
  Käännettävä teksti esikäsitellään kokonaisiksi lauseiksi, mikä
  parantaa käännösten laatua.
* Paranna teksti (valinnainen): korjausnäkymän nappi lähettää kentän
  tekstin valittuun AI-palveluun (Anthropicin Claude tai OpenAI:n
  ChatGPT) ja näyttää kolme parannusehdotusta, joista valittu korvaa
  tekstin; jos korjattavaa ei ole, siitä kerrotaan suoraan. Mallin voi
  valita asetuksista — lista haetaan palvelusta, joten uudet mallit
  näkyvät ilman sovelluspäivitystä, uusimmat ja kyvykkäimmät ensin.
  Vaatii oman API-avaimen; avaimet säilytetään laitteella Android
  Keystorella salattuina, ja ilman avainta nappia ei näytetä eikä
  mitään lähetetä. Lähetettävän tekstin pituus on rajattu, ja
  mahdollisen virheen syy näytetään ilmoituksessa.
* asetussivujen Material 3 -ilme: iso kutistuva otsikko, korttirivit,
  kuvakkeet ja Material You -värit (Android 12+). Värit täyttävät
  WCAG AAA -kontrastivaatimukset molemmissa teemoissa.

## Yksityisyys

Oppiminen ja ehdotukset ovat kokonaan paikallisia, eikä sovelluksessa ole
analytiikkaa tai mainoksia. Sovelluksella on internet-oikeus kahta
tarkoitusta varten: käännöskielten mallien kertalataus (Google ML Kit)
sekä valinnaiset AI-toiminnot (Paranna teksti ja ✨-AI-käännös), jotka
lähettävät tekstin valittuun AI-palveluun (Anthropic tai OpenAI) vain
kun käyttäjä itse painaa nappia ja on ensin asettanut oman
API-avaimensa. API-avaimet säilytetään laitteella Android Keystorella
salattuina. Live-käännös tehdään laitteella, eikä kirjoitettua tekstiä
lähetetä minnekään ilman käyttäjän omaa, tietoista toimintoa. Oppiminen
kytkeytyy kokonaan pois salasana- ja muissa arkaluonteisissa kentissä.

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
