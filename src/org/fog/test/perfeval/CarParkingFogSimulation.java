package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.ArchType;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.OsType;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.entities.VmmType;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.entities.SensorType;
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
	private static Map<ConfigName, Configuration> configs = new HashMap<ConfigName, Configuration>();
	
	private static final int NUMBER_OF_EDGE_NODES;
	private static final int NUMBER_OF_AREAS;
	private static final int SENSORS_PER_AREA;
	private static final int CAMERAS_PER_AREA;
	private static final boolean CLOUD_BASED;
	
	private static class Configuration {
		int edgeNodesCount;
		int areasCount;
		int sensorsPerArea;
		int camerasPerArea;
		boolean cloudBased;
		
		Configuration(int edgeNodesCount, int areasCount,
				int sensorsPerArea, int camerasPerArea, boolean cloudBased) {
			this.edgeNodesCount = edgeNodesCount;
			this.areasCount = areasCount;
			this.sensorsPerArea = sensorsPerArea;
			this.camerasPerArea = camerasPerArea;
			this.cloudBased = cloudBased;
		}
		
		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append("edgeNodesCount=").append(edgeNodesCount).append("\n");
			str.append("areasCount=").append(areasCount).append("\n");
			str.append("sensorsPerArea=").append(sensorsPerArea).append("\n");
			str.append("camerasPerArea=").append(camerasPerArea).append("\n");
			str.append("cloudBased=").append(cloudBased).append("\n");
			return str.toString();
		}
	}
	
	private enum ConfigName {
		CONFIG_1,
		CONFIG_2,
		CONFIG_3,
		CONFIG_4,
		CONFIG_5;
	}
	
	static {
		Log.printLine("Loading Simulation Configuration...");
		createConfigurations();
		// change the config name in the configs map
		// in order to run a simulation with different configuration
		Configuration config = configs.get(ConfigName.CONFIG_1);

		NUMBER_OF_EDGE_NODES = config.edgeNodesCount;
		NUMBER_OF_AREAS = config.areasCount;
		SENSORS_PER_AREA = config.sensorsPerArea;
		CAMERAS_PER_AREA = config.camerasPerArea;
		CLOUD_BASED = config.cloudBased;
		
		Log.printLine("Loaded Simulation Configuration:");
		Log.printLine(config);
	}
	
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
			
			for(int i=0; i < NUMBER_OF_AREAS; i++) {
				String areaName = String.format("area#%s", i);
				int index = i%NUMBER_OF_EDGE_NODES;
				FogDevice edgeNode = edgeNodes.get(index);
				addArea(areaName, broker.getId(), appId,  edgeNode.getId());
			}

			Controller controller = null;
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("camera")){
					moduleMapping.addModuleToDevice("motion_detector", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Camera
				}
				if(device.getName().startsWith("ir-sensor")){
					moduleMapping.addModuleToDevice("ir_detector", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Camera
				}
			}
			
			moduleMapping.addModuleToDevice("user_interface", "cloud"); // fixing instances of User Interface module in the Cloud
			if(CLOUD_BASED){
				// if the mode of deployment is cloud-based
				moduleMapping.addModuleToDevice("object_detector", "cloud"); // placing all instances of Object Detector module in the Cloud
				moduleMapping.addModuleToDevice("object_tracker", "cloud"); // placing all instances of Object Tracker module in the Cloud
			}

			controller = new Controller("master-controller", fogDevices, sensors, actuators);
			
			controller.submitApplication(application, 
					(CLOUD_BASED)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
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
		FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000,
				1, 4, 107.339, 83.4333);
		proxy.setParentId(parentId);
		proxy.setUplinkLatency(120); // latency of connection between GW and Cloud is 120 ms
		fogDevices.add(proxy);
		return proxy;
	}
	
	private static void createEdgeNodes(int parentId) {
		for (int i=0; i < NUMBER_OF_EDGE_NODES; i++) {
			FogDevice edgeNode = createEdgeNode("EdgeNode#"+i, parentId);
			edgeNodes.add(edgeNode);
		}
	}
	
	private static FogDevice createEdgeNode(String id, int parentId) {
		FogDevice edgeNode = createFogDevice("edge-node-"+id, 2800, 4000, 10000, 10000,
				1, 2, 107.339, 83.4333);
		edgeNode.setUplinkLatency(2); // latency of connection between edge node and gateway is 2 ms
		edgeNode.setParentId(parentId);
		fogDevices.add(edgeNode);
		return edgeNode;
	}

	private static void addArea(String id, int userId, String appId, int edgeNodeId){
		for(int i=0; i< SENSORS_PER_AREA; i++){
			String mobileId = id+"-"+i;
			FogDevice irSensor = addIRSensor(mobileId, userId, appId, edgeNodeId); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
			irSensor.setUplinkLatency(2); // latency of connection between IR-Sensor and edge node is 2 ms
			fogDevices.add(irSensor);
		}
		
		for(int i=0; i< CAMERAS_PER_AREA; i++){
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
		Actuator ptz = new Actuator("ptz-"+id, userId, appId, "PTZ_CONTROL");
		actuators.add(ptz);
		sensor.setGatewayDeviceId(irSensor.getId());
		sensor.setLatency(1.0);  // latency of connection between IR Sensor and the parent Smart Camera is 1 ms;
		ptz.setGatewayDeviceId(irSensor.getId());
		ptz.setLatency(1.0);  // latency of connection between PTZ Control and the parent Smart Camera is 1 ms
		return irSensor;
	}
	
	private static FogDevice addNonPTZCamera(String id, int userId, String appId, int parentId){
		FogDevice camera = createFogDevice("camera-"+id, 500, 1000, 10000, 10000,
				3, 2, 87.53, 82.44);
		camera.setParentId(parentId);
		Sensor sensor = new Sensor("s-"+id, SensorType.CAMERA.toString(), userId, appId, new DeterministicDistribution(5)); // inter-transmission time of camera (sensor) follows a deterministic distribution
		sensors.add(sensor);
		Actuator ptz = new Actuator("ptz-"+id, userId, appId, "PTZ_CONTROL");
		actuators.add(ptz);
		sensor.setGatewayDeviceId(camera.getId());
		sensor.setLatency(1.0);  // latency of connection between camera (sensor) and the parent Smart Camera is 1 ms
		ptz.setGatewayDeviceId(camera.getId());
		ptz.setLatency(1.0);  // latency of connection between PTZ Control and the parent Smart Camera is 1 ms
		return camera;
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
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("object_detector", 10);
		application.addAppModule("motion_detector", 10);
		application.addAppModule("object_tracker", 10);
		application.addAppModule("user_interface", 10);
		
		application.addAppModule("ir_detector", 10);
		application.addAppModule("parking_space_detector", 10);
		application.addAppModule("parking_space_tracker", 10);
		
		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */
		application.addAppEdge(SensorType.IR_SENSOR.toString(), "ir_detector", 1000, 20000, SensorType.IR_SENSOR.toString(), Tuple.UP, AppEdge.SENSOR); // adding edge from CAMERA (sensor) to Motion Detector module carrying tuples of type CAMERA
		application.addAppEdge("CAMERA", "motion_detector", 1000, 20000, "CAMERA", Tuple.UP, AppEdge.SENSOR); // adding edge from CAMERA (sensor) to Motion Detector module carrying tuples of type CAMERA
		application.addAppEdge("ir_detector", "parking_space_detector", 2000, 2000, "IR_STREAM", Tuple.UP, AppEdge.MODULE); // adding edge from Motion Detector to Object Detector module carrying tuples of type MOTION_VIDEO_STREAM
		application.addAppEdge("motion_detector", "object_detector", 2000, 2000, "MOTION_VIDEO_STREAM", Tuple.UP, AppEdge.MODULE); // adding edge from Motion Detector to Object Detector module carrying tuples of type MOTION_VIDEO_STREAM
		application.addAppEdge("object_detector", "user_interface", 500, 2000, "DETECTED_OBJECT", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to User Interface module carrying tuples of type DETECTED_OBJECT
		application.addAppEdge("object_detector", "object_tracker", 1000, 100, "OBJECT_LOCATION", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
		application.addAppEdge("object_tracker", "PTZ_CONTROL", 100, 28, 100, "PTZ_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR); // adding edge from Object Tracker to PTZ CONTROL (actuator) carrying tuples of type PTZ_PARAMS
		
		/*
		 * Defining the input-output relationships (represented by selectivity) of the application modules. 
		 */
		application.addTupleMapping("ir_detector", SensorType.IR_SENSOR.toString(), "IR_STREAM", new FractionalSelectivity(1.0)); // 1.0 tuples of type MOTION_VIDEO_STREAM are emitted by Motion Detector module per incoming tuple of type CAMERA
		application.addTupleMapping("motion_detector", "CAMERA", "MOTION_VIDEO_STREAM", new FractionalSelectivity(1.0)); // 1.0 tuples of type MOTION_VIDEO_STREAM are emitted by Motion Detector module per incoming tuple of type CAMERA
		application.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "OBJECT_LOCATION", new FractionalSelectivity(1.0)); // 1.0 tuples of type OBJECT_LOCATION are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
		application.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "DETECTED_OBJECT", new FractionalSelectivity(0.05)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
		application.addTupleMapping("parking_space_detector", SensorType.IR_SENSOR.toString(), "IR_STREAM", new FractionalSelectivity(1.0)); // 1.0 tuples of type MOTION_VIDEO_STREAM are emitted by Motion Detector module per incoming tuple of type CAMERA

		/*
		 * Defining application loops (maybe incomplete loops) to monitor the latency of. 
		 * Here, we add two loops for monitoring : Motion Detector -> Object Detector -> Object Tracker and Object Tracker -> PTZ Control
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>() {
			{
				add("motion_detector");
				add("object_detector");
				add("object_tracker");
			}
		});
		final AppLoop loop2 = new AppLoop(new ArrayList<String>() {
			{
				add("ir_detector");
				add("parking_space_detector");
			}
		});
		List<AppLoop> loops = new ArrayList<AppLoop>() {
			{
				add(loop1);
				add(loop2);
			}
		};
		
		application.setLoops(loops);
		return application;
	}
	
	private static void createConfigurations() {
		configs.put(ConfigName.CONFIG_1, new Configuration(3, 3, 20, 2, false) {});
		
	}
}