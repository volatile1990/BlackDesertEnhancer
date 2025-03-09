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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Setter
public class BDOMarket {

    private Consumer<String> progressCallback;
    // Thread pool for parallel operations
    private final ExecutorService executorService = Executors.newWorkStealingPool();

    public List<Accessory> getAccessories() {
        updateProgress("Initializing market data retrieval...");
        List<Accessory> accessories = new ArrayList<>();

        try {
            updateProgress("Fetching accessory data from market API ...");
            // Step 1: Fetch accessory data in parallel
            Map<String, CompletableFuture<String>> futureDataMap = getAccessoryDataParallel();

            // Wait for all fetches to complete and collect results
            Map<String, String> accessoryDataMap = new ConcurrentHashMap<>();
            for (Map.Entry<String, CompletableFuture<String>> entry : futureDataMap.entrySet()) {
                accessoryDataMap.put(entry.getKey(), entry.getValue().join());
            }

            // Count total accessories
            int totalAccessories = 0;
            for (String jsonData : accessoryDataMap.values()) {
                JSONArray jsonArray = new JSONArray(jsonData);
                totalAccessories += jsonArray.length();
            }

            updateProgress("Processing market data for " + totalAccessories + " accessories");

            AtomicInteger processedCount = new AtomicInteger(0);
            final int finalTotalAccessories = totalAccessories; // Make totalAccessories available in lambda
            List<CompletableFuture<List<Accessory>>> accessoryFutures = new ArrayList<>();

            // Step 2: Process each accessory type in parallel
            for (Map.Entry<String, String> entry : accessoryDataMap.entrySet()) {
                String accessoryType = entry.getKey();
                String jsonData = entry.getValue();
                updateProgress("Processing " + accessoryType + " data");

                CompletableFuture<List<Accessory>> future = CompletableFuture.supplyAsync(() -> {
                    List<Accessory> typeAccessories = new ArrayList<>();
                    JSONArray jsonArray = new JSONArray(jsonData);

                    for (int i = 0; i < jsonArray.length(); i++) {
                        int currentProcessed = processedCount.incrementAndGet();

                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        String name = jsonObject.getString("name");
                        int id = jsonObject.getInt("id");

                        Accessory accessory = new Accessory(name, id);
                        accessory.setBasePrice(jsonObject.getInt("basePrice"));

                        if (skipCurrentAccessory(accessory)) {
                            continue;
                        }

                        // Only update every 5 accessories to avoid too frequent updates
                        if (currentProcessed % 5 == 0 || currentProcessed == finalTotalAccessories) {
                            updateProgress(String.format("Market data progress: %d of %d accessories (%d%%)",
                                    currentProcessed, finalTotalAccessories,
                                    (int)(currentProcessed * 100.0 / finalTotalAccessories)));
                        }

                        typeAccessories.add(accessory);
                    }
                    return typeAccessories;
                }, executorService);

                accessoryFutures.add(future);
            }

            // Wait for all processing to complete and collect results
            List<Accessory> accessoryList = accessoryFutures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .toList();

            updateProgress("Enriching accessory data with enhanced information ...");

            // Step 3: Enrich accessory data in parallel
            List<CompletableFuture<Accessory>> enrichmentFutures = new ArrayList<>();
            for (Accessory accessory : accessoryList) {
                CompletableFuture<Accessory> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        enrichEnhancedData(accessory);
                        return accessory;
                    } catch (IOException e) {
                        updateProgress("Error enriching data for " + accessory.getName() + ": " + e.getMessage());
                        e.printStackTrace();
                        return null;
                    }
                }, executorService);

                enrichmentFutures.add(future);
            }

            // Wait for all enrichment to complete and collect valid results
            accessories = enrichmentFutures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            updateProgress("Market data processing complete. Found " + accessories.size() + " valid accessories");

        } catch (Exception e) {
            updateProgress("Error loading market data: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            executorService.shutdown();
        }

        return accessories;
    }

    private Map<String, CompletableFuture<String>> getAccessoryDataParallel() {
        Map<String, CompletableFuture<String>> futures = new HashMap<>();

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("ring", Constants.ACCESSORY_RING_CALL_URL);
        endpoints.put("necklace", Constants.ACCESSORY_NECKLACE_CALL_URL);
        endpoints.put("earring", Constants.ACCESSORY_EARRING_CALL_URL);
        endpoints.put("belt", Constants.ACCESSORY_BELT_CALL_URL);

        AtomicInteger processed = new AtomicInteger(0);
        int total = endpoints.size();

        // Create a future for each endpoint
        for (Map.Entry<String, String> entry : endpoints.entrySet()) {
            String accessoryType = entry.getKey();
            String endpoint = entry.getValue();

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                int current = processed.incrementAndGet();
                updateProgress(String.format("Requesting %s data from market API (%d of %d)",
                        accessoryType, current, total));

                try {
                    StringBuilder result = new StringBuilder();
                    URL url = new URL(endpoint);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod("GET");

                    if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                result.append(line);
                            }
                        }
                        return result.toString();
                    } else {
                        String errorMsg = String.format("HTTP error for %s: %d", accessoryType, con.getResponseCode());
                        updateProgress(errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error fetching " + accessoryType + ": " + e.getMessage(), e);
                }
            }, executorService);

            futures.put(accessoryType, future);
        }

        return futures;
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

    // Synchronized to prevent concurrent HTTP connections from overwhelming the server
    private synchronized void enrichEnhancedData(Item item) throws IOException {
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

    // Thread-safe method to send progress updates
    private synchronized void updateProgress(String message) {
        if (progressCallback != null) {
            progressCallback.accept(message);
        }
    }

}