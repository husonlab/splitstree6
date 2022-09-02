/*
 *  AnalyzeGenomesPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.dialog.analyzegenomes;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.When;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.*;
import jloda.fx.window.NotificationManager;
import jloda.seq.FastAFileIterator;
import jloda.util.*;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AnalyzeGenomesPresenter {
	public AnalyzeGenomesPresenter(AnalyzeGenomesDialog dialog) {
		var stage = dialog.getStage();
		var controller = dialog.getController();

		stage.setOnCloseRequest(c -> {
			if (dialog.isRunning())
				c.consume();
		});

		controller.getInputBrowseButton().setOnAction(c -> {
			final var files = getInputFiles(dialog);
			if (files != null) {
				if (controller.getInputTextArea().getText().trim().length() > 0 && !controller.getInputTextArea().getText().trim().endsWith("'")) {
					controller.getInputTextArea().setText(controller.getInputTextArea().getText().trim() + ",\n" + StringUtils.toString(files, ",\n"));
				} else
					controller.getInputTextArea().setText(StringUtils.toString(files, ",\n"));
			}
		});
		controller.getInputTextArea().textProperty().addListener((c, o, n) -> {
			final String firstLine = StringUtils.getFirstLine(n);
			if (firstLine.length() > 0) {
				final File inputFile = new File(firstLine);
				if (inputFile.getParentFile().exists()) {
					controller.getOutputFileTextField().setText(createOutputName(inputFile));
				}
			}
			dialog.clearReferences();
		});

		controller.getOutputBrowseButton().setOnAction(c -> {
			final File outputFile = getOutputFile(stage, controller.getOutputFileTextField().getText());
			if (outputFile != null) {
				final String outputFileName;
				if (FileUtils.getFileSuffix(outputFile.getName()).length() == 0)
					outputFileName = outputFile.getPath() + ".stree6";
				else
					outputFileName = outputFile.getPath();

				controller.getOutputFileTextField().setText(outputFileName);
			}
		});

		controller.getClearInputButton().setOnAction(c -> controller.getInputTextArea().setText(""));
		controller.getClearInputButton().disableProperty().bind(controller.getInputTextArea().textProperty().isEmpty());

		controller.getSequenceTypeChoiceBox().getItems().addAll(AnalyzeGenomesDialog.SequenceType.values());
		controller.getSequenceTypeChoiceBox().valueProperty().bindBidirectional(dialog.optionSequenceTypeProperty());

		controller.getMinLengthTextField().setText(String.valueOf(dialog.getOptionMinLength()));
		controller.getMinLengthTextField().textProperty().addListener((c, o, n) -> dialog.setOptionMinLength(NumberUtils.parseInt(n)));

		controller.getTaxaChoiceBox().getItems().addAll(AnalyzeGenomesDialog.TaxonIdentification.values());
		controller.getTaxaChoiceBox().valueProperty().bindBidirectional(dialog.optionTaxonIdentificationProperty());

		controller.getStoreOnlyReferencesCheckBox().selectedProperty().bind(dialog.optionStoreOnlyReferencesProperty());

		controller.getCancelButton().setOnAction(c -> stage.close());
		controller.getCancelButton().disableProperty().bind(dialog.runningProperty());

		controller.getSupportedHTMLTextArea().setText("Supported HTML tags:\n" + RichTextLabel.getSupportedHTMLTags());
		controller.getSupportedHTMLTextArea().visibleProperty().bind(controller.getHtmlInfoButton().selectedProperty());
		controller.getSupportedHTMLTextArea().prefRowCountProperty().bind(new When(controller.getHtmlInfoButton().selectedProperty()).then(4).otherwise(0));
		controller.getSupportedHTMLTextArea().prefHeightProperty().bind(new When(controller.getHtmlInfoButton().selectedProperty()).then(Region.USE_COMPUTED_SIZE).otherwise(0));

		controller.getDisplayLabelsListView().setItems(FXCollections.observableArrayList());
		final LabelListsManager labelListsManager = new LabelListsManager(controller);
		controller.getTaxonLabelsTab().selectedProperty().addListener((c, o, n) -> {
			if (n) {
				labelListsManager.update(getLabels(StringUtils.split(controller.getInputTextArea().getText(), ','), dialog.getOptionTaxonIdentification()));
			}
		});
		controller.getTaxonLabelsTab().disableProperty().bind(dialog.runningProperty());

		controller.getInputTextArea().textProperty().addListener((c, o, n) -> {
			if (n.length() == 0) {
				labelListsManager.clear();
			}
		});

		controller.getFilesTab().disableProperty().bind(dialog.runningProperty());

		final RichTextLabel richTextLabel = new RichTextLabel();
		controller.getStatusFlowPane().getChildren().add(richTextLabel);

		controller.getDisplayLabelsListView().getSelectionModel().selectedItemProperty().addListener((c, o, n) -> richTextLabel.setText(n != null ? n : ""));

		controller.getApplyButton().setOnAction(c -> {
			var genomesAnalyzer = new GenomesAnalyzer(Arrays.asList(StringUtils.split(controller.getInputTextArea().getText(), ',')),
					dialog.getOptionTaxonIdentification(), labelListsManager.computeLine2Label(), dialog.getOptionMinLength(), dialog.isOptionStoreOnlyReferences());

			if (dialog.getReferencesDatabase() != null && dialog.getReferenceIds().size() > 0) {
				var fileCacheDirectory = dialog.getOptionFileCacheDirectory();

				if (fileCacheDirectory.equals("") || !FileUtils.isDirectory(fileCacheDirectory)) {
					var alert = new Alert(Alert.AlertType.CONFIRMATION);
					alert.setTitle("Confirmation Dialog - " + ProgramProperties.getProgramName());
					alert.setHeaderText("The program will download and cache reference genomes");
					alert.setContentText("Do you want to proceed and choose a cache directory?");

					final var result = alert.showAndWait();
					if (result.isPresent() && result.get() != ButtonType.OK) {
						NotificationManager.showWarning("User canceled");
						return;
					}

					final var dir = chooseCacheDirectory(dialog, dialog.getReferencesDatabase().getDbFile().getParentFile());
					if (dir == null || !dir.canWrite())
						return;
					else
						dialog.setOptionFileCacheDirectory(dir.getPath());
				}
			}
			System.err.println("File-caching directory: " + dialog.getOptionFileCacheDirectory());
			genomesAnalyzer.saveData(dialog.getReferencesDatabase(), dialog.getReferenceIds(), controller.getOutputFileTextField().getText(), controller.getStatusFlowPane(), dialog.runningProperty()::set);
		});
		controller.getApplyButton().disableProperty().bind(controller.getInputTextArea().textProperty().isEmpty().or(dialog.runningProperty()).or(labelListsManager.applicableProperty().not())
				.or(controller.getMainTabPane().getSelectionModel().selectedItemProperty().isEqualTo(controller.getRelatedTab()).and(Bindings.isEmpty(dialog.getReferenceIds()))));

		setupReferenceDatabaseTab(dialog, controller, labelListsManager, dialog.referencesDatabaseProperty(), dialog.getReferences());
	}

	private void setupReferenceDatabaseTab(AnalyzeGenomesDialog dialog, AnalyzeGenomesController controller, LabelListsManager labelListsManager, ObjectProperty<AccessReferenceDatabase> accessReferenceDatabase, ObservableList<Map.Entry<Integer, Double>> references) {
		final Label floatingLabel = new Label();
		controller.getAddedReferencesLabel().setText(String.valueOf(dialog.getReferenceIds().size()));
		dialog.getReferenceIds().addListener((InvalidationListener) e -> {
			controller.getAddedReferencesLabel().setText(String.valueOf(dialog.getReferenceIds().size()));
			floatingLabel.setText("Added: " + dialog.getReferenceIds().size());
			if (dialog.getReferenceIds().size() == 0)
				controller.getStatusFlowPane().getChildren().remove(floatingLabel);
			else if (!controller.getStatusFlowPane().getChildren().contains(floatingLabel))
				controller.getStatusFlowPane().getChildren().add(floatingLabel);
		});

		controller.getFoundReferencesLabel().setText("");
		references.addListener((InvalidationListener) e -> controller.getFoundReferencesLabel().setText("Found: " + references.size()));

		controller.getReferencesDatabaseButton().setOnAction(e -> {
			final var file = getReferenceDatabaseFile(dialog);
			if (file != null)
				dialog.setOptionReferenceDatabaseFile(file.getPath());
		});
		controller.getReferencesDatabaseButton().disableProperty().bind(dialog.runningProperty());

		Consumer<String> setupAccessDatabase = n -> {
			if (AccessReferenceDatabase.isDatabaseFile(n)) {
				accessReferenceDatabase.set(null);
				try {
					accessReferenceDatabase.set(new AccessReferenceDatabase(n, () -> new File(dialog.getOptionFileCacheDirectory()), 2 * ProgramExecutorService.getNumberOfCoresToUse()));
				} catch (IOException | SQLException ex) {
					NotificationManager.showError("Open reference database failed: " + ex.getMessage());
				}
			}
		};

		dialog.optionReferenceDatabaseFileProperty().addListener((v, o, n) -> {
			setupAccessDatabase.accept(n);
		});
		controller.getReferencesDatabaseTextField().textProperty().bindBidirectional(dialog.optionReferenceDatabaseFileProperty());
		controller.getReferencesDatabaseTextField().disableProperty().bind(dialog.runningProperty());

		if (!dialog.getOptionReferenceDatabaseFile().isBlank())
			setupAccessDatabase.accept(dialog.getOptionReferenceDatabaseFile());

		controller.getIncludeStrainsCB().selectedProperty().bindBidirectional(dialog.optionIncludeStrainsProperty());
		controller.getIncludeStrainsCB().disableProperty().bind(dialog.runningProperty());

		final var threshold = new SimpleDoubleProperty(this, "threshold");
		threshold.bindBidirectional(controller.getMaxDistanceSlider().valueProperty());

		final XYChart.Series<Double, Integer> thresholdLine = createThresholdLine(threshold, references);

		controller.getFindReferencesButton().setOnAction(e -> {
			final AService<Collection<Map.Entry<Integer, Double>>> service = new AService<>(controller.getStatusFlowPane());
			service.setCallable(new TaskWithProgressListener<>() {
				@Override
				public Collection<Map.Entry<Integer, Double>> call() throws Exception {
					var genomesAnalyzer = new GenomesAnalyzer(Arrays.asList(StringUtils.split(controller.getInputTextArea().getText(), ',')),
							dialog.getOptionTaxonIdentification(), labelListsManager.computeLine2Label(), dialog.getOptionMinLength(), dialog.isOptionStoreOnlyReferences());
					var queries = new ArrayList<byte[]>();
					for (var record : genomesAnalyzer.iterable(getProgressListener())) {
						queries.add(record.getSequence());
					}
					return accessReferenceDatabase.get().findSimilar(service.getProgressListener(), dialog.getOptionMaxDistanceToSearch(), dialog.isOptionIncludeStrains(), queries, true);
				}
			});
			service.runningProperty().addListener((c, o, n) -> dialog.runningProperty().set(n));
			dialog.clearReferences();
			service.setOnSucceeded(z -> {
				references.setAll(service.getValue());
				final ObservableList<XYChart.Data<Double, Integer>> data = FXCollections.observableArrayList();
				int runningSum = 0;
				for (Map.Entry<Integer, Double> pair : references) {
					data.add(new XYChart.Data<>(pair.getValue(), runningSum++));
				}
				controller.getMashDistancesChart().getData().add(new XYChart.Series<>(data));
				controller.getMashDistancesChart().getData().add(thresholdLine);
				final double dist = controller.getMaxDistanceSlider().getValue();
				controller.getMaxDistanceSlider().setValue(0);
				Platform.runLater(() -> controller.getMaxDistanceSlider().setValue(dist));
			});
			service.start();
		});

		controller.getFindReferencesButton().disableProperty().bind(controller.getInputTextArea().textProperty().isEmpty().or(dialog.runningProperty()).or(labelListsManager.applicableProperty().not()).or(Bindings.isNull(dialog.referencesDatabaseProperty())));

		controller.getMaxDistanceSlider().disableProperty().bind(controller.getFindReferencesButton().disabledProperty().or(dialog.runningProperty()));

		final var inThesholdUpdate = new SimpleBooleanProperty(false);
		threshold.addListener((c, o, n) -> {
			if (!inThesholdUpdate.get()) {
				inThesholdUpdate.set(true);
				try {
					controller.getMaxToAddTextField().setText(String.valueOf(references.stream().filter(p -> p.getValue() <= n.doubleValue()).count()));
				} finally {
					inThesholdUpdate.set(false);
				}
			}
		});

		final var maxCount = new SimpleIntegerProperty(this, "maxCount", 0);

		controller.getMaxToAddTextField().textProperty().addListener((c, o, n) -> {
			final var max = Math.min(references.size(), Math.max(0, NumberUtils.parseInt(n)));
			maxCount.set(max);
		});

		maxCount.addListener((c, o, n) -> {
			if (!inThesholdUpdate.get()) {
				inThesholdUpdate.set(true);
				try {
					var thresholdValue = references.stream().limit(n.intValue()).mapToDouble(Map.Entry::getValue).max();
					if (thresholdValue.isPresent())
						threshold.setValue(thresholdValue.getAsDouble());
				} finally {
					inThesholdUpdate.set(false);
				}
			}
		});

		controller.getRemoveAllReferencesButton().setOnAction(e -> dialog.getReferenceIds().clear());
		controller.getRemoveAllReferencesButton().disableProperty().bind(Bindings.isEmpty(dialog.getReferenceIds()).or(dialog.runningProperty()));

		controller.getMaxToAddTextField().disableProperty().bind(controller.getFindReferencesButton().disabledProperty().or(dialog.runningProperty()));

		controller.getAddReferencesButton().setOnAction(e -> dialog.getReferenceIds().setAll(references.stream().limit(maxCount.intValue()).filter(p -> p.getValue() <= threshold.get()).map(Map.Entry::getKey).collect(Collectors.toList())));
		controller.getAddReferencesButton().disableProperty().bind(Bindings.isEmpty(references).or(accessReferenceDatabase.isNull()).or(dialog.runningProperty()));
		controller.getMashDistancesChart().disableProperty().bind(controller.getAddReferencesButton().disabledProperty());

		dialog.optionMaxDistanceToSearchProperty().addListener((v, o, n) -> {
			if (n.doubleValue() > 0.0 && n.doubleValue() < 1)
				controller.getMaxDistanceSlider().setMax(n.doubleValue() + 0.01);
		});
		controller.getMaxDistanceSlider().setMax(dialog.getOptionMaxDistanceToSearch() + 0.01);
		controller.getMaxDistToSearchTextField().textProperty().addListener((v, o, n) -> dialog.setOptionMaxDistanceToSearch(NumberUtils.parseDouble(n)));
		controller.getMaxDistToSearchTextField().setText(String.valueOf(dialog.getOptionMaxDistanceToSearch()));
		controller.getMaxDistToSearchTextField().disableProperty().bind(controller.getFindReferencesButton().disableProperty());

		controller.getCacheButton().setOnAction(e -> {
			var defaultDir = (!dialog.getOptionFileCacheDirectory().isBlank() ? dialog.getOptionFileCacheDirectory() : dialog.getReferencesDatabase().getDbFile().getParent());
			var dir = chooseCacheDirectory(dialog, new File(defaultDir));
			if (dir != null && dir.canRead())
				dialog.setOptionFileCacheDirectory(dir.getPath());
		});
		controller.getCacheButton().disableProperty().bind(controller.getFindReferencesButton().disabledProperty());
	}

	private static XYChart.Series<Double, Integer> createThresholdLine(DoubleProperty threshold, ObservableList<Map.Entry<Integer, Double>> references) {
		final ObservableList<XYChart.Data<Double, Integer>> data = FXCollections.observableArrayList();
		final XYChart.Series<Double, Integer> series = new XYChart.Series<>(data);
		final InvalidationListener listener = e -> {
			data.clear();
			data.add(new XYChart.Data<>(threshold.get(), 0));
			data.add(new XYChart.Data<>(threshold.get(), references.size()));
		};
		threshold.addListener(listener);
		references.addListener(listener);
		return series;
	}

	/**
	 * create a default output file name
	 *
	 * @return name
	 */
	private static String createOutputName(File inputFile) {
		var file = FileUtils.replaceFileSuffix(inputFile, ".stree6");
		var count = 0;
		while (file.exists()) {
			file = FileUtils.replaceFileSuffix(inputFile, "-" + (++count) + ".stree6");
		}
		return file.getPath();
	}

	private static List<File> getInputFiles(AnalyzeGenomesDialog dialog) {
		final var previousDir = new File(dialog.getOptionInputDirectory());
		final var fileChooser = new FileChooser();
		if (previousDir.isDirectory())
			fileChooser.setInitialDirectory(previousDir);
		fileChooser.setTitle("Genome Files");
		fileChooser.getExtensionFilters().addAll(FastAFileFilter.getInstance(), FastQFileFilter.getInstance(), AllFileFilter.getInstance());
		var result = fileChooser.showOpenMultipleDialog(dialog.getStage());
		if (result != null && result.size() > 0)
			dialog.setOptionInputDirectory(result.get(0).getParent());
		return result;
	}

	private static File getOutputFile(Stage owner, String defaultName) {
		final var fileChooser = new FileChooser();
		if (defaultName.length() > 0) {
			final var previousDir = new File(defaultName);
			if (previousDir.isDirectory())
				fileChooser.setInitialDirectory(previousDir);
			fileChooser.setInitialFileName(FileUtils.getFileNameWithoutPath(defaultName));
		}
		fileChooser.setTitle("Output File");
		fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("SplitsTree6 Files", "*.stree6", "*.nxs", "*.nex"));

		return fileChooser.showSaveDialog(owner);
	}

	private static File getReferenceDatabaseFile(AnalyzeGenomesDialog dialog) {
		final var previous = dialog.getOptionReferenceDatabaseFile();
		final var fileChooser = new FileChooser();
		if (previous.length() > 0) {
			fileChooser.setInitialDirectory((new File(previous)).getParentFile());
			fileChooser.setInitialFileName(FileUtils.getFileNameWithoutPath(previous));
		}
		fileChooser.setTitle("SplitsTree6 References Database");
		fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("SplitsTree6 References Database", "*.db", "*.st6db"));

		final var file = fileChooser.showOpenDialog(dialog.getStage());
		if (file != null)
			dialog.setOptionReferenceDatabaseFile(file.getPath());
		return file;
	}

	public static File chooseCacheDirectory(AnalyzeGenomesDialog dialog, File defaultDir) {
		final var fileChooser = new DirectoryChooser();
		fileChooser.setTitle("Choose file cache directory");
		fileChooser.setInitialDirectory(defaultDir);

		final File dir = fileChooser.showDialog(dialog.getStage());
		if (dir == null || !dir.canWrite())
			return null;
		else
			return dir;
	}


	private static List<String> getLabels(String[] inputFiles, AnalyzeGenomesDialog.TaxonIdentification taxonIdentification) {
		final List<String> labels;
		if (taxonIdentification == AnalyzeGenomesDialog.TaxonIdentification.PerFileUsingFileName) {
			labels = Arrays.stream(inputFiles).map(s -> FileUtils.replaceFileSuffix(FileUtils.getFileNameWithoutPath(s), "")).collect(Collectors.toList());
		} else if (taxonIdentification == AnalyzeGenomesDialog.TaxonIdentification.PerFile) {
			labels = new ArrayList<>();

			for (String fileName : inputFiles) {
				final String line = FileUtils.getFirstLineFromFile(new File(fileName));
				if (line != null) {
					labels.add(line.startsWith(">") || line.startsWith("@") ? line.substring(1).trim() : line);
				} else
					labels.add(fileName);
			}
			return labels;
		} else { // perFastARecord
			labels = new ArrayList<>();

			for (String fileName : inputFiles) {
				final String shortName = FileUtils.replaceFileSuffix(FileUtils.getFileNameWithoutPath(fileName), "");
				try (IFastAIterator it = FastAFileIterator.getFastAOrFastQAsFastAIterator(fileName)) {
					int count = 0;
					while (it.hasNext()) {
						count++;
						final String line = it.next().getFirst();
						if (taxonIdentification == AnalyzeGenomesDialog.TaxonIdentification.PerFastARecord)
							labels.add(line.startsWith(">") || line.startsWith("@") ? line.substring(1).trim() : line);
						else
							labels.add(shortName + ":" + count);
						if (labels.size() > 1000)
							throw new IOException("Too many taxon labels (>1000)");

					}
				} catch (IOException e) {
					NotificationManager.showError("Processing failed: " + e);
					return null;
				}
			}
		}
		return labels;
	}
}
