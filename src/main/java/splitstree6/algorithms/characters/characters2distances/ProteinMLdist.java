/*
 * ProteinMLdist.java Copyright (C) 2024 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.algorithms.characters.characters2distances;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.utils.FixUndefinedDistances;
import splitstree6.algorithms.characters.characters2distances.utils.PairwiseCompare;
import splitstree6.algorithms.characters.characters2distances.utils.SaturatedDistancesException;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.CharactersType;
import splitstree6.models.proteinModels.*;

import java.io.IOException;
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

	private final Property<Model> optionModel = new SimpleObjectProperty<>(this, "optionModel", Model.JTT);
	private final DoubleProperty optionPropInvariableSites = new SimpleDoubleProperty(this, "optionPropInvariableSites", 0.0);
	private final DoubleProperty optionGamma = new SimpleDoubleProperty(this, "optionGamma", 0.0);

	@Override
	public String getCitation() {
		return "Swofford et al 1996; " +
			   "D.L. Swofford, G.J. Olsen, P.J. Waddell, and  D.M. Hillis. " +
			   "Chapter  11:  Phylogenetic inference. In D. M. Hillis, C. Moritz, and B. K. Mable, editors, " +
			   "Molecular Systematics, pages 407–514. Sinauer Associates, Inc., 2nd edition, 1996.";
	}

	public List<String> listOptions() {
		return List.of(optionModel.getName(), optionPropInvariableSites.getName(), optionGamma.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionPropInvariableSites.getName())) {
			return "Proportion of invariable sites";
		} else if (optionName.equals(optionGamma.getName())) {
			return "Alpha parameter for gamma distribution. Negative gamma = Equal rates";
		} else if (optionName.equals(optionModel.getName())) {
			return "Choose an amino acid substitution model";
		} else
			return super.getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {
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
		var maxValue = 0.0;
		for (int s = 1; s <= ntax; s++) {
			for (int t = s + 1; t <= ntax; t++) {
				final var seqPair = new PairwiseCompare(charactersBlock, s, t);
				var dist = -1.0;

				//Maximum likelihood distance. Note we want to ignore sites
				//with the stop codon.
				try {
					dist = seqPair.mlDistance(model);
				} catch (SaturatedDistancesException ignored) {
				}

				distancesBlock.set(s, t, dist);
				distancesBlock.set(t, s, dist);

				if (dist != -1.0) {
					var variance = seqPair.bulmerVariance(dist, 0.93);
					distancesBlock.setVariance(s, t, variance);
					distancesBlock.setVariance(t, s, variance);
				}
				progress.incrementProgress();
			}
		}

		FixUndefinedDistances.apply(distancesBlock);
		progress.reportTaskCompleted();
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock datablock) {
		return super.isApplicable(taxa, datablock) && datablock.getDataType() == CharactersType.Protein;
	}

	public ProteinModel selectModel(Model model) {
		ProteinModel themodel;

		System.err.println("Model name = " + model.toString());
		//TODO: Add all models
		themodel = switch (model) {
			case cpREV45 -> new cpREV45Model();
			case Dayhoff -> new DayhoffModel();
			case JTT -> new JTTmodel();
			case mtMAM -> new mtMAMModel();
			case mtREV24 -> new mtREV24Model();
			case pmb -> new pmbModel();
			case Rhodopsin -> new RhodopsinModel();
			case WAG -> new WagModel();
		};

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


	public double getOptionGamma() {
		return this.optionGamma.getValue();
	}

	public DoubleProperty optionGammaProperty() {
		return this.optionGamma;
	}

	public void setOptionGamma(double gamma) {
		this.optionGamma.setValue(gamma);
	}
}
