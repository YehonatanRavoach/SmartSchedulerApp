package hit.service;

import com.hit.dao.FileDaoImpl;
import com.hit.dao.SQLiteDaoImpl;
import com.hit.model.*;

import java.util.List;

public class TestDataGenerator {
    public static void main(String[] args) throws Exception {
        List<Task> tasks = List.of(
                new Task("t1", "Build API", 4, 1, List.of("java")),
                new Task("t2", "Train Model", 5, 2, List.of("ml")),
                new Task("t3", "Database Query", 3, 3, List.of("sql"))
        );

        List<TeamMember> members = List.of(
                new TeamMember("m1", "Alice", List.of("java", "sql"), 8, 1.0),
                new TeamMember("m2", "Bob", List.of("ml"), 6, 1.0),
                new TeamMember("m3", "Charlie", List.of("java", "ml"), 4, 1.0)
        );

        new FileDaoImpl<>(Task.class).save(tasks);
        new FileDaoImpl<TeamMember>(TeamMember.class).save(members);

        System.out.println("✅ Sample tasks and members written to file.");


        new SQLiteDaoImpl<>(Task.class).save(tasks);
        new SQLiteDaoImpl<>(TeamMember.class).save(members);

        System.out.println("✅ Sample tasks and members written to SQLite database.");
    }
}
