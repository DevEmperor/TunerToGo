#!/usr/bin/env bash
# Renders the Play Store and README graphics from art/*.svg and the screenshots taken with
# tools/capture-screenshots.sh. Needs rsvg-convert, ImageMagick 7 and the Noto Sans font.
#
# Usage: tools/render-store-assets.sh <dir with en-US/ and de-DE/ screenshot folders>
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
shots=$(cd "${1:?screenshot dir}" && pwd)
meta=$root/fastlane/metadata/android
readme=$root/docs/images
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

mkdir -p "$readme" "$meta/en-US/images" "$meta/de-DE/images"

# App icon, 512 x 512 (Play masks it itself).
rsvg-convert -w 512 -h 512 "$root/art/icon.svg" -o "$meta/en-US/images/icon.png"
cp "$meta/en-US/images/icon.png" "$meta/de-DE/images/icon.png"

# Feature graphic, 1024 x 500. librsvg only loads images next to the SVG, hence the copy.
feature() {
    local locale=$1 tagline=$2 instruments=$3
    cp "$shots/$locale/01_tuner.png" "$tmp/screen.png"
    sed -e "s|@SCREENSHOT@|screen.png|" -e "s|@TAGLINE@|$tagline|" -e "s|@INSTRUMENTS@|$instruments|" \
        "$root/art/feature-graphic.svg" > "$tmp/feature.svg"
    rsvg-convert -w 1024 -h 500 "$tmp/feature.svg" -o "$meta/$locale/images/featureGraphic.png"
}
feature en-US "The tuner on your wrist" "Guitar · Bass · Ukulele · Violin · Chromatic"
feature de-DE "Das Stimmgerät am Handgelenk" "Gitarre · Bass · Ukulele · Geige und mehr"

# Wear OS screenshots: square, at least 384 px, unmasked and unframed (Play policy).
for locale in en-US de-DE; do
    dir=$meta/$locale/images/wearScreenshots
    rm -rf "$dir"
    mkdir -p "$dir"
    i=1
    for f in "$shots/$locale"/0*.png; do
        magick "$f" -strip "$dir/$i.png"
        i=$((i + 1))
    done
done

# README: the English screenshots cut to the round watch face, plus the banner.
for f in "$shots/en-US"/0*.png; do
    name=$(basename "$f")
    magick "$f" \( +clone -alpha transparent -fill white -draw "circle 227,227 227,1" \) \
        -compose CopyOpacity -composite -strip "$readme/$name"
done
cp "$meta/en-US/images/featureGraphic.png" "$readme/banner.png"

# Preview shown in the watch's tile picker on round watches. The square variant in
# drawable-nodpi was taken once on a "Wear OS Square" emulator and rarely needs updating.
magick "$shots/en-US/07_tile.png" -resize 384x384 -strip "$root/app/src/main/res/drawable-round-nodpi/tile_preview.png"

echo "Rendered store assets into $meta and $readme"
