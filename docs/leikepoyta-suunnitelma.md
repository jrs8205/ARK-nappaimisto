# Leikepöytä – leikkeiden näkymä työkaluriviltä

Tämä dokumentti kattaa leikepöytänäkymän (kokonaissuunnitelman kohdat 4 ja
31–33).

## 1. Käyttö

* Työkaluriville tulee **leikepöytänappi** (järjestys: kursori → www →
  mikrofoni → leikepöytä → asetukset). Napautus avaa leikepöytänäkymän
  näppäinalueen tilalle, kuten nuolitila; sama nappi (korostettuna)
  sulkee sen.
* Näkymässä on vieritettävä lista leikkeistä: **kiinnitetyt ensin**,
  sitten muut uusin ylimpänä. Pitkät leikkeet näytetään lyhennettyinä,
  mutta syöttö vie koko sisällön.
* **Napautus syöttää leikkeen tekstikenttään.** Pitkä painallus avaa
  tutun liu'utusvalikon: Kiinnitä / Poista kiinnitys ja Poista.
* Tyhjä leikepöytä näyttää lyhyen ohjetekstin.

## 2. Keräys ja tallennus

* Näppäimistö kuuntelee leikepöytää ja tallentaa uudet kopiot
  paikalliseen tietokantaan (uusi leiketaulu; versio nousee
  lisäysmigraatiolla — olemassa oleva data säilyy).
* **Vanheneminen**: kiinnittämätön leike vanhenee **1 tunnissa**;
  kiinnitetty säilyy kunnes käyttäjä poistaa sen. Kiinnittämättömiä
  pidetään enintään 20 tuoreinta. Vanhentuneet siivotaan latauksen ja
  uuden leikkeen tallennuksen yhteydessä.
* Sama teksti ei tallennu kahdesti peräkkäin (uudelleenkopiointi vain
  nostaa leikkeen tuoreimmaksi).

## 3. Yksityisyys

* **Arkaluonteisiksi merkityt kopiot ohitetaan kokonaan** (leikkeen
  arkaluonteisuuslippu, jota mm. salasanaohjelmat käyttävät).
* Kaikki leikkeet pysyvät laitteella; ei internet-oikeutta.
* Leikkeen poisto poistaa sen heti tietokannasta.

## 4. Testaus

* Yksikkötestit leikevaraston logiikalle (vanheneminen, 20 leikkeen
  raja, peräkkäisten duplikaattien yhdistäminen, kiinnitettyjen
  säilyminen) muistitoteutusta vasten.
* Laitetesti: kopiointi eri sovelluksista, syöttö napautuksella,
  kiinnitys ja poisto, vanheneminen, arkaluonteisen leikkeen ohitus
  (esim. salasanaohjelmasta), näkymän avaus ja sulku.

## 5. Ulkopuolelle jää

Kuva- ja muut ei-tekstileikkeet, leikkeiden muokkaus, tekstipohjat
(vaihe 8) sekä leikepöydän ehdotusintegraatio (tuore kopio ehdotusrivillä
— harkitaan myöhemmin käyttökokemuksen perusteella).
