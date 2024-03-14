package splitstree6.xtra.mapview.nodes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import splitstree6.xtra.mapview.ColorSchemes;
import splitstree6.xtra.mapview.MapViewController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The LegendView class represents a legend view component in a graphical user interface.
 * It displays a legend for categorizing data with corresponding colors.
 * It provides additional functionality to make the pie chart draggable within a view.
 * Nikolas Kreisz 1.2024
 */
public class LegendView extends VBox {

    private Map<String, String> categoryColors;
    private ArrayList<String> taxa;
    boolean large_legend = false;
    private double xOffset = 0;
    private double yOffset = 0;

    /**
     * Constructs a LegendView with the specified list of taxa, color scheme, and controller.
     *
     * @param taxa         The list of taxa for which the legend is created.
     * @param colorScheme  The color scheme used for the legend.
     * @param controller   The controller associated with the legend.
     */
    public LegendView(ArrayList<String> taxa, String colorScheme, MapViewController controller) {
        this.categoryColors = buildColors(taxa, colorScheme);
        this.taxa = taxa;
        createLegend();
        setPickOnBounds(controller.getCheckBoxLegend().isSelected());
        setStyle("-fx-background-color: white;");
        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);
    }
    /**
     * Updates the colors of the legend based on the specified color scheme.
     *
     * @param scheme The new color scheme for the legend.
     */
    public void updateColors(String scheme){
        var posX = this.getLayoutX();
        var posY = this.getLayoutY();
        getChildren().clear();
        categoryColors = buildColors(taxa, scheme);
        createLegend();
        this.setLayoutX(posX);
        this.setLayoutY(posY);
    }
    /**
     * Builds a map of category names and their associated colors based on the given taxa and color scheme.
     *
     * @param taxa        The list of taxa.
     * @param colorScheme The color scheme.
     * @return A map containing category names and their associated colors.
     */
    private Map<String, String> buildColors(ArrayList<String> taxa, String colorScheme) {
        Map<Integer, String> colors = ColorSchemes.getScheme(colorScheme);
        Map<String, String> categoryColors = new HashMap<>();
        for (int i = 0; i < taxa.size(); i++) {
            categoryColors.put(taxa.get(i), colors.get(i));
        }
        return categoryColors;
    }
    /**
     * Creates the legend based on the category names and their associated colors.
     */
    private void createLegend() {
        if(taxa.size() > 20)large_legend = true;
        boolean second_row = false;
        HBox hBox = new HBox();
        for (Map.Entry<String, String> entry : categoryColors.entrySet()) {
            String category = entry.getKey();
            Color color = Color.web(entry.getValue());
            HBox legendEntry = createLegendEntry(category, color);
            if(large_legend && !second_row){
                hBox.getChildren().add(legendEntry);
                second_row = true;
            } else if (large_legend && second_row){
                hBox.getChildren().add(legendEntry);
                getChildren().add(hBox);
                hBox = new HBox();
                second_row = false;
            } else getChildren().add(legendEntry);
            setMaxSize(Pane.USE_PREF_SIZE, Pane.USE_PREF_SIZE);
        }
        getChildren().add(hBox);
        setSpacing(5);
        setAlignment(Pos.CENTER_LEFT);
    }

    /**
     * Creates an entry for the legend with the specified category name and color.
     * The entry is scaled based on the number of haplotypes.
     *
     * @param category The category name.
     * @param color    The color associated with the category.
     * @return An HBox containing the legend entry.
     */
    private HBox createLegendEntry(String category, Color color) {
        double size = 1;
        if(large_legend) size = 2;
        Rectangle colorBox = new Rectangle(20/size, 16/size);
        colorBox.setFill(color);
        Label categoryLabel = new Label(category);
        HBox legendEntry = new HBox(10/size, colorBox, categoryLabel);
        legendEntry.setPadding(new Insets(10/size));
        legendEntry.setAlignment(Pos.CENTER_LEFT);
        return legendEntry;
    }
    /**
     * Handles the mouse pressed event for dragging the legend.
     *
     * @param event The mouse event.
     */
    private void onMousePressed(MouseEvent event) {
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }
    /**
     * Handles the mouse dragged event for dragging the legend.
     *
     * @param event The mouse event.
     */
    private void onMouseDragged(MouseEvent event) {
        double deltaX = event.getSceneX() - xOffset;
        double deltaY = event.getSceneY() - yOffset;
        setTranslateX(getTranslateX() + deltaX);
        setTranslateY(getTranslateY() + deltaY);
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }
}
