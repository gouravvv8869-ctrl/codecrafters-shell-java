import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Main {
    private static final Set<String> BUILTINS = Set.of("echo", "exit", "type", "pwd", "cd");

    public static void main(String[] args) throws IOException, InterruptedException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("$ ");
            String input = reader.readLine();
            if (input == null) {
                break;
            }
            if (input.trim().isEmpty()) {
                continue;
            }

            String[] parts = tokenize(input);
            if (parts.length == 0) {
                continue;
            }
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
                StringBuilder message = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (i > 1) {
                        message.append(" ");
                    }
                    message.append(parts[i]);
                }
                System.out.println(message.toString());
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
            } else if (command.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (command.equals("cd")) {
                if (parts.length < 2) {
                    continue;
                }
                String targetPath = parts[1];
                changeDirectory(targetPath);
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

    private static String[] tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean hasToken = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;
                } else {
                    current.append(c);
                }
            } else {
                if (c == '\'') {
                    inSingleQuotes = true;
                    hasToken = true;
                } else if (Character.isWhitespace(c)) {
                    if (hasToken) {
                        tokens.add(current.toString());
                        current.setLength(0);
                        hasToken = false;
                    }
                } else {
                    current.append(c);
                    hasToken = true;
                }
            }
        }

        if (hasToken) {
            tokens.add(current.toString());
        }

        return tokens.toArray(new String[0]);
    }

    private static void changeDirectory(String targetPath) {
        targetPath = targetPath.trim();
        if (targetPath.equals("~")) {
            String home = System.getenv("HOME");
            if (home == null) {
                System.out.println("cd: HOME not set");
                return;
            }
            targetPath = home;
        }

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path target;
        if (Paths.get(targetPath).isAbsolute()) {
            target = Paths.get(targetPath).normalize();
        } else {
            target = currentDir.resolve(targetPath).normalize();
        }

        File targetFile = target.toFile();
        if (targetFile.isDirectory()) {
            System.setProperty("user.dir", target.toString());
        } else {
            System.out.println("cd: " + targetPath + ": No such file or directory");
        }
    }

    private static void runExternalProgram(String command, String[] parts) {
        try {
            List<String> commandList = new ArrayList<>();
            commandList.add(command);
            for (int i = 1; i < parts.length; i++) {
                commandList.add(parts[i]);
            }

            ProcessBuilder builder = new ProcessBuilder(commandList);
            builder.directory(new File(System.getProperty("user.dir")));
            builder.inheritIO();
            Process process = builder.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println(command + ": command not found");
        }
    }

    private static String findInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            return null;
        }

        String[] dirs = pathEnv.split(File.pathSeparator);
        for (String dir : dirs) {
            File candidate = new File(dir, command);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate.getPath();
            }
        }
        return null;
    }
}