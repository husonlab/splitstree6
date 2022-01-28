/*
 * Nucleotides2DistancesBase.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances.nucleotide;

import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import jloda.fx.util.AService;
import jloda.fx.window.NotificationManager;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressPercentage;
import splitstree6.algorithms.characters.characters2distances.Characters2Distances;
import splitstree6.analysis.CaptureRecapture;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.models.nucleotideModels.NucleotideModel;

import java.io.IOException;

/**
 * nucleotides to distances algorithms base class
 * Dave Bryant 2005, Daniel Huson 2019
 */
public abstract class Nucleotides2DistancesBase extends Characters2Distances {

	public enum SetParameters {fromChars, defaultValues}

	protected final static double DEFAULT_GAMMA = -1;        //Negative gamma corresponds to equals rates
	protected final static double DEFAULT_PROP_INVARIABLE_SITES = 0.0;

	protected final static double[] DEFAULT_BASE_FREQ = {0.25, 0.25, 0.25, 0.25};  //Use the exact distance by default - transforms without exact distances should set useML = false

	protected final static double DEFAULT_TSTV_RATIO = 2.0;  //default is no difference between transitions and transversions
	protected final static double DEFAULT_AC_VS_AT = 2.0;
	protected final static double[][] DEFAULT_RATE_MATRIX = {{-3, 1, 1, 1}, {1, -3, 1, 1}, {1, 1, -3, 1}, {1, 1, 1, -3}};

	protected final static boolean DEFAULT_USE_ML = false;

	private final DoubleProperty optionGamma = new SimpleDoubleProperty(DEFAULT_GAMMA);
	private final DoubleProperty optionPropInvariableSites = new SimpleDoubleProperty(DEFAULT_PROP_INVARIABLE_SITES);

	private final ObjectProperty<double[]> optionBaseFrequencies = new SimpleObjectProperty<>(DEFAULT_BASE_FREQ);

	private final DoubleProperty optionTsTvRatio = new SimpleDoubleProperty(DEFAULT_TSTV_RATIO);
	private final DoubleProperty optionACvATRatio = new SimpleDoubleProperty(DEFAULT_AC_VS_AT);
	private final ObjectProperty<double[][]> optionRateMatrix = new SimpleObjectProperty<>(DEFAULT_RATE_MATRIX);

	private final BooleanProperty optionUseML_Distances = new SimpleBooleanProperty(DEFAULT_USE_ML);

	private final ObjectProperty<SetParameters> optionSetBaseFrequencies = new SimpleObjectProperty<>(SetParameters.defaultValues);
	private final ObjectProperty<SetParameters> optionSetSiteVarParams = new SimpleObjectProperty<>(SetParameters.defaultValues);

