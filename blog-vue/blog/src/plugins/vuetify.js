import "@mdi/font/css/materialdesignicons.css";
import "vuetify/styles";
import { createVuetify } from "vuetify";
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
    defaultSet: "mdi"
  },
  theme: {
    defaultTheme: "light"
  }
});
