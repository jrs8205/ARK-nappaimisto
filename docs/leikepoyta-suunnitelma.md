# Leikepöytä – leikkeiden näkymä työkaluriviltä

Tämä dokumentti kattaa leikepöytänäkymän (kokonaissuunnitelman kohdat 4 ja
31–33).

## 1. Käyttö

* Työkaluriville tulee **leikepöytänappi** (järjestys: kursori → www →
  mikrofoni → leikepöytä → asetukset). Napautus avaa leikepöytänäkymän
  näppäinalueen tilalle, kuten nuolitila; sama nappi (korostettuna)
  sulkee sen.
* Näkymässä on vieritettävä lista leikkeistä: **kiinnitetyt ensin**,
  sitten muut uusin ylimpänä. Pitkät tekstit näytetään lyhennettyinä,
  mutta syöttö vie koko sisällön. **Kuvaleikkeet** näytetään
  pikkukuvina.
* **Napautus syöttää leikkeen kenttään.** Tekstit syötetään aina;
  kuva syötetään, jos kenttä ottaa kuvia vastaan (esim.
  viestisovellukset) — muuten näytetään lyhyt ilmoitus.
* Jokaisen leikkeen oikeassa yläkulmassa on **kolme pistettä (⋮)** ja
  sen alla **neulakuvake**: ääriviiva kun leikettä ei ole kiinnitetty,
  täysi kun on; napautus vaihtaa tilan suoraan.
* ⋮ avaa valikon, jossa kuvakkeelliset kohdat: **Liitä**,
  **Kiinnitä / Poista kiinnitys**, **Hae verkosta** (maapallo ja
  suurennuslasi; avaa haun selaimessa leikkeen tekstillä — vain
  tekstileikkeille) ja **Poista** (roskakori). Ulkoasu vektorikuvakkein
  kuten muukin näppäimistö.
* Tyhjä leikepöytä näyttää lyhyen ohjetekstin.

## 2. Keräys ja tallennus

* Näppäimistö kuuntelee leikepöytää ja tallentaa uudet kopiot
  paikalliseen tietokantaan (uusi leiketaulu; versio nousee
  lisäysmigraatiolla — olemassa oleva data säilyy).
* **Kuvaleikkeen sisältö kopioidaan sovelluksen omaan tallennustilaan**
  (lähdesovelluksen käyttöoikeus vanhenee muuten nopeasti); tiedosto
  poistetaan, kun leike vanhenee tai poistetaan. Kuvan syöttö kenttään
  tehdään Androidin sisällönsyöttörajapinnalla, ja vastaanottava
  sovellus saa lukuoikeuden vain kyseiseen kuvaan.
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

Muut tiedostotyypit kuin teksti ja kuvat, leikkeiden muokkaus,
tekstipohjat (vaihe 8) sekä leikepöydän ehdotusintegraatio (tuore kopio
ehdotusrivillä — harkitaan myöhemmin käyttökokemuksen perusteella).
