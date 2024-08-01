const fs = require("fs");
const path = require("path");

const filePath = path.join(__dirname, "../server/application-server/openapi.yaml");

fs.readFile(filePath, "utf8", (err, data) => {
  if (err) {
    console.error("Error reading the file:", err);
    return;
  }

  const pattern = /tags:\n\s*-\s*.*?-controller\b/g;

  // Function to remove '-controller' from the tag
  const updatedContent = data.replace(pattern, (match) => {
    return match.replace("-controller", "");
  });

  fs.writeFile(filePath, updatedContent, "utf8", (err) => {
    if (err) {
      console.error("Error writing the file:", err);
      return;
    }
  });
});
