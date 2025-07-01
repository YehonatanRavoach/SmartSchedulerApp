package com.hit.service;

import com.hit.dao.IDao;
import com.hit.model.*;
import com.hit.algorithm.ITaskAssignment;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Main service for managing tasks, team members, and assignments.
 * Completely decoupled from the underlying DAO implementation.
 */
public class TaskAssignmentService {
    private final IDao<Task> taskDao;
    private final IDao<TeamMember> memberDao;
    private final IDao<Assignment> assignmentDao;

    private final UniqueIdGenerator taskIdGen;
    private final UniqueIdGenerator teamMemberIdGen;

    private final ReentrantReadWriteLock taskLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock memberLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock assignmentLock = new ReentrantReadWriteLock();

    public TaskAssignmentService(IDao<Task> taskDao, IDao<TeamMember> memberDao, IDao<Assignment> assignmentDao) throws Exception {
        this.taskDao = taskDao;
        this.memberDao = memberDao;
        this.assignmentDao = assignmentDao;
        this.taskIdGen = new UniqueIdGenerator("T");
        this.teamMemberIdGen = new UniqueIdGenerator("M");
    }

    // --- Task CRUD ---

    /**
     * Create and persist a new Task. Fails if a task with the same name and fields exists (idempotent).
     */
    public Task createNewTask(Task task) throws Exception {
        Objects.requireNonNull(task, "Task cannot be null");
        taskLock.writeLock().lock();
        try {
            // Optional: Defensive check - if an ID is forced from outside (shouldn't happen)
            if (task.getId() != null && !task.getId().isBlank()) {
                Task existing = taskDao.findById(task.getId());
                if (existing != null)
                    throw new IllegalArgumentException("Task already exists with id: " + task.getId());
            }
            // Always assign a new ID
            task.setId(taskIdGen.nextId());
            taskDao.save(task);
            return task;
        } finally {
            taskLock.writeLock().unlock();
        }
    }

    public List<Task> getAllTasks() throws Exception {
        taskLock.readLock().lock();
        try {
            return new ArrayList<>(taskDao.load());
        } finally {
            taskLock.readLock().unlock();
        }
    }

    public Task getTaskById(String taskId) throws Exception {
        if (taskId == null || taskId.isBlank())
            return null;
        taskLock.readLock().lock();
        try {
            return taskDao.findById(taskId);
        } finally {
            taskLock.readLock().unlock();
        }
    }

    public boolean updateTask(String taskId, Task updatedTask, String strategyName) throws Exception {
        Objects.requireNonNull(updatedTask, "Updated task cannot be null");
        if (taskId == null || taskId.isBlank()) return false;
        if (strategyName == null || strategyName.isBlank()) strategyName = "greedy";
        taskLock.writeLock().lock();
        try {
            Task existing = taskDao.findById(taskId);
            if (existing == null) return false;
            updatedTask.setId(taskId);
            taskDao.update(updatedTask);
        } finally {
            taskLock.writeLock().unlock();
        }
        assignTasks(strategyName);
        return true;
    }

    public boolean deleteTask(String taskId) throws Exception {
        if (taskId == null || taskId.isBlank()) return false;
        taskLock.writeLock().lock();
        assignmentLock.writeLock().lock();
        try {
            assignmentDao.deleteIf(a -> a.getTaskId().equals(taskId));
            return taskDao.deleteById(taskId);
        } finally {
            assignmentLock.writeLock().unlock();
            taskLock.writeLock().unlock();
        }
    }

    // --- TeamMember CRUD ---

    /**
     * Create and persist a new TeamMember.
     * Fails if member with same id or (same name + skills) exists.
     */
    public TeamMember createNewTeamMember(TeamMember member) throws Exception {
        Objects.requireNonNull(member, "TeamMember cannot be null");
        memberLock.writeLock().lock();
        try {
            // Only check for ID uniqueness
            if (member.getId() != null && !member.getId().isBlank()) {
                TeamMember existing = memberDao.findById(member.getId());
                if (existing != null) {
                    throw new IllegalArgumentException("Member already exists with id: " + member.getId());
                }
            }
            // Always assign a new ID (to prevent forced ID insertion from the outside)
            member.setId(teamMemberIdGen.nextId());
            memberDao.save(member);
            return member;
        } finally {
            memberLock.writeLock().unlock();
        }
    }


