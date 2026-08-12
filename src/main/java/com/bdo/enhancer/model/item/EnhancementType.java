package com.bdo.enhancer.model.item;

import org.apache.commons.lang3.StringUtils;

/**
 * Describes how an item is enhanced and how its enhancement levels are
 * represented by the Central Market API.
 */
public enum EnhancementType {
    ACCESSORY(true),
    SILVER_EMBROIDERED_CLOTHING(true),
    MANOS_CLOTHING(false);

    private final boolean usesFailstacks;

    EnhancementType(boolean usesFailstacks) {
        this.usesFailstacks = usesFailstacks;
    }

    public boolean usesFailstacks() {
        return usesFailstacks;
    }

    /**
     * Maps the result levels used by the calculator (DUO=2, TRI=3, TET=4)
     * to the sub ids used by the Central Market.
     */
    public int getMarketLevel(int resultLevel) {
        if (this != MANOS_CLOTHING || resultLevel == 0) {
            return resultLevel;
        }

        return switch (resultLevel) {
            case 2 -> 17;
            case 3 -> 18;
            case 4 -> 19;
            default -> throw new IllegalArgumentException("Unsupported Manos result level: " + resultLevel);
        };
    }

    /**
     * Maps a Central Market enhancement sub id back to a calculator result
     * level. Levels which are not displayed by the application return -1.
     */
    public int getResultLevel(int marketLevel) {
        if (this == MANOS_CLOTHING) {
            return switch (marketLevel) {
                case 17 -> 2;
                case 18 -> 3;
                case 19 -> 4;
                default -> -1;
            };
        }

        return switch (marketLevel) {
            case 2, 3, 4 -> marketLevel;
            default -> -1;
        };
    }

    public static EnhancementType fromItemName(String name) {
        if (StringUtils.containsIgnoreCase(name, "Manos")
                && StringUtils.containsIgnoreCase(name, "Clothes")) {
            return MANOS_CLOTHING;
        }
        if (StringUtils.containsIgnoreCase(name, "Silver")) {
            return SILVER_EMBROIDERED_CLOTHING;
        }
        return ACCESSORY;
    }
}
