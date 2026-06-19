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
        Process process;

        public BackgroundJob(int id, long pid, String command, Process process) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = "Running";
            this.process = process;
        }
    }

    private static List<BackgroundJob> activeJobs = new ArrayList<>();
    private static File currentWorkingDirectory = new File(System.getProperty("user.dir"));

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            // Automatic reaping point before showing the prompt
            reapAndPrintCompletedJobsOnly();

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

            if (rawTokens.contains("|")) {
                executePipeline(rawTokens, isBackground, input);
            } else {
                executeCommand(commandToRun, isBackground);
            }
        }
        scanner.close();
    }

    /**
     * Automatic prompt reaper: Checks statuses, prints ONLY completed ("Done") jobs,
     * and clears them from active tracking.
     */
    private static void reapAndPrintCompletedJobsOnly() {
        int totalJobs = activeJobs.size();
        List<BackgroundJob> jobsToRemove = new ArrayList<>();

        // Refresh process states
        for (BackgroundJob job : activeJobs) {
            if (job.status.equals("Running") && !job.process.isAlive()) {
                job.status = "Done";
            }
        }

        // Output only what finished
        for (int i = 0; i < totalJobs; i++) {
            BackgroundJob job = activeJobs.get(i);
            if (job.status.equals("Done")) {
                String symbol = (i == totalJobs - 1) ? "+" : (i == totalJobs - 2) ? "-" : " ";

                String cleanCmd = job.command;
                if (cleanCmd.endsWith("&")) {
                    cleanCmd = cleanCmd.substring(0, cleanCmd.length() - 1).trim();
                }
                System.out.printf("[%d]%s  %-24s %s\n", job.id, symbol, job.status, cleanCmd);
                jobsToRemove.add(job);
            }
        }
        System.out.flush();

        activeJobs.removeAll(jobsToRemove);
    }

    /**
     * Builtin 'jobs' handler: Refreshes execution states, prints EVERYTHING sequentially
     * in original ID order, and clears out the finished ones afterward.
     */
    private static void handleJobsBuiltin() {
        int totalJobs = activeJobs.size();
        List<BackgroundJob> jobsToRemove = new ArrayList<>();

        // 1. Refresh states first without printing anything yet
        for (BackgroundJob job : activeJobs) {
            if (job.status.equals("Running") && !job.process.isAlive()) {
                job.status = "Done";
            }
        }

        // 2. Print everything sequentially in index order (1, 2, 3...)
        for (int i = 0; i < totalJobs; i++) {
            BackgroundJob job = activeJobs.get(i);
            String symbol = (i == totalJobs - 1) ? "+" : (i == totalJobs - 2) ? "-" : " ";

            if (job.status.equals("Done")) {
                String cleanCmd = job.command;
                if (cleanCmd.endsWith("&")) {
                    cleanCmd = cleanCmd.substring(0, cleanCmd.length() - 1).trim();
                }
                System.out.printf("[%d]%s  %-24s %s\n", job.id, symbol, job.status, cleanCmd);
                jobsToRemove.add(job);
            } else {
                System.out.printf("[%d]%s  %-24s %s\n", job.id, symbol, job.status, job.command);
            }
        }
        System.out.flush();

        // 3. Clean up the reaped entries after the full table has been printed
        activeJobs.removeAll(jobsToRemove);
    }

    private static void executePipeline(List<String> rawTokens, boolean isBackground, String rawInput) {
        List<String> tokens = new ArrayList<>(rawTokens);
        if (isBackground && !tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("&")) {
            tokens.remove(tokens.size() - 1);
        }

        int pipeIndex = tokens.indexOf("|");
        List<String> cmd1Tokens = new ArrayList<>(tokens.subList(0, pipeIndex));
        List<String> cmd2Tokens = new ArrayList<>(tokens.subList(pipeIndex + 1, tokens.size()));

        List<String> cleanCmd1 = new ArrayList<>();
        String leftStdinFile = null;
        for (int i = 0; i < cmd1Tokens.size(); i++) {
            String t = cmd1Tokens.get(i);
            if (t.equals("<") && i + 1 < cmd1Tokens.size()) {
                leftStdinFile = cmd1Tokens.get(i + 1);
                i++;
            } else {
                cleanCmd1.add(t);
            }
        }

        List<String> cleanCmd2 = new ArrayList<>();
        String rightStdoutFile = null;
        boolean appendStdout = false;
        for (int i = 0; i < cmd2Tokens.size(); i++) {
            String t = cmd2Tokens.get(i);
            if ((t.equals(">") || t.equals("1>")) && i + 1 < cmd2Tokens.size()) {
                rightStdoutFile = cmd2Tokens.get(i + 1);
                appendStdout = false;
                i++;
            } else if ((t.equals(">>") || t.equals("1>>")) && i + 1 < cmd2Tokens.size()) {
                rightStdoutFile = cmd2Tokens.get(i + 1);
                appendStdout = true;
                i++;
            } else {
                cleanCmd2.add(t);
            }
        }

        try {
            ProcessBuilder pb1 = new ProcessBuilder(cleanCmd1).directory(currentWorkingDirectory);
            ProcessBuilder pb2 = new ProcessBuilder(cleanCmd2).directory(currentWorkingDirectory);

            if (leftStdinFile != null) {
                File file = new File(leftStdinFile);
                if (!file.isAbsolute()) file = new File(currentWorkingDirectory, leftStdinFile);
                pb1.redirectInput(file);
            } else {
                pb1.redirectInput(ProcessBuilder.Redirect.INHERIT);
            }
            pb1.redirectError(ProcessBuilder.Redirect.INHERIT);

            if (rightStdoutFile != null) {
                File file = new File(rightStdoutFile);
                if (!file.isAbsolute()) file = new File(currentWorkingDirectory, rightStdoutFile);
                if (file.getParentFile() != null) file.getParentFile().mkdirs();
                pb2.redirectOutput(appendStdout ? ProcessBuilder.Redirect.appendTo(file) : ProcessBuilder.Redirect.to(file));
            } else {
                pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }
            pb2.redirectError(ProcessBuilder.Redirect.INHERIT);

            List<Process> pipeline = ProcessBuilder.startPipeline(List.of(pb1, pb2));
            Process finalProcess = pipeline.get(pipeline.size() - 1);

            if (isBackground) {
                long pid = finalProcess.toHandle().pid();
                int nextJobId = 1;
                if (!activeJobs.isEmpty()) {
                    int maxId = 0;
                    for (BackgroundJob job : activeJobs) {
                        if (job.id > maxId) maxId = job.id;
                    }
                    nextJobId = maxId + 1;
                }
                BackgroundJob newJob = new BackgroundJob(nextJobId, pid, rawInput, finalProcess);
                activeJobs.add(newJob);

                System.out.printf("[%d] %d\n", newJob.id, newJob.pid);
                System.out.flush();
            } else {
                finalProcess.waitFor();
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("pipeline execution failed");
            System.out.flush();
        }
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

        if (commandTokens.isEmpty()) return;

        try {
            ProcessBuilder pb = new ProcessBuilder(commandTokens).directory(currentWorkingDirectory);

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
                int nextJobId = 1;
                if (!activeJobs.isEmpty()) {
                    int maxId = 0;
                    for (BackgroundJob job : activeJobs) {
                        if (job.id > maxId) maxId = job.id;
                    }
                    nextJobId = maxId + 1;
                }
                BackgroundJob newJob = new BackgroundJob(nextJobId, pid, fullCommand, process);
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
            System.out.println(path != null ? target + " is " + path : target + ": not found");
        }
        System.out.flush();
    }

    private static void handleCdBuiltin(List<String> tokens) {
        String targetPath = (tokens.size() > 1) ? tokens.get(1) : "~";
        File newDir;
        if (targetPath.equals("~")) {
            String homeEnv = System.getenv("HOME");
            if (homeEnv == null) homeEnv = System.getProperty("user.home");
            newDir = new File(homeEnv);
        } else {
            newDir = new File(targetPath);
            if (!newDir.isAbsolute()) newDir = new File(currentWorkingDirectory, targetPath);
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
        boolean inSingleQuotes = false, inDoubleQuotes = false, escaped = false, tokenActive = false;

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
        if (tokenActive || currentToken.length() > 0) tokens.add(currentToken.toString());
        return tokens;
    }

    private static String getPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String directory : pathEnv.split(":")) {
            File file = new File(directory, command);
            if (file.exists() && file.isFile() && file.canExecute()) return file.getAbsolutePath();
        }
        return null;
    }
}