	private ChangeListener<SetParameters> listenerSetBaseFrequencies = null;
	private ChangeListener<SetParameters> listenerSetSiteVarParams = null;


	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "optionPropInvariableSites" -> "Proportion of invariable sites";
			case "optionGamma" -> "Alpha value for the Gamma distribution";
			case "optionUseML_Distances" -> "Use maximum likelihood estimation of distances (rather than exact distances)";
			case "optionTsTvRatio" -> "Ratio of transitions vs transversions";
			case "optionBaseFrequencies" -> "Base frequencies (in order ACGT/U)";
			case "optionRateMatrix" -> "Rate matrix for GTR (in order ACGT/U)";
			case "optionSetBaseFrequencies" -> "Set base frequencies to default values, or to estimations from characters (using Capture-recapture for invariable sites)";
			case "optionSetSiteVarParams" -> "Set site variation parameters to default values, or to estimations from characters";
			default -> super.getToolTip(optionName);
		};
	}

	/**
	 * this is run after the node has been instantiated
	 */
	public void setupBeforeDisplay(TaxaBlock taxaBlock, CharactersBlock parent) {
		if (listenerSetSiteVarParams != null)
			optionSetSiteVarParamsProperty().removeListener(listenerSetSiteVarParams);
		// setup set Parameters control:
		listenerSetSiteVarParams = (c, o, n) -> {
            switch (n) {
                case defaultValues -> {
                    setOptionPropInvariableSites(DEFAULT_PROP_INVARIABLE_SITES);
                    setOptionGamma(DEFAULT_GAMMA);
                    break;
                }
                case fromChars -> {
                    final AService<Double> service = new AService<>(() -> {
                        // todo: want this to run in foot pane
                        try (ProgressPercentage progress = new ProgressPercentage(CaptureRecapture.DESCRIPTION)) {
                            final CaptureRecapture captureRecapture = new CaptureRecapture();
                            return captureRecapture.estimatePropInvariableSites(progress, parent);
                        }
                    });
                    service.setOnSucceeded((e) -> setOptionPropInvariableSites(service.getValue()));
                    service.setOnFailed((e) -> {
                        NotificationManager.showError("Calculation of proportion of invariable sites failed: " + service.getException().getMessage());
                    });
                    service.start();
                    break;
                }
            }
        };
		optionSetSiteVarParamsProperty().addListener(listenerSetSiteVarParams);

		if (listenerSetBaseFrequencies != null)
			optionSetBaseFrequenciesProperty().removeListener(listenerSetBaseFrequencies);
		// setup set Parameters control:
		listenerSetBaseFrequencies = (c, o, n) -> {
            switch (n) {
                case defaultValues -> {
                    setOptionBaseFrequencies(DEFAULT_BASE_FREQ);
                    setOptionRateMatrix(DEFAULT_RATE_MATRIX);
                    setOptionTsTvRatio(DEFAULT_TSTV_RATIO);
                    setOptionACvATRatio(DEFAULT_AC_VS_AT);
                    break;
                }
                case fromChars -> {
                    final AService<double[]> service = new AService<>(() -> NucleotideModel.computeFreqs(parent, false));
                    service.setOnSucceeded((e) -> setOptionBaseFrequencies(service.getValue()));
                    service.setOnFailed((e) -> {
                        NotificationManager.showError("Calculation of base frequencies failed: " + service.getException().getMessage());
                    });
                    service.start();

                    // todo: don't know how to estimate QMatrix from data, ask Dave!
                    setOptionRateMatrix(DEFAULT_RATE_MATRIX);
                    break;
                }
            }
        };
		optionSetBaseFrequenciesProperty().addListener(listenerSetBaseFrequencies);
	}

	@Override
	abstract public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock parent, DistancesBlock child) throws IOException;

	public double getOptionPropInvariableSites() {
		return optionPropInvariableSites.get();
	}

	public DoubleProperty optionPropInvariableSitesProperty() {
		return optionPropInvariableSites;
	}

	public void setOptionPropInvariableSites(double optionPropInvariableSites) {
		this.optionPropInvariableSites.set(optionPropInvariableSites);
	}

	public double getOptionGamma() {
		return optionGamma.get();
	}

	public DoubleProperty optionGammaProperty() {
		return optionGamma;
	}

	public void setOptionGamma(double optionGamma) {
		this.optionGamma.set(optionGamma);
	}

	public boolean isOptionUseML_Distances() {
		return optionUseML_Distances.get();
	}

	public BooleanProperty optionUseML_DistancesProperty() {
		return optionUseML_Distances;
	}

	public void setOptionUseML_Distances(boolean optionUseML_Distances) {
		this.optionUseML_Distances.set(optionUseML_Distances);
	}

	public double getOptionTsTvRatio() {
		return optionTsTvRatio.get();
	}

	public DoubleProperty optionTsTvRatioProperty() {
		return optionTsTvRatio;
	}

	public void setOptionTsTvRatio(double optionTsTvRatio) {
		this.optionTsTvRatio.set(optionTsTvRatio);
	}

	public double[] getOptionBaseFrequencies() {
		return optionBaseFrequencies.get();
	}

	public ObjectProperty<double[]> optionBaseFrequenciesProperty() {
		return optionBaseFrequencies;
	}

	public void setOptionBaseFrequencies(double[] optionBaseFrequencies) {
		this.optionBaseFrequencies.set(optionBaseFrequencies);
	}

	public void setOptionACvATRatio(double value) {
		this.optionACvATRatio.setValue(value);
	}

	public double getOptionACvATRatio() {
		return this.optionACvATRatio.getValue();
	}

	public DoubleProperty optionACvATRatioProperty() {
		return this.optionACvATRatio;
	}

	public double[][] getOptionRateMatrix() {
		return optionRateMatrix.get();
	}

	public ObjectProperty<double[][]> optionRateMatrixProperty() {
		return optionRateMatrix;
	}

	public void setOptionRateMatrix(double[][] optionRateMatrix) {
		this.optionRateMatrix.set(optionRateMatrix);
	}

	public SetParameters getOptionSetBaseFrequencies() {
		return optionSetBaseFrequencies.get();
	}

	public ObjectProperty<SetParameters> optionSetBaseFrequenciesProperty() {
		return optionSetBaseFrequencies;
	}

	public void setOptionSetBaseFrequencies(SetParameters optionSetBaseFrequencies) {
		this.optionSetBaseFrequencies.set(optionSetBaseFrequencies);
	}

	public SetParameters getOptionSetSiteVarParams() {
		return optionSetSiteVarParams.get();
	}

	public ObjectProperty<SetParameters> optionSetSiteVarParamsProperty() {
		return optionSetSiteVarParams;
	}

	public void setOptionSetSiteVarParams(SetParameters optionSetSiteVarParams) {
		this.optionSetSiteVarParams.set(optionSetSiteVarParams);
	}
}
