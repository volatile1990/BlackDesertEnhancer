package com.bdo.enhancer.model.stack;

public class AccessoryStack extends AbstractStack {
    // Statische Factory-Methoden für die vordefinierten Stacks
    public static final AccessoryStack TEN = new AccessoryStack(12, 50, 20, 15, 5, 10);
    public static final AccessoryStack FIFTEEN = new AccessoryStack(21, 62.5, 25, 18.75, 6.25, 15);
    public static final AccessoryStack TWENTY = new AccessoryStack(33, 71, 30, 22.5, 7.5, 20);
    public static final AccessoryStack TWENTYFIVE = new AccessoryStack(53, 73.5, 35, 26.25, 8.75, 25);
    public static final AccessoryStack THIRTY = new AccessoryStack(84, 76, 40, 30, 10, 30);
    public static final AccessoryStack THIRYFIVE = new AccessoryStack(136, 78.5, 45, 33.75, 11.25, 35);
    public static final AccessoryStack FOURTY = new AccessoryStack(230, 81, 50, 37.5, 12.5, 40);
    public static final AccessoryStack FOURTYFIVE = new AccessoryStack(406, 83.5, 51, 40.65, 13.75, 45);
    public static final AccessoryStack FIFTYFIVE = new AccessoryStack(850, 90, 53, 42.75, 16.25, 55);
    public static final AccessoryStack SIXTY = new AccessoryStack(1540, 90, 54, 42.9, 17.5, 60);
    public static final AccessoryStack SEVENTY_FREE = new AccessoryStack(0, 90, 56, 45, 20, 70);
    public static final AccessoryStack EIGHTY_FREE = new AccessoryStack(0, 90, 58, 46.5, 22.5, 80);
    public static final AccessoryStack NINETY_FREE = new AccessoryStack(0, 90, 60, 48, 25, 90);
    public static final AccessoryStack HUNDRED_FREE = new AccessoryStack(0, 90, 62, 49.5, 27.5, 100);
    public static final AccessoryStack HUNDREDTEN_FREE = new AccessoryStack(0, 90, 64, 51, 30, 110);

    // Eine Sammlung aller vordefinierten Stacks für einfachen Zugriff
    public static final AccessoryStack[] VALUES = {
            TEN, FIFTEEN, TWENTY, TWENTYFIVE, THIRTY, THIRYFIVE, FOURTY, FOURTYFIVE,FIFTYFIVE,
            SIXTY, SEVENTY_FREE, EIGHTY_FREE, NINETY_FREE, HUNDRED_FREE, HUNDREDTEN_FREE
    };

    private AccessoryStack(int blackStoneCount, double mon, double duo, double tri, double tet, int stackCount) {
        super(blackStoneCount, mon, duo, tri, tet, stackCount);
    }

    public static AccessoryStack findByStackCount(int stackCount) {
        for (AccessoryStack stack : VALUES) {
            if (stack.getStackCount() == stackCount) {
                return stack;
            }
        }
        throw new IllegalArgumentException("Kein AccessoryStack mit stackCount " + stackCount + " gefunden");
    }

}