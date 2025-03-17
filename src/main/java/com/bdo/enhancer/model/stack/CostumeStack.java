package com.bdo.enhancer.model.stack;

public class CostumeStack extends AbstractStack {
    // Statische Factory-Methoden für die vordefinierten Stacks
    public static final CostumeStack TEN = new CostumeStack(12, 60, 20, 15, 5, 10);
    public static final CostumeStack FIFTEEN = new CostumeStack(21, 72.6, 25, 18.75, 6.25, 15);
    public static final CostumeStack TWENTY = new CostumeStack(33, 75.6, 30, 22.5, 7.5, 20);
    public static final CostumeStack TWENTYFIVE = new CostumeStack(53, 78.6, 35, 26.25, 8.75, 25);
    public static final CostumeStack THIRTY = new CostumeStack(84, 81.6, 40, 30, 10, 30);
    public static final CostumeStack THIRYFIVE = new CostumeStack(136, 84.6, 45, 33.75, 11.25, 35);
    public static final CostumeStack FOURTY = new CostumeStack(230, 87.6, 50, 37.5, 12.5, 40);
    public static final CostumeStack FOURTYFIVE = new CostumeStack(406, 90, 51, 40.65, 13.75, 45);
    public static final CostumeStack FIFTYFIVE = new CostumeStack(850, 90, 53, 42.75, 16.25, 55);
    public static final CostumeStack SIXTY = new CostumeStack(1540, 90, 54, 43.5, 17.5, 60);
    public static final CostumeStack SEVENTY_FREE = new CostumeStack(0, 90, 56, 45, 20, 70);
    public static final CostumeStack EIGHTY_FREE = new CostumeStack(0, 90, 58, 46.5, 22.5, 80);
    public static final CostumeStack NINETY_FREE = new CostumeStack(0, 90, 60, 48, 25, 90);
    public static final CostumeStack HUNDRED_FREE = new CostumeStack(0, 90, 62, 49.5, 27.5, 100);
    public static final CostumeStack HUNDREDTEN_FREE = new CostumeStack(0, 90, 64, 51, 30, 110);

    // Eine Sammlung aller vordefinierten Stacks für einfachen Zugriff
    public static final CostumeStack[] VALUES = {
            TEN, FIFTEEN, TWENTY, TWENTYFIVE, THIRTY, THIRYFIVE, FOURTY, FOURTYFIVE, FIFTYFIVE,
            SIXTY, SEVENTY_FREE, EIGHTY_FREE, NINETY_FREE, HUNDRED_FREE, HUNDREDTEN_FREE
    };

    private CostumeStack(int blackStoneCount, double mon, double duo, double tri, double tet, int stackCount) {
        super(blackStoneCount, mon, duo, tri, tet, stackCount);
    }

    // Methode zum Finden eines Stacks nach Stack-Anzahl
    public static CostumeStack findByStackCount(int stackCount) {
        for (CostumeStack stack : VALUES) {
            if (stack.getStackCount() == stackCount) {
                return stack;
            }
        }
        throw new IllegalArgumentException("Kein CostumeStack mit stackCount " + stackCount + " gefunden");
    }
}