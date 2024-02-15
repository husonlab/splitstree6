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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static splitstree6.xtra.mapview.ColorSchemes.SCHEME1;

public class LegendView extends VBox {

    private Map<String, String> categoryColors;
    private ArrayList<String> taxa;
    private double xOffset = 0;
    private double yOffset = 0;

    public LegendView(ArrayList<String> taxa, String colorScheme) {
        this.categoryColors = buildColors(taxa, colorScheme);
        this.taxa = taxa;
        createLegend();
        setPickOnBounds(false);
        setStyle("-fx-background-color: white;");
        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);
        /**/
    }

    public void updateColors(String scheme){
        var posX = this.getLayoutX();
        var posY = this.getLayoutY();
        buildColors(taxa, scheme);
        createLegend();

        this.setLayoutX(posX);
        this.setLayoutY(posY);
    }

    private Map<String, String> buildColors(ArrayList<String> taxa, String colorScheme) {
        Map<Integer, String> colors = ColorSchemes.getScheme(colorScheme);
        Map<String, String> categoryColors = new HashMap<>();
        for (int i = 0; i < taxa.size(); i++) {
            categoryColors.put(taxa.get(i), colors.get(i));
        }
        return categoryColors;
    }


    private void createLegend() {
        for (Map.Entry<String, String> entry : categoryColors.entrySet()) {
            String category = entry.getKey();


            Color color = Color.web(entry.getValue());

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
        //System.out.println("pressed");
    }

    private void onMouseDragged(MouseEvent event) {
        //System.out.println("dragged");
        // Calculate the new position of the LegendView based on mouse movement
        double deltaX = event.getSceneX() - xOffset;
        double deltaY = event.getSceneY() - yOffset;

        // Update the LegendView's position

        setTranslateX(getTranslateX() + deltaX);
        setTranslateY(getTranslateY() + deltaY);

        // Update the mouse cursor position
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }

}
