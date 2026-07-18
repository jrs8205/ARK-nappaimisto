# Vaihe 2 – Ehdotusrivi, työkalurivi ja yleinen sanalista

Tämä dokumentti tarkentaa kokonaissuunnitelman ([suunnitelma.md](suunnitelma.md))
kohdat 17 ja 21 toteutettavaksi kokonaisuudeksi. Mukana on myös työkalurivi
nuolitiloineen sekä pilkkunäppäimen korjaus.

## 1. Rakenne ja käyttöliittymä

Näppäimistönäkymä koostuu jatkossa kolmesta osasta ylhäältä alas:

1. **ToolbarView** (uusi, ~40 dp): Canvas-piirretty työkalurivi. Ensimmäisessä
   versiossa kaksi painiketta: nuolitila ja asetukset. Rivi laajenee
   myöhemmissä vaiheissa (leikepöytä, emojit, tekstipohjat).
2. **SuggestionBarView** (uusi, ~44 dp): Canvas-piirretty, vaakasuunnassa
   sormella vieritettävä ehdotusrivi. Näkyvissä 5–8 ehdotusta näytön leveyden
   mukaan; ehdotuksen leveys määräytyy sanan pituudesta. Ehdotusten välissä
   ohuet jakoviivat. Napautus syöttää sanan.
3. **KeyboardView** (nykyinen, ennallaan): alimmaisena, hoitaa
   navigointipalkki-insetin kuten ennenkin.

Kaikki kolme näkymää ovat pystysuuntaisessa säiliössä, jonka
`onCreateInputView` palauttaa. Rivit noudattavat valittua teemaa ja
korkeusasetusta.

### Nuolitila

Työkalurivillä on oma nuolikuvake. Sitä napauttamalla koko näppäinalue vaihtuu
isoon nuolipaneeliin samaan tapaan kuin Gboardissa: suuret näppäimet ▲ ◀ ▼ ▶
sekä Takaisin-painike, josta palataan kirjoitusnäkymään. Nuolet lähettävät
DPAD-näppäintapahtumia ja toistuvat pohjassa pidettäessä kuten askelpalautin.
Välilyönnin kursoriliu'utus säilyy ennallaan nuolitilan rinnalla.

Toteutus: uusi asettelusivu `Layouts`iin ja `KeyAction.Arrow`-toiminto.
Erillistä näkymäluokkaa ei tarvita.

### Pilkkunäppäimen korjaus

Pilkku ei enää koskaan korvaudu kenttäkohtaisella merkillä. Sähköposti- ja
osoitekentissä lisämerkki (@ tai /) tulee omana näppäimenään pilkun viereen ja
välilyönti kapenee vastaavasti. Tausta: Google-sovelluksen hakukenttä
ilmoittautuu osoitekentäksi, jolloin vinoviiva korvasi pilkun ja näkyi tuplana
seiskan pitkän painalluksen kanssa.

## 2. Sanalista ja hakumoottori

### Sanalistan hankinta

Yleinen suomen sanalista kootaan Kielipankin (Korp) taajuusaineistosta
kertaluontoisella skriptillä. Lopputulos on `assets/sanalista.txt`:
yksi rivi per sana muodossa `sana taajuus`, yleisyysjärjestyksessä.

Siivous: mukaan vain suomen kirjaimista koostuvat sanamuodot, harvinaisimmat
(typot) karsitaan minimiesiintymismäärällä. Aineiston lisenssi tarkistetaan
ennen käyttöä ja attribuutio kirjataan README:hen sekä tähän docs-kansioon.
Kotuksen nykysuomen sanalista yhdistetään aineistoon myöhempänä parannuksena.

### DictionaryEngine

