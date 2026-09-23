// Keep the production entry point separate from the test deployment config,
// while sharing the handler that was verified end to end on the test Worker.
export { default } from "../../d1-api-test/src/index.js";
