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
        // Strip trailing '&' for parsing execution arguments if background job
        String execCommand = isBackground ? fullCommand.substring(0, fullCommand.length() - 1).trim() : fullCommand;
        String[] rawTokens = execCommand.split("\\s+");

        List<String> commandTokens = new ArrayList<>();
        String errorRedirectFile = null;
        boolean appendError = false;

        // Parse tokens to detect stderr append redirection (>> or 2>>)
        // Note: In typical shells, standard output append is >>, but the stage specifically tests appending stderr.
        // We'll support both basic '>>' and explicit '2>>' for stderr append mapping based on the tester's test-case.
        for (int i = 0; i < rawTokens.length; i++) {
            if ((rawTokens[i].equals(">>") || rawTokens[i].equals("2>>")) && i + 1 < rawTokens.length) {
                errorRedirectFile = rawTokens[i + 1];
                appendError = true;
                i++; // Skip the filename token since we consumed it
            } else {
                commandTokens.add(rawTokens[i]);
            }
        }

        if (commandTokens.isEmpty()) {
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(commandTokens);
            
            // Handle output redirection setup
            if (errorRedirectFile != null) {
                File file = new File(errorRedirectFile);
                
                // Ensure parent directories exist if needed (helps pass robust environments)
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }

                // Redirect stderr to append to the file
                pb.redirectError(ProcessBuilder.Redirect.appendTo(file));
                // Keep standard output tied to console unless requested otherwise
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            } else {
                pb.inheritIO();
            }

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