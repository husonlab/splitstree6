/*
 * DensiTreeMain.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.densitree_old;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import jloda.fx.util.ArgsOptions;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.ResourceManagerFX;
import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.UsageException;
import splitstree6.main.SplitsTree6;
import splitstree6.main.Version;

import java.io.File;

@Deprecated
public class DensiTreeMain extends Application {

	public static void main(String[] args) {
		ProgramProperties.setUseGUI(true);
		Basic.restoreSystemOut(System.err); // send system out to system err
		Basic.startCollectionStdErr();

		ResourceManagerFX.addResourceRoot(SplitsTree6.class, "splitstree6.resources");
		ProgramProperties.getProgramIconsFX().setAll(ResourceManagerFX.getIcons("SplitsTree6-16.png", "SplitsTree6-32.png", "SplitsTree6-48.png", "SplitsTree6-64.png", "SplitsTree6-128.png"));

		ProgramProperties.setProgramName(Version.NAME);
		ProgramProperties.setProgramVersion(Version.SHORT_DESCRIPTION);

		ProgramProperties.setProgramLicence("""
				Copyright (C) 2022. This program comes with ABSOLUTELY NO WARRANTY.
				This is free software, licensed under the terms of the GNU General Public License, Version 3.
				Sources available at: https://github.com/husonlab/splitstree6
				""");

		//CheckForUpdate.programURL = "https://software-ab.cs.uni-tuebingen.de/download/alora";
		//CheckForUpdate.applicationId = "1691242391";


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
		final ArgsOptions options = new ArgsOptions(args, DensiTreeMain.class, "Unrooted DensiTree");

		options.comment("Input:");

		final String defaultPropertiesFile;
		if (ProgramProperties.isMacOS())
			defaultPropertiesFile = System.getProperty("user.home") + "/Library/Preferences/DensiTree.def";
		else
			defaultPropertiesFile = System.getProperty("user.home") + File.separator + ".DensiTree.def";
		final String propertiesFile = options.getOption("-p", "propertiesFile", "Properties file", defaultPropertiesFile);
		final boolean showVersion = options.getOption("-V", "version", "Show version string", false);
		final boolean silentMode = options.getOption("-S", "silentMode", "Silent mode", false);
		options.done();

		ProgramProperties.load(propertiesFile);

		if (silentMode) {
			Basic.stopCollectingStdErr();
			Basic.hideSystemErr();
			Basic.hideSystemOut();
		}

		if (showVersion) {
			System.err.println(ProgramProperties.getProgramVersion());
			System.err.println(jloda.util.Version.getVersion(SplitsTree6.class, ProgramProperties.getProgramName()));
			System.err.println("Java version: " + System.getProperty("java.version"));
		}
	}

	@Override
	public void start(Stage stage) {
		var model = new Model();

		var loader = new ExtendedFXMLLoader<DensiTreeMainController>(DensiTreeMainController.class);
		var controller = loader.getController();
		new DensiTreeMainPresenter(stage, controller, model);
		stage.setScene(new Scene(loader.getRoot()));
		stage.sizeToScene();

		stage.show();
	}
}
