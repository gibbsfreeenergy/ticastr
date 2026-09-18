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
    defaultTheme: "light",
    themes: {
      light: {
        colors: {
          primary: "#55766b",
          secondary: "#b8b5d8",
          surface: "#fffdf9",
          background: "#f8f7f3"
        }
      },
      dark: {
        colors: {
          primary: "#9dbbaa",
          secondary: "#c8c5e5",
          surface: "#202b38",
          background: "#18202b"
        }
      }
    }
  }
});
