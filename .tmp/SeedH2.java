import java.nio.file.*;
import java.sql.*;
public class SeedH2 {
  public static void main(String[] args) throws Exception {
    String url = args[0];
    String sql = Files.readString(Path.of(args[1]));
    try (Connection c = DriverManager.getConnection(url, "sa", "");
         Statement s = c.createStatement()) {
      for (String part : sql.split(";\\s*(\\r?\\n|$)")) {
        String stmt = part.trim();
        if (!stmt.isEmpty()) s.execute(stmt);
      }
    }
  }
}