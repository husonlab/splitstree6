/*
 *  SplitsTree6.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.main;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.stage.Stage;
import jloda.fx.util.ArgsOptions;
import jloda.fx.util.Icebergs;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.ResourceManagerFX;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.fx.window.SplashScreen;
import jloda.fx.window.WindowGeometry;
import jloda.phylo.PhyloTree;
import jloda.phylogeny.dolayout.SimulatedAnnealingMinLA;
import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgramExecutorService;
import jloda.util.UsageException;
import splitstree6.io.FileLoader;
import splitstree6.view.trees.tanglegram.DoTanglegram;
import splitstree6.window.MainWindow;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;

/**
 * the main SplitsTree app program
 * Daniel Huson, 2020
 */
public class SplitsTree6 extends Application {
	public static boolean nodeZoomOnMouseOver = false;

	public static boolean setMinWidthHeightToZero = true;

	private static final ArrayList<String> inputFiles = new ArrayList<>();

	private static boolean allowExperimental = false;
	public static boolean allowRazorNet = false;

	private static boolean showSplash;

	/**
	 * main
	 *
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		ProgramProperties.setUseGUI(true);
		Basic.restoreSystemOut(System.err); // send system out to system err
		Basic.startCollectionStdErr();

		ResourceManagerFX.addResourceRoot(SplitsTree6.class, "splitstree6.resources");
		ProgramProperties.getProgramIconsFX().setAll(ResourceManagerFX.getIcons("SplitsTree6-16.png", "SplitsTree6-32.png", "SplitsTree6-48.png", "SplitsTree6-64.png", "SplitsTree6-128.png"));

		ProgramProperties.setProgramName(Version.NAME);
		ProgramProperties.setProgramVersion(Version.SHORT_DESCRIPTION);

		ProgramProperties.setProgramLicence("""
				Copyright (C) 2026. This program comes with ABSOLUTELY NO WARRANTY.
				This is free software, licensed under the terms of the GNU General Public License, Version 3.
				Sources available at: https://github.com/husonlab/splitstree6
				""");

		//CheckForUpdate.programURL = "https://software-ab.cs.uni-tuebingen.de/download/alora";
		//CheckForUpdate.applicationId = "1691242391";

		SplashScreen.setLabelAnchor(new Point2D(40, 14));
		SplashScreen.setFitHeight(250);
		SplashScreen.setVersionString(ProgramProperties.getProgramVersion());
		SplashScreen.setImageResourceName("SplitsTree6-splash.png");

		try {
			parseArguments(args);
		} catch (Throwable th) {
			//catch any exceptions and the like that propagate up to the top level
			if (!th.getMessage().startsWith("Help")) {
				System.err.println("Fatal error:" + "\n" + th.getMessage());
				Basic.caught(th);
			}
			System.exit(1);
		}

		launch(args);
	}

	protected static void parseArguments(String[] args) throws CanceledException, UsageException {
		var options = new ArgsOptions(args, SplitsTree6.class, Version.NAME + " - Phylogenetic analysis using trees and networks");
		options.setAuthors("Daniel H. Huson and David Bryant");
		options.setLicense(ProgramProperties.getProgramLicence());
		options.setVersion(ProgramProperties.getProgramVersion());

		options.comment("Input:");
		inputFiles.addAll(options.getOption("-i", "input", "Input files to open upon startup", new ArrayList<String>()));

		final String defaultPropertiesFile;
		if (ProgramProperties.isMacOS())
			defaultPropertiesFile = System.getProperty("user.home") + "/Library/Preferences/SplitsTree6.def";
		else
			defaultPropertiesFile = System.getProperty("user.home") + File.separator + ".SplitsTree6.def";

		final var propertiesFile = options.getOption("-p", "propertiesFile", "Properties file", defaultPropertiesFile);
		showSplash = !options.getOption("-s", "hideSplash", "Hide startup splash screen", false);
		final var silentMode = options.getOption("-S", "silentMode", "Silent mode", false);
		Basic.setDebugMode(options.getOption("-d", "debug", "Debug mode", false));
		ProgramExecutorService.setNumberOfCoresToUse(options.getOption("-t", "threads", "Maximum number of threads to use in a parallel algorithm (0=all available)", 0));
		ProgramProperties.setConfirmQuit(options.getOption("-q", "confirmQuit", "Confirm quit on exit", ProgramProperties.isConfirmQuit()));

		allowExperimental = options.getOption("-x", "experimental", "Enable experimental code", false);

		options.done();
		System.err.println("Java version: " + System.getProperty("java.version"));

		ProgramProperties.load(propertiesFile);

		SimulatedAnnealingMinLA.DEFAULT_START_TEMPERATURE = ProgramProperties.get("SA_DEFAULT_START_TEMPERATURE", SimulatedAnnealingMinLA.DEFAULT_START_TEMPERATURE);
		SimulatedAnnealingMinLA.DEFAULT_END_TEMPERATURE = ProgramProperties.get("SA_DEFAULT_END_TEMPERATURE", SimulatedAnnealingMinLA.DEFAULT_END_TEMPERATURE);
		SimulatedAnnealingMinLA.DEFAULT_ITERATIONS_PER_TEMPERATURE = ProgramProperties.get("SA_DEFAULT_ITERATIONS_PER_TEMPERATURE", SimulatedAnnealingMinLA.DEFAULT_ITERATIONS_PER_TEMPERATURE);
		SimulatedAnnealingMinLA.DEFAULT_COOLING_RATE = ProgramProperties.get("SA_DEFAULT_COOLING_RATE", SimulatedAnnealingMinLA.DEFAULT_COOLING_RATE);
		DoTanglegram.PARALLEL_JOBS = ProgramProperties.get("TANGLEGRAM_PARALLEL_JOBS", DoTanglegram.PARALLEL_JOBS);

		Icebergs.setEnabled(true);

		if (silentMode) {
			Basic.stopCollectingStdErr();
			Basic.hideSystemErr();
			Basic.hideSystemOut();
		}
	}

	@Override
	public void init() throws Exception {
		PhyloTree.SUPPORT_RICH_NEWICK = true;
	}

	@Override
	public void start(Stage stage) throws Exception {
		try {
			if (showSplash)
				SplashScreen.showSplash(Duration.ofSeconds(5));
			stage.setTitle("Untitled - " + ProgramProperties.getProgramName());
			NotificationManager.setShowNotifications(true);

			final var mainWindow = new MainWindow();
			WindowGeometry.setToStage(stage);
			mainWindow.show(stage, stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
			WindowGeometry.listenToStage(stage);

			MainWindowManager.getInstance().addMainWindow(mainWindow);

			if (!inputFiles.isEmpty()) {
				for (var file : inputFiles)
					Platform.runLater(() -> FileLoader.apply(false, mainWindow, file, e -> NotificationManager.showError("Open file failed: " + e)));
			}

		} catch (Exception ex) {
			Basic.caught(ex);
			throw ex;
		}
	}

	@Override
	public void stop() {
		ProgramProperties.store();
		System.exit(0);
	}

	public static boolean isAllowExperimental() {
		return allowExperimental;
	}
}

