/**
 * Set of failstacks for different enhancement levels
 */
package com.bdo.enhancer.model.stack;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FailStackSet {

    AccessoryStack monStack;
    AccessoryStack duoStack;
    AccessoryStack triStack;
    AccessoryStack tetStack;

    public FailStackSet(AccessoryStack monStack, AccessoryStack duoStack, AccessoryStack triStack, AccessoryStack tetStack) {
        this.monStack = monStack;
        this.duoStack = duoStack;
        this.triStack = triStack;
        this.tetStack = tetStack;
    }
}
