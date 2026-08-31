<template>
  <v-chart v-bind="$attrs" :option="option" autoresize />
</template>

<script>
import ECharts from "vue-echarts";
import { use } from "echarts/core";
import { BarChart, LineChart, PieChart } from "echarts/charts";
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent
} from "echarts/components";
import { SVGRenderer } from "echarts/renderers";

use([
  SVGRenderer,
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent
]);

export default {
  name: "HomeChart",
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