    public List<TeamMember> getAllTeamMembers() throws Exception {
        memberLock.readLock().lock();
        try {
            return new ArrayList<>(memberDao.load());
        } finally {
            memberLock.readLock().unlock();
        }
    }

    public TeamMember getTeamMemberById(String memberId) throws Exception {
        if (memberId == null || memberId.isBlank())
            return null;
        memberLock.readLock().lock();
        try {
            return memberDao.findById(memberId);
        } finally {
            memberLock.readLock().unlock();
        }
    }

    public boolean updateTeamMember(String memberId, TeamMember updatedMember, String strategyName) throws Exception {
        Objects.requireNonNull(updatedMember, "Updated member cannot be null");
        if (memberId == null || memberId.isBlank()) return false;
        if (strategyName == null || strategyName.isBlank()) strategyName = "greedy";
        memberLock.writeLock().lock();
        try {
            TeamMember existing = memberDao.findById(memberId);
            if (existing == null) return false;
            updatedMember.setId(memberId);
            memberDao.update(updatedMember);
        } finally {
            memberLock.writeLock().unlock();
        }
        assignTasks(strategyName);
        return true;
    }

    public boolean deleteTeamMember(String memberId) throws Exception {
        if (memberId == null || memberId.isBlank()) return false;
        memberLock.writeLock().lock();
        assignmentLock.writeLock().lock();
        try {
            assignmentDao.deleteIf(a -> a.getMemberId().equals(memberId));
            return memberDao.deleteById(memberId);
        } finally {
            assignmentLock.writeLock().unlock();
            memberLock.writeLock().unlock();
        }
    }

    // --- Assignment Management ---

    public boolean assignTasks(String strategyName) throws Exception {
        if (strategyName == null || strategyName.isBlank())
            strategyName = "greedy";
        ITaskAssignment strategy = com.hit.algorithm.StrategyFactory.getStrategy(strategyName);
        Objects.requireNonNull(strategy, "Assignment strategy cannot be null");
        assignmentLock.writeLock().lock();
        try {
            List<Task> tasks = getAllTasks();
            List<TeamMember> members = getAllTeamMembers();
            assignmentDao.deleteIf(_ -> true);
            List<Assignment> assignments = strategy.assignTasks(tasks, members);
            assignmentDao.save(assignments);
            return !assignments.isEmpty();
        } finally {
            assignmentLock.writeLock().unlock();
        }
    }

    public boolean assignTasksToTeamMember(String memberId, String strategyName) throws Exception {
        Objects.requireNonNull(memberId, "Member ID cannot be null");
        if (memberId.isBlank()) throw new IllegalArgumentException("Member ID cannot be blank");
        if (strategyName == null || strategyName.isBlank())
            strategyName = "greedy";
        ITaskAssignment strategy = com.hit.algorithm.StrategyFactory.getStrategy(strategyName);
        Objects.requireNonNull(strategy, "Assignment strategy cannot be null");

        assignmentLock.writeLock().lock();
        try {
            TeamMember member = getTeamMemberById(memberId);
            if (member == null)
                throw new IllegalArgumentException("Team member not found: " + memberId);

            List<Task> tasks = getAllTasks();
            assignmentDao.deleteIf(a -> memberId.equals(a.getMemberId()));

            List<Assignment> memberAssignments = strategy.assignTasks(tasks, List.of(member));
            assignmentDao.save(memberAssignments);

            return !memberAssignments.isEmpty();
        } finally {
            assignmentLock.writeLock().unlock();
        }
    }

