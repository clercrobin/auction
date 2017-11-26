package template;

//the list of imports
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import logist.LogistSettings;
import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

@SuppressWarnings("unused")
public class AuctionPierre implements AuctionBehavior {
    // Attributes
    private Topology mTopology;
    private TaskDistribution mDistribution;
    private Agent mAgent;
    private List<Vehicle> mVehicles;
    // Planner
    private Random mRandom;
    private MyPlanPierre mCurPlan;
    private MyPlanPierre mNextPlan;
    // Strategy
    private String mStrategyName;
    private int mCurRound;
    private int mNbTasksWon;
    private ArrayList<Long[]> mHistory;
    private ArrayList<Task> mTasks;
    private long mTotalReward;
    private double mMarginalCost;
    private double mRatio;
    private int mNbWarmUpRounds;
    private double mDiscount;
    private ArrayList<Double> mMeans;
    private ArrayList<Double> mStds;
    private boolean mDirty;
    // Timeouts
    private long mTimeoutSetup;
    private long mTimeoutBid;
    private long mTimeoutPlan;

    @Override
    public void setup(Topology topology, TaskDistribution distribution, Agent agent) {
        // Timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_auction.xml");
        } catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
        mTimeoutSetup = ls.get(LogistSettings.TimeoutKey.SETUP);
        mTimeoutBid = ls.get(LogistSettings.TimeoutKey.BID);
        mTimeoutPlan = ls.get(LogistSettings.TimeoutKey.PLAN);

        // Set attributes
        mTopology = topology;
        mDistribution = distribution;
        mAgent = agent;
        mVehicles = agent.vehicles();
        mCurPlan = new MyPlanPierre(mVehicles.size());
        mRatio = 1.0;
        mTotalReward = 0;
        mCurRound = 0;
        mNbTasksWon = 0;
        mHistory = new ArrayList<Long[]>();
        mTasks = new ArrayList<Task>();
        mMeans = new ArrayList<Double>();
        mStds = new ArrayList<Double>();
        mDirty = true;

        // Get strategy
        mStrategyName = agent.readProperty("strategy", String.class, "");
        mRatio = agent.readProperty("ratio", Double.class, 1.0);
        mNbWarmUpRounds = agent.readProperty("warm-up", Integer.class, 0);
        mDiscount = agent.readProperty("discount", Double.class, 1.0);

