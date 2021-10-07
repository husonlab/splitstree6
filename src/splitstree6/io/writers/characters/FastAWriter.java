package splitstree6.io.writers.characters;

import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

public class FastAWriter extends CharactersWriter {
	public FastAWriter() {
		setFileExtensions("fasta", "fas", "fa", "seq", "fsa", "fna", "dna");
	}

	public void write(Writer w, TaxaBlock taxa, CharactersBlock characters) throws IOException {

		jloda.util.FastA fasta = new jloda.util.FastA();

		int ntax = taxa.getNtax();
		int nchar = characters.getNchar();

		for (int i = 1; i <= ntax; i++) {
			var sequence = new StringBuilder("");
			for (int j = 1; j <= nchar; j++) {
				sequence.append(characters.get(i, j));
			}
			fasta.add(taxa.getLabel(i), sequence.toString().toUpperCase());
		}
		fasta.write(w);
	}
}
