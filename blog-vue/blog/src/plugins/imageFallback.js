const FALLBACK_IMAGE = "/images/default-image.svg";
const pendingImages = new WeakSet();

function setFallback(image) {
  if (!(image instanceof HTMLImageElement)) {
    return;
  }
  if (image.dataset.imageFallback === "true") {
    return;
  }
  image.dataset.imageFallback = "true";
  image.removeAttribute("srcset");
  image.src = FALLBACK_IMAGE;
}

function checkImage(image) {
  if (!(image instanceof HTMLImageElement)) {
    return;
  }
  const source = image.getAttribute("src") || "";
  if (!source) {
    setFallback(image);
    return;
  }
  if (!pendingImages.has(image) && /^(https?:)?\/\//.test(source)) {
    pendingImages.add(image);
    window.setTimeout(() => {
      pendingImages.delete(image);
      if (image.naturalWidth === 0) {
        setFallback(image);
      }
    }, 2500);
  }
}

function scanImages(node) {
  if (node instanceof HTMLImageElement) {
    checkImage(node);
  }
  if (node.querySelectorAll) {
    node.querySelectorAll("img").forEach(checkImage);
  }
}

export function installImageFallback() {
  document.addEventListener("error", event => {
    setFallback(event.target);
  }, true);

  const observer = new MutationObserver(mutations => {
    mutations.forEach(mutation => {
      if (mutation.type === "childList") {
        mutation.addedNodes.forEach(scanImages);
      } else if (mutation.type === "attributes") {
        checkImage(mutation.target);
      }
    });
  });
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["src"],
    childList: true,
    subtree: true
  });

  scanImages(document);
}
