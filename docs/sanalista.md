# Yleinen sanalista

Näppäimistön yleinen suomen sanalista (`app/src/main/assets/sanalista.txt`) on
muodostettu kahdesta Kotimaisten kielten keskuksen (Kotus) julkaisemasta
aineistosta:

**1. Parole-taajuuslista** — sanamuodot taajuuksineen suomen kielen
Parole-korpuksesta (noin 17 miljoonaa sanetta kirjoitettua suomea).

* Aineisto: Suomen sanomalehtikielen taajuuslista (Parole),
  <https://kaino.kotus.fi/sanat/taajuuslista/parole.php>
* Julkaisija: Kotimaisten kielten keskus (Kotus), aineisto listattu myös
  Kielipankissa
* Lisenssi: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.fi)
  (Kotuksen avointen aineistojen ensisijainen lisenssi)
* Lähtötiedosto: `parole_frek_3.zip` (sanamuodot, jotka esiintyvät korpuksessa
  vähintään kolmesti; 326 514 muotoa)

**2. Nykysuomen sanalista (2024)** — perusmuodot, jotka täydentävät
taajuuslistaa harvinaisemmilla sanoilla.

* Aineisto: Nykysuomen sanalista,
  <https://kaino.kotus.fi/lataa/nykysuomensanalista2024.csv>
* Julkaisija: Kotimaisten kielten keskus (Kotus)
* Lisenssi: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.fi)
* Perusmuodot, joita taajuuslistalla ei jo ole, lisätään listan jatkoksi
  pienellä oletustaajuudella (2), jolloin ne ovat täydennyksissä tarjolla
  mutta yleisyydeltään tunnettujen sanojen takana.

## Käsittely

Lista on muodostettu työkalulla [`tools/sanalista.py`](../tools/sanalista.py):

1. sanamuodot pienennetään ja saman muodon taajuudet yhdistetään
2. mukaan otetaan vain suomen kirjaimista (a–z, å, ä, ö) ja yhdysmerkeistä
   koostuvat muodot; numerot, välimerkit ja muut siivotaan pois
3. alle viidesti esiintyvät muodot karsitaan
4. jäljelle jäävistä otetaan 80 000 yleisintä
5. Nykysuomen sanalistan perusmuodot, joita edellä ei jo ole, lisätään
   loppuun oletustaajuudella 2 (`--kotus`-parametri)

Tulostiedoston muoto: yksi rivi per sana, `sananmuoto taajuus`,
yleisyysjärjestyksessä, UTF-8. Nykyinen lista: 170 100 sanaa, joista
90 100 Nykysuomen sanalistalta.
