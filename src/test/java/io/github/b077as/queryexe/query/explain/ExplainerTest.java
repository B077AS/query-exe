package io.github.b077as.queryexe.query.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

class ExplainerTest {

    @Test
    void sqlitePlanIsATreeWithTheTableAndTheIndex() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement s = connection.createStatement()) {
                s.execute("CREATE TABLE t(id INTEGER PRIMARY KEY, a INTEGER, b TEXT)");
                s.execute("CREATE INDEX idx_a ON t(a)");
            }
            Plan plan = Explainer.explain(connection, "SQLite", "SELECT * FROM t WHERE a = 1;", false, st -> { });
            assertFalse(plan.isAnalyzed());
            assertFalse(plan.isCanAnalyze());
            assertEquals(1, plan.getRoots().size());
            PlanNode node = plan.getRoots().get(0);
            assertEquals("SEARCH", node.getOp());
            assertEquals("t", node.getRelation());
            assertTrue(node.getDetail().contains("idx_a"));
        }
    }

    @Test
    void h2PlanComesBackAsText() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:explainer;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement s = connection.createStatement()) {
                s.execute("CREATE TABLE t(id INT PRIMARY KEY, a INT)");
            }
            Plan plan = Explainer.explain(connection, "H2", "SELECT * FROM t", false, st -> { });
            assertEquals(1, plan.getRoots().size());
            assertTrue(plan.getRoots().get(0).getDetail().toUpperCase().contains("SELECT"));
            assertNotNull(plan.getRaw());
        }
    }

    @Test
    void anExplainStatementIsRefused() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            assertThrows(IllegalArgumentException.class, () -> Explainer.explain(connection, "SQLite", "explain select 1", false, st -> { }));
            assertThrows(IllegalArgumentException.class, () -> Explainer.explain(connection, "SQLite", " ; ", false, st -> { }));
        }
    }

    @Test
    void mysqlTreeKeepsTheNestingAndTheFigures() {
        String text = """
                -> Limit: 10 row(s)  (cost=5.2 rows=10) (actual time=0.5..0.6 rows=10 loops=1)
                    -> Nested loop inner join  (cost=5.2 rows=10) (actual time=0.4..0.5 rows=10 loops=1)
                        -> Table scan on o  (cost=1.1 rows=10) (actual time=0.1..0.2 rows=10 loops=1)
                        -> Single-row index lookup on c using PRIMARY (id=o.cid)  (cost=0.25 rows=1) (actual time=0.01..0.01 rows=1 loops=10)
                    -> Table scan on x  (cost=1.0 rows=1) (never executed)
                """;
        Plan plan = new Plan();
        Explainer.mysqlTree(text, plan);
        assertEquals(1, plan.getRoots().size());
        PlanNode limit = plan.getRoots().get(0);
        assertEquals("Limit", limit.getOp());
        assertEquals("10 row(s)", limit.getDetail());
        assertEquals(5.2, limit.getCost());
        assertEquals(2, limit.getChildren().size());
        PlanNode join = limit.getChildren().get(0);
        assertEquals("Nested loop inner join", join.getOp());
        assertEquals(2, join.getChildren().size());
        PlanNode scan = join.getChildren().get(0);
        assertEquals("o", scan.getRelation());
        assertEquals(10.0, scan.getActualRows());
        PlanNode lookup = join.getChildren().get(1);
        assertEquals("c", lookup.getRelation());
        assertEquals(10.0, lookup.getActualRows()); // 1 row per loop, 10 loops
        assertEquals(0.1, lookup.getActualMs(), 1e-9); // 0.01 ms per loop, 10 loops
        PlanNode never = limit.getChildren().get(1);
        assertEquals(0.0, never.getLoops());
        assertNull(never.getDetail());
    }
}
