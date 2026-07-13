import DOMPurify from "dompurify";

export function installSafeHtml(app) {
  app.directive("safe-html", {
    mounted: updateHtml,
    updated: updateHtml
  });
}

function updateHtml(element, binding) {
  element.innerHTML = sanitizeHtml(binding.value);
}

export const sanitizeHtml = value => DOMPurify.sanitize(value ?? "", {
  USE_PROFILES: { html: true }
});
