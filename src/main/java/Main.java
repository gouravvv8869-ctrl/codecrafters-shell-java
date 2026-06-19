private static List<String> parseCommand(String input) {
    List<String> tokens = new ArrayList<>();

    StringBuilder current = new StringBuilder();

    boolean escaped = false;

    for (int i = 0; i < input.length(); i++) {
        char c = input.charAt(i);

        if (escaped) {
            current.append(c);
            escaped = false;
            continue;
        }

        if (c == '\\') {
            escaped = true;
            continue;
        }

        if (Character.isWhitespace(c)) {

            if (current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
            }

            continue;
        }

        current.append(c);
    }

    if (current.length() > 0) {
        tokens.add(current.toString());
    }

    return tokens;
}