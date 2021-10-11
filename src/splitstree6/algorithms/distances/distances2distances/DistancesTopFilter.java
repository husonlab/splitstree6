package splitstree6.algorithms.distances.distances2distances;

import jloda.util.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.sflow.TopFilter;

import java.io.IOException;

public class DistancesTopFilter extends TopFilter<DistancesBlock, DistancesBlock> {
	public DistancesTopFilter(Class<DistancesBlock> fromClass, Class<DistancesBlock> toClass) {
		super(fromClass, toClass);
	}

	@Override
	public void filter(ProgressListener progress, TaxaBlock originalTaxaBlock, TaxaBlock modifiedTaxaBlock, DistancesBlock inputData, DistancesBlock outputData) throws IOException {
		if (originalTaxaBlock.getTaxa().equals(modifiedTaxaBlock.getTaxa())) {
			outputData.copy(inputData);
			setShortDescription("using all " + modifiedTaxaBlock.size() + " taxa");

		} else {
			progress.setMaximum(modifiedTaxaBlock.getNtax());
			outputData.setNtax(modifiedTaxaBlock.getNtax());

			for (Taxon a : modifiedTaxaBlock.getTaxa()) {
				final int originalI = originalTaxaBlock.indexOf(a);
				final int modifiedI = modifiedTaxaBlock.indexOf(a);
				for (Taxon b : modifiedTaxaBlock.getTaxa()) {
					final int originalJ = originalTaxaBlock.indexOf(b);
					final int modifiedJ = modifiedTaxaBlock.indexOf(b);
					outputData.set(modifiedI, modifiedJ, inputData.get(originalI, originalJ));
					if (inputData.isVariances())
						outputData.setVariance(modifiedI, modifiedJ, inputData.getVariance(originalI, originalJ));
				}
				progress.incrementProgress();
			}
			setShortDescription("using " + modifiedTaxaBlock.size() + " of " + originalTaxaBlock.size() + " taxa");
		}
	}
}