        // To change
        mRandom = new Random();
    }

    @Override
    public void auctionResult(Task previous, int winner, Long[] bids) {
        // Initialize arrays
        if (mDirty) {
            for (int i = 0; i < bids.length; ++i) {
                mMeans.add(0.0);
                mStds.add(0.0);
            }
            mDirty = false;
        }
        // Update the plan
        if (winner == mAgent.id()) {
            mCurPlan = mNextPlan;
            mNextPlan = null;
            mTotalReward += bids[winner];
            ++mNbTasksWon;
        }
        // Update the strategy
        mHistory.add(bids);
        mTasks.add(previous);
        updateStrategy(winner, bids);
        ++mCurRound;
        // FOR THE SCRIPT
        System.out.println("AUCTION_RESULT " + mAgent.id() + " " + winner + " "
                + mCurPlan.getCost(mVehicles));
    }

    @Override
    public Long askPrice(Task task) {
        long start = System.currentTimeMillis();

        // Check if a vehicle can take this task
        boolean able = false;
        Vehicle ableVehicle = null;
        for (Vehicle vehicle : mVehicles) {
            if (vehicle.capacity() >= task.weight) {
                able = true;
                ableVehicle = vehicle;
                break;
            }
        }
        if (!able)
            return null;

        // Give this task to the ableVehicle
        mNextPlan = mCurPlan.addTaskTo(task, ableVehicle);

        // Optimize the new plan
        long remainingTime = mTimeoutBid - (start - System.currentTimeMillis());
        mNextPlan = optimize(mNextPlan, (long) (remainingTime - 200));

        // Compute the marginal cost
        double marginalCost = mNextPlan.getCost(mVehicles) - mCurPlan.getCost(mVehicles);

        // Compute the bid accordingly to the strategy
        long bid = computeBid(task, marginalCost);

        // FOR THE SCRIPT (Je me sers juste de mAgent.id() et de bid pour
        // l'instant, tu peux mettre "0" à la place des autres ...)
        System.out.println("ASK_PRICE " + mAgent.id() + " " + mNextPlan.getCost(mVehicles) + " "
                + marginalCost + " " + task.pathLength() + " " + bid);
        return bid;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        mCurPlan = optimize(mCurPlan, mTimeoutPlan - 500);
        System.out.println(mAgent.id() + " PLAN " + " "
                + (mTotalReward - mCurPlan.getCost(mVehicles)));
        return mCurPlan.convert(vehicles, tasks);
    }

    // Planner

    private MyPlanPierre optimize(MyPlanPierre plan, long maxDuration) {
        // Initialization
        long start = System.currentTimeMillis();
        final double p = 0.5;

        // Print the initial cost.
        // System.out.print(" " + plan.getCost(mVehicles));

        // Restart the optimization several times to try finding a better
        // solution
        MyPlanPierre bestPlan = plan;
        while ((System.currentTimeMillis() - start) < maxDuration) {
            long remainingTime = maxDuration - (System.currentTimeMillis() - start);
            MyPlanPierre candidate = optimizeLocally(plan, remainingTime, 5000);
            if (candidate.getCost(mVehicles) < bestPlan.getCost(mVehicles))
                bestPlan = candidate;
        }

		/*
		 * // Print the final cost. System.out.print(" -> " +
		 * bestPlan.getCost(mVehicles));
		 *
		 * // Print duration long duration = System.currentTimeMillis() - start;
		 * System.out.print(" in " + duration + " milliseconds.");
		 */

        return bestPlan;
    }

    private MyPlanPierre optimizeLocally(MyPlanPierre plan, long maxDuration, int maxNbIterations) {
        // Initialization
        long start = System.currentTimeMillis();
        final double p = 0.5;
        MyPlanPierre bestPlan = plan;
        MyPlanPierre curPlan = plan;

        // Optimize by doing small steps
        int nbIterations = 0;
        while ((System.currentTimeMillis() - start) < maxDuration && nbIterations < maxNbIterations) {
            // Find the best neighbor
            MyPlanPierre bestNeighbor = getBestNeighbor(curPlan, 20, 20, 10);

            // Choose the next plan
            if (bestNeighbor != null
                    && (bestNeighbor.getCost(mVehicles) < curPlan.getCost(mVehicles) || mRandom
                    .nextDouble() > p))
                curPlan = bestNeighbor;

            // System.out.println("Iteration " + (nbIterations + 1) + " Cost ["
            // + curPlan.getCost(mVehicles) + "]");

            // Update the best plan
            if (curPlan.getCost(mVehicles) < bestPlan.getCost(mVehicles))
                bestPlan = curPlan;

            ++nbIterations;
        }
        return bestPlan;
    }

    private MyPlanPierre getBestNeighbor(MyPlanPierre plan, int nbMoveTask, int nbChangeOrder,
                                   int depthChangeOrder) {
        plan.computeMappings(mVehicles);
        MyPlanPierre bestNeighbor = null;
        ArrayList<Vehicle> vehiclesWithPlan = plan.getVehiclesWithNonEmptyPlan(mVehicles);
        ArrayList<ArrayList<Task>> vehiclesToTasks = plan.getVehicleToTasks();
        if (vehiclesWithPlan.size() > 0) {
            // Swap actions between vehicles
            for (int i = 0; i < nbMoveTask; ++i) {
                // Generate the parameters randomly
                Vehicle from = vehiclesWithPlan.get(mRandom.nextInt(vehiclesWithPlan.size()));
                Vehicle to = mVehicles.get(mRandom.nextInt(mVehicles.size()));
                ArrayList<Task> tasks = vehiclesToTasks.get(from.id());
                Task task = tasks.get(mRandom.nextInt(tasks.size()));
                // Create the neighbor
                MyPlanPierre neighbor = plan.moveTask(from, to, task);
                // Change the order of receiver's actions
                if (neighbor != null)
                    neighbor = iterateChangeOrder(neighbor, to, depthChangeOrder);
                // Evaluate the neighbor
                if (neighbor != null
                        && (bestNeighbor == null || neighbor.getCost(mVehicles) < bestNeighbor
                        .getCost(mVehicles)))
                    bestNeighbor = neighbor;
            }

            // Change the order of actions inside a vehicle's plan
            for (int i = 0; i < nbChangeOrder; ++i) {
                // Generate the parameters randomly
                Vehicle vehicle = vehiclesWithPlan.get(mRandom.nextInt(vehiclesWithPlan.size()));
                int nbActions = plan.getNbActions(vehicle);
                // Create the neighbor and evaluate it
                MyPlanPierre neighbor = iterateChangeOrder(plan, vehicle, depthChangeOrder);
                if (neighbor != null
                        && (bestNeighbor == null || neighbor.getCost(mVehicles) < bestNeighbor
                        .getCost(mVehicles)))
                    bestNeighbor = neighbor;
            }
        }

        return bestNeighbor;
    }

    private MyPlanPierre iterateChangeOrder(MyPlanPierre plan, Vehicle vehicle, int nbTimes) {
        MyPlanPierre bestPlan = plan;
        MyPlanPierre curPlan = plan;
        for (int i = 0; i < nbTimes; ++i) {
            curPlan.computeMappings(mVehicles);
            // Generate the parameters randomly
            int nbActions = plan.getNbActions(vehicle);
            int iAction1 = mRandom.nextInt(nbActions);
            int iAction2 = mRandom.nextInt(nbActions);
            MyPlanPierre newPlan = curPlan.changeOrder(vehicle, iAction1, iAction2);
            if (newPlan != null) {
                curPlan = newPlan;
                if (curPlan.getCost(mVehicles) < bestPlan.getCost(mVehicles))
                    bestPlan = curPlan;
            }
        }
        return bestPlan;
    }

    // Strategy

    private long computeBid(Task task, double marginalCost) {
        mMarginalCost = marginalCost;
        if (mStrategyName.equals("fixed-ratio")) {
            double bid = Math.max(0, mRatio * marginalCost);
            return (long) Math.ceil(bid);
        } else if (mStrategyName.equals("adaptative-ratio-1")) {
            double bid = Math.max(0, mRatio * marginalCost);
            return (long) Math.ceil(bid);
        } else if (mStrategyName.equals("adaptative-ratio-2")) {
            double bid = Math.max(0, mRatio * marginalCost);
            return (long) Math.ceil(bid);
        } else if (mStrategyName.equals("learning")) {
            double bid;
            if (mCurRound < mNbWarmUpRounds)
                bid = mRatio * marginalCost;
            else {
                double minBid = Double.POSITIVE_INFINITY;
                for (int i = 0; i < mMeans.size(); ++i) {
                    if (i != mAgent.id()) {
                        bid = findBestBid(task.pathLength(), mMeans.get(i), mStds.get(i),
                                mMeans.get(i) - 2 * mStds.get(i), mMeans.get(i) + 2 * mStds.get(i), 100);
                        minBid = Math.min(bid, minBid);
                    }
                }
                bid = minBid;
            }
            return (long) Math.ceil(bid);
        }
        return 0;
    }

    private void updateStrategy(int winner, Long[] bids) {
        if (mStrategyName.equals("adaptative-ratio-1")) {
            // System.out.print(mAgent.id() + " UPDATE STRATEGY: " + mRatio +
            // " -> ");
            if (mMarginalCost == 0.0)
                return;
            double minRatio = Double.POSITIVE_INFINITY;
            for (int i = 0; i < bids.length; ++i) {
                if (bids[i] != null && i != mAgent.id())
                    minRatio = Math.min(minRatio, bids[i] / mMarginalCost);
            }
            if (minRatio > 1.0 && minRatio != Double.POSITIVE_INFINITY)
                mRatio = (1.0 + minRatio) * 0.5;
            else
                mRatio = 1.0;
        } else if (mStrategyName.equals("adaptative-ratio-2")) {
            if (winner == mAgent.id())
                mRatio *= 1.1;
            else
                mRatio *= 0.9;
        } else if (mStrategyName.equals("learning")) {
            if (mCurRound >= mNbWarmUpRounds - 1) {
                updateMeans();
                updateStds();
                //System.out.println("LEARNING " + mMeans.get(0) + " " + mStds.get(0));
            }
        }
    }

    private void updateMeans() {
        double[] nums = new double[mMeans.size()];
        Arrays.fill(nums, 0.0);
        double denom = 0.0;
        double powDiscount = 1.0;
        for (int i = mHistory.size() - 1; i >= 0; --i) {
            denom += powDiscount;
            for (int j = 0; j < nums.length; ++j)
                nums[j] += powDiscount * mHistory.get(i)[j] / mTasks.get(i).pathLength();
            powDiscount *= mDiscount;
        }
        for (int j = 0; j < mMeans.size(); ++j)
            mMeans.set(j, nums[j] / denom);
    }

    private void updateStds() {
        double[] nums = new double[mMeans.size()];
        Arrays.fill(nums, 0.0);
        double denom = 0.0;
        double powDiscount = 1.0;
        for (int i = mHistory.size() - 1; i >= 0; --i) {
            denom += powDiscount;
            for (int j = 0; j < nums.length; ++j) {
                double error = (mHistory.get(i)[j] / mTasks.get(i).pathLength() - mMeans.get(j));
                nums[j] += powDiscount * error * error;
            }
            powDiscount *= mDiscount;
        }
        for (int j = 0; j < mMeans.size(); ++j)
            mStds.set(j, Math.sqrt(nums[j] / denom));
    }

    // Gaussian

    private double findBestBid(double length, double mu, double std, double minRatio, double maxRatio, int nbPoints) {
        double bestReward = Double.NEGATIVE_INFINITY;
        double bestBid = 0.0;
        double bestRatio = 0.0;
        double dratio = (maxRatio - minRatio) / nbPoints;
        for (double ratio = minRatio; ratio < maxRatio; ratio += dratio) {
            double reward = length * ratio * (1 - gaussianCdf(ratio, mu, std));
            if (reward >= bestReward) {
                bestReward = reward;
                bestBid = length * ratio;
                bestRatio = ratio;
            }
        }
        //System.out.println("BEST RATIO " + bestRatio);
        return bestBid;
    }

    private double gaussianPdf(double x) {
        return Math.exp(-x * x / 2) / Math.sqrt(2 * Math.PI);
    }

    private double gaussianCdf(double z) {
        if (z < -8.0)
            return 0.0;
        if (z > 8.0)
            return 1.0;
        double sum = 0.0, term = z;
        for (int i = 3; sum + term != sum; i += 2) {
            sum = sum + term;
            term = term * z * z / i;
        }
        return 0.5 + sum * gaussianPdf(z);
    }

    private double gaussianCdf(double z, double mu, double sigma) {
        return gaussianCdf((z - mu) / sigma);
    }
}