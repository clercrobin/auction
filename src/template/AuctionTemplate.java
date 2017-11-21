package template;

//the list of imports
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import logist.LogistPlatform;
import logist.LogistSettings;
import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 * 
 */
@SuppressWarnings("unused")
public class AuctionTemplate implements AuctionBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;
	private Vehicle vehicle;
	private List<Vehicle> vehicles;
	private City currentCity;

	private MyPlan currentPlan;
	private MyPlan marginalPlan;
	private List<Task> currentTasks;
	private long timeout_bid;
	private long timeout_plan;

	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicle = agent.vehicles().get(0); // We select only the first vehicle in this initialization
		this.currentCity = vehicle.homeCity();

		long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
		this.random = new Random(seed);
		this.currentTasks = new ArrayList<Task>();
		this.vehicles = agent.vehicles();
		this.currentPlan = new MyPlan(this.vehicles.size());


		this.timeout_bid = LogistPlatform.getSettings().get(LogistSettings.TimeoutKey.BID);
		this.timeout_plan = LogistPlatform.getSettings().get(LogistSettings.TimeoutKey.PLAN);
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		if (winner == agent.id()) {
			this.currentPlan = this.marginalPlan.clone();
			this.currentTasks.add(previous);
		}
	}
	
	@Override
	public Long askPrice(Task task) {

		long tic = System.currentTimeMillis();
		long boundary = tic + timeout_bid;

		Vehicle[] vehicleArray = new Vehicle[this.vehicles.size()];
		vehicleArray = this.vehicles.toArray(vehicleArray);

		List<Task> nextCurrentTasks = new ArrayList<Task>(currentTasks);
		nextCurrentTasks.add(task);


		this.marginalPlan = CentralizedTemplate.plan(vehicles, nextCurrentTasks, boundary-100);
		double newCost = this.marginalPlan.computePlanCost(vehicleArray);

		double marginalCost = Math.max(newCost - currentPlan.computePlanCost(vehicleArray),0);
		System.out.println("Marginal cost : " + marginalCost);



		double ratio = 1.0 + 0.01*(timeout_bid-timeout_plan)/timeout_bid;
		double bid = Math.min(ratio * marginalCost,10);

		System.out.println("Bid : " + bid);

		return (long) Math.round(bid);
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);

		List<Plan> plans = new LinkedList<Plan>();

		if(!tasks.isEmpty()){
			long startTime = System.currentTimeMillis();
			long boundaryEnd = startTime + timeout_plan;

			List<Task> tasks1 = new ArrayList<Task>();

			for (Task task : tasks) {
				tasks1.add(task);
			}
			this.currentPlan = CentralizedTemplate.plan(vehicles, tasks1, boundaryEnd- 100);
			for(Vehicle v : vehicles) plans.add(this.currentPlan.generatePlan(v));
		}
		return plans;
	}

	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path())
				plan.appendMove(city);

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}
}
