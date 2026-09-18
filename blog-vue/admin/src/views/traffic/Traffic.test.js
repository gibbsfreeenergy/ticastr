import { describe, expect, it } from "vitest";
import TrafficView from "./Traffic.vue";

describe("traffic trend metric mapping", () => {
  it("reads the admin API uniqueIps field for the independent IP chart", () => {
    const values = TrafficView.computed.chartValues.call({
      trendMetric: "uniqueIps",
      trend: [{ uniqueIps: 4 }, { uniqueIps: 6 }]
    });

    expect(values).toEqual([4, 6]);
  });
});
