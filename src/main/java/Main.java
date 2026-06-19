import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;

public class Main {
    private static final Set<String> BUILTINS = Set.of("echo", "exit", "type");

    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("$ ");
            String input = reader.readLine();
            if (input == null) break;

            if (input.equals("exit") || input.startsWith("exit ")) {
                int code = 0;
                String[] parts = input.split("\\s+");
                if (parts.length > 1) {
                    try {
                        code = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        code = 0;
                    }
                }
                System.exit(code);
            } else if (input.startsWith("echo ")) {
                String message = input.substring(5);
                System.out.println(message);
            } else if (input.equals("type") || input.startsWith("type ")) {
                String arg = input.equals("type") ? "" : input.substring(5).trim();
                if (arg.isEmpty()) {
                    // no argument given; nothing meaningful to report
                    continue;
                }
                if (BUILTINS.contains(arg)) {
                    System.out.println(arg + " is a shell builtin");
                } else {
                    System.out.println(arg + ": not found");
                }
            } else {
                System.out.println(input + ": command not found");
            }
        }
    }
}