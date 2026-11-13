import { useTranslation } from "react-i18next";

import { useHealth } from "../api/use-health";

/** Footer line naming the backend that answers (docs/sdd.md, Frontend design). */
export function BackendFooter() {
  const { t } = useTranslation();
  const health = useHealth();

  return (
    <footer className="p-4 text-center text-sm text-muted-foreground">
      {health.isSuccess &&
        t("health.footer", {
          backend: t(`health.backendName.${health.data.backend}`),
        })}
    </footer>
  );
}
