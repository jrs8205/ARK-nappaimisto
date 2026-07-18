# Välitehtävät – hallintanäkymä, pystyliike ja Kotus-täydennys

Tämä dokumentti kattaa kolme vaiheiden 5 ja 6 väliin sovittua tehtävää:
opittujen sanojen hallintanäkymän, välilyönnin pystyliikekokeilun ja
yleissanaston täydennyksen Kotuksen sanalistalla.

## 1. Opittujen sanojen hallintanäkymä

Asetuksiin tulee uusi kohta **Opitut sanat**, joka avaa oman näkymän:

* **Hakukenttä** ylhäällä; kirjoittaminen suodattaa listaa välittömästi.
* **Lista opituista sanoista**: rivillä sana, käyttömäärä ja merkit
  kiinnitykselle ja estolle. Oletusjärjestys eniten käytetyt ensin.
* **Estetyt sanat omassa osiossaan** listan lopussa — esto on nyt
  mahdollista myös purkaa, mikä ei aiemmin onnistunut mistään.
* **Rivin napautus** avaa toimintovalikon: Kiinnitä / Poista kiinnitys,
  Estä / Salli taas, Poista kokonaan.
* Muutokset kirjoitetaan suoraan oppimistietokantaan. Näppäimistöpalvelu
  lataa oppimisdatan uudelleen, kun asetuksista palataan (kevyt
  uudelleenlataus, ettei palvelun muisti jää vanhaksi).
* Ulkoasu: Material 3, seuraa järjestelmän teemaa kuten muu sovellus.
* Ei uusia asetuksia — näkymä on työkalu, ei kytkinrypäs.

## 2. Välilyönnin pystyliike (kokeilu)

Välilyönnin kursoriliu'utus laajenee pystysuuntaan:

* Pystyliike siirtää kursoria riveittäin (ylös/alas) samalla
  värinänapsautuksella kuin vaakaliike merkeittäin.
* **Dominanssisääntö**: pystytila kytkeytyy vain, kun pystyliike on
  selvästi vaakaliikettä suurempi ja ylittää oman, vaakaa suuremman
  kynnyksen. Kun jompikumpi suunta on kytkeytynyt, toinen ei laukea
  saman painalluksen aikana — tilat eivät sekoitu.
* Ei uutta asetusta. Toteutus on pieni ja helposti poistettava:
  **jos liike ei tunnu laitteella tarkalta, koko ominaisuus poistetaan**
  (käyttäjän asettama ehto).

## 3. Yleissanaston täydennys Kotuksen sanalistalla

* `tools/sanalista.py` saa valinnaisen parametrin Kotuksen nykysuomen
  sanalistalle (CC BY 4.0): listan perusmuodot, joita
  Parole-taajuuslistalla ei jo ole, lisätään sanaston jatkoksi pienellä
  oletustaajuudella.
* Pieni oletustaajuus pitää täydennyssanat tarjolla mutta tunnetusti
  yleisten sanojen takana; pisteytysmalli ei muutu.
* Siivous samoilla säännöillä kuin ennen (vain suomen kirjaimet ja
  yhdysmerkit, pienennys).
* Attribuutio päivitetään: docs/sanalista.md ja README mainitsevat
  molemmat lähteet lisensseineen.
* Arvioitu kasvu 30 000–50 000 sanaa (~1 Mt muistia); binäärihaun
  nopeuteen ei vaikutusta.

## Testaus

* Hallintanäkymä: laitetesti (haku, kiinnitys, eston purku, poisto ja
  vaikutus ehdotuksiin paluun jälkeen). Tietokantatoiminnot käyttävät
  olemassa olevaa DAO:ta, jonka logiikka on jo katettu.
* Pystyliike: yksikkötesti eleentunnistuksen dominanssisäännölle jos
  logiikka eriytetään; muuten laitetesti (tarkkuus ratkaisee kohtalon).
* Kotus: skriptin ajon jälkeen rivimäärän ja muodon tarkistus; laitteella
  harvinaisen perusmuodon täydentyminen (esim. harvinainen yhdyssana).
