#!/bin/bash

for file in *.{jpg,jpeg,png}; do
  width=$(sips -g pixelWidth "$file" | awk '/pixelWidth:/ {print $2}')
  if [ "$width" -gt 800 ]; then
    sips --resampleWidth 800 "$file"
  fi
done
