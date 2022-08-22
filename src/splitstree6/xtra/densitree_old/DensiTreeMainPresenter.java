/*
 * DensiTreeMainPresenter.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.util.*;
import jloda.util.ProgramProperties;
import splitstree6.io.readers.trees.NewickReader;

import java.io.File;
import java.io.IOException;

/**
 * the presenter
 */
@Deprecated
public class DensiTreeMainPresenter {

    private final DoubleProperty scalingFactor = new SimpleDoubleProperty(1);

    public DensiTreeMainPresenter(Stage stage, DensiTreeMainController controller, Model model) {
        stage.setMinWidth(400);
        stage.setMinHeight(400);
        controller.getLabelPane().prefWidthProperty().bind(stage.widthProperty().subtract(100));
        controller.getHighlightingPane().prefWidthProperty().bind(stage.widthProperty().subtract(100));
        controller.getConsensusPane().prefWidthProperty().bind(stage.widthProperty().subtract(100));
        controller.getCanvas().widthProperty().bind(controller.getLabelPane().widthProperty());
        controller.getLabelPane().prefHeightProperty().bind(stage.heightProperty().subtract(200));
        controller.getHighlightingPane().prefHeightProperty().bind(stage.heightProperty().subtract(100));
        controller.getConsensusPane().prefHeightProperty().bind(stage.heightProperty().subtract(100));
        controller.getCanvas().heightProperty().bind(controller.getLabelPane().heightProperty());

        if (false) {
            BasicFX.reportChanges("stageWidth", stage.widthProperty());
            BasicFX.reportChanges("stageHeight", stage.heightProperty());
            BasicFX.reportChanges("canvasWidth", controller.getCanvas().widthProperty());
            BasicFX.reportChanges("canvasHeight", controller.getCanvas().heightProperty());
            BasicFX.reportChanges("paneWidth", controller.getLabelPane().widthProperty());
            BasicFX.reportChanges("paneHeight", controller.getLabelPane().heightProperty());
        }

        controller.getDrawingGroup().selectToggle(controller.getCircularMenuItem());
        controller.getLabelsGroup().selectToggle(controller.getMeanMenuItem());

        controller.getMessageLabel().setText("");

        controller.getOpenMenuItem().setOnAction(e -> {
            final var previousDir = new File(ProgramProperties.get("InputDir", ""));
            final var fileChooser = new FileChooser();
            if (previousDir.isDirectory())
                fileChooser.setInitialDirectory(previousDir);
            fileChooser.setTitle("Open input file");
            fileChooser.getExtensionFilters().addAll(Utilities.getExtensionFilter());
            final var selectedFile = fileChooser.showOpenDialog(stage);
            if (selectedFile != null) {
                stage.setTitle(selectedFile.getName());
                if (selectedFile.getParentFile().isDirectory())
                    ProgramProperties.put("InputDir", selectedFile.getParent());
                var service = new AService<Integer>(controller.getBottomFlowPane());
                service.setCallable(() -> {
                    var newickReader = new NewickReader();
                    newickReader.read(service.getProgressListener(), selectedFile.getPath(), model.getTaxaBlock(), model.getTreesBlock());
                    if (model.getTreesBlock().isPartial())
                        throw new IOException("Partial trees not acceptable");
                    model.setCircularOrdering(Utilities.computeCycle(model.getTaxaBlock().getTaxaSet(), model.getTreesBlock()));
                    return model.getTreesBlock().getNTrees();
                });
                service.setOnScheduled(a -> model.clear());
                service.setOnSucceeded(a -> controller.getMessageLabel().setText(String.format("Trees: %,d", service.getValue())));
                service.setOnFailed(a -> controller.getMessageLabel().setText("Failed: " + service.getException()));
                service.start();
            }
        });

        final String[] specTrees = new String[1];

        controller.getSpecificTreesMenuItem().setOnAction(e -> {
            var dialog = new TextInputDialog();
            dialog.setTitle("Highlight specific Trees");
            dialog.setHeaderText("Enter the numbers of the trees you want to be highlighted, trees start at 1.\nE.g.: 1,2,3");
            dialog.setContentText("Trees:");

            dialog.showAndWait().ifPresent(trees -> {
                specTrees[0] = trees;
                DensiTree.drawHighlightedTrees(model, controller.getConsensusPane(),
                        new DensiTree.Parameters(controller.getJitterCheckBox().isSelected(),
                                controller.getConsensusMenuItem().isSelected(), specTrees[0],
                                controller.getLabelsGroup().getSelectedToggle().toString(),
                                controller.getDrawingGroup().getSelectedToggle().toString()), controller.getCanvas(), scalingFactor);
            });
        });

        controller.getPrintMenuItem().setOnAction(e -> {
            Print.print(stage, PrintUtils.createImage(controller.getStackPane(), null));
        });

        controller.getQuitMenuItem().setOnAction(e -> {
            var alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm Quit");
            alert.setHeaderText("Closing open document");
            alert.setContentText("Do you really want to quit?");

            final ButtonType buttonTypeCancel = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
            final ButtonType buttonTypeYes = new ButtonType("Yes", ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().setAll(buttonTypeCancel, buttonTypeYes);

            if (alert.showAndWait().orElse(null) == buttonTypeYes) {
                stage.close();
            } else {
                e.consume();
            }
        });

        controller.getCopyMenuItem().setOnAction(e -> {
            final var snapshot = PrintUtils.createImage(controller.getStackPane(), null).snapshot(null, null);
            final var clipboardContent = new ClipboardContent();
            clipboardContent.putImage(snapshot);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        });

        stage.setOnCloseRequest(e -> {
            var alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm Quit");
            alert.setHeaderText("Closing open document");
            alert.setContentText("Do you really want to quit?");

            final ButtonType buttonTypeCancel = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
            final ButtonType buttonTypeYes = new ButtonType("Yes", ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().setAll(buttonTypeCancel, buttonTypeYes);

            if (alert.showAndWait().orElse(null) == buttonTypeYes) {
                stage.close();
            } else {
                e.consume();
            }
        });

        final InvalidationListener scaleListener = observable -> {
            var value = controller.getScaleSlider().getValue();
            if (value >= 0) {
                scalingFactor.set(Math.pow(2, value));
            } else {
                scalingFactor.set(1.0 / Math.pow(2, -value));
            }
        };

        controller.getScaleSlider().valueProperty().addListener(scaleListener);


        final InvalidationListener listener;

        if (false) {
            listener = observable -> {
                try {
                    DensiTree.draw(
                            new DensiTree.Parameters(controller.getJitterCheckBox().isSelected(),
                                    controller.getConsensusMenuItem().isSelected(), specTrees[0],
                                    controller.getLabelsGroup().getSelectedToggle().toString(),
                                    controller.getDrawingGroup().getSelectedToggle().toString()),
                            model, controller.getCanvas(), controller.getLabelPane(), controller.getConsensusPane(), controller.getHighlightingPane(), scalingFactor);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };
        } else { // DHH: only draw once user has stopped using controls
            listener = observable -> RunAfterAWhile.apply(controller, () ->
                    Platform.runLater(() -> {
                        try {
                            DensiTree.draw(
                                    new DensiTree.Parameters(controller.getJitterCheckBox().isSelected(),
                                            controller.getConsensusMenuItem().isSelected(), specTrees[0],
                                            controller.getLabelsGroup().getSelectedToggle().toString(),
                                            controller.getDrawingGroup().getSelectedToggle().toString()),
                                    model, controller.getCanvas(), controller.getLabelPane(), controller.getConsensusPane(), controller.getHighlightingPane(), scalingFactor);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }));
        }

        final InvalidationListener consensusListener = observable -> {
            try {
                DensiTree.drawConsensus(model, controller.getConsensusPane(),
                        new DensiTree.Parameters(controller.getJitterCheckBox().isSelected(),
                                controller.getConsensusMenuItem().isSelected(), specTrees[0],
                                controller.getLabelsGroup().getSelectedToggle().toString(),
                                controller.getDrawingGroup().getSelectedToggle().toString()), controller.getCanvas(), scalingFactor);
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        controller.getStackPane().widthProperty().addListener(listener);
        controller.getStackPane().heightProperty().addListener(listener);
        controller.getJitterCheckBox().selectedProperty().addListener(listener);
        controller.getConsensusMenuItem().selectedProperty().addListener(consensusListener);
        controller.getDrawingGroup().selectedToggleProperty().addListener(listener);
        controller.getLabelsGroup().selectedToggleProperty().addListener(listener);
        controller.getScaleSlider().valueProperty().addListener(listener);

        controller.getDrawButton().setOnAction(e -> {
            var parameters = new DensiTree.Parameters(controller.getJitterCheckBox().isSelected(),
                    controller.getConsensusMenuItem().isSelected(), specTrees[0],
                    controller.getLabelsGroup().getSelectedToggle().toString(),
                    controller.getDrawingGroup().getSelectedToggle().toString());
            try {
                DensiTree.draw(parameters, model, controller.getCanvas(), controller.getLabelPane(), controller.getConsensusPane(), controller.getHighlightingPane(), scalingFactor);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        controller.getDrawButton().disableProperty().bind(Bindings.isEmpty(model.getTreesBlock().getTrees()));

        controller.getClearButton().setOnAction(e -> {
            specTrees[0] = null;
            controller.getScaleSlider().valueProperty().removeListener(scaleListener);
            controller.getScaleSlider().valueProperty().removeListener(listener);
            scalingFactor.set(1);
            controller.getScaleSlider().setValue(0);
            controller.getScaleSlider().valueProperty().addListener(scaleListener);
            controller.getScaleSlider().valueProperty().addListener(listener);
            DensiTree.clear(controller.getCanvas(), controller.getLabelPane(), controller.getConsensusPane(), controller.getHighlightingPane());
        });
    }
}
