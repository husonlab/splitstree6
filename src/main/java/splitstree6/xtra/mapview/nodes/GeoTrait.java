package splitstree6.xtra.mapview.nodes;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * The GeoTrait class represents a geographical trait with latitude, longitude, and associated taxa composition.
 * It encapsulates information about a trait observed at a specific geographic location,
 * including the longitude, latitude, number of taxa, taxa list, and their composition.
 * Nikolas Kreisz 1.2024
 */
public class GeoTrait {
    private double longitude, latitude;
    private int nTaxa;
    private ArrayList<String> taxa;
    private HashMap<String, Integer> compostion;
    /**
     * Constructs a GeoTrait with the specified longitude, latitude, number of taxa, taxa list, and composition.
     *
     * @param longitude   The longitude of the geographical trait.
     * @param latitude    The latitude of the geographical trait.
     * @param nTaxa       The number of taxa associated with the trait.
     * @param taxa        The list of taxa associated with the trait.
     * @param compostion The composition of taxa associated with the trait.
     */
    public GeoTrait(double longitude, double latitude, int nTaxa, ArrayList<String> taxa, HashMap<String, Integer> compostion) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.nTaxa = compostion.size();
        this.taxa = taxa;
        this.compostion = compostion;
    }
    /**
     * Retrieves the longitude of the geographical trait.
     *
     * @return The longitude value.
     */
    public double getlongitude() {
        return longitude;
    }
    /**
     * Retrieves the latitude of the geographical trait.
     *
     * @return The latitude value.
     */
    public double getLatitude() {
        return latitude;
    }
    /**
     * Retrieves the number of taxa associated with the trait.
     *
     * @return The number of taxa.
     */
    public int getnTaxa() {
        return nTaxa;
    }
    /**
     * Retrieves the list of taxa associated with the trait.
     *
     * @return The list of taxa.
     */
    public ArrayList<String> getTaxa() {
        return taxa;
    }
    /**
     * Retrieves the composition of taxa associated with the trait.
     *
     * @return The composition map of taxa.
     */
    public HashMap<String, Integer> getCompostion() {
        return compostion;
    }

}
