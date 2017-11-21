package template;

import java.util.*;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.topology.Topology.City;

public class MyPlan implements Cloneable {

	/* maps a vehicle id to a list of task ordered by order of action */

    private int nbVehicles;
    private HashMap<Integer, LinkedList<MyAction>> repartition;

    public MyPlan(int nbVehicles_) {
        this.nbVehicles = nbVehicles_;
        repartition = new HashMap<Integer, LinkedList<MyAction>>(nbVehicles_);
        for (int i = 0; i < nbVehicles_; i++) {
            repartition.put(i, new LinkedList<MyAction>()); // We initialize
            // with
            // an empty linked
            // list
        }
    }

    public int getNbVehicles() {
        return nbVehicles;
    }

    @Override
    protected MyPlan clone() {
        MyPlan s = new MyPlan(nbVehicles);
        s.setPossiblePlan(new HashMap<Integer, List<MyAction>>(this.repartition));
        return s;
    }

    public void print() {
        for (Integer name : repartition.keySet()) {
            String key = name.toString();
            String value = repartition.get(name).toString();
            System.out.println("Vehicle " + key + " " + value);
        }
    }

    public void addTask(int vehicleId, Task task, double rand) {
        if (task == null)
            return;

        // Giving only one pickup or one delivery makes no sense, we would have
        // an invalid state
        MyAction pickup = new MyAction(task, true);
        MyAction delivery = new MyAction(task, false);

        int length = repartition.get(vehicleId).size();
        if (rand<0.33){
            this.repartition.get(vehicleId).addFirst(delivery);
            this.repartition.get(vehicleId).addFirst(pickup);
        } else if (rand<0.66) {
            this.repartition.get(vehicleId).addLast(pickup);
            this.repartition.get(vehicleId).addLast(delivery);
        } else {
            this.repartition.get(vehicleId).addFirst(pickup);
            this.repartition.get(vehicleId).addLast(delivery);
        }

    }

    public void removeTask(int vehicleId, Task task) {
        Iterator<MyAction> vehicleActions = this.repartition.get(vehicleId).iterator();
        if (task == null)
            return;

        int taskId = task.id;
        while (vehicleActions.hasNext()) {
            MyAction action = vehicleActions.next();
            if (action.getTask().id == taskId)
                vehicleActions.remove();
        }
    }

    public boolean isValid(Vehicle[] vehicles, int nbTasks) {

        List<Integer> alreadyPickedUpTasks = new ArrayList<Integer>();
        List<Integer> alreadyDeliveredTasks = new ArrayList<Integer>();

        int totalActions = 0;
        for (int i = 0; i < nbVehicles; i++) {
            int load = 0;

            List<MyAction> vehicleActions = repartition.get(i);

            totalActions += vehicleActions.size();

            Set<Integer> carriedTasks = new HashSet<Integer>();
            for (MyAction action : vehicleActions) {
                if (action.isPickup()) {
                    load += action.getTask().weight;

                    if (load > vehicles[i].capacity())
                        return false;
                    if (alreadyPickedUpTasks.contains(action.getTask().id))
                        return false;
                    alreadyPickedUpTasks.add(action.getTask().id);
                    carriedTasks.add(action.getTask().id);

                } else {
                    if (alreadyDeliveredTasks.contains(action.getTask().id))
                        return false;
                    if (!carriedTasks.contains(action.getTask().id))
                        return false;

                    alreadyDeliveredTasks.add(action.getTask().id);

                    load -= action.getTask().weight;

                }
            }
        }

        if (totalActions / 2 != nbTasks)
            return false;

        return true;
    }

    public LinkedList<MyAction> getVehicleActions(int vehicleId) {
        if (repartition.containsKey(vehicleId)) {
            if (repartition.get(vehicleId).isEmpty())
                return null;
            else
                return repartition.get(vehicleId);
        } else
            return null;
    }

    public int getNbActionsVehicle(int vehicleId) {
        return repartition.get(vehicleId).size();
    }

    public MyPlan interSwapTasks(int vehicleId1, int vehicleId2, int taskIndex, double rand) {

        MyAction action = getVehicleActions(vehicleId1).get(taskIndex);

        if (action != null && vehicleId1 < this.nbVehicles && vehicleId2 < this.nbVehicles) {
            this.removeTask(vehicleId1, action.getTask());
            this.addTask(vehicleId2, action.getTask(),rand);
        }

        return this;
    }

    public MyPlan intraSwapActions(int vehicleId, int actionIndex1, int actionIndex2) {
        MyPlan neighbor = this.clone();

        LinkedList<MyAction> currentVehicleActions = this.repartition.get(vehicleId);
        LinkedList<MyAction> newVehicleActions = new LinkedList<MyAction>();

        for (int i = 0; i < currentVehicleActions.size(); i++) {
            if (i == actionIndex1)
                newVehicleActions.add(currentVehicleActions.get(actionIndex2));
            else if (i == actionIndex2)
                newVehicleActions.add(currentVehicleActions.get(actionIndex1));
            else
                newVehicleActions.add(currentVehicleActions.get(i));
        }

        neighbor.repartition.put(vehicleId, newVehicleActions);

        return neighbor;
    }

    public double computePlanCost(Vehicle[] vehicles) {
        double totalCost = 0.0;

        for (int i = 0; i < vehicles.length; i++) {
            City tmpCity = vehicles[i].getCurrentCity();
            for (MyAction action : repartition.get(i)) {
                City nextCity = action.isPickup() ? action.getTask().pickupCity
                        : action.getTask().deliveryCity;
                totalCost += (tmpCity.distanceTo(nextCity) * vehicles[i].costPerKm());
                tmpCity = nextCity;
            }
        }

        return totalCost;
    }

    public Plan generatePlan(Vehicle vehicle) {
        int vehicleId = vehicle.id();
        City current = vehicle.getCurrentCity();

        Plan p = new Plan(current);

        List<MyAction> actions = repartition.get(vehicleId);

        City intermediateCity = current;
        for (MyAction action : actions) {
            City nextDestination = action.isPickup() ? action.getTask().pickupCity : action
                    .getTask().deliveryCity;

            for (City c : intermediateCity.pathTo(nextDestination))
                p.appendMove(c);

            if (action.isPickup())
                p.appendPickup(action.getTask());
            else
                p.appendDelivery(action.getTask());

            intermediateCity = nextDestination;
        }

        return p;
    }

    public void setPossiblePlan(HashMap<Integer, List<MyAction>> solution) {

        repartition.clear();

        for (Map.Entry<Integer, List<MyAction>> entry : solution.entrySet()) {
            LinkedList<MyAction> actions = new LinkedList<MyAction>();

            for (MyAction extendedAction : entry.getValue())
                actions.add(extendedAction);

            repartition.put(entry.getKey(), actions);
        }
    }

    public boolean isEquals(MyPlan s) {
        return true;
    }

}
