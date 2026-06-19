import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
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
                executeMultiStagePipeline(rawTokens, isBackground, input);
            } else {
                executeCommand(commandToRun, isBackground);
            }
        }
        scanner.close();
    }

    private static void reapAndPrintCompletedJobsOnly() {
        int totalJobs = activeJobs.size();
        List<BackgroundJob> jobsToRemove = new ArrayList<>();

        for (BackgroundJob job : activeJobs) {
            if (job.status.equals("Running") && !job.process.isAlive()) {
                job.status = "Done";
            }
        }

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

    private static void handleJobsBuiltin() {
        int totalJobs = activeJobs.size();
        List<BackgroundJob> jobsToRemove = new ArrayList<>();

        for (BackgroundJob job : activeJobs) {
            if (job.status.equals("Running") && !job.process.isAlive()) {
                job.status = "Done";
            }
        }

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
        activeJobs.removeAll(jobsToRemove);
    }

    private static boolean isBuiltIn(String cmd) {
        return cmd.equals("echo") || cmd.equals("type") || cmd.equals("cd") || cmd.equals("pwd") || cmd.equals("jobs") || cmd.equals("exit");
    }

    /**
     * Executes arbitrary-stage pipelines natively using ProcessBuilder.startPipeline.
     */
    private static void executeMultiStagePipeline(List<String> rawTokens, boolean isBackground, String rawInput) {
        List<String> tokens = new ArrayList<>(rawTokens);
        if (isBackground && !tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("&")) {
            tokens.remove(tokens.size() - 1);
        }

        List<List<String>> commandsTokensList = new ArrayList<>();
        List<String> currentCmdTokens = new ArrayList<>();
        for (String token : tokens) {
            if (token.equals("|")) {
                if (!currentCmdTokens.isEmpty()) {
                    commandsTokensList.add(currentCmdTokens);
                    currentCmdTokens = new ArrayList<>();
                }
            } else {
                currentCmdTokens.add(token);
            }
        }
        if (!currentCmdTokens.isEmpty()) {
            commandsTokensList.add(currentCmdTokens);
        }

        if (commandsTokensList.isEmpty()) return;

        // Fallback optimizations for single built-in boundary handling logic
        String firstCmdName = commandsTokensList.get(0).get(0);
        if (commandsTokensList.size() == 2 && isBuiltIn(firstCmdName)) {
            executeTwoStageBuiltInLeft(commandsTokensList.get(0), commandsTokensList.get(1), isBackground, rawInput);
            return;
        }
        String lastCmdName = commandsTokensList.get(commandsTokensList.size() - 1).get(0);
        if (commandsTokensList.size() == 2 && isBuiltIn(lastCmdName)) {
            executeTwoStageBuiltInRight(commandsTokensList.get(0), commandsTokensList.get(1), isBackground);
            return;
        }

        // Standard dynamic process construction flow
        List<ProcessBuilder> builders = new ArrayList<>();
        int size = commandsTokensList.size();

        for (int i = 0; i < size; i++) {
            List<String> cmdTokens = commandsTokensList.get(i);
            ProcessBuilder pb = new ProcessBuilder(cmdTokens).directory(currentWorkingDirectory);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            // Configure Redirect.PIPE layout for intermediate steps to satisfy startPipeline()
            if (i == 0) {
                pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
            } else if (i == size - 1) {
                pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            } else {
                pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
            }
            builders.add(pb);
        }

        try {
            List<Process> pipelineProcesses = ProcessBuilder.startPipeline(builders);
            Process finalProcess = pipelineProcesses.get(pipelineProcesses.size() - 1);

            if (isBackground) {
                trackBackgroundJob(finalProcess, rawInput);
            } else {
                finalProcess.waitFor();
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Pipeline execution failed");
            System.out.flush();
        }
    }

    private static void executeTwoStageBuiltInLeft(List<String> cmd1, List<String> cmd2, boolean isBg, String rawInput) {
        try {
            ProcessBuilder pb2 = new ProcessBuilder(cmd2).directory(currentWorkingDirectory);
            pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
            pb2.redirectInput(ProcessBuilder.Redirect.PIPE);

            Process p2 = pb2.start();
            String firstCmd = cmd1.get(0);

            try (PrintStream outStream = new PrintStream(p2.getOutputStream())) {
                PrintStream originalOut = System.out;
                System.setOut(outStream);
                if (firstCmd.equals("echo")) handleEchoExecution(cmd1);
                else if (firstCmd.equals("pwd")) System.out.println(currentWorkingDirectory.getAbsolutePath());
                else if (firstCmd.equals("type")) handleTypeBuiltin(cmd1);
                System.setOut(originalOut);
            }

            if (!isBg) p2.waitFor();
            else trackBackgroundJob(p2, rawInput);
        } catch (Exception e) {
            System.out.println("Pipeline error");
        }
    }

    private static void executeTwoStageBuiltInRight(List<String> cmd1, List<String> cmd2, boolean isBg) {
        try {
            ProcessBuilder pb1 = new ProcessBuilder(cmd1).directory(currentWorkingDirectory);
            pb1.redirectInput(ProcessBuilder.Redirect.INHERIT);
            pb1.redirectError(ProcessBuilder.Redirect.INHERIT);
            pb1.redirectOutput(ProcessBuilder.Redirect.PIPE);

            Process p1 = pb1.start();
            String secondCmd = cmd2.get(0);

            Thread streamDrainer = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p1.getInputStream()))) {
                    while (reader.readLine() != null) {}
                } catch (IOException e) {}
            });
            streamDrainer.start();

            if (secondCmd.equals("type")) handleTypeBuiltin(cmd2);
            else if (secondCmd.equals("echo")) handleEchoExecution(cmd2);
            else if (secondCmd.equals("pwd")) System.out.println(currentWorkingDirectory.getAbsolutePath());

            if (!isBg) {
                p1.waitFor();
                streamDrainer.join();
            }
        } catch (Exception e) {
            System.out.println("Pipeline error");
        }
    }

    private static void handleEchoExecution(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < tokens.size(); i++) {
            sb.append(tokens.get(i));
            if (i < tokens.size() - 1) sb.append(" ");
        }
        System.out.println(sb.toString());
        System.out.flush();
    }

    private static void trackBackgroundJob(Process p, String rawInput) {
        long pid = p.toHandle().pid();
        int nextJobId = 1;
        if (!activeJobs.isEmpty()) {
            int maxId = 0;
            for (BackgroundJob job : activeJobs) {
                if (job.id > maxId) maxId = job.id;
            }
            nextJobId = maxId + 1;
        }
        BackgroundJob newJob = new BackgroundJob(nextJobId, pid, rawInput, p);
        activeJobs.add(newJob);
        System.out.printf("[%d] %d\n", newJob.id, newJob.pid);
        System.out.flush();
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
                trackBackgroundJob(process, fullCommand);
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