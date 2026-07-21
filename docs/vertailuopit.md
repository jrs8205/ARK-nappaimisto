# Vertailuopit: AOSP LatinIME ja HeliBoard

Lähdekoodit kansiossa `vertailu/` (ei repossa). AOSP LatinIME on
Apache 2.0- ja HeliBoard GPL-3.0-lisensoitu, joten molemmista saa
lainata ratkaisuja; FUTO Keyboardin lisenssi ei ole yhteensopiva,
joten sitä käytetään vain käytöksen vertailukohtana.

## Mikä meillä on jo kohdallaan

- Kirjoitus committina ilman composing-tekstiä välttää suurimman osan
  web- ja Compose-kenttien composing-alueongelmista, joita AOSP-suku
  joutuu kiertämään.
- Välimerkkisääntöjen kenttärajaukset (osoite-, sähköposti-, koodi- ja
  numerokentät pois) vastaavat LatinIME:n ja HeliBoardin käytäntöä.
- Merkkipoisto näppäintapahtumana (KEYCODE_DEL) on sama reitti, jota
  HeliBoard käyttää web-kenttien varareittinä — se toimii myös
  selaimissa, jotka eivät ymmärrä deleteSurroundingTextiä.
- Numerorivin piilotus siirtää numerot ylärivin pitkiin painalluksiin —
  sama ratkaisu kuin HeliBoardissa.
- Käännösrivin puskuri käsittelee grafeemit (emojit) BreakIteratorilla
  kuten HeliBoardin poistologiikka.

## Poimittavaa (tehtävälistalle, tärkeysjärjestyksessä)

1. **Hitaan InputConnectionin tunnistus ja kevennys** (LatinIME
   RichInputConnection: 200 ms / 1000 ms kynnykset, 10 min muisti):
   mitataan tekstikyselyjen kesto; hitaassa kentässä ohitetaan
   raskaat kyselyt (ehdotusten kontekstihaku), jottei UI jäädy
   raskaissa chat- ja web-sovelluksissa.
2. **Odotettuun kursoripositioon perustuva oma/vieras-muutosten
   erottelu** (isBelatedExpectedUpdate): nykyinen aikaperusteinen
   heuristiikka (1 s) korvataan odotetuilla positioilla, jolloin
   sanaketjun katkaisu ja automaatti-isot osuvat tarkemmin.
3. **Keskitetty sovelluskohtaisten kikkojen paikka** (HeliBoard
   AppWorkarounds.kt): paketin nimen perusteella korjataan kentän
   liput ennen muuta logiikkaa — esim. Firefox ei merkitse
   web-kenttiään, jolloin liput pakotetaan itse.
4. **Puolustava tarkistus ennen peruutuksia**: ennen välinsiirron tai
   automaattikorjauksen peruutusta varmistetaan, että kentän sisältö
   on yhä odotettu, ja luovutetaan hallitusti jos sovellus ehti
   muuttaa tekstiä (LatinIME revertDoubleSpacePeriod-malli).
5. **Valehteleva alkukursori** (tryFixLyingCursorPosition): laitteen
   kääntö tai kentän uudelleenfokusointi voi antaa vanhentuneen
   kursoriposition; tarkistetaan tekstin todellinen pituus.
6. **Kaksoisvälilyönti pisteeksi** — Gboard-käytös, joka meiltä
   puuttuu kokonaan; ehdot LatinIME:ssa (edeltävä merkki kirjain tai
   sulkeva merkki, aikaraja, vain yleistekstikentissä). Kysytään
   käyttäjältä halutaanko.
7. **Sanapoiston varareitti web-kentissä**: jos deleteSurroundingText
   ei tehoa (selainkentät), poistetaan näppäintapahtumin.

## Sanelusta

HeliBoard ei toteuta omaa sanelua vaan vaihtaa järjestelmän
puhesyöte-IME:hen. Oma jaksotettu istunto -ratkaisumme on tätä
pidemmällä; FUTOn Whisper-pohjainen offline-tunnistus on paras
esikuva tehtävälle #43.