Lataa sanalistan palvelun käynnistyessä taustasäikeessä muistiin
aakkosjärjestettynä taulukkona (~2–5 Mt). Etuliitehaku tehdään binäärihaulla
ja tuloksista valitaan top-N taajuuden mukaan. Tiedosto järjestetään
generointivaiheessa täsmälleen samalla vertailulla kuin ajonaikainen haku,
jotta å, ä ja ö eivät aiheuta järjestysvirheitä; tämä varmistetaan
yksikkötestillä.

### SuggestionEngine

Ensimmäinen versio on kevyt: keskeneräinen sana sisään, täydennykset
yleisyysjärjestyksessä ulos. Kokonaissuunnitelman pisteytysmalli (kohta 22)
liitetään tähän rakenteeseen oppimisvaiheissa 3–5; rajapinta suunnitellaan
niin, ettei sitä tarvitse silloin rikkoa. Myös jälkikäteinen sanan korjaus
(vaihe 7) tulee saamaan ehdotuksensa tämän saman rivin kautta, joten rivin
sisältö ei saa olettaa pelkkiä täydennyksiä.

## 3. Käyttäytyminen

* **Sanan seuranta**: palvelu lukee kursoria edeltävän tekstin
  (`getTextBeforeCursor`) ja poimii keskeneräisen sanan. Ehdotukset päivittyvät
  jokaisen painalluksen ja kursorisiirron jälkeen taustasäikeessä; uusi
  painallus peruu käynnissä olevan haun (kokonaissuunnitelman kohta 30).
* **Ehdotuksen napautus**: keskeneräinen sana korvataan valitulla sanalla ja
  perään lisätään välilyönti. Isot alkukirjaimet seuraavat Shift-tilaa.
* **Tyhjä syöte**: kun sanaa ei ole aloitettu, rivillä näkyvät yleisimmät
  sanat, kunnes n-grammit (vaihe 4) tuovat oikean seuraavan sanan ennustuksen.

### Asetukset

Asetuksiin lisätään uusi Ehdotukset-osio, jossa käyttäytymiset ovat
valittavissa:

* ehdotukset käytössä / pois (piilottaa koko ehdotusrivin) — oletus: käytössä
* välilyönti ehdotuksen hyväksynnän jälkeen — oletus: käytössä
* yleisimmät sanat tyhjällä syötteellä — oletus: käytössä
* ehdotusrivi numero- ja puhelinkentissä — oletus: pois

Salasanakenttien käsittely ei ole asetus: niissä ehdotusrivi on aina piilossa
eikä tekstiä lueta, samoin kuin esikatselukupla on jo nyt pois käytöstä.

## 4. Virhetilanteet

* Jos sanalista ei ole vielä latautunut tai lataus epäonnistuu, ehdotusrivi on
  tyhjä eikä mikään kaadu; lataus ei koskaan estä kirjoittamista.
* DPAD-nuolet eivät toimi kaikissa sovelluksissa (osa tekstikentistä ei
  käsittele niitä) — tunnettu rajoitus.
* `currentInputConnection` voi olla null; kaikki sitä käyttävä koodi varautuu
  tähän kuten nykyisinkin.

## 5. Testaus

* Yksikkötestit DictionaryEnginelle: etuliitehaku, top-N-järjestys,
  å/ä/ö-järjestyksen yhdenmukaisuus, tyhjä ja puuttuva lista.
* Yksikkötestit sanan poiminnalle (sananrajat: välilyönti, välimerkit,
  rivinvaihto).
* Manuaalitestaus laitteella (Pixel 8a): ehdotusten laatu, vieritys,
  nuolitila, pilkku eri kenttätyypeissä, salasanakentän piilotus.

## 6. Vaiheen ulkopuolelle jää

Omien sanojen oppiminen (vaihe 3), n-grammit (vaihe 4), automaattikorjaus ja
jälkikäteinen korjaus (vaihe 7), laajennettava ehdotuspaneeli, tekoälypalvelut
sekä erikoismerkkien paikkojen muokkaus. Nämä on kirjattu tehtävälistalle.
