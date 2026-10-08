#!/bin/sh
# Kokoaa README:n esittelykuvat neljästä kuvakaappauksesta.
#
# Tuottaa kaksi kuvaa, joista README valitsee näytön leveyden mukaan:
#   docs/kuvat/esittely-levea.png  – neljä kuvaa rinnakkain (tietokone)
#   docs/kuvat/esittely-kapea.png  – kaksi riviä, kaksi kuvaa (puhelin)
#
# Käyttö: tools/esittelykuvat.sh  (vaatii ffmpegin, ajetaan repon juuresta)
# Aja uudelleen aina, kun docs/kuvat/-kuvakaappaukset vaihtuvat.

set -eu
K=docs/kuvat
W=720    # yhden kuvan leveys yhdistelmässä
H=1600   # yhden kuvan korkeus (1080x2400 skaalattuna)
G=24     # läpinäkyvä rako kuvien välissä

ffmpeg -v error -y \
  -i "$K/ehdotukset.png" -i "$K/pitka-painallus.png" \
  -i "$K/kaannos.png" -i "$K/leikepoyta.png" \
  -filter_complex "
    [0]scale=$W:$H,format=rgba,pad=$W+$G:$H:0:0:color=black@0[a];
    [1]scale=$W:$H,format=rgba,pad=$W+$G:$H:0:0:color=black@0[b];
    [2]scale=$W:$H,format=rgba,pad=$W+$G:$H:0:0:color=black@0[c];
    [3]scale=$W:$H,format=rgba[d];
    [a][b][c][d]hstack=4[levea];
    [0]scale=$W:$H,format=rgba,pad=$W+$G:$H+$G:0:0:color=black@0[e];
    [1]scale=$W:$H,format=rgba,pad=$W:$H+$G:0:0:color=black@0[f];
    [2]scale=$W:$H,format=rgba,pad=$W+$G:$H:0:0:color=black@0[g];
    [3]scale=$W:$H,format=rgba[h];
    [e][f]hstack[r1];[g][h]hstack[r2];[r1][r2]vstack[kapea]" \
  -map "[levea]" -compression_level 9 "$K/esittely-levea.png" \
  -map "[kapea]" -compression_level 9 "$K/esittely-kapea.png"

echo "Valmis: $K/esittely-levea.png ja $K/esittely-kapea.png"
