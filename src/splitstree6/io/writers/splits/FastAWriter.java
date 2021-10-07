package splitstree6.io.writers.splits;

import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

public class FastAWriter extends SplitsWriter {

	public FastAWriter() {
		setFileExtensions("fasta", "fa", "binary");
	}

	public void write(Writer w, TaxaBlock taxa, SplitsBlock splits) throws IOException {

		jloda.util.FastA fasta = new jloda.util.FastA();
		for (int t = 1; t <= taxa.getNtax(); t++) {
			char[] seq = new char[splits.getNsplits()];
			for (int s = 1; s <= splits.getNsplits(); s++) {
				if (splits.get(s).getA().get(t))
					seq[s - 1] = '1';
				else
					seq[s - 1] = '0';
			}
			fasta.add(taxa.getLabel(t), String.valueOf(seq));
		}
		fasta.write(w);
	}
}
