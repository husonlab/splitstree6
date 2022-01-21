/*
 *  LeastSquaresWeights.java Copyright (C) 2022 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.algorithms.splits.splits2splits;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.LeastSquares;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * wrapper for the least squares computations
 *
 * @author huson
 * Date: 17-Feb-2004
 */
public class LeastSquaresWeights extends Splits2Splits {
	final DistancesBlock distancesBlock = new DistancesBlock();

	private final BooleanProperty optionConstrain = new SimpleBooleanProperty(this, "optionConstrain", false);

	@Override
	public List<String> listOptions() {
		// todo: optionConstrain is not implemented
		// return Collections.singletonList(optionConstrain.getName());
		return Collections.emptyList();
	}

	@Override
	public String getToolTip(String optionName) {
		if ("optionConstrain".equals(optionName)) {
			return "Use constrained least squares";
		}
		return optionName;
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock parent, SplitsBlock child) throws IOException {
		System.err.println("Computing least squares...");
        /*
        try {
            Writer w = new StringWriter();
            splits.write(w, doc.getTaxa());
            System.err.println(w.toString());
        } catch (Exception ex) {
            Basic.caught(ex);
        }
        */
		child.copy(parent);
		LeastSquares.optimizeLS(progress, child, distancesBlock, isOptionConstrain());
		parent.setFit(SplitsUtilities.computeLeastSquaresFit(distancesBlock, parent.getSplits()));
        /*
        try {
            Writer w = new StringWriter();
            splits.write(w, doc.getTaxa());
            System.err.println(w.toString());
        } catch (Exception ex) {
            Basic.caught(ex);
        }
        */

	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, SplitsBlock parent) {
		return !parent.isPartial();
	}

	public boolean isOptionConstrain() {
		return optionConstrain.get();
	}

	public BooleanProperty optionConstrainProperty() {
		return optionConstrain;
	}

	public void setOptionConstrain(boolean optionConstrain) {
		this.optionConstrain.set(optionConstrain);
	}

	public void setDistancesBlock(DistancesBlock dist) {
		this.distancesBlock.copy(dist);
	}
}
