package template;

import logist.task.Task;

public class MyActionPierre {
    enum Type {
        PICK_UP, DELIVER,
    }

    public Type type;
    public Task task;

    public MyActionPierre(Type type_, Task task_) {
        type = type_;
        task = task_;
    }

    public String toString() {
        if (type == Type.PICK_UP)
            return "PickUp(" + task.id + ")";
        else
            return "Deliver(" + task.id + ")";
    }
}