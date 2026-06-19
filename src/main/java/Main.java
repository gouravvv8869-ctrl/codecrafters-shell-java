import java.util.ArrayList;
import java.util.List;

class RedirectionResult {
    List<String> argv = new ArrayList<>();
    String stdoutFile = null;
    boolean stdoutAppend = false;
    String stderrFile = null;
    boolean stderrAppend = false;
}

class RedirectionParser {

    // tokens = the already-tokenized command line, e.g. ["ls", "-1", "nonexistent", ">>", "/tmp/cow/ant.md"]
    // Handles: >  1>  >>  1>>  2>  2>>
    static RedirectionResult parse(List<String> tokens) {
        RedirectionResult result = new RedirectionResult();

        int i = 0;
        while (i < tokens.size()) {
            String tok = tokens.get(i);

            switch (tok) {
                case ">":
                case "1>":
                    result.stdoutFile = tokens.get(i + 1);
                    result.stdoutAppend = false;
                    i += 2;
                    break;

                case ">>":
                case "1>>":
                    result.stdoutFile = tokens.get(i + 1);
                    result.stdoutAppend = true;
                    i += 2;
                    break;

                case "2>":
                    result.stderrFile = tokens.get(i + 1);
                    result.stderrAppend = false;
                    i += 2;
                    break;

                case "2>>":
                    result.stderrFile = tokens.get(i + 1);
                    result.stderrAppend = true;
                    i += 2;
                    break;

                default:
                    result.argv.add(tok);
                    i += 1;
                    break;
            }
        }

        return result;
    }
}