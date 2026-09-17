import "vuetify/styles";
import { createVuetify } from "vuetify";
import { aliases, mdi } from "vuetify/iconsets/mdi-svg";
import {
  mdiBell,
  mdiBookOpenVariant,
  mdiCalendarMonthOutline,
  mdiChevronDown,
  mdiClose,
  mdiMagnify,
} from "@mdi/js";
import {
  VApp,
  VAppBar,
  VAvatar,
  VBtn,
  VCard,
  VCarousel,
  VCarouselItem,
  VCol,
  VDialog,
  VFooter,
  VIcon,
  VImg,
  VMain,
  VNavigationDrawer,
  VRow,
  VSnackbar
} from "vuetify/components";

export default createVuetify({
  components: {
    VApp,
    VAppBar,
    VAvatar,
    VBtn,
    VCard,
    VCarousel,
    VCarouselItem,
    VCol,
    VDialog,
    VFooter,
    VIcon,
    VImg,
    VMain,
    VNavigationDrawer,
    VRow,
    VSnackbar
  },
  icons: {
    aliases: {
      ...aliases,
      "mdi-bell": mdiBell,
      "mdi-book-open-variant": mdiBookOpenVariant,
      "mdi-calendar-month-outline": mdiCalendarMonthOutline,
      "mdi-chevron-down": mdiChevronDown,
      "mdi-close": mdiClose,
      "mdi-magnify": mdiMagnify
    },
    defaultSet: "mdi",
    sets: { mdi }
  },
  theme: {
    defaultTheme: "light"
  }
});
