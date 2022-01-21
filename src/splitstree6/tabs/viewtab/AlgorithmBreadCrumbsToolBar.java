/*
 *  AlgorithmBreadCrumbsToolBar.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.tabs.viewtab;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.concurrent.Worker;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.tabs.inputeditor.InputEditorTab;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.Workflow;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * algorithms bread crumbs for viewer toolbar
 * Daniel Huson, 1.2018
 */
public class AlgorithmBreadCrumbsToolBar extends ToolBar {
    private static final String shape = "-fx-shape: \"M 0 0 L 5 9 L 0 18 L 100 18 L 105 9 L 100 0 z\";-fx-font-size: 10;"; // arrow shape and font size
    private static final String computingColor = "-fx-background-color: LIGHTBLUE;";

    private final ArrayList<ChangeListener<Worker.State>> stateChangeListeners = new ArrayList<>();

    private final InvalidationListener invalidationListener;

    /**
     * constructor
     *
     * @param mainWindow
     * @param node
     */
    public AlgorithmBreadCrumbsToolBar(MainWindow mainWindow, WorkflowNode node) {
        invalidationListener = e -> {
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
                    getItems().add(makeBreadCrumb(mainWindow, aNode, stateChangeListeners));
                }
            }
        };
        //mainWindow.getWorkflow().nodes().addListener(new WeakInvalidationListener(invalidationListener));
        mainWindow.getWorkflow().validProperty().addListener(new WeakInvalidationListener(invalidationListener));
    }

    public List<AlgorithmNode> getAlgorithmNodesPath(Workflow workflow, WorkflowNode node) {
        var list = new LinkedList<AlgorithmNode>();
        while (node != null && workflow.isDerivedNode(node)) {
            if (node instanceof AlgorithmNode algorithmNode) {
                list.add(0, algorithmNode);
            }
            node = node.getPreferredParent();
        }
        return list;
    }

    private static Node makeBreadCrumb(MainWindow mainWindow, AlgorithmNode algorithmNode, ArrayList<ChangeListener<Worker.State>> stateChangeListeners) {
        final var button = new Button();
        button.setStyle(shape);
        button.textProperty().bind(algorithmNode.nameProperty());
        button.disableProperty().bind(algorithmNode.validProperty().not());
        final var tooltip = new Tooltip();
        tooltip.textProperty().bind(algorithmNode.shortDescriptionProperty());
        button.setTooltip(tooltip);

        final ChangeListener<Worker.State> stateChangeListener = (c, o, n) -> {
            switch (n) {
                case RUNNING:
                    button.setTextFill(Color.BLACK);
                    button.setStyle(shape + computingColor);
                    break;
                case FAILED:
                    button.setTextFill(Color.DARKRED);
                    button.setStyle(shape);
                    break;
                default:
                    button.setTextFill(Color.BLACK);
                    button.setStyle(shape);
            }
        };
        algorithmNode.getService().stateProperty().addListener(new WeakChangeListener<>(stateChangeListener));
        stateChangeListeners.add(stateChangeListener);

        button.setOnAction((e) -> {
            mainWindow.getAlgorithmTabsManager().showTab(algorithmNode, true);
        });
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

}
