<script setup lang="ts">
import { axiosInstance } from "@halo-dev/api-client";
import {
  VButton,
  VCard,
  VEmpty,
  VLoading,
  VModal,
  VPagination,
  VPageHeader,
  VSpace,
  VTag,
  Dialog,
  Toast,
  IconRefreshLine,
} from "@halo-dev/components";
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import IconWallpaper from "../components/IconWallpaper.vue";

const CONSOLE_BASE = "/apis/console.api.bingwallpaper.dearend.wang/v1alpha1";
const CORE_BASE = "/apis/bingwallpaper.dearend.wang/v1alpha1";

interface WallpaperSpec {
  date: string;
  year: string;
  title: string;
  caption: string;
  subtitle: string;
  copyright: string;
  description: string;
  bingUrl: string;
  url: string;
  urlbase?: string;
}

const SIZES = [
  "UHD",
  "1920x1200",
  "1920x1080",
  "1366x768",
  "1280x720",
  "1024x768",
  "800x600",
  "768x1280",
  "480x800",
  "400x240",
];

function urlbaseOf(spec: WallpaperSpec): string | null {
  if (spec.urlbase) return spec.urlbase;
  const path = (spec.bingUrl || "").replace(
    /^https?:\/\/(cn|www)\.bing\.com/,
    ""
  );
  if (!path.startsWith("/th?id=")) return null;
  return path.replace(/_(UHD|\d+x\d+)\.jpg$/, "");
}

function sizedUrl(spec: WallpaperSpec, size: string): string {
  const base = urlbaseOf(spec);
  return base ? `https://cn.bing.com${base}_${size}.jpg` : spec.url;
}

interface WallpaperItem {
  metadata: { name: string };
  spec: WallpaperSpec;
}

interface Stats {
  total: number;
  years: { year: string; count: number }[];
  latestDate: string | null;
  todayFetched: boolean;
  startYear: number;
  currentYear: number;
  dataSourceConfigured: boolean;
}

const stats = ref<Stats | null>(null);
const selectedYear = ref("");
const items = ref<WallpaperItem[]>([]);
const page = ref(1);
const size = ref(20);
const loadedSize = ref(20);
const totalInYear = ref(0);
const loading = ref(false);
const fetching = ref(false);
const importing = ref(false);
const importProgress = ref("");
const exportingAll = ref(false);
const exportingYear = ref(false);
const purging = ref(false);
const detail = ref<WallpaperItem | null>(null);
const detailVisible = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);
const initialLoading = ref(true);
const router = useRouter();

function goSettings() {
  router.push({
    path: "/plugins/plugin-bing-wallpaper",
    query: { tab: "basic" },
  });
}

async function loadStats() {
  const { data } = await axiosInstance.get<Stats>(`${CONSOLE_BASE}/wallpapers/stats`);
  stats.value = data;
  if (!selectedYear.value && data.years.length > 0) {
    selectedYear.value = data.years[0].year;
  }
}

async function loadWallpapers() {
  if (!selectedYear.value) {
    items.value = [];
    return;
  }
  loading.value = true;
  try {
    const { data } = await axiosInstance.get(`${CORE_BASE}/wallpapers`, {
      params: {
        page: page.value,
        size: size.value,
        sort: "spec.date,desc",
        fieldSelector: `spec.year=${selectedYear.value}`,
      },
    });
    items.value = data.items;
    totalInYear.value = data.total;
    loadedSize.value = size.value;
  } finally {
    loading.value = false;
  }
}

async function selectYear(year: string) {
  selectedYear.value = year;
  page.value = 1;
  await loadWallpapers();
}

async function onPaginationChange(payload: { page: number; size: number }) {
  if (payload.size !== loadedSize.value) {
    page.value = 1;
  }
  await loadWallpapers();
}

async function fetchToday() {
  fetching.value = true;
  try {
    const { data } = await axiosInstance.post(`${CONSOLE_BASE}/wallpapers/fetch-today`);
    Toast.success(data.created ? `已抓取今日壁纸：${data.wallpaper.date}` : "今日壁纸已存在");
    await refresh();
  } catch (e: any) {
    Toast.error(e?.response?.data?.message || "抓取今日壁纸失败");
  } finally {
    fetching.value = false;
  }
}

