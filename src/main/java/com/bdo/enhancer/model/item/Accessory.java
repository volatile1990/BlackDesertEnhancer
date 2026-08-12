package com.bdo.enhancer.model.item;

public class Accessory extends Item {

	public Accessory(String name, int id) {
		super(name, id);
	}

	public Accessory(String name, int id, EnhancementType enhancementType) {
		super(name, id, enhancementType);
	}
}
