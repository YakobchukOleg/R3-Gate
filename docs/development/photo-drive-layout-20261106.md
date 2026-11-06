# WorkMeter SOLO 2.7.4 — photo folders

New photo uploads use `WorkMeter / record year / TAG + city + address / photo files`.
Month/day and organization folders are no longer created. The year comes from the record date.
Legacy `Adres` takes precedence over street/building/postcode fallback; whitespace and JSON nulls are handled.
Unassigned records and car stock retain `Bez obiektu` and `W samochodzie` under the year.
Saved legacy record folder links do not override the new canonical path. Uploaded file receipts
remain intact, so acknowledged photos are not resent. Newly acknowledged uploads update the
record folder link. Existing Drive files/folders are not moved, renamed or deleted; no account-wide migration is performed.
Backup archives and companion files remain in `WorkMeter / Backup`.

Six JVM cases cover the reference path, day/month consolidation, year boundaries, address formats,
missing/blank values and unassigned/car categories. Full existing CI remains enabled.
