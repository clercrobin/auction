
package template;

//the list of imports

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import logist.LogistSettings;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;

public class CentralizedTemplate {

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private int nbTasks;
    private long timeout_setup;
    private long timeout_plan;


    public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config" + File.separator + "settings_default.xml");
        } catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }

        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);

        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
    }


    public static MyPlan plan(List<Vehicle> vehicles, List<Task> tasks, long boundary) {
        // Initialization
        long tic = System.currentTimeMillis();
        long elapsedTime = 0;
        long boundaryTime = boundary;
        long lastImprovement = tic;
        boolean stop = false;

        final double prob = 0.5;
        int nbTasks = tasks.size();
        Vehicle[] vArray = new Vehicle[vehicles.size()];
        vArray = vehicles.toArray(vArray);
        Random r = new Random();

        //System.out.println(vArray);
        // Give all the tasks to the vehicle with maximum capacity
        MyPlan initialPlan = initialPlan(vArray, tasks);
        MyPlan currentPlan = initialPlan;
        MyPlan finalPlan = currentPlan;
        //currentPlan.print();

        int nbIterations = 0;
        while (!stop && tic + elapsedTime < boundaryTime) {
            //System.out.println("Iteration " + (nbIterations + 1));
            //System.out.println("Elapsed Time" + (elapsedTime));

            // Generate neighbors
            List<MyPlan> possibleNeighbors = new ArrayList<MyPlan>();
            possibleNeighbors.addAll(interChanges(currentPlan));
            possibleNeighbors.addAll(intraChanges(currentPlan));

            // Find the best neighbor
            MyPlan bestNeighbor = null;
            double minCost = Double.MAX_VALUE;
            for (MyPlan neighbor : possibleNeighbors) {
                if (neighbor.isValid(vArray, nbTasks)) {
                    double cost = neighbor.computePlanCost(vArray);
                    if (cost < minCost) {
                        bestNeighbor = neighbor;
                        minCost = cost;
                    }
                }
            }

            // Choose the next plan
            double randomNumber = r.nextDouble();
            double currentCost = currentPlan.computePlanCost(vArray);
            if (minCost < currentCost) {
                currentPlan = bestNeighbor;
                lastImprovement = System.currentTimeMillis();
            }
            if (randomNumber > prob && bestNeighbor != null) {
                currentPlan = bestNeighbor;
            }

            //System.out.println("Cost [" + currentCost + "]");

            // Update the best plan

            if (currentPlan.computePlanCost(vArray) < finalPlan.computePlanCost(vArray)) {
                finalPlan = currentPlan;
            }

            // Return the best plan
            if (r.nextDouble() > 0.99)
                currentPlan = finalPlan;

            // Check if we are stuck
            if (System.currentTimeMillis() - lastImprovement > 5000) {
                stop = true;
            }

            nbIterations++;
            elapsedTime = System.currentTimeMillis() - tic;
        }

        // Print the final possiblePlan.
        System.out.println("Costs : [" + finalPlan.computePlanCost(vArray) + "]");
        finalPlan.print();
        System.out.println("Number of iterations : " + nbIterations);

        long toc = System.currentTimeMillis();
        long duration = toc - tic;
        System.out.println("Computing duration : " + duration + " milliseconds.");

        /*// Compare with the naive solution
        System.out.println("Naive cost : " + initialPlan.computePlanCost(vArray));

        List<Plan> plans = new LinkedList<Plan>();

        for (Vehicle vehicle : vehicles) {
            plans.add(finalPlan.generatePlan(vehicle));
        }*/

        return finalPlan;
    }

    private static MyPlan initialPlan(Vehicle[] vehicles, List<Task> tasks) {
        // Look for the vehicle with the maximum capacity
        Vehicle maxVehicle = vehicles[0];
        for (Vehicle vehicle : vehicles) {
            if (vehicle.capacity() > maxVehicle.capacity())
                maxVehicle = vehicle;
        }
        MyPlan s = new MyPlan(vehicles.length);
        // Give all the tasks to this vehicle
        for (Task t : tasks)
            s.addTask(maxVehicle.id(), t, 0);
        return s;
    }

    private static Set<MyPlan> interChanges(MyPlan currentPlan) {
        // Swap tasks between different vehicles
        Random r = new Random();
/*        Set<MyPlan> neighbors = new HashSet<MyPlan>();
        for (int vehicleId = 0; vehicleId < currentPlan.getNbVehicles(); vehicleId++) {
            int nbActionsVehicle = currentPlan.getNbActionsVehicle(vehicleId);
            for (int i = 0; i < nbActionsVehicle - 1; i++) { // For each of the
                // vehicle actions
                for (int receiverVehicle = 0; receiverVehicle < currentPlan.getNbVehicles(); receiverVehicle++) {
                    if (vehicleId != receiverVehicle) {
                        MyPlan neighbor = currentPlan.clone();
                        neighbor = neighbor.interSwapTasks(vehicleId, receiverVehicle, i);
                        neighbors.addAll(intraChanges(neighbor));
                    }
                }
            }
        }

        return neighbors;*/
        int i, j, nbActionsVehicles, index;
        Set<MyPlan> neighbors = new HashSet<MyPlan>();
        int switchNumber;
        switchNumber = r.nextInt(10);
        for (int sw = 0; sw < switchNumber; sw++) {
            i = r.nextInt(currentPlan.getNbVehicles());
            j = r.nextInt(currentPlan.getNbVehicles());
            nbActionsVehicles = currentPlan.getNbActionsVehicle(i);
            if (nbActionsVehicles > 1) {
                index = r.nextInt(nbActionsVehicles - 1);
                if (i != j) {
                    MyPlan neighbor = currentPlan.clone();
                    neighbor = neighbor.interSwapTasks(i, j, index, 0.20);
                    neighbors.add(neighbor);
                    neighbors.addAll(intraChanges(neighbor));
                    neighbor = currentPlan.clone();
                    neighbor = neighbor.interSwapTasks(i, j, index, 0.50);
                    neighbors.add(neighbor);
                    neighbors.addAll(intraChanges(neighbor));
                    neighbor = currentPlan.clone();
                    neighbor = neighbor.interSwapTasks(i, j, index, 0.70);
                    neighbors.add(neighbor);
                    neighbors.addAll(intraChanges(neighbor));
                }
            }

        }
        return neighbors;
    }

    private static Set<MyPlan> intraChanges(MyPlan currentPlan) {
        // Change the order of actions of each vehicles
        Random r = new Random();
        int i, j;
        Set<MyPlan> neighbors = new HashSet<MyPlan>();
        for (int vehicleId = 0; vehicleId < currentPlan.getNbVehicles(); vehicleId++) {
            int length = currentPlan.getNbActionsVehicle(vehicleId);
            if (length >= 2) {
                int switchNumber;
                switchNumber = r.nextInt(5);
                for (int sw = 0; sw < switchNumber; sw++) {
                    i = r.nextInt(length);
                    j = r.nextInt(length);
                    MyPlan neighbor = currentPlan.intraSwapActions(vehicleId, i, j);
                    neighbors.add(neighbor);
                }
            }
        }
        return neighbors;
    }
}