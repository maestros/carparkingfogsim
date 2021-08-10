package org.fog.entities;

public enum SensorType {
	CAMERA("CAMERA"),
	IR_SENSOR("IR-SENSOR");
	
	private String name;
	
	SensorType(String sensorName) {
		this.name = sensorName;
	}
	
	public String toString() {
		return name;
	}
}