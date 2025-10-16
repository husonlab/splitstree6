/*
 * ImportHaplotypeApply.java Copyright (C) 2025 Daniel H. Huson
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
 *
 */

package splitstree6.dialog.haplotype;

import jloda.fx.util.RunAfterAWhile;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressSilent;
import razornetaccess.RazorHaplotypeNetwork;
import splitstree6.algorithms.characters.characters2distances.Characters2Distances;
import splitstree6.algorithms.characters.characters2distances.HammingDistance;
import splitstree6.algorithms.characters.characters2distances.nucleotide.TN93Distance;
import splitstree6.algorithms.characters.characters2network.MedianJoining;
import splitstree6.algorithms.distances.distances2network.MinSpanningNetwork;
import splitstree6.algorithms.network.network2view.ShowNetwork;
import splitstree6.algorithms.source.source2characters.CharactersLoader;
import splitstree6.data.*;
import splitstree6.io.nexus.CharactersNexusOutput;
import splitstree6.io.nexus.TaxaNexusOutput;
import splitstree6.io.nexus.TraitsNexusOutput;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.utils.DataReaderBase;
import splitstree6.utils.CollapseIdenticalHyplotypes;
import splitstree6.window.ImportButtonUtils;
import splitstree6.window.MainWindow;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;


public class ImportHaplotypeApply {
	public static void apply(HaplotypePresenter.Result result) throws IOException {
		FileUtils.checkFileReadableNonEmpty(result.sequencesPath());

		var importManager = ImportManager.getInstance();
		var inputClass = importManager.determineInputType(result.sequencesPath());
		if (!inputClass.equals(CharactersBlock.class))
			throw new IOException("Input must be an alignment of sequences");

		var progress = new ProgressSilent();

		var reader = (DataReaderBase<CharactersBlock>) importManager.getReader(result.sequencesPath());
		if (reader == null)
			throw new IOException("Failed to read sequences");

		// setup sequences:
		var originalTaxa = new TaxaBlock();
		var originalCharacters = new CharactersBlock();
		reader.read(progress, result.sequencesPath(), originalTaxa, originalCharacters);
		// setup traits:
		if (!result.traitsPath().isBlank()) {
			FileUtils.checkFileReadableNonEmpty(result.traitsPath());

			var traitsBlock = new TraitsBlock();
			traitsBlock.setDimensions(originalTaxa.getNtax(), 0);
			originalTaxa.setTraitsBlock(traitsBlock);

			var first = true;
			char splitToken = 0;

			for (var line : Files.readAllLines((new File(result.traitsPath())).toPath())) {
				if (first) {
					if (line.contains("\t"))
						splitToken = '\t';
					else if (line.contains(";"))
						splitToken = ';';
					else
						splitToken = ',';
					var tokens = StringUtils.split(line, splitToken);
					if (tokens.length > 0) {
						var taxonId = originalTaxa.indexOf(tokens[0]);
						if (taxonId != -1) {
							float[] latLongPair = null;
							for (int i = 1; i < tokens.length; i++) {
								var token = tokens[i];
								var coordinates = parsePair(token);
								if (coordinates != null) {
									latLongPair = coordinates;
								}
							}
							for (int i = 1; i < tokens.length; i++) {
								var token = tokens[i];
								var coordinates = parsePair(token);
								if (coordinates == null) {
									var traitId = traitsBlock.getTraitId(token);
									if (traitId == -1) {
										traitId = traitsBlock.addTrait(token);
									}
									traitsBlock.setTraitValue(taxonId, traitId, 1);
									if (latLongPair != null) {
										traitsBlock.setTraitLatitude(traitId, latLongPair[0]);
										traitsBlock.setTraitLongitude(traitId, latLongPair[1]);
									}
								}
							}
						} else System.err.println("Skipped: " + line);
					}
				}
			}
			if (false) {
				var w = new StringWriter();
				(new TraitsNexusOutput()).write(w, originalTaxa, traitsBlock);
				System.err.println("Input traits:\n" + w);
			}
		}
		var data = CollapseIdenticalHyplotypes.apply(originalTaxa, originalCharacters);
		if (true) {
			if (data != null) {
				var taxaBlock = data.getFirst();
				var traitsBlock = data.getSecond();
				var charactersBlock = data.getThird();
				taxaBlock.setTraitsBlock(traitsBlock);
				if (false) {
					var w = new StringWriter();
					(new TraitsNexusOutput()).write(w, originalTaxa, traitsBlock);
					System.err.println("Output traits:\n" + w);
				}

				var mainWindow = (MainWindow) MainWindowManager.getInstance().createAndShowWindow(true);
				mainWindow.setFileName(FileUtils.replaceFileSuffix(result.sequencesPath(), ".stree6"));
				var workflow = mainWindow.getWorkflow();
				var sourceBlock = new SourceBlock();
				workflow.setupInputAndWorkingNodes(sourceBlock, new CharactersLoader(), taxaBlock, charactersBlock);

				var networkNode = workflow.newDataNode(new NetworkBlock());

						switch (result.method()) {
							case "RazorNet" -> {
								var razor = new RazorHaplotypeNetwork();
								if (result.distanceModel().equalsIgnoreCase("tn93"))
									razor.optionDistanceMethodProperty().set(RazorHaplotypeNetwork.DistanceMethods.TN93);
								workflow.newAlgorithmNode(razor, workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), networkNode);
							}

							case "MinSpanningNetwork" -> {
								var distancesNode = workflow.newDataNode(new DistancesBlock());
								Characters2Distances distancesAlgorithm;
								if (result.distanceModel().equalsIgnoreCase("tn93"))
									distancesAlgorithm = new TN93Distance();
								else distancesAlgorithm = new HammingDistance();
								workflow.newAlgorithmNode(distancesAlgorithm, workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), distancesNode);
								workflow.newAlgorithmNode(new MinSpanningNetwork(), workflow.getWorkingTaxaNode(), distancesNode, networkNode);
							}
							default -> {
								workflow.newAlgorithmNode(new MedianJoining(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), networkNode);
							}
						};

				workflow.newAlgorithmNode(new ShowNetwork(), workflow.getWorkingTaxaNode(), networkNode, workflow.newDataNode(new ViewBlock()));
				workflow.restart(workflow.getInputTaxaFilterNode());
				RunAfterAWhile.applyInFXThread(mainWindow.getStage(), () -> mainWindow.getStage().toFront());
			}
		} else {
			if (data != null) {
				try {
					var w = new StringWriter();
					w.write("#nexus\n");
					(new TaxaNexusOutput()).write(w, data.getFirst());
					(new TraitsNexusOutput()).write(w, data.getFirst(), data.getSecond());
					(new CharactersNexusOutput()).write(w, data.getFirst(), data.getThird());
					ImportButtonUtils.openString(w.toString());
				} catch (IOException ex) {
					NotificationManager.showError("Failed: " + ex.getMessage());
				}
			}
		}
	}

	public static float[] parsePair(String s) {
		if (s == null || !s.startsWith("(") || !s.endsWith(")")) {
			return null;
		}
		String content = s.substring(1, s.length() - 1); // strip parentheses
		String[] parts = content.split(",");
		if (parts.length != 2) {
			return null;
		}
		try {
			float x = Float.parseFloat(parts[0].trim());
			float y = Float.parseFloat(parts[1].trim());
			return new float[]{x, y};
		} catch (NumberFormatException e) {
			return null; // one of the values was not a valid float
		}
	}
}
