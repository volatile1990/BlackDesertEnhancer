package com.bdo.enhancer.calculator;

import com.bdo.enhancer.model.item.Accessory;
import com.bdo.enhancer.model.result.AccessoryEnhancementResult;
import com.bdo.enhancer.model.stack.AccessoryStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AccessoryProfitCalculatorTest {

    @Test
    void calculatesManosClothingWithoutConsumingOrReplacingSelectedStacks() {
        Accessory manosClothes = new Accessory("Manos Cook's Clothes", 705037);
        manosClothes.setBasePrice(218_000_000);
        manosClothes.setDuoPrice(1_250_000_000);
        manosClothes.setTriPrice(1_850_000_000);
        manosClothes.setTetPrice(5_500_000_000L);

        AccessoryProfitCalculator calculator = new AccessoryProfitCalculator();
        calculator.setSimulationRuns(3);
        calculator.setMonStack(AccessoryStack.TEN);
        calculator.setDuoStack(AccessoryStack.FIFTEEN);
        calculator.setTriStack(AccessoryStack.TWENTY);
        calculator.setTetStack(AccessoryStack.TWENTYFIVE);

        AccessoryEnhancementResult result = calculator
                .calculateProfitsWithAccessories(List.of(manosClothes))
                .get(0);

        assertEquals(1, result.duoItems);
        assertEquals(1, result.triItems);
        assertEquals(1, result.tetItems);
        assertSame(AccessoryStack.TEN, calculator.getMonStack());
        assertSame(AccessoryStack.FIFTEEN, calculator.getDuoStack());
        assertSame(AccessoryStack.TWENTY, calculator.getTriStack());
        assertSame(AccessoryStack.TWENTYFIVE, calculator.getTetStack());
    }
}
