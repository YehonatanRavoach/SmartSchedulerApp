package hit.network;

import Util.SkillRepository;
import com.hit.dao.DaoFactory;
import com.hit.dao.IDao;
import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;
import com.hit.service.TaskAssignmentService;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Populates the DB with a **large, random data-set** (≈60 tasks, ≈30 members),
 * then runs the greedy allocator so you can visually confirm different load
 * patterns each time the app starts.
 *
 * ────────────────────────────────────────────────────────────────
 * • Tasks:   5 hand-made “classic” tasks  +  ≈55 random tasks
 * • Members: 3 fixed members              +  ≈27 random members
 * • Efficiency is now an **integer 1‒6** (whole numbers only)
 * ────────────────────────────────────────────────────────────────
 */
public class DBInitAndPopulateTest {

    private static TaskAssignmentService service;
    private static IDao<Task>        taskDao;
    private static IDao<TeamMember>  memberDao;
    private static IDao<Assignment>  assignDao;

    /* ───── one-time DB + service init ───── */
    @BeforeClass
    public static void init() throws Exception {
        taskDao   = DaoFactory.create("sqlite", Task.class);
        memberDao = DaoFactory.create("sqlite", TeamMember.class);
        assignDao = DaoFactory.create("sqlite", Assignment.class);

        // clean slate
        taskDao.deleteAll();
        memberDao.deleteAll();
        assignDao.deleteAll();

        service = new TaskAssignmentService(taskDao, memberDao, assignDao);
    }

    /* ────────────────────────────────────────
     *  MAIN “big sample” test
     * ──────────────────────────────────────── */
    @Test
    public void bigSampleDataAndAssignments() throws Exception {

        /* ---------- TASKS ---------------------------------------------- */
        List<Task> taskSeed = List.of(   // a few fixed, realistic tasks
                new Task(null, "Develop REST API",      8, 1, List.of("Java", "REST API", "Spring")),
                new Task(null, "Fix production bugs",   5, 1, List.of("Java", "Debug", "Testing")),
                new Task(null, "Design new landing",   10, 2, List.of("UI", "UX", "Figma")),
                new Task(null, "Migrate legacy DB",    16, 3, List.of("SQL", "Migration")),
                new Task(null, "Orphan Task",           6, 2, List.of("Haskell"))          // nobody knows Haskell 🙂
        );

        List<Task> randomTasks = generateRandomTasks(55);   // add ~55 more
        for (Task t : concat(taskSeed, randomTasks)) {
            service.createNewTask(t);
        }

        /* ---------- TEAM-MEMBERS --------------------------------------- */
        List<TeamMember> handMade = List.of(
                new TeamMember(null, "Alice Smith",  List.of("Java", "Spring", "REST API"), 8, 2),
                new TeamMember(null, "Bob Lee",      List.of("SQL", "Performance", "Docker"), 7, 5),
                new TeamMember(null, "Clara Nguyen", List.of("UI", "UX", "Design"),          6, 3)
        );

        List<TeamMember> randomMembers = generateRandomMembers(27);
        for (TeamMember m : concat(handMade, randomMembers)) {
            service.createNewTeamMember(m);
        }

        /* ---------- BULK ALLOCATION ------------------------------------ */
        //assertTrue("Bulk assignment failed", service.assignTasks("greedy"));

        printSummary();

        /* ---------- QUICK ASSERTS -------------------------------------- */
        assertTrue(taskDao.load().size()   >= 60);
        assertTrue(memberDao.load().size() >= 30);
        assertTrue(assignDao.load().size() >= 40);

        System.out.println("\n✅  Big-sample allocation finished.");
    }

    /* ===================================================================
     *  HELPERS
     * =================================================================== */

    /** Generate <i>howMany</i> random tasks with varied hours / priority / skills */
    private static List<Task> generateRandomTasks(int howMany) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<String> skills   = SkillRepository.ALL_SKILLS;

        List<Task> out = new ArrayList<>(howMany);
        for (int i = 1; i <= howMany; i++) {

            int hours     = rnd.nextInt(2, 21);          // 2-20 h
            int priority  = rnd.nextInt(1, 5);           // 1-4
            int skillCnt  = rnd.nextInt(2, 6);           // 2-5 required skills

            Collections.shuffle(skills, rnd);
            List<String> req = new ArrayList<>(skills.subList(0, skillCnt));

            String name = "Task #" + i + " (" + req.get(0) + ")";
            out.add(new Task(null, name, hours, priority, req));
        }
        return out;
    }

    /** Generate <i>howMany</i> random members – efficiency is <b>int 1-6</b>. */
    private static List<TeamMember> generateRandomMembers(int howMany) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<String> skills   = SkillRepository.ALL_SKILLS;

        String[] first = {"Liam","Noah","Olivia","Emma","Amir","Yuval","Sofia","Maya",
                "Lucas","Mila","Mateo","Ziv","Idan","Daniel","Sara"};
        String[] last  = {"Cohen","Levi","Mizrahi","Smith","Johnson","Nguyen","Garcia",
                "Kim","Park","Singh","Khan","Gonzalez","Brown","Wilson"};

        List<TeamMember> out = new ArrayList<>(howMany);
        for (int i = 0; i < howMany; i++) {

            String fullName = first[rnd.nextInt(first.length)] + ' ' +
                    last [rnd.nextInt(last.length)];

            int    maxHours   = rnd.nextInt(5, 11);      // 5-10 h/day
            int    effInt     = rnd.nextInt(1, 7);       // 1-6 inclusive
            double efficiency = (double) effInt;         // still passes a double

            int skillCnt = rnd.nextInt(4, 8);            // 4-7 skills
            Collections.shuffle(skills, rnd);
            List<String> has = new ArrayList<>(skills.subList(0, skillCnt));

            out.add(new TeamMember(null, fullName, has, maxHours, efficiency));
        }
        return out;
    }

    /* simple List concat without Java 9 Stream.of(...) */
    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> all = new ArrayList<>(a.size() + b.size());
        all.addAll(a);
        all.addAll(b);
        return all;
    }

    /* Prints a quick, human-readable summary to the console */
    private void printSummary() throws Exception {
        System.out.printf("\n► %d tasks | %d members | %d assignments ◄\n",
                taskDao.load().size(), memberDao.load().size(), assignDao.load().size());

        // assignments per member
        Map<String, Long> byMember = assignDao.load().stream()
                .collect(Collectors.groupingBy(Assignment::getMemberId, Collectors.counting()));
        System.out.println("\nAssignments per member:");
        byMember.forEach((id, cnt) -> {
            try {
                System.out.printf("  %-28s %2d\n", memberDao.findById(id).getName(), cnt);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // remaining hours
        System.out.println("\nRemaining hours:");
        memberDao.load().forEach(m ->
                System.out.printf("  %-28s %2d h left\n", m.getName(), m.getRemainingHours()));

        // unassigned tasks
        long unassigned = service.countUnassignedTasks();
        System.out.printf("\nUnassigned tasks after allocation: %d\n", unassigned);
    }
}
