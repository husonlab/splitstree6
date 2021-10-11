package splitstree6.io.writers.characters;

import jloda.util.FastA;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

public class FastAWriter extends CharactersWriter {
	public FastAWriter() {
		setFileExtensions("fasta", "fas", "fa", "seq", "fsa", "fna", "dna");
	}

	public void write(Writer w, TaxaBlock taxa, CharactersBlock characters) throws IOException {
		var fasta = new FastA();
		var ntax = taxa.getNtax();
		var nchar = characters.getNchar();

		for (var i = 1; i <= ntax; i++) {
			var sequence = new StringBuilder("");
			for (var j = 1; j <= nchar; j++) {
				sequence.append(characters.get(i, j));
			}
			fasta.add(taxa.getLabel(i), sequence.toString().toUpperCase());
		}
		fasta.write(w);
	}
}
