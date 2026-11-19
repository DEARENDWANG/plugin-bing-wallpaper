import { definePlugin } from "@halo-dev/ui-shared";
import { markRaw } from "vue";
import HomeView from "./views/HomeView.vue";
import IconWallpaper from "./components/IconWallpaper.vue";

export default definePlugin({
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/bing-wallpaper",
        name: "BingWallpaper",
        component: HomeView,
        meta: {
          title: "Bing 每日一图",
          searchable: true,
          permissions: ["plugin:plugin-bing-wallpaper:view"],
          menu: {
            name: "Bing 每日一图",
            group: "tool",
            icon: markRaw(IconWallpaper),
            priority: 30,
          },
        },
      },
    },
  ],
});