/*
 *  AnalyzeGenomesDialog.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Stage;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.ProgramProperties;
import jloda.util.ProgramExecutorService;
import splitstree6.main.Version;

import java.util.Map;

/**
 * import genomes dialog
 * Daniel Huson, 2.2020
 */
public class AnalyzeGenomesDialog {
	public enum SequenceType {DNA, Protein}

	public enum TaxonIdentification {PerFastARecord, PerFastARecordUsingFileName, PerFile, PerFileUsingFileName}

	private final Stage stage;
	private final AnalyzeGenomesController controller;
	private final AnalyzeGenomesPresenter presenter;

	private final ObjectProperty<AccessReferenceDatabase> referencesDatabase = new SimpleObjectProperty<>(this, "referencesDatabase", null);
	private final ObservableList<Integer> referenceIds = FXCollections.observableArrayList();
	private final ObservableList<Map.Entry<Integer, Double>> references = FXCollections.observableArrayList();

	private final BooleanProperty running = new SimpleBooleanProperty(this, "running", false);

	private final StringProperty optionInputDirectory = new SimpleStringProperty(this, "optionInputDirectory");
	private final ObjectProperty<SequenceType> optionSequenceType = new SimpleObjectProperty<>(this, "optionSequenceType");
	private final BooleanProperty optionStoreOnlyReferences = new SimpleBooleanProperty(this, "optionStoreOnlyReferences");
	private final ObjectProperty<TaxonIdentification> optionTaxonIdentification = new SimpleObjectProperty<>(this, "TaxonIdentification");
	private final StringProperty optionReferenceDatabaseFile = new SimpleStringProperty(this, "optionReferenceDatabaseFile");
	private final StringProperty optionFileCacheDirectory = new SimpleStringProperty(this, "optionFileCacheDirectory");
	private final IntegerProperty optionMinLength = new SimpleIntegerProperty(this, "optionMinLength");
	private final DoubleProperty optionMaxDistanceToSearch = new SimpleDoubleProperty(this, "optionMaxDistanceToSearch");
	private final BooleanProperty optionIncludeStrains = new SimpleBooleanProperty(this, "optionIncludeStrains");

	{
		ProgramProperties.track(optionInputDirectory, "");
		ProgramProperties.track(optionSequenceType, SequenceType::valueOf, SequenceType.DNA);
		ProgramProperties.track(optionStoreOnlyReferences, true);
		ProgramProperties.track(optionTaxonIdentification, TaxonIdentification::valueOf, TaxonIdentification.PerFastARecordUsingFileName);
		ProgramProperties.track(optionReferenceDatabaseFile, "");
		ProgramProperties.track(optionFileCacheDirectory, "");
		ProgramProperties.track(optionMinLength, 10000);
		ProgramProperties.track(optionMaxDistanceToSearch, 0.4);
		ProgramProperties.track(optionIncludeStrains, false);
	}

	/**
	 * constructor
	 */
	public AnalyzeGenomesDialog(Stage initialParent) {
		var extendedFXMLLoader = new ExtendedFXMLLoader<AnalyzeGenomesController>(this.getClass());
		controller = extendedFXMLLoader.getController();

		stage = new Stage();
		stage.setTitle("Analyze Genomes - " + Version.NAME);
		stage.getIcons().setAll(ProgramProperties.getProgramIconsFX());

		stage.setScene(new Scene(extendedFXMLLoader.getRoot()));
		stage.setX(initialParent.getX() + 100);
		stage.setY(initialParent.getY() + 100);

		presenter = new AnalyzeGenomesPresenter(this);
	}

	public void show() {
		stage.show();
		ProgramExecutorService.submit(500, () -> Platform.runLater(stage::toFront));
	}

	public Stage getStage() {
		return stage;
	}

	public AnalyzeGenomesController getController() {
		return controller;
	}

	public ObservableList<Map.Entry<Integer, Double>> getReferences() {
		return references;
	}

	public void clearReferences() {
		references.clear();
		referenceIds.clear();
		controller.getMashDistancesChart().getData().clear();
	}

	public AccessReferenceDatabase getReferencesDatabase() {
		return referencesDatabase.get();
	}

	public ObjectProperty<AccessReferenceDatabase> referencesDatabaseProperty() {
		return referencesDatabase;
	}

	public ObservableList<Integer> getReferenceIds() {
		return referenceIds;
	}

	public boolean isRunning() {
		return running.get();
	}

	public BooleanProperty runningProperty() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running.set(running);
	}

	public String getOptionInputDirectory() {
		return optionInputDirectory.get();
	}

	public void setOptionInputDirectory(String optionInputDirectory) {
		this.optionInputDirectory.set(optionInputDirectory);
	}

	public SequenceType getOptionSequenceType() {
		return optionSequenceType.get();
	}

	public ObjectProperty<SequenceType> optionSequenceTypeProperty() {
		return optionSequenceType;
	}

	public void setOptionSequenceType(SequenceType optionSequenceTypeType) {
		this.optionSequenceType.set(optionSequenceTypeType);
	}

	public TaxonIdentification getOptionTaxonIdentification() {
		return optionTaxonIdentification.get();
	}

	public ObjectProperty<TaxonIdentification> optionTaxonIdentificationProperty() {
		return optionTaxonIdentification;
	}

	public void setOptionTaxonIdentification(TaxonIdentification optionTaxonIdentification) {
		this.optionTaxonIdentification.set(optionTaxonIdentification);
	}

	public int getOptionMinLength() {
		return optionMinLength.get();
	}

	public IntegerProperty optionMinLengthProperty() {
		return optionMinLength;
	}

	public void setOptionMinLength(int optionMinLength) {
		this.optionMinLength.set(optionMinLength);
	}

	public double getOptionMaxDistanceToSearch() {
		return optionMaxDistanceToSearch.get();
	}

	public DoubleProperty optionMaxDistanceToSearchProperty() {
		return optionMaxDistanceToSearch;
	}

	public void setOptionMaxDistanceToSearch(double optionMaxDistanceToSearch) {
		this.optionMaxDistanceToSearch.set(optionMaxDistanceToSearch);
	}

	public boolean isOptionIncludeStrains() {
		return optionIncludeStrains.get();
	}

	public BooleanProperty optionIncludeStrainsProperty() {
		return optionIncludeStrains;
	}

	public void setOptionIncludeStrains(boolean optionIncludeStrains) {
		this.optionIncludeStrains.set(optionIncludeStrains);
	}

	public boolean isOptionStoreOnlyReferences() {
		return optionStoreOnlyReferences.get();
	}

	public BooleanProperty optionStoreOnlyReferencesProperty() {
		return optionStoreOnlyReferences;
	}

	public void setOptionStoreOnlyReferences(boolean optionStoreOnlyReferences) {
		this.optionStoreOnlyReferences.set(optionStoreOnlyReferences);
	}

	public String getOptionReferenceDatabaseFile() {
		return optionReferenceDatabaseFile.get();
	}

	public StringProperty optionReferenceDatabaseFileProperty() {
		return optionReferenceDatabaseFile;
	}

	public void setOptionReferenceDatabaseFile(String optionReferenceDatabaseFile) {
		this.optionReferenceDatabaseFile.set(optionReferenceDatabaseFile);
	}

	public String getOptionFileCacheDirectory() {
		return optionFileCacheDirectory.get();
	}

	public StringProperty optionFileCacheDirectoryProperty() {
		return optionFileCacheDirectory;
	}

	public void setOptionFileCacheDirectory(String optionFileCacheDirectory) {
		this.optionFileCacheDirectory.set(optionFileCacheDirectory);
	}
}
