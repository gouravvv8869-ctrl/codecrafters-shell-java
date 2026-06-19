import java.io.IOException;
import java.util.Scanner;

public class Main {

    // Simple class to store our single background job's details
    static class BackgroundJob {
        int id;
        long pid; // Process.pid() returns a long
        String command;
        String status;

        public BackgroundJob(int id, long pid, String command) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = "Running";
        }
    }

    // Single static reference for this stage's background job
    private static BackgroundJob activeJob = null;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            // Handle the 'exit' command
            if (input.equals("exit")) {
                break;
            }

            // Handle the 'jobs' builtin command
            if (input.equals("jobs")) {
                handleJobsBuiltin();
                continue;
            }

            // Check if this is a background command
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
            // %-24s left-aligns the status and pads it with spaces to exactly 24 characters
            System.out.printf("[%d]+  %-24s %s\n", activeJob.id, activeJob.status, activeJob.command);
        }
    }

    private static void executeCommand(String fullCommand, boolean isBackground) {
        // Strip the '&' only for execution, keep it for the recorded command string
        String execCommand = isBackground ? fullCommand.substring(0, fullCommand.length() - 1).trim() : fullCommand;
        String[] tokens = execCommand.split("\\s+");

        try {
            ProcessBuilder pb = new ProcessBuilder(tokens);
            pb.inheritIO(); 

            Process process = pb.start();

            if (isBackground) {
                long pid = process.toHandle().pid();
                
                // Track this as job #1
                activeJob = new BackgroundJob(1, pid, fullCommand);
                
                // Print the standard initial background confirmation line: [job_id] pid
                System.out.printf("[%d] %d\n", activeJob.id, activeJob.pid);
            } else {
                // Foreground process: wait for it to complete
                process.waitFor();
            }

        } catch (IOException | InterruptedException e) {
            System.out.println(tokens[0] + ": command not found");
        }
    }
}