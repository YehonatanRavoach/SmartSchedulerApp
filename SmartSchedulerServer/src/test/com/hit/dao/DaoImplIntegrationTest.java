package hit.dao;

import com.hit.dao.DaoFactory;
import com.hit.dao.IDao;
import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;
import org.junit.*;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Comprehensive integration tests for both SQLiteDaoImpl and FileDaoImpl.
 * Tests CRUD, search, update, delete, and predicate functionality.
 * Verifies both in-memory and persistent file/database correctness.
 */
public class DaoImplIntegrationTest {

    private IDao<Task> fileTaskDao;
    private IDao<TeamMember> fileMemberDao;
    private IDao<Assignment> fileAssignmentDao;

    private IDao<Task> sqliteTaskDao;
    private IDao<TeamMember> sqliteMemberDao;
    private IDao<Assignment> sqliteAssignmentDao;

    @Before
    public void setUp() throws Exception {
        // Always start with a clean state
        fileTaskDao = DaoFactory.create("file", Task.class);
        fileMemberDao = DaoFactory.create("file", TeamMember.class);
        fileAssignmentDao = DaoFactory.create("file", Assignment.class);

        sqliteTaskDao = DaoFactory.create("sqlite", Task.class);
        sqliteMemberDao = DaoFactory.create("sqlite", TeamMember.class);
        sqliteAssignmentDao = DaoFactory.create("sqlite", Assignment.class);

        fileTaskDao.deleteAll();
        fileMemberDao.deleteAll();
        fileAssignmentDao.deleteAll();

        sqliteTaskDao.deleteAll();
        sqliteMemberDao.deleteAll();
        sqliteAssignmentDao.deleteAll();
    }

    @After
    public void tearDown() throws Exception {
        fileTaskDao.deleteAll();
        fileMemberDao.deleteAll();
        fileAssignmentDao.deleteAll();

        sqliteTaskDao.deleteAll();
        sqliteMemberDao.deleteAll();
        sqliteAssignmentDao.deleteAll();
    }

    /**
     * Helper method to generate a demo Task entity.
     */
    private Task sampleTask(String id) {
        return new Task(id, "Task_" + id, 4, 1, List.of("java", "sql"));
    }

    /**
     * Helper method to generate a demo TeamMember entity.
     */
    private TeamMember sampleMember(String id) {
        return new TeamMember(id, "Member_" + id, List.of("java", "sql"), 8, 1.0);
    }

    /**
     * Helper method to generate a demo Assignment entity.
     */
    private Assignment sampleAssignment(String tid, String mid) {
        return new Assignment(tid, mid, 4);
    }

    // --- File DAO Tests ---

    @Test
    public void testFileTaskCrud() throws Exception {
        // Create and save a Task
        Task t = sampleTask("T1");
        fileTaskDao.save(t);
        List<Task> loaded = fileTaskDao.load();
        assertEquals(1, loaded.size());
        assertEquals("T1", loaded.getFirst().getId());

        // Update
        t.setName("UpdatedName");
        fileTaskDao.update(t);
        Task updated = fileTaskDao.findById("T1");
        assertEquals("UpdatedName", updated.getName());

        // Delete
        assertTrue(fileTaskDao.deleteById("T1"));
        assertTrue(fileTaskDao.load().isEmpty());
    }

    @Test
    public void testFileMemberCrudAndPredicate() throws Exception {
        TeamMember m1 = sampleMember("M1");
        TeamMember m2 = sampleMember("M2");
        fileMemberDao.save(List.of(m1, m2));
        assertEquals(2, fileMemberDao.load().size());

        // Predicate delete
        boolean deleted = fileMemberDao.deleteIf(member -> member.getName().equals("Member_M2"));
        assertTrue(deleted);
        List<TeamMember> members = fileMemberDao.load();
        assertEquals(1, members.size());
        assertEquals("M1", members.getFirst().getId());
    }

