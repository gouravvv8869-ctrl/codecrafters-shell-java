const readline = require("readline");

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
});

function prompt() {
  rl.question("$ ", (answer) => {
    if (answer.startsWith("echo ")) {
      const args = answer.slice("echo ".length);
      console.log(args);
    } else {
      console.log(`${answer}: command not found`);
    }
    prompt();
  });
}

prompt();