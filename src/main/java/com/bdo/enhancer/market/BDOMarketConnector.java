package com.bdo.enhancer.market;

import com.bdo.enhancer.model.constants.Constants;
import com.bdo.enhancer.model.item.Accessory;
import com.bdo.enhancer.model.item.Item;
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
public class BDOMarketConnector {

    private Consumer<String> progressCallback;
    // Thread pool for parallel operations
    private final ExecutorService executorService = Executors.newWorkStealingPool();

    public List<Accessory> getAccessories() {
        updateProgress("Initializing market data retrieval...");
        List<Accessory> accessories = new ArrayList<>();

        try {
            // Step 1: Fetch accessory data in parallel
            updateProgress("Fetching accessory data from market API ...");
            Map<String, String> accessoryDataMap = getAccessoryDataParallel();

            // Step 2: Process each accessory type in parallel
            List<Accessory> accessoryList = createAndFilterItems(accessoryDataMap);

            // Step 3: Enrich accessory data in parallel
            updateProgress(String.format("Enrichment progress: %d of %d accessories (%d%%)", 0, accessoryList.size(), 0));
            accessories = enrichData(accessoryList);

            updateProgress("Market data processing complete. Found " + accessories.size() + " valid accessories");

        } catch (Exception e) {
            updateProgress("Error loading market data: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup resources
            executorService.shutdown();
        }

        return accessories;
    }

    private List<Accessory> createAndFilterItems(Map<String, String> accessoryDataMap) {
        List<Accessory> allAccessories = new ArrayList<>();

        for (Map.Entry<String, String> entry : accessoryDataMap.entrySet()) {
            String accessoryType = entry.getKey();
            String jsonData = entry.getValue();

            JSONArray jsonArray = new JSONArray(jsonData);
            boolean isCostume = accessoryType.equalsIgnoreCase("costume");

            for (int i = 0; i < jsonArray.length(); i++) {

                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String name = jsonObject.getString("name");
                int id = jsonObject.getInt("id");

                Accessory accessory = new Accessory(name, id);
                accessory.setBasePrice(jsonObject.getInt("basePrice"));

                if (isCostume) {
                    if (!StringUtils.containsIgnoreCase(name, "Silver")) {
                        continue;
                    }
                } else {
                    if (skipCurrentAccessory(accessory)) {
                        continue;
                    }
                }

                allAccessories.add(accessory);
            }
        }

        return allAccessories;
    }

    private List<Accessory> enrichData(List<Accessory> accessoryList) {
        final int totalEnrichments = accessoryList.size();
        AtomicInteger enrichedCount = new AtomicInteger(0);
        List<CompletableFuture<Accessory>> enrichmentFutures = new ArrayList<>();

        // Seperate future to keep track of progress in a single process
        CompletableFuture<Void> progressFuture = createAndRunProgressFuture(totalEnrichments, enrichedCount);

        // The actual futures that call the api for data enrichment in parallel
        createAndRunEnrichmentFutures(accessoryList, enrichedCount, enrichmentFutures);

        // Add to also wait for progress future to finish
        enrichmentFutures.add(progressFuture.thenApply(v -> null));

        // Wait for all futures to complete then return collected data as accessories
        return enrichmentFutures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void createAndRunEnrichmentFutures(List<Accessory> accessoryList, AtomicInteger enrichedCount, List<CompletableFuture<Accessory>> enrichmentFutures) {
        for (Accessory accessory : accessoryList) {
            CompletableFuture<Accessory> future = CompletableFuture.supplyAsync(() -> {
                try {
                    enrichEnhancedData(accessory);
                    return accessory;
                } catch (Exception e) {
                    updateProgress("Error enriching data for " + accessory.getName() + ": " + e.getMessage());
                    return null;
                } finally {
                    enrichedCount.incrementAndGet();
                }
            }, executorService);

            enrichmentFutures.add(future);
        }
    }

    private CompletableFuture<Void> createAndRunProgressFuture(int totalEnrichments, AtomicInteger enrichedCount) {
        // Kurze Pause, um CPU-Belastung zu reduzieren
        return CompletableFuture.runAsync(() -> {
            int lastReported = 0;
            while (lastReported < totalEnrichments) {
                int currentCount = enrichedCount.get();
                if (currentCount > lastReported) {
                    updateProgress(String.format("Enrichment progress: %d of %d accessories (%d%%)",
                            currentCount, totalEnrichments,
                            (int) (currentCount * 100.0 / totalEnrichments)));
                    lastReported = currentCount;
                }
                try {
                    Thread.sleep(100); // Kurze Pause, um CPU-Belastung zu reduzieren
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, executorService);
    }

    private Map<String, String> getAccessoryDataParallel() {
        Map<String, CompletableFuture<String>> futures = new HashMap<>();

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("ring", Constants.ACCESSORY_RING_CALL_URL);
        endpoints.put("necklace", Constants.ACCESSORY_NECKLACE_CALL_URL);
        endpoints.put("earring", Constants.ACCESSORY_EARRING_CALL_URL);
        endpoints.put("belt", Constants.ACCESSORY_BELT_CALL_URL);
        endpoints.put("costume", Constants.FUNCTIONAL_ARMOR_CALL_URL);

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

        Map<String, String> accessoryDataMap = new ConcurrentHashMap<>();
        for (Map.Entry<String, CompletableFuture<String>> entry : futures.entrySet()) {
            accessoryDataMap.put(entry.getKey(), entry.getValue().join());
        }

        return accessoryDataMap;
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