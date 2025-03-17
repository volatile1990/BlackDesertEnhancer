package com.bdo.enhancer.model.item;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

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

	public boolean isCostume() {
		return StringUtils.containsIgnoreCase(this.getName(), "Silver");
	}
}
