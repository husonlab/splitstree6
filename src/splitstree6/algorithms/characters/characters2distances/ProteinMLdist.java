/*
 * ProteinMLdist.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.algorithms.characters.characters2distances;

import javafx.beans.property.*;
import jloda.fx.window.NotificationManager;
import jloda.util.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.utils.PairwiseCompare;
import splitstree6.algorithms.characters.characters2distances.utils.SaturatedDistancesException;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.CharactersType;
import splitstree6.models.proteinModels.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Computes the maximum likelihood protein distance estimates for a set of characters
 * <p>
 * Created on Jun 8, 2004
 *
 * @author bryant
 */

public class ProteinMLdist extends Characters2Distances {
	public enum Model {cpREV45, Dayhoff, JTT, mtMAM, mtREV24, pmb, Rhodopsin, WAG}

	private final Property<Model> optionModel = new SimpleObjectProperty<>(Model.JTT);
	private final DoubleProperty optionPropInvariableSites = new SimpleDoubleProperty(0.0);
	private final DoubleProperty optionGamma = new SimpleDoubleProperty(0.0);

	//todo these are not used? delete?
	private final BooleanProperty optionUsePropInvariableSites = new SimpleBooleanProperty(false);
	private final BooleanProperty optionUseGamma = new SimpleBooleanProperty(false);
	private final BooleanProperty optionEstimateVariance = new SimpleBooleanProperty(true);

	public final static String DESCRIPTION = "Calculates maximum likelihood protein distance estimates";

	@Override
	public String getCitation() {
		return "Swofford et al 1996; " +
				"D.L. Swofford, G.J. Olsen, P.J. Waddell, and  D.M. Hillis. " +
				"Chapter  11:  Phylogenetic inference. In D. M. Hillis, C. Moritz, and B. K. Mable, editors, " +
				"Molecular Systematics, pages 407–514. Sinauer Associates, Inc., 2nd edition, 1996.";
	}

	public List<String> listOptions() {
		return Arrays.asList("Model", "PropInvariableSites", "Gamma",
				"UsePropInvariableSites", "UseGamma", "EstimateVariance");
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "PropInvariableSites" -> "Proportion of invariable sites";
			case "Gamma" -> "Alpha parameter for gamma distribution. Negative gamma = Equal rates";
			case "Model" -> "Choose an amino acid substitution model";
			case "UsePropInvariableSites" -> "UsePropInvariableSites";
			case "UseGamma" -> "UseGamma";
			case "EstimateVariance" -> "EstimateVariance";
			default -> null;
		};
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {

		boolean hasSaturated = false;

		int ntax = charactersBlock.getNtax();
		int npairs = ntax * (ntax - 1) / 2;

		distancesBlock.setNtax(ntax);
		progress.setTasks("Protein ML distance", "Init.");
		progress.setMaximum(npairs);

		ProteinModel model = selectModel(optionModel.getValue());
		model.setPinv(this.getOptionPropInvariableSites());
		model.setGamma(this.getOptionGamma());

        /*if (model == null) {
            throw new SplitsException("Incorrect model name");
        }*/
		int k = 0;
		for (int s = 1; s <= ntax; s++) {
			for (int t = s + 1; t <= ntax; t++) {
				final PairwiseCompare seqPair = new PairwiseCompare(charactersBlock, s, t);
				double dist = 100.0;

				//Maximum likelihood distance. Note we want to ignore sites
				//with the stop codon.
				try {
					dist = seqPair.mlDistance(model);
				} catch (SaturatedDistancesException e) {
					hasSaturated = true;
				}

				distancesBlock.set(s, t, dist);
				distancesBlock.set(t, s, dist);

				double var = seqPair.bulmerVariance(dist, 0.93);
				distancesBlock.setVariance(s, t, var);
				distancesBlock.setVariance(t, s, var);

				k++;
				progress.incrementProgress();
			}
		}

		progress.close();
		if (hasSaturated) {
			NotificationManager.showWarning("Proceed with caution: saturated or missing entries in the distance matrix");
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock ch) {
		return ch.getDataType() == CharactersType.Protein;
	}

	public ProteinModel selectModel(Model model) {
		ProteinModel themodel;

		System.err.println("Model name = " + model.toString());
		//TODO: Add all models
		switch (model) {
			case cpREV45:
				themodel = new cpREV45Model();
				break;
			case Dayhoff:
				themodel = new DayhoffModel();
				break;
			case JTT:
				themodel = new JTTmodel();
				break;
			case mtMAM:
				themodel = new mtMAMModel();
				break;
			case mtREV24:
				themodel = new mtREV24Model();
				break;
			case pmb:
				themodel = new pmbModel();
				break;
			case Rhodopsin:
				themodel = new RhodopsinModel();
				break;
			case WAG:
				themodel = new WagModel();
				break;
			default:
				themodel = null;
				break;
		}

		return themodel;
	}

	// GETTER AND SETTER

	public Model getOptionModel() {
		return this.optionModel.getValue();
	}

	public Property<Model> optionModelProperty() {
		return this.optionModel;
	}

	public void setOptionModel(Model optionModel) {
		this.optionModel.setValue(optionModel);
	}


	public double getOptionPropInvariableSites() {
		return this.optionPropInvariableSites.getValue();
	}

	public DoubleProperty optionPropInvariableSitesProperty() {
		return this.optionPropInvariableSites;
	}

	public void setOptionPropInvariableSites(double pinvar) {
		this.optionPropInvariableSites.setValue(pinvar);
	}


	public boolean getOptionUsePropInvariableSites() {
		return this.optionUsePropInvariableSites.getValue();
	}

	public BooleanProperty optionUsePropInvariableSitesProperty() {
		return this.optionUsePropInvariableSites;
	}

	public void setOptionUsePropInvariableSites(boolean val) {
		this.optionUsePropInvariableSites.setValue(val);
	}


	public double getOptionGamma() {
		return this.optionGamma.getValue();
	}

	public DoubleProperty optionGammaProperty() {
		return this.optionGamma;
	}

	public void setOptionGamma(double gamma) {
		this.optionGamma.setValue(gamma);
	}


	public boolean getOptionUseGamma() {
		return this.optionUseGamma.getValue();
	}

	public BooleanProperty optionUseGammaProperty() {
		return this.optionUseGamma;
	}

	public void setOptionUseGamma(boolean var) {
		this.optionUseGamma.setValue(var);
	}

	public boolean getOptionEstimateVariance() {
		return this.optionEstimateVariance.getValue();
	}

	public BooleanProperty optionEstimateVarianceProperty() {
		return this.optionEstimateVariance;
	}

	public void setOptionEstimateVariance(boolean val) {
		this.optionEstimateVariance.setValue(val);
	}
}
