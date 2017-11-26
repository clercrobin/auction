package template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;

public class MyPlanPierre {
    private final ArrayList<ArrayList<MyActionPierre>> mPlans;
    private HashMap<Integer, Integer> mPickupActions = null;
    private HashMap<Integer, Integer> mDeliveryActions = null;
    private HashMap<Integer, Integer> mTaskToVehicle = null;
    private ArrayList<ArrayList<Task>> mVehicleToTasks = null;
    private boolean mMappingsDirty = true;
    private double mCost;
    private boolean mCostDirty = true;

    public MyPlanPierre(int nbVehicles) {
        mPlans = new ArrayList<ArrayList<MyActionPierre>>(nbVehicles);
        for (int i = 0; i < nbVehicles; ++i)
            mPlans.add(new ArrayList<MyActionPierre>());
    }

    public MyPlanPierre(ArrayList<ArrayList<MyActionPierre>> plans) {
        mPlans = plans;
    }

    private ArrayList<ArrayList<MyActionPierre>> copyPlans() {
        // Do a deep copy
        ArrayList<ArrayList<MyActionPierre>> plans = new ArrayList<ArrayList<MyActionPierre>>();
        for (int i = 0; i < mPlans.size(); ++i) {
            plans.add(new ArrayList<MyActionPierre>(mPlans.get(i)));
        }
        return plans;
    }

    public void computeMappings(List<Vehicle> vehicles) {
        // Do not recompute these mappings
        if (!mMappingsDirty)
            return;
        // Create the hashmaps and arrays
        mPickupActions = new HashMap<Integer, Integer>();
        mDeliveryActions = new HashMap<Integer, Integer>();
        mTaskToVehicle = new HashMap<Integer, Integer>();
        mVehicleToTasks = new ArrayList<ArrayList<Task>>(vehicles.size());
        for (int i = 0; i < vehicles.size(); ++i)
            mVehicleToTasks.add(new ArrayList<Task>());
        // Precompute for each task which vehicle carries it and
        // where is the pickup and delivery action in its plan
        for (Vehicle vehicle : vehicles) {
            ArrayList<MyActionPierre> plan = mPlans.get(vehicle.id());
            for (int i = 0; i < plan.size(); ++i) {
                MyActionPierre action = plan.get(i);
                if (action.type == MyActionPierre.Type.PICK_UP) {
                    mPickupActions.put(action.task.id, i);
                    mTaskToVehicle.put(action.task.id, vehicle.id());
                    mVehicleToTasks.get(vehicle.id()).add(action.task);
                }
                else
                    mDeliveryActions.put(action.task.id, i);
            }
        }
        mMappingsDirty = false;
    }

    public MyPlanPierre addTaskTo(Task task, Vehicle vehicle) {
        // Create new plans
        ArrayList<ArrayList<MyActionPierre>> plans = copyPlans();
        ArrayList<MyActionPierre> plan = plans.get(vehicle.id());
        // Add the actions
        plan.add(new MyActionPierre(MyActionPierre.Type.PICK_UP, task));
        plan.add(new MyActionPierre(MyActionPierre.Type.DELIVER, task));
        // Return the new plan
        return new MyPlanPierre(plans);
    }

    public MyPlanPierre moveTask(Vehicle from, Vehicle to, Task task) {
        // Check if the vehicle can take the task
        if (task.weight > to.capacity())
            return null;
        // Create new plans
        ArrayList<ArrayList<MyActionPierre>> plans = copyPlans();
        ArrayList<MyActionPierre> planFrom = plans.get(from.id());
        ArrayList<MyActionPierre> planTo = plans.get(to.id());
        // Modify planFrom and planTo
        MyActionPierre deliveryAction = planFrom.remove((int)mDeliveryActions.get(task.id));
        MyActionPierre pickupAction = planFrom.remove((int)mPickupActions.get(task.id));
        planTo.add(pickupAction);
        planTo.add(deliveryAction);
        // Return the new plan
        return new MyPlanPierre(plans);
    }

