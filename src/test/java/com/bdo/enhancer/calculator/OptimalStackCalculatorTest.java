package com.bdo.enhancer.calculator;

import com.bdo.enhancer.model.item.Accessory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimalStackCalculatorTest {

    @Test
    void skipsFixedChanceManosClothing() {
        Accessory manosClothes = new Accessory("Manos Cook's Clothes", 705037);
        List<String> progressMessages = new ArrayList<>();
        OptimalStackCalculator calculator = new OptimalStackCalculator(1, 1);

        assertTrue(calculator.findOptimalStacks(List.of(manosClothes), progressMessages::add).isEmpty());
        assertTrue(progressMessages.stream().anyMatch(message -> message.contains("fixed-chance")));
    }
}
