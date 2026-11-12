import i18n from "i18next";
import { initReactI18next } from "react-i18next";

import en from "@/locales/en/translation.json";
import es from "@/locales/es/translation.json";

export const supportedLanguages = ["en", "es"] as const;

export type Language = (typeof supportedLanguages)[number];

export const resources = {
  en: { translation: en },
  es: { translation: es },
} as const;

/** Follow the browser until the `locale` setting exists (docs/sdd.md, setting). */
function browserLanguage(): Language {
  return navigator.language.toLowerCase().startsWith("es") ? "es" : "en";
}

i18n.on("languageChanged", (language) => {
  document.documentElement.lang = language;
});

void i18n.use(initReactI18next).init({
  resources,
  lng: browserLanguage(),
  fallbackLng: "en",
  supportedLngs: supportedLanguages,
  interpolation: { escapeValue: false },
});
