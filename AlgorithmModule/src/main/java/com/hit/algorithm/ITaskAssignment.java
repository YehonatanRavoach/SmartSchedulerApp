package com.hit.algorithm;

import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;

import java.util.List;

/**
 * Interface for task assignment strategies.
 * Each strategy must implement how tasks are assigned to team members.
 */
public interface ITaskAssignment {

    /**
     * Assigns tasks to team members according to the implemented strategy.
     *
     * @param tasks   the list of tasks to be assigned
     * @param members the list of available team members
     * @return a list of assignments indicating how tasks were distributed
     */
    List<Assignment> assignTasks(List<Task> tasks, List<TeamMember> members);
}
