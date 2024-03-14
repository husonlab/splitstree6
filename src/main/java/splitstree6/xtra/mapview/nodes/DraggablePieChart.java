package splitstree6.xtra.mapview.nodes;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.chart.PieChart;
import splitstree6.xtra.mapview.ColorSchemes;
import splitstree6.xtra.mapview.MapViewController;

import java.util.ArrayList;
import java.util.Map;

/**
 * DraggablePieChart is a custom JavaFX component that extends the PieChart class.
 * It provides additional functionality to make the pie chart draggable within a view.
 * Users can interactively drag and reposition the pie chart using mouse events.
 * This class also supports saving color IDs associated with pie chart data items and updating colors dynamically.
 * Nikolas Kreisz 1.2024
 */
public class DraggablePieChart {
    private PieChart pieChart;
    private ArrayList<Integer> colorIndexes = new ArrayList<>();
    private double offsetX, offsetY;
    private final DoubleProperty centerXProperty = new SimpleDoubleProperty();
    private final DoubleProperty centerYProperty = new SimpleDoubleProperty();

    /**
     * Constructs a DraggablePieChart with the specified data.
     *
     * @param data       The observable list of data items to be displayed in the pie chart.
     * @param controller The controller used to link features of the chart to UI elements
     */
    public DraggablePieChart(ObservableList data, MapViewController controller) {

        pieChart = new PieChart();
        pieChart.setData(data);

        // Binding attributes of the chart to UI control elements
        getPieChart().prefHeightProperty().bind(controller.getChartSizeSlider().valueProperty());
        getPieChart().prefWidthProperty().bind(controller.getChartSizeSlider().valueProperty());
        getPieChart().setMinWidth(80);
        getPieChart().setMaxWidth(200);
        getPieChart().setMinHeight(80);
        getPieChart().setMaxHeight(200);
        getPieChart().setCenterShape(true);
        getPieChart().prefWidthProperty().bind(controller.getChartSizeSlider().valueProperty());
        getPieChart().prefHeightProperty().bind(controller.getChartSizeSlider().valueProperty());


        // Set up event handlers for dragging
        pieChart.setOnMousePressed(event -> {
            offsetX = event.getSceneX() - pieChart.getLayoutX();
            offsetY = event.getSceneY() - pieChart.getLayoutY();
        });

        pieChart.setOnMouseDragged(event -> {

            pieChart.setLayoutX(event.getSceneX() - offsetX);
            pieChart.setLayoutY(event.getSceneY() - offsetY);

            centerXProperty.set(pieChart.getLayoutX() + pieChart.getBoundsInLocal().getWidth() / 2);
            centerYProperty.set(pieChart.getLayoutY() + pieChart.getBoundsInLocal().getHeight() / 2);

        });

        // Additional listener to update the center variable if the charts size is changed
        controller.getChartSizeSlider().valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                // Update the center of the pie chart
                updateCenter();
            }
        });
    }

    /**
     * Saves the color indexes associated with the pie chart data items.
     *
     * @param traits The list of traits corresponding to the pie chart data items.
     */
    public void saveColorIDs(ArrayList<String> traits){
        ArrayList<Integer> indexes = new ArrayList<>();
        var data = pieChart.getData();
        for(PieChart.Data label : data){
            indexes.add(traits.indexOf(label.getName()));
        }
        colorIndexes = indexes;
    }

    /**
     * Updates the colors of the pie chart slices based on the specified color scheme.
     *
     * @param scheme The name of the color scheme to be applied.
     */
    public void updateColors(String scheme){
        Map<Integer, String> colors = ColorSchemes.getScheme(scheme);
        for(int i = 0; i < pieChart.getData().size() ; i++){
            String style = "-fx-pie-color: " + colors.get(colorIndexes.get(i)) + ";";
            pieChart.getData().get(i).getNode().setStyle(style);
        }
    }

    /**
     * Updates the center coordinates of the pie chart.
     */
    public void updateCenter(){
        centerXProperty.set(pieChart.getLayoutX() + pieChart.getBoundsInLocal().getWidth() / 2);
        centerYProperty.set(pieChart.getLayoutY() + pieChart.getBoundsInLocal().getHeight() / 2);
    }

    /**
     * Retrieves the PieChart component associated with this DraggablePieChart.
     *
     * @return The PieChart component.
     */
    public PieChart getPieChart() {
        return pieChart;
    }
    /**
     * Retrieves the property representing the center X-coordinate of the pie chart.
     *
     * @return The property representing the center X-coordinate.
     */
    public DoubleProperty centerXProperty() {
        return centerXProperty;
    }
    /**
     * Retrieves the property representing the center Y-coordinate of the pie chart.
     *
     * @return The property representing the center Y-coordinate.
     */
    public DoubleProperty centerYProperty() {
        return centerYProperty;
    }
}
