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

    private static BackgroundJob activeJob = null;

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

            if (input.equals("exit")) {
                break;
            }

            if (input.equals("jobs")) {
                handleJobsBuiltin();
                continue;
            }

            if (input.startsWith("type ")) {
                handleTypeBuiltin(input);
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
        if (activeJob != null) {
            System.out.printf("[%d]+  %-24s %s\n", activeJob.id, activeJob.status, activeJob.command);
            System.out.flush();
        }
    }

    private static void handleTypeBuiltin(String input) {
        String target = input.substring(5).trim();
        
        if (target.equals("exit") || target.equals("echo") || target.equals("type") || target.equals("jobs")) {
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

    private static void executeCommand(String fullCommand, boolean isBackground) {
        String execCommand = isBackground ? fullCommand.substring(0, fullCommand.length() - 1).trim() : fullCommand;
        String[] rawTokens = execCommand.split("\\s+");

        List<String> commandTokens = new ArrayList<>();
        
        String stdoutRedirectFile = null;
        String stderrRedirectFile = null;
        boolean appendStdout = false;
        boolean appendStderr = false;

        // Parse tokens accurately based on standard stream rules
        for (int i = 0; i < rawTokens.length; i++) {
            if (rawTokens[i].equals(">") && i + 1 < rawTokens.length) {
                stdoutRedirectFile = rawTokens[i + 1];
                appendStdout = false;
                i++;
            } else if ((rawTokens[i].equals(">>") || rawTokens[i].equals("1>>")) && i + 1 < rawTokens.length) {
                stdoutRedirectFile = rawTokens[i + 1];
                appendStdout = true;
                i++;
            } else if (rawTokens[i].equals("2>") && i + 1 < rawTokens.length) {
                stderrRedirectFile = rawTokens[i + 1];
                appendStderr = false;
                i++;
            } else if (rawTokens[i].equals("2>>")) {
                stderrRedirectFile = rawTokens[i + 1];
                appendStderr = true;
                i++;
            } else {
                commandTokens.add(rawTokens[i]);
            }
        }

        if (commandTokens.isEmpty()) {
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(commandTokens);
            
            // Handle standard output redirection
            if (stdoutRedirectFile != null) {
                File file = new File(stdoutRedirectFile);
                if (file.getParentFile() != null) file.getParentFile().mkdirs();
                
                pb.redirectOutput(appendStdout ? ProcessBuilder.Redirect.appendTo(file) : ProcessBuilder.Redirect.to(file));
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            // Handle standard error redirection
            if (stderrRedirectFile != null) {
                File file = new File(stderrRedirectFile);
                if (file.getParentFile() != null) file.getParentFile().mkdirs();
                
                pb.redirectError(appendStderr ? ProcessBuilder.Redirect.appendTo(file) : ProcessBuilder.Redirect.to(file));
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

            Process process = pb.start();

            if (isBackground) {
                long pid = process.toHandle().pid();
                activeJob = new BackgroundJob(1, pid, fullCommand);
                System.out.printf("[%d] %d\n", activeJob.id, activeJob.pid);
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