async function importAll() {
  if (!stats.value) return;
  importing.value = true;
  let importedTotal = 0;
  let skippedTotal = 0;
  try {
    const { startYear, currentYear } = stats.value;
    for (let year = startYear; year <= currentYear; year++) {
      importProgress.value = `正在导入 ${year} 年（${year - startYear + 1}/${currentYear - startYear + 1}）`;
      try {
        const { data } = await axiosInstance.post(
          `${CONSOLE_BASE}/wallpapers/import`,
          null,
          { params: { year } }
        );
        importedTotal += data.imported;
        skippedTotal += data.skipped;
      } catch (e: any) {
        Toast.error(`${year} 年导入失败：${e?.response?.data?.message || "未知错误"}`);
      }
    }
    Toast.success(`导入完成：新增 ${importedTotal} 条，已存在跳过 ${skippedTotal} 条`);
    await refresh();
  } finally {
    importing.value = false;
    importProgress.value = "";
  }
}

async function importFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  importing.value = true;
  importProgress.value = `正在导入 ${file.name}`;
  try {
    const text = await file.text();
    const { data } = await axiosInstance.post(
      `${CONSOLE_BASE}/wallpapers/import-data`,
      text,
      { headers: { "Content-Type": "application/json" } }
    );
    Toast.success(
      `导入完成：新增 ${data.imported} 条，已存在跳过 ${data.skipped} 条` +
        (data.invalid > 0 ? `，无效 ${data.invalid} 条` : "")
    );
    await refresh();
  } catch (e: any) {
    Toast.error(e?.response?.data?.message || "文件导入失败");
  } finally {
    importing.value = false;
    importProgress.value = "";
  }
}

async function purgeAll() {
  Dialog.warning({
    title: "确定要清空全部壁纸数据吗？",
    description: "此操作不可恢复，建议先导出备份。清空后当天不再自动补抓（次日恢复），手动抓取与导入不受影响。",
    confirmType: "danger",
    confirmText: "清空",
    cancelText: "取消",
    onConfirm: async () => {
      purging.value = true;
      try {
        const { data } = await axiosInstance.delete(`${CONSOLE_BASE}/wallpapers`, { timeout: 120000 });
        Toast.success(`已清空 ${data.deleted} 条数据`);
      } catch (e: any) {
        Toast.error(e?.response?.data?.message || "清空请求异常，刷新后查看实际状态");
      } finally {
        purging.value = false;
        window.location.reload();
      }
    },
  });
}

async function downloadExport(year?: string) {
  const flag = year ? exportingYear : exportingAll;
  flag.value = true;
  try {
    const response = await axiosInstance.get(`${CONSOLE_BASE}/wallpapers/export`, {
      params: year ? { year } : {},
      responseType: "blob",
    });
    const disposition = response.headers["content-disposition"] || "";
    const match = disposition.match(/filename="?([^";]+)"?/);
    const filename = match ? match[1] : year ? `year_${year}.json` : "bing_wallpapers_all.json";
    const url = URL.createObjectURL(response.data);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
    Toast.success(`已导出 ${filename}`);
  } catch (e: any) {
    Toast.error("导出失败");
  } finally {
    flag.value = false;
  }
}

function openDetail(item: WallpaperItem) {
  detail.value = item;
  detailVisible.value = true;
}

async function copyDetailJson() {
  if (!detail.value) return;
  const s = detail.value.spec;
  const base = urlbaseOf(s);
  const formats: Record<string, string> = {};
  if (base) {
    for (const size of SIZES) {
      formats[size] = `https://cn.bing.com${base}_${size}.jpg`;
    }
  }
  const exportObject = {
    title: s.title,
    caption: s.caption,
    subtitle: s.subtitle,
    copyright: s.copyright,
    description: s.description,
    date: s.date,
    bing_url: base ? `https://cn.bing.com${base}_UHD.jpg` : s.bingUrl,
    url: base ? `https://cn.bing.com${base}_1920x1080.jpg` : s.url,
    urlbase: base || "",
    formats,
  };
  await navigator.clipboard.writeText(JSON.stringify(exportObject, null, 2));
  Toast.success("已复制 JSON");
}

async function deleteDetail() {
  if (!detail.value) return;
  const name = detail.value.metadata.name;
  const date = detail.value.spec.date;
  Dialog.warning({
    title: `确定要删除 ${date} 的壁纸吗？`,
    description: "删除后不可恢复，可通过重新导入或抓取今日壁纸恢复。",
    confirmType: "danger",
    confirmText: "删除",
    cancelText: "取消",
    onConfirm: async () => {
      try {
        await axiosInstance.delete(`${CORE_BASE}/wallpapers/${name}`);
        Toast.success("已删除");
        detailVisible.value = false;
        await refresh();
      } catch (e) {
        Toast.error("删除失败");
      }
    },
  });
}