    @Test
    public void testFileAssignmentCompositeKey() throws Exception {
        Assignment a = sampleAssignment("T1", "M1");
        fileAssignmentDao.save(a);
        Assignment found = fileAssignmentDao.findById("T1-M1");
        assertNotNull(found);
        assertEquals("T1", found.getTaskId());
        assertEquals("M1", found.getMemberId());

        // Deleting non-existent returns false
        assertFalse(fileAssignmentDao.deleteById("fakeTask-fakeMember"));

        // Delete actual
        assertTrue(fileAssignmentDao.deleteById("T1-M1"));
        assertTrue(fileAssignmentDao.load().isEmpty());
    }

    @Test
    public void testFileBulkSaveAndDeleteAll() throws Exception {
        fileTaskDao.save(List.of(sampleTask("T2"), sampleTask("T3")));
        List<Task> all = fileTaskDao.load();
        assertEquals(2, all.size());
        fileTaskDao.deleteAll();
        assertTrue(fileTaskDao.load().isEmpty());
    }

    // --- SQLite DAO Tests ---

    @Test
    public void testSqliteTaskCrud() throws Exception {
        Task t = sampleTask("S1");
        sqliteTaskDao.save(t);
        Task loaded = sqliteTaskDao.findById("S1");
        assertNotNull(loaded);
        assertEquals("S1", loaded.getId());

        // Update and re-check
        t.setPriority(5);
        sqliteTaskDao.update(t);
        Task updated = sqliteTaskDao.findById("S1");
        assertEquals(5, updated.getPriority());

        // Delete and check
        assertTrue(sqliteTaskDao.deleteById("S1"));
        assertNull(sqliteTaskDao.findById("S1"));
    }

    @Test
    public void testSqliteMemberCrudAndPredicate() throws Exception {
        TeamMember m1 = sampleMember("S_M1");
        TeamMember m2 = sampleMember("S_M2");
        sqliteMemberDao.save(List.of(m1, m2));
        assertEquals(2, sqliteMemberDao.load().size());

        // Predicate delete
        boolean deleted = sqliteMemberDao.deleteIf(m -> m.getName().equals("Member_S_M2"));
        assertTrue(deleted);
        List<TeamMember> members = sqliteMemberDao.load();
        assertEquals(1, members.size());
        assertEquals("S_M1", members.getFirst().getId());
    }

    @Test
    public void testSqliteAssignmentCompositeKey() throws Exception {
        Assignment a = sampleAssignment("S_T1", "S_M1");
        sqliteAssignmentDao.save(a);
        Assignment found = sqliteAssignmentDao.findById("S_T1-S_M1");
        assertNotNull(found);
        assertEquals("S_T1", found.getTaskId());
        assertEquals("S_M1", found.getMemberId());

        // Delete actual
        assertTrue(sqliteAssignmentDao.deleteById("S_T1-S_M1"));
        assertTrue(sqliteAssignmentDao.load().isEmpty());
    }

    @Test
    public void testSqliteBulkSaveAndDeleteAll() throws Exception {
        sqliteTaskDao.save(List.of(sampleTask("S_T2"), sampleTask("S_T3")));
        List<Task> all = sqliteTaskDao.load();
        assertEquals(2, all.size());
        sqliteTaskDao.deleteAll();
        assertTrue(sqliteTaskDao.load().isEmpty());
    }

    // --- Additional tests ---

    @Test
    public void testFileAndSqliteConsistency() throws Exception {
        Task t = sampleTask("X1");
        fileTaskDao.save(t);
        sqliteTaskDao.save(t);

        Task fromFile = fileTaskDao.findById("X1");
        Task fromDb = sqliteTaskDao.findById("X1");
        assertNotNull(fromFile);
        assertNotNull(fromDb);
        assertEquals(fromFile.getName(), fromDb.getName());
        assertEquals(fromFile.getDurationHours(), fromDb.getDurationHours());
        assertEquals(fromFile.getPriority(), fromDb.getPriority());
        assertEquals(fromFile.getRequiredSkills(), fromDb.getRequiredSkills());
    }
}
