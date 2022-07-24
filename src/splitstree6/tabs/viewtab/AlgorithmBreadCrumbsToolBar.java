/*
 * AlgorithmBreadCrumbsToolBar.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.tabs.viewtab;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.concurrent.Worker;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import jloda.fx.control.CopyableLabel;
import jloda.fx.util.ResourceManagerFX;
import jloda.fx.workflow.WorkflowNode;
import jloda.util.PluginClassLoader;
import splitstree6.algorithms.trees.trees2view.ShowTrees;
import splitstree6.tabs.inputeditor.InputEditorTab;
import splitstree6.window.MainWindow;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataTaxaFilter;
import splitstree6.workflow.Workflow;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * algorithms bread crumbs for viewer toolbar
 * Daniel Huson, 1.2018
 */
public class AlgorithmBreadCrumbsToolBar extends ToolBar {
    private static final String shape = "-fx-shape: \"M 0 0 L 5 9 L 0 18 L 100 18 L 105 9 L 100 0 z\";-fx-font-size: 10;"; // arrow getShape and font size
	private static final String computingColor = "-fx-background-color: LIGHTBLUE;";

    private final ArrayList<ChangeListener<Worker.State>> stateChangeListeners = new ArrayList<>();

    private final InvalidationListener invalidationListener;

    private final CopyableLabel infoLabel = new CopyableLabel();

    /**
     * constructor
     */
    public AlgorithmBreadCrumbsToolBar(MainWindow mainWindow, WorkflowNode node) {
        //infoLabel.setFont(Font.font("Courier new", 10));

        invalidationListener = e -> {
            stateChangeListeners.clear();
            final Workflow workflow = mainWindow.getWorkflow();
            getItems().clear();

            var editorTab = (InputEditorTab) mainWindow.getTabByClass(InputEditorTab.class);
            if (editorTab != null) {
                getItems().add(makeInputTabBreadCrumb(mainWindow));
            }

            if (workflow.getInputTaxaFilterNode() != null) {
                getItems().add(makeBreadCrumb(mainWindow, workflow.getInputTaxaFilterNode(), stateChangeListeners));
            }
            if (workflow.getWorkingTaxaNode() != null) {
                var algorithmNodes = getAlgorithmNodesPath(workflow, node);

                for (var aNode : algorithmNodes) {
                    if (!(aNode.getAlgorithm() instanceof DataTaxaFilter)) {
                        getItems().add(makeBreadCrumb(mainWindow, aNode, stateChangeListeners));
                    }
                }
            }
            getItems().addAll(new Separator(Orientation.VERTICAL), new Label("  "), infoLabel);
        };
        //mainWindow.getWorkflow().nodes().addListener(new WeakInvalidationListener(invalidationListener));
        mainWindow.getWorkflow().validProperty().addListener(new WeakInvalidationListener(invalidationListener));
    }

    public List<AlgorithmNode> getAlgorithmNodesPath(Workflow workflow, WorkflowNode node0) {
        // todo: node is not necessarily present in the workflow (don't know why...), but there is an equivalent node with the same id
        var node = workflow.nodeStream().filter(v -> v.getId() == node0.getId()).findAny().orElse(node0);
        for (var v : workflow.nodes()) {
            if (v.getId() == node.getId()) {
                node = v;
                break;
            }
        }

        var list = new LinkedList<AlgorithmNode>();
        while (node != null && workflow.isDerivedNode(node)) {
            if (node instanceof AlgorithmNode algorithmNode) {
                list.add(0, algorithmNode);
            }
            node = node.getPreferredParent();
        }
        return list;
    }

    public CopyableLabel getInfoLabel() {
        return infoLabel;
    }

