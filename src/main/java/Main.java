import java.util.LinkedHashMap;
import java.util.Map;

class Job {
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

class JobTable {
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

// In your builtin dispatcher:
static void runJobsBuiltin(JobTable jobTable) {
    Map<Integer, Job> jobs = jobTable.getJobs();
    int mostRecent = jobTable.getMostRecentJobId();

    for (Job job : jobs.values()) {
        char marker = (job.id == mostRecent) ? '+' : '-';
        String statusField = String.format("%-24s", job.status);
        System.out.println("[" + job.id + "]" + marker + "  " + statusField + job.command + " &");
    }
}