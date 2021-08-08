package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.VmSchedulerTimeSharedOverbookingEnergy;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.test.perfeval.CarParkingFogSimulation.ArchType;
import org.fog.test.perfeval.CarParkingFogSimulation.OsType;
import org.fog.test.perfeval.CarParkingFogSimulation.VmmType;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation setup for the FEC-based car parking system
 * @author Apostolos Giannakidis
 *
 */
public class CarParkingFogSimulation {
	private static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	private static List<Sensor> sensors = new ArrayList<Sensor>();
	private static List<Actuator> actuators = new ArrayList<Actuator>();
	private static List<FogDevice> edgeNodes = new ArrayList<FogDevice>();
	
	private static final int NUMBER_OF_EDGE_NODES = 3;
	private static final int NUMBER_OF_AREAS = 4;
	private static final int SENSORS_PER_AREA = 2;
	private static final int CAMERAS_PER_AREA = 2;

	private static final boolean CLOUD = false;
	
	public static void main(String[] args) {

		Log.printLine("Starting Car Parking FEC Simulation...");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = true; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			String appId = "car-parking"; // identifier of the application
			
			FogBroker broker = new FogBroker("broker");
			
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			FogDevice cloud = createCloudEnvironment();
			FogDevice gateway = createLocalGateway(cloud.getId());
			
			createEdgeNodes(gateway.getId());
			
			for(int i=1; i<=NUMBER_OF_AREAS; i++) {
				String areaName = String.format("area#%s", i);
				FogDevice edgeNode = edgeNodes.get(i%NUMBER_OF_EDGE_NODES);
				createArea(areaName, edgeNode.getId(), broker.getId(), appId);
			}

			Controller controller = null;
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			for(FogDevice device : fogDevices){
				moduleMapping.addModuleToDevice("motion_detector", device.getName());
			}

			moduleMapping.addModuleToDevice("user_interface", "cloud"); // fixing instances of User Interface module in the Cloud
			if(CLOUD){
				// if the mode of deployment is cloud-based
				moduleMapping.addModuleToDevice("object_detector", "cloud"); // placing all instances of Object Detector module in the Cloud
				moduleMapping.addModuleToDevice("object_tracker", "cloud"); // placing all instances of Object Tracker module in the Cloud
			}

			controller = new Controller("master-controller", fogDevices, sensors, actuators);
			
			controller.submitApplication(application, 
					(CLOUD)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
							:(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			Log.printLine("Car Parking FEC Simulation finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	
	private static FogDevice createCloudEnvironment() {
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		return cloud;
	}
	
	private static FogDevice createLocalGateway(int parentId) {
		FogDevice proxy = createFogDevice("local-network-gataway", 2800, 4000, 10000, 10000,
				1, 4, 107.339, 83.4333);
		proxy.setParentId(parentId);
		proxy.setUplinkLatency(120); // latency of connection between GW and Cloud is 120 ms
		fogDevices.add(proxy);
		return proxy;
	}
	
	private static void createEdgeNodes(int parentId) {
		for (int i=1; i <= NUMBER_OF_EDGE_NODES; i++) {
			FogDevice edgeNode = createEdgeNode("EdgeNode#"+i, parentId);
			edgeNodes.add(edgeNode);
		}
	}
	
	private static void createArea(String areaName, int parentId, int userId, String appId) {
		addArea(areaName, userId, appId, parentId);
	}
	
	private static FogDevice createEdgeNode(String id, int parentId) {
		FogDevice edgeNode = createFogDevice("edge-node-"+id, 2800, 4000, 10000, 10000,
				1, 2, 107.339, 83.4333);
		fogDevices.add(edgeNode);
		edgeNode.setUplinkLatency(2); // latency of connection between edge node and gateway is 2 ms
		edgeNode.setParentId(parentId);
		return edgeNode;
	}

	private static void addArea(String id, int userId, String appId, int edgeNodeId){
		for(int i=1; i<=SENSORS_PER_AREA; i++){
			String mobileId = id+"-"+i;
			FogDevice irSensor = addIRSensor(mobileId, userId, appId, edgeNodeId); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
			irSensor.setUplinkLatency(2); // latency of connection between IR-Sensor and edge node is 2 ms
			fogDevices.add(irSensor);
		}
		
		for(int i=1; i<=CAMERAS_PER_AREA; i++){
			String mobileId = id+"-"+i;
			FogDevice camera = addNonPTZCamera(mobileId, userId, appId, edgeNodeId); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
			camera.setUplinkLatency(2); // latency of connection between camera and edge node is 2 ms
			fogDevices.add(camera);
		}
	}
	
	private static FogDevice addIRSensor(String id, int userId, String appId, int parentId){
		FogDevice irSensor = createFogDevice("ir-sensor-"+id, 500, 1000, 10000, 10000,
				3, 1, 87.53, 82.44);
		irSensor.setParentId(parentId);
		Sensor sensor = new Sensor("s-"+id, SensorType.IR_SENSOR.toString(), userId, appId, new DeterministicDistribution(5)); // inter-transmission time of camera (sensor) follows a deterministic distribution
		sensors.add(sensor);
		sensor.setGatewayDeviceId(irSensor.getId());
		sensor.setLatency(1.0);  // latency of connection between IR Sensor and the parent Smart Camera is 1 ms
		//irSensor.setTotalCost(1);
		return irSensor;
	}
	
	private static FogDevice addNonPTZCamera(String id, int userId, String appId, int parentId){
		FogDevice camera = createFogDevice("camera-"+id, 500, 1000, 10000, 10000,
				3, 2, 87.53, 82.44);
		camera.setParentId(parentId);
		Sensor sensor = new Sensor("s-"+id, SensorType.CAMERA.toString(), userId, appId, new DeterministicDistribution(5)); // inter-transmission time of camera (sensor) follows a deterministic distribution
		sensors.add(sensor);
		sensor.setGatewayDeviceId(camera.getId());
		sensor.setLatency(1.0);  // latency of connection between camera (sensor) and the parent Smart Camera is 1 ms
		return camera;
	}

	protected static FogDevice createFogDevice(String name, double mips, int ram, long strg, double bPw, double iPw, double costPerMips,
			double costPerMem, double costPerStorage, double costPerBw) {
		List<Pe> processingElementsList = new ArrayList<Pe>();
		processingElementsList.add(new Pe(0, new PeProvisionerSimple(mips)));

		PowerHost host = new PowerHost(
				FogUtils.generateEntityId(),
				new RamProvisionerSimple(ram),
				new BwProvisionerSimple(Long.MAX_VALUE),
				strg,
				processingElementsList,
				new VmSchedulerTimeSharedOverbookingEnergy(processingElementsList),
				new FogLinearPowerModel(bPw, iPw)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(ArchType.x64,
				OsType.Linux, VmmType.Xen, host, 0.0,
				costPerMips, costPerMem, costPerStorage, costPerBw);
		
		try {
			
			return new FogDevice(name, characteristics, 
					new AppModuleAllocationPolicy(hostList),
					new LinkedList<Storage>(), 10, 0, 0, 0, costPerMips);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level,
			double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(Long.MAX_VALUE),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 2.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				ArchType.x86, OsType.Linux, VmmType.Xen,
				host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList),
					storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * Function to create the Intelligent Surveillance application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId);

		application.addAppModule("object_detector", 10);
		application.addAppModule("motion_detector", 10);
		application.addAppModule("object_tracker", 10);
		application.addAppModule("user_interface", 10);
		
		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */
		application.addAppEdge(SensorType.IR_SENSOR.toString(), "motion_detector", 1000, 20000, SensorType.IR_SENSOR.toString(), Tuple.UP, AppEdge.SENSOR); // adding edge from CAMERA (sensor) to Motion Detector module carrying tuples of type CAMERA
		application.addAppEdge(SensorType.CAMERA.toString(), "motion_detector", 1000, 20000, SensorType.CAMERA.toString(), Tuple.UP, AppEdge.SENSOR); // adding edge from CAMERA (sensor) to Motion Detector module carrying tuples of type CAMERA
		application.addAppEdge("motion_detector", "object_detector", 2000, 2000, "MOTION_VIDEO_STREAM", Tuple.UP, AppEdge.MODULE); // adding edge from Motion Detector to Object Detector module carrying tuples of type MOTION_VIDEO_STREAM
		application.addAppEdge("object_detector", "user_interface", 500, 2000, "DETECTED_OBJECT", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to User Interface module carrying tuples of type DETECTED_OBJECT
		application.addAppEdge("object_detector", "object_tracker", 1000, 100, "OBJECT_LOCATION", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
		
		/*
		 * Defining the input-output relationships (represented by selectivity) of the application modules. 
		 */
		application.addTupleMapping("motion_detector", SensorType.CAMERA.toString(), "MOTION_VIDEO_STREAM", new FractionalSelectivity(1.0)); // 1.0 tuples of type MOTION_VIDEO_STREAM are emitted by Motion Detector module per incoming tuple of type CAMERA
		application.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "OBJECT_LOCATION", new FractionalSelectivity(1.0)); // 1.0 tuples of type OBJECT_LOCATION are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
		application.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "DETECTED_OBJECT", new FractionalSelectivity(0.05)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
	
		/*
		 * Defining application loops (maybe incomplete loops) to monitor the latency of. 
		 * Here, we add two loops for monitoring : Motion Detector -> Object Detector -> Object Tracker and Object Tracker -> PTZ Control
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("motion_detector");add("object_detector");add("object_tracker");}});
		final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("object_tracker");add("PTZ_CONTROL");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);add(loop2);}};
		
		application.setLoops(loops);
		return application;
	}
	
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
}