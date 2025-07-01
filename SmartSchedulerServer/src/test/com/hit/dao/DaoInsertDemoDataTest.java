package hit.dao;

import com.hit.dao.DaoFactory;
import com.hit.dao.IDao;
import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;
import org.junit.Test;

import java.util.List;

/**
 * Test that only populates both file and SQLite DBs with demo data, and does not clean up.
 * Run this test to easily inspect data in your database/files.
 */
public class DaoInsertDemoDataTest {

    @Test
    public void insertDemoData() throws Exception {
        IDao<Task> fileTaskDao = DaoFactory.create("file", Task.class);
        IDao<TeamMember> fileMemberDao = DaoFactory.create("file", TeamMember.class);
        IDao<Assignment> fileAssignmentDao = DaoFactory.create("file", Assignment.class);

        IDao<Task> sqliteTaskDao = DaoFactory.create("sqlite", Task.class);
        IDao<TeamMember> sqliteMemberDao = DaoFactory.create("sqlite", TeamMember.class);
        IDao<Assignment> sqliteAssignmentDao = DaoFactory.create("sqlite", Assignment.class);

        // Tasks
        Task t1 = new Task("T1", "Demo Task 1", 4, 1, List.of("java", "sql"));
        Task t2 = new Task("T2", "Demo Task 2", 3, 2, List.of("python"));
        fileTaskDao.save(List.of(t1, t2));
        sqliteTaskDao.save(List.of(t1, t2));

        // Members
        TeamMember m1 = new TeamMember("M1", "Alice", List.of("java", "sql"), 8, 1.0);
        TeamMember m2 = new TeamMember("M2", "Bob", List.of("python"), 6, 1.0);
        fileMemberDao.save(List.of(m1, m2));
        sqliteMemberDao.save(List.of(m1, m2));

        // Assignments
        Assignment a1 = new Assignment("T1", "M1", 4);
        Assignment a2 = new Assignment("T2", "M2", 3);
        fileAssignmentDao.save(List.of(a1, a2));
        sqliteAssignmentDao.save(List.of(a1, a2));

        System.out.println("Demo data inserted to both File and SQLite DAOs.");
    }
}
