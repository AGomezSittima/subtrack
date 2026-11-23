import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { deviceTimeZone } from "@/lib/time/device-time-zone";

import { useSettings, useUpdateSettings } from "../api/use-settings";

/** Saves the device's zone as the home time zone on first use and says so. */
export function HomeZoneNotice() {
  const { t } = useTranslation();
  const settings = useSettings();
  const { mutate, data: saved, isSuccess } = useUpdateSettings();
  const started = useRef(false);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    const current = settings.data;
    if (started.current || current?.homeTimeZone !== null) {
      return;
    }
    started.current = true;
    mutate({ ...current, homeTimeZone: deviceTimeZone() });
  }, [settings.data, mutate]);

  if (!isSuccess || saved.homeTimeZone === null || dismissed) {
    return null;
  }

  return (
    <div
      role="status"
      className="flex items-center justify-between gap-4 border-b bg-muted px-4 py-2 text-sm"
    >
      <p>{t("settings.homeZoneNotice", { zone: saved.homeTimeZone })}</p>
      <Button
        variant="ghost"
        size="sm"
        onClick={() => {
          setDismissed(true);
        }}
      >
        {t("settings.dismissNotice")}
      </Button>
    </div>
  );
}
