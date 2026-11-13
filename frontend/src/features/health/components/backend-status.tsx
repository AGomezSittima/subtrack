import { useTranslation } from "react-i18next";

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

import { useHealth } from "../api/use-health";

export function BackendStatus() {
  const { t } = useTranslation();
  const health = useHealth();

  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <CardTitle>
          <h1>{t("health.title")}</h1>
        </CardTitle>
        {health.isSuccess && (
          <CardDescription>
            {t("health.version", { version: health.data.version })}
          </CardDescription>
        )}
      </CardHeader>
      <CardContent>
        {health.isPending && <p role="status">{t("health.loading")}</p>}
        {health.isError && (
          <p role="alert" className="text-destructive">
            {t("health.error")}
          </p>
        )}
        {health.isSuccess && (
          <p>
            {t("health.connected", {
              backend: t(`health.backendName.${health.data.backend}`),
            })}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
