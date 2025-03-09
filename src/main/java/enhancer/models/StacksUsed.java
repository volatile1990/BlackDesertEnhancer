package enhancer.models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StacksUsed {

    AccessoryStack monStack;
    AccessoryStack duoStack;
    AccessoryStack triStack;
    AccessoryStack tetStack;

    public StacksUsed(AccessoryStack monStack, AccessoryStack duoStack, AccessoryStack triStack, AccessoryStack tetStack) {
        this.monStack = monStack;
        this.duoStack = duoStack;
        this.triStack = triStack;
        this.tetStack = tetStack;
    }
}