    private static Node makeBreadCrumb(MainWindow mainWindow, AlgorithmNode algorithmNode, ArrayList<ChangeListener<Worker.State>> stateChangeListeners) {
        final var button = new Button();
        button.setStyle(shape);
        button.textProperty().bind(algorithmNode.titleProperty());

        button.disableProperty().bind(algorithmNode.validProperty().not());
        final var tooltip = new Tooltip();
        tooltip.textProperty().bind(algorithmNode.shortDescriptionProperty());
        button.setTooltip(tooltip);

        final var imageView = ResourceManagerFX.getIconAsImageView(algorithmNode.getName().endsWith("Filter") ? "Filter16.gif" : "Algorithm16.gif", 16);
        button.setGraphic(imageView);

        final Runnable showTab = () -> mainWindow.getAlgorithmTabsManager().showTab(algorithmNode, true);

        button.setOnAction(e -> showTab.run());

        if (algorithmNode.getAlgorithm() instanceof ShowTrees showTrees) {
            button.setOnContextMenuRequested(e -> createViewChoiceMenu(mainWindow.getWorkflow(), algorithmNode, showTrees).show(button, e.getScreenX(), e.getScreenY()));
        } else if (mainWindow.getWorkflow().isDerivedNode(algorithmNode)) {
            button.setOnContextMenuRequested(e -> createViewChoiceMenu(mainWindow.getWorkflow(), algorithmNode, showTab).show(button, e.getScreenX(), e.getScreenY()));
        }

        final ChangeListener<Worker.State> stateChangeListener = (c, o, n) -> {
            switch (n) {
                case RUNNING -> {
                    button.setTextFill(Color.BLACK);
                    button.setStyle(shape + computingColor);
                }
                case FAILED -> {
                    button.setTextFill(Color.DARKRED);
                    button.setStyle(shape);
                }
                default -> {
                    button.setTextFill(Color.BLACK);
                    button.setStyle(shape);
                }
            }
        };
        algorithmNode.getService().stateProperty().addListener(new WeakChangeListener<>(stateChangeListener));
        stateChangeListeners.add(stateChangeListener);

        return button;
    }

    private static Node makeInputTabBreadCrumb(MainWindow mainWindow) {
        final var button = new Button();
        button.setStyle(shape);
        button.setText("Input");
        button.disableProperty().bind(mainWindow.getWorkflow().runningProperty());
        final var tooltip = new Tooltip("Input editor");
        button.setTooltip(tooltip);
        button.setOnAction((e) -> {
            var editorTab = (InputEditorTab) mainWindow.getTabByClass(InputEditorTab.class);
            mainWindow.getController().getMainTabPane().getSelectionModel().select(editorTab);
        });
        return button;
    }

    public static ContextMenu createViewChoiceMenu(Workflow workflow, AlgorithmNode algorithmNode, ShowTrees showTrees) {
        var menu = new ContextMenu();
        for (var viewType : ShowTrees.ViewType.values()) {
            var menuItem = new MenuItem(viewType.name());
            menuItem.setOnAction(e -> {
                if (!workflow.isRunning()) {
                    showTrees.setOptionView(viewType);
                    algorithmNode.restart();
                }
            });
            menu.getItems().add(menuItem);
        }
        return menu;
    }

    public static ContextMenu createViewChoiceMenu(Workflow workflow, AlgorithmNode algorithmNode0, Runnable runlater) {
        var menu = new ContextMenu();
        for (var algorithm : PluginClassLoader.getInstances(Algorithm.class, "splitstree6.algorithms")) {
            if (algorithm.getFromClass() == algorithmNode0.getAlgorithm().getFromClass()
                && algorithm.getToClass() == algorithmNode0.getAlgorithm().getToClass() && !(algorithm instanceof DataTaxaFilter)) {
                var menuItem = new MenuItem(algorithm.getName());
                menuItem.setOnAction(e -> {
                    var algorithmNode = (AlgorithmNode) workflow.nodeStream().filter(v -> v.getId() == algorithmNode0.getId()).findAny().orElse(algorithmNode0);
                    algorithmNode.setAlgorithm(algorithm);
                    algorithmNode.setTitle(algorithm.getName());
                    algorithmNode.restart();
                    if (runlater != null)
                        Platform.runLater(runlater);
                });
                menu.getItems().add(menuItem);
                menuItem.setDisable(!algorithm.isApplicable(algorithmNode0.getTaxaBlock(), algorithmNode0.getSourceBlock()));
            }
        }
        return menu;
    }

}