async function refresh() {
  try {
    await loadStats();
    await loadWallpapers();
  } finally {
    initialLoading.value = false;
  }
}

onMounted(refresh);
</script>

<template>
  <div>
    <VPageHeader title="Bing 每日一图">
      <template #icon>
        <IconWallpaper />
      </template>
      <template #actions>
        <VButton size="sm" v-permission="['system:plugins:manage']" @click="goSettings">插件设置</VButton>
        <VButton size="sm" :loading="exportingAll" @click="downloadExport()">
          导出全部 JSON
        </VButton>
        <VButton
          size="sm"
          v-permission="['plugin:plugin-bing-wallpaper:manage']"
          :loading="importing"
          @click="fileInput?.click()"
        >
          导入 JSON 文件
        </VButton>
        <VButton
          size="sm"
          v-permission="['plugin:plugin-bing-wallpaper:manage']"
          :loading="importing"
          :disabled="!stats?.dataSourceConfigured"
          @click="importAll"
        >
          {{ importing ? importProgress : "按接口导入" }}
        </VButton>
        <VButton
          type="secondary"
          v-permission="['plugin:plugin-bing-wallpaper:manage']"
          :loading="fetching"
          @click="fetchToday"
        >
          <template #icon>
            <IconRefreshLine />
          </template>
          抓取今日壁纸
        </VButton>
        <VButton
          type="danger"
          v-permission="['plugin:plugin-bing-wallpaper:manage']"
          :loading="purging"
          @click="purgeAll"
        >
          清空全部数据
        </VButton>
      </template>
    </VPageHeader>
    <input
      ref="fileInput"
      type="file"
      accept=".json,application/json"
      style="display: none"
      @change="importFile"
    />
    <div class="bw-console">
      <VCard title="数据概览">
      <VLoading v-if="initialLoading" />
      <div v-else-if="stats" class="bw-stats">
        <div class="bw-stat">
          <span class="bw-stat-value">{{ stats.total }}</span>
          <span class="bw-stat-label">壁纸总数</span>
        </div>
        <div class="bw-stat">
          <span class="bw-stat-value">{{ stats.years.length }}</span>
          <span class="bw-stat-label">覆盖年份</span>
        </div>
        <div class="bw-stat">
          <span class="bw-stat-value">{{ stats.latestDate || "—" }}</span>
          <span class="bw-stat-label">最新日期</span>
        </div>
        <div class="bw-stat">
          <div class="bw-stat-tag">
            <VTag :type="stats.todayFetched ? 'success' : 'warning'">
              {{ stats.todayFetched ? "今日已入库" : "今日未入库" }}
            </VTag>
          </div>
          <span class="bw-stat-label">今日状态</span>
        </div>
      </div>
      <p v-if="stats && !stats.dataSourceConfigured" class="bw-hint">
        尚未配置历史数据接口模板。可到「插件 → Bing 每日一图 → 设置」填写含 {year} 占位符的接口地址后按接口导入，或直接点击「导入 JSON 文件」上传 year_YYYY.json / 合并数据文件。
      </p>
    </VCard>

    <VCard :body-class="['!p-0']" class="bw-list-card">
      <template #header>
        <div class="bw-toolbar">
          <div v-if="stats && stats.years.length" class="bw-year-tabs">
            <button
              v-for="y in stats.years"
              :key="y.year"
              class="bw-year-tab"
              :class="{ active: y.year === selectedYear }"
              @click="selectYear(y.year)"
            >
              {{ y.year }} <span class="bw-year-count">{{ y.count }}</span>
            </button>
          </div>
          <VButton
            v-if="selectedYear"
            size="sm"
            :loading="exportingYear"
            @click="downloadExport(selectedYear)"
          >
            导出 {{ selectedYear }} 年 JSON
          </VButton>
        </div>
      </template>
      <div class="bw-list-body">
      <VLoading v-if="initialLoading || loading" />
      <VEmpty v-else-if="items.length === 0" message="暂无数据，请先导入历史数据或抓取今日壁纸" />
      <div v-else :key="`${selectedYear}-${page}-${size}`" class="bw-grid">
        <div
          v-for="(item, index) in items"
          :key="item.metadata.name"
          class="bw-item"
          :style="{ animationDelay: `${Math.min(index * 25, 300)}ms` }"
          @click="openDetail(item)"
        >
          <img :src="sizedUrl(item.spec, '1366x768')" :alt="item.spec.title" loading="lazy" />
          <div class="bw-item-meta">
            <span class="bw-item-date">{{ item.spec.date }}</span>
            <span class="bw-item-title">{{ item.spec.title || item.spec.subtitle }}</span>
          </div>
        </div>
      </div>

      </div>
      <template #footer>
        <div v-if="totalInYear > 0" class="bw-pagination">
          <VPagination
            v-model:page="page"
            v-model:size="size"
            :total="totalInYear"
            :size-options="[10, 20, 30, 40, 50]"
            @change="onPaginationChange"
          />
        </div>
      </template>
    </VCard>
    </div>

    <VModal v-model:visible="detailVisible" :title="detail?.spec.date || ''" :width="720">
      <div v-if="detail" class="bw-detail">
        <img :src="sizedUrl(detail.spec, '1366x768')" :alt="detail.spec.title" />
        <dl>
          <dt>日期</dt>
          <dd>{{ detail.spec.date }}</dd>
          <dt>标题</dt>
          <dd>{{ detail.spec.title || "—" }}</dd>
          <dt>副标题</dt>
          <dd>{{ detail.spec.subtitle || "—" }}</dd>
          <dt>版权</dt>
          <dd>{{ detail.spec.copyright || "—" }}</dd>
          <dt>描述</dt>
          <dd class="bw-detail-desc">{{ detail.spec.description || "—" }}</dd>
          <dt>官方尺寸</dt>
          <dd class="bw-detail-formats">
            <a
              v-for="size in SIZES"
              :key="size"
              :href="sizedUrl(detail.spec, size)"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ size }}
            </a>
          </dd>
          <dt>必应原图</dt>
          <dd>
            <a :href="sizedUrl(detail.spec, 'UHD')" target="_blank" rel="noopener noreferrer">
              {{ sizedUrl(detail.spec, "UHD") }}
            </a>
          </dd>
        </dl>
      </div>
      <template #footer>
        <VSpace>
          <VButton @click="copyDetailJson">复制 JSON</VButton>
          <VButton type="danger" v-permission="['plugin:plugin-bing-wallpaper:manage']" @click="deleteDetail">删除</VButton>
          <VButton @click="detailVisible = false">关闭</VButton>
        </VSpace>
      </template>
    </VModal>
  </div>
