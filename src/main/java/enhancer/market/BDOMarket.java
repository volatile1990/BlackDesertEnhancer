package enhancer.market;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import enhancer.Constants;

public class BDOMarket {

	public static void main(String[] args) {
		List<Accessory> accesories = new BDOMarket().getAccessories();
		System.out.println("DBEUG");
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
					accessory.basePrice = jsonObject.getInt("basePrice");

					// Remove manos
					if (StringUtils.containsIgnoreCase(accessory.name, "manos")) {
						continue;
					}

					// Remove preonne
					if (StringUtils.containsIgnoreCase(accessory.name, "preonne")) {
						continue;
					}

					// Remove geranoa
					if (StringUtils.containsIgnoreCase(accessory.name, "geranoa")) {
						continue;
					}

					// Remove weird items
					if (StringUtils.containsAnyIgnoreCase(accessory.name, "Diamond Necklace of Fortitude", "Emerald Necklace of Tranquility", "Topaz Necklace of Regeneration", "Sapphire Necklace of Storms", "Corrupt Ruby Necklace")) {
						continue;
					}

					// Remove low price
					if (accessory.basePrice < Constants.BASE_PRICE_ACCESSORY_THRESHOLD) {
						continue;
					}

					// Enrich TRI/TET sale price
					enrichEnhancedData(accessory);

					accessories.add(accessory);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return accessories;
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
					costume.basePrice = jsonObject.getInt("basePrice");

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
			for (String line; (line = reader.readLine()) != null;) {
				result.append(line);
			}
		}

		return result.toString();
	}

	private void enrichEnhancedData(Item item) throws MalformedURLException, IOException, ProtocolException {
		StringBuilder result = new StringBuilder();
		URL url = new URL(Constants.FUNCTIONAL_ARMOR_ENHANCED_COST_URL);
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
		String postData = String.format("keyType=0&mainKey=%d", item.id);
		byte[] postDataBytes = postData.getBytes("UTF-8");

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

		appendCostumePrices(item, result.toString());
	}

	private void appendCostumePrices(Item item, String response) {

		JSONObject jsonResponse = new JSONObject(response);
		String resultMsg = jsonResponse.getString("resultMsg");

		if (resultMsg.contains("|")) {

			String[] split = resultMsg.split("\\|");
			for (String enhancementLine : split) {

				String[] enhancementLineSplit = enhancementLine.split("\\-");
				if (enhancementLineSplit[2].equals("3")) {

					// TRI Price
					item.triPrice = Long.valueOf(enhancementLineSplit[3]);
				} else if (enhancementLineSplit[2].equals("4")) {

					// TET Price
					item.tetPrice = Long.valueOf(enhancementLineSplit[3]);
				} else if (enhancementLineSplit[2].equals("0")) {

					// TET Price
					item.baseStock = Integer.valueOf(enhancementLineSplit[4]);
				}
			}
		} else {
			System.out.println("Could not append enhancement data for " + item.name);
		}
	}
}
