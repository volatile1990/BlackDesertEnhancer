package com.bdo.enhancer.market;

import com.bdo.enhancer.model.item.Accessory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BDOMarketConnectorTest {

    @Test
    void includesManosClothesButStillExcludesManosAccessories() {
        Accessory manosClothes = item("Manos Cook's Clothes", 705037);
        Accessory silverClothes = item("Silver Embroidered Cook's Clothes", 14017);
        Accessory loggiaClothes = item("Loggia Cook's Clothes", 705031);
        Accessory manosRing = item("Manos Ring", 705509);

        assertTrue(BDOMarketConnector.shouldIncludeAccessory(manosClothes, true));
        assertTrue(BDOMarketConnector.shouldIncludeAccessory(silverClothes, true));
        assertFalse(BDOMarketConnector.shouldIncludeAccessory(loggiaClothes, true));
        assertFalse(BDOMarketConnector.shouldIncludeAccessory(manosRing, false));
    }

    @Test
    void includesEveryManosClothingCurrentlyReturnedByTheFunctionalArmorEndpoint() {
        List<String> names = List.of(
                "Manos Alchemist's Clothes",
                "Manos Cook's Clothes",
                "Manos Craftsman's Clothes",
                "Manos Fisher's Clothes",
                "Manos Gatherer's Clothes",
                "Manos Hunter's Clothes",
                "Manos Sailor's Clothes",
                "Manos Trainer's Clothes"
        );

        for (String name : names) {
            assertTrue(BDOMarketConnector.shouldIncludeAccessory(item(name, 1), true), name);
        }
    }

    @Test
    void filtersCompleteEndpointPayloadsByTheirSourceCategory() {
        Map<String, String> endpointPayloads = Map.of(
                "costume", "["
                        + "{\"name\":\"Manos Cook's Clothes\",\"id\":705037,\"basePrice\":211000000},"
                        + "{\"name\":\"Silver Embroidered Cook's Clothes\",\"id\":14017,\"basePrice\":10000000},"
                        + "{\"name\":\"Loggia Cook's Clothes\",\"id\":705031,\"basePrice\":10000000}]",
                "ring", "["
                        + "{\"name\":\"Manos Ring\",\"id\":705509,\"basePrice\":100000000},"
                        + "{\"name\":\"Tungrad Ring\",\"id\":12031,\"basePrice\":100000000}]"
        );

        List<String> includedNames = new BDOMarketConnector()
                .createAndFilterItems(endpointPayloads)
                .stream()
                .map(Accessory::getName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of(
                "Manos Cook's Clothes",
                "Silver Embroidered Cook's Clothes",
                "Tungrad Ring"
        ), includedNames);
    }

    @Test
    void parsesManosDuoTriAndTetPricesFromTheirActualMarketLevels() {
        Accessory manosClothes = item("Manos Cook's Clothes", 705037);
        String response = "{\"resultMsg\":\""
                + "705037-0-5-0-19-0-0-0-218000000-0|"
                + "705037-17-17-0-16-0-0-0-1250000000-0|"
                + "705037-18-18-0-8-0-0-0-1850000000-0|"
                + "705037-19-19-0-0-0-0-0-5500000000-0|\"}";

        BDOMarketConnector.appendBaseEnhancementData(manosClothes, response);

        assertEquals(19, manosClothes.getBaseStock());
        assertEquals(1_250_000_000L, manosClothes.getDuoPrice());
        assertEquals(1_850_000_000L, manosClothes.getTriPrice());
        assertEquals(5_500_000_000L, manosClothes.getTetPrice());
    }

    @Test
    void keepsParsingStandardAccessoryMarketLevels() {
        Accessory accessory = item("Deboreka Necklace", 11653);
        String response = "{\"resultMsg\":\""
                + "11653-0-0-0-10-0-0-0-100000000-0|"
                + "11653-2-2-0-4-0-0-0-200000000-0|"
                + "11653-3-3-0-2-0-0-0-300000000-0|"
                + "11653-4-4-0-1-0-0-0-400000000-0|\"}";

        BDOMarketConnector.appendBaseEnhancementData(accessory, response);

        assertEquals(10, accessory.getBaseStock());
        assertEquals(200_000_000L, accessory.getDuoPrice());
        assertEquals(300_000_000L, accessory.getTriPrice());
        assertEquals(400_000_000L, accessory.getTetPrice());
    }

    private Accessory item(String name, int id) {
        Accessory accessory = new Accessory(name, id);
        accessory.setBasePrice(100_000_000);
        return accessory;
    }
}
