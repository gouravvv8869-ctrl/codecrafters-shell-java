import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    static class BackgroundJob {
        int id;
        long pid;
        String command;
        String status;

        public BackgroundJob(int id, long pid, String command) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = "Running";
        }
    }

    // Dynamic list to track multiple background processes simultaneously
    private static List<BackgroundJob> activeJobs = new ArrayList<>();
    private static File currentWorkingDirectory = new File(System.getProperty("user.dir"));

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush(); 

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            List<String> rawTokens = parseTokens(input);
            if (rawTokens.isEmpty()) {
                continue;
            }

            String command = rawTokens.get(0);

            if (command.equals("exit")) {
                break;
            }

            if (command.equals("jobs")) {
                handleJobsBuiltin();
                continue;
            }

            if (command.equals("type")) {
                handleTypeBuiltin(rawTokens);
                continue;
            }

            if (command.equals("cd")) {
                handleCdBuiltin(rawTokens);
                continue;
            }

            if (command.equals("pwd")) {
                System.out.println(currentWorkingDirectory.getAbsolutePath());
                System.out.flush();
                continue;
            }

            boolean isBackground = false;
            String commandToRun = input;
            if (input.endsWith("&")) {
                isBackground = true;
                commandToRun = input; 
            }

            executeCommand(commandToRun, isBackground);
        }
        scanner.close();
    }

    private static void handleJobsBuiltin() {
        // Render all tracked running background jobs in the order they were executed
        for (int i = 0; i < activeJobs.size(); i++) {
            BackgroundJob job = activeJobs.get(i);
            // The '+' suffix matches standard shell tracking behavior for the current/last job
            String suffix = (i == activeJobs.size() - 1) ? "+" : "-";
            System.out.printf("[%d]%s  %-24s %s\n", job.id, suffix, job.status, job.command);
        }
        System.out.flush();
    }

    private static void handleTypeBuiltin(List<String> tokens) {
        if (tokens.size() < 2) {
            System.out.println("type: missing operand");
            System.out.flush();
            return;
        }
        String target = tokens.get(1);
        
        if (target.equals("exit") || target.equals("echo") || target.equals("type") || target.equals("jobs") || target.equals("cd") || target.equals("pwd")) {
            System.out.println(target + " is a shell builtin");
        } else {
            String path = getPath(target);
            if (path != null) {
                System.out.println(target + " is " + path);
            } else {
                System.out.println(target + ": not found");
            }
        }
        System.out.flush();
    }

    private static void handleCdBuiltin(List<String> tokens) {
        String targetPath = (tokens.size() > 1) ? tokens.get(1) : "~";

        File newDir;
        if (targetPath.equals("~")) {
            String homeEnv = System.getenv("HOME");
            if (homeEnv == null) {
                homeEnv = System.getProperty("user.home");
            }
            newDir = new File(homeEnv);
        } else {
            newDir = new File(targetPath);
            if (!newDir.isAbsolute()) {
                newDir = new File(currentWorkingDirectory, targetPath);
            }
        }

        try {
            newDir = newDir.getCanonicalFile();
        } catch (IOException e) {
            newDir = newDir.getAbsoluteFile();
        }

        if (newDir.exists() && newDir.isDirectory()) {
            currentWorkingDirectory = newDir;
        } else {
            System.out.println("cd: " + targetPath + ": No such file or directory");
            System.out.flush();
        }
    }

    private static List<String> parseTokens(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;
        boolean tokenActive = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaped) {
                currentToken.append(c);
                escaped = false;
                tokenActive = true;
            } else if (c == '\\' && !inSingleQuotes) {
                if (inDoubleQuotes) {
                    if (i + 1 < input.length() && ("\"\\$`".indexOf(input.charAt(i + 1)) != -1)) {
                        escaped = true;
                    } else {
                        currentToken.append(c);
                    }
                } else {
                    escaped = true;
                }
                tokenActive = true;
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                tokenActive = true;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                tokenActive = true;
            } else if (Character.isWhitespace(c) && !inSingleQuotes && !inDoubleQuotes) {
                if (tokenActive || currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                    tokenActive = false;
                }
            } else {
                currentToken.append(c);
                tokenActive = true;
            }
        }

        if (tokenActive || currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }

    private static void executeCommand(String fullCommand, boolean isBackground) {
        String execCommand = isBackground ? fullCommand.substring(0, fullCommand.length() - 1).trim() : fullCommand;
        List<String> rawTokens = parseTokens(execCommand);

        List<String> commandTokens = new ArrayList<>();
        String stdoutRedirectFile = null;
        String stderrRedirectFile = null;
        boolean appendStdout = false;
        boolean appendStderr = false;

        for (int i = 0; i < rawTokens.size(); i++) {
            String token = rawTokens.get(i);
            if ((token.equals(">") || token.equals("1>")) && i + 1 < rawTokens.size()) {
                stdoutRedirectFile = rawTokens.get(i + 1);
                appendStdout = false;
                i++;
            } else if ((token.equals(">>") || token.equals("1>>")) && i + 1 < rawTokens.size()) {
                stdoutRedirectFile = rawTokens.get(i + 1);
                appendStdout = true;
                i++;
            } else if (token.equals("2>") && i + 1 < rawTokens.size()) {
                stderrRedirectFile = rawTokens.get(i + 1);
                appendStderr = false;
                i++;
            } else if (token.equals("2>>") && i + 1 < rawTokens.size()) {
                stderrRedirectFile = rawTokens.get(i + 1);
                appendStderr = true;
                i++;
            } else {
                commandTokens.add(token);
            }
        }

        if (commandTokens.isEmpty()) {
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(commandTokens);
            pb.directory(currentWorkingDirectory);

            if (stdoutRedirectFile != null) {
                File file = new File(stdoutRedirectFile);
                if (!file.isAbsolute()) file = new File(currentWorkingDirectory, stdoutRedirectFile);
                if (file.getParentFile() != null) file.getParentFile().mkdirs();
                pb.redirectOutput(appendStdout ? ProcessBuilder.Redirect.appendTo(file) : ProcessBuilder.Redirect.to(file));
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            if (stderrRedirectFile != null) {
                File file = new File(stderrRedirectFile);
                if (!file.isAbsolute()) file = new File(currentWorkingDirectory, stderrRedirectFile);
                if (file.getParentFile() != null) file.getParentFile().mkdirs();
                pb.redirectError(appendStderr ? ProcessBuilder.Redirect.appendTo(file) : ProcessBuilder.Redirect.to(file));
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

            Process process = pb.start();

            if (isBackground) {
                long pid = process.toHandle().pid();
                // Assign a unique sequential job ID based on list history tracking count
                int nextJobId = activeJobs.size() + 1;
                BackgroundJob newJob = new BackgroundJob(nextJobId, pid, fullCommand);
                activeJobs.add(newJob);
                
                System.out.printf("[%d] %d\n", newJob.id, newJob.pid);
                System.out.flush();
            } else {
                process.waitFor();
            }

        } catch (IOException | InterruptedException e) {
            System.out.println(commandTokens.get(0) + ": command not found");
            System.out.flush();
        }
    }

    private static String getPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        
        String[] directories = pathEnv.split(":");
        for (String directory : directories) {
            File file = new File(directory, command);
            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}