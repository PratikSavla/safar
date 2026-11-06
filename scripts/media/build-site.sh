#!/usr/bin/env bash
# Assembles the GitHub Pages site into _site/ (the Pages workflow runs this same script).
#
#   scripts/media/build-site.sh [--strict]
#
# site/                                                    -> _site/
# fastlane/metadata/android/en-US/images/phoneScreenshots/ -> _site/screens/
# app/src/main/res/font/*.ttf + their OFL licences         -> _site/fonts/
# media/safar-demo.mp4 (optional)                          -> _site/media/  (absent: the film section is removed)
#
# --strict fails when a screenshot that index.html references is missing (used in CI).
# Overrides for local previews: SCREENS_DIR, MEDIA_DIR, OUT_DIR.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SITE_DIR="$ROOT/site"
OUT_DIR="${OUT_DIR:-$ROOT/_site}"
SCREENS_DIR="${SCREENS_DIR:-$ROOT/fastlane/metadata/android/en-US/images/phoneScreenshots}"
MEDIA_DIR="${MEDIA_DIR:-$ROOT/media}"
FONT_DIR="$ROOT/app/src/main/res/font"
LICENSE_DIR="$ROOT/app/src/main/assets/licenses"
STRICT=0

for arg in "$@"; do
  case "$arg" in
    --strict) STRICT=1 ;;
    *) echo "build-site: unknown argument '$arg'" >&2; exit 2 ;;
  esac
done

[[ -f "$SITE_DIR/index.html" ]] || { echo "build-site: $SITE_DIR/index.html not found" >&2; exit 1; }

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/fonts" "$OUT_DIR/screens"
cp -R "$SITE_DIR/." "$OUT_DIR/"
touch "$OUT_DIR/.nojekyll"

# Fonts: the app's own OFL files, with their licences beside them.
cp "$FONT_DIR"/*.ttf "$OUT_DIR/fonts/"
cp "$LICENSE_DIR"/fonts.txt "$LICENSE_DIR"/ofl-*.txt "$OUT_DIR/fonts/"

# Screenshots: one source shared with the README and F-Droid.
if [[ -d "$SCREENS_DIR" ]]; then
  find "$SCREENS_DIR" -maxdepth 1 -type f -name '*.png' -exec cp {} "$OUT_DIR/screens/" \;
else
  echo "build-site: warning: no screenshots folder at $SCREENS_DIR" >&2
fi

missing=0
while IFS= read -r shot; do
  if [[ ! -f "$OUT_DIR/$shot" ]]; then
    echo "build-site: missing $shot" >&2
    missing=$((missing + 1))
  fi
done < <(grep -o 'screens/[A-Za-z0-9._-]*\.png' "$SITE_DIR/index.html" | sort -u)

if (( missing > 0 && STRICT == 1 )); then
  echo "build-site: $missing screenshot(s) missing" >&2
  exit 1
fi

# Demo film: copy it, or cut the section out of the page.
if [[ -f "$MEDIA_DIR/safar-demo.mp4" ]]; then
  mkdir -p "$OUT_DIR/media"
  cp "$MEDIA_DIR/safar-demo.mp4" "$OUT_DIR/media/"
else
  echo "build-site: no $MEDIA_DIR/safar-demo.mp4, removing the film section"
  tmp="$(mktemp)"
  sed '/<!-- demo:start -->/,/<!-- demo:end -->/d' "$OUT_DIR/index.html" > "$tmp"
  mv "$tmp" "$OUT_DIR/index.html"
  chmod 644 "$OUT_DIR/index.html"
fi

echo "build-site: built $OUT_DIR ($(find "$OUT_DIR" -type f | wc -l | tr -d ' ') files)"
