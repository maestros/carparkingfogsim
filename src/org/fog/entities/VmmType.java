package org.fog.entities;

public enum VmmType {
	Xen("Xen"),
	OPENSTACK("OpenStack");
	
	private String name;
	
	VmmType(String vmmType) {
		this.name = vmmType;
	}
	
	public String toString() {
		return name;
	}
}