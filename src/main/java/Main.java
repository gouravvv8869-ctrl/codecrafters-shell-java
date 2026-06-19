import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("$ ");
            String input = reader.readLine();
            if (input == null) break;

            if (input.startsWith("echo ")) {
                String message = input.substring(5);
                System.out.println(message);
            } else {
                System.out.println(input + ": command not found");
            }
        }
    }
}