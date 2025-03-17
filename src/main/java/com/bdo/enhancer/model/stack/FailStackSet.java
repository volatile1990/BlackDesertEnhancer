/**
 * Set of failstacks for different enhancement levels
 */
package com.bdo.enhancer.model.stack;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FailStackSet {

    AbstractStack monStack;
    AbstractStack duoStack;
    AbstractStack triStack;
    AbstractStack tetStack;

    public FailStackSet(AbstractStack monStack, AbstractStack duoStack, AbstractStack triStack, AbstractStack tetStack) {
        this.monStack = monStack;
        this.duoStack = duoStack;
        this.triStack = triStack;
        this.tetStack = tetStack;
    }
}
