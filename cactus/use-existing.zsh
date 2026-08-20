#!/bin/zsh

# Ensure two arguments are given
if [[ $# -ne 2 ]]; then
    echo "Usage: $0 infile outfile"
    exit 1
fi

infile=$1
outfile=$2

# Verify infile exists
if [[ ! -f $infile ]]; then
    echo "Error: infile '$infile' not found."
    exit 1
fi

# Loop through all *-input.txt files in $HOME
for f in $HOME/*-input.txt; do
    [[ -f $f ]] || continue  # skip if no match
    
    # Compare content with infile
    if cmp -s "$infile" "$f"; then
        base=${f##*/}          # strip directory
        prefix=${base%-input.txt}
        outcand="$HOME/$prefix-output.txt"

        if [[ -f $outcand ]]; then
            cp "$outcand" "$outfile"
            echo "Copied $outcand -> $outfile"
            exit 0
        fi
    fi
done

echo "No matching input/output pair found."
exit 1
