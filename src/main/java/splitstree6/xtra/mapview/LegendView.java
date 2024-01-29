package splitstree6.xtra.mapview;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.HashMap;
import java.util.Map;

public class LegendView extends VBox {

    private Map<String, Color> categoryColors;
    private double xOffset = 0;
    private double yOffset = 0;

    public LegendView(Map<String, Color> categoryColors) {
        this.categoryColors = categoryColors;
        createLegend();
        setStyle("-fx-background-color: white;");
        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);

    }


    private void createLegend() {
        for (Map.Entry<String, Color> entry : categoryColors.entrySet()) {
            String category = entry.getKey();
            Color color = entry.getValue();

            HBox legendEntry = createLegendEntry(category, color);
            getChildren().add(legendEntry);
            setMaxSize(Pane.USE_PREF_SIZE, Pane.USE_PREF_SIZE);
        }

        setSpacing(5);
        setAlignment(Pos.CENTER_LEFT);
    }

    private HBox createLegendEntry(String category, Color color) {
        Rectangle colorBox = new Rectangle(20, 15);
        colorBox.setFill(color);


        Label categoryLabel = new Label(category);

        HBox legendEntry = new HBox(10, colorBox, categoryLabel);
        legendEntry.setPadding(new Insets(10));
        legendEntry.setAlignment(Pos.CENTER_LEFT);

        return legendEntry;
    }
    private void onMousePressed(MouseEvent event) {
        // Record the initial mouse cursor position
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }

    private void onMouseDragged(MouseEvent event) {
        // Calculate the new position of the LegendView based on mouse movement
        double deltaX = event.getSceneX() - xOffset;
        double deltaY = event.getSceneY() - yOffset;

        // Update the LegendView's position
        setLayoutX(getLayoutX() + deltaX);
        setLayoutY(getLayoutY() + deltaY);

        // Update the mouse cursor position
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }

}
