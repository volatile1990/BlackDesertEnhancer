package com.bdo.enhancer.model.item;

import lombok.Data;

@Data
public class Item {

	private String name;
	private int id;
	private final EnhancementType enhancementType;
	private long basePrice;
	private int baseStock;
	private long duoPrice;
	private long triPrice;
	private long tetPrice;

	public Item(String name, int id) {
		this(name, id, EnhancementType.fromItemName(name));
	}

	public Item(String name, int id, EnhancementType enhancementType) {
		this.name = name;
		this.id = id;
		this.enhancementType = enhancementType;
	}

	public boolean isCostume() {
		return enhancementType == EnhancementType.SILVER_EMBROIDERED_CLOTHING;
	}

	public boolean isManosClothing() {
		return enhancementType == EnhancementType.MANOS_CLOTHING;
	}

	public boolean usesFailstacks() {
		return enhancementType.usesFailstacks();
	}
}
