package enhancer.market;

import enhancer.Constants;
import enhancer.models.market.Accessory;
import enhancer.models.market.Item;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class BDOMarket {

    public static void main(String[] args) throws Exception {
        List<Accessory> accesories = new BDOMarket().getAccessories();
        System.out.println(accesories.size());
    }

    public List<Accessory> getAccessories() {
        List<Accessory> accessories = new ArrayList<>();
        try {
            Map<String, String> accessoryDataMap = getAccessoryData();

            for (String jsonData : accessoryDataMap.values()) {

                JSONArray jsonArray = new JSONArray(jsonData);
                for (int i = 0; i < jsonArray.length(); i++) {

                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    String name = jsonObject.getString("name");
                    int id = jsonObject.getInt("id");

                    Accessory accessory = new Accessory(name, id);
                    accessory.setBasePrice(jsonObject.getInt("basePrice"));

                    if (skipCurrentAccessory(accessory)) continue;

                    // Enrich Base/TRI/TET sale price
                    enrichEnhancedData(accessory);

                    accessories.add(accessory);
                }
            }
        } catch (Exception e) {
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
        if (accessory.getBasePrice() < Constants.BASE_PRICE_ACCESSORY_THRESHOLD) {
            return true;
        }
        return false;
    }

    private Map<String, String> getAccessoryData() throws Exception {
        Map<String, String> results = new HashMap<>();

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("ring", Constants.ACCESSORY_RING_CALL_URL);
        endpoints.put("necklace", Constants.ACCESSORY_NECKLACE_CALL_URL);
        endpoints.put("earring", Constants.ACCESSORY_EARRING_CALL_URL);
        endpoints.put("belt", Constants.ACCESSORY_BELT_CALL_URL);

        for (Map.Entry<String, String> entry : endpoints.entrySet()) {
            StringBuilder result = new StringBuilder();
            URL url = new URL(entry.getValue());
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            try {
                if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            result.append(line);
                        }
                    }
                    results.put(entry.getKey(), result.toString());
                } else {
                    throw new RuntimeException("HTTP error code for " + entry.getKey() + ": " + con.getResponseCode());
                }
            } finally {
                con.disconnect();
            }
        }

        return results;
    }

    public List<Costume> getCostumes() {
        List<Costume> costumes = new ArrayList<>();

        try {
            String getResult = getCostumeData();
            JSONArray jsonArray = new JSONArray(getResult);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String name = jsonObject.getString("name");

                if (name.contains("Silver Embroidered")) {

                    int id = jsonObject.getInt("id");
                    Costume costume = new Costume(name, id);
                    costume.setBasePrice(jsonObject.getInt("basePrice"));

                    // Get TRI/TET sale price
                    enrichEnhancedData(costume);

                    costumes.add(costume);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return costumes;
    }

    private String getCostumeData() throws MalformedURLException, IOException, ProtocolException {
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

    private void enrichEnhancedData(Item item) throws MalformedURLException, IOException, ProtocolException {

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

    private void enrichBaseEnhancedData(Item item) throws MalformedURLException, IOException, ProtocolException {
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
                case 0:
                    item.setBasePrice(lowestPrice);
                    break;
                case 2:
                    item.setDuoPrice(lowestPrice);
                    break;
                case 3:
                    item.setTriPrice(lowestPrice);
                    break;
                case 4:
                    item.setTetPrice(lowestPrice);
                    break;
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
}
