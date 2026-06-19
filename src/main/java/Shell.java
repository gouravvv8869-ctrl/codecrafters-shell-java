import java.io.IOException;
import java.util.Scanner;

public class Shell {

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
    }

    private static void executeCommand(String fullCommand, boolean isBackground) {
        String execCommand = isBackground ? fullCommand.substring(0, fullCommand.length() - 1).trim() : fullCommand;
        String[] tokens = execCommand.split("\\s+");

        try {
            ProcessBuilder pb = new ProcessBuilder(tokens);
            pb.inheritIO(); 

            Process process = pb.start();

            if (isBackground) {
                long pid = process.toHandle().pid();
                activeJob = new BackgroundJob(1, pid, fullCommand);
                System.out.printf("[%d] %d\n", activeJob.id, activeJob.pid);
            } else {
                process.waitFor();
            }

        } catch (IOException | InterruptedException e) {
            System.out.println(tokens[0] + ": command not found");
        }
    }

    private static String getPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        
        String[] directories = pathEnv.split(":");
        for (String directory : directories) {
            java.io.File file = new java.io.File(directory, command);
            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}