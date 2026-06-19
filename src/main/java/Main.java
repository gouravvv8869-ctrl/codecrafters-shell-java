import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine();
            String commandName = input.split(" ")[0];

            System.out.println(commandName + ": command not found");
        }
    }
}