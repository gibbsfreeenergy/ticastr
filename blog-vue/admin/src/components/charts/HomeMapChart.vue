<template>
  <v-chart v-bind="$attrs" :option="option" autoresize />
</template>

<script>
import ECharts from "vue-echarts";
import { registerMap, use } from "echarts/core";
import { MapChart } from "echarts/charts";
import { GeoComponent, TooltipComponent, VisualMapComponent } from "echarts/components";
import { SVGRenderer } from "echarts/renderers";
import chinaMap from "../../assets/js/china";

use([SVGRenderer, MapChart, GeoComponent, TooltipComponent, VisualMapComponent]);
registerMap("china", chinaMap);

export default {
  name: "HomeMapChart",
  inheritAttrs: false,
  components: { "v-chart": ECharts },
  props: {
    option: {
      type: Object,
      required: true
    }
  }
};
</script>
