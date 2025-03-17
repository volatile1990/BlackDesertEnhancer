package com.bdo.enhancer.model.stack;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Abstrakte Basisklasse, die das Stack-Interface implementiert
 */
@Getter
@AllArgsConstructor
public abstract class AbstractStack {
    protected final int blackStoneCount;
    protected final double monChance;
    protected final double duoChance;
    protected final double triChance;
    protected final double tetChance;
    protected final int stackCount;

    @Override
    public String toString() {
        return String.valueOf(this.stackCount);
    }
}