    public List<Assignment> getAllAssignments() throws Exception {
        assignmentLock.readLock().lock();
        try {
            return new ArrayList<>(assignmentDao.load());
        } finally {
            assignmentLock.readLock().unlock();
        }
    }

    public boolean deleteAssignment(String taskId, String memberId) throws Exception {
        if (taskId == null || memberId == null || taskId.isBlank() || memberId.isBlank())
            return false;
        assignmentLock.writeLock().lock();
        try {
            String id = taskId + "-" + memberId;
            Assignment a = assignmentDao.findById(id);
            if (a != null) {
                Task task = getTaskById(taskId);
                if (task != null) {
                    task.setRemainingHours(task.getRemainingHours() + a.getAssignedHours());
                    taskDao.update(task);
                }
                TeamMember member = getTeamMemberById(memberId);
                if (member != null) {
                    member.setRemainingHours(member.getRemainingHours() + a.getAssignedHours());
                    memberDao.update(member);
                }
            }
            return assignmentDao.deleteById(id);
        } finally {
            assignmentLock.writeLock().unlock();
        }
    }

    public void clearAll() throws Exception {
        // Always delete assignments first to maintain referential integrity!
        assignmentDao.deleteAll();
        taskDao.deleteAll();
        memberDao.deleteAll();
    }

    // --- Statistics & Search (unchanged) ---

    public int countTasks() throws Exception {
        taskLock.readLock().lock();
        try {
            return taskDao.load().size();
        } finally {
            taskLock.readLock().unlock();
        }
    }

    public int countTeamMembers() throws Exception {
        memberLock.readLock().lock();
        try {
            return memberDao.load().size();
        } finally {
            memberLock.readLock().unlock();
        }
    }

    public int countUnassignedTasks() throws Exception {
        assignmentLock.readLock().lock();
        taskLock.readLock().lock();
        try {
            List<Assignment> assignments = assignmentDao.load();
            List<Task> tasks = taskDao.load();
            Set<String> assignedTaskIds = new HashSet<>();
            for (Assignment a : assignments) assignedTaskIds.add(a.getTaskId());
            return (int) tasks.stream()
                    .filter(t -> !assignedTaskIds.contains(t.getId()))
                    .count();
        } finally {
            taskLock.readLock().unlock();
            assignmentLock.readLock().unlock();
        }
    }

    public double averageLoad() throws Exception {
        memberLock.readLock().lock();
        assignmentLock.readLock().lock();
        try {
            List<TeamMember> members = memberDao.load();
            List<Assignment> assignments = assignmentDao.load();
            if (members.isEmpty()) return 0.0;
            Map<String, Long> loadPerMember = new HashMap<>();
            for (Assignment a : assignments) {
                loadPerMember.put(a.getMemberId(), loadPerMember.getOrDefault(a.getMemberId(), 0L) + 1);
            }
            double total = loadPerMember.values().stream().mapToDouble(Long::doubleValue).sum();
            return total / members.size();
        } finally {
            assignmentLock.readLock().unlock();
            memberLock.readLock().unlock();
        }
    }

    public List<Task> searchTasksByName(String name) throws Exception {
        if (name == null || name.isBlank()) return List.of();
        String lower = name.toLowerCase();
        taskLock.readLock().lock();
        try {
            List<Task> result = new ArrayList<>();
            for (Task t : taskDao.load()) {
                if (t.getName().toLowerCase().contains(lower))
                    result.add(t);
            }
            return result;
        } finally {
            taskLock.readLock().unlock();
        }
    }

    public List<TeamMember> searchTeamMembersByName(String name) throws Exception {
        if (name == null || name.isBlank()) return List.of();
        String lower = name.toLowerCase();
        memberLock.readLock().lock();
        try {
            List<TeamMember> result = new ArrayList<>();
            for (TeamMember m : memberDao.load()) {
                if (m.getName().toLowerCase().contains(lower))
                    result.add(m);
            }
            return result;
        } finally {
            memberLock.readLock().unlock();
        }
    }
}
