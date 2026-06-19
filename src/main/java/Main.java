import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
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

            ParseResult parsed = parseCommand(parts);

            String command = parsed.command;

            if (command.equals("exit")) {
                int code = 0;
                if (!parsed.args.isEmpty()) {
                    try {
                        code = Integer.parseInt(parsed.args.get(0));
                    } catch (NumberFormatException e) {
                        code = 0;
                    }
                }
                System.exit(code);
            } else if (command.equals("echo")) {
                StringBuilder message = new StringBuilder();
                for (int i = 0; i < parsed.args.size(); i++) {
                    if (i > 0) message.append(" ");
                    message.append(parsed.args.get(i));
                }
                String output = message.toString() + "\n";
                if (parsed.outputFile != null) {
                    writeToFile(parsed.outputFile, output);
                } else {
                    System.out.print(output);
                }
            } else if (command.equals("type")) {
                if (parsed.args.isEmpty()) continue;
                String arg = parsed.args.get(0);
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
                String output = System.getProperty("user.dir") + "\n";
                if (parsed.outputFile != null) {
                    writeToFile(parsed.outputFile, output);
                } else {
                    System.out.print(output);
                }
            } else if (command.equals("cd")) {
                if (!parsed.args.isEmpty()) {
                    changeDirectory(parsed.args.get(0));
                }
            } else {
                String executablePath = findInPath(command);
                if (executablePath != null) {
                    runExternalProgram(command, parsed.args, parsed.outputFile);
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }
    }

    private static class ParseResult {
        String command;
        List<String> args = new ArrayList<>();
        String outputFile = null;
    }

    private static ParseResult parseCommand(String[] parts) {
        ParseResult result = new ParseResult();
        int i = 0;

        if (parts.length == 0) return result;

        result.command = parts[i++];

        while (i < parts.length) {
            String token = parts[i];
            if (token.equals(">") || token.equals("1>")) {
                i++;
                if (i < parts.length) {
                    result.outputFile = parts[i];
                    i++;
                }
                break; // Only one redirection for now
            } else {
                result.args.add(token);
                i++;
            }
        }
        return result;
    }

    private static String[] tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (inDoubleQuotes) {
                if (c == '\\') {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '"' || next == '\\') {
                            current.append(next);
                            i++;
                        } else {
                            current.append(c);
                        }
                    } else {
                        current.append(c);
                    }
                } else if (c == '"') {
                    inDoubleQuotes = false;
                } else {
                    current.append(c);
                }
            } else {
                if (c == '\\') {
                    if (i + 1 < input.length()) {
                        i++;
                        current.append(input.charAt(i));
                    } else {
                        current.append(c);
                    }
                } else if (c == '\'') {
                    inSingleQuotes = true;
                } else if (c == '"') {
                    inDoubleQuotes = true;
                } else if (Character.isWhitespace(c)) {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(c);
                }
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens.toArray(new String[0]);
    }

    private static void writeToFile(String filename, String content) {
        try (PrintStream ps = new PrintStream(filename)) {
            ps.print(content);
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    private static void runExternalProgram(String command, List<String> args, String outputFile) {
        try {
            List<String> commandList = new ArrayList<>();
            commandList.add(command);
            commandList.addAll(args);

            ProcessBuilder builder = new ProcessBuilder(commandList);
            builder.directory(new File(System.getProperty("user.dir")));

            if (outputFile != null) {
                builder.redirectOutput(new File(outputFile));
                builder.redirectError(ProcessBuilder.Redirect.INHERIT);  // ← Important fix
            } else {
                builder.inheritIO();
            }

            Process process = builder.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println(command + ": command not found");
        }
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