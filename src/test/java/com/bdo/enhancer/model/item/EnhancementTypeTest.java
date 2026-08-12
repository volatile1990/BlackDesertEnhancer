package com.bdo.enhancer.model.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnhancementTypeTest {

    @Test
    void classifiesManosClothingSeparatelyFromManosAccessories() {
        Item clothing = new Accessory("Manos Cook's Clothes", 705037);
        Item ring = new Accessory("Manos Ring", 705509);

        assertEquals(EnhancementType.MANOS_CLOTHING, clothing.getEnhancementType());
        assertTrue(clothing.isManosClothing());
        assertFalse(clothing.usesFailstacks());

        assertEquals(EnhancementType.ACCESSORY, ring.getEnhancementType());
        assertFalse(ring.isManosClothing());
        assertTrue(ring.usesFailstacks());
    }

    @Test
    void mapsManosResultLevelsToCentralMarketSubIds() {
        EnhancementType type = EnhancementType.MANOS_CLOTHING;

        assertEquals(17, type.getMarketLevel(2));
        assertEquals(18, type.getMarketLevel(3));
        assertEquals(19, type.getMarketLevel(4));
        assertEquals(2, type.getResultLevel(17));
        assertEquals(3, type.getResultLevel(18));
        assertEquals(4, type.getResultLevel(19));
    }
}
