import type { Category } from "@/lib/types";

/**
 * Фирменные иллюстрации товаров вместо фото: плоские формы на светлом фоне, как предметная съёмка на циклораме.
 * Тип рисунка определяется по названию (русскому или итальянскому), иначе — по категории.
 */

type Kind =
  | "loafer" | "boot" | "sneaker" | "knit" | "shirt" | "coat"
  | "jar" | "tube" | "soap" | "cloth" | "bowl" | "oil" | "pasta" | "coffee" | "vinegar";

const RULES: [RegExp, Kind][] = [
  [/лофер|мокасин|mocassin|loafer/i, "loafer"],
  [/ботиль|челси|сапог|chelsea|stival/i, "boot"],
  [/кед|кроссов|sneaker/i, "sneaker"],
  [/кардиган|свитер|джемпер|maglia|cardigan|cashmere|мерино/i, "knit"],
  [/рубаш|camici|блуз/i, "shirt"],
  [/пальто|cappotto|жакет|giacca/i, "coat"],
  [/шампун|shampoo|гель|флакон/i, "tube"],
  [/мыл|sapon/i, "soap"],
  [/крем|crema|сыворот|siero|маск/i, "jar"],
  [/скатерт|лён|полотен|tovagli|tessil/i, "cloth"],
  [/миск|керамик|ciotol|тарел|piatt|майолик/i, "bowl"],
  [/уксус|aceto|бальзам/i, "vinegar"],
  [/масло|olio/i, "oil"],
  [/паст|pasta/i, "pasta"],
  [/кофе|caff/i, "coffee"],
];

const FALLBACK: Record<Category, Kind> = { SHOES: "loafer", CLOTHES: "shirt", BEAUTY: "jar", HOME: "bowl", FOOD: "oil" };

// Пары «фон / основной / тень» — приглушённые, чтобы товар читался, а интерфейс не спорил с ним
const PALETTES = [
  { bg: "#EFE9E1", main: "#8A5532", shade: "#6B3F22", detail: "#F3E6D6" },
  { bg: "#E6EBF2", main: "#22355A", shade: "#16243F", detail: "#C9D6EA" },
  { bg: "#F1E7E8", main: "#A8445A", shade: "#843247", detail: "#F4D9DF" },
  { bg: "#E8EDE4", main: "#55703F", shade: "#3F5730", detail: "#DCE7D1" },
  { bg: "#F2EDE3", main: "#C29A4E", shade: "#9C7A36", detail: "#FBF1DA" },
  { bg: "#EAE8F1", main: "#5E5192", shade: "#463C73", detail: "#DDD8EE" },
  { bg: "#ECEBE8", main: "#33343A", shade: "#1C1D21", detail: "#D9D8D3" },
  { bg: "#E5EEEE", main: "#2F7A78", shade: "#215E5C", detail: "#CFE6E4" },
];

export function kindOf(title: string, category: Category): Kind {
  return RULES.find(([re]) => re.test(title))?.[1] ?? FALLBACK[category];
}

export function paletteOf(id: number) {
  return PALETTES[Math.abs(id) % PALETTES.length];
}

