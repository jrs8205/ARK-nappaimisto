# ARK-näppäimistö

Oma suomalainen näppäimistösovellus Androidille (IME, Input Method Editor).

ARK-näppäimistö toimii Androidin järjestelmätason näppäimistönä, jonka voi ottaa
käyttöön puhelimen asetuksista ja käyttää lähes kaikissa sovelluksissa:
viestisovelluksissa, selaimessa, sähköpostissa, muistiinpanoissa ja muissa
tekstikentissä.

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
* toimii täysin paikallisesti — sovelluksella ei ole internet-oikeutta
* ei lähetä kirjoitettua tekstiä palvelimelle
* antaa käyttäjän hallita kaikkea opittua tietoa

## Tila

Projekti on kehitysvaiheessa. Vaiheittainen eteneminen:

| Vaihe | Sisältö | Tila |
|---|---|---|
| 1 | Toimiva perusnäppäimistö: suomalainen QWERTY, numerorivi, teemat, asetussivu | Valmis |
| 2 | Vieritettävä ehdotusrivi ja yleinen suomen sanalista | Valmis |
| 3 | Henkilökohtaisten sanojen oppiminen | — |
| 4 | Sanaparit ja trigrammit, seuraavan sanan ennustus | — |
| 5 | Palautteesta oppiminen (hyväksynnät, hylkäykset, korjausten peruutukset) | — |
| 6 | Sovelluskohtainen oppiminen | — |
| 7 | Oikeinkirjoitus ja varovainen automaattikorjaus | — |
| 8 | Fraasit ja tekstipohjat | — |
| 9 | Paikallinen kielimalli ehdotusten uudelleenjärjestämiseen | — |
| 10 | Edistyneet ominaisuudet (pyyhkäisykirjoitus ym.) | — |

## Ominaisuudet (vaihe 1)

* suomalainen QWERTY-asettelu Å-, Ä- ja Ö-kirjaimilla
* numerorivi aina näkyvissä
* Shift, Caps Lock (kaksoisnapautus), automaattinen iso alkukirjain
* pitkä painallus erikoismerkeille ja tarkkeellisille kirjaimille
* kaksi symbolisivua ja numeronäppäimistö numerokentille
* kenttäkohtainen mukautus: @ sähköpostikentässä, / osoitekentässä,
  Enter-näppäimen toiminto kentän mukaan (Hae, Lähetä, Siirry…)
* tumma, vaalea ja AMOLED-teema
* säädettävä näppäimistön korkeus
* näppäinäänet, värinä ja esikatselukupla
* välilyönnin pyyhkäisy siirtää kursoria
* salasanakentissä esikatselu pois käytöstä

## Ominaisuudet (vaihe 2)

* vieritettävä ehdotusrivi: täydennykset yleisestä suomen sanalistasta
  (80 000 sanamuotoa taajuuksineen, ks. [docs/sanalista.md](docs/sanalista.md))
* työkalurivi: kursorinsiirtotila (isot nuolinäppäimet), verkko-osoitesivu
  (https://, www., .fi …) ja asetukset
* välimerkki siirtyy ehdotuksen lisäämän välilyönnin eteen
* isot alkukirjaimet ehdotuksissa lauseen alussa
* ehdotusten asetukset: näkyvyys, välilyönti hyväksynnän jälkeen,
  yleisimmät sanat tyhjällä syötteellä
* teema seuraa järjestelmän tummaa tilaa; salasana- ja numerokentissä
  ehdotukset pois käytöstä

## Yksityisyys

Kaikki toiminta on paikallista. Sovellus ei pyydä internet-oikeutta, ei sisällä
analytiikkaa eikä mainoksia. Oppiminen kytkeytyy kokonaan pois salasana- ja
muissa arkaluonteisissa kentissä.

## Kääntäminen

Projekti käännetään Android Studiolla tai komentoriviltä:

```
./gradlew :app:assembleDebug
```

Vaatimukset: JDK 17 tai uudempi ja Android SDK (compileSdk 36).

## Lisenssi

Katso [LICENSE](LICENSE).

Yleinen suomen sanalista on muodostettu Kotimaisten kielten keskuksen
Parole-taajuuslistasta ([CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.fi)),
katso [docs/sanalista.md](docs/sanalista.md).
