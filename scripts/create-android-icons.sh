#!/bin/bash

export src=" ../artwork/SplitsTree6-icon-1024x1024.png"

sips -z 36 36 $src --out ../src/android/res/mipmap-ldpi/ic_launcher.png
sips -z 48 48 $src --out ../src/android/res/mipmap-mdpi/ic_launcher.png
sips -z 72 72 $src --out ../src/android/res/mipmap-hdpi/ic_launcher.png
sips -z 96 96 $src --out ../src/android/res/mipmap-xhdpi/ic_launcher.png
sips -z 144 144  $src --out ../src/android/res/mipmap-xxhdpi/ic_launcher.png
sips -z 192 192 $src --out ../src/android/res/mipmap-xxxhdpi/ic_launcher.png
