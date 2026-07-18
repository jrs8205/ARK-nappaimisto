"""Muodostaa näppäimistön sanalistan Parole-taajuuslistasta.

Käyttö:
    python tools/sanalista.py parole_frek_3.txt app/src/main/assets/sanalista.txt

Syöterivin muoto: "järjestysnro taajuus sananmuoto (osuus %)", Latin-1-koodattu.
Tulos: "sananmuoto taajuus" -rivit yleisyysjärjestyksessä, UTF-8.
Sanamuodot pienennetään ja siivotaan: mukaan vain suomen kirjaimista (ja
yhdysmerkeistä) koostuvat muodot, harvinaisimmat karsitaan.
"""
import argparse
import re

SANA = re.compile(r"^[a-zåäö]+(-[a-zåäö]+)*$")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("syote")
    p.add_argument("tulos")
    p.add_argument("--koodaus", default="latin-1")
    p.add_argument("--taajuus-sarake", type=int, default=1)
    p.add_argument("--sana-sarake", type=int, default=2)
    p.add_argument("--min", type=int, default=5)
    p.add_argument("--sanoja", type=int, default=80000)
    a = p.parse_args()

    maarat: dict[str, int] = {}
    with open(a.syote, encoding=a.koodaus) as f:
        for rivi in f:
            osat = rivi.split()
            if len(osat) <= max(a.sana_sarake, a.taajuus_sarake):
                continue
            sana = osat[a.sana_sarake].strip().lower()
            try:
                taajuus = int(osat[a.taajuus_sarake])
            except ValueError:
                continue
            if taajuus < 1 or not SANA.match(sana):
                continue
            # Sama muoto voi esiintyä eri kirjainkoolla; taajuudet yhdistetään.
            maarat[sana] = maarat.get(sana, 0) + taajuus

    kelvot = sorted(
        ((s, t) for s, t in maarat.items() if t >= a.min),
        key=lambda x: -x[1],
    )[: a.sanoja]
    with open(a.tulos, "w", encoding="utf-8", newline="\n") as f:
        for sana, taajuus in kelvot:
            f.write(f"{sana} {taajuus}\n")
    print(f"{len(kelvot)} sanaa kirjoitettu")


if __name__ == "__main__":
    main()
