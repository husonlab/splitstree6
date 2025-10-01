/*
 * ImportApply.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.dialog.importdialog;

import jloda.fx.window.NotificationManager;
import splitstree6.data.DistancesBlock;
import splitstree6.io.nexus.workflow.WorkflowNexusInput;
import splitstree6.io.utils.SimilaritiesToDistances;
import splitstree6.window.MainWindow;
import splitstree6.workflow.WorkflowSetup;

import java.util.function.Consumer;

public class ImportApply {
	public static void parseAndLoad(MainWindow mainWindow, String fileName, ImportDialogController controller) {
		var dataType = controller.getDataTypeComboBox().getValue();
		try {
			final Consumer<Throwable> failedHandler = ex -> {
				mainWindow.getWorkflow().clear();
				NotificationManager.showError("Import failed: " + ex.getMessage());
			};
			final Runnable runOnSuccess = () -> {
				if (dataType.equals(DistancesBlock.class)) {
					if (controller.getSimilarityValues().isSelected()) {
						var method = controller.getSimilarityToDistanceMethod().getValue();
						if (method != null) {
							if (mainWindow.getWorkflow().getInputDataBlock() instanceof DistancesBlock distancesBlock) {
								SimilaritiesToDistances.apply(method, distancesBlock);
							}
							if (mainWindow.getWorkflow().getWorkingDataBlock() instanceof DistancesBlock distancesBlock) {
								SimilaritiesToDistances.apply(method, distancesBlock);
							}
						}
					}
				}
				mainWindow.getPresenter().getSplitPanePresenter().ensureTreeViewIsOpen(false);
				mainWindow.setFileName(fileName);
				mainWindow.setDirty(true);
			};
			if (WorkflowNexusInput.isApplicable(fileName)) {
				WorkflowNexusInput.open(mainWindow, fileName, failedHandler, runOnSuccess);
			} else
				WorkflowSetup.apply(fileName, mainWindow.getWorkflow(), failedHandler, runOnSuccess, dataType);

		} catch (Exception ex) {

			NotificationManager.showError("Import failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
	}
}
