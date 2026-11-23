import type { components } from "@subtrack/contract";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { healthQueryOptions } from "@/features/health/api/use-health";
import { apiClient } from "@/lib/api/client";
import type { DisplayTimeZone } from "@/lib/time/display-time-zone";

type SettingsDto = components["schemas"]["Settings"];

export interface Settings {
  defaultReminderDays: number;
  trialReminderDays: number;
  yearlyReminderDays: number;
  endingReminderDays: number;
  paymentHorizonDays: number;
  logoDevToken: string | null;
  locale: SettingsDto["locale"];
  theme: SettingsDto["theme"];
  fontSize: SettingsDto["font_size"];
  reducedMotion: SettingsDto["reduced_motion"];
  weekStart: number;
  lockPolicy: SettingsDto["lock_policy"];
  homeTimeZone: string | null;
  displayTimeZone: DisplayTimeZone;
}

function toSettings(dto: SettingsDto): Settings {
  return {
    defaultReminderDays: dto.default_reminder_days,
    trialReminderDays: dto.trial_reminder_days,
    yearlyReminderDays: dto.yearly_reminder_days,
    endingReminderDays: dto.ending_reminder_days,
    paymentHorizonDays: dto.payment_horizon_days,
    logoDevToken: dto.logo_dev_token,
    locale: dto.locale,
    theme: dto.theme,
    fontSize: dto.font_size,
    reducedMotion: dto.reduced_motion,
    weekStart: dto.week_start,
    lockPolicy: dto.lock_policy,
    homeTimeZone: dto.home_time_zone,
    displayTimeZone: dto.display_time_zone,
  };
}

function toDto(settings: Settings): SettingsDto {
  return {
    default_reminder_days: settings.defaultReminderDays,
    trial_reminder_days: settings.trialReminderDays,
    yearly_reminder_days: settings.yearlyReminderDays,
    ending_reminder_days: settings.endingReminderDays,
    payment_horizon_days: settings.paymentHorizonDays,
    logo_dev_token: settings.logoDevToken,
    locale: settings.locale,
    theme: settings.theme,
    font_size: settings.fontSize,
    reduced_motion: settings.reducedMotion,
    week_start: settings.weekStart,
    lock_policy: settings.lockPolicy,
    home_time_zone: settings.homeTimeZone,
    display_time_zone: settings.displayTimeZone,
  };
}

export const settingsQueryOptions = queryOptions({
  queryKey: ["settings"],
  queryFn: async ({ signal }) => {
    const { data, response } = await apiClient.GET("/settings", { signal });
    if (data === undefined) {
      throw new Error(
        `GET /settings failed with HTTP ${String(response.status)}`,
      );
    }
    return toSettings(data);
  },
});

export function useSettings() {
  return useQuery(settingsQueryOptions);
}

export function useUpdateSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (settings: Settings) => {
      const { data, response } = await apiClient.PUT("/settings", {
        body: toDto(settings),
      });
      if (data === undefined) {
        throw new Error(
          `PUT /settings failed with HTTP ${String(response.status)}`,
        );
      }
      return toSettings(data);
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(settingsQueryOptions.queryKey, saved);
      return queryClient.invalidateQueries({
        queryKey: healthQueryOptions.queryKey,
      });
    },
  });
}
