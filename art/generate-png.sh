#!/bin/bash
set -e

# mftlrs: minSdk 26 — no legacy launcher and shortcut PNGs (adaptive icons only); no Android TV banner.
# mftlrs: Inkscape 1.4 action names. The foreground is drawn from launcher_icon_foreground.svg —
# launcher_icon.svg without the background circle and its clip; keep the folder shapes in sync.
# The background color below must match launcher_icon_color in app/src/main/res/values/colors.xml.

cp launcher_icon.svg launcher_icon-web.svg
inkscape --actions='select-by-id:circle;edit-remove-filter;export-overwrite;export-do' launcher_icon-web.svg
inkscape -o launcher_icon-web.png --export-area=18:18:90:90 -w 512 -h 512 launcher_icon-web.svg
rm launcher_icon-web.svg

inkscape -o launcher_icon_foreground-mdpi.png -w 108 -h 108 launcher_icon_foreground.svg
inkscape -o launcher_icon_foreground-hdpi.png -w 162 -h 162 launcher_icon_foreground.svg
inkscape -o launcher_icon_foreground-xhdpi.png -w 216 -h 216 launcher_icon_foreground.svg
inkscape -o launcher_icon_foreground-xxhdpi.png -w 324 -h 324 launcher_icon_foreground.svg
inkscape -o launcher_icon_foreground-xxxhdpi.png -w 432 -h 432 launcher_icon_foreground.svg
inkscape -o launcher_icon-play.png --export-area=18:18:90:90 -b '#12876a' -w 512 -h 512 launcher_icon_foreground.svg
