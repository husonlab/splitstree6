#!/bin/zsh

#
# make-iconset.sh Copyright (C) 2025 Daniel H. Huson
#
#  (Some files contain contributions from other authors, who are then mentioned separately.)
#
#  This program is free software: you can redistribute it and/or modify
#  it under the terms of the GNU General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
#

input=../src/main/resources/splitstree6/resources/icons/SplitsTree6-512.png

sips -z 16 16     $input --out icon_16x16.png
sips -z 32 32     $input --out icon_32x32.png
sips -z 48 48     $input --out icon_48x48.png
sips -z 64 64     $input --out icon_64x64.png
sips -z 128 128   $input --out icon_128x128.png
sips -z 256 256   $input --out icon_256x256.png
sips -z 512 512   $input --out icon_512x512.png
