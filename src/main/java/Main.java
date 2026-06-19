import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Main {
    private static final Set<String> BUILTINS = Set.of("echo", "exit", "type");

    public static void main(String[] args) throws IOException, InterruptedException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("$ ");
            String input = reader.readLine();
            if (input == null) break;
            if (input.isEmpty()) continue;

            String[] parts = input.trim().split("\\s+");
            String command = parts[0];

            if (command.equals("exit")) {
                int code = 0;
                if (parts.length > 1) {
                    try {
                        code = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        code = 0;
                    }
                }
                System.exit(code);
            } else if (command.equals("echo")) {
                String message = input.length() > 5 ? input.substring(5) : "";
                System.out.println(message);
            } else if (command.equals("type")) {
                if (parts.length < 2) {
                    continue;
                }
                String arg = parts[1];
                if (BUILTINS.contains(arg)) {
                    System.out.println(arg + " is a shell builtin");
                } else {
                    String foundPath = findInPath(arg);
                    if (foundPath != null) {
                        System.out.println(arg + " is " + foundPath);
                    } else {
                        System.out.println(arg + ": not found");
                    }
                }
            } else {
                String executablePath = findInPath(command);
                if (executablePath != null) {
                    runExternalProgram(command, parts);
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }
    }

    private static void runExternalProgram(String command, String[] parts) {
        try {
            List<String> commandList = new ArrayList<>();
            commandList.add(command); // argv[0] should be the command name, not the full path
            for (int i = 1; i < parts.length; i++) {
                commandList.add(parts[i]);
            }

            ProcessBuilder builder = new ProcessBuilder(commandList);
            builder.inheritIO(); // pipe child's stdin/stdout/stderr straight through
            Process process = builder.start();
            process.waitFor();
        } catch (IOException | InterruptedException e)