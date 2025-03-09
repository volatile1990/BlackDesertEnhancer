package enhancer.models.market;

import lombok.Data;

@Data
public class Item {

	private String name;
	private int id;
	private long basePrice;
	private int baseStock;
	private long duoPrice;
	private long triPrice;
	private long tetPrice;

	public Item(String name, int id) {
		this.name = name;
		this.id = id;
	}
}
