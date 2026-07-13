import hljs from "highlight.js/lib/core";
import "highlight.js/styles/atom-one-dark.css";
import bash from "highlight.js/lib/languages/bash";
import c from "highlight.js/lib/languages/c";
import cpp from "highlight.js/lib/languages/cpp";
import css from "highlight.js/lib/languages/css";
import dockerfile from "highlight.js/lib/languages/dockerfile";
import java from "highlight.js/lib/languages/java";
import javascript from "highlight.js/lib/languages/javascript";
import json from "highlight.js/lib/languages/json";
import markdown from "highlight.js/lib/languages/markdown";
import php from "highlight.js/lib/languages/php";
import python from "highlight.js/lib/languages/python";
import sql from "highlight.js/lib/languages/sql";
import typescript from "highlight.js/lib/languages/typescript";
import xml from "highlight.js/lib/languages/xml";
import yaml from "highlight.js/lib/languages/yaml";

[
  ["bash", bash], ["sh", bash], ["shell", bash], ["c", c], ["cpp", cpp],
  ["csharp", c], ["css", css], ["dockerfile", dockerfile], ["java", java],
  ["javascript", javascript], ["js", javascript], ["json", json], ["markdown", markdown],
  ["md", markdown], ["php", php], ["python", python], ["py", python], ["sql", sql],
  ["typescript", typescript], ["ts", typescript], ["xml", xml], ["html", xml],
  ["vue", xml], ["yaml", yaml], ["yml", yaml]
].forEach(([name, language]) => hljs.registerLanguage(name, language));

export { hljs };
