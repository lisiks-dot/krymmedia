// Контурные иконки 24×24, цвет — currentColor.
const base = { width: 24, height: 24, viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 1.8, strokeLinecap: "round" as const, strokeLinejoin: "round" as const, "aria-hidden": true };

export const IconSearch = () => <svg {...base}><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></svg>;
export const IconHeart = () => <svg {...base}><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z" /></svg>;
export const IconBag = () => <svg {...base}><path d="M5 8h14l-1 12H6z" /><path d="M9 8a3 3 0 0 1 6 0" /></svg>;
export const IconBox = () => <svg {...base}><path d="M3 7l9-4 9 4v10l-9 4-9-4z" /><path d="M3 7l9 4 9-4M12 11v10" /></svg>;
export const IconHome = () => <svg {...base}><path d="M3 11l9-7 9 7v9H3z" /></svg>;
export const IconStore = () => <svg {...base}><path d="M4 9l1-5h14l1 5" /><path d="M4 9v11h16V9" /><path d="M4 9a2.7 2.7 0 0 0 5.3 0 2.7 2.7 0 0 0 5.4 0 2.7 2.7 0 0 0 5.3 0" /></svg>;
export const IconShield = () => <svg {...base}><path d="M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6z" /><path d="m8.5 12 2.5 2.5 4.5-5" /></svg>;
export const IconRuble = () => <svg {...base}><path d="M8 20V4h5a4 4 0 0 1 0 8H6M6 16h8" /></svg>;
export const IconTruck = () => <svg {...base}><path d="M3 6h11v10H3zM14 10h4l3 3v3h-7" /><circle cx="7" cy="18" r="2" /><circle cx="17" cy="18" r="2" /></svg>;
export const IconReturn = () => <svg {...base}><path d="M9 14 4 9l5-5" /><path d="M4 9h11a5 5 0 0 1 0 10h-3" /></svg>;
export const IconCheck = () => <svg {...base}><circle cx="12" cy="12" r="9" /><path d="m8 12 3 3 5-6" /></svg>;
