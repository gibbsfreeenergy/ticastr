import {
  ElAside,
  ElAutocomplete,
  ElAvatar,
  ElBreadcrumb,
  ElBreadcrumbItem,
  ElButton,
  ElCard,
  ElCheckbox,
  ElCheckboxGroup,
  ElCol,
  ElContainer,
  ElDatePicker,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElImage,
  ElInput,
  ElInputNumber,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElPagination,
  ElPopconfirm,
  ElPopover,
  ElRadio,
  ElRadioGroup,
  ElRow,
  ElSelect,
  ElSubMenu,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
  ElTree,
  ElUpload,
  vLoading
} from "element-plus";
import "element-plus/es/components/autocomplete/style/css";
import "element-plus/es/components/avatar/style/css";
import "element-plus/es/components/breadcrumb/style/css";
import "element-plus/es/components/card/style/css";
import "element-plus/es/components/checkbox/style/css";
import "element-plus/es/components/col/style/css";
import "element-plus/es/components/container/style/css";
import "element-plus/es/components/date-picker/style/css";
import "element-plus/es/components/dialog/style/css";
import "element-plus/es/components/dropdown/style/css";
import "element-plus/es/components/empty/style/css";
import "element-plus/es/components/form/style/css";
import "element-plus/es/components/header/style/css";
import "element-plus/es/components/image/style/css";
import "element-plus/es/components/input-number/style/css";
import "element-plus/es/components/main/style/css";
import "element-plus/es/components/menu/style/css";
import "element-plus/es/components/option/style/css";
import "element-plus/es/components/pagination/style/css";
import "element-plus/es/components/popconfirm/style/css";
import "element-plus/es/components/popover/style/css";
import "element-plus/es/components/radio/style/css";
import "element-plus/es/components/row/style/css";
import "element-plus/es/components/select/style/css";
import "element-plus/es/components/switch/style/css";
import "element-plus/es/components/table/style/css";
import "element-plus/es/components/tabs/style/css";
import "element-plus/es/components/tag/style/css";
import "element-plus/es/components/tree/style/css";
import "element-plus/es/components/upload/style/css";
import "element-plus/es/components/loading/style/css";

export function registerAdminElementComponents(app) {
  [
    ElAside,
    ElAutocomplete,
    ElAvatar,
    ElBreadcrumb,
    ElBreadcrumbItem,
    ElButton,
    ElCard,
    ElCheckbox,
    ElCheckboxGroup,
    ElCol,
    ElContainer,
    ElDatePicker,
    ElDialog,
    ElDropdown,
    ElDropdownItem,
    ElDropdownMenu,
    ElEmpty,
    ElForm,
    ElFormItem,
    ElHeader,
    ElImage,
    ElInput,
    ElInputNumber,
    ElMain,
    ElMenu,
    ElMenuItem,
    ElOption,
    ElPagination,
    ElPopconfirm,
    ElPopover,
    ElRadio,
    ElRadioGroup,
    ElRow,
    ElSelect,
    ElSubMenu,
    ElSwitch,
    ElTable,
    ElTableColumn,
    ElTabPane,
    ElTabs,
    ElTag,
    ElTree,
    ElUpload
  ].forEach(component => app.component(component.name, component));
  app.directive("loading", vLoading);
}
