package org.fog.entities;

public enum ArchType {
	x86("x86"),
	x64("x64");
	
	private String name;
	
	ArchType(String archType) {
		this.name = archType;
	}
	
	public String toString() {
		return name;
	}
}