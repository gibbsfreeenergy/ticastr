import "hyalite";

const LIQUID_GLASS_SELECTOR = ".liquid-glass";
const LIQUID_GLASS_OPTIONS = {
  bevel: 34,
  thickness: 52,
  slope: 2.2,
  shape: "squircle",
  blur: 1,
  dispersion: 1.15,
  shade: 0.38,
  rim: 1.55,
  edgeW: 7,
  sat: 0.9,
  edge: 0.42,
  light: -135,
  smooth: 1,
  materialize: 260,
  settle: 90
};

let watcher = null;

function getHyalite() {
  return typeof window !== "undefined" ? window.Hyalite : null;
}

export function installLiquidGlass() {
  if (watcher || typeof document === "undefined") return;

  const hyalite = getHyalite();
  if (!hyalite?.watch || !document.body) return;

  watcher = hyalite.watch(document.body, LIQUID_GLASS_SELECTOR, LIQUID_GLASS_OPTIONS);
}

export function refreshLiquidGlass() {
  const hyalite = getHyalite();
  if (!hyalite?.refresh || typeof document === "undefined") return;

  document.querySelectorAll(LIQUID_GLASS_SELECTOR).forEach(element => hyalite.refresh(element));
}

export function stopLiquidGlass() {
  watcher?.stop();
  watcher = null;
}
