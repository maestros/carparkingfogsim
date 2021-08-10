package org.fog.entities;

public enum OsType {
	Linux("linux"),
	WINDOWS("WINDOWS");
	
	private String name;
	
	OsType(String osType) {
		this.name = osType;
	}
	
	public String toString() {
		return name;
	}
}