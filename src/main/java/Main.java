import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Main {

    // ---------- Job tracking ----------

    static class Job {
        int id;
        long pid;
        String command;
        String status;

        Job(int id, long pid, String command, String status) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = status;
        }
    }

    static class JobTable {
        private final Map<Integer, Job> jobs = new LinkedHashMap<>();
        private int nextJobId = 1;
        private int mostRecentJobId = -1;

        Job addJob(long pid, String command) {
            int id = nextJobId++;
            Job job = new Job(id, pid, command, "Running");
            jobs.put(id, job);
            mostRecentJobId = id;
            return job;
        }

        Map<Integer, Job> getJobs() {
            return jobs;
        }

        int getMostRecentJobId() {
            return mostRecentJobId;
        }
    }

    static final JobTable jobTable = new JobTable();

    // ---------- Main loop ----------

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }

            boolean background = false;
            String commandText = line;
            if (commandText.endsWith("&")) {
                background = true;
                commandText = commandText.substring(0, commandText.length() - 1).trim();
            }

            List<String> argv = splitArgs(commandText);
            if (argv.isEmpty()) {
                continue;
            }

            String cmdName = argv.get(0);

            // Builtins
            if (cmdName.equals("exit")) {
                System.exit(0);
            } else if (cmdName.equals("jobs")) {
                runJobsBuiltin();
                continue;
            } else if (cmdName.equals("cd")) {
                // existing cd logic would go here
                continue;
            } else if (cmdName.equals("echo")) {
                System.out.println(String.join(" ", argv.subList(1, argv.size())));
                continue;
            }

            // External command
            try {
                ProcessBuilder pb = new ProcessBuilder(argv);
                pb.inheritIO();
                Process process = pb.start();

                if (background) {
                    Job job = jobTable.addJob(process.pid(), commandText);
                    System.out.println("[" + job.id + "] " + job.pid);
                    // Do NOT waitFor() — let it run in the background
                } else {
                    process.waitFor();
                }
            } catch (IOException e) {
                System.out.println(cmdName + ": command not found");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Very simple whitespace splitter; replace with your existing parser/tokenizer
    static List<String> splitArgs(String input) {
        List<String> result = new ArrayList<>();
        for (String part : input.split("\\s+")) {
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }

    // ---------- jobs builtin ----------

    static void runJobsBuiltin() {
        Map<Integer, Job> jobs = jobTable.getJobs();
        int mostRecent = jobTable.getMostRecentJobId();

        for (Job job : jobs.values()) {
            char marker = (job.id == mostRecent) ? '+' : '-';
            String statusField = String.format("%-24s", job.status);
            System.out.println("[" + job.id + "]" + marker + "  " + statusField + job.command + " &");
        }
    }
}