</template>

<style scoped>
.bw-console {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.bw-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  width: 100%;
  padding: 12px 16px;
  background: #f9fafb;
}
.bw-list-body {
  padding: 16px;
}
.bw-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 32px;
}
.bw-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.bw-stat-value {
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
}
.bw-stat-tag {
  height: 28px;
  display: flex;
  align-items: center;
}
.bw-stat-label {
  font-size: 12px;
  color: #64748b;
}
.bw-hint {
  margin-top: 12px;
  font-size: 13px;
  color: #b45309;
}
.bw-year-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.bw-year-tab {
  padding: 4px 12px;
  border: 1px solid #e2e8f0;
 border-radius: 999px;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
}
.bw-year-tab.active {
  background: #4cc2ff;
  border-color: #4cc2ff;
  color: #003250;
  font-weight: 600;
}
.bw-year-count {
  opacity: 0.65;
  font-size: 12px;
}
.bw-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.bw-item {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  animation: bw-rise 0.28s ease-out both;
}
@keyframes bw-rise {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .bw-item {
    animation: none;
  }
}
.bw-item:hover {
  border-color: #4cc2ff;
}
.bw-item img {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  display: block;
  background: #f1f5f9;
}
.bw-item-meta {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.bw-item-date {
  font-size: 12px;
  color: #64748b;
}
.bw-item-title {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.bw-pagination {
  display: flex;
  justify-content: flex-end;
}
.bw-detail img {
  width: 100%;
  border-radius: 8px;
  margin-bottom: 12px;
}
.bw-detail dl {
  display: grid;
  grid-template-columns: 80px 1fr;
  gap: 6px 12px;
  font-size: 13px;
  margin: 0;
}
.bw-detail dt {
  color: #64748b;
}
.bw-detail dd {
  margin: 0;
  word-break: break-all;
}
.bw-detail-desc {
  white-space: pre-line;
}
.bw-detail-formats a {
  display: inline-block;
  margin: 0 10px 4px 0;
  font-size: 12px;
}
</style>