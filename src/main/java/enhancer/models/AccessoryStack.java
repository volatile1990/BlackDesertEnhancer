package enhancer.models;

public enum AccessoryStack {

    TEN(12, 50, 20, 15, 5, 10), FIFTEEN(21, 62.5, 25, 18.75, 6.25, 15), TWENTY(33, 71, 30, 22.5, 7.5, 20), TWENTYFIVE(53, 73.5, 35, 26.25, 8.75, 25), THIRTY(84, 76, 40, 30, 10, 30), THIRYFIVE(136, 78.5, 45, 33.75, 11.25, 35), FOURTY(230, 81, 50, 37.5, 12.5, 40),
    FOURTYFIVE(406, 83.5, 51, 40.65, 13.75, 45), SIXTY(1540, 90, 54, 42.9, 17.5, 60), SIXTY_FREE(0, 90, 54, 43.5, 17.5, 60), SEVENTY_FREE(0, 90, 56, 45, 20, 70), EIGHTY_FREE(0, 90, 58, 46.5, 22.5, 80), NINETY_FREE(0, 90, 60, 48, 25, 90),
    HUNDRED_FREE(0, 90, 62, 49.5, 27.5, 100), HUNDREDTEN_FREE(0, 90, 64, 51, 30, 110);

    public final int blackStoneCount;
    public final double mon;
    public final double duo;
    public final double tri;
    public final double tet;
    public final int stackCount;

    AccessoryStack(int blackStoneCount, double mon, double duo, double tri, double tet, int stackCount) {
        this.blackStoneCount = blackStoneCount;
        this.mon = mon;
        this.duo = duo;
        this.tri = tri;
        this.tet = tet;
        this.stackCount = stackCount;
    }
}
