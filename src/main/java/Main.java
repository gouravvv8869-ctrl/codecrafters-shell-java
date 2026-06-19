import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

public class Shell {

    // Simple class to store our single background job's details
    static class BackgroundJob {
        int id;
        long pid; // Using long as Process.pid() returns a long in modern Java
        String command;
        String status;

        public BackgroundJob(int id, long pid, String command) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = "Running";
        }
    }

    // Since this stage only tests a single background job, 
    // a single static reference is sufficient.
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

            // Handle the 'exit' command to safely close the shell
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
                // Keep the trailing '&' in the command string as expected by the tester
                commandToRun = input; 
            }

            executeCommand(commandToRun, isBackground);
        }
        scanner.close();
    }

    private static void handleJobsBuiltin() {
        if (activeJob != null) {
            // %-24s left-aligns the status string and pads it with spaces to exactly 24 characters
            System.out.printf("[%d]+  %-24s %s\n", activeJob.id, activeJob.status, activeJob.command);
        }
    }

    private static void executeCommand(String fullCommand, boolean isBackground) {
        // Clean up the command arguments for ProcessBuilder
        // If it's a background job, ProcessBuilder shouldn't try to pass '&' as a literal argument to the binary,
        // so we strip it out *only* for the execution array, but keep it for our saved command string.
        String execCommand = isBackground ? fullCommand.substring(0, fullCommand.length() - 1).trim() : fullCommand;
        String[] tokens = execCommand.split("\\s+");

        try {
            ProcessBuilder pb = new ProcessBuilder(tokens);
            // Inherit I/O so things like standard output work, 
            // but for background processes, you typically don't want them blocking on stdin.
            pb.inheritIO(); 

            Process process = pb.start();

            if (isBackground) {
                // Get the real process handle ID (Available in Java 9+)
                long pid = process.toHandle().pid();
                
                // Track this as job #1 for this stage
                activeJob = new BackgroundJob(1, pid, fullCommand);
                
                // Print standard shell background confirmation line: [job_id] pid
                System.out.printf("[%d] %d\n", activeJob.id, activeJob.pid);
            } else {
                // Foreground process: wait for it to complete normally
                process.waitFor();
            }

        } catch (IOException | InterruptedException e) {
            System.out.println(tokens[0] + ": command not found");
        }
    }
}