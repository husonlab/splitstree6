package splitstree6.xtra.mapview.nodes;


import de.westnordost.countryboundaries.CountryBoundaries;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * The CountryFinder class provides functionality to retrieve information about countries based on their alpha-2 codes,
 * latitude, and longitude. It loads country data from a CSV file and allows users to query for countries within a specified
 * geographical bounding box.
 */
public class CountryFinder {

    private static final String csvLoc = "countries_codes_and_coordinates.csv";

    /**
     * Represents a country with its name, latitude, and longitude.
     */
    public record Country(String name, double latitude, double longitude) {}

    private final Map<String, Country> countryMap;

    /**
     * Constructs a new CountryFinder instance and initializes it by loading country data from a CSV file.
     *
     * @throws IOException if an I/O error occurs while reading the CSV file.
     */
    public CountryFinder() throws IOException {
        this.countryMap = loadCountryMapFromCSV();
    }


    private Map<String, Country> loadCountryMapFromCSV() throws IOException {
        Map<String, Country> map = new HashMap<>();
        try {
            URL resource = CountryFinder.class.getResource(csvLoc);
            File file = new File(resource.toURI());
            FileReader fileReader = new FileReader(file);
            BufferedReader br = new BufferedReader(fileReader);
            //Skip first line comment and header
            String line = br.readLine();
            line = br.readLine();
            // Load first line with relevant content
            line = br.readLine();
            while (line!= null) {
                String[] parts = line.split(",");
                if (line != null) {
                    String name = parts[0].replaceAll("\"", "").trim();
                    String alpha2Code = parts[1].replaceAll("\"", "").trim();
                    double lat = Double.parseDouble(parts[4].replaceAll("\"", "").trim());
                    double lon = Double.parseDouble(parts[5].replaceAll("\"", "").trim());
                    Country country = new Country(name, lat, lon);
                    map.put(alpha2Code, country);
                    line = br.readLine();
                } else {
                    System.err.println("Invalid line in CSV: " + line);
                }
            }
        }catch (Exception e){
            System.out.println(e.toString());
        }
        return map;
    }

    /**
     * Retrieves a list of countries within the specified geographical bounding box defined by latitude and longitude ranges.
     *
     * @param minLat  The minimum latitude of the bounding box.
     * @param maxLat  The maximum latitude of the bounding box.
     * @param minLong The minimum longitude of the bounding box.
     * @param maxLong The maximum longitude of the bounding box.
     * @return An ArrayList containing Country objects representing countries within the specified bounding box.
     * @throws IOException        if an I/O error occurs while reading the country boundaries data.
     * @throws URISyntaxException if the resource URI is invalid.
     * Nikolas Kreisz 3.2024
     */
    public ArrayList<CountryFinder.Country> getCountriesForAlpha2Codes(double minLat, double maxLat, double minLong, double maxLong) throws IOException, URISyntaxException {
        // Add a 10% margin to avoid showing labels to close to the window limits
        double latMargin = (maxLat-minLat)*0.1;
        double longMargin = (maxLong-minLong)*0.1;
        minLat += latMargin;
        maxLat -= latMargin;
        minLong += longMargin;
        maxLong -= longMargin;

        // Load file holding the geographic information of the country borders
        URL resource = CountryFinder.class.getResource("boundaries360x180.ser");
        File file = new File(resource.toURI());
        byte[] bytes = Files.readAllBytes(file.toPath());
        CountryBoundaries boundaries = CountryBoundaries.load(new ByteArrayInputStream(bytes));
        var alpha2Codes = boundaries.getIntersectingIds(minLong,minLat,maxLong,maxLat);
        ArrayList<Country> countries = new ArrayList<>();
        for (String alpha2Code : alpha2Codes) {
            if(!alpha2Code.contains("-")){
                Country country = countryMap.get(alpha2Code);
                if (country != null) {
                    countries.add(moveCenter(country, minLat,maxLat,minLong,maxLong));
                }
            }
        }
        return countries;
    }


    private CountryFinder.Country moveCenter(CountryFinder.Country country, double minLat, double maxLat, double minLong, double maxLong){
        double centerLat = country.latitude;
        double centerLong = country.longitude;

        if(centerLat > maxLat)centerLat = maxLat;
        if(centerLat < minLat)centerLat = minLat;
        if(centerLong > maxLong)centerLong = maxLong;
        if(centerLong < minLong)centerLong = minLong;

        return new CountryFinder.Country(country.name, centerLat, centerLong);
    }
}

