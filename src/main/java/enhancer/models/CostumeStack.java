package enhancer.models;

public enum CostumeStack {

	TEN(12, 60, 20, 15, 5), FIFTEEN(21, 72.6, 25, 18.75, 6.25), TWENTY(33, 75.6, 30, 22.5, 7.5), TWENTYFIVE(53, 78.6, 35, 26.25, 8.75), THIRY(84, 81.6, 40, 30, 10), THIRYFIVE(136, 84.6, 45, 33.75, 11.25), FOURTY(230, 87.6, 50, 37.5, 12.5),
	FOURTYFIVE(406, 90, 51, 40.65, 13.75), FIFTYFIVE(850, 90, 53, 42.75, 16.25), SIXTY_FREE(0, 90, 54, 43.5, 17.5), SEVENTY_FREE(0, 90, 56, 45, 20), EIGHTY_FREE(0, 90, 58, 46.5, 22.5), NINETY_FREE(0, 90, 60, 48, 25),
	HUNDRED_FREE(0, 90, 62, 49.5, 27.5), HUNDREDTEN_FREE(0, 90, 64, 51, 30);

	public int blackStoneCount;
	public double mon;
	public double duo;
	public double tri;
	public double tet;

	private CostumeStack(int blackStoneCount, double mon, double duo, double tri, double tet) {
		this.blackStoneCount = blackStoneCount;
		this.mon = mon;
		this.duo = duo;
		this.tri = tri;
		this.tet = tet;
	}
}
