package enhancer.market;

import enhancer.Constants;
import enhancer.models.market.Accessory;
import enhancer.models.market.Item;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;

@Setter
public class BDOMarket {

    private Consumer<String> progressCallback;

    public static void main(String[] args) {
        List<Accessory> accessories = new BDOMarket().getAccessories();
        System.out.println(accessories.size());
    }

    public List<Accessory> getAccessories() {
        updateProgress("Initializing market data retrieval...");

        List<Accessory> accessories = new ArrayList<>();
        try {
            updateProgress("Fetching accessory data from market API...");
            Map<String, String> accessoryDataMap = getAccessoryData();

            // Count total accessories
            int totalAccessories = 0;
            for (String jsonData : accessoryDataMap.values()) {
                JSONArray jsonArray = new JSONArray(jsonData);
                totalAccessories += jsonArray.length();
            }

            updateProgress("Processing market data for " + totalAccessories + " accessories");

            int processedAccessories = 0;
            int addedAccessories = 0;

            // Process each accessory type
            for (Map.Entry<String, String> entry : accessoryDataMap.entrySet()) {
                String accessoryType = entry.getKey();
                String jsonData = entry.getValue();
                updateProgress("Processing " + accessoryType + " data");

                JSONArray jsonArray = new JSONArray(jsonData);
                for (int i = 0; i < jsonArray.length(); i++) {
                    processedAccessories++;

                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    String name = jsonObject.getString("name");
                    int id = jsonObject.getInt("id");

                    Accessory accessory = new Accessory(name, id);
                    accessory.setBasePrice(jsonObject.getInt("basePrice"));

                    if (skipCurrentAccessory(accessory)) {
                        continue;
                    }

                    // Only update every 5 accessories to avoid too frequent updates
                    if (processedAccessories % 5 == 0 || processedAccessories == totalAccessories) {
                        updateProgress(String.format("Market data progress: %d of %d accessories (%d%%)",
                                processedAccessories, totalAccessories,
                                (int)(processedAccessories * 100.0 / totalAccessories)));
                    }

                    // Enrich Base/TRI/TET sale price
                    enrichEnhancedData(accessory);

                    accessories.add(accessory);
                    addedAccessories++;

                    // Periodically update on added accessories
                    if (addedAccessories % 10 == 0) {
                        updateProgress(String.format("Found %d valid accessories so far", addedAccessories));
                    }
                }
            }

            updateProgress("Market data processing complete. Found " + accessories.size() + " valid accessories");

        } catch (Exception e) {
            updateProgress("Error loading market data: " + e.getMessage());
            e.printStackTrace();
        }
        return accessories;
    }

    private static boolean skipCurrentAccessory(Accessory accessory) {
        // Remove manos
        if (StringUtils.containsIgnoreCase(accessory.getName(), "manos")) {
            return true;
        }

        // Remove preonne
        if (StringUtils.containsIgnoreCase(accessory.getName(), "preonne")) {
            return true;
        }

        // Remove geranoa
        if (StringUtils.containsIgnoreCase(accessory.getName(), "geranoa")) {
            return true;
        }

        // Remove weird items
        if (StringUtils.containsAnyIgnoreCase(accessory.getName(), "Diamond Necklace of Fortitude", "Emerald Necklace of Tranquility", "Topaz Necklace of Regeneration", "Sapphire Necklace of Storms", "Corrupt Ruby Necklace")) {
            return true;
        }

        // Remove low price
        return accessory.getBasePrice() < Constants.BASE_PRICE_ACCESSORY_THRESHOLD;
    }

