/**
 * Set of failstacks for different enhancement levels
 */
package com.bdo.enhancer.model.stack;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CostumeFailStackSet {

    CostumeStack monStack;
    CostumeStack duoStack;
    CostumeStack triStack;
    CostumeStack tetStack;

    public CostumeFailStackSet(CostumeStack monStack, CostumeStack duoStack, CostumeStack triStack, CostumeStack tetStack) {
        this.monStack = monStack;
        this.duoStack = duoStack;
        this.triStack = triStack;
        this.tetStack = tetStack;
    }
}
