# Car Parking System Fog Simulation based on iFogSim
This repository stores the source code of the simulation that was implemented
for the Fog and Edge Computing module of the Cloud Computing postgraduade course at the National College of Ireland.

This simulation is based on iFogSim.

iFogSim is an open-source Toolkit for Modeling and Simulation of Resource Management Techniques in Internet of Things, Edge and Fog Computing Environments.

# Prerequsites
 * Git
 * Java 8
 * Eclipse

# How to install iFogSim and the simulation?
* Clone this repository:
```
git clone https://github.com/maestros/carparkingfogsim.git
```
* Open Eclipse and import the carparkingfogsim Java project into Eclipse.
* If needed, include the JARs (except the CloudSim ones) to the buildpath of the Eclipse project.  

# How to run the Car Parking Fog simulation?
For the requirements of this simulation, the class `org.fog.test.perfeval.CarParkingFogSimulation` was created.
Additionally, many files of the iFogSim were modified for bug fixes and improvements. Thus, the `CarParkingFogSimulation` must run on the modified version of iFogSim that is hosted in this repository. **Important:** Do not copy and run the  `org.fog.test.perfeval.CarParkingFogSimulation` class in a vanilla iFogSim installation.

* Run the `main` method of the `org.fog.test.perfeval.CarParkingFogSimulation` class, using the modified iFogSim that is hosted in this repository.

# How to change simulation configuration
The `CarParkingFogSimulation` class contains a constant named `CONFIG_NAME`. The possible values of this constant are:
```
ConfigName.CONFIG_1
ConfigName.CONFIG_2
ConfigName.CONFIG_3
ConfigName.CONFIG_4
ConfigName.CONFIG_5
ConfigName.CONFIG_6
```
Each of these configuration names corresponds to one of the pre-defined configurations, as described in the project report.
To run a different configuration, change the value of the `CONFIG_NAME` and re-run the `main` method of the `CarParkingFogSimulation` class.

Read the project report for more information on the pre-defined configurations.

# Project Report
At the root of this repository you will find the file `x20124066_FEC_CA_Apostolos_Giannakidis.pdf`
that fully documents the project and the results of the simulation.

# Credits
Apostolos Giannakidis, x20124066@student.ncirl.ie

PGDCLOUD, School of Computing

National College of Ireland, Dublin 