    private Map<String, String> getAccessoryData() throws Exception {
        Map<String, String> results = new HashMap<>();

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("ring", Constants.ACCESSORY_RING_CALL_URL);
        endpoints.put("necklace", Constants.ACCESSORY_NECKLACE_CALL_URL);
        endpoints.put("earring", Constants.ACCESSORY_EARRING_CALL_URL);
        endpoints.put("belt", Constants.ACCESSORY_BELT_CALL_URL);

        int processed = 0;
        int total = endpoints.size();

        for (Map.Entry<String, String> entry : endpoints.entrySet()) {
            processed++;
            String accessoryType = entry.getKey();
            updateProgress(String.format("Requesting %s data from market API (%d of %d)",
                    accessoryType, processed, total));

            StringBuilder result = new StringBuilder();
            URL url = new URL(entry.getValue());
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            try {
                if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            result.append(line);
                        }
                    }
                    results.put(entry.getKey(), result.toString());
                } else {
                    String errorMsg = String.format("HTTP error for %s: %d", accessoryType, con.getResponseCode());
                    updateProgress(errorMsg);
                    throw new RuntimeException(errorMsg);
                }
            } finally {
                con.disconnect();
            }
        }

        updateProgress("Successfully fetched all accessory data from market API");
        return results;
    }

    public List<Costume> getCostumes() {
        updateProgress("Loading costume data from market...");
        List<Costume> costumes = new ArrayList<>();

        try {
            String getResult = getCostumeData();
            JSONArray jsonArray = new JSONArray(getResult);
            updateProgress("Processing " + jsonArray.length() + " costumes from market data");

            int total = jsonArray.length();
            int processed = 0;
            int added = 0;

            for (int i = 0; i < jsonArray.length(); i++) {
                processed++;

                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String name = jsonObject.getString("name");

                if (name.contains("Silver Embroidered")) {
                    int id = jsonObject.getInt("id");
                    Costume costume = new Costume(name, id);
                    costume.setBasePrice(jsonObject.getInt("basePrice"));

                    // Get TRI/TET sale price
                    enrichEnhancedData(costume);

                    costumes.add(costume);
                    added++;
                }

                // Periodic update
                if (processed % 10 == 0 || processed == total) {
                    updateProgress(String.format("Costume data: processed %d of %d (%d%%)",
                            processed, total, (processed * 100 / total)));
                }
            }

            updateProgress("Costume data processing complete. Added " + added + " costumes");

        } catch (Exception e) {
            updateProgress("Error loading costume data: " + e.getMessage());
            e.printStackTrace();
        }

        return costumes;
    }

    private String getCostumeData() throws IOException {
        updateProgress("Fetching costume data from market API...");
        URL url = new URL(Constants.FUNCTIONAL_ARMOR_CALL_URL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            for (String line; (line = reader.readLine()) != null; ) {
                result.append(line);
            }
        }
        return result.toString();
    }

    private void enrichEnhancedData(Item item) throws IOException {
        enrichBaseEnhancedData(item);

        // Get Base bidding info list
        enrichBiddingInfoForItemLevel(item, 0);

        // Get DUO bidding info list
        enrichBiddingInfoForItemLevel(item, 2);

        // Get TRI bidding info list
        enrichBiddingInfoForItemLevel(item, 3);

        // Get TET bidding info list
        enrichBiddingInfoForItemLevel(item, 4);
    }

    private void enrichBaseEnhancedData(Item item) throws IOException {
        StringBuilder result = new StringBuilder();
        URL url = new URL(Constants.ENHANCED_COST_URL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        // Request-Methode setzen
        con.setRequestMethod("POST");

        // Header setzen
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setRequestProperty("Accept", "*/*");
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        con.setRequestProperty("Origin", "https://eu-trade.naeu.playblackdesert.com");
        con.setRequestProperty("Referer", "https://eu-trade.naeu.playblackdesert.com/");

        // Request-Body vorbereiten
        String postData = String.format("keyType=0&mainKey=%d", item.getId());
        byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);

        // Content-Length entsprechend der tatsächlichen Datengröße setzen
        con.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));

        // Output aktivieren und Daten senden
        con.setDoOutput(true);
        try (var os = con.getOutputStream()) {
            os.write(postDataBytes);
        }

        // Response lesen
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        appendBaseEnhancementData(item, result.toString());
    }

    private void enrichBiddingInfoForItemLevel(Item item, int level) throws IOException {
        StringBuilder result = getBiddingInfoList(item, level);
        JSONObject jsonResponse = new JSONObject(result.toString());

        JSONArray orders = jsonResponse.getJSONArray("orders");
        long lowestPrice = findLowestPrice(orders);
        if (lowestPrice >= 0) {
            switch (level) {
                case 0 -> item.setBasePrice(lowestPrice);
                case 2 -> item.setDuoPrice(lowestPrice);
                case 3 -> item.setTriPrice(lowestPrice);
                case 4 -> item.setTetPrice(lowestPrice);
            }
        }
    }

    private long findLowestPrice(JSONArray orders) {
        return IntStream.range(0, orders.length())
                .mapToObj(orders::getJSONObject)
                .filter(orderObj -> orderObj.getInt("sellers") > 0)
                .mapToLong(orderObj -> orderObj.getLong("price"))
                .min()
                .orElse(-1);
    }

    private static StringBuilder getBiddingInfoList(Item item, int level) throws IOException {
        StringBuilder result = new StringBuilder();

        // URL mit Query-Parametern erstellen
        String urlWithParams = String.format("%s?id=%d&sid=%d",
                Constants.BIDDING_INFO_LIST_URL,
                item.getId(),
                level);
        URL url = new URL(urlWithParams);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        // Request-Methode auf GET setzen
        con.setRequestMethod("GET");

        // Header setzen
        con.setRequestProperty("Accept", "*/*");
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        con.setRequestProperty("Origin", "https://eu-trade.naeu.playblackdesert.com");
        con.setRequestProperty("Referer", "https://eu-trade.naeu.playblackdesert.com/");

        // Response lesen
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result;
    }

    private void appendBaseEnhancementData(Item item, String response) {
        JSONObject jsonResponse = new JSONObject(response);
        String resultMsg = jsonResponse.getString("resultMsg");

        if (resultMsg.contains("|")) {
            String[] split = resultMsg.split("\\|");

            for (String enhancementLine : split) {
                int enhancementIndex = 2;
                int priceIndex = 8;
                int baseStockIndex = 4;
                String[] enhancementLineSplit = enhancementLine.split("-");
                switch (enhancementLineSplit[enhancementIndex]) {
                    case "2" -> item.setDuoPrice(Long.parseLong(enhancementLineSplit[priceIndex])); // DUO Price
                    case "3" -> item.setTriPrice(Long.parseLong(enhancementLineSplit[priceIndex])); // TRI Price
                    case "4" -> item.setTetPrice(Long.parseLong(enhancementLineSplit[priceIndex])); // TET Price
                    case "0" -> item.setBaseStock(Integer.parseInt(enhancementLineSplit[baseStockIndex])); // Base Stock
                }
            }
        } else {
            System.out.println("Could not append enhancement data for " + item.getName());
        }
    }

    // Helper method to send progress updates
    private void updateProgress(String message) {
        if (progressCallback != null) {
            progressCallback.accept(message);
        }
    }
}