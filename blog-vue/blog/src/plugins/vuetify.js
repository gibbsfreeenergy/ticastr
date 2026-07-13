import "vuetify/styles";
import { createVuetify } from "vuetify";
import { aliases, mdi } from "vuetify/iconsets/mdi-svg";
import {
  mdiArrowRightDropCircle,
  mdiBell,
  mdiBookmark,
  mdiCalendarMonthOutline,
  mdiChartLine,
  mdiChat,
  mdiChatOutline,
  mdiCheckDecagram,
  mdiChevronDoubleRight,
  mdiChevronDown,
  mdiClockOutline,
  mdiClose,
  mdiDotsHorizontalCircle,
  mdiEye,
  mdiEyeOff,
  mdiInboxFull,
  mdiKeyboard,
  mdiLinkVariant,
  mdiMagnify,
  mdiMicrophone,
  mdiPauseCircle,
  mdiShareVariant,
  mdiTagMultiple,
  mdiThumbUp
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
  VDivider,
  VFooter,
  VIcon,
  VImg,
  VMain,
  VNavigationDrawer,
  VPagination,
  VRow,
  VSnackbar,
  VTextField
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
    VDivider,
    VFooter,
    VIcon,
    VImg,
    VMain,
    VNavigationDrawer,
    VPagination,
    VRow,
    VSnackbar,
    VTextField
  },
  icons: {
    aliases: {
      ...aliases,
      "mdi-arrow-right-drop-circle": mdiArrowRightDropCircle,
      "mdi-bell": mdiBell,
      "mdi-bookmark": mdiBookmark,
      "mdi-calendar-month-outline": mdiCalendarMonthOutline,
      "mdi-chart-line": mdiChartLine,
      "mdi-chat": mdiChat,
      "mdi-chat-outline": mdiChatOutline,
      "mdi-check-decagram": mdiCheckDecagram,
      "mdi-chevron-double-right": mdiChevronDoubleRight,
      "mdi-chevron-down": mdiChevronDown,
      "mdi-clock-outline": mdiClockOutline,
      "mdi-close": mdiClose,
      "mdi-dots-horizontal-circle": mdiDotsHorizontalCircle,
      "mdi-eye": mdiEye,
      "mdi-eye-off": mdiEyeOff,
      "mdi-inbox-full": mdiInboxFull,
      "mdi-keyboard": mdiKeyboard,
      "mdi-link-variant": mdiLinkVariant,
      "mdi-magnify": mdiMagnify,
      "mdi-microphone": mdiMicrophone,
      "mdi-pause-circle": mdiPauseCircle,
      "mdi-share-variant": mdiShareVariant,
      "mdi-tag-multiple": mdiTagMultiple,
      "mdi-thumb-up": mdiThumbUp
    },
    defaultSet: "mdi",
    sets: { mdi }
  },
  theme: {
    defaultTheme: "light"
  }
});
