package splitstree6.algorithms.characters.characters2characters;

import jloda.util.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.workflow.TopFilter;

import java.io.IOException;

public class CharactersTopFilter extends TopFilter<CharactersBlock, CharactersBlock> {
	public CharactersTopFilter(Class<CharactersBlock> fromClass, Class<CharactersBlock> toClass) {
		super(fromClass, toClass);
	}

	@Override
	public void filter(ProgressListener progress, TaxaBlock originalTaxaBlock, TaxaBlock modifiedTaxaBlock, CharactersBlock inputData, CharactersBlock outputData) throws IOException {
		// todo: implement direct copy?
		{
			progress.setMaximum(modifiedTaxaBlock.size());
			System.err.println("NOT IMPLEMENTED");
			/*
			final StringWriter w = new StringWriter();
			try {
				final CharactersNexusOutput charactersNexusOutput = new CharactersNexusOutput();
				charactersNexusOutput.setIgnoreMatrix(true);
				charactersNexusOutput.write(w, originalTaxaBlock, parent);
				final CharactersNexusInput charactersNexusInput = new CharactersNexusInput();
				charactersNexusInput.setIgnoreMatrix(true);
				charactersNexusInput.parse(new NexusStreamParser(new StringReader(w.toString())), originalTaxaBlock, outputData);
			} catch (IOException e) {
				Basic.caught(e);
			}
			 */
		}
		outputData.setDimension(modifiedTaxaBlock.getNtax(), 0);

		for (Taxon a : modifiedTaxaBlock.getTaxa()) {
			final int originalI = originalTaxaBlock.indexOf(a);
			final int modifiedI = modifiedTaxaBlock.indexOf(a);
			outputData.copyRow(inputData, originalI, modifiedI);
			progress.incrementProgress();
		}
		if (modifiedTaxaBlock.size() == originalTaxaBlock.size())
			setShortDescription("using all " + modifiedTaxaBlock.size() + " sequences");
		else
			setShortDescription("using " + modifiedTaxaBlock.size() + " of " + originalTaxaBlock.size() + " sequences");
	}
}