function Shape({ kind, c }: { kind: Kind; c: (typeof PALETTES)[number] }) {
  const { main, shade, detail } = c;
  switch (kind) {
    case "loafer":
      return (
        <g>
          <ellipse cx="102" cy="168" rx="80" ry="7" fill="#000" opacity=".08" />
          {/* задник выше носка: силуэт лофера в профиль */}
          <path d="M26 156V112c0-8 5-12 12-12 10 0 18 4 28 6l28 4c22 4 40 12 56 24 14 10 26 14 26 22z" fill={main} />
          <path d="M34 108c16-6 40-2 58 8-20 6-42 6-58 0z" fill={shade} />
          <path d="M98 118c18 2 36 10 50 22" fill="none" stroke={detail} strokeWidth="3" strokeLinecap="round" strokeDasharray="1 6" />
          <rect x="96" y="120" width="26" height="7" rx="3.5" fill={detail} transform="rotate(14 109 123)" />
          <path d="M22 154h156a6 6 0 0 1 0 12H28a6 6 0 0 1-6-6z" fill={shade} />
          <path d="M26 154h18v12H26z" fill={shade} />
        </g>
      );
    case "boot":
      return (
        <g>
          <ellipse cx="104" cy="182" rx="70" ry="7" fill="#000" opacity=".08" />
          <path d="M66 40h52l3 86c22 3 40 13 49 30 3 6 4 12 4 20H58l4-48z" fill={main} />
          <path d="M92 58h22l2 64H94z" fill={shade} />
          <path d="M56 176h120a5 5 0 0 1 0 10H56a5 5 0 0 1 0-10z" fill={shade} />
          <path d="M116 40h8v18h-8z" fill={detail} />
        </g>
      );
    case "sneaker":
      return (
        <g>
          <ellipse cx="100" cy="172" rx="78" ry="8" fill="#000" opacity=".08" />
          <path d="M30 154c0-24 10-40 30-44l30 6 18-10c24 10 50 18 62 32 4 5 6 10 6 16z" fill={detail} stroke={main} strokeWidth="3" />
          <path d="M28 156h150v8a8 8 0 0 1-8 8H36a8 8 0 0 1-8-8z" fill={main} />
          <path d="M70 120l40 6M76 132l40 6" stroke={shade} strokeWidth="3" strokeLinecap="round" />
        </g>
      );
    case "knit":
      return (
        <g>
          <path d="M70 46l-38 22-10 74 22 4 10-50v94h92V96l10 50 22-4-10-74-38-22c-6 12-18 18-30 18s-24-6-30-18z" fill={main} />
          <path d="M100 64v126" stroke={shade} strokeWidth="3" />
          {[84, 106, 128, 150, 172].map((y) => <circle key={y} cx="106" cy={y} r="3.5" fill={detail} />)}
          <path d="M54 182h92" stroke={shade} strokeWidth="8" />
          <path d="M70 46c6 12 18 18 30 18s24-6 30-18" fill="none" stroke={shade} strokeWidth="6" />
        </g>
      );
    case "shirt":
      return (
        <g>
          <path d="M74 44l-42 20-12 70 22 6 12-46v100h92V94l12 46 22-6-12-70-42-20-26 20z" fill={detail} stroke={main} strokeWidth="3" />
          <path d="M74 44l26 20 26-20 6 18-20 18-12-14-12 14-20-18z" fill={main} />
          <path d="M100 66v122" stroke={main} strokeWidth="2" />
          {[90, 112, 134, 156].map((y) => <circle key={y} cx="106" cy={y} r="3" fill={shade} />)}
        </g>
      );
    case "coat":
      return (
        <g>
          <path d="M70 36l-36 20-14 116h30l8-80v104h84V92l8 80h30L166 56l-36-20-30 40z" fill={main} />
          <path d="M70 36l30 40 30-40 10 26-40 44-40-44z" fill={shade} />
          {[120, 146].map((y) => <circle key={y} cx="110" cy={y} r="4" fill={detail} />)}
        </g>
      );
    case "jar":
      return (
        <g>
          <ellipse cx="100" cy="178" rx="52" ry="6" fill="#000" opacity=".08" />
          <rect x="52" y="96" width="96" height="80" rx="18" fill={main} />
          <rect x="58" y="74" width="84" height="26" rx="8" fill={shade} />
          <rect x="70" y="122" width="60" height="30" rx="6" fill={detail} />
        </g>
      );
    case "tube":
      return (
        <g>
          <ellipse cx="100" cy="186" rx="40" ry="6" fill="#000" opacity=".08" />
          <rect x="68" y="66" width="64" height="118" rx="20" fill={main} />
          <rect x="84" y="40" width="32" height="30" rx="6" fill={shade} />
          <rect x="78" y="108" width="44" height="46" rx="6" fill={detail} />
        </g>
      );
    case "soap":
      return (
        <g>
          <ellipse cx="100" cy="180" rx="70" ry="7" fill="#000" opacity=".08" />
          <rect x="40" y="140" width="120" height="36" rx="14" fill={shade} />
          <rect x="48" y="110" width="104" height="34" rx="14" fill={main} />
          <rect x="58" y="82" width="84" height="32" rx="14" fill={detail} stroke={main} strokeWidth="3" />
        </g>
      );
    case "cloth":
      return (
        <g>
          <ellipse cx="100" cy="176" rx="76" ry="7" fill="#000" opacity=".08" />
          <path d="M30 150l20-30h120l-20 30z" fill={shade} />
          <path d="M30 130l20-30h120l-20 30z" fill={main} />
          <path d="M30 110l20-30h120l-20 30z" fill={detail} stroke={main} strokeWidth="3" />
          <path d="M30 150h120v20H30z" fill={main} />
          <path d="M52 160h76" stroke={detail} strokeWidth="3" strokeDasharray="6 6" />
        </g>
      );
    case "bowl":
      return (
        <g>
          <ellipse cx="100" cy="176" rx="64" ry="7" fill="#000" opacity=".08" />
          <path d="M28 104h144c0 40-32 70-72 70s-72-30-72-70z" fill={main} />
          <ellipse cx="100" cy="104" rx="72" ry="16" fill={shade} />
          <ellipse cx="100" cy="104" rx="60" ry="10" fill={detail} />
          {[60, 84, 108, 132].map((x) => <circle key={x} cx={x + 6} cy="140" r="6" fill={detail} />)}
        </g>
      );
    case "oil":
      return (
        <g>
          <ellipse cx="100" cy="190" rx="36" ry="6" fill="#000" opacity=".08" />
          <path d="M88 26h24v26c14 8 22 22 22 40v92a6 6 0 0 1-6 6H72a6 6 0 0 1-6-6V92c0-18 8-32 22-40z" fill={main} />
          <rect x="86" y="18" width="28" height="14" rx="4" fill={shade} />
          <rect x="74" y="112" width="52" height="48" rx="4" fill={detail} />
          <path d="M86 138c8-12 20-12 28 0" fill="none" stroke={main} strokeWidth="3" />
        </g>
      );
    case "vinegar":
      return (
        <g>
          <ellipse cx="100" cy="188" rx="40" ry="6" fill="#000" opacity=".08" />
          <path d="M92 40h16v30c22 10 36 34 36 62 0 30-18 54-44 54s-44-24-44-54c0-28 14-52 36-62z" fill={main} />
          <rect x="90" y="28" width="20" height="16" rx="4" fill={shade} />
          <rect x="72" y="118" width="56" height="34" rx="4" fill={detail} />
        </g>
      );
    case "pasta":
      return (
        <g>
          <ellipse cx="100" cy="186" rx="60" ry="6" fill="#000" opacity=".08" />
          <rect x="50" y="40" width="100" height="144" rx="6" fill={main} />
          <rect x="62" y="70" width="76" height="70" rx="34" fill={detail} />
          {[[78, 92], [100, 86], [120, 96], [86, 112], [110, 116]].map(([x, y]) => (
            <rect key={`${x}-${y}`} x={x - 10} y={y - 4} width="20" height="8" rx="4" fill={shade} transform={`rotate(-30 ${x} ${y})`} />
          ))}
          <rect x="62" y="152" width="76" height="14" rx="3" fill={shade} />
        </g>
      );
    case "coffee":
      return (
        <g>
          <ellipse cx="100" cy="188" rx="58" ry="6" fill="#000" opacity=".08" />
          <path d="M52 56h96l8 128H44z" fill={main} />
          <path d="M52 40h96v20H52z" fill={shade} />
          <circle cx="100" cy="92" r="10" fill={detail} />
          <rect x="66" y="120" width="68" height="40" rx="4" fill={detail} />
        </g>
      );
  }
}

export function ProductArt({ id, title, category, size = "card", label, palette }: {
  id: number;
  title: string;
  category: Category;
  size?: "card" | "hero" | "mini";
  label?: string;
  /** Номер палитры вместо вычисленной по id — для тёмных фонов кампаний. */
  palette?: number;
}) {
  const c = palette === undefined ? paletteOf(id) : PALETTES[palette % PALETTES.length];
  return (
    <div className={`art art-${size}`} style={{ background: c.bg }}>
      <svg viewBox="0 0 200 210" role="img" aria-label={label ?? title}>
        <Shape kind={kindOf(title, category)} c={c} />
      </svg>
    </div>
  );
}
