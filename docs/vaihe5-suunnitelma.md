# Vaihe 5 – Palautteesta oppiminen ja yhtenäinen pisteytys

Tämä dokumentti tarkentaa kokonaissuunnitelman ([suunnitelma.md](suunnitelma.md))
kohdan 39 vaiheen 5: hyväksytyistä ja ohitetuista ehdotuksista oppiminen sekä
pisteytysmallin (kohta 22) käyttöönotto. Automaattikorjauksen peruutuksista
oppiminen kuuluu vaiheeseen 7, koska automaattikorjausta ei vielä ole.

## 1. Palautesignaalit

* **Hyväksyntä**: ehdotuksen valinta kasvattaa sanan hyväksyntälaskuria —
  myös yleissanaston sanoilla. Hyväksyntä ei tee yleissanasta "omaa sanaa"
  (täydennyksiin se vaikuttaa vain painona), ja se nollaa sanan
  ohituslaskurin.
* **Ohitus**: kun sana päättyy (käsin tai valinnalla) ja lopullinen sana on
  eri kuin rivillä näkyneet kärkiehdotukset (3 ensimmäistä), näiden
  ohituslaskuri kasvaa. Vaikutus on kevyt: paino laskee vasta kun ohituksia
  on vähintään 2, ja hyväksyntä nollaa ne — yksittäinen ohitus ei rankaise.
* **Kiinnitys**: pitkän painalluksen valikkoon tulee kohta **Kiinnitä**
  (kiinnitetylle sanalle **Poista kiinnitys**). Kiinnitetty sana saa
  ×20-painon eli nousee käytännössä aina kärkeen, kun etuliite täsmää.

## 2. Yhtenäinen pisteytysmalli (SuggestionEngine)

Nykyinen kova järjestys (ennustukset → omat sanat → yleiset) korvautuu
kokonaissuunnitelman kohdan 22 pisteytyksellä niiltä osin kuin signaalit
ovat olemassa:

```text
pisteet =
    yleisyys              × 1.0  +
    oma viimeaikainen käyttö × 2.0  +
    bigram-osuma          × 4.0  +
    trigram-osuma         × 7.0  +
    hyväksytyt ehdotukset × 3.0  +
    käsin kirjoitettu     × 2.5  +
    kiinnitetty           × 20.0 −
    ohitetut ehdotukset   × 5.0
```

* Kukin signaali normalisoidaan välille 0–1 ennen kerrointa, jottei mikään
  laskuri karkaa hallitsemaan; tarkat normalisointikaavat määritellään
  toteutussuunnitelmassa.
* Sama malli pisteyttää sekä keskeneräisen sanan täydennykset että tyhjän
  syötteen ennustukset; tyhjällä syötteellä yleisimmät sanat täyttävät
  rivin loput kuten ennenkin.
* Estetyt sanat suodattuvat aina pois. Sovelluskohtainen paino (vaihe 6)
  ja korjausten peruutukset (vaihe 7) liitetään samaan kaavaan myöhemmin.

## 3. Tallennus

* `WordEntity` saa kolme uutta saraketta: hyväksynnät, ohitukset ja
  kiinnitys. `LearnedWord`-malli laajenee vastaavasti.
* Tietokanta nousee versioon 3 lisäyssarakemigraatiolla (ALTER TABLE) —
  olemassa oleva data säilyy koskemattomana.
* Palvelu muistaa viimeksi näytetyt kärkiehdotukset ohitusten
  kirjaamista varten. Kirjoitukset kulkevat samoissa erissä kuin ennen.

## 4. Valikko

Pitkän painalluksen valikon järjestys: **Kiinnitä / Poista kiinnitys** →
**Poista opittu sana** (vain omille sanoille) → **Älä ehdota tätä**.
Valinta liu'uttamalla kuten ennenkin.

## 5. Testaus

* Yksikkötestit: pisteytysjärjestys eri signaaliyhdistelmillä, ohitusten
  kertyminen ja nollaus hyväksynnästä, kiinnityksen dominointi, estojen
  ja poistojen ennallaan pysyminen, normalisoinnin rajat.
* Laitetesti: toistuvasti hyväksytty sana nousee; toistuvasti ohitettu
  laskee; kiinnitetty pysyy kärjessä; `prx4` ⏎ `Jako 20` ⏎ `nouto 6`
  -ketju toimii kuten ennenkin (regressiotarkistus); migraation jälkeen
  vanhat opitut sanat tallella.

## 6. Vaiheen ulkopuolelle jää

Sovelluskohtainen oppiminen (vaihe 6), automaattikorjaus ja sen
peruutuksista oppiminen (vaihe 7), fraasit (vaihe 8) sekä opittujen
sanojen hallintanäkymä (oma tehtävä).
