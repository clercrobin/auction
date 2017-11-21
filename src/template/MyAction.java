package template;

import logist.task.Task;

public class MyAction {

    private Task task;

    private boolean pickup; // Is it the pickup or the delivery of the task at
    // stake ?

    public MyAction(Task task, boolean pickup) {
        this.task = task;
        this.pickup = pickup;
    }

    public Task getTask() {
        return task;
    }

    public boolean isPickup() {
        return pickup;
    }

    public int getId() {
        if (pickup)
            return 2 * task.id + 1;
        else
            return 2 * task.id;
    }

    @Override
    public String toString() {
        if (pickup)
            return "Pickup : " + task.toString();
        else
            return "Deliver : " + task.toString();
    }

}