    public MyPlanPierre changeOrder(Vehicle vehicle, int iAction1, int iAction2) {
        // Check if the swap is valid
        ArrayList<MyActionPierre> plan = mPlans.get(vehicle.id());
        MyActionPierre action1 = plan.get(iAction1);
        MyActionPierre action2 = plan.get(iAction2);
        // We can't swap the two actions for a same task
        if (action1.task.id == action2.task.id)
            return null;
        // We can't put a delivery action before the corresponding pickup
        if ((action1.type == MyActionPierre.Type.DELIVER && mPickupActions.get(action1.task.id) >= iAction2) ||
                (action2.type == MyActionPierre.Type.DELIVER && mPickupActions.get(action2.task.id) >= iAction1) ||
                (action1.type == MyActionPierre.Type.PICK_UP && mDeliveryActions.get(action1.task.id) < iAction2) ||
                (action2.type == MyActionPierre.Type.PICK_UP && mDeliveryActions.get(action2.task.id) < iAction1))
            return null;
        // Create new plans
        ArrayList<ArrayList<MyActionPierre>> plans = copyPlans();
        plan = plans.get(vehicle.id());
        // Swap the tasks
        plan.set(iAction1, action2);
        plan.set(iAction2, action1);
        // Finally check if the capacity is never exceeded
        int weight = 0;
        for (MyActionPierre action : plan) {
            if (action.type == MyActionPierre.Type.PICK_UP) {
                weight += action.task.weight;
                if (weight > vehicle.capacity())
                    return null;
            }
            else
                weight -= action.task.weight;
        }
        // Return the new plan
        return new MyPlanPierre(plans);
    }

    public ArrayList<Vehicle> getVehiclesWithNonEmptyPlan(List<Vehicle> vehicles) {
        ArrayList<Vehicle> vehiclesWithPlan = new ArrayList<Vehicle>();
        for (Vehicle vehicle : vehicles) {
            if (!mPlans.get(vehicle.id()).isEmpty())
                vehiclesWithPlan.add(vehicle);
        }
        return vehiclesWithPlan;
    }

    public ArrayList<ArrayList<Task>> getVehicleToTasks() {
        return mVehicleToTasks;
    }

    public int getNbActions(Vehicle vehicle) {
        return mPlans.get(vehicle.id()).size();
    }

    public double getCost(List<Vehicle> vehicles) {
        if (mCostDirty) {
            mCost = 0.0;
            for (Vehicle vehicle : vehicles) {
                City current = vehicle.getCurrentCity();
                for (MyActionPierre action : mPlans.get(vehicle.id())) {
                    if (action.type == MyActionPierre.Type.PICK_UP) {
                        mCost += current.distanceTo(action.task.pickupCity)
                                * vehicle.costPerKm();
                        current = action.task.pickupCity;
                    } else {
                        mCost += current.distanceTo(action.task.deliveryCity)
                                * vehicle.costPerKm();
                        current = action.task.deliveryCity;
                    }
                }
            }
            mCostDirty = false;
        }
        return mCost;
    }

    public List<Plan> convert(List<Vehicle> vehicles, TaskSet tasks) {
        // Create a hashmap to map the old tasks the new ones
        HashMap<Integer, Task> newTasks = new HashMap<Integer, Task>();
        for (Task task : tasks)
            newTasks.put(task.id, task);

        // Create plans for all vehicles
        List<Plan> plans = new ArrayList<Plan>();
        for (Vehicle vehicle : vehicles) {
            City current = vehicle.getCurrentCity();
            Plan plan = new Plan(current);
            for (MyActionPierre action : mPlans.get(vehicle.id())) {
                // Pick up
                if (action.type == MyActionPierre.Type.PICK_UP) {
                    Task task = action.task;
                    for (City city : current.pathTo(task.pickupCity))
                        plan.appendMove(city);
                    plan.appendPickup(newTasks.get(task.id));
                    current = task.pickupCity;
                    // Delivery
                } else {
                    Task task = action.task;
                    for (City city : current.pathTo(task.deliveryCity))
                        plan.appendMove(city);
                    plan.appendDelivery(newTasks.get(task.id));
                    current = task.deliveryCity;
                }
            }
            plans.add(plan);
        }
        return plans;
    }

    public String toString() {
        String s = new String();
        for (int i = 0; i < mPlans.size(); ++i) {
            s += "Vehicle " + i + ": ";
            for (MyActionPierre action : mPlans.get(i))
                s += action.toString() + ", ";
            s += "\n";
        }
        return s;
    }
}